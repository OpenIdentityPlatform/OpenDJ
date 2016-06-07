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
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.TimeThread.*;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn;
import org.forgerock.opendj.server.config.server.LastModPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Modification;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

/**
 * This class implements a Directory Server plugin that will add the
 * creatorsName and createTimestamp attributes to an entry whenever it is added
 * to the server, and will add the modifiersName and modifyTimestamp attributes
 * whenever the entry is modified or renamed.
 */
public final class LastModPlugin
       extends DirectoryServerPlugin<LastModPluginCfg>
       implements ConfigurationChangeListener<LastModPluginCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration for this plugin. */
  private LastModPluginCfg currentConfig;


  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public LastModPlugin()
  {
    super();
  }



  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     LastModPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addLastModChangeListener(this);

    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
          // These are acceptable.
          break;

        default:
          throw new ConfigException(ERR_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE.get(t));
      }
    }
  }



  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeLastModChangeListener(this);
  }



  @Override
  public final PluginResult.PreOperation
               doPreOperation(PreOperationAddOperation addOperation)
  {
    // Create the attribute list for the creatorsName attribute, if appropriate.
    AttributeBuilder builder = new AttributeBuilder(getCreatorsNameAttributeType());
    DN creatorDN = addOperation.getAuthorizationDN();
    if (creatorDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the creatorsName attribute.
      builder.add(ByteString.empty());
    }
    else
    {
      builder.add(creatorDN.toString());
    }
    addOperation.setAttribute(getCreatorsNameAttributeType(), builder.toAttributeList());


    //  Create the attribute list for the createTimestamp attribute.
    List<Attribute> timeList = Attributes.createAsList(
        getCreateTimestampAttributeType(), OP_ATTR_CREATE_TIMESTAMP, getGMTTime());
    addOperation.setAttribute(getCreateTimestampAttributeType(), timeList);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  @Override
  public final PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    // Create the modifiersName attribute.
    AttributeBuilder builder = new AttributeBuilder(getModifiersNameAttributeType());
    DN modifierDN = modifyOperation.getAuthorizationDN();
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the modifiersName attribute.
      builder.add(ByteString.empty());
    }
    else
    {
      builder.add(modifierDN.toString());
    }
    Attribute nameAttr = builder.toAttribute();
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       nameAttr, true));
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      // This should never happen.
      return PluginResult.PreOperation.stopProcessing(
          DirectoryConfig.getServerErrorResultCode(), de.getMessageObject());
    }


    //  Create the modifyTimestamp attribute.
    Attribute timeAttr = Attributes.create(getModifyTimestampAttributeType(),
        OP_ATTR_MODIFY_TIMESTAMP, getGMTTime());
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       timeAttr, true));
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      // This should never happen.
      return PluginResult.PreOperation.stopProcessing(
          DirectoryConfig.getServerErrorResultCode(), de.getMessageObject());
    }


    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  @Override
  public final PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    // Create the modifiersName attribute.
    AttributeBuilder builder = new AttributeBuilder(getModifiersNameAttributeType());
    DN modifierDN = modifyDNOperation.getAuthorizationDN();
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the modifiersName attribute.
      builder.add(ByteString.empty());
    }
    else
    {
      builder.add(modifierDN.toString());
    }
    Attribute nameAttr = builder.toAttribute();
    modifyDNOperation.addModification(new Modification(
        ModificationType.REPLACE, nameAttr, true));


    // Create the modifyTimestamp attribute.
    Attribute timeAttr = Attributes.create(getModifyTimestampAttributeType(),
        OP_ATTR_MODIFY_TIMESTAMP, getGMTTime());
    modifyDNOperation.addModification(new Modification(
        ModificationType.REPLACE, timeAttr, true));


    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LastModPluginCfg cfg = (LastModPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  @Override
  public boolean isConfigurationChangeAcceptable(LastModPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only pre-operation add,
    // pre-operation modify, and pre-operation modify DN.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case PREOPERATIONADD:
        case PREOPERATIONMODIFY:
        case PREOPERATIONMODIFYDN:
          // These are acceptable.
          break;


        default:
          unacceptableReasons.add(ERR_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE.get(pluginType));
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }



  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 LastModPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}

