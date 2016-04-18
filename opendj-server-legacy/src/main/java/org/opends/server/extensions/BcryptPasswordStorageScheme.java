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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.extensions;


import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.BcryptPasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import java.util.List;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;


/**
 * This class defines a Directory Server password storage scheme that will
 * encode values using the Blowfish reversible encryption algorithm.  This
 * implementation supports only the user password syntax and not the auth
 * password syntax.
 */
public class BcryptPasswordStorageScheme
       extends PasswordStorageScheme<BcryptPasswordStorageSchemeCfg>
    implements ConfigurationChangeListener<BcryptPasswordStorageSchemeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  /** The current configuration for this storage scheme. */
  private volatile BcryptPasswordStorageSchemeCfg config;

  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the {@link #initializePasswordStorageScheme(BcryptPasswordStorageSchemeCfg)} method.
   */
  public BcryptPasswordStorageScheme()
  {
  }


  @Override
  public void initializePasswordStorageScheme(BcryptPasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    this.config = configuration;
    config.addBcryptChangeListener(this);
  }


  @Override
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_BCRYPT;
  }


  @Override
  public boolean isConfigurationChangeAcceptable(BcryptPasswordStorageSchemeCfg configuration,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }


  @Override
  public ConfigChangeResult applyConfigurationChange(BcryptPasswordStorageSchemeCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult();
  }


  @Override
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    String salt = BCrypt.gensalt(config.getBcryptCost());
    String hashed_password = BCrypt.hashpw(plaintext.toByteArray(), salt);
    return ByteString.valueOfUtf8(hashed_password);
  }


  @Override
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    return ByteString.valueOfUtf8('{' + getStorageSchemeName() + '}' +  encodePassword(plaintext));
  }


  @Override
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    try
    {
      return BCrypt.checkpw(plaintextPassword.toString(), storedPassword.toString());
    }
    catch (IllegalArgumentException e)
    {
      logger.traceException(e);
      logger.error(ERR_PWSCHEME_INVALID_STORED_PASSWORD, e);
      return false;
    }
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
    LocalizableMessage message = ERR_PWSCHEME_NOT_REVERSIBLE.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }


  @Override
  public boolean supportsAuthPasswordSyntax()
  {
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
    return false;
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
    return true;
  }
}

