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
 *      Copyright 2008 Sun Microsystems, Inc.
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
  // Suppress compile warning
  static final long serialVersionUID = 1739875L;
  private String msg = null; // Explicit message
  private short protocolVersion = -1; // protocol version of the pdu
  private byte pduType = -1; // type of the pdu

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
  public String getMessage()
  {
    return msg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getLocalizedMessage()
  {
    return getMessage();
  }

  /**
   * {@inheritDoc}
   */
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
