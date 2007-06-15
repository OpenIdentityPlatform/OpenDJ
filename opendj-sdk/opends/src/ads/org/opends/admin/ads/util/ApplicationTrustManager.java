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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
public class ApplicationTrustManager implements X509TrustManager
{
  /**
   * The enumeration for the different causes for which the trust manager can
   * refuse to accept a certificate.
   */
  public enum Cause
  {
    /**
     * The certificate was not trusted.
     */
    NOT_TRUSTED,
    /**
     * The certificate's subject DN's value and the host name we tried to
     * connect to do not match.
     */
    HOST_NAME_MISMATCH
  }
  static private final Logger LOG =
    Logger.getLogger(ApplicationTrustManager.class.getName());

  private X509TrustManager sunJSSEX509TrustManager;
  private String lastRefusedAuthType;
  private X509Certificate[] lastRefusedChain;
  private Cause lastRefusedCause = null;

  /*
   * The following ArrayList contain information about the certificates
   * explicitly accepted by the user.
   */
  private ArrayList<X509Certificate[]> acceptedChains =
    new ArrayList<X509Certificate[]>();
  private ArrayList<String> acceptedAuthTypes = new ArrayList<String>();
  private ArrayList<String> acceptedHosts = new ArrayList<String>();

  private String host;


  /**
   * The default constructor.
   * @param keystore The keystore to use for this trustmanager.
   */
  public ApplicationTrustManager(KeyStore keystore)
  {
    TrustManagerFactory tmf = null;
    String algo = "SunX509";
    String provider = "SunJSSE";
    try
    {
      tmf = TrustManagerFactory.getInstance(algo, provider);
      tmf.init(keystore);
      sunJSSEX509TrustManager =
        (X509TrustManager)(tmf.getTrustManagers())[0];
    }
    catch (NoSuchAlgorithmException e)
    {
      // Nothing to do: if this occurs we will systematically refuse the
      // certificates.  Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the algorithm", e);
    }
    catch (NoSuchProviderException e)
    {
      // Nothing to do: if this occurs we will systematically refuse the
      // certificates.  Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the provider", e);
    }
    catch (KeyStoreException e)
    {
      // Nothing to do: if this occurs we will systematically refuse the
      // certificates.  Maybe we should avoid this and be strict, but we are
      // in a best effor mode.
      LOG.log(Level.WARNING, "Error with the keystore", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
    boolean explicitlyAccepted = false;
    try
    {
      if (sunJSSEX509TrustManager != null)
      {
        try
        {
          sunJSSEX509TrustManager.checkClientTrusted(chain, authType);
        }
        catch (CertificateException ce)
        {
          verifyAcceptedCertificates(chain, authType);
          explicitlyAccepted = true;
        }
      }
      else
      {
        verifyAcceptedCertificates(chain, authType);
        explicitlyAccepted = true;
      }
    }
    catch (CertificateException ce)
    {
      lastRefusedChain = chain;
      lastRefusedAuthType = authType;
      lastRefusedCause = Cause.NOT_TRUSTED;
      throw ce;
    }

    if (!explicitlyAccepted)
    {
      try
      {
        verifyHostName(chain, authType);
      }
      catch (CertificateException ce)
      {
        lastRefusedChain = chain;
        lastRefusedAuthType = authType;
        lastRefusedCause = Cause.HOST_NAME_MISMATCH;
        throw ce;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void checkServerTrusted(X509Certificate[] chain,
      String authType) throws CertificateException
  {
    boolean explicitlyAccepted = false;
    try
    {
      if (sunJSSEX509TrustManager != null)
      {
        try
        {
          sunJSSEX509TrustManager.checkServerTrusted(chain, authType);
        }
        catch (CertificateException ce)
        {
          verifyAcceptedCertificates(chain, authType);
          explicitlyAccepted = true;
        }
      }
      else
      {
        verifyAcceptedCertificates(chain, authType);
        explicitlyAccepted = true;
      }
    }
    catch (CertificateException ce)
    {
      lastRefusedChain = chain;
      lastRefusedAuthType = authType;
      lastRefusedCause = Cause.NOT_TRUSTED;
      throw ce;
    }

    if (!explicitlyAccepted)
    {
      try
      {
        verifyHostName(chain, authType);
      }
      catch (CertificateException ce)
      {
        lastRefusedChain = chain;
        lastRefusedAuthType = authType;
        lastRefusedCause = Cause.HOST_NAME_MISMATCH;
        throw ce;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public X509Certificate[] getAcceptedIssuers()
  {
    if (sunJSSEX509TrustManager != null)
    {
      return sunJSSEX509TrustManager.getAcceptedIssuers();
    }
    else
    {
      return new X509Certificate[0];
    }
  }

  /**
   * This method is called when the user accepted a certificate.
   * @param chain the certificate chain accepted by the user.
   * @param authType the authentication type.
   * @param host the host we tried to connect and that presented the
   * certificate.
   */
  public void acceptCertificate(X509Certificate[] chain, String authType,
      String host)
  {
    acceptedChains.add(chain);
    acceptedAuthTypes.add(authType);
    acceptedHosts.add(host);
  }

  /**
   * Sets the host name we are trying to contact in a secure mode.  This
   * method is used if we want to verify the correspondance between the
   * hostname and the subject DN of the certificate that is being presented.
   * If this method is never called (or called passing null) no verification
   * will be made on the host name.
   * @param host the host name we are trying to contact in a secure mode.
   */
  public void setHost(String host)
  {
    this.host = host;
  }

  /**
   * This is a method used to set to null the different members that provide
   * information about the last refused certificate.  It is recommended to
   * call this method before trying to establish a connection using this
   * trust manager.
   */
  public void resetLastRefusedItems()
  {
    lastRefusedAuthType = null;
    lastRefusedChain = null;
    lastRefusedCause = null;
  }

  /**
   * Creates a copy of this ApplicationTrustManager.
   * @return a copy of this ApplicationTrustManager.
   */
  public ApplicationTrustManager createCopy()
  {
    ApplicationTrustManager copy = new ApplicationTrustManager(null);
    copy.lastRefusedAuthType = lastRefusedAuthType;
    copy.lastRefusedChain = lastRefusedChain;
    copy.lastRefusedCause = lastRefusedCause;
    copy.acceptedChains.addAll(acceptedChains);
    copy.acceptedAuthTypes.addAll(acceptedAuthTypes);
    copy.acceptedHosts.addAll(acceptedHosts);

    copy.host = host;

    return copy;
  }

  /**
   * Verifies whether the provided chain and authType have been already accepted
   * by the user or not.  If they have not a CertificateException is thrown.
   * @param chain the certificate chain to analyze.
   * @param authType the authentication type.
   * @throws CertificateException if the provided certificate chain and the
   * authentication type have not been accepted explicitly by the user.
   */
  private void verifyAcceptedCertificates(X509Certificate[] chain,
      String authType) throws CertificateException
  {
    boolean found = false;
    for (int i=0; i<acceptedChains.size() && !found; i++)
    {
      if (authType.equals(acceptedAuthTypes.get(i)))
      {
        X509Certificate[] current = acceptedChains.get(i);
        found = current.length == chain.length;
        for (int j=0; j<chain.length && found; j++)
        {
          found = chain[j].equals(current[j]);
        }
      }
    }
    if (!found)
    {
      throw new CertificateException(
          "Certificate not in list of accepted certificates");
    }
  }

  /**
   * Verifies that the provided certificate chains subject DN corresponds to the
   * host name specified with the setHost method.
   * @param chain the certificate chain to analyze.
   * @throws CertificateException if the subject DN of the certificate does
   * not match with the host name specified with the method setHost.
   */
  private void verifyHostName(X509Certificate[] chain, String authType)
  throws CertificateException
  {
    if (host != null)
    {
      boolean matches = false;
      try
      {
        LdapName dn =
          new LdapName(chain[0].getSubjectX500Principal().getName());
        Rdn rdn = dn.getRdn(0);
        String value = rdn.getValue().toString();
        matches = host.equalsIgnoreCase(value);
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error parsing subject dn: "+
            chain[0].getSubjectX500Principal(), t);
      }

      if (!matches)
      {
        throw new CertificateException("Hostname mismatch between host name "+
            host+" and subject DN: "+chain[0].getSubjectX500Principal());
      }
    }
  }

  /**
   * Returns the authentication type for the last refused certificate.
   * @return the authentication type for the last refused certificate.
   */
  public String getLastRefusedAuthType()
  {
    return lastRefusedAuthType;
  }

  /**
   * Returns the last cause for refusal of a certificate.
   * @return the last cause for refusal of a certificate.
   */
  public Cause getLastRefusedCause()
  {
    return lastRefusedCause;
  }

  /**
   * Returns the certificate chain for the last refused certificate.
   * @return the certificate chain for the last refused certificate.
   */
  public X509Certificate[] getLastRefusedChain()
  {
    return lastRefusedChain;
  }
}
