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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.PBKDF2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a Directory Server password storage scheme based on the
 * PBKDF2 algorithm defined in RFC 2898.  This is a one-way digest algorithm
 * so there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  This
 * implementation uses a configurable number of iterations.
 */
public class PBKDF2PasswordStorageScheme
    extends PasswordStorageScheme<PBKDF2PasswordStorageSchemeCfg>
    implements ConfigurationChangeListener<PBKDF2PasswordStorageSchemeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.extensions.PBKDF2PasswordStorageScheme";

  /** The number of bytes of random data to use as the salt when generating the hashes. */
  private static final int NUM_SALT_BYTES = 8;

  /** The number of bytes the SHA-1 algorithm produces. */
  private static final int SHA1_LENGTH = 20;

  /** The secure random number generator to use to generate the salt values. */
  private SecureRandom random;

  /** The current configuration for this storage scheme. */
  private volatile PBKDF2PasswordStorageSchemeCfg config;

  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public PBKDF2PasswordStorageScheme()
  {
    super();
  }

  @Override
  public void initializePasswordStorageScheme(PBKDF2PasswordStorageSchemeCfg configuration)
      throws ConfigException, InitializationException
  {
    try
    {
      random = SecureRandom.getInstance(SECURE_PRNG_SHA1);
      // Just try to verify if the algorithm is supported.
      SecretKeyFactory.getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new InitializationException(null);
    }

    this.config = configuration;
    config.addPBKDF2ChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(PBKDF2PasswordStorageSchemeCfg configuration,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(PBKDF2PasswordStorageSchemeCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult();
  }

  @Override
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_PBKDF2;
  }

  @Override
  public ByteString encodePassword(ByteSequence plaintext)
      throws DirectoryException
  {
    byte[] saltBytes      = new byte[NUM_SALT_BYTES];
    int    iterations     = config.getPBKDF2Iterations();

    byte[] digestBytes = encodeWithRandomSalt(plaintext, saltBytes, iterations,random);
    byte[] hashPlusSalt = concatenateHashPlusSalt(saltBytes, digestBytes);

    return ByteString.valueOfUtf8(iterations + ":" + Base64.encode(hashPlusSalt));
  }

  @Override
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
      throws DirectoryException
  {
    return ByteString.valueOfUtf8('{' + STORAGE_SCHEME_NAME_PBKDF2 + '}' + encodePassword(plaintext));
  }

  @Override
  public boolean passwordMatches(ByteSequence plaintextPassword, ByteSequence storedPassword) {
    // Split the iterations from the stored value (separated by a ':')
    // Base64-decode the remaining value and take the last 8 bytes as the salt.
    try
    {
      final String stored = storedPassword.toString();
      final int pos = stored.indexOf(':');
      if (pos == -1)
      {
        throw new Exception();
      }

      final int iterations = Integer.parseInt(stored.substring(0, pos));
      byte[] decodedBytes = Base64.decode(stored.substring(pos + 1));

      final int saltLength = decodedBytes.length - SHA1_LENGTH;
      if (saltLength <= 0)
      {
        logger.error(ERR_PWSCHEME_INVALID_BASE64_DECODED_STORED_PASSWORD, storedPassword);
        return false;
      }

      final byte[] digestBytes = new byte[SHA1_LENGTH];
      final byte[] saltBytes = new byte[saltLength];
      System.arraycopy(decodedBytes, 0, digestBytes, 0, SHA1_LENGTH);
      System.arraycopy(decodedBytes, SHA1_LENGTH, saltBytes, 0, saltLength);
      return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD, storedPassword, e);
      return false;
    }
  }

  @Override
  public boolean supportsAuthPasswordSyntax()
  {
    return true;
  }

  @Override
  public String getAuthPasswordSchemeName()
  {
    return AUTH_PASSWORD_SCHEME_NAME_PBKDF2;
  }

  @Override
  public ByteString encodeAuthPassword(ByteSequence plaintext)
      throws DirectoryException
  {
    byte[] saltBytes      = new byte[NUM_SALT_BYTES];
    int    iterations     = config.getPBKDF2Iterations();
    byte[] digestBytes = encodeWithRandomSalt(plaintext, saltBytes, iterations,random);

    // Encode and return the value.
    return ByteString.valueOfUtf8(AUTH_PASSWORD_SCHEME_NAME_PBKDF2 + '$'
        + iterations + ':' + Base64.encode(saltBytes) + '$' + Base64.encode(digestBytes));
  }

  @Override
  public boolean authPasswordMatches(ByteSequence plaintextPassword, String authInfo, String authValue)
  {
    try
    {
      int pos = authInfo.indexOf(':');
      if (pos == -1)
      {
        throw new Exception();
      }
      int iterations = Integer.parseInt(authInfo.substring(0, pos));
      byte[] saltBytes   = Base64.decode(authInfo.substring(pos + 1));
      byte[] digestBytes = Base64.decode(authValue);
      return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return false;
    }
  }

  @Override
  public boolean isReversible()
  {
    return false;
  }

  @Override
  public ByteString getPlaintextValue(ByteSequence storedPassword)
      throws DirectoryException
  {
    LocalizableMessage message = ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_PBKDF2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }

  @Override
  public ByteString getAuthPasswordPlaintextValue(String authInfo, String authValue)
      throws DirectoryException
  {
    LocalizableMessage message = ERR_PWSCHEME_NOT_REVERSIBLE.get(AUTH_PASSWORD_SCHEME_NAME_PBKDF2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }

  @Override
  public boolean isStorageSchemeSecure()
  {
    return true;
  }

  /**
   * Generates an encoded password string from the given clear-text password.
   * This method is primarily intended for use when it is necessary to generate a password with the server
   * offline (e.g., when setting the initial root user password).
   *
   * @param  passwordBytes  The bytes that make up the clear-text password.
   * @return  The encoded password string, including the scheme name in curly braces.
   * @throws  DirectoryException  If a problem occurs during processing.
   */
  public static String encodeOffline(byte[] passwordBytes)
      throws DirectoryException
  {
    byte[] saltBytes      = new byte[NUM_SALT_BYTES];
    int    iterations     = 10000;

    final ByteString password = ByteString.wrap(passwordBytes);
    byte[] digestBytes = encodeWithRandomSalt(password, saltBytes, iterations);
    byte[] hashPlusSalt = concatenateHashPlusSalt(saltBytes, digestBytes);

    return '{' + STORAGE_SCHEME_NAME_PBKDF2 + '}' + iterations + ':' + Base64.encode(hashPlusSalt);
  }

  private static byte[] encodeWithRandomSalt(ByteString plaintext, byte[] saltBytes, int iterations)
      throws DirectoryException
  {
    try
    {
      final SecureRandom random = SecureRandom.getInstance(SECURE_PRNG_SHA1);
      return encodeWithRandomSalt(plaintext, saltBytes, iterations, random);
    }
    catch (DirectoryException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw cannotEncodePassword(e);
    }
  }

  private static byte[] encodeWithSalt(ByteSequence plaintext, byte[] saltBytes, int iterations)
      throws DirectoryException
  {
    final char[] plaintextChars = plaintext.toString().toCharArray();
    try
    {
      final SecretKeyFactory factory = SecretKeyFactory.getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2);
      KeySpec spec = new PBEKeySpec(plaintextChars, saltBytes, iterations, SHA1_LENGTH * 8);
      return factory.generateSecret(spec).getEncoded();
    }
    catch (Exception e)
    {
      throw cannotEncodePassword(e);
    }
    finally
    {
      Arrays.fill(plaintextChars, '0');
    }
  }

  private boolean encodeAndMatch(ByteSequence plaintext, byte[] saltBytes, byte[] digestBytes, int iterations)
  {
    try
    {
      final byte[] userDigestBytes = encodeWithSalt(plaintext, saltBytes, iterations);
      return Arrays.equals(digestBytes, userDigestBytes);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  private static byte[] encodeWithRandomSalt(ByteSequence plaintext, byte[] saltBytes,
                                             int iterations, SecureRandom random)
      throws DirectoryException
  {
    random.nextBytes(saltBytes);
    return encodeWithSalt(plaintext, saltBytes, iterations);
  }

  private static DirectoryException cannotEncodePassword(Exception e)
  {
    logger.traceException(e);

    LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(CLASS_NAME, getExceptionMessage(e));
    return new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
  }

  private static byte[] concatenateHashPlusSalt(byte[] saltBytes, byte[] digestBytes) {
    final byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];
    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length, NUM_SALT_BYTES);
    return hashPlusSalt;
  }
}
