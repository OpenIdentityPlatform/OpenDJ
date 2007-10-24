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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.io.OutputStream;

import org.opends.server.util.ServerConstants;

/**
 * This class creates an output stream that can be used to export entries
 * to a synchonization domain.
 */
public class ReplLDIFOutputStream
       extends OutputStream
{
  // The synchronization domain on which the export is done
  ReplicationDomain domain;

  // The number of entries to be exported
  long numEntries;

  // The current number of entries exported
  private long numExportedEntries;
  String entryBuffer = "";

  /**
   * Creates a new ReplLDIFOutputStream related to a replication
   * domain.
   *
   * @param domain The replication domain
   * @param numEntries The max number of entry to process.
   */
  public ReplLDIFOutputStream(ReplicationDomain domain, long numEntries)
  {
    this.domain = domain;
    this.numEntries = numEntries;
  }

  /**
   * {@inheritDoc}
   */
  public void write(int i) throws IOException
  {
    throw new IOException("Invalid call");
  }

  /**
   * {@inheritDoc}
   */
  public void write(byte b[], int off, int len) throws IOException
  {
    int endOfEntryIndex;
    int startOfEntryIndex = off;
    int bytesToRead = len;

    while (true)
    {
      // if we have the bytes for an entry, let's make an entry and send it
      String ebytes = new String(b,startOfEntryIndex,bytesToRead);
      endOfEntryIndex = ebytes.indexOf(ServerConstants.EOL +
          ServerConstants.EOL);

      if ( endOfEntryIndex >= 0 )
      {

        endOfEntryIndex += 2;
        entryBuffer = entryBuffer + ebytes.substring(0, endOfEntryIndex);

        // Send the entry
        if ((numEntries>0) && (getNumExportedEntries() > numEntries))
        {
          // This outputstream has reached the total number
          // of entries to export.
          throw(new IOException());
        }
        domain.exportLDIFEntry(entryBuffer);
        numExportedEntries++;

        startOfEntryIndex = startOfEntryIndex + endOfEntryIndex;
        entryBuffer = "";
        bytesToRead -= endOfEntryIndex;
        if (bytesToRead==0)
          break;
      }
      else
      {
        entryBuffer = new String(b, startOfEntryIndex, bytesToRead);
        break;
      }
    }
  }

  /**
   * Return the number of exported entries.
   * @return the numExportedEntries
   */
  public long getNumExportedEntries() {
    return numExportedEntries;
  }
}
