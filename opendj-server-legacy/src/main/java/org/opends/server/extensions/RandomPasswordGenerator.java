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

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PasswordGeneratorCfg;
import org.opends.server.admin.std.server.RandomPasswordGeneratorCfg;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

/**
 * This class provides an implementation of a Directory Server password
 * generator that will create random passwords based on fixed-length strings
 * built from one or more character sets.
 */
public class RandomPasswordGenerator
       extends PasswordGenerator<RandomPasswordGeneratorCfg>
       implements ConfigurationChangeListener<RandomPasswordGeneratorCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /** The current configuration for this password validator. */
  private RandomPasswordGeneratorCfg currentConfig;

  /** The encoded list of character sets defined for this password generator. */
  private SortedSet<String> encodedCharacterSets;

  /** The DN of the configuration entry for this password generator. */
  private DN configEntryDN;

  /** The total length of the password that will be generated. */
  private int totalLength;

  /**
   * The numbers of characters of each type that should be used to generate the
   * passwords.
   */
  private int[] characterCounts;

  /** The character sets that should be used to generate the passwords. */
  private NamedCharacterSet[] characterSets;

  /**
   * The lock to use to ensure that the character sets and counts are not
   * altered while a password is being generated.
   */
  private Object generatorLock;

  /** The character set format string for this password generator. */
  private String formatString;



  /** {@inheritDoc} */
  @Override
  public void initializePasswordGenerator(
      RandomPasswordGeneratorCfg configuration)
         throws ConfigException, InitializationException
  {
    this.configEntryDN = configuration.dn();
    generatorLock = new Object();

    // Get the character sets for use in generating the password.  At least one
    // must have been provided.
    HashMap<String,NamedCharacterSet> charsets = new HashMap<>();

    try
    {
      encodedCharacterSets = configuration.getPasswordCharacterSet();

      if (encodedCharacterSets.isEmpty())
      {
        LocalizableMessage message = ERR_RANDOMPWGEN_NO_CHARSETS.get(configEntryDN);
        throw new ConfigException(message);
      }
      for (NamedCharacterSet s : NamedCharacterSet
          .decodeCharacterSets(encodedCharacterSets))
      {
        if (charsets.containsKey(s.getName()))
        {
          LocalizableMessage message = ERR_RANDOMPWGEN_CHARSET_NAME_CONFLICT.get(configEntryDN, s.getName());
          throw new ConfigException(message);
        }
        else
        {
          charsets.put(s.getName(), s);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS.get(getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.

    try
    {
      formatString = configuration.getPasswordFormat();
      StringTokenizer tokenizer = new StringTokenizer(formatString, ", ");

      ArrayList<NamedCharacterSet> setList = new ArrayList<>();
      ArrayList<Integer> countList = new ArrayList<>();

      while (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();

        try
        {
          int colonPos = token.indexOf(':');
          String name = token.substring(0, colonPos);
          int count = Integer.parseInt(token.substring(colonPos + 1));

          NamedCharacterSet charset = charsets.get(name);
          if (charset == null)
          {
            throw new ConfigException(ERR_RANDOMPWGEN_UNKNOWN_CHARSET.get(formatString, name));
          }
          else
          {
            setList.add(charset);
            countList.add(count);
          }
        }
        catch (ConfigException ce)
        {
          throw ce;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_RANDOMPWGEN_INVALID_PWFORMAT.get(formatString);
          throw new ConfigException(message, e);
        }
      }

      characterSets = new NamedCharacterSet[setList.size()];
      characterCounts = new int[characterSets.length];

      totalLength = 0;
      for (int i = 0; i < characterSets.length; i++)
      {
        characterSets[i] = setList.get(i);
        characterCounts[i] = countList.get(i);
        totalLength += characterCounts[i];
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT.get(getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    configuration.addRandomChangeListener(this) ;
    currentConfig = configuration;
  }



  /** {@inheritDoc} */
  @Override
  public void finalizePasswordGenerator()
  {
    currentConfig.removeRandomChangeListener(this);
  }



  /**
   * Generates a password for the user whose account is contained in the
   * specified entry.
   *
   * @param  userEntry  The entry for the user for whom the password is to be
   *                    generated.
   *
   * @return  The password that has been generated for the user.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              generate the password.
   */
  @Override
  public ByteString generatePassword(Entry userEntry)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder(totalLength);

    synchronized (generatorLock)
    {
      for (int i=0; i < characterSets.length; i++)
      {
        characterSets[i].getRandomCharacters(buffer, characterCounts[i]);
      }
    }

    return ByteString.valueOf(buffer);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PasswordGeneratorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    RandomPasswordGeneratorCfg config =
         (RandomPasswordGeneratorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      RandomPasswordGeneratorCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    DN cfgEntryDN = configuration.dn();

    // Get the character sets for use in generating the password.
    // At least one must have been provided.
    HashMap<String,NamedCharacterSet> charsets = new HashMap<>();
    try
    {
      SortedSet<String> currentPasSet = configuration.getPasswordCharacterSet();
      if (currentPasSet.isEmpty())
      {
        throw new ConfigException(ERR_RANDOMPWGEN_NO_CHARSETS.get(cfgEntryDN));
      }

      for (NamedCharacterSet s : NamedCharacterSet
          .decodeCharacterSets(currentPasSet))
      {
        if (charsets.containsKey(s.getName()))
        {
          unacceptableReasons.add(ERR_RANDOMPWGEN_CHARSET_NAME_CONFLICT.get(cfgEntryDN, s.getName()));
          return false;
        }
        else
        {
          charsets.put(s.getName(), s);
        }
      }
    }
    catch (ConfigException ce)
    {
      unacceptableReasons.add(ce.getMessageObject());
      return false;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS.get(
              getExceptionMessage(e));
      unacceptableReasons.add(message);
      return false;
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.
    try
    {
        String formatString = configuration.getPasswordFormat() ;
        StringTokenizer tokenizer = new StringTokenizer(formatString, ", ");

        while (tokenizer.hasMoreTokens())
        {
          String token = tokenizer.nextToken();

          try
          {
            int    colonPos = token.indexOf(':');
            String name     = token.substring(0, colonPos);

            NamedCharacterSet charset = charsets.get(name);
            if (charset == null)
            {
              unacceptableReasons.add(ERR_RANDOMPWGEN_UNKNOWN_CHARSET.get(formatString, name));
              return false;
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            unacceptableReasons.add(ERR_RANDOMPWGEN_INVALID_PWFORMAT.get(formatString));
            return false;
          }
        }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT.get(
              getExceptionMessage(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      RandomPasswordGeneratorCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Get the character sets for use in generating the password.  At least one
    // must have been provided.
    SortedSet<String> newEncodedCharacterSets = null;
    HashMap<String,NamedCharacterSet> charsets = new HashMap<>();
    try
    {
      newEncodedCharacterSets = configuration.getPasswordCharacterSet();
      if (newEncodedCharacterSets.isEmpty())
      {
        ccr.addMessage(ERR_RANDOMPWGEN_NO_CHARSETS.get(configEntryDN));
        ccr.setResultCodeIfSuccess(ResultCode.OBJECTCLASS_VIOLATION);
      }
      else
      {
        for (NamedCharacterSet s :
             NamedCharacterSet.decodeCharacterSets(newEncodedCharacterSets))
        {
          if (charsets.containsKey(s.getName()))
          {
            ccr.addMessage(ERR_RANDOMPWGEN_CHARSET_NAME_CONFLICT.get(configEntryDN, s.getName()));
            ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
          }
          else
          {
            charsets.put(s.getName(), s);
          }
        }
      }
    }
    catch (ConfigException ce)
    {
      ccr.addMessage(ce.getMessageObject());
      ccr.setResultCodeIfSuccess(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS.get(getExceptionMessage(e)));
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.
    ArrayList<NamedCharacterSet> newSetList = new ArrayList<>();
    ArrayList<Integer> newCountList = new ArrayList<>();
    String newFormatString = null;

    try
    {
      newFormatString = configuration.getPasswordFormat();
      StringTokenizer tokenizer = new StringTokenizer(newFormatString, ", ");

      while (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();

        try
        {
          int colonPos = token.indexOf(':');
          String name = token.substring(0, colonPos);
          int count = Integer.parseInt(token.substring(colonPos + 1));

          NamedCharacterSet charset = charsets.get(name);
          if (charset == null)
          {
            ccr.addMessage(ERR_RANDOMPWGEN_UNKNOWN_CHARSET.get(newFormatString, name));
            ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
          }
          else
          {
            newSetList.add(charset);
            newCountList.add(count);
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.addMessage(ERR_RANDOMPWGEN_INVALID_PWFORMAT.get(newFormatString));
          ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT.get(getExceptionMessage(e)));
      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
    }


    // If everything looks OK, then apply the changes.
    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      synchronized (generatorLock)
      {
        encodedCharacterSets = newEncodedCharacterSets;
        formatString         = newFormatString;

        characterSets   = new NamedCharacterSet[newSetList.size()];
        characterCounts = new int[characterSets.length];

        totalLength = 0;
        for (int i=0; i < characterCounts.length; i++)
        {
          characterSets[i]    = newSetList.get(i);
          characterCounts[i]  = newCountList.get(i);
          totalLength        += characterCounts[i];
        }
      }
    }

    return ccr;
  }
}
