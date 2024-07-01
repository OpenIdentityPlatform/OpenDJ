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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.UniqueCharactersPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.*;

/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords contain at least a specified number of different
 * characters.
 */
public class UniqueCharactersPasswordValidator
       extends PasswordValidator<UniqueCharactersPasswordValidatorCfg>
       implements ConfigurationChangeListener<
                       UniqueCharactersPasswordValidatorCfg>
{
  /** The current configuration for this password validator. */
  private UniqueCharactersPasswordValidatorCfg currentConfig;

  /** Creates a new instance of this unique characters password validator. */
  public UniqueCharactersPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }

  @Override
  public void initializePasswordValidator(
                   UniqueCharactersPasswordValidatorCfg configuration)
  {
    configuration.addUniqueCharactersChangeListener(this);
    currentConfig = configuration;
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeUniqueCharactersChangeListener(this);
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    // Get a handle to the current configuration and see if we need to count
    // the number of unique characters in the password.
    UniqueCharactersPasswordValidatorCfg config = currentConfig;
    int minUniqueCharacters = config.getMinUniqueCharacters();
    if (minUniqueCharacters <= 0)
    {
      // We don't need to check anything, so the password will be acceptable.
      return true;
    }

    // Create a set that will be used to keep track of the unique characters
    // contained in the proposed password.
    HashSet<Character> passwordCharacters = new HashSet<>();

    // Iterate through the characters in the new password and place them in the
    // set as needed.  If we should behave in a case-insensitive manner, then
    // convert all the characters to lowercase first.
    String passwordString = newPassword.toString();
    if (! config.isCaseSensitiveValidation())
    {
      passwordString = passwordString.toLowerCase();
    }

    for (int i=0; i < passwordString.length(); i++)
    {
      passwordCharacters.add(passwordString.charAt(i));
    }

    // If the size of the password characters set is less than the minimum
    // number of allowed unique characters, then we will reject the password.
    if (passwordCharacters.size() < minUniqueCharacters)
    {
      LocalizableMessage message = ERR_UNIQUECHARS_VALIDATOR_NOT_ENOUGH_UNIQUE_CHARS.get(
              minUniqueCharacters);
      invalidReason.append(message);
      return false;
    }

    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      UniqueCharactersPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // All of the necessary validation should have been performed automatically,
    // so if we get to this point then the new configuration will be acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                      UniqueCharactersPasswordValidatorCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // For this password validator, we will always be able to successfully apply
    // the new configuration.
    currentConfig = configuration;

    return ccr;
  }
}
