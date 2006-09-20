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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server password storage scheme based on the
 * 512-bit SHA-2 algorithm defined in FIPS 180-2.  This is a one-way digest
 * algorithm so there is no way to retrieve the original clear-text version of
 * the password from the hashed value (although this means that it is not
 * suitable for things that need the clear-text password like DIGEST-MD5).  The
 * values that it generates are also salted, which protects against dictionary
 * attacks. It does this by generating a 64-bit random salt which is appended to
 * the clear-text value.  A SHA-2 hash is then generated based on this, the salt
 * is appended to the hash, and then the entire value is base64-encoded.
 */
public class SaltedSHA512PasswordStorageScheme
       extends PasswordStorageScheme
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.SaltedSHA512PasswordStorageScheme";



  /**
   * The number of bytes of random data to use as the salt when generating the
   * hashes.
   */
  private static final int NUM_SALT_BYTES = 8;



  // The message digest that will actually be used to generate the 512-bit SHA-2
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
  public SaltedSHA512PasswordStorageScheme()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this password storage scheme handler based on the information
   * in the provided configuration entry.  It should also register itself with
   * the Directory Server for the particular storage scheme that it will manage.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this password storage scheme
   *                      handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializePasswordStorageScheme(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializePasswordStorageScheme",
                      String.valueOf(configEntry));

    try
    {
      messageDigest =
           MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_512);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializePasswordStorageScheme", e);

      int msgID = MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST;
      String message = getMessage(msgID, MESSAGE_DIGEST_ALGORITHM_SHA_512,
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }

    digestLock = new ReentrantLock();
    random     = new SecureRandom();
  }



  /**
   * Retrieves the name of the password storage scheme provided by this handler.
   *
   * @return  The name of the password storage scheme provided by this handler.
   */
  public String getStorageSchemeName()
  {
    assert debugEnter(CLASS_NAME, "getStorageSchemeName");

    return STORAGE_SCHEME_NAME_SALTED_SHA_512;
  }



  /**
   * Encodes the provided plaintext password for this storage scheme.  Note that
   * the provided plaintext password should not be altered in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The password that has been encoded using this storage scheme.
   *
   * @throws  DirectoryException  If a problem occurs while processing.
   */
  public ByteString encodePassword(ByteString plaintext)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "encodePassword", "ByteString");

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
      assert debugException(CLASS_NAME, "encodePassword", e);

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

    return new ASN1OctetString(Base64.encode(hashPlusSalt));
  }



  /**
   * Encodes the provided plaintext password for this storage scheme, prepending
   * the name of the scheme in curly braces.  Note that the provided plaintext
   * password should not be altered in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The encoded password, including the name of the storage scheme.
   *
   * @throws  DirectoryException  If a problem occurs while processing.
   */
  public ByteString encodePasswordWithScheme(ByteString plaintext)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "encodePasswordWithScheme",
                      "ByteString");

    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_SALTED_SHA_512);
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
      assert debugException(CLASS_NAME, "encodePassword", e);

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

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Indicates whether the provided plaintext password included in a bind
   * request matches the given stored value.
   *
   * @param  plaintextPassword  The plaintext password provided by the user as
   *                            part of a simple bind attempt.
   * @param  storedPassword     The stored password to compare against the
   *                            provided plaintext password.
   *
   * @return  <CODE>true</CODE> if the provided plaintext password matches the
   *          provided stored password, or <CODE>false</CODE> if not.
   */
  public boolean passwordMatches(ByteString plaintextPassword,
                                 ByteString storedPassword)
  {
    assert debugEnter(CLASS_NAME, "passwordMatches",
                      String.valueOf(plaintextPassword),
                      String.valueOf(storedPassword));


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
      assert debugException(CLASS_NAME, "passwordMatches", e);

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
      assert debugException(CLASS_NAME, "passwordMatches", e);

      return false;
    }
    finally
    {
      digestLock.unlock();
    }

    return Arrays.equals(digestBytes, userDigestBytes);
  }



  /**
   * Indicates whether this password storage scheme supports the ability to
   * interact with values using the authentication password syntax defined in
   * RFC 3112.
   *
   * @return  <CODE>true</CODE> if this password storage scheme supports the
   *          ability to interact with values using the authentication password
   *          syntax, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsAuthPasswordSyntax()
  {
    assert debugEnter(CLASS_NAME, "supportsAuthPasswordSyntax");

    // This storage scheme does support the authentication password syntax.
    return true;
  }



  /**
   * Retrieves the scheme name that should be used with this password storage
   * scheme when it is used in the context of the authentication password
   * syntax.  This default implementation will return the same value as the
   * <CODE>getStorageSchemeName</CODE> method.
   *
   * @return  The scheme name that should be used with this password storage
   *          scheme when it is used in the context of the authentication
   *          password syntax.
   */
  public String getAuthPasswordSchemeName()
  {
    assert debugEnter(CLASS_NAME, "getAuthPasswordSchemeName");

    return AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_512;
  }



  /**
   * Encodes the provided plaintext password for this storage scheme using the
   * authentication password syntax defined in RFC 3112.  Note that the
   * provided plaintext password should not be altered in any way.
   *
   * @param  plaintext  The plaintext version of the password.
   *
   * @return  The password that has been encoded in the authentication password
   *          syntax.
   *
   * @throws  DirectoryException  If a problem occurs while processing of if
   *                              this storage scheme does not support the
   *                              authentication password syntax.
   */
  public ByteString encodeAuthPassword(ByteString plaintext)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "encodeAuthPassword",
                      String.valueOf(plaintext));


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
      assert debugException(CLASS_NAME, "encodePassword", e);

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
    authPWValue.append(AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_512);
    authPWValue.append('$');
    authPWValue.append(Base64.encode(saltBytes));
    authPWValue.append('$');
    authPWValue.append(Base64.encode(digestBytes));

    return new ASN1OctetString(authPWValue.toString());
  }



  /**
   * Indicates whether the provided plaintext password matches the encoded
   * password using the authentication password syntax with the given authInfo
   * and authValue components.
   *
   * @param  plaintextPassword  The plaintext password provided by the user.
   * @param  authInfo           The authInfo component of the password encoded
   *                            in the authentication password syntax.
   * @param  authValue          The authValue component of the password encoded
   *                            in the authentication password syntax.
   *
   * @return  <CODE>true</CODE> if the provided plaintext password matches the
   *          encoded password according to the authentication password info
   *          syntax, or <CODE>false</CODE> if it does not or this storage
   *          scheme does not support the authentication password syntax.
   */
  public boolean authPasswordMatches(ByteString plaintextPassword,
                                     String authInfo, String authValue)
  {
    assert debugEnter(CLASS_NAME, "authPasswordMatches",
                      String.valueOf(plaintextPassword),
                      String.valueOf(authInfo), String.valueOf(authValue));


    byte[] saltBytes;
    byte[] digestBytes;
    try
    {
      saltBytes   = Base64.decode(authInfo);
      digestBytes = Base64.decode(authValue);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "authPasswordMatches", e);

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
   * Indicates whether this storage scheme is reversible (i.e., it is possible
   * to obtain the original plaintext value from the stored password).
   *
   * @return  <CODE>true</CODE> if this is a reversible password storage scheme,
   *          or <CODE>false</CODE> if it is not.
   */
  public boolean isReversible()
  {
    assert debugEnter(CLASS_NAME, "isReversible");

    return false;
  }



  /**
   * Retrieves the original plaintext value for the provided stored password.
   * Note that this should only be called if <CODE>isReversible</CODE> returns
   * <CODE>true</CODE>.
   *
   * @param  storedPassword  The password for which to obtain the plaintext
   *                         value.
   *
   * @return  The plaintext value for the provided stored password.
   *
   * @throws  DirectoryException  If it is not possible to obtain the plaintext
   *                              value for the provided stored password.
   */
  public ByteString getPlaintextValue(ByteString storedPassword)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getPlaintextValue",
                      String.valueOf(storedPassword));

    int msgID = MSGID_PWSCHEME_NOT_REVERSIBLE;
    String message = getMessage(msgID, STORAGE_SCHEME_NAME_SALTED_SHA_512);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                 msgID);
  }



  /**
   * Indicates whether this password storage scheme should be considered
   * "secure".  If the encoding used for this scheme does not obscure the value
   * at all, or if it uses a method that is trivial to reverse (e.g., base64),
   * then it should not be considered secure.
   * <BR><BR>
   * This may be used to determine whether a password may be included in a set
   * of search results, including the possibility of overriding access controls
   * in the case that access controls would allow the password to be returned
   * but the password is considered too insecure to reveal.
   *
   * @return  <CODE>false</CODE> if it may be trivial to discover the original
   *          plain-text password from the encoded form, or <CODE>true</CODE> if
   *          the scheme offers sufficient protection that revealing the encoded
   *          password will not easily reveal the corresponding plain-text
   *          value.
   */
  public boolean isStorageSchemeSecure()
  {
    assert debugEnter(CLASS_NAME, "isStorageSchemeSecure");

    // SHA-2 should be considered secure.
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
    byte[] saltBytes = new byte[NUM_SALT_BYTES];
    new SecureRandom().nextBytes(saltBytes);

    byte[] passwordPlusSalt = new byte[passwordBytes.length + NUM_SALT_BYTES];
    System.arraycopy(passwordBytes, 0, passwordPlusSalt, 0,
                     passwordBytes.length);
    System.arraycopy(saltBytes, 0, passwordPlusSalt, passwordBytes.length,
                     NUM_SALT_BYTES);

    MessageDigest messageDigest;
    try
    {
      messageDigest =
           MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_512);
    }
    catch (Exception e)
    {
      int msgID = MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST;
      String message = getMessage(msgID, MESSAGE_DIGEST_ALGORITHM_SHA_512,
                                  String.valueOf(e));
      throw new DirectoryException(ResultCode.OTHER, message, msgID, e);
    }


    byte[] digestBytes    = messageDigest.digest(passwordPlusSalt);
    byte[] digestPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];
    System.arraycopy(digestBytes, 0, digestPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, digestPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);

    return "{" + STORAGE_SCHEME_NAME_SALTED_SHA_512 + "}" +
           Base64.encode(digestPlusSalt);
  }
}

