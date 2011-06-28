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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.server.DraftCNDB.*;
import org.opends.server.types.DebugLogLevel;

import com.sleepycat.je.DatabaseException;

/**
 * This class allows to iterate through the changes received from a given
 * LDAP Server Identifier.
 */
public class DraftCNDbIterator
{
  private static final DebugTracer TRACER = getTracer();
  private DraftCNDBCursor draftCNDbCursor = null;

  /**
   * Creates a new ReplicationIterator.
   * All created iterator must be released by the caller using the
   * releaseCursor() method.
   *
   * @param db           The db where the iterator must be created.
   * @param startDraftCN The draft CN  after which the iterator
   *                     must start.
   * @throws Exception   If there is no other change to push after change
   *                     with changeNumber number.
   * @throws DatabaseException If a database problem happened.
   */
  public DraftCNDbIterator(DraftCNDB db, int startDraftCN)
  throws Exception, DatabaseException
  {
    draftCNDbCursor = db.openReadCursor(startDraftCN);
    if (draftCNDbCursor == null)
    {
      throw new Exception("no new change");
    }
  }

  /**
   * Getter for the serviceID field.
   * @return The service ID.
   */
  public String getServiceID()
  {
    try
    {
      return this.draftCNDbCursor.currentServiceID();
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      return null;
    }
  }

  /**
   * Getter for the replication change number field.
   * @return The replication change number field.
   */
  public ChangeNumber getChangeNumber()
  {
    try
    {
      ChangeNumber cn = this.draftCNDbCursor.currentChangeNumber();
      return cn;
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      return null;
    }
  }

  /**
   * Getter for the draftCN field.
   * @return The draft CN field.
   */
  public int getDraftCN()
  {
    ReplicationDraftCNKey sk = (ReplicationDraftCNKey) draftCNDbCursor.getKey();
    int currentSeqnum = sk.getDraftCN();
    return currentSeqnum;
  }

  /**
   * Skip to the next record of the database.
   * @return true if has next, false elsewhere
   * @throws Exception When exception raised.
   * @throws DatabaseException When database exception raised.
   */
  public boolean next()
  throws Exception, DatabaseException
  {
    return draftCNDbCursor.next();
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
      if (draftCNDbCursor != null)
      {
        draftCNDbCursor.close();
        draftCNDbCursor = null;
      }
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
