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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.HostPort;

import com.forgerock.opendj.cli.Utils;

/**
 * Class providing some utilities to create LDAP connections using JNDI and
 * to manage entries retrieved using JNDI.
 *
 */
public class ConnectionUtils
{
  private static final String STARTTLS_PROPERTY =
    "org.opends.connectionutils.isstarttls";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Private constructor: this class cannot be instantiated.
   */
  private ConnectionUtils()
  {
  }

  /**
   * Creates a clear LDAP connection and returns the corresponding LdapContext.
   * This methods uses the specified parameters to create a JNDI environment
   * hashtable and creates an InitialLdapContext instance.
   *
   * @param ldapURL
   *          the target LDAP URL
   * @param dn
   *          passed as Context.SECURITY_PRINCIPAL if not null
   * @param pwd
   *          passed as Context.SECURITY_CREDENTIALS if not null
   * @param timeout
   *          passed as com.sun.jndi.ldap.connect.timeout if > 0
   * @param env
   *          null or additional environment properties
   *
   * @throws NamingException
   *           the exception thrown when instantiating InitialLdapContext
   *
   * @return the created InitialLdapContext.
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   */
  static InitialLdapContext createLdapContext(String ldapURL, String dn,
      String pwd, int timeout, Hashtable<String, String> env)
      throws NamingException
  {
    env = newEnvironmentFrom(ldapURL, env);
    if (timeout >= 1)
    {
      env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));
    }
    if (dn != null && pwd != null)
    {
      env.put(Context.SECURITY_PRINCIPAL, dn);
      env.put(Context.SECURITY_CREDENTIALS, pwd);
    }

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = { null, null };
    final Hashtable<String, String> fEnv = env;
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          pair[0] = new InitialLdapContext(fEnv, null);
        } catch (NamingException ne)
        {
          pair[1] = ne;
        } catch (Throwable t)
        {
          t.printStackTrace();
          pair[1] = t;
        }
      }
    });
    t.setDaemon(true);
    return getInitialLdapContext(t, pair, timeout);
  }

  /**
   * Creates an LDAPS connection and returns the corresponding LdapContext.
   * This method uses the TrusteSocketFactory class so that the specified
   * trust manager gets called during the SSL handshake. If trust manager is
   * null, certificates are not verified during SSL handshake.
   *
   * @param ldapsURL      the target *LDAPS* URL.
   * @param dn            passed as Context.SECURITY_PRINCIPAL if not null.
   * @param pwd           passed as Context.SECURITY_CREDENTIALS if not null.
   * @param timeout       passed as com.sun.jndi.ldap.connect.timeout if > 0.
   * @param env           null or additional environment properties.
   * @param trustManager  null or the trust manager to be invoked during SSL
   * negotiation.
   * @param keyManager    null or the key manager to be invoked during SSL
   * negotiation.
   * @return the established connection with the given parameters.
   *
   * @throws NamingException the exception thrown when instantiating
   * InitialLdapContext.
   *
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see TrustedSocketFactory
   */
  static InitialLdapContext createLdapsContext(String ldapsURL,
      String dn, String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager, final KeyManager keyManager) throws NamingException {
    final Hashtable<String, String> newEnv = newEnvironmentFrom(ldapsURL, env);
    newEnv.put("java.naming.ldap.factory.socket", TrustedSocketFactory.class.getName());

    if (dn != null && pwd != null)
    {
      newEnv.put(Context.SECURITY_PRINCIPAL, dn);
      newEnv.put(Context.SECURITY_CREDENTIALS, pwd);
    }

    if (trustManager == null)
    {
      trustManager = new BlindTrustManager();
    }

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = { null, null };
    final TrustManager fTrustManager = trustManager;
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          TrustedSocketFactory.setCurrentThreadTrustManager(fTrustManager, keyManager);
          pair[0] = new InitialLdapContext(newEnv, null);
        } catch (NamingException | RuntimeException ne) {
          pair[1] = ne;
        }
      }
    });
    t.setDaemon(true);
    return getInitialLdapContext(t, pair, timeout);
  }

  /**
   * Creates an LDAP+StartTLS connection and returns the corresponding
   * LdapContext.
   * This method first creates an LdapContext with anonymous bind. Then it
   * requests a StartTlsRequest extended operation. The StartTlsResponse is
   * setup with the specified hostname verifier. Negotiation is done using a
   * TrustSocketFactory so that the specified TrustManager gets called during
   * the SSL handshake.
   * If trust manager is null, certificates are not checked during SSL
   * handshake.
   *
   * @param ldapURL       the target *LDAP* URL.
   * @param dn            passed as Context.SECURITY_PRINCIPAL if not null.
   * @param pwd           passed as Context.SECURITY_CREDENTIALS if not null.
   * @param timeout       passed as com.sun.jndi.ldap.connect.timeout if > 0.
   * @param env           null or additional environment properties.
   * @param trustManager  null or the trust manager to be invoked during SSL
   * negotiation.
   * @param keyManager    null or the key manager to be invoked during SSL
   * negotiation.
   * @param verifier      null or the hostname verifier to be setup in the
   * StartTlsResponse.
   * @return the established connection with the given parameters.
   *
   * @throws NamingException the exception thrown when instantiating
   * InitialLdapContext.
   *
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see javax.naming.ldap.StartTlsRequest
   * @see javax.naming.ldap.StartTlsResponse
   * @see TrustedSocketFactory
   */
  static InitialLdapContext createStartTLSContext(String ldapURL,
      final String dn, final String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager, final KeyManager keyManager,
      HostnameVerifier verifier)
  throws NamingException
  {
    if (trustManager == null)
    {
      trustManager = new BlindTrustManager();
    }
    if (verifier == null) {
      verifier = new BlindHostnameVerifier();
    }

    final Hashtable<String, String> newEnv = newEnvironmentFrom(ldapURL, env);
    newEnv.put(Context.SECURITY_AUTHENTICATION, "none");

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = { null, null };
    final TrustManager fTrustManager = trustManager;
    final HostnameVerifier fVerifier = verifier;

    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          InitialLdapContext result = new InitialLdapContext(newEnv, null);

          StartTlsResponse tls = (StartTlsResponse) result.extendedOperation(new StartTlsRequest());
          tls.setHostnameVerifier(fVerifier);
          try
          {
            tls.negotiate(new TrustedSocketFactory(fTrustManager, keyManager));
          }
          catch(IOException x) {
            NamingException xx = new CommunicationException(
                "Failed to negotiate Start TLS operation");
            xx.initCause(x);
            result.close();
            throw xx;
          }

          result.addToEnvironment(STARTTLS_PROPERTY, "true");
          if (dn != null)
          {
            result.addToEnvironment(Context.SECURITY_AUTHENTICATION , "simple");
            result.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
            if (pwd != null)
            {
              result.addToEnvironment(Context.SECURITY_CREDENTIALS, pwd);
            }
            result.reconnect(null);
          }
          pair[0] = result;
        } catch (NamingException | RuntimeException ne)
        {
          pair[1] = ne;
        }
      }
    });
    t.setDaemon(true);
    return getInitialLdapContext(t, pair, timeout);
  }

  private static Hashtable<String, String> copy(Hashtable<String, String> env) {
    return env != null ? new Hashtable<>(env) : new Hashtable<String, String>();
  }

  private static Hashtable<String, String> newEnvironmentFrom(String ldapURL, Hashtable<String, String> env)
  {
    final Hashtable<String, String> copy = copy(env);
    copy.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    copy.put("java.naming.ldap.attributes.binary", EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);
    copy.put(Context.PROVIDER_URL, ldapURL);
    return copy;
  }

  /**
   * Method used to know if we are connected as administrator in a server with a
   * given InitialLdapContext.
   * @param ctx the context.
   * @return {@code true} if we are connected and read the configuration
   * and {@code false} otherwise.
   */
  static boolean connectedAsAdministrativeUser(InitialLdapContext ctx)
  {
    try
    {
      // Search for the config to check that it is the directory manager.
      SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(
          SearchControls. OBJECT_SCOPE);
      searchControls.setReturningAttributes(
          new String[] { SchemaConstants.NO_ATTRIBUTES });
      NamingEnumeration<SearchResult> sr =
       ctx.search("cn=config", "objectclass=*", searchControls);
      try
      {
        while (sr.hasMore())
        {
          sr.next();
        }
      }
      finally
      {
        try
        {
          sr.close();
        }
        catch(Exception ex)
        {
          logger.warn(LocalizableMessage.raw(
              "Unexpected error closing enumeration on cn=Config entry", ex));
        }
      }
      return true;
    } catch (NamingException ne)
    {
      // Nothing to do.
      return false;
    } catch (Throwable t)
    {
      throw new IllegalStateException("Unexpected throwable.", t);
    }
  }

  /**
   * This is just a commodity method used to try to get an InitialLdapContext.
   * @param t the Thread to be used to create the InitialLdapContext.
   * @param pair an Object[] array that contains the InitialLdapContext and the
   * Throwable if any occurred.
   * @param timeout the timeout in milliseconds.  If we do not get to create the
   * connection before the timeout a CommunicationException will be thrown.
   * @return the created InitialLdapContext
   * @throws NamingException if something goes wrong during the creation.
   */
  private static InitialLdapContext getInitialLdapContext(Thread t,
      Object[] pair, int timeout) throws NamingException
  {
    try
    {
      if (timeout > 0)
      {
        t.start();
        t.join(timeout);
      } else
      {
        t.run();
      }
    } catch (InterruptedException x)
    {
      // This might happen for problems in sockets
      // so it does not necessarily imply a bug
    }

    if (timeout > 0 && t.isAlive())
    {
      t.interrupt();
      try
      {
        t.join(2000);
      } catch (InterruptedException x)
      {
        // This might happen for problems in sockets
        // so it does not necessarily imply a bug
      }
      throw connectionTimedOut();
    }

    Object connection = pair[0];
    Object ex = pair[1];
    if (connection == null && ex == null)
    {
      throw connectionTimedOut();
    }

    if (ex != null)
    {
      if (ex instanceof NamingException)
      {
        throw (NamingException) ex;
      }
      else if (ex instanceof RuntimeException)
      {
        throw (RuntimeException) ex;
      }
      else if (ex instanceof Throwable)
      {
        throw new IllegalStateException("Unexpected throwable occurred", (Throwable) ex);
      }
    }
    return (InitialLdapContext) connection;
  }

  private static NamingException connectionTimedOut()
  {
    NamingException xx = new CommunicationException("Connection timed out");
    xx.initCause(new ConnectException("Connection timed out"));
    return xx;
  }

  /**
   * Returns the LDAP URL for the provided parameters.
   * @param hostPort the host name and LDAP port.
   * @param useLdaps whether to use LDAPS.
   * @return the LDAP URL for the provided parameters.
   */
  public static String getLDAPUrl(HostPort hostPort, boolean useLdaps)
  {
    return getLDAPUrl(hostPort.getHost(), hostPort.getPort(), useLdaps);
  }

  /**
   * Returns the LDAP URL for the provided parameters.
   * @param host the host name.
   * @param port the LDAP port.
   * @param useLdaps whether to use LDAPS.
   * @return the LDAP URL for the provided parameters.
   */
  public static String getLDAPUrl(String host, int port, boolean useLdaps)
  {
    host = Utils.getHostNameForLdapUrl(host);
    return (useLdaps ? "ldaps" : "ldap") + "://" + host + ":" + port;
  }

  /**
   * Returns the first attribute value in this attribute decoded as a UTF-8 string.
   *
   * @param sr
   *          the search result entry
   * @param attrDesc
   *          the attribute description
   * @return The first attribute value in this attribute decoded as a UTF-8 string.
   */
  public static String firstValueAsString(Entry sr, String attrDesc)
  {
    org.forgerock.opendj.ldap.Attribute attr = sr.getAttribute(attrDesc);
    return (attr != null && !attr.isEmpty()) ? attr.firstValueAsString() : null;
  }

  /**
   * Returns the first value decoded as an Integer, or {@code null} if the attribute does not
   * contain any values.
   *
   * @param sr
   *          the search result entry
   * @param attrDesc
   *          the attribute description
   * @return The first value decoded as an Integer.
   */
  public static Integer asInteger(SearchResultEntry sr, String attrDesc)
  {
    org.forgerock.opendj.ldap.Attribute attr = sr.getAttribute(attrDesc);
    return attr != null ? attr.parse().asInteger() : null;
  }

  /**
   * Returns the first value decoded as a Boolean, or {@code null} if the attribute does not contain
   * any values.
   *
   * @param sr
   *          the search result entry
   * @param attrDesc
   *          the attribute description
   * @return The first value decoded as an Boolean.
   */
  public static Boolean asBoolean(SearchResultEntry sr, String attrDesc)
  {
    org.forgerock.opendj.ldap.Attribute attr = sr.getAttribute(attrDesc);
    return attr != null ? attr.parse().asBoolean() : null;
  }

  /**
   * Returns the values decoded as a set of Strings.
   *
   * @param sr
   *          the search result entry
   * @param attrDesc
   *          the attribute description
   * @return The values decoded as a set of Strings. Never {@code null} and never contains
   *         {@code null} values.
   */
  public static Set<String> asSetOfString(SearchResultEntry sr, String attrDesc)
  {
    org.forgerock.opendj.ldap.Attribute attr = sr.getAttribute(attrDesc);
    return attr != null ? attr.parse().asSetOfString() : Collections.<String> emptySet();
  }

  /**
   * Returns the values decoded as a set of DNs.
   *
   * @param sr
   *          the search result entry
   * @param attrDesc
   *          the attribute description
   * @return The values decoded as a set of DNs. Never {@code null} and never contains {@code null}
   *         values.
   */
  public static Set<DN> asSetOfDN(SearchResultEntry sr, String attrDesc)
  {
    org.forgerock.opendj.ldap.Attribute attr = sr.getAttribute(attrDesc);
    return attr != null ? attr.parse().asSetOfDN() : Collections.<DN> emptySet();
  }
}
