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

import org.forgerock.opendj.ldap.ByteString;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static java.util.Collections.emptyList;
import static java.util.Collections.frequency;
import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
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

  /**
   * What the cases below arm around a failure they classify: nothing. They are about the other classes of
   * failure - a conflict, a connection the database dropped - and a row lock bound this backend did not put on
   * is the state every one of them was written under (#915).
   */
  private static final JDBCStorage.ArmedLockBound NO_ROW_LOCK_BOUND =
      JDBCStorage.ArmedLockBound.none(JDBCStorage.LockBound.ROW);

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
   * The connection the tree catalog of a storage of this test is written on, so that a test can assert what was
   * written there. Nothing else can: it is opened straight through the driver rather than borrowed from the pool,
   * and the rows it carries are the ones {@code statements} is asserted never to have carried.
   */
  private Connection catalogConnection;

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

  /** sql server, whose one {@code LOCK_TIMEOUT} is read back and put back around every write. */
  interface microsoftConnection extends Connection
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
    assertEquals(conflictOf(failure, driver), expected, name);
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
      // and exactly at the window, which is the boundary the grant is decided on: elapsed >= window, not > it.
      // The rows around this one bracket that point without standing on it, and a > there would refuse the first
      // replay of #903 to every conflict reported at the window to the nanosecond
      { "deadlock at the window, first attempt", 1, seconds(10), sql(1205, "40001"), MSSQL, true },

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
    assertEquals(JDBCStorage.replayableWithin(attempt, elapsedNanos, conflictOf(failure, driver)),
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
   * opens and registers the entry containers of every base DN in one write, and until #993 (PR #999) a replay
   * of it failed with ERR_ENTRY_CONTAINER_ALREADY_REGISTERED - OpenDJ issue #896 - masking the failure that
   * caused the replay.
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
    assertEquals(conflictOf(onRelease, POSTGRES), NONE, "a conflict was read from the release");
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
    final String summary = conflictSummary(
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
    final String summary = conflictSummary(
        new StorageRuntimeException(suppressing(sql(2627, "23000"), sql(0, "08006"))), POSTGRES);
    assertTrue(summary.contains("08006"), summary);
    assertFalse(summary.contains("23000"), summary);
  }

  /**
   * The line names the link the class was decided on, which is not always the first conflict of the chain:
   * {@code conflictVerdict()} keeps the most specific class it finds, so a wrapper carrying a bare class 40 state
   * is walked past to the lock wait timeout underneath it. Naming the wrapper would print "error 0" for a replay
   * whose whole bound was chosen by the 1205 it never shows. Asked of one verdict per chain, the way
   * {@code write()} asks it: the class and the link are one answer, and two walks could disagree about them.
   */
  @Test
  public void testConflictSummaryNamesTheLinkTheClassWasDecidedOn()
  {
    final SQLException lateUnderAWrapper = sql(0, "40001", sql(1205, "40001"));
    final JDBCStorage.ConflictVerdict late = JDBCStorage.conflictVerdict(lateUnderAWrapper, MYSQL);
    assertEquals(late.conflict, AFTER_LOCK_WAIT);
    final String summary = JDBCStorage.conflictSummary(late, lateUnderAWrapper);
    assertTrue(summary.contains("1205"), summary);

    // the same chain under a driver that gives 1205 no such meaning is a prompt conflict, and the first link
    // of it is the one the decision was taken on
    final SQLException sameChain = sql(0, "40001", sql(1205, "40001"));
    final JDBCStorage.ConflictVerdict prompt = JDBCStorage.conflictVerdict(sameChain, POSTGRES);
    assertEquals(prompt.conflict, PROMPT);
    final String firstLink = JDBCStorage.conflictSummary(prompt, sameChain);
    assertTrue(firstLink.contains("error 0"), firstLink);
  }

  /** A failure carrying no SQLException at all, and a cyclic cause chain, still have to yield something loggable. */
  @Test
  public void testConflictSummaryTerminatesWithoutASQLException()
  {
    assertTrue(conflictSummary(new IllegalStateException("connection closed"), POSTGRES).contains("closed"));
    assertTrue(conflictSummary(new SelfCausedException(), POSTGRES).contains("SelfCausedException"));
    assertEquals(conflictSummary(null, POSTGRES), "null");
  }

  /** A statement that carries neither a conflict nor a drop is still the one the summary names. */
  @Test
  public void testConflictSummaryFallsBackToTheFirstFailureOfTheChain()
  {
    final String summary = conflictSummary(new StorageRuntimeException(sql(2627, "23000")), POSTGRES);
    assertTrue(summary.contains("23000"), summary);

    // the fallback names the statement, not the rollback of the release behind it: this is where a replay
    // decided on the closed flag of the connection alone lands - neither chain carries a verdict of its own -
    // and the walk reaches the suppressed exceptions of a failure before its cause
    final StorageRuntimeException killedSession = new StorageRuntimeException(sql(596, "S0001"));
    killedSession.addSuppressed(sql(0, "25P02"));
    final String decidedOnTheConnection = conflictSummary(killedSession, MSSQL);
    assertTrue(decidedOnTheConnection.contains("S0001"), decidedOnTheConnection);
    assertFalse(decidedOnTheConnection.contains("25P02"), decidedOnTheConnection);
  }

  /**
   * The walk of a failure reads every link of it where a decision reads the verdict, however long the chains
   * are - mssql-jdbc chains every error of one message it received through {@code setNextException}. A budget
   * does not leave the question unanswered here: it answers it with the {@code false} of a failure that says
   * nothing about the connection, so the pool is never told of the drop and the attempt is failed to its caller
   * instead of being replayed (issue #961). What terminates the walk is its {@code seen} set, which the cyclic
   * chains of the providers above pin.
   */
  @Test
  public void testTheWalkOfAFailureReachesADropPastAnyBudget()
  {
    assertTrue(JDBCStorage.isConnectionFailure(chainEndingIn(65, "08006")), "a drop one link past the budget");
    assertTrue(JDBCStorage.isConnectionFailure(chainEndingIn(200, "08006")), "a drop far past the budget");
    assertEquals(replayReason(chainEndingIn(200, "08006"), MSSQL, false, false, false),
        "a connection the database dropped", "an attempt worth replaying was failed to its caller");
  }

  /**
   * The line reporting a replay names the link the replay was decided on however deep it sits: a lookup that
   * stopped where the verdict does not would name the statement at the head of the chain instead, and describe
   * a replay that did not happen.
   */
  @Test
  public void testTheSummaryNamesADecidingLinkPastAnyBudget()
  {
    final String drop = conflictSummary(chainEndingIn(200, "08006"), MSSQL);
    assertTrue(drop.contains("08006"), drop);
    final String conflict = conflictSummary(chainEndingIn(200, "40001"), MSSQL);
    assertTrue(conflict.contains("40001"), conflict);
  }

  /**
   * What the budget is left for: the fallback of the summary that names a link no decision reads, where
   * truncation costs the precision of one line of the log. Pinned from both sides - the last link of the budget
   * is named and one link further is not - since a number nothing pins drifts unnoticed in either direction.
   */
  @Test
  public void testTheFallbackOfTheSummaryStopsAtItsBudget()
  {
    assertTrue(conflictSummary(wrappedTimes(63, sql(2627, "23000")), POSTGRES).contains("23000"),
        "the statement on the last link of the budget was not named");
    assertFalse(conflictSummary(wrappedTimes(64, sql(2627, "23000")), POSTGRES).contains("23000"),
        "a link past the budget was walked to");
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

    final JDBCStorage.ConflictVerdict verdict = JDBCStorage.conflictVerdict(bareClass40, MYSQL);
    assertEquals(verdict.conflict, AFTER_LOCK_WAIT,
        "a lock wait timeout past MAX_CHAIN_LINKS came back as a conflict the window does not bound");
    assertFalse(JDBCStorage.replayableWithin(1, seconds(12), verdict.conflict),
        "and was granted the replay past the window");
    // the line reporting a replay names that same link, since one walk produced both
    assertTrue(JDBCStorage.conflictSummary(verdict, bareClass40).contains("1205"));
  }

  /**
   * The widening that reading every link brings, which is the one behaviour change of #903 outside the grant: a
   * chain whose only conflict-bearing link sits past the budget was not a conflict at all on master - the
   * truncating walk never reached it - so {@code replayReason()} answered null and the operation was failed.
   * The case above pins the *class* of such a chain, since it puts a bare class 40 state at the head that the
   * truncating walk already matched; this one pins that the conflict is found at all.
   */
  @Test
  public void testAConflictOnlyPastTheBudgetIsFoundAtAll()
  {
    // 64 links of a rejected statement - the whole budget - and the conflict on the 65th
    final SQLException head = sql(2627, "23000");
    SQLException tail = head;
    for (int link = 2; link <= 64; link++)
    {
      tail = chained(tail, sql(2627, "23000")).getNextException();
    }
    chained(tail, sql(0, "40001"));

    assertEquals(conflictOf(head, POSTGRES), PROMPT,
        "a conflict whose only link sits past MAX_CHAIN_LINKS was not found at all");
    assertEquals(replayReason(head, POSTGRES, false, false, false), "a conflict",
        "and the operation carrying it was not replayed");
  }

  /**
   * What that walk stops at is the strongest class the engine of its driver can report, not the last constant of
   * {@link Conflict}: only MySQL reports a lock wait timeout of its own, so a walk stopping at
   * {@link Conflict#AFTER_LOCK_WAIT} never stops early on the other three engines and reads every link of every
   * failed write on the driver whose chains are longest. Pinned from both sides - no failure of an engine is
   * classed above its own ceiling, and every ceiling is reached by some failure - since a ceiling set too low
   * would end the walk on a class weaker than the chain carries, and one set too high never ends it early.
   */
  @Test
  public void testTheWalkStopsAtTheStrongestClassItsEngineCanReport()
  {
    // one shape per branch of the classification: a bare class 40, the two numbers the engines collide on, a
    // deadlock of oracle, the state MySQL reports before its driver remaps it, and a failure that is no conflict
    final SQLException[] shapes = {
      sql(0, "40001"), sql(1205, "40001"), sql(1213, "40001"), sql(60, "61000"), sql(1205, "HY000"),
      sql(2627, "23000") };

    for (String driver : new String[] { POSTGRES, MYSQL, ORACLE, MSSQL, MARIADB, null })
    {
      final Conflict ceiling = JDBCStorage.ceilingOf(JDBCStorage.dialectOf(driver));
      Conflict strongest = NONE;
      for (SQLException shape : shapes)
      {
        final Conflict conflict = conflictOf(shape, driver);
        assertTrue(conflict.compareTo(ceiling) <= 0,
            driver + " classed " + shape.getSQLState() + "/" + shape.getErrorCode() + " as " + conflict
                + ", above the ceiling its walk stops at");
        strongest = conflict.compareTo(strongest) > 0 ? conflict : strongest;
      }
      assertEquals(strongest, ceiling, "the walk of " + driver + " stops at a class it can never reach");
    }

    // and the walk really stops there: a link behind a conflict already at its engine's ceiling is not looked
    // at. This is what makes reading every link affordable - write() classifies before it knows the failure is
    // replayable at all, so a plain 23000 from adding an entry that is already there reaches this walk too
    final AtomicBoolean walkedPast = new AtomicBoolean();
    final SQLException behindTheCeiling = new SQLException("synthetic failure", "23000", 2627)
    {
      @Override
      public String getSQLState()
      {
        walkedPast.set(true);
        return super.getSQLState();
      }
    };
    assertEquals(conflictOf(sql(0, "40P01", behindTheCeiling), POSTGRES), PROMPT);
    assertFalse(walkedPast.get(), "an engine reporting no lock wait timeout of its own walked past its ceiling");

    // under mysql the same head is not the ceiling - a lock wait timeout could still be behind it - so it is
    assertEquals(conflictOf(sql(0, "40001", behindTheCeiling), MYSQL), PROMPT);
    assertTrue(walkedPast.get(), "mysql stopped before the link a lock wait timeout could have been on");
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
   * The first read-write open of a backend upgraded from a version keeping no catalog creates the catalog and
   * writes one row per tree, and creates no table of its own: every tree it opens is already there. None of that
   * may commit anything of the caller's - {@code RootContainer.open()} opens every tree of every base DN in a
   * single write, and a commit anywhere inside it takes the whole open out of the replay for the life of that
   * attempt, so a deadlock at the twentieth tree would fail the backend start-up that master replayed. The rows
   * still have to be committed, since nothing else of this open would carry them: a connection of the catalog's
   * own is what makes the two compatible.
   */
  @Test
  public void testFillingTheCatalogOfAnUpgradedBackendLeavesTheAttemptReplayable() throws Exception
  {
    // every table of this backend is there except the one the catalog is kept in, which is the shape of an
    // installation whose tables predate the catalog
    final AtomicReference<String> catalogTable = new AtomicReference<>();
    final JDBCStorage storage = storageOverTablesThatAre(tableName -> !tableName.equals(catalogTable.get()));
    catalogTable.set(storage.getTableName(storage.getCatalogTree()));
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      txn.openTree(TREE, true);
      if (attempts.incrementAndGet() == 1)
      {
        throw new StorageRuntimeException(sql(0, "40001"));
      }
    });

    assertEquals(attempts.get(), 2, "the write that created and filled the catalog was not replayed");
    verify(statements, never()).executeUpdate();
    // and the catalog was filled, on the connection of its own: without this every assertion above holds of a
    // storage that enrolled nothing at all - an attempt issuing no statement is replayed the same way, and the
    // caller's connection is exactly as untouched. The row is the ANSI upsert of a plain mock, which tries an
    // update before an insert; the create is the table this fixture is missing
    verify(catalogConnection, atLeastOnce()).prepareStatement(startsWith("create table " + catalogTable.get()));
    verify(catalogConnection, atLeastOnce()).prepareStatement(startsWith("update " + catalogTable.get()));
    // committed where it is written, which is what keeps a row of an attempt that failed afterwards recorded
    verify(catalogConnection, atLeastOnce()).commit();
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
      assertEquals(conflictOf(expected, POSTGRES), PROMPT, "the conflict was not the failure raised");
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
   * A drop the failure reports past any budget reaches the pool and the replay both: the write is replayed on a
   * connection of its own, and the connections the pool established before the same drop are validated on their
   * next borrow instead of being trusted for the rest of the alive window (issue #961). The driver has not closed
   * the connection here - it is the chains of the failure, and only they, that say the database dropped it.
   */
  @Test
  public void testADropDeepInTheChainReachesThePoolAndTheReplay() throws Exception
  {
    final long window = CachedConnection.aliveBypassNanos;
    CachedConnection.aliveBypassNanos = TimeUnit.HOURS.toNanos(1);
    try
    {
      final Connection pooled = mock(Connection.class);
      when(pooled.isValid(anyInt())).thenReturn(true);
      final Connection borrowed = mock(Connection.class);
      when(borrowed.isValid(anyInt())).thenReturn(true);

      final JDBCStorage storage = storageOver(pooled, borrowed);
      // both are proven alive and back in the pool; the one released last is the one the write borrows
      final Connection first = storage.getConnection();
      final Connection second = storage.getConnection();
      first.close();
      second.close();
      verify(borrowed, never()).isValid(anyInt()); // both are inside the window: nothing has validated

      final AtomicInteger attempts = new AtomicInteger();
      storage.write(txn -> {
        if (attempts.incrementAndGet() == 1)
        {
          // the chain mssql-jdbc reports a message of many errors in, with the one that says the session is
          // gone at the end of it - past the budget the walk used to stop at
          throw new StorageRuntimeException(chainEndingIn(200, "08006"));
        }
      });

      assertEquals(attempts.get(), 2, "a write the database dropped the connection under was not replayed");
      // the borrow of the replay validated rather than trusting the last answer of a connection established
      // before the drop: the pool was told
      verify(borrowed, times(1)).isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS);
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
   * The table the index guard names to the catalog is spelled the way the database stores it, and which way
   * that is comes from the driver rather than from the name of the engine. An unquoted identifier is stored
   * folded - upper case on oracle, lower case on postgresql - and {@link DatabaseMetaData#getIndexInfo} matches
   * its argument against the stored form and not against the name as it was written, so a guard spelling it
   * any other way finds no index of a table that carries one and reissues the create behind it: on the two
   * engines whose {@code create index} has no {@code if not exists} that is the open of the tree failing
   * (#902). It is the rule {@code isExistsTable()} takes {@code storedIdentifier()} for, and the guard of the
   * index had it hard-coded in the oracle branch of its caller alone - the one engine of the three that was
   * known to fold upwards.
   */
  @Test
  public void testTheIndexGuardNamesTheTableAsTheDatabaseStoresIt() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true);
    final DatabaseMetaData metaData = engineConnection.getMetaData();
    // a database of this driver that stores what it is given in upper case: what the guard has to ask about
    // is then the folded name, whichever branch of openTree() the driver took to get here
    when(metaData.storesUpperCaseIdentifiers()).thenReturn(true);

    storage.write(txn -> txn.openTree(TREE, true));

    verify(metaData).getIndexInfo(any(), any(), eq(storage.getTableName(TREE).toUpperCase(Locale.ROOT)),
        anyBoolean(), anyBoolean());
  }

  /**
   * The other arm of that rule: a driver saying it stores an unquoted identifier as it was written is asked
   * about the name as it was written. The case above pins the fold alone, and a guard folding upwards
   * whatever the driver answers would pass it: on oracle the two spellings are one, and on postgresql - where
   * the stored form is the lower case name the guard was given - the lookup would report no index of a table
   * that carries one, and the {@code create index if not exists} behind it would reissue in silence, taking
   * every write that opens a tree out of the conflict replay it is guarded for. Nothing would fail and
   * nothing would be logged, so what is pinned here is the question being put to the driver rather than the
   * answer one engine gives.
   */
  @Test
  public void testTheIndexGuardNamesTheTableAsWrittenWhenTheDatabaseStoresItSo() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true);
    final DatabaseMetaData metaData = engineConnection.getMetaData();
    // a database of this driver storing what it is given: storesUpperCaseIdentifiers() and
    // storesLowerCaseIdentifiers() both answer false, and the name to ask about is the one the caller wrote

    storage.write(txn -> txn.openTree(TREE, true));

    verify(metaData).getIndexInfo(any(), any(), eq(storage.getTableName(TREE)), anyBoolean(), anyBoolean());
  }

  /**
   * On mysql the guards of {@code openTree()} ask in the database of the connection. A table name carries no
   * database, so two directories on one server - the stock backend id in two databases - hold the same table
   * and the same index, and Connector/J reads a null catalog as "any database": asked that way, the index of
   * the neighbour answers for this one and the create behind it is skipped for good, leaving every
   * {@code where k>? order by k} batch of every cursor a full scan (#1075). The catalog passed here is one of
   * two layers - {@code TableScope.covers()} reads the database of every row besides, as the case below pins -
   * and a live server cannot tell a loss of either one from their both holding, since each covers for the
   * other: what is pinned here is the question put to the driver.
   */
  @Test
  public void testTheMySqlGuardsAskInTheDatabaseOfTheConnection() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, true);
    when(engineConnection.getCatalog()).thenReturn(THIS_DATABASE);
    final DatabaseMetaData metaData = engineConnection.getMetaData();

    storage.write(txn -> txn.openTree(TREE, true));

    verify(metaData, atLeastOnce()).getTables(eq(THIS_DATABASE), any(), eq(storage.getTableName(TREE)), any());
    verify(metaData).getIndexInfo(eq(THIS_DATABASE), any(), eq(storage.getTableName(TREE)), anyBoolean(),
        anyBoolean());
  }

  /**
   * The other layer of the two: an index a driver reports in another database is not this backend's, however
   * it came to be listed - a driver ignoring the catalog it is given, or a caller no longer passing one. Found
   * there, it must leave the create of this database's own index to go ahead (#1075).
   */
  @Test
  public void testAnIndexOfAnotherDatabaseAnswersForNoneOfThisOne() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, true);
    when(engineConnection.getCatalog()).thenReturn(THIS_DATABASE);
    answerTheIndexFrom(engineConnection.getMetaData(), storage, NEIGHBOUR_DATABASE, null);

    storage.write(txn -> txn.openTree(TREE, true));

    verify(engineConnection).prepareStatement(startsWith("create index k_"));
  }

  /**
   * And the same under {@code databaseTerm=SCHEMA}, the setting of Connector/J that names the database a
   * schema: the connection then names no catalog, the lookup is asked of the whole server, and every row comes
   * back under the catalog {@code def} with the database in its schema - measured against mysql 9.2 with the
   * Connector/J this backend ships (#1075). The schema path {@code TableScope} reads off the connection is the
   * only thing left to tell the neighbour's index from this one's; {@code MySqlTestCase} pins how the driver
   * lists such a row against a live server.
   */
  @Test
  public void testUnderDatabaseTermSchemaAnIndexOfAnotherDatabaseAnswersForNoneOfThisOne() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, true);
    when(engineConnection.getSchema()).thenReturn(THIS_DATABASE);
    answerTheIndexFrom(engineConnection.getMetaData(), storage, "def", NEIGHBOUR_DATABASE);

    storage.write(txn -> txn.openTree(TREE, true));

    verify(engineConnection).prepareStatement(startsWith("create index k_"));
  }

  /** The database of the connection of the three cases above, and that of the directory next to it. */
  private static final String THIS_DATABASE = "this_directory";
  private static final String NEIGHBOUR_DATABASE = "neighbour_directory";

  /**
   * Has the index lookup of the fixture report the {@code k_} index of the table of {@link #TREE} as the one of
   * the given catalog and schema, the way a driver lists the index of another database of the server.
   */
  private static void answerTheIndexFrom(DatabaseMetaData metaData, JDBCStorage storage, String catalog,
      String schema) throws SQLException
  {
    final String tableName = storage.getTableName(TREE);
    // stubbed over the answer of the fixture with doAnswer(): when() would call that answer, which stubs a
    // result set of its own in the middle of this stubbing
    doAnswer(invocation -> {
      final ResultSet indexes = mock(ResultSet.class);
      when(indexes.next()).thenReturn(true, false);
      when(indexes.getString("INDEX_NAME")).thenReturn("k_" + tableName.substring("opendj_".length()));
      when(indexes.getString("TABLE_CAT")).thenReturn(catalog);
      when(indexes.getString("TABLE_SCHEM")).thenReturn(schema);
      return indexes;
    }).when(metaData).getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean());
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
   * The wait a write takes for a row lock another session holds is bounded on the session of the attempt
   * ({@code ROW_LOCK_TIMEOUT_PROPERTY}, #915), and the failure the engine ends that wait with is replayed. On
   * postgres that failure is 55P03, which is no conflict - nothing was rolled back and the blocker still holds
   * the lock - and what makes it worth replaying is that the wait it cost fits inside the replay window, which
   * is the only thing that lets a clock bound these replays at all (#903).
   * <p>
   * The bound is armed per attempt: on postgres it is a {@code set local}, which the rollback of the attempt
   * that failed discards along with everything else the attempt did.
   */
  @Test
  public void testARowLockWaitThisBackendBoundedIsReplayed() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true);
    final List<String> sessionStatements = sessionStatementsOf(engineConnection);
    when(statements.executeUpdate()).thenThrow(sql(0, "55P03")).thenReturn(1);
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      attempts.incrementAndGet();
      txn.openTree(TREE, false);
      txn.put(TREE, ByteString.valueOfUtf8("dc=example,dc=com"), ByteString.valueOfUtf8("an entry"));
    });

    assertEquals(attempts.get(), 2, "a row lock wait this backend bounded was not replayed");
    assertEquals(sessionStatements.stream().filter("set local lock_timeout = 3000"::equals).count(), 2L,
        "the bound was not armed once per attempt: " + sessionStatements);
  }

  /**
   * And where nothing bounds that wait it is not replayed, which is what a deployment asks for by setting the
   * property to 0: this loop must not take again a wait that has no end of its own, and the failure is left
   * exactly as unreplayable as it was before #915 - the behaviour every engine had, and oracle still has.
   */
  @Test
  public void testARowLockWaitNothingBoundedIsNotReplayed() throws Exception
  {
    System.setProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY, "0");
    try
    {
      final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, true);
      final List<String> sessionStatements = sessionStatementsOf(engineConnection);
      when(statements.executeUpdate()).thenThrow(sql(0, "55P03")).thenReturn(1);
      final AtomicInteger attempts = new AtomicInteger();

      try
      {
        storage.write(txn -> {
          attempts.incrementAndGet();
          txn.openTree(TREE, false);
          txn.put(TREE, ByteString.valueOfUtf8("dc=example,dc=com"), ByteString.valueOfUtf8("an entry"));
        });
        fail("a lock wait this backend put no bound on was replayed");
      }
      catch (StorageRuntimeException expected)
      {
        assertEquals(attempts.get(), 1, "an unbounded lock wait was taken a second time");
      }
      assertEquals(sessionStatements, emptyList(), "a bound turned off was armed all the same");
    }
    finally
    {
      System.clearProperty(JDBCStorage.ROW_LOCK_TIMEOUT_PROPERTY);
    }
  }

  /**
   * A DDL of an attempt commits, and that commit is the end of the transaction the row lock bound was armed
   * around: on postgres the {@code set local} goes with it by itself, and on the two engines whose setting is
   * the session's {@code commitStatement()} is what takes it off. What follows such a DDL is a write that has
   * committed part of its work, so it is out of the replay whatever it waits for - and a bound on a wait
   * nothing can replay only fails a write at 3 s where it used to wait for the blocker and go through.
   * <p>
   * The order is what says it: the value is put back before the statement behind the DDL is issued, rather
   * than in the finally of the attempt, which runs after the whole write is through.
   */
  @Test
  public void testTheRowLockBoundComesOffWhereADdlOfTheAttemptCommits() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, false);
    final List<String> issued = statementsOf(engineConnection, "50");

    storage.write(txn -> {
      txn.openTree(TREE, true); // a create index, which commits
      txn.put(TREE, ByteString.valueOfUtf8("dc=example,dc=com"), ByteString.valueOfUtf8("an entry"));
    });

    final int boundArmed = issued.indexOf("set session innodb_lock_wait_timeout=3");
    final int boundOff = issued.indexOf("set session innodb_lock_wait_timeout=50");
    final int theDdl = indexOfFirst(issued, "create index k_");
    final int behindTheDdl = indexOfFirst(issued, "insert into ");
    assertTrue(boundArmed >= 0 && theDdl > boundArmed, "the attempt issued no bounded DDL: " + issued);
    assertTrue(boundOff > theDdl, "the row lock bound was not taken off where the DDL committed: " + issued);
    assertTrue(behindTheDdl > boundOff,
        "the rest of the write ran under a bound its DDL had already taken out of the replay: " + issued);
    // and taken off once: the transaction records that it carries none, so the finally of write() has
    // nothing left to give back and the value is not put back over whatever the rest of the write left
    assertEquals(frequency(issued, "set session innodb_lock_wait_timeout=50"), 1,
        "the value the row lock bound displaced was put back twice: " + issued);
  }

  /**
   * And it is not put back on a connection the driver reports closed: there is nothing there to give it back
   * to, and the setting would fail on a dead session and be reported as a bound left behind - a database
   * restart under write load would read as a stream of stranded bounds for connections that are gone.
   * <p>
   * Which is what the guard is keyed on, rather than on the attempt being classified as a dropped connection:
   * the case below holds the other side of that.
   */
  @Test
  public void testTheRowLockBoundIsNotPutBackOnAConnectionTheDatabaseDropped() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, false);
    final List<String> issued = statementsOf(engineConnection, "50");
    // the state the driver leaves such a connection in, which is what tells it from a connection a failure of
    // this class merely passed through: mssql-jdbc closes the connection for any error of severity 20 and
    // above before it throws, and connector/j closes one the server hung up on
    when(engineConnection.isClosed()).thenReturn(true);

    try
    {
      storage.write(txn -> {
        throw new StorageRuntimeException(sql(0, "08006"));
      });
      fail("the drop of the connection was swallowed");
    }
    catch (StorageRuntimeException expected)
    {
      assertTrue(JDBCStorage.isConnectionFailure(expected), "the failure this case rests on is not a drop");
    }

    assertTrue(issued.contains("set session innodb_lock_wait_timeout=3"),
        "the write armed no row lock bound: " + issued);
    assertEquals(frequency(issued, "set session innodb_lock_wait_timeout=50"), 0,
        "the bound was given back on a connection the database had dropped: " + issued);
  }

  /**
   * While a connection that only this attempt's classification made look dropped is given its value back. A
   * write enrolling a tree opens a connection of the catalog's own, and a database refusing that connect
   * answers a state of class 08 - mysql answers its connection limit with 08004, sql server with 08S01 - which
   * puts the attempt in the replay as a dropped connection with the pooled connection of the write untouched.
   * That connection does go back to the pool: the rollback went through, the validation of the next borrow is
   * {@code isValid()}, and a live session passes it. On the two engines whose bound is a session setting,
   * skipping the restore there hands the next borrow this backend's own 3 s - a read among them, and a read
   * that gives up at a lock wait has no replay to absorb it.
   */
  @Test
  public void testTheRowLockBoundIsPutBackOnALiveConnectionARefusedCatalogConnectMadeLookDropped() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(mysqlConnection.class, false);
    final List<String> issued = statementsOf(engineConnection, "50");

    try
    {
      storage.write(txn -> {
        throw new StorageRuntimeException(new SQLException("Too many connections", "08004", 1040));
      });
      fail("the refusal was swallowed");
    }
    catch (StorageRuntimeException expected)
    {
      assertTrue(JDBCStorage.isConnectionFailure(expected), "the failure this case rests on is not read as a drop");
    }

    // every attempt of the replay, rather than one of them: what the case is about is that no attempt leaves
    // its bound on a connection it hands back, and a refusal of this class is replayed like any other drop
    final int armed = frequency(issued, "set session innodb_lock_wait_timeout=3");
    assertTrue(armed > 0, "the write armed no row lock bound: " + issued);
    assertEquals(frequency(issued, "set session innodb_lock_wait_timeout=50"), armed,
        "the bound was left on a connection the driver still reports open: " + issued);
  }

  /**
   * A DDL told to wait as this backend waited before its bound existed ({@code DDL_LOCK_TIMEOUT_PROPERTY} at 0)
   * is not left waiting at the row bound of the write it is issued from. On postgres that bound is a
   * {@code set local}, which bounds every lock wait of the transaction, so the create table of an open would
   * give up at 3 s - as the bare vendor error, nothing of ours being armed around it - and be replayed as a
   * lock wait of the attempt until the window was spent. The bound comes off in front of the DDL instead.
   * <p>
   * Off the write and not off each of its DDL: the open of a tree that is not there issues two - the create
   * table and the create index behind it - and the second has no bound of this backend left to lift.
   */
  @Test
  public void testADdlToldToWaitDoesNotWaitAtTheRowBoundOnPostgres() throws Exception
  {
    System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "0");
    try
    {
      final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, false);
      tablesAreNotThere(engineConnection);
      final List<String> issued = statementsOf(engineConnection, "0");

      storage.write(txn -> txn.openTree(TREE, true));

      final int bound = issued.indexOf("set local lock_timeout = 3000");
      final int lifted = issued.indexOf("set local lock_timeout to default");
      final int theDdl = indexOfFirst(issued, "create index ");
      assertTrue(bound >= 0, "the write armed no row lock bound: " + issued);
      assertTrue(lifted > bound, "the row lock bound was not taken off in front of an unbounded DDL: " + issued);
      assertTrue(theDdl > lifted, "the DDL ran under the row bound of the write that issued it: " + issued);
      assertTrue(indexOfFirst(issued, "create table ") >= 0, "the open of this case created no table: " + issued);
      assertEquals(frequency(issued, "set local lock_timeout to default"), 1,
          "the row lock bound was lifted once per DDL rather than once per write: " + issued);
    }
    finally
    {
      System.clearProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY);
    }
  }

  /**
   * The same on sql server, where the setting is the session's and what puts the wait back where the deployment
   * left it is the value the row bound displaced - here the -1 that waits forever, which is what this backend
   * waited before either bound existed. The DDL is the create table of a tree that is not there: this engine
   * indexes nothing behind it, {@code k} being a {@code varbinary(max)} no index key column can hold.
   */
  @Test
  public void testADdlToldToWaitDoesNotWaitAtTheRowBoundOnSqlServer() throws Exception
  {
    System.setProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY, "0");
    try
    {
      final JDBCStorage storage = storageOverAnEngine(microsoftConnection.class, false);
      tablesAreNotThere(engineConnection);
      final List<String> issued = statementsOf(engineConnection, "-1");

      storage.write(txn -> txn.openTree(TREE, true));

      final int bound = issued.indexOf("set lock_timeout 3000");
      final int lifted = issued.indexOf("set lock_timeout -1");
      final int theDdl = indexOfFirst(issued, "create table ");
      assertTrue(bound >= 0, "the write armed no row lock bound: " + issued);
      assertTrue(lifted > bound, "the deployment's own value was not put back in front of an unbounded DDL: "
          + issued);
      assertTrue(theDdl > lifted, "the DDL ran under the row bound of the write that issued it: " + issued);
    }
    finally
    {
      System.clearProperty(JDBCStorage.DDL_LOCK_TIMEOUT_PROPERTY);
    }
  }

  /**
   * While a DDL that has a bound of its own is left exactly where it was: it arms that bound over the row one
   * and puts it back afterwards, so taking the row bound off in front of it would be a round trip per DDL -
   * about 25 of them per suffix of an open - buying nothing.
   */
  @Test
  public void testABoundedDdlLeavesTheRowBoundOfTheWriteWhereItIs() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, false);
    final List<String> issued = statementsOf(engineConnection, "0");

    storage.write(txn -> txn.openTree(TREE, true));

    assertTrue(issued.contains("set local lock_timeout = 3000"), "the write armed no row lock bound: " + issued);
    assertEquals(frequency(issued, "set local lock_timeout to default"), 0,
        "a DDL with a bound of its own took the row bound off in front of itself: " + issued);
  }

  /**
   * And a DDL that did give up at its own bound inside a write is replayed like any other lock wait of a
   * bounded attempt - on the copy of the bound that attempt ran under, which is what {@code write()} keeps
   * for the replay while {@code commitStatement()} takes the transaction's copy off at the DDL that ends it.
   * Decided on the transaction's copy instead, such a failure would be thrown at the first attempt.
   */
  @Test
  public void testADdlLockWaitInsideAWriteIsReplayedOnTheBoundTheAttemptRanUnder() throws Exception
  {
    final JDBCStorage storage = storageOverAnEngine(postgresConnection.class, false);
    sessionStatementsOf(engineConnection); // the row bound of the attempt: the fixture takes no session statement
    when(statements.executeUpdate()).thenThrow(sql(0, "55P03")).thenReturn(0);
    final AtomicInteger attempts = new AtomicInteger();

    storage.write(txn -> {
      attempts.incrementAndGet();
      txn.openTree(TREE, true);
    });

    assertEquals(attempts.get(), 2, "a DDL that gave up at its own bound inside a bounded attempt was not replayed");
  }

  /** Where the first statement of the attempt starting with the given text was issued, or -1. */
  private static int indexOfFirst(List<String> issued, String startsWith)
  {
    for (int i = 0; i < issued.size(); i++)
    {
      if (issued.get(i).startsWith(startsWith))
      {
        return i;
      }
    }
    return -1;
  }

  /**
   * Answers the metadata of a connection with a database holding none of the tables asked about, so that an
   * open creates them: the fixture of {@code storageOverAnEngine()} answers that every table asked about is
   * there, which is the existing backend most cases here are about.
   */
  private static void tablesAreNotThere(Connection con) throws Exception
  {
    final ResultSet none = mock(ResultSet.class);
    when(none.next()).thenReturn(false);
    // the metadata is taken out of the chain first, and the stubbing is a doReturn: asking when() for the
    // value of a call already stubbed with an answer would run that answer here, and it stubs a result set
    // of its own as it goes
    final DatabaseMetaData metaData = con.getMetaData();
    doReturn(none).when(metaData).getTables(any(), any(), any(), any());
  }

  /**
   * Records the session statements a connection is given - the bound of an attempt among them - on a fixture
   * whose {@code createStatement()} otherwise refuses them. Stubbed through {@code doReturn}, since asking
   * {@code when()} for the value of a call already stubbed to throw would raise that throw here.
   */
  private static List<String> sessionStatementsOf(Connection con) throws Exception
  {
    final List<String> issued = new ArrayList<>();
    final Statement statement = mock(Statement.class);
    when(statement.execute(anyString())).thenAnswer(invocation -> {
      issued.add((String) invocation.getArguments()[0]);
      return false;
    });
    doReturn(statement).when(con).createStatement();
    return issued;
  }

  /**
   * The same, with the statements of the work itself in the very same list and the readback of a session
   * setting answered: what a case reads off this is the order the two were issued in, which is what a bound
   * armed around a transaction and taken off inside it can only be pinned by.
   */
  private List<String> statementsOf(Connection con, String carries) throws Exception
  {
    final List<String> issued = sessionStatementsOf(con);
    final Statement statement = con.createStatement();
    when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
      issued.add((String) invocation.getArguments()[0]);
      final ResultSet carried = mock(ResultSet.class);
      when(carried.next()).thenReturn(true, false);
      when(carried.getString(1)).thenReturn(carries);
      return carried;
    });
    doAnswer(invocation -> {
      issued.add((String) invocation.getArguments()[0]);
      return statements;
    }).when(con).prepareStatement(anyString());
    return issued;
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
      assertEquals(conflictOf(expected, MYSQL), PROMPT, "the conflict was not the failure raised");
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
   * A storage whose pool hands out one connection of this test, over a database that either holds the tables
   * this backend asks about or holds none of them - the table of {@link #TREE} and the table of the tree
   * catalog alike. The connection is a mock of no recognized driver, which is how the engines that guard their
   * create index - and mssql, which has none - reach {@code openTree}.
   */
  private JDBCStorage storageOverACatalogHolding(boolean theTable) throws Exception
  {
    return storageOverTablesThatAre(tableName -> theTable);
  }

  /**
   * The same, over a database holding exactly the tables the given rule accepts, and with a second connection
   * behind the pooled one: the tree catalog is written on a connection of its own, so that its rows commit
   * nothing of the caller's - which is the very thing {@code statements} is asserted on below.
   */
  private JDBCStorage storageOverTablesThatAre(Predicate<String> present) throws Exception
  {
    final Connection con = mock(Connection.class);
    final JDBCStorage storage = storageOver(con, catalogConnection());

    statements = mock(PreparedStatement.class);
    final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    // a result set of its own per call: the catalog is asked once per attempt, and a replayed attempt
    // reading a result set the previous one had already walked to its end would find no table there
    when(metaData.getTables(any(), any(), any(), any())).thenAnswer(invocation -> {
      // the name asked about and not the one table of a fixture: openTree() asks about the table of the tree
      // and about the table of the catalog, and answering the second with the name of the first would have
      // the catalog created over again on every attempt
      final String asked = (String) invocation.getArguments()[2];
      final ResultSet tables = mock(ResultSet.class);
      when(tables.next()).thenReturn(present.test(asked), false);
      when(tables.getString("TABLE_NAME")).thenReturn(asked);
      return tables;
    });

    when(con.isValid(anyInt())).thenReturn(true);
    when(con.getMetaData()).thenReturn(metaData);
    when(con.prepareStatement(anyString())).thenReturn(statements);
    return storage;
  }

  /** A connection of no rows at all, for a query this fixture has nothing to answer with. */
  private static ResultSet noRows() throws SQLException
  {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.next()).thenReturn(false);
    return rs;
  }

  /**
   * A mock of the given connection type, with the name every engine branch of {@code JDBCStorage} is keyed on
   * asserted rather than assumed. The name of a mock is derived from the type it mocks, so a renamed fixture -
   * or a Mockito that names its mocks differently - would move a case into another engine's branch with no test
   * saying so, and there are cases no count of attempts would catch that in.
   */
  private static Connection mockOfEngine(Class<? extends Connection> engine)
  {
    final Connection con = mock(engine);
    final String engineName = engine.getSimpleName().replace("Connection", "");
    assertTrue(JDBCStorage.driverNameOf(con).contains(engineName),
        "a mock of " + engine.getSimpleName() + " reaches no " + engineName + " branch: "
            + JDBCStorage.driverNameOf(con));
    return con;
  }

  /**
   * The connection the tree catalog of a storage of this test is written on: it opens one straight through the
   * driver, for the reason a stamp opens one of its own - the caller of openTree() is holding a pooled
   * connection already.
   */
  private Connection catalogConnection() throws Exception
  {
    final Connection con = mock(Connection.class);
    final PreparedStatement onIt = mock(PreparedStatement.class);
    final ResultSet empty = noRows(); // the read of what the catalog records: nothing was ever enrolled
    when(onIt.executeQuery()).thenReturn(empty);
    when(con.prepareStatement(anyString())).thenReturn(onIt);
    // the stamp of a tree name opens a connection of its own too, and a fixture that let it have this one
    // would have it issue the session statement of its dialect here
    when(con.createStatement()).thenThrow(new SQLException("no session statement in this test", "42000"));
    catalogConnection = con;
    return con;
  }

  /**
   * A storage whose pool hands out one connection of the given engine, over a database holding every table
   * this backend asks about - that of {@link #TREE} and that of its tree catalog - and either holding the
   * {@code k_} index of the first or not. The index guard and the statement behind it
   * are the branches {@code openTree()} takes per engine, and a mock of plain {@link Connection} reaches none
   * of them - so the name the mock ends up with is asserted here rather than assumed.
   */
  private JDBCStorage storageOverAnEngine(Class<? extends Connection> engine, boolean theIndex,
      Connection... behind) throws Exception
  {
    final Connection con = mockOfEngine(engine);
    engineConnection = con;
    // the connections behind it answer the connects the pool does not make: the tree catalog is read and
    // written on one of its own, straight through the driver, since the caller of openTree() is holding a
    // pooled connection already - and the stamp of a tree name opens one for the same reason. The catalog
    // comes first because openTree() opens the catalog before anything else and stamping its table is the
    // last thing that does, so a test naming a connection of its own names the one behind it
    final Connection[] answers = new Connection[behind.length + 2];
    answers[0] = con;
    answers[1] = catalogConnection();
    System.arraycopy(behind, 0, answers, 2, behind.length);
    final JDBCStorage storage = storageOver(answers);

    statements = mock(PreparedStatement.class);
    final String tableName = storage.getTableName(TREE);
    final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    // a result set of its own per call, for the reason the catalog of the test above hands out one: a replayed
    // attempt reading a result set the previous one had already walked to its end would find nothing there
    when(metaData.getTables(any(), any(), any(), any())).thenAnswer(invocation -> {
      final ResultSet tables = mock(ResultSet.class);
      when(tables.next()).thenReturn(true, false);
      // the name asked about: openTree() asks about the table of the tree and about the table of the catalog
      when(tables.getString("TABLE_NAME")).thenReturn((String) invocation.getArguments()[2]);
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
    final int pool = pools.incrementAndGet();
    when(cfg.getDBDirectory()).thenReturn(StubDriver.PREFIX + pool);
    // the tree catalog of a backend is named after its id: a mock answering null for it would name every
    // storage of this class the same catalog, and the tables of these fixtures are named after that name
    when(cfg.getBackendId()).thenReturn("retry" + pool);
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
    return JDBCStorage.replayReason(conflictOf(failure, driver), failure, committing, partlyCommitted,
        connectionClosed, JDBCStorage.dialectOf(driver), NO_ROW_LOCK_BOUND);
  }

  /**
   * The class of a failure, composed of the pieces {@code write()} composes it of. Forms of this and of
   * {@link #conflictSummary} taking a failure and a driver used to live in {@code JDBCStorage} with no caller of
   * their own in {@code src/main}, which left the classification javadoc hanging off methods production never
   * called and made every test asking both questions walk the chains twice. The convenience is a test's, so it
   * is written here.
   */
  private static Conflict conflictOf(Throwable failure, String driver)
  {
    return JDBCStorage.conflictVerdict(failure, driver).conflict;
  }

  /** The line reporting a replay, composed the way {@code write()} composes it: one walk, then the summary of it. */
  private static String conflictSummary(Throwable failure, String driver)
  {
    return JDBCStorage.conflictSummary(JDBCStorage.conflictVerdict(failure, driver), failure);
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
      { "the window bounds the run, not the attempt", postgresConnection.class, sql(0, "40P01"), 4L, PROMPT, 3 },
      // a step past the window, so only the grant can produce a second attempt: remove it and the run ends on
      // the first. This is the row that pins the grant end to end, and the row above is the one that pins
      // startedAt - at 12 s a per-attempt startedAt also stops at two attempts, and at 4 s the window alone
      // already allows the replay of attempt 1. Neither row is redundant
      { "the first replay is granted past the window", postgresConnection.class, sql(0, "40P01"), 12L, PROMPT, 2 },
      // the same step, and the same class 40 state, for the one conflict the engine had already bounded: a
      // single attempt. The predicate rows pin that decision, but only these rows pin that write() hands the
      // predicate the class of its own failure - dropped on the way to replayableWithin() and the run above
      // stays green while this one buys a second innodb_lock_wait_timeout
      { "a lock wait timeout is granted no replay", mysqlConnection.class, sql(1205, "40001"), 12L,
        AFTER_LOCK_WAIT, 1 },
      // and the driver that reports that same timeout under a name this backend does not recognise: no grant
      // there either, since what the grant rests on - the engine having bounded nothing - is unknown of it.
      // No number of attempts separates this row from the one above: AFTER_LOCK_WAIT and UNKNOWN_ENGINE are
      // refused the grant and bounded by the window identically, so the mysql row misread as unrecognised
      // produces exactly the count expected of it. That is why each row names the class its engine gives its
      // failure and the case asserts it of the mock it actually built
      { "an unrecognised engine is granted no replay", mariadbConnection.class, sql(1205, "40001"), 12L,
        UNKNOWN_ENGINE, 1 },
    };
  }

  @Test(dataProvider = "writeRuns")
  public void testWriteDrivesTheRetryLoop(String name, Class<? extends Connection> engine,
      final SQLException conflict, final long stepSeconds, Conflict expectedClass, int expectedAttempts)
      throws Exception
  {
    final AtomicInteger attempts = new AtomicInteger();
    // the class name of the mock is what write() reads the engine off, asserted here the way
    // storageOverAnEngine() asserts it
    final Connection connection = mockOfEngine(engine);
    // which branch of the classification the row reaches, asserted rather than inferred from the count: the
    // count cannot tell AFTER_LOCK_WAIT from UNKNOWN_ENGINE, since both are refused the grant and bounded by
    // the window alike, so a fixture renamed out of the mysql branch would leave that row green
    assertEquals(conflictOf(conflict, JDBCStorage.driverNameOf(connection)), expectedClass,
        name + ": the mock does not reach the branch the row names");

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

  /** A chain of the given number of next exceptions whose last link carries the given SQLState. */
  private static SQLException chainEndingIn(int links, String lastState)
  {
    final SQLException head = sql(2627, "23000");
    SQLException tail = head;
    for (int link = 2; link <= links; link++)
    {
      tail = chained(tail, sql(0, link == links ? lastState : "23000")).getNextException();
    }
    return head;
  }

  /** The given failure behind the given number of wrappers, the way a caller of this backend wraps one. */
  private static Throwable wrappedTimes(int wrappers, Throwable failure)
  {
    Throwable wrapped = failure;
    for (int wrapper = 0; wrapper < wrappers; wrapper++)
    {
      wrapped = new IllegalStateException("wrapped", wrapped);
    }
    return wrapped;
  }

  /** The second failure suppressed into the first, the way a failing close() joins the failure of an operation. */
  private static SQLException suppressing(SQLException failure, SQLException onRelease)
  {
    failure.addSuppressed(onRelease);
    return failure;
  }
}
