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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.statuspanel;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.server.core.DirectoryServer;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.messages.Message;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.Installation;

import static org.opends.messages.AdminToolMessages.*;

/**
 * This class is used to retrieve configuration information directly from the
 * config.ldif file.
 *
 */
public class ConfigFromFile
{
  private final ObjectClass connectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-connection-handler", true);
  private final ObjectClass ldapConnectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-ldap-connection-handler", true);
  private final ObjectClass jmxConnectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-jmx-connection-handler", true);
  private final ObjectClass ldifConnectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-ldif-connection-handler", true);
  private final ObjectClass backendOc =
    DirectoryServer.getObjectClass("ds-cfg-backend", true);
  private final ObjectClass administrativeUserOc =
    DirectoryServer.getObjectClass("ds-cfg-root-dn-user", true);
  private final ObjectClass syncProviderOc =
    DirectoryServer.getObjectClass("ds-cfg-synchronization-provider", true);
  private final ObjectClass replicationConfigOc =
    DirectoryServer.getObjectClass("ds-cfg-replication-domain", true);
  private DN replicationDomainDN;

  private HashSet<ListenerDescriptor> listeners =
    new HashSet<ListenerDescriptor>();
  private HashSet<ListenerDescriptor> startTLSListeners =
    new HashSet<ListenerDescriptor>();
  private HashSet<DatabaseDescriptor> databases =
    new HashSet<DatabaseDescriptor>();
  private HashSet<String> administrativeUsers = new HashSet<String>();
  private Message errorMessage;
  private boolean replicationConfigured = false;
  private HashSet<String> replicatedSuffixes = new HashSet<String>();

  private static final Logger LOG =
    Logger.getLogger(ConfigFromFile.class.getName());

  /**
   * Default constructor.
   *
   */
  public ConfigFromFile()
  {
  }

  /**
   * Reads the configuration from the config.ldif file.  When calling this
   * method the thread is blocked until all the configuration is read.
   *
   */
  public void readConfiguration()
  {
    errorMessage = null;
    listeners.clear();
    startTLSListeners.clear();
    databases.clear();
    administrativeUsers.clear();
    replicationConfigured = false;
    replicatedSuffixes.clear();
    LDIFReader reader = null;
    try
    {
      Installation installation = Installation.getLocal();
      LDIFImportConfig c = new LDIFImportConfig(
          Utils.getPath(installation.getCurrentConfigurationFile()));
      reader = new LDIFReader(c);
      for (Entry entry = reader.readEntry(false); entry != null;
      entry = reader.readEntry(false))
      {
        updateConfig(entry);
      }
      updateReplication();
    }
    catch (IOException ioe)
    {
      LOG.log(Level.SEVERE, "Error reading config file: "+ioe, ioe);
      errorMessage = Utils.getThrowableMsg(
          ERR_READING_CONFIG_FILE.get(), ioe);
    }
    catch (LDIFException le)
    {
      LOG.log(Level.SEVERE, "Error reading config file: "+le, le);
      errorMessage = Utils.getThrowableMsg(
          ERR_READING_CONFIG_FILE.get(), le);
    }
    catch (Throwable t)
    {
      LOG.log(Level.SEVERE, "Error reading config file: "+t, t);
      // Bug
      t.printStackTrace();
      errorMessage = Utils.getThrowableMsg(
          ERR_READING_CONFIG_FILE.get(), t);
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        }
        catch (Throwable t)
        {
        }
      }
    }
  }

  /**
   * Returns the Administrative User DNs found in the config.ldif.
   * @return the Administrative User DNs found in the config.ldif.
   */
  public HashSet<String> getAdministrativeUsers()
  {
    HashSet<String> copy = new HashSet<String>();
    copy.addAll(administrativeUsers);
    return copy;
  }

  /**
   * Returns the database descriptors found in the config.ldif.
   * @return the database descriptors found in the config.ldif.
   */
  public HashSet<DatabaseDescriptor> getDatabases()
  {
    HashSet<DatabaseDescriptor> copy = new HashSet<DatabaseDescriptor>();
    copy.addAll(databases);
    return copy;
  }

  /**
   * Returns the listener descriptors found in the config.ldif.
   * @return the listeners descriptors found in the config.ldif.
   */
  public HashSet<ListenerDescriptor> getListeners()
  {
    HashSet<ListenerDescriptor> copy = new HashSet<ListenerDescriptor>();
    copy.addAll(listeners);
    return copy;
  }

  /**
   * Returns the error message that we got when retrieving the information
   * from the config.ldif file.
   * @return the error message that we got when retrieving the information
   * from the config.ldif file.
   */
  public Message getErrorMessage()
  {
    return errorMessage;
  }

  /**
   * Returns the ldap URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   * @return the ldap URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   */
  public String getLDAPURL()
  {
    return getLDAPURL(false);
  }

  /**
   * Returns the ldaps URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   * @return the ldaps URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   */
  public String getLDAPSURL()
  {
    return getLDAPURL(true);
  }

  /**
   * Returns the ldap URL that we can use to connect to the server using Start
   * TLS based in what we found in the config.ldif file.
   * @return the ldap URL that we can use to connect to the server using Start
   * TLS based in what we found in the config.ldif file.
   */
  public String getStartTLSURL()
  {
    String url = null;
    for (ListenerDescriptor desc : startTLSListeners)
    {
      if (desc.getState() == ListenerDescriptor.State.ENABLED)
      {
        int port = -1;
        String host = "localhost";
        try
        {
          String addressPort = desc.getAddressPort();
          int index = addressPort.indexOf(":");
          if (index != -1)
          {
            port = Integer.parseInt(addressPort.substring(index+1));
            if (index > 0)
            {
              host = addressPort.substring(0, index);
            }
          }
          else
          {
            port = Integer.parseInt(addressPort);
          }
        }
        catch (Exception ex)
        {
          // Could not get the port
        }

        if (port != -1)
        {
          url = "ldap://"+ConnectionUtils.getHostNameForLdapUrl(host)+":"+port;
          break;
        }
      }
    }
    return url;
  }

  /**
   * Retuns the LDAP URL for the specified connection policy.
   * @param policy the connection policy to be used.
   * @return the LDAP URL for the specified connection policy.
   * @throws ConfigException if a valid LDAP URL could not be found.
   */
  public String getURL(ConnectionProtocolPolicy policy) throws ConfigException
  {
    String url;
    String ldapUrl = getLDAPURL();
    String startTlsUrl = getStartTLSURL();
    String ldapsUrl = getLDAPSURL();
    switch (policy)
    {
    case USE_STARTTLS:
      if (startTlsUrl != null)
      {
        url = startTlsUrl;
      }
      else
      {
        throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      break;
    case USE_LDAPS:
      if (ldapsUrl != null)
      {
        url = ldapsUrl;
      }
      else
      {
        throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      break;
    case USE_LDAP:
      if (ldapUrl != null)
      {
        url = ldapUrl;
      }
      else
      {
        throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      break;
    case USE_MOST_SECURE_AVAILABLE:
      if (ldapsUrl != null)
      {
        url = ldapsUrl;
      }
      else if (startTlsUrl != null)
      {
        url = startTlsUrl;
      }
      else if (ldapUrl != null)
      {
        url = ldapUrl;
      }
      else
      {
        throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      break;
    case USE_LESS_SECURE_AVAILABLE:
      if (ldapUrl != null)
      {
        url = ldapUrl;
      }
      else if (ldapsUrl != null)
      {
        url = ldapsUrl;
      }
      else
      {
        throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      break;
      default:
        throw new IllegalStateException("Unknown connection policy: "+
            policy);
    }
    return url;
  }

  private String getLDAPURL(boolean secure)
  {
    String url = null;

    for (ListenerDescriptor desc : getListeners())
    {
      if (desc.getState() == ListenerDescriptor.State.ENABLED)

      {
        int port = -1;
        String host = "localhost";
        try
        {
          String addressPort = desc.getAddressPort();
          int index = addressPort.indexOf(":");
          if (index != -1)
          {
            port = Integer.parseInt(addressPort.substring(index+1));
            if (index > 0)
            {
              host = addressPort.substring(0, index);
            }
          }
          else
          {
            port = Integer.parseInt(addressPort);
          }
        }
        catch (Exception ex)
        {
          // Could not get the port
        }

        if (port != -1)
        {
          if (!secure &&
              (desc.getProtocol() == ListenerDescriptor.Protocol.LDAP))
          {
            url = "ldap://"+ConnectionUtils.getHostNameForLdapUrl(host)+":"+
            port;
            break;
          }
          if (secure &&
              (desc.getProtocol() == ListenerDescriptor.Protocol.LDAPS))
          {
            url = "ldaps://"+ConnectionUtils.getHostNameForLdapUrl(host)+":"+
            port;
            break;
          }
        }
      }
    }
    return url;
  }

  /**
   * An convenience method to know if the provided ID corresponds to a
   * configuration backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to a configuration
   * backend and <CODE>false</CODE> otherwise.
   */
  static boolean isConfigBackend(String id)
  {
    return "tasks".equalsIgnoreCase(id) ||
    "schema".equalsIgnoreCase(id) ||
    "config".equalsIgnoreCase(id) ||
    "monitor".equalsIgnoreCase(id) ||
    "backup".equalsIgnoreCase(id) ||
    ADSContext.getDefaultBackendName().equalsIgnoreCase(id) ||
    "ads-truststore".equalsIgnoreCase(id) ||
    "replicationchanges".equalsIgnoreCase(id);
  }


  /**
   * Updates the configuration data we expose to the user with the provided
   * entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfig(Entry entry)
  {
   if (entry.hasObjectClass(connectionHandlerOc))
   {
     updateConfigWithConnectionHandler(entry);
   }
   else if (entry.hasObjectClass(backendOc))
   {
     updateConfigWithBackend(entry);
   }
   else if (entry.hasObjectClass(administrativeUserOc))
   {
     updateConfigWithAdministrativeUser(entry);
   }
   else if (entry.hasObjectClass(syncProviderOc))
   {
     updateConfigWithSyncProviderEntry(entry);
   }
   else if (entry.hasObjectClass(replicationConfigOc))
   {
     updateConfigWithReplConfig(entry);
   }
  }

  /**
   * Updates the listener configuration data we expose to the user with the
   * provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithConnectionHandler(Entry entry)
  {
    String address = getFirstValue(entry, "ds-cfg-listen-address");
    String port = getFirstValue(entry, "ds-cfg-listen-port");
    String addressPort;

    boolean isSecure = "true".equalsIgnoreCase(
        getFirstValue(entry, "ds-cfg-use-ssl"));

    ListenerDescriptor.Protocol protocol;
    Message protocolDescription;

    ListenerDescriptor.State state;
    if (entry.hasObjectClass(ldapConnectionHandlerOc))
    {
      addressPort = address+":"+port;
      if (isSecure)
      {
        protocolDescription = INFO_LDAPS_PROTOCOL_LABEL.get();
        protocol = ListenerDescriptor.Protocol.LDAPS;
      }
      else
      {
        protocolDescription = INFO_LDAP_PROTOCOL_LABEL.get();
        protocol = ListenerDescriptor.Protocol.LDAP;
      }
      boolean enabled = "true".equalsIgnoreCase(
          getFirstValue(entry, "ds-cfg-enabled"));
      if (enabled)
      {
        state = ListenerDescriptor.State.ENABLED;
      }
      else
      {
        state = ListenerDescriptor.State.DISABLED;
      }
    }
    else if (entry.hasObjectClass(jmxConnectionHandlerOc))
    {
      addressPort = "0.0.0.0:"+port;
      if (isSecure)
      {
        protocolDescription = INFO_JMX_SECURE_PROTOCOL_LABEL.get();
        protocol = ListenerDescriptor.Protocol.JMXS;
      }
      else
      {
        protocolDescription = INFO_JMX_PROTOCOL_LABEL.get();
        protocol = ListenerDescriptor.Protocol.JMX;
      }
      boolean enabled = "true".equalsIgnoreCase(
          getFirstValue(entry, "ds-cfg-enabled"));
      if (enabled)
      {
        state = ListenerDescriptor.State.ENABLED;
      }
      else
      {
        state = ListenerDescriptor.State.DISABLED;
      }
    }
    else if (entry.hasObjectClass(ldifConnectionHandlerOc))
    {
      addressPort = INFO_UNKNOWN_LABEL.get().toString();
      protocol = ListenerDescriptor.Protocol.LDIF;
      protocolDescription = INFO_LDIF_PROTOCOL_LABEL.get();
      boolean enabled = "true".equalsIgnoreCase(
          getFirstValue(entry, "ds-cfg-connection-handler-enabled"));
      if (enabled)
      {
        state = ListenerDescriptor.State.ENABLED;
      }
      else
      {
        state = ListenerDescriptor.State.DISABLED;
      }
    }
    else
    {
      addressPort = INFO_UNKNOWN_LABEL.get().toString();
      protocolDescription = null;
      protocol = ListenerDescriptor.Protocol.OTHER;
      /* Try to figure a name from the cn */
      String cn = getFirstValue(entry, "cn");
      if (cn != null)
      {
        int index = cn.toLowerCase().indexOf("connection handler");
        if (index > 0)
        {
          protocolDescription = Message.raw(cn.substring(0, index).trim());
        }
        else
        {
          protocolDescription = Message.raw(cn);
        }
      }
      else
      {
        protocolDescription = INFO_UNDEFINED_PROTOCOL_LABEL.get();
      }
      state = ListenerDescriptor.State.UNKNOWN;
    }
    listeners.add(new ListenerDescriptor(addressPort, protocol,
        protocolDescription, state));
    if (protocol == ListenerDescriptor.Protocol.LDAP)
    {
      String allowStartTLS = getFirstValue(entry, "ds-cfg-allow-start-tls");
      if (allowStartTLS != null)
      {
        if ("true".equalsIgnoreCase(allowStartTLS.trim()))
        {
          startTLSListeners.add(new ListenerDescriptor(addressPort, protocol,
              protocolDescription, state));
        }
      }
    }
  }

  /**
   * Updates the database configuration data we expose to the user with the
   * provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithBackend(Entry entry)
  {
    String id = getFirstValue(entry, "ds-cfg-backend-id");
    int nEntries = -1; // Unknown

    if (!isConfigBackend(id))
    {
      Set<String> baseDns = getValues(entry, "ds-cfg-base-dn");
      TreeSet<BaseDNDescriptor> replicas = new TreeSet<BaseDNDescriptor>();

      DatabaseDescriptor db = new DatabaseDescriptor(id, replicas, nEntries);

      for (String baseDn : baseDns)
      {
        BaseDNDescriptor rep = getBaseDNDescriptor(entry, baseDn);
        rep.setDatabase(db);
        db.getBaseDns().add(rep);
      }
      databases.add(db);
    }
  }

  /**
   * Updates the administrative user configuration data we expose to the user
   * with the provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithAdministrativeUser(Entry entry)
  {
    administrativeUsers.addAll(getValues(entry, "ds-cfg-alternate-bind-dn"));
  }

  /**
   * Updates the replication configuration data we expose to the user with
   * the provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithSyncProviderEntry(Entry entry)
  {
    if ("true".equalsIgnoreCase(getFirstValue(entry,
        "ds-cfg-enabled")))
    {
      replicationConfigured = true;
    }
    else
    {
      replicationConfigured = false;
    }
  }


  /**
   * Updates the databases suffixes with the list of replicated suffixes
   * found.
   */
  private void updateReplication()
  {
    if (replicationConfigured)
    {
      for (String suffixDn: replicatedSuffixes)
      {
        BaseDNDescriptor replica = null;
        for (DatabaseDescriptor db: databases)
        {
          Set<BaseDNDescriptor> replicas = db.getBaseDns();
          for (BaseDNDescriptor rep: replicas)
          {
            if (Utils.areDnsEqual(rep.getDn(), suffixDn))
            {
              replica = rep;
              break;
            }
          }
          if (replica != null)
          {
            break;
          }
        }
        if (replica != null)
        {
          replica.setType(BaseDNDescriptor.Type.REPLICATED);        }
      }
    }
  }

  /**
   * Updates the replication configuration data we expose to the user with
   * the provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithReplConfig(Entry entry)
  {
    if (replicationDomainDN == null)
    {
      try
      {
        replicationDomainDN = DN.decode(
            "cn=domains,cn=Multimaster Synchronization,"+
            "cn=Synchronization Providers,cn=config");
      }
      catch (Throwable t)
      {
        // Bug
        throw new IllegalStateException("Bug: "+t, t);
      }
    }
    if (entry.getDN().isDescendantOf(replicationDomainDN))
    {
      replicatedSuffixes.addAll(getValues(entry, "ds-cfg-base-dn"));
    }
  }

  /*
   * The following 2 methods are convenience methods to retrieve String values
   * from an entry.
   */
  private Set<String> getValues(Entry entry, String attrName)
  {
    Set<String> values = new HashSet<String>();
    List<Attribute> attrs = entry.getAttribute(attrName);
    if ((attrs != null) && attrs.size() > 0)
    {
      Attribute attr = attrs.iterator().next();
      LinkedHashSet<AttributeValue> vs = attr.getValues();
      if ((vs != null) && (vs.size() > 0))
      {
        for (AttributeValue v : vs)
        {
          values.add(v.getStringValue());
        }
      }
    }
    return values;
  }

  private String getFirstValue(Entry entry, String attrName)
  {
    String v = null;
    Set<String> values = getValues(entry, attrName);
    if (values.size() > 0)
    {
      v = values.iterator().next();
    }
    return v;
  }

  /**
   * Create a non replicated base DN descriptor.
   */
  private BaseDNDescriptor getBaseDNDescriptor(Entry entry, String baseDn)
  {
    return new BaseDNDescriptor(BaseDNDescriptor.Type.NOT_REPLICATED,
        baseDn, null, -1, -1, -1);
  }
}
