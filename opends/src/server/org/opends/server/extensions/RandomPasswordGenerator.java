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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NamedCharacterSet;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server password
 * generator that will create random passwords based on fixed-length strings
 * built from one or more character sets.
 */
public class RandomPasswordGenerator
       extends PasswordGenerator
       implements ConfigurableComponent
{



  // The encoded list of character sets defined for this password generator.
  private List<String> encodedCharacterSets;

  // The DN of the configuration entry for this password generator.
  private DN configEntryDN;

  // The total length of the password that will be generated.
  private int totalLength;

  // The numbers of characters of each type that should be used to generate the
  // passwords.
  private int[] characterCounts;

  // The character sets that should be used to generate the passwords.
  private NamedCharacterSet[] characterSets;

  // The lock to use to ensure that the character sets and counts are not
  // altered while a password is being generated.
  private ReentrantLock generatorLock;

  // The character set format string for this password generator.
  private String formatString;



  /**
   * Initializes this password generator based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this password generator.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializePasswordGenerator(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {


    this.configEntryDN = configEntry.getDN();
    generatorLock = new ReentrantLock();


    // Get the character sets for use in generating the password.  At least one
    // must have been provided.
    HashMap<String,NamedCharacterSet> charsets =
         new HashMap<String,NamedCharacterSet>();
    int msgID = MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET;
    StringConfigAttribute charsetStub =
         new StringConfigAttribute(ATTR_PASSWORD_CHARSET, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute charsetAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(charsetStub);
      if (charsetAttr == null)
      {
        msgID = MSGID_RANDOMPWGEN_NO_CHARSETS;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        encodedCharacterSets = charsetAttr.activeValues();
        for (NamedCharacterSet s :
             NamedCharacterSet.decodeCharacterSets(encodedCharacterSets))
        {
          if (charsets.containsKey(s.getName()))
          {
            msgID = MSGID_RANDOMPWGEN_CHARSET_NAME_CONFLICT;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        s.getName());
            throw new ConfigException(msgID, message);
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
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.
    msgID = MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT;
    StringConfigAttribute pwFormatStub =
         new StringConfigAttribute(ATTR_PASSWORD_FORMAT, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute pwFormatAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(pwFormatStub);
      if (pwFormatAttr == null)
      {
        msgID = MSGID_RANDOMPWGEN_NO_PWFORMAT;
        String message = getMessage(msgID);
        throw new ConfigException(msgID, message);
      }
      else
      {
        formatString = pwFormatAttr.activeValue();
        StringTokenizer tokenizer = new StringTokenizer(formatString, ", ");

        ArrayList<NamedCharacterSet> setList =
             new ArrayList<NamedCharacterSet>();
        ArrayList<Integer> countList = new ArrayList<Integer>();

        while (tokenizer.hasMoreTokens())
        {
          String token = tokenizer.nextToken();

          try
          {
            int    colonPos = token.indexOf(':');
            String name     = token.substring(0, colonPos);
            int    count    = Integer.parseInt(token.substring(colonPos+1));

            NamedCharacterSet charset = charsets.get(name);
            if (charset == null)
            {
              msgID = MSGID_RANDOMPWGEN_UNKNOWN_CHARSET;
              String message = getMessage(msgID, String.valueOf(formatString),
                                          String.valueOf(name));
              throw new ConfigException(msgID, message);
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
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, e);
            }

            msgID = MSGID_RANDOMPWGEN_INVALID_PWFORMAT;
            String message = getMessage(msgID, String.valueOf(formatString));
            throw new ConfigException(msgID, message, e);
          }
        }

        characterSets   = new NamedCharacterSet[setList.size()];
        characterCounts = new int[characterSets.length];

        totalLength = 0;
        for (int i=0; i < characterSets.length; i++)
        {
          characterSets[i]    = setList.get(i);
          characterCounts[i]  = countList.get(i);
          totalLength        += characterCounts[i];
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any finalization work that may be necessary when this password
   * generator is taken out of service.
   */
  public void finalizePasswordGenerator()
  {

    DirectoryServer.deregisterConfigurableComponent(this);
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
  public ByteString generatePassword(Entry userEntry)
         throws DirectoryException
  {


    StringBuilder buffer = new StringBuilder(totalLength);

    generatorLock.lock();

    try
    {
      for (int i=0; i < characterSets.length; i++)
      {
        characterSets[i].getRandomCharacters(buffer, characterCounts[i]);
      }
    }
    finally
    {
      generatorLock.unlock();
    }

    return ByteStringFactory.create(buffer.toString());
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {


    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {


    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    ArrayList<String> charsetValues = new ArrayList<String>();
    for (int i=0; i < characterSets.length; i++)
    {
      String encodedValue = characterSets[i].encode();
      if (! charsetValues.contains(encodedValue))
      {
        charsetValues.add(encodedValue);
      }
    }


    int msgID = MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET;
    attrList.add(new StringConfigAttribute(ATTR_PASSWORD_CHARSET,
                                           getMessage(msgID), true, true, false,
                                           charsetValues));

    msgID = MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT;
    attrList.add(new StringConfigAttribute(ATTR_PASSWORD_FORMAT,
                                           getMessage(msgID), true, false,
                                           false, formatString));


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {


    // Get the character sets for use in generating the password.  At least one
    // must have been provided.
    HashMap<String,NamedCharacterSet> charsets =
         new HashMap<String,NamedCharacterSet>();
    int msgID = MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET;
    StringConfigAttribute charsetStub =
         new StringConfigAttribute(ATTR_PASSWORD_CHARSET, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute charsetAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(charsetStub);
      if (charsetAttr == null)
      {
        msgID = MSGID_RANDOMPWGEN_NO_CHARSETS;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        return false;
      }
      else
      {
        for (NamedCharacterSet s :
             NamedCharacterSet.decodeCharacterSets(charsetAttr.activeValues()))
        {
          if (charsets.containsKey(s.getName()))
          {
            msgID = MSGID_RANDOMPWGEN_CHARSET_NAME_CONFLICT;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        s.getName());
            unacceptableReasons.add(message);
            return false;
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
      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.
    msgID = MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT;
    StringConfigAttribute pwFormatStub =
         new StringConfigAttribute(ATTR_PASSWORD_FORMAT, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute pwFormatAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(pwFormatStub);
      if (pwFormatAttr == null)
      {
        unacceptableReasons.add(getMessage(MSGID_RANDOMPWGEN_NO_PWFORMAT));
        return false;
      }
      else
      {
        String formatString = pwFormatAttr.activeValue();
        StringTokenizer tokenizer = new StringTokenizer(formatString, ", ");

        while (tokenizer.hasMoreTokens())
        {
          String token = tokenizer.nextToken();

          try
          {
            int    colonPos = token.indexOf(':');
            String name     = token.substring(0, colonPos);
            int    count    = Integer.parseInt(token.substring(colonPos+1));

            NamedCharacterSet charset = charsets.get(name);
            if (charset == null)
            {
              msgID = MSGID_RANDOMPWGEN_UNKNOWN_CHARSET;
              String message = getMessage(msgID, String.valueOf(formatString),
                                          String.valueOf(name));
              unacceptableReasons.add(message);
              return false;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, e);
            }

            msgID = MSGID_RANDOMPWGEN_INVALID_PWFORMAT;
            String message = getMessage(msgID, String.valueOf(formatString));
            unacceptableReasons.add(message);
            return false;
          }
        }
      }
    }
    catch (ConfigException ce)
    {
      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the character sets for use in generating the password.  At least one
    // must have been provided.
    List<String> newEncodedCharacterSets = null;
    HashMap<String,NamedCharacterSet> charsets =
         new HashMap<String,NamedCharacterSet>();
    int msgID = MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET;
    StringConfigAttribute charsetStub =
         new StringConfigAttribute(ATTR_PASSWORD_CHARSET, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute charsetAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(charsetStub);
      if (charsetAttr == null)
      {
        msgID = MSGID_RANDOMPWGEN_NO_CHARSETS;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        }
      }
      else
      {
        newEncodedCharacterSets = charsetAttr.activeValues();
        for (NamedCharacterSet s :
             NamedCharacterSet.decodeCharacterSets(encodedCharacterSets))
        {
          if (charsets.containsKey(s.getName()))
          {
            msgID = MSGID_RANDOMPWGEN_CHARSET_NAME_CONFLICT;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    s.getName()));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
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
      messages.add(ce.getMessage());

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Get the value that describes which character set(s) and how many
    // characters from each should be used.
    ArrayList<NamedCharacterSet> newSetList =
         new ArrayList<NamedCharacterSet>();
    ArrayList<Integer> newCountList = new ArrayList<Integer>();
    String newFormatString = null;

    msgID = MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT;
    StringConfigAttribute pwFormatStub =
         new StringConfigAttribute(ATTR_PASSWORD_FORMAT, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute pwFormatAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(pwFormatStub);
      if (pwFormatAttr == null)
      {
        msgID = MSGID_RANDOMPWGEN_NO_PWFORMAT;
        messages.add(getMessage(MSGID_RANDOMPWGEN_NO_PWFORMAT));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        }
      }
      else
      {
        newFormatString = pwFormatAttr.activeValue();
        StringTokenizer tokenizer = new StringTokenizer(newFormatString, ", ");


        while (tokenizer.hasMoreTokens())
        {
          String token = tokenizer.nextToken();

          try
          {
            int    colonPos = token.indexOf(':');
            String name     = token.substring(0, colonPos);
            int    count    = Integer.parseInt(token.substring(colonPos+1));

            NamedCharacterSet charset = charsets.get(name);
            if (charset == null)
            {
              msgID = MSGID_RANDOMPWGEN_UNKNOWN_CHARSET;
              messages.add(getMessage(msgID, String.valueOf(newFormatString),
                                      String.valueOf(name)));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = ResultCode.CONSTRAINT_VIOLATION;
              }
            }
            else
            {
              newSetList.add(charset);
              newCountList.add(count);
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, e);
            }

            msgID = MSGID_RANDOMPWGEN_INVALID_PWFORMAT;
            messages.add(getMessage(msgID, String.valueOf(newFormatString)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();
            }
          }
        }
      }
    }
    catch (ConfigException ce)
    {
      messages.add(ce.getMessage());

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // If everything looks OK, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      generatorLock.lock();

      try
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
      finally
      {
        generatorLock.unlock();
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

