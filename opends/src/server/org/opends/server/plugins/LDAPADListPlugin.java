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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;


import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.operation.PreParseSearchOperation;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;
import static org.opends.server.loggers.debug.DebugLogger.debugWarning;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This pre-parse plugin modifies the operation to allow an object class
 * identifier to be specified in attributes lists, such as in Search requests,
 * to request the return all attributes belonging to an object class as per the
 * specification in RFC 4529.  The "@" character is used to distinguish an
 * object class identifier from an attribute descriptions.
 */
public final class LDAPADListPlugin
       extends DirectoryServerPlugin
{



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public LDAPADListPlugin()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     ConfigEntry configEntry)
         throws ConfigException
  {
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
    DirectoryConfig.registerSupportedFeature(OID_LDAP_ADLIST_FEATURE);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PreParsePluginResult
       doPreParse(PreParseSearchOperation searchOperation)
  {
    // Iterate through the requested attributes to see if any of them start with
    // an "@" symbol.  If not, then we don't need to do anything.  If so, then
    // keep track of them.
    LinkedHashSet<String> attributes = searchOperation.getAttributes();
    boolean foundOC = false;
    for (String attrName : attributes)
    {
      if (attrName.startsWith("@"))
      {
        foundOC = true;
        break;
      }
    }

    if (foundOC)
    {
      LinkedHashSet<String> newAttrs = new LinkedHashSet<String>();
      for (String attrName : attributes)
      {
        if (attrName.startsWith("@"))
        {
          String lowerName = toLowerCase(attrName.substring(1));
          ObjectClass oc = DirectoryConfig.getObjectClass(lowerName, false);
          if (oc == null)
          {
            if (debugEnabled())
            {
              debugWarning("Cannot replace unknown objectclass %s", lowerName);
            }
          }
          else
          {
            if (debugEnabled())
            {
              debugInfo("Replacing objectclass %s", lowerName);
            }

            for (AttributeType at : oc.getRequiredAttributeChain())
            {
              newAttrs.add(at.getNameOrOID());
            }

            for (AttributeType at : oc.getOptionalAttributeChain())
            {
              newAttrs.add(at.getNameOrOID());
            }
          }
        }
        else
        {
          newAttrs.add(attrName);
        }
      }

      searchOperation.setAttributes(newAttrs);
    }


    return PreParsePluginResult.SUCCESS;
  }
}

