/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;


import java.io.IOException;
import java.io.OutputStream;

import org.opends.server.util.ServerConstants;

/**
 * This class creates an output stream that can be used to export entries
 * to a synchronization domain.
 */
public class ReplLDIFOutputStream
       extends OutputStream
{
  /** The number of entries to be exported. */
  private final long numEntries;

  /** The current number of entries exported. */
  private long numExportedEntries;
  private String entryBuffer = "";

  /** The checksum for computing the generation id. */
  private final GenerationIdChecksum checkSum = new GenerationIdChecksum();

  /**
   * Creates a new ReplLDIFOutputStream related to a replication
   * domain.
   *
   * @param numEntries The max number of entry to process.
   */
  public ReplLDIFOutputStream(long numEntries)
  {
    this.numEntries = numEntries;
  }

  @Override
  public void write(int i) throws IOException
  {
    throw new IOException("Invalid call");
  }

  /**
   * Get the value of the underlying checksum.
   * @return The value of the underlying checksum
   */
  public long getChecksumValue()
  {
    return checkSum.getValue();
  }

  @Override
  public void write(byte b[], int off, int len) throws IOException
  {
    String ebytes = entryBuffer;
    entryBuffer = "";

    ebytes = ebytes + new String(b, off, len);
    int endIndex = ebytes.length();

    while (true)
    {
      // if we have the bytes for an entry, let's make an entry and send it
      int endOfEntryIndex = ebytes.indexOf(ServerConstants.EOL + ServerConstants.EOL);
      if (endOfEntryIndex < 0)
      {
        // a next call to us will provide more bytes to make an entry
        entryBuffer = entryBuffer.concat(ebytes);
        break;
      }

      endOfEntryIndex += 2;
      entryBuffer = ebytes.substring(0, endOfEntryIndex);

      // Send the entry
      if (numEntries > 0 && getNumExportedEntries() > numEntries)
      {
        // This outputstream has reached the total number
        // of entries to export.
        throw new IOException();
      }

      // Add the entry bytes to the checksum
      byte[] entryBytes = entryBuffer.getBytes();
      checkSum.update(entryBytes, 0, entryBytes.length);

      numExportedEntries++;
      entryBuffer = "";

      if (endIndex == endOfEntryIndex)
      {
        // no more data to process
        break;
      }
      // loop to the data of the next entry
      ebytes = ebytes.substring(endOfEntryIndex, endIndex);
      endIndex = ebytes.length();
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
