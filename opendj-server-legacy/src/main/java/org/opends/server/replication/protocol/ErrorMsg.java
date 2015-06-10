/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import static org.opends.server.util.StaticUtils.*;

import java.util.zip.DataFormatException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server or a replication server when an error
 * is detected in the context of a total update.
 */
public class ErrorMsg extends RoutableMsg
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Specifies the messageID built from the error that was detected. */
  private final String msgID;
  /** Specifies the complementary details about the error that was detected. */
  private final LocalizableMessage details;

  /**
   * The time of creation of this message.
   * <p>
   * protocol version previous to V4
   */
  private long creationTime = System.currentTimeMillis();

  /**
   * Creates an ErrorMsg providing the destination server.
   *
   * @param sender The server ID of the server that send this message.
   * @param destination The destination server or servers of this message.
   * @param details The message containing the details of the error.
   */
  public ErrorMsg(int sender, int destination, LocalizableMessage details)
  {
    super(sender, destination);
    this.msgID  = getMessageId(details);
    this.details = details;
    this.creationTime = System.currentTimeMillis();

    if (logger.isTraceEnabled())
    {
      logger.trace(" Creating error message" + this
          + " " + stackTraceToSingleLineString(new Exception("trace")));
    }
  }

  /**
   * Creates an ErrorMsg.
   *
   * @param destination replication server id
   * @param details details of the error
   */
  public ErrorMsg(int destination, LocalizableMessage details)
  {
    this(-2, destination, details);
  }

  /** Returns the unique message Id. */
  private String getMessageId(LocalizableMessage details)
  {
    return details.resourceName() + "-" + details.ordinal();
  }

  /**
   * Creates a new ErrorMsg by decoding the provided byte array.
   *
   * @param  in A byte array containing the encoded information for the message
   * @param version The protocol version to use to decode the msg.
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded message.
   */
  ErrorMsg(byte[] in, short version) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_ERROR)
    {
      throw new DataFormatException("input is not a valid "
          + getClass().getCanonicalName());
    }
    senderID = scanner.nextIntUTF8();
    destination = scanner.nextIntUTF8();
    msgID = scanner.nextString();
    details = LocalizableMessage.raw(scanner.nextString());

    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      creationTime = scanner.nextLongUTF8();
    }
  }

  /**
   * Get the details from this message.
   *
   * @return the details from this message.
   */
  public LocalizableMessage getDetails()
  {
    return details;
  }

  /**
   * Get the msgID from this message.
   *
   * @return the msgID from this message.
   */
  public String getMsgID()
  {
    return msgID;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short version)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_ERROR);
    builder.appendIntUTF8(senderID);
    builder.appendIntUTF8(destination);
    builder.appendString(msgID);
    builder.appendString(details.toString());
    if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      builder.appendLongUTF8(creationTime);
    }
    return builder.toByteArray();
  }

  /**
   * Returns a string representation of the message.
   *
   * @return the string representation of this message.
   */
  @Override
  public String toString()
  {
    return "ErrorMessage=["+
      " sender=" + this.senderID +
      " destination=" + this.destination +
      " msgID=" + this.msgID +
      " details=" + this.details +
      " creationTime=" + this.creationTime + "]";
  }

  /**
   * Get the creation time of this message.
   * When several attempts of initialization are done sequentially, it helps
   * sorting the good ones, from the ones that relate to ended initialization
   * when they are received.
   *
   * @return the creation time of this message.
   */
  public long getCreationTime()
  {
    return creationTime;
  }

  /**
   * Get the creation time of this message.
   * @param creationTime the creation time of this message.
   */
  public void setCreationTime(long creationTime)
  {
    this.creationTime = creationTime;
  }
}
