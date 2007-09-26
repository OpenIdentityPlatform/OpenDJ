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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.
            RepeatedCharactersPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.MessageBuilder;


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
  // The current configuration for this password validator.
  private RepeatedCharactersPasswordValidatorCfg currentConfig;



  /**
   * Creates a new instance of this repeated characters password validator.
   */
  public RepeatedCharactersPasswordValidator()
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
                   RepeatedCharactersPasswordValidatorCfg configuration)
  {
    configuration.addRepeatedCharactersChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeRepeatedCharactersChangeListener(this);
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
    String passwordString = newPassword.stringValue();
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
          Message message =
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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      RepeatedCharactersPasswordValidatorCfg configuration,
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
                      RepeatedCharactersPasswordValidatorCfg configuration)
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

