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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests how a failure is classified - as a transaction conflict, or as a connection the database dropped - which
 * is what decides whether {@link JDBCStorage#write} replays the operation, and how long it waits before it does;
 * and what {@code write()} itself does with that verdict, replay and pool alike.
 * <p>
 * Runs without a database: the failures the drivers report are reproduced as synthetic
 * {@link SQLException}s carrying the same vendor error number and SQLState, and the writes that carry them run
 * against mocked connections handed out by a driver of this test.
 */
@Test(sequential = true)
@SuppressWarnings("javadoc")
public class JDBCStorageRetryTest extends DirectoryServerTestCase
{
  /** Driver class names, which is what the classification keys the vendor error numbers off. */
  private static final String MSSQL = "com.microsoft.sqlserver.jdbc.SQLServerConnection";
  private static final String MYSQL = "com.mysql.cj.jdbc.ConnectionImpl";
  private static final String ORACLE = "oracle.jdbc.driver.T4CConnection";
  private static final String POSTGRES = "org.postgresql.jdbc.PgConnection";

  /** The tree every write of this test opens; the table name behind it is a hash of this name. */
  private static final TreeName TREE = new TreeName("dc=example,dc=com", "id2entry");

  private final StubDriver stub = new StubDriver();

  /** Every test gets a pool of its own: the pools and the distrust of a pool are keyed by the url. */
  private final AtomicInteger pools = new AtomicInteger();

  /** The statement a create of a test runs, so that a test can assert that it ran - or that it did not. */
  private PreparedStatement statements;

  /** A failure whose cause chain is a cycle, to check that walking it terminates. */
  private static final class SelfCausedException extends RuntimeException
  {
    private static final long serialVersionUID = 1L;

    @Override
    public synchronized Throwable getCause()
    {
      return this;
    }
  }

  @DataProvider
  public Object[][] failures()
  {
    return new Object[][] {
      // SQL Server picking a transaction as the deadlock victim: the failure this retry exists for
      { "mssql deadlock victim", sql(1205, "40001"), MSSQL, true },
      // a deployment may add xopenStates=true to its connection URL, which reports the same deadlock as 42000
      { "mssql deadlock victim, xopenStates", sql(1205, "42000"), MSSQL, true },
      // the conflict of most other engines is carried by the SQLState, under a vendor number of their own
      { "postgres serialization failure", sql(0, "40001"), POSTGRES, true },
      { "postgres deadlock detected", sql(0, "40P01"), POSTGRES, true },
      // Connector/J replaces the server side HY000 of both conditions with 40001, so neither needs a number here
      { "mysql deadlock", sql(1213, "40001"), MYSQL, true },
      // not a deadlock, but transient in the same way and equally resolved by a replay
      { "mysql lock wait timeout", sql(1205, "40001"), MYSQL, true },
      // the rollback a MySQL group replication conflict reports, error 3101, which the driver maps to 40000
      { "mysql group replication rollback", sql(3101, "40000"), MYSQL, true },
      // Oracle maps ORA-00060 to SQLState 61000, so only its error number identifies the deadlock
      { "oracle deadlock detected", sql(60, "61000"), ORACLE, true },

      // the conflict reaches JDBCStorage.write() wrapped, so the whole cause chain has to be walked
      { "wrapped once", new StorageRuntimeException(sql(1205, "40001")), MSSQL, true },
      { "wrapped twice",
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(1205, "40001"))), MSSQL,
        true },

      // the vendor numbers collide across engines, so they must not be matched driver-independently:
      // ORA-01205 "not a data file" is fatal, and no replay resolves it
      { "oracle not a data file", sql(1205, "64000"), ORACLE, false },
      // and a lock wait timeout is a MySQL number: 1205 means nothing of the kind to PostgreSQL
      { "postgres unrelated 1205", sql(1205, "22001"), POSTGRES, false },

      // two class 40 states are rollbacks that a replay must not repeat: 40003 leaves the outcome of the
      // transaction unknown, and 40002 is an integrity constraint violation that a replay would only hit again
      { "statement completion unknown", sql(0, "40003"), POSTGRES, false },
      { "transaction integrity constraint violation", sql(0, "40002"), POSTGRES, false },
      // ... but the state of a conflict is still matched whatever vendor number carries it
      { "class 40 is driver independent", sql(0, "40001"), null, true },

      // nothing a replay can resolve
      { "primary key violation", sql(2627, "23000"), MSSQL, false },
      { "syntax error", sql(102, "S0001"), MSSQL, false },
      { "no SQLState", sql(0, null), MSSQL, false },
      { "not a SQLException", new IllegalStateException("connection closed"), MSSQL, false },
      { "wrapped, not a conflict", new StorageRuntimeException(sql(2627, "23000")), MSSQL, false },
      { "no failure at all", null, MSSQL, false },
      // a vendor number is never matched without a driver to key it off, since the engines collide on it
      { "unknown driver", sql(1205, "HY000"), null, false },
      { "cyclic cause chain", new SelfCausedException(), MSSQL, false },
    };
  }

  @Test(dataProvider = "failures")
  public void testIsRetryableConflict(String name, Throwable failure, String driver, boolean expected)
  {
    assertEquals(JDBCStorage.isRetryableConflict(failure, driver), expected, name);
  }

  @DataProvider
  public Object[][] connectionFailures()
  {
    return new Object[][] {
      // class 08, connection exception: pgjdbc reports the next use of a connection the server dropped as 08003,
      // and a socket that failed under it as 08006, while a connect that never came up is 08001
      { "connection does not exist", sql(0, "08003"), true },
      { "connection failure", sql(0, "08006"), true },
      { "unable to establish connection", sql(0, "08001"), true },
      // the FATAL message a pg_terminate_backend or a shutdown sends before the socket closes: the connection is
      // gone, and only its next use would be reported as class 08
      { "admin shutdown", sql(0, "57P01"), true },
      { "crash shutdown", sql(0, "57P02"), true },
      { "cannot connect now", sql(0, "57P03"), true },
      // it reaches write() wrapped, exactly as a conflict does
      { "wrapped once", new StorageRuntimeException(sql(0, "08006")), true },
      { "wrapped twice",
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(0, "08003"))), true },
      { "wrapped 57P0x", new StorageRuntimeException(sql(0, "57P01")), true },
      // the types the JDBC contract gives a driver to say the connection is gone, whatever state it fills in:
      // oracle reports ORA-03113 and ORA-01089 as SQLRecoverableException, and only happens to map them to 08006
      { "recoverable", new SQLRecoverableException("closed connection", "72000", 3113), true },
      { "non transient connection", new SQLNonTransientConnectionException("socket closed", "S1000", 0), true },
      // a driver reports what happened as the next exception of a generic failure as readily as it reports it
      // as the cause, and mssql-jdbc chains every error of a message it received that way
      { "next exception", chained(sql(0, "HY000"), sql(0, "08006")), true },
      // the drop of the rollback that releases a connection arrives suppressed into the failure of the operation
      { "suppressed", suppressing(sql(2627, "23000"), sql(0, "08006")), true },

      // a statement the database answered, however badly, leaves the connection usable
      { "deadlock victim", sql(1205, "40001"), false },
      { "primary key violation", sql(2627, "23000"), false },
      // 53300 is the server refusing a further connection, not the loss of one already established
      { "too many connections", sql(0, "53300"), false },
      // a killed session on SQL Server: generateStateCode maps neither 596 nor its siblings, so with xopenStates
      // off - the default - it arrives as "S"+errorState and no state tells it from a rejected statement. What
      // does tell it apart is the connection the driver closed behind it, see the test below
      { "mssql killed session", sql(596, "S0001"), false },
      { "no SQLState", sql(0, null), false },
      { "not a SQLException", new IllegalStateException("connection closed"), false },
      { "no failure at all", null, false },
      { "cyclic cause chain", new SelfCausedException(), false },
    };
  }

  @Test(dataProvider = "connectionFailures")
  public void testIsConnectionFailure(String name, Throwable failure, boolean expected)
  {
    assertEquals(JDBCStorage.isConnectionFailure(failure), expected, name);
  }

  /**
   * A connection the database dropped is replayed on a connection the next attempt borrows of its own - but only
   * while the transaction has not been committed yet. A drop reported by {@code commit()} leaves the outcome of
   * the transaction unknown, and replaying a write that in fact committed applies it twice.
   */
  @Test
  public void testADroppedConnectionIsReplayedOnlyBeforeTheCommit()
  {
    final SQLException dropped = sql(0, "08006");
    assertEquals(JDBCStorage.replayReason(dropped, POSTGRES, false, false, false),
        "a connection the database dropped");
    assertNull(JDBCStorage.replayReason(dropped, POSTGRES, true, false, false),
        "an in doubt transaction was replayed");
  }

  /**
   * A driver that closed the connection has said the connection is gone whatever SQLState it filled in - which is
   * the only way a killed SQL Server session is ever recognized, since it arrives as S0001.
   */
  @Test
  public void testAConnectionTheDriverClosedIsADroppedOne()
  {
    final SQLException killed = sql(596, "S0001");
    assertNull(JDBCStorage.replayReason(killed, MSSQL, false, false, false), "S0001 was replayed on its own");
    assertEquals(JDBCStorage.replayReason(killed, MSSQL, false, false, true),
        "a connection the database dropped");
    assertNull(JDBCStorage.replayReason(killed, MSSQL, true, false, true),
        "an in doubt transaction was replayed");
  }

  /**
   * An attempt that committed part of its own work is not replayed, whatever the failure says: what it did no
   * longer rolls back as a whole, and a WriteOperation is only idempotent in the database. RootContainer.open
   * opens and registers the entry containers of every base DN in one write, and a replay of it fails with
   * ERR_ENTRY_CONTAINER_ALREADY_REGISTERED, masking the failure that caused the replay.
   */
  @Test
  public void testAnAttemptThatCommittedPartOfItsWorkIsNotReplayed()
  {
    assertNull(JDBCStorage.replayReason(sql(0, "40001"), POSTGRES, false, true, false), "a conflict was replayed");
    assertNull(JDBCStorage.replayReason(sql(0, "08006"), POSTGRES, false, true, false), "a drop was replayed");
    assertNull(JDBCStorage.replayReason(sql(596, "S0001"), MSSQL, false, true, true), "a drop was replayed");
  }

  /**
   * A conflict is replayed on the strength of the engine having rolled the transaction back before it answered,
   * so it is read from the failure of the operation and not from the release of the connection: a class 40 the
   * release contributed - Oracle reports a transaction rolled back under it as ORA-02091, SQLState 40000 - would
   * otherwise re-authorise the replay of a commit whose outcome is unknown, past the guard that exists for it.
   * A drop is read from the release as well, which is the one place it is often stated at all.
   */
  @Test
  public void testAConflictIsNotReadFromTheReleaseOfTheConnection()
  {
    final SQLException onRelease = suppressing(sql(2627, "23000"), sql(0, "40000"));
    assertFalse(JDBCStorage.isRetryableConflict(onRelease, POSTGRES), "a conflict was read from the release");
    assertNull(JDBCStorage.replayReason(onRelease, POSTGRES, true, false, false),
        "a transaction the commit left in doubt was replayed");

    // the same shape carrying a drop instead: read, since the release is where a drop is stated at all
    assertTrue(JDBCStorage.isConnectionFailure(suppressing(sql(2627, "23000"), sql(0, "08006"))),
        "a drop was not read from the release");
  }

  /** The connection is asked only where a state does not already say the connection is gone. */
  @Test
  public void testTheConnectionIsAskedWhetherTheDriverClosedIt() throws Exception
  {
    final Connection closed = mock(Connection.class);
    when(closed.isClosed()).thenReturn(true);
    final Connection alive = mock(Connection.class);
    when(alive.isClosed()).thenReturn(false);
    final Connection mute = mock(Connection.class);
    when(mute.isClosed()).thenThrow(new SQLException("the connection cannot say"));

    assertTrue(JDBCStorage.isConnectionFailure(sql(596, "S0001"), closed), "a killed session was not recognized");
    assertFalse(JDBCStorage.isConnectionFailure(sql(2627, "23000"), alive), "a rejected statement was a drop");
    assertTrue(JDBCStorage.isConnectionFailure(sql(0, "08006"), alive), "class 08 needs no connection to say so");
    assertTrue(JDBCStorage.isConnectionFailure(sql(2627, "23000"), mute), "a connection that cannot answer");
  }

  /** A conflict is a rollback the engine completed before it answered, whichever phase reported it. */
  @Test
  public void testAConflictIsReplayedFromEitherPhase()
  {
    final SQLException conflict = sql(0, "40001");
    assertEquals(JDBCStorage.replayReason(conflict, POSTGRES, false, false, false), "a conflict");
    assertEquals(JDBCStorage.replayReason(conflict, POSTGRES, true, false, false), "a conflict");
  }

  /** Everything else fails the operation, as it did before either replay existed. */
  @Test
  public void testAFailureOfTheStatementIsNotReplayed()
  {
    assertNull(JDBCStorage.replayReason(sql(2627, "23000"), MSSQL, false, false, false));
    assertNull(JDBCStorage.replayReason(sql(2627, "23000"), MSSQL, true, false, false));
  }

  /** The delay grows with the attempt, so that the replays outlast a contention lasting more than a few ms. */
  @Test
  public void testRetryDelayGrowsAndStaysBounded()
  {
    long previousBound = 0;
    for (int attempt = 1; attempt <= 10; attempt++)
    {
      long bound = 0;
      for (int i = 0; i < 100; i++)
      {
        final long delay = JDBCStorage.retryDelayMillis(attempt);
        assertTrue(delay >= 0, "attempt " + attempt + " waited " + delay + " ms");
        assertTrue(delay < 1000, "attempt " + attempt + " waited " + delay + " ms");
        bound = Math.max(bound, delay);
      }
      assertTrue(bound >= previousBound / 2, "attempt " + attempt + " did not grow past attempt " + (attempt - 1));
      previousBound = bound;
    }
  }

  /**
   * A replay is logged once per attempt, so what it logs has to identify the conflict without a stack trace: the
   * SQLState and the vendor error number, reached through however many wrappers the failure arrived in.
   */
  @Test
  public void testConflictSummaryNamesTheStateAndTheNumber()
  {
    final String summary = JDBCStorage.conflictSummary(
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(1205, "40001"))), POSTGRES);
    assertTrue(summary.contains("40001"), summary);
    assertTrue(summary.contains("1205"), summary);
    assertTrue(summary.contains("synthetic failure"), summary);
  }

  /**
   * That line is the only record a replay leaves, so it names the failure the replay was decided on. A write whose
   * operation was rejected and whose release then reported a drop is replayed on the class 08 suppressed into the
   * rejection, and naming the state of the rejected statement would describe a replay that did not happen.
   */
  @Test
  public void testConflictSummaryNamesTheFailureTheReplayWasDecidedOn()
  {
    final String summary = JDBCStorage.conflictSummary(
        new StorageRuntimeException(suppressing(sql(2627, "23000"), sql(0, "08006"))), POSTGRES);
    assertTrue(summary.contains("08006"), summary);
    assertFalse(summary.contains("23000"), summary);
  }

  /** A failure carrying no SQLException at all, and a cyclic cause chain, still have to yield something loggable. */
  @Test
  public void testConflictSummaryTerminatesWithoutASQLException()
  {
    assertTrue(JDBCStorage.conflictSummary(new IllegalStateException("connection closed"), POSTGRES).contains("closed"));
    assertTrue(JDBCStorage.conflictSummary(new SelfCausedException(), POSTGRES).contains("SelfCausedException"));
    assertEquals(JDBCStorage.conflictSummary(null, POSTGRES), "null");
  }

  /** A statement that carries neither a conflict nor a drop is still the one the summary names. */
  @Test
  public void testConflictSummaryFallsBackToTheFirstFailureOfTheChain()
  {
    final String summary = JDBCStorage.conflictSummary(new StorageRuntimeException(sql(2627, "23000")), POSTGRES);
    assertTrue(summary.contains("23000"), summary);
  }

  /**
   * Opening a tree that is already there commits nothing, so the attempt stays replayable. The create table is
   * guarded by a catalog read, and so is the create index on every engine but postgresql, so on an existing
   * backend {@code openTree(name, true)} issues no statement at all - while
   * {@code RootContainer.open()} opens every tree of every base DN in a single write whose first act is one of
   * these. A flag raised on the catalog read alone would leave that write unreplayable for the life of the
   * backend, the conflict replay of #867 included: {@code replayReason()} reads the flag before anything else.
   */
  @Test
  public void testOpeningAnExistingTreeLeavesTheAttemptReplayable() throws Exception
  {
    final JDBCStorage storage = storageOverACatalogHolding(true);
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      txn.openTree(TREE, true);
      if (attempts.incrementAndGet() == 1)
      {
        throw new StorageRuntimeException(sql(0, "40001"));
      }
    });

    assertEquals(attempts.get(), 2, "a transaction that committed nothing was not replayed");
    verify(statements, never()).executeUpdate();
  }

  /**
   * A tree that had to be created did commit - the create table commits, and mysql and oracle commit before a DDL
   * statement of their own accord - so the attempt is out of the replay whatever the failure says: a
   * {@link WriteOperation} is only idempotent in the database, and {@code RootContainer.open()} replayed after the
   * trees of the first base DN were created registers that base DN a second time.
   */
  @Test
  public void testCreatingATreeTakesTheAttemptOutOfTheReplay() throws Exception
  {
    final JDBCStorage storage = storageOverACatalogHolding(false);
    final AtomicInteger attempts = new AtomicInteger();

    try
    {
      storage.write(txn -> {
        txn.openTree(TREE, true);
        attempts.incrementAndGet();
        throw new StorageRuntimeException(sql(0, "40001"));
      });
      fail("a transaction that had committed a create table was replayed");
    }
    catch (StorageRuntimeException expected)
    {
      assertTrue(JDBCStorage.isRetryableConflict(expected, POSTGRES), "the conflict was not the failure raised");
    }
    assertEquals(attempts.get(), 1, "a transaction that committed part of its work was replayed");
    verify(statements).executeUpdate();
  }

  /**
   * The rollback that unwinds a failed attempt is often the first place a drop is stated outright, and on a
   * driver that reports a killed session as a plain vendor error it is the only one. It is joined to the failure
   * being unwound rather than dropped on the floor, so that the classifiers below read it: without it the attempt
   * would lean on the driver having flipped its closed flag already, and a driver that has not gives neither the
   * replay nor the distrust.
   */
  @Test
  public void testTheRollbackOfAFailedAttemptIsNotSwallowed() throws Exception
  {
    final long window = CachedConnection.aliveBypassNanos;
    CachedConnection.aliveBypassNanos = TimeUnit.HOURS.toNanos(1);
    try
    {
      final Connection pooled = mock(Connection.class);
      when(pooled.isValid(anyInt())).thenReturn(true);
      final Connection dropped = mock(Connection.class);
      when(dropped.isValid(anyInt())).thenReturn(true);
      when(dropped.isClosed()).thenReturn(false); // the driver has not flipped its flag yet

      final JDBCStorage storage = storageOver(pooled, dropped);
      final Connection first = storage.getConnection();
      final Connection second = storage.getConnection();
      first.close();
      second.close();
      // proven alive by the connect itself, and inside the window ever since: nothing has validated
      verify(dropped, never()).isValid(anyInt());

      // only the rollback that unwinds the attempt says the connection is gone: the release behind it
      // goes through, so the dropped connection is back at the head of the pool - where the replay
      // borrows it again - with nothing else to report what it saw
      doThrow(new SQLException("connection reset", "08006")).doNothing().when(dropped).rollback();

      final AtomicInteger attempts = new AtomicInteger();
      storage.write(txn -> {
        if (attempts.incrementAndGet() == 1)
        {
          throw new StorageRuntimeException(sql(596, "S0001")); // a killed session, as mssql-jdbc reports it
        }
      });

      assertEquals(attempts.get(), 2, "the drop the rollback reported was not replayed");
      // the distrust reached the pool: the borrow of the replay validated instead of trusting the
      // last answer of a connection that predates the drop
      verify(dropped, times(1)).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
    }
    finally
    {
      CachedConnection.aliveBypassNanos = window;
    }
  }

  /**
   * A drop the release of the connection reported reaches the pool as well as the replay. It is suppressed into
   * the failure of the operation (JLS 14.20.3.1) rather than replacing it, so the attempt sees it only on the
   * chains of that failure - and the pool has no other way of hearing of it: the rest of that generation would
   * otherwise be handed out unvalidated one by one until the pool runs out of it.
   */
  @Test
  public void testADropReportedByTheReleaseReachesThePool() throws Exception
  {
    final long window = CachedConnection.aliveBypassNanos;
    CachedConnection.aliveBypassNanos = TimeUnit.HOURS.toNanos(1);
    try
    {
      final Connection pooled = mock(Connection.class);
      when(pooled.isValid(anyInt())).thenReturn(true);
      final Connection released = mock(Connection.class);
      when(released.isValid(anyInt())).thenReturn(true);

      final JDBCStorage storage = storageOver(pooled, released);
      // both are proven alive and back in the pool; the one released last is the one the write borrows
      final Connection first = storage.getConnection();
      final Connection second = storage.getConnection();
      first.close();
      second.close();

      // from here the database has dropped the connection at the head of the pool: the operation is
      // rejected for its own reasons, and the release that follows it is where the drop surfaces
      doThrow(new SQLException("connection reset", "08006")).when(released).rollback();

      final AtomicInteger attempts = new AtomicInteger();
      storage.write(txn -> {
        if (attempts.incrementAndGet() == 1)
        {
          throw new StorageRuntimeException(sql(2627, "23000"));
        }
      });

      assertEquals(attempts.get(), 2, "the drop suppressed into the failure was not replayed");
      verify(pooled).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
    }
    finally
    {
      CachedConnection.aliveBypassNanos = window;
    }
  }

  @BeforeClass
  public void registerStubDriver() throws Exception
  {
    DriverManager.registerDriver(stub);
  }

  @AfterClass
  public void deregisterStubDriver() throws Exception
  {
    DriverManager.deregisterDriver(stub);
  }

  /**
   * A storage whose pool hands out one connection of this test, over a catalog that either holds the table of
   * {@link #TREE} or does not. The connection is a mock of no recognized driver, which is how the engines that
   * guard their create index - and mssql, which has none - reach {@code openTree}.
   */
  private JDBCStorage storageOverACatalogHolding(boolean theTable) throws Exception
  {
    final Connection con = mock(Connection.class);
    final JDBCStorage storage = storageOver(con);

    statements = mock(PreparedStatement.class);
    final String tableName = storage.getTableName(TREE);
    final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    // a result set of its own per call: the catalog is asked once per attempt, and a replayed attempt
    // reading a result set the previous one had already walked to its end would find no table there
    when(metaData.getTables(any(), any(), any(), any())).thenAnswer(invocation -> {
      final ResultSet tables = mock(ResultSet.class);
      when(tables.next()).thenReturn(theTable, false);
      when(tables.getString("TABLE_NAME")).thenReturn(tableName);
      return tables;
    });

    when(con.isValid(anyInt())).thenReturn(true);
    when(con.getMetaData()).thenReturn(metaData);
    when(con.prepareStatement(anyString())).thenReturn(statements);
    return storage;
  }

  /**
   * A storage of a pool of its own, which connects to the given connections in turn and answers every connect
   * beyond them with the last. Every test gets a url of its own: both the pools and the distrust of a pool are
   * keyed by the connection string, so a shared one would carry the state of one test into the next.
   */
  private JDBCStorage storageOver(Connection... connections) throws Exception
  {
    final JDBCBackendCfg cfg = mock(JDBCBackendCfg.class);
    when(cfg.getDBDirectory()).thenReturn(StubDriver.PREFIX + pools.incrementAndGet());
    final JDBCStorage storage = new JDBCStorage(cfg, null);
    storage.accessMode = AccessMode.READ_WRITE;
    stub.answerWith(connections);
    return storage;
  }

  /** A driver of this test, so that the pool the write borrows from needs no database behind it. */
  private static final class StubDriver implements Driver
  {
    static final String PREFIX = "jdbc:opendj-retry-stub:";

    private volatile Connection[] answers = new Connection[0];
    private final AtomicInteger connects = new AtomicInteger();

    void answerWith(Connection... answers)
    {
      this.answers = answers;
      this.connects.set(0);
    }

    @Override
    public Connection connect(String url, Properties info)
    {
      if (!acceptsURL(url) || answers.length == 0)
      {
        return null;
      }
      // the last one answers every connect beyond the ones named, so a pool that opens more than the
      // test set up gets a working connection rather than a null the driver contract reads as "not mine"
      return answers[Math.min(connects.getAndIncrement(), answers.length - 1)];
    }

    @Override
    public boolean acceptsURL(String url)
    {
      return url != null && url.startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
    {
      return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion()
    {
      return 1;
    }

    @Override
    public int getMinorVersion()
    {
      return 0;
    }

    @Override
    public boolean jdbcCompliant()
    {
      return false;
    }

    @Override
    public Logger getParentLogger()
    {
      return Logger.getLogger(StubDriver.class.getName());
    }
  }

  private static SQLException sql(int errorCode, String sqlState)
  {
    return new SQLException("synthetic failure", sqlState, errorCode);
  }

  /** The second failure as the next exception of the first, the way a driver chains the errors of one message. */
  private static SQLException chained(SQLException first, SQLException next)
  {
    first.setNextException(next);
    return first;
  }

  /** The second failure suppressed into the first, the way a failing close() joins the failure of an operation. */
  private static SQLException suppressing(SQLException failure, SQLException onRelease)
  {
    failure.addSuppressed(onRelease);
    return failure;
  }
}
