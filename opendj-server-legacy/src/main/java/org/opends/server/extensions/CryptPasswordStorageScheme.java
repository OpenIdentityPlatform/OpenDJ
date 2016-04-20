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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2010-2016 ForgeRock AS.
 * Portions Copyright 2012 Dariusz Janny <dariusz.janny@gmail.com>
 */
package org.opends.server.extensions;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.CryptPasswordStorageSchemeCfg;
import org.forgerock.opendj.server.config.server.PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.util.BSDMD5Crypt;
import org.opends.server.util.Crypt;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a Directory Server password storage scheme based on the
 * UNIX Crypt algorithm.  This is a legacy one-way digest algorithm
 * intended only for situations where passwords have not yet been
 * updated to modern hashes such as SHA-1 and friends.  This
 * implementation does perform weak salting, which means that it is more
 * vulnerable to dictionary attacks than schemes with larger salts.
 */
public class CryptPasswordStorageScheme
       extends PasswordStorageScheme<CryptPasswordStorageSchemeCfg>
       implements ConfigurationChangeListener<CryptPasswordStorageSchemeCfg>
{
  /** The fully-qualified name of this class for debugging purposes. */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.CryptPasswordStorageScheme";

  /** The current configuration for the CryptPasswordStorageScheme. */
  private CryptPasswordStorageSchemeCfg currentConfig;

  /**
   * An array of values that can be used to create salt characters
   * when encoding new crypt hashes.
   */
  private static final byte[] SALT_CHARS =
    ("./0123456789abcdefghijklmnopqrstuvwxyz"
    +"ABCDEFGHIJKLMNOPQRSTUVWXYZ").getBytes();

  private final Random randomSaltIndex = new Random();
  private final Object saltLock = new Object();
  private final Crypt crypt = new Crypt();

  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public CryptPasswordStorageScheme()
  {
    super();
  }

  @Override
  public void initializePasswordStorageScheme(
                   CryptPasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException {
    configuration.addCryptChangeListener(this);

    currentConfig = configuration;
  }

  @Override
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_CRYPT;
  }

  /** Encrypt plaintext password with the Unix Crypt algorithm. */
  private ByteString unixCryptEncodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    byte[] plaintextBytes = null;
    byte[] digestBytes;

    try
    {
      // TODO: can we avoid this copy?
      plaintextBytes = plaintext.toByteArray();
      digestBytes = crypt.crypt(plaintextBytes, randomSalt());
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    finally
    {
      if (plaintextBytes != null)
      {
        Arrays.fill(plaintextBytes, (byte) 0);
      }
    }

    return ByteString.wrap(digestBytes);
  }

  /**
   * Return a random 2-byte salt.
   *
   * @return a random 2-byte salt
   */
  private byte[] randomSalt() {
    synchronized (saltLock)
    {
      int sb1 = randomSaltIndex.nextInt(SALT_CHARS.length);
      int sb2 = randomSaltIndex.nextInt(SALT_CHARS.length);

      return new byte[] {
        SALT_CHARS[sb1],
        SALT_CHARS[sb2],
      };
    }
  }

  private ByteString md5CryptEncodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    String output;
    try
    {
      output = BSDMD5Crypt.crypt(plaintext);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    return ByteString.valueOfUtf8(output);
  }

  private ByteString sha256CryptEncodePassword(ByteSequence plaintext)
      throws DirectoryException {
    String output;
    byte[] plaintextBytes = null;

    try
    {
      plaintextBytes = plaintext.toByteArray();
      output = Sha2Crypt.sha256Crypt(plaintextBytes);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(
          DirectoryServer.getServerErrorResultCode(), message, e);
    }
    finally
    {
      if (plaintextBytes != null)
      {
        Arrays.fill(plaintextBytes, (byte) 0);
      }
    }
    return ByteString.valueOfUtf8(output);
  }

  private ByteString sha512CryptEncodePassword(ByteSequence plaintext)
      throws DirectoryException {
    String output;
    byte[] plaintextBytes = null;

    try
    {
      plaintextBytes = plaintext.toByteArray();
      output = Sha2Crypt.sha512Crypt(plaintextBytes);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(
          DirectoryServer.getServerErrorResultCode(), message, e);
    }
    finally
    {
      if (plaintextBytes != null)
      {
        Arrays.fill(plaintextBytes, (byte) 0);
      }
    }
    return ByteString.valueOfUtf8(output);
  }

  @Override
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    ByteString bytes = null;
    switch (currentConfig.getCryptPasswordStorageEncryptionAlgorithm())
    {
      case UNIX:
        bytes = unixCryptEncodePassword(plaintext);
        break;
      case MD5:
        bytes = md5CryptEncodePassword(plaintext);
        break;
      case SHA256:
        bytes = sha256CryptEncodePassword(plaintext);
        break;
      case SHA512:
        bytes = sha512CryptEncodePassword(plaintext);
        break;
    }
    return bytes;
  }

  @Override
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer =
      new StringBuilder(STORAGE_SCHEME_NAME_CRYPT.length()+12);
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_CRYPT);
    buffer.append('}');

    buffer.append(encodePassword(plaintext));

    return ByteString.valueOfUtf8(buffer);
  }

  /** Matches passwords encrypted with the Unix Crypt algorithm. */
  private boolean unixCryptPasswordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    // TODO: Can we avoid this copy?
    byte[] plaintextPasswordBytes = null;

    ByteString userPWDigestBytes;
    try
    {
      plaintextPasswordBytes = plaintextPassword.toByteArray();
      // The salt is stored as the first two bytes of the storedPassword
      // value, and crypt.crypt() only looks at the first two bytes, so
      // we can pass it in directly.
      byte[] salt = storedPassword.copyTo(new byte[2]);
      userPWDigestBytes =
          ByteString.wrap(crypt.crypt(plaintextPasswordBytes, salt));
    }
    catch (Exception e)
    {
      return false;
    }
    finally
    {
      if (plaintextPasswordBytes != null)
      {
        Arrays.fill(plaintextPasswordBytes, (byte) 0);
      }
    }

    return userPWDigestBytes.equals(storedPassword);
  }

  private boolean md5CryptPasswordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    String storedString = storedPassword.toString();
    try
    {
      String userString   = BSDMD5Crypt.crypt(plaintextPassword,
        storedString);
      return userString.equals(storedString);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  private boolean sha256CryptPasswordMatches(ByteSequence plaintextPassword,
      ByteSequence storedPassword) {
    byte[] plaintextPasswordBytes = null;
    String storedString = storedPassword.toString();
    try
    {
      plaintextPasswordBytes = plaintextPassword.toByteArray();
      String userString = Sha2Crypt.sha256Crypt(
          plaintextPasswordBytes, storedString);
      return userString.equals(storedString);
    }
    catch (Exception e)
    {
      return false;
    }
    finally
    {
      if (plaintextPasswordBytes != null)
      {
        Arrays.fill(plaintextPasswordBytes, (byte) 0);
      }
    }
  }

  private boolean sha512CryptPasswordMatches(ByteSequence plaintextPassword,
      ByteSequence storedPassword) {
    byte[] plaintextPasswordBytes = null;
    String storedString = storedPassword.toString();
    try
    {
      plaintextPasswordBytes = plaintextPassword.toByteArray();
      String userString = Sha2Crypt.sha512Crypt(
          plaintextPasswordBytes, storedString);
      return userString.equals(storedString);
    }
    catch (Exception e)
    {
      return false;
    }
    finally
    {
      if (plaintextPasswordBytes != null)
      {
        Arrays.fill(plaintextPasswordBytes, (byte) 0);
      }
    }
  }

  @Override
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    String storedString = storedPassword.toString();
    if (storedString.startsWith(BSDMD5Crypt.getMagicString()))
    {
      return md5CryptPasswordMatches(plaintextPassword, storedPassword);
    }
    else if (storedString.startsWith(Sha2Crypt.getMagicSHA256Prefix()))
    {
      return sha256CryptPasswordMatches(plaintextPassword, storedPassword);
    }
    else if (storedString.startsWith(Sha2Crypt.getMagicSHA512Prefix()))
    {
      return sha512CryptPasswordMatches(plaintextPassword, storedPassword);
    }
    else
    {
      return unixCryptPasswordMatches(plaintextPassword, storedPassword);
    }
  }

  @Override
  public boolean supportsAuthPasswordSyntax()
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
  }

  @Override
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
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
    LocalizableMessage message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_CRYPT);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }

  @Override
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {
    LocalizableMessage message =
      ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public boolean isStorageSchemeSecure()
  {
    // FIXME:
    // Technically, this isn't quite in keeping with the original spirit of
    // this method, since the point was to determine whether the scheme could
    // be trivially reversed.  I'm not sure I would put crypt into that
    // category, but it's certainly a lot more vulnerable to lookup tables
    // than most other algorithms.  I'd say we can keep it this way for now,
    // but it might be something to reconsider later.
    // Currently, this method is unused.  However, the intended purpose is
    // eventually for use in issue #321, where we could do things like prevent
    // even authorized users from seeing the password value over an insecure
    // connection if it isn't considered secure.

    return false;
  }

  @Override
  public boolean isConfigurationAcceptable(
          PasswordStorageSchemeCfg configuration,
          List<LocalizableMessage> unacceptableReasons)
  {
    CryptPasswordStorageSchemeCfg config =
            (CryptPasswordStorageSchemeCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      CryptPasswordStorageSchemeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // If we've gotten this far, then we'll accept the change.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                      CryptPasswordStorageSchemeCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
