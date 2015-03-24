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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to one or several other servers and
 * contain one entry to be sent over the protocol in the context of
 * an import/export over the protocol.
 */
public class EntryMsg extends RoutableMsg
{
  /** The byte array containing the bytes of the entry transported. */
  private final byte[] entryByteArray;
  /** From V4. */
  private int msgId = -1;

  /**
   * Creates a new EntryMsg.
   *
   * @param serverID      The sender of this message.
   * @param destination The destination of this message.
   * @param entryBytes  The bytes of the entry.
   * @param msgId       Message counter.
   */
  public EntryMsg(int serverID, int destination, byte[] entryBytes, int msgId)
  {
    this(serverID, destination, entryBytes, 0, entryBytes.length, msgId);
  }

  /**
   * Creates a new EntryMsg.
   *
   * @param serverID    The sender of this message.
   * @param destination The destination of this message.
   * @param entryBytes  The bytes of the entry.
   * @param startPos    The starting Position in the array.
   * @param length      Number of array elements to be copied.
   * @param msgId       Message counter.
   */
  public EntryMsg(int serverID, int destination, byte[] entryBytes, int startPos,
      int length, int msgId)
  {
    super(serverID, destination);
    this.entryByteArray = new byte[length];
    System.arraycopy(entryBytes, startPos, this.entryByteArray, 0, length);
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
  EntryMsg(byte[] in, short version) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_ENTRY)
    {
      throw new DataFormatException("input is not a valid "
          + getClass().getCanonicalName());
    }
    this.senderID = scanner.nextIntUTF8();
    this.destination = scanner.nextIntUTF8();
    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      this.msgId = scanner.nextIntUTF8();
    }
    this.entryByteArray = scanner.remainingBytesZeroTerminated();
  }

  /**
   * Returns the entry bytes.
   * @return The entry bytes.
   */
  public byte[] getEntryBytes()
  {
    return entryByteArray;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short version)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_ENTRY);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      builder.appendIntUTF8(msgId);
    }
    builder.appendZeroTerminatedByteArray(entryByteArray);
    return builder.toByteArray();
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
