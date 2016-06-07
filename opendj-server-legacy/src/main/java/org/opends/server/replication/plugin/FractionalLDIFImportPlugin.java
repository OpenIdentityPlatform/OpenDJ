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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.FractionalLDIFImportPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.replication.plugin.LDAPReplicationDomain.FractionalConfig;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.*;

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
  /**
   * Holds the fractional configuration and if available the replication domain
   * matching this import session (they form the import fractional context).
   * Domain is available if the server is online (import-ldif, online full
   * update..) otherwise, this is an import-ldif with server off. The key is the
   * ImportConfig object of the session which acts as a cookie for the whole
   * session. This allows to potentially run man imports at the same time.
   */
  private final Map<LDIFImportConfig, ImportFractionalContext>
    importSessionContexts = new Hashtable<>();

  /**
   * Holds an import session fractional context.
   */
  private static class ImportFractionalContext
  {
    /**
     * Fractional configuration of the local domain (may be null if import on a
     * not replicated domain).
     */
    private FractionalConfig fractionalConfig;
    /** The local domain object (may stay null if server is offline). */
    private LDAPReplicationDomain domain;

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

  /** {@inheritDoc} */
  @Override
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
        throw new ConfigException(ERR_PLUGIN_FRACTIONAL_LDIF_IMPORT_INVALID_PLUGIN_TYPE.get(t));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
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
  private static FractionalConfig getStaticReplicationDomainFractionalConfig(ServerContext serverContext,
        Entry entry) throws Exception {
    RootCfg root = serverContext.getRootConfig();
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
      ReplicationDomainCfg replicationDomainCfg =
          sync.getReplicationDomain(domainName);
      // Is the entry a sub entry of the replicated domain main entry ?
      DN replicatedDn = replicationDomainCfg.getBaseDN();
      DN entryDn = entry.getName();
      if (entryDn.isSubordinateOrEqualTo(replicatedDn))
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

  /** {@inheritDoc} */
  @Override
  public final void doLDIFImportEnd(LDIFImportConfig importConfig)
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
  @Override
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

    DN entryDn = entry.getName();
    FractionalConfig localFractionalConfig = null;

    // If no context, create it
    if (importFractionalContext == null)
    {
      synchronized(importSessionContexts)
      {
        // Insure another thread was not creating the context at the same time
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
                getStaticReplicationDomainFractionalConfig(getServerContext(), entry);
            } catch (Exception ex)
            {
              return PluginResult.ImportLDIF.stopEntryProcessing(
                  ERR_FRACTIONAL_COULD_NOT_RETRIEVE_CONFIG.get(entry));
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
      // This is the root entry, try to read a fractional configuration from it
      Attribute exclAttr = getAttribute(REPLICATION_FRACTIONAL_EXCLUDE, entry);
      Iterator<ByteString> exclIt = null;
      if (exclAttr != null)
      {
        exclIt = exclAttr.iterator();
      }

      Attribute inclAttr = getAttribute(REPLICATION_FRACTIONAL_INCLUDE, entry);
      Iterator<ByteString> inclIt = null;
      if (inclAttr != null)
      {
        inclIt = inclAttr.iterator();
      }

      // Compare backend and local fractional configuration
      if (isFractionalConfigConsistent(localFractionalConfig, exclIt, inclIt))
      {
        // local and remote non/fractional config are equivalent :
        // follow import, no need to go with filtering as remote backend
        // should be ok
        // let import finish
        return PluginResult.ImportLDIF.continueEntryProcessing();
      }

      if (localFractionalConfig.isFractional())
      {
        // Local domain is fractional, remote domain has not same config
        boolean remoteDomainHasSomeConfig =
            isNotEmpty(exclAttr) || isNotEmpty(inclAttr);
        if (remoteDomainHasSomeConfig)
        {
          LDAPReplicationDomain domain = importFractionalContext.getDomain();
          if (domain != null)
          {
            // Local domain is fractional, remote domain has some config which
            // is different : stop import (error will be logged when import is
            // stopped)
            domain.setImportErrorMessageId(IMPORT_ERROR_MESSAGE_BAD_REMOTE);
            return PluginResult.ImportLDIF.stopEntryProcessing(null);
          }

          return PluginResult.ImportLDIF.stopEntryProcessing(
              NOTE_ERR_LDIF_IMPORT_FRACTIONAL_BAD_DATA_SET.get(replicatedDomainBaseDn));
        }

        // Local domain is fractional but remote domain has no config :
        // flush local config into root entry and follow import with filtering
        flushFractionalConfigIntoEntry(localFractionalConfig, entry);
      }
      else
      {
        // Local domain is not fractional
        LDAPReplicationDomain domain = importFractionalContext.getDomain();
        if (domain != null)
        {
          // Local domain is not fractional but remote one is : stop import :
          //local domain should be configured with the same config as remote one
          domain.setImportErrorMessageId(
              IMPORT_ERROR_MESSAGE_REMOTE_IS_FRACTIONAL);
          return PluginResult.ImportLDIF.stopEntryProcessing(null);
        }

        return PluginResult.ImportLDIF.stopEntryProcessing(
            NOTE_ERR_LDIF_IMPORT_FRACTIONAL_DATA_SET_IS_FRACTIONAL.get(replicatedDomainBaseDn));
      }
    }

    // If we get here, local domain fractional configuration is enabled.
    // Now filter for potential attributes to be removed.
    LDAPReplicationDomain.fractionalRemoveAttributesFromEntry(
      localFractionalConfig, entry.getName().rdn(),
      entry.getObjectClasses(), entry.getUserAttributes(), true);

    return PluginResult.ImportLDIF.continueEntryProcessing();
  }

  private boolean isNotEmpty(Attribute attr)
  {
    return attr != null && attr.size() > 0;
  }

  private Attribute getAttribute(String attributeName, Entry entry)
  {
    List<Attribute> attrs = entry.getAttribute(DirectoryServer.getSchema().getAttributeType(attributeName));
    return !attrs.isEmpty() ? attrs.get(0) : null;
  }

  /**
   * Write the fractional configuration in the passed domain into the passed
   * entry. WARNING: assumption is that no fractional attributes at all is
   * already present in the passed entry. Also assumption is that domain
   * fractional configuration is on.
   *
   * @param localFractionalConfig
   *          The local domain fractional configuration
   * @param entry
   *          The entry to modify
   */
  private static void flushFractionalConfigIntoEntry(FractionalConfig
    localFractionalConfig, Entry entry)
  {
    if (localFractionalConfig.isFractional()) // Paranoia check
    {
      // Get the fractional configuration of the domain
      boolean fractionalExclusive =
        localFractionalConfig.isFractionalExclusive();
      Map<String, Set<String>> fractionalSpecificClassesAttributes =
        localFractionalConfig.getFractionalSpecificClassesAttributes();
      Set<String> fractionalAllClassesAttributes =
        localFractionalConfig.getFractionalAllClassesAttributes();

      // Create attribute builder for the right fractional mode
      String fractAttribute = fractionalExclusive ?
          REPLICATION_FRACTIONAL_EXCLUDE : REPLICATION_FRACTIONAL_INCLUDE;
      AttributeBuilder attrBuilder = new AttributeBuilder(fractAttribute);
      // Add attribute values for all classes
      boolean somethingToFlush =
          add(attrBuilder, "*", fractionalAllClassesAttributes);

      // Add attribute values for specific classes
      if (!fractionalSpecificClassesAttributes.isEmpty())
      {
        for (Map.Entry<String, Set<String>> specific
            : fractionalSpecificClassesAttributes.entrySet())
        {
          if (add(attrBuilder, specific.getKey(), specific.getValue()))
          {
            somethingToFlush = true;
          }
        }
      }

      // Now flush attribute values into entry
      if (somethingToFlush)
      {
        List<ByteString> duplicateValues = new ArrayList<>();
        entry.addAttribute(attrBuilder.toAttribute(), duplicateValues);
      }
    }
  }

  private static boolean add(AttributeBuilder attrBuilder, String className,
      Set<String> values)
  {
    if (!values.isEmpty())
    {
      attrBuilder.add(className + ":" + Utils.joinAsString(",", values));
      return true;
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
    List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
    FractionalLDIFImportPluginCfg configuration,
    List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
    FractionalLDIFImportPluginCfg configuration)
  {
    return new ConfigChangeResult();
  }
}
