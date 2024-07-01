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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.LengthBasedPasswordValidatorCfg;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ExtensionMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class provides a password validator that can ensure that the provided
 * password meets minimum and/or maximum length requirements.
 */
public class LengthBasedPasswordValidator extends
    PasswordValidator<LengthBasedPasswordValidatorCfg> implements
    ConfigurationChangeListener<LengthBasedPasswordValidatorCfg>
{
  /** The current configuration for this password validator. */
  private LengthBasedPasswordValidatorCfg currentConfig;

  /** Creates a new instance of this password validator. */
  public LengthBasedPasswordValidator()
  {
    super();

    // All initialization must be done in the initializePasswordValidator
    // method.
  }

  @Override
  public void initializePasswordValidator(
                   LengthBasedPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addLengthBasedChangeListener(this);

    currentConfig = configuration;

    // Make sure that if both the maximum and minimum lengths are set, the
    // maximum length is greater than or equal to the minimum length.
    int maxLength = configuration.getMaxPasswordLength();
    int minLength = configuration.getMinPasswordLength();
    if (maxLength > 0 && minLength > 0 && minLength > maxLength)
    {
      LocalizableMessage message =
          ERR_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX.get(minLength, maxLength);
      throw new ConfigException(message);
    }
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeLengthBasedChangeListener(this);
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    LengthBasedPasswordValidatorCfg config = currentConfig;

    int numChars = newPassword.toString().length();

    int minLength = config.getMinPasswordLength();
    if (minLength > 0 && numChars < minLength)
    {
      invalidReason.append(ERR_PWLENGTHVALIDATOR_TOO_SHORT.get(minLength));
      return false;
    }

    int maxLength = config.getMaxPasswordLength();
    if (maxLength > 0 && numChars > maxLength)
    {
      invalidReason.append(ERR_PWLENGTHVALIDATOR_TOO_LONG.get(maxLength));
      return false;
    }

    return true;
  }

  @Override
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LengthBasedPasswordValidatorCfg config =
         (LengthBasedPasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      LengthBasedPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure that if both the maximum and minimum lengths are set, the
    // maximum length is greater than or equal to the minimum length.
    int maxLength = configuration.getMaxPasswordLength();
    int minLength = configuration.getMinPasswordLength();
    if (maxLength > 0 && minLength > 0 && minLength > maxLength)
    {
      LocalizableMessage message = ERR_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX.get(
              minLength, maxLength);
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              LengthBasedPasswordValidatorCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
