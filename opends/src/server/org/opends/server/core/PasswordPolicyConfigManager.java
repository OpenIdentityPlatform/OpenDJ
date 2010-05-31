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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.SubentryChangeListener;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SubEntry;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;



/**
 * This class defines a utility that will be used to manage the set of password
 * policies defined in the Directory Server.  It will initialize the policies
 * when the server starts, and then will manage any additions or removals while
 * the server is running.
 */
public class PasswordPolicyConfigManager
       implements SubentryChangeListener,
       ConfigurationAddListener<PasswordPolicyCfg>,
       ConfigurationDeleteListener<PasswordPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new instance of this password policy config manager.
   */
  public PasswordPolicyConfigManager()
  {
  }



  /**
   * Creates a password policy configuration object
   * from password policy subentry.
   * @param  subEntry password policy subentry.
   * @return password policy configuration.
   * @throws InitializationException if an error
   *         occurs while parsing subentry into
   *         password policy configuration.
   */
  private PasswordPolicyConfig createPasswordPolicyConfig(
          SubEntry subEntry) throws InitializationException
  {
    try
    {
      SubentryPasswordPolicy subentryPolicy =
              new SubentryPasswordPolicy(subEntry);
      PasswordPolicy passwordPolicy =
              new PasswordPolicy(subentryPolicy);
      PasswordPolicyConfig config =
              new PasswordPolicyConfig(passwordPolicy);
      return config;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.
            get(String.valueOf(subEntry.getDN()),
                stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * Initializes all password policies currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the password
   *                           policy initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password policies that is not
   *                                   related to the server configuration.
   */
  public void initializePasswordPolicies()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so we
    // can be notified if any password policy entries are added or removed.
    rootConfiguration.addPasswordPolicyAddListener(this);
    rootConfiguration.addPasswordPolicyDeleteListener(this);

    // First, get the configuration base entry.
    String[] passwordPoliciesName = rootConfiguration.listPasswordPolicies() ;

    // See if the base entry has any children.  If not, then that means that
    // there are no policies defined, so that's a problem.
    if (passwordPoliciesName.length == 0)
    {
      Message message = ERR_CONFIG_PWPOLICY_NO_POLICIES.get();
      throw new ConfigException(message);
    }


    // Get the DN of the default password policy from the core configuration.
    if( null == DirectoryServer.getDefaultPasswordPolicyDN())
    {
      Message message = ERR_CONFIG_PWPOLICY_NO_DEFAULT_POLICY.get();
      throw new ConfigException(message);
    }


    // Iterate through the child entries and process them as password policy
    // configuration entries.
    for (String passwordPolicyName : passwordPoliciesName)
    {
      PasswordPolicyCfg passwordPolicyConfiguration =
        rootConfiguration.getPasswordPolicy(passwordPolicyName);

      try
      {
        PasswordPolicy policy = new PasswordPolicy(passwordPolicyConfiguration);
        PasswordPolicyConfig config = new PasswordPolicyConfig(policy);
        DirectoryServer.registerPasswordPolicy(
            passwordPolicyConfiguration.dn(), config);
        passwordPolicyConfiguration.addChangeListener(config);
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(passwordPolicyConfiguration.dn()), ce.getMessage());
        throw new ConfigException(message, ce);
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
            String.valueOf(passwordPolicyConfiguration.dn()), ie.getMessage());
        throw new InitializationException(message, ie);
      }
      catch (Exception e)
      {
        Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.
            get(String.valueOf(passwordPolicyConfiguration.dn()),
                stackTraceToSingleLineString(e));
        throw new InitializationException(message, e);
      }
    }


    // If the entry specified by the default password policy DN has not been
    // registered, then fail.
    if (null == DirectoryServer.getDefaultPasswordPolicy())
    {
      DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
      Message message = ERR_CONFIG_PWPOLICY_MISSING_DEFAULT_POLICY.get(
              String.valueOf(defaultPolicyDN));
      throw new ConfigException(message);
    }

    // Process and register any password policy subentries.
    List<SubEntry> pwpSubEntries =
            DirectoryServer.getSubentryManager().getSubentries();
    if ((pwpSubEntries != null) && (!pwpSubEntries.isEmpty()))
    {
      for (SubEntry subentry : pwpSubEntries)
      {
        if (subentry.getEntry().isPasswordPolicySubentry())
        {
          PasswordPolicyConfig config =
                  createPasswordPolicyConfig(subentry);
          DirectoryServer.registerPasswordPolicy(
              subentry.getDN(), config);
        }
      }
    }

    // Register this as subentry change listener with SubentryManager.
    DirectoryServer.getSubentryManager().registerChangeListener(this);
  }



  /**
   * Perform any required finalization tasks for all password policies
   * currently defined. This should only be called at Directory Server
   * shutdown.
   */
  public void finalizePasswordPolicies()
  {
    // Deregister this as subentry change listener with SubentryManager.
    DirectoryServer.getSubentryManager().deregisterChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(PasswordPolicyCfg configuration,
                                       List<Message> unacceptableReason)
  {
    // See if we can create a password policy from the provided configuration
    // entry.  If so, then it's acceptable.
    try
    {
      new PasswordPolicy(configuration);
    }
    catch (ConfigException ce)
    {
      Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
                                  ce.getMessage());
      unacceptableReason.add(message);
      return false;
    }
    catch (InitializationException ie)
    {
      Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
                                  ie.getMessage());
      unacceptableReason.add(message);
      return false;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      unacceptableReason.add(message);
      return false;
    }


    // If we've gotten here, then it is acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      PasswordPolicyCfg configuration)
  {
    DN                configEntryDN       = configuration.dn();
    ArrayList<Message> messages            = new ArrayList<Message>();


    // See if we can create a password policy from the provided configuration
    // entry.  If so, then register it with the Directory Server.
    try
    {
      PasswordPolicy policy = new PasswordPolicy(configuration);
      PasswordPolicyConfig config = new PasswordPolicyConfig(policy);

      DirectoryServer.registerPasswordPolicy(configEntryDN, config);
      configuration.addChangeListener(config);
      return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
    }
    catch (ConfigException ce)
    {
      messages.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
              ce.getMessage()));

      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
                                    messages);
    }
    catch (InitializationException ie)
    {
      messages.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
              ie.getMessage()));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
    catch (Exception e)
    {
      messages.add(ERR_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG.get(
              String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e)));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      PasswordPolicyCfg configuration, List<Message> unacceptableReason)
  {
    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    //  a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) &&
        defaultPolicyDN.equals(configuration.dn()))
    {
      Message message = WARN_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY.get(
              String.valueOf(defaultPolicyDN));
      unacceptableReason.add(message);
      return false;
    }
    else
    {
      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      PasswordPolicyCfg configuration)
  {
    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    //  a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    ArrayList<Message> messages = new ArrayList<Message>(1);
    DN policyDN = configuration.dn();
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) && defaultPolicyDN.equals(policyDN))
    {
      messages.add(WARN_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY.get(
              String.valueOf(defaultPolicyDN)));
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
                                    messages);
    }
    DirectoryServer.deregisterPasswordPolicy(policyDN);
    PasswordPolicyConfig config =
      DirectoryServer.getPasswordPolicyConfig(policyDN);
    if (config != null)
    {
      configuration.removeChangeListener(config);
    }

    messages.add(INFO_CONFIG_PWPOLICY_REMOVED_POLICY.get(
            String.valueOf(policyDN)));

    return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
  }



  /**
   * Attempts to parse an entry as password policy
   * subentry to create a password policy object.
   * @param entry subentry to parse.
   * @throws DirectoryException if a problem occurs
   *         while creating a password policy from
   *         given subentry.
   */
  public static void checkSubentryAcceptable(Entry entry)
          throws DirectoryException
  {
    SubEntry subentry = new SubEntry(entry);
    SubentryPasswordPolicy subentryPolicy =
            new SubentryPasswordPolicy(subentry);
    try
    {
      new PasswordPolicy(subentryPolicy);
    }
    catch (ConfigException ex)
    {
      throw new DirectoryException(
              ResultCode.UNWILLING_TO_PERFORM,
              ex.getMessageObject());
    }
    catch (InitializationException ex)
    {
      throw new DirectoryException(
              ResultCode.UNWILLING_TO_PERFORM,
              ex.getMessageObject());
    }
  }



  /**
   * {@inheritDoc}
   */
  public void checkSubentryAddAcceptable(Entry entry)
          throws DirectoryException
  {
    if (entry.isPasswordPolicySubentry())
    {
      checkSubentryAcceptable(entry);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkSubentryDeleteAcceptable(Entry entry)
          throws DirectoryException
  {
    // FIXME: something like a referential integrity check is needed to
    // ensure a policy is not removed when referenced by a user entry (
    // either directly or via a virtual attribute).
  }

  /**
   * {@inheritDoc}
   */
  public void checkSubentryModifyAcceptable(Entry oldEntry, Entry newEntry)
          throws DirectoryException
  {
    if (newEntry.isPasswordPolicySubentry())
    {
      checkSubentryAcceptable(newEntry);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkSubentryModifyDNAcceptable(Entry oldEntry, Entry newEntry)
          throws DirectoryException
  {
    // FIXME: something like a referential integrity check is needed to
    // ensure a policy is not removed when referenced by a user entry (
    // either directly or via a virtual attribute).
  }

  /**
   * {@inheritDoc}
   */
  public void handleSubentryAdd(Entry entry)
  {
    if (entry.isPasswordPolicySubentry())
    {
      try
      {
        SubEntry subentry = new SubEntry(entry);
        PasswordPolicyConfig config =
                  createPasswordPolicyConfig(subentry);
        DirectoryServer.registerPasswordPolicy(
            subentry.getDN(), config);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugError("Could not create password policy subentry "
                  + "DN %s: %s",
                  entry.getDN().toString(),
                  stackTraceToSingleLineString(e));
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleSubentryDelete(Entry entry)
  {
    if (entry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterPasswordPolicy(entry.getDN());
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleSubentryModify(Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterPasswordPolicy(oldEntry.getDN());
    }

    if (newEntry.isPasswordPolicySubentry())
    {
      try
      {
        SubEntry subentry = new SubEntry(newEntry);
        PasswordPolicyConfig config =
                  createPasswordPolicyConfig(subentry);
        DirectoryServer.registerPasswordPolicy(
            subentry.getDN(), config);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugError("Could not create password policy subentry "
                  + "DN %s: %s",
                  newEntry.getDN().toString(),
                  stackTraceToSingleLineString(e));
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleSubentryModifyDN(Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isPasswordPolicySubentry())
    {
      DirectoryServer.deregisterPasswordPolicy(oldEntry.getDN());
    }

    if (newEntry.isPasswordPolicySubentry())
    {
      try
      {
        SubEntry subentry = new SubEntry(newEntry);
        PasswordPolicyConfig config =
                  createPasswordPolicyConfig(subentry);
        DirectoryServer.registerPasswordPolicy(
            subentry.getDN(), config);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugError("Could not create password policy subentry "
                  + "DN %s: %s",
                  newEntry.getDN().toString(),
                  stackTraceToSingleLineString(e));
        }
      }
    }
  }
}
