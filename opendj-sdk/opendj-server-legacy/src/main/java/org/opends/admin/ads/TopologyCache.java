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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.admin.ads;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.quicksetup.util.Utils;

import static com.forgerock.opendj.cli.Utils.*;

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
  private final String bindDN;
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
    bindDN = ConnectionUtils.getBindDN(adsContext.getDirContext());
    bindPwd = ConnectionUtils.getBindPassword(adsContext.getDirContext());
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
      /*
       * Try to consolidate things (even if the data is not complete).
       */

      HashMap<LdapName, Set<SuffixDescriptor>> hmSuffixes = new HashMap<>();
      for (ServerLoader loader : threadSet)
      {
        ServerDescriptor descriptor = loader.getServerDescriptor();
        for (ReplicaDescriptor replica : descriptor.getReplicas())
        {
          logger.info(LocalizableMessage.raw("Handling replica with dn: "
              + replica.getSuffix().getDN()));

          boolean suffixFound = false;
          LdapName dn = new LdapName(replica.getSuffix().getDN());
          Set<SuffixDescriptor> sufs = hmSuffixes.get(dn);
          if (sufs != null)
          {
            Iterator<SuffixDescriptor> it = sufs.iterator();
            while (it.hasNext() && !suffixFound)
            {
              SuffixDescriptor suffix = it.next();
              Iterator<String> it2 = suffix.getReplicationServers().iterator();
              while (it2.hasNext() && !suffixFound)
              {
                if (replica.getReplicationServers().contains(it2.next()))
                {
                  suffixFound = true;
                  Set<ReplicaDescriptor> replicas = suffix.getReplicas();
                  replicas.add(replica);
                  suffix.setReplicas(replicas);
                  replica.setSuffix(suffix);
                }
              }
            }
          }
          if (!suffixFound)
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

  /**
   * Returns the trust manager used by this class.
   *
   * @return the trust manager used by this class.
   */
  public ApplicationTrustManager getTrustManager()
  {
    return trustManager;
  }

  /**
   * Returns the timeout to establish the connection in milliseconds.
   *
   * @return the timeout to establish the connection in milliseconds. Returns
   * {@code 0} to express no timeout.
   */
  public int getConnectTimeout()
  {
    return timeout;
  }

  /**
   * Reads the replication monitoring.
   */
  private void readReplicationMonitoring()
  {
    Set<ReplicaDescriptor> replicasToUpdate = getReplicasToUpdate();
    for (ServerDescriptor server : getServers())
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
          catch (NamingException ne)
          {
            server.setLastException(new TopologyCacheException(
                TopologyCacheException.Type.GENERIC_READING_SERVER, ne));
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
    String repServer = server.getReplicationServerHostPort();
    for (SuffixDescriptor suffix : getSuffixes())
    {
      if (containsIgnoreCase(suffix.getReplicationServers(), repServer))
      {
        candidateReplicas.addAll(suffix.getReplicas());
      }
    }
    return candidateReplicas;
  }

  private boolean containsIgnoreCase(Set<String> col, String toFind)
  {
    for (String s : col)
    {
      if (s.equalsIgnoreCase(toFind))
      {
        return true;
      }
    }
    return false;
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
        timeout,
        getPreferredConnections(), getFilter());
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
    /*
     * Check the exceptions and see if we throw them or not.
     */
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
      throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[]
        {
          "approx-older-change-not-synchronized-millis", "missing-changes",
          "domain-name", "server-id"
        });

    InitialLdapContext ctx = null;
    NamingEnumeration<SearchResult> monitorEntries = null;
    try
    {
      ServerLoader loader =
          getServerLoader(replicationServer.getAdsProperties());
      ctx = loader.createContext();

      monitorEntries = ctx.search(
          new LdapName("cn=monitor"), "(missing-changes=*)", ctls);

      while (monitorEntries.hasMore())
      {
        SearchResult sr = monitorEntries.next();

        String dn = ConnectionUtils.getFirstValue(sr, "domain-name");
        int replicaId = -1;
        try
        {
          String sid = ConnectionUtils.getFirstValue(sr, "server-id");
          if (sid == null)
          {
            // This is not a replica, but a replication server. Skip it
            continue;
          }
          replicaId = Integer.valueOf(sid);
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw("Unexpected error reading replica ID: " + t,
              t));
        }

        for (ReplicaDescriptor replica : candidateReplicas)
        {
          if (Utils.areDnsEqual(dn, replica.getSuffix().getDN())
              && replica.isReplicated()
              && replica.getReplicationId() == replicaId)
          {
            // This statistic is optional.
            setAgeOfOldestMissingChange(replica, sr);
            setMissingChanges(replica, sr);
            updatedReplicas.add(replica);
          }
        }
      }
    }
    catch (NameNotFoundException nse)
    {
    }
    finally
    {
      if (monitorEntries != null)
      {
        try
        {
          monitorEntries.close();
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw(
              "Unexpected error closing enumeration on monitor entries" + t, t));
        }
      }
      if (ctx != null)
      {
        ctx.close();
      }
    }
  }

  private void setMissingChanges(ReplicaDescriptor replica, SearchResult sr) throws NamingException
  {
    String s = ConnectionUtils.getFirstValue(sr, "missing-changes");
    if (s != null)
    {
      try
      {
        replica.setMissingChanges(Integer.valueOf(s));
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw(
            "Unexpected error reading missing changes: " + t, t));
      }
    }
  }

  private void setAgeOfOldestMissingChange(ReplicaDescriptor replica, SearchResult sr) throws NamingException
  {
    String s = ConnectionUtils.getFirstValue(sr, "approx-older-change-not-synchronized-millis");
    if (s != null)
    {
      try
      {
        replica.setAgeOfOldestMissingChange(Long.valueOf(s));
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw(
            "Unexpected error reading age of oldest change: " + t, t));
      }
    }
  }
}
