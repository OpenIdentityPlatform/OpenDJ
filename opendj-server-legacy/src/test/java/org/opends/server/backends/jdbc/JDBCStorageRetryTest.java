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
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.mockito.Mockito.mock;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.AFTER_LOCK_WAIT;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.NONE;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.PROMPT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests how a failure is classified as a transaction conflict, which is what decides whether
 * {@link JDBCStorage#write} replays the operation and whether its first replay is granted regardless of the
 * clock, how long the replays may go on for, and how long it waits before each of them.
 * <p>
 * Runs without a database: the failures the drivers report are reproduced as synthetic
 * {@link SQLException}s carrying the same vendor error number and SQLState.
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
      // ... but the state of a conflict is still matched whatever vendor number carries it
      { "class 40 is driver independent", sql(0, "40001"), null, PROMPT },
      // the number that makes a conflict slow is a MySQL number too, so a class 40 state carrying it under any
      // other driver is classified by its state alone, and keeps the window of a conflict reported promptly
      { "class 40 with 1205, no driver", sql(1205, "40001"), null, PROMPT },

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

      // the attempt count bounds every class, whatever the window has left
      { "last attempt left", 9, 0L, sql(1205, "40001"), MSSQL, true },
      { "attempts exhausted", 10, 0L, sql(1205, "40001"), MSSQL, false },
      // and nothing a replay resolves is replayed, the first attempt included
      { "not a conflict", 1, 0L, sql(2627, "23000"), MSSQL, false },
    };
  }

  @Test(dataProvider = "replays")
  public void testReplayable(String name, int attempt, long elapsedNanos, Throwable failure, String driver,
      boolean expected)
  {
    assertEquals(JDBCStorage.replayable(attempt, elapsedNanos, failure, driver), expected, name);
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
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(1205, "40001"))));
    assertTrue(summary.contains("40001"), summary);
    assertTrue(summary.contains("1205"), summary);
    assertTrue(summary.contains("synthetic failure"), summary);
  }

  /** A failure carrying no SQLException at all, and a cyclic cause chain, still have to yield something loggable. */
  @Test
  public void testConflictSummaryTerminatesWithoutASQLException()
  {
    assertTrue(JDBCStorage.conflictSummary(new IllegalStateException("connection closed")).contains("closed"));
    assertTrue(JDBCStorage.conflictSummary(new SelfCausedException()).contains("SelfCausedException"));
    assertEquals(JDBCStorage.conflictSummary(null), "null");
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
   * advances a fixed step per read, so the attempts made and the reads taken are both exact.
   * <p>
   * Between them the rows pin the two lines the rest of the file would let a refactor take away. A single
   * {@code startedAt} outside the retry loop is what makes the window bound the whole run rather than each
   * attempt: moved inside, every attempt is measured against its own start, sees the step and nothing more, and
   * replays to MAX_RETRIES. And the grant of the first replay is what issue #903 is about: without it an attempt
   * that alone outlasts the window leaves the loop with no replay at all.
   */
  @DataProvider
  public Object[][] writeRuns()
  {
    return new Object[][] {
      // a step under the window, so the window is what ends the run: attempt 1 is granted its replay at 4 s,
      // attempt 2 is inside the window at 8 s, attempt 3 is past it at 12 s. With startedAt inside the loop every
      // attempt measures 4 s, never reaches the window, and the run goes to MAX_RETRIES instead
      { "the window bounds the run, not the attempt", 4L, 3, 4 },
      // a step past the window, so only the grant can produce a second attempt: remove it and the run ends on the
      // first. With startedAt inside the loop the attempts still come to two, but each takes a read of its own
      { "the first replay is granted past the window", 12L, 2, 3 },
    };
  }

  @Test(dataProvider = "writeRuns")
  public void testWriteDrivesTheRetryLoop(String name, final long stepSeconds, int expectedAttempts,
      int expectedClockReads) throws Exception
  {
    final AtomicInteger clockReads = new AtomicInteger();
    final AtomicInteger attempts = new AtomicInteger();
    final Connection connection = mock(Connection.class);
    // no driver name matches a mock, so this is classified by its class 40 state alone: a prompt conflict
    final SQLException conflict = sql(0, "40001");

    final JDBCStorage storage = new JDBCStorage(mock(JDBCBackendCfg.class), null)
    {
      @Override
      Connection getConnection()
      {
        return connection;
      }

      @Override
      long nanoTime()
      {
        return seconds(stepSeconds * clockReads.getAndIncrement());
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
    // read once before the loop and once after each attempt. The count is asserted, not just the placement,
    // because the clock advances per read rather than per attempt: a second read added inside an attempt would
    // halve the effective step and change the run without either row saying so
    assertEquals(clockReads.get(), expectedClockReads, name + ": clock reads");
  }
}
