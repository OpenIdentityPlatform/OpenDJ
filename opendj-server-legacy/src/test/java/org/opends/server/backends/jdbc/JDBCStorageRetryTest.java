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
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests how a failure is classified - as a transaction conflict, or as a connection the database dropped - which
 * is what decides whether {@link JDBCStorage#write} replays the operation, and how long it waits before it does.
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
