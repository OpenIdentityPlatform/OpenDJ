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
package org.opends.server.synchronization.protocol;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * This message is part of the synchronization protocol.
 * This message is sent by a server to one or several other servers and
 * contain one entry to be sent over the protocol in the context of
 * an import/export over the protocol.
 */
public class EntryMessage extends RoutableMessage implements
    Serializable
{
  private static final long serialVersionUID = 6116955858351992926L;

  // The byte array containing the bytes of the entry transported
  private byte[] entryByteArray;

  /**
   * Creates a new EntryMessage.
   *
   * @param sender The sender of this message.
   * @param destination The destination of this message.
   * @param entryBytes The bytes of the entry.
   */
  public EntryMessage(short sender, short destination, byte[] entryBytes)
  {
    super(sender, destination);
    this.entryByteArray = entryBytes.clone();
  }

  /**
   * Creates a new EntryMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public EntryMessage(byte[] in) throws DataFormatException
  {
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_ENTRY)
        throw new DataFormatException("input is not a valid ServerStart msg");
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderIDString = new String(in, pos, length, "UTF-8");
      this.senderID = Short.valueOf(senderIDString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String destinationString = new String(in, pos, length, "UTF-8");
      this.destination = Short.valueOf(destinationString);
      pos += length +1;

      // entry
      length = getNextLength(in, pos);
      this.entryByteArray = new byte[length];
      for (int i=0; i<length; i++)
      {
        entryByteArray[i] = in[pos+i];
      }
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Returns the entry bytes.
   * @return The entry bytes.
   */
  public byte[] getEntryBytes()
  {
    return entryByteArray;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    try {
      byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
      byte[] destinationBytes = String.valueOf(destination).getBytes("UTF-8");
      byte[] entryBytes = entryByteArray;

      int length = 1 + senderBytes.length +
                   1 + destinationBytes.length +
                   1 + entryBytes.length + 1;

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_ENTRY;
      int pos = 1;

      pos = addByteArray(senderBytes, resultByteArray, pos);
      pos = addByteArray(destinationBytes, resultByteArray, pos);
      pos = addByteArray(entryBytes, resultByteArray, pos);
      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }
}
