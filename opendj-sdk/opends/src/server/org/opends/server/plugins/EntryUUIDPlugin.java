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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.LDIFPluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.operation.PreOperationAddOperation;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;



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
       extends DirectoryServerPlugin
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.plugins.EntryUUIDPlugin";



  /**
   * The name of the entryUUID attribute type.
   */
  private static final String ENTRYUUID = "entryuuid";



  // The attribute type for the "entryUUID" attribute.
  private final AttributeType entryUUIDType;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public EntryUUIDPlugin()
  {
    super();

    assert debugConstructor(CLASS_NAME);


    // Get the entryUUID attribute type.  This needs to be done in the
    // constructor in order to make the associated variables "final".
    AttributeType at = DirectoryConfig.getAttributeType(ENTRYUUID,
                                                        false);
    if (at == null)
    {
      at = new AttributeType(ENTRYUUID, Collections.singleton(ENTRYUUID),
                             ENTRYUUID, null, null,
                             DirectoryConfig.getDefaultAttributeSyntax(),
                             AttributeUsage.DIRECTORY_OPERATION, false, true,
                             false, true);
    }

    entryUUIDType = at;
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
        case LDIF_IMPORT:
        case PRE_OPERATION_ADD:
          // These are acceptable.
          break;


        default:
          int msgID = MSGID_PLUGIN_ENTRYUUID_INVALID_PLUGIN_TYPE;
          String message = getMessage(msgID, t.toString());
          throw new ConfigException(msgID, message);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final LDIFPluginResult doLDIFImport(LDIFImportConfig importConfig,
                                             Entry entry)
  {
    assert debugEnter(CLASS_NAME, "doLDIFImport",
                      String.valueOf(importConfig), String.valueOf(entry));


    // See if the entry being imported already contains an entryUUID attribute.
    // If so, then leave it alone.
    List<Attribute> uuidList = entry.getAttribute(entryUUIDType);
    if (uuidList != null)
    {
      return LDIFPluginResult.SUCCESS;
    }


    // Construct a new UUID.  In order to make sure that UUIDs are consistent
    // when the same LDIF is generated on multiple servers, we'll base the UUID
    // on the byte representation of the normalized DN.
    byte[] dnBytes = getBytes(entry.getDN().toNormalizedString());
    UUID uuid = UUID.nameUUIDFromBytes(dnBytes);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryUUIDType,
                                  ByteStringFactory.create(uuid.toString())));

    uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = new Attribute(entryUUIDType, "entryUUID", values);
    uuidList.add(uuidAttr);
    entry.putAttribute(entryUUIDType, uuidList);


    // We shouldn't ever need to return a non-success result.
    return LDIFPluginResult.SUCCESS;
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


    // See if the entry being added already contains an entryUUID attribute.
    // It shouldn't, since it's NO-USER-MODIFICATION, but if it does then leave
    // it alone.
    Map<AttributeType,List<Attribute>> operationalAttributes =
         addOperation.getOperationalAttributes();
    List<Attribute> uuidList = operationalAttributes.get(entryUUIDType);
    if (uuidList != null)
    {
      return PreOperationPluginResult.SUCCESS;
    }


    // Construct a new random UUID.
    UUID uuid = UUID.randomUUID();

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryUUIDType,
                                  ByteStringFactory.create(uuid.toString())));

    uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = new Attribute(entryUUIDType, "entryUUID", values);
    uuidList.add(uuidAttr);


    // Add the attribute to the entry and return.
    addOperation.setAttribute(entryUUIDType, uuidList);
    return PreOperationPluginResult.SUCCESS;
  }
}

