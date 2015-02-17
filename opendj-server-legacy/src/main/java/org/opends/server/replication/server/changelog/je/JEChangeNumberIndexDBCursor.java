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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.replication.server.changelog.je.DraftCNDB.*;

/**
 * This class allows to iterate through the changes comming from the change number index DB.
 *
 * \@NotThreadSafe
 */
public class JEChangeNumberIndexDBCursor implements
    DBCursor<ChangeNumberIndexRecord>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private DraftCNDBCursor draftCNDbCursor;

  /**
   * As underlying cursor is already pointing to a record at start, this
   * indicator allow to shift the pointed record at initialization time.
   */
  private boolean isInitialized;

  /**
   * Creates a new DB Cursor. All created iterator must be released by
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
      return isInitialized ? draftCNDbCursor.currentRecord() : null;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (draftCNDbCursor != null)
    {
      if (!isInitialized)
      {
        isInitialized = true;
        return draftCNDbCursor.currentRecord() != null;
      }
      else
      {
        return draftCNDbCursor.next();
      }
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
