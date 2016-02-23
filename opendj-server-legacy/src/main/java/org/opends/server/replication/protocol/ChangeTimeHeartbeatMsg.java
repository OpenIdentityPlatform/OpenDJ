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
 * Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.CSN;

import static org.opends.server.replication.protocol.ByteArrayBuilder.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;

/**
 * Class that define messages sent by a replication domain (DS) to the
 * replication server to let the RS know the DS current change time.
 */
public class ChangeTimeHeartbeatMsg extends ReplicationMsg
{

  /**
   * The CSN containing the change time.
   */
  private final CSN csn;

  /**
   * Constructor of a Change Time Heartbeat message providing the change time
   * value in a CSN.
   *
   * @param csn
   *          The provided CSN.
   */
  public ChangeTimeHeartbeatMsg(CSN csn)
  {
    this.csn = csn;
  }

  /**
   * Get a CSN with the transmitted change time.
   *
   * @return the CSN
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Creates a message from a provided byte array.
   *
   * @param in
   *          The provided byte array.
   * @param version
   *          The version of the protocol to use to decode the msg.
   * @throws DataFormatException
   *           When an error occurs.
   */
  public ChangeTimeHeartbeatMsg(byte[] in, short version)
      throws DataFormatException
  {
    try
    {
      final ByteArrayScanner scanner = new ByteArrayScanner(in);
      final byte msgType = scanner.nextByte();
      if (msgType != MSG_TYPE_CT_HEARTBEAT)
      {
        throw new DataFormatException("input is not a valid "
            + getClass().getSimpleName() + " message: " + msgType);
      }

      csn = version >= REPLICATION_PROTOCOL_V7
          ? scanner.nextCSN()
          : scanner.nextCSNUTF8();

      if (!scanner.isEmpty())
      {
        throw new DataFormatException(
            "Did not expect to find more bytes to read for "
            + getClass().getSimpleName() + " message.");
      }
    }
    catch (RuntimeException e)
    {
      // Index out of bounds, bad format, etc.
      throw new DataFormatException("byte[] is not a valid CT_HEARTBEAT msg");
    }
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion < ProtocolVersion.REPLICATION_PROTOCOL_V7)
    {
      ByteArrayBuilder builder = new ByteArrayBuilder(bytes(1) + csnsUTF8(1));
      builder.appendByte(MSG_TYPE_CT_HEARTBEAT);
      builder.appendCSNUTF8(csn);
      return builder.toByteArray();
    }

    final ByteArrayBuilder builder = new ByteArrayBuilder(bytes(1) + csns(1));
    builder.appendByte(MSG_TYPE_CT_HEARTBEAT);
    builder.appendCSN(csn);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + ", csn=" + csn.toStringUI();
  }

}
