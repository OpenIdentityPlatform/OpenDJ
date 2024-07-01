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
 * Copyright 2008-2011 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.backends.pluggable.SuffixContainer.*;
import static org.opends.server.config.ConfigConstants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.server.config.client.AdministrationConnectorCfgClient;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendIndexCfgClient;
import org.forgerock.opendj.server.config.client.BackendVLVIndexCfgClient;
import org.forgerock.opendj.server.config.client.BackupBackendCfgClient;
import org.forgerock.opendj.server.config.client.ConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.HTTPConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.JMXConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.LDAPConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.LDIFBackendCfgClient;
import org.forgerock.opendj.server.config.client.LDIFConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.MemoryBackendCfgClient;
import org.forgerock.opendj.server.config.client.MonitorBackendCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationDomainCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationServerCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationSynchronizationProviderCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.client.RootDNCfgClient;
import org.forgerock.opendj.server.config.client.RootDNUserCfgClient;
import org.forgerock.opendj.server.config.client.SNMPConnectionHandlerCfgClient;
import org.forgerock.opendj.server.config.client.TaskBackendCfgClient;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.ServerConstants;

/** A class that reads the configuration and monitoring information using an LDAP connection. */
public class ConfigFromConnection extends ConfigReader
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String DATABASE_JE_MONITORING_ENTRY_SUFFIX = " JE Database";
  private static final String DATABASE_PDB_MONITORING_ENTRY_SUFFIX = " PDB Database";
  private static final String SYNC_PROVIDER_NAME = "Multimaster Synchronization";

  private SearchResultEntry rootMonitor;
  private SearchResultEntry jvmMemoryUsage;
  private SearchResultEntry systemInformation;
  private SearchResultEntry entryCaches;
  private SearchResultEntry workQueue;
  private SearchResultEntry versionMonitor;

  private boolean isLocal = true;

  private final Map<String, SearchResultEntry> hmConnectionHandlersMonitor = new HashMap<>();

  /** The monitor root entry DN. */
  private DN monitorDN = DN.rootDN();
  /** The JVM memory usage monitoring entry DN. */
  private DN jvmMemoryUsageDN = DN.rootDN();
  /** The system information monitoring entry DN. */
  private DN systemInformationDN = DN.rootDN();
  /**The entry cache monitoring entry DN. */
  private DN entryCachesDN = DN.rootDN();
  /** The work queue monitoring entry DN. */
  private DN workQueueDN = DN.rootDN();
  /** The version monitoring entry DN. */
  private DN versionDN = DN.rootDN();

  {
    try
    {
      monitorDN = DN.valueOf("cn=monitor");
      jvmMemoryUsageDN = DN.valueOf("cn=JVM Memory Usage,cn=monitor");
      systemInformationDN = DN.valueOf("cn=System Information,cn=monitor");
      entryCachesDN = DN.valueOf("cn=Entry Caches,cn=monitor");
      workQueueDN = DN.valueOf("cn=Work Queue,cn=monitor");
      versionDN = DN.valueOf("cn=Version,cn=monitor");
    }
    catch (Throwable t)
    {
      throw new RuntimeException("Could not decode DNs: "+t, t);
    }
  }

  /** The date formatter to be used to parse GMT dates. */
  public static final SimpleDateFormat utcParser = new SimpleDateFormat(ServerConstants.DATE_FORMAT_GMT_TIME);
  {
    utcParser.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /** The date formatter to be used to format dates. */
  public static final DateFormat formatter = DateFormat.getDateTimeInstance();

  /**
   * Returns the monitoring entry for the entry caches.
   *
   * @return the monitoring entry for the entry caches.
   */
  public SearchResultEntry getEntryCaches()
  {
    return entryCaches;
  }

  /**
   * Returns the monitoring entry for the JVM memory usage.
   *
   * @return the monitoring entry for the JVM memory usage.
   */
  public SearchResultEntry getJvmMemoryUsage()
  {
    return jvmMemoryUsage;
  }

  /**
   * Returns the root entry of the monitoring tree.
   *
   * @return the root entry of the monitoring tree.
   */
  public SearchResultEntry getRootMonitor()
  {
    return rootMonitor;
  }

  /**
   * Returns the version entry of the monitoring tree.
   *
   * @return the version entry of the monitoring tree.
   */
  public SearchResultEntry getVersionMonitor()
  {
    return versionMonitor;
  }

  /**
   * Returns the monitoring entry for the system information.
   *
   * @return the monitoring entry for the system information.
   */
  public SearchResultEntry getSystemInformation()
  {
    return systemInformation;
  }

  /**
   * Returns the monitoring entry for the work queue.
   *
   * @return the monitoring entry for the work queue.
   */
  public SearchResultEntry getWorkQueue()
  {
    return workQueue;
  }

  /**
   * Sets whether this server represents the local instance or a remote server.
   *
   * @param isLocal
   *          whether this server represents the local instance or a remote
   *          server (in another machine or in another installation on the same
   *          machine).
   */
  public void setIsLocal(boolean isLocal)
  {
    this.isLocal = isLocal;
  }

  /**
   * Returns <CODE>true</CODE> if we are trying to manage the local host and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we are trying to manage the local host and
   *         <CODE>false</CODE> otherwise.
   */
  public boolean isLocal()
  {
    return isLocal;
  }

  /**
   * Reads configuration and monitoring information using the provided
   * connection.
   *
   * @param connWrapper
   *          the connection to be used to read the information.
   */
  public void readConfiguration(final ConnectionWrapper connWrapper)
  {
    final List<Exception> errors = new ArrayList<>();
    final Set<ConnectionHandlerDescriptor> connectionHandlers = new HashSet<>();
    final Set<BackendDescriptor> backendDescriptors = new HashSet<>();
    final Set<DN> as = new HashSet<>();
    final Set<TaskEntry> tasks = new HashSet<>();

    rootMonitor = null;
    jvmMemoryUsage = null;
    systemInformation = null;
    entryCaches = null;
    workQueue = null;
    versionMonitor = null;

    hmConnectionHandlersMonitor.clear();

    readSchemaIfNeeded(connWrapper, errors);

    try
    {
      readConfig(connWrapper, connectionHandlers, backendDescriptors, as, errors);
    }
    catch (final Throwable t)
    {
      errors.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(t), t));
    }

    for (Exception oe : errors)
    {
      logger.warn(LocalizableMessage.raw("Error reading configuration: " + oe, oe));
    }
    administrativeUsers = Collections.unmodifiableSet(as);
    listeners = Collections.unmodifiableSet(connectionHandlers);
    backends = Collections.unmodifiableSet(backendDescriptors);
    try
    {
      updateMonitorInformation(connWrapper, errors);
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error reading monitoring: " + t, t));
      errors.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(t), t));
    }

    try
    {
      updateTaskInformation(connWrapper, errors, tasks);
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error reading task information: " + t, t));
      errors.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(t), t));
    }

    taskEntries = Collections.unmodifiableSet(tasks);
    for (ConnectionHandlerDescriptor ch : getConnectionHandlers())
    {
      ch.setMonitoringEntries(getMonitoringEntries(ch));
    }

    if (adminConnector != null)
    {
      adminConnector.setMonitoringEntries(getMonitoringEntries(adminConnector));
    }
    exceptions = Collections.unmodifiableList(errors);
  }

  private void readSchemaIfNeeded(final ConnectionWrapper connWrapper, final List<Exception> errors)
  {
    if (mustReadSchema())
    {
      try
      {
        Schema schema = readSchema(connWrapper);
        if (schema != null)
        {
          // Update the schema: so that when we call the server code the
          // latest schema read on the server we are managing is used.
          DirectoryServer.getInstance().getServerContext().getSchemaHandler().updateSchema(schema);
        }
      }
      catch (OpenDsException oe)
      {
        errors.add(oe);
      }
    }
  }

  private void readConfig(final ConnectionWrapper connWrapper,
      final Set<ConnectionHandlerDescriptor> connectionHandlers, final Set<BackendDescriptor> backendDescriptors,
      final Set<DN> alternateBindDNs, final List<Exception> errors) throws Exception
  {
    final RootCfgClient root = connWrapper.getRootConfiguration();

    readAdminConnector(root, errors);
    readConnectionHandlers(connectionHandlers, root, errors);
    isSchemaEnabled = root.getGlobalConfiguration().isCheckSchema();

    readBackendConfiguration(backendDescriptors, root, errors);

    boolean isReplicationSecure = readIfReplicationIsSecure(root, errors);

    final ReplicationSynchronizationProviderCfgClient sync = readSyncProviderIfExists(root);
    if (sync != null)
    {
      readReplicationConfig(connectionHandlers, backendDescriptors, sync, isReplicationSecure, errors);
    }

    readAlternateBindDNs(alternateBindDNs, root, errors);
  }

  private void readAdminConnector(final RootCfgClient root, final List<Exception> errors)
  {
    try
    {
      AdministrationConnectorCfgClient adminConnector = root.getAdministrationConnector();
      this.adminConnector = getConnectionHandler(adminConnector);
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  private void readConnectionHandlers(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      RootCfgClient root, final List<Exception> errors)
  {
    try
    {
      for (String connHandler : root.listConnectionHandlers())
      {
        try
        {
          ConnectionHandlerCfgClient connectionHandler = root.getConnectionHandler(connHandler);
          connectionHandlers.add(getConnectionHandler(connectionHandler, connHandler));
        }
        catch (Exception oe)
        {
          errors.add(oe);
        }
      }
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  private void readBackendConfiguration(final Set<BackendDescriptor> backendDescriptors,
      final RootCfgClient root, final List<Exception> errors) throws Exception
  {
    for (final String backendName : root.listBackends())
    {
      try
      {
        BackendCfgClient backend = root.getBackend(backendName);
        Set<BaseDNDescriptor> baseDNs = new HashSet<>();
        for (DN dn : backend.getBaseDN())
        {
          BaseDNDescriptor baseDN = new BaseDNDescriptor(BaseDNDescriptor.Type.NOT_REPLICATED, dn, null, -1, -1, -1);
          baseDNs.add(baseDN);
        }
        Set<IndexDescriptor> indexes = new HashSet<>();
        Set<VLVIndexDescriptor> vlvIndexes = new HashSet<>();
        BackendDescriptor.Type type = getBackendType(backend);
        if (type == BackendDescriptor.Type.PLUGGABLE)
        {
          refreshBackendConfig(indexes, vlvIndexes, backend, errors);
        }

        BackendDescriptor desc = new BackendDescriptor(
            backend.getBackendId(), baseDNs, indexes, vlvIndexes, -1, backend.isEnabled(), type);
        for (AbstractIndexDescriptor index: indexes)
        {
          index.setBackend(desc);
        }
        for (AbstractIndexDescriptor index: vlvIndexes)
        {
          index.setBackend(desc);
        }
        for (BaseDNDescriptor baseDN : baseDNs)
        {
          baseDN.setBackend(desc);
        }
        backendDescriptors.add(desc);
      }
      catch (Exception oe)
      {
        errors.add(oe);
      }
    }
  }

  private BackendDescriptor.Type getBackendType(BackendCfgClient backend)
  {
    if (backend instanceof PluggableBackendCfgClient)
    {
      return BackendDescriptor.Type.PLUGGABLE;
    }
    else if (backend instanceof LDIFBackendCfgClient)
    {
      return BackendDescriptor.Type.LDIF;
    }
    else if (backend instanceof MemoryBackendCfgClient)
    {
      return BackendDescriptor.Type.MEMORY;
    }
    else if (backend instanceof BackupBackendCfgClient)
    {
      return BackendDescriptor.Type.BACKUP;
    }
    else if (backend instanceof MonitorBackendCfgClient)
    {
      return BackendDescriptor.Type.MONITOR;
    }
    else if (backend instanceof TaskBackendCfgClient)
    {
      return BackendDescriptor.Type.TASK;
    }
    else
    {
      return BackendDescriptor.Type.OTHER;
    }
  }

  private void refreshBackendConfig(final Set<IndexDescriptor> indexes,
      final Set<VLVIndexDescriptor> vlvIndexes, final BackendCfgClient backend, final List<Exception> errors)
  {
    final PluggableBackendCfgClient db = (PluggableBackendCfgClient) backend;
    readBackendIndexes(indexes, errors, db);
    readBackendVLVIndexes(vlvIndexes, errors, db);
  }

  private void readBackendIndexes(final Set<IndexDescriptor> indexes, final List<Exception> errors,
      final PluggableBackendCfgClient db)
  {
    indexes.add(new IndexDescriptor(DN2ID_INDEX_NAME));
    indexes.add(new IndexDescriptor(ID2CHILDREN_COUNT_NAME));
    try
    {
      for (final String indexName : db.listBackendIndexes())
      {
        final BackendIndexCfgClient index = db.getBackendIndex(indexName);
        indexes.add(new IndexDescriptor(
            index.getAttribute().getNameOrOID(), index.getAttribute(),
            null, index.getIndexType(), index.getIndexEntryLimit()));
      }
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  private void readBackendVLVIndexes(final Set<VLVIndexDescriptor> vlvIndexes,
      final List<Exception> errors, final PluggableBackendCfgClient db)
  {
    try
    {
      for (final String vlvIndexName : db.listBackendVLVIndexes())
      {
        final BackendVLVIndexCfgClient index = db.getBackendVLVIndex(vlvIndexName);
        final List<VLVSortOrder> sortOrder = getVLVSortOrder(index.getSortOrder());
        vlvIndexes.add(new VLVIndexDescriptor(
            index.getName(), null, index.getBaseDN(), VLVIndexDescriptor.toSearchScope(index.getScope()),
            index.getFilter(), sortOrder));
      }
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  private boolean readIfReplicationIsSecure(final RootCfgClient root, final List<Exception> errors)
  {
    try
    {
      return root.getCryptoManager().isSSLEncryption();
    }
    catch (Exception oe)
    {
      errors.add(oe);
      return false;
    }
  }

  private ReplicationSynchronizationProviderCfgClient readSyncProviderIfExists(final RootCfgClient root)
  {
    try
    {
      return (ReplicationSynchronizationProviderCfgClient) root.getSynchronizationProvider(SYNC_PROVIDER_NAME);
    }
    catch (Exception oe)
    {
      return null;
    }
  }

  private void readReplicationConfig(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      final Set<BackendDescriptor> backendDescriptors, final ReplicationSynchronizationProviderCfgClient sync,
      boolean isReplicationSecure, final List<Exception> errors)
  {
    replicationPort = -1;
    try
    {
      if (sync.isEnabled() && sync.hasReplicationServer())
      {
        ReplicationServerCfgClient replicationServer = sync.getReplicationServer();
        if (replicationServer != null)
        {
          replicationPort = replicationServer.getReplicationPort();
          ConnectionHandlerDescriptor.Protocol protocol =
            isReplicationSecure ? ConnectionHandlerDescriptor.Protocol.REPLICATION_SECURE
                                : ConnectionHandlerDescriptor.Protocol.REPLICATION;
          Set<SearchResultEntry> emptySet = Collections.emptySet();
          ConnectionHandlerDescriptor connHandler = new ConnectionHandlerDescriptor(
              new HashSet<InetAddress>(), replicationPort, protocol, ConnectionHandlerDescriptor.State.ENABLED,
                SYNC_PROVIDER_NAME, emptySet);
          connectionHandlers.add(connHandler);
        }
      }

      String[] domains = sync.listReplicationDomains();
      if (domains != null)
      {
        for (String domain2 : domains)
        {
          ReplicationDomainCfgClient domain = sync.getReplicationDomain(domain2);
          DN dn = domain.getBaseDN();
          for (BackendDescriptor backend : backendDescriptors)
          {
            for (BaseDNDescriptor baseDN : backend.getBaseDns())
            {
              if (baseDN.getDn().equals(dn))
              {
                baseDN.setType(sync.isEnabled() ? BaseDNDescriptor.Type.REPLICATED
                                                : BaseDNDescriptor.Type.DISABLED);
                baseDN.setReplicaID(domain.getServerId());
              }
            }
          }
        }
      }
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  private void readAlternateBindDNs(final Set<DN> alternateBindDNs, final RootCfgClient root,
      final List<Exception> errors)
  {
    try
    {
      RootDNCfgClient rootDN = root.getRootDN();
      String[] rootUsers = rootDN.listRootDNUsers();
      if (rootUsers != null)
      {
        for (String rootUser2 : rootUsers)
        {
          RootDNUserCfgClient rootUser = rootDN.getRootDNUser(rootUser2);
          alternateBindDNs.addAll(rootUser.getAlternateBindDN());
        }
      }
    }
    catch (Exception oe)
    {
      errors.add(oe);
    }
  }

  /**
   * Returns an array of monitoring attributes to be returned in the request.
   *
   * @return an array of monitoring attributes to be returned in the request.
   */
  private String[] getMonitoringAttributes()
  {
    return new String[] {"*"};
  }

  /**
   * Reads the schema from the files.
   *
   * @param connWrapper
   *          the connection to be used to load the schema.
   * @throws OpenDsException
   *           if an error occurs reading the schema.
   */
  private Schema readSchema(ConnectionWrapper connWrapper) throws OpenDsException
  {
    try
    {
      schema = isLocal ? super.readSchema() : new RemoteSchemaLoader().readSchema(connWrapper);
      return schema;
    }
    catch (LdapException e)
    {
      throw new OnlineUpdateException(ERR_READING_SCHEMA_LDAP.get(e), e);
    }
    catch (ConfigException e)
    {
      throw new org.opends.server.config.ConfigException(e.getMessageObject(), e);
    }
  }

  /**
   * Takes the provided search result and updates the monitoring information
   * accordingly.
   *
   * @param sr
   *          the search result.
   * @throws LdapException
   *           if there is an error retrieving the values of the search result.
   */
  private void handleMonitoringSearchResult(SearchResultEntry sr) throws LdapException
  {
    if (javaVersion == null)
    {
      Attribute attr = sr.getAttribute("javaVersion");
      javaVersion = attr != null ? attr.firstValueAsString() : null;
    }

    if (numberConnections == -1)
    {
      Integer nb = sr.parseAttribute("currentConnections").asInteger();
      if (nb != null)
      {
        numberConnections = nb;
      }
    }

    DN suffixDn = sr.parseAttribute("domain-name").asDN();
    Integer replicaId = sr.parseAttribute("server-id").asInteger();
    Integer missingChanges = sr.parseAttribute("missing-changes").asInteger();

    if (suffixDn != null && replicaId != null && missingChanges != null)
    {
      for (BackendDescriptor backend : backends)
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            if (baseDN.getDn().equals(suffixDn) && Objects.equals(baseDN.getReplicaID(), replicaId))
            {
              baseDN.setAgeOfOldestMissingChange(
                  sr.parseAttribute("approx-older-change-not-synchronized-millis").asLong());
              baseDN.setMissingChanges(missingChanges);
            }
          }
          catch (Throwable ignored)
          {
          }
        }
      }
    }
    else
    {
      Attribute backendIdAttr = sr.getAttribute("ds-backend-id");
      Integer entryCount = sr.parseAttribute("ds-backend-entry-count").asInteger();
      Set<String> baseDnEntries = sr.parseAttribute("ds-base-dn-entry-count").asSetOfString();
      if (backendIdAttr != null && (entryCount != null || !baseDnEntries.isEmpty()))
      {
        String backendID = backendIdAttr.firstValueAsString();
        for (BackendDescriptor backend : backends)
        {
          if (backend.getBackendID().equalsIgnoreCase(backendID))
          {
            if (entryCount != null)
            {
              backend.setEntries(entryCount);
            }
            for (String s : baseDnEntries)
            {
              int index = s.indexOf(" ");
              if (index != -1)
              {
                DN dn = DN.valueOf(s.substring(index + 1));
                for (BaseDNDescriptor baseDN : backend.getBaseDns())
                {
                  if (dn.equals(baseDN.getDn()))
                  {
                    try
                    {
                      baseDN.setEntries(Integer.parseInt(s.substring(0, index)));
                    }
                    catch (Throwable t)
                    {
                      /* Ignore */
                    }
                    break;
                  }
                }
              }
            }
          }
        }
      }
      else
      {
        // Check if it is the DB monitor entry
        String cn = sr.getAttribute("cn").firstValueAsString();
        String monitorBackendID = null;
        BackendDescriptor.PluggableType pluggableType = BackendDescriptor.PluggableType.UNKNOWN;
        if (cn != null && cn.endsWith(DATABASE_JE_MONITORING_ENTRY_SUFFIX))
        {
          pluggableType = BackendDescriptor.PluggableType.JE;
          monitorBackendID = cn.substring(0, cn.length() - DATABASE_JE_MONITORING_ENTRY_SUFFIX.length());
        }
        if (cn != null && cn.endsWith(DATABASE_PDB_MONITORING_ENTRY_SUFFIX))
        {
          pluggableType = BackendDescriptor.PluggableType.PDB;
          monitorBackendID = cn.substring(0, cn.length() - DATABASE_PDB_MONITORING_ENTRY_SUFFIX.length());
        }
        if (monitorBackendID != null)
        {
          for (BackendDescriptor backend : backends)
          {
            if (backend.getBackendID().equalsIgnoreCase(monitorBackendID))
            {
              backend.setPluggableType(pluggableType);
              backend.setMonitoringEntry(sr);
            }
          }
        }
      }
      try
      {
        if (rootMonitor == null && isRootMonitor(sr))
        {
          rootMonitor = sr;
        }
        else if (entryCaches == null && isEntryCaches(sr))
        {
          entryCaches = sr;
        }
        else if (workQueue == null && isWorkQueue(sr))
        {
          workQueue = sr;
        }
        else if (jvmMemoryUsage == null && isJvmMemoryUsage(sr))
        {
          jvmMemoryUsage = sr;
        }
        else if (systemInformation == null && isSystemInformation(sr))
        {
          systemInformation = sr;
        }
        else if (versionMonitor == null && isVersionMonitor(sr))
        {
          versionMonitor = sr;
        }
        else if (isConnectionHandler(sr))
        {
          String statistics = " Statistics";
          String cn = sr.getAttribute("cn").firstValueAsString();
          if (cn.endsWith(statistics))
          {
            // Assume it is a connection handler
            String name = cn.substring(0, cn.length() - statistics.length());
            hmConnectionHandlersMonitor.put(getKey(name), sr);
          }
        }
      }
      catch (OpenDsException ode)
      {
        exceptions.add(ode);
      }
    }
  }

  /**
   * Takes the provided search result and updates the task information accordingly.
   *
   * @param sr
   *          the search result.
   * @param taskEntries
   *          the collection of TaskEntries to be updated.
   * @param ex
   *          the list of exceptions to be updated if an error occurs.
   */
  private void handleTaskSearchResult(SearchResultEntry sr, Collection<TaskEntry> taskEntries, List<Exception> ex)
  {
    try
    {
      if (isTaskEntry(sr))
      {
        taskEntries.add(new TaskEntry(Converters.to(sr)));
      }
    }
    catch (RuntimeException e)
    {
      ex.add(e);
    }
  }

  private void updateMonitorInformation(ConnectionWrapper connWrapper, List<Exception> ex)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchRequest request = newSearchRequest("cn=monitor", WHOLE_SUBTREE, "(objectclass=*)", getMonitoringAttributes());

    try (ConnectionEntryReader monitorEntries = connWrapper.getConnection().search(request))
    {
      javaVersion = null;
      numberConnections = -1;

      while (monitorEntries.hasNext())
      {
        handleMonitoringSearchResult(monitorEntries.readEntry());
      }
    }
    catch (IOException e)
    {
      ex.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(e.getMessage()), e));
    }
  }

  /**
   * Updates the provided list of TaskEntry with the task entries found in a
   * server.
   *
   * @param connWrapper
   *          the connection to the server.
   * @param ex
   *          the list of exceptions encountered while retrieving the task
   *          entries.
   * @param ts
   *          the list of task entries to be updated.
   */
  public void updateTaskInformation(ConnectionWrapper connWrapper, List<Exception> ex, Collection<TaskEntry> ts)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchRequest request =
        newSearchRequest(DN_TASK_ROOT, WHOLE_SUBTREE, "(objectclass=ds-task)", getMonitoringAttributes());
    try (ConnectionEntryReader taskEntries = connWrapper.getConnection().search(request))
    {
      while (taskEntries.hasNext())
      {
        SearchResultEntry sr = taskEntries.readEntry();
        handleTaskSearchResult(sr, ts, ex);
      }
    }
    catch (IOException e)
    {
      ex.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(e.getMessage()), e));
    }
  }

  private ConnectionHandlerDescriptor getConnectionHandler(ConnectionHandlerCfgClient connHandler, String name)
  throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<>(getInetAddressComparator());
    ConnectionHandlerDescriptor.State state = connHandler.isEnabled() ? ConnectionHandlerDescriptor.State.ENABLED
                                                                      : ConnectionHandlerDescriptor.State.DISABLED;

    ConnectionHandlerDescriptor.Protocol protocol;
    int port;
    if (connHandler instanceof LDAPConnectionHandlerCfgClient)
    {
      LDAPConnectionHandlerCfgClient ldap = (LDAPConnectionHandlerCfgClient)connHandler;
      if (ldap.isUseSSL())
      {
        protocol = ConnectionHandlerDescriptor.Protocol.LDAPS;
      }
      else if (ldap.isAllowStartTLS())
      {
        protocol = ConnectionHandlerDescriptor.Protocol.LDAP_STARTTLS;
      }
      else
      {
        protocol = ConnectionHandlerDescriptor.Protocol.LDAP;
      }
      addAll(addresses, ldap.getListenAddress());
      port = ldap.getListenPort();
    }
    else if (connHandler instanceof HTTPConnectionHandlerCfgClient)
    {
      HTTPConnectionHandlerCfgClient http = (HTTPConnectionHandlerCfgClient) connHandler;
      if (http.isUseSSL())
      {
        protocol = ConnectionHandlerDescriptor.Protocol.HTTPS;
      }
      else
      {
        protocol = ConnectionHandlerDescriptor.Protocol.HTTP;
      }
      addAll(addresses, http.getListenAddress());
      port = http.getListenPort();
    }
    else if (connHandler instanceof JMXConnectionHandlerCfgClient)
    {
      JMXConnectionHandlerCfgClient jmx = (JMXConnectionHandlerCfgClient)connHandler;
      if (jmx.isUseSSL())
      {
        protocol = ConnectionHandlerDescriptor.Protocol.JMXS;
      }
      else
      {
        protocol = ConnectionHandlerDescriptor.Protocol.JMX;
      }
      addresses.add(jmx.getListenAddress());
      port = jmx.getListenPort();
    }
    else if (connHandler instanceof LDIFConnectionHandlerCfgClient)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.LDIF;
      port = -1;
    }
    else if (connHandler instanceof SNMPConnectionHandlerCfgClient)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.SNMP;
      SNMPConnectionHandlerCfgClient snmp = (SNMPConnectionHandlerCfgClient)connHandler;
      addAll(addresses, snmp.getListenAddress());
      port = snmp.getListenPort();
    }
    else
    {
      protocol = ConnectionHandlerDescriptor.Protocol.OTHER;
      port = -1;
    }
    Set<SearchResultEntry> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state, name, emptySet);
  }

  private <T> void addAll(Collection<T> target, Collection<T> source)
  {
    if (source != null)
    {
      target.addAll(source);
    }
  }

  private ConnectionHandlerDescriptor getConnectionHandler(AdministrationConnectorCfgClient adminConnector)
      throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<>(getInetAddressComparator());

    ConnectionHandlerDescriptor.Protocol protocol = ConnectionHandlerDescriptor.Protocol.ADMINISTRATION_CONNECTOR;
    ConnectionHandlerDescriptor.State state = ConnectionHandlerDescriptor.State.ENABLED;

    addAll(addresses, adminConnector.getListenAddress());
    int port = adminConnector.getListenPort();

    Set<SearchResultEntry> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(
        addresses, port, protocol, state, INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString(), emptySet);
  }

  private boolean isRootMonitor(SearchResultEntry sr) throws OpenDsException
  {
    return monitorDN.equals(sr.getName());
  }

  private boolean isVersionMonitor(SearchResultEntry sr) throws OpenDsException
  {
    return versionDN.equals(sr.getName());
  }

  private boolean isSystemInformation(SearchResultEntry sr) throws OpenDsException
  {
    return systemInformationDN.equals(sr.getName());
  }

  private boolean isJvmMemoryUsage(SearchResultEntry sr) throws OpenDsException
  {
    return jvmMemoryUsageDN.equals(sr.getName());
  }

  private boolean isWorkQueue(SearchResultEntry sr) throws OpenDsException
  {
    return workQueueDN.equals(sr.getName());
  }

  private boolean isEntryCaches(SearchResultEntry sr) throws OpenDsException
  {
    return entryCachesDN.equals(sr.getName());
  }

  private boolean isConnectionHandler(SearchResultEntry sr) throws OpenDsException
  {
    DN dn = sr.getName();
    DN parent = dn.parent();
    if (parent != null && parent.equals(monitorDN))
    {
      Set<String> vs = sr.parseAttribute("cn").asSetOfString();
      if (!vs.isEmpty())
      {
        String cn = vs.iterator().next();
        String statistics = " Statistics";
        if (cn.endsWith(statistics))
        {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isTaskEntry(SearchResultEntry sr)
  {
    for (String oc : sr.parseAttribute("objectclass").asSetOfString())
    {
      if (oc.equalsIgnoreCase("ds-task"))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Commodity method to get the string representation to be used in the hash
   * maps as key.
   *
   * @param value
   *          the value to be transformed into a key for a hash map.
   * @return the string representation to be used in the hash maps as key.
   */
  private String getKey(String value)
  {
    return value.toLowerCase();
  }

  private Set<SearchResultEntry> getMonitoringEntries(ConnectionHandlerDescriptor ch)
  {
    Set<SearchResultEntry> monitorEntries = new HashSet<>();
    if (ch.getState() == ConnectionHandlerDescriptor.State.ENABLED)
    {
      for (Map.Entry<String, SearchResultEntry> entry : hmConnectionHandlersMonitor.entrySet())
      {
        String key = entry.getKey();
        // The name of the connection handler does not appear necessarily in the
        // key (which is based on the DN of the monitoring entry).  In general
        // the DN contains the String specified in
        // LDAPConnectionHandler.DEFAULT_FRIENDLY_NAME, so we have to check that
        // this connection handler is the right one.
        // See org.opends.server.protocols.ldap.LDAPConnectionHandler to see
        // how the DN of the monitoring entry is generated.
        if (key.contains(getKey("port " + ch.getPort()))
            && hasAllAddresses(ch, key))
        {
          monitorEntries.add(entry.getValue());
        }
      }
    }

    return monitorEntries;
  }

  private boolean hasAllAddresses(ConnectionHandlerDescriptor ch, String key)
  {
    for (InetAddress a : ch.getAddresses())
    {
      if (!key.contains(getKey(a.getHostAddress())))
      {
        return false;
      }
    }
    return true;
  }
}
