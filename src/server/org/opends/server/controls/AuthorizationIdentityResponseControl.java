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
import org.opends.server.types.*;

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
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<AuthorizationIdentityResponseControl>
  {
    /**
     * {@inheritDoc}
     */
    public AuthorizationIdentityResponseControl decode(boolean isCritical,
                                                       ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_AUTHZIDRESP_NO_CONTROL_VALUE.get();
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
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, Message.EMPTY);
      }
    }

    public String getOID()
    {
      return OID_AUTHZID_RESPONSE;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<AuthorizationIdentityResponseControl>
      DECODER = new Decoder();


  // The authorization ID for this control.
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
      this.authorizationID = "dn:" + authorizationDN.toString();
    }
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
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



  /**
   * Appends a string representation of this authorization identity response
   * control to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AuthorizationIdentityResponseControl(authzID=\"");
    buffer.append(authorizationID);
    buffer.append("\")");
  }
}

