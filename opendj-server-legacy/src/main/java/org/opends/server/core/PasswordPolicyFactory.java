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
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.PasswordPolicyCfgDefn.StateUpdateFailurePolicy;
import org.forgerock.opendj.server.config.server.PasswordPolicyCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.AuthenticationPolicyFactory;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.InitializationException;
import org.opends.server.util.SchemaUtils;
import org.opends.server.util.SchemaUtils.PasswordType;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Password policy implementation. */
  private static final class PasswordPolicyImpl extends PasswordPolicy
      implements ConfigurationChangeListener<PasswordPolicyCfg>
  {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** Current configuration. */
    private PasswordPolicyCfg configuration;

    /** Indicates whether the attribute type uses the authPassword syntax. */
    private boolean authPasswordSyntax;

    /** The set of account status notification handlers for this password policy. */
    private Map<DN, AccountStatusNotificationHandler<?>> notificationHandlers;
    /** The set of password validators that will be used with this password policy. */
    private Map<DN, PasswordValidator<?>> passwordValidators;

    /** The set of default password storage schemes for this password policy. */
    private List<PasswordStorageScheme<?>> defaultStorageSchemes;
    /** The names of the deprecated password storage schemes for this password policy. */
    private Set<String> deprecatedStorageSchemes;

    /** The password generator for use with this password policy. */
    private PasswordGenerator<?> passwordGenerator;

    /** The the time by which all users will be required to change their passwords. */
    private long requireChangeByTime;

    private final ServerContext serverContext;

    @Override
    public void finalizeAuthenticationPolicy()
    {
      configuration.removePasswordPolicyChangeListener(this);
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(PasswordPolicyCfg configuration)
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      try
      {
        updateConfiguration(configuration, true);
      }
      catch (ConfigException ce)
      {
        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(configuration.dn(), ce.getMessage()));
      }
      catch (InitializationException ie)
      {
        ccr.addMessage(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            configuration.dn(), ie.getMessage()));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
      catch (Exception e)
      {
        ccr.addMessage(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            configuration.dn(), stackTraceToSingleLineString(e)));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
      return ccr;
    }

    @Override
    public boolean isConfigurationChangeAcceptable(
        PasswordPolicyCfg configuration, List<LocalizableMessage> unacceptableReasons)
    {
      try
      {
        updateConfiguration(configuration, false);
      }
      catch (ConfigException | InitializationException e)
      {
        LocalizableMessage message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            configuration.dn(), e.getMessage());
        unacceptableReasons.add(message);
        return false;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG
            .get(configuration.dn(), stackTraceToSingleLineString(e));
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
     * @param serverContext TODO
     * @param configuration
     *          The configuration with the information to use to initialize this
     *          password policy.
     *
     * @throws ConfigException
     *           If the provided entry does not contain a valid password policy
     *           configuration.
     * @throws InitializationException
     *           If an error occurs while initializing the password policy that
     *           is not related to the server configuration.
     */
    private PasswordPolicyImpl(ServerContext serverContext, PasswordPolicyCfg configuration)
        throws ConfigException, InitializationException
    {
      this.serverContext = serverContext;
      updateConfiguration(configuration, true);
    }

    private void updateConfiguration(PasswordPolicyCfg configuration,
        boolean applyChanges) throws ConfigException,
        InitializationException
    {
      final DN configEntryDN = configuration.dn();

      // Get the password attribute. If specified, it must have either the
      // user password or auth password syntax.
      final AttributeType passwordAttribute = configuration.getPasswordAttribute();
      final PasswordType passwordType = SchemaUtils.checkPasswordType(passwordAttribute);
      if (PasswordType.AUTH_PASSWORD.equals(passwordType))
      {
        authPasswordSyntax = true;
      }
      else if (PasswordType.USER_PASSWORD.equals(passwordType))
      {
        authPasswordSyntax = false;
      }
      else
      {
        String syntax = passwordAttribute.getSyntax().getName();
        if (syntax == null || syntax.length() == 0)
        {
          syntax = passwordAttribute.getSyntax().getOID();
        }

        throw new ConfigException(ERR_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX.get(
            configEntryDN, passwordAttribute.getNameOrOID(), syntax));
      }

      // Get the default storage schemes. They must all reference valid storage
      // schemes that support the syntax for the specified password attribute.
      List<PasswordStorageScheme<?>> defaultStorageSchemes = new LinkedList<>();
      for (DN schemeDN : configuration.getDefaultPasswordStorageSchemeDNs())
      {
        PasswordStorageScheme<?> scheme = DirectoryServer
            .getPasswordStorageScheme(schemeDN);

        if (authPasswordSyntax && !scheme.supportsAuthPasswordSyntax())
        {
          throw new ConfigException(ERR_PWPOLICY_SCHEME_DOESNT_SUPPORT_AUTH.get(
              schemeDN, passwordAttribute.getNameOrOID()));
        }

        defaultStorageSchemes.add(scheme);
      }

      // Get the names of the deprecated storage schemes.
      Set<String> deprecatedStorageSchemes = new LinkedHashSet<>();
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
            throw new ConfigException(ERR_PWPOLICY_DEPRECATED_SCHEME_NOT_AUTH.get(
                configEntryDN, schemeDN));
          }
        }
        else
        {
          deprecatedStorageSchemes.add(toLowerCase(scheme.getStorageSchemeName()));
        }
      }

      // Get the password validators.
      Map<DN, PasswordValidator<?>> passwordValidators = new HashMap<>();
      for (DN validatorDN : configuration.getPasswordValidatorDNs())
      {
        passwordValidators.put(validatorDN,
            DirectoryServer.getPasswordValidator(validatorDN));
      }

      // Get the status notification handlers.
      Map<DN, AccountStatusNotificationHandler<?>> notificationHandlers = new HashMap<>();
      for (DN handlerDN : configuration.getAccountStatusNotificationHandlerDNs())
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
      if (!configuration.isExpirePasswordsWithoutWarning()
          && configuration.getPasswordExpirationWarningInterval() <= 0)
      {
        LocalizableMessage message =
          ERR_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING.get(configEntryDN);
        throw new ConfigException(message);
      }

      // Get the required change time.
      String requireChangeBy = configuration.getRequireChangeByTime();
      long requireChangeByTime = 0L;
      try
      {
        if (requireChangeBy != null)
        {
          ByteString valueString = ByteString.valueOfUtf8(requireChangeBy);
          requireChangeByTime = GeneralizedTime.valueOf(valueString.toString()).getTimeInMillis();
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME
            .get(configEntryDN, getExceptionMessage(e));
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
          logger.traceException(e);
          throw new ConfigException(ERR_PWPOLICY_INVALID_LAST_LOGIN_TIME_FORMAT.get(configEntryDN, formatString));
        }
      }

      // Get the previous last login time formats. If specified, they must all
      // be valid format strings.
      SortedSet<String> formatStrings = configuration.getPreviousLastLoginTimeFormat();
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
            logger.traceException(e);
            throw new ConfigException(ERR_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT.get(configEntryDN, s));
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
          if (warnInterval + configuration.getMinPasswordAge() >= configuration.getMaxPasswordAge())
          {
            LocalizableMessage message =
              ERR_PWPOLICY_MIN_AGE_PLUS_WARNING_GREATER_THAN_MAX_AGE.get(configEntryDN);
            throw new ConfigException(message);
          }
        }
        else if (warnInterval >= configuration.getMaxPasswordAge())
        {
          LocalizableMessage message = ERR_PWPOLICY_WARNING_INTERVAL_LARGER_THAN_MAX_AGE.get(configEntryDN);
          throw new ConfigException(message);
        }
      }

      // If we've got this far then the configuration is good and we can commit
      // the changes if required.
      if (applyChanges)
      {
        this.configuration = configuration;
        this.defaultStorageSchemes = defaultStorageSchemes;
        this.deprecatedStorageSchemes = deprecatedStorageSchemes;
        this.notificationHandlers = notificationHandlers;
        this.passwordGenerator = passwordGenerator;
        this.passwordValidators = passwordValidators;
        this.requireChangeByTime = requireChangeByTime;
      }
    }

    @Override
    public boolean isAuthPasswordSyntax()
    {
      return authPasswordSyntax;
    }

    @Override
    public List<PasswordStorageScheme<?>> getDefaultPasswordStorageSchemes()
    {
      return defaultStorageSchemes;
    }

    @Override
    public Set<String> getDeprecatedPasswordStorageSchemes()
    {
      return deprecatedStorageSchemes;
    }

    @Override
    public DN getDN()
    {
      return configuration.dn();
    }

    @Override
    public boolean isDefaultPasswordStorageScheme(String name)
    {
      for (PasswordStorageScheme<?> s : defaultStorageSchemes)
      {
        String schemeName = authPasswordSyntax
            ? s.getAuthPasswordSchemeName()
            : s.getStorageSchemeName();
        if (schemeName.equalsIgnoreCase(name))
        {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean isDeprecatedPasswordStorageScheme(String name)
    {
      return deprecatedStorageSchemes.contains(toLowerCase(name));
    }

    @Override
    public Collection<PasswordValidator<?>> getPasswordValidators()
    {
      return passwordValidators.values();
    }

    @Override
    public Collection<AccountStatusNotificationHandler<?>>
      getAccountStatusNotificationHandlers()
    {
      return notificationHandlers.values();
    }

    @Override
    public PasswordGenerator<?> getPasswordGenerator()
    {
      return passwordGenerator;
    }

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
    @Override
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
      if (defaultStorageSchemes == null || defaultStorageSchemes.isEmpty())
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
      if (deprecatedStorageSchemes == null || deprecatedStorageSchemes.isEmpty())
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
      if (passwordValidators == null || passwordValidators.isEmpty())
      {
        buffer.append("{none specified}");
        buffer.append(EOL);
      }
      else
      {
        Iterator<DN> iterator = passwordValidators.keySet().iterator();
        buffer.append(iterator.next());
        buffer.append(EOL);

        while (iterator.hasNext())
        {
          buffer.append("                                       ");
          buffer.append(iterator.next());
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
        buffer.append(configuration.getPasswordGeneratorDN());
      }
      buffer.append(EOL);

      buffer.append("Account Status Notification Handlers:  ");
      if (notificationHandlers == null || notificationHandlers.isEmpty())
      {
        buffer.append("{none specified}");
        buffer.append(EOL);
      }
      else
      {
        Iterator<DN> iterator = notificationHandlers.keySet().iterator();
        buffer.append(iterator.next());
        buffer.append(EOL);

        while (iterator.hasNext())
        {
          buffer.append("                                       ");
          buffer.append(iterator.next());
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
      if (configuration.getLastLoginTimeAttribute() != null)
      {
        buffer.append(configuration.getLastLoginTimeAttribute().getNameOrOID());
      }
      else
      {
        buffer.append("{none specified}");
      }
      buffer.append(EOL);

      buffer.append("Last Login Time Format:                ");
      if (configuration.getLastLoginTimeFormat() != null)
      {
        buffer.append(configuration.getLastLoginTimeFormat());
      }
      else
      {
        buffer.append("{none specified}");
      }
      buffer.append(EOL);

      buffer.append("Previous Last Login Time Formats:      ");
      if (configuration.getPreviousLastLoginTimeFormat().isEmpty())
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
      buffer.append(configuration.getStateUpdateFailurePolicy());
      buffer.append(EOL);
    }

    @Override
    public boolean isAllowExpiredPasswordChanges()
    {
      return configuration.isAllowExpiredPasswordChanges();
    }

    @Override
    public boolean isAllowMultiplePasswordValues()
    {
      return configuration.isAllowMultiplePasswordValues();
    }

    @Override
    public boolean isAllowPreEncodedPasswords()
    {
      return configuration.isAllowPreEncodedPasswords();
    }

    @Override
    public boolean isAllowUserPasswordChanges()
    {
      return configuration.isAllowUserPasswordChanges();
    }

    @Override
    public boolean isExpirePasswordsWithoutWarning()
    {
      return configuration.isExpirePasswordsWithoutWarning();
    }

    @Override
    public boolean isForceChangeOnAdd()
    {
      return configuration.isForceChangeOnAdd();
    }

    @Override
    public boolean isForceChangeOnReset()
    {
      return configuration.isForceChangeOnReset();
    }

    @Override
    public int getGraceLoginCount()
    {
      return configuration.getGraceLoginCount();
    }

    @Override
    public long getIdleLockoutInterval()
    {
      return configuration.getIdleLockoutInterval();
    }

    @Override
    public AttributeType getLastLoginTimeAttribute()
    {
      return configuration.getLastLoginTimeAttribute();
    }

    @Override
    public String getLastLoginTimeFormat()
    {
      return configuration.getLastLoginTimeFormat();
    }

    @Override
    public long getLockoutDuration()
    {
      return configuration.getLockoutDuration();
    }

    @Override
    public int getLockoutFailureCount()
    {
      return configuration.getLockoutFailureCount();
    }

    @Override
    public long getLockoutFailureExpirationInterval()
    {
      return configuration.getLockoutFailureExpirationInterval();
    }

    @Override
    public long getMaxPasswordAge()
    {
      return configuration.getMaxPasswordAge();
    }

    @Override
    public long getMaxPasswordResetAge()
    {
      return configuration.getMaxPasswordResetAge();
    }

    @Override
    public long getMinPasswordAge()
    {
      return configuration.getMinPasswordAge();
    }

    @Override
    public AttributeType getPasswordAttribute()
    {
      return configuration.getPasswordAttribute();
    }

    @Override
    public boolean isPasswordChangeRequiresCurrentPassword()
    {
      return configuration.isPasswordChangeRequiresCurrentPassword();
    }

    @Override
    public long getPasswordExpirationWarningInterval()
    {
      return configuration.getPasswordExpirationWarningInterval();
    }

    @Override
    public int getPasswordHistoryCount()
    {
      return configuration.getPasswordHistoryCount();
    }

    @Override
    public long getPasswordHistoryDuration()
    {
      return configuration.getPasswordHistoryDuration();
    }

    @Override
    public SortedSet<String> getPreviousLastLoginTimeFormats()
    {
      return configuration.getPreviousLastLoginTimeFormat();
    }

    @Override
    public boolean isRequireSecureAuthentication()
    {
      return configuration.isRequireSecureAuthentication();
    }

    @Override
    public boolean isRequireSecurePasswordChanges()
    {
      return configuration.isRequireSecurePasswordChanges();
    }

    @Override
    public boolean isSkipValidationForAdministrators()
    {
      return configuration.isSkipValidationForAdministrators();
    }

    @Override
    public StateUpdateFailurePolicy getStateUpdateFailurePolicy()
    {
      return configuration.getStateUpdateFailurePolicy();
    }
  }

  private ServerContext serverContext;

  /** Default constructor instantiated from authentication policy config manager. */
  public PasswordPolicyFactory()
  {
    // Nothing to do .
  }

  /**
   * Sets the server context.
   *
   * @param serverContext
   *            The server context.
   */
  @Override
  public void setServerContext(final ServerContext serverContext) {
    this.serverContext = serverContext;
  }

  @Override
  public PasswordPolicy createAuthenticationPolicy(
      final PasswordPolicyCfg configuration) throws ConfigException,
      InitializationException
  {
    PasswordPolicyImpl policy = new PasswordPolicyImpl(serverContext, configuration);
    configuration.addPasswordPolicyChangeListener(policy);
    return policy;
  }

  @Override
  public boolean isConfigurationAcceptable(
      final PasswordPolicyCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      new PasswordPolicyImpl(null, configuration);
    }
    catch (final ConfigException | InitializationException ie)
    {
      logger.traceException(ie);

      unacceptableReasons.add(ie.getMessageObject());
      return false;
    }

    // If we made it here, then the configuration is acceptable.
    return true;
  }
}
