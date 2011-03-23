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
 *      Portions copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.FractionalLDIFImportPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.plugin.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.plugin.LDAPReplicationDomain.
  AttributeValueStringIterator;
import org.opends.server.replication.plugin.LDAPReplicationDomain.
  FractionalConfig;
import org.opends.server.types.*;

import static org.opends.messages.ReplicationMessages.*;

/**
 * This class implements a Directory Server plugin that is used in fractional
 * replication to initialize a just configured fractional domain (when an online
 * full update occurs or offline/online ldif import).
 * The following tasks are done:
 * - check that the fractional configuration (if any) stored in the (incoming)
 * root entry of the domain is compliant with the fractional configuration of
 * the domain (if not make online update stop)
 * - perform filtering according to fractional configuration of the domain
 * - flush the fractional configuration of the domain in the root entry
 *  (if no one already present)
 */
public final class FractionalLDIFImportPlugin
  extends DirectoryServerPlugin<FractionalLDIFImportPluginCfg>
  implements ConfigurationChangeListener<FractionalLDIFImportPluginCfg>
{
  // Holds the fractional configuration and if available the replication domain
  // matching this import session (they form the importfractional context).
  // Domain is available if the server is online (import-ldif, online full
  // update..) otherwise, this is an import-ldif with server off. The key is the
  // ImportConfig object of the session which acts as a cookie for the whole
  // session. This allows to potentially run man imports at the same time.
  private final Hashtable<LDIFImportConfig, ImportFractionalContext>
    importSessionContexts = new Hashtable<LDIFImportConfig,
    ImportFractionalContext>();

  /**
   * Holds an import session fractional context.
   */
  private static class ImportFractionalContext
  {
    // Fractional configuration of the local domain (may be null if import on a
    // not replicated domain)
    private FractionalConfig fractionalConfig = null;
    // The local domain object (may stay null if server is offline)
    private LDAPReplicationDomain domain = null;

    /**
     * Constructor.
     * @param fractionalConfig The fractional configuration.
     * @param domain The replication domain.
     */
    public ImportFractionalContext(FractionalConfig fractionalConfig,
      LDAPReplicationDomain domain)
    {
      this.fractionalConfig = fractionalConfig;
      this.domain = domain;
    }

    /**
     * Getter for the fractional configuration.
     * @return the fractionalConfig
     */
    public FractionalConfig getFractionalConfig()
    {
      return fractionalConfig;
    }

    /**
     * Getter for the domain..
     * @return the domain
     */
    public LDAPReplicationDomain getDomain()
    {
      return domain;
    }
  }

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
        case LDIF_IMPORT_END:
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
    // Nothing to do
  }

  /**
   * Attempts to retrieve the fractional configuration of the domain being
   * imported.
   * @param entry An imported entry of the imported domain
   * @return The parsed fractional configuration for the domain matching the
   * passed entry. Null if no configuration is found for the domain
   * (not a replicated domain).
   */
  private static FractionalConfig getStaticReplicationDomainFractionalConfig(
    Entry entry) throws Exception {

    // Retrieve the configuration
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();


    ReplicationSynchronizationProviderCfg sync =
      (ReplicationSynchronizationProviderCfg)
      root.getSynchronizationProvider("Multimaster Synchronization");

    String[] domainNames = sync.listReplicationDomains();
    if (domainNames == null)
    {
      // No domain in replication
      return null;
    }

    // Find the configuration for domain the entry is part of
    ReplicationDomainCfg matchingReplicatedDomainCfg = null;
    for (String domainName : domainNames)
    {
      // Get the domain configuration object
      ReplicationDomainCfg replicationDomainCfg =
      sync.getReplicationDomain(domainName);
      // Is the entry a sub entry of the replicated domain main entry ?
      DN replicatedDn = replicationDomainCfg.getBaseDN();
      DN entryDn = entry.getDN();
      if (entryDn.isDescendantOf(replicatedDn))
      {
        // Found the matching replicated domain configuration object
        matchingReplicatedDomainCfg = replicationDomainCfg;
        break;
      }
    }

    if (matchingReplicatedDomainCfg == null)
    {
      // No matching replicated domain found
      return null;
    }

    // Extract the fractional configuration from the domain configuration object
    // and return it.
    return FractionalConfig.toFractionalConfig(matchingReplicatedDomainCfg);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doLDIFImportEnd(
    LDIFImportConfig importConfig)
  {
    // Remove the cookie of this import session
    synchronized(importSessionContexts)
    {
      importSessionContexts.remove(importConfig);
    }
  }

  /**
   * See class comment for what we achieve here...
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.ImportLDIF doLDIFImport(
    LDIFImportConfig importConfig, Entry entry)
  {
    /**
     * try to get the import fractional context for this entry. If not found,
     * create and initialize it. The mechanism here is done to take a lock only
     * once for the whole import session (except the necessary lock of the
     * doLDIFImportEnd method)
     */
    ImportFractionalContext importFractionalContext =
      importSessionContexts.get(importConfig);

    DN entryDn = entry.getDN();
    FractionalConfig localFractionalConfig = null;

    // If no context, create it
    if (importFractionalContext == null)
    {
      synchronized(importSessionContexts)
      {
        // Insure antoher thread was not creating the context at the same time
        // (we would create it for the second time which is useless)
        importFractionalContext = importSessionContexts.get(importConfig);
        if (importFractionalContext == null)
        {
          /*
           * Create context
           */

          /**
           * Retrieve the replicated domain this entry belongs to. Try to
           * retrieve replication domain instance first. If we are in an online
           * server, we should get it (if we are treating an entry that belongs
           * to a replicated domain), otherwise the domain is not replicated or
           * we are in an offline server context (import-ldif command run with
           * offline server) and we must retrieve the fractional configuration
           * directly from the configuration management system.
           */
          LDAPReplicationDomain domain =
            MultimasterReplication.findDomain(entryDn, null);

          // Get the fractional configuration extracted from the local server
          // configuration for the currently imported domain
          if (domain == null)
          {
            // Server may be offline, attempt to find fractional configuration
            // from config sub-system
            try
            {
              localFractionalConfig =
                getStaticReplicationDomainFractionalConfig(entry);
            } catch (Exception ex)
            {
              Message message = ERR_FRACTIONAL_COULD_NOT_RETRIEVE_CONFIG.get(
                entry.toString());
              return PluginResult.ImportLDIF.stopEntryProcessing(message);
            }
          } else
          {
            // Found a live domain, retrieve the fractional configuration from
            // it.
            localFractionalConfig = domain.getFractionalConfig();
          }
          // Create context and store it
          importFractionalContext =
            new ImportFractionalContext(localFractionalConfig, domain);
          importSessionContexts.put(importConfig, importFractionalContext);
        }
      }
    }

    // Extract the fractional configuration from the context
    localFractionalConfig = importFractionalContext.getFractionalConfig();
    if (localFractionalConfig == null)
    {
      // Not part of a replicated domain : nothing to do
      return PluginResult.ImportLDIF.continueEntryProcessing();
    }

    /**
     * At this point, either the domain instance has been found and we  use its
     * fractional configuration, or the server is offline and we use the parsed
     * fractional configuration. We differentiate both cases testing if domain
     * is null. We are also for sure handling an entry of a replicated suffix.
     */

    // Is the entry to handle the root entry of the domain ? If yes, analyze the
    // fractional configuration in it and compare with local fractional
    // configuration. Stop the import if some inconsistency is detected
    DN replicatedDomainBaseDn = localFractionalConfig.getBaseDn();
    if (replicatedDomainBaseDn.equals(entryDn))
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
      boolean sameConfig = LDAPReplicationDomain.
        isFractionalConfigConsistent(localFractionalConfig, exclIt, inclIt);
      if (localFractionalConfig.isFractional())
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
            LDAPReplicationDomain domain = importFractionalContext.getDomain();
            if (domain != null)
            {
              // Local domain is fractional, remote domain has some config which
              // is different : stop import (error will be logged when import is
              // stopped)
              domain.setImportErrorMessageId(
                LDAPReplicationDomain.IMPORT_ERROR_MESSAGE_BAD_REMOTE);
              domain.setFollowImport(false);
              return PluginResult.ImportLDIF.stopEntryProcessing(null);
            } else
            {
              Message message = NOTE_ERR_LDIF_IMPORT_FRACTIONAL_BAD_DATA_SET.
                get(replicatedDomainBaseDn.toString());
              return PluginResult.ImportLDIF.stopEntryProcessing(message);
            }
          } else
          {
            // Local domain is fractional but remote domain has no config :
            // flush local config into root entry and follow import with
            // filtering
            flushFractionalConfigIntoEntry(localFractionalConfig, entry);
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
          LDAPReplicationDomain domain = importFractionalContext.getDomain();
          if (domain != null)
          {
            // Local domain is not fractional but remote one is : stop import :
            // local domain should be configured with the same config as remote
            // one
            domain.setImportErrorMessageId(
                LDAPReplicationDomain.
                IMPORT_ERROR_MESSAGE_REMOTE_IS_FRACTIONAL);
            domain.setFollowImport(false);
            return PluginResult.ImportLDIF.stopEntryProcessing(null);
          } else
          {
            Message message =
              NOTE_ERR_LDIF_IMPORT_FRACTIONAL_DATA_SET_IS_FRACTIONAL.get(
                replicatedDomainBaseDn.toString());
            return PluginResult.ImportLDIF.stopEntryProcessing(message);
          }
        }
      }
    }

    // If we get here, local domain fractional configuration is enabled.
    // Now filter for potential attributes to be removed.
    LDAPReplicationDomain.fractionalRemoveAttributesFromEntry(
      localFractionalConfig, entry.getDN().getRDN(),
      entry.getObjectClasses(), entry.getUserAttributes(), true);

    return PluginResult.ImportLDIF.continueEntryProcessing();
  }

  /**
   * Write the fractional configuration in the passed domain into the passed
   * entry.
   * WARNING: assumption is that no fractional attributes at all is already
   * present in the passed entry. Also assumption is that domain fractional
   * configuration is on.
   * @param localFractionalConfig The local domain fractional configuration
   * @param entry The entry to modify
   */
  private static void flushFractionalConfigIntoEntry(FractionalConfig
    localFractionalConfig, Entry entry)
  {
    if (localFractionalConfig.isFractional()) // Paranoia check
    {
      // Get the fractional configuration of the domain
      boolean fractionalExclusive =
        localFractionalConfig.isFractionalExclusive();
      Map<String, List<String>> fractionalSpecificClassesAttributes =
        localFractionalConfig.getFractionalSpecificClassesAttributes();
      List<String> fractionalAllClassesAttributes =
        localFractionalConfig.getFractionalAllClassesAttributes();

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

