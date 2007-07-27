/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.util.StaticUtils;
import com.sleepycat.je.*;

import java.util.Arrays;

/**
 * This class is responsible for storing the configuration state of
 * the JE backend for a particular suffix.
 */
public class State extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private static final byte[] falseBytes = new byte[]{0x00};
  private static final byte[] trueBytes = new byte[]{0x01};

  /**
   * Create a new State object.
   *
   * @param name The name of the entry database.
   * @param env The JE Environment.
   * @param entryContainer The entryContainer of the entry database.
   * @throws com.sleepycat.je.DatabaseException If an error occurs in the
   * JE database.
   *
   */
  State(String name, Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);

    DatabaseConfig dbNodupsConfig = new DatabaseConfig();

    if(env.getConfig().getReadOnly())
    {
      dbNodupsConfig.setReadOnly(true);
      dbNodupsConfig.setAllowCreate(false);
      dbNodupsConfig.setTransactional(false);
    }
    else if(!env.getConfig().getTransactional())
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(false);
      dbNodupsConfig.setDeferredWrite(true);
    }
    else
    {
      dbNodupsConfig.setAllowCreate(true);
      dbNodupsConfig.setTransactional(true);
    }

    this.dbConfig = dbNodupsConfig;
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return true if the entry was removed, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean removeIndexTrustState(Transaction txn, Index index)
       throws DatabaseException
  {
    DatabaseEntry key =
        new DatabaseEntry(StaticUtils.getBytes(index.getName()));

    OperationStatus status = delete(txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Fetch index state from the database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return The trusted state of the index in the database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean getIndexTrustState(Transaction txn, Index index)
      throws DatabaseException
  {
    String sortName =
        index.getName().replace(entryContainer.getDatabasePrefix(), "");
    DatabaseEntry key =
        new DatabaseEntry(StaticUtils.getBytes(sortName));
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = read(txn, key, data, LockMode.DEFAULT);

    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }

    byte[] bytes = data.getData();
    return Arrays.equals(bytes, trueBytes);
  }

  /**
   * Fetch index state from the database.
   * @param txn The database transaction or null if none.
   * @param vlvIndex The index storing the trusted state info.
   * @return The trusted state of the index in the database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean getIndexTrustState(Transaction txn, VLVIndex vlvIndex)
      throws DatabaseException
  {
    String shortName =
        vlvIndex.getName().replace(entryContainer.getDatabasePrefix(), "");
    DatabaseEntry key =
        new DatabaseEntry(StaticUtils.getBytes(shortName));
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = read(txn, key, data, LockMode.DEFAULT);

    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }

    byte[] bytes = data.getData();
    return Arrays.equals(bytes, trueBytes);
  }

  /**
   * Put index state to database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @param trusted The state value to put into the database.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean putIndexTrustState(Transaction txn, Index index,
                                    boolean trusted)
       throws DatabaseException
  {
    String shortName =
        index.getName().replace(entryContainer.getDatabasePrefix(), "");
    DatabaseEntry key =
        new DatabaseEntry(StaticUtils.getBytes(shortName));
    DatabaseEntry data = new DatabaseEntry();

    if(trusted)
      data.setData(trueBytes);
    else
      data.setData(falseBytes);

    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Put VLV index state to database.
   * @param txn The database transaction or null if none.
   * @param vlvIndex The VLV index storing the trusted state info.
   * @param trusted The state value to put into the database.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean putIndexTrustState(Transaction txn, VLVIndex vlvIndex,
                                    boolean trusted)
       throws DatabaseException
  {
    String shortName =
        vlvIndex.getName().replace(entryContainer.getDatabasePrefix(), "");
    DatabaseEntry key =
        new DatabaseEntry(StaticUtils.getBytes(shortName));
    DatabaseEntry data = new DatabaseEntry();

    if(trusted)
      data.setData(trueBytes);
    else
      data.setData(falseBytes);

    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }
}
