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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;

import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;
import static org.opends.server.loggers.Debug.debugException;
import static org.opends.server.loggers.Debug.debugMessage;

import java.io.IOException;

import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;

import java.util.Map;

// JSSE
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;

/**
 * A <code>DirectoryRMIClientSocketFactory</code> instance is used by the
 * RMI runtime in order to obtain client sockets for RMI calls via SSL.
 * <p>
 * This class implements <code>RMIClientSocketFactory</code> over the
 * Secure Sockets Layer (SSL) or Transport Layer Security (TLS) protocols.
 * </p>
 */
public class DirectoryRMIClientSocketFactory implements
    RMIClientSocketFactory, Serializable
{

  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
    "org.opends.server.protocols.jmx.DirectoryRMIClientSocketFactory";

  /**
   * The serial version identifier required to satisfy the compiler because
   * this class implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE> command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = -6701450600497520362L;

  /**
   * the static thread-local connection environment used by the RMI
   * client to configure this factory.
   */
  private static InheritableThreadLocal<Map> tlMapConnectionEnv =
    new InheritableThreadLocal<Map>();

  /**
   * The static thread-local target server hostname used by the RMI
   * client to configure this factory.
   */
  private static InheritableThreadLocal<String> tlStrServerHostname =
    new InheritableThreadLocal<String>();

  /**
   * the connection mode. <code>true</code> indicates that the client
   * will be authenticated by its certificate (SSL protocol).
   * <code>false</code> indicates that we have to perform an lDAP
   * authentication
   */
  private final boolean needClientCertificate;

  /**
   * the ssl socket factory (do not serialize).
   */
  private transient SSLSocketFactory sslSocketFactory = null;

  /**
   * the host to connect to (do not serialize).
   */
  private transient String serverHostname = null;

  /**
   * Constructs a new <code>DirectoryRMIClientSocketFactory</code>.
   *
   * @param wellknown
   *            <code>true</code> for wellknown, <code>false</code>
   *            otherwise
   */
  public DirectoryRMIClientSocketFactory(boolean wellknown)
  {
    assert debugConstructor(CLASS_NAME);
    this.needClientCertificate = wellknown;

    // We don't force the initialization of the SSLSocketFactory
    // at construction time - because the RMI client socket factory is
    // created on the server side, where that initialization is a
    // priori
    // meaningless, unless both server and client run in the same JVM.
    // So contrarily to what we do for the server side, the
    // initialization
    // of the SSLSocketFactory will be delayed until the first time
    // createSocket() is called.
  }

  /**
   * Sets the thread-local connection environment.
   *
   * @param connectionEnv the new connection env
   */
  public static void setConnectionEnv(Map connectionEnv)
  {
    tlMapConnectionEnv.set(connectionEnv);
  }

  /**
   * Returns the thread-local connection environment.
   *
   * @return Map the connection environment used by new connections
   */
  public static Map getConnectionEnv()
  {
    return tlMapConnectionEnv.get();
  }

  /**
   * Sets the thread-local target server hostname.
   *
   * @param serverHostname
   *            the target server hostname
   */
  public static void setServerHostname(String serverHostname)
  {
    tlStrServerHostname.set(serverHostname);
  }

  /**
   * Returns the thread-local target server hostname.
   *
   * @return String the target server hostname
   */
  public static String getServerHostname()
  {
    return tlStrServerHostname.get();
  }

  /**
   * Returns the connection mode as configured at construction time.
   *
   * @return boolean <code>true</code> for wellknown,
   *         <code>false</code> otherwise
   */
  public boolean getNeedClientCertificate()
  {
    return needClientCertificate;
  }

  /**
   * Creates an SSL socket configured with the right trust stores and the
   * right target host.
   * <p>
   * The keystore and truststore used come from client configuration and
   * depend on the connection mode specified at construction time.
   * <p>
   * The target host is the host specified except if a different host was
   * specified in the connection environment.
   *
   * @param host
   *            the target host as known by the RMI stack
   * @param port
   *            the target port number
   * @return an SSL socket
   *
   * @throws IOException
   *             if an error occurs while configuring the socket
   */
  public Socket createSocket(String host, int port) throws IOException
  {

    //
    // gets ssl socket factory
    SSLSocketFactory sslSocketFactory = getSSLSocketFactory();
    String realhost = getRealServerHostname(host);

    final SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
        realhost,
        port);

    return sslSocket;
  }

  /**
   * Indicates whether some other object is "equal to" this one.
   * <p>
   * Because each RMI client might have its own configuration, we do not
   * try to optimize anything. Each RMI connector uses its own RMI client
   * socket factory. In other words, Directory RMI clients never share
   * the same client socket factory.
   * </p>
   *
   * @param obj
   *            the reference object with which to compare
   * @return <code>true</code> if this object is the same as the obj
   *         argument <code>false</code> otherwise
   */
  public boolean equals(Object obj)
  {
    return super.equals(obj);
  }

  /**
   * Returns a hash code value for this
   * <code>DirectoryRMIClientSocketFactory</code>.
   *
   * @return a hash code value for this
   *         <code>DirectoryRMIClientSocketFactory</code>
   */
  public int hashCode()
  {
    return super.hashCode();
  }

  /**
   * Returns the real server hostname to connect to.
   *
   * @param rmiHostname
   *            the target hostname as known by RMI stack
   * @return String the real hostname to connect to
   * @throws IOException
   *             if an error occurs
   */
  private synchronized String getRealServerHostname(String rmiHostname)
      throws IOException
  {

    if (serverHostname == null)
    {
      //
      // does the client specify another host by
      // using thread-local static parameter ?
      serverHostname = getServerHostname();

      //
      // if not found here, don't look for real host in static
      // anymore
      if (serverHostname == null)
      {
        serverHostname = "";
      }
    }

    if (serverHostname.length() > 0)
    {
      return serverHostname;
    }
    else
    {
      return rmiHostname;
    }
  }

  /**
   * Returns the ssl socket factory used by this RMI client socket
   * factory.
   *
   * @return SSLSocketFactory the ssl socket factory
   * @throws IOException
   *             if an error occurs
   */
  private synchronized SSLSocketFactory getSSLSocketFactory()
      throws IOException
  {
    assert debugEnter(CLASS_NAME, "getSSLSocketFactory");
    if (sslSocketFactory == null)
    {
      assert debugMessage(
          DebugLogCategory.CONNECTION_HANDLING,
          DebugLogSeverity.VERBOSE,
          CLASS_NAME,
          "getSSLSocketFactory",
          "sslSocketFactory is null, get a new one");

      // socket factory not yet initialized
      // initialize the trust
      Map connectionEnv = getConnectionEnv();
      TrustManager[] tms = null;

      //
      // Check if a trust manager array was provided in the
      // connection
      // Env. If yes, use it for this SSL Connection
      if ((connectionEnv != null)
          && (connectionEnv
              .containsKey(JmxConnectionHandler.TRUST_MANAGER_ARRAY_KEY)))
      {
        try
        {
          tms = (TrustManager[]) connectionEnv
              .get(JmxConnectionHandler.TRUST_MANAGER_ARRAY_KEY);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "getSSLSocketFactory", e);
          tms = null;
        }

        if (tms == null)
        {
          //
          // Why? The value is not invalid ?
          // Too bad: we raised an exception
          throw new IOException("invalid type or null value for key ["
              + JmxConnectionHandler.TRUST_MANAGER_ARRAY_KEY
              + "] in connection environment : "
              + connectionEnv
                  .get(JmxConnectionHandler.TRUST_MANAGER_ARRAY_KEY));
        }
      }

      // now we have the array of trust Manager
      // we can initialize the ssl ctx
      SSLContext ctx = null;
      try
      {
        ctx = SSLContext.getInstance("TLSv1");
        ctx.init(null, tms, null);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getSSLSocketFactory", e);
        throw new IOException("Unable to initialize SSL context : "
            + e.getMessage());
      }

      // create the SSLSocket
      sslSocketFactory = ctx.getSocketFactory();
    }
    return sslSocketFactory;
  }
}
