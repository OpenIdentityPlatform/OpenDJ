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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;


/**
 * This message is used by LDAP or Replication Server that have been
 * out of credit for a while and want to check that the remote servers.
 *
 * A sending entity that is blocked because its send window is closed
 * for a while should create such a message to check that the window
 * closure is valid.
 *
 * An entity that received such a message should respond with a
 * WindowUpdate message indicating the curent credit available.
 */
public class WindowProbeMsg extends ReplicationMsg
{
  /**
   * Create a new WindowProbeMsg message.
   */
  public WindowProbeMsg()
  {
  }

  /**
   * Creates a new WindowProbeMsg from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           WindowMessage.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the WindowMessage.
   */
  public WindowProbeMsg(byte[] in) throws DataFormatException
  {
    // WindowProbeMsg Message only contains its type.
    if (in[0] != MSG_TYPE_WINDOW_PROBE)
      throw new DataFormatException("input is not a valid Window Message");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    // WindowProbeMsg Message only contains its type.

    byte[] resultByteArray = new byte[1];
    resultByteArray[0] = MSG_TYPE_WINDOW_PROBE;

    return resultByteArray;
  }
}
