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

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

/**
 This class is the interface between the password policy configurable component
 and a password policy state object. When a password policy entry is added to
 the configuration, an instance of this class is created and registered to
 manage subsequent modification to that configuration entry, including
 valiadating any proposed modification and applying an accepted modification.
 */
public class PasswordPolicyConfig
        implements ConfigurationChangeListener<PasswordPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


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
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      PasswordPolicyCfg configuration, List<String> unacceptableReasons)
  {
    assert configuration.dn().equals(this.currentPolicy.getConfigEntryDN() )
            : "Internal Error: mismatch between DN of configuration entry and"
              + "DN of current password policy." ;

    try
    {
      new PasswordPolicy(configuration);
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }

      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      unacceptableReasons.add(ie.getMessage());
      return false;
    }

    // If we made it here, then the configuration is acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      PasswordPolicyCfg configuration)
  {
    assert configuration.dn().equals(this.currentPolicy.getConfigEntryDN() )
            : "Internal Error: mismatch between DN of configuration entry and"
              + "DN of current password policy." ;

    PasswordPolicy p;

    try
    {
      p = new PasswordPolicy(configuration);
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
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
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
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
    int msgID = MSGID_PWPOLICY_UPDATED_POLICY;
    messages.add(getMessage(msgID, String.valueOf(p.getConfigEntryDN())));

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
