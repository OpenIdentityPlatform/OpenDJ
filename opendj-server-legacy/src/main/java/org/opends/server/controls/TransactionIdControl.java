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
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.controls;

import static org.opends.server.util.ServerConstants.OID_TRANSACTION_ID_CONTROL;

import java.io.IOException;

import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.ProtocolMessages;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;

/**
 * Control that provides a transaction ID.
 * <p>
 * The transaction ID is related to Common Audit : it is used for tracking the
 * processing of a user-interaction as it passes through the Forgerock stack
 * <p>
 * The control's value is the UTF-8 encoding of the transaction ID.
 */
public class TransactionIdControl extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder implements ControlDecoder<TransactionIdControl>
  {
    @Override
    public TransactionIdControl decode(boolean isCritical, ByteString value) throws DirectoryException
    {
      if (value == null)
      {
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
            ProtocolMessages.ERR_TRANSACTION_ID_CONTROL_HAS_NO_VALUE.get());
      }
      return new TransactionIdControl(isCritical, value.toString());
    }


    @Override
    public String getOID()
    {
      return OID_TRANSACTION_ID_CONTROL;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<TransactionIdControl> DECODER = new Decoder();

  /** The id value of this control. */
  private final String transactionId;

  /**
   * Creates a new Transaction Id Control.
   *
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical to the operation processing.
   * @param  transactionId The id to pass through this control.
   */
  public TransactionIdControl(boolean isCritical, String transactionId)
  {
    super(OID_TRANSACTION_ID_CONTROL, isCritical);
    this.transactionId = transactionId;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer
   *          The ASN.1 output stream to write to.
   * @throws IOException
   *           If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeOctetString(transactionId);
  }

  /**
   * Retrieves the transaction id associated with this control.
   *
   * @return  The transaction id associated with this control.
   */
  public String getTransactionId()
  {
    return transactionId;
  }

  /**
   * Appends a string representation of this control to the provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("TransactionIdControl(id=");
    buffer.append(transactionId);
    buffer.append(")");
  }
}
