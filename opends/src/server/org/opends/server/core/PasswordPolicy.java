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
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure that holds information about a Directory
 * Server password policy.
 */
public class PasswordPolicy
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.PasswordPolicy";



  // The attribute type that will hold the last login time.
  private AttributeType lastLoginTimeAttribute;

  // The attribute type that will hold user passwords for this password policy.
  private AttributeType passwordAttribute;

  // Indicates whether a user with an expired password will still be allowed to
  // change it via the password modify extended operation.
  private boolean allowExpiredPasswordChanges;

  // Indicates whether the password attribute will be allowed to have multiple
  // distinct values.
  private boolean allowMultiplePasswordValues;

  // Indicates whether to allow pre-encoded passwords.
  private boolean allowPreEncodedPasswords;

  // Indicates whether users will be allowed to change their passwords.
  private boolean allowUserPasswordChanges;

  // Indicates whether the attribute type uses the authPassword syntax.
  private boolean authPasswordSyntax;

  // Indicates whether to allow a password to expire without ever providing the
  // user with a notification.
  private boolean expirePasswordsWithoutWarning;

  // Indicates whether users must change their passwords the first time they
  // authenticate after their account is created.
  private boolean forceChangeOnAdd;

  // Indicates whether a user must change their password after it has been reset
  // by an administrator.
  private boolean forceChangeOnReset;

  // Indicates whether a user must provide their current password in order to
  // use a new password.
  private boolean requireCurrentPassword;

  // Indicates whether users will be required to authenticate using a secure
  // mechanism.
  private boolean requireSecureAuthentication;

  // Indicates whether users will be required to change their passwords using a
  // secure mechanism.
  private boolean requireSecurePasswordChanges;

  // Indicates whether password validation should be performed for
  // administrative password changes.
  private boolean skipValidationForAdministrators;

  // The set of account status notification handlers for this password policy.
  private ConcurrentHashMap<DN,AccountStatusNotificationHandler>
               notificationHandlers;

  // The set of password validators that will be used with this password policy.
  private ConcurrentHashMap<DN,PasswordValidator> passwordValidators;

  // The set of default password storage schemes for this password policy.
  private CopyOnWriteArrayList<PasswordStorageScheme> defaultStorageSchemes;

  // The set of previous last login time format strings.
  private CopyOnWriteArrayList<String> previousLastLoginTimeFormats;

  // The names of the deprecated password storage schemes for this password
  // policy.
  private CopyOnWriteArraySet<String> deprecatedStorageSchemes;

  // The DN of the entry containing the configuration for this password
  // policy.
  private DN configEntryDN;

  // The DN of the password validator for this password policy.
  private DN passwordGeneratorDN;

  // The number of grace logins that a user may have.
  private int graceLoginCount;

  // The maximum length of time in seconds that an account may remain idle
  // before it is locked out.
  private int idleLockoutInterval;

  // The length of time a user should stay locked out, in seconds.
  private int lockoutDuration;

  // The number of authentication failures before an account is locked out.
  private int lockoutFailureCount;

  // The length of time that authentication failures should be counted against
  // a user.
  private int lockoutFailureExpirationInterval;

  // The maximum password age (i.e., expiration interval), in seconds.
  private int maximumPasswordAge;

  // The maximum password age for administratively reset passwords, in seconds.
  private int maximumPasswordResetAge;

  // The minimum password age, in seconds.
  private int minimumPasswordAge;

  // The password expiration warning interval, in seconds.
  private int warningInterval;

  // The the time by which all users will be required to change their passwords.
  private long requireChangeByTime;

  // The password generator for use with this password policy.
  private PasswordGenerator passwordGenerator;

  // The format string to use when generating the last login time.
  private String lastLoginTimeFormat;



  /**
   * Creates a new password policy with all of the default settings.
   */
  private PasswordPolicy()
  {
    assert debugConstructor(CLASS_NAME);

    configEntryDN                    = null;
    passwordAttribute                = null;
    authPasswordSyntax               = false;
    lastLoginTimeAttribute           = null;
    allowExpiredPasswordChanges      = DEFAULT_PWPOLICY_ALLOW_EXPIRED_CHANGES;
    allowMultiplePasswordValues      =
         DEFAULT_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES;
    allowPreEncodedPasswords         =
         DEFAULT_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS;
    allowUserPasswordChanges         = DEFAULT_PWPOLICY_ALLOW_USER_CHANGE;
    expirePasswordsWithoutWarning    = DEFAULT_PWPOLICY_EXPIRE_WITHOUT_WARNING;
    forceChangeOnAdd                 = DEFAULT_PWPOLICY_FORCE_CHANGE_ON_ADD;
    forceChangeOnReset               = DEFAULT_PWPOLICY_FORCE_CHANGE_ON_RESET;
    requireCurrentPassword           =
         DEFAULT_PWPOLICY_REQUIRE_CURRENT_PASSWORD;
    requireSecureAuthentication      =
         DEFAULT_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION;
    requireSecurePasswordChanges     =
         DEFAULT_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES;
    skipValidationForAdministrators  = DEFAULT_PWPOLICY_SKIP_ADMIN_VALIDATION;
    graceLoginCount                  = DEFAULT_PWPOLICY_GRACE_LOGIN_COUNT;
    idleLockoutInterval              = DEFAULT_PWPOLICY_IDLE_LOCKOUT_INTERVAL;
    lockoutDuration                  = DEFAULT_PWPOLICY_LOCKOUT_DURATION;
    lockoutFailureCount              = DEFAULT_PWPOLICY_LOCKOUT_FAILURE_COUNT;
    lockoutFailureExpirationInterval =
         DEFAULT_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL;
    minimumPasswordAge               = DEFAULT_PWPOLICY_MINIMUM_PASSWORD_AGE;
    maximumPasswordAge               = DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_AGE;
    maximumPasswordResetAge          =
         DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE;
    warningInterval                  = DEFAULT_PWPOLICY_WARNING_INTERVAL;
    requireChangeByTime              = -1L;
    lastLoginTimeFormat              = null;
    passwordGenerator                = null;
    passwordGeneratorDN              = null;

    notificationHandlers =
         new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();

    defaultStorageSchemes = new CopyOnWriteArrayList<PasswordStorageScheme>();
    PasswordStorageScheme defaultScheme =
         DirectoryServer.getPasswordStorageScheme(
              DEFAULT_PASSWORD_STORAGE_SCHEME);
    if (defaultScheme != null)
    {
      defaultStorageSchemes.add(defaultScheme);
    }

    deprecatedStorageSchemes = new CopyOnWriteArraySet<String>();

    passwordValidators = new ConcurrentHashMap<DN,PasswordValidator>();

    previousLastLoginTimeFormats = new CopyOnWriteArrayList<String>();
  }



  /**
   * Creates a new password policy based on the configuration contained in the
   * provided configuration entry.  Any parameters not included in the provided
   * configuration entry will be assigned server-wide default values.  This
   * method should only be used to initialize the default password policy -- all
   * other policies should use the constructor that accepts the default password
   * policy as an additional argument.
   *
   * @param  configEntry  The configuration entry with the information to use to
   *                          use to initialize this password policy.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           password policy configuration.
   *
   * @throws  InitializationException  If an error occurs while initializing the
   *                                   password policy that is not related to
   *                                   the server configuration.
   */
  public PasswordPolicy(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    this();

    assert debugConstructor(CLASS_NAME, String.valueOf(configEntry));


    this.configEntryDN = configEntry.getDN();
    initializePasswordPolicyConfig(configEntry, this);


    // Ensure that the password attribute was included in the configuration
    // entry, since it is required.
    if (passwordAttribute == null)
    {
      int    msgID   = MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    // Ensure that at least one default password storage scheme was included in
    // the configuration entry, since it is required.
    if (defaultStorageSchemes.isEmpty())
    {
      int    msgID   = MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Initializes the provided password policy with the information contained in
   * the given configuration entry.
   *
   * @param  configEntry  The configuration entry to use to obtain the settings
   *                      for this password policy.
   * @param  policy       The password policy to be initialized.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           password policy configuration.
   *
   * @throws  InitializationException  If an error occurs while initializing the
   *                                   password policy that is not related to
   *                                   the server configuration.
   */
  private static void initializePasswordPolicyConfig(ConfigEntry configEntry,
                                                     PasswordPolicy policy)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializePasswordPolicyConfig",
                      String.valueOf(configEntry));


    DN configEntryDN = configEntry.getDN();


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


    // Get the password attribute.  If specified, it must have either the
    // user password or auth password syntax.
    int msgID = MSGID_PWPOLICY_DESCRIPTION_PW_ATTR;
    StringConfigAttribute pwAttrStub =
         new StringConfigAttribute(ATTR_PWPOLICY_PASSWORD_ATTRIBUTE,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute pwAttrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(pwAttrStub);
      if (pwAttrAttr == null)
      {
        msgID = MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        String lowerName = toLowerCase(pwAttrAttr.pendingValue());

        AttributeType pwAttrType = DirectoryServer.getAttributeType(lowerName);
        if (pwAttrType == null)
        {
          msgID = MSGID_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                String.valueOf(pwAttrAttr.pendingValue()));
          throw new ConfigException(msgID, message);
        }

        String syntaxOID = pwAttrType.getSyntaxOID();
        if (syntaxOID.equals(SYNTAX_AUTH_PASSWORD_OID))
        {
          policy.passwordAttribute  = pwAttrType;
          policy.authPasswordSyntax = true;
        }
        else if (syntaxOID.equals(SYNTAX_USER_PASSWORD_OID))
        {
          policy.passwordAttribute  = pwAttrType;
          policy.authPasswordSyntax = false;
        }
        else
        {
          String syntax = pwAttrType.getSyntax().getSyntaxName();
          if ((syntax == null) || (syntax.length() == 0))
          {
            syntax = syntaxOID;
          }

          msgID = MSGID_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      String.valueOf(pwAttrAttr.pendingValue()),
                                      String.valueOf(syntaxOID));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_ATTRIBUTE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the default storage schemes.  They must all reference valid storage
    // schemes that support the syntax for the specified password attribute.
    msgID = MSGID_PWPOLICY_DESCRIPTION_DEFAULT_STORAGE_SCHEMES;
    StringConfigAttribute defaultSchemeStub =
           new StringConfigAttribute(ATTR_PWPOLICY_DEFAULT_SCHEME,
                                     getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute defaultSchemeAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(defaultSchemeStub);
      if (defaultSchemeAttr == null)
      {
        msgID = MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        LinkedList<PasswordStorageScheme> schemes =
             new LinkedList<PasswordStorageScheme>();
        for (String schemeName : defaultSchemeAttr.pendingValues())
        {
          PasswordStorageScheme scheme;
          if (policy.authPasswordSyntax)
          {
            scheme = DirectoryServer.getAuthPasswordStorageScheme(schemeName);
          }
          else
          {
            scheme = DirectoryServer.getPasswordStorageScheme(
                                          toLowerCase(schemeName));
          }

          if (scheme == null)
          {
            msgID = MSGID_PWPOLICY_NO_SUCH_DEFAULT_SCHEME;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        String.valueOf(schemeName));
            throw new ConfigException(msgID, message);
          }
          else
          {
            schemes.add(scheme);
          }
        }

        policy.defaultStorageSchemes =
             new CopyOnWriteArrayList<PasswordStorageScheme>(schemes);
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_DEFAULT_STORAGE_SCHEMES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the names of the deprecated storage schemes.
    msgID = MSGID_PWPOLICY_DESCRIPTION_DEPRECATED_STORAGE_SCHEMES;
    StringConfigAttribute deprecatedSchemeStub =
         new StringConfigAttribute(ATTR_PWPOLICY_DEPRECATED_SCHEME,
                                   getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute deprecatedSchemeAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(deprecatedSchemeStub);
      if (deprecatedSchemeAttr != null)
      {
        policy.deprecatedStorageSchemes =
             new CopyOnWriteArraySet<String>(
                      deprecatedSchemeAttr.pendingValues());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_DEPRECATED_STORAGE_SCHEMES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the password validators.
    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_VALIDATORS;
    DNConfigAttribute validatorStub =
         new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_VALIDATOR,
                               getMessage(msgID), false, true, false);
    try
    {
      DNConfigAttribute validatorAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(validatorStub);
      if (validatorAttr != null)
      {
        ConcurrentHashMap<DN,PasswordValidator> validators =
             new ConcurrentHashMap<DN,PasswordValidator>();
        for (DN validatorDN : validatorAttr.pendingValues())
        {
          PasswordValidator validator =
               DirectoryServer.getPasswordValidator(validatorDN);
          if (validator == null)
          {
            msgID = MSGID_PWPOLICY_NO_SUCH_VALIDATOR;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        String.valueOf(validatorDN));
            throw new ConfigException(msgID, message);
          }

          validators.put(validatorDN, validator);
        }

        policy.passwordValidators = validators;
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_VALIDATORS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the status notification handlers.
    msgID = MSGID_PWPOLICY_DESCRIPTION_NOTIFICATION_HANDLERS;
    DNConfigAttribute notificationStub =
         new DNConfigAttribute(ATTR_PWPOLICY_NOTIFICATION_HANDLER,
                               getMessage(msgID), false, true, false);
    try
    {
      DNConfigAttribute notificationAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(notificationStub);
      if (notificationAttr != null)
      {
        ConcurrentHashMap<DN,AccountStatusNotificationHandler> handlers =
             new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();
        for (DN handlerDN : notificationAttr.pendingValues())
        {
          AccountStatusNotificationHandler handler =
               DirectoryServer.getAccountStatusNotificationHandler(handlerDN);
          if (handler == null)
          {
            msgID = MSGID_PWPOLICY_NO_SUCH_NOTIFICATION_HANDLER;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        String.valueOf(handlerDN));
            throw new ConfigException(msgID, message);
          }

          handlers.put(handlerDN, handler);
        }

        policy.notificationHandlers = handlers;
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_NOTIFICATION_HANDLERS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow user password changes.
    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_USER_PW_CHANGES;
    BooleanConfigAttribute userChangeStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_USER_CHANGE,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute userChangeAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(userChangeStub);
      if (userChangeAttr != null)
      {
        policy.allowUserPasswordChanges = userChangeAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_USER_PW_CHANGES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to require the current password for user changes.
    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CURRENT_PW;
    BooleanConfigAttribute requirePWStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_REQUIRE_CURRENT_PASSWORD,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute requirePWAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(requirePWStub);
      if (requirePWAttr != null)
      {
        policy.requireCurrentPassword = requirePWAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CURRENT_PW;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to force password changes on add.
    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_ADD;
    BooleanConfigAttribute forceChangeOnAddStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_ADD,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute forceChangeOnAddAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(forceChangeOnAddStub);
      if (forceChangeOnAddAttr != null)
      {
        policy.forceChangeOnAdd = forceChangeOnAddAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_ADD;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to force password changes on reset.
    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_RESET;
    BooleanConfigAttribute forceChangeOnResetStub =
      new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_RESET,
                                 getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute forceChangeAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(forceChangeOnResetStub);
      if (forceChangeAttr != null)
      {
        policy.forceChangeOnReset = forceChangeAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_RESET;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to validate reset passwords.
    msgID = MSGID_PWPOLICY_DESCRIPTION_SKIP_ADMIN_VALIDATION;
    BooleanConfigAttribute validateResetStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_SKIP_ADMIN_VALIDATION,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute validateResetAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(validateResetStub);
      if (validateResetAttr != null)
      {
        policy.skipValidationForAdministrators =
             validateResetAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_SKIP_ADMIN_VALIDATION;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the password generator.
    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_GENERATOR;
    DNConfigAttribute generatorStub =
         new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_GENERATOR,
                               getMessage(msgID), false, false, false);
    try
    {
      DNConfigAttribute generatorAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(generatorStub);
      if (generatorAttr != null)
      {
        PasswordGenerator generator =
             DirectoryServer.getPasswordGenerator(generatorAttr.pendingValue());
        if (generator == null)
        {
          msgID = MSGID_PWPOLICY_NO_SUCH_GENERATOR;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                String.valueOf(generatorAttr.pendingValue()));
          throw new ConfigException(msgID, message);
        }

        policy.passwordGeneratorDN = generatorAttr.pendingValue();
        policy.passwordGenerator   = generator;
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_GENERATOR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to require secure authentication.
    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_AUTH;
    BooleanConfigAttribute secureAuthStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute secureAuthAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(secureAuthStub);
      if (secureAuthAttr != null)
      {
        policy.requireSecureAuthentication = secureAuthAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_AUTH;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to require secure password changes.
    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_CHANGES;
    BooleanConfigAttribute secureChangeStub =
         new BooleanConfigAttribute(
                  ATTR_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES,
                  getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute secureChangeAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(secureChangeStub);
      if (secureChangeAttr != null)
      {
        policy.requireSecurePasswordChanges = secureChangeAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_CHANGES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow multiple password values.
    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_MULTIPLE_PW_VALUES;
    BooleanConfigAttribute allowMultiplePWStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowMultiplePWAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowMultiplePWStub);
      if (allowMultiplePWAttr != null)
      {
        policy.allowMultiplePasswordValues = allowMultiplePWAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_MULTIPLE_PW_VALUES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow pre-encoded passwords.
    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_PREENCODED;
    BooleanConfigAttribute preEncodedStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute preEncodedAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(preEncodedStub);
      if (preEncodedAttr != null)
      {
        policy.allowPreEncodedPasswords = preEncodedAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_PREENCODED;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the minimum password age.
    msgID = MSGID_PWPOLICY_DESCRIPTION_MIN_AGE;
    IntegerWithUnitConfigAttribute minAgeStub =
         new IntegerWithUnitConfigAttribute(ATTR_PWPOLICY_MINIMUM_PASSWORD_AGE,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute minAgeAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(minAgeStub);
      if (minAgeAttr != null)
      {
        policy.minimumPasswordAge = (int) minAgeAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_MIN_AGE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the maximum password age.
    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_AGE;
    IntegerWithUnitConfigAttribute maxAgeStub =
         new IntegerWithUnitConfigAttribute(ATTR_PWPOLICY_MAXIMUM_PASSWORD_AGE,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute maxAgeAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(maxAgeStub);
      if (maxAgeAttr != null)
      {
        policy.maximumPasswordAge = (int) maxAgeAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_AGE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the maximum password reset age.
    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_RESET_AGE;
    IntegerWithUnitConfigAttribute maxResetStub =
         new IntegerWithUnitConfigAttribute(
                  ATTR_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE, getMessage(msgID),
                  false, timeUnits, true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute maxResetAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(maxResetStub);
      if (maxResetAttr != null)
      {
        policy.maximumPasswordResetAge =
             (int) maxResetAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_RESET_AGE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the warning interval.
    msgID = MSGID_PWPOLICY_DESCRIPTION_WARNING_INTERVAL;
    IntegerWithUnitConfigAttribute warningStub =
         new IntegerWithUnitConfigAttribute(ATTR_PWPOLICY_WARNING_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute warningAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(warningStub);
      if (warningAttr != null)
      {
        policy.warningInterval = (int) warningAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_WARNING_INTERVAL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to expire passwords without warning.
    msgID = MSGID_PWPOLICY_DESCRIPTION_EXPIRE_WITHOUT_WARNING;
    BooleanConfigAttribute expireWithoutWarningStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute expireWithoutWarningAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(expireWithoutWarningStub);
      if (expireWithoutWarningAttr != null)
      {
        policy.expirePasswordsWithoutWarning =
             expireWithoutWarningAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_EXPIRE_WITHOUT_WARNING;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // If the expire without warning option is disabled, then there must be a
    // warning interval.
    if ((! policy.expirePasswordsWithoutWarning()) &&
        (policy.getWarningInterval() <= 0))
    {
      msgID = MSGID_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    // Determine whether to allow user changes for expired passwords.
    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_EXPIRED_CHANGES;
    BooleanConfigAttribute allowExpiredChangesStub =
         new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_EXPIRED_CHANGES,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowExpiredChangesAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowExpiredChangesStub);
      if (allowExpiredChangesAttr != null)
      {
        policy.allowExpiredPasswordChanges =
             allowExpiredChangesAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_EXPIRED_CHANGES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the grace login count.
    msgID = MSGID_PWPOLICY_DESCRIPTION_GRACE_LOGIN_COUNT;
    IntegerConfigAttribute graceStub =
         new IntegerConfigAttribute(ATTR_PWPOLICY_GRACE_LOGIN_COUNT,
                                    getMessage(msgID), false, false, false,
                                    true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute graceAttr =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(graceStub);
      if (graceAttr != null)
      {
        policy.graceLoginCount = graceAttr.pendingIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_GRACE_LOGIN_COUNT;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the lockout failure count.
    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_FAILURE_COUNT;
    IntegerConfigAttribute failureCountStub =
         new IntegerConfigAttribute(ATTR_PWPOLICY_LOCKOUT_FAILURE_COUNT,
                                    getMessage(msgID), false, false, false,
                                    true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute failureCountAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(failureCountStub);
      if (failureCountAttr != null)
      {
        policy.lockoutFailureCount = failureCountAttr.pendingIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_FAILURE_COUNT;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the lockout duration.
    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_DURATION;
    IntegerWithUnitConfigAttribute lockoutDurationStub =
         new IntegerWithUnitConfigAttribute(ATTR_PWPOLICY_LOCKOUT_DURATION,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute lockoutDurationAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(lockoutDurationStub);
      if (lockoutDurationAttr != null)
      {
        policy.lockoutDuration =
             (int) lockoutDurationAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_DURATION;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the lockout failure expiration interval.
    msgID = MSGID_PWPOLICY_DESCRIPTION_FAILURE_EXPIRATION;
    IntegerWithUnitConfigAttribute failureExpirationStub =
         new IntegerWithUnitConfigAttribute(
                  ATTR_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL,
                  getMessage(msgID), false, timeUnits, true, 0, true,
                  Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute failureExpirationAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(failureExpirationStub);
      if (failureExpirationAttr != null)
      {
        policy.lockoutFailureExpirationInterval =
             (int) failureExpirationAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_FAILURE_EXPIRATION;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the required change time.
    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CHANGE_BY_TIME;
    StringConfigAttribute requireChangeByStub =
         new StringConfigAttribute(ATTR_PWPOLICY_REQUIRE_CHANGE_BY_TIME,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute requireChangeByAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(requireChangeByStub);
      if (requireChangeByAttr != null)
      {
        ByteString valueString = new
             ASN1OctetString(requireChangeByAttr.pendingValue());

        GeneralizedTimeSyntax syntax =
             (GeneralizedTimeSyntax)
             DirectoryServer.getAttributeSyntax(SYNTAX_GENERALIZED_TIME_OID,
                                                false);

        if (syntax == null)
        {
          policy.requireChangeByTime =
               GeneralizedTimeSyntax.decodeGeneralizedTimeValue(valueString);
        }
        else
        {
          valueString =
               syntax.getEqualityMatchingRule().normalizeValue(valueString);
          policy.requireChangeByTime =
               GeneralizedTimeSyntax.decodeGeneralizedTimeValue(valueString);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the last login time attribute.  If specified, it must be defined in
    // the server schema.  It does not need to have a generalized time syntax
    // because the value that it will store will not necessarily conform to this
    // format.
    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_ATTR;
    StringConfigAttribute lastLoginAttrStub =
         new StringConfigAttribute(ATTR_PWPOLICY_LAST_LOGIN_TIME_ATTRIBUTE,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute lastLoginAttrAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(lastLoginAttrStub);
      if (lastLoginAttrAttr != null)
      {
        String lowerName = toLowerCase(lastLoginAttrAttr.pendingValue());
        AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          msgID = MSGID_PWPOLICY_UNDEFINED_LAST_LOGIN_TIME_ATTRIBUTE;
          String message =
               getMessage(msgID, String.valueOf(configEntryDN),
                          String.valueOf(lastLoginAttrAttr.pendingValue()));
          throw new ConfigException(msgID, message);
        }

        policy.lastLoginTimeAttribute = attrType;
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the last login time format.  If specified, it must be a valid format
    // string.
    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_FORMAT;
    StringConfigAttribute lastLoginFormatStub =
         new StringConfigAttribute(ATTR_PWPOLICY_LAST_LOGIN_TIME_FORMAT,
                                   getMessage(msgID), false, false, false);
    try
    {
      StringConfigAttribute lastLoginFormatAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(lastLoginFormatStub);
      if (lastLoginFormatAttr != null)
      {
        String formatString = lastLoginFormatAttr.pendingValue();

        try
        {
          SimpleDateFormat format = new SimpleDateFormat(formatString);
          policy.lastLoginTimeFormat = formatString;
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "initializePasswordPolicyConfig",
                                e);

          msgID = MSGID_PWPOLICY_INVALID_LAST_LOGIN_TIME_FORMAT;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      String.valueOf(formatString));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_FORMAT;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the previous last login time formats.  If specified, they must all
    // be valid format strings.
    msgID = MSGID_PWPOLICY_DESCRIPTION_PREVIOUS_LAST_LOGIN_TIME_FORMAT;
    StringConfigAttribute previousFormatStub =
         new StringConfigAttribute(
                  ATTR_PWPOLICY_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
                  getMessage(msgID), false, true, false);
    try
    {
      StringConfigAttribute previousFormatAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(previousFormatStub);
      if (previousFormatAttr != null)
      {
        List<String> formatStrings = previousFormatAttr.pendingValues();
        for (String s : formatStrings)
        {
          try
          {
            SimpleDateFormat format = new SimpleDateFormat(s);
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "initializePasswordPolicyConfig",
                                  e);

            msgID = MSGID_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        String.valueOf(s));
            throw new ConfigException(msgID, message);
          }
        }

        policy.previousLastLoginTimeFormats =
             new CopyOnWriteArrayList<String>(formatStrings);
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_PREVIOUS_LAST_LOGIN_TIME_FORMAT;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the idle lockout duration.
    msgID = MSGID_PWPOLICY_DESCRIPTION_IDLE_LOCKOUT_INTERVAL;
    IntegerWithUnitConfigAttribute idleIntervalStub =
         new IntegerWithUnitConfigAttribute(ATTR_PWPOLICY_IDLE_LOCKOUT_INTERVAL,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, true, Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute idleIntervalAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(idleIntervalStub);
      if (idleIntervalAttr != null)
      {
        policy.idleLockoutInterval =
             (int) idleIntervalAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordPolicyConfig", e);

      msgID = MSGID_PWPOLICY_CANNOT_DETERMINE_IDLE_LOCKOUT_INTERVAL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }
  }



  /**
   * Retrieves the attribute type used to store the password.
   *
   * @return  The attribute type used to store the password.
   */
  public AttributeType getPasswordAttribute()
  {
    assert debugEnter(CLASS_NAME, "getPasswordAttribute");

    return passwordAttribute;
  }



  /**
   * Specifies the attribute type used to store the password.
   *
   * @param  passwordAttribute  The attribute type used to store the password.
   */
  public void setPasswordAttribute(AttributeType passwordAttribute)
  {
    assert debugEnter(CLASS_NAME, "setPasswordAttribute",
                      String.valueOf(passwordAttribute));

    this.passwordAttribute = passwordAttribute;
  }



  /**
   * Indicates whether the associated password attribute uses the auth password
   * syntax.
   *
   * @return  <CODE>true</CODE> if the associated password attribute uses the
   *          auth password syntax, or <CODE>false</CODE> if not.
   */
  public boolean usesAuthPasswordSyntax()
  {
    assert debugEnter(CLASS_NAME, "usesAuthPasswordSyntax");

    return authPasswordSyntax;
  }



  /**
   * Retrieves the default set of password storage schemes that will be used for
   * this password policy.  The returned set should not be modified by the
   * caller.
   *
   * @return  The default set of password storage schemes that will be used for
   *          this password policy.
   */
  public CopyOnWriteArrayList<PasswordStorageScheme> getDefaultStorageSchemes()
  {
    assert debugEnter(CLASS_NAME, "getDefaultStorageSchemes");

    return defaultStorageSchemes;
  }



  /**
   * Specifies the default set of password storage schemes that will be used for
   * this password policy.
   *
   * @param  defaultStorageSchemes  The default set of password storage schemes
   *                                that will be used for this password policy.
   */
  public void setDefaultStorageSchemes(
       CopyOnWriteArrayList<PasswordStorageScheme> defaultStorageSchemes)
  {
    assert debugEnter(CLASS_NAME, "setDefaultStorageSchemes",
                      String.valueOf(defaultStorageSchemes));

    this.defaultStorageSchemes = defaultStorageSchemes;
  }



  /**
   * Indicates whether the specified storage scheme is a default scheme for this
   * password policy.
   *
   * @param  name  The name of the password storage scheme for which to make the
   *               determination.
   *
   * @return  <CODE>true</CODE> if the storage scheme is a default scheme for
   *          this password policy, or <CODE>false</CODE> if not.
   */
  public boolean isDefaultStorageScheme(String name)
  {
    assert debugEnter(CLASS_NAME, "isDefaultStorageScheme",
                      String.valueOf(name));

    CopyOnWriteArrayList<PasswordStorageScheme> defaultSchemes =
         getDefaultStorageSchemes();
    if (defaultSchemes == null)
    {
      return false;
    }

    for (PasswordStorageScheme s : defaultSchemes)
    {
      if (authPasswordSyntax)
      {
        if (s.getAuthPasswordSchemeName().equalsIgnoreCase(name))
        {
          return true;
        }
      }
      else
      {
        if (s.getStorageSchemeName().equalsIgnoreCase(name))
        {
          return true;
        }
      }
    }


    return false;
  }



  /**
   * Retrieves the names of the password storage schemes that have been
   * deprecated.  If an authenticating user has one or more of these deprecated
   * storage schemes in use in their entry, then they will be removed and
   * replaced with the passwords encoded in the default storage scheme(s).  The
   * returned list should not be altered by the caller.
   *
   * @return  The names of the password storage schemes that have been
   *          deprecated.
   */
  public CopyOnWriteArraySet<String> getDeprecatedStorageSchemes()
  {
    assert debugEnter(CLASS_NAME, "getDeprecatedStorageSchemes");

    return deprecatedStorageSchemes;
  }



  /**
   * Specifies the names of the password storage schemes that have been
   * deprecated.
   *
   * @param  deprecatedStorageSchemes  The names of the password storage schemes
   *                                   that have been deprecated.
   */
  public void setDeprecatedStorageSchemes(CopyOnWriteArraySet<String>
                                               deprecatedStorageSchemes)
  {
    assert debugEnter(CLASS_NAME, "setDeprecatedStorageSchemes",
                      String.valueOf(deprecatedStorageSchemes));

    this.deprecatedStorageSchemes = deprecatedStorageSchemes;
  }



  /**
   * Indicates whether the specified storage scheme is deprecated.
   *
   * @param  name  The name of the password storage scheme for which to make the
   *               determination.
   *
   * @return  <CODE>true</CODE> if the storage scheme is deprecated, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isDeprecatedStorageScheme(String name)
  {
    assert debugEnter(CLASS_NAME, "isDeprecatedStorageScheme",
                      String.valueOf(name));

    CopyOnWriteArraySet<String> deprecatedSchemes =
         getDeprecatedStorageSchemes();
    if (deprecatedSchemes == null)
    {
      return false;
    }

    for (String s : deprecatedSchemes)
    {
      if (s.equalsIgnoreCase(name))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the set of password validators for this password policy.  The
   * returned list should not be altered by the caller.
   *
   * @return  The set of password validators for this password policy.
   */
  public ConcurrentHashMap<DN,PasswordValidator> getPasswordValidators()
  {
    assert debugEnter(CLASS_NAME, "getPasswordValidators");

    return passwordValidators;
  }



  /**
   * Specifies the set of password validators for this password policy.
   *
   * @param  passwordValidators  The set of password validators for this
   *                             password policy.
   */
  public void setPasswordValidators(ConcurrentHashMap<DN,PasswordValidator>
                                         passwordValidators)
  {
    assert debugEnter(CLASS_NAME, "setPasswordValidators",
                      String.valueOf(passwordValidators));

    this.passwordValidators = passwordValidators;
  }



  /**
   * Retrieves the set of account status notification handlers that should be
   * used with this password policy.  The returned list should not be altered by
   * the caller.
   *
   * @return  The set of account status notification handlers that should be
   *          used with this password policy.
   */
  public ConcurrentHashMap<DN,AccountStatusNotificationHandler>
              getAccountStatusNotificationHandlers()
  {
    assert debugEnter(CLASS_NAME, "getAccountStatusNotificationHandlers");

    return notificationHandlers;
  }



  /**
   * Specifies the set of account status notification handlers that should be
   * used with this password policy.
   *
   * @param  notificationHandlers  The set of account status notification
   *                               handlers that should be used with this
   *                               password policy.
   */
  public void setAccountStatusNotificationHandlers(
                   ConcurrentHashMap<DN,AccountStatusNotificationHandler>
                        notificationHandlers)
  {
    assert debugEnter(CLASS_NAME, "setAccountStatusNotificationHandlers",
                      String.valueOf(notificationHandlers));

    this.notificationHandlers = notificationHandlers;
  }



  /**
   * Indicates whether end users will be allowed to change their own passwords
   * (subject to access control restrictions).
   *
   * @return  <CODE>true</CODE> if users will be allowed to change their own
   *          passwords, or <CODE>false</CODE> if not.
   */
  public boolean allowUserPasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "allowUserPasswordChanges");

    return allowUserPasswordChanges;
  }



  /**
   * Specifies whether end users will be allowed to change their own passwords
   * (subject to access control restrictions).
   *
   * @param  allowUserPasswordChanges  Specifies whether end users will be
   *                                   allowed to change their own passwords.
   */
  public void setAllowUserPasswordChanges(boolean allowUserPasswordChanges)
  {
    assert debugEnter(CLASS_NAME, "setAllowUserPasswordChanges",
                      String.valueOf(allowUserPasswordChanges));

    this.allowUserPasswordChanges = allowUserPasswordChanges;
  }



  /**
   * Indicates whether the end user must provide their current password (via the
   * password modify extended operation) in order to set a new password.
   *
   * @return  <CODE>true</CODE> if the end user must provide their current
   *          password in order to set a new password, or <CODE>false</CODE> if
   *          they will not.
   */
  public boolean requireCurrentPassword()
  {
    assert debugEnter(CLASS_NAME, "requireCurrentPassword");

    return requireCurrentPassword;
  }



  /**
   * Specifies whether the end user must provide their current password (via the
   * password modify extended operation) in order to set a new password.
   *
   * @param  requireCurrentPassword  Specifies whether the end user must provide
   *                                 their current password in order to set a
   *                                 new password.
   */
  public void setRequireCurrentPassword(boolean requireCurrentPassword)
  {
    assert debugEnter(CLASS_NAME, "setRequireCurrentPassword",
                      String.valueOf(requireCurrentPassword));

    this.requireCurrentPassword = requireCurrentPassword;
  }



  /**
   * Indicates whether users will be required to change their passwords as soon
   * as they authenticate after their accounts have been created.
   *
   * @return  <CODE>true</CODE> if users will be required to change their
   *          passwords at the initial authentication, or <CODE>false</CODE> if
   *          not.
   */
  public boolean forceChangeOnAdd()
  {
    assert debugEnter(CLASS_NAME, "forceChangeOnAdd");

    return forceChangeOnAdd;
  }



  /**
   * Specifies whether user will be required to change their passwords on first
   * authenticating to the server.
   *
   * @param  forceChangeOnAdd  Specifies whether users will be required to
   *                           change their passwords after first authenticating
   *                           to the server.
   */
  public void setForceChangeOnAdd(boolean forceChangeOnAdd)
  {
    assert debugEnter(CLASS_NAME, "setForceChangeOnAdd",
                      String.valueOf(forceChangeOnAdd));

    this.forceChangeOnAdd = forceChangeOnAdd;
  }



  /**
   * Indicates whether a user will be required to change their password after it
   * has been reset by an administrator.
   *
   * @return  <CODE>true</CODE> if a user will be required to change their
   *          password after it has been reset by an administrator, or
   *          <CODE>false</CODE> if they can continue using that password.
   */
  public boolean forceChangeOnReset()
  {
    assert debugEnter(CLASS_NAME, "forceChangeOnReset");

    return forceChangeOnReset;
  }



  /**
   * Specifies whether a user will be required to change their password after it
   * has been reset by an administrator.
   *
   * @param  forceChangeOnReset  Specifies whether a user will be required to
   *                             change their password after it has been reset
   *                             by an administrator.
   */
  public void setForceChangeOnReset(boolean forceChangeOnReset)
  {
    assert debugEnter(CLASS_NAME, "setForceChangeOnReset",
                      String.valueOf(forceChangeOnReset));

    this.forceChangeOnReset = forceChangeOnReset;
  }



  /**
   * Indicates whether operations by administrators that specify a new password
   * for a user (e.g., add, modify, or password modify) will be allowed to
   * bypass the password validation process that will be required for user
   * password changes.
   *
   * @return  <CODE>true</CODE> if administrators will be allowed to bypass the
   *          validation checks, or <CODE>false</CODE> if not.
   */
  public boolean skipValidationForAdministrators()
  {
    assert debugEnter(CLASS_NAME, "skipValidationForAdministrators");

    return skipValidationForAdministrators;
  }



  /**
   * Specifies whether operations by administrators that specify a new password
   * for a user (e.g., add, modify, or password modify) will be allowed to
   * bypass the password validation process that will be required for user
   * password changes.
   *
   * @param  skipValidationForAdministrators  Specifies whether administrators
   *                                          will be allowed to bypass password
   *                                          validation checks.
   */
  public void setSkipValidationForAdministrators(
                   boolean skipValidationForAdministrators)
  {
    assert debugEnter(CLASS_NAME, "setSkipValidationForAdministrators",
                      String.valueOf(skipValidationForAdministrators));

    this.skipValidationForAdministrators = skipValidationForAdministrators;
  }



  /**
   * Retrieves the DN of the password validator configuration entry.
   *
   * @return  The DN of the password validator configuration entry.
   */
  public DN getPasswordGeneratorDN()
  {
    assert debugEnter(CLASS_NAME, "getPasswordGeneratorDN");

    return passwordGeneratorDN;
  }



  /**
   * Retrieves the password generator that will be used with this password
   * policy.
   *
   * @return  The password generator that will be used with this password
   *          policy, or <CODE>null</CODE> if there is none.
   */
  public PasswordGenerator getPasswordGenerator()
  {
    assert debugEnter(CLASS_NAME, "getPasswordGenerator");

    return passwordGenerator;
  }



  /**
   * Specifies the password generator that will be used with this password
   * policy.
   *
   * @param  passwordGeneratorDN  The DN of the password validator configuration
   *                              entry.
   * @param  passwordGenerator    The password generator that will be used with
   *                              this password policy.
   */
  public void setPasswordGenerator(DN passwordGeneratorDN,
                                  PasswordGenerator passwordGenerator)
  {
    assert debugEnter(CLASS_NAME, "setPasswordGenerator",
                      String.valueOf(passwordGenerator));

    this.passwordGeneratorDN = passwordGeneratorDN;
    this.passwordGenerator   = passwordGenerator;
  }



  /**
   * Indicates whether users with this password policy will be required to
   * authenticate in a secure manner that does not expose their password.
   *
   *  @return  <CODE>true</CODE> if users with this password policy will be
   *           required to authenticate in a secure manner that does not expose
   *           their password, or <CODE>false</CODE> if they may authenticate in
   *           an insecure manner.
   */
  public boolean requireSecureAuthentication()
  {
    assert debugEnter(CLASS_NAME, "requireSecureAuthentication");

    return requireSecureAuthentication;
  }



  /**
   * Specifies whether users with this password policy will be required to
   * authenticate in a secure manner that does not expose their password.
   *
   * @param  requireSecureAuthentication  Specifies whether users with this
   *                                      password policy will be required to
   *                                      authenticate in a secure manner that
   *                                      does not expose their password.
   */
  public void setRequireSecureAuthentication(boolean
                                                  requireSecureAuthentication)
  {
    assert debugEnter(CLASS_NAME, "setRequireSecureAuthentication",
                      String.valueOf(requireSecureAuthentication));

    this.requireSecureAuthentication = requireSecureAuthentication;
  }



  /**
   * Indicates whether users with this password policy will be required to
   * change their passwords in a secure manner that does not expose the new
   * password.
   *
   * @return  <CODE>true</CODE> if users with this password policy will be
   *          required to change their passwords in a secure manner that does
   *          not expose the new password, or <CODE>false</CODE> if they may
   *          change their password in an insecure manner.
   */
  public boolean requireSecurePasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "requireSecurePasswordChanges");

    return requireSecurePasswordChanges;
  }



  /**
   * Specifies whether users with this password policy will be required to
   * change their passwords in a secure manner that does not expose the new
   * password.
   *
   * @param  requireSecurePasswordChanges  Specifies whether users with this
   *                                       password policy will be required to
   *                                       change their passwords in a secure
   *                                       manner that does not expose the new
   *                                       password.
   */
  public void setRequireSecurePasswordChanges(boolean
                                                   requireSecurePasswordChanges)
  {
    assert debugEnter(CLASS_NAME, "setRequireSecurePasswordChanges",
                      String.valueOf(requireSecurePasswordChanges));

    this.requireSecurePasswordChanges = requireSecurePasswordChanges;
  }



  /**
   * Indicates whether user entries will be allowed to have multiple distinct
   * values in the password attribute.
   *
   * @return  <CODE>true</CODE> if clients will be allowed to have multiple
   *          distinct password values, or <CODE>false</CODE> if not.
   */
  public boolean allowMultiplePasswordValues()
  {
    assert debugEnter(CLASS_NAME, "allowMultiplePasswordValues");

    return allowMultiplePasswordValues;
  }



  /**
   * Specifies whether user entries will be allowed to have multiple distinct
   * values in the password attribute.
   *
   * @param  allowMultiplePasswordValues  Specifies whether user entries will be
   *                                      allowed to have multiple distinct
   *                                      values in the password attribute.
   */
  public void setAllowMultiplePasswordValues(boolean
                                                  allowMultiplePasswordValues)
  {
    assert debugEnter(CLASS_NAME, "setAllowMultiplePasswordValues",
                      String.valueOf(allowMultiplePasswordValues));

    this.allowMultiplePasswordValues = allowMultiplePasswordValues;
  }



  /**
   * Indicates whether clients will be allowed to set pre-encoded passwords that
   * are already hashed and therefore cannot be validated for correctness.
   *
   * @return  <CODE>true</CODE> if clients will be allowed to set pre-encoded
   *          passwords that cannot be validated, or <CODE>false</CODE> if not.
   */
  public boolean allowPreEncodedPasswords()
  {
    assert debugEnter(CLASS_NAME, "allowPreEncodedPasswords");

    return allowPreEncodedPasswords;
  }



  /**
   * Specifies whether clients will be allowed to set pre-encoded passwords that
   * are already hashed and therefore cannot be validated for correctness.
   *
   * @param  allowPreEncodedPasswords  Specifies whether clients will be allowed
   *                                   to set pre-encoded passwords that are
   *                                   already hashed and therefore cannot be
   *                                   validated for correctness.
   */
  public void setAllowPreEncodedPasswords(boolean allowPreEncodedPasswords)
  {
    assert debugEnter(CLASS_NAME, "setAllowPreEncodedPasswords",
                      String.valueOf(allowPreEncodedPasswords));

    this.allowPreEncodedPasswords = allowPreEncodedPasswords;
  }



  /**
   * Retrieves the minimum password age, which is the minimum length of time in
   * seconds that must elapse between user password changes.
   *
   * @return  The minimum password age, which is the minimum length of time in
   *          seconds that must elapse between user password changes, or zero if
   *          there is no minimum age.
   */
  public int getMinimumPasswordAge()
  {
    assert debugEnter(CLASS_NAME, "getMinimumPasswordAge");

    if (minimumPasswordAge <= 0)
    {
      return 0;
    }

    return minimumPasswordAge;
  }



  /**
   * Specifies the minimum password age, which is the minimum length of time in
   * seconds that must elapse between user password changes.
   *
   * @param  minimumPasswordAge  The minimum password age, which is the minimum
   *                             length of time in seconds that must elapse
   *                             between user password changes.
   */
  public void setMinimumPasswordAge(int minimumPasswordAge)
  {
    assert debugEnter(CLASS_NAME, "setMinimumPasswordAge",
                      String.valueOf(minimumPasswordAge));

    this.minimumPasswordAge = minimumPasswordAge;
  }




  /**
   * Retrieves the maximum length of time in seconds that will be allowed to
   * pass between password changes before the password is expired.
   *
   * @return  The maximum length of time in seconds that will be allowed to pass
   *          between password changes before the password is expired, or zero
   *          if password expiration should not be used.
   */
  public int getMaximumPasswordAge()
  {
    assert debugEnter(CLASS_NAME, "getMaximumPasswordAge");

    if (maximumPasswordAge < 0)
    {
      return 0;
    }

    return maximumPasswordAge;
  }



  /**
   * Specifies the maximum length of time in seconds that will be allowed to
   * pass between password changes before the password is expired.
   *
   * @param  maximumPasswordAge  The maximum length of time in seconds that will
   *                             be allowed to pass between password changes
   *                             before the password is expired.
   */
  public void setMaximumPasswordAge(int maximumPasswordAge)
  {
    assert debugEnter(CLASS_NAME, "setMaximumPasswordAge",
                      String.valueOf(maximumPasswordAge));

    this.maximumPasswordAge = maximumPasswordAge;
  }



  /**
   * Retrieves the maximum length of time in seconds that will be allowed to
   * pass after an administrative password reset before that password is
   * expired.
   *
   * @return  The maximum length of time in seconds that will be allowed to pass
   *          after an administrative password reset before that password is
   *          expired, or zero if there is no limit.
   */
  public int getMaximumPasswordResetAge()
  {
    assert debugEnter(CLASS_NAME, "getMaximumPasswordResetAge");

    if (maximumPasswordResetAge < 0)
    {
      return 0;
    }

    return maximumPasswordResetAge;
  }



  /**
   * Specifies the maximum length of time in seconds that will be allowed to
   * pass after an administrative password reset before that password is
   * expired.
   *
   * @param  maximumPasswordResetAge  The maximum length of time in seconds that
   *                                  will be allowed to pass after an
   *                                  administrative password reset before that
   *                                  password is expired.
   */
  public void setMaximumPasswordResetAge(int maximumPasswordResetAge)
  {
    assert debugEnter(CLASS_NAME, "setMaximumPasswordResetAge",
                      String.valueOf(maximumPasswordResetAge));

    this.maximumPasswordResetAge = maximumPasswordResetAge;
  }



  /**
   * Retrieves the maximum length of time in seconds before the password will
   * expire that the user should start receiving warning notifications.
   *
   * @return  The maximum length of time in seconds before the password will
   *          expire that the user should start receiving warning notifications,
   *          or zero if no warning should be given.
   */
  public int getWarningInterval()
  {
    assert debugEnter(CLASS_NAME, "getWarningInterval");

    if (warningInterval < 0)
    {
      return 0;
    }

    return warningInterval;
  }



  /**
   * Specifies the maximum length of time in seconds before the password will
   * expire that the user should start receiving warning notifications.
   *
   * @param  warningInterval  The maximum length of time in seconds before the
   *                          password will expire that the user should start
   *                          receiving warning notifications.
   */
  public void setWarningInterval(int warningInterval)
  {
    assert debugEnter(CLASS_NAME, "setWarningInterval",
                      String.valueOf(warningInterval));

    this.warningInterval = warningInterval;
  }



  /**
   * Indicates whether user passwords will be allowed to expire without the
   * user receiving at least one notification during the warning period.
   *
   * @return  <CODE>true</CODE> if user passwords will be allowed to expire
   *          without the user receiving at least one notification during the
   *          warning period, or <CODE>false</CODE> if the user will always see
   *          at least one warning before the password expires.
   */
  public boolean expirePasswordsWithoutWarning()
  {
    assert debugEnter(CLASS_NAME, "expirePasswordsWithoutWarning");

    return expirePasswordsWithoutWarning;
  }



  /**
   * Specifies whether user passwords will be allowed to expire without the
   * user receiving at least one notification during the warning period.
   *
   * @param  expirePasswordsWithoutWarning  Specifies whether user passwords
   *                                        will be allowed to expire without
   *                                        the user receiving at least one
   *                                        warning during the warning period.
   */
  public void setExpirePasswordsWithoutWarning(
                   boolean expirePasswordsWithoutWarning)
  {
    assert debugEnter(CLASS_NAME, "setExpirePasswordsWithoutWarning",
                      String.valueOf(expirePasswordsWithoutWarning));

    this.expirePasswordsWithoutWarning = expirePasswordsWithoutWarning;
  }



  /**
   * Indicates whether a user will be allowed to change their password after it
   * expires and they have no remaining grace logins (and will not be allowed to
   * perform any other operation until the password is changed).
   *
   * @return  <CODE>true</CODE> if a user will be allowed to change their
   *          password after it expires and they have no remaining grace longs,
   *          or <CODE>false</CODE> if the account will be completely locked and
   *          the password must be reset by an administrator.
   */
  public boolean allowExpiredPasswordChanges()
  {
    assert debugEnter(CLASS_NAME, "allowExpiredPasswordChanges");

    return allowExpiredPasswordChanges;
  }



  /**
   * Specifies whether a user will be allowed to change their password after it
   * expires and they have no remaining grace logins.
   *
   * @param  allowExpiredPasswordChanges  Specifies whether a user will be
   *                                      allowed to change their password after
   *                                      it expires and they have no remaining
   *                                      grace logins.
   */
  public void setAllowExpiredPasswordChanges(boolean
                                                  allowExpiredPasswordChanges)
  {
    assert debugEnter(CLASS_NAME, "setAllowExpiredPasswordChanges",
                      String.valueOf(allowExpiredPasswordChanges));

    this.allowExpiredPasswordChanges = allowExpiredPasswordChanges;
  }



  /**
   * Retrieves the maximum number of grace logins that a user will be allowed
   * after their password has expired before they are completely locked out.
   *
   * @return  The maximum number of grace logins that a user will be allowed
   *          after their password has expired before they are completely
   *          locked out, or zero if no grace logins will be allowed or the
   *          grace login duration will be in effect instead of a fixed number
   *          of logins.
   */
  public int getGraceLoginCount()
  {
    assert debugEnter(CLASS_NAME, "getGraceLoginCount");

    if (graceLoginCount < 0)
    {
      return 0;
    }

    return graceLoginCount;
  }



  /**
   * Specifies the maximum number of grace logins that a user will be allowed
   * after their password has expired before they are completely locked out.
   *
   * @param  graceLoginCount  The maximum number of grace logins that a user
   *                          will be allowed after their password has expired
   *                          before they are completely locked out.
   */
  public void setGraceLoginCount(int graceLoginCount)
  {
    assert debugEnter(CLASS_NAME, "setGraceLoginCount",
                      String.valueOf(graceLoginCount));

    this.graceLoginCount = graceLoginCount;
  }



  /**
   * Retrieves the maximum number of authentication failures that will be
   * allowed before an account is locked out.
   *
   * @return  The maximum number of authentication failures that will be allowed
   *          before an account is locked out, or zero if no account lockout
   *          will be in effect.
   */
  public int getLockoutFailureCount()
  {
    assert debugEnter(CLASS_NAME, "getLockoutFailureCount");

    if (lockoutFailureCount < 0)
    {
      return 0;
    }

    return lockoutFailureCount;
  }



  /**
   * Specifies the maximum number of authentication failures that will be
   * allowed before an account is locked out.
   *
   * @param  lockoutFailureCount  The maximum number of authentication failures
   *                              that will be allowed before an account is
   *                              locked out.
   */
  public void setLockoutFailureCount(int lockoutFailureCount)
  {
    assert debugEnter(CLASS_NAME, "setLockoutFailureCount",
                      String.valueOf(lockoutFailureCount));

    this.lockoutFailureCount = lockoutFailureCount;
  }



  /**
   * Retrieves the maximum length of time in seconds that an account will be
   * locked out due to too many failed authentication attempts.
   *
   * @return  The maximum length of time in seconds that an account will be
   *          locked out due to too many failed authentication attempts, or
   *          zero if the account will remain locked until explicitly unlocked
   *          by an administrator.
   */
  public int getLockoutDuration()
  {
    assert debugEnter(CLASS_NAME, "getLockoutDuration");

    if (lockoutDuration < 0)
    {
      return 0;
    }

    return lockoutDuration;
  }



  /**
   * Specifies the maximum length of time in seconds that an account will be
   * locked out due to too many failed authentication attempts.
   *
   * @param  lockoutDuration  The maximum length of time in seconds that an
   *                          account will be locked out due to too many failed
   *                          authentication attempts.
   */
  public void setLockoutDuration(int lockoutDuration)
  {
    assert debugEnter(CLASS_NAME, "setLockoutDuration",
                      String.valueOf(lockoutDuration));

    this.lockoutDuration = lockoutDuration;
  }



  /**
   * Retrieves the maximum length of time in seconds that an authentication
   * failure will be held against a user before it is removed from the failed
   * login count.
   *
   * @return  The maximum length of time in seconds that an authentication
   *          failure will be held against a user before it is removed from the
   *          failed login count, or zero if authentication failures will never
   *          expire.
   */
  public int getLockoutFailureExpirationInterval()
  {
    assert debugEnter(CLASS_NAME, "getLockoutFailureExpirationInterval");

    if (lockoutFailureExpirationInterval < 0)
    {
      return 0;
    }

    return lockoutFailureExpirationInterval;
  }



  /**
   * Specifies the maximum length of time in seconds that an authentication
   * failure will be held against a user before it is removed from the failed
   * login count.
   *
   * @param  lockoutFailureExpirationInterval  The maximum length of time in
   *                                           seconds that an authentication
   *                                           failure will be held against a
   *                                           user before it is removed from
   *                                           the failed login count.
   */
  public void setLockoutFailureExpirationInterval(
                   int lockoutFailureExpirationInterval)
  {
    assert debugEnter(CLASS_NAME, "setLockoutFailureExpirationInterval",
                      String.valueOf(lockoutFailureExpirationInterval));

    this.lockoutFailureExpirationInterval = lockoutFailureExpirationInterval;
  }



  /**
   * Retrieves the time by which all users will be required to change their
   * passwords, expressed in the number of milliseconds since midnight of
   * January 1, 1970 (i.e., the zero time for
   * <CODE>System.currentTimeMillis()</CODE>).  Any passwords not changed before
   * this time will automatically enter a state in which they must be changed
   * before any other operation will be allowed.
   *
   * @return  The time by which all users will be required to change their
   *          passwords, or zero if no such constraint is in effect.
   */
  public long getRequireChangeByTime()
  {
    assert debugEnter(CLASS_NAME, "getRequireChangeTime");

    if (requireChangeByTime < 0)
    {
      return 0;
    }

    return requireChangeByTime;
  }



  /**
   * Specifies the time by which all users will be required to change their
   * passwords, expressed in the number of milliseconds since midnight of
   * January 1, 1970.
   *
   * @param  requireChangeByTime  The time by which all users will be required
   *                              to change their passwords.
   */
  public void setRequireChangeByTime(long requireChangeByTime)
  {
    assert debugEnter(CLASS_NAME, "setRequireChangeByTime",
                      String.valueOf(requireChangeByTime));

    this.requireChangeByTime = requireChangeByTime;
  }



  /**
   * Retrieves the attribute type used to store the last login time.
   *
   * @return  The attribute type used to store the last login time, or
   *          <CODE>null</CODE> if the last login time is not to be maintained.
   */
  public AttributeType getLastLoginTimeAttribute()
  {
    assert debugEnter(CLASS_NAME, "getLastLoginTimeAttribute");

    return lastLoginTimeAttribute;
  }



  /**
   * Specifies the attribute type used to store the last login time.
   *
   * @param  lastLoginTimeAttribute  The attribute type used to store the last
   *                                 login time, or <CODE>null</CODE> if the
   *                                 last login time is not to be maintained.
   */
  public void setLastLoginTimeAttribute(AttributeType lastLoginTimeAttribute)
  {
    assert debugEnter(CLASS_NAME, "setLastLoginTimeAttribute",
                      String.valueOf(lastLoginTimeAttribute));

    this.lastLoginTimeAttribute = lastLoginTimeAttribute;
  }



  /**
   * Retrieves the format string that should be used for the last login time.
   *
   * @return  The format string that should be used to for the last login time,
   *          or <CODE>null</CODE> if the last login time is not to be
   *          maintained.
   */
  public String getLastLoginTimeFormat()
  {
    assert debugEnter(CLASS_NAME, "getLastLoginTimeFormat");

    return lastLoginTimeFormat;
  }



  /**
   * Specifies the format string that should be used for the last login time.
   *
   * @param  lastLoginTimeFormat  The format string that should be used for the
   *                              last login time, or <CODE>null</CODE> if the
   *                              last login time is not to be maintained.
   *
   * @throws  IllegalArgumentException  If the provided time format is invalid.
   */
  public void setLastLoginTimeFormat(String lastLoginTimeFormat)
         throws IllegalArgumentException
  {
    assert debugEnter(CLASS_NAME, "setLastLoginTimeFormat",
                      String.valueOf(lastLoginTimeFormat));

    if (lastLoginTimeFormat != null)
    {
      // This will validate that the provided format string is acceptable and
      // ensure that it gets automatically generated from now on.
      TimeThread.getUserDefinedTime(lastLoginTimeFormat);
    }

    if (this.lastLoginTimeFormat != null)
    {
      TimeThread.removeUserDefinedFormatter(this.lastLoginTimeFormat);
    }

    this.lastLoginTimeFormat = lastLoginTimeFormat;
  }



  /**
   * Retrieves the list of previous last login time formats that might have been
   * used for users associated with this password policy.
   *
   * @return  The list of previous last login time formats that might have been
   *          used for users associated with this password policy.
   */
  public CopyOnWriteArrayList<String> getPreviousLastLoginTimeFormats()
  {
    assert debugEnter(CLASS_NAME, "getPreviousLastLoginTimeFormats");

    return previousLastLoginTimeFormats;
  }



  /**
   * Specifies the list of previous last login time formats that might have been
   * used for users associated with this password policy.
   *
   * @param  previousLastLoginTimeFormats  The list of previous last login time
   *                                       formats that might have been used
   *                                       for users associated with this
   *                                       password policy.
   */
  public void setPreviousLastLoginTimeFormats(CopyOnWriteArrayList<String>
                                                   previousLastLoginTimeFormats)
  {
    assert debugEnter(CLASS_NAME, "setPreviousLastLoginTimeFormats",
                      String.valueOf(previousLastLoginTimeFormats));

    if (previousLastLoginTimeFormats == null)
    {
      this.previousLastLoginTimeFormats = new CopyOnWriteArrayList<String>();
    }
    else
    {
      this.previousLastLoginTimeFormats = previousLastLoginTimeFormats;
    }
  }



  /**
   * Retrieves the maximum length of time in seconds that an account will be
   * allowed to remain idle (no authentications performed as the user) before it
   * will be locked out.
   *
   * @return  The maximum length of time in seconds that an account will be
   *          allowed to remain idle before it will be locked out.
   */
  public int getIdleLockoutInterval()
  {
    assert debugEnter(CLASS_NAME, "getIdleLockoutInterval");

    if (idleLockoutInterval < 0)
    {
      return 0;
    }

    return idleLockoutInterval;
  }



  /**
   * Specifies the maximum length of time in seconds that an account will be
   * allowed to remain idle (i.e., unused) before it will be locked out.
   *
   * @param  idleLockoutInterval  The maximum length of time in seconds that an
   *                              account will be allowed to remain idle before
   *                              it will be locked out.
   */
  public void setIdleLockoutInterval(int idleLockoutInterval)
  {
    assert debugEnter(CLASS_NAME, "setIdleLockoutInterval",
                      String.valueOf(idleLockoutInterval));

    this.idleLockoutInterval = idleLockoutInterval;
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
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
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
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");


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


    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();


    int msgID = MSGID_PWPOLICY_DESCRIPTION_PW_ATTR;
    String pwAttr;
    if (passwordAttribute == null)
    {
      pwAttr = null;
    }
    else
    {
      pwAttr = passwordAttribute.getNameOrOID();
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_PASSWORD_ATTRIBUTE,
                                           getMessage(msgID), false, false,
                                           false, pwAttr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_DEFAULT_STORAGE_SCHEMES;
    ArrayList<String> schemes = new ArrayList<String>();
    if (defaultStorageSchemes != null)
    {
      for (PasswordStorageScheme s : defaultStorageSchemes)
      {
        schemes.add(s.getStorageSchemeName());
      }
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_DEFAULT_SCHEME,
                                           getMessage(msgID), false, true,
                                           false, schemes));


    msgID = MSGID_PWPOLICY_DESCRIPTION_DEPRECATED_STORAGE_SCHEMES;
    schemes = new ArrayList<String>();
    if (deprecatedStorageSchemes != null)
    {
      schemes.addAll(deprecatedStorageSchemes);
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_DEPRECATED_SCHEME,
                                           getMessage(msgID), false, true,
                                           false, schemes));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_VALIDATORS;
    ArrayList<DN> validatorDNs = new ArrayList<DN>();
    if (passwordValidators != null)
    {
      validatorDNs.addAll(passwordValidators.keySet());
    }
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_VALIDATOR,
                                       getMessage(msgID), false, true, false,
                                       validatorDNs));


    msgID = MSGID_PWPOLICY_DESCRIPTION_NOTIFICATION_HANDLERS;
    ArrayList<DN> handlerDNs = new ArrayList<DN>();
    if (notificationHandlers != null)
    {
      handlerDNs.addAll(notificationHandlers.keySet());
    }
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_NOTIFICATION_HANDLER,
                                       getMessage(msgID), false, true, false,
                                       handlerDNs));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_USER_PW_CHANGES;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_ALLOW_USER_CHANGE,
                                            getMessage(msgID), false,
                                            allowUserPasswordChanges));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CURRENT_PW;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_REQUIRE_CURRENT_PASSWORD,
                          getMessage(msgID), false, requireCurrentPassword));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_ADD;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_ADD,
                                            getMessage(msgID), false,
                                            forceChangeOnAdd));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_RESET;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_FORCE_CHANGE_ON_RESET,
                                            getMessage(msgID), false,
                                            forceChangeOnReset));


    msgID = MSGID_PWPOLICY_DESCRIPTION_SKIP_ADMIN_VALIDATION;
    attrList.add(new BooleanConfigAttribute(ATTR_PWPOLICY_SKIP_ADMIN_VALIDATION,
                                            getMessage(msgID), false,
                                            skipValidationForAdministrators));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PASSWORD_GENERATOR;
    attrList.add(new DNConfigAttribute(ATTR_PWPOLICY_PASSWORD_GENERATOR,
                                       getMessage(msgID), false, false, false,
                                       passwordGeneratorDN));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_AUTH;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION,
                          getMessage(msgID), false,
                          requireSecureAuthentication));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_CHANGES;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES,
                          getMessage(msgID), false,
                          requireSecurePasswordChanges));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_MULTIPLE_PW_VALUES;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES,
                          getMessage(msgID), false,
                          allowMultiplePasswordValues));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_PREENCODED;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS,
                          getMessage(msgID), false, allowPreEncodedPasswords));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MIN_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MINIMUM_PASSWORD_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, minimumPasswordAge,
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MAXIMUM_PASSWORD_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, maximumPasswordAge,
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_MAX_RESET_AGE;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, maximumPasswordResetAge,
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_WARNING_INTERVAL;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_WARNING_INTERVAL, getMessage(msgID),
                          false, timeUnits, true, 0, true, Integer.MAX_VALUE,
                          warningInterval, TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_EXPIRE_WITHOUT_WARNING;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING,
                          getMessage(msgID), false,
                          expirePasswordsWithoutWarning));


    msgID = MSGID_PWPOLICY_DESCRIPTION_ALLOW_EXPIRED_CHANGES;
    attrList.add(new BooleanConfigAttribute(
                          ATTR_PWPOLICY_ALLOW_EXPIRED_CHANGES,
                          getMessage(msgID), false,
                          allowExpiredPasswordChanges));


    msgID = MSGID_PWPOLICY_DESCRIPTION_GRACE_LOGIN_COUNT;
    attrList.add(new IntegerConfigAttribute(ATTR_PWPOLICY_GRACE_LOGIN_COUNT,
                                            getMessage(msgID), false, false,
                                            false, true, 0, true,
                                            Integer.MAX_VALUE,
                                            graceLoginCount));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_FAILURE_COUNT;
    attrList.add(new IntegerConfigAttribute(ATTR_PWPOLICY_LOCKOUT_FAILURE_COUNT,
                                            getMessage(msgID), false, false,
                                            false, true, 0, true,
                                            Integer.MAX_VALUE,
                                            lockoutFailureCount));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_DURATION;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_LOCKOUT_DURATION, getMessage(msgID),
                          false, timeUnits, true, 0, true, Integer.MAX_VALUE,
                          lockoutDuration, TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_FAILURE_EXPIRATION;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE, lockoutFailureExpirationInterval,
                          TIME_UNIT_SECONDS_FULL));


    msgID = MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CHANGE_BY_TIME;
    String timeStr = null;
    if (requireChangeByTime > 0)
    {
      timeStr = GeneralizedTimeSyntax.createGeneralizedTimeValue(
                     requireChangeByTime).getStringValue();
    }
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_REQUIRE_CHANGE_BY_TIME,
                                           getMessage(msgID), false, false,
                                           false, timeStr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_ATTR;
    String loginTimeAttr;
    if (lastLoginTimeAttribute == null)
    {
      loginTimeAttr = null;
    }
    else
    {
      loginTimeAttr = lastLoginTimeAttribute.getNameOrOID();
    }
    attrList.add(new StringConfigAttribute(
                          ATTR_PWPOLICY_LAST_LOGIN_TIME_ATTRIBUTE,
                          getMessage(msgID), false, false, false,
                          loginTimeAttr));


    msgID = MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_FORMAT;
    attrList.add(new StringConfigAttribute(ATTR_PWPOLICY_LAST_LOGIN_TIME_FORMAT,
                                           getMessage(msgID), false, false,
                                           false, lastLoginTimeFormat));


    msgID = MSGID_PWPOLICY_DESCRIPTION_PREVIOUS_LAST_LOGIN_TIME_FORMAT;
    ArrayList<String> previousFormats = new ArrayList<String>();
    if (previousLastLoginTimeFormats != null)
    {
      previousFormats.addAll(previousLastLoginTimeFormats);
    }
    attrList.add(new StringConfigAttribute(
                          ATTR_PWPOLICY_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
                          getMessage(msgID), false, false, false,
                          previousFormats));


    msgID = MSGID_PWPOLICY_DESCRIPTION_IDLE_LOCKOUT_INTERVAL;
    attrList.add(new IntegerWithUnitConfigAttribute(
                          ATTR_PWPOLICY_IDLE_LOCKOUT_INTERVAL,
                          getMessage(msgID), false, timeUnits, true, 0, true,
                          Integer.MAX_VALUE,  idleLockoutInterval,
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
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    PasswordPolicy p = new PasswordPolicy();

    try
    {
      initializePasswordPolicyConfig(configEntry, p);
    }
    catch (ConfigException ce)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ce);

      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ie);

      unacceptableReasons.add(ie.getMessage());
      return false;
    }


    // The provided config entry must at least specify the password attribute
    // and at least one default storage scheme.
    if (p.passwordAttribute == null)
    {
      int    msgID   = MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReasons.add(message);
      return false;
    }

    if ((p.defaultStorageSchemes == null) ||
        p.defaultStorageSchemes.isEmpty())
    {
      int    msgID   = MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReasons.add(message);
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
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();
    PasswordPolicy    p                   = new PasswordPolicy();

    try
    {
      initializePasswordPolicyConfig(configEntry, p);
    }
    catch (ConfigException ce)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ce);

      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(ce.getMessage());

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ie);

      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(ie.getMessage());

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // The provided config entry must at least specify the password attribute
    // and at least one default storage scheme.
    if (p.passwordAttribute == null)
    {
      resultCode = DirectoryServer.getServerErrorResultCode();

      int    msgID   = MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }

    if ((p.defaultStorageSchemes == null) ||
        p.defaultStorageSchemes.isEmpty())
    {
      resultCode = DirectoryServer.getServerErrorResultCode();

      int    msgID   = MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // If we've made it here, then everything is acceptable.  Apply the new
    // configuration.
    passwordAttribute                = p.passwordAttribute;
    lastLoginTimeAttribute           = p.lastLoginTimeAttribute;
    allowMultiplePasswordValues      = p.allowMultiplePasswordValues;
    allowPreEncodedPasswords         = p.allowPreEncodedPasswords;
    allowUserPasswordChanges         = p.allowUserPasswordChanges;
    expirePasswordsWithoutWarning    = p.expirePasswordsWithoutWarning;
    allowExpiredPasswordChanges      = p.allowExpiredPasswordChanges;
    forceChangeOnAdd                 = p.forceChangeOnAdd;
    forceChangeOnReset               = p.forceChangeOnReset;
    requireCurrentPassword           = p.requireCurrentPassword;
    requireSecureAuthentication      = p.requireSecureAuthentication;
    requireSecurePasswordChanges     = p.requireSecurePasswordChanges;
    skipValidationForAdministrators  = p.skipValidationForAdministrators;
    graceLoginCount                  = p.graceLoginCount;
    idleLockoutInterval              = p.idleLockoutInterval;
    lockoutDuration                  = p.lockoutDuration;
    lockoutFailureCount              = p.lockoutFailureCount;
    lockoutFailureExpirationInterval = p.lockoutFailureExpirationInterval;
    minimumPasswordAge               = p.minimumPasswordAge;
    maximumPasswordAge               = p.maximumPasswordAge;
    maximumPasswordResetAge          = p.maximumPasswordResetAge;
    warningInterval                  = p.warningInterval;
    requireChangeByTime              = p.requireChangeByTime;
    lastLoginTimeFormat              = p.lastLoginTimeFormat;
    previousLastLoginTimeFormats     = p.previousLastLoginTimeFormats;
    passwordGenerator                = p.passwordGenerator;
    notificationHandlers             = p.notificationHandlers;
    defaultStorageSchemes            = p.defaultStorageSchemes;
    deprecatedStorageSchemes         = p.deprecatedStorageSchemes;
    passwordValidators               = p.passwordValidators;

    if (detailedResults)
    {
      int msgID = MSGID_PWPOLICY_UPDATED_POLICY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Retrieves a string representation of this password policy.
   *
   * @return  A string representation of this password policy.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this password policy to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");


    buffer.append("Password Attribute:                    ");
    buffer.append(passwordAttribute.getNameOrOID());
    buffer.append(EOL);

    buffer.append("Default Password Storage Schemes:      ");
    if ((defaultStorageSchemes == null) || defaultStorageSchemes.isEmpty())
    {
      buffer.append("{none specified}");
      buffer.append(EOL);
    }
    else
    {
      Iterator<PasswordStorageScheme> iterator =
           defaultStorageSchemes.iterator();
      buffer.append(iterator.next().getStorageSchemeName());
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append("                                       ");
        buffer.append(iterator.next().getStorageSchemeName());
        buffer.append(EOL);
      }
    }

    buffer.append("Deprecated Password Storage Schemes:   ");
    if ((deprecatedStorageSchemes == null) ||
        deprecatedStorageSchemes.isEmpty())
    {
      buffer.append("{none specified}");
      buffer.append(EOL);
    }
    else
    {
      Iterator<String> iterator = deprecatedStorageSchemes.iterator();
      buffer.append(iterator.next());
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append("                                       ");
        buffer.append(iterator.next());
        buffer.append(EOL);
      }
    }

    buffer.append("Allow Multiple Password Values:        ");
    buffer.append(allowMultiplePasswordValues);
    buffer.append(EOL);

    buffer.append("Allow Pre-Encoded Passwords:           ");
    buffer.append(allowPreEncodedPasswords);
    buffer.append(EOL);

    buffer.append("Allow User Password Changes:           ");
    buffer.append(allowUserPasswordChanges);
    buffer.append(EOL);

    buffer.append("Force Password Change on Add:          ");
    buffer.append(forceChangeOnAdd);
    buffer.append(EOL);

    buffer.append("Force Password Change on Admin Reset:  ");
    buffer.append(forceChangeOnReset);
    buffer.append(EOL);

    buffer.append("Require Current Password:              ");
    buffer.append(requireCurrentPassword);
    buffer.append(EOL);

    buffer.append("Require Secure Authentication:         ");
    buffer.append(requireSecureAuthentication);
    buffer.append(EOL);

    buffer.append("Require Secure Password Changes:       ");
    buffer.append(requireSecurePasswordChanges);
    buffer.append(EOL);

    buffer.append("Lockout Failure Expiration Interval:   ");
    buffer.append(lockoutFailureExpirationInterval);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Password Validators:                   ");
    if ((passwordValidators == null) || passwordValidators.isEmpty())
    {
      buffer.append("{none specified}");
      buffer.append(EOL);
    }
    else
    {
      Iterator<DN> iterator = passwordValidators.keySet().iterator();
      iterator.next().toString(buffer);
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append("                                       ");
        iterator.next().toString(buffer);
        buffer.append(EOL);
      }
    }

    buffer.append("Skip Validation for Administrators:    ");
    buffer.append(skipValidationForAdministrators);
    buffer.append(EOL);

    buffer.append("Password Generator:                    ");
    if (passwordGenerator == null)
    {
      buffer.append("{none specified}");
    }
    else
    {
      passwordGeneratorDN.toString(buffer);
    }
    buffer.append(EOL);

    buffer.append("Account Status Notification Handlers:  ");
    if ((notificationHandlers == null) || notificationHandlers.isEmpty())
    {
      buffer.append("{none specified}");
      buffer.append(EOL);
    }
    else
    {
      Iterator<DN> iterator = notificationHandlers.keySet().iterator();
      iterator.next().toString(buffer);
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append("                                       ");
        iterator.next().toString(buffer);
        buffer.append(EOL);
      }
    }

    buffer.append("Minimum Password Age:                  ");
    buffer.append(minimumPasswordAge);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Maximum Password Age:                  ");
    buffer.append(maximumPasswordAge);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Maximum Password Reset Age:            ");
    buffer.append(maximumPasswordResetAge);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Expiration Warning Interval:           ");
    buffer.append(warningInterval);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Expire Passwords Without Warning:      ");
    buffer.append(expirePasswordsWithoutWarning);
    buffer.append(EOL);

    buffer.append("Allow Expired Password Changes:        ");
    buffer.append(allowExpiredPasswordChanges);
    buffer.append(EOL);

    buffer.append("Grace Login Count:                     ");
    buffer.append(graceLoginCount);
    buffer.append(EOL);

    buffer.append("Lockout Failure Count:                 ");
    buffer.append(lockoutFailureCount);
    buffer.append(EOL);

    buffer.append("Lockout Duration:                      ");
    buffer.append(lockoutDuration);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Lockout Count Expiration Interval:     ");
    buffer.append(lockoutFailureExpirationInterval);
    buffer.append(" seconds");
    buffer.append(EOL);

    buffer.append("Required Password Change By Time:      ");
    if (requireChangeByTime <= 0)
    {
      buffer.append("{none specified}");
    }
    else
    {
      SimpleDateFormat dateFormat =
           new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      buffer.append(dateFormat.format(new Date(requireChangeByTime)));
    }
    buffer.append(EOL);

    buffer.append("Last Login Time Attribute:             ");
    if (lastLoginTimeAttribute == null)
    {
      buffer.append("{none specified}");
    }
    else
    {
      buffer.append(lastLoginTimeAttribute.getNameOrOID());
    }
    buffer.append(EOL);

    buffer.append("Last Login Time Format:                ");
    if (lastLoginTimeFormat == null)
    {
      buffer.append("{none specified}");
    }
    else
    {
      buffer.append(lastLoginTimeFormat);
    }
    buffer.append(EOL);

    buffer.append("Previous Last Login Time Formats:      ");
    if ((previousLastLoginTimeFormats == null) ||
        previousLastLoginTimeFormats.isEmpty())
    {
      buffer.append("{none specified}");
      buffer.append(EOL);
    }
    else
    {
      Iterator<String> iterator = previousLastLoginTimeFormats.iterator();

      buffer.append(iterator.next());
      buffer.append(EOL);

      while (iterator.hasNext())
      {
        buffer.append("                                       ");
        buffer.append(iterator.next());
        buffer.append(EOL);
      }
    }

    buffer.append("Idle Lockout Interval:                 ");
    buffer.append(idleLockoutInterval);
    buffer.append(" seconds");
    buffer.append(EOL);
  }
}

