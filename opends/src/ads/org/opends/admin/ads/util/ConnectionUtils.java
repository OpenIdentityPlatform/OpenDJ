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

import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

/**
 * Class providing some utilities to create LDAP connections using JNDI and
 * to manage entries retrieved using JNDI.
 *
 */
public class ConnectionUtils
{
  private static final int DEFAULT_LDAP_CONNECT_TIMEOUT = 10000;

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
  public static InitialLdapContext createLdapContext(String ldapURL, String dn,
      String pwd, int timeout, Hashtable<String, String> env)
      throws NamingException
  {
    if (env != null)
    { // We clone 'env' so that we can modify it freely
      env = new Hashtable<String, String>(env);
    } else
    {
      env = new Hashtable<String, String>();
    }
    env.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapURL);
    if (timeout >= 1)
    {
      env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));
    }
    if (dn != null)
    {
      env.put(Context.SECURITY_PRINCIPAL, dn);
    }
    if (pwd != null)
    {
      env.put(Context.SECURITY_CREDENTIALS, pwd);
    }

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = new Object[]
      { null, null };
    final Hashtable fEnv = env;
    Thread t = new Thread(new Runnable()
    {
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
          pair[1] = t;
        }
      }
    });
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
   * negociation.
   * @param keyManager    null or the key manager to be invoked during SSL
   * negociation.
   * @return the established connection with the given parameters.
   *
   * @throws NamingException the exception thrown when instantiating
   * InitialLdapContext.
   *
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see TrustedSocketFactory
   */
  public static InitialLdapContext createLdapsContext(String ldapsURL,
      String dn, String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager, KeyManager keyManager) throws NamingException {
    if (env != null)
    { // We clone 'env' so that we can modify it freely
      env = new Hashtable<String, String>(env);
    } else
    {
      env = new Hashtable<String, String>();
    }
    env.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapsURL);
    env.put("java.naming.ldap.factory.socket",
        org.opends.admin.ads.util.TrustedSocketFactory.class.getName());

    if (dn != null)
    {
      env.put(Context.SECURITY_PRINCIPAL, dn);
    }

    if (pwd != null)
    {
      env.put(Context.SECURITY_CREDENTIALS, pwd);
    }

    if (trustManager == null)
    {
      trustManager = new BlindTrustManager();
    }

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = new Object[] {null, null};
    final Hashtable fEnv = env;
    final TrustManager fTrustManager = trustManager;
    final KeyManager   fKeyManage    = keyManager;

    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          TrustedSocketFactory.setCurrentThreadTrustManager(fTrustManager,
              fKeyManage);
          pair[0] = new InitialLdapContext(fEnv, null);

        } catch (NamingException ne) {
          pair[1] = ne;

        } catch (RuntimeException re) {
          pair[1] = re;
        }
      }
    });
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
   * negociation.
   * @param keyManager    null or the key manager to be invoked during SSL
   * negociation.
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

  public static InitialLdapContext createStartTLSContext(String ldapURL,
      String dn, String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager, KeyManager keyManager,
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

    if (env != null)
    { // We clone 'env' to modify it freely
      env = new Hashtable<String, String>(env);
    }
    else
    {
      env = new Hashtable<String, String>();
    }
    env.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapURL);
    env.put(Context.SECURITY_AUTHENTICATION , "none");

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = new Object[] {null, null};
    final Hashtable fEnv = env;
    final String fDn = dn;
    final String fPwd = pwd;
    final TrustManager fTrustManager = trustManager;
    final KeyManager fKeyManager     = keyManager;
    final HostnameVerifier fVerifier = verifier;

    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          StartTlsResponse tls;

          InitialLdapContext result = new InitialLdapContext(fEnv, null);

          tls = (StartTlsResponse) result.extendedOperation(
              new StartTlsRequest());
          tls.setHostnameVerifier(fVerifier);
          try
          {
            tls.negotiate(new TrustedSocketFactory(fTrustManager,fKeyManager));
          }
          catch(IOException x) {
            NamingException xx;
            xx = new CommunicationException(
                "Failed to negotiate Start TLS operation");
            xx.initCause(x);
            result.close();
            throw xx;
          }

          if (fDn != null)
          {
            result.addToEnvironment(Context.SECURITY_AUTHENTICATION , "simple");
            result.addToEnvironment(Context.SECURITY_PRINCIPAL, fDn);
            if (fPwd != null)
            {
              result.addToEnvironment(Context.SECURITY_CREDENTIALS, fPwd);
            }
            result.reconnect(null);
          }
          pair[0] = result;

        } catch (NamingException ne)
        {
          pair[1] = ne;

        } catch (RuntimeException re)
        {
          pair[1] = re;
        }
      }
    });
    return getInitialLdapContext(t, pair, timeout);
  }


  /**
   * Method used to know if we can connect as administrator in a server with a
   * given password and dn.
   * @param ldapUrl the ldap URL of the server.
   * @param dn the dn to be used.
   * @param pwd the password to be used.
   * @return <CODE>true</CODE> if we can connect and read the configuration and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean canConnectAsAdministrativeUser(String ldapUrl,
      String dn, String pwd)
  {
    boolean canConnectAsAdministrativeUser = false;
    try
    {
      InitialLdapContext ctx =
        createLdapContext(ldapUrl, dn, pwd, getDefaultLDAPTimeout(), null);

      /*
       * Search for the config to check that it is the directory manager.
       */
      SearchControls searchControls = new SearchControls();
      searchControls.setCountLimit(1);
      searchControls.setSearchScope(
      SearchControls. OBJECT_SCOPE);
      searchControls.setReturningAttributes(
      new String[] {"dn"});
      ctx.search("cn=config", "objectclass=*", searchControls);

      canConnectAsAdministrativeUser = true;
    } catch (NamingException ne)
    {
      // Nothing to do.
    } catch (Throwable t)
    {
      throw new IllegalStateException("Unexpected throwable.", t);
    }
    return canConnectAsAdministrativeUser;
  }

  /**
   * This is just a commodity method used to try to get an InitialLdapContext.
   * @param t the Thread to be used to create the InitialLdapContext.
   * @param pair an Object[] array that contains the InitialLdapContext and the
   * Throwable if any occurred.
   * @param timeout the timeout.  If we do not get to create the connection
   * before the timeout a CommunicationException will be thrown.
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

    boolean throwException = false;

    if ((timeout > 0) && t.isAlive())
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
      throwException = true;
    }

    if ((pair[0] == null) && (pair[1] == null))
    {
      throwException = true;
    }

    if (throwException)
    {
      NamingException xx;
      ConnectException x = new ConnectException("Connection timed out");
      xx = new CommunicationException("Connection timed out");
      xx.initCause(x);
      throw xx;
    }

    if (pair[1] != null)
    {
      if (pair[1] instanceof NamingException)
      {
        throw (NamingException) pair[1];

      } else if (pair[1] instanceof RuntimeException)
      {
        throw (RuntimeException) pair[1];

      } else if (pair[1] instanceof Throwable)
      {
        throw new IllegalStateException("Unexpected throwable occurred",
            (Throwable) pair[1]);
      }
    }
    return (InitialLdapContext) pair[0];
  }

  /**
   * Returns the default LDAP timeout in milliseconds when we try to connect to
   * a server.
   * @return the default LDAP timeout in milliseconds when we try to connect to
   * a server.
   */
  public static int getDefaultLDAPTimeout()
  {
    return DEFAULT_LDAP_CONNECT_TIMEOUT;
  }

  /**
   * Returns the String that can be used to represent a given host name in a
   * LDAP URL.
   * This method must be used when we have IPv6 addresses (the address in the
   * LDAP URL must be enclosed with brackets).
   * @param host the host name.
   * @return the String that can be used to represent a given host name in a
   * LDAP URL.
   */
  public static String getHostNameForLdapUrl(String host)
  {
    if ((host != null) && host.indexOf(":") != -1)
    {
      // Assume an IPv6 address has been specified and adds the brackets
      // for the URL.
      host = host.trim();
      if (!host.startsWith("["))
      {
        host = "["+host;
      }
      if (!host.endsWith("]"))
      {
        host = host + "]";
      }
    }
    return host;
  }

  /**
   * Tells whether the provided Throwable was caused because of a problem with
   * a certificate while trying to establish a connection.
   * @param t the Throwable to analyze.
   * @return <CODE>true</CODE> if the provided Throwable was caused because of a
   * problem with a certificate while trying to establish a connection and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isCertificateException(Throwable t)
  {
    boolean returnValue = false;

    while (!returnValue && (t != null))
    {
      returnValue = (t instanceof SSLHandshakeException) ||
      (t instanceof GeneralSecurityException);
      t = t.getCause();
    }

    return returnValue;
  }

  /**
   * Returns the String representation of the first value of an attribute in a
   * LDAP entry.
   * @param entry the entry.
   * @param attrName the attribute name.
   * @return the String representation of the first value of an attribute in a
   * LDAP entry.
   * @throws NamingException if there is an error processing the entry.
   */
  static public String getFirstValue(SearchResult entry, String attrName)
  throws NamingException
  {
    String v = null;
    Attributes attrs = entry.getAttributes();
    if (attrs != null)
    {
      Attribute attr = attrs.get(attrName);
      if ((attr != null) && (attr.size() > 0))
      {
        Object o = attr.get();
        if (o instanceof String)
        {
          v = (String)o;
        }
        else
        {
          v = String.valueOf(o);
        }
      }
    }
    return v;
  }

  /**
   * Returns a Set with the String representation of the values of an attribute
   * in a LDAP entry.  The returned Set will never be null.
   * @param entry the entry.
   * @param attrName the attribute name.
   * @return a Set with the String representation of the values of an attribute
   * in a LDAP entry.
   * @throws NamingException if there is an error processing the entry.
   */
  static public Set<String> getValues(SearchResult entry, String attrName)
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
}
