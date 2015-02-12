/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import java.util.Arrays;

import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/**
 * This class is responsible for storing the configuration state of
 * the JE backend for a particular suffix.
 */
public class State extends DatabaseContainer
{
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
    this.dbConfig = JEBUtils.toDatabaseConfigNoDuplicates(env);
  }

  /**
   * Return the key associated with the index in the state database.
   *
   * @param index The index we need the key for.
   * @return the key
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private DatabaseEntry keyForIndex(DatabaseContainer index)
    throws DatabaseException
  {
    String shortName =
      index.getName().replace(entryContainer.getDatabasePrefix(), "");
    return new DatabaseEntry(StaticUtils.getBytes(shortName));
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return true if the entry was removed, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  boolean removeIndexTrustState(Transaction txn, DatabaseContainer index)
       throws DatabaseException
  {
    DatabaseEntry key = keyForIndex(index);

    return delete(txn, key) == SUCCESS;
  }

  /**
   * Fetch index state from the database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return The trusted state of the index in the database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean getIndexTrustState(Transaction txn, DatabaseContainer index)
      throws DatabaseException
  {
    DatabaseEntry key = keyForIndex(index);
    DatabaseEntry data = new DatabaseEntry();

    if (read(txn, key, data, DEFAULT) == SUCCESS)
    {
      byte[] bytes = data.getData();
      return Arrays.equals(bytes, trueBytes);
    }
    return false;
  }

  /**
   * Put index state to database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @param trusted The state value to put into the database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  void putIndexTrustState(Transaction txn, DatabaseContainer index, boolean trusted)
       throws DatabaseException
  {
    DatabaseEntry key = keyForIndex(index);
    DatabaseEntry data = new DatabaseEntry();

    data.setData(trusted ? trueBytes : falseBytes);
    put(txn, key, data);
  }

}
