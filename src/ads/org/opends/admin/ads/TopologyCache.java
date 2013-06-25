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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS
 */
package org.opends.admin.ads;

import static org.opends.messages.QuickSetupMessages
    .INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER;
import static org.opends.messages.QuickSetupMessages
    .INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;

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
  private final Set<ServerDescriptor> servers =
      new HashSet<ServerDescriptor>();
  private final Set<SuffixDescriptor> suffixes =
      new HashSet<SuffixDescriptor>();
  private final LinkedHashSet<PreferredConnection> preferredConnections =
      new LinkedHashSet<PreferredConnection>();
  private final TopologyCacheFilter filter = new TopologyCacheFilter();
  private final boolean isMultiThreaded = true;
  private final static int MULTITHREAD_TIMEOUT = 90 * 1000;
  private static final Logger LOG =
      Logger.getLogger(TopologyCache.class.getName());

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

      Set<ServerLoader> threadSet = new HashSet<ServerLoader>();
      for (Map<ServerProperty, Object> serverProperties : adsServers)
      {
        ServerLoader t = getServerLoader(serverProperties);
        if (isMultiThreaded)
        {
          t.start();
          threadSet.add(t);
        }
        else
        {
          t.run();
        }
      }
      if (isMultiThreaded)
      {
        joinThreadSet(threadSet);
      }
      /*
       * Try to consolidate things (even if the data is not complete).
       */

      HashMap<LdapName, Set<SuffixDescriptor>> hmSuffixes =
          new HashMap<LdapName, Set<SuffixDescriptor>>();
      for (ServerLoader loader : threadSet)
      {
        ServerDescriptor descriptor = loader.getServerDescriptor();
        for (ReplicaDescriptor replica : descriptor.getReplicas())
        {
          LOG.log(Level.INFO, "Handling replica with dn: "
              + replica.getSuffix().getDN());

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
              sufs = new HashSet<SuffixDescriptor>();
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
   *
   * @throws NamingException if an error occurs reading the replication
   * monitoring.
   */
  private void readReplicationMonitoring()
  {
    Set<ReplicaDescriptor> replicasToUpdate = new HashSet<ReplicaDescriptor>();
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
    for (ServerDescriptor server : getServers())
    {
      if (server.isReplicationServer())
      {
        // If is replication server, then at least we were able to read the
        // configuration, so assume that we might be able to read monitoring
        // (even if an exception occurred before).
        Set<ReplicaDescriptor> candidateReplicas =
            new HashSet<ReplicaDescriptor>();
        // It contains replication information: analyze it.
        String repServer = server.getReplicationServerHostPort();
        for (SuffixDescriptor suffix : getSuffixes())
        {
          Set<String> repServers = suffix.getReplicationServers();
          for (String r : repServers)
          {
            if (r.equalsIgnoreCase(repServer))
            {
              candidateReplicas.addAll(suffix.getReplicas());
              break;
            }
          }
        }
        if (!candidateReplicas.isEmpty())
        {
          Set<ReplicaDescriptor> updatedReplicas =
              new HashSet<ReplicaDescriptor>();
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

  /**
   * Sets the list of LDAP URLs and connection type that are preferred to be
   * used to connect to the servers. When we have a server to which we can
   * connect using a URL on the list we will try to use it.
   *
   * @param cnx the list of preferred connections.
   */
  public void setPreferredConnections(LinkedHashSet<PreferredConnection> cnx)
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
    return new LinkedHashSet<PreferredConnection>(preferredConnections);
  }

  /**
   * Returns a Set containing all the servers that are registered in the ADS.
   *
   * @return a Set containing all the servers that are registered in the ADS.
   */
  public Set<ServerDescriptor> getServers()
  {
    HashSet<ServerDescriptor> copy = new HashSet<ServerDescriptor>();
    copy.addAll(servers);
    return copy;
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
    HashSet<SuffixDescriptor> copy = new HashSet<SuffixDescriptor>();
    copy.addAll(suffixes);
    return copy;
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
        LOG.log(Level.INFO, ie + " caught and ignored", ie);
      }
      if (t.isAlive())
      {
        t.interrupt();
      }
    }
    Date endDate = new Date();
    long workingTime = endDate.getTime() - startDate.getTime();
    LOG.log(Level.INFO, "Loading ended at " + workingTime + " ms");
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
  public LinkedHashSet<Message> getErrorMessages()
  {
    Set<TopologyCacheException> exceptions =
        new HashSet<TopologyCacheException>();
    Set<ServerDescriptor> theServers = getServers();
    LinkedHashSet<Message> exceptionMsgs = new LinkedHashSet<Message>();
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
          if ((e.getCause() != null)
              && Utils.isCertificateException(e.getCause()))
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
          replicaId =
              new Integer(ConnectionUtils.getFirstValue(sr, "server-id"));
        }
        catch (Throwable t)
        {
          LOG.log(Level.WARNING, "Unexpected error reading replica ID: " + t,
              t);
        }

        for (ReplicaDescriptor replica : candidateReplicas)
        {
          if (Utils.areDnsEqual(dn, replica.getSuffix().getDN())
              && replica.isReplicated()
              && (replica.getReplicationId() == replicaId))
          {
            // This statistic is optional.
            String s = ConnectionUtils.getFirstValue(sr,
                "approx-older-change-not-synchronized-millis");
            if (s != null)
            {
              try
              {
                replica.setAgeOfOldestMissingChange(Long.valueOf(s));
              }
              catch (Throwable t)
              {
                LOG.log(Level.WARNING,
                    "Unexpected error reading age of oldest change: " + t, t);
              }
            }
            s = ConnectionUtils.getFirstValue(sr, "missing-changes");
            if (s != null)
            {
              try
              {
                replica.setMissingChanges(new Integer(s));
              }
              catch (Throwable t)
              {
                LOG.log(Level.WARNING,
                    "Unexpected error reading missing changes: " + t, t);
              }
            }
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
          LOG.log(Level.WARNING,
              "Unexpected error closing enumeration on monitor entries" + t, t);
        }
      }
      if (ctx != null)
      {
        ctx.close();
      }
    }
  }
}
