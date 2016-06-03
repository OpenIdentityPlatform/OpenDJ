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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.server.config.meta.MatchingRuleCfgDefn;
import org.forgerock.opendj.server.config.server.MatchingRuleCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated matching rule Factories. */
  private ConcurrentHashMap<DN, MatchingRuleFactory<?>> matchingRuleFactories;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this matching rule config manager.
   *
   * @param serverContext
   *          The server context.
   */
  public MatchingRuleConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    matchingRuleFactories = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all matching rules after reading all the Matching Rule
   * factories currently defined in the Directory Server configuration.
   * This should only be called at Directory Server startup.
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
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addMatchingRuleAddListener(this);
    rootConfiguration.addMatchingRuleDeleteListener(this);

    //Initialize the existing matching rules.
    for (String name : rootConfiguration.listMatchingRules())
    {
      MatchingRuleCfg mrConfiguration = rootConfiguration.getMatchingRule(name);
      mrConfiguration.addChangeListener(this);

      if (mrConfiguration.isEnabled())
      {
        String className = mrConfiguration.getJavaClass();
        try
        {
          registerMatchingRules(mrConfiguration, className);
        }
        catch (DirectoryException de)
        {
          logger.warn(WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR, mrConfiguration.dn(), de.getMessageObject());
          continue;
        }
        catch (InitializationException ie)
        {
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(MatchingRuleCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // matching rule Factory.
      String className = configuration.getJavaClass();
      try
      {
        loadMatchingRuleFactory(className, configuration, false);
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

  @Override
  public ConfigChangeResult applyConfigurationAdd(MatchingRuleCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }


    // Get the name of the class and make sure we can instantiate it as a
    // matching rule Factory.
    String className = configuration.getJavaClass();
    registerMatchingRules(configuration, className, ccr);
    return ccr;
  }

  private void registerMatchingRules(MatchingRuleCfg configuration, String className, final ConfigChangeResult ccr)
  {
    try
    {
      registerMatchingRules(configuration, className);
    }
    catch (DirectoryException de)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(configuration.dn(), de.getMessageObject()));
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }
  }

  private void registerMatchingRules(MatchingRuleCfg configuration, String className)
      throws InitializationException, DirectoryException
  {
    MatchingRuleFactory<?> factory = loadMatchingRuleFactory(className, configuration, true);
    DirectoryServer.getSchema().registerMatchingRules(factory.getMatchingRules(), false);
    matchingRuleFactories.put(configuration.dn(),factory);
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(MatchingRuleCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // If the matching rule is enabled, then check to see if there are any
    // defined attribute types or matching rule uses that use the matching rule.
    // If so, then don't allow it to be deleted.
    boolean configAcceptable = true;
    MatchingRuleFactory<?> factory = matchingRuleFactories.get(configuration.dn());
    for(MatchingRule matchingRule: factory.getMatchingRules())
    {
      if (matchingRule != null)
      {
        for (AttributeType at : DirectoryServer.getSchema().getAttributeTypes())
        {
          final String attr = at.getNameOrOID();
          if (!isDeleteAcceptable(at.getApproximateMatchingRule(), matchingRule, attr, unacceptableReasons)
              || !isDeleteAcceptable(at.getEqualityMatchingRule(), matchingRule, attr, unacceptableReasons)
              || !isDeleteAcceptable(at.getOrderingMatchingRule(), matchingRule, attr, unacceptableReasons)
              || !isDeleteAcceptable(at.getSubstringMatchingRule(), matchingRule, attr, unacceptableReasons))
          {
            configAcceptable = false;
            continue;
          }
        }

        final String oid = matchingRule.getOID();
        for (MatchingRuleUse mru : DirectoryServer.getSchema().getMatchingRuleUses())
        {
          if (oid.equals(mru.getMatchingRule().getOID()))
          {
            LocalizableMessage message =
                    WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_MRU.get(
                            matchingRule.getNameOrOID(), mru.getNameOrOID());
            unacceptableReasons.add(message);

            configAcceptable = false;
            continue;
          }
        }
      }
    }

    return configAcceptable;
  }

  private boolean isDeleteAcceptable(MatchingRule mr, MatchingRule matchingRule, String attr,
      List<LocalizableMessage> unacceptableReasons)
  {
    if (mr != null && matchingRule.getOID().equals(mr.getOID()))
    {
      unacceptableReasons.add(WARN_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT.get(matchingRule.getNameOrOID(), attr));
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(MatchingRuleCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    MatchingRuleFactory<?> factory = matchingRuleFactories.remove(configuration.dn());
    if (factory != null)
    {
      deregisterMatchingRules(factory, ccr);
      factory.finalizeMatchingRule();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(MatchingRuleCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // matching rule Factory.
      String className = configuration.getJavaClass();
      try
      {
        loadMatchingRuleFactory(className, configuration, false);
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
      MatchingRuleFactory<?> factory = matchingRuleFactories.get(configuration.dn());
      if(factory == null)
      {
        //Factory was disabled again.
        return configAcceptable;
      }
      for(MatchingRule matchingRule: factory.getMatchingRules())
      {
        if (matchingRule != null)
        {
          for (AttributeType at : DirectoryServer.getSchema().getAttributeTypes())
          {
            final String attr = at.getNameOrOID();
            if (!isDisableAcceptable(at.getApproximateMatchingRule(), matchingRule, attr, unacceptableReasons)
                || !isDisableAcceptable(at.getEqualityMatchingRule(), matchingRule, attr, unacceptableReasons)
                || !isDisableAcceptable(at.getOrderingMatchingRule(), matchingRule, attr, unacceptableReasons)
                || !isDisableAcceptable(at.getSubstringMatchingRule(), matchingRule, attr, unacceptableReasons))
            {
              configAcceptable = false;
              continue;
            }
          }

          final String oid = matchingRule.getOID();
          for (MatchingRuleUse mru : DirectoryServer.getSchema().getMatchingRuleUses())
          {
            if (oid.equals(mru.getMatchingRule().getOID()))
            {
              LocalizableMessage message =
                      WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_MRU.get(
                              matchingRule.getNameOrOID(), mru.getNameOrOID());
              unacceptableReasons.add(message);

              configAcceptable = false;
              continue;
            }
          }
        }
      }
    }
    return configAcceptable;
  }

  private boolean isDisableAcceptable(MatchingRule mr, MatchingRule matchingRule,
      String attrNameOrOID, Collection<LocalizableMessage> unacceptableReasons)
  {
    if (mr != null && matchingRule.getOID().equals(mr.getOID()))
    {
      unacceptableReasons.add(
          WARN_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT.get(matchingRule.getNameOrOID(), attrNameOrOID));
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 MatchingRuleCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

   // Get the existing matching rule factory if it's already enabled.
    MatchingRuleFactory<?> existingFactory =
            matchingRuleFactories.get(configuration.dn());

    // If the new configuration has the matching rule disabled, then disable it
    // if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
     if (existingFactory != null)
      {
        deregisterMatchingRules(existingFactory, ccr);
        matchingRuleFactories.remove(configuration.dn());
        existingFactory.finalizeMatchingRule();
      }
      return ccr;
    }

    // Get the class for the matching rule.  If the matching rule is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the matching rule is disabled, then instantiate the
    // class and initialize and register it as a matching rule.
    String className = configuration.getJavaClass();
    if (existingFactory != null)
    {
      if (! className.equals(existingFactory.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    registerMatchingRules(configuration, className, ccr);
    return ccr;
  }

  private void deregisterMatchingRules(MatchingRuleFactory<?> factory, final ConfigChangeResult ccr)
  {
    for (MatchingRule matchingRule : factory.getMatchingRules())
    {
      try
      {
        DirectoryServer.getSchema().deregisterMatchingRule(matchingRule);
      }
      catch (DirectoryException e)
      {
        ccr.addMessage(e.getMessageObject());
        ccr.setResultCodeIfSuccess(e.getResultCode());
      }
    }
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
  private MatchingRuleFactory<?> loadMatchingRuleFactory(String className,
                                        MatchingRuleCfg configuration,
                                        boolean initialize)
          throws InitializationException
  {
    try
    {
      MatchingRuleFactory factory = null;
      MatchingRuleCfgDefn definition = MatchingRuleCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition = definition.getJavaClassPropertyDefinition();
      Class<? extends MatchingRuleFactory> matchingRuleFactoryClass =
           propertyDefinition.loadClass(className,
                                        MatchingRuleFactory.class);
      factory = matchingRuleFactoryClass.newInstance();

      if (initialize)
      {
        factory.initializeMatchingRule(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!factory.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_SCHEMA_MR_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return factory;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_SCHEMA_MR_CANNOT_INITIALIZE.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
