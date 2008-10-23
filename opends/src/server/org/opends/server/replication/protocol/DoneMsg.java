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
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to one or several other servers after the
 * last entry sent in the context of a total update and signals to the server
 * that receives it that the export is now finished.
 */
public class DoneMsg extends RoutableMsg
{
  /**
   * Creates a message.
   *
   * @param sender The sender server of this message.
   * @param destination The server or servers targetted by this message.
   */
  public DoneMsg(short sender, short destination)
  {
    super(sender, destination);
  }

  /**
   * Creates a new message by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the message,
   * @throws DataFormatException If the in does not contain a properly,
   *                             encoded message.
   */
  public DoneMsg(byte[] in) throws DataFormatException
  {
    super();
    try
    {
      // First byte is the type
      if (in[0] != MSG_TYPE_DONE)
        throw new DataFormatException("input is not a valid DoneMessage");
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderString = new String(in, pos, length, "UTF-8");
      this.senderID = Short.valueOf(senderString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String destinationString = new String(in, pos, length, "UTF-8");
      this.destination = Short.valueOf(destinationString);
      pos += length +1;

    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    try
    {
      byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
      byte[] destinationBytes = String.valueOf(destination).getBytes("UTF-8");

      int length = 1 + senderBytes.length + 1
                     + destinationBytes.length + 1;

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_DONE;
      int pos = 1;

      /* put the sender */
      pos = addByteArray(senderBytes, resultByteArray, pos);

      /* put the destination */
      pos = addByteArray(destinationBytes, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }
}
