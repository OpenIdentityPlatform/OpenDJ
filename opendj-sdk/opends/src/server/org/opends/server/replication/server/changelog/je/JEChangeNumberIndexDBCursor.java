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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.replication.server.changelog.je.DraftCNDB.*;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class allows to iterate through the changes received from a given
 * LDAP Server Identifier.
 */
public class JEChangeNumberIndexDBCursor implements
    DBCursor<ChangeNumberIndexRecord>
{
  private static final DebugTracer TRACER = getTracer();
  private DraftCNDBCursor draftCNDbCursor;

  /**
   * Creates a new ReplicationIterator. All created iterator must be released by
   * the caller using the {@link #close()} method.
   *
   * @param db
   *          The db where the iterator must be created.
   * @param startChangeNumber
   *          The change number after which the iterator must start.
   * @throws ChangelogException
   *           If a database problem happened.
   */
  public JEChangeNumberIndexDBCursor(DraftCNDB db, long startChangeNumber)
      throws ChangelogException
  {
    draftCNDbCursor = db.openReadCursor(startChangeNumber);
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexRecord getRecord()
  {
    try
    {
      return this.draftCNDbCursor.currentRecord();
    }
    catch (Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (draftCNDbCursor != null)
    {
      return draftCNDbCursor.next();
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
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
  @Override
  protected void finalize()
  {
    close();
  }

}
