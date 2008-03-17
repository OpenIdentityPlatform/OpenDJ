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
import org.opends.messages.Message;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.JebMessages.*;

import com.sleepycat.je.*;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * Represents the database containing the LDAP entries. The database key is
 * the entry ID and the value is the entry contents.
 *
 */
public class ID2Entry extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Parameters for compression and encryption.
   */
  private DataConfig dataConfig;

  /**
   * Create a new ID2Entry object.
   *
   * @param name The name of the entry database.
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   * @param env The JE Environment.
   * @param entryContainer The entryContainer of the entry database.
   * @throws DatabaseException If an error occurs in the JE database.
   *
   */
  ID2Entry(String name, DataConfig dataConfig,
           Environment env, EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);
    this.dataConfig = dataConfig;

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
   * Convert an entry to its database format.
   *
   * @param entry The LDAP entry to be converted.
   * @return The database entry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public DatabaseEntry entryData(Entry entry)
          throws DirectoryException
  {
    byte[] entryBytes;
    entryBytes = JebFormat.entryToDatabase(entry, dataConfig);
    return new DatabaseEntry(entryBytes);
  }

  /**
   * Insert a record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was inserted, false if a record with that
   *         ID already existed.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public boolean insert(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException, DirectoryException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = entryData(entry);

    OperationStatus status;
    status = insert(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a record in the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @param entry The LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the entry.
   */
  public boolean put(Transaction txn, EntryID id, Entry entry)
       throws DatabaseException, DirectoryException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = entryData(entry);

    OperationStatus status;
    status = put(txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Write a pre-formatted record into the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param key The key containing a pre-formatted entry ID.
   * @param data The data value containing a pre-formatted LDAP entry.
   * @return true if the entry was written, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
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
   * Remove a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The entry ID which forms the key.
   * @return true if the entry was removed, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean remove(Transaction txn, EntryID id)
       throws DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();

    OperationStatus status = delete(txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
  }

  /**
   * Fetch a record from the entry database.
   *
   * @param txn The database transaction or null if none.
   * @param id The desired entry ID which forms the key.
   * @return The requested entry, or null if there is no such record.
   * @throws JebException If an error occurs in the JE backend.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public Entry get(Transaction txn, EntryID id)
       throws JebException, DatabaseException
  {
    DatabaseEntry key = id.getDatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    OperationStatus status;
    status = read(txn, key, data,
                                 LockMode.DEFAULT);

    if (status != OperationStatus.SUCCESS)
    {
      return null;
    }

    byte[] entryBytes = data.getData();
    byte entryVersion = JebFormat.getEntryVersion(entryBytes);

    //Try to decode the entry based on the version number. On later versions,
    //a case could be written to upgrade entries if it is not the current
    //version
    Entry entry = null;
    switch(entryVersion)
    {
      case JebFormat.FORMAT_VERSION :
        try
        {
          entry = JebFormat.entryFromDatabase(entryBytes,
                       entryContainer.getRootContainer().getCompressedSchema());
        }
        catch (Exception e)
        {
          Message message = ERR_JEB_ENTRY_DATABASE_CORRUPT.get(id.toString());
          throw new JebException(message);
        }
        break;

      //case 0x00                     :
      //  Call upgrade method? Call 0x00 decode method?
      default   :
        Message message =
            ERR_JEB_INCOMPATIBLE_ENTRY_VERSION.get(id.toString(), entryVersion);
        throw new JebException(message);
    }

    if (entry != null)
    {
      entry.processVirtualAttributes();
    }

    return entry;
  }

  /**
   * Set the desired compression and encryption options for data
   * stored in the entry database.
   *
   * @param dataConfig The desired compression and encryption options for data
   * stored in the entry database.
   */
  public void setDataConfig(DataConfig dataConfig)
  {
    this.dataConfig = dataConfig;
  }
}
