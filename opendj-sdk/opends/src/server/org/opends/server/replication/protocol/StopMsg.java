/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
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
      throw new DataFormatException("input is not a valid Stop message: " +
        in[0]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    return new byte[]
      {
        MSG_TYPE_STOP
      };
  }
}
