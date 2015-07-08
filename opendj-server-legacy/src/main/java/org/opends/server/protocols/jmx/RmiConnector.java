/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import org.opends.server.api.KeyManagerProvider;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.NullKeyManagerProvider;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.util.SelectableCertificateKeyManager;

/**
 * The RMI connector class starts and stops the JMX RMI connector server.
 * There are 2 different connector servers
 * <ul>
 * <li> the RMI Client connector server, supporting TLS-encrypted.
 * communication, server authentication by certificate and client
 * authentication by providing appropriate LDAP credentials through
 * SASL/PLAIN.
 * <li> the RMI client connector server, supporting TLS-encrypted
 * communication, server authentication by certificate, client
 * authentication by certificate and identity assertion through SASL/PLAIN.
 * </ul>
 * <p>
 * Each connector is registered into the JMX MBean server.
 */
public class RmiConnector
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * The MBean server used to handle JMX interaction.
   */
  private MBeanServer mbs;

  /**
   * The associated JMX Connection Handler.
   */
  private JmxConnectionHandler jmxConnectionHandler;

  /**
   * The name of the JMX connector with no SSL client
   * authentication.
   */
  private String jmxRmiConnectorNoClientCertificateName;

  /**
   * The reference to the JMX connector client with no SSL client
   * authentication.
   */
  protected JMXConnectorServer jmxRmiConnectorNoClientCertificate;

  /**
   * The reference to the JMX connector client with SSL client
   * authentication.
   */
  private JMXConnectorServer jmxRmiConnectorClientCertificate;

  /**
   * The reference to authenticator.
   */
  private RmiAuthenticator rmiAuthenticator;

  /**
   * The reference to the created RMI registry.
   */
  private Registry registry;

  /**
   * The Underlying Socket factory.
   */
  private OpendsRmiServerSocketFactory rmiSsf;

  /**
   * The RMI protocol version used by this connector.
   */
  private String rmiVersion;

  // ===================================================================
  // CONSTRUCTOR
  // ===================================================================
  /**
   * Create a new instance of RmiConnector .
   *
   * @param mbs
   *            The MBean server.
   * @param jmxConnectionHandler
   *            The associated JMX Connection Handler
   */
  public RmiConnector(MBeanServer mbs,
      JmxConnectionHandler jmxConnectionHandler)
  {
    this.mbs = mbs;
    this.jmxConnectionHandler = jmxConnectionHandler;

    String baseName = JMXMBean.getJmxName(jmxConnectionHandler
        .getComponentEntryDN());

    jmxRmiConnectorNoClientCertificateName = baseName + ","
        + "Type=jmxRmiConnectorNoClientCertificateName";
  }

  // ===================================================================
  // Initialization
  // ===================================================================
  /**
   * Activates the RMI Connectors. It starts the secure connectors.
   */
  public void initialize()
  {
    try
    {
      startCommonRegistry();

      // start the RMI connector (SSL + server authentication)
      startConnectorNoClientCertificate();

      // start the RMI connector (SSL + server authentication +
      // client authentication + identity given part SASL/PLAIN)
      // TODO startConnectorClientCertificate(clientConnection);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      throw new RuntimeException("Error while starting the RMI module : "
          + e.getMessage());
    }

    if (logger.isTraceEnabled())
    {
      logger.trace("RMI module started");
    }
  }

  /**
   * Starts the common RMI registry. In order to provide RMI stub for
   * remote client, the JMX RMI connector should be register into an RMI
   * registry. Each server will maintain its own private one.
   *
   * @throws Exception
   *             if the registry cannot be started
   */
  private void startCommonRegistry() throws Exception
  {
    final InetAddress listenAddress = jmxConnectionHandler.getListenAddress();
    int registryPort = jmxConnectionHandler.getListenPort();

    // create our local RMI registry if it does not exist already
    if (logger.isTraceEnabled())
    {
      logger.trace("start or reach an RMI registry on port %d",
                          registryPort);
    }
    try
    {
      // TODO Not yet implemented: If the host has several interfaces
      if (registry == null)
      {
        rmiSsf = new OpendsRmiServerSocketFactory(listenAddress);
        registry = LocateRegistry.createRegistry(registryPort, null, rmiSsf);
      }
    }
    catch (RemoteException re)
    {
      // is the registry already created ?
      if (logger.isTraceEnabled())
      {
        logger.trace("cannot create the RMI registry -> already done ?");
      }
      try
      {
        // get a 'remote' reference on the registry
        Registry reg = LocateRegistry.getRegistry(registryPort);

        // 'ping' the registry
        reg.list();
        registry = reg;
      }
      catch (Exception e)
      {
        if (logger.isTraceEnabled())
        {
          // no 'valid' registry found on the specified port
          logger.trace("exception thrown while pinging the RMI registry");

          // throw the original exception
          logger.traceException(re);
        }
        throw re;
      }

      // here the registry is ok even though
      // it was not created by this call
      if (logger.isTraceEnabled())
      {
        logger.trace("RMI was registry already started");
      }
    }
  }

  /**
   * Starts a secure RMI connector, with a client that doesn't have to
   * present a certificate, on the local MBean server.
   * This method assumes that the common registry was successfully
   * started.
   * <p>
   * If the connector is already started, this method simply returns
   * without doing anything.
   *
   * @throws Exception
   *             if an error occurs
   */
  private void startConnectorNoClientCertificate() throws Exception
  {
    try
    {
      // Environment map
      HashMap<String, Object> env = new HashMap<>();

      // ---------------------
      // init an ssl context
      // ---------------------
      SslRMIClientSocketFactory rmiClientSockeyFactory = null;
      DirectoryRMIServerSocketFactory rmiServerSockeyFactory = null;
      if (jmxConnectionHandler.isUseSSL())
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("SSL connection");
        }

        // ---------------------
        // SERVER SIDE
        // ---------------------
        // Get a Server socket factory
        KeyManager[] keyManagers;
        KeyManagerProvider provider = DirectoryServer
            .getKeyManagerProvider(jmxConnectionHandler
                .getKeyManagerProviderDN());
        if (provider == null) {
          keyManagers = new NullKeyManagerProvider().getKeyManagers();
        }
        else
        {
          String nickname = jmxConnectionHandler.getSSLServerCertNickname();
          if (nickname == null)
          {
            keyManagers = provider.getKeyManagers();
          }
          else
          {
            keyManagers =
                 SelectableCertificateKeyManager.wrap(provider.getKeyManagers(),
                                                      nickname);
          }
        }

        SSLContext ctx = SSLContext.getInstance("TLSv1");
        ctx.init(
            keyManagers,
            null,
            null);
        SSLSocketFactory ssf = ctx.getSocketFactory();

        // set the Server socket factory in the JMX map
        rmiServerSockeyFactory = new DirectoryRMIServerSocketFactory(ssf, false);
        env.put(
            "jmx.remote.rmi.server.socket.factory",
            rmiServerSockeyFactory);

        // ---------------------
        // CLIENT SIDE : Rmi stores the client stub in the
        // registry
        // ---------------------
        // Set the Client socket factory in the JMX map
        rmiClientSockeyFactory = new SslRMIClientSocketFactory();
        env.put(
            "jmx.remote.rmi.client.socket.factory",
            rmiClientSockeyFactory);
      }
      else
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("UNSECURE CONNECTION");
        }
      }

      // specify the rmi JMX authenticator to be used
      if (logger.isTraceEnabled())
      {
        logger.trace("Add RmiAuthenticator into JMX map");
      }
      rmiAuthenticator = new RmiAuthenticator(jmxConnectionHandler);

      env.put(JMXConnectorServer.AUTHENTICATOR, rmiAuthenticator);

      // Create the JMX Service URL
      String uri = "org.opends.server.protocols.jmx.client-unknown";
      String serviceUrl = "service:jmx:rmi:///jndi/rmi://"
          + jmxConnectionHandler.getListenAddress().getHostName() + ":" + jmxConnectionHandler.getListenPort()
          + "/" + uri;
      JMXServiceURL url = new JMXServiceURL(serviceUrl);

      // Create and start the connector
      if (logger.isTraceEnabled())
      {
        logger.trace("Create and start the JMX RMI connector");
      }
      OpendsRMIJRMPServerImpl opendsRmiConnectorServer =
          new OpendsRMIJRMPServerImpl(jmxConnectionHandler.getRmiPort(),
              rmiClientSockeyFactory, rmiServerSockeyFactory, env);
      jmxRmiConnectorNoClientCertificate = new RMIConnectorServer(url, env,
          opendsRmiConnectorServer, mbs);
      jmxRmiConnectorNoClientCertificate.start();

      // Register the connector into the RMI registry
      // TODO Should we do that?
      ObjectName name = new ObjectName(jmxRmiConnectorNoClientCertificateName);
      mbs.registerMBean(jmxRmiConnectorNoClientCertificate, name);
      rmiVersion = opendsRmiConnectorServer.getVersion();

      if (logger.isTraceEnabled())
      {
        logger.trace("JMX RMI connector Started");
      }

    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw e;
    }

  }

  /**
   * Closes this connection handler so that it will no longer accept new
   * client connections. It may or may not disconnect existing client
   * connections based on the provided flag.
   *
   * @param stopRegistry Indicates if the RMI registry should be stopped
   */
  public void finalizeConnectionHandler(boolean stopRegistry)
  {
    try
    {
      if (jmxRmiConnectorNoClientCertificate != null)
      {
        jmxRmiConnectorNoClientCertificate.stop();
      }
      if (jmxRmiConnectorClientCertificate != null)
      {
        jmxRmiConnectorClientCertificate.stop();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    jmxRmiConnectorNoClientCertificate = null;
    jmxRmiConnectorClientCertificate = null;

    // Unregister connectors and stop them.
    try
    {
      ObjectName name = new ObjectName(jmxRmiConnectorNoClientCertificateName);
      if (mbs.isRegistered(name))
      {
        mbs.unregisterMBean(name);
      }
      if (jmxRmiConnectorNoClientCertificate != null)
      {
        jmxRmiConnectorNoClientCertificate.stop();
      }

      // TODO: unregister the connector with SSL client authen
//      name = new ObjectName(jmxRmiConnectorClientCertificateName);
//      if (mbs.isRegistered(name))
//      {
//        mbs.unregisterMBean(name);
//      }
//      jmxRmiConnectorClientCertificate.stop() ;
    }
    catch (Exception e)
    {
      // TODO Log an error message
      logger.traceException(e);
    }

    if (stopRegistry)
    {
      // Close the socket
      try
      {
        if (rmiSsf != null)
        {
          rmiSsf.close();
        }
      }
      catch (IOException e)
      {
        // TODO Log an error message
        logger.traceException(e);
      }
      registry = null;
    }
  }



  /**
   * Retrieves the RMI protocol version string in use for this connector.
   *
   * @return  The RMI protocol version string in use for this connector.
   */
  public String getProtocolVersion()
  {
    return rmiVersion;
  }
}
