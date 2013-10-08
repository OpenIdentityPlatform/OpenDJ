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
package org.opends.server.replication.server.changelog.je;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicaDBCursor;
import org.opends.server.replication.server.changelog.je.ReplicationDB.*;

/**
 * Berkeley DB JE implementation of {@link ReplicaDBCursor}.
 */
public class JEReplicaDBCursor implements ReplicaDBCursor
{
  private UpdateMsg currentChange;
  private ReplServerDBCursor cursor;
  private JEReplicaDB replicaDB;
  private ReplicationDB db;
  private CSN lastNonNullCurrentCSN;

  /**
   * Creates a new {@link JEReplicaDBCursor}. All created cursor must be
   * released by the caller using the {@link #close()} method.
   *
   * @param db
   *          The db where the cursor must be created.
   * @param startAfterCSN
   *          The CSN after which the cursor must start.If null, start from the
   *          oldest CSN
   * @param replicaDB
   *          The associated JEReplicaDB.
   * @throws ChangelogException
   *           if a database problem happened.
   */
  public JEReplicaDBCursor(ReplicationDB db, CSN startAfterCSN,
      JEReplicaDB replicaDB) throws ChangelogException
  {
    this.db = db;
    this.replicaDB = replicaDB;
    this.lastNonNullCurrentCSN = startAfterCSN;

    try
    {
      cursor = db.openReadCursor(startAfterCSN);
    }
    catch(Exception e)
    {
      // we didn't find it in the db
      cursor = null;
    }

    if (cursor == null)
    {
      // flush the queue into the db
      replicaDB.flush();

      // look again in the db
      cursor = db.openReadCursor(startAfterCSN);
    }
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getChange()
  {
    return currentChange;
  }

  /** {@inheritDoc} */
  @Override
  public boolean next()
  {
    currentChange = cursor.next();

    if (currentChange != null)
    {
      lastNonNullCurrentCSN = currentChange.getCSN();
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
        replicaDB.flush();
        try
        {
          cursor = db.openReadCursor(lastNonNullCurrentCSN);
          currentChange = cursor.next();
          if (currentChange != null)
          {
            lastNonNullCurrentCSN = currentChange.getCSN();
          }
        }
        catch(Exception e)
        {
          currentChange = null;
        }
      }
    }
    return currentChange != null;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    synchronized (this)
    {
      if (cursor != null)
      {
        cursor.close();
        cursor = null;
      }
      this.replicaDB = null;
      this.db = null;
    }
  }

  /**
   * Called by the Gc when the object is garbage collected Release the internal
   * cursor in case the cursor was badly used and {@link #close()} was never
   * called.
   */
  @Override
  protected void finalize()
  {
    close();
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(ReplicaDBCursor o)
  {
    final CSN csn1 = getChange().getCSN();
    final CSN csn2 = o.getChange().getCSN();

    return CSN.compare(csn1, csn2);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " currentChange=" + currentChange + ""
        + replicaDB;
  }
}
