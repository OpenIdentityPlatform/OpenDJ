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
 */

package org.opends.server.util;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import org.opends.messages.Message;
import static org.opends.messages.UtilityMessages.*;


/**
 * This class provides an interface for generating self-signed certificates and
 * certificate signing requests, and for importing, exporting, and deleting
 * certificates from a key store.  It supports JKS, PKCS11, and PKCS12 key store
 * types.
 * <BR><BR>
   This code uses the Platform class to perform all of the certificate
    management.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class CertificateManager {

  /**
   * The key store type value that should be used for the "JKS" key store.
   */
  public static final String KEY_STORE_TYPE_JKS = "JKS";

  /**
   * The key store type value that should be used for the "JCEKS" key store.
   */
  public static final String KEY_STORE_TYPE_JCEKS = "JCEKS";

  /**
   * The key store type value that should be used for the "PKCS11" key store.
   */
  public static final String KEY_STORE_TYPE_PKCS11 = "PKCS11";

  /**
   * The key store type value that should be used for the "PKCS12" key store.
   */
  public static final String KEY_STORE_TYPE_PKCS12 = "PKCS12";

  /**
   * The key store path value that must be used in conjunction with the PKCS11
   * key store type.
   */
  public static final String KEY_STORE_PATH_PKCS11 = "NONE";

  //Error message strings.
  private static final String KEYSTORE_PATH_MSG = "key store path";
  private static final String KEYSTORE_TYPE_MSG = "key store type";
  private static final String KEYSTORE_PWD_MSG = "key store password";
  private static final String SUBJECT_DN_MSG = "subject DN";
  private static final String CERT_ALIAS_MSG = "certificate alias";
  private static final String CERT_REQUEST_FILE_MSG =
                                                    "certificate request file";
  // The parsed key store backing this certificate manager.
  private KeyStore keyStore;

  // The path to the key store that we should be using.
  private final String keyStorePath;

  // The name of the key store type we are using.
  private final String keyStoreType;

  private final char[] password;

  /**
   * Always return true.
   *
   * @return  This always returns true;
   */
  public static boolean mayUseCertificateManager() {
    return true;
  }



  /**
   * Creates a new certificate manager instance with the provided information.
   *
   * @param  keyStorePath  The path to the key store file, or "NONE" if the key
   *                       store type is "PKCS11".  For the other key store
   *                       types, the file does not need to exist if a new
   *                       self-signed certificate or certificate signing
   *                       request is to be generated, although the directory
   *                       containing the file must exist.  The key store file
   *                       must exist if import or export operations are to be
   *                       performed.
   * @param  keyStoreType  The key store type to use.  It should be one of
   *                       {@code KEY_STORE_TYPE_JKS},
   *                       {@code KEY_STORE_TYPE_JCEKS},
   *                       {@code KEY_STORE_TYPE_PKCS11}, or
   *                       {@code KEY_STORE_TYPE_PKCS12}.
   * @param  keyStorePassword   The password required to access the key store.
   *                         It must not be {@code null}.
   * @throws IllegalArgumentException If an argument is invalid or {@code null}.
   *
   */
  public CertificateManager(String keyStorePath, String keyStoreType,
                            String keyStorePassword)
  throws IllegalArgumentException {
    ensureValid(keyStorePath, KEYSTORE_PATH_MSG);
    ensureValid(keyStoreType, KEYSTORE_TYPE_MSG);
    ensureValid(keyStorePassword, KEYSTORE_PWD_MSG);
    if (keyStoreType.equals(KEY_STORE_TYPE_PKCS11)) {
      if (! keyStorePath.equals(KEY_STORE_PATH_PKCS11)) {
        Message msg =
          ERR_CERTMGR_INVALID_PKCS11_PATH.get(KEY_STORE_PATH_PKCS11);
        throw new IllegalArgumentException(msg.toString());
      }
    } else if (keyStoreType.equals(KEY_STORE_TYPE_JKS) ||
        keyStoreType.equals(KEY_STORE_TYPE_JCEKS) ||
        keyStoreType.equals(KEY_STORE_TYPE_PKCS12)) {
      File keyStoreFile = new File(keyStorePath);
      if (keyStoreFile.exists()) {
        if (! keyStoreFile.isFile()) {
          Message msg = ERR_CERTMGR_INVALID_KEYSTORE_PATH.get(keyStorePath);
          throw new IllegalArgumentException(msg.toString());
        }
      } else {
        final File keyStoreDirectory = keyStoreFile.getParentFile();
        if ((keyStoreDirectory == null) || (! keyStoreDirectory.exists()) ||
            (! keyStoreDirectory.isDirectory())) {
          Message msg = ERR_CERTMGR_INVALID_PARENT.get(keyStorePath);
          throw new IllegalArgumentException(msg.toString());
        }
      }
    } else {
      Message msg =  ERR_CERTMGR_INVALID_STORETYPE.get(
          KEY_STORE_TYPE_JKS, KEY_STORE_TYPE_JCEKS,
          KEY_STORE_TYPE_PKCS11, KEY_STORE_TYPE_PKCS12);
      throw new IllegalArgumentException(msg.toString());
    }
    this.keyStorePath = keyStorePath;
    this.keyStoreType = keyStoreType;
    this.password  = keyStorePassword.toCharArray();
    keyStore = null;
  }



  /**
   * Indicates whether the provided alias is in use in the key store.
   *
   * @param  alias  The alias for which to make the determination.  It must not
   *                be {@code null} or empty.
   *
   * @return  {@code true} if the key store exist and already contains a
   *          certificate with the given alias, or {@code false} if not.
   *
   * @throws  KeyStoreException  If a problem occurs while attempting to
   *                             interact with the key store.
   */
  public boolean aliasInUse(final String alias)
  throws KeyStoreException {
    ensureValid(alias, CERT_ALIAS_MSG);
    KeyStore keyStore = getKeyStore();
    if (keyStore == null)
      return false;
    return keyStore.containsAlias(alias);
  }



  /**
   * Retrieves the aliases of the certificates in the specified key store.
   *
   * @return  The aliases of the certificates in the specified key store, or
   *          {@code null} if the key store does not exist.
   *
   * @throws  KeyStoreException  If a problem occurs while attempting to
   *                             interact with the key store.
   */
  public String[] getCertificateAliases() throws KeyStoreException {
    Enumeration<String> aliasEnumeration = null;
    KeyStore keyStore = getKeyStore();
    if (keyStore == null)
      return null;
    aliasEnumeration = keyStore.aliases();
    if (aliasEnumeration == null)
      return new String[0];
    ArrayList<String> aliasList = new ArrayList<String>();
    while (aliasEnumeration.hasMoreElements())
      aliasList.add(aliasEnumeration.nextElement());
    String[] aliases = new String[aliasList.size()];
    return aliasList.toArray(aliases);
  }



  /**
   * Retrieves the certificate with the specified alias from the key store.
   *
   * @param  alias  The alias of the certificate to retrieve.  It must not be
   *                {@code null} or empty.
   *
   * @return  The requested certificate, or {@code null} if the specified
   *          certificate does not exist.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store, or the key store does not exist..
   */
  public Certificate  getCertificate(String alias)
  throws KeyStoreException {
    ensureValid(alias, CERT_ALIAS_MSG);
    Certificate cert = null;
    KeyStore ks = getKeyStore();
    if (ks == null) {
      Message msg = ERR_CERTMGR_KEYSTORE_NONEXISTANT.get();
      throw new KeyStoreException(msg.toString());
    }
    cert = ks.getCertificate(alias);
    return cert;
  }


  /**
   * Generates a self-signed certificate using the provided information.
   *
   * @param  alias      The nickname to use for the certificate in the key
   *                    store.  For the server certificate, it should generally
   *                    be "server-cert".  It must not be {@code null} or empty.
   * @param  subjectDN  The subject DN to use for the certificate.  It must not
   *                    be {@code null} or empty.
   * @param  validity   The length of time in days that the certificate should
   *                    be valid, starting from the time the certificate is
   *                    generated.  It must be a positive integer value.
   * @throws  KeyStoreException  If a problem occurs while actually attempting
   *                             to generate the certificate in the key store.
   *@throws IllegalArgumentException If the validity parameter is not a
   *                                 positive integer, or the alias is already
   *                                 in the keystore.
   */
  public void generateSelfSignedCertificate(String alias, String subjectDN,
                                            int validity)
  throws KeyStoreException, IllegalArgumentException {
    ensureValid(alias, CERT_ALIAS_MSG);
    ensureValid(subjectDN, SUBJECT_DN_MSG);
    if (validity <= 0) {
      Message msg = ERR_CERTMGR_VALIDITY.get(validity);
      throw new IllegalArgumentException(msg.toString());
    }
    if (aliasInUse(alias)) {
      Message msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
      throw new IllegalArgumentException(msg.toString());
    }
    keyStore = null;
    Platform.generateSelfSignedCertificate(getKeyStore(), keyStoreType,
        keyStorePath, alias, password, subjectDN, validity);
  }

  /**
   * Generates a certificate signing request (CSR) using the provided
   * information.
   *
   * @param  alias      The nickname to use for the certificate in the key
   *                    store.  For the server certificate, it should generally
   *                    be "server-cert".  It must not be {@code null} or empty.
   * @param  subjectDN  The subject DN to use for the certificate.  It must not
   *                    be {@code null} or empty.
   *
   * @return  The file containing the generated certificate signing request.
   *
   * @throws  KeyStoreException  If a problem occurs while actually attempting
   *                             to generate the private key in the key store or
   *                             generate the certificate signing request based
   *                             on that key.
   *@throws IllegalArgumentException If the alias already exists in the
   *                                 keystore.
   */
  public File
  generateCertificateSigningRequest(final String alias, final String subjectDN)
  throws KeyStoreException, IllegalArgumentException {
    ensureValid(alias, CERT_ALIAS_MSG);
    ensureValid(subjectDN, SUBJECT_DN_MSG);
    if (aliasInUse(alias)) {
      Message msg = ERR_CERTMGR_ALIAS_ALREADY_EXISTS.get(alias);
      throw new IllegalArgumentException(msg.toString());
    }
    keyStore = null;
    return Platform.generateCertificateRequest(getKeyStore(), keyStoreType,
        keyStorePath, alias, password, subjectDN);
  }



  /**
   * Adds the provided certificate to the key store.  This may be used to
   * associate an externally-signed certificate with an existing private key
   * with the given alias.
   *
   * @param  alias            The alias to use for the certificate.  It must not
   *                          be {@code null} or empty.
   * @param  certificateFile  The file containing the encoded certificate.  It
   *                          must not be {@code null}, and the file must exist.

   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store.
   *
   *@throws IllegalArgumentException If the certificate file is not valid.
   */
  public void addCertificate(String alias, File certificateFile)
  throws  KeyStoreException, IllegalArgumentException {
    ensureValid(alias, CERT_ALIAS_MSG);
    ensureFileValid(certificateFile, CERT_REQUEST_FILE_MSG);
    if ((! certificateFile.exists()) ||
        (! certificateFile.isFile())) {
      Message msg = ERR_CERTMGR_INVALID_CERT_FILE.get(
          certificateFile.getAbsolutePath());
      throw new IllegalArgumentException(msg.toString());
    }
    keyStore = null;
    Platform.addCertificate(getKeyStore(), keyStoreType, keyStorePath, alias,
        password, certificateFile.getAbsolutePath());
  }


  /**
   * Removes the specified certificate from the key store.
   *
   * @param  alias  The alias to use for the certificate to remove.  It must not
   *                be {@code null} or an empty string, and it must exist in
   *                the key store.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store.
   *@throws IllegalArgumentException If the alias is in use and cannot be
   *                                 deleted.
   */
  public void removeCertificate(String alias)
  throws KeyStoreException, IllegalArgumentException {
    ensureValid(alias, CERT_ALIAS_MSG);
    if (!aliasInUse(alias)) {
      Message msg = ERR_CERTMGR_ALIAS_CAN_NOT_DELETE.get(alias);
      throw new IllegalArgumentException(msg.toString());
    }
    keyStore = null;
    Platform.deleteAlias(getKeyStore(), keyStorePath, alias, password);
  }


  /**
   * Retrieves a handle to the key store.
   *
   * @return  The handle to the key store, or {@code null} if the key store
   *          doesn't exist.
   *
   * @throws  KeyStoreException  If a problem occurs while trying to open the
   *                             key store.
   */
  private KeyStore getKeyStore()
  throws KeyStoreException
  {
      if (keyStore != null)
      {
          return keyStore;
      }

      // For JKS and PKCS12 key stores, we should make sure the file exists, and
      // we'll need an input stream that we can use to read it.  For PKCS11 key
      // stores there won't be a file and the input stream should be null.
      FileInputStream keyStoreInputStream = null;
      if (keyStoreType.equals(KEY_STORE_TYPE_JKS) ||
          keyStoreType.equals(KEY_STORE_TYPE_JCEKS) ||
          keyStoreType.equals(KEY_STORE_TYPE_PKCS12))
      {
          final File keyStoreFile = new File(keyStorePath);
          if (! keyStoreFile.exists())
          {
              return null;
          }

          try
          {
              keyStoreInputStream = new FileInputStream(keyStoreFile);
          }
          catch (final Exception e)
          {
              throw new KeyStoreException(String.valueOf(e), e);
          }
      }


      final KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      try
      {
          keyStore.load(keyStoreInputStream, password);
          return this.keyStore = keyStore;
      }
      catch (final Exception e)
      {
          throw new KeyStoreException(String.valueOf(e), e);
      }
      finally
      {
          if (keyStoreInputStream != null)
          {
              try
              {
                  keyStoreInputStream.close();
              }
              catch (final Throwable t)
              {
              }
          }
      }
  }

  private static void ensureFileValid(File arg, String msgStr) {
    if(arg == null) {
      Message msg = ERR_CERTMGR_FILE_NAME_INVALID.get(msgStr);
      throw new NullPointerException(msg.toString());
    }
  }

  private static void ensureValid(String arg, String msgStr) {
    if(arg == null || arg.length() == 0) {
     Message msg = ERR_CERTMGR_VALUE_INVALID.get(msgStr);
      throw new NullPointerException(msg.toString());
    }
  }
}
