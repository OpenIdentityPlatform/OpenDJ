/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.plugins;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.LDAPAttributeDescriptionListPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.operation.PreParseSearchOperation;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.PluginMessages.*;

import static org.opends.server.types.DirectoryConfig.getObjectClass;
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
          final String lowerName = toLowerCase(attrName.substring(1));
          final ObjectClass oc = getObjectClass(lowerName, false);
          if (oc == null)
          {
            if (logger.isTraceEnabled())
            {
              logger.trace("Cannot replace unknown objectclass %s",
                                  lowerName);
            }
          }
          else
          {
            if (logger.isTraceEnabled())
            {
              logger.trace("Replacing objectclass %s", lowerName);
            }

            for (final AttributeType at : oc.getRequiredAttributeChain())
            {
              newAttrs.add(at.getNameOrOID());
            }

            for (final AttributeType at : oc.getOptionalAttributeChain())
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



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeLDAPAttributeDescriptionListChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public final PluginResult.PreParse doPreParse(
      PreParseSearchOperation searchOperation)
  {
    searchOperation.setAttributes(normalizedObjectClasses(searchOperation
        .getAttributes()));
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LDAPAttributeDescriptionListPluginCfg cfg =
         (LDAPAttributeDescriptionListPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
                                 LDAPAttributeDescriptionListPluginCfg
                                      configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}

