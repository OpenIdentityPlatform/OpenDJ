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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import static org.opends.messages.AdminToolMessages.*;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.BackupBackendCfg;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.CryptoManagerCfg;
import org.opends.server.admin.std.server.JMXConnectionHandlerCfg;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.admin.std.server.LDIFBackendCfg;
import org.opends.server.admin.std.server.LDIFConnectionHandlerCfg;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.admin.std.server.LocalDBVLVIndexCfg;
import org.opends.server.admin.std.server.MemoryBackendCfg;
import org.opends.server.admin.std.server.MonitorBackendCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.RootDNCfg;
import org.opends.server.admin.std.server.RootDNUserCfg;
import org.opends.server.admin.std.server.SNMPConnectionHandlerCfg;
import org.opends.server.admin.std.server.TaskBackendCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * A class that reads the configuration information from the files.
 *
 */
public class ConfigFromFile extends ConfigReader
{
  private static final Logger LOG =
    Logger.getLogger(ConfigFromFile.class.getName());

  /**
   * Creates a new instance of this config file handler.  No initialization
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
    exceptions.clear();

    try
    {
      DirectoryServer.getInstance().initializeConfiguration();
      // Get the Directory Server configuration handler and use it.ad
      RootCfg root =
        ServerManagementContext.getInstance().getRootConfiguration();
      listeners.clear();
      try
      {
        AdministrationConnectorCfg adminConnector =
          root.getAdministrationConnector();
        this.adminConnector = getConnectionHandler(adminConnector);
      }
      catch (ConfigException ce)
      {
        exceptions.add(ce);
      }
      String[] connectionHandlers = root.listConnectionHandlers();
      for (int i=0; i<connectionHandlers.length; i++)
      {
        try
        {
          ConnectionHandlerCfg connectionHandler =
            root.getConnectionHandler(connectionHandlers[i]);
          listeners.add(getConnectionHandler(connectionHandler,
              connectionHandlers[i]));
        }
        catch (OpenDsException oe)
        {
          exceptions.add(oe);
        }
      }
      isSchemaEnabled = root.getGlobalConfiguration().isCheckSchema();

      backends.clear();
      String[] backendNames = root.listBackends();
      for (int i=0; i<backendNames.length; i++)
      {
        try
        {
          BackendCfg backend = root.getBackend(backendNames[i]);
          SortedSet<BaseDNDescriptor> baseDNs = new TreeSet<BaseDNDescriptor>();
          for (DN dn : backend.getBaseDN())
          {
            BaseDNDescriptor baseDN =
              new BaseDNDescriptor(BaseDNDescriptor.Type.NOT_REPLICATED, dn,
                  null, -1, -1, -1);
            baseDNs.add(baseDN);
          }
          SortedSet<IndexDescriptor> indexes = new TreeSet<IndexDescriptor>();
          SortedSet<VLVIndexDescriptor> vlvIndexes =
            new TreeSet<VLVIndexDescriptor>();
          BackendDescriptor.Type type;
          if (backend instanceof LocalDBBackendCfg)
          {
            type = BackendDescriptor.Type.LOCAL_DB;
            LocalDBBackendCfg db = (LocalDBBackendCfg)backend;
            String[] indexNames = db.listLocalDBIndexes();
            try
            {
              for (int j=0; j<indexNames.length; j++)
              {
                LocalDBIndexCfg index = db.getLocalDBIndex(indexNames[j]);
                indexes.add(new IndexDescriptor(
                    index.getAttribute().getNameOrOID(), index.getAttribute(),
                    null, index.getIndexType(), index.getIndexEntryLimit()));
              }
            }
            catch (OpenDsException oe)
            {
              exceptions.add(oe);
            }
            indexes.add(new IndexDescriptor("dn2id", null, null,
                new TreeSet<IndexType>(), -1));
            indexes.add(new IndexDescriptor("id2children", null, null,
                new TreeSet<IndexType>(), -1));
            indexes.add(new IndexDescriptor("id2subtree", null, null,
                new TreeSet<IndexType>(), -1));

            String[] vlvIndexNames = db.listLocalDBVLVIndexes();
            try
            {
              for (int j=0; j<vlvIndexNames.length; j++)
              {
                LocalDBVLVIndexCfg index =
                  db.getLocalDBVLVIndex(vlvIndexNames[j]);
                String s = index.getSortOrder();
                List<VLVSortOrder> sortOrder = getVLVSortOrder(s);
                vlvIndexes.add(new VLVIndexDescriptor(index.getName(), null,
                    index.getBaseDN(), index.getScope(), index.getFilter(),
                    sortOrder, index.getMaxBlockSize()));
              }
            }
            catch (OpenDsException oe)
            {
              exceptions.add(oe);
            }
          }
          else if (backend instanceof LDIFBackendCfg)
          {
            type = BackendDescriptor.Type.LDIF;
          }
          else if (backend instanceof MemoryBackendCfg)
          {
            type = BackendDescriptor.Type.MEMORY;
          }
          else if (backend instanceof BackupBackendCfg)
          {
            type = BackendDescriptor.Type.BACKUP;
          }
          else if (backend instanceof MonitorBackendCfg)
          {
            type = BackendDescriptor.Type.MONITOR;
          }
          else if (backend instanceof TaskBackendCfg)
          {
            type = BackendDescriptor.Type.TASK;
          }
          else
          {
            type = BackendDescriptor.Type.OTHER;
          }
          BackendDescriptor desc = new BackendDescriptor(
              backend.getBackendId(), baseDNs, indexes, vlvIndexes, -1,
              backend.isEnabled(), type);
          for (AbstractIndexDescriptor index: indexes)
          {
            index.setBackend(desc);
          }
          for (AbstractIndexDescriptor index: vlvIndexes)
          {
            index.setBackend(desc);
          }

          backends.add(desc);
        }
        catch (OpenDsException oe)
        {
          exceptions.add(oe);
        }
      }

      boolean isReplicationSecure = false;
      try
      {
        CryptoManagerCfg cryptoManager = root.getCryptoManager();
        isReplicationSecure = cryptoManager.isSSLEncryption();
      }
      catch (OpenDsException oe)
      {
        exceptions.add(oe);
      }


      replicationPort = -1;
      ReplicationSynchronizationProviderCfg sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfg)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (OpenDsException oe)
      {
        // Ignore this one
      }
      if (sync != null)
      {
        try
        {
          if (sync.isEnabled() && sync.hasReplicationServer())
          {
            ReplicationServerCfg replicationServer =
              sync.getReplicationServer();
            if (replicationServer != null)
            {
              replicationPort = replicationServer.getReplicationPort();
              ConnectionHandlerDescriptor.Protocol protocol =
                isReplicationSecure ?
                    ConnectionHandlerDescriptor.Protocol.REPLICATION_SECURE :
                    ConnectionHandlerDescriptor.Protocol.REPLICATION;
              ConnectionHandlerDescriptor connHandler =
                new ConnectionHandlerDescriptor(
                    new HashSet<InetAddress>(),
                    replicationPort,
                    protocol,
                    ConnectionHandlerDescriptor.State.ENABLED,
                    "Multimaster Synchronization");
              listeners.add(connHandler);
            }
          }
          String[] domains = sync.listReplicationDomains();
          if (domains != null)
          {
            for (int i=0; i<domains.length; i++)
            {
              ReplicationDomainCfg domain =
                sync.getReplicationDomain(domains[i]);
              DN dn = domain.getBaseDN();
              for (BackendDescriptor backend : backends)
              {
                for (BaseDNDescriptor baseDN : backend.getBaseDns())
                {
                  if (baseDN.getDn().equals(dn))
                  {
                    baseDN.setType(BaseDNDescriptor.Type.REPLICATED);
                  }
                }
              }
            }
          }
        }
        catch (OpenDsException oe)
        {
          exceptions.add(oe);
        }
      }


      try
      {
        RootDNCfg rootDN = root.getRootDN();
        String[] rootUsers = rootDN.listRootDNUsers();
        administrativeUsers.clear();
        if (rootUsers != null)
        {
          for (int i=0; i < rootUsers.length; i++)
          {
            RootDNUserCfg rootUser = rootDN.getRootDNUser(rootUsers[i]);
            administrativeUsers.addAll(rootUser.getAlternateBindDN());
          }
        }
      }
      catch (OpenDsException oe)
      {
        exceptions.add(oe);
      }

      try
      {
        readSchema();
      }
      catch (OpenDsException oe)
      {
        exceptions.add(oe);
      }
    }
    catch (OpenDsException oe)
    {
      exceptions.add(oe);
    }
    catch (final Throwable t)
    {
      LOG.log(Level.WARNING, "Error reading configuration: "+t, t);
      OfflineUpdateException ex = new OfflineUpdateException(
          ERR_READING_CONFIG_LDAP.get(t.getMessage().toString()), t);
      exceptions.add(ex);
    }

    if (exceptions.size() > 0)
    {
      if (environmentSettingException != null)
      {
        exceptions.add(0, environmentSettingException);
      }
    }

    for (OpenDsException oe : exceptions)
    {
      LOG.log(Level.WARNING, "Error reading configuration: "+oe, oe);
    }
  }

  private ConnectionHandlerDescriptor getConnectionHandler(
      ConnectionHandlerCfg connHandler, String name) throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<InetAddress>();
    int port;

    ConnectionHandlerDescriptor.Protocol protocol;

    ConnectionHandlerDescriptor.State state = connHandler.isEnabled() ?
        ConnectionHandlerDescriptor.State.ENABLED :
          ConnectionHandlerDescriptor.State.DISABLED;

    if (connHandler instanceof LDAPConnectionHandlerCfg)
    {
      LDAPConnectionHandlerCfg ldap = (LDAPConnectionHandlerCfg)connHandler;
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
      SortedSet<InetAddress> v = ldap.getListenAddress();
      if (v == null)
      {
        addresses.addAll(v);
      }
      port = ldap.getListenPort();
    }
    else if (connHandler instanceof JMXConnectionHandlerCfg)
    {
      JMXConnectionHandlerCfg jmx = (JMXConnectionHandlerCfg)connHandler;
      if (jmx.isUseSSL())
      {
        protocol = ConnectionHandlerDescriptor.Protocol.JMXS;
      }
      else
      {
        protocol = ConnectionHandlerDescriptor.Protocol.JMX;
      }
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
      SNMPConnectionHandlerCfg snmp = (SNMPConnectionHandlerCfg)connHandler;
      port = snmp.getListenPort();
    }
    else
    {
      protocol = ConnectionHandlerDescriptor.Protocol.OTHER;
      port = -1;
    }
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        name);
  }

  private ConnectionHandlerDescriptor getConnectionHandler(
      AdministrationConnectorCfg adminConnector) throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<InetAddress>();

    ConnectionHandlerDescriptor.Protocol protocol =
      ConnectionHandlerDescriptor.Protocol.ADMINISTRATION_CONNECTOR;

    ConnectionHandlerDescriptor.State state =
      ConnectionHandlerDescriptor.State.ENABLED;


    SortedSet<InetAddress> v = adminConnector.getListenAddress();
    if (v == null)
    {
      addresses.addAll(v);
    }
    int port = adminConnector.getListenPort();

    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString());
  }
}
