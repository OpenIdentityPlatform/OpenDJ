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
 * Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.CSN;

import static org.opends.server.replication.protocol.ByteArrayBuilder.*;

/**
 * Class that define messages sent by a replica (DS) to the replication server
 * (RS) to let the RS know the date at which a replica went offline.
 */
public class ReplicaOfflineMsg extends UpdateMsg
{

  /**
   * Constructor of a replica offline message providing the offline timestamp in
   * a CSN.
   *
   * @param offlineCSN
   *          the provided offline CSN
   */
  public ReplicaOfflineMsg(final CSN offlineCSN)
  {
    super(offlineCSN, new byte[0]);
  }

  /**
   * Creates a message by deserializing it from the provided byte array.
   *
   * @param in
   *          The provided byte array.
   * @throws DataFormatException
   *           When an error occurs during decoding .
   */
  public ReplicaOfflineMsg(byte[] in) throws DataFormatException
  {
    try
    {
      final ByteArrayScanner scanner = new ByteArrayScanner(in);
      final byte msgType = scanner.nextByte();
      if (msgType != MSG_TYPE_REPLICA_OFFLINE)
      {
        throw new DataFormatException("input is not a valid "
            + getClass().getSimpleName() + " message: " + msgType);
      }
      protocolVersion = scanner.nextShort();
      csn = scanner.nextCSN();

      if (!scanner.isEmpty())
      {
        throw new DataFormatException(
            "Did not expect to find more bytes to read for "
                + getClass().getSimpleName());
      }
    }
    catch (RuntimeException e)
    {
      // Index out of bounds, bad format, etc.
      throw new DataFormatException("byte[] is not a valid "
          + getClass().getSimpleName());
    }
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion < ProtocolVersion.REPLICATION_PROTOCOL_V8)
    {
      return null;
    }
    final ByteArrayBuilder builder = new ByteArrayBuilder(size());
    builder.appendByte(MSG_TYPE_REPLICA_OFFLINE);
    builder.appendShort(protocolVersion);
    builder.appendCSN(csn);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    return bytes(1) + shorts(1) + csns(1);
  }

  /** {@inheritDoc} */
  @Override
  public boolean contributesToDomainState()
  {
    return false; // replica offline msg MUST NOT update the ds-sync-state
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " offlineCSN=" + csn;
  }
}
