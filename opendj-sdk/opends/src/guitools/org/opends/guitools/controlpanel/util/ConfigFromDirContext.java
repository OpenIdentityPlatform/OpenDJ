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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * A class that reads the configuration and monitoring information using a
 * DirContext through LDAP.
 *
 */
public class ConfigFromDirContext extends ConfigReader
{
  private static final Logger LOG =
    Logger.getLogger(ConfigFromDirContext.class.getName());

  /**
   * Reads configuration and monitoring information using the provided
   * connection.
   * @param ctx the connection to be used to read the information.
   */
  public void readConfiguration(InitialLdapContext ctx)
  {
    List<OpenDsException> ex = new ArrayList<OpenDsException>();
    Set<ConnectionHandlerDescriptor> ls =
      new HashSet<ConnectionHandlerDescriptor>();
    Set<BackendDescriptor> bs = new HashSet<BackendDescriptor>();
    Set<DN> as = new HashSet<DN>();

    try
    {
      // Get the Directory Server configuration handler and use it.
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();

      try
      {
        AdministrationConnectorCfgClient adminConnector =
          root.getAdministrationConnector();
        this.adminConnector = getConnectionHandler(adminConnector);
      }
      catch (OpenDsException oe)
      {
        ex.add(oe);
      }
      String[] connectionHandlers = root.listConnectionHandlers();
      for (int i=0; i<connectionHandlers.length; i++)
      {
        try
        {
          ConnectionHandlerCfgClient connectionHandler =
            root.getConnectionHandler(connectionHandlers[i]);
          ls.add(getConnectionHandler(connectionHandler,
              connectionHandlers[i]));
        }
        catch (OpenDsException oe)
        {
          ex.add(oe);
        }
      }
      isSchemaEnabled = root.getGlobalConfiguration().isCheckSchema();

      String[] backendNames = root.listBackends();
      for (int i=0; i<backendNames.length; i++)
      {
        try
        {
          BackendCfgClient backend = root.getBackend(backendNames[i]);
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
          if (backend instanceof LocalDBBackendCfgClient)
          {
            type = BackendDescriptor.Type.LOCAL_DB;
            LocalDBBackendCfgClient db = (LocalDBBackendCfgClient)backend;
            String[] indexNames = db.listLocalDBIndexes();
            try
            {
              for (int j=0; j<indexNames.length; j++)
              {
                LocalDBIndexCfgClient index = db.getLocalDBIndex(indexNames[j]);
                indexes.add(new IndexDescriptor(
                    index.getAttribute().getNameOrOID(), index.getAttribute(),
                    null, index.getIndexType(), index.getIndexEntryLimit()));
              }
            }
            catch (OpenDsException oe)
            {
              ex.add(oe);
            }
            indexes.add(
                new IndexDescriptor("dn2id", null, null,
                    new TreeSet<IndexType>(), -1));
            indexes.add(
                new IndexDescriptor("id2children", null, null,
                    new TreeSet<IndexType>(), -1));
            indexes.add(
                new IndexDescriptor("id2subtree", null, null,
                    new TreeSet<IndexType>(), -1));

            String[] vlvIndexNames = db.listLocalDBVLVIndexes();
            try
            {
              for (int j=0; j<vlvIndexNames.length; j++)
              {
                LocalDBVLVIndexCfgClient index =
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
              ex.add(oe);
            }
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
          BackendDescriptor desc = new BackendDescriptor(backend.getBackendId(),
              baseDNs, indexes, vlvIndexes, -1, backend.isEnabled(), type);
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
          bs.add(desc);
        }
        catch (OpenDsException oe)
        {
          ex.add(oe);
        }
      }

      boolean isReplicationSecure = false;
      try
      {
        CryptoManagerCfgClient cryptoManager = root.getCryptoManager();
        isReplicationSecure = cryptoManager.isSSLEncryption();
      }
      catch (OpenDsException oe)
      {
        ex.add(oe);
      }


      replicationPort = -1;
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
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
            ReplicationServerCfgClient replicationServer =
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
              ls.add(connHandler);
            }
          }
          String[] domains = sync.listReplicationDomains();
          if (domains != null)
          {
            for (int i=0; i<domains.length; i++)
            {
              ReplicationDomainCfgClient domain =
                sync.getReplicationDomain(domains[i]);
              DN dn = domain.getBaseDN();
              for (BackendDescriptor backend : bs)
              {
                for (BaseDNDescriptor baseDN : backend.getBaseDns())
                {
                  if (baseDN.getDn().equals(dn))
                  {
                    baseDN.setType(BaseDNDescriptor.Type.REPLICATED);
                    baseDN.setReplicaID(domain.getServerId());
                  }
                }
              }
            }
          }
        }
        catch (OpenDsException oe)
        {
          ex.add(oe);
        }
      }


      try
      {
        RootDNCfgClient rootDN = root.getRootDN();
        String[] rootUsers = rootDN.listRootDNUsers();
        if (rootUsers != null)
        {
          for (int i=0; i < rootUsers.length; i++)
          {
            RootDNUserCfgClient rootUser = rootDN.getRootDNUser(rootUsers[i]);
            as.addAll(rootUser.getAlternateBindDN());
          }
        }
      }
      catch (OpenDsException oe)
      {
        ex.add(oe);
      }

      try
      {
        readSchema();
      }
      catch (OpenDsException oe)
      {
        ex.add(oe);
      }
    }
    catch (final Throwable t)
    {
      OnlineUpdateException oupe = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(t.toString()), t);
      ex.add(oupe);
    }
    for (OpenDsException oe : ex)
    {
      LOG.log(Level.WARNING, "Error reading configuration: "+oe, oe);
    }
    administrativeUsers = Collections.unmodifiableSet(as);
    listeners = Collections.unmodifiableSet(ls);
    backends = Collections.unmodifiableSet(bs);
    try
    {
      updateMonitorInformation(ctx, ex);
    }
    catch (Throwable t)
    {
      OnlineUpdateException oupe = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(t.toString()), t);
      ex.add(oupe);
    }
    exceptions = Collections.unmodifiableList(ex);

  }

  /**
   * Returns an array of monitoring attributes to be returned in the request.
   * @return an array of monitoring attributes to be returned in the request.
   */
  protected String[] getMonitoringAttributes()
  {
    return new String[] {
        "approx-older-change-not-synchronized-millis", "missing-changes",
        "base-dn", "server-id", "javaVersion", "currentConnections",
        "ds-backend-id", "ds-backend-entry-count", "ds-base-dn-entry-count"
    };
  }

  /**
   * Takes the provided search result and updates the monitoring information
   * accordingly.
   * @param sr the search result.
   * @param searchBaseDN the base search.
   * @throws NamingException if there is an error retrieving the values of the
   * search result.
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

    String dn = ConnectionUtils.getFirstValue(sr, "base-dn");
    String replicaId = ConnectionUtils.getFirstValue(sr, "server-id");

    if ((dn != null)  && (replicaId != null))
    {
      for (BackendDescriptor backend : backends)
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (Utilities.areDnsEqual(baseDN.getDn().toString(), dn) &&
              String.valueOf(baseDN.getReplicaID()).equals(replicaId))
          {
            try
            {
              baseDN.setAgeOfOldestMissingChange(
                  new Long(ConnectionUtils.getFirstValue(sr,
              "approx-older-change-not-synchronized-millis")));
            }
            catch (Throwable t)
            {
            }
            try
            {
              baseDN.setMissingChanges(new Integer(
                  ConnectionUtils.getFirstValue(sr, "missing-changes")));
            }
            catch (Throwable t)
            {
            }
          }
        }
      }
    }
    else
    {
      String backendID = ConnectionUtils.getFirstValue(sr,
          "ds-backend-id");
      String entryCount = ConnectionUtils.getFirstValue(sr,
          "ds-backend-entry-count");
      Set<String> baseDnEntries = ConnectionUtils.getValues(sr,
          "ds-base-dn-entry-count");
      if ((backendID != null) && ((entryCount != null) ||
          (baseDnEntries != null)))
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
    }
  }

  private void updateMonitorInformation(InitialLdapContext ctx,
      List<OpenDsException> ex)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        getMonitoringAttributes());
    String filter = "(objectclass=*)";

    try
    {
      LdapName jndiName = new LdapName("cn=monitor");

      NamingEnumeration monitorEntries = ctx.search(jndiName, filter, ctls);

      javaVersion = null;
      numberConnections = -1;

      while (monitorEntries.hasMore())
      {
        SearchResult sr = (SearchResult)monitorEntries.next();
        handleMonitoringSearchResult(sr, "cn=monitor");
      }
    }
    catch (NamingException ne)
    {
      OnlineUpdateException oue = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(ne.getMessage().toString()), ne);
      ex.add(oue);
    }
  }

  private ConnectionHandlerDescriptor getConnectionHandler(
      ConnectionHandlerCfgClient connHandler, String name)
  throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<InetAddress>(
        getInetAddressComparator());
    int port;

    ConnectionHandlerDescriptor.Protocol protocol;

    ConnectionHandlerDescriptor.State state = connHandler.isEnabled() ?
        ConnectionHandlerDescriptor.State.ENABLED :
          ConnectionHandlerDescriptor.State.DISABLED;

    if (connHandler instanceof LDAPConnectionHandlerCfgClient)
    {
      LDAPConnectionHandlerCfgClient ldap =
        (LDAPConnectionHandlerCfgClient)connHandler;
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
      if (v != null)
      {
        addresses.addAll(v);
      }
      port = ldap.getListenPort();
    }
    else if (connHandler instanceof JMXConnectionHandlerCfgClient)
    {
      JMXConnectionHandlerCfgClient jmx =
        (JMXConnectionHandlerCfgClient)connHandler;
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
    else if (connHandler instanceof LDIFConnectionHandlerCfgClient)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.LDIF;
      port = -1;
    }
    else if (connHandler instanceof SNMPConnectionHandlerCfgClient)
    {
      protocol = ConnectionHandlerDescriptor.Protocol.SNMP;
      SNMPConnectionHandlerCfgClient snmp =
        (SNMPConnectionHandlerCfgClient)connHandler;
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
      AdministrationConnectorCfgClient adminConnector) throws OpenDsException
  {
    SortedSet<InetAddress> addresses = new TreeSet<InetAddress>(
        getInetAddressComparator());

    ConnectionHandlerDescriptor.Protocol protocol =
      ConnectionHandlerDescriptor.Protocol.ADMINISTRATION_CONNECTOR;

    ConnectionHandlerDescriptor.State state =
      ConnectionHandlerDescriptor.State.ENABLED;


    SortedSet<InetAddress> v = adminConnector.getListenAddress();
    if (v != null)
    {
      addresses.addAll(v);
    }
    int port = adminConnector.getListenPort();

    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString());
  }
}
