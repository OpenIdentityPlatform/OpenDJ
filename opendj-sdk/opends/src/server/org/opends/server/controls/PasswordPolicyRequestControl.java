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
 * This class implements the password policy request control defined in
 * draft-behera-ldap-password-policy.  It does not have a value.
 */
public class PasswordPolicyRequestControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<PasswordPolicyRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public PasswordPolicyRequestControl decode(boolean isCritical,
                                               ByteString value)
        throws DirectoryException
    {
      if (value != null)
      {
        Message message = ERR_PWPOLICYREQ_CONTROL_HAS_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      return new PasswordPolicyRequestControl(isCritical);
    }

    public String getOID()
    {
      return OID_PASSWORD_POLICY_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PasswordPolicyRequestControl> DECODER =
    new Decoder();


  /**
   * Creates a new instance of the password policy request control with the
   * default settings.
   */
  public PasswordPolicyRequestControl()
  {
    this(false);

  }



  /**
   * Creates a new instance of the password policy request control with the
   * provided information.
   *
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the client processing.
   */
  public PasswordPolicyRequestControl(boolean isCritical)
  {
    super(OID_PASSWORD_POLICY_CONTROL, isCritical);

  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    // No value element.
  }



  /**
   * Appends a string representation of this password policy request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordPolicyRequestControl()");
  }
}

