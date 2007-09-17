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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.*;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigConstants;

import org.opends.server.admin.std.server.CryptoManagerCfg;
import org.opends.server.api.Backend;
import org.opends.server.backends.TrustStoreBackend;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.Validator;
import org.opends.server.util.SelectableCertificateKeyManager;

/**
 * This class provides the interface to the Directory Server
 * cryptographic framework, which may be used for hashing, encryption,
 * and other kinds of cryptographic operations.  Note that it also
 * contains methods for compressing and uncompressing data.  Although
 * these are not strictly cryptographic operations, there are a lot of
 * similarities and it may be conceivable at some point that
 * accelerated compression may be available just as it is for
 * cryptographic operations.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class CryptoManager
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The secure random number generator used for key generation,
  // initialization vector PRNG seed...
  private static final SecureRandom secureRandom = new SecureRandom();

  // The random number generator used for initialization vector
  // production.
  private static final Random pseudoRandom
          = new Random(secureRandom.nextLong());

  // The preferred message digest algorithm for the Directory Server.
  private final String preferredDigestAlgorithm;

  // The map from encryption key ID to MacKeyEntry (cache).
  private final HashMap<KeyEntryID, MacKeyEntry> macKeyEntryCache
          = new HashMap<KeyEntryID, MacKeyEntry>();

  // The preferred MAC algorithm for the Directory Server.
  private final String preferredMACAlgorithm;

  // The preferred key length for the preferred MAC algorithm.
  private final int preferredMACAlgorithmKeyLengthBits;

  // The map from encryption key ID to CipherKeyEntry (cache).
  private final HashMap<KeyEntryID, CipherKeyEntry>
      cipherKeyEntryCache = new HashMap<KeyEntryID, CipherKeyEntry>();

  // The preferred cipher for the Directory Server.
  private final String preferredCipherTransformation;

  // The preferred key length for the preferred cipher.
  private final int preferredCipherTransformationKeyLengthBits;

  // The name of the local certificate to use for SSL.
  private final String sslCertNickname;

  // Whether replication sessions use SSL encryption.
  private final boolean sslEncryption;

  // The set of SSL protocols enabled or null for the default set.
  private final SortedSet<String> sslProtocols;

  // The set of SSL cipher suites enabled or null for the default set.
  private final SortedSet<String> sslCipherSuites;


  /**
   * Creates a new instance of this crypto manager object from a given
   * configuration.
   *
   * @param   cfg  The configuration of this crypto manager.
   *
   * @throws  ConfigException  If a problem occurs while creating this
   *                           crypto manager that is a result of a
   *                           problem in the configuration.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   creating this crypto manager
   *                                   that is not the result of a
   *                                   problem in the configuration.
   */
  public CryptoManager(CryptoManagerCfg cfg)
         throws ConfigException, InitializationException
  {
    // TODO -- Get the defaults from the configuration rather than
    // hard-coding them.
    preferredCipherTransformation = "AES/CBC/PKCS5Padding";
    preferredCipherTransformationKeyLengthBits = 128;
    preferredDigestAlgorithm = "SHA-1";
    preferredMACAlgorithm    = "HmacSHA1";
    preferredMACAlgorithmKeyLengthBits = 128;

    sslCertNickname = cfg.getSSLCertNickname();
    sslEncryption   = cfg.isSSLEncryption();
    sslProtocols    = cfg.getSSLProtocols();
    sslCipherSuites = cfg.getSSLCipherSuites();

    // Make sure that we can create instances of the preferred digest,
    // MAC, and cipher algorithms.
    try
    {
      MessageDigest.getInstance(preferredDigestAlgorithm);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // TODO: i18n
      throw new InitializationException(
                     Message.raw("Can't get preferred digest:  " +
                          getExceptionMessage(e).toString()), e);
    }

    try
    {
      MacKeyEntry.generateKeyEntry(null,
              preferredMACAlgorithm,
              preferredMACAlgorithmKeyLengthBits);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // TODO: i18n
      throw new InitializationException(
                   Message.raw("Can't get preferred MAC provider:  " +
                          getExceptionMessage(e).toString()), e);
    }

    try
    {
      CipherKeyEntry.generateKeyEntry(null,
              preferredCipherTransformation,
              preferredCipherTransformationKeyLengthBits);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // TODO: i18n
      throw new InitializationException(
                     Message.raw("Can't get preferred cipher:  " +
                     getExceptionMessage(e).toString()), e);
    }
  }


  /**
   * Retrieves the name of the preferred message digest algorithm.
   *
   * @return  The name of the preferred message digest algorithm
   */
  public String getPreferredMessageDigestAlgorithm()
  {
    return preferredDigestAlgorithm;
  }


  /**
   * Retrieves a <CODE>MessageDigest</CODE> object that may be used to
   * generate digests using the preferred digest algorithm.
   *
   * @return  A <CODE>MessageDigest</CODE> object that may be used to
   *          generate digests using the preferred digest algorithm.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public MessageDigest getPreferredMessageDigest()
         throws NoSuchAlgorithmException
  {
    return MessageDigest.getInstance(preferredDigestAlgorithm);
  }



  /**
   * Retrieves a <CODE>MessageDigest</CODE> object that may be used to
   * generate digests using the specified algorithm.
   *
   * @param  digestAlgorithm  The algorithm to use to generate the
   *                          message digest.
   *
   * @return  A <CODE>MessageDigest</CODE> object that may be used to
   *          generate digests using the specified algorithm.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public MessageDigest getMessageDigest(String digestAlgorithm)
         throws NoSuchAlgorithmException
  {
    return MessageDigest.getInstance(digestAlgorithm);
  }



  /**
   * Retrieves a byte array containing a message digest based on the
   * provided data, using the preferred digest algorithm.
   *
   * @param  data  The data to be digested.
   *
   * @return  A byte array containing the generated message digest.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] digest(byte[] data)
         throws NoSuchAlgorithmException
  {
    return MessageDigest.getInstance(preferredDigestAlgorithm).
                digest(data);
  }



  /**
   * Retrieves a byte array containing a message digest based on the
   * provided data, using the requested digest algorithm.
   *
   * @param  digestAlgorithm  The algorithm to use to generate the
   *                          message digest.
   * @param  data             The data to be digested.
   *
   * @return  A byte array containing the generated message digest.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] digest(String digestAlgorithm, byte[] data)
         throws NoSuchAlgorithmException
  {
    return MessageDigest.getInstance(digestAlgorithm).digest(data);
  }



  /**
   * Retrieves a byte array containing a message digest based on the
   * data read from the provided input stream, using the preferred
   * digest algorithm.  Data will be read until the end of the stream
   * is reached.
   *
   * @param  inputStream  The input stream from which the data is to
   *                      be read.
   *
   * @return  A byte array containing the generated message digest.
   *
   * @throws  IOException  If a problem occurs while reading data from
   *                       the provided stream.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] digest(InputStream inputStream)
         throws IOException, NoSuchAlgorithmException
  {
    MessageDigest digest =
         MessageDigest.getInstance(preferredDigestAlgorithm);

    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }

      digest.update(buffer, 0, bytesRead);
    }

    return digest.digest();
  }



  /**
   * Retrieves a byte array containing a message digest based on the
   * data read from the provided input stream, using the requested
   * digest algorithm.  Data will be read until the end of the stream
   * is reached.
   *
   * @param  digestAlgorithm  The algorithm to use to generate the
   *                          message digest.
   * @param  inputStream      The input stream from which the data is
   *                          to be read.
   *
   * @return  A byte array containing the generated message digest.
   *
   * @throws  IOException  If a problem occurs while reading data from
   *                       the provided stream.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] digest(String digestAlgorithm,
                       InputStream inputStream)
         throws IOException, NoSuchAlgorithmException
  {
    MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);

    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }

      digest.update(buffer, 0, bytesRead);
    }

    return digest.digest();
  }



  /**
   * For the current preferred MAC algorithm and key length, return
   * the identifier of the corresponding key entry. Note: the result
   * (key identifier) might change across invocations, due to either
   * of the perferred parameters changing, or because the original
   * key was marked compromised and a replacement key generated.
   *
   * @return A String representation of the identifier of a key entry
   * corresponding to the preferred MAC algorithm and key length.
   *
   * @throws CryptoManagerException In case one or more of the key
   * parameters is invalid, or there is a problem instantiating the
   * key entry in case it does not already exist.
   */
  public String getMacEngineKeyEntryID()
          throws CryptoManagerException
  {
    return getMacEngineKeyEntryID(preferredMACAlgorithm,
            preferredMACAlgorithmKeyLengthBits);
  }


  /**
   * For the specified MAC algorithm and key length, return
   * the identifier of the corresponding key entry. Note: the result
   * (key identifier) might change across invocations, due to either
   * of the perferred parameters changing, or because the original
   * key was marked compromised and a replacement key generated.
   *
   * @param  macAlgorithm  The algorithm to use for the MAC engine.
   *
   * @param  keyLengthBits  The key length in bits to use with the
   *         specified algorithm.
   *
   * @return A String representation of the identifier of a key entry
   * corresponding to the specified MAC algorithm and key length.
   *
   * @throws CryptoManagerException In case one or more of the key
   * parameters is invalid, or there is a problem instantiating the
   * key entry in case it does not already exist.
   */
  public String getMacEngineKeyEntryID(final String macAlgorithm,
                                       final int keyLengthBits)
         throws CryptoManagerException {
    Validator.ensureNotNull(macAlgorithm);

    MacKeyEntry keyEntry = MacKeyEntry.getKeyEntry(this, macAlgorithm,
                                                   keyLengthBits);
    if (null == keyEntry) {
      keyEntry = MacKeyEntry.generateKeyEntry(this, macAlgorithm,
                                              keyLengthBits);
    }

    return keyEntry.getKeyID().getStringValue();
  }


  /**
   * For the specified key entry identifier, instantiate a MAC engine.
   *
   * @param keyEntryID The identifier of the key entry containing the
   * desired MAC algorithm name and key length.
   *
   * @return The MAC engine instantiated with the parameters from the
   * referenced key entry, or null if no such entry exists.
   *
   * @throws CryptoManagerException  In case the key entry identifier
   * is invalid or there is a problem instatiating the MAC engine from
   * the parameters in the referenced key entry.
   */
  public Mac getMacEngine(String keyEntryID)
          throws CryptoManagerException
  {
    MacKeyEntry keyEntry;
    try {
      keyEntry = MacKeyEntry.getKeyEntry(this,
              new KeyEntryID(keyEntryID));
    }
    catch (IllegalArgumentException ex) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("MAC key entry identifier \"%s\" is not" +
                      " a valid UUID.", keyEntryID), ex);
    }

    if (null == keyEntry) return null;

    return getMacEngine(keyEntry);
  }


  /**
   * This method produces an initialized MAC engine based on the
   * supplied MacKeyEntry's state.
   *
   * @param keyEntry The MacKeyEntry specifying the Mac properties.
   *
   * @return  An initialized Mac object.
   *
   * @throws CryptoManagerException  In case there was a error
   * instantiating the Mac object.
   */
  private static Mac getMacEngine(MacKeyEntry keyEntry)
          throws CryptoManagerException
  {
    Mac mac;
    try {
      mac = Mac.getInstance(keyEntry.getType());
    }
    catch (NoSuchAlgorithmException ex){
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid MAC algorithm specified: + %s",
                      keyEntry.getType()), ex);
    }

    try {
      mac.init(keyEntry.getKeySpec());
    }
    catch (InvalidKeyException ex) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid key specification supplied to" +
                      " Mac object initialization"), ex);
    }

    return mac;
  }

  /**
   * Retrieves the name of the preferred cipher algorithm.
   *
   * @return  The name of the preferred cipher algorithm
   */
  public String getPreferredCipherTransformation()
  {
    return preferredCipherTransformation;
  }


  /**
   * This method produces an initialized Cipher based on the supplied
   * CipherKeyEntry's state.
   *
   * @param keyEntry  The secret key entry containing the cipher
   * transformation and secret key for which to instantiate
   * the cipher.
   *
   * @param mode  Either Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE.
   *
   * @param initializationVector  For Cipher.DECRYPT_MODE, supply
   * any initialzation vector used in the corresponding encryption
   * cipher. May be null.
   *
   * @return  The initialized cipher object.
   *
   * @throws  CryptoManagerException In case of a problem creating
   * or initializing the requested cipher object. Possible causes
   * include NoSuchAlgorithmException, NoSuchPaddingException,
   * InvalidKeyException, and InvalidAlgorithmParameterException.
   */
  private static Cipher getCipher(final CipherKeyEntry keyEntry,
                                  final int mode,
                                  final byte[] initializationVector)
          throws CryptoManagerException {
    Validator.ensureTrue(Cipher.ENCRYPT_MODE == mode
            || Cipher.DECRYPT_MODE == mode);
    Validator.ensureTrue(Cipher.ENCRYPT_MODE != mode
            || null == initializationVector);
    Validator.ensureTrue(-1 != keyEntry.getIVLengthBits()
            || Cipher.ENCRYPT_MODE == mode);
    Validator.ensureTrue(null == initializationVector
            || initializationVector.length * Byte.SIZE
                                       == keyEntry.getIVLengthBits());

    Cipher cipher;
    try {
      cipher = Cipher.getInstance(keyEntry.getType());
    }
    catch (GeneralSecurityException ex) {
      // NoSuchAlgorithmException, NoSuchPaddingException
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid Cipher transformation specified:"
                      + " %s.", keyEntry.getType()), ex);
    }

    try {
      if (0 < keyEntry.getIVLengthBits()) {
          byte[] iv;
          if (Cipher.ENCRYPT_MODE == mode
                  && null == initializationVector) {
            iv = new byte[keyEntry.getIVLengthBits() / Byte.SIZE];
            pseudoRandom.nextBytes(iv);
          }
          else {
            iv = initializationVector;
          }
          cipher.init(mode, keyEntry.getKeySpec(),
                  new IvParameterSpec(iv));
      }
      else {
        cipher.init(mode, keyEntry.getKeySpec());
      }
    }
    catch (GeneralSecurityException ex) {
      // InvalidKeyException, InvalidAlgorithmParameterException
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Error initializing cipher."), ex);
    }

    return cipher;
  }


  /**
   * Encrypts the data in the provided byte array using the preferred
   * cipher transformation.
   *
   * @param  data  The plain-text data to be encrypted.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *          encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  public byte[] encrypt(byte[] data)
         throws GeneralSecurityException, CryptoManagerException
  {
    return encrypt(preferredCipherTransformation,
            preferredCipherTransformationKeyLengthBits, data);
  }


  /**
   * Encrypts the data in the provided byte array using the requested
   * cipher algorithm.
   *
   * @param  cipherTransformation  The algorithm/mode/padding to use
   *         for the cipher.
   *
   * @param  keyLengthBits  The length in bits of the encryption key
   *         this method is to use. Note the specified key length and
   *         transformation must be compatible.
   *
   * @param  data  The plain-text data to be encrypted.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *          encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  public byte[] encrypt(String cipherTransformation,
                        int keyLengthBits,
                        byte[] data)
         throws GeneralSecurityException, CryptoManagerException
  {
    Validator.ensureNotNull(cipherTransformation, data);

    CipherKeyEntry keyEntry = CipherKeyEntry.getKeyEntry(
            this, cipherTransformation, keyLengthBits);
    if (null == keyEntry) {
      keyEntry = CipherKeyEntry.generateKeyEntry(this,
              cipherTransformation, keyLengthBits);
    }

    final Cipher cipher
            = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);

    final byte[] keyID = keyEntry.getKeyID().getByteValue();
    final byte[] iv = cipher.getIV();
    final int prologueLength
            = keyID.length + ((null == iv) ? 0 : iv.length);
    final int dataLength = cipher.getOutputSize(data.length);
    final byte[] cipherText = new byte[prologueLength + dataLength];
    System.arraycopy(keyID, 0, cipherText, 0, keyID.length);
    if (null != iv) {
      System.arraycopy(iv, 0, cipherText, keyID.length, iv.length);
    }
    System.arraycopy(cipher.doFinal(data), 0, cipherText,
                     prologueLength, dataLength);
    return cipherText;
  }


  /**
   * Writes encrypted data to the provided output stream using the
   * preferred cipher transformation.
   *
   * @param  outputStream The output stream to be wrapped by the
   *         returned cipher output stream.
   *
   * @return  The output stream wrapped with a CipherOutputStream.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  public CipherOutputStream getCipherOutputStream(
          OutputStream outputStream) throws CryptoManagerException
  {
    return getCipherOutputStream(preferredCipherTransformation,
            preferredCipherTransformationKeyLengthBits, outputStream);
  }


  /**
   * Writes encrypted data to the provided output stream using the
   * requested cipher transformation.
   *
   * @param  cipherTransformation  The algorithm/mode/padding to use
   *         for the cipher.
   *
   * @param  keyLengthBits  The length in bits of the encryption key
   *         this method will generate. Note the specified key length
   *         must be compatible with the transformation.
   *
   * @param  outputStream The output stream to be wrapped by the
   *         returned cipher output stream.
   *
   * @return  The output stream wrapped with a CipherOutputStream.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  public CipherOutputStream getCipherOutputStream(
          String cipherTransformation, int keyLengthBits,
          OutputStream outputStream)
         throws CryptoManagerException
  {
    Validator.ensureNotNull(cipherTransformation, outputStream);

    CipherKeyEntry keyEntry = CipherKeyEntry.getKeyEntry(
            this, cipherTransformation, keyLengthBits);
    if (null == keyEntry) {
      keyEntry = CipherKeyEntry.generateKeyEntry(this,
              cipherTransformation, keyLengthBits);
    }

    final Cipher cipher
            = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);
    final byte[] keyID = keyEntry.getKeyID().getByteValue();
    try {
      outputStream.write(keyID);
      if (null != cipher.getIV()) {
        outputStream.write(cipher.getIV());
      }
    }
    catch (IOException ioe) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Exception when writing CryptoManager" +
                      " prologue."), ioe);
    }

    return new CipherOutputStream(outputStream, cipher);
  }


  /**
   * Decrypts the data in the provided byte array using cipher
   * specified by the key identifier prologue to the data.
   * cipher.
   *
   * @param  data  The cipher-text data to be decrypted.
   *
   * @return  A byte array containing the clear-text representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *          encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs reading the
   *          key identifier or initialization vector from the data
   *          prologue, or using these values to initialize a Cipher.
   */
  public byte[] decrypt(byte[] data)
         throws GeneralSecurityException,
                CryptoManagerException
  {
    KeyEntryID keyID;
    try {
      final byte[] keyIDBytes
              = new byte[KeyEntryID.getByteValueLength()];
      System.arraycopy(data, 0, keyIDBytes, 0, keyIDBytes.length);
      keyID = new KeyEntryID(keyIDBytes);
    }
    catch (Exception ex) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw(
                      "Exception when reading key identifier from" +
                              " data prologue."), ex);
    }

    CipherKeyEntry keyEntry = CipherKeyEntry.getKeyEntry(this, keyID);
    if (null == keyEntry) {
      throw new CryptoManagerException(
              // TODO: i18N
              Message.raw("Invalid or unknown key identifier in" +
                      " data prologue."));
    }

    byte[] iv = null;
    if (0 < keyEntry.getIVLengthBits()) {
      iv = new byte[keyEntry.getIVLengthBits()/Byte.SIZE];
      try {
        System.arraycopy(data, KeyEntryID.getByteValueLength(), iv, 0,
                iv.length);
      }
      catch (Exception ex) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Exception when reading initialization" +
                        " vector from data prologue."), ex);
      }
    }

    final Cipher cipher = getCipher(keyEntry, Cipher.DECRYPT_MODE,
            iv);
    final int prologueLength = KeyEntryID.getByteValueLength()
                                     + ((null == iv) ? 0 : iv.length);
    return cipher.doFinal(data, prologueLength,
                          data.length - prologueLength);
  }



  /**
   * Returns a CipherInputStream instantiated with a cipher
   * corresponding to the key identifier prologue to the data.
   *
   * @param  inputStream The input stream be wrapped with the
   *         CipherInputStream.
   *
   * @return The CiperInputStream instantiated as specified.
   *
   * @throws  CryptoManagerException If there is a problem reading the
   *          key ID or initialization vector from the input stream,
   *          or using these values to inititalize a Cipher.
   */
  public CipherInputStream getCipherInputStream(
          InputStream inputStream) throws CryptoManagerException
  {
    CipherKeyEntry keyEntry;
    byte[] iv = null;
    try {
      final byte[] keyID = new byte[KeyEntryID.getByteValueLength()];
      if (keyID.length != inputStream.read(keyID)){
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Stream underflow when reading key" +
                        " identifier from data prologue."));
      }

      keyEntry = CipherKeyEntry.getKeyEntry(this,
              new KeyEntryID(keyID));
      if (null == keyEntry) {
        throw new CryptoManagerException(
                // TODO: i18N
             Message.raw("Invalid key identifier in data prologue."));
      }

      if (0 < keyEntry.getIVLengthBits()) {
        iv = new byte[keyEntry.getIVLengthBits() / Byte.SIZE];
        if (iv.length != inputStream.read(iv)) {
          throw new CryptoManagerException(
                  // TODO: i18n
                  Message.raw("Stream underflow when reading" +
                      " initialization vector from data prologue."));
        }
      }
    }
    catch (IOException ioe) {
      throw new CryptoManagerException(
          // TODO: i18n
          Message.raw(
                 "IO exception when reading CryptoManager prologue."),
                 ioe);
    }

    return new CipherInputStream(inputStream,
            getCipher(keyEntry, Cipher.DECRYPT_MODE, iv));
  }


  /**
   * Attempts to compress the data in the provided source array into
   * the given destination array.  If the compressed data will fit
   * into the destination array, then this method will return the
   * number of bytes of compressed data in the array.  Otherwise, it
   * will return -1 to indicate that the compression was not
   * successful.  Note that if -1 is returned, then the data in the
   * destination array should be considered invalid.
   *
   * @param  src  The array containing the raw data to compress.
   * @param  dst  The array into which the compressed data should be
   *              written.
   *
   * @return  The number of bytes of compressed data, or -1 if it was
   *          not possible to actually compress the data.
   */
  public int compress(byte[] src, byte[] dst)
  {
    Deflater deflater = new Deflater();
    try
    {
      deflater.setInput(src);
      deflater.finish();

      int compressedLength = deflater.deflate(dst);
      if (deflater.finished())
      {
        return compressedLength;
      }
      else
      {
        return -1;
      }
    }
    finally
    {
      deflater.end();
    }
  }



  /**
   * Attempts to uncompress the data in the provided source array into
   * the given destination array.  If the uncompressed data will fit
   * into the given destination array, then this method will return
   * the number of bytes of uncompressed data written into the
   * destination buffer.  Otherwise, it will return a negative value
   * to indicate that the destination buffer was not large enough.
   * The absolute value of that negative return value will indicate
   * the buffer size required to fully decompress the data.  Note that
   * if a negative value is returned, then the data in the destination
   * array should be considered invalid.
   *
   * @param  src  The array containing the compressed data.
   * @param  dst  The array into which the uncompressed data should be
   *              written.
   *
   * @return  A positive value containing the number of bytes of
   *          uncompressed data written into the destination buffer,
   *          or a negative value whose absolute value is the size of
   *          the destination buffer required to fully decompress the
   *          provided data.
   *
   * @throws  DataFormatException  If a problem occurs while
   *                               attempting to uncompress the data.
   */
  public int uncompress(byte[] src, byte[] dst)
         throws DataFormatException
  {
    Inflater inflater = new Inflater();
    try
    {
      inflater.setInput(src);

      int decompressedLength = inflater.inflate(dst);
      if (inflater.finished())
      {
        return decompressedLength;
      }
      else
      {
        int totalLength = decompressedLength;

        while (! inflater.finished())
        {
          totalLength += inflater.inflate(dst);
        }

        return -totalLength;
      }
    }
    finally
    {
      inflater.end();
    }
  }


  /**
   * Retrieve the ADS trust store backend.
   * @return The ADS trust store backend.
   * @throws ConfigException If the ADS trust store backend is
   *                         not configured.
   */
  private TrustStoreBackend getTrustStoreBackend()
       throws ConfigException
  {
    Backend b = DirectoryServer.getBackend(
         ConfigConstants.ID_ADS_TRUST_STORE_BACKEND);
    if (b == null)
    {
      Message msg =
           ERR_CRYPTOMGR_ADS_TRUST_STORE_BACKEND_NOT_ENABLED.get(
                ConfigConstants.ID_ADS_TRUST_STORE_BACKEND);
      throw new ConfigException(msg);
    }
    if (!(b instanceof TrustStoreBackend))
    {
      Message msg =
           ERR_CRYPTOMGR_ADS_TRUST_STORE_BACKEND_WRONG_CLASS.get(
                ConfigConstants.ID_ADS_TRUST_STORE_BACKEND);
      throw new ConfigException(msg);
    }
    return (TrustStoreBackend)b;
  }

  /**
   * Create an SSL context that may be used for communication to
   * another ADS component.
   *
   * @param sslCertNickname The name of the local certificate to use,
   *                        or null if none is specified.
   * @return A new SSL Context.
   * @throws ConfigException If the context could not be created.
   */
  public SSLContext getSslContext(String sslCertNickname)
       throws ConfigException
  {
    SSLContext sslContext;
    try
    {
      TrustStoreBackend trustStoreBackend = getTrustStoreBackend();
      KeyManager[] keyManagers = trustStoreBackend.getKeyManagers();
      TrustManager[] trustManagers =
           trustStoreBackend.getTrustManagers();

      sslContext = SSLContext.getInstance("TLS");

      if (sslCertNickname == null)
      {
        sslContext.init(keyManagers, trustManagers, null);
      }
      else
      {
        X509ExtendedKeyManager[] extendedKeyManagers =
             SelectableCertificateKeyManager.wrap(
                  keyManagers,
                  sslCertNickname);
        sslContext.init(extendedKeyManagers, trustManagers, null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
           ERR_CRYPTOMGR_SSL_CONTEXT_CANNOT_INITIALIZE.get(
                getExceptionMessage(e));
      throw new ConfigException(message, e);
    }

    return sslContext;
  }


  /**
   * Get the name of the local certificate to use for SSL.
   * @return The name of the local certificate to use for SSL.
   */
  public String getSslCertNickname()
  {
    return sslCertNickname;
  }

  /**
   * Determine whether SSL encryption is enabled.
   * @return true if SSL encryption is enabled.
   */
  public boolean isSslEncryption()
  {
    return sslEncryption;
  }

  /**
   * Get the set of enabled SSL protocols.
   * @return The set of enabled SSL protocols.
   */
  public SortedSet<String> getSslProtocols()
  {
    return sslProtocols;
  }

  /**
   * Get the set of enabled SSL cipher suites.
   * @return The set of enabled SSL cipher suites.
   */
  public SortedSet<String> getSslCipherSuites()
  {
    return sslCipherSuites;
  }

  /**
   * This class implements a utility interface to the unique
   * identifier corresponding to a cryptographic key. For each key
   * stored in an entry in ADS, the key identifier is the naming
   * attribute of the entry. The external binary representation of the
   * key entry identifier is compact, because it is typically stored
   * as a prefix of encrypted data.
   */
  private static class KeyEntryID
  {
    /**
     *  Constructs a KeyEntryID using a new unique identifier.
     */
    public KeyEntryID() {
      fValue = UUID.randomUUID();
    }

    /**
     * Construct a {@code KeyEntryID} from its {@code byte[]}
     * representation.
     *
     * @param keyEntryID The {@code byte[]} representation of a
     * {@code KeyEntryID}.
     */
    public KeyEntryID(final byte[] keyEntryID) {
      Validator.ensureTrue(getByteValueLength() == keyEntryID.length);
      long hiBytes = 0;
      long loBytes = 0;
      for (int i = 0; i < 8; ++i) {
        hiBytes = (hiBytes << 8) | (keyEntryID[i] & 0xff);
        loBytes = (loBytes << 8) | (keyEntryID[8 + i] & 0xff);
      }
      fValue = new UUID(hiBytes, loBytes);
    }

    /**
     * Constructs a {@code KeyEntryID} from its {@code String}
     * representation.
     *
     * @param  keyEntryID The {@code String} reprentation of a
     * {@code KeyEntryID}.
     *
     * @throws  CryptoManagerException  If the argument does
     * not conform to the {@code KeyEntryID} string syntax.
     */
    public KeyEntryID(final String keyEntryID)
            throws CryptoManagerException {
      try {
        fValue = UUID.fromString(keyEntryID);
      }
      catch (Exception ex) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Key entry identifier \"%s\" has" +
                        " invalid syntax.", keyEntryID), ex);
      }
    }

    /**
     * Copy constructor.
     *
     * @param keyEntryID  The {@code KeyEntryID} to copy.
     */
    public KeyEntryID(final KeyEntryID keyEntryID) {
      fValue = new UUID(keyEntryID.fValue.getMostSignificantBits(),
                        keyEntryID.fValue.getLeastSignificantBits());
    }

    /**
     * Returns the compact {@code byte[]} representation of this
     * {@code KeyEntryID}.
     * @return The compact {@code byte[]} representation of this
     * {@code KeyEntryID
     */
    public byte[] getByteValue(){
      final byte[] uuidBytes = new byte[16];
      long hiBytes = fValue.getMostSignificantBits();
      long loBytes = fValue.getLeastSignificantBits();
      for (int i = 7; i >= 0; --i) {
        uuidBytes[i] = (byte)hiBytes;
        hiBytes >>>= 8;
        uuidBytes[8 + i] = (byte)loBytes;
        loBytes >>>= 8;
      }
      return uuidBytes;
    }

    /**
     * Returns the {@code String} representation of this
     * {@code KeyEntryID}.
     * @return The {@code String} representation of this
     * {@code KeyEntryID}.
     */
    public String getStringValue() {
      return fValue.toString();
    }

    /**
     * Returns the length of the compact {@code byte[]} representation
     * of a {@code KeyEntryID}.
     *
     * @return The length of the compact {@code byte[]} representation
     * of a {@code KeyEntryID}.
     */
    public static int getByteValueLength() {
      return 16;
    }

    /**
     * Compares this object to the specified object. The result is
     * true if and only if the argument is not null, is of type
     * {@code KeyEntryID}, and has the same value (i.e., the
     * {@code String} and {@code byte[]} representations are
     * identical).
     *
     * @param obj The object to which to compare this instance.
     *
     * @return {@code true} if the objects are the same, {@code false}
     * otherwise.
     */
    public boolean equals(final Object obj){
      return obj instanceof KeyEntryID
              && fValue.equals(((KeyEntryID) obj).fValue);
    }

    /**
     * Returns a hash code for this {@code KeyEntryID}.
     *
     * @return a hash code value for this {@code KeyEntryID}.
     */
    public int hashCode() {
      return fValue.hashCode();
    }

    // state
    private final UUID fValue;
  }


  /**
   * This class corresponds to the secret key portion if a secret
   * key entry in ADS.
   */
  private static class SecretKeyEntry
  {
    /**
     * Construct an instance of {@code SecretKeyEntry} using the
     * specified parameters. This constructor is used for key
     * generation.
     *
     * @param algorithm  The name of the secret key algorithm for
     * which the key entry is to be produced.
     *
     * @param keyLengthBits  The length of the requested key in bits.
     *
     * @throws CryptoManagerException If there is a problem
     * instantiating the key generator.
     */
    public SecretKeyEntry(String algorithm, int keyLengthBits)
    throws CryptoManagerException {
      KeyGenerator keyGen;
      try {
        keyGen = KeyGenerator.getInstance(algorithm);
      }
      catch (NoSuchAlgorithmException ex) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Unable to produce key generator using" +
                        " key algorithm argument %s",
                        algorithm), ex);
      }
      keyGen.init(keyLengthBits, secureRandom);
      final byte[] key = keyGen.generateKey().getEncoded();

      this.fKeyID = new KeyEntryID();
      this.fKeySpec = new SecretKeySpec(key, algorithm);
      this.fKeyLengthBits = key.length * Byte.SIZE;
      this.fIsCompromised = false;
    }


    /**
     * Construct an instance of {@code SecretKeyEntry} using the
     * specified parameters. This constructor would typically be used
     * for key entries imported from ADS, for which the full set of
     * paramters is known.
     *
     * @param keyID  The unique identifier of this algorithm/key pair.
     *
     * @param algorithm  The name of the secret key algorithm for
     * which the key entry is to be produced.
     *
     * @param key  The secret key.
     *
     * @param isCompromised {@code false} if the key may be used
     * for operations on new data, or {@code true} if the key is being
     * retained only for use in validation.
     */
    public SecretKeyEntry(final KeyEntryID keyID,
                          final String algorithm,
                          final byte[] key,
                          final boolean isCompromised) {
      // copy arguments
      this.fKeyID = new KeyEntryID(keyID);
      this.fKeySpec = new SecretKeySpec(key, algorithm);
      this.fKeyLengthBits = key.length * Byte.SIZE;
      this.fIsCompromised = isCompromised;
    }


    /**
     * The unique identifier of this algorithm/key pair.
     *
     * @return The unique identifier of this algorithm/key pair.
     */
    public KeyEntryID getKeyID() {
      return fKeyID;
    }


    /**
     * The secret key spec containing the secret key.
     *
     * @return The secret key spec containing the secret key.
     */
    public SecretKeySpec getKeySpec() {
      return fKeySpec;
    }


    /**
     * Mark a key entry as compromised. The entry will no longer be
     * eligible for use as an encryption key.
     */
    public void setIsCompromised() {
      // TODO: called from ADS monitoring thread. Lock entry?
      fIsCompromised = true;
    }

    /**
     * Returns the length of the secret key in bits.
     * @return the length of the secret key in bits.
     */
    public int getKeyLengthBits() {
      return fKeyLengthBits;
    }

    /**
     * Returns the status of the key.
     * @return  {@code false} if the key may be used for operations on
     * new data, or {@code true} if the key is being retained only for
     * use in validation.
     */
    public boolean isCompromised() {
      return fIsCompromised;
    }

    // state
    private final KeyEntryID fKeyID;
    private final SecretKeySpec fKeySpec;
    private final int fKeyLengthBits;
    private boolean fIsCompromised = false;
  }

  /**
   * This class corresponds to the cipher key entry in ADS. It is
   * used in the local cache of key entries that have been requested
   * by CryptoManager clients.
   */
  private static class CipherKeyEntry extends SecretKeyEntry
  {
    /**
     * This method generates a key according to the key parameters,
     * and creates a key entry and registers it in the supplied map.
     *
     * @param  cryptoManager The CryptoManager instance for which the
     * key is to be generated. Pass {@code null} as the argument to
     * this parameter in order to validate a proposed cipher
     * transformation and key length without publishing the key.
     *
     * @param transformation  The cipher transformation for which the
     * key is to be produced.
     *
     * @param keyLengthBits  The cipher key length in bits.
     *
     * @return The key entry corresponding to the parameters.
     *
     * @throws CryptoManagerException If there is a problem
     * instantiating a Cipher object in order to validate the supplied
     * parameters when creating a new entry.
     *
     * @see CipherKeyEntry#getKeyEntry(CryptoManager, String, int)
     */
    public static CipherKeyEntry generateKeyEntry(
            final CryptoManager cryptoManager,
            final String transformation,
            final int keyLengthBits)
    throws CryptoManagerException {

      final Map<KeyEntryID, CipherKeyEntry> map
              = (null == cryptoManager)
              ? null : cryptoManager.cipherKeyEntryCache;

      CipherKeyEntry keyEntry = new CipherKeyEntry(transformation,
              keyLengthBits);

      // Validate the key entry.
      final Cipher cipher
              = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);
      final byte[] iv = cipher.getIV();
      keyEntry.setIVLengthBits(
              (null == iv) ? 0 : iv.length * Byte.SIZE);

      if (null != map) {
        map.put(keyEntry.getKeyID(), keyEntry);
        // TODO: publish key in ADS. (mark key "blocked" in map
        // until registered? OTOH, Key should be in local map prior to
        // publication, since data could arrive from a remote OpenDS
        // instance encrypted with the key any time after publication.
        // OTOH, the key should be published in ADS before any use,
        // since that is the persistent shared secret key repository.)
      }

      return keyEntry;
    }


    /**
     * Initializes a secret key entry from the supplied parameters,
     * validates it, and registers it in the supplied map. The
     * anticipated use of this method is to import a key entry from
     * ADS.
     *
     * @param cryptoManager  The CryptoManager instance.
     *
     * @param keyIDBytes  The key identifier.
     *
     * @param transformation  The cipher transformation for which the
     * key entry was produced.
     *
     * @param keyAlgorithm  The cipher algorithm for which the key was
     * produced.
     *
     * @param key  The cipher key.
     *
     * @param ivLengthBits  The length of the initialization vector,
     * which will be zero in the case of any stream cipher algorithm,
     * any block cipher algorithm for which the transformation mode
     * does not use an initialization vector, and any HMAC algorithm.
     *
     * @param isCompromised  Mark the key as compromised, so that it
     * will not subsequently be used for encryption. The key entry
     * must be maintained in order to decrypt existing ciphertext.
     *
     * @return  The key entry, if one was successfully produced.
     *
     * @throws CryptoManagerException  In case of an error in the
     * parameters used to initialize or validate the key entry.
     */
    public static CipherKeyEntry importCipherKeyEntry(
            final CryptoManager cryptoManager,
            final byte[] keyIDBytes,
            final String transformation,
            final String keyAlgorithm,
            final byte[] key,
            final int ivLengthBits,
            final boolean isCompromised)
            throws CryptoManagerException {
      Validator.ensureNotNull(keyIDBytes, transformation,
              keyAlgorithm, key);
      Validator.ensureTrue(0 <= ivLengthBits);

      final KeyEntryID keyID = new KeyEntryID(keyIDBytes);

      // Check map for existing key entry with the supplied keyID.
      CipherKeyEntry keyEntry = getKeyEntry(cryptoManager, keyID);
      if (null != keyEntry) {
        // TODO: compare keyEntry with supplied parameters to ensure
        // equal.
        return keyEntry;
      }

      // Instantiate new entry.
      keyEntry = new CipherKeyEntry(keyID, transformation,
              keyAlgorithm, key, ivLengthBits, isCompromised);

      // Validate new entry.
      byte[] iv = null;
      if (0 < ivLengthBits) {
        iv = new byte[ivLengthBits * Byte.SIZE];
        pseudoRandom.nextBytes(iv);
      }
      getCipher(keyEntry, Cipher.DECRYPT_MODE, iv);

      // Cache new entry.
      cryptoManager.cipherKeyEntryCache.put(keyEntry.getKeyID(),
              keyEntry);

      return keyEntry;
    }


    /**
     * Retrieve a CipherKeyEntry from the CipherKeyEntry Map based on
     * the algorithm name and key length.
     *
     * @param cryptoManager  The CryptoManager instance with which the
     * key entry is associated.
     *
     * @param transformation  The cipher transformation for which the
     * key was produced.
     *
     * @param keyLengthBits  The cipher key length in bits.
     *
     * @return  The key entry corresponding to the parameters, or null
     * if no such entry exists.
     */
    public static CipherKeyEntry getKeyEntry(
            final CryptoManager cryptoManager,
            final String transformation,
            final int keyLengthBits) {
      Validator.ensureNotNull(cryptoManager, transformation);
      Validator.ensureTrue(0 < keyLengthBits);


      CipherKeyEntry keyEntry = null;
      // search for an existing key that satisfies the request
      for (Map.Entry<KeyEntryID, CipherKeyEntry> i
              : cryptoManager.cipherKeyEntryCache.entrySet()) {
        CipherKeyEntry entry = i.getValue();
        if (! entry.isCompromised()
                && entry.getType().equals(transformation)
                && entry.getKeyLengthBits() == keyLengthBits) {
          keyEntry = entry;
          break;
        }
      }

      // TODO: if (null == keyEntry) Does ADS monitoring thread keep
      // map updated with keys produced at other sites? Otherwise,
      // search ADS for suitable key.

      // TODO: if (null == keyEntry) consider generating key here.

      return keyEntry;
    }


    /**
     * Given a key identifier, return the associated cipher key entry
     * from the supplied map. This method would typically be used by
     * a decryption routine.
     *
     * @param cryptoManager  The CryptoManager instance with which the
     * key entry is associated.
     *
     * @param keyID  The key identifier.
     *
     * @return  The key entry associated with the key identifier.
     */
    public static CipherKeyEntry getKeyEntry(
            CryptoManager cryptoManager,
            final KeyEntryID keyID) {
      return cryptoManager.cipherKeyEntryCache.get(keyID);
      /* TODO: Does ADS monitorying thread keep map updated with keys
         produced at other sites? If not, fetch from ADS and update
         map (assuming a legitimate key ID, the key should exist in
         ADS because this routine is called for decryption). */
    }

    /**
     In case a transformation is supplied instead of an algorithm:
     E.g., AES/CBC/PKCS5Padding -> AES.

     @param transformation The cipher transformation from which to
     extract the cipher algorithm.

     @return  The algorithm prefix of the Cipher transformation. If
     the transformation is supplied as an algorithm-only (no mode or
     padding), return the transformation as-is.
     */
    private static String keyAlgorithmFromTransformation(
            String transformation){
    final int separatorIndex = transformation.indexOf('/');
      return (0 < separatorIndex)
              ? transformation.substring(0, separatorIndex)
              : transformation;
    }

    /**
     * Construct an instance of {@code CipherKeyEntry} using the
     * specified parameters. This constructor would typically be used
     * for key generation.
     *
     * @param transformation  The name of the Cipher transformation
     * for which the key entry is to be produced.
     *
     * @param keyLengthBits  The length of the requested key in bits.
     *
     * @throws CryptoManagerException If there is a problem
     * instantiating the key generator.
     */
    private CipherKeyEntry(final String transformation,
                           final int keyLengthBits)
            throws CryptoManagerException {
      // Generate a new key.
      super(keyAlgorithmFromTransformation(transformation),
              keyLengthBits);

      // copy arguments.
      this.fType = new String(transformation);
      this.fIVLengthBits = -1; /* compute IV length */
    }

    /**
     * Construct an instance of CipherKeyEntry using the specified
     * parameters. This constructor would typically be used for key
     * entries imported from ADS, for which the full set of paramters
     * is known, and for a newly generated key entry, for which the
     * initialization vector length might not yet be known, but which
     * must be set prior to using the key.
     *
     * @param keyID  The unique identifier of this cipher
     * transformation/key pair.
     *
     * @param transformation  The name of the secret-key cipher
     * transformation for which the key entry is to be produced.
     *
     * @param keyAlgorithm  The name of the secret key cipher
     * algorithm for which the key was produced.
     *
     * @param key  The cipher key.
     *
     * @param ivLengthBits  The length in bits of a mandatory
     * initialization vector or 0 if none is required. Set this
     * parameter to -1 when generating a new encryption key and this
     * method will attempt to compute the proper value by first using
     * the cipher block size and then, if the cipher block size is
     * non-zero, using 0 (i.e., no initialization vector).
     *
     * @param isCompromised {@code false} if the key may be used
     * for encryption, or {@code true} if the key is being retained
     * only for use in decrypting existing data.
     *
     * @throws  CryptoManagerException If there is a problem
     * instantiating a Cipher object in order to validate the supplied
     * parameters when creating a new entry.
     */
    private CipherKeyEntry(final KeyEntryID keyID,
                           final String transformation,
                           final String keyAlgorithm,
                           final byte[] key,
                           final int ivLengthBits,
                           final boolean isCompromised)
            throws CryptoManagerException {
      super(keyID, keyAlgorithm, key, isCompromised);

      // copy arguments
      this.fType = new String(transformation);
      this.fIVLengthBits = ivLengthBits;
    }


    /**
     * The cipher transformation for which the key entry was created.
     *
     * @return The cipher transformation.
     */
    public String getType() {
      return fType;
    }

    /**
     * Set the algorithm/key pair's required initialization vector
     * length in bits. Typically, this will be the cipher's block
     * size, or 0 for a stream cipher or a block cipher mode that does
     * not use an initialization vector (e.g., ECB).
     *
     * @param ivLengthBits The initiazliation vector length in bits.
     */
    private void setIVLengthBits(int ivLengthBits) {
      Validator.ensureTrue(-1 == fIVLengthBits && 0 <= ivLengthBits);
      fIVLengthBits = ivLengthBits;
    }

    /**
     * The initialization vector length in bits: 0 is a stream cipher
     * or a block cipher that does not use an IV (e.g., ECB); or a
     * positive integer, typically the block size of the cipher.
     * <p>
     * This method returns -1 if the object initialization has not
     * been completed.
     *
     * @return The initialization vector length.
     */
    public int getIVLengthBits() {
      return fIVLengthBits;
    }

    // state
    private final String fType;
    private int fIVLengthBits = -1;
  }



  /**
   * This class corresponds to the MAC key entry in ADS. It is
   * used in the local cache of key entries that have been requested
   * by CryptoManager clients.
   */
  private static class MacKeyEntry extends SecretKeyEntry
  {
    /**
     * This method generates a key according to the key parameters,
     * creates a key entry, and optionally registers it in the
     * supplied CryptoManager context.
     *
     * @param  cryptoManager The CryptoManager instance for which the
     * key is to be generated. Pass {@code null} as the argument to
     * this parameter in order to validate a proposed MAC algorithm
     * and key length, but not publish the key entry.
     *
     * @param algorithm  The MAC algorithm for which the
     * key is to be produced. This argument is required.
     *
     * @param keyLengthBits  The MAC key length in bits. The argument
     * must be a positive integer evenly divisible by the value
     * Byte.SIZE.
     *
     * @return The key entry corresponding to the parameters.
     *
     * @throws CryptoManagerException If there is a problem
     * instantiating a Mac object in order to validate the supplied
     * parameters when creating a new entry.
     *
     * @see MacKeyEntry#getKeyEntry(CryptoManager, String, int)
     */
    public static MacKeyEntry generateKeyEntry(
            final CryptoManager cryptoManager,
            final String algorithm,
            final int keyLengthBits)
    throws CryptoManagerException {
      Validator.ensureNotNull(algorithm);

      final Map<KeyEntryID, MacKeyEntry> map = (null == cryptoManager)
              ? null : cryptoManager.macKeyEntryCache;

      final MacKeyEntry keyEntry = new MacKeyEntry(algorithm,
              keyLengthBits);

      // Validate the key entry.
      getMacEngine(keyEntry);

      if (null != map) {
        map.put(keyEntry.getKeyID(), keyEntry);
        // TODO: publish key in ADS. (mark key "blocked" in map
        // until registered? OTOH, Key should be in local map prior to
        // publication, since data could arrive from a remote OpenDS
        // instance encrypted with the key any time after publication.
        // OTOH, the key should be published in ADS before any use,
        // since that is the persistent shared secret key repository.)
      }

      return keyEntry;
    }


    /**
     * Initializes a secret key entry from the supplied parameters,
     * validates it, and registers it in the supplied map. The
     * anticipated use of this method is to import a key entry from
     * ADS.
     *
     * @param cryptoManager  The CryptoManager instance.
     *
     * @param keyIDString  The key identifier.
     *
     * @param algorithm  The algorithm for which the key entry was
     * produced.
     *
     * @param key  The cipher key.
     *
     * @param isCompromised  Mark the key as compromised, so that it
     * will not subsequently be used for new data. The key entry
     * must be maintained in order to verify existing signatures.
     *
     * @return  The key entry, if one was successfully produced.
     *
     * @throws CryptoManagerException  In case of an error in the
     * parameters used to initialize or validate the key entry.
     */
    public static MacKeyEntry importMacKeyEntry(
            final CryptoManager cryptoManager,
            final String keyIDString,
            final String algorithm,
            final byte[] key,
            final boolean isCompromised)
            throws CryptoManagerException {
      Validator.ensureNotNull(keyIDString, algorithm, key);

      final KeyEntryID keyID = new KeyEntryID(keyIDString);

      // Check map for existing key entry with the supplied keyID.
      MacKeyEntry keyEntry = getKeyEntry(cryptoManager, keyID);
      if (null != keyEntry) {
        // TODO: compare keyEntry with supplied parameters to ensure
        // equal.
        return keyEntry;
      }

      // Instantiate new entry.
      keyEntry = new MacKeyEntry(keyID, algorithm, key,
              isCompromised);

      // Validate new entry.
      getMacEngine(keyEntry);

      // Cache new entry.
      cryptoManager.macKeyEntryCache.put(keyEntry.getKeyID(),
              keyEntry);

      return keyEntry;
    }


    /**
     * Retrieve a MacKeyEntry from the MacKeyEntry Map based on
     * the algorithm name and key length.
     *
     * @param cryptoManager  The CryptoManager instance with which the
     * key entry is associated.
     *
     * @param algorithm  The MAC algorithm for which the key was
     * produced.
     *
     * @param keyLengthBits  The MAC key length in bits.
     *
     * @return  The key entry corresponding to the parameters, or null
     * if no such entry exists.
     */
    public static MacKeyEntry getKeyEntry(
            final CryptoManager cryptoManager,
            final String algorithm,
            final int keyLengthBits) {
      Validator.ensureNotNull(cryptoManager, algorithm);
      Validator.ensureTrue(0 < keyLengthBits);

      MacKeyEntry keyEntry = null;
      // search for an existing key that satisfies the request
      for (Map.Entry<KeyEntryID, MacKeyEntry> i
              : cryptoManager.macKeyEntryCache.entrySet()) {
        MacKeyEntry entry = i.getValue();
        if (! entry.isCompromised()
                && entry.getType().equals(algorithm)
                && entry.getKeyLengthBits() == keyLengthBits) {
          keyEntry = entry;
          break;
        }
      }

      // TODO: if (null == keyEntry) Does ADS monitoring thread keep
      // map updated with keys produced at other sites? Otherwise,
      // search ADS for suitable key.

      // TODO: if (null == keyEntry) consider generating key here.

      return keyEntry;
    }


    /**
     * Given a key identifier, return the associated cipher key entry
     * from the supplied map. This method would typically be used by
     * a decryption routine.
     *
     * @param cryptoManager  The CryptoManager instance with which the
     * key entry is associated.
     *
     * @param keyID  The key identifier.
     *
     * @return  The key entry associated with the key identifier.
     */
    public static MacKeyEntry getKeyEntry(
            final CryptoManager cryptoManager,
            final KeyEntryID keyID) {
      return cryptoManager.macKeyEntryCache.get(keyID);

      /* TODO: Does ADS monitorying thread keep map updated with keys
         produced at other sites? If not, fetch from ADS and update
         map (assuming a legitimate key ID, the key should exist in
         ADS because this routine is called for decryption). */
    }

    /**
     * Construct an instance of {@code MacKeyEntry} using the
     * specified parameters. This constructor would typically be used
     * for key generation.
     *
     * @param algorithm  The name of the MAC algorithm for which the
     * key entry is to be produced.
     *
     * @param keyLengthBits  The length of the requested key in bits.
     *
     * @throws CryptoManagerException If there is a problem
     * instantiating the key generator.
     */
    private MacKeyEntry(final String algorithm,
                        final int keyLengthBits)
            throws CryptoManagerException {
      // Generate a new key.
      super(algorithm, keyLengthBits);

      // copy arguments
      this.fType = new String(algorithm);
    }

    /**
     * Construct an instance of MacKeyEntry using the specified
     * parameters. This constructor would typically be used for key
     * entries imported from ADS, for which the full set of paramters
     * is known.
     *
     * @param keyID  The unique identifier of this MAC algorithm/key
     * pair.
     *
     * @param algorithm  The name of the MAC algorithm for which the
     * key entry is to be produced.
     *
     * @param key  The MAC key.
     *
     * @param isCompromised {@code false} if the key may be used
     * for signing, or {@code true} if the key is being retained only
     * for use in signature verification.
     */
    private MacKeyEntry(final KeyEntryID keyID,
                        final String algorithm,
                        final byte[] key,
                        final boolean isCompromised) {
      super(keyID, algorithm, key, isCompromised);

      // copy arguments
      this.fType = new String(algorithm);
    }


    /**
     * The algorithm for which the key entry was created.
     *
     * @return The algorithm.
     */
    public String getType() {
      return fType;
    }


    // state
    private final String fType;
  }



  /**
   * This class defines an exception that is thrown in the case of
   * problems with encryption key managagment, and is a wrapper for a
   * variety of other cipher related exceptions.
   */
  public static class CryptoManagerException extends OpenDsException
  {
    /**
     * The serial version identifier required to satisfy the compiler
     * because this class extends <CODE>java.lang.Exception</CODE>,
     * which implements the <CODE>java.io.Serializable</CODE>
     * interface. This value was generated using the
     * <CODE>serialver</CODE> command-line utility included with the
     * Java SDK.
     */
    static final long serialVersionUID = -5890763923778143774L;

    /**
     * Creates an exception with the given message.
     * @param message the message message.
     */
    public CryptoManagerException(Message message) {
      super(message);
     }

    /**
     * Creates an exception with the given message and underlying
     * cause.
     * @param message The message message.
     * @param cause  The underlying cause.
     */
    public CryptoManagerException(Message message, Exception cause) {
      super(message, cause);
    }
  }
}

