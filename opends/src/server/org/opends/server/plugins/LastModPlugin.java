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
import java.util.Set;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;

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
public final class LastModPlugin
       extends DirectoryServerPlugin
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.plugins.LastModPlugin";



  // The attribute type for the "createTimestamp" attribute.
  private final AttributeType createTimestampType;

  // The attribute type for the "creatorsName" attribute.
  private final AttributeType creatorsNameType;

  // The attribute type for the "modifiersName" attribute.
  private final AttributeType modifiersNameType;

  // The attribute type for the "modifyTimestamp" attribute.
  private final AttributeType modifyTimestampType;



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
                                     ConfigEntry configEntry)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "initializePlugin",
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
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
       doPreOperation(PreOperationAddOperation addOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(addOperation));


    // Create the attribute list for the creatorsName attribute, if appropriate.
    DN creatorDN = addOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (creatorDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the creatorsName attribute.
      nameValues.add(new AttributeValue(creatorsNameType,
                                        ByteStringFactory.create()));
    }
    else
    {
      nameValues.add(new AttributeValue(creatorsNameType,
           ByteStringFactory.create(creatorDN.toString())));
    }
    Attribute nameAttr = new Attribute(creatorsNameType, OP_ATTR_CREATORS_NAME,
                                       nameValues);
    ArrayList<Attribute> nameList = new ArrayList<Attribute>(1);
    nameList.add(nameAttr);
    addOperation.setAttribute(creatorsNameType, nameList);


    //  Create the attribute list for the createTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(createTimestampType,
                                      ByteStringFactory.create(getUTCTime())));

    Attribute timeAttr = new Attribute(createTimestampType,
                                       OP_ATTR_CREATE_TIMESTAMP, timeValues);
    ArrayList<Attribute> timeList = new ArrayList<Attribute>(1);
    timeList.add(timeAttr);
    addOperation.setAttribute(createTimestampType, timeList);


    // We shouldn't ever need to return a non-success result.
    return PreOperationPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyOperation));


    // Create the modifiersName attribute.
    DN modifierDN = modifyOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the modifiersName attribute.
      nameValues.add(new AttributeValue(modifiersNameType,
                                        ByteStringFactory.create()));
    }
    else
    {
      nameValues.add(new AttributeValue(modifiersNameType,
           ByteStringFactory.create(modifierDN.toString())));
    }
    Attribute nameAttr = new Attribute(modifiersNameType,
                                       OP_ATTR_MODIFIERS_NAME, nameValues);
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       nameAttr));
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "doPreOperation", de);

      // This should never happen.
      modifyOperation.setResultCode(DirectoryConfig.getServerErrorResultCode());
      modifyOperation.appendErrorMessage(de.getErrorMessage());
      return new PreOperationPluginResult(false, false, true);
    }


    //  Create the modifyTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(modifyTimestampType,
                                      ByteStringFactory.create(getUTCTime())));

    Attribute timeAttr = new Attribute(modifyTimestampType,
                                       OP_ATTR_MODIFY_TIMESTAMP, timeValues);
    try
    {
      modifyOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       timeAttr));
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "doPreOperation", de);

      // This should never happen.
      modifyOperation.setResultCode(DirectoryConfig.getServerErrorResultCode());
      modifyOperation.appendErrorMessage(de.getErrorMessage());
      return new PreOperationPluginResult(false, false, true);
    }


    // We shouldn't ever need to return a non-success result.
    return PreOperationPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreOperationPluginResult
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreOperation",
                      String.valueOf(modifyDNOperation));


    // Create the modifiersName attribute.
    DN modifierDN = modifyDNOperation.getAuthorizationDN();
    LinkedHashSet<AttributeValue> nameValues =
         new LinkedHashSet<AttributeValue>(1);
    if (modifierDN == null)
    {
      // This must mean that the operation was performed anonymously.  Even so,
      // we still need to update the modifiersName attribute.
      nameValues.add(new AttributeValue(modifiersNameType,
                                        ByteStringFactory.create()));
    }
    else
    {
      nameValues.add(new AttributeValue(modifiersNameType,
           ByteStringFactory.create(modifierDN.toString())));
    }
    Attribute nameAttr = new Attribute(modifiersNameType,
                                       OP_ATTR_MODIFIERS_NAME, nameValues);
    modifyDNOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       nameAttr));


    //  Create the modifyTimestamp attribute.
    LinkedHashSet<AttributeValue> timeValues =
         new LinkedHashSet<AttributeValue>(1);
    timeValues.add(new AttributeValue(modifyTimestampType,
                                      ByteStringFactory.create(getUTCTime())));

    Attribute timeAttr = new Attribute(modifyTimestampType,
                                       OP_ATTR_MODIFY_TIMESTAMP, timeValues);
    modifyDNOperation.addModification(new Modification(ModificationType.REPLACE,
                                                       timeAttr));


    // We shouldn't ever need to return a non-success result.
    return PreOperationPluginResult.SUCCESS;
  }
}

