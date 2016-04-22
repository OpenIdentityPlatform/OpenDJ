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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * An implementation of SSLSocketFactory.
 * <p>
 * Note: The class must be public so it can be instantiated by the
 * {@link javax.naming.ldap.InitialLdapContext}.
 */
public class TrustedSocketFactory extends SSLSocketFactory
{
  private static final Map<Thread, TrustManager> hmTrustManager = new HashMap<>();
  private static final Map<Thread, KeyManager> hmKeyManager = new HashMap<>();

  private static final Map<TrustManager, SocketFactory> hmDefaultFactoryTm = new HashMap<>();
  private static final Map<KeyManager, SocketFactory> hmDefaultFactoryKm = new HashMap<>();

  private SSLSocketFactory innerFactory;
  private final TrustManager trustManager;
  private final KeyManager keyManager;

  /**
   * Constructor of the TrustedSocketFactory.
   * <p>
   * Note: The class must be public so it can be instantiated by the
   * {@link javax.naming.ldap.InitialLdapContext}.
   *
   * @param trustManager
   *          the trust manager to use.
   * @param keyManager
   *          the key manager to use.
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
  static synchronized void setCurrentThreadTrustManager(TrustManager trustManager, KeyManager keyManager)
  {
    setThreadTrustManager(trustManager, Thread.currentThread());
    setThreadKeyManager  (keyManager, Thread.currentThread());
  }

  /**
   * Sets the provided trust manager for the operations in the provided thread.
   * @param trustManager the trust manager to use.
   * @param thread the thread where we want to use the provided trust manager.
   */
  static synchronized void setThreadTrustManager(TrustManager trustManager, Thread thread)
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
  static synchronized void setThreadKeyManager(KeyManager keyManager, Thread thread)
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

  // SocketFactory implementation
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
        if (tmsf == null || kmsf == null)
        {
          result = new TrustedSocketFactory(trustManager, keyManager);
          hmDefaultFactoryTm.put(trustManager, result);
          hmDefaultFactoryKm.put(keyManager, result);
        }
        else if (!tmsf.equals(kmsf))
        {
          result = new TrustedSocketFactory(trustManager, keyManager);
          hmDefaultFactoryTm.put(trustManager, result);
          hmDefaultFactoryKm.put(keyManager, result);
        }
        else
        {
          result = tmsf;
        }
      }
    }

    return result;
  }

  @Override
  public Socket createSocket(InetAddress address, int port) throws IOException {
    return getInnerFactory().createSocket(address, port);
  }

  @Override
  public Socket createSocket(InetAddress address, int port,
      InetAddress clientAddress, int clientPort) throws IOException
  {
    return getInnerFactory().createSocket(address, port, clientAddress, clientPort);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException
  {
    return getInnerFactory().createSocket(host, port);
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress clientHost,
      int clientPort) throws IOException
  {
    return getInnerFactory().createSocket(host, port, clientHost, clientPort);
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
  throws IOException
  {
    return getInnerFactory().createSocket(s, host, port, autoClose);
  }

  @Override
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

  @Override
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

  private SSLSocketFactory getInnerFactory() throws IOException {
    if (innerFactory == null)
    {
      String algorithm = "TLSv1";

      try {
        KeyManager[] km = null;
        TrustManager[] tm = null;
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
        SSLKeyException xx = new SSLKeyException("Failed to create SSLContext for " + algorithm);
        xx.initCause(x);
        throw xx;
      }
    }
    return innerFactory;
  }
}
