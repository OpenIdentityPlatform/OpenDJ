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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.naming.ldap.Rdn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.quicksetup.Constants;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.HostPort;

/**
 * The object of this class represent an OpenDJ server instance.
 * <p>
 * It can represent either a DS-only, a RS-only or a combined DS-RS.
 */
public class ServerDescriptor implements Comparable<ServerDescriptor>
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
    HOST_NAME(ADSContext.ServerProperty.HOST_NAME),
    /** The associated value is an List of Integer. */
    LDAP_PORT(ADSContext.ServerProperty.LDAP_PORT),
    /** The associated value is an List of Integer. */
    LDAPS_PORT(ADSContext.ServerProperty.LDAPS_PORT),
    /** The associated value is an Integer. */
    ADMIN_PORT(ADSContext.ServerProperty.ADMIN_PORT),
    /** The associated value is an List of Boolean. */
    LDAP_ENABLED(ADSContext.ServerProperty.LDAP_ENABLED),
    /** The associated value is an List of Boolean. */
    LDAPS_ENABLED(ADSContext.ServerProperty.LDAPS_ENABLED),
    /** The associated value is an List of Boolean. */
    ADMIN_ENABLED(ADSContext.ServerProperty.ADMIN_ENABLED),
    /** The associated value is an List of Boolean. */
    STARTTLS_ENABLED(ADSContext.ServerProperty.STARTTLS_ENABLED),
    /** The associated value is an List of Integer. */
    JMX_PORT(ADSContext.ServerProperty.JMX_PORT),
    /** The associated value is an List of Integer. */
    JMXS_PORT(ADSContext.ServerProperty.JMXS_PORT),
    /** The associated value is an List of Boolean. */
    JMX_ENABLED(ADSContext.ServerProperty.JMX_ENABLED),
    /** The associated value is an List of Boolean. */
    JMXS_ENABLED(ADSContext.ServerProperty.JMXS_ENABLED),
    /** The associated value is an Integer. */
    REPLICATION_SERVER_PORT(null),
    /** The associated value is a Boolean. */
    IS_REPLICATION_SERVER(null),
    /** The associated value is a Boolean. */
    IS_REPLICATION_ENABLED(null),
    /** The associated value is a Boolean. */
    IS_REPLICATION_SECURE(null),
    /** List of servers specified in the Replication Server configuration. This is a Set of String. */
    EXTERNAL_REPLICATION_SERVERS(null),
    /** The associated value is an Integer. */
    REPLICATION_SERVER_ID(null),
    /**
     * The instance key-pair public-key certificate. The associated value is a
     * byte[] (ds-cfg-public-key-certificate;binary).
     */
    INSTANCE_PUBLIC_KEY_CERTIFICATE(ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE),
    /** The schema generation ID. */
    SCHEMA_GENERATION_ID(null);

    private org.opends.admin.ads.ADSContext.ServerProperty adsEquivalent;

    private ServerProperty(ADSContext.ServerProperty adsEquivalent)
    {
      this.adsEquivalent = adsEquivalent;
    }
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
   * Tells whether this server is registered in the ADS.
   * @return {@code true} if the server is registered in the ADS and {@code false} otherwise.
   */
  public boolean isRegistered()
  {
    return !adsProperties.isEmpty();
  }

  /**
   * Tells whether this server is a replication server.
   * @return {@code true} if the server is a replication server and {@code false} otherwise.
   */
  public boolean isReplicationServer()
  {
    return Boolean.TRUE.equals(
        serverProperties.get(ServerProperty.IS_REPLICATION_SERVER));
  }

  /**
   * Tells whether replication is enabled on this server.
   * @return {@code true} if replication is enabled and {@code false} otherwise.
   */
  public boolean isReplicationEnabled()
  {
    return Boolean.TRUE.equals(serverProperties.get(ServerProperty.IS_REPLICATION_ENABLED));
  }

  /**
   * Returns the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * {@code null} if this is not a replication server.
   * @return the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * {@code null} if this is not a replication server.
   */
  public HostPort getReplicationServerHostPort()
  {
    return isReplicationServer() ? new HostPort(getHostName(), getReplicationServerPort()) : null;
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
   * Returns whether the communication with the replication port on the server is encrypted.
   * @return {@code true} if the communication with the replication port on
   * the server is encrypted and {@code false} otherwise.
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
   * Returns the HostPort to access this server using LDAP.
   * @return the HostPort to access this server using LDAP,
   *         {@code null} if the server is not configured to listen on an LDAP port.
   */
  public HostPort getLdapHostPort()
  {
    return getHostPort(getLdapPort(-1));
  }

  /**
   * Returns the HostPort to access this server using LDAPS.
   * @return the HostPort to access this server using LDAPS,
   *         {@code null} if the server is not configured to listen on an LDAPS port.
   */
  public HostPort getLdapsHostPort()
  {
    return getHostPort(getLdapsPort(-1));
  }

  /**
   * Returns the HostPort to access this server using the administration connector.
   * @return the HostPort to access this server using the administration connector,
   *         {@code null} if the server cannot get the administration connector.
   */
  public HostPort getAdminConnectorHostPort()
  {
    return getHostPort(getAdminPort(-1));
  }

  private HostPort getHostPort(final int port)
  {
    return port != -1 ? new HostPort(getHostName(), port) : null;
  }

  /**
   * Returns the list of enabled administration ports.
   * @return the list of enabled administration ports.
   */
  public List<Integer> getEnabledAdministrationPorts()
  {
    List<Integer> ports = new ArrayList<>(1);
    List<?> s = (List<?>) serverProperties.get(ServerProperty.ADMIN_ENABLED);
    List<?> p = (List<?>) serverProperties.get(ServerProperty.ADMIN_PORT);
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
   * of the returning String.
   * @return a String of type host-name:port-number for the server.
   */
  public HostPort getHostPort(boolean securePreferred)
  {
    int port = -1;

    if (!serverProperties.isEmpty())
    {
      port = getLdapPort(port);
      if (securePreferred)
      {
        port = getAdminPort(port);
      }
    }
    else
    {
      List<ADSContext.ServerProperty> enabledAttrs = new ArrayList<>();

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

  private int getLdapPort(int port)
  {
    return getPort(ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT, port);
  }

  private int getLdapsPort(int port)
  {
    return getPort(ServerProperty.LDAPS_ENABLED, ServerProperty.LDAPS_PORT, port);
  }

  private int getAdminPort(int port)
  {
    return getPort(ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT, port);
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
      ServerProperty [] props = {
          ServerProperty.LDAP_PORT,
          ServerProperty.LDAPS_PORT,
          ServerProperty.ADMIN_PORT,
          ServerProperty.LDAP_ENABLED,
          ServerProperty.LDAPS_ENABLED,
          ServerProperty.ADMIN_ENABLED
      };
      for (ServerProperty prop : props) {
        List<?> s = (List<?>) serverProperties.get(prop);
        for (Object o : s) {
          buf.append(":").append(o);
        }
      }
    }
    else
    {
      ADSContext.ServerProperty[] props = {
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

  @Override
  public int compareTo(ServerDescriptor o)
  {
    return getId().compareTo(o.getId());
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
      List<?> s = (List<?>) serverProperties.get(sProps[i][0]);
      List<?> p = (List<?>) serverProperties.get(sProps[i][1]);
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

    List<?> array = (List<?>) serverProperties.get(ServerProperty.STARTTLS_ENABLED);
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
      List<Integer> ldapPorts = new ArrayList<>();
      List<Integer> ldapsPorts = new ArrayList<>();
      List<Boolean> ldapEnabled = new ArrayList<>();
      List<Boolean> ldapsEnabled = new ArrayList<>();
      List<Boolean> startTLSEnabled = new ArrayList<>();

      desc.serverProperties.put(ServerProperty.LDAP_PORT, ldapPorts);
      desc.serverProperties.put(ServerProperty.LDAPS_PORT, ldapsPorts);
      desc.serverProperties.put(ServerProperty.LDAP_ENABLED, ldapEnabled);
      desc.serverProperties.put(ServerProperty.LDAPS_ENABLED, ldapsEnabled);
      desc.serverProperties.put(ServerProperty.STARTTLS_ENABLED, startTLSEnabled);

      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();

        Integer portNumber = sr.parseAttribute("ds-cfg-listen-port").asInteger();
        Boolean enabled = sr.parseAttribute("ds-cfg-enabled").asBoolean();
        if (sr.parseAttribute("ds-cfg-use-ssl").asBoolean())
        {
          ldapsPorts.add(portNumber);
          ldapsEnabled.add(enabled);
        }
        else
        {
          ldapPorts.add(portNumber);
          ldapEnabled.add(enabled);
          startTLSEnabled.add(sr.parseAttribute("ds-cfg-allow-start-tls").asBoolean());
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
    Integer adminConnectorPort = sr.parseAttribute("ds-cfg-listen-port").asInteger();

    // Even if we have a single port, use an array to be consistent with
    // other protocols.
    List<Integer> adminPorts = new ArrayList<>();
    List<Boolean> adminEnabled = new ArrayList<>();
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
    List<Integer> jmxPorts = new ArrayList<>();
    List<Integer> jmxsPorts = new ArrayList<>();
    List<Boolean> jmxEnabled = new ArrayList<>();
    List<Boolean> jmxsEnabled = new ArrayList<>();

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

        Integer portNumber = sr.parseAttribute("ds-cfg-listen-port").asInteger();
        boolean enabled = sr.parseAttribute("ds-cfg-enabled").asBoolean();
        if (sr.parseAttribute("ds-cfg-use-ssl").asBoolean())
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

        String backendId = sr.parseAttribute("ds-cfg-backend-id").asString();
        if (!isConfigBackend(backendId) || isSchemaBackend(backendId))
        {
          Set<String> entries;
          if (cacheFilter.searchMonitoringInformation())
          {
            entries = getBaseDNEntryCount(conn, backendId);
          }
          else
          {
            entries = new HashSet<>();
          }

          Set<ReplicaDescriptor> replicas = desc.getReplicas();
          Set<DN> baseDns = sr.parseAttribute("ds-cfg-base-dn").asSetOfDN();
          for (DN baseDn : baseDns)
          {
            if (isAddReplica(cacheFilter, baseDn))
            {
              ReplicaDescriptor replica = new ReplicaDescriptor();
              replica.setServer(desc);
              replica.setObjectClasses(sr.parseAttribute(ConfigConstants.ATTR_OBJECTCLASS).asSetOfString());
              replica.setBackendId(backendId);
              replica.setSuffix(new SuffixDescriptor(baseDn, replica));
              replica.setEntries(getNumberOfEntriesForBaseDn(entries, baseDn));

              replicas.add(replica);
            }
          }
          desc.setReplicas(replicas);
        }
      }
    }
  }

  private static int getNumberOfEntriesForBaseDn(Set<String> entries, DN baseDn)
  {
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
            return Integer.parseInt(s.substring(0, index));
          }
          catch (Throwable t)
          {
            /* Ignore */
          }
          break;
        }
      }
    }
    return -1;
  }

  private static boolean isAddReplica(TopologyCacheFilter cacheFilter, DN baseDn)
  {
    return cacheFilter.searchAllBaseDNs() || cacheFilter.getBaseDNsToSearch().contains(baseDn);
  }

  private static void updateReplication(ServerDescriptor desc, ConnectionWrapper conn, TopologyCacheFilter cacheFilter)
      throws IOException
  {
    final Map<ServerProperty, Object> serverProps = desc.serverProperties;

    SearchRequest request = newSearchRequest(
        "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config",
        WHOLE_SUBTREE,
        "(objectclass=ds-cfg-synchronization-provider)",
        "ds-cfg-enabled");
    SearchResultEntry sre = conn.getConnection().searchSingleEntry(request);
    serverProps.put(ServerProperty.IS_REPLICATION_ENABLED, sre.parseAttribute("ds-cfg-enabled").asBoolean());

    Set<HostPort> allReplicationServers = new LinkedHashSet<>();

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

          int serverId = sr.parseAttribute("ds-cfg-server-id").asInteger();
          Set<HostPort> replicationServers = sr.parseAttribute("ds-cfg-replication-server").asSetOf(hostPorts());
          Set<DN> dns = sr.parseAttribute("ds-cfg-base-dn").asSetOfDN();
          for (DN dn : dns)
          {
            for (ReplicaDescriptor replica : desc.getReplicas())
            {
              if (replica.getSuffix().getDN().equals(dn))
              {
                replica.setServerId(serverId);
                replica.setReplicationServers(replicationServers);
                allReplicationServers.addAll(replicationServers);
              }
            }
          }
        }
      }
    }

    serverProps.put(ServerProperty.IS_REPLICATION_SERVER, Boolean.FALSE);

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

        serverProps.put(ServerProperty.IS_REPLICATION_SERVER, Boolean.TRUE);
        serverProps.put(ServerProperty.REPLICATION_SERVER_PORT,
            sr.parseAttribute("ds-cfg-replication-port").asInteger());
        serverProps.put(ServerProperty.REPLICATION_SERVER_ID,
            sr.parseAttribute("ds-cfg-replication-server-id").asInteger());
        allReplicationServers.addAll(sr.parseAttribute("ds-cfg-replication-server").asSetOf(hostPorts()));
        serverProps.put(ServerProperty.EXTERNAL_REPLICATION_SERVERS, allReplicationServers);
      }
    }

    serverProps.put(ServerProperty.IS_REPLICATION_SECURE,
        isReplicationSecure(conn, sre.parseAttribute("ds-cfg-enabled").asBoolean()));
  }

  private static Function<ByteString, HostPort, RuntimeException> hostPorts()
  {
    return new Function<ByteString, HostPort, RuntimeException>()
    {
      @Override
      public HostPort apply(ByteString value)
      {
        return HostPort.valueOf(value.toString());
      }
    };
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
          return sr.parseAttribute("ds-cfg-ssl-encryption").asBoolean();
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
            sr.parseAttribute("ds-sync-generation-id").asString());
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
        results.addAll(sr.parseAttribute("ds-base-dn-entry-count").asSetOfString());
      }
    }
    return results;
  }

  /**
   * Returns whether the provided backendID corresponds to a configuration backend.
   * @param backendId the backend ID to analyze
   * @return {@code true} if the the id corresponds to a configuration
   * backend and {@code false} otherwise.
   */
  private static boolean isConfigBackend(String backendId)
  {
    return "tasks".equalsIgnoreCase(backendId)
        || "schema".equalsIgnoreCase(backendId)
        || "config".equalsIgnoreCase(backendId)
        || "monitor".equalsIgnoreCase(backendId)
        || "backup".equalsIgnoreCase(backendId)
        || "ads-truststore".equalsIgnoreCase(backendId);
  }

  /**
   * Returns whether the provided ID corresponds to the schema backend.
   * @param backendId the backend ID to analyze
   * @return {@code true} if the the id corresponds to the schema backend
   * and {@code false} otherwise.
   */
  private static boolean isSchemaBackend(String backendId)
  {
    return "schema".equalsIgnoreCase(backendId);
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
   * @return {@code true} if the provided server descriptor represents the same server
   * as this object, {@code false} otherwise.
   */
  public boolean isSameServer(ServerDescriptor server)
  {
    return getId().equals(server.getId());
  }

  @Override
  public String toString()
  {
    final int defaultPort = -1;
    final int adminPort = getAdminPort(defaultPort);
    final int ldapPort = getLdapPort(defaultPort);
    final int ldapsPort = getLdapsPort(defaultPort);
    final boolean isRs = isReplicationServer();
    StringBuilder sb = new StringBuilder(getClass().getSimpleName());
    sb.append("(host-name=").append(getHostName());
    if (adminPort != defaultPort)
    {
      sb.append(", adminPort=").append(adminPort);
    }
    if (ldapPort != defaultPort)
    {
      sb.append(", ldapPort=").append(ldapPort);
    }
    if (ldapsPort != defaultPort)
    {
      sb.append(", ldapsPort=").append(ldapsPort);
    }
    sb.append(", isReplicationServer=").append(isRs);
    if (isRs)
    {
      sb.append(", replication-server-id=").append(getReplicationServerId());
    }
    appendInconsistencies(sb);
    sb.append(")");
    return sb.toString();
  }

  private void appendInconsistencies(StringBuilder sb)
  {
    Map<ServerProperty, Pair<?, ?>> inconsistencies = new HashMap<>();
    for (ServerProperty prop : ServerProperty.values())
    {
      if (prop.adsEquivalent != null)
      {
        Object propVal = toScalar(serverProperties.get(prop));
        Object propValue = propVal instanceof byte[] ? (byte[]) propVal : toStringValue(propVal);
        Object adsPropValue = adsProperties.get(prop.adsEquivalent);
        if (!Objects.equals(propValue, adsPropValue))
        {
          inconsistencies.put(prop, Pair.of(propValue, adsPropValue));
        }
      }
    }
    if (!inconsistencies.isEmpty())
    {
      sb.append(", inconsistencies=").append(inconsistencies);
    }
  }

  private Object toScalar(Object propValue)
  {
    if (propValue instanceof List)
    {
      List<?> propValues = (List<?>) propValue;
      return !propValues.isEmpty() ? propValues.get(0) : null;
    }
    return propValue;
  }

  private String toStringValue(Object propValue)
  {
    return propValue != null ? propValue.toString() : null;
  }
}
