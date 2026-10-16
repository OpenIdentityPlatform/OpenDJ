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
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The key wrapping scheme for a Java runtime which offers no RSA-OAEP, such as one whose only
 * provider is SunPKCS11: a scheme of OpenDJ's own, built on the hybrid key transport of NIST
 * SP 800-56B Rev. 2, section 9.3, but not the KAS1-basic scheme to the letter, since no nonce of
 * the receiving instance enters the derivation. The RSA secret value encapsulation (RSASVE,
 * section 7.2.1) establishes a secret value with the public key of the receiving instance, the
 * one-step key derivation of NIST SP 800-56C Rev. 2 over SHA-256 turns it into a key wrapping key,
 * and the AES key wrap with padding of NIST SP 800-38F (KWP) wraps the secret key with that key.
 * The runtime has to offer a raw RSA cipher, a SHA-256 digest and AES-KWP, nothing more.
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
  /**
   * The names of AES-KWP: first the only one Bouncy Castle FIPS knows, so that it does the key wrap
   * when it is installed first, as in the FIPS mode of OpenDJ, even where the Java runtime offers
   * AES-KWP too; then the standard one.
   */
  private static final String[] KEY_WRAP_TRANSFORMATIONS = { "AESWrapPad", "AES/KWP/NoPadding" };
  /** The length in bits of the key wrapping key: one SHA-256 hash. */
  private static final int KEY_WRAPPING_KEY_LENGTH_BITS = 256;
  /** The counter of the only hash the key derivation computes. */
  private static final byte[] KDF_COUNTER = { 0, 0, 0, 1 };
  /** The object identifier of the key usage extension of an X.509 certificate. */
  private static final String KEY_USAGE_OID = "2.5.29.15";
  /** The position of keyEncipherment in the key usage of an X.509 certificate. */
  private static final int KEY_ENCIPHERMENT = 2;

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
   * Wraps a secret key for the instance which holds the private key of a certificate. Like
   * {@code Cipher.init(Cipher.WRAP_MODE, certificate)}, it refuses an X.509 certificate whose key
   * usage extension is critical and does not allow keyEncipherment.
   *
   * @param certificate
   *          The certificate of the instance, with its RSA public key.
   * @param secretKey
   *          The secret key to wrap.
   * @param wrappingKeyID
   *          The identifier of the instance key, as stored next to the wrapped key.
   * @param random
   *          The random generator of the secret value.
   * @return The wrapped key.
   * @throws GeneralSecurityException
   *           If the key usage of the certificate does not allow wrapping keys, if its public key
   *           is not an RSA key, or if the runtime cannot wrap the key.
   */
  static byte[] wrap(final Certificate certificate, final SecretKey secretKey, final String wrappingKeyID,
      final SecureRandom random) throws GeneralSecurityException
  {
    if (certificate instanceof X509Certificate)
    {
      final X509Certificate x509Certificate = (X509Certificate) certificate;
      final Set<String> criticalExtensions = x509Certificate.getCriticalExtensionOIDs();
      final boolean[] keyUsage = x509Certificate.getKeyUsage();
      if (criticalExtensions != null && criticalExtensions.contains(KEY_USAGE_OID)
          && keyUsage != null && keyUsage.length > KEY_ENCIPHERMENT && !keyUsage[KEY_ENCIPHERMENT])
      {
        throw new InvalidKeyException(NAME + " needs a certificate whose key usage allows keyEncipherment");
      }
    }
    return wrap(certificate.getPublicKey(), secretKey, wrappingKeyID, random);
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
    final BigInteger modulus = getModulus(publicKey);
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
   * <p>
   * The encapsulated value is checked against the modulus before the private key decrypts it, and
   * every failure from the decryption on is reported with one and the same message, the cause
   * aside: what the private key recovers from a value does not show in how the value is refused.
   * A runtime which lacks one of the ciphers, or whose RSA cipher cannot use the private key, is
   * reported before that, with its own reason.
   *
   * @param instanceKeyPair
   *          The RSA key pair of this instance. The modulus is taken from its public key, since a
   *          private key held by a token does not always expose it.
   * @param wrappedKey
   *          The wrapped key.
   * @param keyAlgorithm
   *          The algorithm of the wrapped key, as stored next to it.
   * @param wrappingKeyID
   *          The identifier of the instance key, as stored next to the wrapped key.
   * @return The secret key.
   * @throws GeneralSecurityException
   *           If the wrapped key is malformed, if it was not wrapped for this key pair with these
   *           fields, or if the runtime cannot unwrap it.
   */
  static SecretKey unwrap(final KeyPair instanceKeyPair, final byte[] wrappedKey, final String keyAlgorithm,
      final String wrappingKeyID) throws GeneralSecurityException
  {
    final PrivateKey privateKey = instanceKeyPair.getPrivate();
    if (privateKey == null)
    {
      throw new InvalidKeyException(NAME + " needs the private key of this instance");
    }
    final BigInteger modulus = getModulus(instanceKeyPair.getPublic());
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
    // RSASVE.RECOVER takes a value on as many bytes as the modulus, and RSADP an integer c with
    // 1 < c < n - 1: a value which is neither does not reach the private key.
    if (length != (modulus.bitLength() + 7) / 8)
    {
      throw new InvalidKeyException(NAME + " encapsulated value is not as long as the RSA modulus");
    }
    final byte[] encapsulated = new byte[length];
    buffer.get(encapsulated);
    final BigInteger c = new BigInteger(1, encapsulated);
    if (c.compareTo(BigInteger.ONE) <= 0 || c.compareTo(modulus.subtract(BigInteger.ONE)) >= 0)
    {
      throw new InvalidKeyException(NAME + " encapsulated value is out of range");
    }
    final byte[] wrapped = new byte[buffer.remaining()];
    buffer.get(wrapped);

    // Neither cipher depends on the value, nor does the private key: a runtime which lacks one of
    // the ciphers, or whose RSA cipher cannot use the private key, is told apart from a value which
    // cannot be unwrapped.
    final Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
    rsa.init(Cipher.DECRYPT_MODE, privateKey);
    final Cipher keyWrap = getKeyWrapCipher();
    byte[] decrypted = null;
    byte[] secretValue = null;
    try
    {
      decrypted = rsa.doFinal(encapsulated);
      // Bouncy Castle FIPS drops the leading zero bytes of the secret value.
      secretValue = toLength(decrypted, length);
      keyWrap.init(Cipher.UNWRAP_MODE, deriveKeyWrappingKey(secretValue, wrappingKeyID, keyAlgorithm));
      return (SecretKey) keyWrap.unwrap(wrapped, keyAlgorithm, Cipher.SECRET_KEY);
    }
    catch (GeneralSecurityException | RuntimeException e)
    {
      throw new InvalidKeyException(NAME + " cannot unwrap the key", e);
    }
    finally
    {
      if (decrypted != null)
      {
        Arrays.fill(decrypted, (byte) 0);
      }
      if (secretValue != null)
      {
        Arrays.fill(secretValue, (byte) 0);
      }
    }
  }

  /** Returns the modulus of an RSA public key, refusing any other key. */
  private static BigInteger getModulus(final PublicKey publicKey) throws InvalidKeyException
  {
    if (!(publicKey instanceof RSAPublicKey))
    {
      throw new InvalidKeyException(
          NAME + " needs an RSA public key, not " + (publicKey != null ? publicKey.getAlgorithm() : null));
    }
    return ((RSAPublicKey) publicKey).getModulus();
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

  /**
   * Returns an AES-KWP cipher under the first of its names the runtime knows. When it knows none,
   * the failure names the standard one.
   */
  static Cipher getKeyWrapCipher() throws GeneralSecurityException
  {
    GeneralSecurityException lastFailure = null;
    for (final String transformation : KEY_WRAP_TRANSFORMATIONS)
    {
      try
      {
        return Cipher.getInstance(transformation);
      }
      catch (NoSuchAlgorithmException | NoSuchPaddingException e)
      {
        lastFailure = e;
      }
    }
    throw lastFailure;
  }
}
