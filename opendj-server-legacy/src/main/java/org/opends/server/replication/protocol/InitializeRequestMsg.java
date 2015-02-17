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

import org.opends.server.types.DN;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to another server in order to
 * request this other server to do an export to the server sender
 * of this message.
 */
public class InitializeRequestMsg extends RoutableMsg
{
  private final DN baseDN;
  private int initWindow;

  /**
   * Creates a InitializeRequestMsg message.
   *
   * @param baseDN      the base DN of the replication domain.
   * @param destination destination of this message
   * @param serverID    serverID of the server that will send this message
   * @param initWindow  initialization window for flow control
   */
  public InitializeRequestMsg(DN baseDN, int serverID, int destination,
      int initWindow)
  {
    super(serverID, destination);
    this.baseDN = baseDN;
    this.initWindow = initWindow; // V4
  }

  /**
   * Creates a new InitializeRequestMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the message
   * @param version The protocol version to use to decode the msg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded InitializeMessage.
   */
  InitializeRequestMsg(byte[] in, short version) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_INITIALIZE_REQUEST)
    {
      throw new DataFormatException(
          "input is not a valid InitializeRequestMessage");
    }
    baseDN = scanner.nextDN();
    senderID = scanner.nextIntUTF8();
    destination = scanner.nextIntUTF8();

    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      initWindow = scanner.nextIntUTF8();
    }
  }

  /**
   * Get the base DN from this InitializeRequestMsg.
   *
   * @return the base DN from this InitializeRequestMsg.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short version)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_INITIALIZE_REQUEST);
    builder.appendDN(baseDN);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      builder.appendIntUTF8(initWindow);
    }
    return builder.toByteArray();
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return "InitializeRequestMessage: baseDN=" + baseDN + " senderId="
       + senderID + " destination=" + destination + " initWindow=" + initWindow;
  }

  /**
   * Return the initWindow value.
   * @return the initWindow.
   */
  public int getInitWindow()
  {
    return this.initWindow;
  }

  /**
   * Set the initWindow value.
   * @param initWindow The initialization window.
   */
  public void setInitWindow(int initWindow)
  {
    this.initWindow = initWindow;
  }
}
