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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The key wrapping scheme for a Java runtime which offers no RSA-OAEP, such as one whose only
 * provider is SunPKCS11: the hybrid key transport of NIST SP 800-56B Rev. 2, section 9.3. The RSA
 * secret value encapsulation (RSASVE, section 7.2.1) establishes a secret value with the public
 * key of the receiving instance, the one-step key derivation of NIST SP 800-56C Rev. 2 over
 * SHA-256 turns it into a key wrapping key, and the AES key wrap with padding of NIST SP 800-38F
 * (KWP) wraps the secret key with that key. The runtime has to offer a raw RSA cipher, a SHA-256
 * digest and AES-KWP, nothing more.
 * <p>
 * A wrapped key is the encapsulated secret value, on as many bytes as the RSA modulus and preceded
 * by that length on two bytes, followed by the KWP-wrapped secret key. The derivation binds the
 * scheme name, the identifier of the instance key the secret value is encapsulated with and the
 * algorithm of the wrapped key, the fields stored next to the wrapped key in a ds-cfg-symmetric-key
 * value, so that none of them can be changed without the unwrapping failing. No nonce of the
 * receiving instance enters the derivation: keys are wrapped for an instance from its published
 * certificate alone, without the instance taking part.
 */
final class RsaSveKeyTransport
{
  /**
   * The name of the scheme: the value of the key-wrapping-transformation property which selects
   * it, and the transformation recorded in the ds-cfg-symmetric-key values it produces.
   */
  static final String NAME = "RSASVE/KDF-SHA256/AESKWP";

  private static final String RSA_TRANSFORMATION = "RSA/ECB/NoPadding";
  private static final String DIGEST_ALGORITHM = "SHA-256";
  /** The names of AES-KWP: the standard one, then the only one Bouncy Castle FIPS knows. */
  private static final String[] KEY_WRAP_TRANSFORMATIONS = { "AES/KWP/NoPadding", "AESWrapPad" };
  /** The length in bits of the key wrapping key: one SHA-256 hash. */
  private static final int KEY_WRAPPING_KEY_LENGTH_BITS = 256;
  /** The counter of the only hash the key derivation computes. */
  private static final byte[] KDF_COUNTER = { 0, 0, 0, 1 };

  private RsaSveKeyTransport()
  {
    // Not instantiable.
  }

  /**
   * Returns whether a key wrapping transformation names this scheme. Like the names of the Java
   * transformations, the name is not case sensitive.
   *
   * @param transformation
   *          The key wrapping transformation.
   * @return Whether it names this scheme.
   */
  static boolean isScheme(final String transformation)
  {
    return NAME.equalsIgnoreCase(transformation);
  }

  /**
   * Checks that this Java runtime offers every algorithm the scheme needs.
   *
   * @throws GeneralSecurityException
   *           If it does not, naming the first algorithm missing.
   */
  static void checkSupported() throws GeneralSecurityException
  {
    Cipher.getInstance(RSA_TRANSFORMATION);
    MessageDigest.getInstance(DIGEST_ALGORITHM);
    getKeyWrapCipher();
  }

  /**
   * Wraps a secret key for the instance which holds the private key of an RSA public key.
   *
   * @param publicKey
   *          The RSA public key of the instance.
   * @param secretKey
   *          The secret key to wrap.
   * @param wrappingKeyID
   *          The identifier of the instance key, as stored next to the wrapped key.
   * @param random
   *          The random generator of the secret value.
   * @return The wrapped key.
   * @throws GeneralSecurityException
   *           If the public key is not an RSA key, or if the runtime cannot wrap the key.
   */
  static byte[] wrap(final PublicKey publicKey, final SecretKey secretKey, final String wrappingKeyID,
      final SecureRandom random) throws GeneralSecurityException
  {
    if (!(publicKey instanceof RSAPublicKey))
    {
      throw new InvalidKeyException(
          NAME + " needs an RSA public key, not " + (publicKey != null ? publicKey.getAlgorithm() : null));
    }
    final BigInteger modulus = ((RSAPublicKey) publicKey).getModulus();
    final int length = (modulus.bitLength() + 7) / 8;
    final byte[] secretValue = generateSecretValue(modulus, length, random);
    try
    {
      final Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
      rsa.init(Cipher.ENCRYPT_MODE, publicKey);
      // On as many bytes as the modulus whatever the provider: the unwrapping takes the length of
      // the encapsulated value for the length of the secret value.
      final byte[] encapsulated = toLength(rsa.doFinal(secretValue), length);
      final Cipher keyWrap = getKeyWrapCipher();
      keyWrap.init(Cipher.WRAP_MODE, deriveKeyWrappingKey(secretValue, wrappingKeyID, secretKey.getAlgorithm()));
      final byte[] wrapped = keyWrap.wrap(secretKey);
      return ByteBuffer.allocate(2 + encapsulated.length + wrapped.length)
          .putShort((short) encapsulated.length)
          .put(encapsulated)
          .put(wrapped)
          .array();
    }
    finally
    {
      Arrays.fill(secretValue, (byte) 0);
    }
  }

  /**
   * Unwraps a secret key which {@link #wrap} wrapped with the public key of this instance.
   *
   * @param privateKey
   *          The RSA private key of this instance.
   * @param wrappedKey
   *          The wrapped key.
   * @param keyAlgorithm
   *          The algorithm of the wrapped key, as stored next to it.
   * @param wrappingKeyID
   *          The identifier of the instance key, as stored next to the wrapped key.
   * @return The secret key.
   * @throws GeneralSecurityException
   *           If the wrapped key is malformed, if it was not wrapped for this private key with
   *           these fields, or if the runtime cannot unwrap it.
   */
  static SecretKey unwrap(final PrivateKey privateKey, final byte[] wrappedKey, final String keyAlgorithm,
      final String wrappingKeyID) throws GeneralSecurityException
  {
    if (privateKey == null)
    {
      throw new InvalidKeyException(NAME + " needs the private key of this instance");
    }
    if (wrappedKey.length < 2)
    {
      throw new InvalidKeyException(NAME + " wrapped key is truncated");
    }
    final ByteBuffer buffer = ByteBuffer.wrap(wrappedKey);
    final int length = buffer.getShort() & 0xFFFF;
    if (length == 0 || buffer.remaining() <= length)
    {
      throw new InvalidKeyException(NAME + " wrapped key is truncated");
    }
    final byte[] encapsulated = new byte[length];
    buffer.get(encapsulated);
    final byte[] wrapped = new byte[buffer.remaining()];
    buffer.get(wrapped);

    final Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
    rsa.init(Cipher.DECRYPT_MODE, privateKey);
    final byte[] decrypted = rsa.doFinal(encapsulated);
    // Bouncy Castle FIPS drops the leading zero bytes of the secret value.
    final byte[] secretValue = toLength(decrypted, length);
    try
    {
      final Cipher keyWrap = getKeyWrapCipher();
      keyWrap.init(Cipher.UNWRAP_MODE, deriveKeyWrappingKey(secretValue, wrappingKeyID, keyAlgorithm));
      return (SecretKey) keyWrap.unwrap(wrapped, keyAlgorithm, Cipher.SECRET_KEY);
    }
    finally
    {
      Arrays.fill(decrypted, (byte) 0);
      Arrays.fill(secretValue, (byte) 0);
    }
  }

  /**
   * Draws the secret value of RSASVE.GENERATE: a random integer z with 1 < z < n - 1, on as many
   * bytes as the modulus n. A draw outside the interval is discarded and drawn again.
   */
  private static byte[] generateSecretValue(final BigInteger modulus, final int length, final SecureRandom random)
  {
    final BigInteger upperBound = modulus.subtract(BigInteger.ONE);
    final byte[] secretValue = new byte[length];
    while (true)
    {
      random.nextBytes(secretValue);
      final BigInteger z = new BigInteger(1, secretValue);
      if (z.compareTo(BigInteger.ONE) > 0 && z.compareTo(upperBound) < 0)
      {
        return secretValue;
      }
    }
  }

  /**
   * Returns the unsigned big-endian integer of the bytes on exactly {@code length} bytes, the way
   * NIST SP 800-56B converts an integer to a byte string: leading zero bytes are added or dropped.
   */
  private static byte[] toLength(final byte[] bytes, final int length) throws InvalidKeyException
  {
    int start = 0;
    while (bytes.length - start > length && bytes[start] == 0)
    {
      start++;
    }
    final int significant = bytes.length - start;
    if (significant > length)
    {
      throw new InvalidKeyException(NAME + " value is longer than the RSA modulus");
    }
    if (significant == length)
    {
      return start == 0 ? bytes : Arrays.copyOfRange(bytes, start, bytes.length);
    }
    final byte[] result = new byte[length];
    System.arraycopy(bytes, start, result, length - significant, significant);
    return result;
  }

  /**
   * Derives the key wrapping key with the one-step key derivation of NIST SP 800-56C Rev. 2, with
   * SHA-256 as the auxiliary function: one hash of the counter 1, the secret value and the fixed
   * information yields its 256 bits.
   */
  private static SecretKey deriveKeyWrappingKey(final byte[] secretValue, final String wrappingKeyID,
      final String keyAlgorithm) throws NoSuchAlgorithmException
  {
    final MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
    digest.update(KDF_COUNTER);
    digest.update(secretValue);
    digest.update(fixedInfo(wrappingKeyID, keyAlgorithm));
    final byte[] key = digest.digest();
    try
    {
      return new SecretKeySpec(key, "AES");
    }
    finally
    {
      Arrays.fill(key, (byte) 0);
    }
  }

  /**
   * Returns the fixed information of the key derivation, each variable-length field preceded by
   * its length on four bytes: the scheme name as AlgorithmID; an empty PartyUInfo, since the
   * wrapping instance is not identified; the identifier of the instance key as PartyVInfo; then,
   * as SuppPubInfo, the algorithm of the wrapped key and the length in bits of the key wrapping key.
   */
  private static byte[] fixedInfo(final String wrappingKeyID, final String keyAlgorithm)
  {
    final byte[] algorithmID = NAME.getBytes(UTF_8);
    final byte[] partyVInfo = wrappingKeyID.getBytes(UTF_8);
    final byte[] wrappedKeyAlgorithm = keyAlgorithm.getBytes(UTF_8);
    return ByteBuffer.allocate(5 * 4 + algorithmID.length + partyVInfo.length + wrappedKeyAlgorithm.length)
        .putInt(algorithmID.length).put(algorithmID)
        .putInt(0)
        .putInt(partyVInfo.length).put(partyVInfo)
        .putInt(wrappedKeyAlgorithm.length).put(wrappedKeyAlgorithm)
        .putInt(KEY_WRAPPING_KEY_LENGTH_BITS)
        .array();
  }

  /** Returns an AES-KWP cipher under the first of its names the runtime knows. */
  private static Cipher getKeyWrapCipher() throws GeneralSecurityException
  {
    GeneralSecurityException firstFailure = null;
    for (final String transformation : KEY_WRAP_TRANSFORMATIONS)
    {
      try
      {
        return Cipher.getInstance(transformation);
      }
      catch (NoSuchAlgorithmException | NoSuchPaddingException e)
      {
        if (firstFailure == null)
        {
          firstFailure = e;
        }
      }
    }
    throw firstFailure;
  }
}
