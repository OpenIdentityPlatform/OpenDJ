/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2009 Parametric Technology Corporation (PTC)
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.opends.admin.ads.util;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
 * NOTE: this class is not aimed to be used when we have connections in
 * parallel.
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private X509TrustManager trustManager;
  private String lastRefusedAuthType;
  private X509Certificate[] lastRefusedChain;
  private Cause lastRefusedCause;
  private KeyStore keystore;

  /**
   * The following ArrayList contain information about the certificates
   * explicitly accepted by the user.
   */
  private ArrayList<X509Certificate[]> acceptedChains = new ArrayList<>();
  private ArrayList<String> acceptedAuthTypes = new ArrayList<>();
  private ArrayList<String> acceptedHosts = new ArrayList<>();

  private String host;


  /**
   * The default constructor.
   *
   * @param keystore The keystore to use for this trustmanager.
   */
  public ApplicationTrustManager(KeyStore keystore)
  {
    this.keystore = keystore;
    String userSpecifiedAlgo = System.getProperty("org.opends.admin.trustmanageralgo");
    String userSpecifiedProvider = System.getProperty("org.opends.admin.trustmanagerprovider");

    //Handle IBM specific cases if the user did not specify a algorithm and/or provider.
    if(userSpecifiedAlgo == null && Platform.isVendor("IBM"))
    {
      userSpecifiedAlgo = "IbmX509";
    }
    if(userSpecifiedProvider == null && Platform.isVendor("IBM"))
    {
      userSpecifiedProvider = "IBMJSSE2";
    }

    // Have some fallbacks to choose the provider and algorithm of the key manager.
    // First see if the user wanted to use something specific,
    // then try with the SunJSSE provider and SunX509 algorithm.
    // Finally,fallback to the default algorithm of the JVM.
    String[] preferredProvider =
        { userSpecifiedProvider, "SunJSSE", null, null };
    String[] preferredAlgo =
        { userSpecifiedAlgo, "SunX509", "SunX509",
          TrustManagerFactory.getDefaultAlgorithm() };

      for (int i=0; i<preferredProvider.length && trustManager == null; i++)
      {
        String provider = preferredProvider[i];
        String algo = preferredAlgo[i];
        if (algo == null)
        {
          continue;
        }
        try
        {
          TrustManagerFactory tmf = null;
          if (provider != null)
          {
            tmf = TrustManagerFactory.getInstance(algo, provider);
          }
          else
          {
            tmf = TrustManagerFactory.getInstance(algo);
          }
          tmf.init(keystore);
          for (TrustManager tm : tmf.getTrustManagers())
          {
            if (tm instanceof X509TrustManager)
            {
              trustManager = (X509TrustManager) tm;
              break;
            }
          }
        }
        catch (NoSuchProviderException e)
        {
          logger.warn(LocalizableMessage.raw("Error with the provider: "+provider, e));
        }
        catch (NoSuchAlgorithmException e)
        {
          logger.warn(LocalizableMessage.raw("Error with the algorithm: "+algo, e));
        }
        catch (KeyStoreException e)
        {
          logger.warn(LocalizableMessage.raw("Error with the keystore", e));
        }
      }
  }

  /** {@inheritDoc} */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
  throws CertificateException
  {
    boolean explicitlyAccepted = false;
    try
    {
      if (trustManager != null)
      {
        try
        {
          trustManager.checkClientTrusted(chain, authType);
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
      manageException(chain, authType, ce, Cause.NOT_TRUSTED);
    }

    if (!explicitlyAccepted)
    {
      try
      {
        verifyHostName(chain, authType);
      }
      catch (CertificateException ce)
      {
        manageException(chain, authType, ce, Cause.HOST_NAME_MISMATCH);
      }
    }
  }

  /** {@inheritDoc} */
  public void checkServerTrusted(X509Certificate[] chain,
      String authType) throws CertificateException
  {
    boolean explicitlyAccepted = false;
    try
    {
      if (trustManager != null)
      {
        try
        {
          trustManager.checkServerTrusted(chain, authType);
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
      manageException(chain, authType, ce, Cause.NOT_TRUSTED);
    }

    if (!explicitlyAccepted)
    {
      try
      {
        verifyHostName(chain, authType);
      }
      catch (CertificateException ce)
      {
        manageException(chain, authType, ce, Cause.HOST_NAME_MISMATCH);
      }
    }
  }

  private void manageException(final X509Certificate[] chain,
      final String authType, final CertificateException ce, final Cause cause)
      throws OpendsCertificateException
  {
    lastRefusedChain = chain;
    lastRefusedAuthType = authType;
    lastRefusedCause = cause;
    throw new OpendsCertificateException(chain, ce);
  }

  /** {@inheritDoc} */
  public X509Certificate[] getAcceptedIssuers()
  {
    if (trustManager != null)
    {
      return trustManager.getAcceptedIssuers();
    }
    return new X509Certificate[0];
  }

  /**
   * This method is called when the user accepted a certificate.
   * @param chain the certificate chain accepted by the user.
   * @param authType the authentication type.
   * @param host the host we tried to connect and that presented the certificate.
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
   * method is used if we want to verify the correspondence between the
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
    ApplicationTrustManager copy = new ApplicationTrustManager(keystore);
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
      throw new OpendsCertificateException(
          "Certificate not in list of accepted certificates", chain);
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
        Rdn rdn = dn.getRdn(dn.getRdns().size() - 1);
        String value = rdn.getValue().toString();
        matches = hostMatch(value, host);
        if (!matches)
        {
          logger.warn(LocalizableMessage.raw("Subject DN RDN value is: "+value+
              " and does not match host value: "+host));
          // Try with the accepted hosts names
          for (int i =0; i<acceptedHosts.size() && !matches; i++)
          {
            if (hostMatch(acceptedHosts.get(i), host))
            {
              X509Certificate[] current = acceptedChains.get(i);
              matches = current.length == chain.length;
              for (int j=0; j<chain.length && matches; j++)
              {
                matches = chain[j].equals(current[j]);
              }
            }
          }
        }
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw("Error parsing subject dn: "+
            chain[0].getSubjectX500Principal(), t));
      }

      if (!matches)
      {
        throw new OpendsCertificateException(
            "Hostname mismatch between host name " + host
                + " and subject DN: " + chain[0].getSubjectX500Principal(),
            chain);
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

  /**
   * Checks whether two host names match.  It accepts the use of wildcard in the
   * host name.
   * @param host1 the first host name.
   * @param host2 the second host name.
   * @return <CODE>true</CODE> if the host match and <CODE>false</CODE>
   * otherwise.
   */
  private boolean hostMatch(String host1, String host2)
  {
    if (host1 == null)
    {
      throw new IllegalArgumentException("The host1 parameter cannot be null");
    }
    if (host2 == null)
    {
      throw new IllegalArgumentException("The host2 parameter cannot be null");
    }
    String[] h1 = host1.split("\\.");
    String[] h2 = host2.split("\\.");

    boolean hostMatch = h1.length == h2.length;
    for (int i=0; i<h1.length && hostMatch; i++)
    {
      if (!"*".equals(h1[i]) && !"*".equals(h2[i]))
      {
        hostMatch = h1[i].equalsIgnoreCase(h2[i]);
      }
    }
    return hostMatch;
  }
}
