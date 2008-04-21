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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.*;

import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.Comparator;

/**
 * This class represents the DN database, or dn2id, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
public class DN2ID extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The key comparator used for the DN database.
   */
  private Comparator<byte[]> dn2idComparator;

  /**
   * Create a DN2ID instance for the DN database in a given entryContainer.
   *
   * @param name The name of the DN database.
   * @param env The JE environment.
   * @param entryContainer The entryContainer of the DN database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  DN2ID(String name, Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);

    dn2idComparator = new EntryContainer.KeyReverseComparator();
    DatabaseConfig dn2idConfig = new DatabaseConfig();

    if(env.getConfig().getReadOnly())
    {
      dn2idConfig.setReadOnly(true);
      dn2idConfig.setAllowCreate(false);
      dn2idConfig.setTransactional(false);
    }
    else if(!env.getConfig().getTransactional())
    {
      dn2idConfig.setAllowCreate(true);
      dn2idConfig.setTransactional(false);
      dn2idConfig.setDeferredWrite(true);
    }
    else
    {
      dn2idConfig.setAllowCreate(true);
      dn2idConfig.setTransactional(true);
    }

    this.dbConfig = dn2idConfig;
    this.dbConfig.setBtreeComparator(dn2idComparator.getClass());
  }

  /**
   * Create a DN database key from an entry DN.
   * @param dn The entry DN.
   * @return A DatabaseEntry containing the key.
   */
  private static DatabaseEntry DNdata(DN dn)
  {
    byte[] normDN = StaticUtils.getBytes(dn.toNormalizedString());
    return new DatabaseEntry(normDN);
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
  public boolean insert(Transaction txn, DN dn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = id.getDatabaseEntry();

    OperationStatus status;

    status = insert(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record to the DN database.  If a record with the given key already
   * exists, the record will be replaced, otherwise a new record will be
   * inserted.
   * @param txn A JE database transaction to be used for the database operation,
   * or null if none.
   * @param dn The entry DN, which is the key to the record.
   * @param id The entry ID, which is the value of the record.
   * @return true if the record was written, false if it was not written.
   * @throws DatabaseException If an error occurred while attempting to write
   * the record.
   */
  public boolean put(Transaction txn, DN dn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = id.getDatabaseEntry();

    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public boolean putRaw(Transaction txn, DatabaseEntry key, DatabaseEntry data)
       throws DatabaseException
  {
    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public boolean remove(Transaction txn, DN dn)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);

    OperationStatus status = delete(txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public EntryID get(Transaction txn, DN dn, LockMode lockMode)
       throws DatabaseException
  {
    DatabaseEntry key = DNdata(dn);
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = read(txn, key, data, LockMode.DEFAULT);
    if (status != OperationStatus.SUCCESS)
    {
      return null;
    }
    return new EntryID(data);
  }

  /**
   * Gets the comparator for records stored in this database.
   *
   * @return The comparator for records stored in this database.
   */
  public Comparator<byte[]> getComparator()
  {
    return dn2idComparator;
  }
}
