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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

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
   * @param serverID The sender server of this message.
   * @param i The server or servers targeted by this message.
   */
  public DoneMsg(int serverID, int i)
  {
    super(serverID, i);
  }

  /**
   * Creates a new message by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the message,
   * @throws DataFormatException If the in does not contain a properly,
   *                             encoded message.
   */
  DoneMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_DONE)
    {
      throw new DataFormatException("input is not a valid DoneMessage");
    }
    this.senderID = scanner.nextIntUTF8();
    this.destination = scanner.nextIntUTF8();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_DONE);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    return builder.toByteArray();
  }
}
