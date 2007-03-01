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
package org.opends.server.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.ServerConstants.*;

/**
 This class is the interface between the password policy configurable component
 and a password policy state object. When a password policy entry is added to
 the configuration, an instance of this class is created and registered to
 manage subsequent modification to that configuration entry, including
 valiadating any proposed modification and applying an accepted modification.
 */
public class PasswordPolicyConfig
        implements ConfigurableComponent
{

  /**
   * The password policy object corresponding to the configuration entry. The
   * policy referenced by this field is assumed to be valid, hence any
   * changes resulting from a modification of the configuration entry must be
   * applied to a newly allocated instance and validated before updating this
   * reference to point to the new policy instance.
   */
  private PasswordPolicy currentPolicy;


  /**
   * Creates a new password policy configurable component to manage the provided
   * password policy object.
   *
   * @param policy The password policy object this object will manage.
   */
  public PasswordPolicyConfig(PasswordPolicy policy)
  {

    this.currentPolicy = policy;
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Finalize a password policy configuration handler.
   */
  public void finalizePasswordPolicyConfig()
  {

    DirectoryServer.deregisterConfigurableComponent(this);
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {

    return currentPolicy.getConfigEntryDN();
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {


    // Create a list of units and values that we can use to represent time
    // periods.
    LinkedHashMap<String,Double> timeUnits = new LinkedHashMap<String,Double>();
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_MINUTES_ABBR, 60D);
    timeUnits.put(TIME_UNIT_MINUTES_FULL, 60D);
    timeUnits.put(TIME_UNIT_HOURS_ABBR, (double) (60 * 60));
    timeUnits.put(TIME_UNIT_HOURS_FULL, (double) (60 * 60));
    timeUnits.put(TIME_UNIT_DAYS_ABBR, (double) (60 * 60 * 24));
    timeUnits.put(TIME_UNIT_DAYS_FULL, (double) (60 * 60 * 24));
    timeUnits.put(TIME_UNIT_WEEKS_ABBR, (double) (60 * 60 * 24 * 7));
    timeUnits.put(TIME_UNIT_WEEKS_FULL, (double) (60 * 60 * 24 * 7));


    PasswordPolicy policy = this.currentPolicy; // this field is volatile

    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_PWPOLICY_DESCRIPTION_PW_ATTR;
    String pwAttr = (policy.getPasswordAttribute() == null)
                    ? null
                    : policy.getPasswordAttribute().getNameOrOID();
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_PASSWORD_ATTRIBUTE,
                                           getMessage(msgID), false, false,
                                           false, pwAttr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_DEFAULT_STORAGE_SCHEMES;
    ArrayList<String> schemes = new ArrayList<String>();
    for (PasswordStorageScheme s : policy.getDefaultStorageSchemes())
    {
      schemes.add(s.getStorageSchemeName());
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_DEFAULT_SCHEME,
                                           getMessage(msgID), false, true,
                                           false, schemes));


    msgID = MSGID_PWPOLICY_DESCRIPTION_DEPRECATED_STORAGE_SCHEMES;
    ArrayList<String> deprecatedSchemes = new ArrayList<String>();
    deprecatedSchemes.addAll(policy.getDeprecatedStorageSchemes());
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_DEPRECATED_SCHEME,
                                           getMessage(msgID), false, true,
                                           false, deprecatedSchemes));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_VALIDATORS;
    ArrayList<DN> validatorDNs = new ArrayList<DN>();
    validatorDNs.addAll(policy.getPasswordValidators().keySet());
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_VALIDATOR,
                                       getMessage(msgID), false, true, false,
                                       validatorDNs));


    msgID = MSGID_PWPOLICY_DESCRIPTION_NOTIFICATION_HANDLERS;
    ArrayList<DN> handlerDNs = new ArrayList<DN>();
    handlerDNs.addAll(policy.getAccountStatusNotificationHandlers().keySet());
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_NOTIFICATION_HANDLER,
                                       getMessage(msgID), false, true, false,
                                       handlerDNs));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_USER_PW_CHANGES;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_USER_CHANGE,
                                            getMessage(msgID), false,
                                            policy.allowUserPasswordChanges()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CURRENT_PW;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_REQUIRE_CURRENT_PASSWORD,
                             getMessage(msgID), false,
                             policy.requireCurrentPassword()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_ADD;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_ADD,
                                            getMessage(msgID), false,
                                            policy.forceChangeOnAdd()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_RESET;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_RESET,
                                            getMessage(msgID), false,
                                            policy.forceChangeOnReset()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_SKIP_ADMIN_VALIDATION;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_SKIP_ADMIN_VALIDATION,
                             getMessage(msgID), false,
                             policy.skipValidationForAdministrators()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_GENERATOR;
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_GENERATOR,
                                       getMessage(msgID), false, false, false,
                                       policy.getPasswordGeneratorDN()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_AUTH;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION,
                             getMessage(msgID), false,
                             policy.requireSecureAuthentication()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_CHANGES;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES,
                             getMessage(msgID), false,
                             policy.requireSecurePasswordChanges()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_MULTIPLE_PW_VALUES;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES,
                             getMessage(msgID), false,
                             policy.allowMultiplePasswordValues()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_PREENCODED;
    attrList.add(new BooleanConfigAttribute(
                             ATTR_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS,
                             getMessage(msgID), false,
                             policy.allowPreEncodedPasswords()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MIN_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MINIMUM_PASSWORD_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, policy.getMinimumPasswordAge(),
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MAXIMUM_PASSWORD_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, policy.getMaximumPasswordAge(),
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_RESET_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE,
                          policy.getMaximumPasswordResetAge(),
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_WARNING_INTERVAL;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_WARNING_INTERVAL, getMessage(msgID),
                          false, timeUnits, true, 0, true, Integer.MAX_VALUE,
                          policy.getWarningInterval(), TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_EXPIRE_WITHOUT_WARNING;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING,
                          getMessage(msgID), false,
                          policy.expirePasswordsWithoutWarning()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_EXPIRED_CHANGES;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_ALLOW_EXPIRED_CHANGES,
                          getMessage(msgID), false,
                          policy.allowExpiredPasswordChanges()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_GRACE_LOGIN_COUNT;
    attrList.add(new IntegerConfigAttribute(ATTR_PWPOLICY_GRACE_LOGIN_COUNT,
                                            getMessage(msgID), false, false,
                                            false, true, 0, true,
                                            Integer.MAX_VALUE,
                                            policy.getGraceLoginCount()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_FAILURE_COUNT;
    attrList.add(new IntegerConfigAttribute(ATTR_PWPOLICY_LOCKOUT_FAILURE_COUNT,
                                            getMessage(msgID), false, false,
                                            false, true, 0, true,
                                            Integer.MAX_VALUE,
                                            policy.getLockoutFailureCount()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_DURATION;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_LOCKOUT_DURATION, getMessage(msgID),
                          false, timeUnits, true, 0, true, Integer.MAX_VALUE,
                          policy.getLockoutDuration(), TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FAILURE_EXPIRATION;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE,
                          policy.getLockoutFailureExpirationInterval(),
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CHANGE_BY_TIME;
    String timeStr = null;
    if (policy.getRequireChangeByTime() > 0)
    {
      timeStr = GeneralizedTimeSyntax.createGeneralizedTimeValue(
                     policy.getRequireChangeByTime()).getStringValue();
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_REQUIRE_CHANGE_BY_TIME,
                                           getMessage(msgID), false, false,
                                           false, timeStr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_ATTR;
    String loginTimeAttr = (policy.getLastLoginTimeAttribute() == null)
                           ? null
                           : policy.getLastLoginTimeAttribute().getNameOrOID();
    attrList.add(new StringConfigAttribute(
                          ATTR_PWPOLICY_LAST_LOGIN_TIME_ATTRIBUTE,
                          getMessage(msgID), false, false, false,
                          loginTimeAttr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_FORMAT;
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_LAST_LOGIN_TIME_FORMAT,
                                           getMessage(msgID), false, false,
                                           false,
                                           policy.getLastLoginTimeFormat()));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PREVIOUS_LAST_LOGIN_TIME_FORMAT;
    ArrayList<String> previousFormats = new ArrayList<String>();
    previousFormats.addAll(policy.getPreviousLastLoginTimeFormats());
    attrList.add(new StringConfigAttribute(
                          ATTR_PWPOLICY_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
                          getMessage(msgID), false, false, false,
                          previousFormats));


    msgID = MSGID_PWPOLICY_DESCRIPTION_IDLE_LOCKOUT_INTERVAL;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_IDLE_LOCKOUT_INTERVAL,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE,  policy.getIdleLockoutInterval(),
                          TIME_UNIT_SECONDS_FULL));

    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {

    assert configEntry.getDN().equals(this.currentPolicy.getConfigEntryDN() )
            : "Internal Error: mismatch between DN of configuration entry and"
              + "DN of current password policy." ;

    try
    {
      new PasswordPolicy(configEntry);
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ce);
      }

      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ie);
      }

      unacceptableReasons.add(ie.getMessage());
      return false;
    }

    // If we made it here, then the configuration is acceptable.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {

    assert configEntry.getDN().equals(this.currentPolicy.getConfigEntryDN() )
            : "Internal Error: mismatch between DN of configuration entry and"
              + "DN of current password policy." ;

    PasswordPolicy p;

    try
    {
      p = new PasswordPolicy(configEntry);
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ce);
      }
      ArrayList<String> messages = new ArrayList<String>();
      messages.add(ce.getMessage());
      return new ConfigChangeResult(
              DirectoryServer.getServerErrorResultCode(),
              /*adminActionRequired*/ true, messages);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ie);
      }
      ArrayList<String> messages = new ArrayList<String>();
      messages.add(ie.getMessage());
      return new ConfigChangeResult(
              DirectoryServer.getServerErrorResultCode(),
              /*adminActionRequired*/ true, messages);
    }

    // If we've made it here, then everything is acceptable.  Apply the new
    // configuration.
    ArrayList<String> messages = new ArrayList<String>();
    if (detailedResults)
    {
      int msgID = MSGID_PWPOLICY_UPDATED_POLICY;
      messages.add(getMessage(msgID, String.valueOf(p.getConfigEntryDN())));
    }

    this.currentPolicy = p;

    return new ConfigChangeResult(ResultCode.SUCCESS,
                                  /*adminActionRequired*/ false, messages);
  }


  /**
   * Retrieves the PasswordPolicy object representing the configuration entry
   * managed by this object.
   *
   * @return The PasswordPolicy object.
   */
  public PasswordPolicy getPolicy()
  {
    return currentPolicy;
  }
}
