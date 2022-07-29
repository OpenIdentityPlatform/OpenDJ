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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class used to describe the Security Options specified by the user. */
public class SecurityOptions
{
  private boolean enableSSL;
  private boolean enableStartTLS;

  private int sslPort = 636;

  /** Alias of a self-signed certificate. */
  public static final String SELF_SIGNED_CERT_ALIAS = "server-cert";
  /** Alias of a self-signed certificate using elliptic curve. */
  public static final String SELF_SIGNED_EC_CERT_ALIAS = SELF_SIGNED_CERT_ALIAS + "-ec";

  /** The different type of security options that we can have. */
  public enum CertificateType
  {
    /** No certificate to be used (and so no SSL and no Start TLS). */
    NO_CERTIFICATE,
    /** Use a newly created Self Signed Certificate. */
    SELF_SIGNED_CERTIFICATE,
    /** Use an existing JKS key store. */
    JKS,
    /** Use an existing JCEKS key store. */
    JCEKS,
    /** Use an existing PKCS#11 key store. */
    PKCS11,
    /** Use an existing PKCS#12 key store. */
    PKCS12
  }

  private CertificateType certificateType;
  private String keyStorePath;
  private String keyStorePassword;
  private final Set<String> aliasesToUse = new TreeSet<>();

  private SecurityOptions()
  {
  }

  /**
   * Creates a new instance of a SecurityOptions representing for no certificate
   * (no SSL or Start TLS).
   *
   * @return a new instance of a SecurityOptions representing for no certificate
   *         (no SSL or Start TLS).
   */
  public static SecurityOptions createNoCertificateOptions()
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.NO_CERTIFICATE);
    ops.setEnableSSL(false);
    ops.setEnableStartTLS(false);
    return ops;
  }

  /**
   * Creates a new instance of a SecurityOptions using a self-signed
   * certificate.
   *
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @return a new instance of a SecurityOptions using a self-signed
   *         certificate.
   */
  public static SecurityOptions createSelfSignedCertificateOptions(
          boolean enableSSL, boolean enableStartTLS, int sslPort)
  {
    return createSelfSignedCertificateOptions(enableSSL, enableStartTLS, sslPort,
        Arrays.asList(SELF_SIGNED_CERT_ALIAS));
  }

  /**
   * Creates a new instance of a SecurityOptions using a self-signed
   * certificate.
   *
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @param aliasesToUse
   *          the aliases of the certificates in the key store to be used.
   * @return a new instance of a SecurityOptions using a self-signed
   *         certificate.
   */
  private static SecurityOptions createSelfSignedCertificateOptions(boolean enableSSL, boolean enableStartTLS,
      int sslPort, Collection<String> aliasesToUse)
  {
      return createOptionsForCertificatType(
              CertificateType.SELF_SIGNED_CERTIFICATE, null, null, enableSSL, enableStartTLS, sslPort, aliasesToUse);
  }

  /**
   * Creates a new instance of a SecurityOptions using a Java Key Store.
   *
   * @param keystorePath
   *          the path of the key store.
   * @param keystorePwd
   *          the password of the key store.
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @param aliasesToUse
   *          the aliases of the certificates in the key store to be used.
   * @return a new instance of a SecurityOptions using a Java Key Store.
   */
  public static SecurityOptions createJKSCertificateOptions(String keystorePath, String keystorePwd, boolean enableSSL,
      boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
    return createOptionsForCertificatType(
            CertificateType.JKS, keystorePath, keystorePwd, enableSSL, enableStartTLS, sslPort, aliasesToUse);
  }

  /**
   * Creates a new instance of a SecurityOptions using a JCE Key Store.
   *
   * @param keystorePath
   *          the path of the key store.
   * @param keystorePwd
   *          the password of the key store.
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @param aliasesToUse
   *          the aliases of the certificates in the keystore to be used.
   * @return a new instance of a SecurityOptions using a JCE Key Store.
   */
  public static SecurityOptions createJCEKSCertificateOptions(String keystorePath, String keystorePwd,
      boolean enableSSL, boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
    return createOptionsForCertificatType(
            CertificateType.JCEKS, keystorePath, keystorePwd, enableSSL, enableStartTLS, sslPort, aliasesToUse);
  }


  /**
   * Creates a new instance of a SecurityOptions using a PKCS#11 Key Store.
   *
   * @param keystorePwd
   *          the password of the key store.
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @param aliasesToUse
   *          the aliases of the certificates in the keystore to be used.
   * @return a new instance of a SecurityOptions using a PKCS#11 Key Store.
   */
  public static SecurityOptions createPKCS11CertificateOptions(String keystorePwd, boolean enableSSL,
      boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
    return createOptionsForCertificatType(
            CertificateType.PKCS11, null, keystorePwd, enableSSL, enableStartTLS, sslPort, aliasesToUse);
  }

  /**
   * Creates a new instance of a SecurityOptions using a PKCS#12 Key Store.
   *
   * @param keystorePath
   *          the path of the key store.
   * @param keystorePwd
   *          the password of the key store.
   * @param enableSSL
   *          whether SSL is enabled or not.
   * @param enableStartTLS
   *          whether Start TLS is enabled or not.
   * @param sslPort
   *          the value of the LDAPS port.
   * @param aliasesToUse
   *          the aliases of the certificates in the keystore to be used.
   * @return a new instance of a SecurityOptions using a PKCS#12 Key Store.
   */
  public static SecurityOptions createPKCS12CertificateOptions( String keystorePath, String keystorePwd,
          boolean enableSSL, boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
    return createOptionsForCertificatType(
            CertificateType.PKCS12, keystorePath, keystorePwd, enableSSL, enableStartTLS, sslPort, aliasesToUse);
  }

  /**
   * Creates a new instance of a SecurityOptions using the provided type Key
   * Store.
   *
   * @param certType
   *          The Key Store type.
   * @param keystorePath
   *          The path of the key store (may be @null).
   * @param keystorePwd
   *          The password of the key store.
   * @param enableSSL
   *          Whether SSL is enabled or not.
   * @param enableStartTLS
   *          Whether Start TLS is enabled or not.
   * @param sslPort
   *          The value of the LDAPS port.
   * @param aliasesToUse
   *          The aliases of the certificates in the keystore to be used.
   * @return a new instance of a SecurityOptions.
   */
  public static SecurityOptions createOptionsForCertificatType(CertificateType certType, String keystorePath,
      String keystorePwd, boolean enableSSL, boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
      if (certType == CertificateType.NO_CERTIFICATE)
      {
        return createNoCertificateOptions();
      }
      else if ( certType.equals(CertificateType.SELF_SIGNED_CERTIFICATE) && aliasesToUse.isEmpty() )
      {
        aliasesToUse = Arrays.asList(SELF_SIGNED_CERT_ALIAS);
      }

      SecurityOptions ops = new SecurityOptions();
      if (keystorePath != null)
      {
        ops.setKeyStorePath(keystorePath);
      }
      if (keystorePwd != null)
      {
        ops.setKeyStorePassword(keystorePwd);
      }
      ops.setCertificateType(certType);
      updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort, aliasesToUse);
      return ops;
  }

  /**
   * Returns the CertificateType for this instance.
   * @return the CertificateType for this instance.
   */
  public CertificateType getCertificateType()
  {
    return certificateType;
  }

  /**
   * Sets the CertificateType for this instance.
   * @param certificateType the CertificateType for this instance.
   */
  private void setCertificateType(CertificateType certificateType)
  {
    this.certificateType = certificateType;
  }

  /**
   * Returns whether SSL is enabled or not.
   * @return <CODE>true</CODE> if SSL is enabled and <CODE>false</CODE>
   * otherwise.
   */
  public boolean getEnableSSL()
  {
    return enableSSL;
  }

  /**
   * Sets whether SSL is enabled or not.
   * @param enableSSL whether SSL is enabled or not.
   */
  private void setEnableSSL(boolean enableSSL)
  {
    this.enableSSL = enableSSL;
  }

  /**
   * Returns whether StartTLS is enabled or not.
   * @return <CODE>true</CODE> if StartTLS is enabled and <CODE>false</CODE>
   * otherwise.
   */
  public boolean getEnableStartTLS()
  {
    return enableStartTLS;
  }

  /**
   * Sets whether StartTLS is enabled or not.
   * @param enableStartTLS whether StartTLS is enabled or not.
   */
  private void setEnableStartTLS(boolean enableStartTLS)
  {
    this.enableStartTLS = enableStartTLS;
  }

  /**
   * Returns the key store password.
   * @return the key store password.
   */
  public String getKeystorePassword()
  {
    return keyStorePassword;
  }

  /**
   * Sets the key store password.
   * @param keyStorePassword the new key store password.
   */
  private void setKeyStorePassword(String keyStorePassword)
  {
    this.keyStorePassword = keyStorePassword;
  }

  /**
   * Returns the key store path.
   * @return the key store path.
   */
  public String getKeystorePath()
  {
    return keyStorePath;
  }

  /**
   * Sets the key store path.
   * @param keyStorePath the new key store path.
   */
  private void setKeyStorePath(String keyStorePath)
  {
    this.keyStorePath = keyStorePath;
  }

  /**
   * Updates the provided certificate options object with some parameters.
   * @param ops the SecurityOptions object to be updated.
   * @param enableSSL whether to enable SSL or not.
   * @param enableStartTLS whether to enable StartTLS or not.
   * @param sslPort the LDAPS port number.
   * @param aliasToUse the name of the alias to be used.
   */
  private static void updateCertificateOptions(SecurityOptions ops,
      boolean enableSSL, boolean enableStartTLS, int sslPort, Collection<String> aliasesToUse)
  {
    if (!enableSSL && !enableStartTLS)
    {
      throw new IllegalArgumentException(
          "You must enable SSL or StartTLS to use a certificate.");
    }
    ops.setEnableSSL(enableSSL);
    ops.setEnableStartTLS(enableStartTLS);
    ops.setSslPort(sslPort);
    ops.setAliasToUse(aliasesToUse);
  }

  /**
   * Returns the SSL port.
   * @return the SSL port.
   */
  public int getSslPort()
  {
    return sslPort;
  }

  /**
   * Sets the SSL port.
   * @param sslPort the new SSL port.
   */
  void setSslPort(int sslPort)
  {
    this.sslPort = sslPort;
  }

  /**
   * Returns the alias of the certificate in the key store to be used.
   * @return the alias of the certificate in the key store to be used.
   */
  public Set<String> getAliasesToUse()
  {
    return aliasesToUse;
  }

  /**
   * Sets the certificates aliases name.
   * @param aliasesToUse the certificates aliases name.
   */
  private void setAliasToUse(Collection<String> aliasesToUse)
  {
    this.aliasesToUse.clear();
    this.aliasesToUse.addAll(aliasesToUse);
  }

}
