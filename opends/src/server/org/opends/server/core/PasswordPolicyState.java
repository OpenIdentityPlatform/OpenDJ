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
package org.opends.server.core;



import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a data structure for holding password policy state
 * information for a user account.
 */
public class PasswordPolicyState
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.PasswordPolicyState";



  // Indicates whether to debug password policy processing performed wth this
  // state object.
  private boolean debug;

  // Indicates whether the user entry itself should be updated or if the updates
  // should be stored as modifications.
  private boolean updateEntry;

  // Indicates whether an expiration warning message should be sent if the
  // authentication is successful.
  private boolean sendExpirationWarning;

  // Indicates whether a grace login will be used if the authentication is
  // successful.
  private boolean useGraceLogin;

  // Indicates whether the user's account is expired.
  private ConditionResult isAccountExpired;

  // Indicates whether the user's account is disabled.
  private ConditionResult isDisabled;

  // Indicates whether the user's password is expired.
  private ConditionResult isPasswordExpired;

  // Indicates whether the warning to send to the client would be the first
  // warning for the user.
  private ConditionResult isFirstWarning;

  // Indicates whether the user's account is locked by the idle lockout.
  private ConditionResult isIdleLocked;

  // Indicates whether the user's account is locked by administrative reset.
  private ConditionResult isResetLocked;

  // Indicates whether the user may use a grace login if the password is expired
  // and there are one or more grace logins remaining.
  private ConditionResult mayUseGraceLogin;

  // Indicates whether the user's password must be changed.
  private ConditionResult mustChangePassword;

  // Indicates whether the user should be warned of an upcoming expiration.
  private ConditionResult shouldWarn;

  // The user entry with which this state information is associated.
  private Entry userEntry;

  // The number of seconds until the user's account is automatically unlocked.
  private int secondsUntilUnlock;

  // The number of seconds until the user's password expires.
  private int secondsUntilExpiration;

  // The set of modifications that should be applied to the user's entry.
  private LinkedList<Modification> modifications;

  // The set of authentication failure times for this user.
  private List<Long> authFailureTimes;

  // The set of grace login times for this user.
  private List<Long> graceLoginTimes;

  // The time that the user's account was created.
  private long createTime;

  // The current time for use in all password policy calculations.
  private long currentTime;

  // The time that the user's password should expire (or did expire).
  private long expirationTime;

  // The time that the user's entry was locked due to too many authentication
  // failures.
  private long failureLockedTime;

  // The time that the user's entry was locked due to the idle lockout.
  private long idleLockedTime;

  // The time that the user last authenticated to the Directory Server.
  private long lastLoginTime;

  // The time that the user's password was last changed.
  private long passwordChangedTime;

  // The last required change time with which the user complied.
  private long requiredChangeTime;

  // The time that the user was first warned about an upcoming expiration.
  private long warnedTime;

  // The password policy with which the account is associated.
  private PasswordPolicy passwordPolicy;

  // The string representation of the current time.
  private String currentGeneralizedTime;

  // The string representation of the user's DN.
  private String userDNString;



  /**
   * Creates a new password policy state object with the provided information.
   *
   * @param  userEntry    The entry with the user account.
   * @param  updateEntry  Indicates whether changes should update the provided
   *                      user entry directly or whether they should be
   *                      collected as a set of modifications.
   * @param  debug        Indicates whether to enable debugging for the
   *                      operations performed.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              determine the password policy for the user or
   *                              perform any other state initialization.
   */
  public PasswordPolicyState(Entry userEntry, boolean updateEntry,
                             boolean debug)
         throws DirectoryException
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(userEntry),
                            String.valueOf(passwordPolicy));


    this.userEntry   = userEntry;
    this.updateEntry = updateEntry;
    this.debug       = debug;

    userDNString           = userEntry.getDN().toString();
    passwordPolicy         = getPasswordPolicyInternal();
    currentGeneralizedTime = TimeThread.getGeneralizedTime();
    currentTime            = TimeThread.getTime();
    modifications          = new LinkedList<Modification>();
    isDisabled             = ConditionResult.UNDEFINED;
    isAccountExpired       = ConditionResult.UNDEFINED;
    isPasswordExpired      = ConditionResult.UNDEFINED;
    isFirstWarning         = ConditionResult.UNDEFINED;
    isIdleLocked           = ConditionResult.UNDEFINED;
    isResetLocked          = ConditionResult.UNDEFINED;
    mayUseGraceLogin       = ConditionResult.UNDEFINED;
    mustChangePassword     = ConditionResult.UNDEFINED;
    shouldWarn             = ConditionResult.UNDEFINED;
    expirationTime         = Long.MIN_VALUE;
    failureLockedTime      = Long.MIN_VALUE;
    idleLockedTime         = Long.MIN_VALUE;
    lastLoginTime          = Long.MIN_VALUE;
    requiredChangeTime     = Long.MIN_VALUE;
    warnedTime             = Long.MIN_VALUE;
    authFailureTimes       = null;
    sendExpirationWarning  = false;
    useGraceLogin          = false;
    secondsUntilExpiration = Integer.MIN_VALUE;
    secondsUntilUnlock     = Integer.MIN_VALUE;


    // Get the time that the user's account was created.
    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_CREATE_TIMESTAMP_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(OP_ATTR_CREATE_TIMESTAMP);
    }

    createTime = getGeneralizedTime(type);


    // Get the password changed time for the user.
    type = DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_CHANGED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_CHANGED_TIME);
    }

    passwordChangedTime = getGeneralizedTime(type);
    if (passwordChangedTime <= 0)
    {
      passwordChangedTime = createTime;

      if (passwordChangedTime <= 0)
      {
        passwordChangedTime = 0;

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME, "<init>",
                       "Could not determine password changed time for user " +
                       userDNString);
        }
      }
    }
  }



  /**
   * Retrieves the password policy for the user.
   *
   * @return  The password policy for the user.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              determine the password policy for the user.
   */
  private PasswordPolicy getPasswordPolicyInternal()
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getPasswordPolicy");


    // See if the user entry contains the pwdPolicySubentry attribute to select
    // a custom objectclass (whether real or virtual).
    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_SUBENTRY_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(OP_ATTR_PWPOLICY_SUBENTRY);
    }

    List<Attribute> attrList = userEntry.getAttribute(type);
    if ((attrList == null) || attrList.isEmpty())
    {
      // There is no policy subentry defined, so we'll use the default.
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "getPasswordPolicy",
                     "Using the default password policy for user " +
                     userDNString);
      }

      return DirectoryServer.getDefaultPasswordPolicy();
    }


    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        DN subentryDN;
        try
        {
          subentryDN = DN.decode(v.getValue());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "getPasswordPolicy", e);

          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.ERROR, CLASS_NAME,
                         "getPasswordPolicy",
                         "Could not parse password policy subentry DN \"" +
                         v.getStringValue() + "\" for user \"" + userDNString +
                         "\":  " + stackTraceToSingleLineString(e));
          }

          int    msgID   = MSGID_PWPSTATE_CANNOT_DECODE_SUBENTRY_VALUE_AS_DN;
          String message = getMessage(msgID, v.getStringValue(), userDNString,
                                      e.getMessage());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message,
                                       msgID, e);
        }

        PasswordPolicy policy = DirectoryServer.getPasswordPolicy(subentryDN);
        if (policy == null)
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.ERROR, CLASS_NAME,
                         "getPasswordPolicy",
                         "Password policy subentry \"" +
                         String.valueOf(subentryDN) + "\" for user \"" +
                         userDNString + "\" is not defined in the Directory " +
                         "Server.");
          }

          int msgID = MSGID_PWPSTATE_NO_SUCH_POLICY;
          String message = getMessage(msgID, userDNString,
                                      String.valueOf(subentryDN));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID);
        }
        else
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "getPasswordPolicy",
                         "Using password policy subentry \"" +
                         String.valueOf(subentryDN) + "\" for user \"" +
                         userDNString + "\".");
          }

          return policy;
        }
      }
    }


    // This shouldn't happen, but if it does then use the default.
    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getPasswordPolicy",
                   "Falling back to the default password policy for user " +
                   userDNString);
    }

    return DirectoryServer.getDefaultPasswordPolicy();
  }



  /**
   * Retrieves the value of the specified attribute as a string.
   *
   * @param  attributeType  The attribute type whose value should be retrieved.
   *
   * @return  The value of the specified attribute as a string, or
   *          <CODE>null</CODE> if there is no such value.
   */
  private String getValue(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "getValue", String.valueOf(attributeType));

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "getValue",
                     "Returning null because attribute " +
                     attributeType.getNameOrOID() + " does not exist in user " +
                     "entry " + userDNString);
      }

      return null;
    }

    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        String stringValue = v.getStringValue();

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "getValue",
                       "Returning value \"" + stringValue + "\" for user " +
                       userDNString);
        }

        return stringValue;
      }
    }

    return null;
  }



  /**
   * Retrieves the value of the specified attribute from the user's entry as a
   * time in generalized time format.
   *
   * @param  attributeType  The attribute type whose value should be parsed as a
   *                        generalized time value.
   *
   * @return  The requested time, or -1 if it could not be determined.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              decode the value as a generalized time.
   */
  private long getGeneralizedTime(AttributeType attributeType)
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getGeneralizedTime",
                      String.valueOf(attributeType));

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "getGeneralizedTime",
                     "Returning -1 because attribute " +
                     attributeType.getNameOrOID() + " does not exist in user " +
                     "entry " + userDNString);
      }

      return -1;
    }


    for (Attribute a : attrList)
    {
      for (AttributeValue v  : a.getValues())
      {
        try
        {
          return GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                                            v.getNormalizedValue());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "getGeneralizedTime", e);

          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.WARNING, CLASS_NAME,
                         "getGeneralizedTime",
                         "Unable to decode value " + v.getStringValue() +
                         " for attribute " + attributeType.getNameOrOID() +
                         " in user entry " + userDNString + ":  " + e);
          }

          int msgID = MSGID_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME;
          String message = getMessage(msgID, v.getStringValue(),
                                      attributeType.getNameOrOID(),
                                      userDNString, String.valueOf(e));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getGeneralizedTime",
                   "Returning -1 for attribute " +
                   attributeType.getNameOrOID() + " in user entry " +
                   userDNString + " because all options have been exhausted.");
    }

    return -1;
  }



  /**
   * Retrieves the set of values of the specified attribute from the user's
   * entry in generalized time format.
   *
   * @param  attributeType  The attribute type whose values should be parsed as
   *                        generalized time values.
   *
   * @return  The set of generalized time values, or an empty list if there are
   *          none.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              decode a value as a generalized time.
   */
  private List<Long> getGeneralizedTimes(AttributeType attributeType)
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getGeneralizedTimes",
                      String.valueOf(attributeType));


    ArrayList<Long> timeValues = new ArrayList<Long>();

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "getGeneralizedTimes",
                     "Returning an empty list because attribute " +
                     attributeType.getNameOrOID() + " does not exist in user " +
                     "entry " + userDNString);
      }

      return timeValues;
    }


    for (Attribute a : attrList)
    {
      for (AttributeValue v  : a.getValues())
      {
        try
        {
          timeValues.add(GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                                                    v.getNormalizedValue()));
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "getGeneralizedTimes", e);

          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.WARNING, CLASS_NAME,
                         "getGeneralizedTimes",
                         "Unable to decode value " + v.getStringValue() +
                         " for attribute " + attributeType.getNameOrOID() +
                         " in user entry " + userDNString + ":  " + e);
          }

          int msgID = MSGID_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME;
          String message = getMessage(msgID, v.getStringValue(),
                                      attributeType.getNameOrOID(),
                                      userDNString, String.valueOf(e));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID, e);
        }
      }
    }

    return timeValues;
  }



  /**
   * Retrieves the value of the specified attribute from the user's entry as a
   * Boolean.
   *
   * @param  attributeType  The attribute type whose value should be parsed as a
   *                        Boolean.
   * @param  defaultValue   The default value that should be used if the
   *                        specified attribute does not exist.
   *
   * @return  The requested Boolean value, or the default value if the specified
   *          attribute does not exist with a Boolean value.
   *
   * @throws  DirectoryException  If the value cannot be decoded as a Boolean.
   */
  private boolean getBoolean(AttributeType attributeType, boolean defaultValue)
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getBoolean", String.valueOf(attributeType),
                      String.valueOf(defaultValue));

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "getBoolean",
                     "Returning default of " + defaultValue +
                     " because attribute " + attributeType.getNameOrOID() +
                     " does not exist in user entry " + userDNString);
      }

      return defaultValue;
    }


    for (Attribute a : attrList)
    {
      for (AttributeValue v  : a.getValues())
      {
        String valueString = toLowerCase(v.getStringValue());
        if (valueString.equals("true") || valueString.equals("yes") ||
            valueString.equals("on") || valueString.equals("1"))
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "getBoolean",
                         "Attribute " + attributeType.getNameOrOID() +
                         " resolves to true for user entry " + userDNString);
          }

          return true;
        }
        else if (valueString.equals("false") || valueString.equals("no") ||
                 valueString.equals("off") || valueString.equals("0"))
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "getBoolean",
                         "Attribute " + attributeType.getNameOrOID() +
                         " resolves to false for user entry " + userDNString);
          }

          return false;
        }
        else
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.ERROR, CLASS_NAME, "getBoolean",
                         "Unable to resolve value \"" + valueString +
                         "\" for attribute " + attributeType.getNameOrOID() +
                         " in user entry " + userDNString + " as a Boolean.");
          }

          int msgID = MSGID_PWPSTATE_CANNOT_DECODE_BOOLEAN;
          String message = getMessage(msgID, v.getStringValue(),
                                      attributeType.getNameOrOID(),
                                      userDNString);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getBoolean",
                   "Returning default of " + defaultValue +" for attribute " +
                   attributeType.getNameOrOID() + " in user entry " +
                   userDNString + " because all options have been exhausted.");
    }

    return defaultValue;
  }



  /**
   * Retrieves the password policy associated with this state information.
   *
   * @return  The password policy associated with this state information.
   */
  public PasswordPolicy getPasswordPolicy()
  {
    assert debugEnter(CLASS_NAME, "getPasswordPolicy");

    return passwordPolicy;
  }



  /**
   * Retrieves the user entry associated with this state information.
   *
   * @return  The user entry associated with this state information.
   */
  public Entry getUserEntry()
  {
    assert debugEnter(CLASS_NAME, "getUserEntry");

    return userEntry;
  }



  /**
   * Retrieves the set of modifications that correspond to changes made in
   * password policy processing that may need to be applied to the user entry.
   *
   * @return  The set of modifications that correspond to changes made in
   *          password policy processing that may need to be applied to the user
   *          entry.
   */
  public LinkedList<Modification> getModifications()
  {
    assert debugEnter(CLASS_NAME, "getModifications");

    return modifications;
  }



  /**
   * Retrieves the attribute type used to hold user passwords.
   *
   * @return  The attribute type used to hold user passwords.
   */
  public AttributeType getPasswordAttribute()
  {
    assert debugEnter(CLASS_NAME, "getPasswordAttribute");

    return passwordPolicy.getPasswordAttribute();
  }



  /**
   * Retrieves the set of values for the password attribute from the user entry.
   *
   * @return  The set of values for the password attribute from the user entry.
   */
  public LinkedHashSet<AttributeValue> getPasswordValues()
  {
    assert debugEnter(CLASS_NAME, "getPasswordValues");

    List<Attribute> attrList =
         userEntry.getAttribute(passwordPolicy.getPasswordAttribute());
    for (Attribute a : attrList)
    {
      return a.getValues();
    }

    return new LinkedHashSet<AttributeValue>(0);
  }



  /**
   * Indicates whether the associated password policy requires that
   * authentication be performed in a secure manner.
   *
   * @return  <CODE>true</CODE> if the associated password policy requires that
   *          authentication be performed in a secure manner, or
   *          <CODE>false</CODE> if not.
   */
  public boolean requireSecureAuthentication()
  {
    assert debugEnter(CLASS_NAME, "requireSecureAuthentication");

    return passwordPolicy.requireSecureAuthentication();
  }



  /**
   * Indicates whether the associated password policy requires that password
   * changes be performed in a secure manner.
   *
   * @return  <CODE>true</CODE> if the associated password policy requires that
   *          password changes be performed in a secure manner, or
   *          <CODE>false</CODE> if not.
   */
  public boolean requireSecurePasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "requireSecurePasswordChanges");

    return passwordPolicy.requireSecurePasswordChanges();
  }



  /**
   * Indicates whether the associated password policy allows multiple different
   * password values to be maintained.
   *
   * @return  <CODE>true</CODE> if the associated password policy allows
   *          multiple different password values to be maintained, or
   *          <CODE>false</CODE> if it does not.
   */
  public boolean allowMultiplePasswordValues()
  {
    assert debugEnter(CLASS_NAME, "allowMultiplePasswordValues");

    return passwordPolicy.allowMultiplePasswordValues();
  }



  /**
   * Indicates whether the associated password policy allows new passwords to be
   * provided in a pre-encoded form.
   *
   * @return  <CODE>true</CODE> if the associated password policy allows new
   *          passwords to be provided in a pre-encoded form, or
   *          <CODE>false</CODE> if not.
   */
  public boolean allowPreEncodedPasswords()
  {
    assert debugEnter(CLASS_NAME, "allowPreEncodedPasswords");

    return passwordPolicy.allowPreEncodedPasswords();
  }



  /**
   * Indicates whether the associated password policy allows end users to change
   * their own passwords.
   *
   * @return  <CODE>true</CODE> if the associated password policy allows end
   *          users to change their own passwords, or <CODE>false</CODE> if not.
   */
  public boolean allowUserPasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "allowUserPasswordChanges");

    return passwordPolicy.allowUserPasswordChanges();
  }



  /**
   * Indicates whether users will be required to provide their current password
   * when choosing a new one.
   *
   * @return  <CODE>true</CODE> if users will be required to provide their
   *          current password when choosing a new one, or <CODE>false</CODE>
   *          if not.
   */
  public boolean requireCurrentPassword()
  {
    assert debugEnter(CLASS_NAME, "requireCurrentPassword");

    return passwordPolicy.requireCurrentPassword();
  }



  /**
   * Indicates whether administrative password resets should be allowed to
   * bypass validity checks for the new password.
   *
   * @return  <CODE>true</CODE> if administrative password resets should be
   *          allowed to bypass validity checks for the new password, or
   *          <CODE>false</CODE> if not.
   */
  public boolean skipValidationForAdministrators()
  {
    assert debugEnter(CLASS_NAME, "skipValidationForAdministrators");

    return passwordPolicy.skipValidationForAdministrators();
  }



  /**
   * Indicates whether users should be required to change their passwords when
   * they first authenticate after their account has been added.
   *
   * @return  <CODE>true</CODE> if users should be required to change their
   *          passwords when they first authenticate after their account has
   *          been added, or <CODE>false</CODE> if not.
   */
  public boolean forceChangeOnAdd()
  {
    assert debugEnter(CLASS_NAME, "forceChangeOnAdd");

    return passwordPolicy.forceChangeOnAdd();
  }



  /**
   * Indicates whether users should be required to change their passwords after
   * they have been reset by an administrator.
   *
   * @return  <CODE>true</CODE> if users should be required to change their
   *          passwords after they have been reset by an administrator, or
   *          <CODE>false</CODE> if not.
   */
  public boolean forceChangeOnReset()
  {
    assert debugEnter(CLASS_NAME, "forceChangeOnReset");

    return passwordPolicy.forceChangeOnReset();
  }



  /**
   * Indicates whether user passwords will be allowed to expire even if the user
   * has never seen an expiration warning notification.
   *
   * @return  <CODE>true</CODE> if user passwords will be allowed to expire even
   *          if the user has never seen an expiration warning notification, or
   *          <CODE>false</CODE> if they will always be guaranteed at least one
   *          warning before the password expires.
   */
  public boolean expirePasswordsWithoutWarning()
  {
    assert debugEnter(CLASS_NAME, "expirePasswordsWithoutWarning");

    return passwordPolicy.expirePasswordsWithoutWarning();
  }



  /**
   * Retrieves the time that the user's password was last changed.
   *
   * @return  The time that the user's password was last changed.
   */
  public long getPasswordChangedTime()
  {
    assert debugEnter(CLASS_NAME, "getPasswordChangedTime");

    return passwordChangedTime;
  }



  /**
   * Retrieves time that this password policy state object was created.
   *
   * @return  The time that this password policy state object was created.
   */
  public long getCurrentTime()
  {
    assert debugEnter(CLASS_NAME, "getCurrentTime");

    return currentTime;
  }



  /**
   * Retrieves the generalized time representation of the time that this
   * password policy state object was created.
   *
   * @return  The generalized time representation of the time that this
   *          password policy state object was created.
   */
  public String getCurrentGeneralizedTime()
  {
    assert debugEnter(CLASS_NAME, "getCurrentGeneralizedTime");

    return currentGeneralizedTime;
  }



  /**
   * Sets a new value for the password changed time equal to the current time.
   */
  public void setPasswordChangedTime()
  {
    assert debugEnter(CLASS_NAME, "setPasswordChangedTime");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setPasswordChangedTime",
                   "Setting password changed time for user " + userDNString +
                   " to current time of " + currentTime);
    }

    if (passwordChangedTime != currentTime)
    {
      passwordChangedTime = currentTime;

      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_CHANGED_TIME_LC);
      if (type == null)
      {
        type = DirectoryServer.getDefaultAttributeType(
                                    OP_ATTR_PWPOLICY_CHANGED_TIME);
      }

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      String timeValue = GeneralizedTimeSyntax.format(passwordChangedTime);
      values.add(new AttributeValue(type, timeValue));

      Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_CHANGED_TIME, values);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (updateEntry)
      {
        userEntry.putAttribute(type, attrList);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE, a));
      }
    }
  }



  /**
   * Indicates whether the user account has been administratively disabled.
   *
   * @return  <CODE>true</CODE> if the user account has been administratively
   *          disabled, or <CODE>false</CODE> otherwise.
   */
  public boolean isDisabled()
  {
    assert debugEnter(CLASS_NAME, "isDisabled");

    if ((isDisabled == null) || (isDisabled == ConditionResult.UNDEFINED))
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_DISABLED, true);
      try
      {
        if (getBoolean(type, false))
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "isDisabled",
                         "User " + userDNString +
                         " is administratively disabled.");
          }

          isDisabled = ConditionResult.TRUE;
          return true;
        }
        else
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "isDisabled",
                         "User " + userDNString +
                         " is not administratively disabled.");
          }

          isDisabled = ConditionResult.FALSE;
          return false;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "isDisabled", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME, "isDisabled",
                       "User " + userDNString +" is considered " +
                       "administratively disabled because an error occurred " +
                       "while attempting to make the determination:  " +
                       stackTraceToSingleLineString(e) + ".");
        }

        isDisabled = ConditionResult.TRUE;
        return true;
      }
    }

    if (isDisabled == ConditionResult.FALSE)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isDisabled",
                     "Returning stored result of false for user " +
                     userDNString);
      }

      return false;
    }
    else
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isDisabled",
                     "Returning stored result of true for user " +
                     userDNString);
      }

      return true;
    }
  }



  /**
   * Updates the user entry to indicate whether user account has been
   * administratively disabled.
   *
   * @param  isDisabled  Indicates whether the user account has been
   *                     administratively disabled.
   */
  public void setDisabled(boolean isDisabled)
  {
    assert debugEnter(CLASS_NAME, "setDisabled", String.valueOf(isDisabled));

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setDisabled",
                   "Updating user " + userDNString +
                   " to set the disabled flag to " + isDisabled);
    }


    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_DISABLED, true);
    LinkedHashSet<AttributeValue> values;

    if (isDisabled)
    {
      if (this.isDisabled == ConditionResult.TRUE)
      {
        return;
      }

      this.isDisabled = ConditionResult.TRUE;
      values = new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(type, String.valueOf(isDisabled)));

      Attribute a = new Attribute(type, OP_ATTR_ACCOUNT_DISABLED, values);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (updateEntry)
      {
        userEntry.putAttribute(type, attrList);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE, a));
      }
    }
    else
    {
      if (this.isDisabled == ConditionResult.FALSE)
      {
        return;
      }

      this.isDisabled = ConditionResult.FALSE;
      values = new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(type, String.valueOf(isDisabled)));

      if (updateEntry)
      {
        userEntry.removeAttribute(type);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE,
                                           new Attribute(type)));
      }
    }
  }



  /**
   * Indicates whether the user's account is currently expired.
   *
   * @return  <CODE>true</CODE> if the user's account is expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isAccountExpired()
  {
    assert debugEnter(CLASS_NAME, "isAccountExpired");

    if ((isAccountExpired == null) ||
        (isAccountExpired == ConditionResult.UNDEFINED))
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_EXPIRATION_TIME,
                                            true);
      try
      {
        long expirationTime = getGeneralizedTime(type);
        if (expirationTime < 0)
        {
          // The user doesn't have an expiration time in their entry, so it
          // can't be expired.
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "isAccountExpired",
                         "The account for user " + userDNString +
                         " is not expired because there is no expiration " +
                         "time in the user's entry.");
          }

          isAccountExpired = ConditionResult.FALSE;
          return false;
        }
        else if (expirationTime > currentTime)
        {
          // The user does have an expiration time, but it hasn't arrived yet.
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "isAccountExpired",
                         "The account for user " + userDNString +
                         " is not expired because the expiration time has " +
                         "not yet arrived.");
          }

          isAccountExpired = ConditionResult.FALSE;
          return false;
        }
        else
        {
          // The user does have an expiration time, and it is in the past.
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME, "isAccountExpired",
                         "The account for user " + userDNString +
                         " is expired because the expiration time in that " +
                         "account has passed.");
          }

          isAccountExpired = ConditionResult.TRUE;
          return true;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "isAccountExpired", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME, "isAccountExpired",
                       "User " + userDNString +" is considered to have an " +
                       "expired account because an error occurred " +
                       "while attempting to make the determination:  " +
                       stackTraceToSingleLineString(e) + ".");
        }

        isAccountExpired = ConditionResult.TRUE;
        return true;
      }
    }


    if (isAccountExpired == ConditionResult.FALSE)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isAccountExpired",
                     "Returning stored result of false for user " +
                     userDNString);
      }

      return false;
    }
    else
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isAccountExpired",
                     "Returning stored result of true for user " +
                     userDNString);
      }

      return true;
    }
  }



  /**
   * Retrieves the set of times of failed authentication attempts for the user.
   *
   * @return  The set of times of failed authentication attempts for the user.
   */
  public List<Long> getAuthFailureTimes()
  {
    assert debugEnter(CLASS_NAME, "getAuthFailureTimes");

    if (authFailureTimes == null)
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
      if (type == null)
      {
        type = DirectoryServer.getDefaultAttributeType(
                                    OP_ATTR_PWPOLICY_FAILURE_TIME);
      }

      try
      {
        authFailureTimes = getGeneralizedTimes(type);


        // Remove any expired failures from the list.
        if (passwordPolicy.getLockoutFailureExpirationInterval() > 0)
        {
          LinkedHashSet<AttributeValue> values = null;

          long expirationTime = currentTime -
                    (passwordPolicy.getLockoutFailureExpirationInterval()*1000);
          Iterator<Long> iterator = authFailureTimes.iterator();
          while (iterator.hasNext())
          {
            long l = iterator.next();
            if (l < expirationTime)
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.INFO, CLASS_NAME,
                             "getAuthFailureTimes",
                             "Removing expired auth failure time " + l +
                             " for user " + userDNString);
              }

              iterator.remove();

              if (values == null)
              {
                values = new LinkedHashSet<AttributeValue>();
              }

              values.add(new AttributeValue(type,
                                            GeneralizedTimeSyntax.format(l)));
            }
          }

          if (values != null)
          {
            Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_FAILURE_TIME,
                                        values);
            ArrayList<Attribute> removeList = new ArrayList<Attribute>(1);
            removeList.add(a);


            if (authFailureTimes.isEmpty())
            {
              if (updateEntry)
              {
                userEntry.removeAttribute(type);
              }
            }
            else
            {
              LinkedHashSet<AttributeValue> keepValues =
                   new LinkedHashSet<AttributeValue>(authFailureTimes.size());
              for (Long l : authFailureTimes)
              {
                keepValues.add(new AttributeValue(type,
                                        GeneralizedTimeSyntax.format(l)));
              }

              ArrayList<Attribute> keepList = new ArrayList<Attribute>(1);
              keepList.add(new Attribute(type, OP_ATTR_PWPOLICY_FAILURE_TIME,
                                         keepValues));

              if (updateEntry)
              {
                userEntry.putAttribute(type, keepList);
              }
            }


            if (! updateEntry)
            {
              modifications.add(new Modification(ModificationType.DELETE, a));
            }
          }
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getAuthFailureTimes", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME,
                       "getAuthFailureTimes",
                       "Error while processing auth failure times for user " +
                       userDNString + ":  " + stackTraceToSingleLineString(e));
        }

        authFailureTimes = new ArrayList<Long>();

        if (updateEntry)
        {
          userEntry.removeAttribute(type);
        }
        else
        {
          modifications.add(new Modification(ModificationType.REPLACE,
                                             new Attribute(type)));
        }
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getAuthFailureTimes",
                   "Returning auth failure time list of " +
                   authFailureTimes.size() + " elements for user " +
                   userDNString);
    }

    return authFailureTimes;
  }



  /**
   * Updates the set of authentication failure times to include the current
   * time.
   */
  public void updateAuthFailureTimes()
  {
    assert debugEnter(CLASS_NAME, "updateAuthFailureTimes");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "updateAuthFailureTimes",
                   "Updating authentication failure times for user " +
                   userDNString);
    }


    List<Long> failureTimes = getAuthFailureTimes();
    long highestFailureTime = -1;
    for (Long l : failureTimes)
    {
      highestFailureTime = Math.max(l, highestFailureTime);
    }

    if (highestFailureTime >= currentTime)
    {
      highestFailureTime++;
    }
    else
    {
      highestFailureTime = currentTime;
    }
    failureTimes.add(highestFailureTime);

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_FAILURE_TIME);
    }

    LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(failureTimes.size());
    for (Long l : failureTimes)
    {
      values.add(new AttributeValue(type, GeneralizedTimeSyntax.format(l)));
    }

    Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_FAILURE_TIME, values);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);

    LinkedHashSet<AttributeValue> addValues =
         new LinkedHashSet<AttributeValue>(1);
    addValues.add(new AttributeValue(type,
                           GeneralizedTimeSyntax.format(highestFailureTime)));
    Attribute addAttr = new Attribute(type, OP_ATTR_PWPOLICY_FAILURE_TIME,
                                      addValues);

    if (updateEntry)
    {
      userEntry.putAttribute(type, attrList);
    }
    else
    {
      modifications.add(new Modification(ModificationType.ADD, addAttr));
    }
  }



  /**
   * Updates the user entry to remove any record of previous authentication
   * failures.
   */
  public void clearAuthFailureTimes()
  {
    assert debugEnter(CLASS_NAME, "clearAuthFailureTimes");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "clearAuthFailureTimes",
                   "Clearing authentication failure times for user " +
                   userDNString);
    }

    List<Long> failureTimes = getAuthFailureTimes();
    if (failureTimes.isEmpty())
    {
      return;
    }
    failureTimes.clear();

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_FAILURE_TIME);
    }

    if (updateEntry)
    {
      userEntry.removeAttribute(type);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE,
                                         new Attribute(type)));
    }
  }



  /**
   * Retrieves the maximum number of password failures that will be allowed
   * before a user account is locked.
   *
   * @return  The maximum number of password failures that will be allowed
   *          before a user account is locked, or zero if there is no maximum.
   */
  public int getMaxAllowedFailures()
  {
    assert debugEnter(CLASS_NAME, "getMaxAllowedFailures");

    return passwordPolicy.getLockoutFailureCount();
  }



  /**
   * Indicates whether the associated user should be considered locked out as a
   * result of too many authentication failures.
   *
   * @return  <CODE>true</CODE> if the user is currently locked out due to too
   *          many authentication failures, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToFailures()
  {
    assert debugEnter(CLASS_NAME, "lockedDueToFailures");


    int maxFailures = passwordPolicy.getLockoutFailureCount();
    if (maxFailures <= 0)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToFailures",
                     "Returning false for user " + userDNString +
                     " because lockout due to failures is not enabled.");
      }

      return false;
    }


    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    // Get the locked time from the user's entry.  If it's not there, then the
    // account is not locked.
    if (failureLockedTime == Long.MIN_VALUE)
    {
      try
      {
        failureLockedTime = getGeneralizedTime(type);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "lockedDueToFailures", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME,
                       "lockedDueToFailures",
                       "Returning true for user " + userDNString +
                       " because an error occurred:  " +
                       stackTraceToSingleLineString(e));
        }

        return true;
      }
    }

    if (failureLockedTime <= 0)
    {
      // There is no failure locked time, but that doesn't mean that the
      // account isn't locked anyway due to the maximum number of failures
      // (which may happen in certain cases due to synchronization latency).
      List<Long> failureTimes = getAuthFailureTimes();
      if ((failureTimes != null) && (failureTimes.size() >= maxFailures))
      {
        // The account isn't locked but should be, so do so now.
        lockDueToFailures();

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "lockedDueToFailures",
                       "Setting the lock for user " + userDNString +
                       " because there were enough preexisting failures even " +
                       "though there was no account locked time.");
        }

        return true;
      }



      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY,
                     DebugLogSeverity.INFO, CLASS_NAME, "lockedDueToFailures",
                     "Returning false for user " + userDNString +
                     " because there is no locked time.");
      }

      return false;
    }


    // There is a failure locked time, but it may be expired.  See if that's the
    // case.
    if (passwordPolicy.getLockoutDuration() > 0)
    {
      long unlockTime = failureLockedTime +
                        (1000*passwordPolicy.getLockoutDuration());
      if (unlockTime > currentTime)
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "lockedDueToFailures",
                       "Returning true for user " + userDNString +
                       " because there is a locked time and the lockout " +
                       "duration has not been reached.");

          secondsUntilUnlock = (int) (unlockTime - currentTime);
        }

        return true;
      }
      else
      {
        if (updateEntry)
        {
          userEntry.removeAttribute(type);
        }
        else
        {
          modifications.add(new Modification(ModificationType.REPLACE,
                                             new Attribute(type)));
        }

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "lockedDueToFailures",
                       "Returning false for user " + userDNString +
                       " because the existing lockout has expired.");
        }

        return false;
      }
    }
    else
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToFailures",
                     "Returning true for user " + userDNString +
                     " because there is a locked time and no lockout " +
                     "duration.");
      }

      return true;
    }
  }



  /**
   * Retrieves the length of time in seconds until the user's account is
   * automatically unlocked.  This should only be called after calling
   * <CODE>lockedDueToFailures</CODE>.
   *
   * @return  The length of time in seconds until the user's account is
   *          automatically unlocked, or -1 if the account is not locked or the
   *          lockout requires administrative action to clear.
   */
  public int getSecondsUntilUnlock()
  {
    assert debugEnter(CLASS_NAME, "getSecondsBeforeUnlock");

    if (secondsUntilUnlock < 0)
    {
      return -1;
    }
    else
    {
      return secondsUntilUnlock;
    }
  }



  /**
   * Updates the user account to indicate that it has been locked due to too
   * many authentication failures.
   */
  public void lockDueToFailures()
  {
    assert debugEnter(CLASS_NAME, "lockDueToFailures");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "lockDueToFailures",
                   "Locking user account " + userDNString +
                   " due to too many failures.");
    }

    failureLockedTime = currentTime;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(type,
                        GeneralizedTimeSyntax.format(failureLockedTime)));
    Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_LOCKED_TIME, values);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);

    if (updateEntry)
    {
      userEntry.putAttribute(type, attrList);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE, a));
    }
  }



  /**
   * Updates the user account to remove any record of a previous lockout due to
   * failed authentications.
   */
  public void clearFailureLockout()
  {
    assert debugEnter(CLASS_NAME, "clearFailureLock");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "clearFailureLockout",
                   "Clearing lockout failures for user " + userDNString);
    }

    if (! lockedDueToFailures())
    {
      return;
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    if (updateEntry)
    {
      userEntry.removeAttribute(type);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE,
                                         new Attribute(type)));
    }
  }



  /**
   * Retrieves the time that the user last authenticated to the Directory
   * Server.
   *
   * @return  The time that the user last authenticated to the Directory Server,
   *          or -1 if it cannot be determined.
   */
  public long getLastLoginTime()
  {
    assert debugEnter(CLASS_NAME, "getLastLoginTime");

    if (lastLoginTime == Long.MIN_VALUE)
    {
      AttributeType type   = passwordPolicy.getLastLoginTimeAttribute();
      String        format = passwordPolicy.getLastLoginTimeFormat();

      if ((type == null) || (format == null))
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "getLastLoginTime",
                       "Returning creation time of " + createTime +
                       " for user " + userDNString + " because no last login " +
                       "time will be maintained.");
        }

        lastLoginTime = createTime;
        return lastLoginTime;
      }

      List<Attribute> attrList = userEntry.getAttribute(type);
      if ((attrList == null) || attrList.isEmpty())
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "getLastLoginTime",
                       "Returning creation time of " + createTime +
                       " for user " + userDNString + " because no last login " +
                       "time value exists.");
        }

        lastLoginTime = createTime;
        return lastLoginTime;
      }

      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          String valueString = v.getStringValue();
          SimpleDateFormat dateFormat;

          try
          {
            dateFormat    = new SimpleDateFormat(format);
            lastLoginTime = dateFormat.parse(valueString).getTime();

            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.INFO,
                           CLASS_NAME, "getLastLoginTime",
                           "Returning last login time of " + lastLoginTime +
                           " for user " + userDNString + " decoded using " +
                           "current last login time format.");
            }

            return lastLoginTime;
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "getLastLoginTime", e);

            // This could mean that the last login time was encoded using a
            // previous format.
            for (String f : passwordPolicy.getPreviousLastLoginTimeFormats())
            {
              try
              {
                dateFormat    = new SimpleDateFormat(f);
                lastLoginTime = dateFormat.parse(valueString).getTime();

                if (debug)
                {
                  debugMessage(DebugLogCategory.PASSWORD_POLICY,
                               DebugLogSeverity.INFO,
                               CLASS_NAME, "getLastLoginTime",
                               "Returning last login time of " + lastLoginTime +
                               " for user " + userDNString + " decoded using " +
                               "previous last login time format of " + f);
                }

                return lastLoginTime;
              }
              catch (Exception e2)
              {
                assert debugException(CLASS_NAME, "getLastLoginTime", e2);
              }
            }


            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.WARNING,
                           CLASS_NAME, "getLastLoginTime",
                           "Returning creation time of " + createTime +
                           " for user " + userDNString + " because the last " +
                           "login time value " + valueString +
                           "could not be parsed using any known format.");
            }

            lastLoginTime = createTime;
            return lastLoginTime;
          }
        }
      }
    }

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getLastLoginTime",
                   "Returning previously calculated last login time of " +
                   lastLoginTime + " for user " + userDNString);
    }

    return lastLoginTime;
  }



  /**
   * Updates the user entry to set the current time as the last login time.
   */
  public void setLastLoginTime()
  {
    assert debugEnter(CLASS_NAME, "setLastLoginTime");

    AttributeType type   = passwordPolicy.getLastLoginTimeAttribute();
    String        format = passwordPolicy.getLastLoginTimeFormat();

    if ((type == null) || (format == null))
    {
      return;
    }

    String timestamp;
    try
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      timestamp = dateFormat.format(TimeThread.getDate());
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "setLastLoginTime", e);

      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.WARNING,
                     CLASS_NAME, "setLastLoginTime",
                     "Unable to set last login time for user " + userDNString +
                     " because an error occurred:  " +
                     stackTraceToSingleLineString(e));
      }

      return;
    }


    String existingTimestamp = getValue(type);
    if ((existingTimestamp != null) && timestamp.equals(existingTimestamp))
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "setLastLoginTime",
                     "Not updating last login time for user " + userDNString +
                     " because the new value matches the existing value.");
      }

      return;
    }


    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(type, timestamp));

    Attribute a = new Attribute(type, type.getNameOrOID(), values);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);

    if (updateEntry)
    {
      userEntry.putAttribute(type, attrList);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE, a));
    }

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setLastLoginTime",
                   "Updated the last login time for user " + userDNString +
                   " to " + timestamp);
    }
  }



  /**
   * Indicates whether the user's account is currently locked because it has
   * been idle for too long.
   *
   * @return  <CODE>true</CODE> if the user's account is locked because it has
   *          been idle for too long, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToIdleInterval()
  {
    assert debugEnter(CLASS_NAME, "lockedDueToIdleInterval");

    if ((isIdleLocked == null) || (isIdleLocked == ConditionResult.UNDEFINED))
    {
      if (passwordPolicy.getIdleLockoutInterval() <= 0)
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "lockedDueToIdleInterval",
                       "Returning false for user " + userDNString +
                       " because no idle lockout interval is defined.");
        }

        isIdleLocked = ConditionResult.FALSE;
        return false;
      }

      long lockTime = currentTime -
                      (passwordPolicy.getIdleLockoutInterval()*1000);
      long lastLoginTime = getLastLoginTime();
      if (lastLoginTime > 0)
      {
        if (lastLoginTime > lockTime)
        {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "lockedDueToIdleInterval",
                       "Returning false for user " + userDNString +
                       " because the last login time is in an acceptable " +
                         "window.");
        }

          isIdleLocked = ConditionResult.FALSE;
          return false;
        }
        else
        {
          if (passwordChangedTime > lockTime)
          {
            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.INFO, CLASS_NAME,
                           "lockedDueToIdleInterval",
                           "Returning false for user " + userDNString +
                           " because the password changed time is in an " +
                           "acceptable window.");
            }

            isIdleLocked = ConditionResult.FALSE;
            return false;
          }
          else
          {
            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.INFO, CLASS_NAME,
                           "lockedDueToIdleInterval",
                           "Returning true for user " + userDNString +
                           " because neither last login time nor password " +
                           "changed time are in an acceptable window.");
            }

            isIdleLocked = ConditionResult.TRUE;
            return true;
          }
        }
      }
      else
      {
        if (passwordChangedTime < lockTime)
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "lockedDueToIdleInterval",
                         "Returning true for user " + userDNString +
                         " because there is no last login time and the " +
                         "password changed time is not in an acceptable " +
                         "window.");
          }

          isIdleLocked = ConditionResult.TRUE;
          return true;
        }
        else
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "lockedDueToIdleInterval",
                         "Returning false for user " + userDNString +
                         " because there is no last login time but the " +
                         "password changed time is in an acceptable window.");
          }

          isIdleLocked = ConditionResult.FALSE;
          return false;
        }
      }
    }


    if (isIdleLocked == ConditionResult.TRUE)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToIdleInterval",
                     "Returning stored result of true for user " +
                     userDNString);
      }

      return true;
    }
    else
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToIdleInterval",
                     "Returning stored result of false for user " +
                     userDNString);
      }

      return false;
    }
  }



  /**
   * Indicates whether the user's password must be changed before any other
   * operation can be performed.
   *
   * @return  <CODE>true</CODE> if the user's password must be changed before
   *          any other operation can be performed.
   */
  public boolean mustChangePassword()
  {
    assert debugEnter(CLASS_NAME, "mustChangePassword");

    if ((mustChangePassword == null) ||
        (mustChangePassword == ConditionResult.UNDEFINED))
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
      if (type == null)
      {
        type = DirectoryServer.getDefaultAttributeType(
                                    OP_ATTR_PWPOLICY_RESET_REQUIRED);
      }

      try
      {
        boolean resetRequired = getBoolean(type, false);
        if (resetRequired)
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "mustChangePassword",
                         "Returning true for user " + userDNString);
          }

          mustChangePassword = ConditionResult.TRUE;
          return true;
        }
        else
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "mustChangePassword",
                         "Returning false for user " + userDNString);
          }

          mustChangePassword = ConditionResult.FALSE;
          return false;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "mustChangePassword", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.INFO, CLASS_NAME,
                       "mustChangePassword",
                       "Returning true for user " + userDNString +
                       " because an unexpected error occurred:  " +
                       stackTraceToSingleLineString(e));
        }

        mustChangePassword = ConditionResult.TRUE;
        return true;
      }
    }


    if (mustChangePassword == ConditionResult.TRUE)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "mustChangePassword",
                     "Returning stored result of true for user " +
                     userDNString);
      }

      return true;
    }
    else
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "mustChangePassword",
                     "Returning stored result of false for user " +
                     userDNString);
      }

      return false;
    }
  }



  /**
   * Updates the user entry to indicate whether the user's password must be
   * changed.
   *
   * @param  mustChangePassword  Indicates whether the user's password must be
   *                             changed.
   */
  public void setMustChangePassword(boolean mustChangePassword)
  {
    assert debugEnter(CLASS_NAME, "setMustChangePassword",
                      String.valueOf(mustChangePassword));

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setMustChangePassword",
                   "Updating user " + userDNString +
                   " to set the reset flag to " + mustChangePassword);
    }


    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_RESET_REQUIRED);
    }

    if (mustChangePassword)
    {
      if (this.mustChangePassword == ConditionResult.TRUE)
      {
        return;
      }

      this.mustChangePassword = ConditionResult.TRUE;

      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      values.add(new AttributeValue(type, String.valueOf(mustChangePassword)));
      Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_RESET_REQUIRED,
                                  values);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (updateEntry)
      {
        userEntry.putAttribute(type, attrList);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE, a));
      }
    }
    else
    {
      if (this.mustChangePassword == ConditionResult.FALSE)
      {
        return;
      }

      this.mustChangePassword = ConditionResult.FALSE;

      if (updateEntry)
      {
        userEntry.removeAttribute(type);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE,
                                           new Attribute(type)));
      }
    }
  }



  /**
   * Indicates whether the user's account is locked because the password has
   * been reset by an administrator but the user did not change the password in
   * a timely manner.
   *
   * @return  <CODE>true</CODE> if the user's account is locked because of the
   *          maximum reset age, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToMaximumResetAge()
  {
    assert debugEnter(CLASS_NAME, "lockedDueToMaximumResetAge");

    if (passwordPolicy.getMaximumPasswordResetAge() <= 0)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToMaximumResetAge",
                     "Returning false for user " + userDNString +
                     " because there is no maximum reset age .");
      }

      return false;
    }

    if (! mustChangePassword())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "lockedDueToMaximumResetAge",
                     "Returning false for user " + userDNString +
                     " because the user's password has not been reset.");
      }

      return false;
    }

    long maxResetTime = passwordChangedTime +
                        (1000 * passwordPolicy.getMaximumPasswordResetAge());
    boolean locked = (maxResetTime < currentTime);

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "lockedDueToMaximumResetAge",
                   "Returning " + locked + " for user " + userDNString +
                   " after comparing the current and max reset times.");
    }

    return locked;
  }



  /**
   * Retrieves the time that the user's password should expire (if the
   * expiration is in the future) or did expire (if the expiration was in the
   * past).  Note that this method should be called after the
   * <CODE>lockedDueToMaximumResetAge</CODE> method because grace logins will
   * not be allowed in the case that the maximum reset age has passed whereas
   * they may be used for expiration due to maximum password age or forced
   * change time.
   *
   * @return  The time that the user's password should/did expire, or -1 if it
   *          should not expire.
   */
  public long getPasswordExpirationTime()
  {
    assert debugEnter(CLASS_NAME, "getPasswordExpirationTime");

    if (expirationTime == Long.MIN_VALUE)
    {
      expirationTime = Long.MAX_VALUE;

      boolean checkWarning = false;

      int maxAge = passwordPolicy.getMaximumPasswordAge();
      if (maxAge > 0)
      {
        long expTime = passwordChangedTime + (1000*maxAge);
        if (expTime < expirationTime)
        {
          expirationTime = expTime;
          checkWarning   = true;
        }
      }

      int maxResetAge = passwordPolicy.getMaximumPasswordResetAge();
      if (mustChangePassword() && (maxResetAge > 0))
      {
        long expTime = passwordChangedTime + (1000*maxResetAge);
        if (expTime < expirationTime)
        {
          expirationTime = expTime;
          checkWarning   = false;
        }
      }

      long mustChangeTime = passwordPolicy.getRequireChangeByTime();
      if (mustChangeTime > 0)
      {
        long reqChangeTime = getRequiredChangeTime();
        if ((reqChangeTime != mustChangeTime) &&
            (mustChangeTime < expirationTime))
        {
          expirationTime = mustChangeTime;
          checkWarning   = true;
        }
      }

      if (expirationTime == Long.MAX_VALUE)
      {
        expirationTime    = -1;
        shouldWarn        = ConditionResult.FALSE;
        isFirstWarning    = ConditionResult.FALSE;
        isPasswordExpired = ConditionResult.FALSE;
        mayUseGraceLogin  = ConditionResult.TRUE;
      }
      else if (checkWarning)
      {
        mayUseGraceLogin = ConditionResult.TRUE;

        int warningInterval = passwordPolicy.getWarningInterval();
        if (warningInterval > 0)
        {
          long shouldWarnTime = expirationTime - (warningInterval*1000);
          if (shouldWarnTime > currentTime)
          {
            // The warning time is in the future, so we know the password isn't
            // expired.
            shouldWarn        = ConditionResult.FALSE;
            isFirstWarning    = ConditionResult.FALSE;
            isPasswordExpired = ConditionResult.FALSE;
          }
          else
          {
            // We're at least in the warning period, but the password may be
            // expired.
            long warnedTime = getWarnedTime();

            if (expirationTime > currentTime)
            {
              // The password is not expired but we should warn the user.
              shouldWarn        = ConditionResult.TRUE;
              isPasswordExpired = ConditionResult.FALSE;

              if (warnedTime < 0)
              {
                isFirstWarning = ConditionResult.TRUE;
                setWarnedTime();

                if (! passwordPolicy.expirePasswordsWithoutWarning())
                {
                  expirationTime = currentTime + (warningInterval*1000);
                }
              }
              else
              {
                isFirstWarning = ConditionResult.FALSE;

                if (! passwordPolicy.expirePasswordsWithoutWarning())
                {
                  expirationTime = warnedTime + (warningInterval*1000);
                }
              }
            }
            else
            {
              // The expiration time has passed, but we may not actually be
              // expired if the user has not yet seen a warning.
              if (passwordPolicy.expirePasswordsWithoutWarning())
              {
                shouldWarn        = ConditionResult.FALSE;
                isFirstWarning    = ConditionResult.FALSE;
                isPasswordExpired = ConditionResult.TRUE;
              }
              else if (warnedTime > 0)
              {
                expirationTime = warnedTime + (warningInterval*1000);
                if (expirationTime > currentTime)
                {
                  shouldWarn        = ConditionResult.TRUE;
                  isFirstWarning    = ConditionResult.FALSE;
                  isPasswordExpired = ConditionResult.FALSE;
                }
                else
                {
                  shouldWarn        = ConditionResult.FALSE;
                  isFirstWarning    = ConditionResult.FALSE;
                  isPasswordExpired = ConditionResult.TRUE;
                }
              }
              else
              {
                shouldWarn        = ConditionResult.TRUE;
                isFirstWarning    = ConditionResult.TRUE;
                isPasswordExpired = ConditionResult.FALSE;
                expirationTime    = currentTime + (warningInterval*1000);
              }
            }
          }
        }
        else
        {
          // There will never be a warning, and the user's password may be
          // expired.
          shouldWarn     = ConditionResult.FALSE;
          isFirstWarning = ConditionResult.FALSE;

          if (currentTime > expirationTime)
          {
            isPasswordExpired = ConditionResult.TRUE;
          }
          else
          {
            isPasswordExpired = ConditionResult.FALSE;
          }
        }
      }
      else
      {
        mayUseGraceLogin = ConditionResult.FALSE;
        shouldWarn       = ConditionResult.FALSE;
        isFirstWarning   = ConditionResult.FALSE;

        if (expirationTime < currentTime)
        {
          isPasswordExpired = ConditionResult.TRUE;
        }
        else
        {
          isPasswordExpired = ConditionResult.FALSE;
        }
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getPasswordExpirationTime",
                   "Returning password expiration time of " + expirationTime +
                   " for user " + userDNString);
    }

    secondsUntilExpiration = (int) (expirationTime - currentTime);
    return expirationTime;
  }



  /**
   * Indicates whether the user's password is currently expired.
   *
   * @return  <CODE>true</CODE> if the user's password is currently expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isPasswordExpired()
  {
    assert debugEnter(CLASS_NAME, "isPasswordExpired");

    if ((isPasswordExpired == null) ||
        (isPasswordExpired == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    if (isPasswordExpired == ConditionResult.TRUE)
    {
      return true;
    }
    else
    {
      return false;
    }
  }



  /**
   * Indicates whether users will be allowed to change their passwords if they
   * are expired.
   *
   * @return  <CODE>true</CODE> if users will be allowed to change their
   *          passwords if they are expired, or <CODE>false</CODE> if not.
   */
  public boolean allowExpiredPasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "allowExpiredPasswordChanges");

    return passwordPolicy.allowExpiredPasswordChanges();
  }



  /**
   * Indicates whether the user's last password change was within the minimum
   * password age.
   *
   * @return  <CODE>true</CODE> if the password minimum age is nonzero, the
   *          account is not in force-change mode, and the last password change
   *          was within the minimum age, or <CODE>false</CODE> otherwise.
   */
  public boolean isWithinMinimumAge()
  {
    assert debugEnter(CLASS_NAME, "isWithinMinimumAge");

    int minAge = passwordPolicy.getMinimumPasswordAge();
    if (minAge <= 0)
    {
      // There is no minimum age, so the user isn't in it.
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isWithinMinimumAge",
                     "Returning false because there is no minimum age.");
      }

      return false;
    }
    else if ((passwordChangedTime + (minAge*1000)) < currentTime)
    {
      // It's been long enough since the user changed their password.
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isWithinMinimumAge",
                     "Returning false because the minimum age has expired.");
      }

      return false;
    }
    else if (mustChangePassword())
    {
      // The user is in a must-change mode, so the minimum age doesn't apply.
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "isWithinMinimumAge",
                     "Returning false because the account is in a " +
                     "must-change state.");
      }

      return false;
    }
    else
    {
      // The user is within the minimum age.
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.WARNING,
                     CLASS_NAME, "isWithinMinimumAge", "Returning true.");
      }

      return true;
    }
  }



  /**
   * Indicates whether the user may use a grace login if the password is expired
   * and there is at least one grace login remaining.  Note that this does not
   * check to see if the user's password is expired, does not verify that there
   * are any remaining grace logins, and does not update the set of grace login
   * times.
   *
   * @return  <CODE>true</CODE> if the user may use a grace login if the
   *          password is expired and there is at least one grace login
   *          remaining, or <CODE>false</CODE> if the user may not use a grace
   *          login for some reason.
   */
  public boolean mayUseGraceLogin()
  {
    assert debugEnter(CLASS_NAME, "mayUseGraceLogin");

    if ((mayUseGraceLogin == null) ||
        (mayUseGraceLogin == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    if (mayUseGraceLogin == ConditionResult.TRUE)
    {
      return true;
    }
    else
    {
      return false;
    }
  }



  /**
   * Indicates whether the user should receive a warning notification that the
   * password is about to expire.
   *
   * @return  <CODE>true</CODE> if the user should receive a warning
   *          notification that the password is about to expire, or
   *          <CODE>false</CODE> if not.
   */
  public boolean shouldWarn()
  {
    assert debugEnter(CLASS_NAME, "shouldWarn");

    if ((shouldWarn == null) || (shouldWarn == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    if (shouldWarn == ConditionResult.TRUE)
    {
      return true;
    }
    else
    {
      return false;
    }
  }



  /**
   * Indicates whether the warning that the user should receive would be the
   * first warning for the user.
   *
   * @return  <CODE>true</CODE> if the warning that should be sent to the user
   *          would be the first warning, or <CODE>false</CODE> if not.
   */
  public boolean isFirstWarning()
  {
    assert debugEnter(CLASS_NAME, "isFirstWarning");

    if ((isFirstWarning == null) ||
        (isFirstWarning == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    if (isFirstWarning == ConditionResult.TRUE)
    {
      return true;
    }
    else
    {
      return false;
    }
  }



  /**
   * Retrieves the length of time in seconds until the user's password expires.
   *
   * @return  The length of time in seconds until the user's password expires,
   *          0 if the password is currently expired, or -1 if the password
   *          should not expire.
   */
  public int getSecondsUntilExpiration()
  {
    assert debugEnter(CLASS_NAME, "getSecondsUntilExpiration");

    long expirationTime = getPasswordExpirationTime();
    if (expirationTime < 0)
    {
      return -1;
    }
    else if (expirationTime < currentTime)
    {
      return 0;
    }
    else
    {
      return (int) ((expirationTime - currentTime) / 1000);
    }
  }



  /**
   * Retrieves the timestamp for the last required change time that the user
   * complied with.
   *
   * @return  The timestamp for the last required change time that the user
   *          complied with, or -1 if the user's password has not been changed
   *          in compliance with this configuration.
   */
  public long getRequiredChangeTime()
  {
    assert debugEnter(CLASS_NAME, "getRequiredChangeTime");

    if (requiredChangeTime == Long.MIN_VALUE)
    {
      AttributeType type = DirectoryServer.getAttributeType(
                                OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME,
                                true);
      try
      {
        requiredChangeTime = getGeneralizedTime(type);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getRequiredChangeTime", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME,
                       "getRequiredChangeTime",
                       "An error occurred while attempting to determine the " +
                       "required change time for user " + userDNString + ":  " +
                       stackTraceToSingleLineString(e));
        }

        requiredChangeTime = -1;
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getRequiredChangeTime",
                   "Returning required change time of " + requiredChangeTime +
                   " for user " + userDNString);
    }

    return requiredChangeTime;
  }



  /**
   * Updates the user entry with a timestamp indicating that the password has
   * been changed in accordance with the require change time.
   */
  public void setRequiredChangeTime()
  {
    assert debugEnter(CLASS_NAME, "setRequiredChangeTime");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setRequiredChangeTime",
                   "Updating required change time for user " + userDNString);
    }


    long reqChangeTime = getRequiredChangeTime();
    if (reqChangeTime != passwordPolicy.getRequireChangeByTime())
    {
      reqChangeTime = passwordPolicy.getRequireChangeByTime();
      requiredChangeTime = reqChangeTime;

      AttributeType type = DirectoryServer.getAttributeType(
                                OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME,
                                true);
      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(1);
      String timeValue = GeneralizedTimeSyntax.format(passwordChangedTime);
      values.add(new AttributeValue(type, timeValue));

      Attribute a = new Attribute(type,
                                  OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME,
                                  values);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (updateEntry)
      {
        userEntry.putAttribute(type, attrList);
      }
      else
      {
        modifications.add(new Modification(ModificationType.REPLACE, a));
      }
    }
  }



  /**
   * Retrieves the time that the user was first warned about an upcoming
   * expiration.
   *
   * @return  The time that the user was first warned about an upcoming
   *          expiration, or -1 if the user has not been warned.
   */
  public long getWarnedTime()
  {
    assert debugEnter(CLASS_NAME, "getWarnedTime");


    if (warnedTime == Long.MIN_VALUE)
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
      try
      {
        warnedTime = getGeneralizedTime(type);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getWarnedTime", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME, "getWarnedTime",
                       "Unable to decode the warned time for user " +
                       userDNString + ":  " + stackTraceToSingleLineString(e));
        }

        warnedTime = -1;
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getWarnedTime",
                   "Returning a warned time of " + warnedTime + " for user " +
                   userDNString);
    }

    return warnedTime;
  }



  /**
   * Updates the user entry to set the warned time to the current time.
   */
  public void setWarnedTime()
  {
    assert debugEnter(CLASS_NAME, "setWarnedTime");

    long warnTime = getWarnedTime();
    if (warnTime == currentTime)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "setWarnedTime",
                     "Not updating warned time for user " + userDNString +
                     " because the warned time is the same as the current " +
                     "time.");
      }

      return;
    }

    warnedTime = currentTime;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(GeneralizedTimeSyntax.createGeneralizedTimeValue(currentTime));

    Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_WARNED_TIME, values);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);

    if (updateEntry)
    {
      userEntry.putAttribute(type, attrList);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE, a));
    }

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "setWarnedTime",
                   "Updated the warned time for user " + userDNString);
    }
  }



  /**
   * Updates the user entry to clear the warned time.
   */
  public void clearWarnedTime()
  {
    assert debugEnter(CLASS_NAME, "clearWarnedTime");

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
    if (updateEntry)
    {
      userEntry.removeAttribute(type);
    }
    else
    {
      Attribute a = new Attribute(type);
      modifications.add(new Modification(ModificationType.REPLACE, a));
    }

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "clearWarnedTime",
                   "Cleared the warned time for user " + userDNString);
    }
  }



  /**
   * Retrieves the maximum number of grace logins that the user will be allowed
   * according to the associated password policy.
   *
   * @return  The maximum number of grace logins that the user will be allowed
   *          according to the associated password policy.
   */
  public int getMaxAllowedGraceLogins()
  {
    assert debugEnter(CLASS_NAME, "getMaxAllowedGraceLogins");

    return passwordPolicy.getGraceLoginCount();
  }



  /**
   * Retrieves the times that the user has authenticated to the server using a
   * grace login.
   *
   * @return  The times that the user has authenticated to the server using a
   *          grace login.
   */
  public List<Long> getGraceLoginTimes()
  {
    assert debugEnter(CLASS_NAME, "getGraceLoginTimes");


    if (graceLoginTimes == null)
    {
      AttributeType type = DirectoryServer.getAttributeType(
                                OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
      if (type == null)
      {
        type = DirectoryServer.getDefaultAttributeType(
                                    OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
      }

      try
      {
        graceLoginTimes = getGeneralizedTimes(type);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getGraceLoginTimes", e);

        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY,
                       DebugLogSeverity.WARNING, CLASS_NAME,
                       "getGraceLoginTimes",
                       "Error while processing grace login times for user " +
                       userDNString + ":  " + stackTraceToSingleLineString(e));
        }

        graceLoginTimes = new ArrayList<Long>();

        if (updateEntry)
        {
          userEntry.removeAttribute(type);
        }
        else
        {
          modifications.add(new Modification(ModificationType.REPLACE,
                                             new Attribute(type)));
        }
      }
    }


    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "getGraceLoginTimes",
                   "Returning grace login times for user " + userDNString);
    }

    return graceLoginTimes;
  }



  /**
   * Retrieves the number of grace logins that the user has left.
   *
   * @return  The number of grace logins that the user has left, or -1 if grace
   *          logins are not allowed.
   */
  public int getGraceLoginsRemaining()
  {
    assert debugEnter(CLASS_NAME, "getGraceLoginsRemaining");

    int maxGraceLogins = passwordPolicy.getGraceLoginCount();
    if (maxGraceLogins <= 0)
    {
      return -1;
    }

    List<Long> graceLoginTimes = getGraceLoginTimes();
    return maxGraceLogins - graceLoginTimes.size();
  }



  /**
   * Updates the set of grace login times for the user to include the current
   * time.
   */
  public void updateGraceLoginTimes()
  {
    assert debugEnter(CLASS_NAME, "updateGraceLoginTimes");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "updateGraceLoginTimes",
                   "Updating grace login times for user " + userDNString);
    }


    List<Long> graceTimes = getGraceLoginTimes();
    long highestGraceTime = -1;
    for (Long l : graceTimes)
    {
      highestGraceTime = Math.max(l, highestGraceTime);
    }

    if (highestGraceTime >= currentTime)
    {
      highestGraceTime++;
    }
    else
    {
      highestGraceTime = currentTime;
    }
    graceTimes.add(highestGraceTime);

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
    }

    LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>(graceTimes.size());
    for (Long l : graceTimes)
    {
      values.add(new AttributeValue(type, GeneralizedTimeSyntax.format(l)));
    }

    Attribute a = new Attribute(type, OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME,
                                values);
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);

    LinkedHashSet<AttributeValue> addValues =
         new LinkedHashSet<AttributeValue>(1);
    addValues.add(new AttributeValue(type,
                           GeneralizedTimeSyntax.format(highestGraceTime)));
    Attribute addAttr = new Attribute(type, OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME,
                                      addValues);

    if (updateEntry)
    {
      userEntry.putAttribute(type, attrList);
    }
    else
    {
      modifications.add(new Modification(ModificationType.ADD, addAttr));
    }
  }



  /**
   * Updates the user entry to remove any record of previous grace logins.
   */
  public void clearGraceLoginTimes()
  {
    assert debugEnter(CLASS_NAME, "clearGraceLoginTimes");

    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "clearGraceLoginTimes",
                   "Clearing grace login times for user " + userDNString);
    }

    List<Long> graceTimes = getGraceLoginTimes();
    if (graceTimes.isEmpty())
    {
      return;
    }
    graceTimes.clear();

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
    }

    if (updateEntry)
    {
      userEntry.removeAttribute(type);
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE,
                                         new Attribute(type)));
    }
  }



  /**
   * Indicates whether the provided password value matches any of the stored
   * passwords in the user entry.
   *
   * @param  password  The user-provided password to verify.
   *
   * @return  <CODE>true</CODE> if the provided password matches any of the
   *          stored password values, or <CODE>false</CODE> if not.
   */
  public boolean passwordMatches(ByteString password)
  {
    assert debugEnter(CLASS_NAME, "passwordMatches", "ByteString");


    List<Attribute> attrList =
         userEntry.getAttribute(passwordPolicy.getPasswordAttribute());
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "passwordMatches",
                     "Returning false because user " + userDNString +
                     " does not have any values for password attribute " +
                     passwordPolicy.getPasswordAttribute().getNameOrOID());
      }

      return false;
    }


    if (passwordPolicy.usesAuthPasswordSyntax())
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          try
          {
            StringBuilder[] pwComponents =
                 AuthPasswordSyntax.decodeAuthPassword(v.getStringValue());
            PasswordStorageScheme scheme =
                 DirectoryServer.getAuthPasswordStorageScheme(
                                      pwComponents[0].toString());
            if (scheme == null)
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "passwordMatches",
                             "User entry " + userDNString + " contains a " +
                             "password with scheme " + pwComponents[0] +
                             " that is not defined in the server.");
              }

              continue;
            }

            if (scheme.authPasswordMatches(password, pwComponents[1].toString(),
                                           pwComponents[2].toString()))
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.INFO, CLASS_NAME,
                             "passwordMatches",
                             "Returning true for user " + userDNString +
                             " because the provided password matches a " +
                             "value encoded with scheme " + pwComponents[0]);
              }

              return true;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "passwordMatches", e);

            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.ERROR, CLASS_NAME,
                           "passwordMatches",
                           "An error occurred while attempting to process a " +
                           "password value for user " + userDNString + ":  " +
                           stackTraceToSingleLineString(e));
            }
          }
        }
      }
    }
    else
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          try
          {
            String[] pwComponents =
                 UserPasswordSyntax.decodeUserPassword(v.getStringValue());
            PasswordStorageScheme scheme =
                 DirectoryServer.getPasswordStorageScheme(pwComponents[0]);
            if (scheme == null)
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "passwordMatches",
                             "User entry " + userDNString + " contains a " +
                             "password with scheme " + pwComponents[0] +
                             " that is not defined in the server.");
              }

              continue;
            }

            if (scheme.passwordMatches(password,
                                       new ASN1OctetString(pwComponents[1])))
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.INFO, CLASS_NAME,
                             "passwordMatches",
                             "Returning true for user " + userDNString +
                             " because the provided password matches a " +
                             "value encoded with scheme " + pwComponents[0]);
              }

              return true;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "passwordMatches", e);

            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.ERROR, CLASS_NAME,
                           "passwordMatches",
                           "An error occurred while attempting to process a " +
                           "password value for user " + userDNString + ":  " +
                           stackTraceToSingleLineString(e));
            }
          }
        }
      }
    }


    // If we've gotten here, then we couldn't find a match.
    if (debug)
    {
      debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                   CLASS_NAME, "passwordMatches",
                   "Returning false because the provided password does not " +
                   "match any of the stored password values for user " +
                   userDNString);
    }

    return false;
  }



  /**
   * Indicates whether the user's password is stored using the auth password
   * syntax or the user password syntax.
   *
   * @return  <CODE>true</CODE> if the user's password is stored using the auth
   *          password syntax, or <CODE>false</CODE> if it is stored using the
   *          user password syntax.
   */
  public boolean usesAuthPasswordSyntax()
  {
    assert debugEnter(CLASS_NAME, "usesAuthPasswordSyntax");

    return passwordPolicy.usesAuthPasswordSyntax();
  }



  /**
   * Indicates whether the provided password value is pre-encoded.
   *
   * @param  passwordValue  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided password value is pre-encoded,
   *          or <CODE>false</CODE> if it is not.
   */
  public boolean passwordIsPreEncoded(ByteString passwordValue)
  {
    assert debugEnter(CLASS_NAME, "isPreEncoded", "ByteString");

    if (passwordPolicy.usesAuthPasswordSyntax())
    {
      return AuthPasswordSyntax.isEncoded(passwordValue);
    }
    else
    {
      return UserPasswordSyntax.isEncoded(passwordValue);
    }
  }



  /**
   * Encodes the provided password using the default storage schemes (using the
   * appropriate syntax for the password attribute).
   *
   * @param  password  The password to be encoded.
   *
   * @return  The password encoded using the default schemes.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the password.
   */
  public List<ByteString> encodePassword(ByteString password)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "encodePassword", "ByteString");

    List<PasswordStorageScheme> schemes =
         passwordPolicy.getDefaultStorageSchemes();
    List<ByteString> encodedPasswords =
         new ArrayList<ByteString>(schemes.size());

    if (passwordPolicy.usesAuthPasswordSyntax())
    {
      for (PasswordStorageScheme s : schemes)
      {
        encodedPasswords.add(s.encodeAuthPassword(password));
      }
    }
    else
    {
      for (PasswordStorageScheme s : schemes)
      {
        encodedPasswords.add(s.encodePasswordWithScheme(password));
      }
    }

    return encodedPasswords;
  }



  /**
   * Indicates whether the provided password appears to be acceptable according
   * to the password validators.
   *
   * @param  operation      The operation that provided the password.
   * @param  userEntry      The user entry in which the password is used.
   * @param  password       The password to be validated.
   * @param  invalidReason  A buffer that may be used to hold the invalid reason
   *                        if the password is rejected.
   *
   * @return  <CODE>true</CODE> if the password is acceptable for use, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean passwordIsAcceptable(Operation operation, Entry userEntry,
                                      ByteString password,
                                      StringBuilder invalidReason)
  {
    assert debugEnter(CLASS_NAME, "passwordIsAcceptable",
                      String.valueOf(operation), String.valueOf(userEntry),
                      "ByteString", "StringBuilder");

    for (DN validatorDN : passwordPolicy.getPasswordValidators().keySet())
    {
      PasswordValidator validator =
           passwordPolicy.getPasswordValidators().get(validatorDN);

      if (! validator.passwordIsValid(password, operation, userEntry,
                                      invalidReason))
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "passwordIsAcceptable",
                       "The password provided for user " + userDNString +
                       " failed the " + validatorDN.toString() +
                       " password validator.");
        }

        return false;
      }
      else
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "passwordIsAcceptable",
                       "The password provided for user " + userDNString +
                       " passed the " + validatorDN.toString() +
                       " password validator.");
        }
      }
    }

    return true;
  }



  /**
   * Performs any processing that may be necessary to remove deprecated storage
   * schemes from the user's entry that match the provided password and
   * re-encodes them using the default schemes.
   *
   * @param  password  The clear-text password provided by the user.
   */
  public void handleDeprecatedStorageSchemes(ByteString password)
  {
    assert debugEnter(CLASS_NAME, "handleDeprecatedStorageSchemes",
                      "ByteString");

    if (passwordPolicy.getDefaultStorageSchemes().isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "handleDeprecatedStorageSchemes",
                     "Doing nothing for user " + userDNString +
                     " because no deprecated storage schemes have been " +
                     "defined.");
      }

      return;
    }


    AttributeType type = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = userEntry.getAttribute(type);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                     CLASS_NAME, "handleDeprecatedStorageSchemes",
                     "Doing nothing for entry " + userDNString +
                     " because no password values were found.");
      }

      return;
    }


    HashSet<String> existingDefaultSchemes = new HashSet<String>();
    LinkedHashSet<AttributeValue> removedValues =
         new LinkedHashSet<AttributeValue>();
    LinkedHashSet<AttributeValue> updatedValues =
         new LinkedHashSet<AttributeValue>();

    if (passwordPolicy.usesAuthPasswordSyntax())
    {
      for (Attribute a : attrList)
      {
        Iterator<AttributeValue> iterator = a.getValues().iterator();
        while (iterator.hasNext())
        {
          AttributeValue v = iterator.next();

          try
          {
            StringBuilder[] pwComponents =
                 AuthPasswordSyntax.decodeAuthPassword(v.getStringValue());
            String schemeName = pwComponents[0].toString();
            PasswordStorageScheme scheme =
                 DirectoryServer.getAuthPasswordStorageScheme(schemeName);
            if (scheme == null)
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "handleDeprecatedStorageSchemes",
                             "Skipping password value for user " +
                             userDNString + " because the associated storage " +
                             "scheme \"" + schemeName +
                             "\" is not configured for use.");
              }

              continue;
            }

            if (scheme.authPasswordMatches(password, pwComponents[1].toString(),
                                           pwComponents[2].toString()))
            {
              if (passwordPolicy.isDefaultStorageScheme(schemeName))
              {
                existingDefaultSchemes.add(schemeName);
                updatedValues.add(v);
              }
              else if (passwordPolicy.isDeprecatedStorageScheme(schemeName))
              {
                if (debug)
                {
                  debugMessage(DebugLogCategory.PASSWORD_POLICY,
                               DebugLogSeverity.INFO,
                               CLASS_NAME, "handleDeprecatedStorageSchemes",
                               "Marking password with scheme " +
                               pwComponents[0] + " for removal from user " +
                               "entry " + userDNString);
                }

                iterator.remove();
                removedValues.add(v);
              }
              else
              {
                updatedValues.add(v);
              }
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "handleDeprecatedStorageSchemes",
                                  e);

            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.WARNING, CLASS_NAME,
                           "handleDeprecatedStorageSchemes",
                           "Skipping password value for user " + userDNString +
                           " because an error occurred while attempting " +
                           "to decode it based on the user password syntax:  " +
                           stackTraceToSingleLineString(e));
            }
          }
        }
      }

      if (removedValues.isEmpty())
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "handleDeprecatedStorageSchemes",
                       "User entry " + userDNString + " does not have any " +
                       "password values encoded using deprecated schemes.");
        }
      }
      else
      {
        LinkedHashSet<AttributeValue> addedValues = new
             LinkedHashSet<AttributeValue>();
        for (PasswordStorageScheme s :
             passwordPolicy.getDefaultStorageSchemes())
        {
          if (! existingDefaultSchemes.contains(
                     toLowerCase(s.getStorageSchemeName())))
          {
            try
            {
              ByteString encodedPassword = s.encodeAuthPassword(password);
              AttributeValue v = new AttributeValue(type, encodedPassword);
              addedValues.add(v);
              updatedValues.add(v);
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME,
                                    "handleDeprecatedStorageSchemes", e);

              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "handleDeprecatedStorageSchemes",
                             "Unable to encode password for user " +
                             userDNString + " using default scheme " +
                             s.getStorageSchemeName() + ":  " +
                             stackTraceToSingleLineString(e));
              }
            }
          }
        }

        if (updatedValues.isEmpty())
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.WARNING, CLASS_NAME,
                         "handleDeprecatedStorageSchemes",
                         "Not updating user entry " + userDNString +
                         " because removing deprecated schemes would leave " +
                         "the user without a password.");
          }

          return;
        }
        else
        {
          Attribute a = new Attribute(type, type.getNameOrOID(), removedValues);
          if (! updateEntry)
          {
            modifications.add(new Modification(ModificationType.DELETE, a));
          }

          if (! addedValues.isEmpty())
          {
            Attribute a2 = new Attribute(type, type.getNameOrOID(),
                                         addedValues);
            if (! updateEntry)
            {
              modifications.add(new Modification(ModificationType.ADD, a2));
            }
          }

          ArrayList<Attribute> newList = new ArrayList<Attribute>(1);
          newList.add(new Attribute(type, type.getNameOrOID(), updatedValues));
          if (updateEntry)
          {
            userEntry.putAttribute(type, newList);
          }

          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "handleDeprecatedStorageSchemes",
                         "Updating user entry " + userDNString +
                         "to replace password values encoded with deprecated " +
                         "schemes with values encoded with the default " +
                         "schemes.");
          }
        }
      }
    }
    else
    {
      for (Attribute a : attrList)
      {
        Iterator<AttributeValue> iterator = a.getValues().iterator();
        while (iterator.hasNext())
        {
          AttributeValue v = iterator.next();

          try
          {
            String[] pwComponents =
                 UserPasswordSyntax.decodeUserPassword(v.getStringValue());
            PasswordStorageScheme scheme =
                 DirectoryServer.getPasswordStorageScheme(pwComponents[0]);
            if (scheme == null)
            {
              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "handleDeprecatedStorageSchemes",
                             "Skipping password value for user " +
                             userDNString + " because the associated storage " +
                             "scheme \"" + pwComponents[0] +
                             "\" is not configured for use.");
              }

              continue;
            }

            if (scheme.passwordMatches(password,
                                       new ASN1OctetString(pwComponents[1])))
            {
              if (passwordPolicy.isDefaultStorageScheme(pwComponents[0]))
              {
                existingDefaultSchemes.add(pwComponents[0]);
                updatedValues.add(v);
              }
              else if (passwordPolicy.isDeprecatedStorageScheme(
                                           pwComponents[0]))
              {
                if (debug)
                {
                  debugMessage(DebugLogCategory.PASSWORD_POLICY,
                               DebugLogSeverity.INFO,
                               CLASS_NAME, "handleDeprecatedStorageSchemes",
                               "Marking password with scheme " +
                               pwComponents[0] + " for removal from user " +
                               "entry " + userDNString);
                }

                iterator.remove();
                removedValues.add(v);
              }
              else
              {
                updatedValues.add(v);
              }
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "handleDeprecatedStorageSchemes",
                                  e);

            if (debug)
            {
              debugMessage(DebugLogCategory.PASSWORD_POLICY,
                           DebugLogSeverity.WARNING, CLASS_NAME,
                           "handleDeprecatedStorageSchemes",
                           "Skipping password value for user " + userDNString +
                           " because an error occurred while attempting " +
                           "to decode it based on the user password syntax:  " +
                           stackTraceToSingleLineString(e));
            }
          }
        }
      }

      if (removedValues.isEmpty())
      {
        if (debug)
        {
          debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.INFO,
                       CLASS_NAME, "handleDeprecatedStorageSchemes",
                       "User entry " + userDNString + " does not have any " +
                       "password values encoded using deprecated schemes.");
        }
      }
      else
      {
        LinkedHashSet<AttributeValue> addedValues = new
             LinkedHashSet<AttributeValue>();
        for (PasswordStorageScheme s :
             passwordPolicy.getDefaultStorageSchemes())
        {
          if (! existingDefaultSchemes.contains(
                     toLowerCase(s.getStorageSchemeName())))
          {
            try
            {
              ByteString encodedPassword = s.encodePasswordWithScheme(password);
              AttributeValue v = new AttributeValue(type, encodedPassword);
              addedValues.add(v);
              updatedValues.add(v);
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME,
                                    "handleDeprecatedStorageSchemes", e);

              if (debug)
              {
                debugMessage(DebugLogCategory.PASSWORD_POLICY,
                             DebugLogSeverity.WARNING, CLASS_NAME,
                             "handleDeprecatedStorageSchemes",
                             "Unable to encode password for user " +
                             userDNString + " using default scheme " +
                             s.getStorageSchemeName() + ":  " +
                             stackTraceToSingleLineString(e));
              }
            }
          }
        }

        if (updatedValues.isEmpty())
        {
          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.WARNING, CLASS_NAME,
                         "handleDeprecatedStorageSchemes",
                         "Not updating user entry " + userDNString +
                         " because removing deprecated schemes would leave " +
                         "the user without a password.");
          }

          return;
        }
        else
        {
          Attribute a = new Attribute(type, type.getNameOrOID(), removedValues);
          if (! updateEntry)
          {
            modifications.add(new Modification(ModificationType.DELETE, a));
          }

          if (! addedValues.isEmpty())
          {
            Attribute a2 = new Attribute(type, type.getNameOrOID(),
                                         addedValues);
            if (! updateEntry)
            {
              modifications.add(new Modification(ModificationType.ADD, a2));
            }
          }

          ArrayList<Attribute> newList = new ArrayList<Attribute>(1);
          newList.add(new Attribute(type, type.getNameOrOID(), updatedValues));
          if (updateEntry)
          {
            userEntry.putAttribute(type, newList);
          }

          if (debug)
          {
            debugMessage(DebugLogCategory.PASSWORD_POLICY,
                         DebugLogSeverity.INFO, CLASS_NAME,
                         "handleDeprecatedStorageSchemes",
                         "Updating user entry " + userDNString +
                         "to replace password values encoded with deprecated " +
                         "schemes with values encoded with the default " +
                         "schemes.");
          }
        }
      }
    }
  }



  /**
   * Generates a new password for the user.
   *
   * @return  The new password that has been generated, or <CODE>null</CODE> if
   *          no password generator has been defined.
   *
   * @throws  DirectoryException  If an error occurs while attempting to
   *                              generate the new password.
   */
  public ByteString generatePassword()
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "generatePassword");

    PasswordGenerator generator = passwordPolicy.getPasswordGenerator();
    if (generator == null)
    {
      if (debug)
      {
        debugMessage(DebugLogCategory.PASSWORD_POLICY, DebugLogSeverity.WARNING,
                     CLASS_NAME, "generatePassword",
                     "Unable to generate a new password for user " +
                     userDNString + " because no password generator has been " +
                     "defined in the associated password policy.");
      }

      return null;
    }

    return generator.generatePassword(userEntry);
  }



  /**
   * Generates an account status notification for this user.
   *
   * @param  notificationType  The type for the account status notification.
   * @param  messageID         The unique ID for the notification.
   * @param  message           The human-readable message for the notification.
   */
  public void generateAccountStatusNotification(
                   AccountStatusNotificationType notificationType,
                   int messageID, String message)
  {
    assert debugEnter(CLASS_NAME, "generateAccountStatusNotification",
                      String.valueOf(notificationType),
                      String.valueOf(messageID), String.valueOf(message));


    Collection<AccountStatusNotificationHandler> handlers =
         passwordPolicy.getAccountStatusNotificationHandlers().values();
    if ((handlers == null) || handlers.isEmpty())
    {
      return;
    }

    for (AccountStatusNotificationHandler handler : handlers)
    {
      handler.handleStatusNotification(notificationType, messageID, message);
    }
  }



  /**
   * Generates an account status notification for this user.
   *
   * @param  notification  The account status notification that should be
   *                       generated.
   */
  public void generateAccountStatusNotification(
                   AccountStatusNotification notification)
  {
    assert debugEnter(CLASS_NAME, "generateAccountStatusNotification",
                      String.valueOf(notification));


    Collection<AccountStatusNotificationHandler> handlers =
         passwordPolicy.getAccountStatusNotificationHandlers().values();
    if ((handlers == null) || handlers.isEmpty())
    {
      return;
    }

    for (AccountStatusNotificationHandler handler : handlers)
    {
      handler.handleStatusNotification(notification);
    }
  }



  /**
   * Performs an internal modification to update the user's entry, if necessary.
   * This will do nothing if no modifications are required.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              internal modification.
   */
  public void updateUserEntry()
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "updateUserEntry");


    // If there are no modifications, then there's nothing to do.
    if (modifications.isEmpty())
    {
      return;
    }


    // Convert the set of modifications to a set of LDAP modifications.
    ArrayList<LDAPModification> modList = new ArrayList<LDAPModification>();
    for (Modification m : modifications)
    {
      modList.add(new LDAPModification(m.getModificationType(),
                                       new LDAPAttribute(m.getAttribute())));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation internalModify =
         conn.processModify(new ASN1OctetString(userDNString), modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      int    msgID   = MSGID_PWPSTATE_CANNOT_UPDATE_USER_ENTRY;
      String message = getMessage(msgID, userDNString,
                            String.valueOf(internalModify.getErrorMessage()));

      throw new DirectoryException(resultCode, message, msgID);
    }
  }
}

