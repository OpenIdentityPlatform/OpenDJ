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
 * Copyright 2011 profiq s.r.o.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.plugins;

import static org.opends.messages.PluginMessages.*;
import static com.forgerock.opendj.util.StaticUtils.toLowerCase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.AttributeCleanupPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseModifyOperation;

/**
 * The attribute cleanup plugin implementation class. The plugin removes and/or
 * renames the configured parameters from the incoming ADD and MODIFY requests.
 */
public class AttributeCleanupPlugin extends
    DirectoryServerPlugin<AttributeCleanupPluginCfg> implements
    ConfigurationChangeListener<AttributeCleanupPluginCfg>
{

  /** Plugin configuration. */
  private AttributeCleanupPluginCfg config;

  /** Debug tracer. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A table of attributes to be renamed. */
  private Map<String, String> attributesToRename;

  /** The set of attributes to be removed. */
  private Set<String> attributesToRemove;

  /**
   * This lock prevents concurrent updates to the configuration while operations
   * are being processed.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReadLock sharedLock = lock.readLock();
  private final WriteLock exclusiveLock = lock.writeLock();



  /** Default constructor. */
  public AttributeCleanupPlugin()
  {
    super();
  }



  @Override
  public ConfigChangeResult applyConfigurationChange(
      final AttributeCleanupPluginCfg config)
  {
    exclusiveLock.lock();
    try
    {
      /* Apply the change, as at this point is has been validated. */
      this.config = config;

      attributesToRename = new HashMap<>();
      for (final String mapping : config.getRenameInboundAttributes())
      {
        final int colonPos = mapping.lastIndexOf(":");
        final String fromAttr = mapping.substring(0, colonPos).trim();
        final String toAttr = mapping.substring(colonPos + 1).trim();
        attributesToRename.put(toLowerCase(fromAttr), toLowerCase(toAttr));
      }

      attributesToRemove = new HashSet<>();
      for (final String attr : config.getRemoveInboundAttributes())
      {
        attributesToRemove.add(toLowerCase(attr.trim()));
      }

      /* Update was successful, no restart required. */
      return new ConfigChangeResult();
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  @Override
  public PluginResult.PreParse doPreParse(
      final PreParseAddOperation addOperation)
  {
    sharedLock.lock();
    try
    {
      /* First strip the listed attributes, then rename the ones that remain. */
      processInboundRemove(addOperation);
      processInboundRename(addOperation);

      return PluginResult.PreParse.continueOperationProcessing();
    }
    finally
    {
      sharedLock.unlock();
    }
  }



  @Override
  public PluginResult.PreParse doPreParse(
      final PreParseModifyOperation modifyOperation)
  {
    sharedLock.lock();
    try
    {
      /* First strip the listed attributes, then rename the ones that remain. */
      processInboundRemove(modifyOperation);
      processInboundRename(modifyOperation);

      /*
       * If the MODIFY request has been stripped of ALL modifications, stop the
       * processing and return SUCCESS to the client.
       */
      if (modifyOperation.getRawModifications().isEmpty())
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("The AttributeCleanupPlugin has eliminated all "
              + "modifications. The processing should be stopped.");
        }
        return PluginResult.PreParse.stopProcessing(ResultCode.SUCCESS, null);
      }

      return PluginResult.PreParse.continueOperationProcessing();
    }
    finally
    {
      sharedLock.unlock();
    }
  }



  @Override
  public void finalizePlugin()
  {
    /*
     * It's not essential to take the lock here, but we will anyhow for
     * consistency with other methods.
     */
    exclusiveLock.lock();
    try
    {
      /* Deregister change listeners. */
      config.removeAttributeCleanupChangeListener(this);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  @Override
  public void initializePlugin(final Set<PluginType> pluginTypes,
      final AttributeCleanupPluginCfg configuration) throws ConfigException,
      InitializationException
  {
    /* The plugin should be invoked only for pre-parse ADD and MODIFY operations. */
    for (final PluginType t : pluginTypes)
    {
      switch (t)
      {
      case PRE_PARSE_ADD:
        break;
      case PRE_PARSE_MODIFY:
        break;
      default:
        throw new ConfigException(ERR_PLUGIN_ATTR_CLEANUP_INITIALIZE_PLUGIN.get(t));
      }
    }

    /* Verify the current configuration. */
    final List<LocalizableMessage> messages = new LinkedList<>();
    if (!isConfigurationChangeAcceptable(configuration, messages))
    {
      throw new ConfigException(messages.get(0));
    }

    /* Register change listeners. */
    configuration.addAttributeCleanupChangeListener(this);

    /* Save the configuration. */
    applyConfigurationChange(configuration);
  }



  @Override
  public boolean isConfigurationAcceptable(final PluginCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    final AttributeCleanupPluginCfg cfg =
      (AttributeCleanupPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  @Override
  public boolean isConfigurationChangeAcceptable(
      final AttributeCleanupPluginCfg config, final List<LocalizableMessage> messages)
  {
    /* The admin framework will ensure that there are no duplicate attributes to be removed. */
    boolean isValid = true;

    /*
     * Verify that there are no duplicate mappings and that attributes are
     * renamed to valid attribute types.
     */
    final Set<String> fromAttrs = new HashSet<>();
    for (final String attr : config.getRenameInboundAttributes())
    {
      /*
       * The format is: from:to where each 'from' and 'to' are attribute
       * descriptions. The admin framework ensures that the format is correct.
       */
      final int colonPos = attr.lastIndexOf(":");
      final String fromAttr = attr.substring(0, colonPos).trim();
      final String toAttr = attr.substring(colonPos + 1).trim();

      /*
       * Make sure that toAttr is defined within the server, being careful to
       * ignore attribute options.
       */
      final int semicolonPos = toAttr.indexOf(";");
      final String toAttrType = semicolonPos < 0 && semicolonPos < toAttr.length() - 1
          ? toAttr
          : toAttr.substring(semicolonPos + 1);

      if (DirectoryServer.getSchema().getAttributeType(toAttrType).isPlaceHolder())
      {
        messages.add(ERR_PLUGIN_ATTR_CLEANUP_ATTRIBUTE_MISSING.get(toAttr));
        isValid = false;
      }

      // Check for duplicates.
      final String nfromAttr = toLowerCase(fromAttr);
      if (!fromAttrs.add(nfromAttr))
      {
        messages.add(ERR_PLUGIN_ATTR_CLEANUP_DUPLICATE_VALUE.get(fromAttr));
        isValid = false;
      }

      // Check that attribute does not map to itself.
      if (nfromAttr.equals(toLowerCase(toAttr)))
      {
        messages.add(ERR_PLUGIN_ATTR_CLEANUP_EQUAL_VALUES.get(fromAttr, toAttr));
        isValid = false;
      }
    }

    return isValid;
  }



  /**
   * Remove the attributes listed in the configuration under
   * ds-cfg-remove-inbound-attributes from the incoming ADD request.
   *
   * @param addOperation
   *          Current ADD operation.
   */
  private void processInboundRemove(final PreParseAddOperation addOperation)
  {
    final List<RawAttribute> inAttrs = new LinkedList<>(addOperation.getRawAttributes());
    final ListIterator<RawAttribute> iterator = inAttrs.listIterator();
    while (iterator.hasNext())
    {
      final RawAttribute rawAttr = iterator.next();
      final String attrName = toLowerCase(rawAttr.getAttributeType().trim());
      if (attributesToRemove.contains(attrName))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("AttributeCleanupPlugin removing '%s'",
              rawAttr.getAttributeType());
        }
        iterator.remove();
      }
    }
    addOperation.setRawAttributes(inAttrs);
  }



  /**
   * Remove the attributes listed in the configuration under
   * ds-cfg-remove-inbound-attributes from the incoming MODIFY request.
   *
   * @param modifyOperation
   *          Current MODIFY operation.
   */
  private void processInboundRemove(
      final PreParseModifyOperation modifyOperation)
  {
    final List<RawModification> rawMods = new LinkedList<>(modifyOperation.getRawModifications());
    final ListIterator<RawModification> iterator = rawMods.listIterator();
    while (iterator.hasNext())
    {
      final RawModification rawMod = iterator.next();
      final RawAttribute rawAttr = rawMod.getAttribute();
      final String attrName = toLowerCase(rawAttr.getAttributeType().trim());
      if (attributesToRemove.contains(attrName))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("AttributeCleanupPlugin removing '%s'",
              rawAttr.getAttributeType());
        }
        iterator.remove();
      }
    }
    modifyOperation.setRawModifications(rawMods);
  }



  /**
   * Map the incoming attributes to the local ones.
   *
   * @param addOperation
   *          Current ADD operation.
   */
  private void processInboundRename(final PreParseAddOperation addOperation)
  {
    final List<RawAttribute> inAttrs = new LinkedList<>(addOperation.getRawAttributes());
    final ListIterator<RawAttribute> iterator = inAttrs.listIterator();
    while (iterator.hasNext())
    {
      final RawAttribute rawAttr = iterator.next();
      final String fromName = toLowerCase(rawAttr.getAttributeType().trim());
      final String toName = attributesToRename.get(fromName);
      if (toName != null)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("AttributeCleanupPlugin renaming '%s' to '%s'",
              rawAttr.getAttributeType(), toName);
        }
        rawAttr.setAttributeType(toName);
      }
    }
    addOperation.setRawAttributes(inAttrs);
  }



  /**
   * Rename the attributes in the incoming MODIFY request to names that exist in
   * the local schema as defined in the configuration.
   *
   * @param modifyOperation
   *          Current MODIFY operation.
   */
  private void processInboundRename(
      final PreParseModifyOperation modifyOperation)
  {
    final List<RawModification> rawMods = new LinkedList<>(modifyOperation.getRawModifications());
    final ListIterator<RawModification> iterator = rawMods.listIterator();
    while (iterator.hasNext())
    {
      final RawModification rawMod = iterator.next();
      final RawAttribute rawAttr = rawMod.getAttribute();
      final String fromName = toLowerCase(rawAttr.getAttributeType().trim());
      final String toName = attributesToRename.get(fromName);
      if (toName != null)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("AttributeCleanupPlugin renaming '%s' to '%s'",
              rawAttr.getAttributeType(), toName);
        }
        rawAttr.setAttributeType(toName);
      }
    }
    modifyOperation.setRawModifications(rawMods);
  }
}
