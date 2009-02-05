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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.LastModPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.TimeThread.*;


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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type for the "createTimestamp" attribute.
  private final AttributeType createTimestampType;

  // The attribute type for the "creatorsName" attribute.
  private final AttributeType creatorsNameType;

  // The attribute type for the "modifiersName" attribute.
  private final AttributeType modifiersNameType;

  // The attribute type for the "modifyTimestamp" attribute.
  private final AttributeType modifyTimestampType;

  // The current configuration for this plugin.
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


    // Get the attribute types for the attributes that we will use.  This needs
    // to be done in the constructor in order to make the associated variables
    // "final".
    createTimestampType =
         DirectoryConfig.getAttributeType(OP_ATTR_CREATE_TIMESTAMP_LC, true);
    creatorsNameType =
         DirectoryConfig.getAttributeType(OP_ATTR_CREATORS_NAME_LC, true);
    modifiersNameType =
         DirectoryConfig.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC, true);
    modifyTimestampType =
         DirectoryConfig.getAttributeType(OP_ATTR_MODIFY_TIMESTAMP_LC, true);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
          Message message =
              ERR_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {
    currentConfig.removeLastModChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
               doPreOperation(PreOperationAddOperation addOperation)
  {
    // Create the attribute list for the creatorsName attribute, if appropriate.
    AttributeBuilder builder = new AttributeBuilder(creatorsNameType,
        OP_ATTR_CREATORS_NAME);
    DN creatorDN = addOperation.getAuthorizationDN();
    if (creatorDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the creatorsName attribute.
      builder.add(AttributeValues.create(creatorsNameType,
          ByteString.empty()));
    }
    else
    {
      builder.add(AttributeValues.create(creatorsNameType,
          ByteString.valueOf(creatorDN.toString())));
    }
    Attribute nameAttr = builder.toAttribute();
    ArrayList<Attribute> nameList = new ArrayList<Attribute>(1);
    nameList.add(nameAttr);
    addOperation.setAttribute(creatorsNameType, nameList);


    //  Create the attribute list for the createTimestamp attribute.
    Attribute timeAttr = Attributes.create(createTimestampType,
        OP_ATTR_CREATE_TIMESTAMP,
        AttributeValues.create(createTimestampType,
            ByteString.valueOf(getGMTTime())));
    ArrayList<Attribute> timeList = new ArrayList<Attribute>(1);
    timeList.add(timeAttr);
    addOperation.setAttribute(createTimestampType, timeList);


    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    // Create the modifiersName attribute.
    AttributeBuilder builder = new AttributeBuilder(modifiersNameType,
        OP_ATTR_MODIFIERS_NAME);
    DN modifierDN = modifyOperation.getAuthorizationDN();
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the modifiersName attribute.
      builder.add(AttributeValues.create(modifiersNameType,
          ByteString.empty()));
    }
    else
    {
      builder.add(AttributeValues.create(modifiersNameType,
          ByteString.valueOf(modifierDN.toString())));
    }
    Attribute nameAttr = builder.toAttribute();
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       nameAttr, true));
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // This should never happen.
      return PluginResult.PreOperation.stopProcessing(
          DirectoryConfig.getServerErrorResultCode(), de.getMessageObject());
    }


    //  Create the modifyTimestamp attribute.
    Attribute timeAttr = Attributes.create(modifyTimestampType,
        OP_ATTR_MODIFY_TIMESTAMP,
        AttributeValues.create(modifyTimestampType,
            ByteString.valueOf(getGMTTime())));
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       timeAttr, true));
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // This should never happen.
      return PluginResult.PreOperation.stopProcessing(
          DirectoryConfig.getServerErrorResultCode(), de.getMessageObject());
    }


    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    // Create the modifiersName attribute.
    AttributeBuilder builder = new AttributeBuilder(modifiersNameType,
        OP_ATTR_MODIFIERS_NAME);
    DN modifierDN = modifyDNOperation.getAuthorizationDN();
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.
      // Even so, we still need to update the modifiersName attribute.
      builder.add(AttributeValues.create(modifiersNameType,
          ByteString.empty()));
    }
    else
    {
      builder.add(AttributeValues.create(modifiersNameType,
          ByteString.valueOf(modifierDN.toString())));
    }
    Attribute nameAttr = builder.toAttribute();
    modifyDNOperation.addModification(new Modification(
        ModificationType.REPLACE, nameAttr, true));


    // Create the modifyTimestamp attribute.
    Attribute timeAttr = Attributes.create(modifyTimestampType,
        OP_ATTR_MODIFY_TIMESTAMP,
        AttributeValues.create(modifyTimestampType,
            ByteString.valueOf(getGMTTime())));
    modifyDNOperation.addModification(new Modification(
        ModificationType.REPLACE, timeAttr, true));


    // We shouldn't ever need to return a non-success result.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    LastModPluginCfg cfg = (LastModPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(LastModPluginCfg configuration,
                      List<Message> unacceptableReasons)
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
          Message message = ERR_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE.get(
                  pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 LastModPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

