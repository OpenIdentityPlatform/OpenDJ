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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.UniqueCharactersPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.MessageBuilder;


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
  // The current configuration for this password validator.
  private UniqueCharactersPasswordValidatorCfg currentConfig;



  /**
   * Creates a new instance of this unique characters password validator.
   */
  public UniqueCharactersPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordValidator(
                   UniqueCharactersPasswordValidatorCfg configuration)
  {
    configuration.addUniqueCharactersChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeUniqueCharactersChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      MessageBuilder invalidReason)
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
    HashSet<Character> passwordCharacters = new HashSet<Character>();

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
      Message message = ERR_UNIQUECHARS_VALIDATOR_NOT_ENOUGH_UNIQUE_CHARS.get(
              minUniqueCharacters);
      invalidReason.append(message);
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      UniqueCharactersPasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // All of the necessary validation should have been performed automatically,
    // so if we get to this point then the new configuration will be acceptable.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                      UniqueCharactersPasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    // For this password validator, we will always be able to successfully apply
    // the new configuration.
    currentConfig = configuration;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

