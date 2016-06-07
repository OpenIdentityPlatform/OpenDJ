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
package org.opends.server.plugins;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.messages.PluginMessages.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn;
import org.forgerock.opendj.server.config.server.EntryUUIDPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.operation.PreOperationAddOperation;

/**
 * This class implements a Directory Server plugin that will add the entryUUID
 * attribute to an entry whenever it is added or imported as per RFC 4530.  For
 * entries added over LDAP, the entryUUID will be based on a semi-random UUID
 * (which is still guaranteed to be unique).  For entries imported from LDIF,
 * the UUID will be constructed from the entry DN using a repeatable algorithm.
 * This will ensure that LDIF files imported in parallel across multiple systems
 * will have identical entryUUID values.
 */
public final class EntryUUIDPlugin
       extends DirectoryServerPlugin<EntryUUIDPluginCfg>
       implements ConfigurationChangeListener<EntryUUIDPluginCfg>
{
  /** The attribute type for the "entryUUID" attribute. */
  private static final AttributeType entryUUIDType = getEntryUUIDAttributeType();
  /** The current configuration for this plugin. */
  private EntryUUIDPluginCfg currentConfig;

  /** Mandatory default constructor of this Directory Server plugin. */
  public EntryUUIDPlugin()
  {
    super();
  }

  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     EntryUUIDPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addEntryUUIDChangeListener(this);

    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
        case PRE_OPERATION_ADD:
          // These are acceptable.
          break;

        default:
          throw new ConfigException(ERR_PLUGIN_ENTRYUUID_INVALID_PLUGIN_TYPE.get(t));
      }
    }
  }

  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeEntryUUIDChangeListener(this);
  }

  @Override
  public final PluginResult.ImportLDIF
               doLDIFImport(LDIFImportConfig importConfig, Entry entry)
  {
    // See if the entry being imported already contains an entryUUID attribute.
    // If so, then leave it alone.
    List<Attribute> uuidList = entry.getAttribute(entryUUIDType);
    if (!uuidList.isEmpty())
    {
      return PluginResult.ImportLDIF.continueEntryProcessing();
    }

    // Construct a new UUID.  In order to make sure that UUIDs are consistent
    // when the same LDIF is generated on multiple servers, we'll base the UUID
    // on the byte representation of the normalized DN.
    entry.putAttribute(entryUUIDType, toAttributeList(entry.getName().toUUID()));

    // We shouldn't ever need to return a non-success result.
    return PluginResult.ImportLDIF.continueEntryProcessing();
  }

  @Override
  public final PluginResult.PreOperation
               doPreOperation(PreOperationAddOperation addOperation)
  {
    // See if the entry being added already contains an entryUUID attribute.
    // It shouldn't, since it's NO-USER-MODIFICATION, but if it does then leave
    // it alone.
    List<Attribute> uuidList = addOperation.getOperationalAttributes().get(entryUUIDType);
    if (uuidList != null)
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    // Construct a new random UUID.
    addOperation.setAttribute(entryUUIDType, toAttributeList(UUID.randomUUID()));
    return PluginResult.PreOperation.continueOperationProcessing();
  }

  private List<Attribute> toAttributeList(UUID uuid)
  {
    return Attributes.createAsList(entryUUIDType, uuid.toString());
  }

  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    EntryUUIDPluginCfg cfg = (EntryUUIDPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      EntryUUIDPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only LDIF import and
    // pre-operation add.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case LDIFIMPORT:
        case PREOPERATIONADD:
          // These are acceptable.
          break;

        default:
          unacceptableReasons.add(ERR_PLUGIN_ENTRYUUID_INVALID_PLUGIN_TYPE.get(pluginType));
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 EntryUUIDPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
