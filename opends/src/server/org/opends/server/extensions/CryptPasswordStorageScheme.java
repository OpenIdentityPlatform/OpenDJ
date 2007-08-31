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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.CryptPasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Crypt;

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
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.CryptPasswordStorageScheme";

  /**
   * An array of values that can be used to create salt characters
   * when encoding new crypt hashes.
   * */
  private static final byte[] SALT_CHARS =
    ("./0123456789abcdefghijklmnopqrstuvwxyz"
    +"ABCDEFGHIJKLMNOPQRSTUVWXYZ").getBytes();

  private final Random randomSaltIndex = new Random();
  private final ReentrantLock saltLock = new ReentrantLock();
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


  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   CryptPasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException {
    // Nothing to configure
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
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteString plaintext)
         throws DirectoryException
  {

    byte[] digestBytes;

    try
    {
      digestBytes = crypt.crypt(plaintext.value(), randomSalt());
    }
    catch (Exception e)
    {
      Message message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    return ByteStringFactory.create(digestBytes);
  }


  /**
   * Return a random 2-byte salt.
   *
   * @return a random 2-byte salt
   */
  private byte[] randomSalt() {
    saltLock.lock();

    try {
      byte[] salt = new byte[2];
      int sb1 = randomSaltIndex.nextInt(SALT_CHARS.length);
      int sb2 = randomSaltIndex.nextInt(SALT_CHARS.length);
      salt[0] = SALT_CHARS[sb1];
      salt[1] = SALT_CHARS[sb2];

      return salt;
    } finally {
      saltLock.unlock();
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteString plaintext)
         throws DirectoryException
  {
    StringBuilder buffer =
      new StringBuilder(STORAGE_SCHEME_NAME_CRYPT.length()+12);
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_CRYPT);
    buffer.append('}');

    buffer.append(encodePassword(plaintext));

    return ByteStringFactory.create(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteString plaintextPassword,
                                 ByteString storedPassword)
  {
    byte[] storedPWDigestBytes = storedPassword.value();

    byte[] userPWDigestBytes;
    try
    {
      // The salt is stored as the first two bytes of the storedPassword
      // value, and crypt.crypt() only looks at the first two bytes, so
      // we can pass it in directly.
      byte[] salt = storedPWDigestBytes;

      userPWDigestBytes = crypt.crypt(plaintextPassword.value(), salt);
    }
    catch (Exception e)
    {
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
    Message message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
}

