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

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.types.DN;

/**
 * This class represents the DN database, or dn2id, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
public class DN2ID extends DatabaseContainer
{
  private final int prefixRDNComponents;

  /**
   * Create a DN2ID instance for the DN database in a given entryContainer.
   *
   * @param treeName The name of the DN database.
   * @param env The JE environment.
   * @param entryContainer The entryContainer of the DN database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  DN2ID(TreeName treeName, Storage env, EntryContainer entryContainer)
      throws StorageRuntimeException
  {
    super(treeName, env, entryContainer);

    prefixRDNComponents = entryContainer.getBaseDN().size();
  }

  /**
   * Insert a new record into the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @return true if the record was inserted, false if a record with that key
   * already exists.
   * @throws StorageRuntimeException If an error occurred while attempting to insert
   * the new record.
   */
  public boolean insert(WriteableStorage txn, DN dn, EntryID id) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);
    ByteString value = id.toByteString();

    return insert(txn, key, value);
  }

  /**
   * Write a record to the DN database.  If a record with the given key already
   * exists, the record will be replaced, otherwise a new record will be
   * inserted.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @throws StorageRuntimeException If an error occurred while attempting to write
   * the record.
   */
  public void put(WriteableStorage txn, DN dn, EntryID id) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);
    ByteString value = id.toByteString();

    put(txn, key, value);
  }

  /**
   * Write a record to the DN database, where the key and value are already
   * formatted.
   *
   * @param txn
   *          A JE database transaction to be used for the database operation,
   *          or null if none.
   * @param key
   *          A ByteString containing the record key.
   * @param value
   *          A ByteString containing the record value.
   * @throws StorageRuntimeException
   *           If an error occurred while attempting to write the record.
   */
  @Override
  public void put(WriteableStorage txn, ByteSequence key, ByteSequence value) throws StorageRuntimeException
  {
    super.put(txn, key, value);
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
  public boolean remove(WriteableStorage txn, DN dn) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);

    return delete(txn, key);
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn A JE database transaction to be used for the database read, or
   * null if none is required.
   * @param dn The DN for which the entry ID is desired.
   * @param isRMW whether the read operation is part of a larger read-modify-write operation
   * @return The entry ID, or null if the given DN is not in the DN database.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public EntryID get(ReadableStorage txn, DN dn, boolean isRMW) throws StorageRuntimeException
  {
    ByteString key = dnToDNKey(dn, prefixRDNComponents);
    ByteString value = read(txn, key, isRMW);
    if (value != null)
    {
      return new EntryID(value);
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public ByteString read(ReadableStorage txn, ByteSequence key, boolean isRMW)
  {
    return super.read(txn, key, isRMW);
  }
}
