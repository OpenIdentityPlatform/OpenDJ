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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.util.Utils;

import org.opends.messages.Message;
import org.opends.messages.QuickSetupMessages;
import static org.opends.messages.AdminToolMessages.*;

/**
 * This class is used to retrieve configuration and monitoring information using
 * LDAP protocol.
 *
 */
public class ConfigFromLDAP
{
  private HashSet<ListenerDescriptor> listeners =
    new HashSet<ListenerDescriptor>();
  private HashSet<DatabaseDescriptor> databases =
    new HashSet<DatabaseDescriptor>();
  private HashSet<String> administrativeUsers = new HashSet<String>();
  private Message errorMessage;
  private boolean replicationConfigured = false;
  private ArrayList<String> replicatedSuffixes = new ArrayList<String>();
  private HashMap<String, Integer> hmMissingChanges =
    new HashMap<String, Integer>();
  private HashMap<String, Long> hmAgeOfOldestMissingChanges =
    new HashMap<String, Long>();
  private static final Logger LOG = Logger.getLogger(
      ConfigFromLDAP.class.getName());

  private String dn;
  private String pwd;
  private String lastUrl;
  private ConnectionProtocolPolicy policy;
  private ConfigFromFile offlineConf;
  private TrustManager trustManager;

  private InitialLdapContext ctx;
  private String javaVersion;
  private int openConnections = -1;

  /**
   * Default constructor.
   *
   */
  public ConfigFromLDAP()
  {
  }

  /**
   * Sets the connection information required to contact the server using LDAP.
   * @param offlineConf the ConfigFromFile object used to retrieve the LDAP URL
   * that will be used to connect to the server.
   * @param policy the configuration policy to be used (whether we prefer the
   * most secure, the less secure, a specific method...).
   * @param dn the authentication Distinguished Name to bind.
   * @param pwd the authentication password to bind.
   * @param trustManager the trust manager to be used for the secure
   * connections.
   * @throws ConfigException if a valid URL could not be found with the provided
   * parameters.
   */
  public void setConnectionInfo(ConfigFromFile offlineConf,
      ConnectionProtocolPolicy policy, String dn, String pwd,
      TrustManager trustManager) throws ConfigException
  {
    if (offlineConf == null)
    {
      throw new IllegalArgumentException("offlineConf cannot be null.");
    }
    if (policy == null)
    {
      throw new IllegalArgumentException("policy cannot be null.");
    }
    if (dn == null)
    {
      throw new IllegalArgumentException("dn cannot be null.");
    }
    if (pwd == null)
    {
      throw new IllegalArgumentException("pwd cannot be null.");
    }
    this.trustManager = trustManager;
    this.offlineConf = offlineConf;
    this.policy = policy;
    String ldapUrl = offlineConf.getURL(policy);

    if (!Utils.areDnsEqual(dn, this.dn) ||
        !pwd.equals(this.pwd) ||
        (policy != this.policy) ||
        !ldapUrl.equals(lastUrl))
    {
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
        ctx = null;
      }
    }

    this.lastUrl = ldapUrl;
    this.dn = dn;
    this.pwd = pwd;
  }

  /**
   * Method to be called to close properly the connection.
   */
  public void closeConnection()
  {
    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
  }

  /**
   * Reads the configuration and monitoring information of the server using
   * LDAP.
   * When calling this method the thread is blocked until all the configuration
   * is read.
   *
   * This method assumes that the setConnectionInfo has been called previously.
   *
   */
  public void readConfiguration()
  {
    errorMessage = null;

    listeners.clear();
    databases.clear();
    administrativeUsers.clear();
    replicationConfigured = false;
    replicatedSuffixes.clear();
    hmMissingChanges.clear();
    hmAgeOfOldestMissingChanges.clear();
    javaVersion = null;
    openConnections = -1;

    try
    {
      InitialLdapContext ctx = getDirContext();
      updateAdministrativeUsers(ctx);
      updateListeners(ctx);
      updateReplication(ctx);
      updateDatabases(ctx);
      javaVersion = getJavaVersion(ctx);
      openConnections = getOpenConnections(ctx);
    }
    catch (NamingException ne)
    {
      String detail;
      if (ne.getMessage() != null)
      {
        detail = ne.getMessage();
      }
      else
      {
        detail = ne.toString();
      }
      if (Utils.isCertificateException(ne))
      {
        errorMessage =
          QuickSetupMessages.INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(
              detail);
      }
      else
      {
        errorMessage = ERR_READING_CONFIG_LDAP.get(detail);
      }

      /*
       *  Display the information that we find in the off line configuration.
       */
      if (listeners.isEmpty())
      {
        listeners.addAll(offlineConf.getListeners());
      }
      if (databases.isEmpty())
      {
        databases.addAll(offlineConf.getDatabases());
      }
      if (administrativeUsers.isEmpty())
      {
        administrativeUsers.addAll(offlineConf.getAdministrativeUsers());
      }
    }
    catch (Throwable t)
    {
      // Bug: this is not necessarily a bug on our side (see issue 3318).
      // Just log it.
      LOG.log(Level.SEVERE, "Unexpected error reading configuration: "+t, t);
      errorMessage = ERR_READING_CONFIG_LDAP.get(t.toString());
    }
  }


  /**
   * Returns the Administrative User DNs found using LDAP.
   * @return the Administrative User DNs found using LDAP.
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
   * Return the java version we found using LDAP.
   * @return the java version we found using LDAP.
   */
  public String getJavaVersion()
  {
    return javaVersion;
  }

  /**
   * Return the number of open connections we found using LDAP.
   * @return the number of open connections we found using LDAP.
   */
  public int getOpenConnections()
  {
    return openConnections;
  }

  /**
   * Returns the error message that we got when retrieving the information
   * using LDAP.
   * @return the error message that we got when retrieving the information
   * using LDAP.
   */
  public Message getErrorMessage()
  {
    return errorMessage;
  }

  /**
   * Returns the InitialLdapContext object to be used to retrieve configuration
   * and monitoring information.
   * @return the InitialLdapContext object to be used to retrieve configuration
   * and monitoring information.
   * @throws NamingException if we could not get an InitialLdapContext.
   * @throws ConfigException if we could not retrieve a valid LDAP URL in
   * the configuration.
   */
  private InitialLdapContext getDirContext() throws NamingException,
  ConfigException
  {
    if (ctx != null)
    {
      try
      {
        pingDirContext(ctx);
      }
      catch (NamingException ne)
      {
        try
        {
          ctx.close();
        }
        catch(NamingException xx)
        {
        }
        ctx = null;
      }
    }
    if (ctx == null)
    {
      String ldapUrl = offlineConf.getLDAPURL();
      String startTlsUrl = offlineConf.getStartTLSURL();
      String ldapsUrl = offlineConf.getLDAPSURL();
      switch (policy)
      {
      case USE_STARTTLS:
        if (startTlsUrl != null)
        {
          ctx = Utils.createStartTLSContext(startTlsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager, null);
        }
        else
        {
          throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        }
        break;
      case USE_LDAPS:
        if (ldapsUrl != null)
        {
          ctx = Utils.createLdapsContext(ldapsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        }
        break;
      case USE_LDAP:
        if (ldapUrl != null)
        {
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
        }
        else
        {
          throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        }
        break;
      case USE_MOST_SECURE_AVAILABLE:
        if (ldapsUrl != null)
        {
          ctx = Utils.createLdapsContext(ldapsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager);
        }
        else if (startTlsUrl != null)
        {
          ctx = Utils.createStartTLSContext(startTlsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null,
              trustManager, null);
        }
        else if (ldapUrl != null)
        {
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
        }
        else
        {
          throw new ConfigException(ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
        }
        break;
      case USE_LESS_SECURE_AVAILABLE:
        if (ldapUrl != null)
        {
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
        }
        else if (ldapsUrl != null)
        {
          ctx = Utils.createLdapsContext(ldapsUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager);
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
    }
    return ctx;
  }

  /**
   * Ping the specified InitialLdapContext.
   * This method sends a search request on the root entry of the DIT
   * and forward the corresponding exception (if any).
   * @param ctx the InitialLdapContext to be "pinged".
   * @throws NamingException if the ping could not be performed.
   */
  private void pingDirContext(InitialLdapContext ctx) throws NamingException {
    SearchControls sc = new SearchControls(
        SearchControls.OBJECT_SCOPE,
        0, // count limit
        0, // time limit
        new String[0], // No attributes
        false, // Don't return bound object
        false // Don't dereference link
    );
    ctx.search("", "objectclass=*", sc);
  }

  /**
   * Updates the listener configuration data we expose to the user with the
   * provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @throws NamingException if there was an error.
   */
  private void updateListeners(InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-enabled",
            "ds-cfg-listen-address",
            "ds-cfg-listen-port",
            "ds-cfg-use-ssl",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-connection-handler)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      updateConfigWithConnectionHandler(sr);

    }
  }

  /**
   * Updates the replication configuration data we expose to the user with
   * the provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @throws NamingException if there was an error.
   */
  private void updateReplication(InitialLdapContext ctx)
  throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-enabled"
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
          "ds-cfg-enabled")))
        {
          replicationConfigured = true;
        }
      }
    }
    catch (NameNotFoundException nse)
    {
    }

    ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-base-dn",
            "ds-cfg-server-id"
        });
    filter = "(objectclass=ds-cfg-replication-domain)";

    jndiName = new LdapName(
      "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");
    ArrayList<String> replicaIds = new ArrayList<String>();
    try
    {
      NamingEnumeration syncProviders = ctx.search(jndiName, filter, ctls);

      while(syncProviders.hasMore())
      {
        SearchResult sr = (SearchResult)syncProviders.next();

        replicatedSuffixes.add(getFirstValue(sr, "ds-cfg-base-dn"));
        replicaIds.add(getFirstValue(sr, "ds-cfg-server-id"));
      }
    }
    catch (NameNotFoundException nse)
    {
    }

    ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    ctls.setReturningAttributes(
    new String[] {
      "approx-older-change-not-synchronized-millis", "missing-changes",
      "base-dn", "server-id"
    });
    filter = "(missing-changes=*)";

    jndiName = new LdapName("cn=monitor");

    if (replicatedSuffixes.size() > 0)
    {
      try
      {
        NamingEnumeration monitorEntries = ctx.search(jndiName, filter, ctls);

        while(monitorEntries.hasMore())
        {
          SearchResult sr = (SearchResult)monitorEntries.next();

          String dn = getFirstValue(sr, "base-dn");
          String replicaId = getFirstValue(sr, "server-id");

          for (int i=0; i<replicatedSuffixes.size(); i++)
          {
            String baseDn = replicatedSuffixes.get(i);
            String id = replicaIds.get(i);
            if (Utils.areDnsEqual(dn, baseDn) && id.equals(replicaId))
            {
              try
              {
                hmAgeOfOldestMissingChanges.put(baseDn,
                  new Long(getFirstValue(sr,
                      "approx-older-change-not-synchronized-millis")));
              }
              catch (Throwable t)
              {
              }
              try
              {
                hmMissingChanges.put(baseDn,
                  new Integer(getFirstValue(sr, "missing-changes")));
              }
              catch (Throwable t)
              {
              }
            }
          }
        }
      }
      catch (NameNotFoundException nse)
      {
      }
    }
  }

  /**
   * Updates the database configuration data we expose to the user with the
   * provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @throws NamingException if there was an error.
   */
  private void updateDatabases(InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-base-dn",
            "ds-cfg-backend-id"
        });
    String filter = "(objectclass=ds-cfg-backend)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration databases = ctx.search(jndiName, filter, ctls);

    while(databases.hasMore())
    {
      SearchResult sr = (SearchResult)databases.next();

      updateConfigWithBackend(sr, ctx);

    }
  }

  /**
   * Updates the administrative user configuration we expose to the user with
   * the provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @throws NamingException if there was an error.
   */
  private void updateAdministrativeUsers(InitialLdapContext ctx)
  throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-alternate-bind-dn"
        });
    String filter = "(objectclass=ds-cfg-root-dn-user)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration users = ctx.search(jndiName, filter, ctls);

    while(users.hasMore())
    {
      SearchResult sr = (SearchResult)users.next();

      updateConfigWithAdministrativeUser(sr);

    }
  }

  /**
   * Returns the java version we find using the provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @return the java version we find using the provided InitialLdapContext.
   * @throws NamingException if there was an error.
   */
  private String getJavaVersion(InitialLdapContext ctx)
  throws NamingException
  {
    String v = null;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "jvmVersion"
        });
    String filter = "(objectclass=*)";

    LdapName jndiName = new LdapName("cn=System Information,cn=monitor");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      v = getFirstValue(sr, "jvmVersion");

    }
    return v;
  }

  /**
   * Returns the number of open connections we find using the provided
   * InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @return the number of open connections we find using the provided
   * InitialLdapContext.
   * @throws NamingException if there was an error.
   */
  private int getOpenConnections(InitialLdapContext ctx)
  throws NamingException
  {
    int nConnections = -1;
    String v = null;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "currentConnections"
        });
    String filter = "(objectclass=*)";

    LdapName jndiName = new LdapName("cn=monitor");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      v = getFirstValue(sr, "currentConnections");
    }
    try
    {
      nConnections = Integer.parseInt(v);
    }
    catch (Exception ex)
    {

    }
    return nConnections;

  }

  /**
   * Returns the number of entries in a given backend using the provided
   * InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @param backendID the id of the backend.
   * @return the number of entries in the backend.
   * @throws NamingException if there was an error.
   */
  private int getEntryCount(InitialLdapContext ctx, String backendID)
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

  /**
   * Create the base DN descriptor.  Assumes that the replicatedSuffixes Set
   * and replicationConfigured have already been initialized.
   */
  private BaseDNDescriptor getBaseDNDescriptor(InitialLdapContext ctx,
      String baseDn)
  throws NamingException
  {
    BaseDNDescriptor.Type type;
    int missingChanges = -1;
    long ageOfOldestMissingChange = -1;
    String mapSuffixDn = null;

    boolean replicated = false;
    if (replicationConfigured)
    {
      for (String suffixDn: replicatedSuffixes)
      {
        if (Utils.areDnsEqual(baseDn, suffixDn))
        {
          replicated = true;
          mapSuffixDn = suffixDn;
          break;
        }
      }
    }
    if (replicated)
    {
      type = BaseDNDescriptor.Type.REPLICATED;

      Integer missing = hmMissingChanges.get(mapSuffixDn);
      Long age = hmAgeOfOldestMissingChanges.get(mapSuffixDn);

      if (age != null)
      {
        ageOfOldestMissingChange = age.longValue();
      }

      if (missing != null)
      {
        missingChanges = missing.intValue();
      }
    }
    else
    {
      type = BaseDNDescriptor.Type.NOT_REPLICATED;
    }

    return new BaseDNDescriptor(type, baseDn, null, -1,
        ageOfOldestMissingChange, missingChanges);
  }

  /**
   * Updates the listener configuration data we expose to the user with the
   * provided SearchResult object.
   * @param entry the entry to analyze.
   * @throws NamingException if there was an error.
   */
  private void updateConfigWithConnectionHandler(SearchResult entry)
  throws NamingException
  {
    String address = getFirstValue(entry, "ds-cfg-listen-address");
    String port = getFirstValue(entry, "ds-cfg-listen-port");
    String addressPort;

    boolean isSecure = "true".equalsIgnoreCase(
        getFirstValue(entry, "ds-cfg-use-ssl"));

    ListenerDescriptor.Protocol protocol;
    Message protocolDescription;

    ListenerDescriptor.State state;
    if (hasObjectClass(entry, "ds-cfg-ldap-connection-handler"))
    {
      if (address == null)
      {
        address = ConfigFromFile.getDefaultLdapAddress();
      }
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
    else if (hasObjectClass(entry, "ds-cfg-jmx-connection-handler"))
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
    else if (hasObjectClass(entry, "ds-cfg-ldif-connection-handler"))
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
    else if (hasObjectClass(entry, "ds-cfg-snmp-connection-handler"))
    {
      addressPort = "0.0.0.0:"+port;
      protocolDescription = INFO_SNMP_PROTOCOL_LABEL.get();
      protocol = ListenerDescriptor.Protocol.SNMP;
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
  }

  /**
   * Updates the database configuration data we expose to the user with the
   * provided SearchResult object.
   * @param entry the entry to analyze.
   * @throws NamingException if there was an error.
   */
  private void updateConfigWithBackend(SearchResult entry,
      InitialLdapContext ctx)
  throws NamingException
  {
    String id = getFirstValue(entry, "ds-cfg-backend-id");

    if (!isConfigBackend(id))
    {
      Set<String> baseDns = getValues(entry, "ds-cfg-base-dn");
      TreeSet<BaseDNDescriptor> replicas = new TreeSet<BaseDNDescriptor>();
      int nEntries = getEntryCount(ctx, id);
      Set<String> baseDnEntries = getBaseDNEntryCount(ctx, id);
      DatabaseDescriptor db = new DatabaseDescriptor(id, replicas, nEntries);

      for (String baseDn : baseDns)
      {
        BaseDNDescriptor rep = getBaseDNDescriptor(ctx, baseDn);
        rep.setDatabase(db);
        nEntries = -1;
        for (String s : baseDnEntries)
        {
          int index = s.indexOf(" ");
          if (index != -1)
          {
            String dn = s.substring(index +1);
            if (Utils.areDnsEqual(baseDn, dn))
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
        rep.setEntries(nEntries);
        db.getBaseDns().add(rep);
      }

      databases.add(db);
    }
  }

  /**
   * Updates the administrative user configuration data we expose to the user
   * with the provided SearchResult object.
   * @param entry the entry to analyze.
   * @throws NamingException if there was an error.
   */
  private void updateConfigWithAdministrativeUser(SearchResult entry)
  throws NamingException
  {
    administrativeUsers.addAll(getValues(entry, "ds-cfg-alternate-bind-dn"));
  }

  /*
   * The following 2 methods are convenience methods to retrieve String values
   * from an entry.
   */
  private String getFirstValue(SearchResult entry, String attrName)
  throws NamingException
  {
    return Utils.getFirstValue(entry, attrName);
  }

  private Set<String> getValues(SearchResult entry, String attrName)
  throws NamingException
  {
    Set<String> values = new HashSet<String>();
    Attributes attrs = entry.getAttributes();
    if (attrs != null)
    {
      Attribute attr = attrs.get(attrName);
      if (attr != null)
      {
        for (int i=0; i<attr.size(); i++)
        {
          values.add((String)attr.get(i));
        }
      }
    }
    return values;
  }

  /**
   * Returns true if the SearchResult object is of a given objectclass.
   * @param entry the SearchResult to analyze.
   * @param ocName the objectclass.
   * @return <CODE>true</CODE> if the SearchResult is of a the objectclass and
   * <CODE>false</CODE> otherwise.
   * @throws NamingException if there was an error.
   */
  private boolean hasObjectClass(SearchResult entry, String ocName)
  throws NamingException
  {
    boolean hasObjectClass = false;
    Attributes attrs = entry.getAttributes();
    if (attrs != null)
    {
      Attribute attr = attrs.get("objectclass");
      if (attr != null)
      {
        for (int i=0; i<attr.size() && !hasObjectClass; i++)
        {
          hasObjectClass = ocName.equalsIgnoreCase((String)attr.get(i));
        }
      }
    }
    return hasObjectClass;
  }


  private boolean isConfigBackend(String id)
  {
    return ConfigFromFile.isConfigBackend(id);
  }

  /**
   * Returns the values of the ds-base-dn-entry count attributes for the given
   * backend monitor entry using the provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @param backendID the id of the backend.
   * @return the values of the ds-base-dn-entry count attribute.
   * @throws NamingException if there was an error.
   */
  private static Set<String> getBaseDNEntryCount(InitialLdapContext ctx,
      String backendID) throws NamingException
  {
    LinkedHashSet<String> v = new LinkedHashSet<String>();
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-base-dn-entry-count"
        });
    String filter = "(ds-backend-id="+backendID+")";

    LdapName jndiName = new LdapName("cn=monitor");
    NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

    while(listeners.hasMore())
    {
      SearchResult sr = (SearchResult)listeners.next();

      v.addAll(ConnectionUtils.getValues(sr, "ds-base-dn-entry-count"));
    }
    return v;
  }
}
