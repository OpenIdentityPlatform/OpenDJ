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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.util;


import java.security.KeyStoreException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.opends.messages.Message;
import static org.opends.messages.UtilityMessages.*;

/**
 * Provides a wrapper class that collects all of the JVM vendor
 * and JDK version specific code in a single place.
 *
 */
public final class Platform {

   //Prefix that determines which security package to use.
    private static String pkgPrefix;

    //IBM security package doesn't appear to support PCKS10, this flags turns
    //off support for that.
    private static boolean certReqAllowed;

    //The two security package prefixes (IBM and SUN).
    private static final String IBM_SEC = "com.ibm.security";
    private static final String SUN_SEC = "sun.security";

    private static final PlatformIMPL IMPL;

    static {
     String vendor = System.getProperty("java.vendor");
     String ver = System.getProperty("java.version");

      if(vendor.startsWith("IBM"))
      {
        pkgPrefix = IBM_SEC;
        certReqAllowed = false;
        if(ver.startsWith("1.5"))
        {
          IMPL = new IBM5PlatformIMPL();
        }
        else
        {
          IMPL = new DefaultPlatformIMPL();
        }
      }
      else
      {
        pkgPrefix = SUN_SEC;
        certReqAllowed = true;
        if(ver.startsWith("1.5"))
        {
         IMPL = new Sun5PlatformIMPL();
        }
        else
        {
          IMPL = new DefaultPlatformIMPL();
        }
      }
    }

   /**
    * Platform base class. Performs all of the certificate management functions.
    */
    private abstract static class PlatformIMPL {

        //Key size, key algorithm and signature algorithms used.
        private static final  int KEY_SIZE = 1024;
        private static final String KEY_ALGORITHM = "rsa";
        private static final String SIG_ALGORITHM = "SHA1WithRSA";

        //Time values used in validity calculations.
        private static final int SEC_IN_DAY = 24 * 60 * 60;
        private static final int DEFAULT_VALIDITY = 90 * SEC_IN_DAY;

        //These two are used to build certificate request files.
        private static final String TMPFILE_PREFIX = "CertificateManager-";
        private static final String TMPFILE_EXT = ".csr";

        //Methods pulled from the classes.
        private static final String ENCODE_SIGN_METHOD = "encodeAndSign";
        private static final String GENERATE_METHOD = "generate";
        private static final String GET_PRIVATE_KEY_METHOD = "getPrivateKey";
        private static final String GET_SELFSIGNED_CERT_METHOD =
                                                          "getSelfCertificate";
        private static final String PRINT_METHOD = "print";

        //Classes needed to manage certificates.
        private static Class<?> certKeyGenClass, X500NameClass,
                                X500SignerClass, PKCS10Class;

        //Constructors for each of the above classes.
        private static Constructor<?> certKeyGenCons, X500NameCons,
                                      X500SignerCons, pkcs10Cons;

        static {
          String x509pkg = pkgPrefix + ".x509";
          String pkcs10Pkg = pkgPrefix + ".pkcs";
          String certAndKeyGen=  x509pkg + ".CertAndKeyGen";
          String X500Name =  x509pkg + ".X500Name";
          String X500Signer = x509pkg + ".X500Signer";
          try {
            certKeyGenClass = Class.forName(certAndKeyGen);
            X500NameClass = Class.forName(X500Name);
            X500SignerClass = Class.forName(X500Signer);
            if(certReqAllowed) {
              String pkcs10 = pkcs10Pkg + ".PKCS10";
              PKCS10Class = Class.forName(pkcs10);
              pkcs10Cons = PKCS10Class.getConstructor(PublicKey.class);
            }
            certKeyGenCons =
                    certKeyGenClass.getConstructor(String.class, String.class);
            X500NameCons = X500NameClass.getConstructor(String.class);
            X500SignerCons =
                 X500SignerClass.getConstructor(Signature.class, X500NameClass);
          } catch (ClassNotFoundException e) {
            Message msg = ERR_CERTMGR_CLASS_NOT_FOUND.get(e.getMessage());
            throw new ExceptionInInitializerError(msg.toString());
          } catch (SecurityException e) {
            Message msg = ERR_CERTMGR_SECURITY.get(e.getMessage());
            throw new ExceptionInInitializerError(msg.toString());
          } catch (NoSuchMethodException e) {
            Message msg = ERR_CERTMGR_NO_METHOD.get(e.getMessage());
            throw new ExceptionInInitializerError(msg.toString());
          }
        }

        protected PlatformIMPL() {}

        /**
         * Generate a certificate request. Note that this methods checks if
         * the certificate request generation is allowed and throws an
         * exception if it isn't supported. Some vendors JDKs aren't compatible
         * with Sun's certificate request generation classes so they aren't
         * supported.
         *
         * @param ks The keystore to use in the request creation.
         * @param ksType The keystore type.
         * @param ksPath The path to the keystore.
         * @param alias The alias to use in the request generation.
         * @param pwd The keystore password to use.
         * @param dn A dn string to use as the certificate subject.
         *
         * @return A file object pointing at the created certificate request.
         * @throws KeyStoreException If the certificate request failed.
         */
        public final File
        generateCertificateRequest(KeyStore ks, String ksType, String ksPath,
            String alias, char[] pwd, String dn) throws KeyStoreException {
          if(!certReqAllowed) {
            String vendor = System.getProperty("java.vendor");
            Message msg =
              ERR_CERTMGR_CERT_SIGN_REQ_NOT_SUPPORTED.get(vendor);
            throw new KeyStoreException(msg.toString());
          }
          KeyStore keyStore = generateSelfSignedCertificate(ks, ksType, ksPath,
                                            alias, pwd, dn, DEFAULT_VALIDITY);
          File csrFile;
          try {
            csrFile = File.createTempFile(TMPFILE_PREFIX, TMPFILE_EXT);
            csrFile.deleteOnExit();
            PrintStream printStream =
              new PrintStream(new FileOutputStream(csrFile.getAbsolutePath()));
            if(keyStore == null) {
              Message msg = ERR_CERTMGR_KEYSTORE_NONEXISTANT.get();
              throw new KeyStoreException(msg.toString());
            }
            PrivateKey privateKey = getPrivateKey(keyStore, alias, pwd);
            if(privateKey == null) {
              Message msg =  ERR_CERTMGR_PRIVATE_KEY.get(alias);
              throw new KeyStoreException(msg.toString());
            }
            Certificate cert = keyStore.getCertificate(alias);
            if(cert == null) {
              Message msg = ERR_CERTMGR_ALIAS_NO_CERTIFICATE.get(alias);
              throw new KeyStoreException(msg.toString());
            }
            Signature signature = Signature.getInstance(SIG_ALGORITHM);
            signature.initSign(privateKey);
            Object request = pkcs10Cons.newInstance(cert.getPublicKey());
            Object subject = X500NameCons.newInstance(dn);
            Object signer =
              X500SignerCons.newInstance(signature, subject);
            Method encodeAndSign =
              PKCS10Class.getMethod(ENCODE_SIGN_METHOD, X500SignerClass);
            Method print =
              PKCS10Class.getMethod(PRINT_METHOD, PrintStream.class);
            encodeAndSign.invoke(request, signer);
            print.invoke(request, printStream);
            printStream.close();
          } catch (Exception e) {
            Message msg = ERR_CERTMGR_CERT_REQUEST.get(alias,e.getMessage());
            throw new KeyStoreException(msg.toString());
          }
          return csrFile;
        }

        /**
         * Delete the specified alias from the specified keystore.
         *
         * @param ks The keystore to delete the alias from.
         * @param ksPath The path to the keystore.
         * @param alias The alias to use in the request generation.
         * @param pwd The keystore password to use.
         *
         * @throws KeyStoreException If an error occurred deleting the alias.
         */
        public final void deleteAlias(KeyStore ks, String ksPath,
            String alias, char[] pwd) throws KeyStoreException {
              try {
                  if(ks == null) {
                      Message msg = ERR_CERTMGR_KEYSTORE_NONEXISTANT.get();
                      throw new KeyStoreException(msg.toString());
                  }
                  ks.deleteEntry(alias);
                  FileOutputStream fs = new FileOutputStream(ksPath);
                  ks.store(fs, pwd);
                  fs.close();
              } catch (Exception e) {
                  Message msg =
                      ERR_CERTMGR_DELETE_ALIAS.get(alias,e.getMessage());
                  throw new KeyStoreException(msg.toString());
              }
        }

        /**
         * Add the certificate in the specified path to the specified keystore,
         * creating the keystore using the specified type and path if it the
         * keystore doesn't exist.
         *
         * @param ks The keystore to add the certificate to, may be null if it
         *           doesn't exist.
         * @param ksType The type to use if the keystore is created.
         * @param ksPath The path to the keystore if it is created.
         * @param alias The alias to store the certificate under.
         * @param pwd The password to use in saving the certificate.
         * @param certPath The path to the file containing the certificate.
         * @throws KeyStoreException If an error occurred adding the
         *                           certificate to the keystore.
         */
        public final void addCertificate(KeyStore ks, String ksType,
            String ksPath, String alias, char[] pwd, String certPath)
        throws KeyStoreException {
          try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            InputStream inStream = new FileInputStream(certPath);
            if(ks == null) {
              ks = KeyStore.getInstance(ksType);
              ks.load(null, pwd);
            }
            //Do not support certificate replies.
            if (ks.entryInstanceOf(alias ,KeyStore.PrivateKeyEntry.class)) {
              Message msg = ERR_CERTMGR_CERT_REPLIES_INVALID.get(alias);
              throw new KeyStoreException(msg.toString());
            } else if(!ks.containsAlias(alias) ||
                ks.entryInstanceOf(alias,
                    KeyStore.TrustedCertificateEntry.class))
              trustedCert(alias, cf, ks, inStream);
            else {
              Message msg = ERR_CERTMGR_ALIAS_INVALID.get(alias);
              throw new KeyStoreException(msg.toString());
            }
            FileOutputStream fileOutStream = new FileOutputStream(ksPath);
            ks.store(fileOutStream, pwd);
            fileOutStream.close();
            inStream.close();
          } catch (Exception e) {
            Message msg =
              ERR_CERTMGR_ADD_CERT.get(alias, e.getMessage());
            throw new KeyStoreException(msg.toString());
          }
        }

        /**
         * Generate a self-signed certificate using the specified alias, dn
         * string and validity period. If the keystore does not exist, create it
         * using the specified type and path.
         *
         * @param ks The keystore to save the certificate in. May be null if it
         *           does not exist.
         * @param ksType The keystore type to use if the keystore is created.
         * @param ksPath The path to the keystore if the keystore is created.
         * @param alias The alias to store the certificate under.
         * @param pwd The password to us in saving the certificate.
         * @param dn The dn string used as the certificate subject.
         * @param validity The validity of the certificate in days.
         * @return The keystore that the self-signed certificate was stored in.
         *
         * @throws KeyStoreException If the self-signed certificate cannot be
         *                           generated.
         */
        public final
        KeyStore generateSelfSignedCertificate(KeyStore ks, String ksType,
            String ksPath, String alias, char[] pwd, String dn, int validity)
        throws KeyStoreException {
          try {
            if(ks == null) {
              ks = KeyStore.getInstance(ksType);
              ks.load(null, pwd);
            } else if(ks.containsAlias(alias)) {
              Message msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
              throw new KeyStoreException(msg.toString());
            }
            Object keypair =
              certKeyGenCons.newInstance(KEY_ALGORITHM, SIG_ALGORITHM);
            Object subject = X500NameCons.newInstance(dn);
            Method certAndKeyGenGenerate =
              certKeyGenClass.getMethod(GENERATE_METHOD, int.class);
            certAndKeyGenGenerate.invoke(keypair, KEY_SIZE);
            Method certAndKeyGetPrivateKey =
              certKeyGenClass.getMethod(GET_PRIVATE_KEY_METHOD);
            PrivateKey privatevKey =
              (PrivateKey) certAndKeyGetPrivateKey.invoke(keypair);
            Certificate[] certificateChain = new Certificate[1];
            Method getSelfCertificate =
              certKeyGenClass.getMethod(GET_SELFSIGNED_CERT_METHOD,
                                        X500NameClass,long.class);
            int days = validity * SEC_IN_DAY;
            certificateChain[0] =
              (Certificate) getSelfCertificate.invoke(keypair, subject, days);
            ks.setKeyEntry(alias, privatevKey, pwd, certificateChain);
            FileOutputStream fileOutStream = new FileOutputStream(ksPath);
            ks.store(fileOutStream, pwd);
            fileOutStream.close();
          } catch (Exception e) {
            Message msg =
                   ERR_CERTMGR_GEN_SELF_SIGNED_CERT.get(alias, e.getMessage());
            throw new KeyStoreException(msg.toString());
          }
          return ks;
        }

        /**
         * Generate a x509 certificate from the input stream. Verification is
         * done only if it is self-signed.
         *
         * @param alias The alias to save the certificate under.
         * @param cf The x509 certificate factory.
         * @param ks The keystore to add the certificate in.
         * @param in The input stream to read the certificate from.
         * @throws KeyStoreException If the alias exists already in the
         *         keystore, if the self-signed certificate didn't verify, or
         *         the certificate could not be stored.
         */
        private void trustedCert(String alias, CertificateFactory cf,
             KeyStore ks, InputStream in) throws KeyStoreException {
          try {
            if (ks.containsAlias(alias) == true) {
              Message msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
              throw new KeyStoreException(msg.toString());
            }
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            if (isSelfSigned(cert))
              cert.verify(cert.getPublicKey());
            ks.setCertificateEntry(alias, cert);
          } catch (Exception e) {
            Message msg =
              ERR_CERTMGR_TRUSTED_CERT.get(alias,e.getMessage());
            throw new KeyStoreException(msg.toString());
          }
        }

        /**
         * Check that the issuer and subject DNs match.
         *
         * @param cert The certificate to examine.
         * @return {@code true} if the certificate is self-signed.
         */
        private boolean isSelfSigned(X509Certificate cert) {
          return cert.getSubjectDN().equals(cert.getIssuerDN());
        }

        /**
         * Returns the private key associated with specified alias and keystore.
         * The keystore was already checked for existance.
         *
         * @param ks The keystore to get the private key from, it must exist.
         * @param alias The alias to get the private key of.
         * @param pwd The password used to get the key from the keystore.
         * @return The private key of related to the alias.
         *
         * @throws KeyStoreException If the alias is not in the keystore, the
         *    entry related to the alias is not of
         */
        private PrivateKey getPrivateKey(KeyStore ks, String alias, char[] pwd)
        throws KeyStoreException  {
            PrivateKey key = null;
            try {
                if(!ks.containsAlias(alias)) {
                    Message msg = ERR_CERTMGR_ALIAS_DOES_NOT_EXIST.get(alias);
                    throw new KeyStoreException(msg.toString());
                }
                if(!ks.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class) &&
                  !ks.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
                    Message msg =
                                ERR_CERTMGR_ALIAS_INVALID_ENTRY_TYPE.get(alias);
                    throw new KeyStoreException(msg.toString());
                }
                key = (PrivateKey)ks.getKey(alias, pwd);
            } catch (Exception  e) {
                Message msg =
                    ERR_CERTMGR_GET_KEY.get(alias,e.getMessage());
                throw new KeyStoreException(msg.toString());
            }
            return key;
        }

        /**
         * Normalize the data in the specified buffer.
         *
         * @param buffer The buffer to normalize.
         */
         public abstract void normalize(StringBuilder buffer);
    }

    //Prevent instantiation.
    private Platform() {}

    /**
     * Add the certificate in the specified path to the provided keystore;
     * creating the keystore with the provided type and path if it doesn't
     * exist.
     *
     * @param ks The keystore to add the certificate to, may be null if it
     *           doesn't exist.
     * @param ksType The type to use if the keystore is created.
     * @param ksPath The path to the keystore if it is created.
     * @param alias The alias to store the certificate under.
     * @param pwd The password to use in saving the certificate.
     * @param certPath The path to the file containing the certificate.
     *
     * @throws KeyStoreException If an error occurred adding the
     *                           certificate to the keystore.
     */
    public static void addCertificate(KeyStore ks, String ksType, String ksPath,
        String alias, char[] pwd, String certPath) throws KeyStoreException {
        IMPL.addCertificate(ks,ksType, ksPath, alias, pwd, certPath);
    }


    /**
     * Delete the specified alias from the provided keystore.
     *
     * @param ks The keystore to delete the alias from.
     * @param ksPath The path to the keystore.
     * @param alias The alias to use in the request generation.
     * @param pwd The keystore password to use.
     *
     * @throws KeyStoreException If an error occurred deleting the alias.
     */
    public static void deleteAlias(KeyStore ks, String ksPath, String alias,
        char[] pwd) throws KeyStoreException {
        IMPL.deleteAlias(ks, ksPath, alias, pwd);
    }


    /**
     * Generate a certificate request using the specified parameters.
     *
     * @param ks The keystore to use in the request creation.
     * @param ksType The keystore type.
     * @param ksPath The path to the keystore.
     * @param alias The alias to use in the request generation.
     * @param pwd The keystore password to use.
     * @param dn A dn string to use as the certificate subject.
     * @return A file object pointing at the created certificate request.
     *
     * @throws KeyStoreException If the certificate request failed.
     */
    public static File generateCertificateRequest(KeyStore ks, String ksType,
        String ksPath, String alias, char[] pwd, String dn)
    throws KeyStoreException {
        return IMPL.generateCertificateRequest(ks, ksType, ksPath, alias,
                                               pwd, dn);
    }


    /**
     * Generate a self-signed certificate using the specified alias, dn
     * string and validity period. If the keystore does not exist, it will be
     * created using the specified keystore type and path.
     *
     * @param ks The keystore to save the certificate in. May be null if it
     *           does not exist.
     * @param ksType The keystore type to use if the keystore is created.
     * @param ksPath The path to the keystore if the keystore is created.
     * @param alias The alias to store the certificate under.
     * @param pwd The password to us in saving the certificate.
     * @param dn The dn string used as the certificate subject.
     * @param validity The validity of the certificate in days.
     *
     * @throws KeyStoreException If the self-signed certificate cannot be
     *                           generated.
     */
    public static void generateSelfSignedCertificate(KeyStore ks, String ksType,
        String ksPath, String alias, char[] pwd, String dn, int validity)
    throws KeyStoreException {
        IMPL.generateSelfSignedCertificate(ks, ksType, ksPath, alias, pwd, dn,
                                      validity);
    }



    /**
     * Sun 5 JDK platform class.
     */
    private static class Sun5PlatformIMPL extends PlatformIMPL {
       //normalize method.
      private static final Method NORMALIZE;
      //Normalized form method.
      private static final Object FORM_NFKC;

      static {
        Method normalize = null;
        Object formNFKC = null;
        try {
          Class<?> normalizer = Class.forName("sun.text.Normalizer");
          formNFKC = normalizer.getField("DECOMP_COMPAT").get(null);
          Class<?> normalizerForm = Class.forName("sun.text.Normalizer$Mode");
          normalize = normalizer.getMethod("normalize", String.class,
                 normalizerForm, Integer.TYPE);
        }
        catch (Exception ex) {
        // Do not use Normalizer. The values are already set to null.
        }
      NORMALIZE = normalize;
      FORM_NFKC = formNFKC;
     }


      @Override
      public void normalize(StringBuilder buffer) {
        try {
          String normal =
               (String) NORMALIZE.invoke(null, buffer.toString(), FORM_NFKC,0);
          buffer.replace(0,buffer.length(),normal);
        }
        catch(Exception ex) {
          //Don't do anything. buffer should be used.
        }
      }
   }

    /**
     * Default platform class.
     */
     private static class DefaultPlatformIMPL extends PlatformIMPL {
       //normalize method.
      private static final Method NORMALIZE;
      //Normalized form method.
      private static final Object FORM_NFKC;

      static {

        Method normalize = null;
        Object formNFKC = null;
        try {
          Class<?> normalizer = Class.forName("java.text.Normalizer");
          Class<?> normalizerForm = Class.forName("java.text.Normalizer$Form");
          normalize = normalizer.getMethod("normalize", CharSequence.class,
                normalizerForm);
          formNFKC = normalizerForm.getField("NFKD").get(null);
        }
        catch (Exception ex) {
        // Do not use Normalizer. The values are already set to null.
        }
        NORMALIZE = normalize;
        FORM_NFKC = formNFKC;
     }


      @Override
      public void normalize(StringBuilder buffer) {
        try {
          String normal = (String) NORMALIZE.invoke(null, buffer, FORM_NFKC);
          buffer.replace(0,buffer.length(),normal);
        }
        catch(Exception ex) {
          //Don't do anything. buffer should be used.
        }
      }
   }

   /**
    * IBM JDK 5 platform class.
    */
   private static class IBM5PlatformIMPL extends PlatformIMPL {

    @Override
    public void normalize(StringBuilder buffer) {
      //No implementation.
    }
   }

   /**
    * Normalize the specified buffer.
    *
    * @param buffer The buffer to normalize.
    */
   public static void normalize(StringBuilder buffer) {
     IMPL.normalize(buffer);
   }

   /**
    * Test if a platform java vendor property starts with the specified
    * vendor string.
    *
    * @param vendor The vendor to check for.
    * @return {@code true} if the java vendor starts with the specified vendor
    *         string.
    */
   public static boolean isVendor(String vendor) {
     String javaVendor = System.getProperty("java.vendor");
     return javaVendor.startsWith(vendor);
   }
}

