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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.opends.quicksetup.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class used to get the KeyStore that the graphical utilities use.
 *
 */
public class UIKeyStore extends KeyStore
{
  private static KeyStore keyStore = null;

  private static final Logger LOG = Logger.getLogger(
      UIKeyStore.class.getName());

  /**
   * This should never be called.
   */
  private UIKeyStore()
  {
    super(null, null, null);
  }
  /**
   * Returns the KeyStore to be used by graphical applications.
   * @return the KeyStore to be used by graphical applications.
   * @throws IOException if there was a file system access error.
   * @throws KeyStoreException if there was a problem while reading the key
   * store.
   * @throws CertificateException if an error with a certificate occurred.
   * @throws NoSuchAlgorithmException if the used algorithm is not supported
   * by the system.
   */
  public static KeyStore getInstance() throws IOException, KeyStoreException,
      CertificateException, NoSuchAlgorithmException
  {
    if (keyStore == null)
    {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      String keyStorePath = getKeyStorePath();

      File f = new File(keyStorePath);
      if (!f.exists())
      {
        LOG.log(Level.INFO, "Path "+keyStorePath+ " does not exist");
        keyStorePath = null;
      }
      else if (f.isDirectory())
      {
        LOG.log(Level.SEVERE, "Path "+keyStorePath+ " is a directory");
        keyStorePath = null;
      }
      else if (!f.canRead())
      {
        LOG.log(Level.SEVERE, "Path "+keyStorePath+ " is not readable");
        keyStorePath = null;
      }
      else if (!f.canWrite())
      {
        LOG.log(Level.SEVERE, "Path "+keyStorePath+ " is not writable");
        keyStorePath = null;
      }


      if (keyStorePath != null)
      {
        FileInputStream fos = new FileInputStream(keyStorePath);
        try
        {
          keyStore.load(fos, null);
        }
        catch (Throwable t)
        {
          LOG.log(Level.SEVERE, "Error reading key store on "+keyStorePath, t);
          keyStore.load(null, null);
        }
        fos.close();
      }
      else
      {
        keyStore.load(null, null);
      }
      loadLocalAdminTrustStore(keyStore);
    }
    return keyStore;
  }

  /**
   * Updates the Key Store with the provided certificate chain.
   * @param chain the certificate chain to be accepted.
   * @throws IOException if there was a file system access error.
   * @throws KeyStoreException if there was a problem while reading or writing
   * to the key store.
   * @throws CertificateException if an error with a certificate occurred.
   * @throws NoSuchAlgorithmException if the used algorithm is not supported
   * by the system.
   */
  public static void acceptCertificate(X509Certificate[] chain)
      throws IOException,KeyStoreException, CertificateException,
      NoSuchAlgorithmException
  {
    LOG.log(Level.INFO, "Accepting certificate chain.");
    KeyStore k = getInstance();
    for (X509Certificate aChain : chain) {
      if (!containsCertificate(aChain, k)) {
        String alias = aChain.getSubjectDN().getName();
        int j = 1;
        while (k.containsAlias(alias)) {
          alias = aChain.getSubjectDN().getName() + "-" + j;
          j++;
        }
        k.setCertificateEntry(alias, aChain);
      }
    }
    String keyStorePath = getKeyStorePath();
    File f = new File(keyStorePath);
    if (!f.exists())
    {
      Utils.createFile(f);
    }
    FileOutputStream fos = new FileOutputStream(getKeyStorePath(), false);
    k.store(fos, new char[]{});
    fos.close();
  }

  /**
   * Returns the path where we store the keystore for the graphical
   * applications.
   * @return the path where we store the keystore for the graphical
   * applications.
   */
  private static String getKeyStorePath()
  {
    return System.getProperty("user.home") + File.separator +
    ".opendj" + File.separator + "gui-keystore";
  }

  /**
   * Loads the local admin truststore.
   * @param keyStore the keystore where the admin truststore will be loaded.
   */
  private static void loadLocalAdminTrustStore(KeyStore keyStore)
  {
    String adminTrustStorePath = getLocalAdminTrustStorePath();
    File f = new File(adminTrustStorePath);
    if (!f.exists())
    {
      LOG.log(Level.INFO, "Path "+adminTrustStorePath+ " does not exist");
      adminTrustStorePath = null;
    }
    else if (f.isDirectory())
    {
      LOG.log(Level.SEVERE, "Path "+adminTrustStorePath+ " is a directory");
      adminTrustStorePath = null;
    }
    else if (!f.canRead())
    {
      LOG.log(Level.SEVERE, "Path "+adminTrustStorePath+ " is not readable");
      adminTrustStorePath = null;
    }

    if (adminTrustStorePath != null)
    {
      FileInputStream fos = null;
      try
      {
        fos = new FileInputStream(adminTrustStorePath);
        KeyStore adminKeyStore =
          KeyStore.getInstance(KeyStore.getDefaultType());
        adminKeyStore.load(fos, null);
        Enumeration<String> aliases = adminKeyStore.aliases();
        while (aliases.hasMoreElements())
        {
          String alias = aliases.nextElement();
          if (adminKeyStore.isCertificateEntry(alias))
          {
            keyStore.setCertificateEntry(alias,
                adminKeyStore.getCertificate(alias));
          }
          else
          {
            keyStore.setEntry(alias, adminKeyStore.getEntry(alias, null), null);
          }
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error reading admin key store on "+
            adminTrustStorePath, t);
      }
      finally
      {
        try
        {
          if (fos != null)
          {
            fos.close();
          }
        }
        catch (Throwable t)
        {
          LOG.log(Level.SEVERE, "Error closing admin key store on "+
              adminTrustStorePath, t);
        }
      }
    }
  }

  /**
   * Returns the path where the local admin trust store is.
   * @return the path where the local admin trust store is.
   */
  private static String getLocalAdminTrustStorePath()
  {
    String instancePath =
      Utils.getInstancePathFromInstallPath(Utils.getInstallPathFromClasspath());
    return  instancePath + File.separator + "config" +
    File.separator + "admin-truststore";
  }

  /**
   * Returns whether the key store contains the provided certificate or not.
   * @param cert the certificate.
   * @param keyStore the key store.
   * @return whether the key store contains the provided certificate or not.
   * @throws KeyStoreException if an error occurs reading the contents of the
   * key store.
   */
  private static boolean containsCertificate(X509Certificate cert,
      KeyStore keyStore) throws KeyStoreException
  {
    boolean found = false;
    Enumeration<String> aliases = keyStore.aliases();
    while (aliases.hasMoreElements() && !found)
    {
      String alias = aliases.nextElement();
      if (keyStore.isCertificateEntry(alias))
      {
        Certificate c = keyStore.getCertificate(alias);
        found = c.equals(cert);
      }
    }
    return found;
  }
}
