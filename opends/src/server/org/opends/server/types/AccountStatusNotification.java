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
package org.opends.server.types;



import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opends.messages.Message;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;

import static org.opends.server.types.
                   AccountStatusNotificationProperty.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data type for storing information associated
 * with an account status notification.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class AccountStatusNotification
{
  // The notification type for this account status notification.
  private AccountStatusNotificationType notificationType;

  // The entry for the user to whom this notification applies.
  private Entry userEntry;

  // A set of additional properties that may be useful for this
  // notification.
  private Map<AccountStatusNotificationProperty,List<String>>
               notificationProperties;

  // A message that provides additional information for this account
  // status notification.
  private Message message;



  /**
   * Creates a new account status notification object with the
   * provided information.
   *
   * @param  notificationType        The type for this account status
   *                                 notification.
   * @param  userEntry               The entry for the user to whom
   *                                 this notification applies.
   * @param  message                 The human-readable message for
   *                                 this notification.
   * @param  notificationProperties  A set of properties that may
   *                                 include additional information
   *                                 about this notification.
   */
  public AccountStatusNotification(
              AccountStatusNotificationType notificationType,
              Entry userEntry, Message message,
              Map<AccountStatusNotificationProperty,List<String>>
                   notificationProperties)
  {
    this.notificationType = notificationType;
    this.userEntry        = userEntry;
    this.message          = message;

    if (notificationProperties == null)
    {
      this.notificationProperties =
           new HashMap<AccountStatusNotificationProperty,
                       List<String>>(0);
    }
    else
    {
      this.notificationProperties = notificationProperties;
    }
  }



  /**
   * Retrieves the notification type for this account status
   * notification.
   *
   * @return  The notification type for this account status
   *          notification.
   */
  public AccountStatusNotificationType getNotificationType()
  {
    return notificationType;
  }



  /**
   * Retrieves the DN of the user entry to which this notification
   * applies.
   *
   * @return  The DN of the user entry to which this notification
   *          applies.
   */
  public DN getUserDN()
  {
    return userEntry.getDN();
  }



  /**
   * Retrieves user entry for whom this notification applies.
   *
   * @return  The user entry for whom this notification applies.
   */
  public Entry getUserEntry()
  {
    return userEntry;
  }



  /**
   * Retrieves a message that provides additional information for this
   * account status notification.
   *
   * @return  A message that provides additional information for this
   *          account status notification.
   */
  public Message getMessage()
  {
    return message;
  }



  /**
   * Retrieves a set of properties that may provide additional
   * information for this account status notification.
   *
   * @return  A set of properties that may provide additional
   *          information for this account status notification.
   */
  public Map<AccountStatusNotificationProperty,List<String>>
              getNotificationProperties()
  {
    return notificationProperties;
  }



  /**
   * Retrieves the set of values for the specified account status
   * notification property.
   *
   * @param  property  The account status notification property for
   *                   which to retrieve the associated values.
   *
   * @return  The set of values for the specified account status
   *          notification property, or {@code null} if the specified
   *          property is not defined for this account status
   *          notification.
   */
  public List<String> getNotificationProperty(
                           AccountStatusNotificationProperty property)
  {
    return notificationProperties.get(property);
  }



  /**
   * Creates a set of account status notification properties from the
   * provided information.
   *
   * @param  pwPolicyState     The password policy state for the user
   *                           associated with the notification.
   * @param  tempLocked        Indicates whether the user's account
   *                           has been temporarily locked.
   * @param  timeToExpiration  The length of time in seconds until the
   *                           user's password expires, or -1 if it's
   *                           not about to expire.
   * @param  oldPasswords      The set of old passwords for the user,
   *                           or {@code null} if this is not
   *                           applicable.
   * @param  newPasswords      The set of new passwords for the user,
   *                           or {@code null} if this is not
   *                           applicable.
   *
   * @return  The created set of account status notification
   *          properties.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public static Map<AccountStatusNotificationProperty,List<String>>
                     createProperties(
                          PasswordPolicyState pwPolicyState,
                          boolean tempLocked, int timeToExpiration,
                          List<AttributeValue> oldPasswords,
                          List<AttributeValue> newPasswords)
  {
    HashMap<AccountStatusNotificationProperty,List<String>> props =
         new HashMap<AccountStatusNotificationProperty,
                     List<String>>(4);

    PasswordPolicy policy = pwPolicyState.getPolicy();

    ArrayList<String> propList = new ArrayList<String>(1);
    propList.add(policy.getConfigEntryDN().toString());
    props.put(PASSWORD_POLICY_DN, propList);

    if (tempLocked)
    {
      int secondsUntilUnlock = policy.getLockoutDuration();
      if (secondsUntilUnlock > 0)
      {
        propList = new ArrayList<String>(1);
        propList.add(String.valueOf(secondsUntilUnlock));
        props.put(SECONDS_UNTIL_UNLOCK, propList);

        propList = new ArrayList<String>(1);
        propList.add(
             secondsToTimeString(secondsUntilUnlock).toString());
        props.put(TIME_UNTIL_UNLOCK, propList);

        long unlockTime = System.currentTimeMillis() +
                          (1000*secondsUntilUnlock);
        propList = new ArrayList<String>(1);
        propList.add(new Date(unlockTime).toString());
        props.put(ACCOUNT_UNLOCK_TIME, propList);
      }
    }

    if (timeToExpiration >= 0)
    {
        propList = new ArrayList<String>(1);
        propList.add(String.valueOf(timeToExpiration));
        props.put(SECONDS_UNTIL_EXPIRATION, propList);

        propList = new ArrayList<String>(1);
        propList.add(
             secondsToTimeString(timeToExpiration).toString());
        props.put(TIME_UNTIL_EXPIRATION, propList);

        long expTime = System.currentTimeMillis() +
                       (1000*timeToExpiration);
        propList = new ArrayList<String>(1);
        propList.add(new Date(expTime).toString());
        props.put(PASSWORD_EXPIRATION_TIME, propList);
    }

    if ((oldPasswords != null) && (! oldPasswords.isEmpty()))
    {
      propList = new ArrayList<String>(oldPasswords.size());
      for (AttributeValue v : oldPasswords)
      {
        propList.add(v.getStringValue());
      }

      props.put(OLD_PASSWORD, propList);
    }

    if ((newPasswords != null) && (! newPasswords.isEmpty()))
    {
      propList = new ArrayList<String>(newPasswords.size());
      for (AttributeValue v : newPasswords)
      {
        propList.add(v.getStringValue());
      }

      props.put(NEW_PASSWORD, propList);
    }

    return props;
  }



  /**
   * Retrieves a string representation of this account status
   * notification.
   *
   * @return  A string representation of this account status
   *          notification.
   */
  public String toString()
  {
    return "AccountStatusNotification(type=" +
           notificationType.getName() + ",dn=" + userEntry.getDN() +
           ",message=" + message + ")";
  }
}

