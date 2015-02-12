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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * This message is used by LDAP server or by Replication Servers to
 * update the send window of the remote entities.
 *
 * A receiving entity should create such a message with a given credit
 * when it wants to open the send window of the remote entity.
 * A LDAP or Replication Server should increase its send window when receiving
 * such a message.
 */
public class InitializeRcvAckMsg extends RoutableMsg
{
  private final int numAck;

  /**
   * Create a new message..
   *
   * @param sender The server ID of the server that send this message.
   * @param destination The destination server or servers of this message.
   * @param numAck The number of acknowledged messages.
   *               The window will be increase by this credit number.
   */
  public InitializeRcvAckMsg(int sender, int destination, int numAck)
  {
    super(sender, destination);
    this.numAck = numAck;
  }

  /**
   * Creates a new message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the message.
   */
  InitializeRcvAckMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    if (scanner.nextByte() != MSG_TYPE_INITIALIZE_RCV_ACK)
    {
      throw new DataFormatException("input is not a valid "
          + getClass().getCanonicalName());
    }

    senderID = scanner.nextIntUTF8();
    destination = scanner.nextIntUTF8();
    numAck = scanner.nextIntUTF8();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_INITIALIZE_RCV_ACK);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    builder.appendIntUTF8(numAck);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "=[" +
      " sender=" + this.senderID +
      " destination=" + this.destination +
      " msgID=" + this.numAck + "]";
  }

  /**
   * Get the number of message acknowledged by this message.
   *
   * @return the number of message acknowledged by this message.
   */
  public int getNumAck()
  {
    return numAck;
  }
}
