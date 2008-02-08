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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;


import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opends.server.extensions.BlindTrustManagerProvider;
import org.opends.server.util.ExpirationCheckTrustManager;
import org.opends.server.util.SelectableCertificateKeyManager;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;


/**
 * This class provides SSL connection related utility functions.
 */
public class SSLConnectionFactory
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  private SSLSocketFactory sslSocketFactory = null;

  /**
   * Constructor for the SSL connection factory.
   */
  public SSLConnectionFactory()
  {
  }

  /**
   * Initialize the connection factory by creating the key and
   * trust managers for the SSL connection.
   *
   * @param  trustAll            Indicates whether to blindly trust all
   *                             certificates.
   * @param  keyStorePath        The path to the key store file.
   * @param  keyStorePassword    The PIN to use to access the key store
   *                             contents.
   * @param  clientAlias         The alias to use for the client certificate.
   * @param  trustStorePath      The path to the trust store file.
   * @param  trustStorePassword  The PIN to use to access the trust store
   *                             contents.
   *
   * @throws  SSLConnectionException  If a problem occurs while initializing the
   *                                  connection factory.
   */
  public void init(boolean trustAll, String keyStorePath,
                   String keyStorePassword, String clientAlias,
                   String trustStorePath, String trustStorePassword)
         throws SSLConnectionException
  {
    try
    {
      SSLContext ctx = SSLContext.getInstance("TLS");
      KeyManager[] keyManagers = null;
      TrustManager[] trustManagers = null;

      if(trustAll)
      {
        BlindTrustManagerProvider blindTrustProvider =
            new BlindTrustManagerProvider();
        trustManagers = blindTrustProvider.getTrustManagers();
      } else if (trustStorePath == null) {
        trustManagers = PromptTrustManager.getTrustManagers();
      } else
      {
        TrustManager[] tmpTrustManagers =
             getTrustManagers(KeyStore.getDefaultType(), null, trustStorePath,
                              trustStorePassword);
        trustManagers = new TrustManager[tmpTrustManagers.length];
        for (int i=0; i < trustManagers.length; i++)
        {
          trustManagers[i] =
               new ExpirationCheckTrustManager((X509TrustManager)
                                               tmpTrustManagers[i]);
        }
      }
      if(keyStorePath != null)
      {
        keyManagers = getKeyManagers(KeyStore.getDefaultType(), null,
                          keyStorePath, keyStorePassword);

        if (clientAlias != null)
        {
          keyManagers = SelectableCertificateKeyManager.wrap(keyManagers,
                                                             clientAlias);
        }
      }

      ctx.init(keyManagers, trustManagers, new java.security.SecureRandom());
      sslSocketFactory = ctx.getSocketFactory();
    } catch(Exception e)
    {
      throw new SSLConnectionException(
              ERR_TOOLS_CANNOT_CREATE_SSL_CONNECTION.get(e.getMessage()), e);
    }
  }

  /**
   * Create the SSL socket connection to the specified host.
   *
   * @param  hostName    The address of the system to which the connection
   *                     should be established.
   * @param  portNumber  The port number to which the connection should be
   *                     established.
   *
   * @return  The SSL socket established to the specified host.
   *
   * @throws  SSLConnectionException  If a problem occurs while performing SSL
   *                                  negotiation.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the server.
   */
  public Socket createSocket(String hostName, int portNumber)
      throws SSLConnectionException, IOException
  {
    if(sslSocketFactory == null)
    {

      throw new SSLConnectionException(
              ERR_TOOLS_SSL_CONNECTION_NOT_INITIALIZED.get());
    }
    return sslSocketFactory.createSocket(hostName, portNumber);
  }

  /**
   * Create the SSL socket connection to the specified host layered over
   * an existing socket.
   *
   * @param  s           The socket to use for the existing connection.
   * @param  hostName    The address of the system to which the connection
   *                     should be established.
   * @param  portNumber  The port number to which the connection should be
   *                     established.
   * @param  autoClose   Indicates whether the underlying connection should be
   *                     automatically closed when the SSL session is ended.
   *
   * @return  The SSL socket established to the specified host.
   *
   * @throws  SSLConnectionException  If a problem occurs while performing SSL
   *                                  negotiation.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the server.
   */
  public Socket createSocket(Socket s, String hostName, int portNumber,
                             boolean autoClose)
         throws SSLConnectionException, IOException
  {
    if(sslSocketFactory == null)
    {

      throw new SSLConnectionException(
              ERR_TOOLS_SSL_CONNECTION_NOT_INITIALIZED.get());
    }
    return sslSocketFactory.createSocket(s, hostName, portNumber, autoClose);
  }

  /**
   * Retrieves a set of <CODE>KeyManager</CODE> objects that may be used for
   * interactions requiring access to a key manager.
   *
   * @param  keyStoreType  The key store type to use with the specified file.
   * @param  provider      The provider to use when accessing the key store.
   * @param  keyStoreFile  The path to the file containing the key store data.
   * @param  keyStorePass  The PIN needed to access the key store contents.
   *
   * @return  A set of <CODE>KeyManager</CODE> objects that may be used for
   *          interactions requiring access to a key manager.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store.
   *
   * @throws  SSLConnectionException  If a problem occurs while trying to load
   *                                 key store file.
   */

  private KeyManager[] getKeyManagers(String keyStoreType,
                                      Provider provider,
                                      String keyStoreFile,
                                      String keyStorePass)
          throws KeyStoreException, SSLConnectionException
  {
    if(keyStoreFile == null)
    {
      // Lookup the file name through the JDK property.
      keyStoreFile = getKeyStore();
    }

    if(keyStorePass == null)
    {
      // Lookup the keystore PIN through the JDK property.
      keyStorePass = getKeyStorePIN();
    }

    KeyStore ks = null;
    if(provider != null)
    {
      ks = KeyStore.getInstance(keyStoreType, provider);
    } else
    {
      ks = KeyStore.getInstance(keyStoreType);
    }

    char[] keyStorePIN = null;
    if(keyStorePass != null)
    {
      keyStorePIN = keyStorePass.toCharArray();
    }

    try
    {
      FileInputStream inputStream = new FileInputStream(keyStoreFile);
      ks.load(inputStream, keyStorePIN);
      inputStream.close();

    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      throw new SSLConnectionException(
              ERR_TOOLS_CANNOT_LOAD_KEYSTORE_FILE.get(keyStoreFile), e);
    }

    try
    {
      String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
      KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(keyManagerAlgorithm);

      keyManagerFactory.init(ks, keyStorePIN);
      return keyManagerFactory.getKeyManagers();
    } catch(Exception ke)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ke);
      }

      throw new SSLConnectionException(
              ERR_TOOLS_CANNOT_INIT_KEYMANAGER.get(keyStoreFile), ke);
    }

  }


  /**
   * Retrieves a set of <CODE>TrustManager</CODE> objects that may be used for
   * interactions requiring access to a trust manager.
   *
   * @param  trustStoreType  The trust store type to use with the specified
   *                         file.
   * @param  provider        The provider to use when accessing the trust store.
   * @param  trustStoreFile  The path to the file containing the trust store
   *                         data.
   * @param  trustStorePass  The PIN needed to access the trust store contents.
   *
   * @return  A set of <CODE>TrustManager</CODE> objects that may be used for
   *          interactions requiring access to a trust manager.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             trust store.
   *
   * @throws  SSLConnectionException  If a problem occurs while trying to load
   *                                 trust store file.
   */
  private TrustManager[] getTrustManagers(String trustStoreType,
                                            Provider provider,
                                            String trustStoreFile,
                                            String trustStorePass)
      throws KeyStoreException, SSLConnectionException
  {
    if(trustStoreFile == null)
    {
      trustStoreFile = getTrustStore();
      // No trust store file available.
      if(trustStoreFile == null)
      {
        return null;
      }
    }

    if(trustStorePass == null)
    {
      trustStorePass = getTrustStorePIN();
    }

    KeyStore trustStore = null;
    if(provider != null)
    {
      trustStore = KeyStore.getInstance(trustStoreType, provider);
    } else
    {
      trustStore = KeyStore.getInstance(trustStoreType);
    }

    char[] trustStorePIN = null;
    if(trustStorePass != null)
    {
      trustStorePIN = trustStorePass.toCharArray();
    }

    try
    {
      FileInputStream inputStream = new FileInputStream(trustStoreFile);
      trustStore.load(inputStream, trustStorePIN);
      inputStream.close();
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      throw new SSLConnectionException(
              ERR_TOOLS_CANNOT_LOAD_TRUSTSTORE_FILE.get(trustStoreFile), e);
    }

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(trustManagerAlgorithm);

      trustManagerFactory.init(trustStore);
      return trustManagerFactory.getTrustManagers();
    } catch(Exception ke)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ke);
      }

      throw new SSLConnectionException(
              ERR_TOOLS_CANNOT_INIT_TRUSTMANAGER.get(trustStoreFile), ke);
    }

  }

  /**
   * Read the KeyStore PIN from the JSSE system property.
   *
   * @return  The PIN that should be used to access the key store.
   */

   private String getKeyStorePIN()
   {
    return System.getProperty("javax.net.ssl.keyStorePassword");
   }

  /**
   * Read the TrustStore PIN from the JSSE system property.
   *
   * @return  The PIN that should be used to access the trust store.
   */

   private String getTrustStorePIN()
   {
    return System.getProperty("javax.net.ssl.trustStorePassword");
   }

  /**
   * Read the KeyStore from the JSSE system property.
   *
   * @return  The path to the key store file.
   */

   private String getKeyStore()
   {
    return System.getProperty("javax.net.ssl.keyStore");
   }

  /**
   * Read the TrustStore from the JSSE system property.
   *
   * @return  The path to the trust store file.
   */

   private String getTrustStore()
   {
    return System.getProperty("javax.net.ssl.trustStore");
   }

}

