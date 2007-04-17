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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * This message is part of the synchronization protocol.
 * This message is sent by a server or a changelog server when an error
 * is detected in the context of a total update.
 */
public class ErrorMessage extends RoutableMessage implements
    Serializable
{
  private static final long serialVersionUID = 2726389860247088266L;

  // Specifies the messageID built form the error that was detected
  private int msgID;
  // Specifies the complementary details about the error that was detected
  private String details = null;

  /**
   * Create a InitializeMessage.
   * @param sender The server ID of the server that send this message.
   * @param destination The destination server or servers of this message.
   * @param msgID The error message ID.
   * @param details The details of the error.
   */
  public ErrorMessage(short sender, short destination, int msgID,
      String details)
  {
    super(sender, destination);
    this.msgID  = msgID;
    this.details = details;
  }

  /**
   * Create a InitializeMessage.
   *
   * @param destination changelog server id
   * @param msgID error message ID
   * @param details details of the error
   */
  public ErrorMessage(short destination, int msgID, String details)
  {
    super((short)-2, destination);
    this.msgID  = msgID;
    this.details = details;
  }

  /**
   * Creates a new InitializeMessage by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the Message
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded InitializeMessage.
   */
  public ErrorMessage(byte[] in) throws DataFormatException
  {
    super();
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_ERROR)
        throw new DataFormatException("input is not a valid InitializeMessage");
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderString = new String(in, pos, length, "UTF-8");
      senderID = Short.valueOf(senderString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      destination = Short.valueOf(serverIdString);
      pos += length +1;

      // MsgID
      length = getNextLength(in, pos);
      String msgIdString = new String(in, pos, length, "UTF-8");
      msgID = Integer.valueOf(msgIdString);
      pos += length +1;

      // Details
      length = getNextLength(in, pos);
      details = new String(in, pos, length, "UTF-8");
      pos += length +1;

    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the base DN from this InitializeMessage.
   *
   * @return the base DN from this InitializeMessage.
   */
  public String getDetails()
  {
    return details;
  }

  /**
   * Get the base DN from this InitializeMessage.
   *
   * @return the base DN from this InitializeMessage.
   */
  public int getMsgID()
  {
    return msgID;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    /* The InitializeMessage is stored in the form :
     * <operation type><basedn><serverid>
     */
    try {
      byte[] byteSender = String.valueOf(senderID).getBytes("UTF-8");
      byte[] byteDestination = String.valueOf(destination).getBytes("UTF-8");
      byte[] byteErrMsgId = String.valueOf(msgID).getBytes("UTF-8");
      byte[] byteDetails = details.getBytes("UTF-8");

      int length = 1 + byteSender.length + 1
                     + byteDestination.length + 1
                     + byteErrMsgId.length + 1
                     + byteDetails.length + 1;

      byte[] resultByteArray = new byte[length];

      // put the type of the operation
      resultByteArray[0] = MSG_TYPE_ERROR;
      int pos = 1;

      // sender
      pos = addByteArray(byteSender, resultByteArray, pos);

      // destination
      pos = addByteArray(byteDestination, resultByteArray, pos);

      // MsgId
      pos = addByteArray(byteErrMsgId, resultByteArray, pos);

      // details
      pos = addByteArray(byteDetails, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }
}
