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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import org.opends.server.config.ConfigException;

import javax.crypto.Mac;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import javax.net.ssl.SSLContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DataFormatException;
import java.util.SortedSet;

/**
 This interface defines the methods to call to access cryptographic
 services including encryption and hashing; in particular, when the
 ciphertext or HMAC is produced on one directory server instance and
 is to be consumed on another.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)public interface CryptoManager {
  /**
   * Retrieves the name of the preferred message digest algorithm.
   *
   * @return  The name of the preferred message digest algorithm
   */
  String getPreferredMessageDigestAlgorithm();

  /**
   * Retrieves a <CODE>MessageDigest</CODE> object that may be used to
   * generate digests using the preferred digest algorithm.
   *
   * @return  A <CODE>MessageDigest</CODE> object that may be used to
   *          generate digests using the preferred digest algorithm.
   *
   * @throws java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  MessageDigest getPreferredMessageDigest()
         throws NoSuchAlgorithmException;

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
   * @throws  java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  MessageDigest getMessageDigest(String digestAlgorithm)
         throws NoSuchAlgorithmException;

  /**
   * Retrieves a byte array containing a message digest based on the
   * provided data, using the preferred digest algorithm.
   *
   * @param  data  The data to be digested.
   *
   * @return  A byte array containing the generated message digest.
   *
   * @throws  java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  byte[] digest(byte[] data)
         throws NoSuchAlgorithmException;

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
   * @throws  java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  byte[] digest(String digestAlgorithm, byte[] data)
         throws NoSuchAlgorithmException;

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
   * @throws java.io.IOException  If a problem occurs while reading
   * data from the provided stream.
   *
   * @throws  java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  byte[] digest(InputStream inputStream)
         throws IOException, NoSuchAlgorithmException;

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
   * @throws  java.io.IOException  If a problem occurs while reading
   * data from the provided stream.
   *
   * @throws  java.security.NoSuchAlgorithmException  If the requested
   * algorithm is not supported or is unavailable.
   */
  byte[] digest(String digestAlgorithm,
                       InputStream inputStream)
         throws IOException, NoSuchAlgorithmException;

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
  String getMacEngineKeyEntryID()
          throws CryptoManagerException;

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
  String getMacEngineKeyEntryID(String macAlgorithm,
                                       int keyLengthBits)
         throws CryptoManagerException;

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
   * is invalid or there is a problem instantiating the MAC engine
   * from the parameters in the referenced key entry.
   */
  Mac getMacEngine(String keyEntryID)
          throws CryptoManagerException;

  /**
   * Encrypts the data in the provided byte array using the preferred
   * cipher transformation.
   *
   * @param  data  The plain-text data to be encrypted.
   *
   * @return  A byte array containing the encrypted representation of
   *          the provided data.
   *
   * @throws java.security.GeneralSecurityException  If a problem
   * occurs while encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  byte[] encrypt(byte[] data)
         throws GeneralSecurityException, CryptoManagerException;

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
   * @throws  java.security.GeneralSecurityException  If a problem
   * occurs while encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs managing the
   *          encryption key or producing the cipher.
   */
  byte[] encrypt(String cipherTransformation,
                        int keyLengthBits,
                        byte[] data)
         throws GeneralSecurityException, CryptoManagerException;

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
  CipherOutputStream getCipherOutputStream(
          OutputStream outputStream) throws CryptoManagerException;

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
  CipherOutputStream getCipherOutputStream(
          String cipherTransformation, int keyLengthBits,
          OutputStream outputStream)
         throws CryptoManagerException;

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
   * @throws  java.security.GeneralSecurityException  If a problem
   * occurs while encrypting the data.
   *
   * @throws  CryptoManagerException  If a problem occurs reading the
   *          key identifier or initialization vector from the data
   *          prologue, or using these values to initialize a Cipher.
   */
  byte[] decrypt(byte[] data)
         throws GeneralSecurityException,
                CryptoManagerException;

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
  CipherInputStream getCipherInputStream(
          InputStream inputStream) throws CryptoManagerException;

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
   * @param  srcOff The start offset of the source data.
   * @param  srcLen The maximum number of source data bytes to
   *                compress.
   * @param  dst  The array into which the compressed data should be
   *              written.
   * @param  dstOff The start offset of the compressed data.
   * @param  dstLen The maximum number of bytes of compressed data.
   *
   * @return  The number of bytes of compressed data, or -1 if it was
   *          not possible to actually compress the data.
   */
  int compress(byte[] src, int srcOff, int srcLen,
               byte[] dst, int dstOff, int dstLen);

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
   * @param  src  The array containing the raw data to compress.
   * @param  srcOff The start offset of the source data.
   * @param  srcLen The maximum number of source data bytes to
   *                compress.
   * @param  dst  The array into which the compressed data should be
   *              written.
   * @param  dstOff The start offset of the compressed data.
   * @param  dstLen The maximum number of bytes of compressed data.
   *
   * @return  A positive value containing the number of bytes of
   *          uncompressed data written into the destination buffer,
   *          or a negative value whose absolute value is the size of
   *          the destination buffer required to fully decompress the
   *          provided data.
   *
   * @throws java.util.zip.DataFormatException  If a problem occurs
   * while attempting to uncompress the data.
   */
  int uncompress(byte[] src, int srcOff, int srcLen,
                 byte[] dst, int dstOff, int dstLen)
         throws DataFormatException;

  /**
   * Create an SSL context that may be used for communication to
   * another ADS component.
   *
   * @param sslCertNickname The name of the local certificate to use,
   *                        or null if none is specified.
   * @return A new SSL Context.
   * @throws org.opends.server.config.ConfigException If the context
   * could not be created.
   */
  SSLContext getSslContext(String sslCertNickname)
       throws ConfigException;

  /**
   * Get the name of the local certificate to use for SSL.
   * @return The name of the local certificate to use for SSL.
   */
  String getSslCertNickname();

  /**
   * Determine whether SSL encryption is enabled.
   * @return true if SSL encryption is enabled.
   */
  boolean isSslEncryption();

  /**
   * Get the set of enabled SSL protocols.
   * @return The set of enabled SSL protocols.
   */
  SortedSet<String> getSslProtocols();

  /**
   * Get the set of enabled SSL cipher suites.
   * @return The set of enabled SSL cipher suites.
   */
  SortedSet<String> getSslCipherSuites();
}
