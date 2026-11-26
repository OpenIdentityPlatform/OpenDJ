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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013-2014 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
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
  /** Identifies a preparation acknowledgement, which carries no entry credit. */
  private final String initializationAttemptId;

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
    this(sender, destination, numAck, null);
  }

  /**
   * Acknowledges readiness to receive the entries for a particular initialization.
   * @param sender the prepared importer
   * @param destination the exporter
   * @param initializationAttemptId the attempt identifier from the initialization header
   */
  public InitializeRcvAckMsg(int sender, int destination, String initializationAttemptId)
  {
    this(sender, destination, 0, initializationAttemptId);
  }

  private InitializeRcvAckMsg(int sender, int destination, int numAck, String initializationAttemptId)
  {
    super(sender, destination);
    this.numAck = numAck;
    this.initializationAttemptId = initializationAttemptId;
  }

  /**
   * Creates a new message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @param protocolVersion the session's negotiated protocol version
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the message.
   */
  InitializeRcvAckMsg(byte[] in, short protocolVersion) throws DataFormatException
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
    initializationAttemptId = protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V9 ? scanner.nextString() : null;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (initializationAttemptId != null && protocolVersion < ProtocolVersion.REPLICATION_PROTOCOL_V9)
    {
      return null;
    }
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_INITIALIZE_RCV_ACK);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    builder.appendIntUTF8(numAck);
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V9)
    {
      builder.appendString(initializationAttemptId);
    }
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

  /** @return the preparation acknowledgement's attempt identifier, or null for an entry acknowledgement */
  public String getInitializationAttemptId()
  {
    return initializationAttemptId;
  }
}
