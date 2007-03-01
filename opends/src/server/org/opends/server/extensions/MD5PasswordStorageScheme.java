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
 * MD5 algorithm defined in RFC 1321.  This is a one-way digest algorithm
 * so there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  This
 * implementation does not perform any salting, which means that it is more
 * vulnerable to dictionary attacks than salted variants.
 */
public class MD5PasswordStorageScheme
       extends PasswordStorageScheme
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.MD5PasswordStorageScheme";



  // The message digest that will actually be used to generate the MD5 hashes.
  private MessageDigest messageDigest;

  // The lock used to provide threadsafe access to the message digest.
  private ReentrantLock digestLock;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public MD5PasswordStorageScheme()
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
      messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_MD5);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST;
      String message = getMessage(msgID, MESSAGE_DIGEST_ALGORITHM_MD5,
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }

    digestLock = new ReentrantLock();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {

    return STORAGE_SCHEME_NAME_MD5;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteString plaintext)
         throws DirectoryException
  {

    byte[] digestBytes;

    digestLock.lock();

    try
    {
      digestBytes = messageDigest.digest(plaintext.value());
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

    return ByteStringFactory.create(Base64.encode(digestBytes));
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
    buffer.append(STORAGE_SCHEME_NAME_MD5);
    buffer.append('}');

    byte[] digestBytes;

    digestLock.lock();

    try
    {
      digestBytes = messageDigest.digest(plaintext.value());
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

    buffer.append(Base64.encode(digestBytes));


    return ByteStringFactory.create(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteString plaintextPassword,
                                 ByteString storedPassword)
  {

    byte[] userPWDigestBytes;

    digestLock.lock();

    try
    {
      userPWDigestBytes = messageDigest.digest(plaintextPassword.value());
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

    byte[] storedPWDigestBytes;
    try
    {
      storedPWDigestBytes = Base64.decode(storedPassword.stringValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.MILD_ERROR,
               MSGID_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD,
               storedPassword.stringValue(), String.valueOf(e));

      return false;
    }

    return Arrays.equals(userPWDigestBytes, storedPWDigestBytes);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsAuthPasswordSyntax()
  {

    // This storage scheme does not support the authentication password syntax.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodeAuthPassword(ByteString plaintext)
         throws DirectoryException
  {


    int    msgID   = MSGID_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD;
    String message = getMessage(msgID, getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteString plaintextPassword,
                                     String authInfo, String authValue)
  {


    // This storage scheme does not support the authentication password syntax.
    return false;
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
    String message = getMessage(msgID, STORAGE_SCHEME_NAME_MD5);
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

    int    msgID   = MSGID_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD;
    String message = getMessage(msgID, getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {

    // MD5 may be considered reasonably secure for this purpose.
    return true;
  }
}

