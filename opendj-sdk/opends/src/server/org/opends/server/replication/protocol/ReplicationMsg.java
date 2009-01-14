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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
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
public abstract class ReplicationMsg
{
  // PDU type values kept for compatibility with replication protocol version 1
  static final byte MSG_TYPE_MODIFY_V1 = 1;
  static final byte MSG_TYPE_ADD_V1 = 2;
  static final byte MSG_TYPE_DELETE_V1 = 3;
  static final byte MSG_TYPE_MODIFYDN_V1 = 4;
  static final byte MSG_TYPE_SERVER_START_V1 = 6;
  static final byte MSG_TYPE_REPL_SERVER_START_V1 = 7;
  static final byte MSG_TYPE_REPL_SERVER_INFO_V1 = 16;

  // PDU type values for current protocol version (see ProtocolVersion)
  static final byte MSG_TYPE_ACK = 5;
  static final byte MSG_TYPE_WINDOW = 8;
  static final byte MSG_TYPE_HEARTBEAT = 9;
  static final byte MSG_TYPE_INITIALIZE_REQUEST = 10;
  static final byte MSG_TYPE_INITIALIZE_TARGET = 11;
  static final byte MSG_TYPE_ENTRY = 12;
  static final byte MSG_TYPE_DONE = 13;
  static final byte MSG_TYPE_ERROR = 14;
  static final byte MSG_TYPE_WINDOW_PROBE = 15;
  static final byte MSG_TYPE_RESET_GENERATION_ID = 17;
  static final byte MSG_TYPE_REPL_SERVER_MONITOR_REQUEST = 18;
  static final byte MSG_TYPE_REPL_SERVER_MONITOR = 19;
  static final byte MSG_TYPE_SERVER_START = 20;
  static final byte MSG_TYPE_REPL_SERVER_START = 21;
  static final byte MSG_TYPE_MODIFY = 22;
  static final byte MSG_TYPE_ADD = 23;
  static final byte MSG_TYPE_DELETE = 24;
  static final byte MSG_TYPE_MODIFYDN = 25;
  static final byte MSG_TYPE_TOPOLOGY = 26;
  static final byte MSG_TYPE_START_SESSION = 27;
  static final byte MSG_TYPE_CHANGE_STATUS = 28;
  static final byte MSG_TYPE_GENERIC_UPDATE = 29;

  // Adding a new type of message here probably requires to
  // change accordingly generateMsg method below

  /**
   * Return the byte[] representation of this message.
   * Depending on the message type, the first byte of the byte[] must be one of
   * the MSG_TYPE* definitions. The serialization is done using the current
   * protocol version. For a serialization using a particular protocol version,
   * call the getBytes(byte protocolVersion) method that should be available
   * for the subclasses (PDUs) that allow such a translation.
   *
   * @return the byte[] representation of this message.
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public abstract byte[] getBytes() throws UnsupportedEncodingException;

  /**
   * Serializes the PDU using the provided replication protocol version.
   * WARNING: should be overwritten by a PDU (sub class) we want to support
   * older protocol version serialization for.
   * @param reqProtocolVersion The protocol version to use for serialization.
   * The version should normally be older than the current one.
   * @return The encoded PDU.
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported or the
   *         requested protocol version to use is not supported by this PDU.
   */
  public byte[] getBytes(short reqProtocolVersion)
    throws UnsupportedEncodingException
  {
    // Of course, always support current protocol version
    if (reqProtocolVersion == ProtocolVersion.getCurrentVersion())
    {
      return getBytes();
    }

    // Unsupported requested version
    // Any PDU that support older protocol version serialization should
    // overwrite this method for that.
    throw new UnsupportedEncodingException(getClass().getSimpleName() +
      " PDU does not support requested protocol version serialization: " +
      reqProtocolVersion);
  }


  /**
   * Generates a ReplicationMsg from its encoded form. This un-serialization
   * is done taking into account the various supported replication protocol
   * versions.
   *
   * @param buffer The encode form of the ReplicationMsg.
   * @return The generated SycnhronizationMessage.
   * @throws DataFormatException If the encoded form was not a valid msg.
   * @throws UnsupportedEncodingException If UTF8 is not supported.
   * @throws NotSupportedOldVersionPDUException If the PDU is part of an old
   * protocol version and we do not support it.
   */
  public static ReplicationMsg generateMsg(byte[] buffer)
                throws DataFormatException, UnsupportedEncodingException,
                NotSupportedOldVersionPDUException
  {
    ReplicationMsg msg = null;
    switch (buffer[0])
    {
      case MSG_TYPE_SERVER_START_V1:
        throw new NotSupportedOldVersionPDUException("Server Start",
          ProtocolVersion.REPLICATION_PROTOCOL_V1, buffer[0]);
      case MSG_TYPE_REPL_SERVER_INFO_V1:
        throw new NotSupportedOldVersionPDUException("Replication Server Info",
          ProtocolVersion.REPLICATION_PROTOCOL_V1, buffer[0]);
      case MSG_TYPE_MODIFY:
        msg = new ModifyMsg(buffer);
      break;
      case MSG_TYPE_MODIFY_V1:
          msg = ModifyMsg.createV1(buffer);
      break;
      case MSG_TYPE_ADD:
      case MSG_TYPE_ADD_V1:
          msg = new AddMsg(buffer);
      break;
      case MSG_TYPE_DELETE:
      case MSG_TYPE_DELETE_V1:
          msg = new DeleteMsg(buffer);
      break;
      case MSG_TYPE_MODIFYDN:
      case MSG_TYPE_MODIFYDN_V1:
          msg = new ModifyDNMsg(buffer);
      break;
      case MSG_TYPE_ACK:
        msg = new AckMsg(buffer);
      break;
      case MSG_TYPE_SERVER_START:
        msg = new ServerStartMsg(buffer);
      break;
      case MSG_TYPE_REPL_SERVER_START:
      case MSG_TYPE_REPL_SERVER_START_V1:
        msg = new ReplServerStartMsg(buffer);
      break;
      case MSG_TYPE_WINDOW:
        msg = new WindowMsg(buffer);
      break;
      case MSG_TYPE_HEARTBEAT:
        msg = new HeartbeatMsg(buffer);
      break;
      case MSG_TYPE_INITIALIZE_REQUEST:
        msg = new InitializeRequestMsg(buffer);
      break;
      case MSG_TYPE_INITIALIZE_TARGET:
        msg = new InitializeTargetMsg(buffer);
      break;
      case MSG_TYPE_ENTRY:
        msg = new EntryMsg(buffer);
      break;
      case MSG_TYPE_DONE:
        msg = new DoneMsg(buffer);
      break;
      case MSG_TYPE_ERROR:
        msg = new ErrorMsg(buffer);
      break;
      case MSG_TYPE_RESET_GENERATION_ID:
        msg = new ResetGenerationIdMsg(buffer);
      break;
      case MSG_TYPE_WINDOW_PROBE:
        msg = new WindowProbeMsg(buffer);
      break;
      case MSG_TYPE_TOPOLOGY:
        msg = new TopologyMsg(buffer);
      break;
      case MSG_TYPE_REPL_SERVER_MONITOR_REQUEST:
        msg = new MonitorRequestMsg(buffer);
      break;
      case MSG_TYPE_REPL_SERVER_MONITOR:
        msg = new MonitorMsg(buffer);
      break;
      case MSG_TYPE_START_SESSION:
        msg = new StartSessionMsg(buffer);
      break;
      case MSG_TYPE_CHANGE_STATUS:
        msg = new ChangeStatusMsg(buffer);
      break;
      case MSG_TYPE_GENERIC_UPDATE:
        msg = new UpdateMsg(buffer);
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
  protected static int addByteArray(byte[] tail, byte[] resultByteArray,
    int pos)
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
   * @param pos the position where to start from in the byte array.
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
        throw new DataFormatException("byte[] is not a valid msg");
      length++;
    }
    return length;
  }
}
