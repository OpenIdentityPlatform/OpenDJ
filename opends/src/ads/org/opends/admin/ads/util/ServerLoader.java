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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.admin.ads.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.TimeLimitExceededException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.TopologyCacheFilter;
import org.opends.admin.ads.ADSContext.ServerProperty;

/**
 * Class used to load the configuration of a server.  Basically the code
 * uses some provided properties and authentication information to connect
 * to the server and then generate a ServerDescriptor object based on the
 * read configuration.
 */
public class ServerLoader extends Thread
{
  private Map<ServerProperty,Object> serverProperties;
  private boolean isOver;
  private boolean isInterrupted;
  private String lastLdapUrl;
  private TopologyCacheException lastException;
  private ServerDescriptor serverDescriptor;
  private ApplicationTrustManager trustManager;
  private String dn;
  private String pwd;
  private LinkedHashSet<PreferredConnection> preferredLDAPURLs;
  private TopologyCacheFilter filter;

  private static final Logger LOG =
    Logger.getLogger(ServerLoader.class.getName());

  /**
   * Constructor.
   * @param serverProperties the server properties of the server we want to
   * load.
   * @param dn the DN that we must use to bind to the server.
   * @param pwd the password that we must use to bind to the server.
   * @param trustManager the ApplicationTrustManager to be used when we try
   * to connect to the server.
   * @param preferredLDAPURLs the list of preferred LDAP URLs that we want
   * to use to connect to the server.  They will be used only if they correspond
   * to the URLs that we found in the the server properties.
   * @param filter the topology cache filter to be used.  This can be used not
   * to retrieve all the information.
   */
  public ServerLoader(Map<ServerProperty,Object> serverProperties,
      String dn, String pwd, ApplicationTrustManager trustManager,
      LinkedHashSet<PreferredConnection> preferredLDAPURLs,
      TopologyCacheFilter filter)
  {
    this.serverProperties = serverProperties;
    this.dn = dn;
    this.pwd = pwd;
    this.trustManager = trustManager;
    this.preferredLDAPURLs =
      new LinkedHashSet<PreferredConnection>(preferredLDAPURLs);
    this.filter = filter;
  }

  /**
   * Returns the ServerDescriptor that could be retrieved.
   * @return the ServerDescriptor that could be retrieved.
   */
  public ServerDescriptor getServerDescriptor()
  {
    if (serverDescriptor == null)
    {
      serverDescriptor = ServerDescriptor.createStandalone(serverProperties);
    }
    serverDescriptor.setLastException(lastException);
    return serverDescriptor;
  }

  /**
   * Returns the last exception that occurred while trying to generate
   * the ServerDescriptor object.
   * @return the last exception that occurred while trying to generate
   * the ServerDescriptor object.
   */
  public TopologyCacheException getLastException()
  {
    return lastException;
  }

  /**
   * {@inheritDoc}
   */
  public void interrupt()
  {
    if (!isOver)
    {
      isInterrupted = true;
      String ldapUrl = getLastLdapUrl();
      if (ldapUrl == null)
      {
        LinkedHashSet<PreferredConnection> urls = getLDAPURLsByPreference();
        if (!urls.isEmpty())
        {
          ldapUrl = urls.iterator().next().getLDAPURL();
        }
      }
      lastException = new TopologyCacheException(
          TopologyCacheException.Type.TIMEOUT,
          new TimeLimitExceededException("Timeout reading server: "+ldapUrl),
          trustManager, ldapUrl);
      LOG.log(Level.WARNING, "Timeout reading server: "+ldapUrl);
    }
    super.interrupt();
  }

  /**
   * The method where we try to generate the ServerDescriptor object.
   */
  public void run()
  {
    lastException = null;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createContext();
      serverDescriptor = ServerDescriptor.createStandalone(ctx, filter);
      serverDescriptor.setAdsProperties(serverProperties);
    }
    catch (NoPermissionException npe)
    {
      LOG.log(Level.WARNING,
          "Permissions error reading server: "+getLastLdapUrl(), npe);
      if (!isAdministratorDn())
      {
        lastException = new TopologyCacheException(
                TopologyCacheException.Type.NOT_GLOBAL_ADMINISTRATOR, npe,
                trustManager, getLastLdapUrl());
      }
      else
      {
        lastException =
          new TopologyCacheException(
              TopologyCacheException.Type.NO_PERMISSIONS, npe,
              trustManager, getLastLdapUrl());
      }
    }
    catch (AuthenticationException ae)
    {
      LOG.log(Level.WARNING,
          "Authentication exception: "+getLastLdapUrl(), ae);
      if (!isAdministratorDn())
      {
        lastException = new TopologyCacheException(
                TopologyCacheException.Type.NOT_GLOBAL_ADMINISTRATOR, ae,
                trustManager, getLastLdapUrl());
      }
      else
      {
        lastException =
          new TopologyCacheException(
              TopologyCacheException.Type.GENERIC_READING_SERVER, ae,
              trustManager, getLastLdapUrl());
      }
    }
    catch (NamingException ne)
    {
      LOG.log(Level.WARNING,
          "NamingException error reading server: "+getLastLdapUrl(), ne);
      if (ctx == null)
      {
        lastException =
            new TopologyCacheException(
                TopologyCacheException.Type.GENERIC_CREATING_CONNECTION, ne,
                trustManager, getLastLdapUrl());
      }
      else
      {
        lastException =
          new TopologyCacheException(
              TopologyCacheException.Type.GENERIC_READING_SERVER, ne,
              trustManager, getLastLdapUrl());
      }
    }
    catch (Throwable t)
    {
      if (!isInterrupted)
      {
        LOG.log(Level.WARNING,
            "Generic error reading server: "+getLastLdapUrl(), t);
        LOG.log(Level.WARNING, "server Properties: "+serverProperties);
        lastException =
            new TopologyCacheException(TopologyCacheException.Type.BUG, t);
      }
    }
    finally
    {
      isOver = true;
      try
      {
        if (ctx != null)
        {
          ctx.close();
        }
      }
      catch (Throwable t)
      {
      }
    }
  }

  /**
   * Create an InitialLdapContext based in the provide server properties and
   * authentication data provided in the constructor.
   * @return an InitialLdapContext based in the provide server properties and
   * authentication data provided in the constructor.
   * @throws NamingException if an error occurred while creating the
   * InitialLdapContext.
   */
  public InitialLdapContext createContext() throws NamingException
  {
    InitialLdapContext ctx = null;
    if (trustManager != null)
    {
      trustManager.resetLastRefusedItems();

      String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
      trustManager.setHost(host);
    }

    /* Try to connect to the server in a certain order of preference.  If an
     * URL fails, we will try with the others.
     */
    LinkedHashSet<PreferredConnection> conns = getLDAPURLsByPreference();

    for (PreferredConnection connection : conns)
    {
      if (ctx == null)
      {
        lastLdapUrl = connection.getLDAPURL();
        switch (connection.getType())
        {
        case LDAPS:
          ctx = ConnectionUtils.createLdapsContext(lastLdapUrl, dn, pwd,
              ConnectionUtils.getDefaultLDAPTimeout(), null, trustManager,
              null);
          break;
        case START_TLS:
          ctx = ConnectionUtils.createStartTLSContext(lastLdapUrl, dn, pwd,
              ConnectionUtils.getDefaultLDAPTimeout(), null, trustManager,
              null, null);
          break;
        default:
          ctx = ConnectionUtils.createLdapContext(lastLdapUrl, dn, pwd,
              ConnectionUtils.getDefaultLDAPTimeout(), null);
        }
      }
    }
    return ctx;
  }

  /**
   * Returns the last LDAP URL to which we tried to connect.
   * @return the last LDAP URL to which we tried to connect.
   */
  private String getLastLdapUrl()
  {
    return lastLdapUrl;
  }

  /**
   * Returns the non-secure LDAP URL for the given server properties.  It
   * returns NULL if according to the server properties no non-secure LDAP URL
   * can be generated (LDAP disabled or port not defined).
   * @param serverProperties the server properties to be used to generate
   * the non-secure LDAP URL.
   * @return the non-secure LDAP URL for the given server properties.
   */
  private String getLdapUrl(Map<ServerProperty,Object> serverProperties)
  {
    String ldapUrl = null;
    Object v = serverProperties.get(ServerProperty.LDAP_ENABLED);
    boolean ldapEnabled = (v != null) && "true".equalsIgnoreCase(v.toString());
    if (ldapEnabled)
    {
      ldapUrl = "ldap://"+getHostNameForLdapUrl(serverProperties)+":"+
      serverProperties.get(ServerProperty.LDAP_PORT);
    }
    return ldapUrl;
  }

  /**
   * Returns the StartTLS LDAP URL for the given server properties.  It
   * returns NULL if according to the server properties no StartTLS LDAP URL
   * can be generated (StartTLS disabled or port not defined).
   * @param serverProperties the server properties to be used to generate
   * the StartTLS LDAP URL.
   * @return the StartTLS LDAP URL for the given server properties.
   */
  private String getStartTlsLdapUrl(Map<ServerProperty,Object> serverProperties)
  {
    String ldapUrl = null;
    Object v = serverProperties.get(ServerProperty.LDAP_ENABLED);
    boolean ldapEnabled = (v != null) && "true".equalsIgnoreCase(v.toString());
    v = serverProperties.get(ServerProperty.STARTTLS_ENABLED);
    boolean startTLSEnabled = (v != null) &&
    "true".equalsIgnoreCase(v.toString());
    if (ldapEnabled && startTLSEnabled)
    {
      ldapUrl = "ldap://"+getHostNameForLdapUrl(serverProperties)+":"+
      serverProperties.get(ServerProperty.LDAP_PORT);
    }
    return ldapUrl;
  }

  /**
   * Returns the LDAPs URL for the given server properties.  It
   * returns NULL if according to the server properties no LDAPS URL
   * can be generated (LDAPS disabled or port not defined).
   * @param serverProperties the server properties to be used to generate
   * the LDAPS URL.
   * @return the LDAPS URL for the given server properties.
   */
  private String getLdapsUrl(Map<ServerProperty,Object> serverProperties)
  {
    String ldapsUrl = null;
    Object v = serverProperties.get(ServerProperty.LDAPS_ENABLED);
    boolean ldapsEnabled = (v != null) && "true".equalsIgnoreCase(v.toString());
    if (ldapsEnabled)
    {
      ldapsUrl = "ldaps://"+getHostNameForLdapUrl(serverProperties)+":"+
      serverProperties.get(ServerProperty.LDAPS_PORT);
    }
    return ldapsUrl;
  }

  /**
   * Returns the host name to be used to generate an LDAP URL based on the
   * contents of the provided server properties.
   * @param serverProperties the server properties.
   * @return the host name to be used to generate an LDAP URL based on the
   * contents of the provided server properties.
   */
  private String getHostNameForLdapUrl(
      Map<ServerProperty,Object> serverProperties)
  {
    String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
    return ConnectionUtils.getHostNameForLdapUrl(host);
  }

  /**
   * Returns whether the DN provided in the constructor is a Global
   * Administrator DN or not.
   * @return <CODE>true</CODE> if the DN provided in the constructor is a Global
   * Administrator DN and <CODE>false</CODE> otherwise.
   */
  private boolean isAdministratorDn()
  {
    boolean isAdministratorDn = false;
    try
    {
      LdapName theDn = new LdapName(dn);
      LdapName containerDn =
        new LdapName(ADSContext.getAdministratorContainerDN());
      isAdministratorDn = theDn.startsWith(containerDn);
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error parsing authentication DNs.", t);
    }
    return isAdministratorDn;
  }

  /**
   * Returns the list of LDAP URLs that can be used to connect to the server.
   * They are ordered so that the first URL is the preferred URL to be used.
   * @return the list of LDAP URLs that can be used to connect to the server.
   * They are ordered so that the first URL is the preferred URL to be used.
   */
  private LinkedHashSet<PreferredConnection> getLDAPURLsByPreference()
  {
    LinkedHashSet<PreferredConnection> ldapUrls =
      new LinkedHashSet<PreferredConnection>();

    String ldapsUrl = getLdapsUrl(serverProperties);
    String startTLSUrl = getStartTlsLdapUrl(serverProperties);
    String ldapUrl = getLdapUrl(serverProperties);

    /**
     * Check the preferred connections passed in the constructor.
     */
    for (PreferredConnection connection : preferredLDAPURLs)
    {
      String url = connection.getLDAPURL();
      if (url.equalsIgnoreCase(ldapsUrl) &&
          connection.getType() == PreferredConnection.Type.LDAPS)
      {
        ldapUrls.add(connection);
      }
      else if (url.equalsIgnoreCase(startTLSUrl) &&
          connection.getType() == PreferredConnection.Type.START_TLS)
      {
        ldapUrls.add(connection);
      }
      else if (url.equalsIgnoreCase(ldapUrl) &&
          connection.getType() == PreferredConnection.Type.LDAP)
      {
        ldapUrls.add(connection);
      }
    }

    if (ldapsUrl != null)
    {
      ldapUrls.add(
          new PreferredConnection(ldapsUrl, PreferredConnection.Type.LDAPS));
    }
    if (startTLSUrl != null)
    {
      ldapUrls.add(new PreferredConnection(startTLSUrl,
              PreferredConnection.Type.START_TLS));
    }
    if (ldapUrl != null)
    {
      ldapUrls.add(new PreferredConnection(ldapUrl,
          PreferredConnection.Type.LDAP));
    }
    return ldapUrls;
  }
}
