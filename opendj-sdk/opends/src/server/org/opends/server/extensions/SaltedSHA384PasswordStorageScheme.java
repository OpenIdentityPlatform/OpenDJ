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
package org.opends.server.extensions;



import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server password storage scheme based on the
 * 384-bit SHA-2 algorithm defined in FIPS 180-2.  This is a one-way digest
 * algorithm so there is no way to retrieve the original clear-text version of
 * the password from the hashed value (although this means that it is not
 * suitable for things that need the clear-text password like DIGEST-MD5).  The
 * values that it generates are also salted, which protects against dictionary
 * attacks. It does this by generating a 64-bit random salt which is appended to
 * the clear-text value.  A SHA-2 hash is then generated based on this, the salt
 * is appended to the hash, and then the entire value is base64-encoded.
 */
public class SaltedSHA384PasswordStorageScheme
       extends PasswordStorageScheme
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.SaltedSHA384PasswordStorageScheme";



  /**
   * The number of bytes of random data to use as the salt when generating the
   * hashes.
   */
  private static final int NUM_SALT_BYTES = 8;



  // The message digest that will actually be used to generate the 384-bit SHA-2
  // hashes.
  private MessageDigest messageDigest;

  // The lock used to provide threadsafe access to the message digest.
  private ReentrantLock digestLock;

  // The secure random number generator to use to generate the salt values.
  private SecureRandom random;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public SaltedSHA384PasswordStorageScheme()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {

    try
    {
      messageDigest =
           MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_384);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST;
      String message = getMessage(msgID, MESSAGE_DIGEST_ALGORITHM_SHA_384,
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }


    digestLock = new ReentrantLock();
    random     = new SecureRandom();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {

    return STORAGE_SCHEME_NAME_SALTED_SHA_384;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteString plaintext)
         throws DirectoryException
  {

    byte[] plainBytes    = plaintext.value();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plainBytes.length + NUM_SALT_BYTES];

    System.arraycopy(plainBytes, 0, plainPlusSalt,0,plainBytes.length);

    byte[] digestBytes;

    digestLock.lock();

    try
    {
      // Generate the salt and put in the plain+salt array.
      random.nextBytes(saltBytes);
      System.arraycopy(saltBytes,0, plainPlusSalt, plainBytes.length,
                       NUM_SALT_BYTES);

      // Create the hash from the concatenated value.
      digestBytes = messageDigest.digest(plainPlusSalt);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_PWSCHEME_CANNOT_ENCODE_PASSWORD;
      String message = getMessage(msgID, CLASS_NAME,
                                  stackTraceToSingleLineString(e));

      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      digestLock.unlock();
    }

    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);

    return ByteStringFactory.create(Base64.encode(hashPlusSalt));
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteString plaintext)
         throws DirectoryException
  {

    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_SALTED_SHA_384);
    buffer.append('}');

    byte[] plainBytes    = plaintext.value();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plainBytes.length + NUM_SALT_BYTES];

    System.arraycopy(plainBytes, 0, plainPlusSalt,0,plainBytes.length);

    byte[] digestBytes;

    digestLock.lock();

    try
    {
      // Generate the salt and put in the plain+salt array.
      random.nextBytes(saltBytes);
      System.arraycopy(saltBytes,0, plainPlusSalt, plainBytes.length,
                       NUM_SALT_BYTES);

      // Create the hash from the concatenated value.
      digestBytes = messageDigest.digest(plainPlusSalt);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_PWSCHEME_CANNOT_ENCODE_PASSWORD;
      String message = getMessage(msgID, CLASS_NAME,
                                  stackTraceToSingleLineString(e));

      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      digestLock.unlock();
    }

    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);
    buffer.append(Base64.encode(hashPlusSalt));

    return ByteStringFactory.create(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteString plaintextPassword,
                                 ByteString storedPassword)
  {


    // Base64-decode the stored value and take the last 8 bytes as the salt.
    byte[] saltBytes = new byte[NUM_SALT_BYTES];
    byte[] digestBytes;
    try
    {
      byte[] decodedBytes = Base64.decode(storedPassword.stringValue());

      int digestLength = decodedBytes.length - NUM_SALT_BYTES;
      digestBytes = new byte[digestLength];
      System.arraycopy(decodedBytes, 0, digestBytes, 0, digestLength);
      System.arraycopy(decodedBytes, digestLength, saltBytes, 0,
                       NUM_SALT_BYTES);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD;
      String message = getMessage(msgID, storedPassword.stringValue(),
                                  String.valueOf(e));
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.MILD_ERROR,
               message, msgID);
      return false;
    }


    // Use the salt to generate a digest based on the provided plain-text value.
    byte[] plainBytes    = plaintextPassword.value();
    byte[] plainPlusSalt = new byte[plainBytes.length + NUM_SALT_BYTES];
    System.arraycopy(plainBytes, 0, plainPlusSalt, 0, plainBytes.length);
    System.arraycopy(saltBytes, 0,plainPlusSalt, plainBytes.length,
                     NUM_SALT_BYTES);

    byte[] userDigestBytes;

    digestLock.lock();

    try
    {
      userDigestBytes = messageDigest.digest(plainPlusSalt);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      return false;
    }
    finally
    {
      digestLock.unlock();
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

    return AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_384;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodeAuthPassword(ByteString plaintext)
         throws DirectoryException
  {


    byte[] plainBytes    = plaintext.value();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plainBytes.length + NUM_SALT_BYTES];

    System.arraycopy(plainBytes, 0, plainPlusSalt, 0, plainBytes.length);

    byte[] digestBytes;

    digestLock.lock();

    try
    {
      // Generate the salt and put in the plain+salt array.
      random.nextBytes(saltBytes);
      System.arraycopy(saltBytes,0, plainPlusSalt, plainBytes.length,
                       NUM_SALT_BYTES);

      // Create the hash from the concatenated value.
      digestBytes = messageDigest.digest(plainPlusSalt);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PWSCHEME_CANNOT_ENCODE_PASSWORD;
      String message = getMessage(msgID, CLASS_NAME,
                                  stackTraceToSingleLineString(e));

      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
    finally
    {
      digestLock.unlock();
    }


    // Encode and return the value.
    StringBuilder authPWValue = new StringBuilder();
    authPWValue.append(AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_384);
    authPWValue.append('$');
    authPWValue.append(Base64.encode(saltBytes));
    authPWValue.append('$');
    authPWValue.append(Base64.encode(digestBytes));

    return ByteStringFactory.create(authPWValue.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteString plaintextPassword,
                                     String authInfo, String authValue)
  {


    byte[] saltBytes;
    byte[] digestBytes;
    try
    {
      saltBytes   = Base64.decode(authInfo);
      digestBytes = Base64.decode(authValue);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      return false;
    }


    byte[] plainBytes = plaintextPassword.value();
    byte[] plainPlusSaltBytes = new byte[plainBytes.length + saltBytes.length];
    System.arraycopy(plainBytes, 0, plainPlusSaltBytes, 0, plainBytes.length);
    System.arraycopy(saltBytes, 0, plainPlusSaltBytes, plainBytes.length,
                     saltBytes.length);

    digestLock.lock();

    try
    {
      return Arrays.equals(digestBytes,
                                messageDigest.digest(plainPlusSaltBytes));
    }
    finally
    {
      digestLock.unlock();
    }
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
  public ByteString getPlaintextValue(ByteString storedPassword)
         throws DirectoryException
  {

    int msgID = MSGID_PWSCHEME_NOT_REVERSIBLE;
    String message = getMessage(msgID, STORAGE_SCHEME_NAME_SALTED_SHA_384);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {

    int msgID = MSGID_PWSCHEME_NOT_REVERSIBLE;
    String message = getMessage(msgID,
                                AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_384);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {

    // SHA-2 should be considered secure.
    return true;
  }
}

