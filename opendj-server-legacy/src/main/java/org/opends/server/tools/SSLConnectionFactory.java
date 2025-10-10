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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools;


import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opends.server.extensions.BlindTrustManagerProvider;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.ExpirationCheckTrustManager;
import org.opends.server.util.SelectableCertificateKeyManager;

import com.forgerock.opendj.cli.ConnectionFactoryProvider;

import static org.opends.messages.ToolMessages.*;
import static com.forgerock.opendj.util.StaticUtils.isFips;

/**
 * This class provides SSL connection related utility functions.
 */
public class SSLConnectionFactory
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * List of available TLS protocols. By default, corresponds to all TLS protocols available in the JVM.
   * The list may be overridden if <em>org.opends.ldaps.protocols</em> system property is set.
   */
  private static final String[] TLS_PROTOCOLS;

  static
  {
    List<String> protocols = null;
    try
    {
      protocols = ConnectionFactoryProvider.getDefaultProtocols();
    }
    catch (NoSuchAlgorithmException ex)
    {
      logger.trace("Unable to retrieve default TLS protocols of the JVM, defaulting to TLS", ex);
      protocols = Arrays.asList(SSLContextBuilder.PROTOCOL_TLS);
    }
    TLS_PROTOCOLS = protocols.toArray(new String[protocols.size()]);
  }

  private SSLSocketFactory sslSocketFactory;

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
			if (isFips()) {
				TrustManager tm = TrustManagers.checkUsingPkcs12TrustStore();
				trustManagers = new TrustManager[] { tm };
			} else {
				trustManagers = PromptTrustManager.getTrustManagers();
			}
	  } else
      {
        TrustManager[] tmpTrustManagers =
             getTrustManagers(KeyStore.getDefaultType(), null, trustStorePath,
                              trustStorePassword);
        trustManagers = new TrustManager[tmpTrustManagers.length];
        if (isFips()) {
          trustManagers = tmpTrustManagers;
        } else {
          for (int i=0; i < trustManagers.length; i++)
          {
            trustManagers[i] =
                 new ExpirationCheckTrustManager((X509TrustManager)
                                                 tmpTrustManagers[i]);
          }
        }
      }
      if(keyStorePath != null)
      {
        keyManagers = getKeyManagers(KeyStore.getDefaultType(), null,
                          keyStorePath, keyStorePassword);

        if (clientAlias != null)
        {
          keyManagers = SelectableCertificateKeyManager.wrap(keyManagers, CollectionUtils.newTreeSet(clientAlias));
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
      throw new SSLConnectionException(ERR_TOOLS_SSL_CONNECTION_NOT_INITIALIZED.get());
    }
    return socketWithEnabledProtocols(sslSocketFactory.createSocket(hostName, portNumber));
  }

  private Socket socketWithEnabledProtocols(Socket socket)
  {
    SSLSocket sslSocket = (SSLSocket) socket;
    sslSocket.setEnabledProtocols(TLS_PROTOCOLS);
    return sslSocket;
  }

  /**
   * Create the SSL socket connection to the specified host.
   *
   * @param host
   *          The address of the system to which the connection should be
   *          established.
   * @param portNumber
   *          The port number to which the connection should be established.
   * @return The SSL socket established to the specified host.
   * @throws SSLConnectionException
   *           If a problem occurs while performing SSL negotiation.
   * @throws IOException
   *           If a problem occurs while attempting to communicate with the
   *           server.
   */
  public Socket createSocket(InetAddress host, int portNumber)
      throws SSLConnectionException, IOException
  {
    if (sslSocketFactory == null)
    {
      throw new SSLConnectionException(ERR_TOOLS_SSL_CONNECTION_NOT_INITIALIZED.get());
    }
    return socketWithEnabledProtocols(sslSocketFactory.createSocket(host, portNumber));
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
      throw new SSLConnectionException(ERR_TOOLS_SSL_CONNECTION_NOT_INITIALIZED.get());
    }
    return socketWithEnabledProtocols(sslSocketFactory.createSocket(s, hostName, portNumber, autoClose));
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
      logger.traceException(e);

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
      logger.traceException(ke);

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
      logger.traceException(e);

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
      logger.traceException(ke);

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

