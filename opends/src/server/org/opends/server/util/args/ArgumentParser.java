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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.util.args;
import org.opends.messages.Message;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.TreeSet;
import java.util.Set;

import org.opends.server.core.DirectoryServer;
import org.opends.server.util.SetupUtils;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.messages.ToolMessages.*;

import org.opends.messages.MessageBuilder;


/**
 * This class defines a utility that can be used to deal with command-line
 * arguments for applications in a CLIP-compliant manner using either short
 * one-character or longer word-based arguments.  It is also integrated with the
 * Directory Server message catalog so that it can display messages in an
 * internationalizeable format, can automatically generate usage information,
 * can detect conflicts between arguments, and can interact with a properties
 * file to obtain default values for arguments there if they are not specified
 * on the command-line.
 */
public class ArgumentParser
{
  /**
   * The argument that will be used to indicate the file properties.
   */
  private StringArgument filePropertiesPathArgument;

  /**
   * The argument that will be used to indicate that we'll not look for
   * default properties file.
   */
  private BooleanArgument noPropertiesFileArgument;

  // The argument that will be used to trigger the display of usage information.
  private Argument usageArgument;

  // The set of unnamed trailing arguments that were provided for this parser.
  private ArrayList<String> trailingArguments;

  // Indicates whether this parser will allow additional unnamed arguments at
  // the end of the list.
  private boolean allowsTrailingArguments;

  // Indicates whether long arguments should be treated in a case-sensitive
  // manner.
  private boolean longArgumentsCaseSensitive;

  // Indicates whether the usage or version information has been displayed.
  private boolean usageOrVersionDisplayed;

  // Indicates whether the version argument was provided.
  private boolean versionPresent;

  // The set of arguments defined for this parser, referenced by short ID.
  private HashMap<Character,Argument> shortIDMap;

  //  The set of arguments defined for this parser, referenced by argument name.
  private HashMap<String,Argument> argumentMap;

  //  The set of arguments defined for this parser, referenced by long ID.
  private HashMap<String,Argument> longIDMap;

  // The maximum number of unnamed trailing arguments that may be provided.
  private int maxTrailingArguments;

  // The minimum number of unnamed trailing arguments that may be provided.
  private int minTrailingArguments;

  // The total set of arguments defined for this parser.
  private LinkedList<Argument> argumentList;

  // The output stream to which usage information should be printed.
  private OutputStream usageOutputStream;

  // The fully-qualified name of the Java class that should be invoked to launch
  // the program with which this argument parser is associated.
  private String mainClassName;

  // A human-readable description for the tool, which will be included when
  // displaying usage information.
  private Message toolDescription;

  // The display name that will be used for the trailing arguments in the usage
  // information.
  private String trailingArgsDisplayName;

  // The raw set of command-line arguments that were provided.
  private String[] rawArguments;

  /** Set of argument groups. */
  protected Set<ArgumentGroup> argumentGroups;


  /**
   * Group for arguments that have not been explicitly grouped.
   * These will appear at the top of the usage statement without
   * a header.
   */
  protected ArgumentGroup defaultArgGroup = new ArgumentGroup(
          Message.EMPTY, Integer.MAX_VALUE);


  /**
   * Group for arguments that are related to utility input/output like
   * verbose, quite, no-prompt etc.  These will appear toward the bottom
   * of the usage statement.
   */
  protected ArgumentGroup ldapArgGroup = new ArgumentGroup(
          INFO_DESCRIPTION_LDAP_CONNECTION_ARGS.get(), Integer.MIN_VALUE + 2);


  /**
   * Group for arguments that are related to utility input/output like
   * verbose, quite, no-prompt etc.  These will appear toward the bottom
   * of the usage statement.
   */
  protected ArgumentGroup ioArgGroup = new ArgumentGroup(
          INFO_DESCRIPTION_IO_ARGS.get(), Integer.MIN_VALUE + 1);


  /**
   * Group for arguments that are general like help, version etc.
   * These will appear at the end of the usage statement.
   */
  protected ArgumentGroup generalArgGroup = new ArgumentGroup(
          INFO_DESCRIPTION_GENERAL_ARGS.get(), Integer.MIN_VALUE);


  private final static String INDENT = "    ";
  private final static int MAX_LENGTH = SetupUtils.isWindows() ? 79 : 80;

  /**
   * Creates a new instance of this argument parser with no arguments.
   * Unnamed trailing arguments will not be allowed.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether long arguments should
   *                                     be treated in a case-sensitive manner.
   */
  public ArgumentParser(String mainClassName, Message toolDescription,
                        boolean longArgumentsCaseSensitive)
  {
    this.mainClassName              = mainClassName;
    this.toolDescription            = toolDescription;
    this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;

    argumentList            = new LinkedList<Argument>();
    argumentMap             = new HashMap<String,Argument>();
    shortIDMap              = new HashMap<Character,Argument>();
    longIDMap               = new HashMap<String,Argument>();
    allowsTrailingArguments = false;
    usageOrVersionDisplayed = false;
    versionPresent         = false;
    trailingArgsDisplayName = null;
    maxTrailingArguments    = 0;
    minTrailingArguments    = 0;
    trailingArguments       = new ArrayList<String>();
    rawArguments            = null;
    usageArgument           = null;
    filePropertiesPathArgument = null;
    noPropertiesFileArgument = null;
    usageOutputStream       = System.out;
    initGroups();
  }





  /**
   * Creates a new instance of this argument parser with no arguments that may
   * or may not be allowed to have unnamed trailing arguments.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether long arguments should
   *                                     be treated in a case-sensitive manner.
   * @param  allowsTrailingArguments     Indicates whether this parser allows
   *                                     unnamed trailing arguments to be
   *                                     provided.
   * @param  minTrailingArguments        The minimum number of unnamed trailing
   *                                     arguments that must be provided.  A
   *                                     value less than or equal to zero
   *                                     indicates that no minimum will be
   *                                     enforced.
   * @param  maxTrailingArguments        The maximum number of unnamed trailing
   *                                     arguments that may be provided.  A
   *                                     value less than or equal to zero
   *                                     indicates that no maximum will be
   *                                     enforced.
   * @param  trailingArgsDisplayName     The display name that should be used
   *                                     as a placeholder for unnamed trailing
   *                                     arguments in the generated usage
   *                                     information.
   */
  public ArgumentParser(String mainClassName, Message toolDescription,
                        boolean longArgumentsCaseSensitive,
                        boolean allowsTrailingArguments,
                        int minTrailingArguments, int maxTrailingArguments,
                        String trailingArgsDisplayName)
  {
    this.mainClassName              = mainClassName;
    this.toolDescription            = toolDescription;
    this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;
    this.allowsTrailingArguments    = allowsTrailingArguments;
    this.minTrailingArguments       = minTrailingArguments;
    this.maxTrailingArguments       = maxTrailingArguments;
    this.trailingArgsDisplayName    = trailingArgsDisplayName;

    argumentList      = new LinkedList<Argument>();
    argumentMap       = new HashMap<String,Argument>();
    shortIDMap        = new HashMap<Character,Argument>();
    longIDMap         = new HashMap<String,Argument>();
    trailingArguments = new ArrayList<String>();
    usageOrVersionDisplayed = false;
    versionPresent   = false;
    rawArguments      = null;
    usageArgument     = null;
    usageOutputStream = System.out;
    initGroups();
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
  public Message getToolDescription()
  {
    return toolDescription;
  }



  /**
   * Indicates whether this parser will allow unnamed trailing arguments.  These
   * will be arguments at the end of the list that are not preceded by either a
   * long or short identifier and will need to be manually parsed by the
   * application using this parser.  Note that once an unnamed trailing argument
   * has been identified, all remaining arguments will be classified as such.
   *
   * @return  <CODE>true</CODE> if this parser allows unnamed trailing
   *          arguments, or <CODE>false</CODE> if it does not.
   */
  public boolean allowsTrailingArguments()
  {
    return allowsTrailingArguments;
  }



  /**
   * Retrieves the minimum number of unnamed trailing arguments that must be
   * provided.
   *
   * @return  The minimum number of unnamed trailing arguments that must be
   *          provided, or a value less than or equal to zero if no minimum will
   *          be enforced.
   */
  public int getMinTrailingArguments()
  {
    return minTrailingArguments;
  }



  /**
   * Retrieves the maximum number of unnamed trailing arguments that may be
   * provided.
   *
   * @return  The maximum number of unnamed trailing arguments that may be
   *          provided, or a value less than or equal to zero if no maximum will
   *          be enforced.
   */
  public int getMaxTrailingArguments()
  {
    return maxTrailingArguments;
  }



  /**
   * Retrieves the list of all arguments that have been defined for this
   * argument parser.
   *
   * @return  The list of all arguments that have been defined for this argument
   *          parser.
   */
  public LinkedList<Argument> getArgumentList()
  {
    return argumentList;
  }



  /**
   * Retrieves the argument with the specified name.
   *
   * @param  name  The name of the argument to retrieve.
   *
   * @return  The argument with the specified name, or <CODE>null</CODE> if
   *          there is no such argument.
   */
  public Argument getArgument(String name)
  {
    return argumentMap.get(name);
  }



  /**
   * Retrieves the set of arguments mapped by the short identifier that may be
   * used to reference them.  Note that arguments that do not have a short
   * identifier will not be present in this list.
   *
   * @return  The set of arguments mapped by the short identifier that may be
   *          used to reference them.
   */
  public HashMap<Character,Argument> getArgumentsByShortID()
  {
    return shortIDMap;
  }



  /**
   * Retrieves the argument with the specified short identifier.
   *
   * @param  shortID  The short ID for the argument to retrieve.
   *
   * @return  The argument with the specified short identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getArgumentForShortID(Character shortID)
  {
    return shortIDMap.get(shortID);
  }



  /**
   * Retrieves the set of arguments mapped by the long identifier that may be
   * used to reference them.  Note that arguments that do not have a long
   * identifier will not be present in this list.
   *
   * @return  The set of arguments mapped by the long identifier that may be
   *          used to reference them.
   */
  public HashMap<String,Argument> getArgumentsByLongID()
  {
    return longIDMap;
  }



  /**
   * Retrieves the argument with the specified long identifier.
   *
   * @param  longID  The long identifier of the argument to retrieve.
   *
   * @return  The argument with the specified long identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getArgumentForLongID(String longID)
  {
    return longIDMap.get(longID);
  }



  /**
   * Retrieves the set of unnamed trailing arguments that were provided on the
   * command line.
   *
   * @return  The set of unnamed trailing arguments that were provided on the
   *          command line.
   */
  public ArrayList<String> getTrailingArguments()
  {
    return trailingArguments;
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
   * Sets the usage group description for the default argument group.
   *
   * @param description for the default group
   */
  public void setDefaultArgumentGroupDescription(Message description)
  {
    this.defaultArgGroup.setDescription(description);
  }


  /**
   * Sets the usage group description for the LDAP argument group.
   *
   * @param description for the LDAP group
   */
  public void setLdapArgumentGroupDescription(Message description)
  {
    this.ldapArgGroup.setDescription(description);
  }


  /**
   * Sets the usage group description for the input/output argument group.
   *
   * @param description for the input/output group
   */
  public void setInputOutputArgumentGroupDescription(Message description)
  {
    this.ioArgGroup.setDescription(description);
  }


  /**
   * Sets the usage group description for the general argument group.
   *
   * @param description for the general group
   */
  public void setGeneralArgumentGroupDescription(Message description)
  {
    this.generalArgGroup.setDescription(description);
  }


  /**
   * Adds the provided argument to the set of arguments handled by this parser.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addArgument(Argument argument)
         throws ArgumentException
  {
    addArgument(argument, null);
  }

  /**
   * Adds the provided argument to the set of arguments handled by this parser
   * and puts the arguement in the default group.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addDefaultArgument(Argument argument)
         throws ArgumentException
  {
    addArgument(argument, defaultArgGroup);
  }

  /**
   * Adds the provided argument to the set of arguments handled by this parser
   * and puts the argument in the LDAP connection group.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addLdapConnectionArgument(Argument argument)
         throws ArgumentException
  {
    addArgument(argument, ldapArgGroup);
  }

  /**
   * Adds the provided argument to the set of arguments handled by this parser
   * and puts the argument in the input/output group.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addInputOutputArgument(Argument argument)
         throws ArgumentException
  {
    addArgument(argument, ioArgGroup);
  }

  /**
   * Adds the provided argument to the set of arguments handled by this parser
   * and puts the arguement in the general group.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addGeneralArgument(Argument argument)
         throws ArgumentException
  {
    addArgument(argument, generalArgGroup);
  }

  /**
   * Adds the provided argument to the set of arguments handled by this parser.
   *
   * @param  argument  The argument to be added.
   * @param  group     The argument group to which the argument belongs.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             argument that has already been defined.
   */
  public void addArgument(Argument argument, ArgumentGroup group)
         throws ArgumentException
  {

    Character shortID = argument.getShortIdentifier();
    if ((shortID != null) && shortIDMap.containsKey(shortID))
    {
      String conflictingName = shortIDMap.get(shortID).getName();

      Message message = ERR_ARGPARSER_DUPLICATE_SHORT_ID.get(
          argument.getName(), String.valueOf(shortID), conflictingName);
      throw new ArgumentException(message);
    }

    String longID = argument.getLongIdentifier();
    if (longID != null)
    {
      if (! longArgumentsCaseSensitive)
      {
        longID = toLowerCase(longID);
      }
      if (longIDMap.containsKey(longID))
      {
        String conflictingName = longIDMap.get(longID).getName();

        Message message = ERR_ARGPARSER_DUPLICATE_LONG_ID.get(
            argument.getName(), String.valueOf(longID), conflictingName);
        throw new ArgumentException(message);
      }
    }

    if (shortID != null)
    {
      shortIDMap.put(shortID, argument);
    }

    if (longID != null)
    {
      longIDMap.put(longID, argument);
    }

    argumentList.add(argument);

    if (group == null) {
      group = getStandardGroup(argument);
    }
    group.addArgument(argument);
    argumentGroups.add(group);
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
   * Sets the provided argument which will be used to identify the
   * file properties.
   *
   * @param argument
   *          The argument which will be used to identify the file
   *          properties.
   */
  public void setFilePropertiesArgument(StringArgument argument)
  {
    filePropertiesPathArgument= argument;
  }

  /**
   * Sets the provided argument which will be used to identify the
   * file properties.
   *
   * @param argument
   *          The argument which will be used to indicate if we have to
   *          look for properties file.
   */
  public void setNoPropertiesFileArgument(BooleanArgument argument)
  {
    noPropertiesFileArgument = argument;
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
        Message message = ERR_ARGPARSER_CANNOT_READ_PROPERTIES_FILE.get(
            String.valueOf(propertiesFile), getExceptionMessage(e));
        throw new ArgumentException(message, e);
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

    boolean inTrailingArgs = false;

    int numArguments = rawArguments.length;
    for (int i=0; i < numArguments; i++)
    {
      String arg = rawArguments[i];

      if (inTrailingArgs)
      {
        trailingArguments.add(arg);
        if ((maxTrailingArguments > 0) &&
            (trailingArguments.size() > maxTrailingArguments))
        {
          Message message =
              ERR_ARGPARSER_TOO_MANY_TRAILING_ARGS.get(maxTrailingArguments);
          throw new ArgumentException(message);
        }

        continue;
      }

      if (arg.equals("--"))
      {
        // This is a special indicator that we have reached the end of the named
        // arguments and that everything that follows after this should be
        // considered trailing arguments.
        inTrailingArgs = true;
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
          Message message = ERR_ARGPARSER_LONG_ARG_WITHOUT_NAME.get(arg);
          throw new ArgumentException(message);
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

        // Get the argument with the specified name.
        Argument a = longIDMap.get(argName);
        if (a == null)
        {
          if (argName.equals(OPTION_LONG_HELP))
          {
            // "--help" will always be interpreted as requesting usage
            // information.
            try
            {
              getUsage(usageOutputStream);
            } catch (Exception e) {}

            return;
          }
          else
          if (argName.equals(OPTION_LONG_PRODUCT_VERSION))
          {
            // "--version" will always be interpreted as requesting version
            // information.
            usageOrVersionDisplayed = true;
            versionPresent = true;
            try
            {
              DirectoryServer.printVersion(usageOutputStream);
            } catch (Exception e) {}

            return;
          }
          else
          {
            // There is no such argument registered.
            Message message =
                ERR_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID.get(argName);
            throw new ArgumentException(message);
          }
        }
        else
        {
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
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              Message message =
                  ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get(argName);
              throw new ArgumentException(message);
            }

            argValue = rawArguments[++i];
          }

          MessageBuilder invalidReason = new MessageBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            Message message = ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.get(
                argValue, argName, invalidReason.toString());
            throw new ArgumentException(message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            Message message =
                ERR_ARGPARSER_NOT_MULTIVALUED_FOR_LONG_ID.get(argName);
            throw new ArgumentException(message);
          }

          a.addValue(argValue);
        }
        else
        {
          if (argValue != null)
          {
            Message message =
                ERR_ARGPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE.get(argName);
            throw new ArgumentException(message);
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
          Message message = ERR_ARGPARSER_INVALID_DASH_AS_ARGUMENT.get();
          throw new ArgumentException(message);
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


        // Get the argument with the specified short ID.
        Argument a = shortIDMap.get(argCharacter);
        if (a == null)
        {
          if (argCharacter == '?')
          {
            // "-?" will always be interpreted as requesting usage information.
            try
            {
              getUsage(usageOutputStream);
            } catch (Exception e) {}

            return;
          }
          else
          if ( (argCharacter == OPTION_SHORT_PRODUCT_VERSION)
               &&
               ( ! shortIDMap.containsKey(OPTION_SHORT_PRODUCT_VERSION)))
          {
            // "-V" will always be interpreted as requesting
            // version information except if it's already defined (e.g in
            // ldap tools).
            usageOrVersionDisplayed = true ;
            versionPresent = true;
            try
            {
              DirectoryServer.printVersion(usageOutputStream);
            } catch (Exception e) {}
            return;
          }
          else
          {
            // There is no such argument registered.
            Message message = ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID.get(
                String.valueOf(argCharacter));
            throw new ArgumentException(message);
          }
        }
        else
        {
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
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              Message message =
                  ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.
                    get(String.valueOf(argCharacter));
              throw new ArgumentException(message);
            }

            argValue = rawArguments[++i];
          }

          MessageBuilder invalidReason = new MessageBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            Message message = ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID.
                get(argValue, String.valueOf(argCharacter),
                    invalidReason.toString());
            throw new ArgumentException(message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            Message message = ERR_ARGPARSER_NOT_MULTIVALUED_FOR_SHORT_ID.get(
                String.valueOf(argCharacter));
            throw new ArgumentException(message);
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
              Argument b = shortIDMap.get(c);
              if (b == null)
              {
                // There is no such argument registered.
                Message message = ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID.get(
                    String.valueOf(argCharacter));
                throw new ArgumentException(message);
              }
              else if (b.needsValue())
              {
                // This means we're in a scenario like "-abc" where b is a
                // valid argument that takes a value.  We don't support that.
                Message message = ERR_ARGPARSER_CANT_MIX_ARGS_WITH_VALUES.get(
                    String.valueOf(argCharacter), argValue, String.valueOf(c));
                throw new ArgumentException(message);
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
      else if (allowsTrailingArguments)
      {
        // It doesn't start with a dash, so it must be a trailing argument if
        // that is acceptable.
        inTrailingArgs = true;
        trailingArguments.add(arg);
      }
      else
      {
        // It doesn't start with a dash and we don't allow trailing arguments,
        // so this is illegal.
        Message message = ERR_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT.get(arg);
        throw new ArgumentException(message);
      }
    }


    // If we allow trailing arguments and there is a minimum number, then make
    // sure at least that many were provided.
    if (allowsTrailingArguments && (minTrailingArguments > 0))
    {
      if (trailingArguments.size() < minTrailingArguments)
      {
        Message message =
            ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(minTrailingArguments);
        throw new ArgumentException(message);
      }
    }

    // If we don't have the argumentProperties, try to load a properties file.
    if (argumentProperties == null)
    {
      argumentProperties = checkExternalProperties();
    }

    // Iterate through all of the arguments.  For any that were not provided on
    // the command line, see if there is an alternate default that can be used.
    // For cases where there is not, see that argument is required.
    for (Argument a : argumentList)
    {
      if (! a.isPresent())
      {
        // See if there is a value in the properties that can be used
        if ((argumentProperties != null) && (a.getPropertyName() != null))
        {
          String value = argumentProperties.getProperty(a.getPropertyName()
              .toLowerCase());
          MessageBuilder invalidReason =  new MessageBuilder();
          if (value != null)
          {
            Boolean addValue = true;
            if (!( a instanceof BooleanArgument))
            {
              addValue = a.valueIsAcceptable(value, invalidReason);
            }
            if (addValue)
            {
              a.addValue(value);
              if (a.needsValue())
              {
                a.setPresent(true);
              }
              a.setValueSetByProperty(true);
            }
          }
        }
      }


      if ((! a.isPresent()) && a.needsValue())
      {
        // See if the argument defines a default.
        if (a.getDefaultValue() != null)
        {
          a.addValue(a.getDefaultValue());
        }

        // If there is still no value and the argument is required, then that's
        // a problem.
        if ((! a.hasValue()) && a.isRequired())
        {
          Message message =
              ERR_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG.get(a.getName());
          throw new ArgumentException(message);
        }
      }
    }
  }



  /**
   * Check if we have a properties file.
   *
   * @return The properties found in the properties file or null.
   * @throws ArgumentException
   *           If a problem was encountered while parsing the provided
   *           arguments.
   */
  protected Properties checkExternalProperties()
      throws ArgumentException
  {
    // We don't look for properties file.
    if ((noPropertiesFileArgument != null)
        && (noPropertiesFileArgument.isPresent()))
    {
      return null;
    }

    // Check if we have a properties file argument
    if (filePropertiesPathArgument == null)
    {
      return null;
    }

    // check if the properties file argument has been set. If not
    // look for default location.
    String propertiesFilePath = null;
    if (filePropertiesPathArgument.isPresent())
    {
      propertiesFilePath = filePropertiesPathArgument.getValue();
    }
    else
    {
      // Check in "user home"/.opends directory
      String userDir = System.getProperty("user.home");
      propertiesFilePath = findPropertiesFile(userDir + File.separator
          + DEFAULT_OPENDS_CONFIG_DIR);

      if (propertiesFilePath == null)
      {
        // check "Opends instance"/config directory
        String instanceDir = DirectoryServer.getServerRoot();
        propertiesFilePath = findPropertiesFile(instanceDir+ File.separator
            + "config");
      }
    }

    // We don't have a properties file location
    if (propertiesFilePath == null)
    {
      return null;
    }

    // We have a location for the properties file.
    Properties argumentProperties = new Properties();
    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    try
    {
      Properties p = new Properties();
      FileInputStream fis = new FileInputStream(propertiesFilePath);
      p.load(fis);
      fis.close();

      for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements();)
      {
        String currentPropertyName = (String) e.nextElement();
        String propertyName = currentPropertyName;

        // Property name form <script name>.<property name> has the
        // precedence to <property name>
        if (scriptName != null)
        {
          if (currentPropertyName.startsWith(scriptName))
          {
           propertyName = currentPropertyName
                .substring(scriptName.length() + 1);
          }
          else
          {
            if (p.containsKey(scriptName + "." + currentPropertyName ))
            {
              continue;
            }
          }
        }
        argumentProperties.setProperty(propertyName.toLowerCase(), p
            .getProperty(currentPropertyName));
      }
    }
    catch (Exception e)
    {
      Message message = ERR_ARGPARSER_CANNOT_READ_PROPERTIES_FILE.get(String
          .valueOf(propertiesFilePath), getExceptionMessage(e));
      throw new ArgumentException(message, e);
    }
    return argumentProperties;
  }


  /**
   * Get the absolute path of the properties file.
   *
   * @param directory
   *          The location in which we should look for properties file
   * @return The absolute path of the properties file or null
   */
  private String findPropertiesFile(String directory)
  {
    // Look for the tools properties file
    File f = new File(directory,DEFAULT_OPENDS_PROPERTIES_FILE_NAME
        + DEFAULT_OPENDS_PROPERTIES_FILE_EXTENSION);
    if (f.exists() && f.canRead())
    {
      return f.getAbsolutePath();
    }
    else
    {
      return null;
    }
  }

  /**
   * Appends usage information based on the defined arguments to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the usage information should be
   *          appended.
   */
  public void getUsage(StringBuilder buffer)
  {
    usageOrVersionDisplayed = true;
    if ((toolDescription != null) && (toolDescription.length() > 0))
    {
      buffer.append(wrapText(toolDescription.toString(), MAX_LENGTH - 1));
      buffer.append(EOL);
      buffer.append(EOL);
    }

    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      buffer.append(INFO_ARGPARSER_USAGE_JAVA_CLASSNAME.get(mainClassName));
    }
    else
    {
      buffer.append(INFO_ARGPARSER_USAGE_JAVA_SCRIPTNAME.get(scriptName));
    }

    if (allowsTrailingArguments)
    {
      if (trailingArgsDisplayName == null)
      {
        buffer.append(" "+INFO_ARGPARSER_USAGE_TRAILINGARGS.get());
      }
      else
      {
        buffer.append(" ");
        buffer.append(trailingArgsDisplayName);
      }
    }
    buffer.append(EOL);
    buffer.append(INFO_SUBCMDPARSER_WHERE_OPTIONS_INCLUDE.get());
    buffer.append(EOL);
    buffer.append(EOL);

    Argument helpArgument = null ;

    boolean printHeaders = printUsageGroupHeaders();
    for (ArgumentGroup argGroup : argumentGroups)
    {
      if (argGroup.containsArguments() && printHeaders)
      {
        // Print the groups description if any
        Message groupDesc = argGroup.getDescription();
        if (groupDesc != null && !Message.EMPTY.equals(groupDesc)) {
          buffer.append(EOL);
          buffer.append(wrapText(groupDesc.toString(), MAX_LENGTH - 1));
          buffer.append(EOL);
          buffer.append(EOL);
        }
      }

      for (Argument a : argGroup.getArguments())
      {
        // If this argument is hidden, then skip it.
        if (a.isHidden())
        {
          continue;
        }

        // Help argument should be printed at the end
        if ((usageArgument != null) &&
                usageArgument.getName().equals(a.getName()))
        {
          helpArgument = a ;
          continue ;
        }
        printArgumentUsage(a, buffer);
      }
    }
    if (helpArgument != null)
    {
      printArgumentUsage(helpArgument, buffer);
    }
    else
    {
      buffer.append(EOL);
      buffer.append("-?");
      buffer.append(EOL);
    }
  }



  /**
   * Retrieves a message containing usage information based on the defined
   * arguments.
   *
   * @return  A string containing usage information based on the defined
   *          arguments.
   */
  public Message getUsageMessage()
  {
    StringBuilder buffer = new StringBuilder();
    getUsage(buffer);

    // TODO: rework getUsage(OutputStream) to work with messages framework
    return Message.raw(buffer.toString());
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
    getUsage(buffer);

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
    getUsage(buffer);

    outputStream.write(getBytes(buffer.toString()));
  }



  /**
   * Indicates whether the version or the usage information has been
   * displayed to the end user either by an explicit argument like
   * "-H" or "--help", or by a built-in argument like "-?".
   *
   * @return {@code true} if the usage information has been displayed,
   *         or {@code false} if not.
   */
  public boolean usageOrVersionDisplayed()
  {
    return usageOrVersionDisplayed;
  }

  /**
  * Appends argument usage information to the provided buffer.
  *
  * @param a The argument to handle.
  * @param buffer
  *          The buffer to which the usage information should be
  *          appended.
  */
   private void printArgumentUsage(Argument a, StringBuilder buffer)
  {
    // Write a line with the short and/or long identifiers that may be
    // used
    // for the argument.
    final int indentLength = INDENT.length();
    Character shortID = a.getShortIdentifier();
    String longID = a.getLongIdentifier();
    if (shortID != null)
    {
      int currentLength = buffer.length();

      if (usageArgument.getName().equals(a.getName()))
      {
        buffer.append("-?, ");
      }

      buffer.append("-");
      buffer.append(shortID.charValue());

      if (a.needsValue() && longID == null)
      {
        buffer.append(" ");
        buffer.append(a.getValuePlaceholder());
      }

      if (longID != null)
      {
        StringBuilder newBuffer = new StringBuilder();
        newBuffer.append(", --");
        newBuffer.append(longID);

        if (a.needsValue())
        {
          newBuffer.append(" ");
          newBuffer.append(a.getValuePlaceholder());
        }

        int lineLength = (buffer.length() - currentLength) +
                         newBuffer.length();
        if (lineLength > MAX_LENGTH)
        {
          buffer.append(EOL);
          buffer.append(newBuffer.toString());
        }
        else
        {
          buffer.append(newBuffer.toString());
        }
      }

      buffer.append(EOL);
    }
    else
    {
      if (longID != null)
      {
        if (usageArgument.getName().equals(a.getName()))
        {
          buffer.append("-?, ");
        }
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


    // Write one or more lines with the description of the argument.
    // We will
    // indent the description five characters and try our best to wrap
    // at or
    // before column 79 so it will be friendly to 80-column displays.
    Message description = a.getDescription();
    int descMaxLength = MAX_LENGTH - indentLength - 1;
    if (description.length() <= descMaxLength)
    {
      buffer.append(INDENT);
      buffer.append(description);
      buffer.append(EOL);
    }
    else
    {
      String s = description.toString();
      while (s.length() > descMaxLength)
      {
        int spacePos = s.lastIndexOf(' ', descMaxLength);
        if (spacePos > 0)
        {
          buffer.append(INDENT);
          buffer.append(s.substring(0, spacePos).trim());
          s = s.substring(spacePos+1).trim();
          buffer.append(EOL);
        }
        else
        {
          // There are no spaces in the first 74 columns. See if there
          // is one
          // after that point. If so, then break there. If not, then
          // don't
          // break at all.
          spacePos = s.indexOf(' ');
          if (spacePos > 0)
          {
            buffer.append(INDENT);
            buffer.append(s.substring(0, spacePos).trim());
            s = s.substring(spacePos+1).trim();
            buffer.append(EOL);
          }
          else
          {
            buffer.append(INDENT);
            buffer.append(s);
            s = "";
            buffer.append(EOL);
          }
        }
      }

      if (s.length() > 0)
      {
        buffer.append(INDENT);
        buffer.append(s);
        buffer.append(EOL);
      }
    }
  }

  /**
   * Given an argument, returns an appropriate group.  Arguments may
   * be part of one of the special groups or the default group.
   *
   * @param argument for which a group is requested
   * @return argument group appropriate for <code>argument</code>
   */
  protected ArgumentGroup getStandardGroup(Argument argument) {
    ArgumentGroup group;
    if (isInputOutputArgument(argument)) {
      group = ioArgGroup;
    } else if (isGeneralArgument(argument)) {
      group = generalArgGroup;
    } else if (isLdapConnectionArgument(argument)) {
      group = ldapArgGroup;
    } else {
      group = defaultArgGroup;
    }
    return group;
  }

  /**
   * Indicates whether or not argument group description headers
   * should be printed.
   *
   * @return boolean where true means print the descriptions
   */
  protected boolean printUsageGroupHeaders() {
    // If there is only a single group then we won't print them.
    int groupsContainingArgs = 0;
    for (ArgumentGroup argGroup : argumentGroups)
    {
      if (argGroup.containsNonHiddenArguments())
      {
        groupsContainingArgs++;
      }
    }
    return groupsContainingArgs > 1;
  }

  private void initGroups() {
    this.argumentGroups = new TreeSet<ArgumentGroup>();
    this.argumentGroups.add(defaultArgGroup);
    this.argumentGroups.add(ldapArgGroup);
    this.argumentGroups.add(generalArgGroup);
    this.argumentGroups.add(ioArgGroup);

    try {
      Argument version = new BooleanArgument(
              OPTION_LONG_PRODUCT_VERSION,
              null,
              OPTION_LONG_PRODUCT_VERSION,
              INFO_DESCRIPTION_PRODUCT_VERSION.get());
      this.generalArgGroup.addArgument(version);
    } catch (ArgumentException e) {
      // ignore
    }
  }


  private boolean isInputOutputArgument(Argument arg) {
    boolean io = false;
    if (arg != null) {
      String longId = arg.getLongIdentifier();
      io = OPTION_LONG_VERBOSE.equals(longId) ||
              OPTION_LONG_QUIET.equals(longId) ||
              OPTION_LONG_NO_PROMPT.equals(longId) ||
              OPTION_LONG_PROP_FILE_PATH.equals(longId) ||
              OPTION_LONG_NO_PROP_FILE.equals(longId) ||
              OPTION_LONG_SCRIPT_FRIENDLY.equals(longId) ||
              OPTION_LONG_DONT_WRAP.equals(longId) ||
              OPTION_LONG_ENCODING.equals(longId);
    }
    return io;
  }

  private boolean isLdapConnectionArgument(Argument arg) {
    boolean ldap = false;
    if (arg != null) {
      String longId = arg.getLongIdentifier();
      ldap = OPTION_LONG_USE_SSL.equals(longId) ||
              OPTION_LONG_START_TLS.equals(longId) ||
              OPTION_LONG_HOST.equals(longId) ||
              OPTION_LONG_PORT.equals(longId) ||
              OPTION_LONG_BINDDN.equals(longId) ||
              OPTION_LONG_BINDPWD.equals(longId) ||
              OPTION_LONG_BINDPWD_FILE.equals(longId) ||
              OPTION_LONG_SASLOPTION.equals(longId) ||
              OPTION_LONG_TRUSTALL.equals(longId) ||
              OPTION_LONG_TRUSTSTOREPATH.equals(longId) ||
              OPTION_LONG_TRUSTSTORE_PWD.equals(longId) ||
              OPTION_LONG_TRUSTSTORE_PWD_FILE.equals(longId) ||
              OPTION_LONG_KEYSTOREPATH.equals(longId) ||
              OPTION_LONG_KEYSTORE_PWD.equals(longId) ||
              OPTION_LONG_KEYSTORE_PWD_FILE.equals(longId) ||
              OPTION_LONG_CERT_NICKNAME.equals(longId) ||
              OPTION_LONG_REFERENCED_HOST_NAME.equals(longId) ||
              OPTION_LONG_ADMIN_UID.equals(longId) ||
              OPTION_LONG_REPORT_AUTHZ_ID.equals(longId) ||
              OPTION_LONG_USE_PW_POLICY_CTL.equals(longId) ||
              OPTION_LONG_USE_SASL_EXTERNAL.equals(longId) ||
              OPTION_LONG_PROTOCOL_VERSION.equals(longId);
    }
    return ldap;
  }


  private boolean isGeneralArgument(Argument arg) {
    boolean general = false;
    if (arg != null) {
      String longId = arg.getLongIdentifier();
      general = OPTION_LONG_HELP.equals(longId) ||
                    OPTION_LONG_PRODUCT_VERSION.equals(longId);
    }
    return general;
  }

  /**
   * Returns whether the usage argument was provided or not.  This method
   * should be called after a call to parseArguments.
   * @return <CODE>true</CODE> if the usage argument was provided and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isUsageArgumentPresent()
  {
    boolean isUsageArgumentPresent = false;
    if (usageArgument != null)
    {
      isUsageArgumentPresent = usageArgument.isPresent();
    }
    return isUsageArgumentPresent;
  }

  /**
   * Returns whether the version argument was provided or not.  This method
   * should be called after a call to parseArguments.
   * @return <CODE>true</CODE> if the version argument was provided and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isVersionArgumentPresent()
  {
    return versionPresent;
  }
}

