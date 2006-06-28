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



import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.InitializationException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.ResultCode;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;

import java.util.Arrays;



/**
 * This class defines a Directory Server password storage scheme that will store
 * the values in clear-text with no encoding at all.  This is not at all secure
 * but may be required for backward-compatibility and support for certain legacy
 * applications.
 */
public class ClearPasswordStorageScheme
       extends PasswordStorageScheme
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.ClearPasswordStorageScheme";



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public ClearPasswordStorageScheme()
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

    // No initialization is required.
  }



  /**
   * Retrieves the name of the password storage scheme provided by this handler.
   *
   * @return  The name of the password storage scheme provided by this handler.
   */
  public String getStorageSchemeName()
  {
    assert debugEnter(CLASS_NAME, "getStorageSchemeName");

    return STORAGE_SCHEME_NAME_CLEAR;
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

    return plaintext.duplicate();
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
    assert debugEnter(CLASS_NAME, "encodePasswordWithScheme", "ByteString");

    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_CLEAR);
    buffer.append('}');
    buffer.append(plaintext.stringValue());

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

    return Arrays.equals(plaintextPassword.value(), storedPassword.value());
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

    return true;
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

    return storedPassword.duplicate();
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

    // This storage scheme does not support the authentication password syntax.
    return false;
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


    int    msgID   = MSGID_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD;
    String message = getMessage(msgID, getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
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


    // This storage scheme does not support the authentication password syntax.
    return false;
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

    // Clear-text passwords are not obscured in any way.
    return false;
  }
}

