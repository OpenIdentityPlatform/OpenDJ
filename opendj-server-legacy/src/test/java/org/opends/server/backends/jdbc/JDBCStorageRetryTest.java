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
import org.opends.server.backends.jdbc.JDBCStorage.Conflict;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
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
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.AFTER_LOCK_WAIT;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.NONE;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.PROMPT;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.UNKNOWN_ENGINE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests how a failure is classified - as a transaction conflict of one class or another, or as a connection the
 * database dropped - which is what decides whether {@link JDBCStorage#write} replays the operation, whether its
 * first replay is granted regardless of the clock, how long the replays may go on for and how long it waits
 * before each of them; and what {@code write()} itself does with that verdict, replay and pool alike.
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
  /** A MySQL-wire-compatible driver, whose class name carries no engine this backend recognises. */
  private static final String MARIADB = "org.mariadb.jdbc.Connection";

  /** The tree every write of this test opens; the table name behind it is a hash of this name. */
  private static final TreeName TREE = new TreeName("dc=example,dc=com", "id2entry");

  private final StubDriver stub = new StubDriver();

  /** Every test gets a pool of its own: the pools and the distrust of a pool are keyed by the url. */
  private final AtomicInteger pools = new AtomicInteger();

  /** The statement a create of a test runs, so that a test can assert that it ran - or that it did not. */
  private PreparedStatement statements;

  /** The connection behind the pool of a test, so that a test can assert the statement it was asked to prepare. */
  private Connection engineConnection;

  /**
   * Connections whose class names carry the engine the way the drivers' own do - pgjdbc's
   * {@code org.postgresql.jdbc.PgConnection}, Connector/J's {@code com.mysql.cj.jdbc.ConnectionImpl}. That name
   * is what {@code driverNameOf()} matches an engine on, and the name of a mock is derived from the type it
   * mocks, so a mock of plain {@link Connection} reaches no engine branch of {@code openTree()} at all. Lowercase
   * because the match is case sensitive.
   */
  interface postgresConnection extends Connection
  {
  }

  interface mysqlConnection extends Connection
  {
  }

  /**
   * A MySQL-wire-compatible driver none of the four engines is recognised in - MariaDB Connector/J, an Aurora-
   * or Percona-branded one. It reports a lock wait timeout as 1205 under class 40 exactly as Connector/J does,
   * and a backend created under {@code com.mysql.cj.jdbc} opens through it: every DDL of {@code openTree()} is
   * guarded by a catalog read, so an existing backend issues none of it.
   */
  interface mariadbConnection extends Connection
  {
  }

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

  /**
   * The failures the engines report, and the class each of them belongs to: {@link Conflict#NONE} for a failure no
   * replay resolves, and otherwise how promptly the engine reporting it does so, which is all the class decides.
   */
  @DataProvider
  public Object[][] failures()
  {
    return new Object[][] {
      // SQL Server picking a transaction as the deadlock victim: the failure this retry exists for. It is reported
      // as promptly as the deadlock monitor runs, but only once the victim has waited out a lock wait of its own,
      // which SQL Server leaves unbounded - the wait belongs to the attempt, not to the reporting
      { "mssql deadlock victim", sql(1205, "40001"), MSSQL, PROMPT },
      // a deployment may add xopenStates=true to its connection URL, which reports the same deadlock as 42000
      { "mssql deadlock victim, xopenStates", sql(1205, "42000"), MSSQL, PROMPT },
      // the conflict of most other engines is carried by the SQLState, under a vendor number of their own
      { "postgres serialization failure", sql(0, "40001"), POSTGRES, PROMPT },
      { "postgres deadlock detected", sql(0, "40P01"), POSTGRES, PROMPT },
      // Connector/J replaces the server side HY000 of both conditions with 40001, so neither needs a number here
      { "mysql deadlock", sql(1213, "40001"), MYSQL, PROMPT },
      // not a deadlock, but transient in the same way and equally resolved by a replay - and the one conflict of
      // them all that an engine reports only after a lock wait timeout of its own, innodb_lock_wait_timeout
      { "mysql lock wait timeout", sql(1205, "40001"), MYSQL, AFTER_LOCK_WAIT },
      // the rollback a MySQL group replication conflict reports, error 3101, which the driver maps to 40000
      { "mysql group replication rollback", sql(3101, "40000"), MYSQL, PROMPT },
      // Oracle maps ORA-00060 to SQLState 61000, so only its error number identifies the deadlock
      { "oracle deadlock detected", sql(60, "61000"), ORACLE, PROMPT },

      // the conflict reaches JDBCStorage.write() wrapped, so the whole cause chain has to be walked
      { "wrapped once", new StorageRuntimeException(sql(1205, "40001")), MSSQL, PROMPT },
      { "wrapped twice",
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(1205, "40001"))), MSSQL,
        PROMPT },

      // the vendor numbers collide across engines, so they must not be matched driver-independently:
      // ORA-01205 "not a data file" is fatal, and no replay resolves it
      { "oracle not a data file", sql(1205, "64000"), ORACLE, NONE },
      // and a lock wait timeout is a MySQL number: 1205 means nothing of the kind to PostgreSQL
      { "postgres unrelated 1205", sql(1205, "22001"), POSTGRES, NONE },

      // two class 40 states are rollbacks that a replay must not repeat: 40003 leaves the outcome of the
      // transaction unknown, and 40002 is an integrity constraint violation that a replay would only hit again
      { "statement completion unknown", sql(0, "40003"), POSTGRES, NONE },
      { "transaction integrity constraint violation", sql(0, "40002"), POSTGRES, NONE },
      // ... but the state of a conflict is still matched whatever vendor number carries it, which is what makes
      // an unrecognised engine replayable at all. Which class of conflict it is cannot be told, though: the
      // grant rests on the engine not having bounded the wait already, and of this engine that is not known
      { "class 40 is driver independent", sql(0, "40001"), null, UNKNOWN_ENGINE },
      // the case that costs: a MySQL-wire-compatible driver reports innodb_lock_wait_timeout as 1205 under
      // class 40 exactly as Connector/J does, and reading the number only under a name carrying "mysql" would
      // hand it the grant - a second full 50 s wait, the one thing the window exists to refuse
      { "mysql wire compatible lock wait timeout", sql(1205, "40001"), MARIADB, UNKNOWN_ENGINE },
      { "class 40 with 1205, no driver", sql(1205, "40001"), null, UNKNOWN_ENGINE },

      // nothing a replay can resolve
      { "primary key violation", sql(2627, "23000"), MSSQL, NONE },
      { "syntax error", sql(102, "S0001"), MSSQL, NONE },
      { "no SQLState", sql(0, null), MSSQL, NONE },
      { "not a SQLException", new IllegalStateException("connection closed"), MSSQL, NONE },
      { "wrapped, not a conflict", new StorageRuntimeException(sql(2627, "23000")), MSSQL, NONE },
      { "no failure at all", null, MSSQL, NONE },
      // a vendor number is never matched without a driver to key it off, since the engines collide on it
      { "unknown driver", sql(1205, "HY000"), null, NONE },
      // the state the MySQL server itself gives a lock wait timeout, before Connector/J remaps it to class 40: the
      // number read to date that conflict refines a match its state has already made, and never makes one of its own
      { "mysql lock wait timeout, server state", sql(1205, "HY000"), MYSQL, NONE },
      { "cyclic cause chain", new SelfCausedException(), MSSQL, NONE },

      // the chain is walked to its end, not stopped at its first conflict: a hop carrying a bare class 40 state
      // is a conflict by itself, and returning it would hand the lock wait timeout it wraps - a wait MySQL has
      // already bounded - the replay that only the conflicts nothing bounds are granted
      { "lock wait timeout under a bare class 40 wrapper", sql(0, "40001", sql(1205, "40001")), MYSQL,
        AFTER_LOCK_WAIT },
      { "bare class 40 wrapper over a deadlock", sql(0, "40001", sql(1213, "40001")), MYSQL, PROMPT },
    };
  }

  /**
   * Whether a failure is a conflict at all decides that it is replayed; which class of conflict it is decides
   * only whether its first replay is granted regardless of the clock.
   */
  @Test(dataProvider = "failures")
  public void testConflictClass(String name, Throwable failure, String driver, Conflict expected)
  {
    assertEquals(JDBCStorage.conflictOf(failure, driver), expected, name);
  }

  /**
   * Which replays happen. The window bounds them from the first attempt, with one grant: a conflict its engine
   * reports promptly is given its first replay whatever the clock says, since the wait charged to the attempt
   * that hit it is unbounded and no window survives it. A conflict the engine reported only after a lock wait
   * timeout of its own gets no such grant - that wait is bounded already, and repeating it is what the window
   * refuses.
   */
  @DataProvider
  public Object[][] replays()
  {
    return new Object[][] {
      // the engine asked for the transaction to be rerun after a wait nothing here bounds, and no clock denies
      // that first rerun. The failure of run 33010633197 is the case: SQL Server leaves the lock wait unbounded,
      // so its deadlock monitor picked a victim ~12 s into the first attempt, and master replayed it zero times
      { "deadlock reported after a long lock wait", 1, seconds(12), sql(1205, "40001"), MSSQL, true },
      { "deadlock reported later than any window", 1, seconds(600), sql(1205, "40001"), MSSQL, true },

      // the grant is one replay, not an exemption: from the second attempt on the window governs, so that a
      // conflict which never clears is failed rather than never returned
      { "deadlock within the window", 2, seconds(9), sql(1205, "40001"), MSSQL, true },
      { "deadlock at the window", 2, seconds(10), sql(1205, "40001"), MSSQL, false },
      // the same elapsed time that was granted on attempt 1 is refused on attempt 2: one grant, and only one
      { "deadlock past the window", 2, seconds(12), sql(1205, "40001"), MSSQL, false },
      // a MySQL deadlock is reported as promptly as any other engine reports one, so it is granted the same
      { "mysql deadlock, first attempt", 1, seconds(12), sql(1213, "40001"), MYSQL, true },
      { "mysql deadlock, past the window", 2, seconds(12), sql(1213, "40001"), MYSQL, false },

      // MySQL reports a lock wait timeout only after innodb_lock_wait_timeout, 50 s by default: that wait is
      // bounded by the engine, so the window is measured against it from the first attempt and a second 50 s wait
      // is refused - which is the whole reason the window was introduced
      { "mysql lock wait timeout at the default 50 s", 1, seconds(50), sql(1205, "40001"), MYSQL, false },
      { "mysql lock wait timeout past the window", 1, seconds(12), sql(1205, "40001"), MYSQL, false },
      // ... and a deployment that tuned innodb_lock_wait_timeout below the window still gets its replays
      { "mysql lock wait timeout tuned under the window", 1, seconds(3), sql(1205, "40001"), MYSQL, true },
      { "mysql lock wait timeout, second attempt within", 2, seconds(6), sql(1205, "40001"), MYSQL, true },
      { "mysql lock wait timeout, second attempt at the window", 2, seconds(10), sql(1205, "40001"), MYSQL, false },
      // the same 1205 under a MySQL-wire-compatible driver, which is what a backend created under Connector/J
      // and opened through MariaDB Connector/J reports: the window governs it from the first attempt too, since
      // a grant here would buy the second innodb_lock_wait_timeout the rows above refuse
      { "mysql wire compatible lock wait timeout", 1, seconds(12), sql(1205, "40001"), MARIADB, false },
      { "mysql wire compatible conflict within the window", 1, seconds(3), sql(1205, "40001"), MARIADB, true },

      // the attempt count bounds every class, whatever the window has left. It is the bound that rarely fires:
      // reaching it takes ten attempts inside a 10 s window, which only a conflict reported in milliseconds
      // leaves room for - a conflict preceded by a wait longer than the window stops at two attempts, the
      // granted one included, and the line reporting the replay names the window for that reason
      { "last attempt left", 9, 0L, sql(1205, "40001"), MSSQL, true },
      { "attempts exhausted", 10, 0L, sql(1205, "40001"), MSSQL, false },
      // a failure carrying no conflict class at all still passes these bounds: what makes it replayable is
      // replayReason(), which write() asks first, and a dropped connection carries no class 40 state
      { "a drop, which no class describes", 1, 0L, sql(2627, "23000"), MSSQL, true },
    };
  }

  /**
   * Composed the way {@code write()} composes it: the class is read off the failure once, and the bounds are
   * asked of the class. Whether the failure is worth replaying at all is {@code replayReason()}, tested apart.
   */
  @Test(dataProvider = "replays")
  public void testReplayable(String name, int attempt, long elapsedNanos, Throwable failure, String driver,
      boolean expected)
  {
    assertEquals(JDBCStorage.replayableWithin(attempt, elapsedNanos, JDBCStorage.conflictOf(failure, driver)),
        expected, name);
  }

  /**
   * The grant is reported rather than inferred: the line reporting a replay says "granted past it" only where
   * the loop really took that branch. Pinned apart from {@link #testReplayable} because the two agree today by
   * construction - a replay past the window can only be the grant - and it is that coincidence, not the claim,
   * that a later change to the bounds would take away.
   */
  @Test
  public void testTheGrantIsTheOnlyReplayPastTheWindow()
  {
    // the grant, and the only shape of it: the first replay of a conflict reported past the window
    assertTrue(JDBCStorage.grantedPastTheWindow(1, seconds(12), PROMPT), "the conflict of #903 was not granted");
    // inside the window nothing is granted - the window itself allows the replay, and the line says nothing
    assertFalse(JDBCStorage.grantedPastTheWindow(1, seconds(9), PROMPT), "a replay inside the window was granted");
    // and past the first attempt, or for a wait the engine already bounded, there is no grant at all
    assertFalse(JDBCStorage.grantedPastTheWindow(2, seconds(12), PROMPT), "a second replay was granted");
    assertFalse(JDBCStorage.grantedPastTheWindow(1, seconds(12), AFTER_LOCK_WAIT), "a bounded wait was granted");
    assertFalse(JDBCStorage.grantedPastTheWindow(1, seconds(12), UNKNOWN_ENGINE),
        "an engine whose wait cannot be vouched for was granted");
    assertFalse(JDBCStorage.grantedPastTheWindow(1, seconds(12), NONE), "a failure carrying no conflict");

    // every replay the bounds allow past the window is that grant, which is what lets the line name it
    for (int attempt = 1; attempt < 12; attempt++)
    {
      for (Conflict conflict : Conflict.values())
      {
        final boolean pastTheWindow = JDBCStorage.replayableWithin(attempt, seconds(11), conflict);
        assertEquals(pastTheWindow, JDBCStorage.grantedPastTheWindow(attempt, seconds(11), conflict),
            "attempt " + attempt + " of a " + conflict + " conflict past the window");
      }
    }
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
    assertEquals(replayReason(dropped, POSTGRES, false, false, false),
        "a connection the database dropped");
    assertNull(replayReason(dropped, POSTGRES, true, false, false),
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
    assertNull(replayReason(killed, MSSQL, false, false, false), "S0001 was replayed on its own");
    assertEquals(replayReason(killed, MSSQL, false, false, true),
        "a connection the database dropped");
    assertNull(replayReason(killed, MSSQL, true, false, true),
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
    assertNull(replayReason(sql(0, "40001"), POSTGRES, false, true, false), "a conflict was replayed");
    assertNull(replayReason(sql(0, "08006"), POSTGRES, false, true, false), "a drop was replayed");
    assertNull(replayReason(sql(596, "S0001"), MSSQL, false, true, true), "a drop was replayed");
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
    assertEquals(JDBCStorage.conflictOf(onRelease, POSTGRES), NONE, "a conflict was read from the release");
    assertNull(replayReason(onRelease, POSTGRES, true, false, false),
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
    assertEquals(replayReason(conflict, POSTGRES, false, false, false), "a conflict");
    assertEquals(replayReason(conflict, POSTGRES, true, false, false), "a conflict");
  }

  /** Everything else fails the operation, as it did before either replay existed. */
  @Test
  public void testAFailureOfTheStatementIsNotReplayed()
  {
    assertNull(replayReason(sql(2627, "23000"), MSSQL, false, false, false));
    assertNull(replayReason(sql(2627, "23000"), MSSQL, true, false, false));
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

  /**
   * The line names the link the class was decided on, which is not always the first conflict of the chain:
   * {@code conflictOf()} keeps the most specific class it finds, so a wrapper carrying a bare class 40 state is
   * walked past to the lock wait timeout underneath it. Naming the wrapper would print "error 0" for a replay
   * whose whole bound was chosen by the 1205 it never shows.
   */
  @Test
  public void testConflictSummaryNamesTheLinkTheClassWasDecidedOn()
  {
    final SQLException lateUnderAWrapper = sql(0, "40001", sql(1205, "40001"));
    assertEquals(JDBCStorage.conflictOf(lateUnderAWrapper, MYSQL), AFTER_LOCK_WAIT);
    final String summary = JDBCStorage.conflictSummary(lateUnderAWrapper, MYSQL);
    assertTrue(summary.contains("1205"), summary);

    // the same chain under a driver that gives 1205 no such meaning is a prompt conflict, and the first link
    // of it is the one the decision was taken on
    assertEquals(JDBCStorage.conflictOf(sql(0, "40001", sql(1205, "40001")), POSTGRES), PROMPT);
    final String firstLink = JDBCStorage.conflictSummary(sql(0, "40001", sql(1205, "40001")), POSTGRES);
    assertTrue(firstLink.contains("error 0"), firstLink);
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

    // the fallback names the statement, not the rollback of the release behind it: this is where a replay
    // decided on the closed flag of the connection alone lands - neither chain carries a verdict of its own -
    // and the walk reaches the suppressed exceptions of a failure before its cause
    final StorageRuntimeException killedSession = new StorageRuntimeException(sql(596, "S0001"));
    killedSession.addSuppressed(sql(0, "25P02"));
    final String decidedOnTheConnection = JDBCStorage.conflictSummary(killedSession, MSSQL);
    assertTrue(decidedOnTheConnection.contains("S0001"), decidedOnTheConnection);
    assertFalse(decidedOnTheConnection.contains("25P02"), decidedOnTheConnection);
  }

  /**
   * The walk of a failure looks at a bounded number of links: mssql-jdbc chains every error of one message it
   * received through {@code setNextException}, and a budget spent on those would never reach the cause a wrapper
   * carries. Pinned from both sides - a drop on the last link of the budget is found and one link further is not
   * - since a number nothing pins drifts unnoticed in either direction.
   */
  @Test
  public void testTheWalkOfAFailureStopsAtItsBudget()
  {
    assertTrue(JDBCStorage.isConnectionFailure(chainEndingInADrop(64)), "a drop on the last link of the budget");
    assertFalse(JDBCStorage.isConnectionFailure(chainEndingInADrop(65)), "a drop past the budget was walked to");
  }

  /**
   * The class of a conflict is the one walk that budget must not bound, and it is the reason
   * {@code failureScope()} does not bound its own either: truncation does not leave this verdict unanswered, it
   * weakens it. A lock wait timeout past the budget, with a bare class 40 link inside it, would come back
   * {@link Conflict#PROMPT} and be handed the one replay the class exists to refuse - a second full
   * {@code innodb_lock_wait_timeout}. Truncation here grants a replay rather than losing one.
   */
  @Test
  public void testTheConflictClassIsReadFromEveryLinkOfTheChain()
  {
    // a bare class 40 wrapper, then 64 links of a rejected statement, then the timeout that decided the class
    final SQLException bareClass40 = sql(0, "40001");
    SQLException tail = bareClass40;
    for (int link = 0; link < 64; link++)
    {
      tail = chained(tail, sql(2627, "23000")).getNextException();
    }
    chained(tail, sql(1205, "40001"));

    assertEquals(JDBCStorage.conflictOf(bareClass40, MYSQL), AFTER_LOCK_WAIT,
        "a lock wait timeout past MAX_CHAIN_LINKS came back as a conflict the window does not bound");
    assertFalse(JDBCStorage.replayableWithin(1, seconds(12), JDBCStorage.conflictOf(bareClass40, MYSQL)),
        "and was granted the replay past the window");
    // the line reporting a replay names that same link, since one walk produced both
    assertTrue(JDBCStorage.conflictSummary(bareClass40, MYSQL).contains("1205"));
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
      assertEquals(JDBCStorage.conflictOf(expected, POSTGRES), PROMPT, "the conflict was not the failure raised");
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
      // rejected for its own reasons, the rollback that unwinds the attempt goes through, and the release
      // behind it is where the drop surfaces. Chained, so that the drop lands on the second rollback: an
      // unchained stub fails the first one - the rollback of the attempt - and pins the sibling test above
      doNothing().doThrow(new SQLException("connection reset", "08006")).when(released).rollback();

      final AtomicInteger attempts = new AtomicInteger();
      storage.write(txn -> {
        if (attempts.incrementAndGet() == 1)
        {
          throw new StorageRuntimeException(sql(2627, "23000"));
        }
      });

      assertEquals(attempts.get(), 2, "the drop suppressed into the failure was not replayed");
      // the return to the pool that seeded it, the rollback of the attempt, and the release behind it - which
      // is the one that reported, since the stub above lets the rollback of the attempt through
      verify(released, times(3)).rollback();
      // the distrust reached the pool from the release: the borrow of the replay validated instead of
      // trusting the last answer of a connection established before the drop
      verify(pooled).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
    }
    finally
    {
      CachedConnection.aliveBypassNanos = window;
    }
  }

  /**
   * A read is never replayed - two of the read operations of this server are not idempotent - but a drop it ran
   * into still has to reach the pool, which has no other way of hearing of one: a borrow inside the window asks
   * the database nothing, so the statement that broke is the only place the drop is ever seen. The release of
   * the connection counts as such a statement: its rollback is the one round trip a read that found nothing
   * makes.
   */
  @Test
  public void testADropAReadRanIntoReachesThePool() throws Exception
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
      final Connection first = storage.getConnection();
      final Connection second = storage.getConnection();
      first.close();
      second.close();
      // both are proven alive and inside the window: nothing has validated
      verify(released, never()).isValid(anyInt());

      // the read is rejected for its own reasons, and the release behind it - a read issues no rollback of its
      // own - is where the connection the database dropped says so
      doThrow(new SQLException("connection reset", "08006")).when(released).rollback();
      try
      {
        storage.read(txn -> {
          throw new StorageRuntimeException(sql(2627, "23000"));
        });
        fail("the failure of the read was swallowed");
      }
      catch (StorageRuntimeException expected)
      {
        assertTrue(JDBCStorage.isConnectionFailure(expected), "the drop of the release did not reach the failure");
      }

      storage.getConnection().close(); // the borrow that follows it validates instead of trusting

      verify(pooled).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
    }
    finally
    {
      CachedConnection.aliveBypassNanos = window;
    }
  }

  /**
   * The create index of the postgres branch is asked of the catalog first, although postgresql has "create index
   * if not exists": that statement commits whether it creates anything or not, and unguarded it would take every
   * write that opens a tree out of the replay - {@code RootContainer.open()} and its ~25 trees per suffix
   * included.
   */
  @Test
  public void testThePostgresIndexIsAskedOfTheCatalogBeforeItIsCreated() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true);
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
   * postgresql runs DDL inside the transaction, so a create index the engine rolled back has committed nothing:
   * {@code write()} rolls the attempt back whole and replays it. Raising the flag in front of the statement -
   * which is what mysql and oracle need, since they commit before a DDL of their own accord - would turn a
   * deadlock the engine itself undid into a hard failure of the open.
   */
  @Test
  public void testACreateIndexPostgresRolledBackLeavesTheAttemptReplayable() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, false);
    when(statements.executeUpdate()).thenThrow(sql(0, "40P01")).thenReturn(0);
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      attempts.incrementAndGet();
      txn.openTree(TREE, true);
    });

    assertEquals(attempts.get(), 2, "a create index the engine rolled back was not replayed");
    verify(engineConnection, times(2)).prepareStatement(startsWith("create index if not exists k_"));
  }

  /**
   * mysql commits before a DDL statement whether asked to or not, so a create index that failed there has
   * committed everything the transaction did before it just as surely as one that succeeded: the attempt is out
   * of the replay whatever the failure says.
   */
  @Test
  public void testACreateIndexMysqlCommittedBeforeTakesTheAttemptOutOfTheReplay() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, false);
    when(statements.executeUpdate()).thenThrow(sql(1213, "40001"));
    final AtomicInteger attempts = new AtomicInteger();

    try
    {
      storage.write(txn -> {
        attempts.incrementAndGet();
        txn.openTree(TREE, true);
      });
      fail("a transaction whose create index had committed before it was replayed");
    }
    catch (StorageRuntimeException expected)
    {
      assertEquals(JDBCStorage.conflictOf(expected, MYSQL), PROMPT, "the conflict was not the failure raised");
    }
    assertEquals(attempts.get(), 1, "an attempt that committed part of its work was replayed");
    verify(engineConnection).prepareStatement(startsWith("create index k_"));
  }

  /**
   * The connection the tree names are stamped on is closed as the attempt is unwound, and an unchecked throw out
   * of a driver's {@code close()} there would replace the exception being unwound (JLS 14.20.2) - the very one
   * the replay is decided on, and the only one that says what went wrong. The stamp is a diagnostic aid: it is
   * joined to the failure instead, and the replay goes ahead.
   */
  @Test
  public void testAFailingCommentConnectionDoesNotReplaceTheFailureOfTheWrite() throws Exception
  {
    final Connection stamp = mock(Connection.class);
    when(stamp.createStatement()).thenReturn(mock(Statement.class)); // the lock bound of the stamp session
    // the readback of the stored comment fails, so the stamp is given up on - with its connection open
    when(stamp.prepareStatement(anyString())).thenThrow(new SQLException("no readback in this test", "42000"));
    doThrow(new IllegalStateException("the driver threw out of close()")).when(stamp).close();
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true, stamp);
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      txn.openTree(TREE, true); // opens the stamp session, which is closed as this attempt is unwound
      if (attempts.incrementAndGet() == 1)
      {
        throw new StorageRuntimeException(sql(0, "40001"));
      }
    });

    assertEquals(attempts.get(), 2, "the failure of the comment connection replaced the conflict being unwound");
    verify(stamp).close();
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
   * A storage whose pool hands out one connection of the given engine, over a catalog holding the table of
   * {@link #TREE} and either holding its {@code k_} index or not. The index guard and the statement behind it
   * are the branches {@code openTree()} takes per engine, and a mock of plain {@link Connection} reaches none
   * of them - so the name the mock ends up with is asserted here rather than assumed.
   */
  private JDBCStorage storageOverAnEngine(Class<? extends Connection> engine, boolean theIndex,
      Connection... behind) throws Exception
  {
    final Connection con = mock(engine);
    final String engineName = engine.getSimpleName().replace("Connection", "");
    assertTrue(JDBCStorage.driverNameOf(con).contains(engineName),
        "a mock of " + engine.getSimpleName() + " reaches no " + engineName + " branch: "
            + JDBCStorage.driverNameOf(con));
    engineConnection = con;
    // the connections behind it answer the connects the pool does not make: the stamp of a tree name opens one
    // of its own, straight through the driver, since the caller of openTree() is holding a pooled connection
    final Connection[] answers = new Connection[behind.length + 1];
    answers[0] = con;
    System.arraycopy(behind, 0, answers, 1, behind.length);
    final JDBCStorage storage = storageOver(answers);

    statements = mock(PreparedStatement.class);
    final String tableName = storage.getTableName(TREE);
    final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    // a result set of its own per call, for the reason the catalog of the test above hands out one: a replayed
    // attempt reading a result set the previous one had already walked to its end would find nothing there
    when(metaData.getTables(any(), any(), any(), any())).thenAnswer(invocation -> {
      final ResultSet tables = mock(ResultSet.class);
      when(tables.next()).thenReturn(true, false);
      when(tables.getString("TABLE_NAME")).thenReturn(tableName);
      return tables;
    });
    when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
      final ResultSet indexes = mock(ResultSet.class);
      when(indexes.next()).thenReturn(theIndex, false);
      when(indexes.getString("INDEX_NAME")).thenReturn("k_" + tableName.substring("opendj_".length()));
      return indexes;
    });

    when(con.isValid(anyInt())).thenReturn(true);
    when(con.getMetaData()).thenReturn(metaData);
    when(con.prepareStatement(anyString())).thenReturn(statements);
    // the tree name the sweep stamps the table with runs on a connection of its own and is a diagnostic aid: a
    // failure of it only logs, and this fixture is about the index statement rather than about the comment
    when(con.createStatement()).thenThrow(new SQLException("no session statement in this test", "42000"));
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

  /**
   * The two questions {@code write()} asks after a failed attempt, composed here the way it composes them: the
   * conflict class is read off the failure once and handed to the reason, rather than being asked for again.
   */
  private static String replayReason(Throwable failure, String driver, boolean committing, boolean partlyCommitted,
      boolean connectionClosed)
  {
    return JDBCStorage.replayReason(JDBCStorage.conflictOf(failure, driver), failure, committing, partlyCommitted,
        connectionClosed);
  }

  private static SQLException sql(int errorCode, String sqlState)
  {
    return new SQLException("synthetic failure", sqlState, errorCode);
  }

  private static SQLException sql(int errorCode, String sqlState, Throwable cause)
  {
    return new SQLException("synthetic failure", sqlState, errorCode, cause);
  }

  private static long seconds(long seconds)
  {
    return TimeUnit.SECONDS.toNanos(seconds);
  }

  /**
   * How {@link JDBCStorage#write} drives the two decisions above, which the cases before this one cannot see:
   * they are handed an elapsed time and an attempt number rather than producing them. The clock is scripted and
   * advances a fixed step per attempt - not per read of it - so that the timeline the loop sees depends on what
   * it does rather than on how often it asks the time: a read added anywhere in {@code write()} leaves every row
   * of this provider answering exactly as it does now.
   * <p>
   * Between them the rows pin the three lines the rest of the file would let a refactor take away. A single
   * {@code startedAt} outside the retry loop is what makes the window bound the whole run rather than each
   * attempt: moved inside, every attempt is measured against its own start, sees the step and nothing more, and
   * replays to MAX_RETRIES. The grant of the first replay is what issue #903 is about: without it an attempt
   * that alone outlasts the window leaves the loop with no replay at all. And the class the grant is asked of is
   * read off the failure of this very run, rather than off a driver the loop does not carry: the last two rows
   * fail as plainly as the first two and are replayed no times at all.
   */
  @DataProvider
  public Object[][] writeRuns()
  {
    return new Object[][] {
      // a step under the window, so the window is what ends the run: attempt 1 is granted its replay at 4 s,
      // attempt 2 is inside the window at 8 s, attempt 3 is past it at 12 s. With startedAt inside the loop every
      // attempt measures 4 s, never reaches the window, and the run goes to MAX_RETRIES instead
      { "the window bounds the run, not the attempt", postgresConnection.class, sql(0, "40P01"), 4L, 3 },
      // a step past the window, so only the grant can produce a second attempt: remove it and the run ends on
      // the first. This is the row that pins the grant end to end, and the row above is the one that pins
      // startedAt - at 12 s a per-attempt startedAt also stops at two attempts, and at 4 s the window alone
      // already allows the replay of attempt 1. Neither row is redundant
      { "the first replay is granted past the window", postgresConnection.class, sql(0, "40P01"), 12L, 2 },
      // the same step, and the same class 40 state, for the one conflict the engine had already bounded: a
      // single attempt. The predicate rows pin that decision, but only these rows pin that write() hands the
      // predicate the class of its own failure - dropped on the way to replayableWithin(), or read off a null
      // driver, and the run above stays green while this one buys a second innodb_lock_wait_timeout
      { "a lock wait timeout is granted no replay", mysqlConnection.class, sql(1205, "40001"), 12L, 1 },
      // and the driver that reports that same timeout under a name this backend does not recognise: no grant
      // there either, since what the grant rests on - the engine having bounded nothing - is unknown of it
      { "an unrecognised engine is granted no replay", mariadbConnection.class, sql(1205, "40001"), 12L, 1 },
    };
  }

  @Test(dataProvider = "writeRuns")
  public void testWriteDrivesTheRetryLoop(String name, Class<? extends Connection> engine,
      final SQLException conflict, final long stepSeconds, int expectedAttempts) throws Exception
  {
    final AtomicInteger attempts = new AtomicInteger();
    // the class name of the mock is what write() reads the engine off, the way storageOverAnEngine() does it
    final Connection connection = mock(engine);

    // a url of its own, as storageOver() gives every fixture of this file: getConnection() is overridden below,
    // but distrustPool() is not, and a null one would reach ConcurrentHashMap.merge(null, ...) rather than the
    // assertion under test the moment a row of this provider scripts a connection failure
    final JDBCBackendCfg cfg = mock(JDBCBackendCfg.class);
    when(cfg.getDBDirectory()).thenReturn(StubDriver.PREFIX + pools.incrementAndGet());

    final JDBCStorage storage = new JDBCStorage(cfg, null)
    {
      @Override
      Connection getConnection()
      {
        return connection;
      }

      @Override
      long nanoTime()
      {
        // the attempts made are what moves this clock, so the run is the same however often write() reads it
        return seconds(stepSeconds * attempts.get());
      }
    };
    storage.accessMode = AccessMode.READ_WRITE;

    StorageRuntimeException thrown = null;
    try
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn)
        {
          attempts.incrementAndGet();
          throw new StorageRuntimeException(conflict);
        }
      });
    }
    catch (StorageRuntimeException e)
    {
      thrown = e;
    }

    assertSame(thrown != null ? thrown.getCause() : null, conflict, name + ": the conflict reaches the caller");
    assertEquals(attempts.get(), expectedAttempts, name + ": attempts made");
  }

  /** The second failure as the next exception of the first, the way a driver chains the errors of one message. */
  private static SQLException chained(SQLException first, SQLException next)
  {
    first.setNextException(next);
    return first;
  }

  /** A chain of the given number of next exceptions whose last link is a connection that broke. */
  private static SQLException chainEndingInADrop(int links)
  {
    final SQLException head = sql(2627, "23000");
    SQLException tail = head;
    for (int link = 2; link <= links; link++)
    {
      tail = chained(tail, sql(0, link == links ? "08006" : "23000")).getNextException();
    }
    return head;
  }

  /** The second failure suppressed into the first, the way a failing close() joins the failure of an operation. */
  private static SQLException suppressing(SQLException failure, SQLException onRelease)
  {
    failure.addSuppressed(onRelease);
    return failure;
  }
}
