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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the account usable response control.  This is a
 * Sun-defined control with OID 1.3.6.1.4.1.42.2.27.9.5.8.  The value of this
 * control is composed according to the following BNF:
 * <BR>
 * <PRE>
 * ACCOUNT_USABLE_RESPONSE ::= CHOICE {
 *      is_available           [0] INTEGER, -- Seconds before expiration --
 *      is_not_available       [1] MORE_INFO }
 *
 * MORE_INFO
 *      inactive               [0] BOOLEAN DEFAULT FALSE,
 *      reset                  [1] BOOLEAN DEFAULT FALSE,
 *      expired                [2] BOOLEAN DEFAULT_FALSE,
 *      remaining_grace        [3] INTEGER OPTIONAL,
 *      seconds_before_unlock  [4] INTEGER OPTIONAL }
 * </PRE>
 */
public class AccountUsableResponseControl
       extends Control
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.controls.AccountUsableResponseControl";



  /**
   * The BER type to use for the seconds before expiration when the account is
   * available.
   */
  public static final byte TYPE_SECONDS_BEFORE_EXPIRATION = (byte) 0x80;



  /**
   * The BER type to use for the MORE_INFO sequence when the account is not
   * available.
   */
  public static final byte TYPE_MORE_INFO = (byte) 0xA1;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * account has been inactivated.
   */
  public static final byte TYPE_INACTIVE = (byte) 0x80;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * password has been administratively reset.
   */
  public static final byte TYPE_RESET = (byte) 0x81;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * user's password is expired.
   */
  public static final byte TYPE_EXPIRED = (byte) 0x82;



  /**
   * The BER type to use for the MORE_INFO element that provides the number of
   * remaining grace logins.
   */
  public static final byte TYPE_REMAINING_GRACE_LOGINS = (byte) 0x83;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * password has been administratively reset.
   */
  public static final byte TYPE_SECONDS_BEFORE_UNLOCK = (byte) 0x84;



  // Indicates whether the user's account is usable.
  private boolean isUsable;

  // Indicates whether the user's password is expired.
  private boolean isExpired;

  // Indicates whether the user's account is inactive.
  private boolean isInactive;

  // Indicates whether the user's account is currently locked.
  private boolean isLocked;

  // Indicates whether the user's password has been reset and must be changed
  // before anything else can be done.
  private boolean isReset;

  // The number of remaining grace logins, if available.
  private int remainingGraceLogins;

  // The length of time in seconds before the user's password expires, if
  // available.
  private int secondsBeforeExpiration;

  // The length of time before the user's account is unlocked, if available.
  private int secondsBeforeUnlock;



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is available and provide the number of seconds
   * until expiration.  It will use the default OID and criticality.
   *
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   */
  public AccountUsableResponseControl(int secondsBeforeExpiration)
  {
    super(OID_ACCOUNT_USABLE_CONTROL, false,
          encodeValue(secondsBeforeExpiration));

    assert debugConstructor(CLASS_NAME,
                            String.valueOf(secondsBeforeExpiration));

    this.secondsBeforeExpiration = secondsBeforeExpiration;

    isUsable             = true;
    isInactive           = false;
    isReset              = false;
    isExpired            = false;
    remainingGraceLogins = -1;
    isLocked             = false;
    secondsBeforeUnlock  = 0;
  }



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is available and provide the number of seconds
   * until expiration.
   *
   * @param  oid                      The OID for this account usability
   *                                  response control.
   * @param  isCritical               Indicates whether this control should be
   *                                  marked critical.
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   */
  public AccountUsableResponseControl(String oid, boolean isCritical,
                                      int secondsBeforeExpiration)
  {
    super(oid, isCritical, encodeValue(secondsBeforeExpiration));

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(secondsBeforeExpiration));

    this.secondsBeforeExpiration = secondsBeforeExpiration;

    isUsable             = true;
    isInactive           = false;
    isReset              = false;
    isExpired            = false;
    remainingGraceLogins = -1;
    isLocked             = false;
    secondsBeforeUnlock  = 0;
  }



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is not available and provide information about
   * the underlying reason.  It will use the default OID and criticality.
   *
   * @param  isInactive            Indicates whether the user's account has been
   *                               inactivated by an administrator.
   * @param  isReset               Indicates whether the user's password has
   *                               been reset by an administrator.
   * @param  isExpired             Indicates whether the user's password is
   *                               expired.
   * @param  remainingGraceLogins  The number of grace logins remaining.  A
   *                               value of zero indicates that there are none
   *                               remaining.  A value of -1 indicates that
   *                               grace login functionality is not enabled.
   * @param  isLocked              Indicates whether the user's account is
   *                               currently locked out.
   * @param  secondsBeforeUnlock   The length of time in seconds until the
   *                               account is unlocked.  A value of -1 indicates
   *                               that the account will not be automatically
   *                               unlocked and must be reset by an
   *                               administrator.
   */
  public AccountUsableResponseControl(boolean isInactive, boolean isReset,
                                      boolean isExpired,
                                      int remainingGraceLogins,
                                      boolean isLocked, int secondsBeforeUnlock)
  {
    super(OID_ACCOUNT_USABLE_CONTROL, false,
          encodeValue(isInactive, isReset, isExpired, remainingGraceLogins,
                      isLocked, secondsBeforeUnlock));

    assert debugConstructor(CLASS_NAME, String.valueOf(isInactive),
                            String.valueOf(isReset), String.valueOf(isExpired),
                            String.valueOf(remainingGraceLogins),
                            String.valueOf(isLocked),
                            String.valueOf(secondsBeforeUnlock));

    this.isInactive           = isInactive;
    this.isReset              = isReset;
    this.isExpired            = isExpired;
    this.remainingGraceLogins = remainingGraceLogins;
    this.isLocked             = isLocked;
    this.secondsBeforeUnlock  = secondsBeforeUnlock;

    isUsable                = false;
    secondsBeforeExpiration = -1;
  }



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is not available and provide information about
   * the underlying reason.
   *
   * @param  oid                   The OID for this account usability response
   *                               control.
   * @param  isCritical            Indicates whether this control should be
   *                               marked critical.
   * @param  isInactive            Indicates whether the user's account has been
   *                               inactivated by an administrator.
   * @param  isReset               Indicates whether the user's password has
   *                               been reset by an administrator.
   * @param  isExpired             Indicates whether the user's password is
   *                               expired.
   * @param  remainingGraceLogins  The number of grace logins remaining.  A
   *                               value of zero indicates that there are none
   *                               remaining.  A value of -1 indicates that
   *                               grace login functionality is not enabled.
   * @param  isLocked              Indicates whether the user's account is
   *                               currently locked out.
   * @param  secondsBeforeUnlock   The length of time in seconds until the
   *                               account is unlocked.  A value of -1 indicates
   *                               that the account will not be automatically
   *                               unlocked and must be reset by an
   *                               administrator.
   */
  public AccountUsableResponseControl(String oid, boolean isCritical,
                                      boolean isInactive, boolean isReset,
                                      boolean isExpired,
                                      int remainingGraceLogins,
                                      boolean isLocked, int secondsBeforeUnlock)
  {
    super(oid, isCritical,
          encodeValue(isInactive, isReset, isExpired, remainingGraceLogins,
                      isLocked, secondsBeforeUnlock));

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(isInactive), String.valueOf(isReset),
                            String.valueOf(isExpired),
                            String.valueOf(remainingGraceLogins),
                            String.valueOf(isLocked),
                            String.valueOf(secondsBeforeUnlock));

    this.isInactive           = isInactive;
    this.isReset              = isReset;
    this.isExpired            = isExpired;
    this.remainingGraceLogins = remainingGraceLogins;
    this.isLocked             = isLocked;
    this.secondsBeforeUnlock  = secondsBeforeUnlock;

    isUsable                = false;
    secondsBeforeExpiration = -1;
  }



  /**
   * Creates a new account usability response control using the provided
   * information.  This version of the constructor is only intended for internal
   * use.
   *
   * @param  oid                      The OID for this account usability
   *                                  response control.
   * @param  isCritical               Indicates whether this control should be
   *                                  marked critical.
   * @param  isAvailable              Indicates whether the user's account is
   *                                  available for use.
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   * @param  isInactive               Indicates whether the user's account has
   *                                  been inactivated by an administrator.
   * @param  isReset                  Indicates whether the user's password has
   *                                  been reset by an administrator.
   * @param  isExpired                Indicates whether the user's password is
   *                                  expired.
   * @param  remainingGraceLogins     The number of grace logins remaining.  A
   *                                  value of zero indicates that there are
   *                                  none remaining.  A value of -1 indicates
   *                                  that grace login functionality is not
   *                                  enabled.
   * @param  isLocked                 Indicates whether the user's account is
   *                                  currently locked out.
   * @param  secondsBeforeUnlock      The length of time in seconds until the
   *                                  account is unlocked.  A value of -1
   *                                  indicates that the account will not be
   *                                  automatically unlocked and must be reset
   *                                  by an administrator.
   * @param  encodedValue             The pre-encoded value for this account
   *                                  usable response control.
   */
  private AccountUsableResponseControl(String oid, boolean isCritical,
                                             boolean isAvailable,
                                             int secondsBeforeExpiration,
                                             boolean isInactive,
                                             boolean isReset, boolean isExpired,
                                             int remainingGraceLogins,
                                             boolean isLocked,
                                             int secondsBeforeUnlock,
                                             ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(isAvailable),
                            String.valueOf(secondsBeforeExpiration),
                            String.valueOf(isInactive),
                            String.valueOf(isReset),
                            String.valueOf(isExpired),
                            String.valueOf(remainingGraceLogins),
                            String.valueOf(isLocked),
                            String.valueOf(secondsBeforeUnlock));

    this.isUsable                = isAvailable;
    this.secondsBeforeExpiration = secondsBeforeExpiration;
    this.isInactive              = isInactive;
    this.isReset                 = isReset;
    this.isExpired               = isExpired;
    this.remainingGraceLogins    = remainingGraceLogins;
    this.isLocked                = isLocked;
    this.secondsBeforeUnlock     = secondsBeforeUnlock;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the value for an account usable response control in which the use's
   * account is available.
   *
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   *
   * @return  An ASN.1 octet string containing the encoded control value.
   */
  private static ASN1OctetString encodeValue(int secondsBeforeExpiration)
  {
    assert debugEnter(CLASS_NAME, "encodeValue",
                      String.valueOf(secondsBeforeExpiration));


    ASN1Integer sbeInteger = new ASN1Integer(TYPE_SECONDS_BEFORE_EXPIRATION,
                                             secondsBeforeExpiration);

    return new ASN1OctetString(sbeInteger.encode());
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the value for an account usable response control in which the user's
   * account is not available.
   *
   *
   * @param  isInactive            Indicates whether the user's account has been
   *                               inactivated by an administrator.
   * @param  isReset               Indicates whether the user's password has
   *                               been reset by an administrator.
   * @param  isExpired             Indicates whether the user's password is
   *                               expired.
   * @param  remainingGraceLogins  The number of grace logins remaining.  A
   *                               value of zero indicates that there are none
   *                               remaining.  A value of -1 indicates that
   *                               grace login functionality is not enabled.
   * @param  isLocked              Indicates whether the user's account is
   *                               currently locked out.
   * @param  secondsBeforeUnlock   The length of time in seconds until the
   *                               account is unlocked.  A value of -1 indicates
   *                               that the account will not be automatically
   *                               unlocked and must be reset by an
   *                               administrator.
   *
   * @return  An ASN.1 octet string containing the encoded control value.
   */
  private static ASN1OctetString encodeValue(boolean isInactive,
                                             boolean isReset, boolean isExpired,
                                             int remainingGraceLogins,
                                             boolean isLocked,
                                             int secondsBeforeUnlock)
  {
    assert debugEnter(CLASS_NAME, "encodeValue", String.valueOf(isInactive),
                      String.valueOf(isReset), String.valueOf(isExpired),
                      String.valueOf(remainingGraceLogins),
                      String.valueOf(isLocked),
                      String.valueOf(secondsBeforeUnlock));


    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(5);

    if (isInactive)
    {
      elements.add(new ASN1Boolean(TYPE_INACTIVE, true));
    }

    if (isReset)
    {
      elements.add(new ASN1Boolean(TYPE_RESET, true));
    }

    if (isExpired)
    {
      elements.add(new ASN1Boolean(TYPE_EXPIRED, true));

      if (remainingGraceLogins >= 0)
      {
        elements.add(new ASN1Integer(TYPE_REMAINING_GRACE_LOGINS,
                                     remainingGraceLogins));
      }
    }

    if (isLocked)
    {
      elements.add(new ASN1Integer(TYPE_SECONDS_BEFORE_UNLOCK,
                                   secondsBeforeUnlock));
    }

    ASN1Sequence moreInfoSequence = new ASN1Sequence(TYPE_MORE_INFO,
                                                     elements);
    return new ASN1OctetString(moreInfoSequence.encode());
  }



  /**
   * Creates a new account usable response control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this account usable response control.
   *
   * @return  The account usable response control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         account usable response control.
   */
  public static AccountUsableResponseControl decodeControl(Control control)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeControl", String.valueOf(control));


    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      // The response control must always have a value.
      int    msgID   = MSGID_ACCTUSABLERES_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    try
    {
      ASN1Element valueElement = ASN1Element.decode(controlValue.value());
      switch (valueElement.getType())
      {
        case TYPE_SECONDS_BEFORE_EXPIRATION:
          int secondsBeforeExpiration =
                   valueElement.decodeAsInteger().intValue();
          return new AccountUsableResponseControl(control.getOID(),
                                                  control.isCritical(), true,
                                                  secondsBeforeExpiration,
                                                  false, false, false, -1,
                                                  false, 0, controlValue);
        case TYPE_MORE_INFO:
          boolean isInactive = false;
          boolean isReset = false;
          boolean isExpired = false;
          boolean isLocked = false;
          int     remainingGraceLogins = -1;
          int     secondsBeforeUnlock = 0;

          for (ASN1Element e : valueElement.decodeAsSequence().elements())
          {
            switch (e.getType())
            {
              case TYPE_INACTIVE:
                isInactive = e.decodeAsBoolean().booleanValue();
                break;
              case TYPE_RESET:
                isReset = e.decodeAsBoolean().booleanValue();
                break;
              case TYPE_EXPIRED:
                isExpired = e.decodeAsBoolean().booleanValue();
                break;
              case TYPE_REMAINING_GRACE_LOGINS:
                remainingGraceLogins = e.decodeAsInteger().intValue();
                break;
              case TYPE_SECONDS_BEFORE_UNLOCK:
                isLocked = true;
                secondsBeforeUnlock = e.decodeAsInteger().intValue();
                break;
              default:
                int    msgID   = MSGID_ACCTUSABLERES_UNKNOWN_UNAVAILABLE_TYPE;
                String message = getMessage(msgID, byteToHex(e.getType()));
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }
          }

          return new AccountUsableResponseControl(control.getOID(),
                                                  control.isCritical(), false,
                                                  -1, isInactive, isReset,
                                                  isExpired,
                                                  remainingGraceLogins,
                                                  isLocked, secondsBeforeUnlock,
                                                  controlValue);

        default:
          int    msgID   = MSGID_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE;
          String message = getMessage(msgID, byteToHex(valueElement.getType()));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (ASN1Exception ae)
    {
      assert debugException(CLASS_NAME, "decodeControl", ae);

      int    msgID   = MSGID_ACCTUSABLERES_DECODE_ERROR;
      String message = getMessage(msgID, ae.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeControl", e);

      int    msgID   = MSGID_ACCTUSABLERES_DECODE_ERROR;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
  }



  /**
   * Indicates whether the associated user account is available for use.
   *
   * @return  <CODE>true</CODE> if the associated user account is available, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isUsable()
  {
    assert debugEnter(CLASS_NAME, "isAvailable");

    return isUsable;
  }



  /**
   * Retrieves the length of time in seconds before the user's password expires.
   * This value is unreliable if the account is not available.
   *
   * @return  The length of time in seconds before the user's password expires,
   *          or -1 if it is unknown or password expiration is not enabled for
   *          the user.
   */
  public int getSecondsBeforeExpiration()
  {
    assert debugEnter(CLASS_NAME, "getSecondsBeforeExpiration");

    return secondsBeforeExpiration;
  }



  /**
   * Indicates whether the user's account has been inactivated by an
   * administrator.
   *
   * @return  <CODE>true</CODE> if the user's account has been inactivated by
   *          an administrator, or <CODE>false</CODE> if not.
   */
  public boolean isInactive()
  {
    assert debugEnter(CLASS_NAME, "isInactive");

    return isInactive;
  }



  /**
   * Indicates whether the user's password has been administratively reset and
   * the user must change that password before any other operations will be
   * allowed.
   *
   * @return  <CODE>true</CODE> if the user's password has been administratively
   *          reset, or <CODE>false</CODE> if not.
   */
  public boolean isReset()
  {
    assert debugEnter(CLASS_NAME, "isReset");

    return isReset;
  }



  /**
   * Indicates whether the user's password is expired.
   *
   * @return  <CODE>true</CODE> if the user's password is expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isExpired()
  {
    assert debugEnter(CLASS_NAME, "isExpired");

    return isExpired;
  }



  /**
   * Retrieves the number of remaining grace logins for the user.  This value is
   * unreliable if the user's password is not expired.
   *
   * @return  The number of remaining grace logins for the user, or -1 if the
   *          grace logins feature is not enabled for the user.
   */
  public int getRemainingGraceLogins()
  {
    assert debugEnter(CLASS_NAME, "getRemainingGraceLogins");

    return remainingGraceLogins;
  }



  /**
   * Indicates whether the user's account is locked for some reason.
   *
   * @return  <CODE>true</CODE> if the user's account is locked, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isLocked()
  {
    assert debugEnter(CLASS_NAME, "isLocked");

    return isLocked;
  }



  /**
   * Retrieves the length of time in seconds before the user's account is
   * automatically unlocked.  This value is unreliable is the user's account is
   * not locked.
   *
   * @return  The length of time in seconds before the user's account is
   *          automatically unlocked, or -1 if it requires administrative action
   *          to unlock the account.
   */
  public int getSecondsBeforeUnlock()
  {
    assert debugEnter(CLASS_NAME, "getSecondsBeforeUnlock");

    return secondsBeforeUnlock;
  }



  /**
   * Retrieves a string representation of this password policy response control.
   *
   * @return  A string representation of this password policy response control.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this password policy response control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("AccountUsableResponseControl(isUsable=");
    buffer.append(isUsable);

    if (isUsable)
    {
      buffer.append(",secondsBeforeExpiration=");
      buffer.append(secondsBeforeExpiration);
    }
    else
    {
      buffer.append(",isInactive=");
      buffer.append(isInactive);
      buffer.append(",isReset=");
      buffer.append(isReset);
      buffer.append(",isExpired=");
      buffer.append(isExpired);
      buffer.append(",remainingGraceLogins=");
      buffer.append(remainingGraceLogins);
      buffer.append(",isLocked=");
      buffer.append(isLocked);
      buffer.append(",secondsBeforeUnlock=");
      buffer.append(secondsBeforeUnlock);
    }

    buffer.append(")");
  }
}

