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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessage;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.
            RepeatedCharactersPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.*;

/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords are not allowed to have the same character appear
 * several times consecutively.
 */
public class RepeatedCharactersPasswordValidator
       extends PasswordValidator<RepeatedCharactersPasswordValidatorCfg>
       implements ConfigurationChangeListener<
                       RepeatedCharactersPasswordValidatorCfg>
{
  /** The current configuration for this password validator. */
  private RepeatedCharactersPasswordValidatorCfg currentConfig;

  /** Creates a new instance of this repeated characters password validator. */
  public RepeatedCharactersPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }

  @Override
  public void initializePasswordValidator(
                   RepeatedCharactersPasswordValidatorCfg configuration)
  {
    configuration.addRepeatedCharactersChangeListener(this);
    currentConfig = configuration;
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeRepeatedCharactersChangeListener(this);
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    // Get a handle to the current configuration and see if we need to count
    // the number of repeated characters in the password.
    RepeatedCharactersPasswordValidatorCfg config = currentConfig;
    int maxRepeats = config.getMaxConsecutiveLength();
    if (maxRepeats <= 0)
    {
      // We don't need to check anything, so the password will be acceptable.
      return true;
    }

    // Get the password as a string.  If we should use case-insensitive
    // validation, then convert it to use all lowercase characters.
    String passwordString = newPassword.toString();
    if (! config.isCaseSensitiveValidation())
    {
      passwordString = passwordString.toLowerCase();
    }

    // Create variables to keep track of the last character we've seen and how
    // many times we have seen it.
    char lastCharacter    = '\u0000';
    int  consecutiveCount = 0;

    // Iterate through the characters in the password.  If the consecutive
    // count ever gets too high, then fail.
    for (int i=0; i < passwordString.length(); i++)
    {
      char currentCharacter = passwordString.charAt(i);
      if (currentCharacter == lastCharacter)
      {
        consecutiveCount++;
        if (consecutiveCount > maxRepeats)
        {
          LocalizableMessage message =
                  ERR_REPEATEDCHARS_VALIDATOR_TOO_MANY_CONSECUTIVE.get(
                          maxRepeats);
          invalidReason.append(message);
          return false;
        }
      }
      else
      {
        lastCharacter    = currentCharacter;
        consecutiveCount = 1;
      }
    }

    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      RepeatedCharactersPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // All of the necessary validation should have been performed automatically,
    // so if we get to this point then the new configuration will be acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                      RepeatedCharactersPasswordValidatorCfg configuration)
  {
    // For this password validator, we will always be able to successfully apply
    // the new configuration.
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
