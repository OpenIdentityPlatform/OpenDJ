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
import java.net.Socket;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.security.GeneralSecurityException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.TrustManager;

/**
 * An implementation of SSLSocketFactory.
 */
public class TrustedSocketFactory extends SSLSocketFactory
{
  static private final Logger LOG =
    Logger.getLogger(TrustedSocketFactory.class.getName());
  private static Map<Thread, TrustManager> hmTrustManager =
    new HashMap<Thread, TrustManager>();
  private static Map<TrustManager, SocketFactory> hmDefaultFactory =
    new HashMap<TrustManager, SocketFactory>();

  private SSLSocketFactory innerFactory;
  private TrustManager trustManager;

  /**
   * Constructor of the TrustedSocketFactory.
   * @param trustManager the trust manager to use.
   */
  public TrustedSocketFactory(TrustManager trustManager)
  {
    this.trustManager = trustManager;
  }

  /**
   * Sets the provided trust manager for the operations in the current thread.
   * @param trustManager the trust manager to use.
   */
  public static synchronized void setCurrentThreadTrustManager(
      TrustManager trustManager)
  {
    setThreadTrustManager(trustManager, Thread.currentThread());
  }

  /**
   * Sets the provided trust manager for the operations in the provided thread.
   * @param trustManager the trust manager to use.
   * @param thread the thread where we want to use the provided trust manager.
   */
  public static synchronized void setThreadTrustManager(
      TrustManager trustManager, Thread thread)
  {
    TrustManager currentTrustManager = hmTrustManager.get(thread);
    if (currentTrustManager != null) {
      hmDefaultFactory.remove(currentTrustManager);
      hmTrustManager.remove(thread);
    }
    if (trustManager != null) {
      hmTrustManager.put(thread, trustManager);
    }
  }

  //
  // SocketFactory implementation
  //
  /**
   * Returns the default SSL socket factory. The default
   * implementation can be changed by setting the value of the
   * "ssl.SocketFactory.provider" security property (in the Java
   * security properties file) to the desired class. If SSL has not
   * been configured properly for this virtual machine, the factory
   * will be inoperative (reporting instantiation exceptions).
   *
   * @return the default SocketFactory
   */
  public static synchronized SocketFactory getDefault()
  {
    Thread currentThread = Thread.currentThread();
    TrustManager trustManager = hmTrustManager.get(currentThread);
    SocketFactory result;

    if (trustManager == null)
    {
      LOG.log(Level.SEVERE, "Can't find a trust manager associated to thread " +
          currentThread);
      result = new TrustedSocketFactory(null);
    }
    else
    {
      result = hmDefaultFactory.get(trustManager);
      if (result == null)
      {
        result = new TrustedSocketFactory(trustManager);
        hmDefaultFactory.put(trustManager, result);
      }
    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public Socket createSocket(InetAddress address, int port) throws IOException {
    return getInnerFactory().createSocket(address, port);
  }

  /**
   * {@inheritDoc}
   */
  public Socket createSocket(InetAddress address, int port,
      InetAddress clientAddress, int clientPort) throws IOException
  {
    return getInnerFactory().createSocket(address, port, clientAddress,
        clientPort);
  }

  /**
   * {@inheritDoc}
   */
  public Socket createSocket(String host, int port) throws IOException
  {
    return getInnerFactory().createSocket(host, port);
  }

  /**
   * {@inheritDoc}
   */
  public Socket createSocket(String host, int port, InetAddress clientHost,
      int clientPort) throws IOException
  {
    return getInnerFactory().createSocket(host, port, clientHost, clientPort);
  }

  /**
   * {@inheritDoc}
   */
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
  throws IOException
  {
    return getInnerFactory().createSocket(s, host, port, autoClose);
  }

  /**
   * {@inheritDoc}
   */
  public String[] getDefaultCipherSuites()
  {
    try
    {
      return getInnerFactory().getDefaultCipherSuites();
    }
    catch(IOException x)
    {
      return new String[0];
    }
  }

  /**
   * {@inheritDoc}
   */
  public String[] getSupportedCipherSuites()
  {
    try
    {
      return getInnerFactory().getSupportedCipherSuites();
    }
    catch(IOException x)
    {
      return new String[0];
    }
  }


  //
  // Private
  //

  private SSLSocketFactory getInnerFactory() throws IOException {
    if (innerFactory == null)
    {
      String algorithm = "TLSv1";
      SSLKeyException xx;

      try {
        SSLContext sslCtx = SSLContext.getInstance(algorithm);
        if (trustManager == null)
        {
          LOG.log(Level.SEVERE, "Warning : no trust for this factory");
          sslCtx.init(null, null, null); // No certif -> no SSL connection
        }
        else {
          sslCtx.init(null, new TrustManager[] { trustManager }, null
          );
        }
        innerFactory = sslCtx.getSocketFactory();
      }
      catch(GeneralSecurityException x) {
        xx = new SSLKeyException("Failed to create SSLContext for " +
            algorithm);
        xx.initCause(x);
        throw xx;
      }
    }

    return innerFactory;
  }
}

