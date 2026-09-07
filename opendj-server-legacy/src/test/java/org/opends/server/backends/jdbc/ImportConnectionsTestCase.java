/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

/**
 * The {@link Importer} of the JDBC backend is used by several threads at once - phase two of an
 * import runs one thread per tree - and {@code java.sql.Connection} is not thread-safe. Sharing
 * one connection between those threads corrupts the driver rather than merely serializing the
 * work: the sql server driver keeps the reconnect listeners of a connection in a plain
 * {@code ArrayList} that every {@code prepareStatement()} and every statement {@code close()}
 * mutates, and two import threads on one connection walked it past the end of its array
 * (issue #891).
 * <p>
 * Needs no database: the connections are handed out by a driver of this test, which records which
 * thread issues a statement on which connection - and which thread commits, rolls back or returns
 * one while another is inside a statement on it - and holds the first statement of each thread at a
 * rendezvous until its peers have one in flight of their own. What that records is the defect
 * itself - two threads on one connection at the same time - rather than an exception of one driver,
 * so it holds for every dialect this backend takes.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class ImportConnectionsTestCase extends DirectoryServerTestCase {

	/** Long enough for a peer thread to reach a statement on a loaded machine, and no longer. */
	private static final long RENDEZVOUS_SECONDS = 30;

	private static final TreeName ID2ENTRY = new TreeName("dc=example,dc=com", "id2entry");
	private static final TreeName DN2ID = new TreeName("dc=example,dc=com", "dn2id");
	private static final TreeName STATE = new TreeName("dc=example,dc=com", "state");

	private final StubDriver stub = new StubDriver();

	/** The connections an import issued a statement on, in no particular order. */
	private final Set<Connection> used =
		Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>()));
	/** The same, in the order the import first issued a statement on them. */
	private final List<Connection> usedInOrder = Collections.synchronizedList(new ArrayList<Connection>());
	/** Every commit of the import, in the order the connections took them. */
	private final List<Connection> commits = Collections.synchronizedList(new ArrayList<Connection>());
	/** The thread inside a statement of a connection right now, one entry per connection in flight. */
	private final Map<Connection, Thread> inStatement =
		Collections.synchronizedMap(new IdentityHashMap<Connection, Thread>());
	/** Every pair of threads that was inside a statement of one connection at the same time. */
	private final List<String> shared = Collections.synchronizedList(new ArrayList<String>());

	/** Where the threads of a test meet, so that their statements are in flight at the same time. */
	private volatile CyclicBarrier rendezvous;
	/** Whether this thread has already met its peers: a thread waits there once, whatever it issues after. */
	private final ThreadLocal<Boolean> met = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private ExecutorService threads;
	/** The pool bound and its borrow deadline as this JVM had them, put back after every test. */
	private String poolMaxOfTheJvm;
	private String poolTimeoutOfTheJvm;

	@BeforeClass
	public void registerStubDriver() throws Exception {
		DriverManager.registerDriver(stub);
	}

	/**
	 * The bound of the pools of this suite, pinned: how many connections an import takes is clamped
	 * to it, so a value another suite of this package left set - CachedConnectionTestCase varies it
	 * - would decide the counts asserted here. A pool reads it once, when the first borrow of a
	 * connection string creates it, so it has to stand before the storage of a test is opened.
	 */
	@BeforeMethod
	public void pinThePoolBound() {
		poolMaxOfTheJvm = System.getProperty(CachedConnection.POOL_MAX_PROPERTY);
		poolTimeoutOfTheJvm = System.getProperty(CachedConnection.POOL_TIMEOUT_PROPERTY);
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "16");
	}

	@AfterClass
	public void deregisterStubDriver() throws Exception {
		DriverManager.deregisterDriver(stub);
	}

	@AfterMethod
	public void forgetWhatTheTestRecorded() {
		System.clearProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY);
		// put back rather than cleared: a sibling suite of this package varies this property, and a
		// pool is created with the value that stands when its connection string is first borrowed on
		putBack(CachedConnection.POOL_MAX_PROPERTY, poolMaxOfTheJvm);
		putBack(CachedConnection.POOL_TIMEOUT_PROPERTY, poolTimeoutOfTheJvm);
		// the rendezvous of the next test is not the one this thread already went to
		met.remove();
		if (threads != null) {
			threads.shutdownNow();
			threads = null;
		}
		rendezvous = null;
		forgetTheConnections();
		inStatement.clear();
		shared.clear();
	}

	private static void putBack(String property, String value) {
		if (value == null) {
			System.clearProperty(property);
		} else {
			System.setProperty(property, value);
		}
	}

	private void forgetTheConnections() {
		used.clear();
		usedInOrder.clear();
		commits.clear();
	}

	/**
	 * Two trees written at the same time are written through connections of their own. This is the
	 * contract {@code Importer} states and the defect of #891: every thread of phase two wrote
	 * through the one connection the importer borrowed in its constructor.
	 */
	@Test(timeOut = 120000)
	public void testTwoTreesAreWrittenThroughConnectionsOfTheirOwn() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "2");
		final String url = StubDriver.PREFIX + "importer-parallel";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			// both threads have to be inside a statement at once for the record below to say
			// anything: a run where one finished before the other started shares no connection
			// however the importer borrows them
			rendezvous = new CyclicBarrier(2);
			threads = Executors.newFixedThreadPool(2);
			final Future<?> id2entry = put(importer, ID2ENTRY);
			final Future<?> dn2id = put(importer, DN2ID);
			id2entry.get(RENDEZVOUS_SECONDS * 2, TimeUnit.SECONDS);
			dn2id.get(RENDEZVOUS_SECONDS * 2, TimeUnit.SECONDS);

			assertEquals(shared, Collections.emptyList(),
				"two import threads issued a statement on one connection at the same time");
			assertEquals(used.size(), 2, "the two trees of the import did not get a connection each");
		} finally {
			importer.close();
			storage.close();
		}
	}

	/**
	 * ... and no more connections than that: an import takes them for its whole duration, and the
	 * pool they come from is bounded and shared with the operations of every other backend on that
	 * database (#878). One thread per tree is what phase two runs, and a default backend has trees
	 * enough to empty a default pool.
	 */
	@Test(timeOut = 120000)
	public void testTreesShareTheConnectionsOfABoundAboveOne() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "2");
		final String url = StubDriver.PREFIX + "importer-bounded-two";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			// five trees over two connections: the bound is what the round robin wraps at
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(new TreeName("dc=example,dc=com", "id2childrencount"),
				ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(new TreeName("dc=example,dc=com", "referral"),
				ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));

			assertEquals(used.size(), 2, "the import did not spread its trees over the connections of its bound");
		} finally {
			importer.close();
			storage.close();
		}
	}

	/** ... and a bound the pool cannot honour is the pool's, not the property's. */
	@Test(timeOut = 120000)
	public void testTheBoundOfAnImportIsClampedToTheBoundOfThePool() throws Exception {
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "2");
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "8");
		final String url = StubDriver.PREFIX + "importer-clamped";
		final JDBCStorage storage = importingStorage(url);
		try {
			assertEquals(storage.importConnections(), 2, "the bound of an import was not clamped to the pool");
		} finally {
			storage.close();
		}
	}

	@Test(timeOut = 120000)
	public void testTheConnectionsOfAnImportAreBounded() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "1");
		final String url = StubDriver.PREFIX + "importer-bounded";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));

			assertEquals(used.size(), 1, "the import took more connections than the bound allows");
		} finally {
			importer.close();
			storage.close();
		}
	}

	/**
	 * The trees decide which connection a write goes through, not the threads that write them. The
	 * connections of an import are transactions of their own, so two of them writing one row would
	 * have the second wait for the first to commit - which an import does in {@code close()}, when
	 * the thread that would have to release the row is long done, and the bulk class carries no
	 * bound to break the wait. {@code setTrust()} writes the state tree of a container from an
	 * import thread in {@code beforePhaseOne} and from the closing thread in {@code afterPhaseTwo},
	 * which is exactly that shape.
	 */
	@Test(timeOut = 120000)
	public void testOneTreeIsWrittenThroughOneConnectionWhoeverWritesIt() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "4");
		final String url = StubDriver.PREFIX + "importer-one-tree";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			// three threads, one tree: an import thread of phase one, another of phase two, and the
			// thread that closes the importer, which is the one afterPhaseTwo runs on
			putOnAThreadOfItsOwn(importer, STATE);
			putOnAThreadOfItsOwn(importer, STATE);
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));

			assertEquals(used.size(), 1, "one tree was written through more than one connection");
		} finally {
			importer.close();
			storage.close();
		}
	}

	/**
	 * Every connection an import took is committed and given back, not only the one its
	 * constructor borrowed: what a connection left behind holds is a transaction of the import and
	 * a permit of the pool, and a pool is never removed from the map - so a permit lost to an
	 * import is lost for the life of the server (#878).
	 */
	@Test(timeOut = 120000)
	public void testEveryConnectionOfAnImportIsCommittedAndReturned() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "3");
		final String url = StubDriver.PREFIX + "importer-returned";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));

			importer.close();

			assertEquals(used.size(), 3, "the three trees of the import did not get a connection each");
			for (final Connection con : connectionsUsed()) {
				verify(con, times(1)).commit();
			}
			assertEquals(CachedConnection.poolOf(url).idleCount(), 3, "an import kept a connection of the pool");
		} finally {
			storage.close();
		}
		assertEquals(CachedConnection.poolOf(url).meteredCount(), 0, "an import kept a permit of the pool");
	}

	/**
	 * A clear commits every connection of the import, not only the one whose tree it clears. The
	 * one connection an import used to hold made that so of its own accord - {@code clearTree()}
	 * ends in a commit, and that commit made durable every write the import had made so far, the
	 * {@code setTrust(false)} of {@code beforePhaseOne} among them. Left to {@code close()}, that
	 * flag would still be uncommitted while the table it describes was emptied and committed, and a
	 * server that stopped in between would come back to an index that is empty and marked trusted.
	 */
	@Test(timeOut = 120000)
	public void testAClearCommitsTheOtherConnectionsOfTheImport() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "2");
		final String url = StubDriver.PREFIX + "importer-clear-commits";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			// the state tree stands for the trust flag: written, and left uncommitted until
			// something commits the connection it went to
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.clearTree(ID2ENTRY);

			assertEquals(connectionsCommitted(), 2,
				"the clear left the writes of the other connection of the import uncommitted");
		} finally {
			importer.close();
			storage.close();
		}
	}

	/**
	 * The connection of the last write is committed last. The connections of an import are
	 * transactions of their own, so {@code close()} cannot commit them as one - and the last thing
	 * an import writes is the flag that says the rest of it is good ({@code setTrust(true)} of
	 * {@code afterPhaseTwo}). Committed last, that flag is rolled back with the connection it is on
	 * whenever an earlier commit fails, so a failed import cannot leave an index marked trusted
	 * over data that never got there.
	 */
	@Test(timeOut = 120000)
	public void testTheConnectionsAreCommittedInTheOrderTheyWereLastWrittenTo() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "3");
		final String url = StubDriver.PREFIX + "importer-last-write";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			// the two writes that stand for the trust flags afterPhaseTwo writes, one per base DN:
			// both have to be committed after the connection that holds nothing but data
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k2"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k2"), ByteString.valueOfUtf8("v"));

			importer.close();

			assertEquals(usedInOrder.size(), 3, "the three trees of the import did not get a connection each");
			assertEquals(commits, Arrays.asList(usedInOrder.get(2), usedInOrder.get(0), usedInOrder.get(1)),
				"the connections were not committed in the order they were last written to");
		} finally {
			storage.close();
		}
	}

	/**
	 * A write that arrives after {@code close()} takes no connection. Phase two gives its threads
	 * five seconds to answer an interrupt and then closes the importer whether they did or not, so
	 * a write can reach it once its connections are back in the pool - and one that borrowed there
	 * would take a permit nothing is left to give back, out of a pool that is never removed from
	 * the map (#878).
	 */
	@Test(timeOut = 120000)
	public void testAWriteAfterCloseTakesNoConnection() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "4");
		final String url = StubDriver.PREFIX + "importer-after-close";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.close();

			try {
				importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
				fail("a write that arrives after the import is closed must be refused");
			} catch (StorageRuntimeException expected) {
				// the import is over: there is no transaction left for this write to belong to
			}
			try {
				// the same for a clear, which commits the other connections of the import before it
				// empties a tree: those are back in the pool, serving whoever borrowed them next
				importer.clearTree(ID2ENTRY);
				fail("a clear that arrives after the import is closed must be refused");
			} catch (StorageRuntimeException expected) {
				// as above
			}

			assertEquals(used.size(), 1, "a write after close() took a connection of the pool");
			assertEquals(CachedConnection.poolOf(url).idleCount(), 1, "a write after close() kept a connection");
		} finally {
			storage.close();
		}
		assertEquals(CachedConnection.poolOf(url).meteredCount(), 0, "a write after close() kept a permit");
	}

	/**
	 * A commit that fails still gives every connection of the import back. What one left behind
	 * holds is a permit of a pool that is never removed from the map, so a permit lost to a failed
	 * import is lost for the life of the server (#878).
	 */
	@Test(timeOut = 120000)
	public void testAFailedCommitStillReturnsEveryConnection() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "3");
		final String url = StubDriver.PREFIX + "importer-failed-commit";
		final JDBCStorage storage = importingStorage(url);
		final Importer importer = storage.startImport();
		try {
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(STATE, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			doThrow(new SQLException("the socket went away")).when(usedInOrder.get(1)).commit();

			try {
				importer.close();
				fail("the failure of the commit was not reported");
			} catch (StorageRuntimeException expected) {
				assertEquals(expected.getCause().getMessage(), "the socket went away");
			}

			assertEquals(CachedConnection.poolOf(url).idleCount(), 3, "a failed import kept a connection of the pool");
		} finally {
			storage.close();
		}
		assertEquals(CachedConnection.poolOf(url).meteredCount(), 0, "a failed import kept a permit of the pool");
	}

	/**
	 * A tree that cannot be given a connection of its own is written through one the import already
	 * holds, rather than failing the import. The connections are taken as the trees are first
	 * touched, so a pool at its bound would otherwise stop an import halfway through - with the
	 * clears of {@code beforePhaseOne} already committed - where the one connection an import held
	 * before #891 would have carried it to the end.
	 */
	@Test(timeOut = 120000)
	public void testATreeSharesAConnectionWhenThePoolHasNoneToSpare() throws Exception {
		System.setProperty(CachedConnection.POOL_MAX_PROPERTY, "2");
		System.setProperty(CachedConnection.POOL_TIMEOUT_PROPERTY, "1"); // nothing is going to be returned
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "2");
		final String url = StubDriver.PREFIX + "importer-pool-full";
		final JDBCStorage storage = importingStorage(url);
		try {
			final Importer importer = storage.startImport(); // one of the two connections of the pool
			// and the other one to an operation of the server, so that the pool has none to spare
			final Connection heldByAnOperation = CachedConnection.getConnection(url);
			try {
				importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
				// on a thread of its own: a thread that already holds a connection of the pool is
				// exempt from waiting at its bound, and every thread of phase two starts holding none
				putOnAThreadOfItsOwn(importer, DN2ID);

				assertEquals(used.size(), 1, "the tree the pool had no connection for did not share one");
			} finally {
				importer.close();
				heldByAnOperation.close();
			}
		} finally {
			storage.close();
		}
	}

	/**
	 * A second {@code close()} touches nothing: the connections of the import are back in the pool
	 * by then, and the pool hands them out again - so a close that walked its map a second time
	 * would roll back and re-pool a connection another borrower is holding.
	 */
	@Test(timeOut = 120000)
	public void testASecondCloseTouchesNothingTheImportGaveBack() throws Exception {
		System.setProperty(JDBCStorage.IMPORT_CONNECTIONS_PROPERTY, "2");
		final String url = StubDriver.PREFIX + "importer-second-close";
		final JDBCStorage storage = importingStorage(url);
		try {
			final Importer importer = storage.startImport();
			importer.put(ID2ENTRY, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.put(DN2ID, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			importer.close();

			// the pool hands one of them straight back out, the way an LDAP operation would
			final Connection borrowedAgain = CachedConnection.getConnection(url);
			try {
				importer.close();
				assertEquals(CachedConnection.poolOf(url).idleCount(), 1,
					"a second close() returned a connection the pool had already handed to somebody else");
			} finally {
				borrowedAgain.close();
			}
		} finally {
			storage.close();
		}
		assertEquals(CachedConnection.poolOf(url).meteredCount(), 0, "a connection of the import was lost");
	}

	/** How many distinct connections of the import have been committed so far. */
	private int connectionsCommitted() {
		final Set<Connection> committed =
			Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>());
		synchronized (commits) {
			committed.addAll(commits);
		}
		return committed.size();
	}

	/** A storage open for writing over the driver of this test, which is what an import needs. */
	private JDBCStorage importingStorage(String url) throws Exception {
		// mockCfg rather than a bare mock: it answers every getter with the value declared in
		// JDBCBackendConfiguration.xml, so a storage that comes to read a setting this test never
		// thought of gets the default instead of a null - the way the sibling suites of this package
		// build their configurations
		final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
		when(cfg.getDBDirectory()).thenReturn(url);
		final JDBCStorage storage = new JDBCStorage(cfg, null);
		storage.open(AccessMode.READ_WRITE);
		// the connection the open borrowed and gave back is not one of the import's: only what a
		// statement was issued on is recorded, and the open issues none
		forgetTheConnections();
		return storage;
	}

	private Future<?> put(final Importer importer, final TreeName treeName) {
		return threads.submit(() ->
			importer.put(treeName, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v")));
	}

	/** A write of a thread that has written nothing before, the way every thread of an import starts. */
	private void putOnAThreadOfItsOwn(final Importer importer, final TreeName treeName) throws Exception {
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread thread = new Thread(() -> {
			try {
				importer.put(treeName, ByteString.valueOfUtf8("k"), ByteString.valueOfUtf8("v"));
			} catch (Throwable t) {
				failure.set(t);
			}
		}, "import-" + treeName.getIndexId());
		thread.start();
		thread.join(TimeUnit.SECONDS.toMillis(RENDEZVOUS_SECONDS));
		if (failure.get() != null) {
			throw new IllegalStateException("the write of " + treeName + " failed", failure.get());
		}
		// asserted rather than left to the join: a write still blocked - on the monitor of a
		// connection, or on a borrow - would otherwise leave the assertions of the test standing on
		// a run that never made the state they are about
		assertFalse(thread.isAlive(), "the write of " + treeName + " did not finish");
	}

	private List<Connection> connectionsUsed() {
		synchronized (used) {
			return new ArrayList<>(used);
		}
	}

	/**
	 * A connection of this test: it answers every statement with one mock of its own and records
	 * the thread that asked for it while that thread is inside the call.
	 */
	private Connection newConnection() throws SQLException {
		final Connection con = org.mockito.Mockito.mock(Connection.class);
		final PreparedStatement statement = org.mockito.Mockito.mock(PreparedStatement.class);
		when(con.isValid(anyInt())).thenReturn(true);
		// the statement names the connection it runs on, as CachedConnection.prepareStatement() has
		// it: the bound of a statement is arbitrated on the connection, which is read off the statement
		when(statement.getConnection()).thenReturn(con);
		// the commits of the import in the order they happen: which connection is committed when is
		// what keeps a failed import from leaving an index marked trusted over data that is not there
		doAnswer(invocation -> {
			recordAThreadInsideAStatement(con, "commit");
			commits.add(con);
			return null;
		}).when(con).commit();
		// the return of a connection rolls it back and hands it to the next borrower, so neither may
		// happen while a statement of an import thread is in flight on it
		doAnswer(invocation -> {
			recordAThreadInsideAStatement(con, "rollback");
			return null;
		}).when(con).rollback();
		doAnswer(invocation -> {
			recordAThreadInsideAStatement(con, "close");
			return null;
		}).when(con).close();
		when(con.prepareStatement(anyString())).thenAnswer(invocation -> {
			if (used.add(con)) {
				usedInOrder.add(con);
			}
			final Thread peer = inStatement.putIfAbsent(con, Thread.currentThread());
			if (peer != null && peer != Thread.currentThread()) {
				shared.add(peer.getName() + " and " + Thread.currentThread().getName());
			}
			try {
				meetPeers();
				return statement;
			} finally {
				inStatement.remove(con, Thread.currentThread());
			}
		});
		return con;
	}

	/** Records a connection touched from one thread while another is inside a statement on it. */
	private void recordAThreadInsideAStatement(Connection con, String what) {
		final Thread inside = inStatement.get(con);
		if (inside != null && inside != Thread.currentThread()) {
			shared.add(what + " of " + Thread.currentThread().getName() + " while " + inside.getName()
				+ " was inside a statement");
		}
	}

	/**
	 * Holds the first statement of a thread until every peer of the rendezvous has one in flight
	 * too. Reported as a failure of the statement rather than waited out: a rendezvous nobody else
	 * reaches is an import that serialized its threads, which is what the parallel test is about.
	 */
	private void meetPeers() throws SQLException {
		final CyclicBarrier barrier = rendezvous;
		if (barrier == null || met.get()) {
			return;
		}
		met.set(Boolean.TRUE);
		try {
			barrier.await(RENDEZVOUS_SECONDS, TimeUnit.SECONDS);
		} catch (TimeoutException | BrokenBarrierException e) {
			throw new SQLException("no peer thread had a statement of its own in flight within "
				+ RENDEZVOUS_SECONDS + "s: the import did not let its threads write at the same time", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SQLException("interrupted while waiting for the peer threads of the import", e);
		}
	}

	/** A driver of this test, so that the connections of an import need no database behind them. */
	private final class StubDriver implements Driver {
		static final String PREFIX = "jdbc:opendj-import-stub:";

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			return acceptsURL(url) ? newConnection() : null;
		}

		@Override
		public boolean acceptsURL(String url) {
			return url != null && url.startsWith(PREFIX);
		}

		@Override
		public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
			return new DriverPropertyInfo[0];
		}

		@Override
		public int getMajorVersion() {
			return 1;
		}

		@Override
		public int getMinorVersion() {
			return 0;
		}

		@Override
		public boolean jdbcCompliant() {
			return false;
		}

		@Override
		public Logger getParentLogger() {
			return Logger.getLogger(StubDriver.class.getName());
		}
	}
}
