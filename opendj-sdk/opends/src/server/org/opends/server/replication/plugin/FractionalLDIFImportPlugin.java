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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FractionalLDIFImportPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.plugin.LDAPReplicationDomain.
  AttributeValueStringIterator;
import org.opends.server.types.*;

import static org.opends.messages.ReplicationMessages.*;

/**
 * This class implements a Directory Server plugin that is used in fractional
 * replication when an online full update occurs.
 * The following tasks are done:
 * - check that the fractional configuration (if any) stored in the root entry
 * of the domain is compliant with the fractional configuration of the domain
 *  (if not make online update stop)
 * - perform filtering according to fractional configuration of the domain
 * - flush the fractional configuration of the domain in the root entry
 *  (if no one already present)
 */
public final class FractionalLDIFImportPlugin
  extends DirectoryServerPlugin<FractionalLDIFImportPluginCfg>
  implements ConfigurationChangeListener<FractionalLDIFImportPluginCfg>
{

  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call {@code super()} as its first element.
   */
  public FractionalLDIFImportPlugin()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
    FractionalLDIFImportPluginCfg configuration)
    throws ConfigException
  {
    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
          // This is acceptable.
          break;

        default:
          Message message =
            ERR_PLUGIN_FRACTIONAL_LDIF_IMPORT_INVALID_PLUGIN_TYPE.get(
            t.toString());
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
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.ImportLDIF doLDIFImport(
    LDIFImportConfig importConfig, Entry entry)
  {
    /**
     * See class comment for what we achieve here...
     */

    // Retrieve the replicated domain this entry belongs to
    DN entryDn = entry.getDN();
    LDAPReplicationDomain domain = MultimasterReplication.findDomain(entryDn,
      null);
    if (domain == null)
    {
      // Not part of a replicated domain : nothing to do
      return PluginResult.ImportLDIF.continueEntryProcessing();
    }

    // Is the entry to treat the root entry of the domain ? If yes, analyze the
    // fractional configuration in it and compare with local domain fractional
    // configuration. Stop the import if some inconsistency is detcted
    DN domainBaseDn = domain.getBaseDN();
    if (domainBaseDn.equals(entryDn))
    {
      /*
       * This is the root entry, try to read a fractional configuration from it
       */
      Iterator<String> exclIt = null;
      AttributeType fractionalExcludeType =
        DirectoryServer.getAttributeType(
        LDAPReplicationDomain.REPLICATION_FRACTIONAL_EXCLUDE);
      List<Attribute> exclAttrs =
        entry.getAttribute(fractionalExcludeType);
      Attribute exclAttr = null;
      if (exclAttrs != null)
      {
        exclAttr = exclAttrs.get(0);
        if (exclAttr != null)
        {
          exclIt = new AttributeValueStringIterator(exclAttr.iterator());
        }
      }

      Iterator<String> inclIt = null;
      AttributeType fractionalIncludeType =
        DirectoryServer.getAttributeType(
        LDAPReplicationDomain.REPLICATION_FRACTIONAL_INCLUDE);
      List<Attribute> inclAttrs =
        entry.getAttribute(fractionalIncludeType);
      Attribute inclAttr = null;
      if (inclAttrs != null)
      {
        inclAttr = inclAttrs.get(0);
        if (inclAttr != null)
        {
          inclIt = new AttributeValueStringIterator(inclAttr.iterator());
        }
      }

      // Compare backend and local fractional configuration
      boolean sameConfig = domain.isFractionalConfigConsistent(exclIt, inclIt);
      if (domain.isFractional())
      {
        // Local domain is fractional
        if (sameConfig)
        {
          // Both local and remote fractional configuration are equivalent :
          // follow import, no need to go with filtering as remote backend
          // should be ok
          return PluginResult.ImportLDIF.continueEntryProcessing();
        } else
        {
          // Local domain is fractional, remote domain has not same config
          boolean remoteDomainHasSomeConfig = false;
          if ((exclAttr != null && (exclAttr.size() > 0)) ||
            (inclAttr != null && (inclAttr.size() > 0)))
          {
            remoteDomainHasSomeConfig = true;
          }
          if (remoteDomainHasSomeConfig)
          {
            // Local domain is fractional, remote domain has some config which
            // is different : stop import (error will be logged when import is
            // stopped)
            domain.setImportErrorMessageId(
              LDAPReplicationDomain.IMPORT_ERROR_MESSAGE_BAD_REMOTE);
            domain.setFollowImport(false);
            return PluginResult.ImportLDIF.continueEntryProcessing();
          } else
          {
            // Local domain is fractional but remote domain has no config :
            // flush local config into root entry and follow import with
            // filtering
            flushFractionalConfigIntoEntry(domain, entry);
          }
        }
      } else
      {
        // Local domain is not fractional
        if (sameConfig)
        {
          // None of the local or remote domain has fractional config : nothing
          // more to do : let import finish
          return PluginResult.ImportLDIF.continueEntryProcessing();
        } else
        {
          // Local domain is not fractional but remote one is : stop import :
          // local domain should be configured with the same config as remote
          // one
          domain.setImportErrorMessageId(
              LDAPReplicationDomain.IMPORT_ERROR_MESSAGE_REMOTE_IS_FRACTIONAL);
          domain.setFollowImport(false);
          return PluginResult.ImportLDIF.continueEntryProcessing();
        }
      }
    }

    // If we get here, local domain fractional configuration is enabled.
    // Now filter for potential attributes to be removed.
    domain.fractionalRemoveAttributesFromEntry(entry.getDN().getRDN(),
      entry.getObjectClasses(), entry.getUserAttributes(), true);

    return PluginResult.ImportLDIF.continueEntryProcessing();
  }

  /**
   * Write the fractional configuration in the passed domain into the passed
   * entry.
   * WARNING: assumption is that no fractional attributes at all is already
   * present in the passed entry. Also assumption is that domain fractional
   * configuration is on.
   * @param domain Domain containing the fractional configuration to use
   * @param entry The entry to modify
   */
  private static void flushFractionalConfigIntoEntry(
    LDAPReplicationDomain domain, Entry entry)
  {
    if (domain.isFractional()) // Paranoia check
    {
      // Get the fractional configuration of the domain
      boolean fractionalExclusive = domain.isFractionalExclusive();
      Map<String, List<String>> fractionalSpecificClassesAttributes =
        domain.getFractionalSpecificClassesAttributes();
      List<String> fractionalAllClassesAttributes =
        domain.getFractionalAllClassesAttributes();

      // Create attribute builder for the rigth fractional mode
      String fractAttribute = null;
      if (fractionalExclusive)
      {
        fractAttribute = LDAPReplicationDomain.REPLICATION_FRACTIONAL_EXCLUDE;
      } else
      {
        fractAttribute = LDAPReplicationDomain.REPLICATION_FRACTIONAL_INCLUDE;
      }
      AttributeBuilder attrBuilder = new AttributeBuilder(fractAttribute);
      boolean somethingToFlush = false;

      // Add attribute values for all classes
      int size = fractionalAllClassesAttributes.size();
      if (size > 0)
      {
        String fracValue = "*:";
        int i = 1;
        for (String attrName : fractionalAllClassesAttributes)
        {
          fracValue += attrName;
          if (i < size)
          {
            fracValue += ",";
          }
          i++;
        }
        somethingToFlush = true;
        attrBuilder.add(fracValue);
      }

      // Add attribute values for specific classes
      size = fractionalSpecificClassesAttributes.size();
      if (size > 0)
      {
        for (String className : fractionalSpecificClassesAttributes.keySet())
        {
          int valuesSize =
            fractionalSpecificClassesAttributes.get(className).size();
          if (valuesSize > 0)
          {
            String fracValue = className + ":";
            int i = 1;
            for (String attrName : fractionalSpecificClassesAttributes.get(
              className))
            {
              fracValue += attrName;
              if (i < valuesSize)
              {
                fracValue += ",";
              }
              i++;
            }
            somethingToFlush = true;
            attrBuilder.add(fracValue);
          }
        }
      }

      // Now flush attribute values into entry
      if (somethingToFlush)
      {
        List<AttributeValue> duplicateValues = new ArrayList<AttributeValue>();
        entry.addAttribute(attrBuilder.toAttribute(), duplicateValues);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
    List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
    FractionalLDIFImportPluginCfg configuration,
    List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
    FractionalLDIFImportPluginCfg configuration)
  {
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

