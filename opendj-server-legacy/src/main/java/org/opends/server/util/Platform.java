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
 */

package org.opends.server.util;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.util.Reject;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.CERTANDKEYGEN_PROVIDER;

/**
 * Provides a wrapper class that collects all of the JVM vendor and JDK version
 * specific code in a single place.
 */
public final class Platform
{

  /** Prefix that determines which security package to use. */
  private static final String pkgPrefix;

  /** The two security package prefixes (IBM and SUN). */
  private static final String IBM_SEC = "com.ibm.security";
  private static final String SUN_SEC = "sun.security";

  /** The CertAndKeyGen class is located in different packages depending on JVM environment. */
  private static final String[] CERTANDKEYGEN_PATHS = new String[] {
      "sun.security.x509.CertAndKeyGen",          // Oracle/Sun/OpenJDK 6,7
      "sun.security.tools.keytool.CertAndKeyGen", // Oracle/Sun/OpenJDK 8
      "com.ibm.security.x509.CertAndKeyGen",      // IBM SDK 7
      "com.ibm.security.tools.CertAndKeyGen"      // IBM SDK 8
    };

  private static final PlatformIMPL IMPL;

  /** The minimum java supported version. */
  public static final String JAVA_MINIMUM_VERSION_NUMBER = "7.0";

  static
  {
    String vendor = System.getProperty("java.vendor");

    if (vendor.startsWith("IBM"))
    {
      pkgPrefix = IBM_SEC;
    }
    else
    {
      pkgPrefix = SUN_SEC;
    }
    IMPL = new DefaultPlatformIMPL();
  }

  /** Key size, key algorithm and signature algorithms used. */
  public static enum KeyType
  {
    /** RSA key algorithm with 2048 bits size and SHA1withRSA signing algorithm. */
    RSA("rsa", 2048, "SHA1WithRSA"),

    /** Elliptic Curve key algorithm with 233 bits size and SHA1withECDSA signing algorithm. */
    EC("ec", 256, "SHA1withECDSA");

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
     * Check whether this key type is supported by the current JVM.
     * @return true if this key type is supported, false otherwise.
     */
    public boolean isSupported()
    {
      try
      {
        return KeyPairGenerator.getInstance(keyAlgorithm.toUpperCase()) != null;
      }
      catch (NoSuchAlgorithmException e)
      {
        return false;
      }
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
    /** Time values used in validity calculations. */
    private static final int SEC_IN_DAY = 24 * 60 * 60;

    /** Methods pulled from the classes. */
    private static final String GENERATE_METHOD = "generate";
    private static final String GET_PRIVATE_KEY_METHOD = "getPrivateKey";
    private static final String GET_SELFSIGNED_CERT_METHOD =
      "getSelfCertificate";

    /** Classes needed to manage certificates. */
    private static final Class<?> certKeyGenClass, X500NameClass;

    /** Constructors for each of the above classes. */
    private static Constructor<?> certKeyGenCons, X500NameCons;

    /** Filesystem APIs */

    static
    {

      String certAndKeyGen = getCertAndKeyGenClassName();
      if(certAndKeyGen == null)
      {
        LocalizableMessage msg = ERR_CERTMGR_CERTGEN_NOT_FOUND.get(CERTANDKEYGEN_PROVIDER);
        throw new ExceptionInInitializerError(msg.toString());
      }

      String X500Name = pkgPrefix + ".x509.X500Name";
      try
      {
        certKeyGenClass = Class.forName(certAndKeyGen);
        X500NameClass = Class.forName(X500Name);
        certKeyGenCons = certKeyGenClass.getConstructor(String.class,
            String.class);
        X500NameCons = X500NameClass.getConstructor(String.class);
      }
      catch (ClassNotFoundException e)
      {
        LocalizableMessage msg = ERR_CERTMGR_CLASS_NOT_FOUND.get(e.getMessage());
        throw new ExceptionInInitializerError(msg.toString());
      }
      catch (SecurityException e)
      {
        LocalizableMessage msg = ERR_CERTMGR_SECURITY.get(e.getMessage());
        throw new ExceptionInInitializerError(msg.toString());
      }
      catch (NoSuchMethodException e)
      {
        LocalizableMessage msg = ERR_CERTMGR_NO_METHOD.get(e.getMessage());
        throw new ExceptionInInitializerError(msg.toString());
      }
    }

    /**
     * Try to decide which CertAndKeyGen class to use.
     *
     * @return a fully qualified class name or null
     */
    private static String getCertAndKeyGenClassName() {
      String certAndKeyGen = System.getProperty(CERTANDKEYGEN_PROVIDER);
      if (certAndKeyGen != null)
      {
        return certAndKeyGen;
      }

      for (String className : CERTANDKEYGEN_PATHS)
      {
        if (classExists(className))
        {
          return className;
        }
      }
      return null;
    }

    /**
     * A quick check to see if a class can be loaded. Doesn't check if
     * it can be instantiated.
     *
     * @param className full class name to check
     * @return true if the class is found
     */
    private static boolean classExists(final String className)
    {
      try {
        Class.forName(className);
        return true;
      } catch (ClassNotFoundException | ClassCastException e) {
        return false;
      }
    }

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
      try
      {
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

        final Object keypair = newKeyPair(keyType);
        final Object subject = newX500Name(dn);
        generate(keypair, keyType.keySize);
        final PrivateKey privateKey = getPrivateKey(keypair);
        final Certificate[] certificateChain = new Certificate[] {
          getSelfCertificate(keypair, subject, validity * SEC_IN_DAY)
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
    }

    private static Object newKeyPair(KeyType keyType) throws Exception
    {
      return certKeyGenCons.newInstance(keyType.keyAlgorithm, keyType.signatureAlgorithm);
    }

    private static Object newX500Name(String dn) throws Exception
    {
      return X500NameCons.newInstance(dn);
    }

    private static void generate(Object keypair, int keySize) throws Exception
    {
      Method certAndKeyGenGenerate = certKeyGenClass.getMethod(GENERATE_METHOD, int.class);
      certAndKeyGenGenerate.invoke(keypair, keySize);
    }

    private static PrivateKey getPrivateKey(Object keypair) throws Exception
    {
      Method certAndKeyGetPrivateKey = certKeyGenClass.getMethod(GET_PRIVATE_KEY_METHOD);
      return (PrivateKey) certAndKeyGetPrivateKey.invoke(keypair);
    }

    private static Certificate getSelfCertificate(Object keypair, Object subject, int days) throws Exception
    {
      Method getSelfCertificate = certKeyGenClass.getMethod(GET_SELFSIGNED_CERT_METHOD, X500NameClass, long.class);
      return (Certificate) getSelfCertificate.invoke(keypair, subject, days);
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
