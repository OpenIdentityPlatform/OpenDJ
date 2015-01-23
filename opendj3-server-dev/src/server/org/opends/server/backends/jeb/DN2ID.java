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
package org.opends.server.backends.jeb;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.server.backends.jeb.JebFormat.*;

import java.util.Comparator;

import org.opends.server.types.DN;

import com.sleepycat.je.*;

/**
 * This class represents the DN database, or dn2id, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
public class DN2ID extends DatabaseContainer
{
  /** The key comparator used for the DN database. */
  private final Comparator<byte[]> comparator;
  private final int prefixRDNComponents;

  /**
   * Create a DN2ID instance for the DN database in a given entryContainer.
   *
   * @param name The name of the DN database.
   * @param env The JE environment.
   * @param entryContainer The entryContainer of the DN database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  @SuppressWarnings("unchecked")
  DN2ID(String name, Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);

    comparator = new AttributeIndex.KeyComparator();
    prefixRDNComponents = entryContainer.getBaseDN().size();

    this.dbConfig = JEBUtils.toDatabaseConfigNoDuplicates(env);
    this.dbConfig.setKeyPrefixing(true);
    this.dbConfig.setBtreeComparator((Class<? extends Comparator<byte[]>>) comparator.getClass());
  }

  /**
   * Insert a new record into the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @return true if the record was inserted, false if a record with that key
   * already exists.
   * @throws DatabaseException If an error occurred while attempting to insert
   * the new record.
   */
  boolean insert(Transaction txn, DN dn, EntryID id) throws DatabaseException
  {
    DatabaseEntry key = new DatabaseEntry(dnToDNKey(dn, prefixRDNComponents));
    DatabaseEntry data = id.getDatabaseEntry();

    return insert(txn, key, data) == SUCCESS;
  }

  /**
   * Write a record to the DN database, where the key and value are already
   * formatted.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param key A DatabaseEntry containing the record key.
   * @param data A DatabaseEntry containing the record value.
   * @return true if the record was written, false if it was not written.
   * @throws DatabaseException If an error occurred while attempting to write
   * the record.
   */
  @Override
  public OperationStatus put(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException
  {
    return super.put(txn, key, data);
  }

  /**
   * Remove a record from the DN database.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @return true if the record was removed, false if it was not removed.
   * @throws DatabaseException If an error occurred while attempting to remove
   * the record.
   */
  boolean remove(Transaction txn, DN dn) throws DatabaseException
  {
    DatabaseEntry key = new DatabaseEntry(dnToDNKey(dn, prefixRDNComponents));

    return delete(txn, key) == SUCCESS;
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn A JE database transaction to be used for the database read, or
   * null if none is required.
   * @param dn The DN for which the entry ID is desired.
   * @param lockMode The JE locking mode to be used for the read.
   * @return The entry ID, or null if the given DN is not in the DN database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public EntryID get(Transaction txn, DN dn, LockMode lockMode) throws DatabaseException
  {
    DatabaseEntry key = new DatabaseEntry(dnToDNKey(dn, prefixRDNComponents));
    DatabaseEntry data = new DatabaseEntry();

    if (read(txn, key, data, DEFAULT) == SUCCESS)
    {
      return new EntryID(data);
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public OperationStatus read(Transaction txn, DatabaseEntry key, DatabaseEntry data, LockMode lockMode)
  {
    return super.read(txn, key, data, lockMode);
  }

  /**
   * Gets the comparator for records stored in this database.
   *
   * @return The comparator for records stored in this database.
   */
  public Comparator<byte[]> getComparator()
  {
    return comparator;
  }
}
