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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LockManager;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



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
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The raw, unprocessed authorization DN from the control value.
  private ASN1OctetString rawAuthorizationDN;

  // The processed authorization DN from the control value.
  private DN authorizationDN;



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN from the
   *                             control value.  It must not be {@code null}.
   */
  public ProxiedAuthV1Control(ASN1OctetString rawAuthorizationDN)
  {
    super(OID_PROXIED_AUTH_V1, true, encodeValue(rawAuthorizationDN));


    this.rawAuthorizationDN = rawAuthorizationDN;

    authorizationDN = null;
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
    super(OID_PROXIED_AUTH_V1, true,
          encodeValue(new ASN1OctetString(authorizationDN.toString())));


    this.authorizationDN = authorizationDN;

    rawAuthorizationDN = new ASN1OctetString(authorizationDN.toString());
  }



  /**
   * Creates a new instance of the proxied authorization v1 control with the
   * provided information.
   *
   * @param  oid                 The OID to use for this control.
   * @param  isCritical          Indicates whether support for this control
   *                             should be considered a critical part of the
   *                             server processing.
   * @param  controlValue        The encoded value for this control.
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN from the
   *                             control value.
   */
  private ProxiedAuthV1Control(String oid, boolean isCritical,
                             ASN1OctetString controlValue,
                             ASN1OctetString rawAuthorizationDN)
  {
    super(oid, isCritical, controlValue);


    this.rawAuthorizationDN = rawAuthorizationDN;

    authorizationDN = null;
  }



  /**
   * Generates an encoded value for this control containing the provided raw
   * authorization DN.
   *
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN to use in
   *                             the control value.  It must not be
   *                             {@code null}.
   *
   * @return  The encoded control value.
   */
  private static ASN1OctetString encodeValue(ASN1OctetString rawAuthorizationDN)
  {
    ensureNotNull(rawAuthorizationDN);

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(1);
    elements.add(rawAuthorizationDN);

    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Creates a new proxied authorization v1 control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this proxied authorization v1 control.  It must not
   *                  be {@code null}.
   *
   * @return  The proxied authorization v1 control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         proxied authorization v1 control.
   */
  public static ProxiedAuthV1Control decodeControl(Control control)
         throws LDAPException
  {
    ensureNotNull(control);

    if (! control.isCritical())
    {
      int    msgID   = MSGID_PROXYAUTH1_CONTROL_NOT_CRITICAL;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                              message);
    }

    if (! control.hasValue())
    {
      int    msgID   = MSGID_PROXYAUTH1_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    ASN1OctetString rawAuthorizationDN;
    try
    {
      ArrayList<ASN1Element> elements =
           ASN1Sequence.decodeAsSequence(control.getValue().value()).elements();
      if (elements.size() != 1)
      {
        int    msgID   = MSGID_PROXYAUTH1_INVALID_ELEMENT_COUNT;
        String message = getMessage(msgID, elements.size());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }

      rawAuthorizationDN = elements.get(0).decodeAsOctetString();
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_PROXYAUTH1_CANNOT_DECODE_VALUE;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message, e);
    }

    return new ProxiedAuthV1Control(control.getOID(), control.isCritical(),
                                    control.getValue(), rawAuthorizationDN);
  }



  /**
   * Retrieves the raw, unprocessed authorization DN from the control value.
   *
   * @return  The raw, unprocessed authorization DN from the control value.
   */
  public ASN1OctetString getRawAuthorizationDN()
  {
    return rawAuthorizationDN;
  }



  /**
   * Specifies the raw, unprocessed authorization DN for this proxied auth
   * control.
   *
   * @param  rawAuthorizationDN  The raw, unprocessed authorization DN for this
   *                             proxied auth control.
   */
  public void setRawAuthorizationDN(ASN1OctetString rawAuthorizationDN)
  {
    this.rawAuthorizationDN = rawAuthorizationDN;

    setValue(encodeValue(rawAuthorizationDN));
    authorizationDN = null;
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
   * Specifies the authorization DN for this proxied auth control.
   *
   * @param  authorizationDN  The authorizationDN for this proxied auth control.
   *                          It must not be {@code null}.
   */
  public void setAuthorizationDN(DN authorizationDN)
  {
    ensureNotNull(authorizationDN);

    this.authorizationDN = authorizationDN;

    rawAuthorizationDN = new ASN1OctetString(authorizationDN.toString());
    setValue(encodeValue(rawAuthorizationDN));
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
      int    msgID   = MSGID_PROXYAUTH1_CANNOT_LOCK_USER;
      String message = getMessage(msgID, String.valueOf(authzDN));
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message,
                                   msgID);
    }

    try
    {
      Entry userEntry = DirectoryServer.getEntry(authzDN);
      if (userEntry == null)
      {
        // The requested user does not exist.
        int    msgID   = MSGID_PROXYAUTH1_NO_SUCH_USER;
        String message = getMessage(msgID, String.valueOf(authzDN));
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message,
                                     msgID);
      }


      // FIXME -- We should provide some mechanism for enabling debug
      // processing.
      PasswordPolicyState pwpState = new PasswordPolicyState(userEntry, false,
                                                             false);
      if (pwpState.isDisabled() || pwpState.isAccountExpired() ||
          pwpState.lockedDueToFailures() ||
          pwpState.lockedDueToIdleInterval() ||
          pwpState.lockedDueToMaximumResetAge() ||
          pwpState.isPasswordExpired())
      {
        int    msgID   = MSGID_PROXYAUTH1_UNUSABLE_ACCOUNT;
        String message = getMessage(msgID, String.valueOf(authzDN));
        throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED, message,
                                     msgID);
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
   * Retrieves a string representation of this proxied auth v1 control.
   *
   * @return  A string representation of this proxied auth v1 control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this proxied auth v1 control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ProxiedAuthorizationV1Control(authorizationDN=\"");
    rawAuthorizationDN.toString(buffer);
    buffer.append("\")");
  }
}

