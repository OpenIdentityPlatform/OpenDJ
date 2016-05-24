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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.AuthenticationPolicyCfgDefn;
import org.forgerock.opendj.server.config.server.AuthenticationPolicyCfg;
import org.forgerock.opendj.server.config.server.PasswordPolicyCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.AuthenticationPolicyFactory;
import org.opends.server.api.SubentryChangeListener;
import org.opends.server.types.*;

/**
 * This class defines a utility that will be used to manage the set of password
 * policies defined in the Directory Server. It will initialize the policies
 * when the server starts, and then will manage any additions or removals while
 * the server is running.
 */
final class PasswordPolicyConfigManager implements SubentryChangeListener,
    ConfigurationAddListener<AuthenticationPolicyCfg>,
    ConfigurationDeleteListener<AuthenticationPolicyCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this password policy config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public PasswordPolicyConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes all authentication policies currently defined in the Directory
   * Server configuration. This should only be called at Directory Server
   * startup.
   *
   * @throws ConfigException
   *           If a configuration problem causes the authentication policy
   *           initialization process to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the authentication
   *           policies that is not related to the server configuration.
   */
  public void initializeAuthenticationPolicies() throws ConfigException,
      InitializationException
  {
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addPasswordPolicyAddListener(this);
    rootConfiguration.addPasswordPolicyDeleteListener(this);

    // First, get the configuration base entry.
    String[] passwordPolicyNames = rootConfiguration.listPasswordPolicies();

    // See if the base entry has any children. If not, then that means that
    // there are no policies defined, so that's a problem.
    if (passwordPolicyNames.length == 0)
    {
      LocalizableMessage message = ERR_CONFIG_PWPOLICY_NO_POLICIES.get();
      throw new ConfigException(message);
    }

    // Get the DN of the default password policy from the core configuration.
    if (DirectoryServer.getDefaultPasswordPolicyDN() == null)
    {
      LocalizableMessage message = ERR_CONFIG_PWPOLICY_NO_DEFAULT_POLICY.get();
      throw new ConfigException(message);
    }

    // Iterate through the child entries and process them as password policy
    // configuration entries.
    for (String passwordPolicyName : passwordPolicyNames)
    {
      AuthenticationPolicyCfg passwordPolicyConfiguration = rootConfiguration
          .getPasswordPolicy(passwordPolicyName);
      createAuthenticationPolicy(passwordPolicyConfiguration);
    }

    // If the entry specified by the default password policy DN has not been
    // registered, then fail.
    if (null == DirectoryServer.getDefaultPasswordPolicy())
    {
      DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
      throw new ConfigException(ERR_CONFIG_PWPOLICY_MISSING_DEFAULT_POLICY.get(defaultPolicyDN));
    }

    // Process and register any password policy subentries.
    List<SubEntry> pwpSubEntries = DirectoryServer.getSubentryManager().getSubentries();
    if (pwpSubEntries != null && !pwpSubEntries.isEmpty())
    {
      for (SubEntry subentry : pwpSubEntries)
      {
        if (subentry.getEntry().isPasswordPolicySubentry())
        {
          try
          {
            PasswordPolicy policy = new SubentryPasswordPolicy(subentry);
            DirectoryServer.registerAuthenticationPolicy(subentry.getDN(),
                policy);
          }
          catch (Exception e)
          {
            // Just log a message instead of failing the server initialization.
            // This will allow the administrator to fix any problems.
            logger.error(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG, subentry.getDN(), stackTraceToSingleLineString(e));
          }
        }
      }
    }

    // Register this as subentry change listener with SubentryManager.
    DirectoryServer.getSubentryManager().registerChangeListener(this);
  }

  /**
   * Perform any required finalization tasks for all authentication policies
   * currently defined. This should only be called at Directory Server shutdown.
   */
  public void finalizeAuthenticationPolicies()
  {
    DirectoryServer.getSubentryManager().deregisterChangeListener(this);

    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.removePasswordPolicyAddListener(this);
    rootConfiguration.removePasswordPolicyDeleteListener(this);
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      AuthenticationPolicyCfg configuration, List<LocalizableMessage> unacceptableReason)
  {
    // See if we can create a password policy from the provided configuration
    // entry. If so, then it's acceptable.
    return isAuthenticationPolicyConfigurationAcceptable(configuration,
        unacceptableReason);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(AuthenticationPolicyCfg configuration)
  {
    // See if we can create a password policy from the provided configuration
    // entry. If so, then register it with the Directory Server.
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      createAuthenticationPolicy(configuration);
    }
    catch (ConfigException ce)
    {
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      ccr.addMessage(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(configuration.dn(), ce.getMessage()));
    }
    catch (InitializationException ie)
    {
      ccr.addMessage(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(configuration.dn(), ie.getMessage()));
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
  public boolean isConfigurationDeleteAcceptable(
      AuthenticationPolicyCfg configuration, List<LocalizableMessage> unacceptableReason)
  {
    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    // a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if (defaultPolicyDN != null && defaultPolicyDN.equals(configuration.dn()))
    {
      unacceptableReason.add(WARN_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY.get(defaultPolicyDN));
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(AuthenticationPolicyCfg configuration)
  {
    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    // a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    final ConfigChangeResult ccr = new ConfigChangeResult();
    DN policyDN = configuration.dn();
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if (defaultPolicyDN != null && defaultPolicyDN.equals(policyDN))
    {
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      ccr.addMessage(WARN_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY.get(defaultPolicyDN));
      return ccr;
    }
    DirectoryServer.deregisterAuthenticationPolicy(policyDN);
    ccr.addMessage(INFO_CONFIG_PWPOLICY_REMOVED_POLICY.get(policyDN));
    return ccr;
  }

  @Override
  public void checkSubentryAddAcceptable(Entry entry) throws DirectoryException
  {
    if (entry.isPasswordPolicySubentry())
    {
      new SubentryPasswordPolicy(new SubEntry(entry));
    }
  }

  @Override
  public void checkSubentryDeleteAcceptable(Entry entry)
      throws DirectoryException
  {
    // FIXME: something like a referential integrity check is needed to
    // ensure a policy is not removed when referenced by a user entry (
    // either directly or via a virtual attribute).
  }

  @Override
  public void checkSubentryModifyAcceptable(Entry oldEntry, Entry newEntry)
      throws DirectoryException
  {
    if (newEntry.isPasswordPolicySubentry())
    {
      new SubentryPasswordPolicy(new SubEntry(newEntry));
    }
  }

  @Override
  public void checkSubentryModifyDNAcceptable(Entry oldEntry, Entry newEntry)
      throws DirectoryException
  {
    // FIXME: something like a referential integrity check is needed to
    // ensure a policy is not removed when referenced by a user entry (
    // either directly or via a virtual attribute).
  }

  @Override
  public void handleSubentryAdd(Entry entry)
  {
    if (entry.isPasswordPolicySubentry())
    {
      try
      {
        PasswordPolicy policy = new SubentryPasswordPolicy(new SubEntry(entry));
        DirectoryServer.registerAuthenticationPolicy(entry.getName(), policy);
      }
      catch (Exception e)
      {
        logger.traceException(e, "Could not create password policy subentry DN %s",
            entry.getName());
      }
    }
  }

  @Override
  public void handleSubentryDelete(Entry entry)
  {
    if (entry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterAuthenticationPolicy(entry.getName());
    }
  }

  @Override
  public void handleSubentryModify(Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterAuthenticationPolicy(oldEntry.getName());
    }

    if (newEntry.isPasswordPolicySubentry())
    {
      try
      {
        PasswordPolicy policy = new SubentryPasswordPolicy(new SubEntry(
            newEntry));
        DirectoryServer.registerAuthenticationPolicy(newEntry.getName(),
            policy);
      }
      catch (Exception e)
      {
        logger.traceException(e, "Could not create password policy subentry DN %s",
            newEntry.getName());
      }
    }
  }

  @Override
  public void handleSubentryModifyDN(Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterAuthenticationPolicy(oldEntry.getName());
    }

    if (newEntry.isPasswordPolicySubentry())
    {
      try
      {
        PasswordPolicy policy = new SubentryPasswordPolicy(new SubEntry(
            newEntry));
        DirectoryServer.registerAuthenticationPolicy(newEntry.getName(),
            policy);
      }
      catch (Exception e)
      {
        logger.traceException(e, "Could not create password policy subentry DN %s",
            newEntry.getName());
      }
    }
  }

  /** Creates and registers the provided authentication policy configuration. */
  private <T extends AuthenticationPolicyCfg> void createAuthenticationPolicy(
      T policyConfiguration) throws ConfigException, InitializationException
  {
    // If this is going to be the default password policy then check the type is
    // correct.
    if (policyConfiguration.dn().equals(DirectoryServer.getDefaultPasswordPolicyDN())
        && !(policyConfiguration instanceof PasswordPolicyCfg))
    {
      throw new ConfigException(ERR_CONFIG_PWPOLICY_DEFAULT_POLICY_IS_WRONG_TYPE.get(policyConfiguration.dn()));
    }

    String className = policyConfiguration.getJavaClass();
    AuthenticationPolicyCfgDefn d = AuthenticationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    try
    {
      Class<AuthenticationPolicyFactory<T>> theClass =
          (Class<AuthenticationPolicyFactory<T>>) pd.loadClass(className,
              AuthenticationPolicyFactory.class);
      AuthenticationPolicyFactory<T> factory = theClass.newInstance();
      factory.setServerContext(serverContext);

      AuthenticationPolicy policy = factory.createAuthenticationPolicy(policyConfiguration);

      DirectoryServer.registerAuthenticationPolicy(policyConfiguration.dn(), policy);
    }
    catch (Exception e)
    {
      if (e instanceof InvocationTargetException)
      {
        Throwable t = e.getCause();

        if (t instanceof InitializationException)
        {
          throw (InitializationException) t;
        }
        else if (t instanceof ConfigException)
        {
          throw (ConfigException) t;
        }
      }

      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
          policyConfiguration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /**
   * Determines whether the new authentication policy configuration's
   * implementation class is acceptable.
   */
  private <T extends AuthenticationPolicyCfg> boolean isAuthenticationPolicyConfigurationAcceptable(
      T policyConfiguration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // If this is going to be the default password policy then check the type is
    // correct.
    if (policyConfiguration.dn().equals(DirectoryServer.getDefaultPasswordPolicyDN())
        && !(policyConfiguration instanceof PasswordPolicyCfg))
    {
      unacceptableReasons.add(ERR_CONFIG_PWPOLICY_DEFAULT_POLICY_IS_WRONG_TYPE.get(policyConfiguration.dn()));
      return false;
    }

    String className = policyConfiguration.getJavaClass();
    AuthenticationPolicyCfgDefn d = AuthenticationPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    // Validate the configuration.
    try
    {
      Class<?> theClass =
          pd.loadClass(className, AuthenticationPolicyFactory.class);
      AuthenticationPolicyFactory<T> factory =
          (AuthenticationPolicyFactory<T>) theClass.newInstance();
      factory.setServerContext(serverContext);

      return factory.isConfigurationAcceptable(policyConfiguration, unacceptableReasons);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
          policyConfiguration.dn(), stackTraceToSingleLineString(e)));
      return false;
    }
  }
}
