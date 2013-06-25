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
 *      Copyright 2008-2011 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock, AS.
 */

package org.opends.guitools.controlpanel.util;

import static org.opends.messages.AdminToolMessages.*;

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
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.ServerConstants;

/**
 * A class that reads the configuration and monitoring information using a
 * DirContext through LDAP.
 *
 */
public class ConfigFromDirContext extends ConfigReader
{
  private static final String DATABASE_ENVIRONMENT_SUFFIX =
    " Database Environment";
  private static final Logger LOG =
    Logger.getLogger(ConfigFromDirContext.class.getName());

  private CustomSearchResult rootMonitor;
  private CustomSearchResult jvmMemoryUsage;
  private CustomSearchResult systemInformation;
  private CustomSearchResult entryCaches;
  private CustomSearchResult workQueue;
  private CustomSearchResult versionMonitor;

  private boolean isLocal = true;

  private final Map<String, CustomSearchResult> hmConnectionHandlersMonitor =
    new HashMap<String, CustomSearchResult>();

  /**
   * The monitor root entry DN.
   */
  protected DN monitorDN = DN.nullDN();
  /**
   * The JVM memory usage monitoring entry DN.
   */
  protected DN jvmMemoryUsageDN = DN.nullDN();
  /**
   * The system information monitoring entry DN.
   */
  protected DN systemInformationDN = DN.nullDN();
  /**
   * The entry cache monitoring entry DN.
   */
  protected DN entryCachesDN = DN.nullDN();
  /**
   * The work queue monitoring entry DN.
   */
  protected DN workQueueDN = DN.nullDN();
  /**
   * The version monitoring entry DN.
   */
  protected DN versionDN = DN.nullDN();

  {
    try
    {
      monitorDN = DN.decode("cn=monitor");
      jvmMemoryUsageDN = DN.decode("cn=JVM Memory Usage,cn=monitor");
      systemInformationDN = DN.decode("cn=System Information,cn=monitor");
      entryCachesDN = DN.decode("cn=Entry Caches,cn=monitor");
      workQueueDN = DN.decode("cn=Work Queue,cn=monitor");
      versionDN = DN.decode("cn=Version,cn=monitor");
    }
    catch (Throwable t)
    {
      throw new RuntimeException("Could not decode DNs: "+t, t);
    }
  }

  /**
   * The date formatter to be used to parse GMT dates.
   */
  public static final SimpleDateFormat utcParser = new SimpleDateFormat(
      ServerConstants.DATE_FORMAT_GMT_TIME);
  {
    utcParser.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  /**
   * The date formatter to be used to format dates.
   */
  public static final DateFormat formatter = DateFormat.getDateTimeInstance();

  /**
   * Returns the monitoring entry for the entry caches.
   * @return the monitoring entry for the entry caches.
   */
  public CustomSearchResult getEntryCaches()
  {
    return entryCaches;
  }

  /**
   * Returns the monitoring entry for the JVM memory usage.
   * @return the monitoring entry for the JVM memory usage.
   */
  public CustomSearchResult getJvmMemoryUsage()
  {
    return jvmMemoryUsage;
  }

  /**
   * Returns the root entry of the monitoring tree.
   * @return the root entry of the monitoring tree.
   */
  public CustomSearchResult getRootMonitor()
  {
    return rootMonitor;
  }

  /**
   * Returns the version entry of the monitoring tree.
   * @return the version entry of the monitoring tree.
   */
  public CustomSearchResult getVersionMonitor()
  {
    return versionMonitor;
  }

  /**
   * Returns the monitoring entry for the system information.
   * @return the monitoring entry for the system information.
   */
  public CustomSearchResult getSystemInformation()
  {
    return systemInformation;
  }

  /**
   * Returns the monitoring entry for the work queue.
   * @return the monitoring entry for the work queue.
   */
  public CustomSearchResult getWorkQueue()
  {
    return workQueue;
  }

  /**
   * Sets whether this server represents the local instance or a remote server.
   * @param isLocal whether this server represents the local instance or a
   * remote server (in another machine or in another installation on the same
   * machine).
   */
  public void setIsLocal(boolean isLocal)
  {
    this.isLocal = isLocal;
  }

  /**
   * Returns <CODE>true</CODE> if we are trying to manage the local host and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are trying to manage the local host and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isLocal()
  {
    return isLocal;
  }

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
    Set<TaskEntry> ts = new HashSet<TaskEntry>();

    rootMonitor = null;
    jvmMemoryUsage = null;
    systemInformation = null;
    entryCaches = null;
    workQueue = null;
    versionMonitor = null;

    hmConnectionHandlersMonitor.clear();

    if (mustReadSchema())
    {
      try
      {
        readSchema(ctx);
        if (getSchema() != null)
        {
          // Update the schema: so that when we call the server code the
          // latest schema read on the server we are managing is used.
          DirectoryServer.setSchema(getSchema());
        }
      }
      catch (OpenDsException oe)
      {
        ex.add(oe);
      }
    }

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
      for (String connHandler : root.listConnectionHandlers())
      {
        try
        {
          ConnectionHandlerCfgClient connectionHandler =
              root.getConnectionHandler(connHandler);
          ls.add(getConnectionHandler(connectionHandler, connHandler));
        }
        catch (OpenDsException oe)
        {
          ex.add(oe);
        }
      }
      isSchemaEnabled = root.getGlobalConfiguration().isCheckSchema();

      for (String backendName : root.listBackends())
      {
        try
        {
          BackendCfgClient backend = root.getBackend(backendName);
          Set<BaseDNDescriptor> baseDNs = new HashSet<BaseDNDescriptor>();
          for (DN dn : backend.getBaseDN())
          {
            BaseDNDescriptor baseDN =
              new BaseDNDescriptor(BaseDNDescriptor.Type.NOT_REPLICATED, dn,
                  null, -1, -1, -1);
            baseDNs.add(baseDN);
          }
          Set<IndexDescriptor> indexes = new HashSet<IndexDescriptor>();
          Set<VLVIndexDescriptor> vlvIndexes =
            new HashSet<VLVIndexDescriptor>();
          BackendDescriptor.Type type;
          if (backend instanceof LocalDBBackendCfgClient)
          {
            type = BackendDescriptor.Type.LOCAL_DB;
            LocalDBBackendCfgClient db = (LocalDBBackendCfgClient)backend;
            try
            {
              for (String indexName : db.listLocalDBIndexes())
              {
                LocalDBIndexCfgClient index = db.getLocalDBIndex(indexName);
                indexes.add(new IndexDescriptor(
                    index.getAttribute().getNameOrOID(), index.getAttribute(),
                    null, index.getIndexType(), index.getIndexEntryLimit()));
              }
            }
            catch (OpenDsException oe)
            {
              ex.add(oe);
            }
            indexes.add(new IndexDescriptor("dn2id", null, null,
                new TreeSet<IndexType>(), -1));
            indexes.add(new IndexDescriptor("id2children", null, null,
                new TreeSet<IndexType>(), -1));
            indexes.add(new IndexDescriptor("id2subtree", null, null,
                new TreeSet<IndexType>(), -1));

            try
            {
              for (String vlvIndexName : db.listLocalDBVLVIndexes())
              {
                LocalDBVLVIndexCfgClient index =
                  db.getLocalDBVLVIndex(vlvIndexName);
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
              Set<CustomSearchResult> emptySet = Collections.emptySet();
              ConnectionHandlerDescriptor connHandler =
                new ConnectionHandlerDescriptor(
                    new HashSet<InetAddress>(),
                    replicationPort,
                    protocol,
                    ConnectionHandlerDescriptor.State.ENABLED,
                    "Multimaster Synchronization",
                    emptySet);
              ls.add(connHandler);
            }
          }
          String[] domains = sync.listReplicationDomains();
          if (domains != null)
          {
            for (String domain2 : domains)
            {
              ReplicationDomainCfgClient domain =
                sync.getReplicationDomain(domain2);
              DN dn = domain.getBaseDN();
              for (BackendDescriptor backend : bs)
              {
                for (BaseDNDescriptor baseDN : backend.getBaseDns())
                {
                  if (baseDN.getDn().equals(dn))
                  {
                    baseDN.setType(sync.isEnabled() ?
                        BaseDNDescriptor.Type.REPLICATED :
                          BaseDNDescriptor.Type.DISABLED);
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
          for (String rootUser2 : rootUsers)
          {
            RootDNUserCfgClient rootUser = rootDN.getRootDNUser(rootUser2);
            as.addAll(rootUser.getAlternateBindDN());
          }
        }
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
      LOG.log(Level.WARNING, "Error reading monitoring: "+t, t);
      OnlineUpdateException oupe = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(t.toString()), t);
      ex.add(oupe);
    }
    try
    {
      updateTaskInformation(ctx, ex, ts);
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error reading task information: "+t, t);
      OnlineUpdateException oupe = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(t.toString()), t);
      ex.add(oupe);
    }
    taskEntries = Collections.unmodifiableSet(ts);
    for (ConnectionHandlerDescriptor ch : getConnectionHandlers())
    {
      ch.setMonitoringEntries(getMonitoringEntries(ch));
    }
    if (adminConnector != null)
    {
      adminConnector.setMonitoringEntries(getMonitoringEntries(adminConnector));
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
        "*"
    };
  }

  /**
   * Reads the schema from the files.
   * @param ctx the connection to be used to load the schema.
   * @throws OpenDsException if an error occurs reading the schema.
   */
  private void readSchema(InitialLdapContext ctx) throws OpenDsException
  {
    if (isLocal)
    {
      super.readSchema();
    }
    else
    {
      RemoteSchemaLoader loader = new RemoteSchemaLoader();
      try
      {
        loader.readSchema(ctx);
      }
      catch (NamingException ne)
      {
        throw new OnlineUpdateException(
            ERR_READING_SCHEMA_LDAP.get(ne.toString()), ne);
      }
      schema = loader.getSchema();
    }
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

    String dn = ConnectionUtils.getFirstValue(sr, "domain-name");
    String replicaId = ConnectionUtils.getFirstValue(sr, "server-id");
    String missingChanges = ConnectionUtils.getFirstValue(sr,
        "missing-changes");

    if ((dn != null)  && (replicaId != null) && (missingChanges != null))
    {
      for (BackendDescriptor backend : backends)
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            if (baseDN.getDn().equals(DN.decode(dn)) &&
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
                baseDN.setMissingChanges(new Integer(missingChanges));
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
      else
      {
        // Check if it is the DB monitor entry
        String cn = ConnectionUtils.getFirstValue(sr, "cn");
        if ((cn != null) && cn.endsWith(DATABASE_ENVIRONMENT_SUFFIX))
        {
          String monitorBackendID = cn.substring(0, cn.length() -
              DATABASE_ENVIRONMENT_SUFFIX.length());
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
//          Assume it is a connection handler
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
   * @param sr the search result.
   * @param searchBaseDN the base search.
   * @param taskEntries the collection of TaskEntries to be updated.
   * @param ex the list of exceptions to be updated if an error occurs.
   * @throws NamingException if there is an error retrieving the values of the
   * search result.
   */
  private void handleTaskSearchResult(SearchResult sr,
      String searchBaseDN,
      Collection<TaskEntry> taskEntries, List<OpenDsException> ex)
  throws NamingException
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
    ctls.setReturningAttributes(
        getMonitoringAttributes());
    String filter = "(objectclass=*)";

    try
    {
      LdapName jndiName = new LdapName("cn=monitor");

      NamingEnumeration<SearchResult> monitorEntries =
        ctx.search(jndiName, filter, ctls);

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
      OnlineUpdateException oue = new OnlineUpdateException(
          ERR_READING_CONFIG_LDAP.get(ne.getMessage().toString()), ne);
      ex.add(oue);
    }
  }

  /**
   * Updates the provided list of TaskEntry with the task entries found in
   * a server.
   * @param ctx the connection to the server.
   * @param ex the list of exceptions encountered while retrieving the task
   * entries.
   * @param ts the list of task entries to be updated.
   */
  public void updateTaskInformation(InitialLdapContext ctx,
      List<OpenDsException> ex, Collection<TaskEntry> ts)
  {
    // Read monitoring information: since it is computed, it is faster
    // to get everything in just one request.
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        getMonitoringAttributes());
    String filter = "(objectclass=ds-task)";

    try
    {
      LdapName jndiName = new LdapName(ConfigConstants.DN_TASK_ROOT);

      NamingEnumeration<SearchResult> taskEntries =
        ctx.search(jndiName, filter, ctls);

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
      addAll(addresses, ldap.getListenAddress());
      port = ldap.getListenPort();
    }
    else if (connHandler instanceof HTTPConnectionHandlerCfgClient)
    {
      HTTPConnectionHandlerCfgClient http =
          (HTTPConnectionHandlerCfgClient) connHandler;
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
      addAll(addresses, jmx.getListenAddress());
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
      addAll(addresses, snmp.getListenAddress());
      port = snmp.getListenPort();
    }
    else
    {
      protocol = ConnectionHandlerDescriptor.Protocol.OTHER;
      port = -1;
    }
    Set<CustomSearchResult> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        name, emptySet);
  }

  private <T> void addAll(Collection<T> target, Collection<T> source)
  {
    if (source != null)
    {
      target.addAll(source);
    }
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

    addAll(addresses, adminConnector.getListenAddress());
    int port = adminConnector.getListenPort();

    Set<CustomSearchResult> emptySet = Collections.emptySet();
    return new ConnectionHandlerDescriptor(addresses, port, protocol, state,
        INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get().toString(), emptySet);
  }

  private boolean isRootMonitor(CustomSearchResult csr)
  throws OpenDsException
  {
    return monitorDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isVersionMonitor(CustomSearchResult csr)
  throws OpenDsException
  {
    return versionDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isSystemInformation(CustomSearchResult csr)
  throws OpenDsException
  {
    return systemInformationDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isJvmMemoryUsage(CustomSearchResult csr)
  throws OpenDsException
  {
    return jvmMemoryUsageDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isWorkQueue(CustomSearchResult csr)
  throws OpenDsException
  {
    return workQueueDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isEntryCaches(CustomSearchResult csr)
  throws OpenDsException
  {
    return entryCachesDN.equals(DN.decode(csr.getDN()));
  }

  private boolean isConnectionHandler(CustomSearchResult csr)
  throws OpenDsException
  {
    boolean isConnectionHandler = false;
    DN dn = DN.decode(csr.getDN());
    DN parent = dn.getParent();
    if ((parent != null) && parent.equals(monitorDN))
    {
      List<?> vs = csr.getAttributeValues("cn");
      if ((vs != null) && !vs.isEmpty())
      {
        String cn = (String)vs.iterator().next();
        String statistics = " Statistics";
        if (cn.endsWith(statistics))
        {
          isConnectionHandler = true;
        }
      }
    }
    return isConnectionHandler;
  }

  private static boolean isTaskEntry(CustomSearchResult csr)
  throws OpenDsException
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
   * Commodity method to get the string representation to be used in the
   * hash maps as key.
   * @param value the value to be transformed into a key for a hash map.
   * @return the string representation to be used in the hash maps as key.
   */
  private String getKey(String value)
  {
    return value.toLowerCase();
  }

  private Set<CustomSearchResult>getMonitoringEntries(
      ConnectionHandlerDescriptor ch)
  {
    Set<CustomSearchResult> monitorEntries = new HashSet<CustomSearchResult>();
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
        if (key.indexOf(getKey("port "+ch.getPort())) != -1)
        {
          boolean hasAllAddresses = true;
          for (InetAddress a : ch.getAddresses())
          {
            if (key.indexOf(getKey(a.getHostAddress())) == -1)
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
