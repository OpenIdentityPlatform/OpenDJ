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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.forgerock.opendj.ldap.DN;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to one or several servers as the
 * first message of an export, before sending the entries.
 */
public class InitializeTargetMsg extends RoutableMsg
{
  private final DN baseDN;

  /** Specifies the number of entries expected to be exported. */
  private final long entryCount;

  /**
   * Specifies the serverID of the server that requested this export to happen.
   * It allows a server that previously sent an InitializeRequestMessage to know
   * that the current message is related to its own request.
   */
  private final int requestorID;

  private int initWindow;

  /**
   * Creates a InitializeTargetMsg.
   *
   * @param baseDN     The base DN for which the InitializeMessage is created.
   * @param serverID   The serverID of the server that sends this message.
   * @param destination     The destination of this message.
   * @param requestorID    The server that initiates this export.
   * @param entryCount The count of entries that will be sent.
   * @param initWindow the initialization window.
   */
  public InitializeTargetMsg(DN baseDN, int serverID,
      int destination, int requestorID, long entryCount, int initWindow)
  {
    super(serverID, destination);
    this.requestorID = requestorID;
    this.baseDN = baseDN;
    this.entryCount = entryCount;
    this.initWindow = initWindow; // V4
  }

  /**
   * Creates an InitializeTargetMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the message
   * @param version The protocol version to use to decode the msg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded InitializeMessage.
   */
  InitializeTargetMsg(byte[] in, short version) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_INITIALIZE_TARGET)
    {
      throw new DataFormatException(
          "input is not a valid InitializeDestinationMessage");
    }
    destination = scanner.nextIntUTF8();
    baseDN = scanner.nextDN();
    senderID = scanner.nextIntUTF8();
    requestorID = scanner.nextIntUTF8();
    entryCount = scanner.nextLongUTF8();

    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      initWindow = scanner.nextIntUTF8();
    }
  }

  /**
   * Get the number of entries expected to be sent during the export.
   * @return the entry count
   */
  public long getEntryCount()
  {
    return this.entryCount;
  }

  /**
   * Get the serverID of the server that initiated the export.
   * Roughly it is the server running the task,
   * - the importer for the Initialize task,
   * - the exporter for the InitializeRemote task.
   * @return the serverID
   */
  public long getInitiatorID()
  {
    return this.requestorID;
  }

  /**
   * Get the base DN of the domain.
   *
   * @return the base DN
   */
  public DN getBaseDN()
  {
    return this.baseDN;
  }

  /**
   * Get the initializationWindow.
   *
   * @return the initialization window.
   */
  public int getInitWindow()
  {
    return this.initWindow;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short version)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_INITIALIZE_TARGET);
    builder.appendIntUTF8(destination);
    builder.appendDN(baseDN);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(requestorID);
    builder.appendLongUTF8(entryCount);
    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      builder.appendIntUTF8(initWindow);
    }
    return builder.toByteArray();
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
