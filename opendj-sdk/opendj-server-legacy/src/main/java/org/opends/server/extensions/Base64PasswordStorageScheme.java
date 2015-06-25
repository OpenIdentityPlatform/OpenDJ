/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.Base64PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;



/**
 * This class defines a Directory Server password storage scheme that will store
 * the values in base64-encoded form.  This is a reversible algorithm that
 * offers very little actual protection -- it will merely obscure the plaintext
 * value from the casual observer.
 */
public class Base64PasswordStorageScheme
       extends PasswordStorageScheme<Base64PasswordStorageSchemeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public Base64PasswordStorageScheme()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializePasswordStorageScheme(
                   Base64PasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /** {@inheritDoc} */
  @Override
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_BASE64;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    return ByteString.valueOf(Base64.encode(plaintext));
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_BASE64);
    buffer.append('}');
    buffer.append(Base64.encode(plaintext));

    return ByteString.valueOf(buffer);
  }



  /** {@inheritDoc} */
  @Override
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    String userString   = Base64.encode(plaintextPassword);
    String storedString = storedPassword.toString();
    return userString.equals(storedString);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isReversible()
  {
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    try
    {
      return ByteString.wrap(Base64.decode(storedPassword.toString()));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD.get(
          storedPassword, e);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message, e);
    }
  }



  /** {@inheritDoc} */
  @Override
  public boolean supportsAuthPasswordSyntax()
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /** {@inheritDoc} */
  @Override
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isStorageSchemeSecure()
  {
    // Base64-encoded values may be easily decoded with no key or special
    // knowledge.
    return false;
  }
}

