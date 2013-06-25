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
 *      Copyright 2011 profiq s.r.o.
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.plugins;



import static org.opends.messages.PluginMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeCleanupPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
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

  /**
   * Plugin configuration.
   */
  private AttributeCleanupPluginCfg config;

  /**
   * Debug tracer.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  /**
   * A table of attributes to be renamed.
   */
  private Map<String, String> attributesToRename;

  /**
   * The set of attributes to be removed.
   */
  private Set<String> attributesToRemove;

  /**
   * This lock prevents concurrent updates to the configuration while operations
   * are being processed.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReadLock sharedLock = lock.readLock();
  private final WriteLock exclusiveLock = lock.writeLock();



  /**
   * Default constructor.
   */
  public AttributeCleanupPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      final AttributeCleanupPluginCfg config)
  {
    exclusiveLock.lock();
    try
    {
      /* Apply the change, as at this point is has been validated. */
      this.config = config;

      attributesToRename = new HashMap<String, String>();
      for (final String mapping : config.getRenameInboundAttributes())
      {
        final int colonPos = mapping.lastIndexOf(":");
        final String fromAttr = mapping.substring(0, colonPos).trim();
        final String toAttr = mapping.substring(colonPos + 1).trim();
        attributesToRename.put(toLowerCase(fromAttr), toLowerCase(toAttr));
      }

      attributesToRemove = new HashSet<String>();
      for (final String attr : config.getRemoveInboundAttributes())
      {
        attributesToRemove.add(toLowerCase(attr.trim()));
      }

      /* Update was successful, no restart required. */
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }
    finally
    {
      exclusiveLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse doPreParse(
      final PreParseAddOperation addOperation)
  {
    sharedLock.lock();
    try
    {
      /*
       * First strip the listed attributes, then rename the ones that remain.
       */
      processInboundRemove(addOperation);
      processInboundRename(addOperation);

      return PluginResult.PreParse.continueOperationProcessing();
    }
    finally
    {
      sharedLock.unlock();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse doPreParse(
      final PreParseModifyOperation modifyOperation)
  {
    sharedLock.lock();
    try
    {
      /*
       * First strip the listed attributes, then rename the ones that remain.
       */
      processInboundRemove(modifyOperation);
      processInboundRename(modifyOperation);

      /*
       * If the MODIFY request has been stripped of ALL modifications, stop the
       * processing and return SUCCESS to the client.
       */
      if (modifyOperation.getRawModifications().isEmpty())
      {
        if (debugEnabled())
        {
          TRACER.debugVerbose("The AttributeCleanupPlugin has eliminated all "
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



  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(final Set<PluginType> pluginTypes,
      final AttributeCleanupPluginCfg configuration) throws ConfigException,
      InitializationException
  {
    /*
     * The plugin should be invoked only for pre-parse ADD and MODIFY
     * operations.
     */
    for (final PluginType t : pluginTypes)
    {
      switch (t)
      {
      case PRE_PARSE_ADD:
        break;
      case PRE_PARSE_MODIFY:
        break;
      default:
        final Message message = ERR_PLUGIN_ATTR_CLEANUP_INITIALIZE_PLUGIN
            .get(String.valueOf(t));
        throw new ConfigException(message);
      }
    }

    /* Verify the current configuration. */
    final List<Message> messages = new LinkedList<Message>();
    if (!isConfigurationChangeAcceptable(configuration, messages))
    {
      throw new ConfigException(messages.get(0));
    }

    /* Register change listeners. */
    configuration.addAttributeCleanupChangeListener(this);

    /* Save the configuration. */
    applyConfigurationChange(configuration);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(final PluginCfg configuration,
      final List<Message> unacceptableReasons)
  {
    final AttributeCleanupPluginCfg cfg =
      (AttributeCleanupPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      final AttributeCleanupPluginCfg config, final List<Message> messages)
  {
    /*
     * The admin framework will ensure that there are no duplicate attributes to
     * be removed.
     */
    boolean isValid = true;

    /*
     * Verify that there are no duplicate mappings and that attributes are
     * renamed to valid attribute types.
     */
    final Set<String> fromAttrs = new HashSet<String>();
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
      final String toAttrType = (semicolonPos < 0)
          && (semicolonPos < (toAttr.length() - 1)) ? toAttr : toAttr
          .substring(semicolonPos + 1);

      if (DirectoryServer.getAttributeType(toLowerCase(toAttrType)) == null)
      {
        messages.add(ERR_PLUGIN_ATTR_CLEANUP_ATTRIBUTE_MISSING.get(toAttr));
        isValid = false;
      }

      /*
       * Check for duplicates.
       */
      final String nfromAttr = toLowerCase(fromAttr);
      if (fromAttrs.contains(nfromAttr))
      {
        messages.add(ERR_PLUGIN_ATTR_CLEANUP_DUPLICATE_VALUE.get(fromAttr));
        isValid = false;
      }
      else
      {
        fromAttrs.add(nfromAttr);
      }

      /*
       * Check that attribute does not map to itself.
       */
      if (nfromAttr.equals(toLowerCase(toAttr)))
      {
        messages
            .add(ERR_PLUGIN_ATTR_CLEANUP_EQUAL_VALUES.get(fromAttr, toAttr));
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
    final List<RawAttribute> inAttrs = new LinkedList<RawAttribute>(
        addOperation.getRawAttributes());
    final ListIterator<RawAttribute> iterator = inAttrs.listIterator();
    while (iterator.hasNext())
    {
      final RawAttribute rawAttr = iterator.next();
      final String attrName = toLowerCase(rawAttr.getAttributeType().trim());
      if (attributesToRemove.contains(attrName))
      {
        if (debugEnabled())
        {
          TRACER.debugVerbose("AttributeCleanupPlugin removing '%s'",
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
    final List<RawModification> rawMods = new LinkedList<RawModification>(
        modifyOperation.getRawModifications());
    final ListIterator<RawModification> iterator = rawMods.listIterator();
    while (iterator.hasNext())
    {
      final RawModification rawMod = iterator.next();
      final RawAttribute rawAttr = rawMod.getAttribute();
      final String attrName = toLowerCase(rawAttr.getAttributeType().trim());
      if (attributesToRemove.contains(attrName))
      {
        if (debugEnabled())
        {
          TRACER.debugVerbose("AttributeCleanupPlugin removing '%s'",
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
    final List<RawAttribute> inAttrs = new LinkedList<RawAttribute>(
        addOperation.getRawAttributes());
    final ListIterator<RawAttribute> iterator = inAttrs.listIterator();
    while (iterator.hasNext())
    {
      final RawAttribute rawAttr = iterator.next();
      final String fromName = toLowerCase(rawAttr.getAttributeType().trim());
      final String toName = attributesToRename.get(fromName);
      if (toName != null)
      {
        if (debugEnabled())
        {
          TRACER.debugVerbose("AttributeCleanupPlugin renaming '%s' to '%s'",
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
    final List<RawModification> rawMods = new LinkedList<RawModification>(
        modifyOperation.getRawModifications());
    final ListIterator<RawModification> iterator = rawMods.listIterator();
    while (iterator.hasNext())
    {
      final RawModification rawMod = iterator.next();
      final RawAttribute rawAttr = rawMod.getAttribute();
      final String fromName = toLowerCase(rawAttr.getAttributeType().trim());
      final String toName = attributesToRename.get(fromName);
      if (toName != null)
      {
        if (debugEnabled())
        {
          TRACER.debugVerbose("AttributeCleanupPlugin renaming '%s' to '%s'",
              rawAttr.getAttributeType(), toName);
        }
        rawAttr.setAttributeType(toName);
      }
    }
    modifyOperation.setRawModifications(rawMods);
  }
}
