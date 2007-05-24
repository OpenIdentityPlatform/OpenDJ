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

package org.opends.admin.ads.util;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCacheException;
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
   */
  public ServerLoader(Map<ServerProperty,Object> serverProperties,
      String dn, String pwd, ApplicationTrustManager trustManager)
  {
    this.serverProperties = serverProperties;
    this.dn = dn;
    this.pwd = pwd;
    this.trustManager = trustManager;
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
      String ldapUrl = getLdapUrl(serverProperties);
      if (ldapUrl == null)
      {
        ldapUrl = getLdapsUrl(serverProperties);
      }
      lastException = new TopologyCacheException(
          TopologyCacheException.Type.TIMEOUT, null, trustManager, ldapUrl);
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
      serverDescriptor = ServerDescriptor.createStandalone(ctx);
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
    trustManager.resetLastRefusedItems();
    String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
    trustManager.setHost(host);
    lastLdapUrl = getLdapsUrl(serverProperties);

    if (lastLdapUrl == null)
    {
      lastLdapUrl = getStartTlsLdapUrl(serverProperties);
      if (lastLdapUrl == null)
      {
        lastLdapUrl = getLdapUrl(serverProperties);
        ctx = ConnectionUtils.createLdapContext(lastLdapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null);
      }
      else
      {
        ctx = ConnectionUtils.createStartTLSContext(lastLdapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null, trustManager,
            null);
      }
    }
    else
    {
      ctx = ConnectionUtils.createLdapsContext(lastLdapUrl, dn, pwd,
          ConnectionUtils.getDefaultLDAPTimeout(), null, trustManager);
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
      ldapUrl = "ldaps://"+getHostNameForLdapUrl(serverProperties)+":"+
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
      isAdministratorDn = theDn.endsWith(containerDn);
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error parsing authentication DNs.", t);
    }
    return isAdministratorDn;
  }
}
