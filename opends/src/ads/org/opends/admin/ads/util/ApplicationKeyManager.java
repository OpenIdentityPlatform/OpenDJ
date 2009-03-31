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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2009 Parametric Technology Corporation (PTC)
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
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

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
 * NOTE: this class is not aimed to be used when we have connections in paralel.
 */
public class ApplicationKeyManager implements X509KeyManager
{
  static private final Logger LOG =
    Logger.getLogger(ApplicationKeyManager.class.getName());

  /**
   * The default keyManager.
   */
  private X509KeyManager keyManager = null ;

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
      userSpecifiedAlgo = "IbmX509";
    if(userSpecifiedProvider == null && Platform.isVendor("IBM"))
      userSpecifiedProvider = "IBMJSEE2";

    // Have some fallbacks to choose the provider and algorith of the key
    // manager.  First see if the user wanted to use something specific,
    // then try with the SunJSSE provider and SunX509 algorithm. Finally,
    // fallback to the default algorithm of the JVM.
    String[] preferredProvider =
    {
        userSpecifiedProvider,
        "SunJSSE",
        null,
        null
    };
    String[] preferredAlgo =
    {
        userSpecifiedAlgo,
        "SunX509",
        "SunX509",
        TrustManagerFactory.getDefaultAlgorithm()
    };
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
         * of X509KeyManager. If found, use that as our "default" key
         * manager.
         */
        for (int j = 0; j < kms.length; j++)
        {
          if (kms[i] instanceof X509KeyManager)
          {
            keyManager = (X509KeyManager) kms[j];
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
      catch (NoSuchProviderException e)
      {
        // Nothing to do. Maybe we should avoid this and be strict, but we are
        // in a best effor mode.
        LOG.log(Level.WARNING, "Error with the provider", e);
      }
    }
  }


  /**
   * Choose an alias to authenticate the client side of a secure
   * socket given the public key type and the list of certificate
   * issuer authorities recognized by the peer (if any).
   *
   * @param keyType
   *          the key algorithm type name(s), ordered with the
   *          most-preferred key type first.
   * @param issuers
   *          the list of acceptable CA issuer subject names or null
   *          if it does not matter which issuers are used.
   * @param socket
   *          the socket to be used for this connection. This
   *          parameter can be null, in which case this method will
   *          return the most generic alias to use.
   * @return the alias name for the desired key, or null if there are
   *         no matches.
   */
  public String chooseClientAlias(String[] keyType, Principal[] issuers,
      Socket socket)
  {
    if (keyManager != null)
    {
      return keyManager.chooseClientAlias(keyType, issuers, socket);
    }
    else
    {
      return null ;
    }
  }

  /**
   * Choose an alias to authenticate the client side of a secure
   * socket given the public key type and the list of certificate
   * issuer authorities recognized by the peer (if any).
   *
   * @param keyType
   *          the key algorithm type name(s), ordered with the
   *          most-preferred key type first.
   * @param issuers
   *          the list of acceptable CA issuer subject names or null
   *          if it does not matter which issuers are used.
   * @param socket
   *          the socket to be used for this connection. This
   *          parameter can be null, in which case this method will
   *          return the most generic alias to use.
   * @return the alias name for the desired key, or null if there are
   *         no matches.
   */
  public String chooseServerAlias(String keyType, Principal[] issuers,
      Socket socket)
  {
    if (keyManager != null)
    {
      return keyManager.chooseServerAlias(keyType, issuers, socket);
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns the certificate chain associated with the given alias.
   *
   * @param alias
   *          the alias name
   * @return the certificate chain (ordered with the user's
   *         certificate first and the root certificate authority
   *         last), or null if the alias can't be found.
   */
  public X509Certificate[] getCertificateChain(String alias)
  {
    if (keyManager != null)
    {
      return keyManager.getCertificateChain(alias);
    }
    else
    {
      return null;
    }
  }

  /**
   * Get the matching aliases for authenticating the server side of a
   * secure socket given the public key type and the list of
   * certificate issuer authorities recognized by the peer (if any).
   *
   * @param keyType
   *          the key algorithm type name
   * @param issuers
   *          the list of acceptable CA issuer subject names or null
   *          if it does not matter which issuers are used.
   * @return an array of the matching alias names, or null if there
   *         were no matches.
   */
  public String[] getClientAliases(String keyType, Principal[] issuers)
  {
    if (keyManager != null)
    {
      return keyManager.getClientAliases(keyType, issuers);
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns the key associated with the given alias.
   *
   * @param alias
   *          the alias name
   * @return the requested key, or null if the alias can't be found.
   */
  public PrivateKey getPrivateKey(String alias)
  {
    if (keyManager != null)
    {
      return keyManager.getPrivateKey(alias);
    }
    else
    {
      return null;
    }
  }

  /**
   * Get the matching aliases for authenticating the server side of a
   * secure socket given the public key type and the list of
   * certificate issuer authorities recognized by the peer (if any).
   *
   * @param keyType
   *          the key algorithm type name
   * @param issuers
   *          the list of acceptable CA issuer subject names or null
   *          if it does not matter which issuers are used.
   * @return an array of the matching alias names, or null if there
   *         were no matches.
   */
  public String[] getServerAliases(String keyType, Principal[] issuers)
  {
    if (keyManager != null)
    {
      return keyManager.getServerAliases(keyType, issuers);
    }
    else
    {
      return null;
    }
  }
}
