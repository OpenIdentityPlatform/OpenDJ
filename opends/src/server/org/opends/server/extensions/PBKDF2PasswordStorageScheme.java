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
 *      Copyright 2013 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PBKDF2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.PBKDF2PasswordStorageScheme";


  /**
   * The number of bytes of random data to use as the salt when generating the
   * hashes.
   */
  private static final int NUM_SALT_BYTES = 8;

  // The number of bytes the SHA-1 algorithm produces
  private static final int SHA1_LENGTH = 20;

  // The factory used to generate the PBKDF2 hashes.
  private SecretKeyFactory factory;

  // The lock used to provide threadsafe access to the message digest.
  private Object factoryLock;

  // The secure random number generator to use to generate the salt values.
  private SecureRandom random;

  // The current configuration for this storage scheme.
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   PBKDF2PasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    factoryLock = new Object();
    try
    {
      random = SecureRandom.getInstance(SECURE_PRNG_SHA1);
      factory = SecretKeyFactory.getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new InitializationException(null);
    }

    this.config = configuration;
    config.addPBKDF2ChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PBKDF2PasswordStorageSchemeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      PBKDF2PasswordStorageSchemeCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_PBKDF2;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] digestBytes;
    int    iterations    = config.getPBKDF2Iterations();

    synchronized(factoryLock)
    {
      try
      {
        random.nextBytes(saltBytes);

        KeySpec spec = new PBEKeySpec(plaintext.toString().toCharArray(),
            saltBytes, iterations, SHA1_LENGTH * 8);
        digestBytes = factory.generateSecret(spec).getEncoded();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
            CLASS_NAME, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }
    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);

    StringBuilder sb = new StringBuilder();
    sb.append(Integer.toString(iterations));
    sb.append(':');
    sb.append(Base64.encode(hashPlusSalt));
    return ByteString.valueOf(sb.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_PBKDF2);
    buffer.append('}');

    buffer.append(encodePassword(plaintext));

    return ByteString.valueOf(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {

    // Split the iterations from the stored value (separated by a ":")
    // Base64-decode the remaining value and take the last 8 bytes as the salt.
    int iterations;
    byte[] saltBytes;
    byte[] digestBytes = new byte[SHA1_LENGTH];
    int saltLength = 0;
    try
    {
      String stored = storedPassword.toString();
      int stored_length = stored.length();
      int pos = 0;
      while (pos < stored_length && stored.charAt(pos) != ':')
      {
        pos++;
      }
      if (pos >= (stored_length - 1) || pos == 0)
        throw new Exception();

      iterations = Integer.parseInt(stored.substring(0, pos));
      byte[] decodedBytes = Base64.decode(stored.substring(pos + 1));

      saltLength = decodedBytes.length - SHA1_LENGTH;
      if (saltLength <= 0)
      {
        Message message =
          ERR_PWSCHEME_INVALID_BASE64_DECODED_STORED_PASSWORD.get(
          storedPassword.toString());
        ErrorLogger.logError(message);
        return false;
      }
      saltBytes = new byte[saltLength];
      System.arraycopy(decodedBytes, 0, digestBytes, 0, SHA1_LENGTH);
      System.arraycopy(decodedBytes, SHA1_LENGTH, saltBytes, 0,
                       saltLength);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD.get(
          storedPassword.toString(), String.valueOf(e));
      ErrorLogger.logError(message);
      return false;
    }


    // Use the salt to generate a digest based on the provided plain-text value.
    int plainBytesLength = plaintextPassword.length();
    byte[] plainPlusSalt = new byte[plainBytesLength + saltLength];
    plaintextPassword.copyTo(plainPlusSalt);
    System.arraycopy(saltBytes, 0, plainPlusSalt, plainBytesLength,
                     saltLength);

    byte[] userDigestBytes;

    synchronized (factoryLock)
    {
      try
      {
        KeySpec spec = new PBEKeySpec(
            plaintextPassword.toString().toCharArray(), saltBytes,
            iterations, SHA1_LENGTH * 8);
        userDigestBytes = factory.generateSecret(spec).getEncoded();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        return false;
      }
    }

    return Arrays.equals(digestBytes, userDigestBytes);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsAuthPasswordSyntax()
  {
    // This storage scheme does support the authentication password syntax.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getAuthPasswordSchemeName()
  {
    return AUTH_PASSWORD_SCHEME_NAME_PBKDF2;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] digestBytes;
    int    iterations    = config.getPBKDF2Iterations();

    synchronized(factoryLock)
    {
      try
      {
        random.nextBytes(saltBytes);

        KeySpec spec = new PBEKeySpec(
            plaintext.toString().toCharArray(), saltBytes,
            iterations, SHA1_LENGTH * 8);
        digestBytes = factory.generateSecret(spec).getEncoded();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
            CLASS_NAME, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }
    // Encode and return the value.
    StringBuilder authPWValue = new StringBuilder();
    authPWValue.append(AUTH_PASSWORD_SCHEME_NAME_PBKDF2);
    authPWValue.append('$');
    authPWValue.append(Integer.toString(iterations));
    authPWValue.append(':');
    authPWValue.append(Base64.encode(saltBytes));
    authPWValue.append('$');
    authPWValue.append(Base64.encode(digestBytes));

    return ByteString.valueOf(authPWValue.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    byte[] saltBytes;
    byte[] digestBytes;
    int    iterations;

    try
    {
      int pos = 0;
      int length = authInfo.length();
      while (pos < length && authInfo.charAt(pos) != ':')
      {
        pos++;
      }
      if (pos >= (length - 1) || pos == 0)
        throw new Exception();
      iterations = Integer.parseInt(authInfo.substring(0, pos));
      saltBytes   = Base64.decode(authInfo.substring(pos + 1));
      digestBytes = Base64.decode(authValue);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }


    int plainBytesLength = plaintextPassword.length();
    byte[] plainPlusSalt = new byte[plainBytesLength + saltBytes.length];
    plaintextPassword.copyTo(plainPlusSalt);
    System.arraycopy(saltBytes, 0, plainPlusSalt, plainBytesLength,
                     saltBytes.length);

    byte[] userDigestBytes;

    synchronized (factoryLock)
    {
      try
      {
        KeySpec spec = new PBEKeySpec(
            plaintextPassword.toString().toCharArray(), saltBytes,
            iterations, SHA1_LENGTH * 8);
        userDigestBytes = factory.generateSecret(spec).getEncoded();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        return false;
      }
    }

    return Arrays.equals(digestBytes, userDigestBytes);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isReversible()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    Message message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_PBKDF2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {
    Message message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(AUTH_PASSWORD_SCHEME_NAME_PBKDF2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {
    // PBKDF2 should be considered secure.
    return true;
  }



  /**
   * Generates an encoded password string from the given clear-text password.
   * This method is primarily intended for use when it is necessary to generate
   * a password with the server offline (e.g., when setting the initial root
   * user password).
   *
   * @param  passwordBytes  The bytes that make up the clear-text password.
   *
   * @return  The encoded password string, including the scheme name in curly
   *          braces.
   *
   * @throws  DirectoryException  If a problem occurs during processing.
   */
  public static String encodeOffline(byte[] passwordBytes)
         throws DirectoryException
  {
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] digestBytes;
    int    iterations    = 10000;

    try
    {
      SecureRandom.getInstance(SECURE_PRNG_SHA1).nextBytes(saltBytes);

      KeySpec spec = new PBEKeySpec(
          passwordBytes.toString().toCharArray(), saltBytes,
          iterations, SHA1_LENGTH * 8);
      digestBytes = SecretKeyFactory
          .getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2)
          .generateSecret(spec).getEncoded();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }

    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);

    return "{" + STORAGE_SCHEME_NAME_PBKDF2 + "}" + iterations + ":" +
      Base64.encode(hashPlusSalt);
  }

}
