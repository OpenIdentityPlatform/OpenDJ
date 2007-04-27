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
import java.util.List;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of password
 * policies defined in the Directory Server.  It will initialize the policies
 * when the server starts, and then will manage any additions or removals while
 * the server is running.
 */
public class PasswordPolicyConfigManager
       implements ConfigurationAddListener<PasswordPolicyCfg>,
       ConfigurationDeleteListener<PasswordPolicyCfg>
{



  /**
   * Creates a new instance of this password policy config manager.
   */
  public PasswordPolicyConfigManager()
  {
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
    // can be notified if any password ploicies entries are added or removed.
    rootConfiguration.addPasswordPolicyAddListener(this);
    rootConfiguration.addPasswordPolicyDeleteListener(this);

    // First, get the configuration base entry.
    String[] passwordPoliciesName = rootConfiguration.listPasswordPolicys() ;

    // See if the base entry has any children.  If not, then that means that
    // there are no policies defined, so that's a problem.
    if (passwordPoliciesName.length == 0)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_NO_POLICIES;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Get the DN of the default password policy from the core configuration.
    if( null == DirectoryServer.getDefaultPasswordPolicyDN())
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_NO_DEFAULT_POLICY;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
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
        int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String
            .valueOf(passwordPolicyConfiguration.dn()), ce.getMessage());
        throw new ConfigException(msgID, message, ce);
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String
            .valueOf(passwordPolicyConfiguration.dn()), ie.getMessage());
        throw new InitializationException(msgID, message, ie);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String
            .valueOf(passwordPolicyConfiguration.dn()),
            stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    // If the entry specified by the default password policy DN has not been
    // registered, then fail.
    if (null == DirectoryServer.getDefaultPasswordPolicy())
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_MISSING_DEFAULT_POLICY;
      DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
      String message = getMessage(msgID, String.valueOf(defaultPolicyDN));
      throw new ConfigException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(PasswordPolicyCfg configuration,
                                       List<String> unacceptableReason)
  {
    // See if we can create a password policy from the provided configuration
    // entry.  If so, then it's acceptable.
    try
    {
      new PasswordPolicy(configuration);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configuration.dn()),
                                  ce.getMessage());
      unacceptableReason.add(message);
      return false;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configuration.dn()),
                                  ie.getMessage());
      unacceptableReason.add(message);
      return false;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configuration.dn()),
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
    ArrayList<String> messages            = new ArrayList<String>();


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
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configuration.dn()),
                              ce.getMessage()));

      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
                                    messages);
    }
    catch (InitializationException ie)
    {
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configuration.dn()),
                              ie.getMessage()));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configuration.dn()),
                              stackTraceToSingleLineString(e)));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      PasswordPolicyCfg configuration, List<String> unacceptableReason)
  {
    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    //  a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) &&
        defaultPolicyDN.equals(configuration.dn()))
    {
      int msgID = MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY;
      String message = getMessage(msgID, String.valueOf(defaultPolicyDN));
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
    ArrayList<String> messages = new ArrayList<String>(1);
    DN policyDN = configuration.dn();
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) && defaultPolicyDN.equals(policyDN))
    {
      int msgID = MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY;
      messages.add(getMessage(msgID, String.valueOf(defaultPolicyDN)));

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

    int msgID = MSGID_CONFIG_PWPOLICY_REMOVED_POLICY;
    messages.add(getMessage(msgID, String.valueOf(policyDN)));

    return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
  }
}
