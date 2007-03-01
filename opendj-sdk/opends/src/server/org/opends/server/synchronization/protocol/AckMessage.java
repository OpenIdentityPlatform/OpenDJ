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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.synchronization.common.ChangeNumber;

/**
 * Used to send acks between LDAP and changelog servers.
 */
public class AckMessage extends SynchronizationMessage
{
  // ChangeNumber of the update that was acked.
  private ChangeNumber changeNumber;

  /**
   * Creates a new AckMessage from a ChangeNumber.
   *
   * @param changeNumber The ChangeNumber used to build the AckMessage.
   */
  public AckMessage(ChangeNumber changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * Creates a new AckMessage by decoding the provided byte array.
   *
   * @param in The byte array containing the encoded form of the AckMessage.
   * @throws DataFormatException If in does not contain a properly encoded
   *                             AckMessage.
   */
  public AckMessage(byte[] in) throws DataFormatException
  {
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_ACK)
        throw new DataFormatException("byte[] is not a valid modify msg");
      int pos = 1;

      /* read the changeNumber */
      int length = getNextLength(in, pos);
      String changenumberStr = new  String(in, pos, length, "UTF-8");
      changeNumber = new ChangeNumber(changenumberStr);
      pos +=24;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the ChangeNumber from the message.
   *
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    try
    {
      byte[] changeNumberByte = changeNumber.toString().getBytes("UTF-8");
      int length = 1 + changeNumberByte.length + 1;
      byte[] resultByteArray = new byte[length];
      int pos = 1;

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_ACK;

      /* put the ChangeNumber */
      pos = addByteArray(changeNumberByte, resultByteArray, pos);

      return resultByteArray;
    } catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }
}
