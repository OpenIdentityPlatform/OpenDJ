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

package org.opends.statuspanel;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.statuspanel.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;

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
  private String errorMessage;

  private String dn;
  private String pwd;
  private String ldapUrl;

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
   * @param ldapUrl the LDAP URL of the server.
   * @param dn the authentication Distinguished Name to bind.
   * @param pwd the authentication password to bind.
   */
  public void setConnectionInfo(String ldapUrl, String dn, String pwd)
  {
    if (ldapUrl == null)
    {
      throw new IllegalArgumentException("ldapUrl cannot be null.");
    }
    if (dn == null)
    {
      throw new IllegalArgumentException("dn cannot be null.");
    }
    if (pwd == null)
    {
      throw new IllegalArgumentException("pwd cannot be null.");
    }
    if (!Utils.areDnsEqual(dn, this.dn) ||
        !pwd.equals(this.pwd) ||
        !ldapUrl.equals(this.ldapUrl))
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

    this.ldapUrl = ldapUrl;
    this.dn = dn;
    this.pwd = pwd;
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
    javaVersion = null;
    openConnections = -1;

    try
    {
      InitialLdapContext ctx = getDirContext();
      updateAdministrativeUsers(ctx);
      updateListeners(ctx);
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
      String[] arg = {detail};
      errorMessage = getMsg("error-reading-config-ldap", arg);
    }
    catch (Throwable t)
    {
      // Bug
      t.printStackTrace();
      String[] arg = {t.toString()};
      errorMessage = getMsg("error-reading-config-ldap", arg);
    }
  }


  /**
   * Returns the Administrative User DNs found using LDAP.
   * @return the Administrative User DNs found using LDAP.
   */
  public HashSet<String> getAdministrativeUsers()
  {
    return administrativeUsers;
  }

  /**
   * Returns the database descriptors found using LDAP.
   * @return the database descriptors found using LDAP.
   */
  public HashSet<DatabaseDescriptor> getDatabases()
  {
    return databases;
  }

  /**
   * Returns the listener descriptors found using LDAP.
   * @return the listeners descriptors found using LDAP.
   */
  public HashSet<ListenerDescriptor> getListeners()
  {
    return listeners;
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
  public String getErrorMessage()
  {
    return errorMessage;
  }

  /**
   * Returns the InitialLdapContext object to be used to retrieve configuration
   * and monitoring information.
   * @return the InitialLdapContext object to be used to retrieve configuration
   * and monitoring information.
   * @throws NamingException if we could not get an InitialLdapContext.
   */
  private InitialLdapContext getDirContext() throws NamingException
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
      ctx = Utils.createLdapContext(ldapUrl, dn, pwd, 3000, null);
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
            "ds-cfg-connection-handler-enabled",
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
            "ds-cfg-backend-base-dn",
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
    String filter = "(objectclass=ds-cfg-root-dn)";

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
   * @param backenID the id of the backend.
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
    String protocolDescription;

    ListenerDescriptor.State state;
    if (hasObjectClass(entry, "ds-cfg-ldap-connection-handler"))
    {
      addressPort = address+":"+port;
      if (isSecure)
      {
        protocolDescription = getMsg("ldaps-protocol-label");
        protocol = ListenerDescriptor.Protocol.LDAPS;
      }
      else
      {
        protocolDescription = getMsg("ldap-protocol-label");
        protocol = ListenerDescriptor.Protocol.LDAP;
      }
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
    else if (hasObjectClass(entry, "ds-cfg-jmx-connection-handler"))
    {
      addressPort = "0.0.0.0:"+port;
      if (isSecure)
      {
        protocolDescription = getMsg("jmx-secure-protocol-label");
        protocol = ListenerDescriptor.Protocol.JMXS;
      }
      else
      {
        protocolDescription = getMsg("jmx-protocol-label");
        protocol = ListenerDescriptor.Protocol.JMX;
      }
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
      addressPort = getMsg("unknown-label");
      protocolDescription = null;
      protocol = ListenerDescriptor.Protocol.OTHER;
      /* Try to figure a name from the cn */
      String cn = getFirstValue(entry, "cn");
      if (cn != null)
      {
        int index = cn.toLowerCase().indexOf("connection handler");
        if (index > 0)
        {
          protocolDescription = cn.substring(0, index).trim();
        }
        else
        {
          protocolDescription = cn;
        }
      }
      else
      {
        protocolDescription = getMsg("undefined-protocol-label");
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
    String baseDn = getFirstValue(entry, "ds-cfg-backend-base-dn");
    String id = getFirstValue(entry, "ds-cfg-backend-id");

    if (!isConfigBackend(id))
    {
      int nEntries = getEntryCount(ctx, id);
      databases.add(new DatabaseDescriptor(id, baseDn, nEntries));
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
    String v = null;
    Attributes attrs = entry.getAttributes();
    if (attrs != null)
    {
      Attribute attr = attrs.get(attrName);
      if ((attr != null) && (attr.size() > 0))
      {
        v = (String)attr.get();
      }
    }
    return v;
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
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
