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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to one or several other servers and
 * contain one entry to be sent over the protocol in the context of
 * an import/export over the protocol.
 */
public class EntryMsg extends RoutableMsg
{
  // The byte array containing the bytes of the entry transported
  private byte[] entryByteArray;
  private int msgId = -1; // from V4

  /**
   * Creates a new EntryMsg.
   *
   * @param sender      The sender of this message.
   * @param destination The destination of this message.
   * @param entryBytes  The bytes of the entry.
   * @param msgId       Message counter.
   */
  public EntryMsg(
      int sender,
      int destination,
      byte[] entryBytes,
      int msgId)
  {
    super(sender, destination);
    this.entryByteArray = new byte[entryBytes.length];
    System.arraycopy(entryBytes, 0, this.entryByteArray, 0, entryBytes.length);
    this.msgId = msgId;
  }

  /**
   * Creates a new EntryMsg.
   *
   * @param serverID    The sender of this message.
   * @param i           The destination of this message.
   * @param entryBytes  The bytes of the entry.
   * @param pos         The starting Position in the array.
   * @param length      Number of array elements to be copied.
   * @param msgId       Message counter.
   */
  public EntryMsg(
      int serverID,
      int i,
      byte[] entryBytes,
      int pos,
      int length,
      int msgId)
  {
    super(serverID, i);
    this.entryByteArray = new byte[length];
    System.arraycopy(entryBytes, pos, this.entryByteArray, 0, length);
    this.msgId = msgId;
  }

  /**
   * Creates a new EntryMsg from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @param version The protocol version to use to decode the msg
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public EntryMsg(byte[] in, short version) throws DataFormatException
  {
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_ENTRY)
        throw new DataFormatException("input is not a valid " +
            this.getClass().getCanonicalName());
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderIDString = new String(in, pos, length, "UTF-8");
      this.senderID = Integer.valueOf(senderIDString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String destinationString = new String(in, pos, length, "UTF-8");
      this.destination = Integer.valueOf(destinationString);
      pos += length +1;

      // msgCnt
      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        // msgCnt
        length = getNextLength(in, pos);
        String msgcntString = new String(in, pos, length, "UTF-8");
        this.msgId = Integer.valueOf(msgcntString);
        pos += length +1;
      }

      // data
      length = in.length - (pos + 1);
      this.entryByteArray = new byte[length];
      System.arraycopy(in, pos, entryByteArray, 0, length);
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
  public byte[] getBytes(short version)
  {
    try {
      byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
      byte[] destinationBytes = String.valueOf(destination).getBytes("UTF-8");
      byte[] msgCntBytes = null;
      byte[] entryBytes = entryByteArray;

      int length = 1 + senderBytes.length +
                   1 + destinationBytes.length +
                   1 + entryBytes.length + 1;

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        msgCntBytes = String.valueOf(msgId).getBytes("UTF-8");
        length += (1 + msgCntBytes.length);
      }

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_ENTRY;
      int pos = 1;

      pos = addByteArray(senderBytes, resultByteArray, pos);
      pos = addByteArray(destinationBytes, resultByteArray, pos);
      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        pos = addByteArray(msgCntBytes, resultByteArray, pos);
      pos = addByteArray(entryBytes, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * Return the msg id.
   * @return The msg id.
   */
  public int getMsgId()
  {
    return this.msgId;
  }

  /**
   * Set the msg id.
   * @param msgId The msg id.
   */
  public void setMsgId(int msgId)
  {
    this.msgId = msgId;
  }
}
