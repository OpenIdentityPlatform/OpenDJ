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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.extensions.ExtensionsConstants.*;
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
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public ClearPasswordStorageScheme()
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

    // No initialization is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {

    return STORAGE_SCHEME_NAME_CLEAR;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteString plaintext)
         throws DirectoryException
  {

    return plaintext.duplicate();
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
    buffer.append(STORAGE_SCHEME_NAME_CLEAR);
    buffer.append('}');
    buffer.append(plaintext.stringValue());

    return ByteStringFactory.create(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteString plaintextPassword,
                                 ByteString storedPassword)
  {

    return Arrays.equals(plaintextPassword.value(), storedPassword.value());
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
  public ByteString getPlaintextValue(ByteString storedPassword)
         throws DirectoryException
  {

    return storedPassword.duplicate();
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

    // Clear-text passwords are not obscured in any way.
    return false;
  }
}

