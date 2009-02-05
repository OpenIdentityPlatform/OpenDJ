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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import org.opends.messages.Message;
import org.opends.server.admin.std.server.RC4PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class defines a Directory Server password storage scheme that will
 * encode values using the RC4 reversible encryption algorithm.  This
 * implementation supports only the user password syntax and not the auth
 * password syntax.
 */
public class RC4PasswordStorageScheme
       extends PasswordStorageScheme<RC4PasswordStorageSchemeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The reference to the Directory Server crypto manager that we will use to
  // handle the encryption/decryption.
  private CryptoManager cryptoManager;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the {@code initializePasswordStorageScheme} method.
   */
  public RC4PasswordStorageScheme()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   RC4PasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    cryptoManager = DirectoryServer.getCryptoManager();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_RC4;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    try
    {
      // TODO: Can we avoid this copy?
      byte[] plaintextBytes = plaintext.toByteArray();
      byte[] encodedBytes = cryptoManager.encrypt(CIPHER_TRANSFORMATION_RC4,
                                                  KEY_SIZE_RC4,
                                                  plaintextBytes);
      return ByteString.valueOf(Base64.encode(encodedBytes));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message m = ERR_PWSCHEME_CANNOT_ENCRYPT.get(STORAGE_SCHEME_NAME_RC4,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
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
    buffer.append(STORAGE_SCHEME_NAME_RC4);
    buffer.append('}');

    try
    {
      // TODO: Can we avoid this copy?
      byte[] plaintextBytes = plaintext.toByteArray();
      byte[] encodedBytes = cryptoManager.encrypt(CIPHER_TRANSFORMATION_RC4,
                                                  KEY_SIZE_RC4,
                                                  plaintextBytes);
      buffer.append(Base64.encode(encodedBytes));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message m = ERR_PWSCHEME_CANNOT_ENCRYPT.get(STORAGE_SCHEME_NAME_RC4,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }

    return ByteString.valueOf(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    try
    {
      ByteString decryptedPassword =
          ByteString.wrap(cryptoManager.decrypt(
               Base64.decode(storedPassword.toString())));
      return plaintextPassword.equals(decryptedPassword);
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isReversible()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    try
    {
      byte[] decryptedPassword =
           cryptoManager.decrypt(Base64.decode(storedPassword.toString()));
      return ByteString.wrap(decryptedPassword);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message m = ERR_PWSCHEME_CANNOT_DECRYPT.get(STORAGE_SCHEME_NAME_RC4,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
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
    // This password storage scheme should be considered secure.
    return true;
  }
}

