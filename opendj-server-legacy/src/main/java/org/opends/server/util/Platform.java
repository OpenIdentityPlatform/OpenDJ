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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2025 Wren Security.
 * Portions Copyright 2025 3A Systems LLC.
 */

package org.opends.server.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.forgerock.opendj.util.StaticUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.BigIntegers;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.util.Reject;

import static org.opends.messages.UtilityMessages.ERR_CERTMGR_ADD_CERT;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_ALIAS_ALREADY_EXISTS;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_ALIAS_INVALID;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_CERT_REPLIES_INVALID;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_DELETE_ALIAS;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_GEN_SELF_SIGNED_CERT;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_KEYSTORE_NONEXISTANT;
import static org.opends.messages.UtilityMessages.ERR_CERTMGR_TRUSTED_CERT;

/**
 * Provides a wrapper class that collects all of the JVM vendor and JDK version
 * specific code in a single place.
 */
public final class Platform
{

  private static final PlatformIMPL IMPL;

  /** The minimum java supported version. */
  public static final String JAVA_MINIMUM_VERSION_NUMBER = "8";

  static
  {
    if (Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) == null)
    {

    }

    IMPL = new DefaultPlatformIMPL();
  }

  /** Key size, key algorithm and signature algorithms used. */
  public static enum KeyType
  {
    /** RSA key algorithm with 2048 bits size and SHA256withRSA signing algorithm. */
    RSA("RSA", 2048, "SHA256withRSA"),

    /** Elliptic Curve key algorithm with 256 bits size and SHA256withECDSA signing algorithm. */
    EC("EC", 256, "SHA256withECDSA");

    /** Default key type used when none can be determined. */
    public final static KeyType DEFAULT = RSA;

    final String keyAlgorithm;
    final int keySize;
    final String signatureAlgorithm;

    private KeyType(String keyAlgorithm, int keySize, String signatureAlgorithm)
    {
      this.keySize = keySize;
      this.keyAlgorithm = keyAlgorithm;
      this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * Get a KeyType based on the alias name.
     *
     * @param alias
     *          certificate alias
     * @return KeyTpe deduced from the alias.
     */
    public static KeyType getTypeOrDefault(String alias)
    {
      try
      {
        return KeyType.valueOf(alias.substring(alias.lastIndexOf('-') + 1).toUpperCase());
      }
      catch (Exception e)
      {
        return KeyType.DEFAULT;
      }
    }
  }

  /**
   * Platform base class. Performs all of the certificate management functions.
   */
  private static abstract class PlatformIMPL
  {

    protected PlatformIMPL()
    {
    }

    private final void deleteAlias(KeyStore ks, String ksPath, String alias,
                                   char[] pwd) throws KeyStoreException
    {
      try
      {
        if (ks == null)
        {
          LocalizableMessage msg = ERR_CERTMGR_KEYSTORE_NONEXISTANT.get();
          throw new KeyStoreException(msg.toString());
        }
        ks.deleteEntry(alias);
        try (final FileOutputStream fs = new FileOutputStream(ksPath))
        {
          ks.store(fs, pwd);
        }
      }
      catch (Exception e)
      {
        throw new KeyStoreException(ERR_CERTMGR_DELETE_ALIAS.get(alias, e.getMessage()).toString(), e);
      }
    }

    private final void addCertificate(KeyStore ks, String ksType, String ksPath,
                                      String alias, char[] pwd, String certPath) throws KeyStoreException
    {
      try
      {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        if (ks == null)
        {
          ks = KeyStore.getInstance(ksType);
          ks.load(null, pwd);
        }
        // Do not support certificate replies.
        if (ks.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class))
        {
          LocalizableMessage msg = ERR_CERTMGR_CERT_REPLIES_INVALID.get(alias);
          throw new KeyStoreException(msg.toString());
        }
        else if (!ks.containsAlias(alias)
                || ks.entryInstanceOf(alias, KeyStore.TrustedCertificateEntry.class))
        {
          try (InputStream inStream = new FileInputStream(certPath)) {
            trustedCert(alias, cf, ks, inStream);
          }
        }
        else
        {
          LocalizableMessage msg = ERR_CERTMGR_ALIAS_INVALID.get(alias);
          throw new KeyStoreException(msg.toString());
        }
        try (FileOutputStream fileOutStream = new FileOutputStream(ksPath)) {
          ks.store(fileOutStream, pwd);
        }
      }
      catch (Exception e)
      {
        throw new KeyStoreException(ERR_CERTMGR_ADD_CERT.get(alias, e.getMessage()).toString(), e);
      }
    }

    private static final KeyStore generateSelfSignedCertificate(KeyStore ks,
                                                                String ksType, String ksPath, KeyType keyType, String alias, char[] pwd, String dn,
                                                                int validity) throws KeyStoreException
    {
      boolean isFips = StaticUtils.isFips();
      try
      {
        if(isFips)
        {
          Security.addProvider(new BouncyCastleFipsProvider());
        }
        if (ks == null)
        {
          ks = KeyStore.getInstance(ksType);
          ks.load(null, pwd);
        }
        else if (ks.containsAlias(alias))
        {
          LocalizableMessage msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
          throw new KeyStoreException(msg.toString());
        }

        KeyPair keyPair = newKeyPair(keyType);
        PrivateKey privateKey = keyPair.getPrivate();
        X500Name subject = new X500Name(dn);
        Certificate[] certificateChain = new Certificate[] {
                generateSelfCertificate(keyPair, keyType, subject, validity)
        };
        ks.setKeyEntry(alias, privateKey, pwd, certificateChain);
        try (FileOutputStream fileOutStream = new FileOutputStream(ksPath)) {
          ks.store(fileOutStream, pwd);
        }
        return ks;
      }
      catch (Exception e)
      {
        throw new KeyStoreException(ERR_CERTMGR_GEN_SELF_SIGNED_CERT.get(alias, e.getMessage()).toString(), e);
      }
      finally
      {
        if(!isFips)
        {
          Security.removeProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        }
      }
    }

    private static KeyPair newKeyPair(KeyType keyType) throws Exception
    {
      KeyPairGenerator generator = KeyPairGenerator.getInstance(keyType.keyAlgorithm, BouncyCastleFipsProvider.PROVIDER_NAME);
      generator.initialize(keyType.keySize);
      return generator.generateKeyPair();
    }

    private static Certificate generateSelfCertificate(KeyPair keyPair, KeyType keyType, X500Name subject, int days) throws Exception
    {
      BigInteger serial = BigIntegers.createRandomBigInteger(64, new SecureRandom());
      Instant now = Instant.now();
      Date notBeforeDate = Date.from(now);
      Date notAfterDate = Date.from(now.plus(days, ChronoUnit.DAYS));

      JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
              subject, serial, notBeforeDate, notAfterDate, subject, keyPair.getPublic()
      );
      ContentSigner signer = new JcaContentSignerBuilder(keyType.signatureAlgorithm)
              .setProvider(BouncyCastleFipsProvider.PROVIDER_NAME)
              .build(keyPair.getPrivate());
      X509CertificateHolder holder = builder.build(signer);
      JcaX509CertificateConverter converter = new JcaX509CertificateConverter()
              .setProvider(BouncyCastleFipsProvider.PROVIDER_NAME);


      return converter.getCertificate(holder);
    }

    /**
     * Generate a x509 certificate from the input stream. Verification is done
     * only if it is self-signed.
     */
    private void trustedCert(String alias, CertificateFactory cf, KeyStore ks,
                             InputStream in) throws KeyStoreException
    {
      try
      {
        if (ks.containsAlias(alias))
        {
          LocalizableMessage msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
          throw new KeyStoreException(msg.toString());
        }
        X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
        if (isSelfSigned(cert))
        {
          cert.verify(cert.getPublicKey());
        }
        ks.setCertificateEntry(alias, cert);
      }
      catch (Exception e)
      {
        throw new KeyStoreException(ERR_CERTMGR_TRUSTED_CERT.get(alias, e.getMessage()).toString(), e);
      }
    }

    /**
     * Check that the issuer and subject DNs match.
     */
    private boolean isSelfSigned(X509Certificate cert)
    {
      return cert.getSubjectDN().equals(cert.getIssuerDN());
    }
  }

  /** Prevent instantiation. */
  private Platform()
  {
  }

  /**
   * Add the certificate in the specified path to the provided keystore;
   * creating the keystore with the provided type and path if it doesn't exist.
   *
   * @param ks
   *          The keystore to add the certificate to, may be null if it doesn't
   *          exist.
   * @param ksType
   *          The type to use if the keystore is created.
   * @param ksPath
   *          The path to the keystore if it is created.
   * @param alias
   *          The alias to store the certificate under.
   * @param pwd
   *          The password to use in saving the certificate.
   * @param certPath
   *          The path to the file containing the certificate.
   * @throws KeyStoreException
   *           If an error occurred adding the certificate to the keystore.
   */
  public static void addCertificate(KeyStore ks, String ksType, String ksPath,
                                    String alias, char[] pwd, String certPath) throws KeyStoreException
  {
    IMPL.addCertificate(ks, ksType, ksPath, alias, pwd, certPath);
  }

  /**
   * Delete the specified alias from the provided keystore.
   *
   * @param ks
   *          The keystore to delete the alias from.
   * @param ksPath
   *          The path to the keystore.
   * @param alias
   *          The alias to use in the request generation.
   * @param pwd
   *          The keystore password to use.
   * @throws KeyStoreException
   *           If an error occurred deleting the alias.
   */
  public static void deleteAlias(KeyStore ks, String ksPath, String alias,
                                 char[] pwd) throws KeyStoreException
  {
    IMPL.deleteAlias(ks, ksPath, alias, pwd);
  }

  /**
   * Generate a self-signed certificate using the specified alias, dn string and
   * validity period. If the keystore does not exist, it will be created using
   * the specified keystore type and path.
   *
   * @param ks
   *          The keystore to save the certificate in. May be null if it does
   *          not exist.
   * @param keyType
   *          The keystore type to use if the keystore is created.
   * @param ksPath
   *          The path to the keystore if the keystore is created.
   * @param ksType
   *          Specify the key size, key algorithm and signature algorithms used.
   * @param alias
   *          The alias to store the certificate under.
   * @param pwd
   *          The password to us in saving the certificate.
   * @param dn
   *          The dn string used as the certificate subject.
   * @param validity
   *          The validity of the certificate in days.
   * @throws KeyStoreException
   *           If the self-signed certificate cannot be generated.
   */
  public static void generateSelfSignedCertificate(KeyStore ks, String ksType,
                                                   String ksPath, KeyType keyType, String alias, char[] pwd, String dn, int validity)
          throws KeyStoreException
  {
    PlatformIMPL.generateSelfSignedCertificate(ks, ksType, ksPath, keyType, alias, pwd, dn, validity);
  }

  /**
   * Default platform class.
   */
  private static class DefaultPlatformIMPL extends PlatformIMPL
  {
  }

  /**
   * Test if a platform java vendor property starts with the specified vendor
   * string.
   *
   * @param vendor
   *          The vendor to check for.
   * @return {@code true} if the java vendor starts with the specified vendor
   *         string.
   */
  public static boolean isVendor(String vendor)
  {
    String javaVendor = System.getProperty("java.vendor");
    return javaVendor.startsWith(vendor);
  }

  /**
   * Computes the number of replay/worker/cleaner threads based on the number of cpus in the system.
   * Allows for a multiplier to be specified and a minimum value to be returned if not enough processors
   * are present in the system.
   *
   * @param minimumValue at least this value should be returned.
   * @param cpuMultiplier the scaling multiplier of the number of threads to return
   * @return the number of threads based on the number of cpus in the system.
   * @throws IllegalArgumentException if {@code cpuMultiplier} is a non positive number
   */
  public static int computeNumberOfThreads(int minimumValue, float cpuMultiplier)
  {
    Reject.ifTrue(cpuMultiplier < 0, "Multiplier must be a positive number");
    return Math.max(minimumValue, (int)(Runtime.getRuntime().availableProcessors() * cpuMultiplier));
  }
}