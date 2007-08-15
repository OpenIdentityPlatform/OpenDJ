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
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.ApproximateMatchingRuleCfgDefn;
import org.opends.server.admin.std.meta.EqualityMatchingRuleCfgDefn;
import org.opends.server.admin.std.meta.OrderingMatchingRuleCfgDefn;
import org.opends.server.admin.std.meta.SubstringMatchingRuleCfgDefn;
import org.opends.server.admin.std.server.ApproximateMatchingRuleCfg;
import org.opends.server.admin.std.server.EqualityMatchingRuleCfg;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.admin.std.server.OrderingMatchingRuleCfg;
import org.opends.server.admin.std.server.SubstringMatchingRuleCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;


import org.opends.server.types.InitializationException;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;
import org.opends.server.loggers.ErrorLogger;


/**
 * This class defines a utility that will be used to manage the set of matching
 * rules defined in the Directory Server.  It wil initialize the rules when the
 * server starts, and then will manage any additions, removals, or modifications
 * to any matching rules while the server is running.
 */
public class MatchingRuleConfigManager
       implements ConfigurationChangeListener<MatchingRuleCfg>,
                  ConfigurationAddListener<MatchingRuleCfg>,
                  ConfigurationDeleteListener<MatchingRuleCfg>

{
  // A mapping between the DNs of the config entries and the associated matching
  // rules.
  private ConcurrentHashMap<DN,MatchingRule> matchingRules;



  /**
   * Creates a new instance of this matching rule config manager.
   */
  public MatchingRuleConfigManager()
  {
    matchingRules = new ConcurrentHashMap<DN,MatchingRule>();
  }



  /**
   * Initializes all matching rules currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the matching
   *                           rule initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the matching rules that is not related to
   *                                   the server configuration.
   */
  public void initializeMatchingRules()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any matching rule entries are added or removed.
    rootConfiguration.addMatchingRuleAddListener(this);
    rootConfiguration.addMatchingRuleDeleteListener(this);


    //Initialize the existing matching rules.
    for (String name : rootConfiguration.listMatchingRules())
    {
      MatchingRuleCfg mrConfiguration = rootConfiguration.getMatchingRule(name);
      mrConfiguration.addChangeListener(this);

      if (mrConfiguration.isEnabled())
      {
        String className = mrConfiguration.getMatchingRuleClass();
        try
        {
          MatchingRule matchingRule =
               loadMatchingRule(className, mrConfiguration, true);

          try
          {
            DirectoryServer.registerMatchingRule(matchingRule, false);
            matchingRules.put(mrConfiguration.dn(), matchingRule);
          }
          catch (DirectoryException de)
          {
            Message message = WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(
                String.valueOf(mrConfiguration.dn()), de.getMessageObject());
            ErrorLogger.logError(message);
            continue;
          }
        }
        catch (InitializationException ie)
        {
          ErrorLogger.logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(MatchingRuleCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // matching rule.
      String className = configuration.getMatchingRuleClass();
      try
      {
        loadMatchingRule(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(MatchingRuleCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    MatchingRule matchingRule = null;

    // Get the name of the class and make sure we can instantiate it as a
    // matching rule.
    String className = configuration.getMatchingRuleClass();
    try
    {
      matchingRule = loadMatchingRule(className, configuration, true);

      try
      {
        DirectoryServer.registerMatchingRule(matchingRule, false);
        matchingRules.put(configuration.dn(), matchingRule);
      }
      catch (DirectoryException de)
      {
        Message message = WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        messages.add(message);

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(MatchingRuleCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // If the matching rule is enabled, then check to see if there are any
    // defined attribute types or matching rule uses that use the matching rule.
    // If so, then don't allow it to be deleted.
    boolean configAcceptable = true;
    MatchingRule matchingRule = matchingRules.get(configuration.dn());
    if (matchingRule != null)
    {
      String oid = matchingRule.getOID();
      for (AttributeType at : DirectoryServer.getAttributeTypes().values())
      {
        ApproximateMatchingRule amr = at.getApproximateMatchingRule();
        if ((amr != null) && oid.equals(amr.getOID()))
        {
          Message message =
                  WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT.get(
                          matchingRule.getName(),
                          at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
          continue;
        }

        EqualityMatchingRule emr = at.getEqualityMatchingRule();
        if ((emr != null) && oid.equals(emr.getOID()))
        {
          Message message =
                  WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT.get(
                          matchingRule.getName(),
                          at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
          continue;
        }

        OrderingMatchingRule omr = at.getOrderingMatchingRule();
        if ((omr != null) && oid.equals(omr.getOID()))
        {
          Message message =
                  WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT.get(
                          matchingRule.getName(),
                          at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
          continue;
        }

        SubstringMatchingRule smr = at.getSubstringMatchingRule();
        if ((smr != null) && oid.equals(smr.getOID()))
        {
          Message message =
                  WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT.get(
                          matchingRule.getName(),
                          at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
          continue;
        }
      }

      for (MatchingRuleUse mru : DirectoryServer.getMatchingRuleUses().values())
      {
        if (oid.equals(mru.getMatchingRule().getOID()))
        {
          Message message =
                  WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_MRU.get(
                          matchingRule.getName(),
                          mru.getName());
          unacceptableReasons.add(message);

          configAcceptable = false;
          continue;
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 MatchingRuleCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    MatchingRule matchingRule = matchingRules.remove(configuration.dn());
    if (matchingRule != null)
    {
      DirectoryServer.deregisterMatchingRule(matchingRule);
      matchingRule.finalizeMatchingRule();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(MatchingRuleCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // matching rule.
      String className = configuration.getMatchingRuleClass();
      try
      {
        loadMatchingRule(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        configAcceptable = false;
      }
    }
    else
    {
      // If the matching rule is currently enabled and the change would make it
      // disabled, then only allow it if the matching rule isn't already in use.
      MatchingRule matchingRule = matchingRules.get(configuration.dn());
      if (matchingRule != null)
      {
        String oid = matchingRule.getOID();
        for (AttributeType at : DirectoryServer.getAttributeTypes().values())
        {
          ApproximateMatchingRule amr = at.getApproximateMatchingRule();
          if ((amr != null) && oid.equals(amr.getOID()))
          {
            Message message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT.get(
                            matchingRule.getName(),
                            at.getNameOrOID());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }

          EqualityMatchingRule emr = at.getEqualityMatchingRule();
          if ((emr != null) && oid.equals(emr.getOID()))
          {
            Message message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT.get(
                            matchingRule.getName(),
                            at.getNameOrOID());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }

          OrderingMatchingRule omr = at.getOrderingMatchingRule();
          if ((omr != null) && oid.equals(omr.getOID()))
          {
            Message message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT.get(
                            matchingRule.getName(),
                            at.getNameOrOID());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }

          SubstringMatchingRule smr = at.getSubstringMatchingRule();
          if ((smr != null) && oid.equals(smr.getOID()))
          {
            Message message = WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT
                    .get(matchingRule.getName(), at.getNameOrOID());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }
        }

        for (MatchingRuleUse mru :
             DirectoryServer.getMatchingRuleUses().values())
        {
          if (oid.equals(mru.getMatchingRule().getOID()))
          {
            Message message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_MRU.get(
                            matchingRule.getName(), mru.getName());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 MatchingRuleCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing matching rule if it's already enabled.
    MatchingRule existingRule = matchingRules.get(configuration.dn());


    // If the new configuration has the matching rule disabled, then disable it
    // if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingRule != null)
      {
        DirectoryServer.deregisterMatchingRule(existingRule);

        MatchingRule rule = matchingRules.remove(configuration.dn());
        if (rule != null)
        {
          rule.finalizeMatchingRule();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the matching rule.  If the matching rule is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the matching rule is disabled, then instantiate the
    // class and initialize and register it as a matching rule.
    String className = configuration.getMatchingRuleClass();
    if (existingRule != null)
    {
      if (! className.equals(existingRule.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    MatchingRule matchingRule = null;
    try
    {
      matchingRule = loadMatchingRule(className, configuration, true);

      try
      {
        DirectoryServer.registerMatchingRule(matchingRule, false);
        matchingRules.put(configuration.dn(), matchingRule);
      }
      catch (DirectoryException de)
      {
        Message message = WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(
                String.valueOf(configuration.dn()), de.getMessageObject());
        messages.add(message);

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as an attribute syntax, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the attribute syntax
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the attribute
   *                        syntax.  It must not be {@code null}.
   * @param  initialize     Indicates whether the matching rule instance should
   *                        be initialized.
   *
   * @return  The possibly initialized attribute syntax.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the attribute syntax.
   */
  private MatchingRule loadMatchingRule(String className,
                                        MatchingRuleCfg configuration,
                                        boolean initialize)
          throws InitializationException
  {
    try
    {
      MatchingRule matchingRule = null;
      if (configuration instanceof ApproximateMatchingRuleCfg)
      {
        ApproximateMatchingRuleCfgDefn definition =
             ApproximateMatchingRuleCfgDefn.getInstance();
        ClassPropertyDefinition propertyDefinition =
             definition.getMatchingRuleClassPropertyDefinition();
        Class<? extends ApproximateMatchingRule> approximateMatchingRuleClass =
             propertyDefinition.loadClass(className,
                                          ApproximateMatchingRule.class);
        matchingRule = approximateMatchingRuleClass.newInstance();
      }
      else if (configuration instanceof EqualityMatchingRuleCfg)
      {
        EqualityMatchingRuleCfgDefn definition =
             EqualityMatchingRuleCfgDefn.getInstance();
        ClassPropertyDefinition propertyDefinition =
             definition.getMatchingRuleClassPropertyDefinition();
        Class<? extends EqualityMatchingRule> equalityMatchingRuleClass =
             propertyDefinition.loadClass(className,
                                          EqualityMatchingRule.class);
        matchingRule = equalityMatchingRuleClass.newInstance();
      }
      else if (configuration instanceof OrderingMatchingRuleCfg)
      {
        OrderingMatchingRuleCfgDefn definition =
             OrderingMatchingRuleCfgDefn.getInstance();
        ClassPropertyDefinition propertyDefinition =
             definition.getMatchingRuleClassPropertyDefinition();
        Class<? extends OrderingMatchingRule> orderingMatchingRuleClass =
             propertyDefinition.loadClass(className,
                                          OrderingMatchingRule.class);
        matchingRule = orderingMatchingRuleClass.newInstance();
      }
      else if (configuration instanceof SubstringMatchingRuleCfg)
      {
        SubstringMatchingRuleCfgDefn definition =
             SubstringMatchingRuleCfgDefn.getInstance();
        ClassPropertyDefinition propertyDefinition =
             definition.getMatchingRuleClassPropertyDefinition();
        Class<? extends SubstringMatchingRule> substringMatchingRuleClass =
             propertyDefinition.loadClass(className,
                                          SubstringMatchingRule.class);
        matchingRule = substringMatchingRuleClass.newInstance();
      }
      else
      {
        throw new AssertionError("Unsupported matching rule type:  " +
                                 className + " with config type " +
                                 configuration.getClass().getName());
      }

      if (initialize)
      {
        Method method =
             matchingRule.getClass().getMethod("initializeMatchingRule",
                  configuration.definition().getServerConfigurationClass());
        method.invoke(matchingRule, configuration);
      }
      else
      {
        Method method =
             matchingRule.getClass().getMethod("isConfigurationAcceptable",
                                               MatchingRuleCfg.class,
                                               List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(matchingRule,
                                                     configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<Message> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message = ERR_CONFIG_SCHEMA_MR_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return matchingRule;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_SCHEMA_MR_CANNOT_INITIALIZE.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

