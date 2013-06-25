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
package org.opends.server.controls;
import org.opends.messages.Message;



import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class implements the Sun-defined account usable request control.  The
 * OID for this control is 1.3.6.1.4.1.42.2.27.9.5.8, and it does not have a
 * value.
 */
public class AccountUsableRequestControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<AccountUsableRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public AccountUsableRequestControl decode(boolean isCritical,
                                              ByteString value)
           throws DirectoryException
    {
      if (value != null)
      {
        Message message = ERR_ACCTUSABLEREQ_CONTROL_HAS_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      return new AccountUsableRequestControl(isCritical);
    }

    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<AccountUsableRequestControl> DECODER =
    new Decoder();

  /**
   * Creates a new instance of the account usable request control with the
   * default settings.
   */
  public AccountUsableRequestControl()
  {
    this(false);
  }

  /**
   * Creates a new instance of the account usable request control with the
   * default settings.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   */
  public AccountUsableRequestControl(boolean isCritical)
  {
    super(OID_ACCOUNT_USABLE_CONTROL, isCritical);

  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  protected void writeValue(ASN1Writer writer) throws IOException {
    // No value element.
  }



  /**
   * Appends a string representation of this account usable request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AccountUsableRequestControl()");
  }
}

