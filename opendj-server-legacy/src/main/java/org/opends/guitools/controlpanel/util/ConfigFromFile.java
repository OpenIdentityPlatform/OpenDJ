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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.backends.pluggable.SuffixContainer.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.server.config.server.AdministrationConnectorCfg;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.BackupBackendCfg;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.HTTPConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.JMXConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.LDIFBackendCfg;
import org.forgerock.opendj.server.config.server.LDIFConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.MemoryBackendCfg;
import org.forgerock.opendj.server.config.server.MonitorBackendCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.forgerock.opendj.server.config.server.ReplicationServerCfg;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.RootDNCfg;
import org.forgerock.opendj.server.config.server.RootDNUserCfg;
import org.forgerock.opendj.server.config.server.SNMPConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.TaskBackendCfg;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.OpenDsException;

/**
 * A class that reads the configuration information from the files.
 */
public class ConfigFromFile extends ConfigReader
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this config file handler. No initialization
   * should be performed here, as all of that work should be done in the
   * <CODE>initializeConfigHandler</CODE> method.
   */
  public ConfigFromFile()
  {
    super();
  }

  /**
   * Reads configuration information from the configuration files.
   */
  public void readConfiguration()
  {
    final List<OpenDsException> errors = new ArrayList<>();
    final Set<ConnectionHandlerDescriptor> connectionHandlers = new HashSet<>();
    final Set<BackendDescriptor> backendDescriptors = new HashSet<>();
    final Set<DN> alternateBindDNs = new HashSet<>();
    try
    {
      readConfig(connectionHandlers, backendDescriptors, alternateBindDNs, errors);
    }
    catch (final OpenDsException oe)
    {
      errors.add(oe);
    }
    catch (final Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error reading configuration: " + t, t));
      errors.add(new OfflineUpdateException(ERR_READING_CONFIG_LDAP.get(t.getMessage()), t));
    }

    if (!errors.isEmpty() && environmentSettingException != null)
    {
      errors.add(0, environmentSettingException);
    }

    for (final OpenDsException oe : errors)
    {
      logger.warn(LocalizableMessage.raw("Error reading configuration: " + oe, oe));
    }
    exceptions = Collections.unmodifiableList(new ArrayList<Exception>(errors));
    administrativeUsers = Collections.unmodifiableSet(alternateBindDNs);
    listeners = Collections.unmodifiableSet(connectionHandlers);
    backends = Collections.unmodifiableSet(backendDescriptors);
  }

  private void readConfig(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      final Set<BackendDescriptor> backendDescriptors, final Set<DN> alternateBindDNs,
      final List<OpenDsException> errors) throws OpenDsException, ConfigException
  {
    final RootCfg root = DirectoryServer.getInstance().getServerContext().getRootConfig();
    readAdminConnector(root, errors);
    readConnectionHandlers(connectionHandlers, root, errors);
    isSchemaEnabled = root.getGlobalConfiguration().isCheckSchema();

    readBackendConfiguration(backendDescriptors, root, errors);
    boolean isReplicationSecure = readIfReplicationIsSecure(root, errors);
    ReplicationSynchronizationProviderCfg sync = readSyncProviderIfExists(root);
    if (sync != null)
    {
      readReplicationConfig(connectionHandlers, backendDescriptors, sync, isReplicationSecure, errors);
    }
    readAlternateBindDNs(alternateBindDNs, root, errors);
  }

  private void readAdminConnector(final RootCfg root, final List<OpenDsException> errors) throws OpenDsException
  {
    try
    {
      final AdministrationConnectorCfg adminConnector = root.getAdministrationConnector();
      this.adminConnector = getConnectionHandler(adminConnector);
    }
    catch (final ConfigException ce)
    {
      errors.add(toConfigException(ce));
    }
  }

  private void readConnectionHandlers(final Set<ConnectionHandlerDescriptor> connectionHandlers, final RootCfg root,
      final List<OpenDsException> errors) throws ConfigException
  {
    for (final String connHandler : root.listConnectionHandlers())
    {
      try
      {
        final ConnectionHandlerCfg connectionHandler = root.getConnectionHandler(connHandler);
        connectionHandlers.add(getConnectionHandler(connectionHandler, connHandler));
      }
      catch (final OpenDsException oe)
      {
        errors.add(oe);
      }
    }
  }

  private void readBackendConfiguration(final Set<BackendDescriptor> backendDescriptors, final RootCfg root,
      final List<OpenDsException> errors)
  {
    for (final String backendName : root.listBackends())
    {
      try
      {
        final BackendCfg backend = root.getBackend(backendName);
        final Set<BaseDNDescriptor> baseDNs = new HashSet<>();
        for (final DN dn : backend.getBaseDN())
        {
          final BaseDNDescriptor baseDN =
              new BaseDNDescriptor(BaseDNDescriptor.Type.NOT_REPLICATED, dn, null, -1, -1, -1);
          baseDNs.add(baseDN);
        }
        final Set<IndexDescriptor> indexes = new HashSet<>();
        final Set<VLVIndexDescriptor> vlvIndexes = new HashSet<>();
        BackendDescriptor.Type type = getBackendType(backend);
        if (type == BackendDescriptor.Type.PLUGGABLE)
        {
          refreshBackendConfig(indexes, vlvIndexes, backend, errors);
        }

        final BackendDescriptor desc =
            new BackendDescriptor(backend.getBackendId(), baseDNs, indexes, vlvIndexes, -1, backend.isEnabled(), type);
        for (final AbstractIndexDescriptor index : indexes)
        {
          index.setBackend(desc);
        }
        for (final AbstractIndexDescriptor index : vlvIndexes)
        {
          index.setBackend(desc);
        }

        backendDescriptors.add(desc);
      }
      catch (final ConfigException ce)
      {
        errors.add(toConfigException(ce));
      }
    }
  }

  private BackendDescriptor.Type getBackendType(final BackendCfg backend)
  {
    if (backend instanceof PluggableBackendCfg)
    {
      return BackendDescriptor.Type.PLUGGABLE;
    }
    else if (backend instanceof LDIFBackendCfg)
    {
      return BackendDescriptor.Type.LDIF;
    }
    else if (backend instanceof MemoryBackendCfg)
    {
      return BackendDescriptor.Type.MEMORY;
    }
    else if (backend instanceof BackupBackendCfg)
    {
      return BackendDescriptor.Type.BACKUP;
    }
    else if (backend instanceof MonitorBackendCfg)
    {
      return BackendDescriptor.Type.MONITOR;
    }
    else if (backend instanceof TaskBackendCfg)
    {
      return BackendDescriptor.Type.TASK;
    }
    else
    {
      return BackendDescriptor.Type.OTHER;
    }
  }

  private void refreshBackendConfig(final Set<IndexDescriptor> indexes,
      final Set<VLVIndexDescriptor> vlvIndexes, final BackendCfg backend, final List<OpenDsException> errors)
  {
    final PluggableBackendCfg db = (PluggableBackendCfg) backend;
    readBackendIndexes(indexes, errors, db);
    readBackendVLVIndexes(vlvIndexes, errors, db);
  }

  private void readBackendIndexes(final Set<IndexDescriptor> indexes, final List<OpenDsException> errors,
      final PluggableBackendCfg db)
  {
    indexes.add(new IndexDescriptor(DN2ID_INDEX_NAME));
    indexes.add(new IndexDescriptor(ID2CHILDREN_COUNT_NAME));
    try
    {
      for (final String indexName : db.listBackendIndexes())
      {
        final BackendIndexCfg index = db.getBackendIndex(indexName);
        indexes.add(new IndexDescriptor(
            index.getAttribute().getNameOrOID(), index.getAttribute(),
            null, index.getIndexType(), index.getIndexEntryLimit()));
      }
    }
    catch (ConfigException ce)
    {
      errors.add(toConfigException(ce));
    }
  }

  private void readBackendVLVIndexes(final Set<VLVIndexDescriptor> vlvIndexes,
      final List<OpenDsException> errors, final PluggableBackendCfg db)
  {
    try
    {
      for (final String vlvIndexName : db.listBackendVLVIndexes())
      {
        final BackendVLVIndexCfg index = db.getBackendVLVIndex(vlvIndexName);
        final List<VLVSortOrder> sortOrder = getVLVSortOrder(index.getSortOrder());
        vlvIndexes.add(new VLVIndexDescriptor(
            index.getName(), null, index.getBaseDN(), VLVIndexDescriptor.toSearchScope(index.getScope()),
            index.getFilter(), sortOrder));
      }
    }
    catch (ConfigException ce)
    {
      errors.add(toConfigException(ce));
    }
  }

  private boolean readIfReplicationIsSecure(final RootCfg root, final List<OpenDsException> errors)
  {
    try
    {
      return root.getCryptoManager().isSSLEncryption();
    }
    catch (final ConfigException ce)
    {
      errors.add(toConfigException(ce));
      return false;
    }
  }

  private ReplicationSynchronizationProviderCfg readSyncProviderIfExists(final RootCfg root)
  {
    replicationPort = -1;
    try
    {
      return (ReplicationSynchronizationProviderCfg) root.getSynchronizationProvider("Multimaster Synchronization");
    }
    catch (final ConfigException ce)
    {
      // Ignore this one
      return null;
    }
  }

  private void readReplicationConfig(final Set<ConnectionHandlerDescriptor> connectionHandlers,
      final Set<BackendDescriptor> backendDescriptors, ReplicationSynchronizationProviderCfg sync,
      boolean isReplicationSecure, final List<OpenDsException> errors)
  {
    try
    {
      if (sync.isEnabled() && sync.hasReplicationServer())
      {
        final ReplicationServerCfg replicationServer = sync.getReplicationServer();
        if (replicationServer != null)
        {
          replicationPort = replicationServer.getReplicationPort();
          final ConnectionHandlerDescriptor.Protocol protocol =
              isReplicationSecure ? ConnectionHandlerDescriptor.Protocol.REPLICATION_SECURE
                  : ConnectionHandlerDescriptor.Protocol.REPLICATION;
          final Set<SearchResultEntry> emptySet = Collections.emptySet();
          final ConnectionHandlerDescriptor connHandler =
              new ConnectionHandlerDescriptor(new HashSet<InetAddress>(), replicationPort, protocol,
                  ConnectionHandlerDescriptor.State.ENABLED, "Multimaster Synchronization", emptySet);
          connectionHandlers.add(connHandler);
        }
      }
      final String[] domains = sync.listReplicationDomains();
      if (domains != null)
      {
        for (final String domain2 : domains)
        {
          final ReplicationDomainCfg domain = sync.getReplicationDomain(domain2);
          final DN dn = domain.getBaseDN();
          for (final BackendDescriptor backend : backendDescriptors)
          {
            for (final BaseDNDescriptor baseDN : backend.getBaseDns())
            {
              if (baseDN.getDn().equals(dn))
              {
                baseDN
                    .setType(sync.isEnabled() ? BaseDNDescriptor.Type.REPLICATED : BaseDNDescriptor.Type.DISABLED);
                baseDN.setReplicaID(domain.getServerId());
              }
            }
          }
        }
      }
    }
    catch (final ConfigException ce)
    {
      errors.add(toConfigException(ce));
    }
  }

  private void readAlternateBindDNs(final Set<DN> dns, final RootCfg root, final List<OpenDsException> errors)
  {
    try
    {
      final RootDNCfg rootDN = root.getRootDN();
      final String[] rootUsers = rootDN.listRootDNUsers();
      dns.clear();
      if (rootUsers != null)
      {
        for (final String rootUser2 : rootUsers)
        {
          final RootDNUserCfg rootUser = rootDN.getRootDNUser(rootUser2);
          dns.addAll(rootUser.getAlternateBindDN());
        }
      }
    }
    catch (final ConfigException ce)
    {
      errors.add(toConfigException(ce));
    }
  }

  private org.opends.server.config.ConfigException toConfigException(final ConfigException ce)
  {
    return new org.opends.server.config.ConfigException(ce.getMessageObject(), ce);
  }

  private ConnectionHandlerDescriptor getConnectionHandler(final ConnectionHandlerCfg connHandler, final String name)
      throws OpenDsException
  {
    final SortedSet<InetAddress> addresses = new TreeSet<>(getInetAddressComparator());

    final ConnectionHandlerDescriptor.State state =
        connHandler.isEnabled() ? ConnectionHandlerDescriptor.State.ENABLED
            : ConnectionHandlerDescriptor.State.DISABLED;

    ConnectionHandlerDescriptor.Protocol protocol;
    int port;
    if (connHandler instanceof LDAPConnectionHandlerCfg)
    {
      final LDAPConnectionHandlerCfg ldap = (LDAPConnectionHandlerCfg) connHandler;
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
    else if (connHandler instanceof HTTPConnectionHandlerCfg)
    {
      final HTTPConnectionHandlerCfg http = (HTTPConnectionHandlerCfg) connHandler;
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
    else if (connHandler instanceof JMXConnectionHandlerCfg)
    {
      final JMXConnectionHandlerCfg jmx = (JMXConnectionHandlerCfg) connHandler;
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
    else if (connHandler instanceof LDIFConnectionHandlerCfg)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.LDIF;
      port = -1;
    }
    else if (connHandler instanceof SNMPConnectionHandlerCfg)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.SNMP;
      final SNMPConnectionHandlerCfg snmp = (SNMPConnectionHandlerCfg) connHandler;
      addAll(addresses, snmp.getListenAddress());
      port = snmp.getListenPort();
    }
    else
    {
      protocol = ConnectionHandlerDescriptor.Protocol.OTHER;
      port = -1;
    }
    final Set<SearchResultEntry> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state, name, emptySet);
  }

  private <T> void addAll(final Collection<T> target, final Collection<T> source)
  {
    if (source != null)
    {
      target.addAll(source);
    }
  }

  private ConnectionHandlerDescriptor getConnectionHandler(final AdministrationConnectorCfg adminConnector)
      throws OpenDsException
  {
    final SortedSet<InetAddress> addresses = new TreeSet<>(getInetAddressComparator());

    final ConnectionHandlerDescriptor.Protocol protocol = ConnectionHandlerDescriptor.Protocol.ADMINISTRATION_CONNECTOR;
    final ConnectionHandlerDescriptor.State state = ConnectionHandlerDescriptor.State.ENABLED;

    addAll(addresses, adminConnector.getListenAddress());
    final int port = adminConnector.getListenPort();
    final Set<SearchResultEntry> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString(), emptySet);
  }
}
