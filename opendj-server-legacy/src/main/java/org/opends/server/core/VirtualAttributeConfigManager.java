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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.VirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;

/**
 * This class defines a utility that will be used to manage the set of
 * virtual attribute providers defined in the Directory Server.  It will
 * initialize the providers when the server starts, and then will manage any
 * additions, removals, or modifications to any virtual attribute providers
 * while the server is running.
 */
public class VirtualAttributeConfigManager
       implements ConfigurationChangeListener<VirtualAttributeCfg>,
                  ConfigurationAddListener<VirtualAttributeCfg>,
                  ConfigurationDeleteListener<VirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated virtual attribute rules. */
  private final ConcurrentMap<DN, VirtualAttributeRule> rules = new ConcurrentHashMap<>();

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this virtual attribute config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public VirtualAttributeConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes all virtual attribute providers currently defined in the
   * Directory Server configuration. This should only be called at Directory
   * Server startup.
   *
   * @throws ConfigException
   *           If a configuration problem causes the virtual attribute provider
   *           initialization process to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the virtual attribute
   *           providers that is not related to the server configuration.
   */
  public void initializeVirtualAttributes()
         throws ConfigException, InitializationException
  {
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addVirtualAttributeAddListener(this);
    rootConfiguration.addVirtualAttributeDeleteListener(this);

    //Initialize the existing virtual attribute providers.
    for (String providerName : rootConfiguration.listVirtualAttributes())
    {
      VirtualAttributeCfg cfg =
           rootConfiguration.getVirtualAttribute(providerName);
      cfg.addChangeListener(this);

      if (cfg.isEnabled())
      {
        String className = cfg.getJavaClass();
        try
        {
          VirtualAttributeProvider<? extends VirtualAttributeCfg> provider =
               loadProvider(className, cfg, true);

          Map<LocalizableMessage, DirectoryException> reasons =
              new LinkedHashMap<>();
          Set<SearchFilter> filters = buildFilters(cfg, reasons);
          if (!reasons.isEmpty())
          {
            Entry<LocalizableMessage, DirectoryException> entry =
                reasons.entrySet().iterator().next();
            throw new ConfigException(entry.getKey(), entry.getValue());
          }

          if (cfg.getAttributeType().isSingleValue())
          {
            if (provider.isMultiValued())
            {
              LocalizableMessage message = ERR_CONFIG_VATTR_SV_TYPE_WITH_MV_PROVIDER.
                  get(cfg.dn(), cfg.getAttributeType().getNameOrOID(), className);
              throw new ConfigException(message);
            }
            else if (cfg.getConflictBehavior() ==
                     VirtualAttributeCfgDefn.ConflictBehavior.
                          MERGE_REAL_AND_VIRTUAL)
            {
              LocalizableMessage message = ERR_CONFIG_VATTR_SV_TYPE_WITH_MERGE_VALUES.
                  get(cfg.dn(), cfg.getAttributeType().getNameOrOID());
              throw new ConfigException(message);
            }
          }

          VirtualAttributeRule rule = createRule(cfg, provider, filters);
          rules.put(cfg.dn(), rule);
        }
        catch (InitializationException ie)
        {
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }

  private VirtualAttributeRule createRule(VirtualAttributeCfg cfg,
      VirtualAttributeProvider<? extends VirtualAttributeCfg> provider,
      Set<SearchFilter> filters)
  {
    return new VirtualAttributeRule(cfg.getAttributeType(), provider,
           cfg.getBaseDN(),
           from(cfg.getScope()),
           cfg.getGroupDN(),
           filters,
           cfg.getConflictBehavior());
  }

  @Override
  public boolean isConfigurationAddAcceptable(
                      VirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // virtual attribute provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If there were any search filters provided, then make sure they are all
    // valid.
    return areFiltersAcceptable(configuration, unacceptableReasons);
  }

  private Set<SearchFilter> buildFilters(VirtualAttributeCfg cfg,
      Map<LocalizableMessage, DirectoryException> unacceptableReasons)
  {
    Set<SearchFilter> filters = new LinkedHashSet<>();
    for (String filterString : cfg.getFilter())
    {
      try
      {
        filters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        LocalizableMessage message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
            filterString, cfg.dn(), de.getMessageObject());
        unacceptableReasons.put(message, de);
      }
    }
    return filters;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
                                 VirtualAttributeCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    // Make sure that we can parse all of the search filters.
    Map<LocalizableMessage, DirectoryException> reasons =
        new LinkedHashMap<>();
    Set<SearchFilter> filters = buildFilters(configuration, reasons);
    if (!reasons.isEmpty())
    {
      ccr.getMessages().addAll(reasons.keySet());
      ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
    }

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    VirtualAttributeProvider<? extends VirtualAttributeCfg> provider = null;
    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      String className = configuration.getJavaClass();
      try
      {
        provider = loadProvider(className, configuration, true);
      }
      catch (InitializationException ie)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ie.getMessageObject());
      }
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      VirtualAttributeRule rule = createRule(configuration, provider, filters);
      rules.put(configuration.dn(), rule);
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      VirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // We will always allow getting rid of a virtual attribute rule.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 VirtualAttributeCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    VirtualAttributeRule rule = rules.remove(configuration.dn());
    if (rule != null)
    {
      rule.getProvider().finalizeVirtualAttributeProvider();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      VirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // virtual attribute provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If there were any search filters provided, then make sure they are all
    // valid.
    return areFiltersAcceptable(configuration, unacceptableReasons);
  }

  private boolean areFiltersAcceptable(VirtualAttributeCfg cfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    Map<LocalizableMessage, DirectoryException> reasons =
        new LinkedHashMap<>();
    buildFilters(cfg, reasons);
    if (!reasons.isEmpty())
    {
      unacceptableReasons.addAll(reasons.keySet());
      return false;
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 VirtualAttributeCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the existing rule if it's already enabled.
    VirtualAttributeRule existingRule = rules.get(configuration.dn());

    // If the new configuration has the rule disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingRule != null)
      {
        rules.remove(configuration.dn());
        existingRule.getProvider().finalizeVirtualAttributeProvider();
      }

      return ccr;
    }

    // Make sure that we can parse all of the search filters.
    Map<LocalizableMessage, DirectoryException> reasons =
        new LinkedHashMap<>();
    Set<SearchFilter> filters = buildFilters(configuration, reasons);
    if (!reasons.isEmpty())
    {
      ccr.getMessages().addAll(reasons.keySet());
      ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
    }

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    VirtualAttributeProvider<? extends VirtualAttributeCfg> provider = null;
    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      String className = configuration.getJavaClass();
      try
      {
        provider = loadProvider(className, configuration, true);
      }
      catch (InitializationException ie)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ie.getMessageObject());
      }
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      VirtualAttributeRule rule = createRule(configuration, provider, filters);
      rules.put(configuration.dn(), rule);
      if (existingRule != null)
      {
        existingRule.getProvider().finalizeVirtualAttributeProvider();
      }
    }

    return ccr;
  }

  /**
   * Loads the specified class, instantiates it as a certificate mapper, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the certificate mapper
   *                        class to load, instantiate, and initialize.
   * @param  cfg            The configuration to use to initialize the
   *                        virtual attribute provider.  It must not be
   *                        {@code null}.
   * @param  initialize     Indicates whether the virtual attribute provider
   *                        instance should be initialized.
   *
   * @return  The possibly initialized certificate mapper.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the certificate mapper.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private VirtualAttributeProvider<? extends VirtualAttributeCfg>
               loadProvider(String className, VirtualAttributeCfg cfg,
                            boolean initialize)
          throws InitializationException
  {
    try
    {
      VirtualAttributeCfgDefn definition =
           VirtualAttributeCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends VirtualAttributeProvider> providerClass =
           propertyDefinition.loadClass(className,
                                        VirtualAttributeProvider.class);
      VirtualAttributeProvider provider = providerClass.newInstance();

      if (initialize)
      {
        provider.initializeVirtualAttributeProvider(cfg);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!provider.isConfigurationAcceptable(cfg, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          LocalizableMessage message = ERR_CONFIG_VATTR_CONFIG_NOT_ACCEPTABLE.get(cfg.dn(), reasons);
          throw new InitializationException(message);
        }
      }

      return provider;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_VATTR_INITIALIZATION_FAILED.
          get(className, cfg.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /**
   * Retrieves the collection of registered virtual attribute rules.
   *
   * @return The collection of registered virtual attribute rules.
   */
  public Collection<VirtualAttributeRule> getVirtualAttributes()
  {
    return this.rules.values();
  }

  /**
   * Registers the provided virtual attribute rule.
   *
   * @param rule
   *          The virtual attribute rule to be registered.
   */
  public void register(VirtualAttributeRule rule)
  {
    rules.put(getDummyDN(rule), rule);
  }

  /**
   * Deregisters the provided virtual attribute rule.
   *
   * @param rule
   *          The virtual attribute rule to be deregistered.
   */
  public void deregister(VirtualAttributeRule rule)
  {
    rules.remove(getDummyDN(rule));
  }

  private DN getDummyDN(VirtualAttributeRule rule)
  {
    String name = rule.getAttributeType().getNameOrOID();
    return DN.valueOf("cn=" + name + ",cn=Virtual Attributes,cn=config");
  }
}
