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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.AttributeSyntaxCfgDefn;
import org.forgerock.opendj.server.config.server.AttributeSyntaxCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class defines a utility that will be used to manage the set of attribute
 * syntaxes defined in the Directory Server.  It will initialize the syntaxes
 * when the server starts, and then will manage any additions, removals, or
 * modifications to any syntaxes while the server is running.
 */
public class AttributeSyntaxConfigManager
       implements ConfigurationChangeListener<AttributeSyntaxCfg>,
                  ConfigurationAddListener<AttributeSyntaxCfg>,
                  ConfigurationDeleteListener<AttributeSyntaxCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs of the config entries and the associated attribute syntaxes. */
  private ConcurrentHashMap<DN,AttributeSyntax> syntaxes;

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this attribute syntax config manager.
   *
   * @param serverContext
   *            The server context, that contains the schema.
   */
  public AttributeSyntaxConfigManager(final ServerContext serverContext)
  {
    this.serverContext = serverContext;
    syntaxes = new ConcurrentHashMap<>();
  }

  /**
   * Initializes all attribute syntaxes currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the attribute
   *                           syntax initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the attribute syntaxes that is not
   *                                   related to the server configuration.
   */
  public void initializeAttributeSyntaxes()
         throws ConfigException, InitializationException
  {
    RootCfg rootConfiguration = serverContext.getRootConfig();
    rootConfiguration.addAttributeSyntaxAddListener(this);
    rootConfiguration.addAttributeSyntaxDeleteListener(this);

    //Initialize the existing attribute syntaxes.
    for (String name : rootConfiguration.listAttributeSyntaxes())
    {
      AttributeSyntaxCfg syntaxConfiguration =
           rootConfiguration.getAttributeSyntax(name);
      syntaxConfiguration.addChangeListener(this);

      if (syntaxConfiguration.isEnabled())
      {
        String className = syntaxConfiguration.getJavaClass();
        try
        {
          AttributeSyntax<?> syntax = loadSyntax(className, syntaxConfiguration, true);
          try
          {
            Schema schemaNG = serverContext.getSchemaNG();
            Syntax sdkSyntax = syntax.getSDKSyntax(schemaNG);
            // skip the syntax registration if already defined in the (core) schema
            if (!schemaNG.hasSyntax(sdkSyntax.getOID()))
            {
              // The syntaxes configuration options (e.g. strictness, support for zero length values, etc)
              // are set by the call to loadSyntax() which calls initializeSyntax()
              // which updates the SDK schema options.
              serverContext.getSchema().registerSyntax(sdkSyntax, false);
            }
            syntaxes.put(syntaxConfiguration.dn(), syntax);
          }
          catch (DirectoryException de)
          {
            logger.warn(WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX, syntaxConfiguration.dn(), de.getMessageObject());
            continue;
          }
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
  public boolean isConfigurationAddAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // attribute syntax.
      String className = configuration.getJavaClass();
      try
      {
        loadSyntax(className, configuration, false);
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
  public ConfigChangeResult applyConfigurationAdd(
                                 AttributeSyntaxCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    AttributeSyntax syntax = null;

    // Get the name of the class and make sure we can instantiate it as an
    // attribute syntax.
    String className = configuration.getJavaClass();
    try
    {
      syntax = loadSyntax(className, configuration, true);

      try
      {
        Syntax sdkSyntax = syntax.getSDKSyntax(serverContext.getSchemaNG());
        serverContext.getSchema().registerSyntax(sdkSyntax, false);
        syntaxes.put(configuration.dn(), syntax);
      }
      catch (DirectoryException de)
      {
        ccr.addMessage(WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX.get(configuration.dn(), de.getMessageObject()));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // If the syntax is enabled, then check to see if there are any defined
    // attribute types that use the syntax.  If so, then don't allow it to be
    // deleted.
    boolean configAcceptable = true;
    AttributeSyntax syntax = syntaxes.get(configuration.dn());
    if (syntax != null)
    {
      String oid = syntax.getOID();
      for (AttributeType at : DirectoryServer.getSchema().getAttributeTypes())
      {
        if (oid.equals(at.getSyntax().getOID()))
        {
          LocalizableMessage message = WARN_CONFIG_SCHEMA_CANNOT_DELETE_SYNTAX_IN_USE.get(
                  syntax.getName(), at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
        }
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 AttributeSyntaxCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    AttributeSyntax<?> syntax = syntaxes.remove(configuration.dn());
    if (syntax != null)
    {
      Syntax sdkSyntax = syntax.getSDKSyntax(serverContext.getSchemaNG());
      try
      {
        serverContext.getSchema().deregisterSyntax(sdkSyntax);
      }
      catch (DirectoryException e)
      {
        ccr.addMessage(e.getMessageObject());
        ccr.setResultCodeIfSuccess(e.getResultCode());
      }
      syntax.finalizeSyntax();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // attribute syntax.
      String className = configuration.getJavaClass();
      try
      {
        loadSyntax(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }
    else
    {
      // If the syntax is currently enabled and the change would make it
      // disabled, then only allow it if the syntax isn't already in use.
      AttributeSyntax<?> syntax = syntaxes.get(configuration.dn());
      if (syntax != null)
      {
        String oid = syntax.getOID();
        for (AttributeType at : DirectoryServer.getSchema().getAttributeTypes())
        {
          if (oid.equals(at.getSyntax().getOID()))
          {
            LocalizableMessage message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_SYNTAX_IN_USE.get(syntax.getName(), at.getNameOrOID());
            unacceptableReasons.add(message);
            return false;
          }
        }
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(AttributeSyntaxCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the existing syntax if it's already enabled.
    AttributeSyntax<?> existingSyntax = syntaxes.get(configuration.dn());

    // If the new configuration has the syntax disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingSyntax != null)
      {
        Syntax sdkSyntax = existingSyntax.getSDKSyntax(serverContext.getSchemaNG());
        try
        {
          serverContext.getSchema().deregisterSyntax(sdkSyntax);
        }
        catch (DirectoryException e)
        {
          ccr.addMessage(e.getMessageObject());
          ccr.setResultCodeIfSuccess(e.getResultCode());
        }
        AttributeSyntax<?> syntax = syntaxes.remove(configuration.dn());
        if (syntax != null)
        {
          syntax.finalizeSyntax();
        }
      }

      return ccr;
    }

    // Get the class for the attribute syntax.  If the syntax is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the syntax is disabled, then instantiate the class and
    // initialize and register it as an attribute syntax.
    String className = configuration.getJavaClass();
    if (existingSyntax != null)
    {
      if (! className.equals(existingSyntax.getClass().getName()))
      {
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    AttributeSyntax<?> syntax = null;
    try
    {
      syntax = loadSyntax(className, configuration, true);

      try
      {
        Syntax sdkSyntax = syntax.getSDKSyntax(serverContext.getSchemaNG());
        serverContext.getSchema().registerSyntax(sdkSyntax, false);
        syntaxes.put(configuration.dn(), syntax);
      }
      catch (DirectoryException de)
      {
        ccr.addMessage(WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX.get(configuration.dn(), de.getMessageObject()));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
    }
    catch (InitializationException ie)
    {
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    return ccr;
  }

  /**
   * Loads the specified class, instantiates it as an attribute syntax, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the attribute syntax
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the attribute
   *                        syntax.  It should not be {@code null}.
   * @param  initialize     Indicates whether the attribute syntax instance
   *                        should be initialized.
   *
   * @return  The possibly initialized attribute syntax.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the attribute syntax.
   */
  private AttributeSyntax<?> loadSyntax(String className,
                                     AttributeSyntaxCfg configuration,
                                     boolean initialize)
          throws InitializationException
  {
    try
    {
      AttributeSyntaxCfgDefn definition =
           AttributeSyntaxCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends AttributeSyntax> syntaxClass =
           propertyDefinition.loadClass(className, AttributeSyntax.class);
      AttributeSyntax syntax = syntaxClass.newInstance();

      if (initialize)
      {
        syntax.initializeSyntax(configuration, serverContext);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!syntax.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_SCHEMA_SYNTAX_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return syntax;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_SCHEMA_SYNTAX_CANNOT_INITIALIZE.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}
