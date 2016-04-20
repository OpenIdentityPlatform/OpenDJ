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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import java.util.ArrayList;
import java.util.HashMap;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.event.ReferralAuthenticationListener;
import org.opends.server.types.HostPort;
import org.opends.server.types.LDAPURL;

import com.forgerock.opendj.cli.CliConstants;

import static org.opends.admin.ads.util.ConnectionUtils.*;

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
 * for each connection: two calls to getConnection() with the same URL
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

  private final HashMap<String, AuthRecord> authTable = new HashMap<>();
  private final HashMap<String, ConnectionRecord> connectionTable = new HashMap<>();

  private ArrayList<ReferralAuthenticationListener> listeners;

  private Control[] requestControls = new Control[] {};
  private ApplicationTrustManager trustManager;
  private int connectTimeout = CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT;

  /**
   * Returns <CODE>true</CODE> if the connection passed is registered in the
   * connection pool, <CODE>false</CODE> otherwise.
   * @param ctx the connection.
   * @return <CODE>true</CODE> if the connection passed is registered in the
   * connection pool, <CODE>false</CODE> otherwise.
   */
  public boolean isConnectionRegistered(InitialLdapContext ctx) {
    for (String key : connectionTable.keySet())
    {
      ConnectionRecord cr = connectionTable.get(key);
      HostPort hostPort = getHostPort(ctx);
      HostPort crHostPort = getHostPort(cr.ctx);
      if (cr.ctx != null
          && hostPort.equals(crHostPort)
          && getBindDN(cr.ctx).equals(getBindDN(ctx))
          && getBindPassword(cr.ctx).equals(getBindPassword(ctx))
          && isSSL(cr.ctx) == isSSL(ctx)
          && isStartTLS(cr.ctx) == isStartTLS(ctx)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Registers a connection in this connection pool.
   * @param ctx the connection to be registered.
   */
  public void registerConnection(InitialLdapContext ctx) {
    registerAuth(ctx);
    LDAPURL url = makeLDAPUrl(ctx);
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
    LDAPURL url = makeLDAPUrl(ctx);
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
      listeners = new ArrayList<>();
    }
    listeners.add(listener);
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
          boolean registerAuth = false;
          AuthRecord authRecord = authTable.get(key);
          if (authRecord == null)
          {
            // Best-effort: try with an already registered authentication
            authRecord = authTable.values().iterator().next();
            registerAuth = true;
          }
          cr.ctx = createLDAPConnection(ldapUrl, authRecord);
          cr.ctx.setRequestControls(requestControls);
          if (registerAuth)
          {
            authTable.put(key, authRecord);
          }
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

    synchronized (targetRecord)
    {
      targetRecord.counter--;
      if (targetRecord.counter == 0 && targetRecord.disconnectAfterUse)
      {
        disconnectAndRemove(targetRecord);
      }
    }
  }

  /**
   * Register authentication data.
   * If authentication data are already available for the protocol/host/port
   * specified in the LDAPURl, they are replaced by the new data.
   * If true is passed as 'connect' parameter, registerAuth() creates the
   * connection and attempts to connect() and bind() . If connect() or bind()
   * fail, registerAuth() forwards the NamingException and does not register
   * the authentication data.
   * @param ldapUrl the LDAP URL of the server.
   * @param dn the bind DN.
   * @param pw the password.
   * @param connect whether to connect or not to the server with the
   * provided authentication (for testing purposes).
   * @throws NamingException if an error occurs connecting.
   */
  private void registerAuth(LDAPURL ldapUrl, String dn, String pw,
      boolean connect) throws NamingException {

    String key = makeKeyFromLDAPUrl(ldapUrl);
    final AuthRecord ar = new AuthRecord();
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
  private void registerAuth(InitialLdapContext ctx) {
    LDAPURL url = makeLDAPUrl(ctx);
    try {
      registerAuth(url, getBindDN(ctx), getBindPassword(ctx), false);
    }
    catch (NamingException x) {
      throw new RuntimeException("Bug");
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
  private void unRegisterAuth(LDAPURL ldapUrl) throws NamingException {
    String key = makeKeyFromLDAPUrl(ldapUrl);

    authTable.remove(key);
    notifyListeners();
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

  /** Notifies the listeners that a referral authentication change happened. */
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
    return protocol + ":" + getHostPort(rec.ctx);
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
    // Take the base DN out of the URL and only keep the protocol, host and port
    ldapUrl = new LDAPURL(ldapUrl.getScheme(), ldapUrl.getHost(),
          ldapUrl.getPort(), (DN)null, null, null, null, null);

    if (isSecureLDAPUrl(ldapUrl))
    {
      return ConnectionUtils.createLdapsContext(ldapUrl.toString(), ar.dn,
          ar.password, getConnectTimeout(), null,
          getTrustManager(), getKeyManager());
    }
    return ConnectionUtils.createLdapContext(ldapUrl.toString(), ar.dn,
        ar.password, getConnectTimeout(), null);
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

  /**
   * Returns the timeout to establish the connection in milliseconds.
   * @return the timeout to establish the connection in milliseconds.
   */
  public int getConnectTimeout()
  {
    return connectTimeout;
  }

  /**
   * Sets the timeout to establish the connection in milliseconds.
   * Use {@code 0} to express no timeout.
   * @param connectTimeout the timeout to establish the connection in
   * milliseconds.
   * Use {@code 0} to express no timeout.
   */
  public void setConnectTimeout(int connectTimeout)
  {
    this.connectTimeout = connectTimeout;
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
  private static boolean isSecureLDAPUrl(LDAPURL url) {
    return !LDAPURL.DEFAULT_SCHEME.equalsIgnoreCase(url.getScheme());
  }

  private LDAPURL makeLDAPUrl(InitialLdapContext ctx) {
    return makeLDAPUrl(ConnectionUtils.getHostPort(ctx), "", isSSL(ctx));
  }

  /**
   * Make an url from the specified arguments.
   * @param hostPort the host name and port of the server.
   * @param dn the base DN of the URL.
   * @param isSSL whether the connection uses SSL
   * @return an LDAP URL from the specified arguments.
   */
  public static LDAPURL makeLDAPUrl(HostPort hostPort, String dn, boolean isSSL)
  {
    return new LDAPURL(
        isSSL ? "ldaps" : LDAPURL.DEFAULT_SCHEME,
               hostPort.getHost(),
               hostPort.getPort(),
               dn,
               null, // No attributes
               SearchScope.BASE_OBJECT,
               null, // No filter
               null); // No extensions
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

}

/** A structure representing authentication data. */
class AuthRecord {
  String dn;
  String password;
}

/** A structure representing an active connection. */
class ConnectionRecord {
  InitialLdapContext ctx;
  int counter;
  boolean disconnectAfterUse;
}
