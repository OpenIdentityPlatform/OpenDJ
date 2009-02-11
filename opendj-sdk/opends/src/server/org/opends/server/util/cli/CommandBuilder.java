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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.util.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;

/**
 * Class used to be able to generate the non interactive mode.
 *
 */
public class CommandBuilder
{
  // The command name.
  private String commandName;

  // The subcommand name.
  private String subcommandName;

  private ArrayList<Argument> args = new ArrayList<Argument>();
  private HashSet<Argument> obfuscatedArgs = new HashSet<Argument>();

  // The value used to display arguments that must be obfuscated (such as
  // passwords).  This does not require localization (since the output of
  // command builder by its nature is not localized).
  private final static String OBFUSCATED_VALUE = "******";

  /**
   * The constructor for the CommandBuilder.
   * @param commandName the command name.
   */
  public CommandBuilder(String commandName)
  {
    this(commandName, null);
  }

  /**
   * The constructor for the CommandBuilder.
   * @param commandName the command name.
   * @param subcommandName the subcommand name.
   */
  public CommandBuilder(String commandName, String subcommandName)
  {
    this.commandName = commandName;
    this.subcommandName = subcommandName;
  }

  /**
   * Adds an argument to the list of the command builder.
   * @param argument the argument to be added.
   */
  public void addArgument(Argument argument)
  {
    // We use an ArrayList to be able to provide the possibility of updating
    // the position of the attributes.
    if (!args.contains(argument))
    {
      args.add(argument);
    }
  }

  /**
   * Adds an argument whose values must be obfuscated (passwords for instance).
   * @param argument the argument to be added.
   */
  public void addObfuscatedArgument(Argument argument)
  {
    addArgument(argument);
    obfuscatedArgs.add(argument);
  }

  /**
   * Removes the provided argument from this CommandBuilder.
   * @param argument the argument to be removed.
   * @return <CODE>true</CODE> if the attribute was present and removed and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeArgument(Argument argument)
  {
    obfuscatedArgs.remove(argument);
    return args.remove(argument);
  }

  /**
   * Appends the arguments of another command builder to this command builder.
   * @param builder the CommandBuilder to append.
   */
  public void append(CommandBuilder builder)
  {
    for (Argument arg : builder.args)
    {
      if (builder.isObfuscated(arg))
      {
        addObfuscatedArgument(arg);
      }
      else
      {
        addArgument(arg);
      }
    }
  }

  /**
   * Returns the String representation of this command builder (i.e. what we
   * want to show to the user).
   * @return the String representation of this command builder (i.e. what we
   * want to show to the user).
   */
  public String toString()
  {
    return toString(false);
  }

  /**
   * Returns the String representation of this command builder (i.e. what we
   * want to show to the user).
   * @param showObfuscated displays in clear the obfuscated values.
   * @return the String representation of this command builder (i.e. what we
   * want to show to the user).
   */
  private String toString(boolean showObfuscated)
  {
    StringBuilder builder = new StringBuilder();
    builder.append(commandName);
    if (subcommandName != null)
    {
      builder.append(" "+subcommandName);
    }
    for (Argument arg : args)
    {
      // This CLI is always using SSL, and the argument has been removed from
      // the user interface
      if (arg.getName().equals("useSSL") ) {
        continue;
      }
      String argName;
      if (arg.getLongIdentifier() != null)
      {
        argName = "--"+arg.getLongIdentifier();
      }
      else
      {
        argName = "-"+arg.getShortIdentifier();
      }
      String separator;
      if (SetupUtils.isWindows())
      {
        separator = " ";
      }
      else
      {
        separator = " \\\n          ";
      }

      if (arg instanceof BooleanArgument)
      {
        builder.append(separator+argName);
      }
      else if (arg instanceof FileBasedArgument)
      {
        for (String value :
          ((FileBasedArgument)arg).getNameToValueMap().keySet())
        {
          builder.append(separator+argName+" ");
          if (isObfuscated(arg) && !showObfuscated)
          {
            value = OBFUSCATED_VALUE;
          }
          else
          {
            value = escapeValue(value);
          }
          builder.append(value);
        }
      }
      else
      {
        for (String value : arg.getValues())
        {
          builder.append(separator+argName+" ");
          if (isObfuscated(arg) && !showObfuscated)
          {
            value = OBFUSCATED_VALUE;
          }
          else
          {
            value = escapeValue(value);
          }
          builder.append(value);
        }
      }
    }
    return builder.toString();
  }

  /**
   * Clears the arguments.
   */
  public void clearArguments()
  {
    args.clear();
    obfuscatedArgs.clear();
  }

  /**
   * Returns the list of arguments.
   * @return the list of arguments.
   */
  public List<Argument> getArguments()
  {
    return args;
  }

  /**
   * Tells whether the provided argument's values must be obfuscated or not.
   * @param argument the argument to handle.
   * @return <CODE>true</CODE> if the attribute's values must be obfuscated and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isObfuscated(Argument argument)
  {
    return obfuscatedArgs.contains(argument);
  }

  // Chars that require special treatment when passing them to command-line.
  private final static char[] charsToEscape = {' ', '\t', '\n', '|', ';', '<',
    '>', '(', ')', '$', '`', '\\', '"', '\''};
  /**
   * This method simply takes a value and tries to transform it (with escape or
   * '"') characters so that it can be used in a command line.
   * @param value the String to be treated.
   * @return the transformed value.
   */
  public static String escapeValue(String value)
  {
    StringBuilder b = new StringBuilder();
    if (SetupUtils.isUnix())
    {
      for (int i=0 ; i<value.length(); i++)
      {
        char c = value.charAt(i);
        boolean charToEscapeFound = false;
        for (int j=0; j<charsToEscape.length && !charToEscapeFound; j++)
        {
          charToEscapeFound = c == charsToEscape[j];
        }
        if (charToEscapeFound)
        {
          b.append('\\');
        }
        b.append(c);
      }
    }
    else
    {
      b.append('"').append(value).append('"');
    }

    return b.toString();
  }
}
