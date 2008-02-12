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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CharacterSetPasswordValidatorCfg;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords contain at least a specified number of characters
 * from one or more user-defined character sets.
 */
public class CharacterSetPasswordValidator
       extends PasswordValidator<CharacterSetPasswordValidatorCfg>
       implements ConfigurationChangeListener<CharacterSetPasswordValidatorCfg>
{
  // The current configuration for this password validator.
  private CharacterSetPasswordValidatorCfg currentConfig;

  // A mapping between the character sets and the minimum number of characters
  // required for each.
  private HashMap<String,Integer> characterSets;



  /**
   * Creates a new instance of this character set password validator.
   */
  public CharacterSetPasswordValidator()
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
                   CharacterSetPasswordValidatorCfg configuration)
         throws ConfigException
  {
    configuration.addCharacterSetChangeListener(this);
    currentConfig = configuration;

    // Make sure that each of the character set definitions are acceptable.
    characterSets = processCharacterSets(configuration);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeCharacterSetChangeListener(this);
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
    // Get a handle to the current configuration.
    CharacterSetPasswordValidatorCfg config = currentConfig;
    HashMap<String,Integer> characterSets = this.characterSets;


    // Process the provided password.
    String password = newPassword.stringValue();
    HashMap<String,Integer> counts = new HashMap<String,Integer>();
    for (int i=0; i < password.length(); i++)
    {
      char c = password.charAt(i);
      boolean found = false;
      for (String characterSet : characterSets.keySet())
      {
        if (characterSet.indexOf(c) >= 0)
        {
          Integer count = counts.get(characterSet);
          if (count == null)
          {
            counts.put(characterSet, 1);
          }
          else
          {
            counts.put(characterSet, count+1);
          }

          found = true;
          break;
        }
      }

      if ((! found) && (! config.isAllowUnclassifiedCharacters()))
      {
        invalidReason.append(ERR_CHARSET_VALIDATOR_ILLEGAL_CHARACTER.get(
                String.valueOf(c)));
        return false;
      }
    }

    for (String characterSet : characterSets.keySet())
    {
      int minimumCount = characterSets.get(characterSet);
      Integer passwordCount = counts.get(characterSet);
      if ((passwordCount == null) || (passwordCount < minimumCount))
      {
        invalidReason.append(ERR_CHARSET_VALIDATOR_TOO_FEW_CHARS_FROM_SET.get(
                characterSet, minimumCount));
        return false;
      }
    }


    // If we've gotten here, then the password is acceptable.
    return true;
  }



  /**
   * Parses the provided configuration and extracts the character set
   * definitions and associated minimum counts from them.
   *
   * @param  configuration  the configuration for this password validator.
   *
   * @return  The mapping between strings of character set values and the
   *          minimum number of characters required from those sets.
   *
   * @throws  ConfigException  If any of the character set definitions cannot be
   *                           parsed, or if there are any characters present in
   *                           multiple sets.
   */
  private HashMap<String,Integer>
               processCharacterSets(
                    CharacterSetPasswordValidatorCfg configuration)
          throws ConfigException
  {
    HashMap<String,Integer> characterSets  = new HashMap<String,Integer>();
    HashSet<Character>      usedCharacters = new HashSet<Character>();

    for (String definition : configuration.getCharacterSet())
    {
      int colonPos = definition.indexOf(':');
      if (colonPos <= 0)
      {
        Message message = ERR_CHARSET_VALIDATOR_NO_COLON.get(definition);
        throw new ConfigException(message);
      }
      else if (colonPos == (definition.length() - 1))
      {
        Message message = ERR_CHARSET_VALIDATOR_NO_CHARS.get(definition);
        throw new ConfigException(message);
      }

      int minCount;
      try
      {
        minCount = Integer.parseInt(definition.substring(0, colonPos));
      }
      catch (Exception e)
      {
        Message message = ERR_CHARSET_VALIDATOR_INVALID_COUNT.get(definition);
        throw new ConfigException(message);
      }

      if (minCount <= 0)
      {
        Message message = ERR_CHARSET_VALIDATOR_INVALID_COUNT.get(definition);
        throw new ConfigException(message);
      }

      String characterSet = definition.substring(colonPos+1);
      for (int i=0; i < characterSet.length(); i++)
      {
        char c = characterSet.charAt(i);
        if (usedCharacters.contains(c))
        {
          Message message = ERR_CHARSET_VALIDATOR_DUPLICATE_CHAR.get(
              definition, String.valueOf(c));
          throw new ConfigException(message);
        }

        usedCharacters.add(c);
      }

      characterSets.put(characterSet, minCount);
    }

    return characterSets;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    CharacterSetPasswordValidatorCfg config =
         (CharacterSetPasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      CharacterSetPasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // Make sure that we can process the defined character sets.  If so, then
    // we'll accept the new configuration.
    try
    {
      processCharacterSets(configuration);
    }
    catch (ConfigException ce)
    {
      unacceptableReasons.add(ce.getMessageObject());
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                      CharacterSetPasswordValidatorCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Make sure that we can process the defined character sets.  If so, then
    // activate the new configuration.
    try
    {
      characterSets = processCharacterSets(configuration);
      currentConfig = configuration;
    }
    catch (Exception e)
    {
      resultCode = DirectoryConfig.getServerErrorResultCode();
      messages.add(getExceptionMessage(e));
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

