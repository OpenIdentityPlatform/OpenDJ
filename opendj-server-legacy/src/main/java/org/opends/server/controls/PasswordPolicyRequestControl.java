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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;



import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.ResultCode;

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
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<PasswordPolicyRequestControl>
  {
    @Override
    public PasswordPolicyRequestControl decode(boolean isCritical,
                                               ByteString value)
        throws DirectoryException
    {
      if (value != null)
      {
        LocalizableMessage message = ERR_PWPOLICYREQ_CONTROL_HAS_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      return new PasswordPolicyRequestControl(isCritical);
    }

    @Override
    public String getOID()
    {
      return OID_PASSWORD_POLICY_CONTROL;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<PasswordPolicyRequestControl> DECODER =
    new Decoder();


  /** Creates a new instance of the password policy request control with the default settings. */
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

  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    // No value element.
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordPolicyRequestControl()");
  }
}

