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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.plugins;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPAttributeDescriptionListPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.operation.PreParseSearchOperation;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This pre-parse plugin modifies the operation to allow an object class
 * identifier to be specified in attributes lists, such as in Search requests,
 * to request the return all attributes belonging to an object class as per the
 * specification in RFC 4529.  The "@" character is used to distinguish an
 * object class identifier from an attribute descriptions.
 */
public final class LDAPADListPlugin
       extends DirectoryServerPlugin<LDAPAttributeDescriptionListPluginCfg>
       implements ConfigurationChangeListener<
                       LDAPAttributeDescriptionListPluginCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * Filters the set of attributes provided in a search request or pre- / post-
   * read controls according to RFC 4529. More specifically, this method
   * iterates through the requested attributes to see if any of them reference
   * an object class, as indicated by a "@" prefix, and substitutes the object
   * class reference with the attribute types contained in the object class, as
   * well as any of the attribute types contained in any superior object
   * classes.
   *
   * @param attributes
   *          The attribute list to be normalized.
   * @return The normalized attribute list.
   */
  public static Set<String> normalizedObjectClasses(Set<String> attributes)
  {
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
      final LinkedHashSet<String> newAttrs = new LinkedHashSet<>();
      for (final String attrName : attributes)
      {
        if (attrName.startsWith("@"))
        {
          final String ocName = attrName.substring(1);
          final ObjectClass oc = getSchema().getObjectClass(ocName);
          if (oc.isPlaceHolder())
          {
            logger.trace("Cannot replace unknown objectclass %s", ocName);
          }
          else
          {
            logger.trace("Replacing objectclass %s", ocName);

            for (final AttributeType at : oc.getRequiredAttributes())
            {
              newAttrs.add(at.getNameOrOID());
            }

            for (final AttributeType at : oc.getOptionalAttributes())
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
      attributes = newAttrs;
    }

    return attributes;
  }



  /** The current configuration for this plugin. */
  private LDAPAttributeDescriptionListPluginCfg currentConfig;



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



  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                         LDAPAttributeDescriptionListPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addLDAPAttributeDescriptionListChangeListener(this);

    // The set of plugin types must contain only the pre-parse search element.
    if (pluginTypes.isEmpty())
    {
      throw new ConfigException(ERR_PLUGIN_ADLIST_NO_PLUGIN_TYPES.get(configuration.dn()));
    }
    else
    {
      for (PluginType t : pluginTypes)
      {
        if (t != PluginType.PRE_PARSE_SEARCH)
        {
          throw new ConfigException(ERR_PLUGIN_ADLIST_INVALID_PLUGIN_TYPE.get(configuration.dn(), t));
        }
      }
    }


    // Register the appropriate supported feature with the Directory Server.
    DirectoryConfig.registerSupportedFeature(OID_LDAP_ADLIST_FEATURE);
  }



  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeLDAPAttributeDescriptionListChangeListener(this);
  }



  @Override
  public final PluginResult.PreParse doPreParse(
      PreParseSearchOperation searchOperation)
  {
    searchOperation.setAttributes(normalizedObjectClasses(searchOperation
        .getAttributes()));
    return PluginResult.PreParse.continueOperationProcessing();
  }



  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LDAPAttributeDescriptionListPluginCfg cfg =
         (LDAPAttributeDescriptionListPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  @Override
  public boolean isConfigurationChangeAcceptable(
                      LDAPAttributeDescriptionListPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only pre-parse search.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case PREPARSESEARCH:
          // This is acceptable.
          break;


        default:
          unacceptableReasons.add(ERR_PLUGIN_ADLIST_INVALID_PLUGIN_TYPE.get(configuration.dn(), pluginType));
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }



  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 LDAPAttributeDescriptionListPluginCfg
                                      configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}

