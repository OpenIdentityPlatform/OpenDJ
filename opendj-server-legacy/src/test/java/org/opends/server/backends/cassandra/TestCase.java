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
 * Copyright 2023-2026 3A Systems, LLC.
 */
package org.opends.server.backends.cassandra;

import static org.mockito.Mockito.when;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import java.net.InetSocketAddress;

//docker run --rm -it -p 9042:9042 --name cassandra cassandra

@Test
public class TestCase extends PluggableBackendImplTestCase<CASBackendCfg> {

	CassandraContainer cassandraContainer;
	@Override
	protected Backend createBackend() {
		if(DockerClientFactory.instance().isDockerAvailable()) {
			cassandraContainer = new CassandraContainer<>("cassandra:latest").withExposedPorts(9042);
			cassandraContainer.start();
			InetSocketAddress contactPoint = cassandraContainer.getContactPoint();
			final String contactPointString = String.format("%s:%s", contactPoint.getHostName(), contactPoint.getPort());
			System.setProperty("datastax-java-driver.basic.contact-points.0", contactPointString);
			System.setProperty("datastax-java-driver.basic.load-balancing-policy.local-datacenter", cassandraContainer.getLocalDatacenter());
		}

		//test allow cassandra
		try(CqlSession session=CqlSession.builder()
				.withConfigLoader(DriverConfigLoader.fromDefaults(CASStorage.class.getClassLoader()))
				.build()){
			session.close();
		}catch (AllNodesFailedException e) {
			throw new SkipException("run before test: docker run --rm -it -p 9042:9042 --name cassandra cassandra");
		}
		return new Backend();
	}

	@Override
	protected CASBackendCfg createBackendCfg() {
		CASBackendCfg backendCfg = mockCfg(CASBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn("CASTestCase");
		when(backendCfg.getDBDirectory()).thenReturn("CASTestCase");
		return backendCfg;
	}

	@AfterClass
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		if(cassandraContainer != null) {
			cassandraContainer.close();
		}
	}

	private static ByteString key(int i) {
		return ByteString.valueOfUtf8(String.format("key%02d", i));
	}

	private static ByteString value(int i) {
		return ByteString.valueOfUtf8("value" + i);
	}

	/**
	 * The driver ResultSet is consumed once and cannot be rewound, so every repositioning that is
	 * not a forward move within the already-fetched rows must run a new server-side slice query.
	 * The old implementation "restarted" the iterator via rc.iterator(), which is a no-op: backward
	 * repositioning returned the wrong row and positionToIndex counted from the current position.
	 */
	@Test
	public void testCursorReposition() throws Exception {
		final CASStorage storage = new CASStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCursorReposition", "tree");
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					for (int i = 0; i < 40; i++) {
						txn.put(tree, key(i), value(i));
					}
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						assertEquals(impl.queryCount, 0); // opening a cursor runs no query

						assertTrue(cursor.positionToKeyOrNext(key(5))); // server-side seek
						assertEquals(cursor.getKey(), key(5));
						assertEquals(impl.queryCount, 1);
						assertTrue(cursor.positionToKeyOrNext(key(5))); // same key: stays, no query
						assertEquals(cursor.getKey(), key(5));
						assertEquals(impl.queryCount, 1);
						assertTrue(cursor.positionToKeyOrNext(key(9))); // forward: served from fetched rows
						assertEquals(cursor.getKey(), key(9));
						assertEquals(cursor.getValue(), value(9));
						assertEquals(impl.queryCount, 1);

						// backward: the old no-op "restart" returned the next remaining row instead
						assertTrue(cursor.positionToKeyOrNext(key(2)));
						assertEquals(cursor.getKey(), key(2));
						assertEquals(cursor.getValue(), value(2));
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key021"))); // between rows
						assertEquals(cursor.getKey(), key(3));

						assertTrue(cursor.positionToKey(key(1))); // backward exact match
						assertEquals(cursor.getKey(), key(1));
						assertFalse(cursor.positionToKey(ByteString.valueOfUtf8("key011"))); // missing key
						assertFalse(cursor.isDefined());
						assertTrue(cursor.positionToKey(key(1)));
						assertTrue(cursor.next()); // next() continues right after the positioned key (DN2ID)
						assertEquals(cursor.getKey(), key(2));

						// positionToIndex counts from the first row, not from the current position
						assertTrue(cursor.positionToIndex(0));
						assertEquals(cursor.getKey(), key(0));
						assertTrue(cursor.positionToIndex(39));
						assertEquals(cursor.getKey(), key(39));
						assertFalse(cursor.positionToIndex(40));

						assertTrue(cursor.positionToLastKey()); // LIMIT 1 query, no partition scan
						assertEquals(cursor.getKey(), key(39));
						assertFalse(cursor.next());
						assertTrue(cursor.positionToKeyOrNext(key(0))); // reposition after exhaustion
						assertEquals(cursor.getKey(), key(0));
						assertFalse(cursor.positionToKeyOrNext(ByteString.valueOfUtf8("key99"))); // beyond last

						// VLVIndex.evaluateVLVRequestByAssertion: seek to the assertion, then to the start
						assertTrue(cursor.positionToKeyOrNext(key(20)) && cursor.positionToIndex(0));
						assertEquals(cursor.getKey(), key(0));
					}

					// DN2ID.ChildrenCursor: reposition to currentKey+0x01 for every row; forward
					// repositioning is served from the fetched rows, so the scan stays at ~2 queries
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						final CASStorage.CursorImpl impl = (CASStorage.CursorImpl) cursor;
						assertTrue(cursor.positionToKeyOrNext(key(0)));
						int rows = 1;
						while (cursor.positionToKeyOrNext(
								new ByteStringBuilder().appendBytes(cursor.getKey()).appendByte(0x01).toByteString())) {
							rows++;
						}
						assertEquals(rows, 40);
						assertTrue(impl.queryCount <= 3, "sibling scan took " + impl.queryCount + " queries");
					}
					return null;
				}
			});
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}

	/** Serving forward repositioning from fetched rows relies on the unsigned blob clustering order. */
	@Test
	public void testCursorKeyOrderIsUnsigned() throws Exception {
		final CASStorage storage = new CASStorage(createBackendCfg(), null);
		final TreeName tree = new TreeName("testCursorOrder", "tree");
		final ByteString low = ByteString.valueOfBytes(new byte[] { 0x7F });
		final ByteString high = ByteString.valueOfBytes(new byte[] { (byte) 0x80, 0x01 });
		try {
			storage.open(AccessMode.READ_WRITE);
			storage.write(new WriteOperation() {
				@Override
				public void run(WriteableTransaction txn) throws Exception {
					txn.openTree(tree, true);
					txn.put(tree, low, value(1));
					txn.put(tree, high, value(2));
				}
			});
			storage.read(new ReadOperation<Void>() {
				@Override
				public Void run(ReadableTransaction txn) throws Exception {
					try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(tree)) {
						// with a signed collation 0x80 would sort before 0x7F and these would fail
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), low);
						assertTrue(cursor.next());
						assertEquals(cursor.getKey(), high);
						assertTrue(cursor.positionToKeyOrNext(ByteString.valueOfBytes(new byte[] { (byte) 0x80 })));
						assertEquals(cursor.getKey(), high);
						assertTrue(cursor.positionToLastKey());
						assertEquals(cursor.getKey(), high);
					}
					return null;
				}
			});
		} finally {
			try {
				storage.write(new WriteOperation() {
					@Override
					public void run(WriteableTransaction txn) throws Exception {
						txn.deleteTree(tree);
					}
				});
			} catch (Exception ignored) {}
			storage.close();
		}
	}
}
