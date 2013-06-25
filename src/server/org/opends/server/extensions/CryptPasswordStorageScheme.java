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
 *      Portions Copyright 2010-2013 ForgeRock AS
 *      Portions Copyright 2012 Dariusz Janny <dariusz.janny@gmail.com>
 *
 */

package org.opends.server.extensions;


import java.util.List;
import java.util.ArrayList;
import java.util.Random;


import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PasswordStorageSchemeCfg;
import org.opends.server.admin.std.server.CryptPasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.Crypt;
import org.opends.server.util.BSDMD5Crypt;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;


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

  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.CryptPasswordStorageScheme";

  /*
   * The current configuration for the CryptPasswordStorageScheme
   */
  private CryptPasswordStorageSchemeCfg currentConfig;

  /**
   * An array of values that can be used to create salt characters
   * when encoding new crypt hashes.
   * */
  private static final byte[] SALT_CHARS =
    ("./0123456789abcdefghijklmnopqrstuvwxyz"
    +"ABCDEFGHIJKLMNOPQRSTUVWXYZ").getBytes();

  private final Random randomSaltIndex = new Random();
  private final Object saltLock = new Object();
  private final Crypt crypt = new Crypt();
  private final BSDMD5Crypt bsdmd5crypt = new BSDMD5Crypt();


  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public CryptPasswordStorageScheme()
  {
    super();
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   CryptPasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException {

    configuration.addCryptChangeListener(this);

    currentConfig = configuration;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_CRYPT;
  }


  /**
   * Encrypt plaintext password with the Unix Crypt algorithm.
   */

  private ByteString unixCryptEncodePassword(ByteSequence plaintext)
         throws DirectoryException
  {

    byte[] digestBytes;

    try
    {
      // TODO: Can we avoid this copy?
      byte[] plaintextBytes = plaintext.toByteArray();
      digestBytes = crypt.crypt(plaintextBytes, randomSalt());
    }
    catch (Exception e)
    {
      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
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
      byte[] salt = new byte[2];
      int sb1 = randomSaltIndex.nextInt(SALT_CHARS.length);
      int sb2 = randomSaltIndex.nextInt(SALT_CHARS.length);
      salt[0] = SALT_CHARS[sb1];
      salt[1] = SALT_CHARS[sb2];

      return salt;
    }
  }

  private ByteString md5CryptEncodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    String output;
    try
    {
      output = BSDMD5Crypt.crypt(plaintext.toString());
    }
    catch (Exception e)
    {
      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
    return ByteString.valueOf(output);
  }

  private ByteString sha256CryptEncodePassword(ByteSequence plaintext)
      throws DirectoryException {
    String output;
    try
    {
      output = Sha2Crypt.sha256Crypt(plaintext.toByteArray());
    }
    catch (Exception e)
    {
      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(
          DirectoryServer.getServerErrorResultCode(), message, e);
    }
    return ByteString.valueOf(output);
  }

  private ByteString sha512CryptEncodePassword(ByteSequence plaintext)
      throws DirectoryException {
    String output;
    try
    {
      output = Sha2Crypt.sha512Crypt(plaintext.toByteArray());
    }
    catch (Exception e)
    {
      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(
          DirectoryServer.getServerErrorResultCode(), message, e);
    }
    return ByteString.valueOf(output);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
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


  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer =
      new StringBuilder(STORAGE_SCHEME_NAME_CRYPT.length()+12);
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_CRYPT);
    buffer.append('}');

    buffer.append(encodePassword(plaintext));

    return ByteString.valueOf(buffer.toString());
  }

  /**
   * Matches passwords encrypted with the Unix Crypt algorithm.
   */
  private boolean unixCryptPasswordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    // TODO: Can we avoid this copy?
    byte[] plaintextPasswordBytes = plaintextPassword.toByteArray();

    ByteString userPWDigestBytes;
    try
    {
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

    return userPWDigestBytes.equals(storedPassword);
  }

  private boolean md5CryptPasswordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    String storedString = storedPassword.toString();
    try
    {
      String userString   = BSDMD5Crypt.crypt(plaintextPassword.toString(),
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
    String storedString = storedPassword.toString();
    try
    {
      String userString = Sha2Crypt.sha256Crypt(
          plaintextPassword.toByteArray(), storedString);
      return userString.equals(storedString);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  private boolean sha512CryptPasswordMatches(ByteSequence plaintextPassword,
      ByteSequence storedPassword) {
    String storedString = storedPassword.toString();
    try
    {
      String userString = Sha2Crypt.sha512Crypt(
          plaintextPassword.toByteArray(), storedString);
      return userString.equals(storedString);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
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
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_CRYPT);
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
    // FIXME:
    // Technically, this isn't quite in keeping with the original spirit of
    // this method, since the point was to determine whether the scheme could
    // be trivially reversed.  I'm not sure I would put crypt into that
    // category, but it's certainly a lot more vulnerable to lookup tables
    // than most other algorithms.  I'd say we can keep it this way for now,
    // but it might be something to reconsider later.
    //
    // Currently, this method is unused.  However, the intended purpose is
    // eventually for use in issue #321, where we could do things like prevent
    // even authorized users from seeing the password value over an insecure
    // connection if it isn't considered secure.

    return false;
  }
  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(
          PasswordStorageSchemeCfg configuration,
          List<Message> unacceptableReasons)
  {
    CryptPasswordStorageSchemeCfg config =
            (CryptPasswordStorageSchemeCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
}



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      CryptPasswordStorageSchemeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // If we've gotten this far, then we'll accept the change.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                      CryptPasswordStorageSchemeCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    currentConfig = configuration;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}
