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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.util.ConnectionUtils;

/**
 * The object of this class represent an OpenDS server.
 */
public class ServerDescriptor
{
  private Map<ADSContext.ServerProperty, Object> adsProperties =
    new HashMap<ADSContext.ServerProperty, Object>();
  private Set<ReplicaDescriptor> replicas = new HashSet<ReplicaDescriptor>();
  private Map<ServerProperty, Object> serverProperties =
    new HashMap<ServerProperty, Object>();
  private TopologyCacheException lastException;

  /**
   * Enumeration containing the different server properties that we can keep in
   * the ServerProperty object.
   */
  public enum ServerProperty
  {
    /**
     * The associated value is a String.
     */
    HOST_NAME,
    /**
     * The associated value is an ArrayList of Integer.
     */
    LDAP_PORT,
    /**
     * The associated value is an ArrayList of Integer.
     */
    LDAPS_PORT,
    /**
     * The associated value is an ArrayList of Boolean.
     */
    LDAP_ENABLED,
    /**
     * The associated value is an ArrayList of Boolean.
     */
    LDAPS_ENABLED,
    /**
     * The associated value is an ArrayList of Integer.
     */
    JMX_PORT,
    /**
     * The associated value is an ArrayList of Integer.
     */
    JMXS_PORT,
    /**
     * The associated value is an ArrayList of Boolean.
     */
    JMX_ENABLED,
    /**
     * The associated value is an ArrayList of Boolean.
     */
    JMXS_ENABLED,
    /**
     * The associated value is an Integer.
     */
    REPLICATION_SERVER_PORT,
    /**
     * The associated value is a Boolean.
     */
    IS_REPLICATION_SERVER,
    /**
     * The associated value is a Boolean.
     */
    IS_REPLICATION_ENABLED,
    /**
     * List of servers specified in the Replication Server configuration.
     * This is a Set of String.
     */
    EXTERNAL_REPLICATION_SERVERS,
    /**
     * The associated value is an Integer.
     */
    REPLICATION_SERVER_ID
  }
  private static final Logger LOG =
    Logger.getLogger(ServerDescriptor.class.getName());

  private ServerDescriptor()
  {
  }

  /**
   * Returns the replicas contained on the server.
   * @return the replicas contained on the server.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    Set<ReplicaDescriptor> copy = new HashSet<ReplicaDescriptor>();
    copy.addAll(replicas);
    return copy;
  }

  /**
   * Sets the replicas contained on the server.
   * @param replicas the replicas contained on the server.
   */
  public void setReplicas(Set<ReplicaDescriptor> replicas)
  {
    this.replicas.clear();
    this.replicas.addAll(replicas);
  }

  /**
   * Returns a Map containing the ADS properties of the server.
   * @return a Map containing the ADS properties of the server.
   */
  public Map<ADSContext.ServerProperty, Object> getAdsProperties()
  {
    return adsProperties;
  }

  /**
   * Returns a Map containing the properties of the server.
   * @return a Map containing the properties of the server.
   */
  public Map<ServerProperty, Object> getServerProperties()
  {
    return serverProperties;
  }

  /**
   * Tells whether this server is registered in the ADS or not.
   * @return <CODE>true</CODE> if the server is registered in the ADS and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isRegistered()
  {
    return !adsProperties.isEmpty();
  }

  /**
   * Sets the ADS properties of the server.
   * @param adsProperties a Map containing the ADS properties of the server.
   */
  public void setAdsProperties(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    this.adsProperties = adsProperties;
  }

  /**
   * Returns the host name of the server.
   * @return the host name of the server.
   */
  public String getHostName()
  {
    String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
    if (host == null)
    {
      host = (String)adsProperties.get(ADSContext.ServerProperty.HOST_NAME);
    }
    return host;
  }

  /**
   * Returns a String of type host-name:port-number for the server.  If
   * the provided securePreferred is set to true the port that will be used
   * (if LDAPS is enabled) will be the LDAPS port.
   * @param securePreferred whether to try to use the secure port as part
   * of the returning String or not.
   * @return a String of type host-name:port-number for the server.
   */
  public String getHostPort(boolean securePreferred)
  {
    String host = getHostName();
    int port = -1;

    if (!serverProperties.isEmpty())
    {
      ArrayList s = (ArrayList)serverProperties.get(
          ServerProperty.LDAP_ENABLED);
      ArrayList p = (ArrayList)serverProperties.get(
          ServerProperty.LDAP_PORT);
      for (int i=0; i<s.size(); i++)
      {
        if (Boolean.TRUE.equals(s.get(i)))
        {
          port = (Integer)p.get(i);
          break;
        }
      }
      if (securePreferred)
      {
        s = (ArrayList)serverProperties.get(
            ServerProperty.LDAPS_ENABLED);
        p = (ArrayList)serverProperties.get(ServerProperty.LDAPS_PORT);
        for (int i=0; i<s.size(); i++)
        {
          if (Boolean.TRUE.equals(s.get(i)))
          {
            port = (Integer)p.get(i);
            break;
          }
        }
      }
    }
    else
    {
      boolean secure;

      Object v = adsProperties.get(ADSContext.ServerProperty.LDAPS_ENABLED);
      secure = securePreferred && "true".equalsIgnoreCase(String.valueOf(v));
      try
      {
        if (secure)
        {
          port = Integer.parseInt((String)adsProperties.get(
              ADSContext.ServerProperty.LDAPS_PORT));
        }
        else
        {
          port = Integer.parseInt((String)adsProperties.get(
              ADSContext.ServerProperty.LDAP_PORT));
        }
      }
      catch (Throwable t)
      {
      }
    }
    return host + "." + port;
  }

  /**
   * Returns an Id that is unique for this server.
   * @return an Id that is unique for this server.
   */
  public String getId()
  {
    StringBuilder buf = new StringBuilder();
    if (serverProperties.size() > 0)
    {
      buf.append(serverProperties.get(ServerProperty.HOST_NAME));
      ServerProperty [] props =
      {
          ServerProperty.LDAP_PORT, ServerProperty.LDAPS_PORT,
          ServerProperty.LDAP_ENABLED, ServerProperty.LDAPS_ENABLED
      };
      for (int i=0; i<props.length; i++)
      {
        ArrayList s = (ArrayList)serverProperties.get(props[i]);
        for (Object o : s)
        {
          buf.append(":"+o);
        }
      }
    }
    else
    {
      ADSContext.ServerProperty[] props =
      {
          ADSContext.ServerProperty.HOST_NAME,
          ADSContext.ServerProperty.LDAP_PORT,
          ADSContext.ServerProperty.LDAPS_PORT,
          ADSContext.ServerProperty.LDAP_ENABLED,
          ADSContext.ServerProperty.LDAPS_ENABLED
      };
      for (int i=0; i<props.length; i++)
      {
        if (i != 0)
        {
          buf.append(":");
        }
        buf.append(adsProperties.get(props[i]));
      }
    }
    return buf.toString();
  }

  /**
   * Returns the last exception that was encountered reading the configuration
   * of the server.  Returns null if there was no problem loading the
   * configuration of the server.
   * @return the last exception that was encountered reading the configuration
   * of the server.  Returns null if there was no problem loading the
   * configuration of the server.
   */
  public TopologyCacheException getLastException()
  {
    return lastException;
  }

  /**
   * Sets the last exception that occurred while reading the configuration of
   * the server.
   * @param lastException the last exception that occurred while reading the
   * configuration of the server.
   */
  public void setLastException(TopologyCacheException lastException)
  {
    this.lastException = lastException;
  }

  /**
   * Creates a ServerDescriptor object based on some ADS properties provided.
   * @param adsProperties the ADS properties of the server.
   * @return a ServerDescriptor object that corresponds to the provided ADS
   * properties.
   */
  public static ServerDescriptor createStandalone(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    ServerDescriptor desc = new ServerDescriptor();
    desc.setAdsProperties(adsProperties);
    return desc;
  }

  /**
   * Creates a ServerDescriptor object based on the configuration that we read
   * using the provided InitialLdapContext.
   * @param ctx the InitialLdapContext that will be used to read the
   * configuration of the server.
   * @return a ServerDescriptor object that corresponds to the read
   * configuration.
   * @throws NamingException if a problem occurred reading the server
   * configuration.
   */
  public static ServerDescriptor createStandalone(InitialLdapContext ctx)
  throws NamingException
  {
    ServerDescriptor desc = new ServerDescriptor();


    updateLdapConfiguration(desc, ctx);
    updateJmxConfiguration(desc, ctx);
    updateReplicas(desc, ctx);
    updateReplication(desc, ctx);

    String s = (String)ctx.getEnvironment().get(Context.PROVIDER_URL);
    try
    {
      URI ldapURL = new URI(s);
      desc.serverProperties.put(ServerProperty.HOST_NAME, ldapURL.getHost());
    }
    catch (URISyntaxException use)
    {
      // This is really strange.  Seems like a bug somewhere.
      LOG.log(Level.WARNING, "Error parsing ldap URL "+s, use);
    }
    return desc;
  }

  private static void updateLdapConfiguration(ServerDescriptor desc,
      InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-connection-handler-enabled",
            "ds-cfg-listen-address",
            "ds-cfg-listen-port",
            "ds-cfg-use-ssl",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-ldap-connection-handler)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    ArrayList<Integer> ldapPorts = new ArrayList<Integer>();
    ArrayList<Integer> ldapsPorts = new ArrayList<Integer>();
    ArrayList<Boolean> ldapEnabled = new ArrayList<Boolean>();
    ArrayList<Boolean> ldapsEnabled = new ArrayList<Boolean>();

    desc.serverProperties.put(ServerProperty.LDAP_PORT, ldapPorts);
    desc.serverProperties.put(ServerProperty.LDAPS_PORT, ldapsPorts);
    desc.serverProperties.put(ServerProperty.LDAP_ENABLED, ldapEnabled);
    desc.serverProperties.put(ServerProperty.LDAPS_ENABLED, ldapsEnabled);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      String port = getFirstValue(sr, "ds-cfg-listen-port");

      boolean isSecure = "true".equalsIgnoreCase(
          getFirstValue(sr, "ds-cfg-use-ssl"));

      boolean enabled = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-connection-handler-enabled"));
      if (isSecure)
      {
        ldapsPorts.add(new Integer(port));
        ldapsEnabled.add(enabled);
      }
      else
      {
        ldapPorts.add(new Integer(port));
        ldapEnabled.add(enabled);
      }
    }
  }

  private static void updateJmxConfiguration(ServerDescriptor desc,
      InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-connection-handler-enabled",
            "ds-cfg-listen-address",
            "ds-cfg-listen-port",
            "ds-cfg-use-ssl",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-jmx-connection-handler)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    ArrayList<Integer> jmxPorts = new ArrayList<Integer>();
    ArrayList<Integer> jmxsPorts = new ArrayList<Integer>();
    ArrayList<Boolean> jmxEnabled = new ArrayList<Boolean>();
    ArrayList<Boolean> jmxsEnabled = new ArrayList<Boolean>();

    desc.serverProperties.put(ServerProperty.JMX_PORT, jmxPorts);
    desc.serverProperties.put(ServerProperty.JMXS_PORT, jmxsPorts);
    desc.serverProperties.put(ServerProperty.JMX_ENABLED, jmxEnabled);
    desc.serverProperties.put(ServerProperty.JMXS_ENABLED, jmxsEnabled);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      String port = getFirstValue(sr, "ds-cfg-listen-port");

      boolean isSecure = "true".equalsIgnoreCase(
          getFirstValue(sr, "ds-cfg-use-ssl"));

      boolean enabled = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-connection-handler-enabled"));
      if (isSecure)
      {
        jmxsPorts.add(new Integer(port));
        jmxsEnabled.add(enabled);
      }
      else
      {
        jmxPorts.add(new Integer(port));
        jmxEnabled.add(enabled);
      }
    }
  }

  private static void updateReplicas(ServerDescriptor desc,
      InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-backend-base-dn",
            "ds-cfg-backend-id"
        });
    String filter = "(objectclass=ds-cfg-backend)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration databases = ctx.search(jndiName, filter, ctls);

    while(databases.hasMore())
    {
      SearchResult sr = (SearchResult)databases.next();

      String id = getFirstValue(sr, "ds-cfg-backend-id");

      if (!isConfigBackend(id))
      {
        Set<String> baseDns = getValues(sr, "ds-cfg-backend-base-dn");

        int nEntries = getEntryCount(ctx, id);

        for (String baseDn : baseDns)
        {
          SuffixDescriptor suffix = new SuffixDescriptor();
          suffix.setDN(baseDn);
          ReplicaDescriptor replica = new ReplicaDescriptor();
          replica.setServer(desc);
          Set<ReplicaDescriptor> replicas = new HashSet<ReplicaDescriptor>();
          replicas.add(replica);
          suffix.setReplicas(replicas);
          replica.setSuffix(suffix);
          desc.setReplicas(replicas);
          if (baseDns.size() == 1)
          {
            replica.setEntries(nEntries);
          }
          else
          {
            /* Cannot know how many entries correspond to this replica */
            replica.setEntries(-1);
          }
        }
      }
    }
  }

  private static void updateReplication(ServerDescriptor desc,
      InitialLdapContext ctx) throws NamingException
  {
    boolean replicationEnabled = false;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-synchronization-provider-enabled"
        });
    String filter = "(objectclass=ds-cfg-synchronization-provider)";

    LdapName jndiName = new LdapName(
      "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");

    try
    {
      NamingEnumeration syncProviders = ctx.search(jndiName, filter, ctls);

      while(syncProviders.hasMore())
      {
        SearchResult sr = (SearchResult)syncProviders.next();

        if ("true".equalsIgnoreCase(getFirstValue(sr,
          "ds-cfg-synchronization-provider-enabled")))
        {
          replicationEnabled = true;
        }
      }
    }
    catch (NameNotFoundException nse)
    {
    }
    desc.serverProperties.put(ServerProperty.IS_REPLICATION_ENABLED,
        replicationEnabled ? Boolean.TRUE : Boolean.FALSE);

    ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-replication-dn",
            "ds-cfg-replication-server",
            "ds-cfg-directory-server-id"
        });
    filter = "(objectclass=ds-cfg-replication-domain-config)";

    jndiName = new LdapName(
      "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");

    try
    {
      NamingEnumeration syncProviders = ctx.search(jndiName, filter, ctls);

      while(syncProviders.hasMore())
      {
        SearchResult sr = (SearchResult)syncProviders.next();

        int id = Integer.parseInt(
            getFirstValue(sr, "ds-cfg-directory-server-id"));
        Set<String> replicationServers = getValues(sr,
            "ds-cfg-replication-server");
        Set<String> dns = getValues(sr, "ds-cfg-replication-dn");
        for (String dn : dns)
        {
          for (ReplicaDescriptor replica : desc.getReplicas())
          {
            if (areDnsEqual(replica.getSuffix().getDN(), dn))
            {
              replica.setReplicationId(id);
              replica.setReplicationServers(replicationServers);
            }
          }
        }
      }
    }
    catch (NameNotFoundException nse)
    {
    }

    ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
    new String[] {
      "ds-cfg-replication-server-port", "ds-cfg-replication-server",
      "ds-cfg-replication-server-id"
    });
    filter = "(objectclass=ds-cfg-replication-server-config)";

    jndiName = new LdapName("cn=Replication Server,cn=Multimaster "+
        "Synchronization,cn=Synchronization Providers,cn=config");

    desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER,
        Boolean.FALSE);
    try
    {
      NamingEnumeration entries = ctx.search(jndiName, filter, ctls);

      while(entries.hasMore())
      {
        SearchResult sr = (SearchResult)entries.next();

        desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER,
            Boolean.TRUE);
        String v = getFirstValue(sr, "ds-cfg-replication-server-port");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_PORT,
            Integer.parseInt(v));
        v = getFirstValue(sr, "ds-cfg-replication-server-id");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_ID,
            Integer.parseInt(v));
      }
    }
    catch (NameNotFoundException nse)
    {
    }
  }

  /**
   * Returns the number of entries in a given backend using the provided
   * InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @param backenID the id of the backend.
   * @return the number of entries in the backend.
   * @throws NamingException if there was an error.
   */
  private static int getEntryCount(InitialLdapContext ctx, String backendID)
  throws NamingException
  {
    int nEntries = -1;
    String v = null;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-backend-entry-count"
        });
    String filter = "(ds-backend-id="+backendID+")";

    LdapName jndiName = new LdapName("cn=monitor");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      v = getFirstValue(sr, "ds-backend-entry-count");
    }
    try
    {
      nEntries = Integer.parseInt(v);
    }
    catch (Exception ex)
    {

    }
    return nEntries;

  }

  /*
   * The following 2 methods are convenience methods to retrieve String values
   * from an entry.
   */
  private static String getFirstValue(SearchResult entry, String attrName)
  throws NamingException
  {
    return ConnectionUtils.getFirstValue(entry, attrName);
  }

  private static Set<String> getValues(SearchResult entry, String attrName)
  throws NamingException
  {
    return ConnectionUtils.getValues(entry, attrName);
  }

  /**
   * An convenience method to know if the provided ID corresponds to a
   * configuration backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to a configuration
   * backend and <CODE>false</CODE> otherwise.
   */
  private static boolean isConfigBackend(String id)
  {
    return "tasks".equalsIgnoreCase(id) ||
    "schema".equalsIgnoreCase(id) ||
    "config".equalsIgnoreCase(id) ||
    "monitor".equalsIgnoreCase(id) ||
    "backup".equalsIgnoreCase(id);
  }

  /**
   * Returns <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   * @param dn1 the first dn to compare.
   * @param dn2 the second dn to compare.
   * @return <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   */
  private static boolean areDnsEqual(String dn1, String dn2)
  {
    boolean areDnsEqual = false;
    try
    {
      LdapName name1 = new LdapName(dn1);
      LdapName name2 = new LdapName(dn2);
      areDnsEqual = name1.equals(name2);
    } catch (Exception ex)
    {
    }
    return areDnsEqual;
  }
}
