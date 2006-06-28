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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.TimeThread.*;



/**
 * This class implements a Directory Server plugin that will add the
 * creatorsName and createTimestamp attributes to an entry whenever it is added
 * to the server, and will add the modifiersName and modifyTimestamp attributes
 * whenever the entry is modified or renamed.
 */
public class LastModPlugin
       extends DirectoryServerPlugin
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.plugins.LastModPlugin";



  // The attribute type for the "createTimestamp" attribute.
  private AttributeType createTimestampType;

  // The attribute type for the "creatorsName" attribute.
  private AttributeType creatorsNameType;

  // The attribute type for the "modifiersName" attribute.
  private AttributeType modifiersNameType;

  // The attribute type for the "modifyTimestamp" attribute.
  private AttributeType modifyTimestampType;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public LastModPlugin()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Performs any initialization necessary for this plugin.  This will be called
   * as soon as the plugin has been loaded and before it is registered with the
   * server.
   *
   * @param  directoryServer  The reference to the Directory Server instance in
   *                          which the plugin will be running.
   * @param  pluginTypes      The set of plugin types that indicate the ways in
   *                          which this plugin will be invoked.
   * @param  configEntry      The entry containing the configuration information
   *                          for this plugin.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           configuration for this plugin.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the plugin that is not related to the
   *                                   server configuration.
   */
  public void initializePlugin(DirectoryServer directoryServer,
                               Set<PluginType> pluginTypes,
                               ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializePlugin",
                      String.valueOf(directoryServer),
                      String.valueOf(pluginTypes),
                      String.valueOf(configEntry));


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
          int msgID = MSGID_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE;
          String message = getMessage(msgID, t.toString());
          throw new ConfigException(msgID, message);
      }
    }


    // Get the attribute types for the attributes that we will use.
    createTimestampType =
         DirectoryServer.getAttributeType(OP_ATTR_CREATE_TIMESTAMP_LC);
    if (createTimestampType == null)
    {
      createTimestampType =
           DirectoryServer.getDefaultAttributeType(OP_ATTR_CREATE_TIMESTAMP);
    }

    creatorsNameType =
         DirectoryServer.getAttributeType(OP_ATTR_CREATORS_NAME_LC);
    if (creatorsNameType == null)
    {
      creatorsNameType =
           DirectoryServer.getDefaultAttributeType(OP_ATTR_CREATORS_NAME);
    }

    modifiersNameType =
         DirectoryServer.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC);
    if (modifiersNameType == null)
    {
      modifiersNameType =
           DirectoryServer.getDefaultAttributeType(OP_ATTR_MODIFIERS_NAME);
    }

    modifyTimestampType =
         DirectoryServer.getAttributeType(OP_ATTR_MODIFY_TIMESTAMP_LC);
    if (modifyTimestampType == null)
    {
      modifyTimestampType =
           DirectoryServer.getDefaultAttributeType(OP_ATTR_MODIFY_TIMESTAMP);
    }
  }



  /**
   * Performs any necessary processing that should be done just before the
   * Directory Server performs the core processing for an add operation.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(AddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(addOperation));


    // Get the set of operational attributes for the add operation.
    Map<AttributeType,List<Attribute>> operationalAttrs =
         addOperation.getOperationalAttributes();


    // Create the attribute list for the creatorsName attribute, if appropriate.
    DN creatorDN = addOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (creatorDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the creatorsName attribute.
      nameValues.add(new AttributeValue(creatorsNameType,
                                        new ASN1OctetString()));
    }
    else
    {
      nameValues.add(new AttributeValue(creatorsNameType,
           new ASN1OctetString(creatorDN.toString())));
    }
    Attribute nameAttr = new Attribute(creatorsNameType, OP_ATTR_CREATORS_NAME,
                                       nameValues);
    ArrayList<Attribute> nameList = new ArrayList<Attribute>(1);
    nameList.add(nameAttr);
    operationalAttrs.put(creatorsNameType, nameList);


    //  Create the attribute list for the createTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(createTimestampType,
                                      new ASN1OctetString(getUTCTime())));

    Attribute timeAttr = new Attribute(createTimestampType,
                                       OP_ATTR_CREATE_TIMESTAMP, timeValues);
    ArrayList<Attribute> timeList = new ArrayList<Attribute>(1);
    timeList.add(timeAttr);
    operationalAttrs.put(createTimestampType, timeList);


    // We shouldn't ever need to return a non-success result.
    return new PreOperationPluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before the
   * Directory Server performs the core processing for a modify operation.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(ModifyOperation
                                                      modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyOperation));


    // Get the set of modifications for this operation.  Also get the modified
    // entry.  We need to make sure that both get updated appropriately.
    List<Modification> mods = modifyOperation.getModifications();
    Entry modifiedEntry = modifyOperation.getModifiedEntry();


    // Create the modifiersName attribute.
    DN modifierDN = modifyOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the modifiersName attribute.
      nameValues.add(new AttributeValue(modifiersNameType,
                                        new ASN1OctetString()));
    }
    else
    {
      nameValues.add(new AttributeValue(modifiersNameType,
           new ASN1OctetString(modifierDN.toString())));
    }
    Attribute nameAttr = new Attribute(modifiersNameType,
                                       OP_ATTR_MODIFIERS_NAME, nameValues);
    mods.add(new Modification(ModificationType.REPLACE, nameAttr));

    ArrayList<Attribute> nameList = new ArrayList<Attribute>(1);
    nameList.add(nameAttr);
    modifiedEntry.putAttribute(modifiersNameType, nameList);


    //  Create the modifyTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(modifyTimestampType,
                                      new ASN1OctetString(getUTCTime())));

    Attribute timeAttr = new Attribute(modifyTimestampType,
                                       OP_ATTR_MODIFY_TIMESTAMP, timeValues);
    mods.add(new Modification(ModificationType.REPLACE, timeAttr));

    ArrayList<Attribute> timeList = new ArrayList<Attribute>(1);
    timeList.add(timeAttr);
    modifiedEntry.putAttribute(modifyTimestampType, timeList);


    // We shouldn't ever need to return a non-success result.
    return new PreOperationPluginResult();
  }



  /**
   * Performs any necessary processing that should be done just before the
   * Directory Server performs the core processing for a modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation to be processed.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreOperationPluginResult doPreOperation(ModifyDNOperation
                                                      modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyDNOperation));


    // Get the set of modifications for this operation.
    List<Modification> mods = modifyDNOperation.getModifications();


    // Create the modifiersName attribute.
    DN modifierDN = modifyDNOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the modifiersName attribute.
      nameValues.add(new AttributeValue(modifiersNameType,
                                        new ASN1OctetString()));
    }
    else
    {
      nameValues.add(new AttributeValue(modifiersNameType,
           new ASN1OctetString(modifierDN.toString())));
    }
    Attribute nameAttr = new Attribute(modifiersNameType,
                                       OP_ATTR_MODIFIERS_NAME, nameValues);
    mods.add(new Modification(ModificationType.REPLACE, nameAttr));


    //  Create the modifyTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(modifyTimestampType,
                                      new ASN1OctetString(getUTCTime())));

    Attribute timeAttr = new Attribute(modifyTimestampType,
                                       OP_ATTR_MODIFY_TIMESTAMP, timeValues);
    mods.add(new Modification(ModificationType.REPLACE, timeAttr));


    // We shouldn't ever need to return a non-success result.
    return new PreOperationPluginResult();
  }
}

