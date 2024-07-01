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

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;

/**
 * This class implements the Sun-defined account usable request control.  The
 * OID for this control is 1.3.6.1.4.1.42.2.27.9.5.8, and it does not have a
 * value.
 */
public class AccountUsableRequestControl
       extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<AccountUsableRequestControl>
  {
    @Override
    public AccountUsableRequestControl decode(boolean isCritical,
                                              ByteString value)
           throws DirectoryException
    {
      if (value != null)
      {
        LocalizableMessage message = ERR_ACCTUSABLEREQ_CONTROL_HAS_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      return new AccountUsableRequestControl(isCritical);
    }

    @Override
    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<AccountUsableRequestControl> DECODER =
    new Decoder();

  /** Creates a new instance of the account usable request control with the default settings. */
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

  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    // No value element.
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("AccountUsableRequestControl()");
  }
}

