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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

/**
 * This exception should be raised by the un-serialization code of a PDU
 * (typically the constructor code with a byte[] parameter), when the detected
 * PDU type (deduced from the first received byte of the message) is a PDU used
 * in an older version of the replication protocol than the current one, and we
 * do not support translation from this old version PDU to his matching PDU in
 * the current protocol version (if it exists). Thus, the code catching this
 * exception may decide special treatment, depending on the situation. For
 * instance it may decide to trash the PDU and keep the connection opened, or to
 * trash the PDU and close the connection...
 */
public class NotSupportedOldVersionPDUException extends Exception
{
  /** Suppress compile warning. */
  static final long serialVersionUID = 1739875L;
  /** Explicit message. */
  private String msg;
  /** Protocol version of the pdu. */
  private short protocolVersion = -1;
  /** Type of the pdu. */
  private byte pduType = -1;

  /**
   * Exception constructor.
   * @param pduStr PDU description.
   * @param protocolVersion PDU protocol version.
   * @param pduType PDU number.
   */
  public NotSupportedOldVersionPDUException(String pduStr,
    short protocolVersion, byte pduType)
  {
    super();
    msg = "Received unsupported " + pduStr + " PDU (" + pduType +
      ") from protocol version " + protocolVersion;
    this.protocolVersion = protocolVersion;
    this.pduType = pduType;
  }

  /**
   * Get the PDU message.
   * @return The PDU message.
   */
  @Override
  public String getMessage()
  {
    return msg;
  }

  @Override
  public String getLocalizedMessage()
  {
    return getMessage();
  }

  @Override
  public String toString()
  {
    return getMessage();
  }

  /**
   * Get the PDU protocol version.
   * @return The PDU protocol version.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * Get the PDU type.
   * @return The PDU type.
   */
  public byte getPduType()
  {
    return pduType;
  }
}
