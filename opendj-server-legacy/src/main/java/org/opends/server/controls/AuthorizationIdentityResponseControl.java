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
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class implements the authorization identity response control as defined
 * in RFC 3829.  It may be included in a bind response message to provide the
 * authorization ID resulting for a client after the bind operation as
 * completed.
 */
public class AuthorizationIdentityResponseControl
       extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<AuthorizationIdentityResponseControl>
  {
    @Override
    public AuthorizationIdentityResponseControl decode(boolean isCritical,
                                                       ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_AUTHZIDRESP_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      try
      {
        String authID = value.toString();
        return new AuthorizationIdentityResponseControl(isCritical,
            authID);
      }
      catch(Exception e)
      {
        // TODO: message.
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, LocalizableMessage.EMPTY);
      }
    }

    @Override
    public String getOID()
    {
      return OID_AUTHZID_RESPONSE;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<AuthorizationIdentityResponseControl>
      DECODER = new Decoder();


  /** The authorization ID for this control. */
  private String authorizationID;



  /**
   * Creates a new authorization identity response control using the default
   * settings to indicate an anonymous authentication.
   */
  public AuthorizationIdentityResponseControl()
  {
    this(false);
  }

  /**
   * Creates a new authorization identity response control using the default
   * settings to indicate an anonymous authentication.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   */
  public AuthorizationIdentityResponseControl(boolean isCritical)
  {
    super(OID_AUTHZID_RESPONSE, isCritical);
  }



  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  authorizationID  The authorization ID for this control.
   */
  public AuthorizationIdentityResponseControl(String authorizationID)
  {
    this(false, authorizationID);
  }


  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  authorizationID  The authorization ID for this control.
   */
  public AuthorizationIdentityResponseControl(boolean isCritical,
                                              String authorizationID)
  {
    super(OID_AUTHZID_RESPONSE, isCritical);


    this.authorizationID = authorizationID;
  }




  /**
   * Creates a new authorization identity response control with the provided
   * information.
   *
   * @param  authorizationDN  The authorization DN for this control.
   */
  public AuthorizationIdentityResponseControl(DN authorizationDN)
  {
    super(OID_AUTHZID_RESPONSE, false);


    if (authorizationDN == null)
    {
      this.authorizationID = "dn:";
    }
    else
    {
      this.authorizationID = "dn:" + authorizationDN;
    }
  }

  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeOctetString(authorizationID);
  }



  /**
   * Retrieves the authorization ID for this authorization identity response
   * control.
   *
   * @return  The authorization ID for this authorization identity response
   *          control.
   */
  public String getAuthorizationID()
  {
    return authorizationID;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("AuthorizationIdentityResponseControl(authzID=\"");
    buffer.append(authorizationID);
    buffer.append("\")");
  }
}

