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
import java.security.InvalidAlgorithmParameterException;
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
  private final SecureRandom secureRandom = new SecureRandom();

  // The random number generator used for initialization vector
  // production.
  private final Random pseudoRandom
          = new Random(secureRandom.nextLong());

  // The map from encryption key ID to cipher algorithm name.
  private final HashMap<ByteArray, String> cipherTransformationMap
          = new HashMap<ByteArray, String>();

  // The map from encryption key ID to cipher key.
  private final HashMap<ByteArray, byte[]> secretKeyMap
          = new HashMap<ByteArray, byte[]>();

  // The preferred cipher for the Directory Server.
  private final String preferredCipherTransformation;

  // The preferred message digest algorithm for the Directory Server.
  private final String preferredDigestAlgorithm;

  // The preferred MAC algorithm for the Directory Server.
  private final String preferredMACAlgorithm;

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
    // FIXME -- Get the defaults from the configuration rather than
    // hard-coding them.
    preferredDigestAlgorithm = "SHA-1";
    preferredMACAlgorithm    = "HmacSHA1";
    preferredCipherTransformation = "AES/CBC/PKCS5Padding";

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

    byte[] keyValue = new byte[16];
    secureRandom.nextBytes(keyValue);
    try
    {
      Mac mac = Mac.getInstance(preferredMACAlgorithm);
      mac.init(new SecretKeySpec(keyValue, preferredMACAlgorithm));
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
      Cipher cipher
              = Cipher.getInstance(preferredCipherTransformation);
      cipher.init(Cipher.ENCRYPT_MODE,
              new SecretKeySpec(keyValue,
                     preferredCipherTransformation.substring(0,
                        preferredCipherTransformation.indexOf('/'))));
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
   *  Converts a UUID string into a compact byte[16] representation.
   *
   *  @param  uuidString A string reprentation of a UUID.
   *
   *  @return  A new byte[16] containing the binary representation of
   *  the UUID.
   *
   *  @throws  CryptoManagerException  If the uuidString argument does
   *  not conform to the UUID string syntax.
   */
  private static byte[] uuidToBytes(String uuidString)
          throws CryptoManagerException
  {
    UUID uuid;
    try {
      uuid = UUID.fromString(uuidString);
    }
    catch (Exception ex) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid string representation of a UUID."),
              ex);
    }
    return uuidToBytes(uuid);
  }



  /**
   *  Converts a UUID string into a compact byte[16] representation.
   *
   *  @param  uuid A UUID.
   *
   *  @return  A new byte[16] containing the binary representation of
   *  the UUID.
   */
  private static byte[] uuidToBytes(UUID uuid)
  {
    final byte[] uuidBytes = new byte[16];
    long hiBytes = uuid.getMostSignificantBits();
    long loBytes = uuid.getLeastSignificantBits();
    for (int i = 7; i >= 0; --i) {
      uuidBytes[i] = (byte)hiBytes;
      hiBytes >>>= 8;
      uuidBytes[8 + i] = (byte)loBytes;
      loBytes >>>= 8;
    }
    return uuidBytes;
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
   * Retrieves the name of the preferred MAC algorithm.
   *
   * @return  The name of the preferred MAC algorithm
   */
  public String getPreferredMACAlgorithm()
  {
    return preferredMACAlgorithm;
  }



  /**
   * Retrieves a MAC provider using the preferred algorithm.
   *
   * @return  A MAC provider using the preferred algorithm.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   *
   * @throws  InvalidKeyException  If the provided key is not
   *                               appropriate for use with the
   *                               requested MAC algorithm.
   */
  public Mac getPreferredMACProvider()
         throws NoSuchAlgorithmException, InvalidKeyException
  {
    return getMACProvider(getPreferredMACAlgorithm());
  }



  /**
   * Retrieves a MAC provider using the specified algorithm.
   *
   * @param  macAlgorithm  The algorithm to use for the MAC provider.
   *
   * @return  A MAC provider using the specified algorithm.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   *
   * @throws  InvalidKeyException  If the provided key is not
   *                               appropriate for use with the
   *                               requested MAC algorithm.
   */
  public Mac getMACProvider(String macAlgorithm)
         throws NoSuchAlgorithmException, InvalidKeyException
  {
    Mac mac = Mac.getInstance(macAlgorithm);
    byte[] keyValue = new byte[16];
    secureRandom.nextBytes(keyValue);
    mac.init(new SecretKeySpec(keyValue, macAlgorithm));

    return mac;
  }



  /**
   * Retrieves a byte array containing a MAC based on the provided
   * data, using the preferred MAC algorithm.
   *
   * @param  data  The data for which to generate the MAC.
   *
   * @return  A byte array containing the generated MAC.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] mac(byte[] data)
         throws NoSuchAlgorithmException
  {
    return Mac.getInstance(preferredMACAlgorithm).doFinal(data);
  }



  /**
   * Retrieves a byte array containing a MAC based on the provided
   * data, using the requested MAC algorithm.
   *
   * @param  macAlgorithm  The algorithm to use for the MAC.
   * @param  data          The data for which to generate the MAC.
   *
   * @return  A byte array containing the generated MAC.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] mac(String macAlgorithm, byte[] data)
         throws NoSuchAlgorithmException
  {
    return Mac.getInstance(macAlgorithm).doFinal(data);
  }



  /**
   * Retrieves a byte array containing a MAC based on the data read
   * from the provided input stream, using the preferred MAC
   * algorithm.  Data will be read until the end of the stream is
   * reached.
   *
   * @param  inputStream  The input stream from which the data is to
   *                      be read.
   *
   * @return  A byte array containing the generated MAC.
   *
   * @throws  IOException  If a problem occurs while reading data from
   *                       the provided stream.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] mac(InputStream inputStream)
         throws IOException, NoSuchAlgorithmException
  {
    Mac mac = Mac.getInstance(preferredMACAlgorithm);

    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }

      mac.update(buffer, 0, bytesRead);
    }

    return mac.doFinal();
  }



  /**
   * Retrieves a byte array containing a MAC based on the data read
   * from the provided input stream, using the preferred MAC
   * algorithm.  Data will be read until the end of the stream is
   * reached.
   *
   * @param  macAlgorithm  The algorithm to use for the MAC.
   * @param  inputStream   The input stream from which the data is to
   *                       be read.
   *
   * @return  A byte array containing the generated MAC.
   *
   * @throws  IOException  If a problem occurs while reading data from
   *                       the provided stream.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   */
  public byte[] mac(String macAlgorithm, InputStream inputStream)
         throws IOException, NoSuchAlgorithmException
  {
    Mac mac = Mac.getInstance(macAlgorithm);

    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }

      mac.update(buffer, 0, bytesRead);
    }

    return mac.doFinal();
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
   * Retrieves an encryption mode cipher using the specified
   * algorithm.
   *
   * @param  cipherTransformation  The algorithm/mode/padding to use
   *         for the cipher.
   *
   * @param  keyIdentifier  The key identifier assigned to the cipher
   *         key used by the cipher.
   *
   * @return  An encryption mode cipher using the preferred algorithm.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   *
   * @throws  NoSuchPaddingException  If the requested padding
   *                                  mechanism is not supported or is
   *                                  unavailable.
   *
   * @throws  InvalidKeyException  If the provided key is not
   *                               appropriate for use with the
   *                               requested cipher algorithm.
   *
   * @throws  InvalidAlgorithmParameterException
   *               If an internal problem occurs as a result of the
   *               initialization vector used.
   *
   * @throws  CryptoManagerException If there is a problem managing
   *          the encryption key.
   */
  private Cipher getEncryptionCipher(String cipherTransformation,
                                     byte[] keyIdentifier)
         throws NoSuchAlgorithmException, NoSuchPaddingException,
                InvalidKeyException,
                InvalidAlgorithmParameterException,
                CryptoManagerException
  {
    Validator.ensureNotNull(cipherTransformation, keyIdentifier);

    byte[] keyID = null;
    for (Map.Entry<ByteArray, String> cipherEntry
            : cipherTransformationMap.entrySet()) {
      if (cipherEntry.getValue().equals(cipherTransformation)) {
        keyID = cipherEntry.getKey().array();
        break;
      }
    }

    byte[] secretKey;
    if (null != keyID) {
      secretKey = secretKeyMap.get(new ByteArray(keyID));
    } else {
      // generate a new key
      secretKey = new byte[16]; // FIXME: not all keys are 128-bits
      secureRandom.nextBytes(secretKey);
      keyID = uuidToBytes(UUID.randomUUID());
      final ByteArray mapKey = new ByteArray(keyID);
      secretKeyMap.put(mapKey, secretKey);
      cipherTransformationMap.put(mapKey, cipherTransformation);
    }

    // E.g., AES/CBC/PKCS5Padding -> AES
    final int separatorIndex = cipherTransformation.indexOf('/');
    final String cipherAlgorithm = (0 < separatorIndex)
            ? cipherTransformation.substring(0, separatorIndex)
            : cipherTransformation;

    // TODO: initialization vector length: key length?
    final byte[] initializationVector = new byte[16];
    pseudoRandom.nextBytes(initializationVector);

    final Cipher cipher = Cipher.getInstance(cipherTransformation);
    cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(secretKey, cipherAlgorithm),
                new IvParameterSpec(initializationVector));

    try {
      System.arraycopy(keyID, 0, keyIdentifier, 0, keyID.length);
    }
    catch (Exception ex) {
      throw new CryptoManagerException(
                    // TODO: i18n
                    Message.raw("Error copying out key identifier."),
                    ex);
    }

    return cipher;
  }



  /**
   * Retrieves a decryption mode cipher using the algorithm and
   * encryption key specified by the key identifier and the supplied
   * initialization vector.
   *
   * @param  keyID  The ID of the key to be used for decryption.
   * @param  initializationVector The initialization vector to be used
   *         for decryption.
   *
   * @return  A decryption mode cipher using the specified parameters.
   *
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   *
   * @throws  NoSuchPaddingException  If the requested padding
   *                                  mechanism is not supported or is
   *                                  unavailable.
   *
   * @throws  InvalidKeyException  If the provided key is not
   *                               appropriate for use with the
   *                               requested cipher algorithm.
   *
   * @throws  InvalidAlgorithmParameterException
   *               If an internal problem occurs as a result of the
   *               initialization vector used.
   *
   * @throws  CryptoManagerException If an invalid keyID is supplied.
   */
  private Cipher getDecryptionCipher(byte[] keyID,
                                     byte[] initializationVector)
         throws NoSuchAlgorithmException, NoSuchPaddingException,
                InvalidKeyException,
                InvalidAlgorithmParameterException,
                CryptoManagerException
  {
    Validator.ensureNotNull(keyID, initializationVector);

    final ByteArray mapKey = new ByteArray(keyID);
    final String cipherTransformation
            = cipherTransformationMap.get(mapKey);
    final byte[] secretKey = secretKeyMap.get(mapKey);
    if (null == cipherTransformation || null == secretKey) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid encryption key identifier %s.",
                          mapKey));
    }

    // E.g., AES/CBC/PKCS5Padding -> AES
    final int separatorIndex = cipherTransformation.indexOf('/');
    final String cipherAlgorithm = (0 < separatorIndex)
            ? cipherTransformation.substring(0, separatorIndex)
            : cipherTransformation;

    final Cipher cipher = Cipher.getInstance(cipherTransformation);
    cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(secretKey, cipherAlgorithm),
                new IvParameterSpec(initializationVector));

    return cipher;
  }



  /**
   * Encrypts the data in the provided byte array using the preferred
   * cipher transformation.
   *
   * @param  data  The data to be encrypted.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *                                    attempting to encrypt the
   *                                    data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key.
   */
  public byte[] encrypt(byte[] data)
         throws GeneralSecurityException,
                CryptoManagerException
  {
    return encrypt(getPreferredCipherTransformation(), data);
  }



  /**
   * Encrypts the data in the provided byte array using the requested
   * cipher algorithm.
   *
   * @param  cipherTransformation  The algorithm to use to encrypt the
   *                          data.
   * @param  data             The data to be encrypted.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *                                    attempting to encrypt the
   *                                    data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key.
   */
  public byte[] encrypt(String cipherTransformation, byte[] data)
         throws GeneralSecurityException, CryptoManagerException
  {
    Validator.ensureNotNull(cipherTransformation, data);

    final byte[] keyID = new byte[16];// FIXME: key id length constant
    final Cipher cipher
            = getEncryptionCipher(cipherTransformation, keyID);
    final byte[] iv = cipher.getIV();
    final int prologueLength = keyID.length + iv.length;
    final int dataLength = cipher.getOutputSize(data.length);
    final byte[] cipherText = new byte[prologueLength + dataLength];
    System.arraycopy(keyID, 0, cipherText, 0, keyID.length);
    System.arraycopy(iv, 0, cipherText, keyID.length, iv.length);
    System.arraycopy(cipher.doFinal(data), 0, cipherText,
                     prologueLength, dataLength);
    return cipherText;
  }



  /**
   * Encrypts the data in the provided byte array using the preferred
   * cipher algorithm.
   *
   * @param  outputStream The output stream to be wrapped by the
   *         returned output stream.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *                                    attempting to encrypt the
   *                                    data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key.
   */
  public CipherOutputStream getCipherOutputStream(
          OutputStream outputStream)
          throws GeneralSecurityException, CryptoManagerException
  {
    return getCipherOutputStream(getPreferredCipherTransformation(),
                                 outputStream);
  }



  /**
   * Encrypts the data in the provided output stream using the
   * requested cipher transformation.
   *
   * @param  cipherTransformation  The algorithm to use to encrypt the
   *                          data.
   * @param  outputStream The output stream to be wrapped by the
   *         returned output stream.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *                                    attempting to encrypt the
   *                                    data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key.
   */
  public CipherOutputStream getCipherOutputStream(
          String cipherTransformation, OutputStream outputStream)
         throws GeneralSecurityException, CryptoManagerException
  {
    final byte[] keyID = new byte[16];// FIXME: key id length constant
    final Cipher cipher
            = getEncryptionCipher(cipherTransformation, keyID);
    try {
      outputStream.write(keyID);
      outputStream.write(cipher.getIV());
    }
    catch (IOException ioe) {
      throw new CryptoManagerException(
        // TODO: i18n
        Message.raw("Exception when writing CryptoManager prologue."),
        ioe);
    }
    return new CipherOutputStream(outputStream, cipher);
  }



  /**
   * Decrypts the data in the provided byte array using cipher
   * specified by the key identifier prologue to the data.
   * cipher.
   *
   * @param  data  The data to be decrypted.
   *
   * @return  A byte array containing the cleartext representation of
   *          the provided data.
   *
   * @throws  GeneralSecurityException  If a problem occurs while
   *                                    attempting to decrypt the
   *                                    data.
   *
   * @throws  CryptoManagerException  If a problem occurs reading the
   *          key identifier or initialization vector from the data
   *          prologue.
   */
  public byte[] decrypt(byte[] data)
         throws GeneralSecurityException,
                CryptoManagerException
  {
    final byte[] keyID = new byte[16];
    final byte[] iv = new byte[16]; // FIXME: always 128 bits?
    final int prologueLength = keyID.length + iv.length;
    try {
      System.arraycopy(data, 0, keyID, 0, keyID.length);
      System.arraycopy(data, keyID.length, iv, 0, iv.length);
    }
    catch (Exception ex) {
      throw new CryptoManagerException(
        // TODO: i18n
        Message.raw("Exception when reading CryptoManager prologue."),
        ex);
    }

    Cipher cipher = getDecryptionCipher(keyID, iv);
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
   * @throws  NoSuchAlgorithmException  If the requested algorithm is
   *                                    not supported or is
   *                                    unavailable.
   *
   * @throws  NoSuchPaddingException  If the requested padding
   *                                  mechanism is not supported or is
   *                                  unavailable.
   *
   * @throws  InvalidKeyException  If the provided key is not
   *                               appropriate for use with the
   *                               requested cipher algorithm.
   *
   * @throws  InvalidAlgorithmParameterException
   *               If an internal problem occurs as a result of the
   *               initialization vector used.
   *
   * @throws  CryptoManagerException If there is a problem reading the
   *          key ID or initialization vector from the input stream.
   */
  public CipherInputStream getCipherInputStream(
                                            InputStream inputStream)
          throws NoSuchAlgorithmException, NoSuchPaddingException,
                 InvalidKeyException,
                 InvalidAlgorithmParameterException,
                 CryptoManagerException
  {
    byte[] keyID = new byte[16];
    byte[] iv = new byte[16];  // FIXME: not all ivs are key length
    try {
      if (keyID.length != inputStream.read(keyID)
              || iv.length != inputStream.read(iv)){
        throw new CryptoManagerException(
          // TODO: i18n
          Message.raw(
            "Stream underflow when reading CryptoManager prologue."));
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
                          getDecryptionCipher(keyID, iv));
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

