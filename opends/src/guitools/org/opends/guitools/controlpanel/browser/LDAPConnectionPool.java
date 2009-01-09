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

package org.opends.guitools.controlpanel.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.event.ReferralAuthenticationListener;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.SearchScope;

/**
 * An LDAPConnectionPool is a pool of LDAPConnection.
 * <BR><BR>
 * When a client class needs to access an LDAPUrl, it simply passes
 * this URL to getConnection() and gets an LDAPConnection back.
 * When the client has finished with this LDAPConnection, it *must*
 * pass it releaseConnection() which will take care of its disconnection
 * or caching.
 * <BR><BR>
 * LDAPConnectionPool maintains a pool of authentications. This pool
 * is populated using registerAuth(). When getConnection() has created
 * a new connection for accessing a host:port, it looks in the authentication
 * pool if any authentication is available for this host:port and, if yes,
 * tries to bind the connection. If no authentication is available, the
 * returned connection is simply connected (ie anonymous bind).
 * <BR><BR>
 * LDAPConnectionPool shares connections and maintains a usage counter
 * for each connection: two calls to getConnection() withe the same URL
 * will return the same connection. Two calls to releaseConnection() will
 * be needed to make the connection 'potentially disconnectable'.
 * <BR><BR>
 * releaseConnection() does not disconnect systematically a connection
 * whose usage counter is null. It keeps it connected a while (TODO:
 * to be implemented).
 * <BR><BR>
 * TODO: synchronization is a bit simplistic...
 */
public class LDAPConnectionPool {

  HashMap<String, AuthRecord> authTable = new HashMap<String, AuthRecord>();
  HashMap<String, ConnectionRecord> connectionTable =
    new HashMap<String, ConnectionRecord>();

  ArrayList<ReferralAuthenticationListener> listeners;

  private Control[] requestControls = new Control[] {};
  private ApplicationTrustManager trustManager;

  /**
   * Returns <CODE>true</CODE> if the connection passed is registered in the
   * connection pool, <CODE>false</CODE> otherwise.
   * @param ctx the connection.
   * @return <CODE>true</CODE> if the connection passed is registered in the
   * connection pool, <CODE>false</CODE> otherwise.
   */
  public boolean isConnectionRegistered(InitialLdapContext ctx) {
    boolean isConnectionRegistered = false;
    for (String key : connectionTable.keySet())
    {
      ConnectionRecord cr = connectionTable.get(key);
      if (cr.ctx != null) {
        isConnectionRegistered =
         ConnectionUtils.getHostName(cr.ctx).equals(
             ConnectionUtils.getHostName(ctx)) &&
        (ConnectionUtils.getPort(cr.ctx) == ConnectionUtils.getPort(ctx)) &&
        ConnectionUtils.getBindDN(cr.ctx).equals(
            ConnectionUtils.getBindDN(ctx)) &&
        ConnectionUtils.getBindPassword(cr.ctx).equals(
            ConnectionUtils.getBindPassword(ctx)) &&
        (ConnectionUtils.isSSL(cr.ctx) == ConnectionUtils.isSSL(ctx)) &&
        (ConnectionUtils.isStartTLS(cr.ctx) == ConnectionUtils.isStartTLS(ctx));
      }
      if (isConnectionRegistered)
      {
        break;
      }
    }
    return isConnectionRegistered;
  }

  /**
   * Registers a connection in this connection pool.
   * @param ctx the connection to be registered.
   */
  public void registerConnection(InitialLdapContext ctx) {
    registerAuth(ctx);
    LDAPURL url = makeLDAPUrl(
                  ConnectionUtils.getHostName(ctx),
                  ConnectionUtils.getPort(ctx),
                  "",
                  ConnectionUtils.isSSL(ctx)
                  );
    String key = makeKeyFromLDAPUrl(url);
    ConnectionRecord cr = new ConnectionRecord();
    cr.ctx = ctx;
    cr.counter = 1;
    cr.disconnectAfterUse = false;
    connectionTable.put(key, cr);
  }

  /**
   * Unregisters a connection from this connection pool.
   * @param ctx the connection to be unregistered.
   * @throws NamingException if there is a problem unregistering the connection.
   */
  public void unregisterConnection(InitialLdapContext ctx)
  throws NamingException
  {
    LDAPURL url = makeLDAPUrl(
        ConnectionUtils.getHostName(ctx),
        ConnectionUtils.getPort(ctx),
        "",
        ConnectionUtils.isSSL(ctx));
    unRegisterAuth(url);
    String key = makeKeyFromLDAPUrl(url);
    connectionTable.remove(key);
  }

  /**
   * Adds a referral authentication listener.
   * @param listener the referral authentication listener.
   */
  public void addReferralAuthenticationListener(
      ReferralAuthenticationListener listener) {
    if (listeners == null) {
      listeners = new ArrayList<ReferralAuthenticationListener>();
    }
    listeners.add(listener);
  }

  /**
   * Removes a referral authentication listener.
   * @param listener the referral authentication listener.
   */
  public void removeReferralAuthenticationListener(
      ReferralAuthenticationListener listener) {
    if (listeners != null) {
      listeners.remove(listener);
    }
  }

  /**
   * Returns an LDAPConnection for accessing the specified url.
   * If no connection are available for the protocol/host/port
   * of the URL, getConnection() makes a new one and call connect().
   * If authentication data available for this protocol/host/port,
   * getConnection() call bind() on the new connection.
   * If connect() or bind() failed, getConnection() forward the
   * NamingException.
   * When getConnection() succeeds, the returned connection must
   * be passed to releaseConnection() after use.
   * @param ldapUrl the LDAP URL to which the connection must connect.
   * @return a connection to the provided LDAP URL.
   * @throws NamingException if there was an error connecting.
   */
  public InitialLdapContext getConnection(LDAPURL ldapUrl)
  throws NamingException {
    String key = makeKeyFromLDAPUrl(ldapUrl);
    ConnectionRecord cr;

    synchronized(this) {
      cr = connectionTable.get(key);
      if (cr == null) {
        cr = new ConnectionRecord();
        cr.ctx = null;
        cr.counter = 1;
        cr.disconnectAfterUse = false;
        connectionTable.put(key, cr);
      }
      else {
        cr.counter++;
      }
    }

    synchronized(cr) {
      try {
        if (cr.ctx == null) {
          cr.ctx = createLDAPConnection(ldapUrl,
              authTable.get(key));
          cr.ctx.setRequestControls(requestControls);
        }
      }
      catch(NamingException x) {
        synchronized (this) {
          cr.counter--;
          if (cr.counter == 0) {
            connectionTable.remove(key);
          }
        }
        throw x;
      }
    }

    return cr.ctx;
  }

  /**
   * Sets the request controls to be used by the connections of this connection
   * pool.
   * @param ctls the request controls.
   * @throws NamingException if an error occurs updating the connections.
   */
  public synchronized void setRequestControls(Control[] ctls)
  throws NamingException
  {
    requestControls = ctls;
    for (ConnectionRecord cr : connectionTable.values())
    {
      if (cr.ctx != null)
      {
        cr.ctx.setRequestControls(requestControls);
      }
    }
  }


  /**
   * Release an LDAPConnection created by getConnection().
   * The connection should be considered as virtually disconnected
   * and not be used anymore.
   * @param ctx the connection to be released.
   */
  public synchronized void releaseConnection(InitialLdapContext ctx) {

    String targetKey = null;
    ConnectionRecord targetRecord = null;
    synchronized(this) {
      for (String key : connectionTable.keySet()) {
        ConnectionRecord cr = connectionTable.get(key);
        if (cr.ctx == ctx) {
          targetKey = key;
          targetRecord = cr;
          if (targetKey != null)
          {
            break;
          }
        }
      }
    }

    if (targetRecord == null) { // ldc is not in _connectionTable -> bug
      throw new IllegalArgumentException("Invalid LDAP connection");
    }
    else {
      synchronized(targetRecord) {
        targetRecord.counter--;
        if ((targetRecord.counter == 0) && targetRecord.disconnectAfterUse) {
          disconnectAndRemove(targetRecord);
        }
      }
    }
  }


  /**
   * Disconnect the connections which are not being used.
   * Connections being used will be disconnected as soon
   * as they are released.
   */
  public synchronized void flush() {
    for (ConnectionRecord cr : connectionTable.values())
    {
      if (cr.counter <= 0) {
        disconnectAndRemove(cr);
      }
      else {
        cr.disconnectAfterUse = true;
      }
    }
  }


  /**
   * Register authentication data.
   * If authentication data are already available for the protocol/host/port
   * specified in the LDAPURl, they are replaced by the new data.
   * If true is passed as 'connect' parameter, registerAuth() creates the
   * connection and attemps to connect() and bind() . If connect() or bind()
   * fail, registerAuth() forwards the NamingException and does not register
   * the authentication data.
   * @param ldapUrl the LDAP URL of the server.
   * @param dn the bind DN.
   * @param pw the password.
   * @param connect whether to connect or not to the server with the
   * provided authentication (for testing purposes).
   * @throws NamingException if an error occurs connecting.
   */
  public void registerAuth(LDAPURL ldapUrl, String dn, String pw,
      boolean connect)
  throws NamingException {

    String key = makeKeyFromLDAPUrl(ldapUrl);
    AuthRecord ar;
    ar = new AuthRecord();
    ar.ldapUrl  = ldapUrl;
    ar.dn       = dn;
    ar.password = pw;

    if (connect) {
      InitialLdapContext ctx = createLDAPConnection(ldapUrl, ar);
      ctx.close();
    }

    synchronized(this) {
      authTable.put(key, ar);
      ConnectionRecord cr = connectionTable.get(key);
      if (cr != null) {
        if (cr.counter <= 0) {
          disconnectAndRemove(cr);
        }
        else {
          cr.disconnectAfterUse = true;
        }
      }
    }
    notifyListeners();

  }


  /**
   * Register authentication data from an existing connection.
   * This routine recreates the LDAP URL corresponding to
   * the connection and passes it to registerAuth(LDAPURL).
   * @param ctx the connection that we retrieve the authentication information
   * from.
   */
  public void registerAuth(InitialLdapContext ctx) {
    LDAPURL url = makeLDAPUrl(
      ConnectionUtils.getHostName(ctx),
      ConnectionUtils.getPort(ctx),
      "",
      ConnectionUtils.isSSL(ctx));
    try {
      registerAuth(url, ConnectionUtils.getBindDN(ctx),
          ConnectionUtils.getBindPassword(ctx), false);
    }
    catch (NamingException x) {
      throw new IllegalStateException("Bug");
    }
  }


  /**
   * Unregister authentication data.
   * If for the given url there's a connection, try to bind as anonymous.
   * If unbind fails throw NamingException.
   * @param ldapUrl the url associated with the authentication to be
   * unregistered.
   * @throws NamingException if the unbind fails.
   */
  public void unRegisterAuth(LDAPURL ldapUrl) throws NamingException {
    String key = makeKeyFromLDAPUrl(ldapUrl);

    authTable.remove(key);
    notifyListeners();
  }

  /**
   * Get authentication DN registered for this url.
   * @param ldapUrl the LDAP URL for which we want to get authentication DN.
   * @return the bind DN of the authentication.
   */
  public synchronized String getAuthDN(LDAPURL ldapUrl) {
    String result;
    String key = makeKeyFromLDAPUrl(ldapUrl);
    AuthRecord ar = authTable.get(key);
    if (ar == null) {
      result = null;
    }
    else {
      result = ar.dn;
    }
    return result;
  }


  /**
   * Get authentication password registered for this url.
   * @param ldapUrl the LDAP URL for which we want to get authentication
   * password.
   * @return the password of the authentication.
   */
  public synchronized String getAuthPassword(LDAPURL ldapUrl) {
    String result;
    String key = makeKeyFromLDAPUrl(ldapUrl);
    AuthRecord ar = authTable.get(key);
    if (ar == null) {
      result = null;
    }
    else {
      result = ar.password;
    }
    return result;
  }


  /**
   * Disconnect the connection associated to a record
   * and remove the record from connectionTable.
   * @param cr the ConnectionRecord to remove.
   */
  private void disconnectAndRemove(ConnectionRecord cr)
  {
    String key = makeKeyFromRecord(cr);
    connectionTable.remove(key);
    try
    {
      cr.ctx.close();
    }
    catch (NamingException x)
    {
      // Bizarre. However it's not really a problem here.
    }
  }

  /**
   * Notifies the listeners that a referral authentication change happened.
   *
   */
  private void notifyListeners()
  {
    for (ReferralAuthenticationListener listener : listeners)
    {
      listener.notifyAuthDataChanged();
    }
  }

  /**
   * Make the key string for an LDAP URL.
   * @param url the LDAP URL.
   * @return the key to be used in Maps for the provided LDAP URL.
   */
  private static String makeKeyFromLDAPUrl(LDAPURL url) {
    String protocol = isSecureLDAPUrl(url) ? "LDAPS" : "LDAP";
    return protocol + ":" + url.getHost() + ":" + url.getPort();
  }


  /**
   * Make the key string for an connection record.
   * @param rec the connection record.
   * @return the key to be used in Maps for the provided connection record.
   */
  private static String makeKeyFromRecord(ConnectionRecord rec) {
    String protocol = ConnectionUtils.isSSL(rec.ctx) ? "LDAPS" : "LDAP";
    return protocol + ":" + ConnectionUtils.getHostName(rec.ctx) + ":" +
    ConnectionUtils.getPort(rec.ctx);
  }

  /**
   * Creates an LDAP Connection for a given LDAP URL and using the
   * authentication of a AuthRecord.
   * @param ldapUrl the LDAP URL.
   * @param ar the authentication information.
   * @return a connection.
   * @throws NamingException if an error occurs when connecting.
   */
  private InitialLdapContext createLDAPConnection(LDAPURL ldapUrl,
      AuthRecord ar) throws NamingException
  {
    InitialLdapContext ctx;

    if (isSecureLDAPUrl(ldapUrl))
    {
      ctx = ConnectionUtils.createLdapsContext(ldapUrl.toString(), ar.dn,
          ar.password, ConnectionUtils.getDefaultLDAPTimeout(), null,
          getTrustManager() , getKeyManager());
    }
    else
    {
      ctx = ConnectionUtils.createLdapContext(ldapUrl.toString(), ar.dn,
          ar.password, ConnectionUtils.getDefaultLDAPTimeout(), null);
    }
    return ctx;
  }

  /**
   * Sets the ApplicationTrustManager used by the connection pool to
   * connect to servers.
   * @param trustManager the ApplicationTrustManager.
   */
  public void setTrustManager(ApplicationTrustManager trustManager)
  {
    this.trustManager = trustManager;
  }

  /**
   * Returns the ApplicationTrustManager used by the connection pool to
   * connect to servers.
   * @return the ApplicationTrustManager used by the connection pool to
   * connect to servers.
   */
  public ApplicationTrustManager getTrustManager()
  {
    return trustManager;
  }

  private KeyManager getKeyManager()
  {
//  TODO: we should get it from ControlPanelInfo
    return null;
  }

  /**
   * Returns whether the URL is ldaps URL or not.
   * @param url the URL.
   * @return <CODE>true</CODE> if the LDAP URL is secure and <CODE>false</CODE>
   * otherwise.
   */
  public static boolean isSecureLDAPUrl(LDAPURL url) {
    return !LDAPURL.DEFAULT_SCHEME.equalsIgnoreCase(url.getScheme());
  }


  /**
   * Make an url from the specified arguments.
   * @param host the host.
   * @param port the port.
   * @param dn the dn.
   * @param secure whether it is a secure URL or not.
   * @return an LDAP URL from the specified arguments.
   */
  public static LDAPURL makeLDAPUrl(String host, int port, String dn,
      boolean secure) {
    return new LDAPURL(
        secure ? "ldaps" : LDAPURL.DEFAULT_SCHEME,
            host,
            port,
            dn,
            null, // no attributes
            SearchScope.BASE_OBJECT,
            null, // No filter
            null); // No extensions
  }


  /**
   * Make an url from the specified arguments.
   * @param ctx the connection to the server.
   * @param dn the base DN of the URL.
   * @return an LDAP URL from the specified arguments.
   */
  public static LDAPURL makeLDAPUrl(InitialLdapContext ctx, String dn) {
    return new LDAPURL(
        ConnectionUtils.isSSL(ctx) ? "ldaps" : LDAPURL.DEFAULT_SCHEME,
               ConnectionUtils.getHostName(ctx),
               ConnectionUtils.getPort(ctx),
               dn,
               null, // No attributes
               SearchScope.BASE_OBJECT,
               null,
               null); // No filter
  }


  /**
   * Make an url from the specified arguments.
   * @param url an LDAP URL to use as base of the new LDAP URL.
   * @param dn the base DN for the new LDAP URL.
   * @return an LDAP URL from the specified arguments.
   */
  public static LDAPURL makeLDAPUrl(LDAPURL url, String dn) {
    return new LDAPURL(
        url.getScheme(),
        url.getHost(),
        url.getPort(),
        dn,
        null, // no attributes
        SearchScope.BASE_OBJECT,
        null, // No filter
        null); // No extensions
  }

  /**
   * Returns a collection of AuthRecord.
   * @return a collection of AuthRecord.
   */
  Collection getRegisteredAuthentication() {
    return authTable.values();
  }
}

/**
 * A struct representing authentication data.
 */
class AuthRecord {
  LDAPURL ldapUrl;
  String dn;
  String password;
}

/**
 * A struct representing an active connection.
 */
class ConnectionRecord {
  InitialLdapContext ctx;
  int counter;
  boolean disconnectAfterUse;
}
