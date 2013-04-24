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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationDB.ReplServerDBCursor;

import com.sleepycat.je.DatabaseException;

/**
 * This class allows to iterate through the changes received from a given
 * LDAP Server Identifier.
 */
public class ReplicationIterator
{
  private UpdateMsg currentChange = null;
  private ReplServerDBCursor cursor = null;
  private DbHandler dbh;
  private ReplicationDB db;
  ChangeNumber lastNonNullCurrentCN;

  /**
   * Creates a new ReplicationIterator.
   * All created iterator must be released by the caller using the
   * releaseCursor() method.
   *
   * @param id the Identifier of the server on which the iterator applies.
   * @param db The db where the iterator must be created.
   * @param changeNumber The ChangeNumber after which the iterator must start.
   * @param dbh The associated DbHandler.
   * @throws Exception If there is no other change to push after change
   *         with changeNumber number.
   * @throws DatabaseException if a database problem happened.
   */
  public ReplicationIterator(
          int id, ReplicationDB db, ChangeNumber changeNumber, DbHandler dbh)
          throws Exception, DatabaseException
  {
    this.db = db;
    this.dbh = dbh;
    this.lastNonNullCurrentCN = changeNumber;

    try
    {
      cursor = db.openReadCursor(changeNumber);
    }
    catch(Exception e)
    {
      // we didn't find it in the db
      cursor = null;
    }

    if (cursor == null)
    {
      // flush the queue into the db
      dbh.flush();

      // look again in the db
      cursor = db.openReadCursor(changeNumber);
      if (cursor == null)
      {
        throw new Exception("no new change");
      }
    }
  }

  /**
   * Get the UpdateMsg where the iterator is currently set.
   * @return The UpdateMsg where the iterator is currently set.
   */
  public UpdateMsg getChange()
  {
    return currentChange;
  }

  /**
   * Go to the next change in the ReplicationDB or in the server Queue.
   * @return false if the iterator is already on the last change before
   *         this call.
   */
  public boolean next()
  {
    boolean hasNext;

    currentChange = cursor.next(); // can return null

    if (currentChange != null)
    {
      lastNonNullCurrentCN = currentChange.getChangeNumber();
      hasNext = true;
    }
    else
    {
      synchronized (this)
      {
        if (cursor != null)
        {
          cursor.close();
          cursor = null;
        }
        dbh.flush();
        try
        {
          cursor = db.openReadCursor(lastNonNullCurrentCN);
          currentChange = cursor.next();
          if (currentChange != null)
          {
            lastNonNullCurrentCN = currentChange.getChangeNumber();
            hasNext = true;
          }
          else
          {
            hasNext = false;
          }
        }
        catch(Exception e)
        {
          currentChange = null;
          hasNext = false;
        }
      }
    }
    return hasNext;
  }

  /**
   * Release the resources and locks used by this Iterator.
   * This method must be called when the iterator is no longer used.
   * Failure to do it could cause DB deadlock.
   */
  public void releaseCursor()
  {
    synchronized (this)
    {
      if (cursor != null)
      {
        cursor.close();
        cursor = null;
      }
      this.dbh = null;
      this.db = null;
    }
  }

  /**
   * Called by the Gc when the object is garbage collected
   * Release the cursor in case the iterator was badly used and releaseCursor
   * was never called.
   */
  protected void finalize()
  {
    releaseCursor();
  }
}
