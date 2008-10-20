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
package org.opends.quicksetup;

/**
 * Class used to describe the Security Options specified by the user.
 *
 */
public class SecurityOptions
{
  private boolean enableSSL;
  private boolean enableStartTLS;

  private int sslPort = 636;

  /**
   * The different type of security options that we can have.
   */
  public enum CertificateType
  {
    /**
     * No certificate to be used (and so no SSL and no Start TLS).
     */
    NO_CERTIFICATE,
    /**
     * Use a newly created Self Signed Certificate.
     */
    SELF_SIGNED_CERTIFICATE,
    /**
     * Use an existing JKS keystore.
     */
    JKS,
    /**
     * Use an existing JCEKS keystore.
     */
    JCEKS,
    /**
     * Use an existing PKCS#11 keystore.
     */
    PKCS11,
    /**
     * Use an existing PKCS#12 keystore.
     */
    PKCS12
  }

  private CertificateType certificateType;
  private String keyStorePath;
  private String keyStorePassword;
  private String aliasToUse;

  private SecurityOptions()
  {
  }

  /**
   * Creates a new instance of a SecurityOptions representing for no certificate
   * (no SSL or Start TLS).
   * @return a new instance of a SecurityOptions representing for no certificate
   * (no SSL or Start TLS).
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
   * @param enableSSL whether SSL is enabled or not.
   * @param enableStartTLS whether Start TLS is enabled or not.
   * @param sslPort the value of the LDAPS port.
   * @return a new instance of a SecurityOptions using a self-signed
   * certificate.
   */
  public static SecurityOptions createSelfSignedCertificateOptions(
      boolean enableSSL, boolean enableStartTLS, int sslPort)
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.SELF_SIGNED_CERTIFICATE);
    updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort, null);
    return ops;
  }

  /**
   * Creates a new instance of a SecurityOptions using a Java Key Store.
   * @param keystorePath the path of the key store.
   * @param keystorePwd the password of the key store.
   * @param enableSSL whether SSL is enabled or not.
   * @param enableStartTLS whether Start TLS is enabled or not.
   * @param sslPort the value of the LDAPS port.
   * @param aliasToUse the alias of the certificate in the keystore to be used.
   * @return a new instance of a SecurityOptions using a Java Key Store.
   */
  public static SecurityOptions createJKSCertificateOptions(String keystorePath,
      String keystorePwd, boolean enableSSL, boolean enableStartTLS,
      int sslPort, String aliasToUse)
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.JKS);
    ops.setKeyStorePath(keystorePath);
    ops.setKeyStorePassword(keystorePwd);
    updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort,
        aliasToUse);
    return ops;
  }

  /**
   * Creates a new instance of a SecurityOptions using a JCE Key Store.
   * @param keystorePath the path of the key store.
   * @param keystorePwd the password of the key store.
   * @param enableSSL whether SSL is enabled or not.
   * @param enableStartTLS whether Start TLS is enabled or not.
   * @param sslPort the value of the LDAPS port.
   * @param aliasToUse the alias of the certificate in the keystore to be used.
   * @return a new instance of a SecurityOptions using a JCE Key Store.
   */
  public static SecurityOptions createJCEKSCertificateOptions(
      String keystorePath,
      String keystorePwd, boolean enableSSL, boolean enableStartTLS,
      int sslPort, String aliasToUse)
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.JCEKS);
    ops.setKeyStorePath(keystorePath);
    ops.setKeyStorePassword(keystorePwd);
    updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort,
        aliasToUse);
    return ops;
  }


  /**
   * Creates a new instance of a SecurityOptions using a PKCS#11 Key Store.
   * @param keystorePwd the password of the key store.
   * @param enableSSL whether SSL is enabled or not.
   * @param enableStartTLS whether Start TLS is enabled or not.
   * @param sslPort the value of the LDAPS port.
   * @param aliasToUse the alias of the certificate in the keystore to be used.
   * @return a new instance of a SecurityOptions using a PKCS#11 Key Store.
   */
  public static SecurityOptions createPKCS11CertificateOptions(
      String keystorePwd, boolean enableSSL, boolean enableStartTLS,
      int sslPort, String aliasToUse)
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.PKCS11);
    ops.setKeyStorePassword(keystorePwd);
    updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort,
        aliasToUse);
    return ops;
  }

  /**
   * Creates a new instance of a SecurityOptions using a PKCS#12 Key Store.
   * @param keystorePath the path of the key store.
   * @param keystorePwd the password of the key store.
   * @param enableSSL whether SSL is enabled or not.
   * @param enableStartTLS whether Start TLS is enabled or not.
   * @param sslPort the value of the LDAPS port.
   * @param aliasToUse the alias of the certificate in the keystore to be used.
   * @return a new instance of a SecurityOptions using a PKCS#12 Key Store.
   */
  public static SecurityOptions createPKCS12CertificateOptions(
      String keystorePath, String keystorePwd, boolean enableSSL,
      boolean enableStartTLS, int sslPort, String aliasToUse)
  {
    SecurityOptions ops = new SecurityOptions();
    ops.setCertificateType(CertificateType.PKCS12);
    ops.setKeyStorePath(keystorePath);
    ops.setKeyStorePassword(keystorePwd);
    updateCertificateOptions(ops, enableSSL, enableStartTLS, sslPort,
        aliasToUse);
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
      boolean enableSSL, boolean enableStartTLS, int sslPort, String aliasToUse)
  {
    if (!enableSSL && !enableStartTLS)
    {
      throw new IllegalArgumentException(
          "You must enable SSL or StartTLS to use a certificate.");
    }
    ops.setEnableSSL(enableSSL);
    ops.setEnableStartTLS(enableStartTLS);
    ops.setSslPort(sslPort);
    ops.setAliasToUse(aliasToUse);
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
   * Returns the alias of the certificate in the keystore to be used.
   * @return the alias of the certificate in the keystore to be used.
   */
  public String getAliasToUse()
  {
    return aliasToUse;
  }

  /**
   * Sets the certificate alias name.
   * @param aliasToUse the certificate alias name.
   */
  void setAliasToUse(String aliasToUse)
  {
    this.aliasToUse = aliasToUse;
  }
}
