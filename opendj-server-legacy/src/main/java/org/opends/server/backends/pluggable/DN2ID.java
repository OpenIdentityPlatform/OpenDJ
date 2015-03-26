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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.JebFormat.*;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.DN;

/**
 * This class represents the DN database, or dn2id, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
class DN2ID extends DatabaseContainer
{
  private final int prefixRDNComponents;

  /**
   * Create a DN2ID instance for the DN database in a given entryContainer.
   *
   * @param treeName The name of the DN database.
   * @param entryContainer The entryContainer of the DN database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  DN2ID(TreeName treeName, EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(treeName);
    prefixRDNComponents = entryContainer.getBaseDN().size();
  }

  /**
   * Adds a new record into the DN database replacing any existing record having the same DN.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @throws StorageRuntimeException If an error occurred while attempting to insert
   * the new record.
   */
  void put(WriteableTransaction txn, DN dn, EntryID id) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);
    ByteString value = id.toByteString();
    txn.put(getName(), key, value);
  }

  /**
   * Remove a record from the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @return true if the record was removed, false if it was not removed.
   * @throws StorageRuntimeException If an error occurred while attempting to remove
   * the record.
   */
  boolean remove(WriteableTransaction txn, DN dn) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);

    return txn.delete(getName(), key);
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn A JE database transaction to be used for the database read, or
   * null if none is required.
   * @param dn The DN for which the entry ID is desired.
   * @return The entry ID, or null if the given DN is not in the DN database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  EntryID get(ReadableTransaction txn, DN dn) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);
    ByteString value = txn.read(getName(), key);
    if (value != null)
    {
      return new EntryID(value);
    }
    return null;
  }
}
