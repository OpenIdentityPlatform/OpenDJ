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

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.jdbc.JDBCStorage.Conflict;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.AFTER_LOCK_WAIT;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.NONE;
import static org.opends.server.backends.jdbc.JDBCStorage.Conflict.PROMPT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests how a failure is classified as a transaction conflict, which is what decides whether
 * {@link JDBCStorage#write} replays the operation, how long the replays may go on for, and how long it waits
 * before each of them.
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
    };
  }

  /** Whether a failure is replayed at all is what the classification decides first; the class does not change it. */
  @Test(dataProvider = "failures")
  public void testIsRetryableConflict(String name, Throwable failure, String driver, Conflict expected)
  {
    assertEquals(JDBCStorage.isRetryableConflict(failure, driver), expected != NONE, name);
  }

  /** Which class it lands in decides only how long its replays may go on for. */
  @Test(dataProvider = "failures")
  public void testConflictClass(String name, Throwable failure, String driver, Conflict expected)
  {
    assertEquals(JDBCStorage.conflictOf(failure, driver), expected, name);
  }

  /**
   * Which replays happen. The first one is never denied, since the wait an engine spends before reporting a
   * conflict is charged to the attempt that hit it and no window can be chosen that some wait does not outlast;
   * the replays after it are bounded by the window of the class of the conflict.
   */
  @DataProvider
  public Object[][] replays()
  {
    return new Object[][] {
      // the engine asked for the transaction to be rerun, and no clock denies that first rerun. The failure of run
      // 33010633197 is the case: SQL Server leaves the lock wait unbounded, so its deadlock monitor picked a victim
      // ~12 s into the first attempt, and a window of any size is outlasted by some wait
      { "deadlock reported after a long lock wait", 1, seconds(12), sql(1205, "40001"), MSSQL, true },
      { "deadlock reported later than any window", 1, seconds(600), sql(1205, "40001"), MSSQL, true },
      // what granting it costs is one more wait of the engine that waits longest, and only once
      { "mysql lock wait timeout, first attempt", 1, seconds(50), sql(1205, "40001"), MYSQL, true },

      // the replays after the first are what the window bounds, so that a conflict which never clears is failed
      // rather than never returned
      { "deadlock within the window", 2, seconds(59), sql(1205, "40001"), MSSQL, true },
      { "deadlock at the window", 2, seconds(60), sql(1205, "40001"), MSSQL, false },
      { "deadlock past the window", 2, seconds(61), sql(1205, "40001"), MSSQL, false },
      // MySQL reports a lock wait timeout only after innodb_lock_wait_timeout, 50 s by default: ten attempts of
      // those park a worker thread for eight minutes, which is the wait the narrower window exists to bound
      { "mysql lock wait timeout within its window", 2, seconds(9), sql(1205, "40001"), MYSQL, true },
      { "mysql lock wait timeout at its window", 2, seconds(10), sql(1205, "40001"), MYSQL, false },
      { "mysql lock wait timeout past its window", 2, seconds(12), sql(1205, "40001"), MYSQL, false },
      // the two MySQL numbers part ways here: a deadlock is reported as promptly as any other engine reports one
      { "mysql deadlock", 2, seconds(12), sql(1213, "40001"), MYSQL, true },

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

  private static long seconds(long seconds)
  {
    return seconds * 1000L * 1000L * 1000L;
  }
}
