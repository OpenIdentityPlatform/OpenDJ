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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.controls;

import java.io.IOException;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements version 1 of the proxied authorization control as
 * defined in early versions of draft-weltman-ldapv3-proxy (this implementation
 * is based on the "-04" revision).  It makes it possible for one user to
 * request that an operation be performed under the authorization of another.
 * The target user is specified as a DN in the control value, which
 * distinguishes it from later versions of the control (which used a different
 * OID) in which the target user was specified using an authorization ID.
 */
public class ProxiedAuthV1Control
       extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<ProxiedAuthV1Control>
  {
    @Override
    public ProxiedAuthV1Control decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (!isCritical)
      {
        LocalizableMessage message = ERR_PROXYAUTH1_CONTROL_NOT_CRITICAL.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      if (value == null)
      {
        LocalizableMessage message = ERR_PROXYAUTH1_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      DN authorizationDN;
      try
      {
        reader.readStartSequence();
        authorizationDN = DN.valueOf(reader.readOctetString());
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message =
            ERR_PROXYAUTH1_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      return new ProxiedAuthV1Control(isCritical, authorizationDN);
    }

    @Override
    public String getOID()
    {
      return OID_PROXIED_AUTH_V1;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<ProxiedAuthV1Control> DECODER =
    new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /** The raw, unprocessed authorization DN from the control value. */
  private ByteString rawAuthorizationDN;

  /** The processed authorization DN from the control value. */
  private DN authorizationDN;



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN from the
   *                             control value.  It must not be {@code null}.
   */
  public ProxiedAuthV1Control(ByteString rawAuthorizationDN)
  {
    this(true, rawAuthorizationDN);
  }



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  authorizationDN  The authorization DN from the control value.  It
   *                          must not be {@code null}.
   */
  public ProxiedAuthV1Control(DN authorizationDN)
  {
    this(true, authorizationDN);
  }



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  isCritical          Indicates whether support for this control
   *                             should be considered a critical part of the
   *                             server processing.
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN from the
   *                             control value.
   */
  public ProxiedAuthV1Control(boolean isCritical, ByteString rawAuthorizationDN)
  {
    super(OID_PROXIED_AUTH_V1, isCritical);


    this.rawAuthorizationDN = rawAuthorizationDN;

    authorizationDN = null;
  }



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  isCritical          Indicates whether support for this control
   *                             should be considered a critical part of the
   *                             server processing.
   * @param  authorizationDN     The authorization DN from the control value.
   *                             It must not be {@code null}.
   */
  public ProxiedAuthV1Control(boolean isCritical, DN authorizationDN)
  {
    super(OID_PROXIED_AUTH_V1, isCritical);


    this.authorizationDN = authorizationDN;

    rawAuthorizationDN = ByteString.valueOfUtf8(authorizationDN.toString());
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    writer.writeOctetString(rawAuthorizationDN);
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  /**
   * Retrieves the raw, unprocessed authorization DN from the control value.
   *
   * @return  The raw, unprocessed authorization DN from the control value.
   */
  public ByteString getRawAuthorizationDN()
  {
    return rawAuthorizationDN;
  }



  /**
   * Retrieves the authorization DN from the control value.
   *
   * @return  The authorization DN from the control value.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to decode
   *                              the raw authorization DN as a DN.
   */
  public DN getAuthorizationDN()
         throws DirectoryException
  {
    if (authorizationDN == null)
    {
      authorizationDN = DN.valueOf(rawAuthorizationDN);
    }

    return authorizationDN;
  }



  /**
   * Retrieves the authorization entry for this proxied authorization V1
   * control.  It will also perform any necessary password policy checks to
   * ensure that the associated user account is suitable for use in performing
   * this processing.
   *
   * @return  The entry for user specified as the authorization identity in this
   *          proxied authorization V1 control, or {@code null} if the
   *          authorization DN is the null DN.
   *
   * @throws  DirectoryException  If the target user does not exist or is not
   *                              available for use, or if a problem occurs
   *                              while making the determination.
   */
  public Entry getAuthorizationEntry()
         throws DirectoryException
  {
    DN authzDN = getAuthorizationDN();
    if (authzDN.isRootDN())
    {
      return null;
    }


    // See if the authorization DN is one of the alternate bind DNs for one of
    // the root users and if so then map it accordingly.
    DN actualDN = DirectoryServer.getActualRootBindDN(authzDN);
    if (actualDN != null)
    {
      authzDN = actualDN;
    }


    Entry userEntry = DirectoryServer.getEntry(authzDN);
    if (userEntry == null)
    {
      // The requested user does not exist.
      LocalizableMessage message = ERR_PROXYAUTH1_NO_SUCH_USER.get(authzDN);
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
    }


    // FIXME -- We should provide some mechanism for enabling debug
    // processing.
    AuthenticationPolicyState state = AuthenticationPolicyState.forUser(
        userEntry, false);

    if (state.isDisabled())
    {
      LocalizableMessage message = ERR_PROXYAUTH1_UNUSABLE_ACCOUNT.get(userEntry.getName());
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
    }

    if (state.isPasswordPolicy())
    {
      PasswordPolicyState pwpState = (PasswordPolicyState) state;
      if (pwpState.isAccountExpired() || pwpState.isLocked() || pwpState.isPasswordExpired())
      {
        LocalizableMessage message = ERR_PROXYAUTH1_UNUSABLE_ACCOUNT.get(authzDN);
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
      }
    }

    // If we've made it here, then the user is acceptable.
    return userEntry;
  }



  /**
   * Appends a string representation of this proxied auth v1 control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ProxiedAuthorizationV1Control(authorizationDN=\"");
    buffer.append(rawAuthorizationDN);
    buffer.append("\")");
  }
}

