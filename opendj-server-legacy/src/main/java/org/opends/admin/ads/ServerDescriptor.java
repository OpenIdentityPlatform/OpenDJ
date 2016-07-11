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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.server.util.CollectionUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ldap.Rdn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.quicksetup.Constants;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.HostPort;

/** The object of this class represent an OpenDS server. */
public class ServerDescriptor
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final String TRUSTSTORE_DN = "cn=ads-truststore";

  private final Map<ADSContext.ServerProperty, Object> adsProperties = new HashMap<>();
  private final Set<ReplicaDescriptor> replicas = new HashSet<>();
  private final Map<ServerProperty, Object> serverProperties = new HashMap<>();
  private TopologyCacheException lastException;

  /**
   * Enumeration containing the different server properties that we can keep in
   * the ServerProperty object.
   */
  public enum ServerProperty
  {
    /** The associated value is a String. */
    HOST_NAME,
    /** The associated value is an ArrayList of Integer. */
    LDAP_PORT,
    /** The associated value is an ArrayList of Integer. */
    LDAPS_PORT,
    /** The associated value is an Integer. */
    ADMIN_PORT,
    /** The associated value is an ArrayList of Boolean. */
    LDAP_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    LDAPS_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    ADMIN_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    STARTTLS_ENABLED,
    /** The associated value is an ArrayList of Integer. */
    JMX_PORT,
    /** The associated value is an ArrayList of Integer. */
    JMXS_PORT,
    /** The associated value is an ArrayList of Boolean. */
    JMX_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    JMXS_ENABLED,
    /** The associated value is an Integer. */
    REPLICATION_SERVER_PORT,
    /** The associated value is a Boolean. */
    IS_REPLICATION_SERVER,
    /** The associated value is a Boolean. */
    IS_REPLICATION_ENABLED,
    /** The associated value is a Boolean. */
    IS_REPLICATION_SECURE,
    /** List of servers specified in the Replication Server configuration. This is a Set of String. */
    EXTERNAL_REPLICATION_SERVERS,
    /** The associated value is an Integer. */
    REPLICATION_SERVER_ID,
    /**
     * The instance key-pair public-key certificate. The associated value is a
     * byte[] (ds-cfg-public-key-certificate;binary).
     */
    INSTANCE_PUBLIC_KEY_CERTIFICATE,
    /** The schema generation ID. */
    SCHEMA_GENERATION_ID
  }

  /** Default constructor. */
  protected ServerDescriptor()
  {
  }

  /**
   * Returns the replicas contained on the server.
   * @return the replicas contained on the server.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    return new HashSet<>(replicas);
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
   * Tells whether this server is a replication server or not.
   * @return <CODE>true</CODE> if the server is a replication server and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationServer()
  {
    return Boolean.TRUE.equals(
        serverProperties.get(ServerProperty.IS_REPLICATION_SERVER));
  }

  /**
   * Tells whether replication is enabled on this server or not.
   * @return <CODE>true</CODE> if replication is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationEnabled()
  {
    return Boolean.TRUE.equals(serverProperties.get(ServerProperty.IS_REPLICATION_ENABLED));
  }

  /**
   * Returns the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * <CODE>null</CODE> if this is not a replication server.
   * @return the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * <CODE>null</CODE> if this is not a replication server.
   */
  public String getReplicationServerHostPort()
  {
    return isReplicationServer() ? getReplicationServer(getHostName(), getReplicationServerPort()) : null;
  }

  /**
   * Returns the replication server ID of this server and -1 if this is not a
   * replications server.
   * @return the replication server ID of this server and -1 if this is not a
   * replications server.
   */
  public int getReplicationServerId()
  {
    return isReplicationServer() ? (Integer) serverProperties.get(ServerProperty.REPLICATION_SERVER_ID) : -1;
  }

  /**
   * Returns the replication port of this server and -1 if this is not a
   * replications server.
   * @return the replication port of this server and -1 if this is not a
   * replications server.
   */
  public int getReplicationServerPort()
  {
    return isReplicationServer() ? (Integer) serverProperties.get(ServerProperty.REPLICATION_SERVER_PORT) : -1;
  }

  /**
   * Returns whether the communication with the replication port on the server
   * is encrypted or not.
   * @return <CODE>true</CODE> if the communication with the replication port on
   * the server is encrypted and <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationSecure()
  {
    return isReplicationServer()
        && Boolean.TRUE.equals(serverProperties.get(ServerProperty.IS_REPLICATION_SECURE));
  }

  /**
   * Sets the ADS properties of the server.
   * @param adsProperties a Map containing the ADS properties of the server.
   */
  public void setAdsProperties(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    this.adsProperties.clear();
    this.adsProperties.putAll(adsProperties);
  }

  /**
   * Returns the host name of the server.
   * @return the host name of the server.
   */
  public String getHostName()
  {
    String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
    if (host != null)
    {
      return host;
    }
    return (String) adsProperties.get(ADSContext.ServerProperty.HOST_NAME);
  }

  /**
   * Returns the URL to access this server using LDAP.  Returns
   * <CODE>null</CODE> if the server is not configured to listen on an LDAP
   * port.
   * @return the URL to access this server using LDAP.
   */
  public String getLDAPURL()
  {
    return getLDAPUrl0(ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT, false);
  }

  /**
   * Returns the URL to access this server using LDAPS.  Returns
   * <CODE>null</CODE> if the server is not configured to listen on an LDAPS
   * port.
   * @return the URL to access this server using LDAP.
   */
  public String getLDAPsURL()
  {
    return getLDAPUrl0(ServerProperty.LDAPS_ENABLED, ServerProperty.LDAPS_PORT, true);
  }

  private String getLDAPUrl0(ServerProperty enabledProp, ServerProperty portProp, boolean useSSL)
  {
    int port = getPort(enabledProp, portProp);
    if (port != -1)
    {
      String host = getHostName();
      return getLDAPUrl(host, port, useSSL);
    }
    return null;
  }

  private int getPort(ServerProperty enabledProp, ServerProperty portProp)
  {
    if (!serverProperties.isEmpty())
    {
      return getPort(enabledProp, portProp, -1);
    }
    return -1;
  }

  /**
   * Returns the URL to access this server using the administration connector.
   * Returns <CODE>null</CODE> if the server cannot get the administration
   * connector.
   * @return the URL to access this server using the administration connector.
   */
  public String getAdminConnectorURL()
  {
    return getLDAPUrl0(ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT, true);
  }

  /**
   * Returns the list of enabled administration ports.
   * @return the list of enabled administration ports.
   */
  public List<Integer> getEnabledAdministrationPorts()
  {
    List<Integer> ports = new ArrayList<>(1);
    ArrayList<?> s = (ArrayList<?>)serverProperties.get(ServerProperty.ADMIN_ENABLED);
    ArrayList<?> p = (ArrayList<?>)serverProperties.get(ServerProperty.ADMIN_PORT);
    if (s != null)
    {
      for (int i=0; i<s.size(); i++)
      {
        if (Boolean.TRUE.equals(s.get(i)))
        {
          ports.add((Integer)p.get(i));
        }
      }
    }
    return ports;
  }

  /**
   * Returns a String of type host-name:port-number for the server.  If
   * the provided securePreferred is set to true the port that will be used
   * will be the administration connector port.
   * @param securePreferred whether to try to use the secure port as part
   * of the returning String or not.
   * @return a String of type host-name:port-number for the server.
   */
  public HostPort getHostPort(boolean securePreferred)
  {
    int port = -1;

    if (!serverProperties.isEmpty())
    {
      port = getPort(ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT, port);
      if (securePreferred)
      {
        port = getPort(ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT, port);
      }
    }
    else
    {
      ArrayList<ADSContext.ServerProperty> enabledAttrs = new ArrayList<>();

      if (securePreferred)
      {
        enabledAttrs.add(ADSContext.ServerProperty.ADMIN_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAPS_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAP_ENABLED);
      }
      else
      {
        enabledAttrs.add(ADSContext.ServerProperty.LDAP_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.ADMIN_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAPS_ENABLED);
      }

      for (ADSContext.ServerProperty prop : enabledAttrs)
      {
        Object v = adsProperties.get(prop);
        if (v != null && "true".equalsIgnoreCase(String.valueOf(v)))
        {
          ADSContext.ServerProperty portProp = getPortProperty(prop);
          Object p = adsProperties.get(portProp);
          if (p != null)
          {
            try
            {
              port = Integer.parseInt(String.valueOf(p));
            }
            catch (Throwable t)
            {
              logger.warn(LocalizableMessage.raw("Error calculating host port: "+t+" in "+
                  adsProperties, t));
            }
            break;
          }
          else
          {
            logger.warn(LocalizableMessage.raw("Value for "+portProp+" is null in "+
                adsProperties));
          }
        }
      }
    }
    return new HostPort(getHostName(), port);
  }

  private ADSContext.ServerProperty getPortProperty(ADSContext.ServerProperty prop)
  {
    switch (prop)
    {
    case ADMIN_ENABLED:
      return ADSContext.ServerProperty.ADMIN_PORT;
    case LDAPS_ENABLED:
      return ADSContext.ServerProperty.LDAPS_PORT;
    case LDAP_ENABLED:
      return ADSContext.ServerProperty.LDAP_PORT;
    default:
      throw new IllegalStateException("Unexpected prop: "+prop);
    }
  }

  private int getPort(ServerProperty enabledProp, ServerProperty portProp, int defaultValue)
  {
    List<?> s = (List<?>) serverProperties.get(enabledProp);
    if (s != null)
    {
      List<?> p = (List<?>) serverProperties.get(portProp);
      for (int i=0; i<s.size(); i++)
      {
        if (Boolean.TRUE.equals(s.get(i)))
        {
          return (Integer) p.get(i);
        }
      }
    }
    return defaultValue;
  }

  /**
   * Returns an Id that is unique for this server.
   * @return an Id that is unique for this server.
   */
  public String getId()
  {
    StringBuilder buf = new StringBuilder();
    if (!serverProperties.isEmpty())
    {
      buf.append(serverProperties.get(ServerProperty.HOST_NAME));
      ServerProperty [] props =
      {
          ServerProperty.LDAP_PORT, ServerProperty.LDAPS_PORT,
          ServerProperty.ADMIN_PORT,
          ServerProperty.LDAP_ENABLED, ServerProperty.LDAPS_ENABLED,
          ServerProperty.ADMIN_ENABLED
      };
      for (ServerProperty prop : props) {
        ArrayList<?> s = (ArrayList<?>) serverProperties.get(prop);
        for (Object o : s) {
          buf.append(":").append(o);
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
          ADSContext.ServerProperty.ADMIN_PORT,
          ADSContext.ServerProperty.LDAP_ENABLED,
          ADSContext.ServerProperty.LDAPS_ENABLED,
          ADSContext.ServerProperty.ADMIN_ENABLED
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
   * Returns the instance-key public-key certificate retrieved from the
   * truststore backend of the instance referenced through this descriptor.
   *
   * @return The public-key certificate of the instance.
   */
  public byte[] getInstancePublicKeyCertificate()
  {
    return (byte[]) serverProperties.get(ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE);
  }

  /**
   * Returns the schema generation ID of the server.
   * @return the schema generation ID of the server.
   */
  public String getSchemaReplicationID()
  {
    return (String)serverProperties.get(ServerProperty.SCHEMA_GENERATION_ID);
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
   * This methods updates the ADS properties (the ones that were read from
   * the ADS) with the contents of the server properties (the ones that were
   * read directly from the server).
   */
  public void updateAdsPropertiesWithServerProperties()
  {
    adsProperties.put(ADSContext.ServerProperty.HOST_NAME, getHostName());
    ServerProperty[][] sProps =
    {
        {ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT},
        {ServerProperty.LDAPS_ENABLED, ServerProperty.LDAPS_PORT},
        {ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT},
        {ServerProperty.JMX_ENABLED, ServerProperty.JMX_PORT},
        {ServerProperty.JMXS_ENABLED, ServerProperty.JMXS_PORT}
    };
    ADSContext.ServerProperty[][] adsProps =
    {
        {ADSContext.ServerProperty.LDAP_ENABLED,
          ADSContext.ServerProperty.LDAP_PORT},
        {ADSContext.ServerProperty.LDAPS_ENABLED,
          ADSContext.ServerProperty.LDAPS_PORT},
        {ADSContext.ServerProperty.ADMIN_ENABLED,
          ADSContext.ServerProperty.ADMIN_PORT},
        {ADSContext.ServerProperty.JMX_ENABLED,
          ADSContext.ServerProperty.JMX_PORT},
        {ADSContext.ServerProperty.JMXS_ENABLED,
          ADSContext.ServerProperty.JMXS_PORT}
    };

    for (int i=0; i<sProps.length; i++)
    {
      ArrayList<?> s = (ArrayList<?>)serverProperties.get(sProps[i][0]);
      ArrayList<?> p = (ArrayList<?>)serverProperties.get(sProps[i][1]);
      if (s != null)
      {
        int port = getPort(s, p);
        if (port == -1)
        {
          adsProperties.put(adsProps[i][0], "false");
          if (!p.isEmpty())
          {
            port = (Integer)p.iterator().next();
          }
        }
        else
        {
          adsProperties.put(adsProps[i][0], "true");
        }
        adsProperties.put(adsProps[i][1], String.valueOf(port));
      }
    }

    ArrayList<?> array = (ArrayList<?>)serverProperties.get(
        ServerProperty.STARTTLS_ENABLED);
    boolean startTLSEnabled = false;
    if (array != null && !array.isEmpty())
    {
      startTLSEnabled = Boolean.TRUE.equals(array.get(array.size() -1));
    }
    adsProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, Boolean.toString(startTLSEnabled));
    adsProperties.put(ADSContext.ServerProperty.ID, getHostPort(true).toString());
    adsProperties.put(ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE,
                      getInstancePublicKeyCertificate());
  }

  private int getPort(List<?> enabled, List<?> port)
  {
    for (int j = 0; j < enabled.size(); j++)
    {
      if (Boolean.TRUE.equals(enabled.get(j)))
      {
        return (Integer) port.get(j);
      }
    }
    return -1;
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
   * using the provided connection.
   * @param conn the connection that will be used to read the configuration of the server.
   * @param filter the topology cache filter describing the information that
   * must be retrieved.
   * @return a ServerDescriptor object that corresponds to the read configuration.
   * @throws IOException if a problem occurred reading the server configuration.
   */
  public static ServerDescriptor createStandalone(ConnectionWrapper conn, TopologyCacheFilter filter) throws IOException
  {
    ServerDescriptor desc = new ServerDescriptor();

    updateLdapConfiguration(desc, conn);
    updateAdminConnectorConfiguration(desc, conn);
    updateJmxConfiguration(desc, conn);
    updateReplicas(desc, conn, filter);
    updateReplication(desc, conn, filter);
    updatePublicKeyCertificate(desc, conn);
    updateMiscellaneous(desc, conn);

    desc.serverProperties.put(ServerProperty.HOST_NAME, conn.getHostPort().getHost());

    return desc;
  }

  private static void updateLdapConfiguration(ServerDescriptor desc, ConnectionWrapper conn)
      throws IOException
  {
    String filter = "(objectclass=ds-cfg-ldap-connection-handler)";

    SearchRequest request = newSearchRequest("cn=config", WHOLE_SUBTREE, filter,
        "ds-cfg-enabled",
        "ds-cfg-listen-address",
        "ds-cfg-listen-port",
        "ds-cfg-use-ssl",
        "ds-cfg-allow-start-tls",
        "objectclass");
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      ArrayList<Integer> ldapPorts = new ArrayList<>();
      ArrayList<Integer> ldapsPorts = new ArrayList<>();
      ArrayList<Boolean> ldapEnabled = new ArrayList<>();
      ArrayList<Boolean> ldapsEnabled = new ArrayList<>();
      ArrayList<Boolean> startTLSEnabled = new ArrayList<>();

      desc.serverProperties.put(ServerProperty.LDAP_PORT, ldapPorts);
      desc.serverProperties.put(ServerProperty.LDAPS_PORT, ldapsPorts);
      desc.serverProperties.put(ServerProperty.LDAP_ENABLED, ldapEnabled);
      desc.serverProperties.put(ServerProperty.LDAPS_ENABLED, ldapsEnabled);
      desc.serverProperties.put(ServerProperty.STARTTLS_ENABLED, startTLSEnabled);

      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        Integer portNumber = asInteger(sr, "ds-cfg-listen-port");
        boolean enabled = asBoolean(sr, "ds-cfg-enabled");
        if (asBoolean(sr, "ds-cfg-use-ssl"))
        {
          ldapsPorts.add(portNumber);
          ldapsEnabled.add(enabled);
        }
        else
        {
          ldapPorts.add(portNumber);
          ldapEnabled.add(enabled);
          startTLSEnabled.add(asBoolean(sr, "ds-cfg-allow-start-tls"));
        }
      }
    }
  }

  private static void updateAdminConnectorConfiguration(ServerDescriptor desc, ConnectionWrapper conn)
      throws IOException
  {
    SearchRequest request = newSearchRequest(
        "cn=config", WHOLE_SUBTREE, "(objectclass=ds-cfg-administration-connector)",
        "ds-cfg-listen-port", "objectclass");
    SearchResultEntry sr = conn.getConnection().searchSingleEntry(request);
    Integer adminConnectorPort = asInteger(sr, "ds-cfg-listen-port");

    // Even if we have a single port, use an array to be consistent with
    // other protocols.
    ArrayList<Integer> adminPorts = new ArrayList<>();
    ArrayList<Boolean> adminEnabled = new ArrayList<>();
    if (adminConnectorPort != null)
    {
      adminPorts.add(adminConnectorPort);
      adminEnabled.add(Boolean.TRUE);
    }
    desc.serverProperties.put(ServerProperty.ADMIN_PORT, adminPorts);
    desc.serverProperties.put(ServerProperty.ADMIN_ENABLED, adminEnabled);
  }

  private static void updateJmxConfiguration(ServerDescriptor desc, ConnectionWrapper conn) throws IOException
  {
    ArrayList<Integer> jmxPorts = new ArrayList<>();
    ArrayList<Integer> jmxsPorts = new ArrayList<>();
    ArrayList<Boolean> jmxEnabled = new ArrayList<>();
    ArrayList<Boolean> jmxsEnabled = new ArrayList<>();

    desc.serverProperties.put(ServerProperty.JMX_PORT, jmxPorts);
    desc.serverProperties.put(ServerProperty.JMXS_PORT, jmxsPorts);
    desc.serverProperties.put(ServerProperty.JMX_ENABLED, jmxEnabled);
    desc.serverProperties.put(ServerProperty.JMXS_ENABLED, jmxsEnabled);

    String filter = "(objectclass=ds-cfg-jmx-connection-handler)";
    SearchRequest request = newSearchRequest("cn=config", WHOLE_SUBTREE, filter,
        "ds-cfg-enabled",
        "ds-cfg-listen-address",
        "ds-cfg-listen-port",
        "ds-cfg-use-ssl",
        "objectclass");
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        Integer portNumber = asInteger(sr, "ds-cfg-listen-port");
        boolean enabled = asBoolean(sr, "ds-cfg-enabled");
        if (asBoolean(sr, "ds-cfg-use-ssl"))
        {
          jmxsPorts.add(portNumber);
          jmxsEnabled.add(enabled);
        }
        else
        {
          jmxPorts.add(portNumber);
          jmxEnabled.add(enabled);
        }
      }
    }
  }

  private static void updateReplicas(ServerDescriptor desc, ConnectionWrapper conn, TopologyCacheFilter cacheFilter)
      throws IOException
  {
    if (!cacheFilter.searchBaseDNInformation())
    {
      return;
    }

    SearchRequest request = newSearchRequest("cn=config", WHOLE_SUBTREE, "(objectclass=ds-cfg-backend)",
        "ds-cfg-base-dn",
        "ds-cfg-backend-id",
        ConfigConstants.ATTR_OBJECTCLASS);
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        String id = firstValueAsString(sr, "ds-cfg-backend-id");
        if (!isConfigBackend(id) || isSchemaBackend(id))
        {
          Set<DN> baseDns = asSetOfDN(sr, "ds-cfg-base-dn");

          Set<String> entries;
          if (cacheFilter.searchMonitoringInformation())
          {
            entries = getBaseDNEntryCount(conn, id);
          }
          else
          {
            entries = new HashSet<>();
          }

          Set<ReplicaDescriptor> replicas = desc.getReplicas();
          for (DN baseDn : baseDns)
          {
            if (isAddReplica(cacheFilter, baseDn))
            {
              ReplicaDescriptor replica = new ReplicaDescriptor();
              replica.setServer(desc);
              replica.setObjectClasses(asSetOfString(sr, ConfigConstants.ATTR_OBJECTCLASS));
              replica.setBackendName(id);
              replicas.add(replica);

              SuffixDescriptor suffix = new SuffixDescriptor();
              suffix.setDN(baseDn);
              suffix.setReplicas(newHashSet(replica));
              replica.setSuffix(suffix);

              int nEntries = -1;
              for (String s : entries)
              {
                int index = s.indexOf(" ");
                if (index != -1)
                {
                  DN dn = DN.valueOf(s.substring(index + 1));
                  if (baseDn.equals(dn))
                  {
                    try
                    {
                      nEntries = Integer.parseInt(s.substring(0, index));
                    }
                    catch (Throwable t)
                    {
                      /* Ignore */
                    }
                    break;
                  }
                }
              }
              replica.setEntries(nEntries);
            }
          }
          desc.setReplicas(replicas);
        }
      }
    }
  }

  private static boolean isAddReplica(TopologyCacheFilter cacheFilter, DN baseDn)
  {
    if (cacheFilter.searchAllBaseDNs())
    {
      return true;
    }

    for (String dn : cacheFilter.getBaseDNsToSearch())
    {
      if (DN.valueOf(dn).equals(baseDn))
      {
        return true;
      }
    }
    return false;
  }

  private static void updateReplication(ServerDescriptor desc, ConnectionWrapper conn, TopologyCacheFilter cacheFilter)
      throws IOException
  {
    SearchRequest request = newSearchRequest(
        "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config",
        WHOLE_SUBTREE,
        "(objectclass=ds-cfg-synchronization-provider)",
        "ds-cfg-enabled");
    SearchResultEntry sre = conn.getConnection().searchSingleEntry(request);
    Boolean replicationEnabled = asBoolean(sre, "ds-cfg-enabled");
    desc.serverProperties.put(ServerProperty.IS_REPLICATION_ENABLED, replicationEnabled);

    Set<String> allReplicationServers = new LinkedHashSet<>();

    if (cacheFilter.searchBaseDNInformation())
    {
      request = newSearchRequest(
          "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config",
          WHOLE_SUBTREE,
          "(objectclass=ds-cfg-replication-domain)",
          "ds-cfg-base-dn",
          "ds-cfg-replication-server",
          "ds-cfg-server-id"
      );
      try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
      {
        while (entryReader.hasNext())
        {
          SearchResultEntry sr = entryReader.readEntry();

          int id = asInteger(sr, "ds-cfg-server-id");
          Set<String> replicationServers = asSetOfString(sr, "ds-cfg-replication-server");
          Set<DN> dns = asSetOfDN(sr, "ds-cfg-base-dn");
          for (DN dn : dns)
          {
            for (ReplicaDescriptor replica : desc.getReplicas())
            {
              if (replica.getSuffix().getDN().equals(dn))
              {
                replica.setReplicationId(id);
                LinkedHashSet<String> repServers = toLowercase(replicationServers);
                replica.setReplicationServers(repServers);
                allReplicationServers.addAll(repServers);
              }
            }
          }
        }
      }
    }

    desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER, Boolean.FALSE);

    request = newSearchRequest(
        "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config",
        WHOLE_SUBTREE,
        "(objectclass=ds-cfg-replication-server)",
        "ds-cfg-replication-port",
        "ds-cfg-replication-server",
        "ds-cfg-replication-server-id"
    );
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER, Boolean.TRUE);
        Integer port = asInteger(sr, "ds-cfg-replication-port");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_PORT, port);
        Integer serverId = asInteger(sr, "ds-cfg-replication-server-id");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_ID, serverId);
        LinkedHashSet<String> repServers = toLowercase(asSetOfString(sr, "ds-cfg-replication-server"));
        allReplicationServers.addAll(repServers);
        desc.serverProperties.put(ServerProperty.EXTERNAL_REPLICATION_SERVERS, allReplicationServers);
      }
    }

    Boolean replicationSecure = isReplicationSecure(conn, replicationEnabled);
    desc.serverProperties.put(ServerProperty.IS_REPLICATION_SECURE, replicationSecure);
  }

  /**
   * Keep the values of the replication servers in lower case to make use of Sets as String simpler.
   */
  private static LinkedHashSet<String> toLowercase(Set<String> values)
  {
    LinkedHashSet<String> repServers = new LinkedHashSet<>();
    for (String s: values)
    {
      repServers.add(s.toLowerCase());
    }
    return repServers;
  }

  private static boolean isReplicationSecure(ConnectionWrapper conn, boolean replicationEnabled) throws IOException
  {
    if (replicationEnabled)
    {
      SearchRequest request = newSearchRequest(
          "cn=Crypto Manager,cn=config", BASE_OBJECT, "(objectclass=ds-cfg-crypto-manager)",
          "ds-cfg-ssl-encryption");
      try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
      {
        while (entryReader.hasNext())
        {
          SearchResultEntry sr = entryReader.readEntry();
          return asBoolean(sr, "ds-cfg-ssl-encryption");
        }
      }
    }
    return false;
  }

  /**
   * Updates the instance key public-key certificate value of this context from the local truststore
   * of the instance bound by this context. Any current value of the certificate is overwritten. The
   * intent of this method is to retrieve the instance-key public-key certificate when this context
   * is bound to an instance, and cache it for later use in registering the instance into ADS.
   *
   * @param desc
   *          The map to update with the instance key-pair public-key certificate.
   * @param connWrapper
   *          The connection to the server.
   * @throws LdapException
   *           if unable to retrieve certificate from bound instance.
   */
  private static void updatePublicKeyCertificate(ServerDescriptor desc, ConnectionWrapper connWrapper)
      throws LdapException
  {
    /* TODO: this DN is declared in some core constants file. Create a constants
       file for the installer and import it into the core. */
    String dn = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
    Connection conn = connWrapper.getConnection();
    for (int i = 0; i < 2 ; ++i) {
      /* If the entry does not exist in the instance's truststore backend, add
         it (which induces the CryptoManager to create the public-key
         certificate attribute), then repeat the search. */
      try {
        SearchRequest request = newSearchRequest(
            dn,
            BASE_OBJECT,
            "(objectclass=ds-cfg-instance-key)",
            "ds-cfg-public-key-certificate;binary");
        SearchResultEntry certEntry = conn.searchSingleEntry(request);
        final Attribute certAttr = certEntry.getAttribute("ds-cfg-public-key-certificate;binary");
        if (null != certAttr) {
          /* attribute ds-cfg-public-key-certificate is a MUST in the schema */
          desc.serverProperties.put(
                  ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE,
                  certAttr.firstValue().toByteArray());
        }
        break;
      }
      catch (LdapException e)
      {
        if (0 != i || e.getResult().getResultCode() != ResultCode.NO_SUCH_OBJECT)
        {
          throw e;
        }
        // Poke CryptoManager to initialize truststore. Note the special attribute in the request.
        AddRequest request = newAddRequest(dn)
            .addAttribute("objectclass", "top", "ds-cfg-self-signed-cert-request");
        conn.add(request);
      }
    }
  }

  private static void updateMiscellaneous(ServerDescriptor desc, ConnectionWrapper conn) throws IOException
  {
    String filter = "(|(objectclass=*)(objectclass=ldapsubentry))";
    SearchRequest request = newSearchRequest("cn=schema", BASE_OBJECT, filter, "ds-sync-generation-id");
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        desc.serverProperties.put(ServerProperty.SCHEMA_GENERATION_ID,
            firstValueAsString(sr, "ds-sync-generation-id"));
      }
    }
  }

  /**
   Seeds the bound instance's local ads-truststore with a set of instance
   key-pair public key certificates. The result is the instance will trust any
   instance possessing the private key corresponding to one of the public-key
   certificates. This trust is necessary at least to initialize replication,
   which uses the trusted certificate entries in the ads-truststore for server
   authentication.
   @param connWrapper The connection to the server.
   @param keyEntryMap The set of valid (i.e., not tagged as compromised)
   instance key-pair public-key certificate entries in ADS represented as a map
   from keyID to public-key certificate (binary).
   @throws LdapException in case an error occurs while updating the instance's
   ads-truststore via LDAP.
   */
  public static void seedAdsTrustStore(ConnectionWrapper connWrapper, Map<String, byte[]> keyEntryMap)
      throws LdapException
  {
    Connection conn = connWrapper.getConnection();
    /* TODO: this DN is declared in some core constants file. Create a
       constants file for the installer and import it into the core. */
    for (Map.Entry<String, byte[]> keyEntry : keyEntryMap.entrySet()){
      String instanceKeyId = ADSContext.ServerProperty.INSTANCE_KEY_ID.getAttributeName();
      String instancePublicKeyCertificate =
          ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE.getAttributeName() + ";binary";
      String dn = instanceKeyId + "=" + Rdn.escapeValue(keyEntry.getKey()) + "," + TRUSTSTORE_DN;
      AddRequest request = newAddRequest(dn)
          .addAttribute("objectclass", "top", "ds-cfg-instance-key")
          .addAttribute(instanceKeyId, keyEntry.getKey())
          .addAttribute(instancePublicKeyCertificate, keyEntry.getValue());
      try
      {
        conn.add(request);
      }
      catch (LdapException e)
      {
        if (e.getResult().getResultCode() != ResultCode.ENTRY_ALREADY_EXISTS)
        {
          throw e;
        }
        conn.delete(dn);
        conn.add(request);
      }
    }
  }

  /**
   * Returns the values of the ds-base-dn-entry count attributes for the given backend monitor entry
   * using the provided connection.
   *
   * @param conn
   *          the connection to use to update the configuration.
   * @param backendID
   *          the id of the backend.
   * @return the values of the ds-base-dn-entry count attribute.
   * @throws IOException
   *           if there was an error.
   */
  private static Set<String> getBaseDNEntryCount(ConnectionWrapper conn, String backendID) throws IOException
  {
    LinkedHashSet<String> results = new LinkedHashSet<>();
    SearchRequest request =
        newSearchRequest("cn=monitor", SINGLE_LEVEL, "(ds-backend-id=" + backendID + ")", "ds-base-dn-entry-count");
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();
        results.addAll(asSetOfString(sr, "ds-base-dn-entry-count"));
      }
    }
    return results;
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
    "backup".equalsIgnoreCase(id) ||
    "ads-truststore".equalsIgnoreCase(id);
  }

  /**
   * An convenience method to know if the provided ID corresponds to the schema
   * backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to the schema backend
   * and <CODE>false</CODE> otherwise.
   */
  private static boolean isSchemaBackend(String id)
  {
    return "schema".equalsIgnoreCase(id);
  }

  /**
   * Returns the replication server normalized String for a given host name
   * and replication port.
   * @param hostName the host name.
   * @param replicationPort the replication port.
   * @return the replication server normalized String for a given host name
   * and replication port.
   */
  public static String getReplicationServer(String hostName, int replicationPort)
  {
    return HostPort.toString(hostName, replicationPort);
  }

  /**
   * Returns a representation of a base DN for a set of servers.
   * @param baseDN the base DN.
   * @param servers the servers.
   * @return a representation of a base DN for a set of servers.
   */
  public static String getSuffixDisplay(DN baseDN, Set<ServerDescriptor> servers)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(baseDN);
    for (ServerDescriptor server : servers)
    {
      sb.append(Constants.LINE_SEPARATOR).append("    ");
      sb.append(server.getHostPort(true));
    }
    return sb.toString();
  }

  /**
   * Tells whether the provided server descriptor represents the same server
   * as this object.
   * @param server the server to make the comparison.
   * @return whether the provided server descriptor represents the same server
   * as this object or not.
   */
  public boolean isSameServer(ServerDescriptor server)
  {
    return getId().equals(server.getId());
  }
}
