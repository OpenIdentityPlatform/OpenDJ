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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util.args;



import java.util.HashMap;
import java.util.LinkedList;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for holding information about a
 * subcommand that may be used with the subcommand argument parser.  The
 * subcommand has a name, a description, and a set of arguments.
 */
public class SubCommand
{
  // The mapping between the short argument IDs and the arguments for this
  // subcommand.
  private HashMap<Character,Argument> shortIDMap;

  // The mapping between the long argument IDs and the arguments for this
  // subcommand.
  private HashMap<String,Argument> longIDMap;

  // The unique identifier for the description of this subcommand.
  private int descriptionID;

  // The list of arguments associated with this subcommand.
  private LinkedList<Argument> arguments;

  // The description for this subcommand.
  private String description;

  // The name of this subcommand.
  private String name;

  // The argument parser with which this subcommand is associated.
  private SubCommandArgumentParser parser;



  /**
   * Creates a new subcommand with the provided information.  The subcommand
   * will be automatically registered with the associated parser.
   *
   * @param  parser           The argument parser with which this subcommand is
   *                          associated.
   * @param  name             The name of this subcommand.
   * @param  descriptionID    The unique ID for the description of this
   *                          subcommand.
   * @param  descriptionArgs  The arguments to use to generate the description
   *                          string for this subcommand.
   *
   * @throws  ArgumentException  If the associated argument parser already has a
   *                             subcommand with the same name.
   */
  public SubCommand(SubCommandArgumentParser parser, String name,
                    int descriptionID, Object... descriptionArgs)
         throws ArgumentException
  {
    this.parser        = parser;
    this.name          = name;
    this.descriptionID = descriptionID;

    String nameToCheck = name;
    if (parser.longArgumentsCaseSensitive())
    {
      nameToCheck = toLowerCase(name);
    }

    if (parser.hasSubCommand(nameToCheck))
    {
      int    msgID   = MSGID_ARG_SUBCOMMAND_DUPLICATE_SUBCOMMAND;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }

    parser.addSubCommand(this);
    description = getMessage(descriptionID, descriptionArgs);
    shortIDMap  = new HashMap<Character,Argument>();
    longIDMap   = new HashMap<String,Argument>();
    arguments   = new LinkedList<Argument>();
  }



  /**
   * Retrieves the name of this subcommand.
   *
   * @return  The name of this subcommand.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves the unique ID for the description of this subcommand.
   *
   * @return  The unique ID for the description of this subcommand.
   */
  public int getDescriptionID()
  {
    return descriptionID;
  }



  /**
   * Retrieves the description for this subcommand.
   *
   * @return  The description for this subcommand.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Retrieves the set of arguments for this subcommand.
   *
   * @return  The set of arguments for this subcommand.
   */
  public LinkedList<Argument> getArguments()
  {
    return arguments;
  }



  /**
   * Retrieves the subcommand argument with the specified short identifier.
   *
   * @param  shortID  The short identifier of the argument to retrieve.
   *
   * @return  The subcommand argument with the specified short identifier, or
   *          <CODE>null</CODE> if there is none.
   */
  public Argument getArgument(Character shortID)
  {
    return shortIDMap.get(shortID);
  }



  /**
   * Retrieves the subcommand argument with the specified long identifier.
   *
   * @param  longID  The long identifier of the argument to retrieve.
   *
   * @return  The subcommand argument with the specified long identifier, or
   *          <CODE>null</CODE> if there is none.
   */
  public Argument getArgument(String longID)
  {
    return longIDMap.get(longID);
  }



  /**
   * Retrieves the subcommand argument with the specified name.
   *
   * @param  name  The name of the argument to retrieve.
   *
   * @return  The subcommand argument with the specified name, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getArgumentForName(String name)
  {
    for (Argument a : arguments)
    {
      if (a.getName().equals(name))
      {
        return a;
      }
    }

    return null;
  }



  /**
   * Adds the provided argument for use with this subcommand.
   *
   * @param  argument  The argument to add for use with this subcommand.
   *
   * @throws  ArgumentException  If either the short ID or long ID for the
   *                             argument conflicts with that of another
   *                             argument already associated with this
   *                             subcommand.
   */
  public void addArgument(Argument argument)
         throws ArgumentException
  {
    String argumentName = argument.getName();
    for (Argument a : arguments)
    {
      if (argumentName.equals(a.getName()))
      {
        int    msgID   = MSGID_ARG_SUBCOMMAND_DUPLICATE_ARGUMENT_NAME;
        String message = getMessage(msgID, name, argumentName);
        throw new ArgumentException(msgID, message);
      }
    }

    if (parser.hasGlobalArgument(argumentName))
    {
      int    msgID   = MSGID_ARG_SUBCOMMAND_ARGUMENT_GLOBAL_CONFLICT;
      String message = getMessage(msgID, argumentName, name);
      throw new ArgumentException(msgID, message);
    }


    Character shortID = argument.getShortIdentifier();
    if (shortID != null)
    {
      if (shortIDMap.containsKey(shortID))
      {
        int    msgID   = MSGID_ARG_SUBCOMMAND_DUPLICATE_SHORT_ID;
        String message = getMessage(msgID, argumentName, name,
                                    String.valueOf(shortID),
                                    shortIDMap.get(shortID).getName());
        throw new ArgumentException(msgID, message);
      }

      Argument arg = parser.getGlobalArgumentForShortID(shortID);
      if (arg != null)
      {
        int    msgID   = MSGID_ARG_SUBCOMMAND_ARGUMENT_SHORT_ID_GLOBAL_CONFLICT;
        String message = getMessage(msgID, argumentName, name,
                                    String.valueOf(shortID), arg.getName());
        throw new ArgumentException(msgID, message);
      }
    }


    String longID = argument.getLongIdentifier();
    if (longID != null)
    {
      if (! parser.longArgumentsCaseSensitive())
      {
        longID = toLowerCase(longID);
      }

      if (longIDMap.containsKey(longID))
      {
        int    msgID   = MSGID_ARG_SUBCOMMAND_DUPLICATE_LONG_ID;
        String message = getMessage(msgID, argumentName, name, longID,
                                    longIDMap.get(longID).getName());
        throw new ArgumentException(msgID, message);
      }

      Argument arg = parser.getGlobalArgumentForLongID(longID);
      if (arg != null)
      {
        int    msgID   = MSGID_ARG_SUBCOMMAND_ARGUMENT_LONG_ID_GLOBAL_CONFLICT;
        String message = getMessage(msgID, argumentName, name, longID,
                                    arg.getName());
        throw new ArgumentException(msgID, message);
      }
    }


    arguments.add(argument);

    if (shortID != null)
    {
      shortIDMap.put(shortID, argument);
    }

    if (longID != null)
    {
      longIDMap.put(longID, argument);
    }
  }
}

