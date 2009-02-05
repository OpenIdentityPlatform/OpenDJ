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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.MessageDigest;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.SHA1PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server password storage scheme based on the
 * SHA-1 algorithm defined in FIPS 180-1.  This is a one-way digest algorithm
 * so there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  This
 * implementation does not perform any salting, which means that it is more
 * vulnerable to dictionary attacks than salted variants.
 */
public class SHA1PasswordStorageScheme
       extends PasswordStorageScheme<SHA1PasswordStorageSchemeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.SHA1PasswordStorageScheme";



  // The message digest that will actually be used to generate the SHA-1 hashes.
  private MessageDigest messageDigest;

  // The lock used to provide threadsafe access to the message digest.
  private Object digestLock;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public SHA1PasswordStorageScheme()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   SHA1PasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    try
    {
      messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_1);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST.get(
          MESSAGE_DIGEST_ALGORITHM_SHA_1, String.valueOf(e));
      throw new InitializationException(message, e);
    }

    digestLock = new Object();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_SHA_1;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    byte[] digestBytes;

    synchronized (digestLock)
    {
      try
      {
        // TODO: Can we avoid this copy?
        byte[] plaintextBytes = plaintext.toByteArray();
        digestBytes = messageDigest.digest(plaintextBytes);
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

    return ByteString.valueOf(Base64.encode(digestBytes));
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
    buffer.append(STORAGE_SCHEME_NAME_SHA_1);
    buffer.append('}');

    // TODO: Can we avoid this copy?
    byte[] plaintextBytes = plaintext.toByteArray();
    byte[] digestBytes;

    synchronized (digestLock)
    {
      try
      {
        digestBytes = messageDigest.digest(plaintextBytes);
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

    buffer.append(Base64.encode(digestBytes));

    return ByteString.valueOf(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    // TODO: Can we avoid this copy?
    byte[] plaintextPasswordBytes = plaintextPassword.toByteArray();
    ByteString userPWDigestBytes;

    synchronized (digestLock)
    {
      try
      {
        userPWDigestBytes =
            ByteString.wrap(messageDigest.digest(plaintextPasswordBytes));
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

    ByteString storedPWDigestBytes;
    try
    {
      storedPWDigestBytes =
          ByteString.wrap(Base64.decode(storedPassword.toString()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      logError(ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD.get(
          storedPassword.toString(), String.valueOf(e)));

      return false;
    }

    return userPWDigestBytes.equals(storedPWDigestBytes);
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
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    Message message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
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
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    Message message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_SHA_1);
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
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {
    // SHA-1 should be considered secure.
    return true;
  }
}

