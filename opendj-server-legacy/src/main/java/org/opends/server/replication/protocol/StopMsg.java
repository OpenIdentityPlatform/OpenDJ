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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to tell a peer the communication will be
 * terminated.
 */
public class StopMsg extends ReplicationMsg
{
  /**
   * Creates a message.
   */
  public StopMsg()
  {
  }

  /**
   * Creates a new message by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the message,
   * @throws DataFormatException If the in does not contain a properly,
   *                             encoded message.
   */
  public StopMsg(byte[] in) throws DataFormatException
  {
    // First byte is the type
    if (in[0] != MSG_TYPE_STOP)
    {
      throw new DataFormatException("input is not a valid Stop message: " + in[0]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    return new byte[] { MSG_TYPE_STOP };
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName();
  }
}
