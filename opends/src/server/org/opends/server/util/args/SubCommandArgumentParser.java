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
package org.opends.server.util.args;



import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a variant of the argument parser that can be used with
 * applications that use subcommands to customize their behavior and that have a
 * different set of options per subcommand (e.g, "cvs checkout" takes different
 * options than "cvs commit").  This parser also has the ability to use global
 * options that will always be applicable regardless of the subcommand in
 * addition to the subcommand-specific arguments.  There must not be any
 * conflicts between the global options and the option for any subcommand, but
 * it is allowed to re-use subcommand-specific options for different purposes
 * between different subcommands.
 */
public class SubCommandArgumentParser
{
  // The argument that will be used to trigger the display of usage information.
  private Argument usageArgument;

  // Indicates whether subcommand and long argument names should be treated in a
  // case-sensitive manner.
  private boolean longArgumentsCaseSensitive;

  // The set of global arguments defined for this parser, referenced by short
  // ID.
  private HashMap<Character,Argument> globalShortIDMap;

  //  The set of global arguments defined for this parser, referenced by
  // argument name.
  private HashMap<String,Argument> globalArgumentMap;

  //  The set of global arguments defined for this parser, referenced by long
  // ID.
  private HashMap<String,Argument> globalLongIDMap;

  // The set of subcommands defined for this parser, referenced by subcommand
  // name.
  private HashMap<String,SubCommand> subCommands;

  // The total set of global arguments defined for this parser.
  private LinkedList<Argument> globalArgumentList;

  // The output stream to which usage information should be printed.
  private OutputStream usageOutputStream;

  // The fully-qualified name of the Java class that should be invoked to launch
  // the program with which this argument parser is associated.
  private String mainClassName;

  // A human-readable description for the tool, which will be included when
  // displaying usage information.
  private String toolDescription;

  // The raw set of command-line arguments that were provided.
  private String[] rawArguments;

  // The subcommand requested by the user as part of the command-line arguments.
  private SubCommand subCommand;



  /**
   * Creates a new instance of this subcommand argument parser with no
   * arguments.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether subcommand and long
   *                                     argument names should be treated in a
   *                                     case-sensitive manner.
   */
  public SubCommandArgumentParser(String mainClassName, String toolDescription,
                                  boolean longArgumentsCaseSensitive)
  {
    this.mainClassName              = mainClassName;
    this.toolDescription            = toolDescription;
    this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;

    globalArgumentList = new LinkedList<Argument>();
    globalArgumentMap  = new HashMap<String,Argument>();
    globalShortIDMap   = new HashMap<Character,Argument>();
    globalLongIDMap    = new HashMap<String,Argument>();
    subCommands        = new HashMap<String,SubCommand>();
    rawArguments       = null;
    subCommand         = null;
    usageArgument      = null;
    usageOutputStream  = null;
  }



  /**
   * Retrieves the fully-qualified name of the Java class that should be invoked
   * to launch the program with which this argument parser is associated.
   *
   * @return  The fully-qualified name of the Java class that should be invoked
   *          to launch the program with which this argument parser is
   *          associated.
   */
  public String getMainClassName()
  {
    return mainClassName;
  }



  /**
   * Retrieves a human-readable description for this tool, which should be
   * included at the top of the command-line usage information.
   *
   * @return  A human-readable description for this tool, or {@code null} if
   *          none is available.
   */
  public String getToolDescription()
  {
    return toolDescription;
  }



  /**
   * Indicates whether subcommand names and long argument strings should be
   * treated in a case-sensitive manner.
   *
   * @return  <CODE>true</CODE> if subcommand names and long argument strings
   *          should be treated in a case-sensitive manner, or
   *          <CODE>false</CODE> if they should not.
   */
  public boolean longArgumentsCaseSensitive()
  {
    return longArgumentsCaseSensitive;
  }



  /**
   * Retrieves the list of all global arguments that have been defined for this
   * argument parser.
   *
   * @return  The list of all global arguments that have been defined for this
   *          argument parser.
   */
  public LinkedList<Argument> getGlobalArgumentList()
  {
    return globalArgumentList;
  }



  /**
   * Indicates whether this argument parser contains a global argument with the
   * specified name.
   *
   * @param  argumentName  The name for which to make the determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          name, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgument(String argumentName)
  {
    return globalArgumentMap.containsKey(argumentName);
  }



  /**
   * Retrieves the global argument with the specified name.
   *
   * @param  name  The name of the global argument to retrieve.
   *
   * @return  The global argument with the specified name, or <CODE>null</CODE>
   *          if there is no such argument.
   */
  public Argument getGlobalArgument(String name)
  {
    return globalArgumentMap.get(name);
  }



  /**
   * Retrieves the set of global arguments mapped by the short identifier that
   * may be used to reference them.  Note that arguments that do not have a
   * short identifier will not be present in this list.
   *
   * @return  The set of global arguments mapped by the short identifier that
   *          may be used to reference them.
   */
  public HashMap<Character,Argument> getGlobalArgumentsByShortID()
  {
    return globalShortIDMap;
  }



  /**
   * Indicates whether this argument parser has a global argument with the
   * specified short ID.
   *
   * @param  shortID  The short ID character for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          short ID, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgumentWithShortID(Character shortID)
  {
    return globalShortIDMap.containsKey(shortID);
  }



  /**
   * Retrieves the global argument with the specified short identifier.
   *
   * @param  shortID  The short identifier for the global argument to retrieve.
   *
   * @return  The global argument with the specified short identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getGlobalArgumentForShortID(Character shortID)
  {
    return globalShortIDMap.get(shortID);
  }



  /**
   * Retrieves the set of global arguments mapped by the long identifier that
   * may be used to reference them.  Note that arguments that do not have a long
   * identifier will not be present in this list.
   *
   * @return  The set of global arguments mapped by the long identifier that may
   *          be used to reference them.
   */
  public HashMap<String,Argument> getGlobalArgumentsByLongID()
  {
    return globalLongIDMap;
  }



  /**
   * Indicates whether this argument parser has a global argument with the
   * specified long ID.
   *
   * @param  longID  The long ID string for which to make the determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          long ID, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgumentWithLongID(String longID)
  {
    return globalLongIDMap.containsKey(longID);
  }



  /**
   * Retrieves the global argument with the specified long identifier.
   *
   * @param  longID  The long identifier for the global argument to retrieve.
   *
   * @return  The global argument with the specified long identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getGlobalArgumentForLongID(String longID)
  {
    return globalLongIDMap.get(longID);
  }



  /**
   * Retrieves the set of subcommands defined for this argument parser,
   * referenced by subcommand name.
   *
   * @return  The set of subcommands defined for this argument parser,
   *          referenced by subcommand name.
   */
  public HashMap<String,SubCommand> getSubCommands()
  {
    return subCommands;
  }



  /**
   * Indicates whether this argument parser has a subcommand with the specified
   * name.
   *
   * @param  name  The subcommand name for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this argument parser has a subcommand with
   *          the specified name, or <CODE>false</CODE> if it does not.
   */
  public boolean hasSubCommand(String name)
  {
    return subCommands.containsKey(name);
  }



  /**
   * Retrieves the subcommand with the specified name.
   *
   * @param  name  The name of the subcommand to retrieve.
   *
   * @return  The subcommand with the specified name, or <CODE>null</CODE> if no
   *          such subcommand is defined.
   */
  public SubCommand getSubCommand(String name)
  {
    return subCommands.get(name);
  }



  /**
   * Retrieves the subcommand that was selected in the set of command-line
   * arguments.
   *
   * @return  The subcommand that was selected in the set of command-line
   *          arguments, or <CODE>null</CODE> if none was selected.
   */
  public SubCommand getSubCommand()
  {
    return subCommand;
  }



  /**
   * Retrieves the raw set of arguments that were provided.
   *
   * @return  The raw set of arguments that were provided, or <CODE>null</CODE>
   *          if the argument list has not yet been parsed.
   */
  public String[] getRawArguments()
  {
    return rawArguments;
  }



  /**
   * Adds the provided argument to the set of global arguments handled by this
   * parser.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             global or subcommand argument that has already
   *                             been defined.
   */
  public void addGlobalArgument(Argument argument)
         throws ArgumentException
  {
    String argumentName = argument.getName();
    if (globalArgumentMap.containsKey(argumentName))
    {
      int    msgID   = MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_NAME;
      String message = getMessage(msgID, argumentName);
      throw new ArgumentException(msgID, message);
    }
    for (SubCommand s : subCommands.values())
    {
      if (s.getArgumentForName(argumentName) != null)
      {
        int    msgID   = MSGID_SUBCMDPARSER_GLOBAL_ARG_NAME_SUBCMD_CONFLICT;
        String message = getMessage(msgID, argumentName, s.getName());
        throw new ArgumentException(msgID, message);
      }
    }


    Character shortID = argument.getShortIdentifier();
    if (shortID != null)
    {
      if (globalShortIDMap.containsKey(shortID))
      {
        String name = globalShortIDMap.get(shortID).getName();

        int    msgID   = MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_SHORT_ID;
        String message = getMessage(msgID, String.valueOf(shortID),
                                    argumentName, name);
        throw new ArgumentException(msgID, message);
      }

      for (SubCommand s : subCommands.values())
      {
        if (s.getArgument(shortID) != null)
        {
          String cmdName = s.getName();
          String name    = s.getArgument(shortID).getName();

          int    msgID   = MSGID_SUBCMDPARSER_GLOBAL_ARG_SHORT_ID_CONFLICT;
          String message = getMessage(msgID, String.valueOf(shortID),
                                      argumentName, name, cmdName);
          throw new ArgumentException(msgID, message);
        }
      }
    }


    String longID = argument.getLongIdentifier();
    if (longID != null)
    {
      if (! longArgumentsCaseSensitive)
      {
        longID = toLowerCase(longID);
      }

      if (globalLongIDMap.containsKey(longID))
      {
        String name = globalLongIDMap.get(longID).getName();

        int    msgID   = MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_LONG_ID;
        String message = getMessage(msgID, longID, argumentName, name);
        throw new ArgumentException(msgID, message);
      }

      for (SubCommand s : subCommands.values())
      {
        if (s.getArgument(longID) != null)
        {
          String cmdName = s.getName();
          String name    = s.getArgument(longID).getName();

          int    msgID   = MSGID_SUBCMDPARSER_GLOBAL_ARG_LONG_ID_CONFLICT;
          String message = getMessage(msgID, longID, argumentName, name,
                                      cmdName);
          throw new ArgumentException(msgID, message);
        }
      }
    }


    if (shortID != null)
    {
      globalShortIDMap.put(shortID, argument);
    }

    if (longID != null)
    {
      globalLongIDMap.put(longID, argument);
    }

    globalArgumentList.add(argument);
  }



  /**
   * Sets the provided argument as one which will automatically trigger the
   * output of usage information if it is provided on the command line and no
   * further argument validation will be performed.  Note that the caller will
   * still need to add this argument to the parser with the
   * <CODE>addArgument</CODE> method, and the argument should not be required
   * and should not take a value.  Also, the caller will still need to check
   * for the presence of the usage argument after calling
   * <CODE>parseArguments</CODE> to know that no further processing will be
   * required.
   *
   * @param  argument      The argument whose presence should automatically
   *                       trigger the display of usage information.
   */
  public void setUsageArgument(Argument argument)
  {
    usageArgument     = argument;
    usageOutputStream = System.out;
  }



  /**
   * Sets the provided argument as one which will automatically trigger the
   * output of usage information if it is provided on the command line and no
   * further argument validation will be performed.  Note that the caller will
   * still need to add this argument to the parser with the
   * <CODE>addArgument</CODE> method, and the argument should not be required
   * and should not take a value.  Also, the caller will still need to check
   * for the presence of the usage argument after calling
   * <CODE>parseArguments</CODE> to know that no further processing will be
   * required.
   *
   * @param  argument      The argument whose presence should automatically
   *                       trigger the display of usage information.
   * @param  outputStream  The output stream to which the usage information
   *                       should be written.
   */
  public void setUsageArgument(Argument argument, OutputStream outputStream)
  {
    usageArgument     = argument;
    usageOutputStream = outputStream;
  }



  /**
   * Adds the provided subcommand to this argument parser.  This is only
   * intended for use by the <CODE>SubCommand</CODE> constructor and does not
   * do any validation of its own to ensure that there are no conflicts with the
   * subcommand or any of its arguments.
   *
   * @param  subCommand  The subcommand to add to this argument parser.
   */
  void addSubCommand(SubCommand subCommand)
  {
    subCommands.put(toLowerCase(subCommand.getName()), subCommand);
  }



  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.
   *
   * @param  rawArguments  The raw set of arguments to parse.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments.
   */
  public void parseArguments(String[] rawArguments)
         throws ArgumentException
  {
    parseArguments(rawArguments, null);
  }



  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.  Default values for unspecified arguments
   * may be read from the specified properties file.
   *
   * @param  rawArguments           The set of raw arguments to parse.
   * @param  propertiesFile         The path to the properties file to use to
   *                                obtain default values for unspecified
   *                                properties.
   * @param  requirePropertiesFile  Indicates whether the parsing should fail if
   *                                the provided properties file does not exist
   *                                or is not accessible.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments or interacting with the
   *                             properties file.
   */
  public void parseArguments(String[] rawArguments, String propertiesFile,
                             boolean requirePropertiesFile)
         throws ArgumentException
  {
    this.rawArguments = rawArguments;

    Properties argumentProperties = null;

    try
    {
      Properties p = new Properties();
      FileInputStream fis = new FileInputStream(propertiesFile);
      p.load(fis);
      fis.close();
      argumentProperties = p;
    }
    catch (Exception e)
    {
      if (requirePropertiesFile)
      {
        int    msgID   = MSGID_SUBCMDPARSER_CANNOT_READ_PROPERTIES_FILE;
        String message = getMessage(msgID, String.valueOf(propertiesFile),
                                    stackTraceToSingleLineString(e));
        throw new ArgumentException(msgID, message, e);
      }
    }

    parseArguments(rawArguments, argumentProperties);
  }



  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.  Default values for unspecified arguments may
   * be read from the specified properties if any are provided.
   *
   * @param  rawArguments        The set of raw arguments to parse.
   * @param  argumentProperties  A set of properties that may be used to provide
   *                             default values for arguments not included in
   *                             the given raw arguments.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments.
   */
  public void parseArguments(String[] rawArguments,
                             Properties argumentProperties)
         throws ArgumentException
  {
    this.rawArguments = rawArguments;

    int numArguments = rawArguments.length;
    for (int i=0; i < numArguments; i++)
    {
      String arg = rawArguments[i];

      if (arg.equals("--"))
      {
        // This is not legal because we don't allow unnamed trailing arguments
        // in this parser.
        int    msgID   = MSGID_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME;
        String message = getMessage(msgID, arg);
        throw new ArgumentException(msgID, message);
      }
      else if (arg.startsWith("--"))
      {
        // This indicates that we are using the long name to reference the
        // argument.  It may be in any of the following forms:
        // --name
        // --name value
        // --name=value

        String argName  = arg.substring(2);
        String argValue = null;
        int    equalPos = argName.indexOf('=');
        if (equalPos < 0)
        {
          // This is fine.  The value is not part of the argument name token.
        }
        else if (equalPos == 0)
        {
          // The argument starts with "--=", which is not acceptable.
          int    msgID   = MSGID_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME;
          String message = getMessage(msgID, arg);
          throw new ArgumentException(msgID, message);
        }
        else
        {
          // The argument is in the form --name=value, so parse them both out.
          argValue = argName.substring(equalPos+1);
          argName  = argName.substring(0, equalPos);
        }

        // If we're not case-sensitive, then convert the name to lowercase.
        if (! longArgumentsCaseSensitive)
        {
          argName = toLowerCase(argName);
        }

        // See if the specified name references a global argument.  If not, then
        // see if it references a subcommand argument.
        Argument a = globalLongIDMap.get(argName);
        if (a == null)
        {
          if (subCommand == null)
          {
            // There is no such global argument.
            int    msgID   = MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID;
            String message = getMessage(msgID, argName);
            throw new ArgumentException(msgID, message);
          }
          else
          {
            a = subCommand.getArgument(argName);
            if (a == null)
            {
              // There is no such global or subcommand argument.
              int    msgID   = MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_LONG_ID;
              String message = getMessage(msgID, argName);
              throw new ArgumentException(msgID, message);
            }
          }
        }

        a.setPresent(true);

        // If this is the usage argument, then immediately stop and print
        // usage information.
        if ((usageArgument != null) &&
            usageArgument.getName().equals(a.getName()))
        {
          try
          {
            getUsage(usageOutputStream);
          } catch (Exception e) {}

          return;
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              int msgID = MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID;
              String message = getMessage(msgID, argName);
              throw new ArgumentException(msgID, message);
            }

            argValue = rawArguments[++i];
          }

          StringBuilder invalidReason = new StringBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            int    msgID   = MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID;
            String message = getMessage(msgID, argValue, argName,
                                        invalidReason.toString());
            throw new ArgumentException(msgID, message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            int    msgID   = MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_LONG_ID;
            String message = getMessage(msgID, argName);
            throw new ArgumentException(msgID, message);
          }

          a.addValue(argValue);
        }
        else
        {
          if (argValue != null)
          {
            int msgID = MSGID_SUBCMDPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE;
            String message = getMessage(msgID, argName);
            throw new ArgumentException(msgID, message);
          }
        }
      }
      else if (arg.startsWith("-"))
      {
        // This indicates that we are using the 1-character name to reference
        // the argument.  It may be in any of the following forms:
        // -n
        // -nvalue
        // -n value
        if (arg.equals("-"))
        {
          int    msgID   = MSGID_SUBCMDPARSER_INVALID_DASH_AS_ARGUMENT;
          String message = getMessage(msgID);
          throw new ArgumentException(msgID, message);
        }

        char argCharacter = arg.charAt(1);
        String argValue;
        if (arg.length() > 2)
        {
          argValue = arg.substring(2);
        }
        else
        {
          argValue = null;
        }


        // Get the argument with the specified short ID.  It may be either a
        // global argument or a subcommand-specific argument.
        Argument a = globalShortIDMap.get(argCharacter);
        if (a == null)
        {
          if (subCommand == null)
          {
            // There is no such argument registered.
            int msgID = MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID;
            String message = getMessage(msgID, String.valueOf(argCharacter));
            throw new ArgumentException(msgID, message);
          }
          else
          {
            a = subCommand.getArgument(argCharacter);
            if (a == null)
            {
              // There is no such argument registered.
              int    msgID   = MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID;
              String message = getMessage(msgID, String.valueOf(argCharacter));
              throw new ArgumentException(msgID, message);
            }
          }
        }

        a.setPresent(true);

        // If this is the usage argument, then immediately stop and print
        // usage information.
        if ((usageArgument != null) &&
            usageArgument.getName().equals(a.getName()))
        {
          try
          {
            getUsage(usageOutputStream);
          } catch (Exception e) {}

          return;
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              int msgID =
                       MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID;
              String message = getMessage(msgID, String.valueOf(argCharacter));
              throw new ArgumentException(msgID, message);
            }

            argValue = rawArguments[++i];
          }

          StringBuilder invalidReason = new StringBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            int    msgID   = MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID;
            String message = getMessage(msgID, argValue,
                                        String.valueOf(argCharacter),
                                        invalidReason.toString());
            throw new ArgumentException(msgID, message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            int    msgID   = MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_SHORT_ID;
            String message = getMessage(msgID, String.valueOf(argCharacter));
            throw new ArgumentException(msgID, message);
          }

          a.addValue(argValue);
        }
        else
        {
          if (argValue != null)
          {
            // If we've gotten here, then it means that we're in a scenario like
            // "-abc" where "a" is a valid argument that doesn't take a value.
            // However, this could still be valid if all remaining characters in
            // the value are also valid argument characters that don't take
            // values.
            int valueLength = argValue.length();
            for (int j=0; j < valueLength; j++)
            {
              char c = argValue.charAt(j);
              Argument b = globalShortIDMap.get(c);
              if (b == null)
              {
                if (subCommand == null)
                {
                  int msgID =
                           MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID;
                  String message = getMessage(msgID,
                                              String.valueOf(argCharacter));
                  throw new ArgumentException(msgID, message);
                }
                else
                {
                  b = subCommand.getArgument(c);
                  if (b == null)
                  {
                    int msgID = MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID;
                    String message = getMessage(msgID,
                                                String.valueOf(argCharacter));
                    throw new ArgumentException(msgID, message);
                  }
                }
              }

              if (b.needsValue())
              {
                // This means we're in a scenario like "-abc" where b is a
                // valid argument that takes a value.  We don't support that.
                int msgID = MSGID_SUBCMDPARSER_CANT_MIX_ARGS_WITH_VALUES;
                String message = getMessage(msgID, String.valueOf(argCharacter),
                                            argValue, String.valueOf(c));
                throw new ArgumentException(msgID, message);
              }
              else
              {
                b.setPresent(true);

                // If this is the usage argument, then immediately stop and
                // print usage information.
                if ((usageArgument != null) &&
                    usageArgument.getName().equals(b.getName()))
                {
                  try
                  {
                    getUsage(usageOutputStream);
                  } catch (Exception e) {}

                  return;
                }
              }
            }
          }
        }
      }
      else
      {
        // It's not a short or long identifier, so check to see if it is a
        // subcommand name.  If not, then it's invalid.  If so, then make sure
        // that it was the only subcommand provided.
        String nameToCheck = arg;
        if (! longArgumentsCaseSensitive)
        {
          nameToCheck = toLowerCase(arg);
        }

        SubCommand sc = subCommands.get(nameToCheck);
        if (sc == null)
        {
          int    msgID   = MSGID_SUBCMDPARSER_INVALID_ARGUMENT;
          String message = getMessage(msgID, arg);
          throw new ArgumentException(msgID, message);
        }
        else if (subCommand == null)
        {
          subCommand = sc;
        }
        else
        {
          int    msgID   = MSGID_SUBCMDPARSER_MULTIPLE_SUBCOMMANDS;
          String message = getMessage(msgID, arg, subCommand.getName());
          throw new ArgumentException(msgID, message);
        }
      }
    }


    // Iterate through all the global arguments and make sure that they have
    // values or a suitable default is available.
    for (Argument a : globalArgumentList)
    {
      if ((! a.isPresent()) && a.needsValue())
      {
        // See if there is a default value in the properties that can be used.
        boolean valueSet = false;
        if ((argumentProperties != null) && (a.getPropertyName() != null))
        {
          String value = argumentProperties.getProperty(a.getPropertyName());
          if (value != null)
          {
            a.addValue(value);
            valueSet = true;
          }
        }

        // If there is still no value, then see if the argument defines a
        // default.
        if ((! valueSet) && (a.getDefaultValue() != null))
        {
          a.addValue(a.getDefaultValue());
          valueSet = true;
        }

        // If there is still no value and the argument is required, then that's
        // a problem.
        if ((! valueSet) && a.isRequired())
        {
          int    msgID = MSGID_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG;
          String message = getMessage(msgID, a.getName());
          throw new ArgumentException(msgID, message);
        }
      }
    }


    // Iterate through all the subcommand-specific arguments and make sure that
    // they have values or a suitable default is available.
    if (subCommand != null)
    {
      for (Argument a : subCommand.getArguments())
      {
        if ((! a.isPresent()) && a.needsValue())
        {
          // See if there is a default value in the properties that can be used.
          boolean valueSet = false;
          if ((argumentProperties != null) && (a.getPropertyName() != null))
          {
            String value = argumentProperties.getProperty(a.getPropertyName());
            if (value != null)
            {
              a.addValue(value);
              valueSet = true;
            }
          }

          // If there is still no value, then see if the argument defines a
          // default.
          if ((! valueSet) && (a.getDefaultValue() != null))
          {
            a.addValue(a.getDefaultValue());
            valueSet = true;
          }

          // If there is still no value and the argument is required, then
          // that's a problem.
          if ((! valueSet) && a.isRequired())
          {
            int    msgID = MSGID_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG;
            String message = getMessage(msgID, a.getName());
            throw new ArgumentException(msgID, message);
          }
        }
      }
    }
  }



  /**
   * Appends complete usage information to the provided buffer.  It will include
   * information about global options as well as all subcommand-specific
   * options.  The output will be somewhat compressed in that it will not
   * include any of the descriptions for any of the arguments.
   *
   * @param  buffer  The buffer to which the usage information should be
   *                 appended.
   */
  public void getFullUsage(StringBuilder buffer)
  {
    if ((toolDescription != null) && (toolDescription.length() > 0))
    {
      buffer.append(wrapText(toolDescription, 79));
      buffer.append(EOL);
    }

    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      buffer.append("Usage:  java ");
      buffer.append(mainClassName);
    }
    else
    {
      buffer.append("Usage:  ");
      buffer.append(scriptName);
    }

    buffer.append(" {subcommand} {options}");
    buffer.append(EOL);
    buffer.append(EOL);

    buffer.append("Global Options:");
    buffer.append(EOL);

    for (Argument a : globalArgumentList)
    {
      if (a.isHidden())
      {
        continue;
      }

      String value;
      if (a.needsValue())
      {
        String valuePlaceholder = a.getValuePlaceholder();
        if (a == null)
        {
          value = " {value}";
        }
        else
        {
          value = " " + valuePlaceholder;
        }
      }
      else
      {
        value = "";
      }

      Character shortIDChar = a.getShortIdentifier();
      if (shortIDChar != null)
      {
        buffer.append("     -");
        buffer.append(shortIDChar);
        buffer.append(value);

        String longIDString = a.getLongIdentifier();
        if (longIDString != null)
        {
          buffer.append("  or  --");
          buffer.append(longIDString);
          buffer.append(value);
        }
      }
      else
      {
        String longIDString = a.getLongIdentifier();
        if (longIDString != null)
        {
          buffer.append("     --");
          buffer.append(longIDString);
          buffer.append(value);
        }
      }
    }

    buffer.append(EOL);


    for (SubCommand sc : subCommands.values())
    {
      buffer.append(sc.getName());
      buffer.append(" Subcommand Options:");
      buffer.append(EOL);

      for (Argument a : sc.getArguments())
      {
        if (a.isHidden())
          {
          continue;
        }

        String value;
        if (a.needsValue())
        {
          String valuePlaceholder = a.getValuePlaceholder();
          if (a == null)
          {
            value = " {value}";
          }
          else
          {
            value = " " + valuePlaceholder;
          }
        }
        else
        {
          value = "";
        }

        Character shortIDChar = a.getShortIdentifier();
        if (shortIDChar != null)
        {
          buffer.append("     -");
          buffer.append(shortIDChar);
          buffer.append(value);

          String longIDString = a.getLongIdentifier();
          if (longIDString != null)
          {
            buffer.append("  or  --");
            buffer.append(longIDString);
            buffer.append(value);
          }
        }
        else
        {
          String longIDString = a.getLongIdentifier();
          if (longIDString != null)
          {
            buffer.append("     --");
            buffer.append(longIDString);
            buffer.append(value);
          }
        }

        buffer.append(EOL);
      }
    }
  }



  /**
   * Appends usage information for the specified subcommand to the provided
   * buffer.
   *
   * @param  buffer      The buffer to which the usage information should be
   *                     appended.
   * @param  subCommand  The subcommand for which to display the usage
   *                     information.
   */
  public void getSubCommandUsage(StringBuilder buffer, SubCommand subCommand)
  {
    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      buffer.append("Usage:  java ");
      buffer.append(mainClassName);
    }
    else
    {
      buffer.append("Usage:  ");
      buffer.append(scriptName);
    }

    buffer.append(" ");
    buffer.append(subCommand.getName());
    buffer.append(" {options}");
    buffer.append(EOL);
    buffer.append("             where {options} include:");

    for (Argument a : globalArgumentList)
    {
      // If this argument is hidden, then skip it.
      if (a.isHidden())
      {
        continue;
      }


      // Write a line with the short and/or long identifiers that may be used
      // for the argument.
      Character shortID = a.getShortIdentifier();
      if (shortID != null)
      {
        buffer.append("-");
        buffer.append(shortID.charValue());

        if (a.needsValue())
        {
          buffer.append(" ");
          buffer.append(a.getValuePlaceholder());
        }

        String longID = a.getLongIdentifier();
        if (longID != null)
        {
          buffer.append("   or   --");
          buffer.append(longID);

          if (a.needsValue())
          {
            buffer.append(" ");
            buffer.append(a.getValuePlaceholder());
          }
        }

        buffer.append(EOL);
      }
      else
      {
        String longID = a.getLongIdentifier();
        if (longID != null)
        {
          buffer.append("--");
          buffer.append(longID);

          if (a.needsValue())
          {
            buffer.append(" ");
            buffer.append(a.getValuePlaceholder());
          }

          buffer.append(EOL);
        }
      }


      // Write one or more lines with the description of the argument.  We will
      // indent the description five characters and try our best to wrap at or
      // before column 79 so it will be friendly to 80-column displays.
      String description = a.getDescription();
      if (description.length() <= 75)
      {
        buffer.append("    ");
        buffer.append(description);
        buffer.append(EOL);
      }
      else
      {
        String s = description;
        while (s.length() > 75)
        {
          int spacePos = s.lastIndexOf(' ', 75);
          if (spacePos > 0)
          {
            buffer.append("    ");
            buffer.append(s.substring(0, spacePos).trim());
            s = s.substring(spacePos+1).trim();
            buffer.append(EOL);
          }
          else
          {
            // There are no spaces in the first 74 columns.  See if there is one
            // after that point.  If so, then break there.  If not, then don't
            // break at all.
            spacePos = s.indexOf(' ');
            if (spacePos > 0)
            {
              buffer.append("    ");
              buffer.append(s.substring(0, spacePos).trim());
              s = s.substring(spacePos+1).trim();
              buffer.append(EOL);
            }
            else
            {
              buffer.append("    ");
              buffer.append(s);
              s = "";
              buffer.append(EOL);
            }
          }
        }

        if (s.length() > 0)
        {
          buffer.append("    ");
          buffer.append(s);
          buffer.append(EOL);
        }
      }

      buffer.append(EOL);
    }

    for (Argument a : subCommand.getArguments())
    {
      // If this argument is hidden, then skip it.
      if (a.isHidden())
      {
        continue;
      }


      // Write a line with the short and/or long identifiers that may be used
      // for the argument.
      Character shortID = a.getShortIdentifier();
      if (shortID != null)
      {
        int currentLength = buffer.length();

        buffer.append("-");
        buffer.append(shortID.charValue());

        if (a.needsValue())
        {
          buffer.append(" ");
          buffer.append(a.getValuePlaceholder());
        }

        String longID = a.getLongIdentifier();
        if (longID != null)
        {
          StringBuilder newBuffer = new StringBuilder();
          newBuffer.append("or   --");
          newBuffer.append(longID);

          if (a.needsValue())
          {
            newBuffer.append(" ");
            newBuffer.append(a.getValuePlaceholder());
          }

          int lineLength = (buffer.length() - currentLength) + 2 +
                           newBuffer.length();
          if (lineLength > 80)
          {
            buffer.append(EOL);
            buffer.append(newBuffer.toString());
          }
          else
          {
            buffer.append("  ");
            buffer.append(newBuffer.toString());
          }
        }

        buffer.append(EOL);
      }
      else
      {
        String longID = a.getLongIdentifier();
        if (longID != null)
        {
          buffer.append("--");
          buffer.append(longID);

          if (a.needsValue())
          {
            buffer.append(" ");
            buffer.append(a.getValuePlaceholder());
          }

          buffer.append(EOL);
        }
      }


      // Write one or more lines with the description of the argument.  We will
      // indent the description five characters and try our best to wrap at or
      // before column 79 so it will be friendly to 80-column displays.
      String description = a.getDescription();
      if (description.length() <= 74)
      {
        buffer.append("     ");
        buffer.append(description);
        buffer.append(EOL);
      }
      else
      {
        String s = description;
        while (s.length() > 74)
        {
          int spacePos = s.lastIndexOf(' ', 74);
          if (spacePos > 0)
          {
            buffer.append("     ");
            buffer.append(s.substring(0, spacePos).trim());
            s = s.substring(spacePos+1).trim();
            buffer.append(EOL);
          }
          else
          {
            // There are no spaces in the first 74 columns.  See if there is one
            // after that point.  If so, then break there.  If not, then don't
            // break at all.
            spacePos = s.indexOf(' ');
            if (spacePos > 0)
            {
              buffer.append("     ");
              buffer.append(s.substring(0, spacePos).trim());
              s = s.substring(spacePos+1).trim();
              buffer.append(EOL);
            }
            else
            {
              buffer.append("     ");
              buffer.append(s);
              s = "";
              buffer.append(EOL);
            }
          }
        }

        if (s.length() > 0)
        {
          buffer.append("     ");
          buffer.append(s);
          buffer.append(EOL);
        }
      }
    }
  }



  /**
   * Retrieves a string containing usage information based on the defined
   * arguments.
   *
   * @return  A string containing usage information based on the defined
   *          arguments.
   */
  public String getUsage()
  {
    StringBuilder buffer = new StringBuilder();

    if (subCommand == null)
    {
      getFullUsage(buffer);
    }
    else
    {
      getSubCommandUsage(buffer, subCommand);
    }

    return buffer.toString();
  }



  /**
   * Writes usage information based on the defined arguments to the provided
   * output stream.
   *
   * @param  outputStream  The output stream to which the usage information
   *                       should be written.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       usage information to the provided output stream.
   */
  public void getUsage(OutputStream outputStream)
         throws IOException
  {
    StringBuilder buffer = new StringBuilder();

    if (subCommand == null)
    {
      getFullUsage(buffer);
    }
    else
    {
      getSubCommandUsage(buffer, subCommand);
    }

    outputStream.write(getBytes(buffer.toString()));
  }
}

