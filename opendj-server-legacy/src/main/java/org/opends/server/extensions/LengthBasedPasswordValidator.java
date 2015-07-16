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
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LengthBasedPasswordValidatorCfg;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
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



  /**
   * Creates a new instance of this password validator.
   */
  public LengthBasedPasswordValidator()
  {
    super();

    // All initialization must be done in the initializePasswordValidator
    // method.
  }



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeLengthBasedChangeListener(this);
  }



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    LengthBasedPasswordValidatorCfg config =
         (LengthBasedPasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
              LengthBasedPasswordValidatorCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
