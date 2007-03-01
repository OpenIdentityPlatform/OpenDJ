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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
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
       implements ConfigAddListener, ConfigDeleteListener
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


    // First, get the configuration base entry.
    ConfigEntry baseEntry;
    try
    {
      DN policyBase = DN.decode(DN_PWPOLICY_CONFIG_BASE);
      baseEntry = DirectoryServer.getConfigHandler().getConfigEntry(policyBase);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_PWPOLICY_CANNOT_GET_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (baseEntry == null)
    {
      // The password policy base entry does not exist.  This is not
      // acceptable, so throw an exception.
      int    msgID   = MSGID_CONFIG_PWPOLICY_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the policy base entry.  We
    // don't care about modifications to it.
    baseEntry.registerAddListener(this);
    baseEntry.registerDeleteListener(this);


    // See if the base entry has any children.  If not, then that means that
    // there are no policies defined, so that's a problem.
    if (! baseEntry.hasChildren())
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
    for (ConfigEntry childEntry : baseEntry.getChildren().values())
    {
      try
      {
        PasswordPolicy policy = new PasswordPolicy(childEntry);
        DirectoryServer.registerPasswordPolicy(childEntry.getDN(), policy);
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String.valueOf(childEntry.getDN()),
                                    ce.getMessage());
        throw new ConfigException(msgID, message, ce);
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String.valueOf(childEntry.getDN()),
                                    ie.getMessage());
        throw new InitializationException(msgID, message, ie);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
        String message = getMessage(msgID, String.valueOf(childEntry.getDN()),
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
   * Indicates whether the configuration entry that will result from a proposed
   * add is acceptable to this add listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested add.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed entry is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
                                       StringBuilder unacceptableReason)
  {


    // See if we can create a password policy from the provided configuration
    // entry.  If so, then it's acceptable.
    try
    {
      new PasswordPolicy(configEntry);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  ce.getMessage());
      unacceptableReason.append(message);
      return false;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  ie.getMessage());
      unacceptableReason.append(message);
      return false;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  stackTraceToSingleLineString(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here, then it is acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided added entry.
   *
   * @param  configEntry  The new configuration entry that contains the
   *                      configuration to apply.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {


    DN                configEntryDN       = configEntry.getDN();
    ArrayList<String> messages            = new ArrayList<String>();


    // See if we can create a password policy from the provided configuration
    // entry.  If so, then register it with the Directory Server.
    try
    {
      PasswordPolicy policy = new PasswordPolicy(configEntry);
      DirectoryServer.registerPasswordPolicy(configEntryDN, policy);
      return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
    }
    catch (ConfigException ce)
    {
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configEntry.getDN()),
                              ce.getMessage()));

      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
                                    messages);
    }
    catch (InitializationException ie)
    {
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configEntry.getDN()),
                              ie.getMessage()));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
    catch (Exception e)
    {
      int msgID = MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG;
      messages.add(getMessage(msgID, String.valueOf(configEntry.getDN()),
                              stackTraceToSingleLineString(e)));

      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false, messages);
    }
  }



  /**
   * Indicates whether it is acceptable to remove the provided configuration
   * entry.
   *
   * @param  configEntry         The configuration entry that will be removed
   *                             from the configuration.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed delete is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed from the
   *          configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {


    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    //  a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) &&
        defaultPolicyDN.equals(configEntry.getDN()))
    {
      int msgID = MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY;
      String message = getMessage(msgID, String.valueOf(defaultPolicyDN));
      unacceptableReason.append(message);
      return false;
    }
    else
    {
      return true;
    }
  }



  /**
   * Attempts to apply a new configuration based on the provided deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been deleted.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {


    // We'll allow the policy to be removed as long as it isn't the default.
    // FIXME: something like a referential integrity check is needed to ensure
    //  a policy is not removed when referenced by a user entry (either
    // directly or via a virtual attribute).
    ArrayList<String> messages = new ArrayList<String>(1);
    DN policyDN = configEntry.getDN();
    DN defaultPolicyDN = DirectoryServer.getDefaultPasswordPolicyDN();
    if ((defaultPolicyDN != null) && defaultPolicyDN.equals(policyDN))
    {
      int msgID = MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY;
      messages.add(getMessage(msgID, String.valueOf(defaultPolicyDN)));

      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false,
                                    messages);
    }

    DirectoryServer.deregisterPasswordPolicy(policyDN);

    int msgID = MSGID_CONFIG_PWPOLICY_REMOVED_POLICY;
    messages.add(getMessage(msgID, String.valueOf(policyDN)));

    return new ConfigChangeResult(ResultCode.SUCCESS, false, messages);
  }
}
