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
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.core;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.text.SimpleDateFormat;
import java.util.*;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn.*;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.api.*;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;



/**
 * This class is the interface between the password policy configurable
 * component and a password policy state object. When a password policy entry is
 * added to the configuration, an instance of this class is created and
 * registered to manage subsequent modification to that configuration entry,
 * including validating any proposed modification and applying an accepted
 * modification.
 */
public final class PasswordPolicyFactory implements
    AuthenticationPolicyFactory<PasswordPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Password policy implementation.
   */
  private static final class PasswordPolicyImpl extends PasswordPolicy
      implements ConfigurationChangeListener<PasswordPolicyCfg>
  {
    /**
     * The tracer object for the debug logger.
     */
    private static final DebugTracer TRACER = getTracer();

    // Current configuration.
    private PasswordPolicyCfg configuration;

    // Indicates whether the attribute type uses the authPassword syntax.
    private boolean authPasswordSyntax;

    // The set of account status notification handlers for this password policy.
    private Map<DN, AccountStatusNotificationHandler<?>> notificationHandlers;

    // The set of password validators that will be used with this
    // password policy.
    private Map<DN, PasswordValidator<?>> passwordValidators;

    // The set of default password storage schemes for this password
    // policy.
    private List<PasswordStorageScheme<?>> defaultStorageSchemes;

    // The names of the deprecated password storage schemes for this password
    // policy.
    private Set<String> deprecatedStorageSchemes;

    // The password generator for use with this password policy.
    private PasswordGenerator<?> passwordGenerator;

    // The the time by which all users will be required to change their
    // passwords.
    private long requireChangeByTime;



    /**
     * {@inheritDoc}
     */
    public void finalizeAuthenticationPolicy()
    {
      configuration.removePasswordPolicyChangeListener(this);
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        PasswordPolicyCfg configuration)
    {
      ArrayList<Message> messages = new ArrayList<Message>();
      try
      {
        updateConfiguration(configuration, this);
        return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
      }
      catch (ConfigException ce)
      {
        messages.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(configuration.dn()), ce.getMessage()));

        return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
            messages);
      }
      catch (InitializationException ie)
      {
        messages.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(configuration.dn()), ie.getMessage()));

        return new ConfigChangeResult(
            DirectoryServer.getServerErrorResultCode(), false, messages);
      }
      catch (Exception e)
      {
        messages
            .add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
                String.valueOf(configuration.dn()),
                stackTraceToSingleLineString(e)));

        return new ConfigChangeResult(
            DirectoryServer.getServerErrorResultCode(), false, messages);
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        PasswordPolicyCfg configuration, List<Message> unacceptableReasons)
    {
      try
      {
        updateConfiguration(configuration, null);
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(configuration.dn()), ce.getMessage());
        unacceptableReasons.add(message);
        return false;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(configuration.dn()), ie.getMessage());
        unacceptableReasons.add(message);
        return false;
      }
      catch (Exception e)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG
            .get(String.valueOf(configuration.dn()),
                stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      // If we've gotten here, then it is acceptable.
      return true;
    }



    /**
     * Creates a new password policy based on the configuration contained in the
     * provided configuration entry. Any parameters not included in the provided
     * configuration entry will be assigned server-wide default values.
     *
     * @param configuration
     *          The configuration with the information to use to initialize this
     *          password policy.
     * @throws ConfigException
     *           If the provided entry does not contain a valid password policy
     *           configuration.
     * @throws InitializationException
     *           If an error occurs while initializing the password policy that
     *           is not related to the server configuration.
     */
    private PasswordPolicyImpl(PasswordPolicyCfg configuration)
        throws ConfigException, InitializationException
    {
      updateConfiguration(configuration, this);
    }



    private static void updateConfiguration(PasswordPolicyCfg configuration,
        PasswordPolicyImpl policy) throws ConfigException,
        InitializationException
    {
      final DN configEntryDN = configuration.dn();

      // Get the password attribute. If specified, it must have either the
      // user password or auth password syntax.
      final AttributeType passwordAttribute = configuration
          .getPasswordAttribute();
      final String syntaxOID = passwordAttribute.getSyntaxOID();
      final boolean authPasswordSyntax;
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

        Message message = ERR_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX.get(
            String.valueOf(configEntryDN), passwordAttribute.getNameOrOID(),
            String.valueOf(syntax));
        throw new ConfigException(message);
      }

      // Get the default storage schemes. They must all reference valid storage
      // schemes that support the syntax for the specified password attribute.
      List<PasswordStorageScheme<?>> defaultStorageSchemes =
        new LinkedList<PasswordStorageScheme<?>>();
      for (DN schemeDN : configuration.getDefaultPasswordStorageSchemeDNs())
      {
        PasswordStorageScheme<?> scheme = DirectoryServer
            .getPasswordStorageScheme(schemeDN);

        if (authPasswordSyntax && (!scheme.supportsAuthPasswordSyntax()))
        {
          Message message = ERR_PWPOLICY_SCHEME_DOESNT_SUPPORT_AUTH.get(
              String.valueOf(schemeDN), passwordAttribute.getNameOrOID());
          throw new ConfigException(message);
        }

        defaultStorageSchemes.add(scheme);
      }

      // Get the names of the deprecated storage schemes.
      Set<String> deprecatedStorageSchemes = new LinkedHashSet<String>();
      for (DN schemeDN : configuration.getDeprecatedPasswordStorageSchemeDNs())
      {
        PasswordStorageScheme<?> scheme = DirectoryServer
            .getPasswordStorageScheme(schemeDN);
        if (authPasswordSyntax)
        {
          if (scheme.supportsAuthPasswordSyntax())
          {
            deprecatedStorageSchemes.add(toLowerCase(scheme
                .getAuthPasswordSchemeName()));
          }
          else
          {
            Message message = ERR_PWPOLICY_DEPRECATED_SCHEME_NOT_AUTH.get(
                String.valueOf(configEntryDN), String.valueOf(schemeDN));
            throw new ConfigException(message);
          }
        }
        else
        {
          deprecatedStorageSchemes.add(toLowerCase(scheme
              .getStorageSchemeName()));
        }
      }

      // Get the password validators.
      Map<DN, PasswordValidator<?>> passwordValidators =
        new HashMap<DN, PasswordValidator<?>>();
      for (DN validatorDN : configuration.getPasswordValidatorDNs())
      {
        passwordValidators.put(validatorDN,
            DirectoryServer.getPasswordValidator(validatorDN));
      }

      // Get the status notification handlers.
      Map<DN, AccountStatusNotificationHandler<?>> notificationHandlers =
        new HashMap<DN, AccountStatusNotificationHandler<?>>();
      for (DN handlerDN : configuration
          .getAccountStatusNotificationHandlerDNs())
      {
        AccountStatusNotificationHandler<?> handler = DirectoryServer
            .getAccountStatusNotificationHandler(handlerDN);
        notificationHandlers.put(handlerDN, handler);
      }

      // Get the password generator.
      PasswordGenerator<?> passwordGenerator = null;
      DN passGenDN = configuration.getPasswordGeneratorDN();
      if (passGenDN != null)
      {
        passwordGenerator = DirectoryServer.getPasswordGenerator(passGenDN);
      }

      // If the expire without warning option is disabled, then there must be a
      // warning interval.
      if ((!configuration.isExpirePasswordsWithoutWarning())
          && (configuration.getPasswordExpirationWarningInterval() <= 0))
      {
        Message message =
          ERR_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING
            .get(String.valueOf(configEntryDN));
        throw new ConfigException(message);
      }

      // Get the required change time.
      String requireChangeBy = configuration.getRequireChangeByTime();
      long requireChangeByTime = 0L;
      try
      {
        if (requireChangeBy != null)
        {
          ByteString valueString = ByteString.valueOf(requireChangeBy);

          GeneralizedTimeSyntax syntax = (GeneralizedTimeSyntax) DirectoryServer
              .getAttributeSyntax(SYNTAX_GENERALIZED_TIME_OID, false);

          if (syntax == null)
          {
            requireChangeByTime = GeneralizedTimeSyntax
                .decodeGeneralizedTimeValue(valueString);
          }
          else
          {
            valueString = syntax.getEqualityMatchingRule().normalizeValue(
                valueString);
            requireChangeByTime = GeneralizedTimeSyntax
                .decodeGeneralizedTimeValue(valueString);
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME
            .get(String.valueOf(configEntryDN), getExceptionMessage(e));
        throw new InitializationException(message, e);
      }

      // Get the last login time format. If specified, it must be a valid format
      // string.
      String formatString = configuration.getLastLoginTimeFormat();
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
      }

      // Get the previous last login time formats. If specified, they must all
      // be valid format strings.
      SortedSet<String> formatStrings = configuration
          .getPreviousLastLoginTimeFormat();
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
              ERR_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT
                .get(String.valueOf(configEntryDN), String.valueOf(s));
            throw new ConfigException(message);
          }
        }
      }

      // If both a maximum password age and a warning interval are provided,
      // then
      // ensure that the warning interval is less than the maximum age. Further,
      // if a minimum age is specified, then the sum of the minimum age and the
      // warning interval should be less than the maximum age.
      if (configuration.getMaxPasswordAge() > 0)
      {
        long warnInterval = Math.max(0L,
            configuration.getPasswordExpirationWarningInterval());
        if (configuration.getMinPasswordAge() > 0)
        {
          if ((warnInterval + configuration.getMinPasswordAge()) >=configuration
              .getMaxPasswordAge())
          {
            Message message =
              ERR_PWPOLICY_MIN_AGE_PLUS_WARNING_GREATER_THAN_MAX_AGE
                .get(String.valueOf(configEntryDN));
            throw new ConfigException(message);
          }
        }
        else if (warnInterval >= configuration.getMaxPasswordAge())
        {
          Message message = ERR_PWPOLICY_WARNING_INTERVAL_LARGER_THAN_MAX_AGE
              .get(String.valueOf(configEntryDN));
          throw new ConfigException(message);
        }
      }

      // If we've got this far then the configuration is good and we can commit
      // the changes if required.
      if (policy != null)
      {
        policy.configuration = configuration;
        policy.authPasswordSyntax = authPasswordSyntax;
        policy.defaultStorageSchemes = defaultStorageSchemes;
        policy.deprecatedStorageSchemes = deprecatedStorageSchemes;
        policy.notificationHandlers = notificationHandlers;
        policy.passwordGenerator = passwordGenerator;
        policy.passwordValidators = passwordValidators;
        policy.requireChangeByTime = requireChangeByTime;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAuthPasswordSyntax()
    {
      return authPasswordSyntax;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public List<PasswordStorageScheme<?>> getDefaultPasswordStorageSchemes()
    {
      return defaultStorageSchemes;
    }



    /**
     * {@inheritDoc}
     */
    public Set<String> getDeprecatedPasswordStorageSchemes()
    {
      return deprecatedStorageSchemes;
    }



    /**
     * {@inheritDoc}
     */
    public DN getDN()
    {
      return configuration.dn();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefaultPasswordStorageScheme(String name)
    {
      for (PasswordStorageScheme<?> s : defaultStorageSchemes)
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
     * {@inheritDoc}
     */
    @Override
    public boolean isDeprecatedPasswordStorageScheme(String name)
    {
      return deprecatedStorageSchemes.contains(toLowerCase(name));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<PasswordValidator<?>> getPasswordValidators()
    {
      return passwordValidators.values();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<AccountStatusNotificationHandler<?>>
      getAccountStatusNotificationHandlers()
    {
      return notificationHandlers.values();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public PasswordGenerator<?> getPasswordGenerator()
    {
      return passwordGenerator;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getRequireChangeByTime()
    {
      return requireChangeByTime;
    }



    /**
     * Retrieves a string representation of this password policy.
     *
     * @return A string representation of this password policy.
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
     * @param buffer
     *          The buffer to which the information should be appended.
     */
    public void toString(StringBuilder buffer)
    {
      buffer.append("Password Attribute:                    ");
      buffer.append(configuration.getPasswordAttribute().getNameOrOID());
      buffer.append(EOL);

      buffer.append("Default Password Storage Schemes:      ");
      if ((defaultStorageSchemes == null) || defaultStorageSchemes.isEmpty())
      {
        buffer.append("{none specified}");
        buffer.append(EOL);
      }
      else
      {
        Iterator<PasswordStorageScheme<?>> iterator = defaultStorageSchemes
            .iterator();
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
      if ((deprecatedStorageSchemes == null)
          || deprecatedStorageSchemes.isEmpty())
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
      buffer.append(configuration.isAllowMultiplePasswordValues());
      buffer.append(EOL);

      buffer.append("Allow Pre-Encoded Passwords:           ");
      buffer.append(configuration.isAllowPreEncodedPasswords());
      buffer.append(EOL);

      buffer.append("Allow User Password Changes:           ");
      buffer.append(configuration.isAllowUserPasswordChanges());
      buffer.append(EOL);

      buffer.append("Force Password Change on Add:          ");
      buffer.append(configuration.isForceChangeOnAdd());
      buffer.append(EOL);

      buffer.append("Force Password Change on Admin Reset:  ");
      buffer.append(configuration.isForceChangeOnReset());
      buffer.append(EOL);

      buffer.append("Require Current Password:              ");
      buffer.append(configuration.isPasswordChangeRequiresCurrentPassword());
      buffer.append(EOL);

      buffer.append("Require Secure Authentication:         ");
      buffer.append(configuration.isRequireSecureAuthentication());
      buffer.append(EOL);

      buffer.append("Require Secure Password Changes:       ");
      buffer.append(configuration.isRequireSecurePasswordChanges());
      buffer.append(EOL);

      buffer.append("Lockout Failure Expiration Interval:   ");
      buffer.append(configuration.getLockoutFailureExpirationInterval());
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
      buffer.append(configuration.isSkipValidationForAdministrators());
      buffer.append(EOL);

      buffer.append("Password Generator:                    ");
      if (passwordGenerator == null)
      {
        buffer.append("{none specified}");
      }
      else
      {
        configuration.getPasswordGeneratorDN().toString(buffer);
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
      buffer.append(configuration.getMinPasswordAge());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Maximum Password Age:                  ");
      buffer.append(configuration.getMaxPasswordAge());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Maximum Password Reset Age:            ");
      buffer.append(configuration.getMaxPasswordResetAge());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Expiration Warning Interval:           ");
      buffer.append(configuration.getPasswordExpirationWarningInterval());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Expire Passwords Without Warning:      ");
      buffer.append(configuration.isExpirePasswordsWithoutWarning());
      buffer.append(EOL);

      buffer.append("Allow Expired Password Changes:        ");
      buffer.append(configuration.isAllowExpiredPasswordChanges());
      buffer.append(EOL);

      buffer.append("Grace Login Count:                     ");
      buffer.append(configuration.getGraceLoginCount());
      buffer.append(EOL);

      buffer.append("Lockout Failure Count:                 ");
      buffer.append(configuration.getLockoutFailureCount());
      buffer.append(EOL);

      buffer.append("Lockout Duration:                      ");
      buffer.append(configuration.getLockoutDuration());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Lockout Count Expiration Interval:     ");
      buffer.append(configuration.getLockoutFailureExpirationInterval());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("Required Password Change By Time:      ");
      if (requireChangeByTime <= 0)
      {
        buffer.append("{none specified}");
      }
      else
      {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
            DATE_FORMAT_GENERALIZED_TIME);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        buffer.append(dateFormat.format(new Date(requireChangeByTime)));
      }
      buffer.append(EOL);

      buffer.append("Last Login Time Attribute:             ");
      if (configuration.getLastLoginTimeAttribute() == null)
      {
        buffer.append("{none specified}");
      }
      else
      {
        buffer.append(configuration.getLastLoginTimeAttribute().getNameOrOID());
      }
      buffer.append(EOL);

      buffer.append("Last Login Time Format:                ");
      if (configuration.getLastLoginTimeFormat() == null)
      {
        buffer.append("{none specified}");
      }
      else
      {
        buffer.append(configuration.getLastLoginTimeFormat());
      }
      buffer.append(EOL);

      buffer.append("Previous Last Login Time Formats:      ");
      if ((configuration.getPreviousLastLoginTimeFormat().isEmpty()))
      {
        buffer.append("{none specified}");
        buffer.append(EOL);
      }
      else
      {
        Iterator<String> iterator = configuration
            .getPreviousLastLoginTimeFormat().iterator();

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
      buffer.append(configuration.getIdleLockoutInterval());
      buffer.append(" seconds");
      buffer.append(EOL);

      buffer.append("History Count:                         ");
      buffer.append(configuration.getPasswordHistoryCount());
      buffer.append(EOL);

      buffer.append("Update Failure Policy:                 ");
      buffer.append(configuration.getStateUpdateFailurePolicy().toString());
      buffer.append(EOL);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowExpiredPasswordChanges()
    {
      return configuration.isAllowExpiredPasswordChanges();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowMultiplePasswordValues()
    {
      return configuration.isAllowMultiplePasswordValues();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowPreEncodedPasswords()
    {
      return configuration.isAllowPreEncodedPasswords();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowUserPasswordChanges()
    {
      return configuration.isAllowUserPasswordChanges();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExpirePasswordsWithoutWarning()
    {
      return configuration.isExpirePasswordsWithoutWarning();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isForceChangeOnAdd()
    {
      return configuration.isForceChangeOnAdd();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isForceChangeOnReset()
    {
      return configuration.isForceChangeOnReset();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int getGraceLoginCount()
    {
      return configuration.getGraceLoginCount();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getIdleLockoutInterval()
    {
      return configuration.getIdleLockoutInterval();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeType getLastLoginTimeAttribute()
    {
      return configuration.getLastLoginTimeAttribute();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getLastLoginTimeFormat()
    {
      return configuration.getLastLoginTimeFormat();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getLockoutDuration()
    {
      return configuration.getLockoutDuration();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int getLockoutFailureCount()
    {
      return configuration.getLockoutFailureCount();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getLockoutFailureExpirationInterval()
    {
      return configuration.getLockoutFailureExpirationInterval();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxPasswordAge()
    {
      return configuration.getMaxPasswordAge();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxPasswordResetAge()
    {
      return configuration.getMaxPasswordResetAge();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getMinPasswordAge()
    {
      return configuration.getMinPasswordAge();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeType getPasswordAttribute()
    {
      return configuration.getPasswordAttribute();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPasswordChangeRequiresCurrentPassword()
    {
      return configuration.isPasswordChangeRequiresCurrentPassword();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getPasswordExpirationWarningInterval()
    {
      return configuration.getPasswordExpirationWarningInterval();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int getPasswordHistoryCount()
    {
      return configuration.getPasswordHistoryCount();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public long getPasswordHistoryDuration()
    {
      return configuration.getPasswordHistoryDuration();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public SortedSet<String> getPreviousLastLoginTimeFormats()
    {
      return configuration.getPreviousLastLoginTimeFormat();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequireSecureAuthentication()
    {
      return configuration.isRequireSecureAuthentication();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequireSecurePasswordChanges()
    {
      return configuration.isRequireSecurePasswordChanges();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSkipValidationForAdministrators()
    {
      return configuration.isSkipValidationForAdministrators();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StateUpdateFailurePolicy getStateUpdateFailurePolicy()
    {
      return configuration.getStateUpdateFailurePolicy();
    }

  }



  /**
   * Default constructor instantiated from authentication policy config manager.
   */
  public PasswordPolicyFactory()
  {
    // Nothing to do .
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PasswordPolicy createAuthenticationPolicy(
      final PasswordPolicyCfg configuration) throws ConfigException,
      InitializationException
  {
    PasswordPolicyImpl policy = new PasswordPolicyImpl(configuration);
    configuration.addPasswordPolicyChangeListener(policy);
    return policy;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(
      final PasswordPolicyCfg configuration,
      final List<Message> unacceptableReasons)
  {
    try
    {
      new PasswordPolicyImpl(configuration);
    }
    catch (final ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }

      unacceptableReasons.add(ce.getMessageObject());
      return false;
    }
    catch (final InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      unacceptableReasons.add(ie.getMessageObject());
      return false;
    }

    // If we made it here, then the configuration is acceptable.
    return true;
  }

}
