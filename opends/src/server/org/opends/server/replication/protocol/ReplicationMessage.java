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
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * Abstract class that must be used when defining messages that can
 * be sent for replication purpose between servers.
 *
 * When extending this class one should also create a new MSG_TYPE
 * and should update the generateMsg() method.
 */
public abstract class ReplicationMessage
{
  static final byte MSG_TYPE_MODIFY_REQUEST = 1;
  static final byte MSG_TYPE_ADD_REQUEST = 2;
  static final byte MSG_TYPE_DELETE_REQUEST = 3;
  static final byte MSG_TYPE_MODIFYDN_REQUEST = 4;
  static final byte MSG_TYPE_ACK = 5;
  static final byte MSG_TYPE_SERVER_START = 6;
  static final byte MSG_TYPE_CHANGELOG_START = 7;
  static final byte MSG_TYPE_WINDOW = 8;
  static final byte MSG_TYPE_HEARTBEAT = 9;
  static final byte MSG_TYPE_INITIALIZE_REQUEST = 10;
  static final byte MSG_TYPE_INITIALIZE_TARGET = 11;
  static final byte MSG_TYPE_ENTRY = 12;
  static final byte MSG_TYPE_DONE = 13;
  static final byte MSG_TYPE_ERROR = 14;
  // Adding a new type of message here probably requires to
  // change accordingly generateMsg method below

  /**
   * Return the byte[] representation of this message.
   * Depending on the message type, the first byte of the byte[] must be.
   * MSG_TYPE_MODIFY_REQUEST
   * MSG_TYPE_ADD_REQUEST
   * MSG_TYPE_DELETE_REQUEST
   * MSG_TYPE_MODIFY_DN_REQUEST
   * MSG_TYPE_ACK
   * MSG_TYPE_SERVER_START
   * MSG_TYPE_CHANGELOG_START
   * MSG_TYPE_WINDOW
   * MSG_TYPE_HEARTBEAT
   * MSG_TYPE_INITIALIZE
   * MSG_TYPE_INITIALIZE_TARGET
   * MSG_TYPE_ENTRY
   * MSG_TYPE_DONE
   * MSG_TYPE_ERROR
   *
   * @return the byte[] representation of this message.
   */
  public abstract byte[] getBytes();


  /**
   * Generates a ReplicationMessage from its encoded form.
   *
   * @param buffer The encode form of the ReplicationMessage.
   * @return the generated SycnhronizationMessage.
   * @throws DataFormatException if the encoded form was not a valid msg.
   * @throws UnsupportedEncodingException if UTF8 is not supported.
   */
  public static ReplicationMessage generateMsg(byte[] buffer)
                throws DataFormatException, UnsupportedEncodingException
  {
    ReplicationMessage msg = null;
    switch (buffer[0])
    {
      case MSG_TYPE_MODIFY_REQUEST:
          msg = new ModifyMsg(buffer);
      break;
      case MSG_TYPE_ADD_REQUEST:
          msg = new AddMsg(buffer);
      break;
      case MSG_TYPE_DELETE_REQUEST:
          msg = new DeleteMsg(buffer);
      break;
      case MSG_TYPE_MODIFYDN_REQUEST:
          msg = new ModifyDNMsg(buffer);
      break;
      case MSG_TYPE_ACK:
        msg = new AckMessage(buffer);
      break;
      case MSG_TYPE_SERVER_START:
        msg = new ServerStartMessage(buffer);
      break;
      case MSG_TYPE_CHANGELOG_START:
        msg = new ChangelogStartMessage(buffer);
      break;
      case MSG_TYPE_WINDOW:
        msg = new WindowMessage(buffer);
      break;
      case MSG_TYPE_HEARTBEAT:
        msg = new HeartbeatMessage(buffer);
      break;
      case MSG_TYPE_INITIALIZE_REQUEST:
        msg = new InitializeRequestMessage(buffer);
      break;
      case MSG_TYPE_INITIALIZE_TARGET:
        msg = new InitializeTargetMessage(buffer);
      break;
      case MSG_TYPE_ENTRY:
        msg = new EntryMessage(buffer);
      break;
      case MSG_TYPE_DONE:
        msg = new DoneMessage(buffer);
      break;
      case MSG_TYPE_ERROR:
        msg = new ErrorMessage(buffer);
      break;
      default:
        throw new DataFormatException("received message with unknown type");
    }
    return msg;
  }

  /**
   * Concatenate the tail byte array into the resultByteArray.
   * The resultByteArray must be large enough before calling this method.
   *
   * @param tail the byte array to concatenate.
   * @param resultByteArray The byte array to concatenate to.
   * @param pos the position where to concatenate.
   * @return the next position to use in the resultByteArray.
   */
  protected int addByteArray(byte[] tail, byte[] resultByteArray, int pos)
  {
    for (int i=0; i<tail.length; i++,pos++)
    {
      resultByteArray[pos] = tail[i];
    }
    resultByteArray[pos++] = 0;
    return pos;
  }

  /**
   * Get the length of the next String encoded in the in byte array.
   *
   * @param in the byte array where to calculate the string.
   * @param pos the position whre to start from in the byte array.
   * @return the length of the next string.
   * @throws DataFormatException If the byte array does not end with null.
   */
  protected int getNextLength(byte[] in, int pos) throws DataFormatException
  {
    int offset = pos;
    int length = 0;
    while (in[offset++] != 0)
    {
      if (offset >= in.length)
        throw new DataFormatException("byte[] is not a valid modify msg");
      length++;
    }
    return length;
  }
}
