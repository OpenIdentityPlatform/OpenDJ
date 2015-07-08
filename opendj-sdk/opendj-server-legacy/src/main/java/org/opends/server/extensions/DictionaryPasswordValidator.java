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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011 profiq, s.r.o.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.DictionaryPasswordValidatorCfg;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.*;

/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords are not contained in a specified dictionary.
 */
public class DictionaryPasswordValidator
       extends PasswordValidator<DictionaryPasswordValidatorCfg>
       implements ConfigurationChangeListener<DictionaryPasswordValidatorCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration for this password validator. */
  private DictionaryPasswordValidatorCfg currentConfig;

  /** The current dictionary that we should use when performing the validation. */
  private HashSet<String> dictionary;



  /**
   * Creates a new instance of this dictionary password validator.
   */
  public DictionaryPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }



  /** {@inheritDoc} */
  @Override
  public void initializePasswordValidator(
                   DictionaryPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addDictionaryChangeListener(this);
    currentConfig = configuration;

    dictionary = loadDictionary(configuration);
  }



  /** {@inheritDoc} */
  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeDictionaryChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    // Get a handle to the current configuration.
    DictionaryPasswordValidatorCfg config = currentConfig;

    // Check to see if the provided password is in the dictionary in the order
    // that it was provided.
    String password = newPassword.toString();
    if (! config.isCaseSensitiveValidation())
    {
      password = toLowerCase(password);
    }

    // Check to see if we should verify the whole password or the substrings.
    // Either way, we initialise the minSubstringLength to the length of
    // the password which is the default behaviour ('check-substrings: false')
    int minSubstringLength = password.length();

    if (config.isCheckSubstrings()
        // We apply the minimal substring length only if the provided value
        // is smaller then the actual password length
        && config.getMinSubstringLength() < password.length())
    {
      minSubstringLength = config.getMinSubstringLength();
    }

    // Verify if the dictionary contains the word(s) in the password
    if (isDictionaryBased(password, minSubstringLength))
    {
      invalidReason.append(
        ERR_DICTIONARY_VALIDATOR_PASSWORD_IN_DICTIONARY.get());
      return false;
    }

    // If the reverse password checking is enabled, then verify if the
    // reverse value of the password is in the dictionary.
    if (config.isTestReversedPassword()
        && isDictionaryBased(
            new StringBuilder(password).reverse().toString(), minSubstringLength))
    {
      invalidReason.append(ERR_DICTIONARY_VALIDATOR_PASSWORD_IN_DICTIONARY.get());
      return false;
    }


    // If we've gotten here, then the password is acceptable.
    return true;
  }



  /**
   * Loads the configured dictionary and returns it as a hash set.
   *
   * @param  configuration  the configuration for this password validator.
   *
   * @return  The hash set containing the loaded dictionary data.
   *
   * @throws  ConfigException  If the configured dictionary file does not exist.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   read from the dictionary file.
   */
  private HashSet<String> loadDictionary(
                               DictionaryPasswordValidatorCfg configuration)
          throws ConfigException, InitializationException
  {
    // Get the path to the dictionary file and make sure it exists.
    File dictionaryFile = getFileForPath(configuration.getDictionaryFile());
    if (! dictionaryFile.exists())
    {
      LocalizableMessage message = ERR_DICTIONARY_VALIDATOR_NO_SUCH_FILE.get(
          configuration.getDictionaryFile());
      throw new ConfigException(message);
    }


    // Read the contents of file into the dictionary as per the configuration.
    BufferedReader reader = null;
    HashSet<String> dictionary = new HashSet<>();
    try
    {
      reader = new BufferedReader(new FileReader(dictionaryFile));
      String line = reader.readLine();
      while (line != null)
      {
        if (! configuration.isCaseSensitiveValidation())
        {
          line = line.toLowerCase();
        }

        dictionary.add(line);
        line = reader.readLine();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_DICTIONARY_VALIDATOR_CANNOT_READ_FILE.get(configuration.getDictionaryFile(), e);
      throw new InitializationException(message);
    }
    finally
    {
      close(reader);
    }

    return dictionary;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    DictionaryPasswordValidatorCfg config =
         (DictionaryPasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      DictionaryPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure that we can load the dictionary.  If so, then we'll accept the
    // new configuration.
    try
    {
      loadDictionary(configuration);
    }
    catch (ConfigException | InitializationException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }
    catch (Exception e)
    {
      unacceptableReasons.add(getExceptionMessage(e));
      return false;
    }

    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                      DictionaryPasswordValidatorCfg configuration)
  {
    // Make sure we can load the dictionary.  If we can, then activate the new
    // configuration.
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      dictionary    = loadDictionary(configuration);
      currentConfig = configuration;
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryConfig.getServerErrorResultCode());
      ccr.addMessage(getExceptionMessage(e));
    }
    return ccr;
  }

  private boolean isDictionaryBased(String password,
                                    int minSubstringLength)
  {
    HashSet<String> dictionary = this.dictionary;
    final int passwordLength = password.length();

    for (int i = 0; i < passwordLength; i++)
    {
      for (int j = i + minSubstringLength; j <= passwordLength; j++)
      {
        String substring = password.substring(i, j);
        if (dictionary.contains(substring))
        {
          return true;
        }
      }
    }

    return false;
  }
}
