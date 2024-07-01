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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2009 Parametric Technology Corporation (PTC)
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.util.Platform;

/**
 * This class is in charge of checking whether the certificates that are
 * presented are trusted or not.
 * This implementation tries to check also that the subject DN of the
 * certificate corresponds to the host passed using the setHostName method.
 *
 * The constructor tries to use a default TrustManager from the system and if
 * it cannot be retrieved this class will only accept the certificates
 * explicitly accepted by the user (and specified by calling acceptCertificate).
 *
 * NOTE: this class is not aimed to be used when we have connections in parallel.
 */
public class ApplicationKeyManager implements X509KeyManager
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default keyManager. */
  private X509KeyManager keyManager;

  /**
   * The default constructor.
   * @param keystore The keystore to use for this keymanager.
   * @param password The keystore password to use for this keymanager.
   */
  public ApplicationKeyManager(KeyStore keystore, char[] password)
  {
    KeyManagerFactory kmf = null;
    String userSpecifiedAlgo =
      System.getProperty("org.opends.admin.keymanageralgo");
    String userSpecifiedProvider =
      System.getProperty("org.opends.admin.keymanagerprovider");

    //Handle IBM specific cases if the user did not specify a algorithm and/or
    //provider.
    if(userSpecifiedAlgo == null && Platform.isVendor("IBM"))
    {
      userSpecifiedAlgo = "IbmX509";
    }
    if(userSpecifiedProvider == null && Platform.isVendor("IBM"))
    {
      userSpecifiedProvider = "IBMJSSE2";
    }

    // Have some fallbacks to choose the provider and algorith of the key
    // manager.  First see if the user wanted to use something specific,
    // then try with the SunJSSE provider and SunX509 algorithm. Finally,
    // fallback to the default algorithm of the JVM.
    String[] preferredProvider =
        { userSpecifiedProvider, "SunJSSE", null, null };
    String[] preferredAlgo =
        { userSpecifiedAlgo, "SunX509", "SunX509",
          TrustManagerFactory.getDefaultAlgorithm() };

    for (int i=0; i<preferredProvider.length && keyManager == null; i++)
    {
      String provider = preferredProvider[i];
      String algo = preferredAlgo[i];
      if (algo == null)
      {
        continue;
      }
      try
      {
        if (provider != null)
        {
          kmf = KeyManagerFactory.getInstance(algo, provider);
        }
        else
        {
          kmf = KeyManagerFactory.getInstance(algo);
        }
        kmf.init(keystore, password);
        KeyManager kms[] = kmf.getKeyManagers();
        /*
         * Iterate over the returned keymanagers, look for an instance
         * of X509KeyManager. If found, use that as our "default" key manager.
         */
        for (KeyManager km : kms)
        {
          if (kms[i] instanceof X509KeyManager)
          {
            keyManager = (X509KeyManager) km;
            break;
          }
        }
      }
      catch (NoSuchAlgorithmException e)
      {
        // Nothing to do. Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the algorithm", e));
      }
      catch (KeyStoreException e)
      {
        // Nothing to do. Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the keystore", e));
      }
      catch (UnrecoverableKeyException e)
      {
        // Nothing to do. Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the key", e));
      }
      catch (NoSuchProviderException e)
      {
        // Nothing to do. Maybe we should avoid this and be strict, but we are
        // in a best effort mode.
        logger.warn(LocalizableMessage.raw("Error with the provider", e));
      }
    }
  }

  @Override
  public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
  {
    return keyManager != null ? keyManager.chooseClientAlias(keyType, issuers, socket) : null;
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
  {
    return keyManager != null ? keyManager.chooseServerAlias(keyType, issuers, socket) : null;
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias)
  {
    return keyManager != null ? keyManager.getCertificateChain(alias) : null;
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers)
  {
    return keyManager != null ? keyManager.getClientAliases(keyType, issuers) : null;
  }

  @Override
  public PrivateKey getPrivateKey(String alias)
  {
    return keyManager != null ? keyManager.getPrivateKey(alias) : null;
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers)
  {
    return keyManager != null ? keyManager.getServerAliases(keyType, issuers) : null;
  }
}
