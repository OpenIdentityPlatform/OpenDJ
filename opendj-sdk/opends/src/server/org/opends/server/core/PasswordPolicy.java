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
package org.opends.server.core;



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opends.messages.Message;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure that holds information about a Directory
 * Server password policy.
 */
public class PasswordPolicy
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The DN of the entry containing the configuration for this password
  // policy.
  private final DN configEntryDN;

  // The attribute type that will hold user passwords for this password policy.
  private final AttributeType passwordAttribute;

  // Indicates whether the attribute type uses the authPassword syntax.
  private final boolean authPasswordSyntax;

  // Indicates whether a user with an expired password will still be allowed to
  // change it via the password modify extended operation.
  private boolean allowExpiredPasswordChanges =
       DEFAULT_PWPOLICY_ALLOW_EXPIRED_CHANGES;

  // Indicates whether the password attribute will be allowed to have multiple
  // distinct values.
  private boolean allowMultiplePasswordValues =
       DEFAULT_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES;

  // Indicates whether to allow pre-encoded passwords.
  private boolean allowPreEncodedPasswords =
       DEFAULT_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS;

  // Indicates whether users will be allowed to change their passwords.
  private boolean allowUserPasswordChanges =
       DEFAULT_PWPOLICY_ALLOW_USER_CHANGE;

  // Indicates whether to allow a password to expire without ever providing the
  // user with a notification.
  private boolean expirePasswordsWithoutWarning =
       DEFAULT_PWPOLICY_EXPIRE_WITHOUT_WARNING;

  // Indicates whether users must change their passwords the first time they
  // authenticate after their account is created.
  private boolean forceChangeOnAdd =
       DEFAULT_PWPOLICY_FORCE_CHANGE_ON_ADD;

  // Indicates whether a user must change their password after it has been reset
  // by an administrator.
  private boolean forceChangeOnReset =
       DEFAULT_PWPOLICY_FORCE_CHANGE_ON_RESET;

  // Indicates whether a user must provide their current password in order to
  // use a new password.
  private boolean requireCurrentPassword =
       DEFAULT_PWPOLICY_REQUIRE_CURRENT_PASSWORD;

  // Indicates whether users will be required to authenticate using a secure
  // mechanism.
  private boolean requireSecureAuthentication =
       DEFAULT_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION;

  // Indicates whether users will be required to change their passwords using a
  // secure mechanism.
  private boolean requireSecurePasswordChanges =
       DEFAULT_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES;

  // Indicates whether password validation should be performed for
  // administrative password changes.
  private boolean skipValidationForAdministrators =
       DEFAULT_PWPOLICY_SKIP_ADMIN_VALIDATION;

  // The set of account status notification handlers for this password policy.
  private ConcurrentHashMap<DN, AccountStatusNotificationHandler>
    notificationHandlers;

  // The set of password validators that will be used with this
  // password policy.
  private ConcurrentHashMap<DN, PasswordValidator<?>> passwordValidators;

  // The set of default password storage schemes for this password
  // policy.
  private CopyOnWriteArrayList<PasswordStorageScheme> defaultStorageSchemes =
       new CopyOnWriteArrayList<PasswordStorageScheme>();
  {
    PasswordStorageScheme defaultScheme =
      DirectoryServer.getPasswordStorageScheme(DEFAULT_PASSWORD_STORAGE_SCHEME);
    if (defaultScheme != null) defaultStorageSchemes.add(defaultScheme);
  }

  // The names of the deprecated password storage schemes for this password
  // policy.
  private CopyOnWriteArraySet<String> deprecatedStorageSchemes =
       new CopyOnWriteArraySet<String>();

  // The DN of the password validator for this password policy.
  private DN passwordGeneratorDN = null;

  // The password generator for use with this password policy.
  private PasswordGenerator passwordGenerator = null;

  // The number of grace logins that a user may have.
  private int graceLoginCount = DEFAULT_PWPOLICY_GRACE_LOGIN_COUNT;

  // The number of passwords to keep in the history.
  private int historyCount = DEFAULT_PWPOLICY_HISTORY_COUNT;

  // The maximum length of time in seconds to keep passwords in the history.
  private int historyDuration = DEFAULT_PWPOLICY_HISTORY_DURATION;

  // The maximum length of time in seconds that an account may remain idle
  // before it is locked out.
  private int idleLockoutInterval = DEFAULT_PWPOLICY_IDLE_LOCKOUT_INTERVAL;

  // The length of time a user should stay locked out, in seconds.
  private int lockoutDuration = DEFAULT_PWPOLICY_LOCKOUT_DURATION;

  // The number of authentication failures before an account is locked out.
  private int lockoutFailureCount = DEFAULT_PWPOLICY_LOCKOUT_FAILURE_COUNT;

  // The length of time that authentication failures should be counted against
  // a user.
  private int lockoutFailureExpirationInterval =
       DEFAULT_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL;

  // The maximum password age (i.e., expiration interval), in seconds.
  private int maximumPasswordAge = DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_AGE;

  // The maximum password age for administratively reset passwords, in seconds.
  private int maximumPasswordResetAge =
       DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE;

  // The minimum password age, in seconds.
  private int minimumPasswordAge = DEFAULT_PWPOLICY_MINIMUM_PASSWORD_AGE;

  // The password expiration warning interval, in seconds.
  private int warningInterval = DEFAULT_PWPOLICY_WARNING_INTERVAL;

  // The the time by which all users will be required to change their passwords.
  private long requireChangeByTime = -1L;

  // The attribute type that will hold the last login time.
  private AttributeType lastLoginTimeAttribute = null;

  // The format string to use when generating the last login time.
  private String lastLoginTimeFormat = null;

  // The set of previous last login time format strings.
  private CopyOnWriteArrayList<String> previousLastLoginTimeFormats =
       new CopyOnWriteArrayList<String>();

  // The state update failure policy.
  private PasswordPolicyCfgDefn.StateUpdateFailurePolicy
       stateUpdateFailurePolicy =
            PasswordPolicyCfgDefn.StateUpdateFailurePolicy.REACTIVE;



  /**
   * Creates a new password policy based on the configuration contained in the
   * provided configuration entry.  Any parameters not included in the provided
   * configuration entry will be assigned server-wide default values.
   *
   * @param  configuration  The configuration with the information to use to
   *                      initialize this password policy.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           password policy configuration.
   *
   * @throws  InitializationException  If an error occurs while initializing the
   *                                   password policy that is not related to
   *                                   the server configuration.
   */
  public PasswordPolicy(PasswordPolicyCfg configuration)
         throws ConfigException, InitializationException
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

    this.configEntryDN = configuration.dn();

    // Get the password attribute.  If specified, it must have either the
    // user password or auth password syntax.
    passwordAttribute = configuration.getPasswordAttribute();
    String syntaxOID = passwordAttribute.getSyntaxOID();
    if (syntaxOID.equals(SYNTAX_AUTH_PASSWORD_OID))
    {
      authPasswordSyntax = true;
    }
    else if (syntaxOID.equals(SYNTAX_USER_PASSWORD_OID))
    {
      authPasswordSyntax = false;
    }
    else
    {
      String syntax = passwordAttribute.getSyntax().getSyntaxName();
      if ((syntax == null) || (syntax.length() == 0))
      {
        syntax = syntaxOID;
      }

      Message message = ERR_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX.
          get(String.valueOf(configEntryDN), passwordAttribute.getNameOrOID(),
              String.valueOf(syntax));
      throw new ConfigException(message);
    }


    // Get the default storage schemes.  They must all reference valid storage
    // schemes that support the syntax for the specified password attribute.
    SortedSet<DN> storageSchemeDNs =
      configuration.getDefaultPasswordStorageSchemeDNs();
    try
    {
      LinkedList<PasswordStorageScheme> schemes =
        new LinkedList<PasswordStorageScheme>();
      for (DN configEntryDN : storageSchemeDNs)
      {
        PasswordStorageScheme scheme =
          DirectoryServer.getPasswordStorageScheme(configEntryDN);

        if (this.authPasswordSyntax &&
            (! scheme.supportsAuthPasswordSyntax()))
        {
          Message message = ERR_PWPOLICY_SCHEME_DOESNT_SUPPORT_AUTH.get(
              String.valueOf(configEntryDN),
              this.passwordAttribute.getNameOrOID());
          throw new ConfigException(message);
        }

        schemes.add(scheme);
      }

      this.defaultStorageSchemes =
        new CopyOnWriteArrayList<PasswordStorageScheme>(schemes);
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWPOLICY_CANNOT_DETERMINE_DEFAULT_STORAGE_SCHEMES.
          get(String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the names of the deprecated storage schemes.
    SortedSet<DN> deprecatedStorageSchemeDNs =
      configuration.getDeprecatedPasswordStorageSchemeDNs();
    try
    {
      LinkedHashSet<String> newDeprecatedStorageSchemes =
        new LinkedHashSet<String>();
      for (DN schemeDN : deprecatedStorageSchemeDNs)
      {
        PasswordStorageScheme scheme =
          DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (this.authPasswordSyntax)
        {
          if (scheme.supportsAuthPasswordSyntax())
          {
            newDeprecatedStorageSchemes.add(
                scheme.getAuthPasswordSchemeName());
          }
          else
          {
            Message message = ERR_PWPOLICY_DEPRECATED_SCHEME_NOT_AUTH.get(
                String.valueOf(configEntryDN),
                String.valueOf(schemeDN));
            throw new ConfigException(message);
          }
        }
        else
        {
          newDeprecatedStorageSchemes.add(
              toLowerCase(scheme.getStorageSchemeName()));
        }
      }

      this.deprecatedStorageSchemes =
        new CopyOnWriteArraySet<String>(newDeprecatedStorageSchemes);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_PWPOLICY_CANNOT_DETERMINE_DEPRECATED_STORAGE_SCHEMES.
            get(String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the password validators.
    SortedSet<DN> passwordValidators = configuration.getPasswordValidatorDNs();
    ConcurrentHashMap<DN, PasswordValidator<?>> validators =
      new ConcurrentHashMap<DN, PasswordValidator<?>>();
    for (DN validatorDN : passwordValidators)
    {
      validators.put(validatorDN,
          DirectoryServer.getPasswordValidator(validatorDN));
    }
    this.passwordValidators = validators;


    // Get the status notification handlers.
    SortedSet<DN> statusNotificationHandlers =
      configuration.getAccountStatusNotificationHandlerDNs();
    ConcurrentHashMap<DN,AccountStatusNotificationHandler> handlers =
      new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();
    for (DN handlerDN : statusNotificationHandlers)
    {
      AccountStatusNotificationHandler handler =
        DirectoryServer.getAccountStatusNotificationHandler(handlerDN);
      handlers.put(handlerDN, handler);
    }
    this.notificationHandlers = handlers;


    // Determine whether to allow user password changes.
    this.allowUserPasswordChanges = configuration.isAllowUserPasswordChanges();

    // Determine whether to require the current password for user changes.
    this.requireCurrentPassword =
      configuration.isPasswordChangeRequiresCurrentPassword();

    // Determine whether to force password changes on add.
    this.forceChangeOnAdd = configuration.isForceChangeOnAdd();

    // Determine whether to force password changes on reset.
    this.forceChangeOnReset = configuration.isForceChangeOnReset();

    // Determine whether to validate reset passwords.
    this.skipValidationForAdministrators =
      configuration.isSkipValidationForAdministrators();

    // Get the password generator.
    DN passGenDN = configuration.getPasswordGeneratorDN() ;
    if (passGenDN != null)
    {
      this.passwordGeneratorDN = passGenDN;
      this.passwordGenerator = DirectoryServer.getPasswordGenerator(passGenDN);
    }


    // Determine whether to require secure authentication.
    this.requireSecureAuthentication =
      configuration.isRequireSecureAuthentication();

    // Determine whether to require secure password changes.
    this.requireSecurePasswordChanges =
      configuration.isRequireSecurePasswordChanges() ;

    // Determine whether to allow multiple password values.
    this.allowMultiplePasswordValues =
      configuration.isAllowMultiplePasswordValues();

    // Determine whether to allow pre-encoded passwords.
    this.allowPreEncodedPasswords = configuration.isAllowPreEncodedPasswords();

    // Get the minimum password age.
    this.minimumPasswordAge = (int) configuration.getMinPasswordAge();

    // Get the maximum password age.
    this.maximumPasswordAge = (int) configuration.getMaxPasswordAge();

    // Get the maximum password reset age.
    this.maximumPasswordResetAge = (int) configuration
        .getMaxPasswordResetAge();

    // Get the warning interval.
    this.warningInterval = (int) configuration
        .getPasswordExpirationWarningInterval();

    // Determine whether to expire passwords without warning.
    this.expirePasswordsWithoutWarning = configuration
        .isExpirePasswordsWithoutWarning();

    // If the expire without warning option is disabled, then there must be a
    // warning interval.
    if ((! this.expirePasswordsWithoutWarning()) &&
        (this.getWarningInterval() <= 0))
    {
      Message message =
        ERR_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING.
            get(String.valueOf(configEntryDN));
      throw new ConfigException(message);
    }

    // Determine whether to allow user changes for expired passwords.
    this.allowExpiredPasswordChanges = configuration
        .isAllowExpiredPasswordChanges();

    // Get the grace login count.
    this.graceLoginCount = configuration.getGraceLoginCount();

    // Get the lockout failure count.
    this.lockoutFailureCount = configuration.getLockoutFailureCount();

    // Get the lockout duration.
    this.lockoutDuration = (int) configuration.getLockoutDuration();

    // Get the lockout failure expiration interval.
    this.lockoutFailureExpirationInterval = (int) configuration
        .getLockoutFailureExpirationInterval();

    // Get the required change time.
    String requireChangeBy = configuration.getRequireChangeByTime();
    try
    {
      if (requireChangeBy != null)
      {
        ByteString valueString = new ASN1OctetString(requireChangeBy);

        GeneralizedTimeSyntax syntax =
             (GeneralizedTimeSyntax)
             DirectoryServer.getAttributeSyntax(SYNTAX_GENERALIZED_TIME_OID,
                                                false);

        if (syntax == null)
        {
          this.requireChangeByTime =
               GeneralizedTimeSyntax.decodeGeneralizedTimeValue(valueString);
        }
        else
        {
          valueString =
               syntax.getEqualityMatchingRule().normalizeValue(valueString);
          this.requireChangeByTime =
               GeneralizedTimeSyntax.decodeGeneralizedTimeValue(valueString);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME.
          get(String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the last login time attribute.  If specified, it must be defined in
    // the server schema.  It does not need to have a generalized time syntax
    // because the value that it will store will not necessarily conform to this
    // format.
    lastLoginTimeAttribute = configuration.getLastLoginTimeAttribute();


    // Get the last login time format.  If specified, it must be a valid format
    // string.
    String formatString = configuration.getLastLoginTimeFormat();
    try
    {
      if (formatString != null)
      {
        try
        {
          new SimpleDateFormat(formatString);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_PWPOLICY_INVALID_LAST_LOGIN_TIME_FORMAT.get(
              String.valueOf(configEntryDN), String.valueOf(formatString));
          throw new ConfigException(message);
        }

        this.lastLoginTimeFormat = formatString;
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_FORMAT.
          get(String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the previous last login time formats.  If specified, they must all
    // be valid format strings.
    SortedSet<String> formatStrings =
      configuration.getPreviousLastLoginTimeFormat() ;
    try
    {
      if (formatStrings != null)
      {
        for (String s : formatStrings)
        {
          try
          {
            new SimpleDateFormat(s);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message =
              ERR_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT.
                  get(String.valueOf(configEntryDN), String.valueOf(s));
            throw new ConfigException(message);
          }
        }

        this.previousLastLoginTimeFormats =
             new CopyOnWriteArrayList<String>(formatStrings);
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_PWPOLICY_CANNOT_DETERMINE_PREVIOUS_LAST_LOGIN_TIME_FORMAT.
            get(String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the idle lockout duration.
    this.idleLockoutInterval = (int) configuration.getIdleLockoutInterval();


    // Get the state update failure policy.
    this.stateUpdateFailurePolicy = configuration.getStateUpdateFailurePolicy();


    // Get the password history count and duration.
    this.historyCount    = configuration.getPasswordHistoryCount();
    this.historyDuration = (int) configuration.getPasswordHistoryDuration();


    /*
     *  Holistic validation.
     */

    // Ensure that the password attribute was included in the configuration
    // entry, since it is required.
    if (passwordAttribute == null)
    {
      Message message =
          ERR_PWPOLICY_NO_PASSWORD_ATTRIBUTE.get(String.valueOf(configEntryDN));
      throw new ConfigException(message);
    }

    // Ensure that at least one default password storage scheme was included in
    // the configuration entry, since it is required.
    if (defaultStorageSchemes.isEmpty())
    {
      Message message = ERR_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES.get(
          String.valueOf(configEntryDN));
      throw new ConfigException(message);
    }

    // If both a maximum password age and a warning interval are provided, then
    // ensure that the warning interval is less than the maximum age.  Further,
    // if a minimum age is specified, then the sum of the minimum age and the
    // warning interval should be less than the maximum age.
    if (maximumPasswordAge > 0)
    {
      int warnInterval = Math.max(0, warningInterval);
      if (minimumPasswordAge > 0)
      {
        if ((warnInterval + minimumPasswordAge) >= maximumPasswordAge)
        {
          Message message =
              ERR_PWPOLICY_MIN_AGE_PLUS_WARNING_GREATER_THAN_MAX_AGE.
                get(String.valueOf(configEntryDN));
          throw new ConfigException(message);
        }
      }
      else if (warnInterval >= maximumPasswordAge)
      {
        Message message = ERR_PWPOLICY_WARNING_INTERVAL_LARGER_THAN_MAX_AGE.get(
            String.valueOf(configEntryDN));
        throw new ConfigException(message);
      }
    }
  }



  /**
   * Retrieves the DN of the configuration entry to which this password policy
   * corresponds.
   *
   * @return  The DN of the configuration entry.
   */
  public DN getConfigEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the attribute type used to store the password.
   *
   * @return  The attribute type used to store the password.
   */
  public AttributeType getPasswordAttribute()
  {
    return passwordAttribute;
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
    return defaultStorageSchemes;
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
    return deprecatedStorageSchemes;
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
  public ConcurrentHashMap<DN,
              PasswordValidator<? extends PasswordValidatorCfg>>
              getPasswordValidators()
  {
    return passwordValidators;
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
    return notificationHandlers;
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
    return allowUserPasswordChanges;
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
    return requireCurrentPassword;
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
    return forceChangeOnAdd;
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
    return forceChangeOnReset;
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
    return skipValidationForAdministrators;
  }



  /**
   * Retrieves the DN of the password validator configuration entry.
   *
   * @return  The DN of the password validator configuration entry.
   */
  public DN getPasswordGeneratorDN()
  {
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
    return passwordGenerator;
  }



  /**
   * Retrieves the maximum number of previous passwords to maintain in the
   * password history.
   *
   * @return  The maximum number of previous passwords to maintain in the
   *          password history.
   */
  public int getPasswordHistoryCount()
  {
    return historyCount;
  }



  /**
   * Retrieves the maximum length of time in seconds that previous passwords
   * should remain in the password history.
   *
   * @return  The maximum length of time in seconds that previous passwords
   *          should remain in the password history.
   */
  public int getPasswordHistoryDuration()
  {
    return historyDuration;
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
    return requireSecureAuthentication;
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
    return requireSecurePasswordChanges;
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
    return allowMultiplePasswordValues;
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
    return allowPreEncodedPasswords;
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
    if (minimumPasswordAge <= 0)
    {
      return 0;
    }

    return minimumPasswordAge;
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
    if (maximumPasswordAge < 0)
    {
      return 0;
    }

    return maximumPasswordAge;
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
    if (maximumPasswordResetAge < 0)
    {
      return 0;
    }

    return maximumPasswordResetAge;
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
    if (warningInterval < 0)
    {
      return 0;
    }

    return warningInterval;
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
    return expirePasswordsWithoutWarning;
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
    return allowExpiredPasswordChanges;
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
    if (graceLoginCount < 0)
    {
      return 0;
    }

    return graceLoginCount;
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
    if (lockoutFailureCount < 0)
    {
      return 0;
    }

    return lockoutFailureCount;
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
    if (lockoutDuration < 0)
    {
      return 0;
    }

    return lockoutDuration;
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
    if (lockoutFailureExpirationInterval < 0)
    {
      return 0;
    }

    return lockoutFailureExpirationInterval;
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
    if (requireChangeByTime < 0)
    {
      return 0;
    }

    return requireChangeByTime;
  }



  /**
   * Retrieves the attribute type used to store the last login time.
   *
   * @return  The attribute type used to store the last login time, or
   *          <CODE>null</CODE> if the last login time is not to be maintained.
   */
  public AttributeType getLastLoginTimeAttribute()
  {
    return lastLoginTimeAttribute;
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
    return lastLoginTimeFormat;
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
    return previousLastLoginTimeFormats;
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
    if (idleLockoutInterval < 0)
    {
      return 0;
    }

    return idleLockoutInterval;
  }



  /**
   * Retrieves the state update failure policy for this password policy.
   *
   * @return  The state update failure policy for this password policy.
   */
  public PasswordPolicyCfgDefn.StateUpdateFailurePolicy
              getStateUpdateFailurePolicy()
  {
    return stateUpdateFailurePolicy;
  }



  /**
   * Retrieves a string representation of this password policy.
   *
   * @return  A string representation of this password policy.
   */
  public String toString()
  {
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

    buffer.append("History Count:                         ");
    buffer.append(historyCount);
    buffer.append(EOL);

    buffer.append("Update Failure Policy:                 ");
    buffer.append(stateUpdateFailurePolicy.toString());
    buffer.append(EOL);
  }
}

