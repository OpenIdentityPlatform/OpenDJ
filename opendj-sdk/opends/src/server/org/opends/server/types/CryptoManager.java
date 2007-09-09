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

  // The map from encryption key ID to SecretKeyEntry (cache).
  private final HashMap<ByteArray, SecretKeyEntry> secretKeyEntryMap
          = new HashMap<ByteArray, SecretKeyEntry>();

  // The preferred cipher for the Directory Server.
  private final String preferredCipherTransformation;

  // The preferred key length for the preferred cipher.
  private final int preferredCipherTransformationKeyLength;

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
    // TODO -- Get the defaults from the configuration rather than
    // hard-coding them.
    preferredDigestAlgorithm = "SHA-1";
    preferredMACAlgorithm    = "HmacSHA1";
    preferredCipherTransformation = "AES/CBC/PKCS5Padding";
    preferredCipherTransformationKeyLength = 128;

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
      // TODO: wrap like encryption
      byte[] keyValue = new byte[16];
      secureRandom.nextBytes(keyValue);
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
      SecretKeyEntry.generateKeyEntry(null,
              preferredCipherTransformation,
              preferredCipherTransformationKeyLength);
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
   *  Converts a UUID instance into a compact byte[16] representation.
   *
   *  @param  uuid A UUID instance.
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
   * This class corresponds to the encryption key entry in ADS. It is
   * used in the local cache of key entries that have been requested
   * by CryptoManager clients.
   */
  private static class SecretKeyEntry
  {
    /**
     * This method generates a key according to the key parameters,
     * and creates a key entry and registers it in the supplied map.
     *
     * @param map  The SecretKeyEntry Map in which the key is to be
     * stored. Pass null as the argument to this parameter in order to
     * validate a proposed cipher transformation and key length.
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
     */
    public static SecretKeyEntry generateKeyEntry(
            final Map<ByteArray, SecretKeyEntry> map,
            final String transformation,
            final int keyLengthBits)
    throws CryptoManagerException {

      SecretKeyEntry keyEntry;

      // check for existing uncompromised key.
      if (null != map) {
        keyEntry = getKeyEntry(map, transformation, keyLengthBits);
        if (null != keyEntry) {
          return keyEntry;
        }
      }

      // generate a new key
      if (0 != keyLengthBits % Byte.SIZE) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("keyLengthBits argument must be evenly"
                        + " divisible by %d.", Byte.SIZE));
      }

      // In case a transformation is supplied instead of an algorithm:
      // E.g., AES/CBC/PKCS5Padding -> AES
      final int separatorIndex = transformation.indexOf('/');
      final String keyAlgorithm = (0 < separatorIndex)
              ? transformation.substring(0, separatorIndex)
              : transformation;
      KeyGenerator keyGen;
      try {
        keyGen = KeyGenerator.getInstance(keyAlgorithm);
      }
      catch (NoSuchAlgorithmException ex) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Unable to produce key generator from" +
                        " cipher transformation argument %s",
                        transformation), ex);
      }
      keyGen.init(keyLengthBits, secureRandom);
      final byte[] key = keyGen.generateKey().getEncoded();

      // generate the key entry
      final byte[] keyID = uuidToBytes(UUID.randomUUID());
      keyEntry = new SecretKeyEntry(keyID, transformation,
              keyAlgorithm, key, /* compute IV length */ -1, false);

      // Validate the entry. Pass the keyAlgorithm for the cipher
      // transformation.
      final Cipher cipher
              = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);
      final byte[] iv = cipher.getIV();
      keyEntry.setIVLengthBits(
              (null == iv) ? 0 : iv.length * Byte.SIZE);

      if (null != map) {
        map.put(new ByteArray(keyID), keyEntry);
        // TODO: publish key to ADS. (mark key "blocked" in map
        // until registered?)
      }

      return keyEntry;
    }


    /**
     * Initializes a secret key entry from the supplied parameters,
     * validates it, and registers it in the supplied map. The
     * anticipated use of this method is to import a key entry from
     * ADS.
     *
     * @param map  The secret key map.
     *
     * @param keyID  The key identifier.
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
     * or any block cipher algorithm for which the transformation mode
     * does not use an initialization vector.
     *
     * @param isCompromised  Mark the key as compromised, so that it
     * will not subsequently be used for encryption. The key must be
     * maintained in order to decrypt existing ciphertext.
     *
     * @return  The key entry, if one was successfully produced.
     *
     * @throws CryptoManagerException  In case of an error in the
     * parameters used to initialize or validate the key entry.
     */
    public static SecretKeyEntry setKeyEntry(
            final Map<ByteArray, SecretKeyEntry> map,
            final byte[] keyID,
            final String transformation,
            final String keyAlgorithm,
            final byte[] key,
            final int ivLengthBits,
            final boolean isCompromised)
            throws CryptoManagerException {
      Validator.ensureNotNull(keyID, transformation, keyAlgorithm,
              key);
      Validator.ensureTrue(16 == keyID.length);
      Validator.ensureTrue(0 <= ivLengthBits);

      // Check map for existing key entry with the supplied keyID.
      SecretKeyEntry keyEntry = getKeyEntry(map, keyID);
      if (null != keyEntry) {
        // TODO: compare keyEntry with supplied parameters to ensure
        // equal.
        return keyEntry;
      }

      // Instantiate new entry.
      keyEntry = new SecretKeyEntry(keyID, transformation,
              keyAlgorithm, key, ivLengthBits, isCompromised);

      // Validate the entry. Pass the keyAlgorithm for the cipher
      // transformation.
      byte[] iv = null;
      if (0 < ivLengthBits) {
        iv = new byte[ivLengthBits * Byte.SIZE];
        pseudoRandom.nextBytes(iv);
      }
      getCipher(keyEntry, Cipher.DECRYPT_MODE, iv);

      map.put(new ByteArray(keyID), keyEntry);

      return keyEntry;
    }


    /**
     * Retrieve a SecretKeyEntry from the SecretKeyEntry Map based on
     * cipher algorithm name and key length.
     *
     * @param map  The SecretKeyEntry Map in which the key is stored.
     *
     * @param transformation  The cipher algorithm for which the key
     * was produced.
     *
     * @param keyLengthBits  The cipher key length in bits.
     *
     * @return  The key entry corresponding to the parameters, or null
     * if no such entry exists.
     */
    public static SecretKeyEntry getKeyEntry(
            final Map<ByteArray, SecretKeyEntry> map,
            final String transformation,
            final int keyLengthBits) {
      Validator.ensureNotNull(map, transformation);
      Validator.ensureTrue(0 < keyLengthBits);

      SecretKeyEntry keyEntry = null;

      // search for an existing key that satisfies the request
      for (Map.Entry<ByteArray, SecretKeyEntry> i: map.entrySet()) {
        SecretKeyEntry entry = i.getValue();
        if (! entry.fIsCompromised
                && entry.getTransformation().equals(transformation)
                && entry.fKeyLengthBits == keyLengthBits) {
          assert Arrays.equals(i.getKey().array(), entry.fKeyID);
          keyEntry = entry;
          break;
        }
      }

      // TODO: if (null == keyEntry) Does ADS monitoring thread keep
      // map updated with keys produced at other sites? Otherwise,
      // search ADS for suitable key.

      // TODO: if (null == keyEntry) consider generating key here.

      if (null != keyEntry) {
        Validator.ensureTrue(0 <= keyEntry.getIVLength(),
                "SecretKeyEntry initialization is not complete.");
      }

      return keyEntry;
    }


    /**
     * Given a key identifier, return the associated secret key entry
     * from the supplied map. This method would typically be used by
     * a decryption routine.
     *
     * @param map  The local cache of key entries.
     *
     * @param keyID  The key identifier.
     *
     * @return  The key entry associated with the key identifier.
     */
    public static SecretKeyEntry getKeyEntry(
            Map<ByteArray, SecretKeyEntry> map, byte[] keyID) {
      return map.get(new ByteArray(keyID));
      /* TODO: Does ADS monitorying thread keep map updated with keys
         produced at other sites? If not, fetch from ADS and update
         map (assuming a legitimate key ID, the key should exist in
         ADS because this routine is called for decryption). */
    }


    /**
     * Construct an instance of SecretKeyEntry using the specified
     * parameters. This constructor would typically be used for key
     * entries imported from ADS, for which the full set of paramters
     * is known, and for a new key entry, for which the initialization
     * vector length might not yet be known, but which must be set
     * before use.
     *
     * @param keyID  The unique identifier of this cipher
     * transformation/key pair.
     *
     * @param transformation  The secret-key cipher transformation for
     * which the key entry is to be produced.
     *
     * @param keyAlgorithm  The secret key cipher algorithm for which
     * the key was produced.
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
     * @param isCompromised If the key
     *
     * @throws  CryptoManagerException If there is a problem
     * instantiating a Cipher object in order to validate the supplied
     * parameters when creating a new entry.
     */
    private SecretKeyEntry( final byte[] keyID,
                            final String transformation,
                            final String keyAlgorithm,
                            final byte[] key,
                            final int ivLengthBits,
                            final boolean isCompromised)
            throws CryptoManagerException {
      Validator.ensureNotNull(keyID, transformation, key);
      Validator.ensureTrue(16 == keyID.length); // FIXME: const for id

      // copy arguments
      this.fKeyID = new byte[keyID.length];
      System.arraycopy(keyID, 0, this.fKeyID, 0, keyID.length);
      this.fTransformation = new String(transformation);
      this.fKeySpec = new SecretKeySpec(key, keyAlgorithm);
      this.fKeyLengthBits = key.length * Byte.SIZE;
      this.fIVLengthBits = ivLengthBits;
      this.fIsCompromised = isCompromised;
    }


    /**
     * The cipher transformation for which the key entry was created.
     *
     * @return The cipher transformation.
     */
    public String getTransformation() {
      return fTransformation;
    }

    /**
     * The unique identifier of this cipher transformation/key pair.
     *
     * @return The unique identifier of this cipher transformation/key
     * pair.
     */
    public byte[] getKeyID() {
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
     * The initialization vector length in bits: 0 is a stream cipher
     * or a block cipher that does not use an IV (e.g., ECB); or a
     * positive integer, typically the block size of the cipher.
     * <p>
     * This method returns -1 if the object initialization has not
     * been completed.
     *
     * @return The initialization vector length.
     */
    public int getIVLength() {
      return fIVLengthBits;
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
     * Mark a key entry as compromised. The entry will no longer be
     * eligible for use as an encryption key.
     */
    public void setIsCompromised() {
      // TODO: called from ADS monitoring thread. Lock entry?
      fIsCompromised = true;
    }

    // state
    private final byte[] fKeyID;
    private final String fTransformation;
    private final SecretKeySpec fKeySpec;
    private final int fKeyLengthBits;
    private int fIVLengthBits;
    private boolean fIsCompromised = false;
  }


  /**
   * This method produces and initialized Cipher based on this
   * SecretKeyEntry's state and the method parameters.
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
  private static Cipher getCipher(final SecretKeyEntry keyEntry,
                                  final int mode,
                                  final byte[] initializationVector)
          throws CryptoManagerException {
    Validator.ensureTrue(Cipher.ENCRYPT_MODE == mode
            || Cipher.DECRYPT_MODE == mode);
    Validator.ensureTrue(Cipher.ENCRYPT_MODE != mode
            || null == initializationVector);
    Validator.ensureTrue(-1 != keyEntry.getIVLength()
            || Cipher.ENCRYPT_MODE == mode);
    Validator.ensureTrue(null == initializationVector
            || initializationVector.length * Byte.SIZE
                                          == keyEntry.getIVLength());

    Cipher cipher;
    try {
      cipher = Cipher.getInstance(keyEntry.getTransformation());
    }
    catch (GeneralSecurityException ex) {
      // NoSuchAlgorithmException, NoSuchPaddingException
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw("Invalid cipher transformation specified"
                      + " %s.", keyEntry.getTransformation()), ex);
    }

    try {
      if (0 < keyEntry.getIVLength()) {
          byte[] iv;
          if (Cipher.ENCRYPT_MODE == mode
                  && null == initializationVector) {
            iv = new byte[keyEntry.getIVLength() / Byte.SIZE];
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
                   preferredCipherTransformationKeyLength, data);
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

    SecretKeyEntry keyEntry = SecretKeyEntry.getKeyEntry(
            secretKeyEntryMap, cipherTransformation, keyLengthBits);
    if (null == keyEntry) {
      keyEntry = SecretKeyEntry.generateKeyEntry(secretKeyEntryMap,
              cipherTransformation, keyLengthBits);
    }

    final Cipher cipher
            = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);

    final byte[] keyID = keyEntry.getKeyID();
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
                preferredCipherTransformationKeyLength, outputStream);
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

    SecretKeyEntry keyEntry = SecretKeyEntry.getKeyEntry(
            secretKeyEntryMap, cipherTransformation, keyLengthBits);
    if (null == keyEntry) {
      keyEntry = SecretKeyEntry.generateKeyEntry(secretKeyEntryMap,
              cipherTransformation, keyLengthBits);
    }

    final Cipher cipher
            = getCipher(keyEntry, Cipher.ENCRYPT_MODE, null);
    final byte[] keyID = keyEntry.getKeyID();
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
    final byte[] keyID = new byte[16]; //FIXME: key length constant
    try {
      System.arraycopy(data, 0, keyID, 0, keyID.length);
    }
    catch (Exception ex) {
      throw new CryptoManagerException(
              // TODO: i18n
              Message.raw(
                      "Exception when reading key identifier from" +
                              " data prologue."), ex);
    }

    SecretKeyEntry keyEntry
            = SecretKeyEntry.getKeyEntry(secretKeyEntryMap, keyID);
    if (null == keyEntry) {
      throw new CryptoManagerException(
              // TODO: i18N
              Message.raw("Invalid key identifier in data" +
                      " prologue."));
    }

    byte[] iv = null;
    if (0 < keyEntry.getIVLength()) {
      iv = new byte[keyEntry.getIVLength()/Byte.SIZE];
      try {
        System.arraycopy(data, keyID.length, iv, 0, iv.length);
      }
      catch (Exception ex) {
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Exception when reading initialization" +
                        " vector from data prologue."), ex);
      }
    }

    final Cipher cipher
            = getCipher(keyEntry, Cipher.DECRYPT_MODE, iv);
    final int prologueLength
            = keyID.length + ((null == iv) ? 0 : iv.length);
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
    SecretKeyEntry keyEntry;
    byte[] iv = null;
    try {
      final byte[] keyID = new byte[16]; //FIXME: key length constant
      if (keyID.length != inputStream.read(keyID)){
        throw new CryptoManagerException(
                // TODO: i18n
                Message.raw("Stream underflow when reading key" +
                        " identifier from data prologue."));
      }

      keyEntry = SecretKeyEntry.getKeyEntry(secretKeyEntryMap, keyID);
      if (null == keyEntry) {
        throw new CryptoManagerException(
                // TODO: i18N
             Message.raw("Invalid key identifier in data prologue."));
      }

      if (0 < keyEntry.getIVLength()) {
        iv = new byte[keyEntry.getIVLength() / Byte.SIZE];
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

