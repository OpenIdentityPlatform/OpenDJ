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


import java.util.concurrent.locks.Lock;
import java.io.IOException;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

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
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<ProxiedAuthV1Control>
  {
    /**
     * {@inheritDoc}
     */
    public ProxiedAuthV1Control decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (!isCritical)
      {
        Message message = ERR_PROXYAUTH1_CONTROL_NOT_CRITICAL.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      if (value == null)
      {
        Message message = ERR_PROXYAUTH1_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      DN authorizationDN;
      try
      {
        reader.readStartSequence();
        authorizationDN = DN.decode(reader.readOctetString());
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_PROXYAUTH1_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }

      return new ProxiedAuthV1Control(isCritical, authorizationDN);
    }

    public String getOID()
    {
      return OID_PROXIED_AUTH_V1;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ProxiedAuthV1Control> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The raw, unprocessed authorization DN from the control value.
  private ByteString rawAuthorizationDN;

  // The processed authorization DN from the control value.
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

    rawAuthorizationDN = ByteString.valueOf(authorizationDN.toString());
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
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

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
      authorizationDN = DN.decode(rawAuthorizationDN);
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
    if (authzDN.isNullDN())
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


    Lock entryLock = null;
    for (int i=0; i < 3; i++)
    {
      entryLock = LockManager.lockRead(authzDN);
      if (entryLock != null)
      {
        break;
      }
    }

    if (entryLock == null)
    {
      Message message =
          ERR_PROXYAUTH1_CANNOT_LOCK_USER.get(String.valueOf(authzDN));
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
    }

    try
    {
      Entry userEntry = DirectoryServer.getEntry(authzDN);
      if (userEntry == null)
      {
        // The requested user does not exist.
        Message message =
            ERR_PROXYAUTH1_NO_SUCH_USER.get(String.valueOf(authzDN));
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
      }


      // FIXME -- We should provide some mechanism for enabling debug
      // processing.
      PasswordPolicyState pwpState = new PasswordPolicyState(userEntry, false);
      if (pwpState.isDisabled() || pwpState.isAccountExpired() ||
          pwpState.lockedDueToFailures() ||
          pwpState.lockedDueToIdleInterval() ||
          pwpState.lockedDueToMaximumResetAge() ||
          pwpState.isPasswordExpired())
      {
        Message message =
            ERR_PROXYAUTH1_UNUSABLE_ACCOUNT.get(String.valueOf(authzDN));
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message);
      }


      // If we've made it here, then the user is acceptable.
      return userEntry;
    }
    finally
    {
      LockManager.unlock(authzDN, entryLock);
    }
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

