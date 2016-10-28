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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.HostPort;

import static com.forgerock.opendj.cli.Utils.*;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class allows to read the configuration of the different servers that are
 * registered in a given ADS server. It provides a read only view of the
 * configuration of the servers and of the replication topologies that might be
 * configured between them.
 */
public class TopologyCache
{
  private final ADSContext adsContext;
  private final ApplicationTrustManager trustManager;
  private final int timeout;
  private final DN bindDN;
  private final String bindPwd;
  private final Set<ServerDescriptor> servers = new HashSet<>();
  private final Set<SuffixDescriptor> suffixes = new HashSet<>();
  private final Set<PreferredConnection> preferredConnections = new LinkedHashSet<>();
  private final TopologyCacheFilter filter = new TopologyCacheFilter();
  private static final int MULTITHREAD_TIMEOUT = 90 * 1000;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Constructor of the TopologyCache.
   *
   * @param adsContext the adsContext to the ADS registry.
   * @param trustManager the ApplicationTrustManager that must be used to trust
   * certificates when we create connections to the registered servers to read
   * their configuration.
   * @param timeout the timeout to establish the connection in milliseconds.
   * Use {@code 0} to express no timeout.
   */
  public TopologyCache(ADSContext adsContext,
                       ApplicationTrustManager trustManager,
                       int timeout)
  {
    this.adsContext = adsContext;
    this.trustManager = trustManager;
    this.timeout = timeout;
    ConnectionWrapper conn = adsContext.getConnection();
    bindDN = conn.getBindDn();
    bindPwd = conn.getBindPassword();
  }

  /**
   * Reads the configuration of the registered servers.
   *
   * @throws TopologyCacheException if there is an issue reading the
   * configuration of the registered servers.
   */
  public void reloadTopology() throws TopologyCacheException
  {
    suffixes.clear();
    servers.clear();
    try
    {
      Set<Map<ServerProperty, Object>> adsServers =
          adsContext.readServerRegistry();

      Set<ServerLoader> threadSet = new HashSet<>();
      for (Map<ServerProperty, Object> serverProperties : adsServers)
      {
        ServerLoader t = getServerLoader(serverProperties);
        t.start();
        threadSet.add(t);
      }
      joinThreadSet(threadSet);

      // Try to consolidate things (even if the data is not complete)
      Map<DN, Set<SuffixDescriptor>> hmSuffixes = new HashMap<>();
      for (ServerLoader loader : threadSet)
      {
        ServerDescriptor descriptor = loader.getServerDescriptor();
        for (ReplicaDescriptor replica : descriptor.getReplicas())
        {
          DN dn = replica.getSuffix().getDN();
          logger.info(LocalizableMessage.raw("Handling replica with dn: " + dn));

          Set<SuffixDescriptor> sufs = hmSuffixes.get(dn);
          SuffixDescriptor suffix = findSuffix(replica, sufs);
          if (suffix != null)
          {
            suffix.addReplica(replica);
            replica.setSuffix(suffix);
          }
          else
          {
            if (sufs == null)
            {
              sufs = new HashSet<>();
              hmSuffixes.put(dn, sufs);
            }
            sufs.add(replica.getSuffix());
            suffixes.add(replica.getSuffix());
          }
        }
        servers.add(descriptor);
      }

      // Figure out the replication monitoring if it is required.
      if (getFilter().searchMonitoringInformation())
      {
        readReplicationMonitoring();
      }
    }
    catch (ADSContextException ade)
    {
      throw new TopologyCacheException(ade);
    }
    catch (Throwable t)
    {
      throw new TopologyCacheException(TopologyCacheException.Type.BUG, t);
    }
  }

  private SuffixDescriptor findSuffix(ReplicaDescriptor replica, Set<SuffixDescriptor> sufs)
  {
    if (sufs != null)
    {
      for (SuffixDescriptor suffix : sufs)
      {
        for (HostPort replicationServer : suffix.getReplicationServers())
        {
          if (replica.getReplicationServers().contains(replicationServer))
          {
            return suffix;
          }
        }
      }
    }
    return null;
  }

  /** Reads the replication monitoring. */
  private void readReplicationMonitoring()
  {
    Set<ReplicaDescriptor> replicasToUpdate = getReplicasToUpdate();
    for (ServerDescriptor server : putQueriedReplicaFirst(this.servers))
    {
      if (server.isReplicationServer())
      {
        // If is replication server, then at least we were able to read the
        // configuration, so assume that we might be able to read monitoring
        // (even if an exception occurred before).
        Set<ReplicaDescriptor> candidateReplicas = getCandidateReplicas(server);
        if (!candidateReplicas.isEmpty())
        {
          Set<ReplicaDescriptor> updatedReplicas = new HashSet<>();
          try
          {
            updateReplicas(server, candidateReplicas, updatedReplicas);
          }
          catch (IOException e)
          {
            server.setLastException(new TopologyCacheException(
                TopologyCacheException.Type.GENERIC_READING_SERVER, e));
          }
          replicasToUpdate.removeAll(updatedReplicas);
        }
      }

      if (replicasToUpdate.isEmpty())
      {
        break;
      }
    }
  }

  /** Put first in the list the replica which host/port was provided on the command line. */
  private List<ServerDescriptor> putQueriedReplicaFirst(Set<ServerDescriptor> servers)
  {
    List<ServerDescriptor> results = new ArrayList<>(servers);
    for (Iterator<ServerDescriptor> it = results.iterator(); it.hasNext();)
    {
      ServerDescriptor server = it.next();
      if (adsContext.getHostPort().equals(server.getHostPort(true)))
      {
        it.remove();
        results.add(0, server);
        break; // avoids any ConcurrentModificationException
      }
    }
    return results;
  }

  private Set<ReplicaDescriptor> getReplicasToUpdate()
  {
    Set<ReplicaDescriptor> replicasToUpdate = new HashSet<>();
    for (ServerDescriptor server : getServers())
    {
      for (ReplicaDescriptor replica : server.getReplicas())
      {
        if (replica.isReplicated())
        {
          replicasToUpdate.add(replica);
        }
      }
    }
    return replicasToUpdate;
  }

  private Set<ReplicaDescriptor> getCandidateReplicas(ServerDescriptor server)
  {
    Set<ReplicaDescriptor> candidateReplicas = new HashSet<>();
    // It contains replication information: analyze it.
    HostPort repServer = server.getReplicationServerHostPort();
    for (SuffixDescriptor suffix : getSuffixes())
    {
      if (suffix.getReplicationServers().contains(repServer))
      {
        candidateReplicas.addAll(suffix.getReplicas());
      }
    }
    return candidateReplicas;
  }

  /**
   * Sets the list of LDAP URLs and connection type that are preferred to be
   * used to connect to the servers. When we have a server to which we can
   * connect using a URL on the list we will try to use it.
   *
   * @param cnx the list of preferred connections.
   */
  public void setPreferredConnections(Set<PreferredConnection> cnx)
  {
    preferredConnections.clear();
    preferredConnections.addAll(cnx);
  }

  /**
   * Returns the list of LDAP URLs and connection type that are preferred to be
   * used to connect to the servers. If a URL is on this list, when we have a
   * server to which we can connect using that URL and the associated connection
   * type we will try to use it.
   *
   * @return the list of preferred connections.
   */
  public LinkedHashSet<PreferredConnection> getPreferredConnections()
  {
    return new LinkedHashSet<>(preferredConnections);
  }

  /**
   * Returns a Set containing all the servers that are registered in the ADS.
   *
   * @return a Set containing all the servers that are registered in the ADS.
   */
  public Set<ServerDescriptor> getServers()
  {
    return new HashSet<>(servers);
  }

  /**
   * Returns a Set containing the suffixes (replication topologies) that could
   * be retrieved after the last call to reloadTopology.
   *
   * @return a Set containing the suffixes (replication topologies) that could
   * be retrieved after the last call to reloadTopology.
   */
  public Set<SuffixDescriptor> getSuffixes()
  {
    return new HashSet<>(suffixes);
  }

  /**
   * Returns the filter to be used when retrieving information.
   *
   * @return the filter to be used when retrieving information.
   */
  public TopologyCacheFilter getFilter()
  {
    return filter;
  }

  /**
   * Method used to wait at most a certain time (MULTITHREAD_TIMEOUT) for the
   * different threads to finish.
   *
   * @param threadSet the list of threads (we assume that they are started) that
   * we must wait for.
   */
  private void joinThreadSet(Set<ServerLoader> threadSet)
  {
    Date startDate = new Date();
    for (ServerLoader t : threadSet)
    {
      long timeToJoin = MULTITHREAD_TIMEOUT - System.currentTimeMillis()
          + startDate.getTime();
      try
      {
        if (timeToJoin > 0)
        {
          t.join(MULTITHREAD_TIMEOUT);
        }
      }
      catch (InterruptedException ie)
      {
        logger.info(LocalizableMessage.raw(ie + " caught and ignored", ie));
      }
      if (t.isAlive())
      {
        t.interrupt();
      }
    }
    Date endDate = new Date();
    long workingTime = endDate.getTime() - startDate.getTime();
    logger.info(LocalizableMessage.raw("Loading ended at " + workingTime + " ms"));
  }

  /**
   * Creates a ServerLoader object based on the provided server properties.
   *
   * @param serverProperties the server properties to be used to generate the
   * ServerLoader.
   * @return a ServerLoader object based on the provided server properties.
   */
  private ServerLoader getServerLoader(
      Map<ServerProperty, Object> serverProperties)
  {
    return new ServerLoader(serverProperties, bindDN, bindPwd,
        trustManager == null ? null : trustManager.createCopy(),
        timeout, getPreferredConnections(), getFilter());
  }

  /**
   * Returns the adsContext used by this TopologyCache.
   *
   * @return the adsContext used by this TopologyCache.
   */
  public ADSContext getAdsContext()
  {
    return adsContext;
  }

  /**
   * Returns a set of error messages encountered in the TopologyCache.
   *
   * @return a set of error messages encountered in the TopologyCache.
   */
  public Set<LocalizableMessage> getErrorMessages()
  {
    Set<TopologyCacheException> exceptions = new HashSet<>();
    Set<ServerDescriptor> theServers = getServers();
    Set<LocalizableMessage> exceptionMsgs = new LinkedHashSet<>();
    for (ServerDescriptor server : theServers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      switch (e.getType())
      {
        case NOT_GLOBAL_ADMINISTRATOR:
          exceptionMsgs.add(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());

          break;
        case GENERIC_CREATING_CONNECTION:
          if (isCertificateException(e.getCause()))
          {
            exceptionMsgs.add(
                INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
                e.getHostPort(), e.getCause().getMessage()));
          }
          else
          {
            exceptionMsgs.add(Utils.getMessage(e));
          }
          break;
        default:
          exceptionMsgs.add(Utils.getMessage(e));
      }
    }
    return exceptionMsgs;
  }

  /**
   * Updates the monitoring information of the provided replicas using the
   * information located in cn=monitor of a given replication server.
   *
   * @param replicationServer the replication server.
   * @param candidateReplicas the collection of replicas that must be updated.
   * @param updatedReplicas the collection of replicas that are actually
   * updated. This list is updated by the method.
   */
  private void updateReplicas(ServerDescriptor replicationServer,
                              Collection<ReplicaDescriptor> candidateReplicas,
                              Collection<ReplicaDescriptor> updatedReplicas)
      throws IOException
  {
    ServerLoader loader = getServerLoader(replicationServer.getAdsProperties());
    // only replicas have "server-id", but not replication servers
    SearchRequest request = newSearchRequest("cn=monitor", WHOLE_SUBTREE, "(&(missing-changes=*)(server-id=*))",
        "domain-name",
        "server-id",
        "missing-changes",
        "approx-older-change-not-synchronized-millis");
    try (ConnectionWrapper conn = loader.createConnectionWrapper();
        ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        final DN dn = sr.parseAttribute("domain-name").asDN();
        int replicaId = -1;
        try
        {
          replicaId = sr.parseAttribute("server-id").asInteger();
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw("Unexpected error reading replica ID: " + t, t));
        }

        for (ReplicaDescriptor replica : candidateReplicas)
        {
          if (replica.isReplicated()
              && dn.equals(replica.getSuffix().getDN())
              && replica.getServerId() == replicaId)
          {
            // This statistic is optional.
            setAgeOfOldestMissingChange(replica, sr);
            setMissingChanges(replica, sr);
            updatedReplicas.add(replica);
            break;
          }
        }
      }
    }
    catch (EntryNotFoundException ignored)
    {
      // no replicas updated this time. Try with another server higher up the stack.
    }
  }

  private void setMissingChanges(ReplicaDescriptor replica, SearchResultEntry sr)
  {
    Integer value = sr.parseAttribute("missing-changes").asInteger();
    if (value != null)
    {
      try
      {
        replica.setMissingChanges(value);
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw(
            "Unexpected error reading missing changes: " + t, t));
      }
    }
  }

  private void setAgeOfOldestMissingChange(ReplicaDescriptor replica, SearchResultEntry sr)
  {
    try
    {
      final Long value = sr.parseAttribute("approx-older-change-not-synchronized-millis").asLong();
      if (value != null)
      {
        replica.setAgeOfOldestMissingChange(value);
      }
    }
    catch (LocalizedIllegalArgumentException t)
    {
      logger.warn(LocalizableMessage.raw("Unexpected error reading age of oldest change: " + t, t));
    }
  }

  @Override
  public String toString()
  {
    List<SuffixDescriptor> sortedSuffixes = new ArrayList<>(suffixes);
    Collections.sort(sortedSuffixes, new Comparator<SuffixDescriptor>()
    {
      @Override
      public int compare(SuffixDescriptor suffix1, SuffixDescriptor suffix2)
      {
        return suffix1.getDN().compareTo(suffix2.getDN());
      }
    });

    final StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("\n");
    sb.append("Suffix DN,Server,Entries,Replication enabled,DS ID,RS ID,RS Port,M.C.,A.O.M.C.,Security\n");
    for (SuffixDescriptor suffix : sortedSuffixes)
    {
      List<ReplicaDescriptor> sortedReplicas = new ArrayList<>(suffix.getReplicas());
      Collections.sort(sortedReplicas, new Comparator<ReplicaDescriptor>()
      {
        @Override
        public int compare(ReplicaDescriptor replica1, ReplicaDescriptor replica2)
        {
          HostPort hp1 = replica1.getServer().getHostPort(true);
          HostPort hp2 = replica2.getServer().getHostPort(true);
          return hp1.toString().compareTo(hp2.toString());
        }
      });
      for (ReplicaDescriptor replica : sortedReplicas)
      {
        ServerDescriptor server = replica.getServer();
        boolean isReplEnabled = server.isReplicationEnabled();
        boolean secureReplication = server.isReplicationSecure();
        sb.append(suffix.getDN()).append(",")
          .append(server.getHostPort(true)).append(",")
          .append(replica.getEntries()).append(",")
          .append(isReplEnabled).append(",")
          .append(replica.getServerId()).append(",")
          .append(orBlank(server.getReplicationServerId())).append(",")
          .append(orBlank(server.getReplicationServerPort())).append(",")
          .append(replica.getMissingChanges()).append(",")
          .append(orBlank(replica.getAgeOfOldestMissingChange())).append(",")
          .append(secureReplication ? secureReplication : "")
          .append("\n");
      }
    }
    return sb.toString();
  }

  private Object orBlank(long value)
  {
    return value!=-1 ? value : "";
  }
}
