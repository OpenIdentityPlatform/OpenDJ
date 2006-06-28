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


import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.ObjectClass;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;


/**
 *  This pre-parse plugin modifies the operation to allow an object class
 * identifier to be specified in attributes lists, such as in Search requests,
 * to request the return all attributes belonging to an object class as per the
 * specification in RFC 4529.  The "@" character is used to distinguish an
 * object class identifier from an attribute descriptions.
 */
public class LDAPADListPlugin
       extends DirectoryServerPlugin
{
  private static final String CLASS_NAME =
      "org.opends.server.plugins.LDAPADListPlugin";



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public LDAPADListPlugin()
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
                      String.valueOf(pluginTypes), String.valueOf(configEntry));


    // The set of plugin types must contain only the pre-parse search element.
    if (pluginTypes.isEmpty())
    {
      int    msgID   = MSGID_PLUGIN_ADLIST_NO_PLUGIN_TYPES;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()));
      throw new ConfigException(msgID, message);
    }
    else
    {
      for (PluginType t : pluginTypes)
      {
        if (t != PluginType.PRE_PARSE_SEARCH)
        {
          int    msgID   = MSGID_PLUGIN_ADLIST_INVALID_PLUGIN_TYPE;
          String message = getMessage(msgID,
                                      String.valueOf(configEntry.getDN()),
                                      String.valueOf(t));
          throw new ConfigException(msgID, message);
        }
      }
    }


    // Register the appropriate supported feature with the Directory Server.
    DirectoryServer.registerSupportedFeature(OID_LDAP_ADLIST_FEATURE);
  }



  /**
   * Performs any necessary processing that should be done before the Directory
   * Server parses the elements of a search request.
   *
   * @param  searchOperation  The search operation that has been requested.
   *
   * @return  Information about the result of the plugin processing.
   */
  public PreParsePluginResult doPreParse(SearchOperation searchOperation)
  {
    assert debugEnter(CLASS_NAME, "doPreParseSearch",
          String.valueOf(searchOperation));

    LinkedHashSet<String> attributes = searchOperation.getAttributes();

    LinkedHashSet<String> objectClassList = new LinkedHashSet<String> ();

    // Create list of object classes that need to be replaced with attributes.
    for(String attribute : attributes)
    {
      // check if it starts with "@". If so add it to the list of object classes
      if(attribute.startsWith("@"))
      {
        objectClassList.add(attribute);
      }
    }

    // Iterate through list of object classes and replace with attributes.
    for (String objectClass : objectClassList)
    {
      // find object class and get list of attributes.
      ObjectClass objClass = DirectoryServer.getObjectClass(
          objectClass.substring(1, objectClass.length()));
      // remove the object class from the attribute list.
      attributes.remove(objectClass);

      if(objClass == null)
      {
        // object class not found.
        assert debugMessage(DebugLogCategory.PLUGIN, DebugLogSeverity.WARNING,
                        CLASS_NAME, "doPreSearch",
                        "Invalid object class: " + objectClass);
      } else
      {
        Set<AttributeType> requiredAttributes =
            objClass.getRequiredAttributeChain();
        Set<AttributeType> optionalAttributes =
            objClass.getOptionalAttributeChain();

        // remove attribute and replace with expanded list.
        assert debugMessage(DebugLogCategory.PLUGIN, DebugLogSeverity.INFO,
                            CLASS_NAME, "doPreParse",
                            "Replacing object class " +
                                 String.valueOf(objClass));
        for(AttributeType req : requiredAttributes)
        {
          attributes.add(req.getNameOrOID());
        }
        for(AttributeType opt : optionalAttributes)
        {
          attributes.add(opt.getNameOrOID());
        }
      }
    }

    return new PreParsePluginResult();
  }
}

