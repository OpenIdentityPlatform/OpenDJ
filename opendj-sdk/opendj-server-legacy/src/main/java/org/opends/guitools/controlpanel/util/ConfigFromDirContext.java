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
 *      Copyright 2008-2011 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.backends.pluggable.SuffixContainer.*;

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
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexTypeDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.AdministrationConnectorCfgClient;
import org.opends.server.admin.std.client.BackendCfgClient;
import org.opends.server.admin.std.client.BackendIndexCfgClient;
import org.opends.server.admin.std.client.BackendVLVIndexCfgClient;
import org.opends.server.admin.std.client.BackupBackendCfgClient;
import org.opends.server.admin.std.client.ConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.HTTPConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.JMXConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.LDAPConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.LDIFBackendCfgClient;
import org.opends.server.admin.std.client.LDIFConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.LocalDBIndexCfgClient;
import org.opends.server.admin.std.client.LocalDBVLVIndexCfgClient;
import org.opends.server.admin.std.client.MemoryBackendCfgClient;
import org.opends.server.admin.std.client.MonitorBackendCfgClient;
import org.opends.server.admin.std.client.PluggableBackendCfgClient;
import org.opends.server.admin.std.client.ReplicationDomainCfgClient;
import org.opends.server.admin.std.client.ReplicationServerCfgClient;
import org.opends.server.admin.std.client.ReplicationSynchronizationProviderCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.client.RootDNCfgClient;
import org.opends.server.admin.std.client.RootDNUserCfgClient;
import org.opends.server.admin.std.client.SNMPConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.TaskBackendCfgClient;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.ServerConstants;

/**
 * A class that reads the configuration and monitoring information using a
 * DirContext through LDAP.
 */
public class ConfigFromDirContext extends ConfigReader
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String DATABASE_ENVIRONMENT_SUFFIX = " Database Environment";
  private static final String SYNC_PROVIDER_NAME = "Multimaster Synchronization";

  private CustomSearchResult rootMonitor;
  private CustomSearchResult jvmMemoryUsage;
  private CustomSearchResult systemInformation;
  private CustomSearchResult entryCaches;
  private CustomSearchResult workQueue;
  private CustomSearchResult versionMonitor;

  private boolean isLocal = true;

  private final Map<String, CustomSearchResult> hmConnectionHandlersMonitor = new HashMap<>();

  /** The monitor root entry DN. */
  protected DN monitorDN = DN.rootDN();
  /** The JVM memory usage monitoring entry DN. */
  protected DN jvmMemoryUsageDN = DN.rootDN();
  /** The system information monitoring entry DN. */
  protected DN systemInformationDN = DN.rootDN();
  /**The entry cache monitoring entry DN. */
  protected DN entryCachesDN = DN.rootDN();
  /** The work queue monitoring entry DN. */
  protected DN workQueueDN = DN.rootDN();
  /** The version monitoring entry DN. */
  protected DN versionDN = DN.rootDN();

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
  public CustomSearchResult getEntryCaches()
  {
    return entryCaches;
  }

  /**
   * Returns the monitoring entry for the JVM memory usage.
   *
   * @return the monitoring entry for the JVM memory usage.
   */
  public CustomSearchResult getJvmMemoryUsage()
  {
    return jvmMemoryUsage;
  }

  /**
   * Returns the root entry of the monitoring tree.
   *
   * @return the root entry of the monitoring tree.
   */
  public CustomSearchResult getRootMonitor()
  {
    return rootMonitor;
  }

  /**
   * Returns the version entry of the monitoring tree.
   *
   * @return the version entry of the monitoring tree.
   */
  public CustomSearchResult getVersionMonitor()
  {
    return versionMonitor;
  }

  /**
   * Returns the monitoring entry for the system information.
   *
   * @return the monitoring entry for the system information.
   */
  public CustomSearchResult getSystemInformation()
  {
    return systemInformation;
  }

  /**
   * Returns the monitoring entry for the work queue.
   *
   * @return the monitoring entry for the work queue.
   */
  public CustomSearchResult getWorkQueue()
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
   * @param context
   *          the connection to be used to read the information.
   */
  public void readConfiguration(final InitialLdapContext context)
  {
    final List<OpenDsException> errors = new ArrayList<>();
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

    readSchemaIfNeeded(context, errors);

    try
    {
      readConfig(context, connectionHandlers, backendDescriptors, as, errors);
    }
    catch (final Throwable t)
    {
      errors.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(t), t));
    }

    for (OpenDsException oe : errors)
    {
      logger.warn(LocalizableMessage.raw("Error reading configuration: " + oe, oe));
    }
    administrativeUsers = Collections.unmodifiableSet(as);
    listeners = Collections.unmodifiableSet(connectionHandlers);
    backends = Collections.unmodifiableSet(backendDescriptors);
    try
    {
      updateMonitorInformation(context, errors);
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error reading monitoring: " + t, t));
      errors.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(t), t));
    }

    try
    {
      updateTaskInformation(context, errors, tasks);
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

  private void readSchemaIfNeeded(final InitialLdapContext context, final List<OpenDsException> errors)
  {
    if (mustReadSchema())
    {
      try
      {
        readSchema(context);
        if (getSchema() != null)
        {
          // Update the schema: so that when we call the server code the
          // latest schema read on the server we are managing is used.
          DirectoryServer.setSchema(getSchema());
        }
      }
      catch (OpenDsException oe)
      {
        errors.add(oe);
      }
    }
  }

  private void readConfig(final InitialLdapContext context,
      final Set<ConnectionHandlerDescriptor> connectionHandlers, final Set<BackendDescriptor> backendDescriptors,
      final Set<DN> alternateBindDNs, final List<OpenDsException> errors) throws Exception
  {
    // Get the Directory Server configuration handler and use it.
    ManagementContext mCtx = LDAPManagementContext.createFromContext(JNDIDirContextAdaptor.adapt(context));
    final RootCfgClient root = mCtx.getRootConfiguration();

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

  private void readAdminConnector(final RootCfgClient root, final List<OpenDsException> errors)
  {
    try
    {
      AdministrationConnectorCfgClient adminConnector = root.getAdministrationConnector();
      this.adminConnector = getConnectionHandler(adminConnector);
    }
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  private void readConnectionHandlers(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      RootCfgClient root, final List<OpenDsException> errors) throws ConcurrentModificationException,
      AuthorizationException, CommunicationException
  {
    for (String connHandler : root.listConnectionHandlers())
    {
      try
      {
        ConnectionHandlerCfgClient connectionHandler = root.getConnectionHandler(connHandler);
        connectionHandlers.add(getConnectionHandler(connectionHandler, connHandler));
      }
      catch (OpenDsException oe)
      {
        errors.add(oe);
      }
    }
  }

  private void readBackendConfiguration(final Set<BackendDescriptor> backendDescriptors,
      final RootCfgClient root, final List<OpenDsException> errors) throws Exception
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
        BackendDescriptor.Type type;
        if (backend instanceof LocalDBBackendCfgClient)
        {
          type = BackendDescriptor.Type.LOCAL_DB;
          refreshLocalDBBackendConfig(indexes, vlvIndexes, backend, errors);
        }
        else if (backend instanceof PluggableBackendCfgClient)
        {
          type = BackendDescriptor.Type.PLUGGABLE;
          refreshBackendConfig(indexes, vlvIndexes, backend, errors);
        }
        else if (backend instanceof LDIFBackendCfgClient)
        {
          type = BackendDescriptor.Type.LDIF;
        }
        else if (backend instanceof MemoryBackendCfgClient)
        {
          type = BackendDescriptor.Type.MEMORY;
        }
        else if (backend instanceof BackupBackendCfgClient)
        {
          type = BackendDescriptor.Type.BACKUP;
        }
        else if (backend instanceof MonitorBackendCfgClient)
        {
          type = BackendDescriptor.Type.MONITOR;
        }
        else if (backend instanceof TaskBackendCfgClient)
        {
          type = BackendDescriptor.Type.TASK;
        }
        else
        {
          type = BackendDescriptor.Type.OTHER;
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
      catch (OpenDsException oe)
      {
        errors.add(oe);
      }
    }
  }

  private void refreshBackendConfig(final Set<IndexDescriptor> indexes,
      final Set<VLVIndexDescriptor> vlvIndexes, final BackendCfgClient backend, final List<OpenDsException> errors)
  {
    final PluggableBackendCfgClient db = (PluggableBackendCfgClient) backend;
    readBackendIndexes(indexes, errors, db);
    readBackendVLVIndexes(vlvIndexes, errors, db);
  }

  private void readBackendIndexes(final Set<IndexDescriptor> indexes, final List<OpenDsException> errors,
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
            null, IndexTypeDescriptor.fromBackendIndexTypes(index.getIndexType()), index.getIndexEntryLimit()));
      }
    }
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  private void readBackendVLVIndexes(final Set<VLVIndexDescriptor> vlvIndexes,
      final List<OpenDsException> errors, final PluggableBackendCfgClient db)
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
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  @RemoveOnceLocalDBBackendIsPluggable
  private void refreshLocalDBBackendConfig(final Set<IndexDescriptor> indexes,
      final Set<VLVIndexDescriptor> vlvIndexes, final BackendCfgClient backend, final List<OpenDsException> errors)
  {
    LocalDBBackendCfgClient localDBBackend = (LocalDBBackendCfgClient)backend;
    try
    {
      for (String indexName : localDBBackend.listLocalDBIndexes())
      {
        LocalDBIndexCfgClient index = localDBBackend.getLocalDBIndex(indexName);
        indexes.add(new IndexDescriptor(
            index.getAttribute().getNameOrOID(), index.getAttribute(),
            null, IndexTypeDescriptor.fromLocalDBIndexTypes(index.getIndexType()), index.getIndexEntryLimit()));
      }
    }
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }

    indexes.add(new IndexDescriptor(DN2ID_INDEX_NAME));
    if (localDBBackend.isSubordinateIndexesEnabled())
    {
      indexes.add(new IndexDescriptor(ID2CHILDREN_INDEX_NAME));
      indexes.add(new IndexDescriptor(ID2SUBTREE_INDEX_NAME));
    }

    try
    {
      for (String vlvIndexName : localDBBackend.listLocalDBVLVIndexes())
      {
        LocalDBVLVIndexCfgClient index = localDBBackend.getLocalDBVLVIndex(vlvIndexName);
        String s = index.getSortOrder();
        List<VLVSortOrder> sortOrder = getVLVSortOrder(s);
        vlvIndexes.add(new VLVIndexDescriptor(
            index.getName(), null, index.getBaseDN(), VLVIndexDescriptor.toSearchScope(index.getScope()),
            index.getFilter(), sortOrder));
      }
    }
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  private boolean readIfReplicationIsSecure(final RootCfgClient root, final List<OpenDsException> errors)
  {
    try
    {
      return root.getCryptoManager().isSSLEncryption();
    }
    catch (OpenDsException oe)
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
    catch (OpenDsException oe)
    {
      return null;
    }
  }

  private void readReplicationConfig(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      final Set<BackendDescriptor> backendDescriptors, final ReplicationSynchronizationProviderCfgClient sync,
      boolean isReplicationSecure, final List<OpenDsException> errors)
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
          Set<CustomSearchResult> emptySet = Collections.emptySet();
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
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  private void readAlternateBindDNs(final Set<DN> alternateBindDNs, final RootCfgClient root,
      final List<OpenDsException> errors)
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
    catch (OpenDsException oe)
    {
      errors.add(oe);
    }
  }

  /**
   * Returns an array of monitoring attributes to be returned in the request.
   *
   * @return an array of monitoring attributes to be returned in the request.
   */
  protected String[] getMonitoringAttributes()
  {
    return new String[] {"*"};
  }

  /**
   * Reads the schema from the files.
   *
   * @param ctx
   *          the connection to be used to load the schema.
   * @throws OpenDsException
   *           if an error occurs reading the schema.
   */
  private void readSchema(InitialLdapContext ctx) throws OpenDsException
  {
    try
    {
      if (isLocal)
      {
        super.readSchema();
      }
      else
      {
        RemoteSchemaLoader loader = new RemoteSchemaLoader();
        loader.readSchema(ctx);
        schema = loader.getSchema();
      }
    }
    catch (NamingException ne)
    {
      throw new OnlineUpdateException(ERR_READING_SCHEMA_LDAP.get(ne), ne);
    }
    catch (ConfigException ce)
    {
      throw new org.opends.server.config.ConfigException(ce.getMessageObject(), ce);
    }
  }

  /**
   * Takes the provided search result and updates the monitoring information
   * accordingly.
   *
   * @param sr
   *          the search result.
   * @param searchBaseDN
   *          the base search.
   * @throws NamingException
   *           if there is an error retrieving the values of the search result.
   */
  protected void handleMonitoringSearchResult(SearchResult sr,
      String searchBaseDN)
  throws NamingException
  {
    if (javaVersion == null)
    {
      javaVersion = ConnectionUtils.getFirstValue(sr, "javaVersion");
    }

    if (numberConnections == -1)
    {
      String v = ConnectionUtils.getFirstValue(sr, "currentConnections");
      if (v != null)
      {
        numberConnections = Integer.parseInt(v);
      }
    }

    String dn = ConnectionUtils.getFirstValue(sr, "domain-name");
    String replicaId = ConnectionUtils.getFirstValue(sr, "server-id");
    String missingChanges = ConnectionUtils.getFirstValue(sr, "missing-changes");

    if ((dn != null)  && (replicaId != null) && (missingChanges != null))
    {
      for (BackendDescriptor backend : backends)
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            if (baseDN.getDn().equals(DN.valueOf(dn)) &&
                Integer.toString(baseDN.getReplicaID()).equals(replicaId))
            {
              try
              {
                baseDN.setAgeOfOldestMissingChange(
                    Long.valueOf(ConnectionUtils.getFirstValue(sr, "approx-older-change-not-synchronized-millis")));
              }
              catch (Throwable t)
              {
              }
              try
              {
                baseDN.setMissingChanges(Integer.valueOf(missingChanges));
              }
              catch (Throwable t)
              {
              }
            }
          }
          catch (Throwable t)
          {
          }
        }
      }
    }
    else
    {
      CustomSearchResult csr = new CustomSearchResult(sr, searchBaseDN);
      String backendID = ConnectionUtils.getFirstValue(sr, "ds-backend-id");
      String entryCount = ConnectionUtils.getFirstValue(sr, "ds-backend-entry-count");
      Set<String> baseDnEntries = ConnectionUtils.getValues(sr, "ds-base-dn-entry-count");
      if ((backendID != null) && ((entryCount != null) || (baseDnEntries != null)))
      {
        for (BackendDescriptor backend : backends)
        {
          if (backend.getBackendID().equalsIgnoreCase(backendID))
          {
            if (entryCount != null)
            {
              backend.setEntries(Integer.parseInt(entryCount));
            }
            if (baseDnEntries != null)
            {
              for (String s : baseDnEntries)
              {
                int index = s.indexOf(" ");
                if (index != -1)
                {
                  for (BaseDNDescriptor baseDN : backend.getBaseDns())
                  {
                    dn = s.substring(index +1);

                    if (Utilities.areDnsEqual(dn,
                        baseDN.getDn().toString()))
                    {
                      try
                      {
                        baseDN.setEntries(
                            Integer.parseInt(s.substring(0, index)));
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
      }
      else
      {
        // Check if it is the DB monitor entry
        String cn = ConnectionUtils.getFirstValue(sr, "cn");
        if ((cn != null) && cn.endsWith(DATABASE_ENVIRONMENT_SUFFIX))
        {
          String monitorBackendID = cn.substring(0, cn.length() - DATABASE_ENVIRONMENT_SUFFIX.length());
          for (BackendDescriptor backend : backends)
          {
            if (backend.getBackendID().equalsIgnoreCase(monitorBackendID))
            {
              backend.setMonitoringEntry(csr);
            }
          }
        }
      }
      try
      {
        if ((rootMonitor == null) && isRootMonitor(csr))
        {
          rootMonitor = csr;
        }
        else if ((entryCaches == null) && isEntryCaches(csr))
        {
          entryCaches = csr;
        }
        else if ((workQueue == null) && isWorkQueue(csr))
        {
          workQueue = csr;
        }
        else if ((jvmMemoryUsage == null) && isJvmMemoryUsage(csr))
        {
          jvmMemoryUsage = csr;
        }
        else if ((systemInformation == null) && isSystemInformation(csr))
        {
          systemInformation = csr;
        }
        else if ((versionMonitor == null) && isVersionMonitor(csr))
        {
          versionMonitor = csr;
        }
        else if (isConnectionHandler(csr))
        {
          String statistics = " Statistics";
          String cn = ConnectionUtils.getFirstValue(sr, "cn");
          if (cn.endsWith(statistics))
          {
            // Assume it is a connection handler
            String name = cn.substring(0, cn.length() - statistics.length());
            hmConnectionHandlersMonitor.put(getKey(name), csr);
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
   * Takes the provided search result and updates the task information
   * accordingly.
   *
   * @param sr
   *          the search result.
   * @param searchBaseDN
   *          the base search.
   * @param taskEntries
   *          the collection of TaskEntries to be updated.
   * @param ex
   *          the list of exceptions to be updated if an error occurs.
   * @throws NamingException
   *           if there is an error retrieving the values of the search result.
   */
  private void handleTaskSearchResult(SearchResult sr, String searchBaseDN, Collection<TaskEntry> taskEntries,
      List<OpenDsException> ex) throws NamingException
  {
    CustomSearchResult csr = new CustomSearchResult(sr, searchBaseDN);
    try
    {
      if (isTaskEntry(csr))
      {
        taskEntries.add(new TaskEntry(csr.getEntry()));
      }
    }
    catch (OpenDsException ode)
    {
      ex.add(ode);
    }
  }

  private void updateMonitorInformation(InitialLdapContext ctx,
      List<OpenDsException> ex)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(getMonitoringAttributes());
    String filter = "(objectclass=*)";

    try
    {
      LdapName jndiName = new LdapName("cn=monitor");
      NamingEnumeration<SearchResult> monitorEntries = ctx.search(jndiName, filter, ctls);
      javaVersion = null;
      numberConnections = -1;

      try
      {
        while (monitorEntries.hasMore())
        {
          SearchResult sr = monitorEntries.next();
          handleMonitoringSearchResult(sr, "cn=monitor");
        }
      }
      finally
      {
        monitorEntries.close();
      }
    }
    catch (NamingException ne)
    {
      ex.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(ne.getMessage()), ne));
    }
  }

  /**
   * Updates the provided list of TaskEntry with the task entries found in a
   * server.
   *
   * @param ctx
   *          the connection to the server.
   * @param ex
   *          the list of exceptions encountered while retrieving the task
   *          entries.
   * @param ts
   *          the list of task entries to be updated.
   */
  public void updateTaskInformation(InitialLdapContext ctx, List<OpenDsException> ex, Collection<TaskEntry> ts)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(getMonitoringAttributes());
    String filter = "(objectclass=ds-task)";

    try
    {
      LdapName jndiName = new LdapName(ConfigConstants.DN_TASK_ROOT);
      NamingEnumeration<SearchResult> taskEntries = ctx.search(jndiName, filter, ctls);
      try
      {
        while (taskEntries.hasMore())
        {
          SearchResult sr = taskEntries.next();
          handleTaskSearchResult(sr, ConfigConstants.DN_TASK_ROOT, ts, ex);
        }
      }
      finally
      {
        taskEntries.close();
      }
    }
    catch (NamingException ne)
    {
      ex.add(new OnlineUpdateException(ERR_READING_CONFIG_LDAP.get(ne.getMessage()), ne));
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
    Set<CustomSearchResult> emptySet = Collections.emptySet();
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

    Set<CustomSearchResult> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(
        addresses, port, protocol, state, INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString(), emptySet);
  }

  private boolean isRootMonitor(CustomSearchResult csr) throws OpenDsException
  {
    return monitorDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isVersionMonitor(CustomSearchResult csr) throws OpenDsException
  {
    return versionDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isSystemInformation(CustomSearchResult csr) throws OpenDsException
  {
    return systemInformationDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isJvmMemoryUsage(CustomSearchResult csr) throws OpenDsException
  {
    return jvmMemoryUsageDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isWorkQueue(CustomSearchResult csr) throws OpenDsException
  {
    return workQueueDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isEntryCaches(CustomSearchResult csr) throws OpenDsException
  {
    return entryCachesDN.equals(DN.valueOf(csr.getDN()));
  }

  private boolean isConnectionHandler(CustomSearchResult csr) throws OpenDsException
  {
    boolean isConnectionHandler = false;
    DN dn = DN.valueOf(csr.getDN());
    DN parent = dn.parent();
    if ((parent != null) && parent.equals(monitorDN))
    {
      List<?> vs = csr.getAttributeValues("cn");
      if ((vs != null) && !vs.isEmpty())
      {
        String cn = (String) vs.iterator().next();
        String statistics = " Statistics";
        if (cn.endsWith(statistics))
        {
          isConnectionHandler = true;
        }
      }
    }
    return isConnectionHandler;
  }

  private static boolean isTaskEntry(CustomSearchResult csr) throws OpenDsException
  {
    boolean isTaskEntry = false;
    List<Object> vs = csr.getAttributeValues("objectclass");
    if ((vs != null) && !vs.isEmpty())
    {
      for (Object oc : vs)
      {
        if (oc.toString().equalsIgnoreCase("ds-task"))
        {
          isTaskEntry = true;
          break;
        }
      }
    }
    return isTaskEntry;
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

  private Set<CustomSearchResult>getMonitoringEntries(ConnectionHandlerDescriptor ch)
  {
    Set<CustomSearchResult> monitorEntries = new HashSet<>();
    if (ch.getState() == ConnectionHandlerDescriptor.State.ENABLED)
    {
      for (String key : hmConnectionHandlersMonitor.keySet())
      {
        // The name of the connection handler does not appear necessarily in the
        // key (which is based on the DN of the monitoring entry).  In general
        // the DN contains the String specified in
        // LDAPConnectionHandler.DEFAULT_FRIENDLY_NAME, so we have to check that
        // this connection handler is the right one.
        // See org.opends.server.protocols.ldap.LDAPConnectionHandler to see
        // how the DN of the monitoring entry is generated.
        if (key.contains(getKey("port " + ch.getPort())))
        {
          boolean hasAllAddresses = true;
          for (InetAddress a : ch.getAddresses())
          {
            if (!key.contains(getKey(a.getHostAddress())))
            {
              hasAllAddresses = false;
              break;
            }
          }
          if (hasAllAddresses)
          {
            monitorEntries.add(hmConnectionHandlersMonitor.get(key));
          }
        }
      }
    }

    return monitorEntries;
  }

}
