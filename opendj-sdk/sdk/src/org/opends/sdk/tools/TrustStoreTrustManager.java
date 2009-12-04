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

package org.opends.sdk.tools;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opends.sdk.DN;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * This class is in charge of checking whether the certificates that are
 * presented are trusted or not. This implementation tries to check also
 * that the subject DN of the certificate corresponds to the host passed
 * using the setHostName method. This implementation also checks to make
 * sure the certificate is in the validity period. The constructor tries
 * to use a default TrustManager from the system and if it cannot be
 * retrieved this class will only accept the certificates explicitly
 * accepted by the user (and specified by calling acceptCertificate).
 */
class TrustStoreTrustManager implements X509TrustManager
{
  static private final Logger LOG = Logger
      .getLogger(TrustStoreTrustManager.class.getName());

  private final X509TrustManager trustManager;

  private final KeyStore truststore;

  private final File truststoreFile;

  private final char[] truststorePassword;

  private final String hostname;



  /**
   * The default constructor.
   */
  TrustStoreTrustManager(String truststorePath,
      String truststorePassword, String hostname,
      boolean checkValidityDates) throws KeyStoreException,
      IOException, NoSuchAlgorithmException, CertificateException
  {
    Validator.ensureNotNull(truststorePath);
    this.truststoreFile = new File(truststorePath);
    if (truststorePassword != null)
    {
      this.truststorePassword = truststorePassword.toCharArray();
    }
    else
    {
      this.truststorePassword = null;
    }
    truststore = KeyStore.getInstance(KeyStore.getDefaultType());

    FileInputStream fos = new FileInputStream(truststoreFile);
    truststore.load(fos, this.truststorePassword);
    TrustManagerFactory tmf = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm());

    tmf.init(truststore);
    X509TrustManager x509tm = null;
    for (TrustManager tm : tmf.getTrustManagers())
    {
      if (tm instanceof X509TrustManager)
      {
        x509tm = (X509TrustManager) tm;
        break;
      }
    }
    if (x509tm == null)
    {
      throw new NoSuchAlgorithmException();
    }
    this.trustManager = x509tm;
    this.hostname = hostname;
    // this.checkValidityDates = checkValidityDates;
  }



  /**
   * {@inheritDoc}
   */
  public void checkClientTrusted(X509Certificate[] chain,
      String authType) throws CertificateException
  {
    verifyExpiration(chain);
    verifyHostName(chain);
    trustManager.checkClientTrusted(chain, authType);
  }



  /**
   * {@inheritDoc}
   */
  public void checkServerTrusted(X509Certificate[] chain,
      String authType) throws CertificateException
  {
    verifyExpiration(chain);
    verifyHostName(chain);
    trustManager.checkClientTrusted(chain, authType);
  }



  /**
   * {@inheritDoc}
   */
  public X509Certificate[] getAcceptedIssuers()
  {
    if (trustManager != null)
    {
      return trustManager.getAcceptedIssuers();
    }
    else
    {
      return new X509Certificate[0];
    }
  }



  private void verifyExpiration(X509Certificate[] chain)
      throws CertificateException
  {
    Date currentDate = new Date();
    for (X509Certificate c : chain)
    {
      try
      {
        c.checkValidity(currentDate);
      }
      catch (CertificateExpiredException cee)
      {
        LOG.log(Level.WARNING, "Refusing to trust security"
            + " certificate \"" + c.getSubjectDN().getName()
            + "\" because it" + " expired on "
            + String.valueOf(c.getNotAfter()));

        throw cee;
      }
      catch (CertificateNotYetValidException cnyve)
      {
        LOG.log(Level.WARNING, "Refusing to trust security"
            + " certificate \"" + c.getSubjectDN().getName()
            + "\" because it" + " is not valid until "
            + String.valueOf(c.getNotBefore()));

        throw cnyve;
      }
    }
  }



  /**
   * Verifies that the provided certificate chains subject DN
   * corresponds to the host name specified with the setHost method.
   *
   * @param chain
   *          the certificate chain to analyze.
   * @throws HostnameMismatchCertificateException
   *           if the subject DN of the certificate does not match with
   *           the host name specified with the method setHost.
   */
  private void verifyHostName(X509Certificate[] chain)
      throws HostnameMismatchCertificateException
  {
    if (hostname != null)
    {
      try
      {
        DN dn = DN.valueOf(
            chain[0].getSubjectX500Principal().getName(), Schema
                .getCoreSchema());
        String value = dn.iterator().next().iterator().next()
            .getAttributeValue().toString();
        if (!hostMatch(value, hostname))
        {
          throw new HostnameMismatchCertificateException(
              "Hostname mismatch between host name " + hostname
                  + " and subject DN: "
                  + chain[0].getSubjectX500Principal(), hostname,
              chain[0].getSubjectX500Principal().getName());
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error parsing subject dn: "
            + chain[0].getSubjectX500Principal(), t);
      }
    }
  }



  /**
   * Checks whether two host names match. It accepts the use of wildcard
   * in the host name.
   *
   * @param host1
   *          the first host name.
   * @param host2
   *          the second host name.
   * @return <CODE>true</CODE> if the host match and <CODE>false</CODE>
   *         otherwise.
   */
  private boolean hostMatch(String host1, String host2)
  {
    if (host1 == null)
    {
      throw new IllegalArgumentException(
          "The host1 parameter cannot be null");
    }
    if (host2 == null)
    {
      throw new IllegalArgumentException(
          "The host2 parameter cannot be null");
    }
    String[] h1 = host1.split("\\.");
    String[] h2 = host2.split("\\.");

    boolean hostMatch = h1.length == h2.length;
    for (int i = 0; i < h1.length && hostMatch; i++)
    {
      if (!h1[i].equals("*") && !h2[i].equals("*"))
      {
        hostMatch = h1[i].equalsIgnoreCase(h2[i]);
      }
    }
    return hostMatch;
  }
}
