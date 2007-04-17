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
package org.opends.server.synchronization.plugin;

import java.io.IOException;
import java.io.OutputStream;


/**
 * This class creates an output stream that can be used to export entries
 * to a synchonization domain.
 */
public class SynchroLDIFOutputStream
       extends OutputStream
{
  SynchronizationDomain domain;
  String entryBuffer = "";

  /**
   * Creates a new SynchroLDIFOutputStream related to a synchronization
   * domain.
   *
   * @param domain The synchronization domain
   */
  public SynchroLDIFOutputStream(SynchronizationDomain domain)
  {
    this.domain = domain;
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
      endOfEntryIndex = ebytes.indexOf("\n\n");
      if ( endOfEntryIndex >= 0 )
      {
        endOfEntryIndex += 2;
        entryBuffer = entryBuffer + ebytes.substring(0, endOfEntryIndex);

        // Send the entry
        domain.sendEntryLines(entryBuffer);

        startOfEntryIndex = startOfEntryIndex + endOfEntryIndex;
        entryBuffer = "";
        bytesToRead -= endOfEntryIndex;
        if (bytesToRead==0)
          break;
      }
      else
      {
        entryBuffer = new String(b, startOfEntryIndex, len);
        break;
      }
    }
  }
}
