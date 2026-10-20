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

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.opends.server.util.StaticUtils;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the RSASVE key transport scheme of the crypto manager. */
@SuppressWarnings("javadoc")
public class RsaSveKeyTransportTestCase extends CryptoTestCase
{
  private static final String KEY_ID = "0123456789ABCDEF0123456789ABCDEF";
  /** The providers of the Java runtime, which offer AES/KWP/NoPadding from Java 17 on. */
  private static final String JDK = "jdk";
  /**
   * Bouncy Castle FIPS first and no provider offering AES/KWP/NoPadding: AES-KWP only under the
   * name AESWrapPad, and a raw RSA which drops the leading zero bytes of what it decrypts.
   */
  private static final String BC_FIPS = "bcfips";

  /** Created before any test installs Bouncy Castle FIPS, so that it comes from the SUN provider. */
  private final SecureRandom random = new SecureRandom();
  private KeyPair keyPair;

  @BeforeClass
  public void generateKeyPair() throws Exception
  {
    keyPair = rsaKeyPair(2048);
  }

  @DataProvider
  public Object[][] providers()
  {
    return new Object[][] { { JDK }, { BC_FIPS } };
  }

  @DataProvider
  public Object[][] keys()
  {
    final Object[][] keys = { { "DES", 8 }, { "AES", 16 }, { "HmacSHA1", 20 }, { "DESede", 24 }, { "AES", 32 } };
    final Object[][] rows = new Object[keys.length * 2][];
    for (int i = 0; i < keys.length; i++)
    {
      rows[2 * i] = new Object[] { JDK, keys[i][0], keys[i][1] };
      rows[2 * i + 1] = new Object[] { BC_FIPS, keys[i][0], keys[i][1] };
    }
    return rows;
  }

  /** Keys of every length unwrap to themselves: the 20 bytes of an HmacSHA1 key need the padding of KWP. */
  @Test(dataProvider = "keys")
  public void testWrappedKeyUnwrapsToTheSameKey(final String providers, final String algorithm, final int length)
      throws Exception
  {
    final SecretKey key = new SecretKeySpec(bytes(length, 0x3C), algorithm);
    inProviders(providers, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, random);
      final SecretKey unwrapped = RsaSveKeyTransport.unwrap(keyPair, wrapped, algorithm, KEY_ID);
      assertThat(unwrapped.getAlgorithm()).isEqualTo(algorithm);
      assertThat(unwrapped.getEncoded()).isEqualTo(key.getEncoded());
      return null;
    });
  }

  /**
   * The key wrapping key is derived from the encapsulated secret value: another private key
   * recovers another value and cannot unwrap. The other modulus is the larger one, so that the
   * encapsulated value is in its range and its private key decrypts it instead of refusing it.
   */
  @Test
  public void testAnotherPrivateKeyCannotUnwrap() throws Exception
  {
    KeyPair other;
    do
    {
      other = rsaKeyPair(2048);
    }
    while (modulus(other).compareTo(modulus(keyPair)) <= 0);
    final KeyPair otherKeyPair = other;
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(BC_FIPS, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, random);
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(otherKeyPair, wrapped, "AES", KEY_ID))
          .isInstanceOf(GeneralSecurityException.class);
      return null;
    });
  }

  /** The derivation binds the fields stored next to the wrapped key: changing either one fails the unwrapping. */
  @Test
  public void testFieldsStoredNextToTheWrappedKeyAreBound() throws Exception
  {
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(BC_FIPS, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, random);
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID.toLowerCase()))
          .as("another instance key identifier").isInstanceOf(GeneralSecurityException.class);
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "HmacSHA256", KEY_ID))
          .as("another key algorithm").isInstanceOf(GeneralSecurityException.class);
      return null;
    });
  }

  /**
   * The wrapped key of a known secret value. What this version stores in cn=secret keys is what
   * every later version has to unwrap, so the derivation and the key wrap are pinned to a value
   * computed independently of this implementation: SHA-256 of the counter 1, the secret value and
   * the fixed information, then AES-KWP of the key with it. The key wrap of both the Java runtime and
   * Bouncy Castle FIPS has to give it.
   */
  @Test(dataProvider = "providers")
  public void testKnownAnswer(final String providers) throws Exception
  {
    final byte[] secretValue = new byte[256];
    for (int i = 0; i < secretValue.length; i++)
    {
      secretValue[i] = (byte) (i + 1);
    }
    final Draws draws = new Draws(secretValue);
    final SecretKey key =
        new SecretKeySpec(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 }, "AES");
    inProviders(providers, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, draws);
      assertThat(ByteBuffer.wrap(wrapped).getShort()).isEqualTo((short) 256);
      final byte[] wrappedKey = Arrays.copyOfRange(wrapped, 2 + 256, wrapped.length);
      assertThat(StaticUtils.bytesToHexNoSpace(wrappedKey))
          .isEqualToIgnoringCase("A29EDD3A5DDBD441B69FB866DDE179CF6CC59C86B54A1AF5");
      return null;
    });
  }

  @DataProvider
  public Object[][] secretValuesOutsideTheInterval()
  {
    return new Object[][] {
      { "0", new byte[256] },
      { "1", toBytes(BigInteger.ONE) },
      { "n - 1", toBytes(modulus(keyPair).subtract(BigInteger.ONE)) },
      { "2^2048 - 1", bytes(256, 0xFF) } };
  }

  /** RSASVE.GENERATE draws z with 1 < z < n - 1: a value outside is discarded and drawn again. */
  @Test(dataProvider = "secretValuesOutsideTheInterval")
  public void testSecretValueOutsideTheIntervalIsDrawnAgain(final String value, final byte[] outside)
      throws Exception
  {
    final Draws draws = new Draws(outside, bytes(256, 0x5A));
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(BC_FIPS, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, draws);
      assertThat(draws.count()).as(value).isEqualTo(2);
      assertThat(RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID).getEncoded())
          .isEqualTo(key.getEncoded());
      return null;
    });
  }

  /** Bouncy Castle FIPS drops the leading zero bytes of a decrypted secret value, which the derivation needs. */
  @Test
  public void testSecretValueWithALeadingZeroByteUnwraps() throws Exception
  {
    final byte[] secretValue = bytes(256, 0x5A);
    secretValue[0] = 0;
    final Draws draws = new Draws(secretValue);
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(BC_FIPS, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, draws);
      assertThat(RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID).getEncoded())
          .isEqualTo(key.getEncoded());
      return null;
    });
  }

  /**
   * A provider may drop the leading zero bytes of the encapsulated value as well, which the
   * wrapping puts back: the value is stored on as many bytes as the modulus, the only length the
   * unwrapping accepts.
   */
  @Test
  public void testEncapsulatedValueWithALeadingZeroByteKeepsTheModulusLength() throws Exception
  {
    final BigInteger n = modulus(keyPair);
    final BigInteger e = ((RSAPublicKey) keyPair.getPublic()).getPublicExponent();
    final BigInteger upperBound = n.subtract(BigInteger.ONE);
    // About one secret value in 256 has an encapsulated value z^e mod n below 2^2040.
    BigInteger z;
    do
    {
      z = new BigInteger(n.bitLength(), random);
    }
    while (z.compareTo(BigInteger.ONE) <= 0 || z.compareTo(upperBound) >= 0 || z.modPow(e, n).bitLength() > 2040);
    final Draws draws = new Draws(toBytes(z));
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(BC_FIPS, () ->
    {
      final Provider droppingRsa = new Provider("LeadingZeroDroppingRsa", "1.0", "Raw RSA without leading zeros") {};
      droppingRsa.put("Cipher.RSA", LeadingZeroDroppingRawRsa.class.getName());
      Security.insertProviderAt(droppingRsa, 1);
      try
      {
        final byte[] wrapped = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, draws);
        assertThat(ByteBuffer.wrap(wrapped).getShort()).isEqualTo((short) 256);
        assertThat(wrapped[2]).isEqualTo((byte) 0);
        assertThat(RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID).getEncoded())
            .isEqualTo(key.getEncoded());
      }
      finally
      {
        Security.removeProvider(droppingRsa.getName());
      }
      return null;
    });
  }

  /** Nothing assumes a 2048-bit modulus: instance keys generated before 2015 have 1024 bits. */
  @Test
  public void testSmallerModulusRoundTrips() throws Exception
  {
    final KeyPair small = rsaKeyPair(1024);
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    inProviders(JDK, () ->
    {
      final byte[] wrapped = RsaSveKeyTransport.wrap(small.getPublic(), key, KEY_ID, random);
      assertThat(ByteBuffer.wrap(wrapped).getShort()).isEqualTo((short) 128);
      assertThat(RsaSveKeyTransport.unwrap(small, wrapped, "AES", KEY_ID).getEncoded())
          .isEqualTo(key.getEncoded());
      return null;
    });
  }

  /** An instance key which is not an RSA key is refused with an exception the crypto manager reports. */
  @Test
  public void testNonRsaPublicKeyIsRefusedWithASecurityException() throws Exception
  {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    final PublicKey ecKey = generator.generateKeyPair().getPublic();
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    assertThatThrownBy(() -> RsaSveKeyTransport.wrap(ecKey, key, KEY_ID, random))
        .isInstanceOf(InvalidKeyException.class);
  }

  /**
   * The unwrapping refuses an instance key pair without its private key, as a trust store entry
   * holding only a certificate gives, or whose public key is not an RSA key, with an exception the
   * crypto manager reports and before any cipher.
   */
  @Test
  public void testUnusableInstanceKeyPairIsRefusedWithASecurityException() throws Exception
  {
    final byte[] wrapped = { 0, 3, 1, 2, 3, 4 };
    assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(new KeyPair(keyPair.getPublic(), null), wrapped, "AES", KEY_ID))
        .isInstanceOf(InvalidKeyException.class)
        .hasMessageContaining("needs the private key");
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    final KeyPair ecKeyPair = new KeyPair(generator.generateKeyPair().getPublic(), keyPair.getPrivate());
    assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(ecKeyPair, wrapped, "AES", KEY_ID))
        .isInstanceOf(InvalidKeyException.class)
        .hasMessageContaining("needs an RSA public key");
  }

  @DataProvider
  public Object[][] malformedWrappedKeys()
  {
    return new Object[][] {
      { "empty", new byte[0] },
      { "one byte", new byte[] { 1 } },
      { "zero length", new byte[] { 0, 0, 1, 2, 3 } },
      { "length beyond the end", new byte[] { 0, 9, 1, 2, 3 } },
      { "no wrapped key after the encapsulated value", new byte[] { 0, 3, 1, 2, 3 } } };
  }

  /** A malformed value in cn=secret keys is refused with an exception the crypto manager reports. */
  @Test(dataProvider = "malformedWrappedKeys")
  public void testMalformedWrappedKeyIsRefusedWithASecurityException(final String what, final byte[] wrapped)
  {
    assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID))
        .as(what).isInstanceOf(InvalidKeyException.class).hasMessageContaining("truncated");
  }

  @DataProvider
  public Object[][] encapsulatedValuesRefusedBeforeDecrypting()
  {
    final BigInteger n = modulus(keyPair);
    return new Object[][] {
      { "one byte shorter than the modulus", bytes(255, 0x5A), "not as long as the RSA modulus" },
      { "longer than the modulus", bytes(300, 0x5A), "not as long as the RSA modulus" },
      { "0", new byte[256], "out of range" },
      { "1", toBytes(BigInteger.ONE), "out of range" },
      { "n - 1", toBytes(n.subtract(BigInteger.ONE)), "out of range" },
      { "n", toBytes(n), "out of range" },
      { "2^2048 - 1", bytes(256, 0xFF), "out of range" } };
  }

  /**
   * An encapsulated value which is not on as many bytes as the modulus, or not an integer c with
   * 1 < c < n - 1, is refused before the private key decrypts it, whatever the providers: Bouncy
   * Castle FIPS throws an ArrayIndexOutOfBoundsException for a value longer than the modulus.
   */
  @Test(dataProvider = "encapsulatedValuesRefusedBeforeDecrypting")
  public void testEncapsulatedValueIsCheckedBeforeDecrypting(final String what, final byte[] encapsulated,
      final String message) throws Exception
  {
    final byte[] wrapped = wrappedKey(encapsulated, bytes(24, 0x3C));
    final Callable<Void> unwrap = () ->
    {
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID))
          .as(what).isInstanceOf(InvalidKeyException.class).hasMessageContaining(message);
      return null;
    };
    unwrap.call();
    inProviders(BC_FIPS, unwrap);
  }

  @DataProvider
  public Object[][] encapsulatedValuesInRange()
  {
    final BigInteger n = modulus(keyPair);
    final Object[][] values = {
      { "2", toBytes(BigInteger.valueOf(2)) },
      { "n - 2", toBytes(n.subtract(BigInteger.valueOf(2))) },
      { "0x5A on every byte", bytes(256, 0x5A) } };
    final Object[][] rows = new Object[values.length * 2][];
    for (int i = 0; i < values.length; i++)
    {
      rows[2 * i] = new Object[] { JDK, values[i][0], values[i][1] };
      rows[2 * i + 1] = new Object[] { BC_FIPS, values[i][0], values[i][1] };
    }
    return rows;
  }

  /**
   * Whatever secret value the private key recovers from an encapsulated value in range, a wrapped
   * key which does not unwrap is refused with one and the same message.
   */
  @Test(dataProvider = "encapsulatedValuesInRange")
  public void testUnwrapFailureHasOneMessage(final String providers, final String what, final byte[] encapsulated)
      throws Exception
  {
    final SecretKey key = new SecretKeySpec(bytes(16, 0x3C), "AES");
    final Callable<Void> unwrap = () ->
    {
      final byte[] genuine = RsaSveKeyTransport.wrap(keyPair.getPublic(), key, KEY_ID, random);
      final byte[] wrapped = wrappedKey(encapsulated, Arrays.copyOfRange(genuine, 2 + 256, genuine.length));
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID))
          .as(what).isInstanceOf(InvalidKeyException.class)
          .hasMessage(RsaSveKeyTransport.NAME + " cannot unwrap the key");
      return null;
    };
    inProviders(providers, unwrap);
  }

  /**
   * The support check names AES/KWP/NoPadding when the runtime offers raw RSA and SHA-256 but no
   * AES-KWP, as a Java 11 runtime does without Bouncy Castle FIPS.
   */
  @Test
  public void testCheckSupportedNamesTheMissingKeyWrap() throws Exception
  {
    withoutKeyWrap(() ->
    {
      assertThatThrownBy(RsaSveKeyTransport::checkSupported)
          .isInstanceOf(GeneralSecurityException.class)
          .hasMessageContaining("AES/KWP/NoPadding");
      return null;
    });
  }

  /**
   * The unwrapping names AES/KWP/NoPadding on a runtime which lacks it, as a server which does not
   * use the scheme but receives keys wrapped with it does: no cipher depends on the value, so such a
   * runtime is not reported as a value which cannot be unwrapped.
   */
  @Test
  public void testUnwrapNamesTheMissingKeyWrap() throws Exception
  {
    final byte[] wrapped = wrappedKey(toBytes(BigInteger.valueOf(2)), bytes(24, 0x3C));
    withoutKeyWrap(() ->
    {
      assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(keyPair, wrapped, "AES", KEY_ID))
          .isInstanceOf(GeneralSecurityException.class)
          .hasMessageContaining("AES/KWP/NoPadding");
      return null;
    });
  }

  /**
   * The unwrapping reports why the RSA cipher refuses the private key, as a provider which cannot
   * use the key of the trust store does: the private key does not depend on the value either, so
   * such a runtime is not reported as a value which cannot be unwrapped.
   */
  @Test
  public void testUnwrapReportsWhyTheRsaCipherRefusesThePrivateKey() throws Exception
  {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    final PrivateKey ecKey = generator.generateKeyPair().getPrivate();
    final Throwable refusal =
        catchThrowable(() -> Cipher.getInstance("RSA/ECB/NoPadding").init(Cipher.DECRYPT_MODE, ecKey));
    assertThat(refusal).isInstanceOf(InvalidKeyException.class);
    final byte[] wrapped = wrappedKey(toBytes(BigInteger.valueOf(2)), bytes(24, 0x3C));
    assertThatThrownBy(() -> RsaSveKeyTransport.unwrap(new KeyPair(keyPair.getPublic(), ecKey), wrapped, "AES", KEY_ID))
        .isInstanceOf(InvalidKeyException.class)
        .hasMessage(refusal.getMessage());
  }

  /**
   * With Bouncy Castle FIPS installed first, as in the FIPS mode of OpenDJ, Bouncy Castle FIPS does
   * the key wrap even where the Java runtime offers AES/KWP/NoPadding too, a name it does not know.
   */
  @Test
  public void testKeyWrapComesFromBcFipsWhenItIsFirst() throws Exception
  {
    assertThat(withBcFipsFirst(() -> RsaSveKeyTransport.getKeyWrapCipher().getProvider().getName()))
        .isEqualTo(BouncyCastleFipsProvider.PROVIDER_NAME);
  }

  /** Runs the action with the providers of the Java runtime, or with AES-KWP from Bouncy Castle FIPS only. */
  private static void inProviders(final String providers, final Callable<Void> action) throws Exception
  {
    final boolean jdkOffersKwp = Security.getProviders("Cipher.AES/KWP/NoPadding") != null;
    if (JDK.equals(providers))
    {
      if (!jdkOffersKwp)
      {
        throw new SkipException("This Java runtime offers no AES/KWP/NoPadding");
      }
      action.call();
    }
    else if (jdkOffersKwp)
    {
      withoutJceService("Cipher", "AES/KWP/NoPadding", action, new BouncyCastleFipsProvider());
    }
    else
    {
      withBcFipsFirst(action);
    }
  }

  private static KeyPair rsaKeyPair(final int bits) throws Exception
  {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "SunRsaSign");
    generator.initialize(bits);
    return generator.generateKeyPair();
  }

  private static BigInteger modulus(final KeyPair keyPair)
  {
    return ((RSAPublicKey) keyPair.getPublic()).getModulus();
  }

  private static byte[] bytes(final int length, final int value)
  {
    final byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) value);
    return bytes;
  }

  /** Returns a wrapped key: the length of the encapsulated value on two bytes, that value, then the KWP-wrapped key. */
  private static byte[] wrappedKey(final byte[] encapsulated, final byte[] wrapped)
  {
    return ByteBuffer.allocate(2 + encapsulated.length + wrapped.length)
        .putShort((short) encapsulated.length)
        .put(encapsulated)
        .put(wrapped)
        .array();
  }

  /** Returns the value on 256 bytes, big-endian, without the sign byte BigInteger may add. */
  private static byte[] toBytes(final BigInteger value)
  {
    final byte[] bytes = value.toByteArray();
    final byte[] result = new byte[256];
    final int length = Math.min(bytes.length, result.length);
    System.arraycopy(bytes, bytes.length - length, result, result.length - length, length);
    return result;
  }

  /**
   * A random generator which hands out the given values, one per draw, the last one again once
   * they are all drawn, and counts the draws. Created outside any provider fixture, so that its
   * own default generator comes from the SUN provider.
   */
  private static final class Draws extends SecureRandom
  {
    private static final long serialVersionUID = 1L;
    private final byte[][] values;
    private final AtomicInteger draws = new AtomicInteger();

    private Draws(final byte[]... values)
    {
      this.values = values;
    }

    @Override
    public void nextBytes(final byte[] bytes)
    {
      final byte[] value = values[Math.min(draws.getAndIncrement(), values.length - 1)];
      assertThat(value).hasSize(bytes.length);
      System.arraycopy(value, 0, bytes, 0, bytes.length);
    }

    int count()
    {
      return draws.get();
    }
  }

  /**
   * A raw RSA cipher which returns what it encrypts or decrypts on as few bytes as the value needs,
   * without its leading zero bytes.
   */
  public static final class LeadingZeroDroppingRawRsa extends CipherSpi
  {
    private final ByteArrayOutputStream input = new ByteArrayOutputStream();
    private RSAKey key;
    private BigInteger exponent;

    @Override
    protected void engineSetMode(final String mode) throws NoSuchAlgorithmException
    {
      if (!"ECB".equalsIgnoreCase(mode))
      {
        throw new NoSuchAlgorithmException(mode);
      }
    }

    @Override
    protected void engineSetPadding(final String padding) throws NoSuchPaddingException
    {
      if (!"NoPadding".equalsIgnoreCase(padding))
      {
        throw new NoSuchPaddingException(padding);
      }
    }

    @Override
    protected int engineGetBlockSize()
    {
      return 0;
    }

    @Override
    protected int engineGetOutputSize(final int inputLength)
    {
      return (key.getModulus().bitLength() + 7) / 8;
    }

    @Override
    protected byte[] engineGetIV()
    {
      return null;
    }

    @Override
    protected AlgorithmParameters engineGetParameters()
    {
      return null;
    }

    @Override
    protected void engineInit(final int mode, final Key key, final SecureRandom random) throws InvalidKeyException
    {
      if (mode == Cipher.ENCRYPT_MODE && key instanceof RSAPublicKey)
      {
        exponent = ((RSAPublicKey) key).getPublicExponent();
      }
      else if (mode == Cipher.DECRYPT_MODE && key instanceof RSAPrivateKey)
      {
        exponent = ((RSAPrivateKey) key).getPrivateExponent();
      }
      else
      {
        throw new InvalidKeyException("Mode " + mode + " with " + key.getClass().getName());
      }
      this.key = (RSAKey) key;
      input.reset();
    }

    @Override
    protected void engineInit(final int mode, final Key key, final AlgorithmParameterSpec params,
        final SecureRandom random) throws InvalidKeyException
    {
      engineInit(mode, key, random);
    }

    @Override
    protected void engineInit(final int mode, final Key key, final AlgorithmParameters params,
        final SecureRandom random) throws InvalidKeyException
    {
      engineInit(mode, key, random);
    }

    @Override
    protected byte[] engineUpdate(final byte[] in, final int offset, final int length)
    {
      input.write(in, offset, length);
      return new byte[0];
    }

    @Override
    protected int engineUpdate(final byte[] in, final int offset, final int length, final byte[] out,
        final int outOffset)
    {
      input.write(in, offset, length);
      return 0;
    }

    @Override
    protected byte[] engineDoFinal(final byte[] in, final int offset, final int length)
    {
      input.write(in, offset, length);
      final byte[] result = new BigInteger(1, input.toByteArray()).modPow(exponent, key.getModulus()).toByteArray();
      input.reset();
      int start = 0;
      while (start < result.length - 1 && result[start] == 0)
      {
        start++;
      }
      return Arrays.copyOfRange(result, start, result.length);
    }

    @Override
    protected int engineDoFinal(final byte[] in, final int offset, final int length, final byte[] out,
        final int outOffset)
    {
      final byte[] result = engineDoFinal(in, offset, length);
      System.arraycopy(result, 0, out, outOffset, result.length);
      return result.length;
    }
  }
}
