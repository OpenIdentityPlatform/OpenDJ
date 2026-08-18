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

import java.sql.SQLException;

import static org.forgerock.i18n.LocalizableMessage.raw;
import static org.forgerock.opendj.ldap.ResultCode.OTHER;
import static org.testng.Assert.assertEquals;

/**
 * Tests how a failure is classified as a transaction conflict, which is what decides whether
 * {@link JDBCStorage#write} and {@link JDBCStorage#read} replay the operation.
 * <p>
 * Runs without a database: the failures the drivers report are reproduced as synthetic
 * {@link SQLException}s carrying the same vendor error number and SQLState.
 */
@Test(sequential = true)
@SuppressWarnings("javadoc")
public class JDBCStorageRetryTest extends DirectoryServerTestCase
{
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
      { "mssql deadlock victim", sql(1205, "40001"), true },
      // the xopenStates connection property makes the same driver report a state of no help here
      { "mssql deadlock victim, xopenStates", sql(1205, "42000"), true },
      // the conflict of the other engines is carried by the SQLState, under a vendor number of their own
      { "postgres serialization failure", sql(0, "40001"), true },
      { "postgres deadlock detected", sql(0, "40P01"), true },
      { "mysql deadlock", sql(1213, "40001"), true },
      // not a deadlock, but transient in the same way and equally resolved by a replay
      { "mysql lock wait timeout", sql(1205, "HY000"), true },

      // the conflict reaches JDBCStorage.write() wrapped, so the whole cause chain has to be walked
      { "wrapped once", new StorageRuntimeException(sql(1205, "40001")), true },
      { "wrapped twice",
        new DirectoryException(OTHER, raw("unchecked"), new StorageRuntimeException(sql(1205, "40001"))), true },

      // nothing a replay can resolve
      { "primary key violation", sql(2627, "23000"), false },
      { "syntax error", sql(102, "S0001"), false },
      { "no SQLState", sql(0, null), false },
      { "not a SQLException", new IllegalStateException("connection closed"), false },
      { "wrapped, not a conflict", new StorageRuntimeException(sql(2627, "23000")), false },
      { "no failure at all", null, false },
      { "cyclic cause chain", new SelfCausedException(), false },
    };
  }

  @Test(dataProvider = "failures")
  public void testIsRetryableConflict(String name, Throwable failure, boolean expected)
  {
    assertEquals(JDBCStorage.isRetryableConflict(failure), expected, name);
  }

  private static SQLException sql(int errorCode, String sqlState)
  {
    return new SQLException("synthetic failure", sqlState, errorCode);
  }
}
