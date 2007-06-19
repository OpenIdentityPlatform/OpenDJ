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

import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;


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
 * NOTE: this class is not aimed to be used when we have connections in paralel.
 */
public class ApplicationKeyManager implements X509KeyManager
{
  static private final Logger LOG =
    Logger.getLogger(ApplicationKeyManager.class.getName());

  /**
   * The default keyManager.
   */
  private X509KeyManager sunJSSEX509KeyManager = null ;

  /**
   * The default constructor.
   * @param keystore The keystore to use for this keymanager.
   * @param password The keystore password to use for this keymanager.
   */
  public ApplicationKeyManager(KeyStore keystore, char[] password)
  {
    KeyManagerFactory kmf = null;
    String algo = "SunX509";
    String provider = "SunJSSE";
    try
    {
      kmf = KeyManagerFactory.getInstance(algo, provider);
      kmf.init(keystore, password);
      KeyManager kms[] = kmf.getKeyManagers();

      /*
       * Iterate over the returned keymanagers, look for an instance
       * of X509KeyManager. If found, use that as our "default" key
       * manager.
       */
      for (int i = 0; i < kms.length; i++)
      {
        if (kms[i] instanceof X509KeyManager)
        {
          sunJSSEX509KeyManager = (X509KeyManager) kms[i];
          break;
        }
      }

    }
    catch (NoSuchAlgorithmException e)
    {
      // Nothing to do. Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the algorithm", e);
    }
    catch (NoSuchProviderException e)
    {
      // Nothing to do. Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the provider", e);
    }
    catch (KeyStoreException e)
    {
      // Nothing to do. Maybe we should avoid this and be strict, but we are
      // in a best effor mode..
      LOG.log(Level.WARNING, "Error with the keystore", e);
    }
    catch (UnrecoverableKeyException e)
    {
      // Nothing to do. Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the key", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public String chooseClientAlias(String[] keyType, Principal[] issuers,
      Socket socket)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.chooseClientAlias(keyType, issuers, socket);
    }
    else
    {
      return null ;
    }
  }

  /**
   * {@inheritDoc}
   */
  public String chooseServerAlias(String keyType, Principal[] issuers,
      Socket socket)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.chooseServerAlias(keyType, issuers, socket);
    }
    else
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public X509Certificate[] getCertificateChain(String alias)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.getCertificateChain(alias);
    }
    else
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public String[] getClientAliases(String keyType, Principal[] issuers)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.getClientAliases(keyType, issuers);
    }
    else
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public PrivateKey getPrivateKey(String alias)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.getPrivateKey(alias);
    }
    else
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public String[] getServerAliases(String keyType, Principal[] issuers)
  {
    if (sunJSSEX509KeyManager != null)
    {
      return sunJSSEX509KeyManager.getServerAliases(keyType, issuers);
    }
    else
    {
      return null;
    }
  }
}
