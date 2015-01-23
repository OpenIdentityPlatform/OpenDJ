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
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.util.StaticUtils;

/**
 * This class is responsible for storing the configuration state of
 * the JE backend for a particular suffix.
 */
public class State extends DatabaseContainer
{
  private static final ByteString falseBytes = ByteString.wrap(new byte[] { 0x00 });
  private static final ByteString trueBytes = ByteString.wrap(new byte[] { 0x01 });

  /**
   * Create a new State object.
   *
   * @param name The name of the entry database.
   * @param env The JE Storage.
   * @param entryContainer The entryContainer of the entry database.
   */
  State(TreeName name, Storage env, EntryContainer entryContainer)
  {
    super(name, env, entryContainer);
  }

  /**
   * Return the key associated with the index in the state database.
   *
   * @param index The index we need the key for.
   * @return the key
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  private ByteString keyForIndex(DatabaseContainer index)
    throws StorageRuntimeException
  {
    String shortName = index.getName().toString();
    return ByteString.wrap(StaticUtils.getBytes(shortName));
  }

  /**
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return true if the entry was removed, false if it was not.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  boolean removeIndexTrustState(WriteableStorage txn, DatabaseContainer index) throws StorageRuntimeException
  {
    ByteString key = keyForIndex(index);
    return delete(txn, key);
  }

  /**
   * Fetch index state from the database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @return The trusted state of the index in the database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public boolean getIndexTrustState(ReadableStorage txn, DatabaseContainer index)
      throws StorageRuntimeException
  {
    ByteString key = keyForIndex(index);
    ByteString value = read(txn, key, false);

    if (value != null)
    {
      return value.equals(trueBytes);
    }
    return false;
  }

  /**
   * Put index state to database.
   * @param txn The database transaction or null if none.
   * @param index The index storing the trusted state info.
   * @param trusted The state value to put into the database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  void putIndexTrustState(WriteableStorage txn, DatabaseContainer index, boolean trusted)
      throws StorageRuntimeException
  {
    ByteString key = keyForIndex(index);

    txn.create(treeName, key, trusted ? trueBytes : falseBytes);
  }

}
