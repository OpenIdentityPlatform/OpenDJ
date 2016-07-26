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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol. RS1 sends a
 * MonitorRequestMsg to RS2 to request its monitoring information. When RS2
 * receives a MonitorRequestMsg from RS1, RS2 responds with a MonitorMessage.
 */
public class MonitorRequestMsg extends ReplicationMsg
{
  /** The destination server or servers of this message. */
  private final int destination;
  /** The serverID of the server that sends this message. */
  private final int senderID;

  /**
   * Creates a message.
   *
   * @param serverID
   *          The sender server of this message.
   * @param destination
   *          The server or servers targeted by this message.
   */
  public MonitorRequestMsg(int serverID, int destination)
  {
    this.senderID = serverID;
    this.destination = destination;
  }

  /**
   * Creates a new message by decoding the provided byte array.
   *
   * @param in
   *          A byte array containing the encoded information for the message,
   * @throws DataFormatException
   *           If the in does not contain a properly, encoded message.
   */
  MonitorRequestMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_REPL_SERVER_MONITOR_REQUEST)
    {
      throw new DataFormatException("input is not a valid "
          + getClass().getCanonicalName());
    }
    this.senderID = scanner.nextIntUTF8();
    this.destination = scanner.nextIntUTF8();
  }

  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_REPL_SERVER_MONITOR_REQUEST);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    return builder.toByteArray();
  }

  /**
   * Get the destination.
   *
   * @return the destination
   */
  public int getDestination()
  {
    return destination;
  }

  /**
   * Get the server ID of the server that sent this message.
   *
   * @return the server id
   */
  public int getSenderID()
  {
    return senderID;
  }

  @Override
  public String toString()
  {
    return "[" + getClass().getSimpleName() + " sender=" + senderID + " destination=" + destination + "]";
  }
}
