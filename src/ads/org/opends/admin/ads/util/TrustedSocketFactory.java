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

import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;

import java.security.GeneralSecurityException;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.TrustManager;

/**
 * An implementation of SSLSocketFactory.
 */
public class TrustedSocketFactory extends SSLSocketFactory
{
  private static Map<Thread, TrustManager> hmTrustManager =
    new HashMap<Thread, TrustManager>();
  private static Map<Thread, KeyManager> hmKeyManager =
    new HashMap<Thread, KeyManager>();

  private static Map<TrustManager, SocketFactory> hmDefaultFactoryTm =
    new HashMap<TrustManager, SocketFactory>();
  private static Map<KeyManager, SocketFactory> hmDefaultFactoryKm =
    new HashMap<KeyManager, SocketFactory>();

  private SSLSocketFactory innerFactory;
  private TrustManager trustManager;
  private KeyManager   keyManager;

  /**
   * Constructor of the TrustedSocketFactory.
   * @param trustManager the trust manager to use.
   * @param keyManager   the key manager to use.
   */
  public TrustedSocketFactory(TrustManager trustManager, KeyManager keyManager)
  {
    this.trustManager = trustManager;
    this.keyManager   = keyManager;
  }

  /**
   * Sets the provided trust and key manager for the operations in the
   * current thread.
   *
   * @param trustManager
   *          the trust manager to use.
   * @param keyManager
   *          the key manager to use.
   */
  public static synchronized void setCurrentThreadTrustManager(
      TrustManager trustManager, KeyManager keyManager)
  {
    setThreadTrustManager(trustManager, Thread.currentThread());
    setThreadKeyManager  (keyManager, Thread.currentThread());
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
      hmDefaultFactoryTm.remove(currentTrustManager);
      hmTrustManager.remove(thread);
    }
    if (trustManager != null) {
      hmTrustManager.put(thread, trustManager);
    }
  }

  /**
   * Sets the provided key manager for the operations in the provided thread.
   * @param keyManager the key manager to use.
   * @param thread the thread where we want to use the provided key manager.
   */
  public static synchronized void setThreadKeyManager(
      KeyManager keyManager, Thread thread)
  {
    KeyManager currentKeyManager = hmKeyManager.get(thread);
    if (currentKeyManager != null) {
      hmDefaultFactoryKm.remove(currentKeyManager);
      hmKeyManager.remove(thread);
    }
    if (keyManager != null) {
      hmKeyManager.put(thread, keyManager);
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
    KeyManager   keyManager   = hmKeyManager.get(currentThread);
    SocketFactory result;

    if (trustManager == null)
    {
      if (keyManager == null)
      {
        result = new TrustedSocketFactory(null,null);
      }
      else
      {
        result = hmDefaultFactoryKm.get(keyManager);
        if (result == null)
        {
          result = new TrustedSocketFactory(null,keyManager);
          hmDefaultFactoryKm.put(keyManager, result);
        }
      }
    }
    else
    {
      if (keyManager == null)
      {
        result = hmDefaultFactoryTm.get(trustManager);
        if (result == null)
        {
          result = new TrustedSocketFactory(trustManager, null);
          hmDefaultFactoryTm.put(trustManager, result);
        }
      }
      else
      {
        SocketFactory tmsf = hmDefaultFactoryTm.get(trustManager);
        SocketFactory kmsf = hmDefaultFactoryKm.get(keyManager);
        if ( tmsf == null || kmsf == null)
        {
          result = new TrustedSocketFactory(trustManager, keyManager);
          hmDefaultFactoryTm.put(trustManager, result);
          hmDefaultFactoryKm.put(keyManager, result);
        }
        else
        if ( !tmsf.equals(kmsf) )
        {
          result = new TrustedSocketFactory(trustManager, keyManager);
          hmDefaultFactoryTm.put(trustManager, result);
          hmDefaultFactoryKm.put(keyManager, result);
        }
        else
        {
          result = tmsf ;
        }
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
      KeyManager[] km = null;
      TrustManager[] tm = null;

      try {
        SSLContext sslCtx = SSLContext.getInstance(algorithm);
        if (trustManager != null)
        {
          tm = new TrustManager[] { trustManager };
        }
        if (keyManager != null)
        {
          km = new KeyManager[] { keyManager };
        }
        sslCtx.init(km, tm, new java.security.SecureRandom() );
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

