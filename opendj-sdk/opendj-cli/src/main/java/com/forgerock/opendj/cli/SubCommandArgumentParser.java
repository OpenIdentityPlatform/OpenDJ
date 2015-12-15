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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.DocGenerationHelper.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * This class defines a variant of the argument parser that can be used with applications that use subcommands to
 * customize their behavior and that have a different set of options per subcommand (e.g, "cvs checkout" takes different
 * options than "cvs commit"). This parser also has the ability to use global options that will always be applicable
 * regardless of the subcommand in addition to the subcommand-specific arguments. There must not be any conflicts
 * between the global options and the option for any subcommand, but it is allowed to re-use subcommand-specific options
 * for different purposes between different subcommands.
 */
public class SubCommandArgumentParser extends ArgumentParser {

    private static final String INDENT = "    ";
    private static final int COLUMN_ADJUST = OperatingSystem.isWindows() ? 1 : 0;

    /** The arguments that will be used to trigger the display of usage information for groups of sub-commands. */
    private final Map<Argument, Collection<SubCommand>> usageGroupArguments = new HashMap<>();

    /** The set of global arguments defined for this parser, referenced by short ID. */
    private final Map<Character, Argument> globalShortIDMap = new HashMap<>();
    /** The set of global arguments defined for this parser, referenced by long ID. */
    private final Map<String, Argument> globalLongIDMap = new HashMap<>();
    /** The set of global arguments defined for this parser, referenced by argument name. */
    private final Map<String, Argument> globalArgumentMap = new HashMap<>();
    /** The total set of global arguments defined for this parser. */
    private final List<Argument> globalArgumentList = new LinkedList<>();
    /** The set of subcommands defined for this parser, referenced by subcommand name. */
    private final SortedMap<String, SubCommand> subCommands = new TreeMap<>();

    /** The subcommand requested by the user as part of the command-line arguments. */
    private SubCommand subCommand;
    private SubCommandUsageHandler subCommandUsageHandler;

    /**
     * Creates a new instance of this subcommand argument parser with no arguments.
     *
     * @param mainClassName
     *            The fully-qualified name of the Java class that should be invoked to launch the program with which
     *            this argument parser is associated.
     * @param toolDescription
     *            A human-readable description for the tool, which will be included when displaying usage information.
     * @param longArgumentsCaseSensitive
     *            Indicates whether subcommand and long argument names should be treated in a case-sensitive manner.
     */
    public SubCommandArgumentParser(String mainClassName, LocalizableMessage toolDescription,
            boolean longArgumentsCaseSensitive) {
        super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    }

    /**
     * Retrieves the list of all global arguments that have been defined for this argument parser.
     *
     * @return The list of all global arguments that have been defined for this argument parser.
     */
    public List<Argument> getGlobalArgumentList() {
        return globalArgumentList;
    }

    /**
     * Indicates whether this argument parser contains a global argument with the specified name.
     *
     * @param argumentName
     *            The name for which to make the determination.
     * @return <CODE>true</CODE> if a global argument exists with the specified name, or <CODE>false</CODE> if not.
     */
    public boolean hasGlobalArgument(String argumentName) {
        return globalArgumentMap.containsKey(argumentName);
    }

    /**
     * Retrieves the global argument with the specified name.
     *
     * @param name
     *            The name of the global argument to retrieve.
     * @return The global argument with the specified name, or <CODE>null</CODE> if there is no such argument.
     */
    public Argument getGlobalArgument(String name) {
        return globalArgumentMap.get(name);
    }

    /**
     * Retrieves the set of global arguments mapped by the short identifier that may be used to reference them. Note
     * that arguments that do not have a short identifier will not be present in this list.
     *
     * @return The set of global arguments mapped by the short identifier that may be used to reference them.
     */
    public Map<Character, Argument> getGlobalArgumentsByShortID() {
        return globalShortIDMap;
    }

    /**
     * Indicates whether this argument parser has a global argument with the specified short ID.
     *
     * @param shortID
     *            The short ID character for which to make the determination.
     * @return <CODE>true</CODE> if a global argument exists with the specified short ID, or <CODE>false</CODE> if not.
     */
    public boolean hasGlobalArgumentWithShortID(Character shortID) {
        return globalShortIDMap.containsKey(shortID);
    }

    /**
     * Retrieves the global argument with the specified short identifier.
     *
     * @param shortID
     *            The short identifier for the global argument to retrieve.
     * @return The global argument with the specified short identifier, or <CODE>null</CODE> if there is no such
     *         argument.
     */
    public Argument getGlobalArgumentForShortID(Character shortID) {
        return globalShortIDMap.get(shortID);
    }

    /**
     * Retrieves the set of global arguments mapped by the long identifier that may be used to reference them. Note that
     * arguments that do not have a long identifier will not be present in this list.
     *
     * @return The set of global arguments mapped by the long identifier that may be used to reference them.
     */
    public Map<String, Argument> getGlobalArgumentsByLongID() {
        return globalLongIDMap;
    }

    /**
     * Indicates whether this argument parser has a global argument with the specified long ID.
     *
     * @param longID
     *            The long ID string for which to make the determination.
     * @return <CODE>true</CODE> if a global argument exists with the specified long ID, or <CODE>false</CODE> if not.
     */
    public boolean hasGlobalArgumentWithLongID(String longID) {
        return globalLongIDMap.containsKey(longID);
    }

    /**
     * Retrieves the global argument with the specified long identifier.
     *
     * @param longID
     *            The long identifier for the global argument to retrieve.
     * @return The global argument with the specified long identifier, or <CODE>null</CODE> if there is no such
     *         argument.
     */
    public Argument getGlobalArgumentForLongID(String longID) {
        return globalLongIDMap.get(longID);
    }

    /**
     * Retrieves the set of subcommands defined for this argument parser, referenced by subcommand name.
     *
     * @return The set of subcommands defined for this argument parser, referenced by subcommand name.
     */
    public SortedMap<String, SubCommand> getSubCommands() {
        return subCommands;
    }

    /**
     * Indicates whether this argument parser has a subcommand with the specified name.
     *
     * @param name
     *            The subcommand name for which to make the determination.
     * @return <CODE>true</CODE> if this argument parser has a subcommand with the specified name, or <CODE>false</CODE>
     *         if it does not.
     */
    public boolean hasSubCommand(String name) {
        return subCommands.containsKey(name);
    }

    /**
     * Retrieves the subcommand with the specified name.
     *
     * @param name
     *            The name of the subcommand to retrieve.
     * @return The subcommand with the specified name, or <CODE>null</CODE> if no such subcommand is defined.
     */
    public SubCommand getSubCommand(String name) {
        return subCommands.get(name);
    }

    /**
     * Retrieves the subcommand that was selected in the set of command-line arguments.
     *
     * @return The subcommand that was selected in the set of command-line arguments, or <CODE>null</CODE> if none was
     *         selected.
     */
    public SubCommand getSubCommand() {
        return subCommand;
    }

    /**
     * Adds the provided argument to the set of global arguments handled by this parser.
     *
     * @param argument
     *            The argument to be added.
     * @throws ArgumentException
     *             If the provided argument conflicts with another global or subcommand argument that has already been
     *             defined.
     */
    public void addGlobalArgument(Argument argument) throws ArgumentException {
        addGlobalArgument(argument, null);
    }

    /**
     * Adds the provided argument to the set of arguments handled by this parser and puts the argument in the LDAP
     * connection group.
     *
     * @param argument
     *            The argument to add to this sub command.
     * @throws ArgumentException
     *             If the provided argument conflicts with another global or subcommand argument that has already been
     *             defined.
     */
    @Override
    public void addLdapConnectionArgument(final Argument argument) throws ArgumentException {
        addGlobalArgument(argument, null);
    }

    /**
     * Adds the provided argument to the set of global arguments handled by this parser.
     *
     * @param argument
     *            The argument to be added.
     * @param group
     *            The argument group to which the argument belongs.
     * @throws ArgumentException
     *             If the provided argument conflicts with another global or subcommand argument that has already been
     *             defined.
     */
    public void addGlobalArgument(Argument argument, ArgumentGroup group) throws ArgumentException {
        String argumentName = argument.getName();
        if (globalArgumentMap.containsKey(argumentName)) {
            throw new ArgumentException(ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_NAME.get(argumentName));
        }
        for (SubCommand s : subCommands.values()) {
            if (s.getArgumentForName(argumentName) != null) {
                throw new ArgumentException(ERR_SUBCMDPARSER_GLOBAL_ARG_NAME_SUBCMD_CONFLICT.get(
                        argumentName, s.getName()));
            }
        }

        Character shortID = argument.getShortIdentifier();
        if (shortID != null) {
            if (globalShortIDMap.containsKey(shortID)) {
                String name = globalShortIDMap.get(shortID).getName();

                throw new ArgumentException(ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_SHORT_ID.get(
                        shortID, argumentName, name));
            }

            for (SubCommand s : subCommands.values()) {
                if (s.getArgument(shortID) != null) {
                    String cmdName = s.getName();
                    String name = s.getArgument(shortID).getName();

                    throw new ArgumentException(ERR_SUBCMDPARSER_GLOBAL_ARG_SHORT_ID_CONFLICT.get(
                            shortID, argumentName, name, cmdName));
                }
            }
        }

        String longID = argument.getLongIdentifier();
        if (longID != null) {
            if (!longArgumentsCaseSensitive()) {
                longID = toLowerCase(longID);
            }

            if (globalLongIDMap.containsKey(longID)) {
                String name = globalLongIDMap.get(longID).getName();

                throw new ArgumentException(ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_LONG_ID.get(
                        argument.getLongIdentifier(), argumentName, name));
            }

            for (SubCommand s : subCommands.values()) {
                if (s.getArgument(longID) != null) {
                    String cmdName = s.getName();
                    String name = s.getArgument(longID).getName();

                    throw new ArgumentException(ERR_SUBCMDPARSER_GLOBAL_ARG_LONG_ID_CONFLICT.get(
                            argument.getLongIdentifier(), argumentName, name, cmdName));
                }
            }
        }

        if (shortID != null) {
            globalShortIDMap.put(shortID, argument);
        }

        if (longID != null) {
            globalLongIDMap.put(longID, argument);
        }

        globalArgumentList.add(argument);

        if (group == null) {
            group = getStandardGroup(argument);
        }
        group.addArgument(argument);
        argumentGroups.add(group);
    }

    /**
     * Removes the provided argument from the set of global arguments handled by this parser.
     *
     * @param argument
     *            The argument to be removed.
     */
    protected void removeGlobalArgument(Argument argument) {
        String argumentName = argument.getName();
        globalArgumentMap.remove(argumentName);

        Character shortID = argument.getShortIdentifier();
        if (shortID != null) {
            globalShortIDMap.remove(shortID);
        }

        String longID = argument.getLongIdentifier();
        if (longID != null) {
            if (!longArgumentsCaseSensitive()) {
                longID = toLowerCase(longID);
            }

            globalLongIDMap.remove(longID);
        }

        globalArgumentList.remove(argument);
    }

    /**
     * Sets the provided argument as one which will automatically trigger the output of full usage information if it is
     * provided on the command line and no further argument validation will be performed.
     * <p>
     * If sub-command groups are defined using the {@link #setUsageGroupArgument(Argument, Collection)} method, then
     * this usage argument, when specified, will result in usage information being displayed which does not include
     * information on sub-commands.
     * <p>
     * Note that the caller will still need to add this argument to the parser with the
     * {@link #addGlobalArgument(Argument)} method, and the argument should not be required and should not take a value.
     * Also, the caller will still need to check for the presence of the usage argument after calling
     * {@link #parseArguments(String[])} to know that no further processing will be required.
     *
     * @param argument
     *            The argument whose presence should automatically trigger the display of full usage information.
     * @param outputStream
     *            The output stream to which the usage information should be written.
     */
    @Override
    public void setUsageArgument(Argument argument, OutputStream outputStream) {
        super.setUsageArgument(argument, outputStream);
        usageGroupArguments.put(argument, Collections.<SubCommand>emptySet());
    }

    /**
     * Sets the provided argument as one which will automatically trigger the output of partial usage information if it
     * is provided on the command line and no further argument validation will be performed.
     * <p>
     * Partial usage information will include a usage synopsis, a summary of each of the sub-commands listed in the
     * provided sub-commands collection, and a summary of the global options.
     * <p>
     * Note that the caller will still need to add this argument to the parser with the
     * {@link #addGlobalArgument(Argument)} method, and the argument should not be required and should not take a value.
     * Also, the caller will still need to check for the presence of the usage argument after calling
     * {@link #parseArguments(String[])} to know that no further processing will be required.
     *
     * @param argument
     *            The argument whose presence should automatically trigger the display of partial usage information.
     * @param subCommands
     *            The list of sub-commands which should have their usage displayed.
     */
    public void setUsageGroupArgument(Argument argument, Collection<SubCommand> subCommands) {
        usageGroupArguments.put(argument, subCommands);
    }

    /**
     * Sets the sub-command usage handler which will be used to display the usage information.
     *
     * @param subCommandUsageHandler the sub-command usage handler
     */
    public void setUsageHandler(SubCommandUsageHandler subCommandUsageHandler) {
        this.subCommandUsageHandler = subCommandUsageHandler;
    }

    /**
     * Parses the provided set of arguments and updates the information associated with this parser accordingly. Default
     * values for unspecified arguments may be read from the specified properties if any are provided.
     *
     * @param rawArguments
     *            The set of raw arguments to parse.
     * @param argumentProperties
     *            A set of properties that may be used to provide default values for arguments not included in the given
     *            raw arguments.
     * @throws ArgumentException
     *             If a problem was encountered while parsing the provided arguments.
     */
    @Override
    public void parseArguments(String[] rawArguments, Properties argumentProperties) throws ArgumentException {
        setRawArguments(rawArguments);
        this.subCommand = null;
        final ArrayList<String> trailingArguments = getTrailingArguments();
        trailingArguments.clear();
        setUsageOrVersionDisplayed(false);

        boolean inTrailingArgs = false;

        int numArguments = rawArguments.length;
        for (int i = 0; i < numArguments; i++) {
            final String arg = rawArguments[i];

            if (inTrailingArgs) {
                trailingArguments.add(arg);

                if (subCommand == null) {
                    throw new ArgumentException(ERR_ARG_SUBCOMMAND_INVALID.get());
                }

                if (subCommand.getMaxTrailingArguments() > 0
                        && trailingArguments.size() > subCommand.getMaxTrailingArguments()) {
                    throw new ArgumentException(ERR_ARGPARSER_TOO_MANY_TRAILING_ARGS.get(
                            subCommand.getMaxTrailingArguments()));
                }

                continue;
            }

            if (arg.equals("--")) {
                inTrailingArgs = true;
            } else if (arg.startsWith("--")) {
                // This indicates that we are using the long name to reference the
                // argument. It may be in any of the following forms:
                // --name
                // --name value
                // --name=value

                String argName = arg.substring(2);
                String argValue = null;
                int equalPos = argName.indexOf('=');
                if (equalPos < 0) {
                    // This is fine. The value is not part of the argument name token.
                } else if (equalPos == 0) {
                    // The argument starts with "--=", which is not acceptable.
                    throw new ArgumentException(ERR_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME.get(arg));
                } else {
                    // The argument is in the form --name=value, so parse them both out.
                    argValue = argName.substring(equalPos + 1);
                    argName = argName.substring(0, equalPos);
                }

                // If we're not case-sensitive, then convert the name to lowercase.
                String origArgName = argName;
                if (!longArgumentsCaseSensitive()) {
                    argName = toLowerCase(argName);
                }

                // See if the specified name references a global argument. If not, then
                // see if it references a subcommand argument.
                Argument a = globalLongIDMap.get(argName);
                if (a == null) {
                    if (subCommand != null) {
                        a = subCommand.getArgument(argName);
                    }
                    if (a == null) {
                        if (OPTION_LONG_HELP.equals(argName)) {
                            // "--help" will always be interpreted as requesting usage
                            // information.
                            writeToUsageOutputStream(getUsage());
                            return;
                        } else if (OPTION_LONG_PRODUCT_VERSION.equals(argName)) {
                            // "--version" will always be interpreted as requesting usage information.
                            printVersion();
                            return;
                        } else if (subCommand != null) {
                            // There is no such global argument.
                            throw new ArgumentException(
                                    ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID.get(origArgName));
                        } else {
                            // There is no such global or subcommand argument.
                            throw new ArgumentException(ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_LONG_ID.get(origArgName));
                        }
                    }
                }

                a.setPresent(true);

                // If this is a usage argument, then immediately stop and print
                // usage information.
                if (usageGroupArguments.containsKey(a)) {
                    getUsage(a);
                    return;
                }

                // See if the argument takes a value. If so, then make sure one was
                // provided. If not, then make sure none was provided.
                if (a.needsValue()) {
                    if (argValue == null) {
                        if ((i + 1) == numArguments) {
                            throw new ArgumentException(
                                    ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get(argName));
                        }

                        argValue = rawArguments[++i];
                    }

                    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
                    if (!a.valueIsAcceptable(argValue, invalidReason)) {
                        throw new ArgumentException(ERR_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.get(
                                argValue, argName, invalidReason));
                    }

                    // If the argument already has a value, then make sure it is
                    // acceptable to have more than one.
                    if (a.hasValue() && !a.isMultiValued()) {
                        throw new ArgumentException(ERR_SUBCMDPARSER_NOT_MULTIVALUED_FOR_LONG_ID.get(origArgName));
                    }

                    a.addValue(argValue);
                } else if (argValue != null) {
                    throw new ArgumentException(
                            ERR_SUBCMDPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE.get(origArgName));
                }
            } else if (arg.equals("-")) {
                throw new ArgumentException(ERR_SUBCMDPARSER_INVALID_DASH_AS_ARGUMENT.get());
            } else if (arg.startsWith("-")) {
                // This indicates that we are using the 1-character name to reference
                // the argument. It may be in any of the following forms:
                // -n
                // -nvalue
                // -n value
                char argCharacter = arg.charAt(1);
                String argValue;
                if (arg.length() > 2) {
                    argValue = arg.substring(2);
                } else {
                    argValue = null;
                }

                // Get the argument with the specified short ID. It may be either a
                // global argument or a subcommand-specific argument.
                Argument a = globalShortIDMap.get(argCharacter);
                if (a == null) {
                    if (subCommand == null) {
                        if (argCharacter == '?') {
                            // "-?" will always be interpreted as requesting usage.
                            writeToUsageOutputStream(getUsage());
                            if (getUsageArgument() != null) {
                                getUsageArgument().setPresent(true);
                            }
                            return;
                        } else if (argCharacter == OPTION_SHORT_PRODUCT_VERSION) {
                            // "-V" will always be interpreted as requesting
                            // version information except if it's already defined.
                            if (dashVAccepted()) {
                                printVersion();
                                return;
                            } else {
                                // -V is defined in another subcommand, so we cannot
                                // accept it as the version information argument
                                throw new ArgumentException(
                                        ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.get(argCharacter));
                            }
                        } else {
                            // There is no such argument registered.
                            throw new ArgumentException(
                                    ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.get(argCharacter));
                        }
                    } else {
                        a = subCommand.getArgument(argCharacter);
                        if (a == null) {
                            if (argCharacter == '?') {
                                // "-?" will always be interpreted as requesting usage.
                                writeToUsageOutputStream(getUsage());
                                return;
                            } else if (argCharacter == OPTION_SHORT_PRODUCT_VERSION) {
                                if (dashVAccepted()) {
                                    printVersion();
                                    return;
                                }
                            } else {
                                // There is no such argument registered.
                                throw new ArgumentException(
                                        ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.get(argCharacter));
                            }
                        }
                    }
                }

                a.setPresent(true);

                // If this is the usage argument, then immediately stop and print
                // usage information.
                if (usageGroupArguments.containsKey(a)) {
                    getUsage(a);
                    return;
                }

                // See if the argument takes a value. If so, then make sure one was
                // provided. If not, then make sure none was provided.
                if (a.needsValue()) {
                    if (argValue == null) {
                        if ((i + 1) == numArguments) {
                            throw new ArgumentException(
                                    ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.get(argCharacter));
                        }

                        argValue = rawArguments[++i];
                    }

                    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
                    if (!a.valueIsAcceptable(argValue, invalidReason)) {
                        throw new ArgumentException(ERR_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID.get(argValue,
                                argCharacter, invalidReason));
                    }

                    // If the argument already has a value, then make sure it is
                    // acceptable to have more than one.
                    if (a.hasValue() && !a.isMultiValued()) {
                        throw new ArgumentException(ERR_SUBCMDPARSER_NOT_MULTIVALUED_FOR_SHORT_ID.get(argCharacter));
                    }

                    a.addValue(argValue);
                } else if (argValue != null) {
                    // If we've gotten here, then it means that we're in a scenario like
                    // "-abc" where "a" is a valid argument that doesn't take a value.
                    // However, this could still be valid if all remaining characters in
                    // the value are also valid argument characters that don't take
                    // values.
                    int valueLength = argValue.length();
                    for (int j = 0; j < valueLength; j++) {
                        char c = argValue.charAt(j);
                        Argument b = globalShortIDMap.get(c);
                        if (b == null) {
                            if (subCommand == null) {
                                throw new ArgumentException(
                                        ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.get(argCharacter));
                            }
                            b = subCommand.getArgument(c);
                            if (b == null) {
                                throw new ArgumentException(
                                        ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.get(argCharacter));
                            }
                        }

                        if (b.needsValue()) {
                            // This means we're in a scenario like "-abc" where b is a
                            // valid argument that takes a value. We don't support that.
                            throw new ArgumentException(ERR_SUBCMDPARSER_CANT_MIX_ARGS_WITH_VALUES.get(
                                    argCharacter, argValue, c));
                        }
                        b.setPresent(true);

                        // If this is the usage argument, then immediately stop and
                        // print usage information.
                        if (usageGroupArguments.containsKey(b)) {
                            getUsage(b);
                            return;
                        }
                    }
                }
            } else if (subCommand != null) {
                // It's not a short or long identifier and the sub-command has
                // already been specified, so it must be the first trailing argument.
                if (subCommand.allowsTrailingArguments()) {
                    trailingArguments.add(arg);
                    inTrailingArgs = true;
                } else {
                    // Trailing arguments are not allowed for this sub-command.
                    throw new ArgumentException(ERR_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT.get(arg));
                }
            } else {
                // It must be the sub-command.
                String nameToCheck = arg;
                if (!longArgumentsCaseSensitive()) {
                    nameToCheck = toLowerCase(arg);
                }

                SubCommand sc = subCommands.get(nameToCheck);
                if (sc == null) {
                    throw new ArgumentException(ERR_SUBCMDPARSER_INVALID_ARGUMENT.get(arg));
                }
                subCommand = sc;
            }
        }

        // If we have a sub-command and it allows trailing arguments and
        // there is a minimum number, then make sure at least that many
        // were provided.
        if (subCommand != null) {
            int minTrailingArguments = subCommand.getMinTrailingArguments();
            if (subCommand.allowsTrailingArguments()
                    && minTrailingArguments > 0
                    && trailingArguments.size() < minTrailingArguments) {
                throw new ArgumentException(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(minTrailingArguments));
            }
        }

        // If we don't have the argumentProperties, try to load a properties file.
        if (argumentProperties == null) {
            argumentProperties = checkExternalProperties();
        }

        // Iterate through all the global arguments
        normalizeArguments(argumentProperties, globalArgumentList);

        // Iterate through all the subcommand-specific arguments
        if (subCommand != null) {
            normalizeArguments(argumentProperties, subCommand.getArguments());
        }
    }

    private boolean dashVAccepted() {
        if (globalShortIDMap.containsKey(OPTION_SHORT_PRODUCT_VERSION)) {
            return false;
        }
        for (SubCommand subCmd : subCommands.values()) {
            if (subCmd.getArgument(OPTION_SHORT_PRODUCT_VERSION) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Appends usage information for the specified subcommand to the provided buffer.
     *
     * @param buffer
     *            The buffer to which the usage information should be appended.
     * @param subCommand
     *            The subcommand for which to display the usage information.
     */
    public void getSubCommandUsage(StringBuilder buffer, SubCommand subCommand) {
        buffer.append(getLocalizableScriptName());
        buffer.append(" ");
        buffer.append(subCommand.getName());
        buffer.append(" ").append(INFO_SUBCMDPARSER_OPTIONS.get());
        if (subCommand.allowsTrailingArguments()) {
            buffer.append(' ');
            buffer.append(subCommand.getTrailingArgumentsDisplayName());
        }
        buffer.append(EOL);
        wrap(buffer, subCommand.getDescription());
        buffer.append(EOL);

        if (!globalArgumentList.isEmpty()) {
            buffer.append(EOL);
            buffer.append(INFO_GLOBAL_OPTIONS.get());
            buffer.append(EOL);
            buffer.append("    ");
            buffer.append(INFO_GLOBAL_OPTIONS_REFERENCE.get(getScriptNameOrJava()));
            buffer.append(EOL);
        }

        if (!subCommand.getArguments().isEmpty()) {
            buffer.append(EOL);
            buffer.append(INFO_SUBCMD_OPTIONS.get());
            buffer.append(EOL);
        }

        for (Argument a : subCommand.getArguments()) {
            // If this argument is hidden, then skip it.
            if (a.isHidden()) {
                continue;
            }

            printLineForShortLongArgument(a, buffer);

            indentAndWrap(buffer, INDENT, a.getDescription());
            if (a.needsValue() && a.getDefaultValue() != null && a.getDefaultValue().length() > 0) {
                indentAndWrap(buffer, INDENT, INFO_ARGPARSER_USAGE_DEFAULT_VALUE.get(a.getDefaultValue()));
            }
        }
    }

    /**
     * Retrieves a string containing usage information based on the defined arguments.
     *
     * @return A string containing usage information based on the defined arguments.
     */
    @Override
    public String getUsage() {
        setUsageOrVersionDisplayed(true);

        final StringBuilder buffer = new StringBuilder();
        if (subCommand == null) {
            if (System.getProperty("org.forgerock.opendj.gendoc") != null) {
                generateReferenceDoc(buffer, subCommands.values());
            } else if (usageGroupArguments.size() > 1) {
                // We have sub-command groups, so don't display any
                // sub-commands by default.
                getFullUsage(Collections.<SubCommand> emptySet(), true, buffer);
            } else {
                // No grouping, so display all sub-commands.
                getFullUsage(subCommands.values(), true, buffer);
            }
        } else {
            getSubCommandUsage(buffer, subCommand);
        }
        return buffer.toString();
    }

    /**
     * Adds the provided subcommand to this argument parser. This is only intended for use by the
     * <CODE>SubCommand</CODE> constructor and does not do any validation of its own to ensure that there are no
     * conflicts with the subcommand or any of its arguments.
     *
     * @param subCommand
     *            The subcommand to add to this argument parser.
     */
    void addSubCommand(SubCommand subCommand) {
        subCommands.put(toLowerCase(subCommand.getName()), subCommand);
    }

    /** Get usage for a specific usage argument. */
    private void getUsage(Argument a) {
        setUsageOrVersionDisplayed(true);

        final StringBuilder buffer = new StringBuilder();
        final boolean isUsageArgument = isUsageArgument(a);
        if (isUsageArgument && subCommand != null) {
            getSubCommandUsage(buffer, subCommand);
        } else if (isUsageArgument && usageGroupArguments.size() <= 1) {
            // No groups - so display all sub-commands.
            getFullUsage(subCommands.values(), true, buffer);
        } else if (isUsageArgument) {
            // Using groups - so display all sub-commands group help.
            getFullUsage(Collections.<SubCommand> emptySet(), true, buffer);
        } else {
            // Requested help on specific group - don't display global options.
            getFullUsage(usageGroupArguments.get(a), false, buffer);
        }
        writeToUsageOutputStream(buffer);
    }

    /** Appends complete usage information for the specified set of sub-commands. */
    private void getFullUsage(Collection<SubCommand> c, boolean showGlobalOptions, StringBuilder buffer) {
        final LocalizableMessage toolDescription = getToolDescription();
        if (toolDescription != null && toolDescription.length() > 0) {
            buffer.append(wrapText(toolDescription, MAX_LINE_WIDTH - 1));
            buffer.append(EOL).append(EOL);
        }

        buffer.append(INFO_ARGPARSER_USAGE.get());
        buffer.append("  ");
        buffer.append(getScriptNameOrJava());

        if (subCommands.isEmpty()) {
            buffer.append(" ").append(INFO_SUBCMDPARSER_OPTIONS.get());
        } else {
            buffer.append(" ").append(INFO_SUBCMDPARSER_SUBCMD_AND_OPTIONS.get());
        }

        if (!subCommands.isEmpty()) {
            buffer.append(EOL);
            buffer.append(EOL);
            if (c.isEmpty()) {
                buffer.append(INFO_SUBCMDPARSER_SUBCMD_HELP_HEADING.get());
            } else {
                buffer.append(INFO_SUBCMDPARSER_SUBCMD_HEADING.get());
            }
            buffer.append(EOL);
        }

        if (c.isEmpty()) {
            // Display usage arguments (except the default one).
            for (Argument a : globalArgumentList) {
                if (a.isHidden()) {
                    continue;
                }

                if (usageGroupArguments.containsKey(a) && !isUsageArgument(a)) {
                    printArgumentUsage(a, buffer);
                }
            }
        } else {
            boolean isFirst = true;
            for (SubCommand sc : c) {
                if (sc.isHidden()) {
                    continue;
                }
                if (isFirst) {
                    buffer.append(EOL);
                }
                buffer.append(sc.getName());
                buffer.append(EOL);
                indentAndWrap(buffer, INDENT, sc.getDescription());
                buffer.append(EOL);
                isFirst = false;
            }
        }

        buffer.append(EOL);

        if (showGlobalOptions) {
            if (subCommands.isEmpty()) {
                buffer.append(INFO_SUBCMDPARSER_WHERE_OPTIONS_INCLUDE.get());
            } else {
                buffer.append(INFO_SUBCMDPARSER_GLOBAL_HEADING.get());
            }
            buffer.append(EOL).append(EOL);

            boolean printGroupHeaders = printUsageGroupHeaders();

            // Display non-usage arguments.
            for (ArgumentGroup argGroup : argumentGroups) {
                if (argGroup.containsArguments() && printGroupHeaders) {
                    // Print the groups description if any
                    LocalizableMessage groupDesc = argGroup.getDescription();
                    if (groupDesc != null && !LocalizableMessage.EMPTY.equals(groupDesc)) {
                        buffer.append(EOL);
                        buffer.append(wrapText(groupDesc.toString(), MAX_LINE_WIDTH - 1));
                        buffer.append(EOL).append(EOL);
                    }
                }

                for (Argument a : argGroup.getArguments()) {
                    if (a.isHidden()) {
                        continue;
                    }

                    if (!usageGroupArguments.containsKey(a)) {
                        printArgumentUsage(a, buffer);
                    }
                }
            }

            // Finally print default usage argument.
            final Argument usageArgument = getUsageArgument();
            if (usageArgument != null) {
                printArgumentUsage(usageArgument, buffer);
            } else {
                buffer.append("-?");
            }
            buffer.append(EOL);
        }
    }

    /**
     * Appends argument usage information to the provided buffer.
     *
     * @param a
     *            The argument to handle.
     * @param buffer
     *            The buffer to which the usage information should be appended.
     */
    private void printArgumentUsage(Argument a, StringBuilder buffer) {
        String value;
        if (a.needsValue()) {
            LocalizableMessage pHolder = a.getValuePlaceholder();
            if (pHolder != null) {
                value = " " + pHolder;
            } else {
                value = " {value}";
            }
        } else {
            value = "";
        }

        final Argument usageArgument = getUsageArgument();
        Character shortIDChar = a.getShortIdentifier();
        if (shortIDChar != null) {
            if (a.equals(usageArgument)) {
                buffer.append("-?, ");
            }
            buffer.append("-");
            buffer.append(shortIDChar);

            String longIDString = a.getLongIdentifier();
            if (longIDString != null) {
                buffer.append(", --");
                buffer.append(longIDString);
            }
            buffer.append(value);
        } else {
            String longIDString = a.getLongIdentifier();
            if (longIDString != null) {
                if (a.equals(usageArgument)) {
                    buffer.append("-?, ");
                }
                buffer.append("--");
                buffer.append(longIDString);
                buffer.append(value);
            }
        }

        buffer.append(EOL);

        indentAndWrap(buffer, INDENT, a.getDescription());
        if (a.needsValue() && a.getDefaultValue() != null && a.getDefaultValue().length() > 0) {
            indentAndWrap(buffer, INDENT, INFO_ARGPARSER_USAGE_DEFAULT_VALUE.get(a.getDefaultValue()));
        }
    }

    /** Wraps long lines without indentation. */
    private void wrap(StringBuilder buffer, LocalizableMessage text) {
        indentAndWrap(buffer, "", text);
    }

    /**
     * Write one or more lines with the description of the argument. We will indent the description five characters and
     * try our best to wrap at or before column 79 so it will be friendly to 80-column displays.
     * <p>
     * FIXME consider merging with com.forgerock.opendj.cli.Utils#wrapText(String, int, int)
     */
    private void indentAndWrap(StringBuilder buffer, String indent, LocalizableMessage text) {
        int actualSize = MAX_LINE_WIDTH - indent.length() - COLUMN_ADJUST;
        indentAndWrap(indent, buffer, actualSize, text);
    }

    static void indentAndWrap(String indent, StringBuilder buffer, int actualSize, LocalizableMessage text) {
        if (text.length() <= actualSize) {
            buffer.append(indent);
            buffer.append(text);
            buffer.append(EOL);
        } else {
            String s = text.toString();
            while (s.length() > actualSize) {
                int spacePos = s.lastIndexOf(' ', actualSize);
                if (spacePos == -1) {
                    // There are no spaces in the first actualSize -1 columns.
                    // See if there is one after that point.
                    // If so, then break there. If not, then don't break at all.
                    spacePos = s.indexOf(' ');
                }
                if (spacePos == -1) {
                    buffer.append(indent).append(s).append(EOL);
                    return;
                }
                buffer.append(indent);
                buffer.append(s.substring(0, spacePos).trim());
                s = s.substring(spacePos + 1).trim();
                buffer.append(EOL);
            }

            if (s.length() > 0) {
                buffer.append(indent).append(s).append(EOL);
            }
        }
    }

    /**
     * Appends a generated DocBook XML RefEntry element for this command to the StringBuilder.
     *
     * @param builder       Append the RefEntry element to this.
     * @param subCommands   SubCommands containing reference information.
     */
    private void generateReferenceDoc(final StringBuilder builder, Collection<SubCommand> subCommands) {
        toRefEntry(builder, subCommands);
    }

    @Override
    String getSynopsisArgs() {
        if (subCommands.isEmpty()) {
            return INFO_SUBCMDPARSER_OPTIONS.get().toString();
        } else {
            return INFO_SUBCMDPARSER_SUBCMD_AND_OPTIONS.get().toString();
        }
    }

    /**
     * Appends one or more generated DocBook XML RefEntry elements (man pages) to the StringBuilder.
     * <br>
     * If the result contains more than one RefEntry,
     * then the RefEntry elements are separated with a marker:
     * {@code @@@scriptName + "-" + subCommand.getName() + @@@}.
     *
     * @param builder       Append the RefEntry element to this.
     * @param subCommands   Collection of subcommands for this tool.
     */
    void toRefEntry(StringBuilder builder, Collection<SubCommand> subCommands) {
        final String scriptName = getScriptName();
        if (scriptName == null) {
            throw new RuntimeException("The script name should have been set via the environment property '"
                    + PROPERTY_SCRIPT_NAME + "'.");
        }

        // Model for a FreeMarker template.
        Map<String, Object> map = new HashMap<>();
        map.put("locale", Locale.getDefault().getLanguage());
        map.put("year", new SimpleDateFormat("yyyy").format(new Date()));
        map.put("name", scriptName);
        map.put("shortDesc", getShortToolDescription());
        map.put("descTitle", REF_TITLE_DESCRIPTION.get());
        map.put("args", getSynopsisArgs());
        map.put("description", eolToNewPara(getToolDescription()));
        map.put("info", getDocToolDescriptionSupplement());
        if (!globalArgumentList.isEmpty()) {
            map.put("optionSection", getOptionsRefSect1(scriptName));
        }
        map.put("subcommands", toRefSect1(scriptName, subCommands));
        map.put("trailingSectionString", System.getProperty("org.forgerock.opendj.gendoc.trailing"));
        applyTemplate(builder, "refEntry.ftl", map);

        // For dsconfig, generate one page per subcommand.
        if (scriptName.equals("dsconfig")) {
            appendSubCommandPages(builder, scriptName, subCommands);
        }
    }

    /**
     * Returns a generated DocBook XML RefSect1 element for all subcommands.
     * @param scriptName    The name of this script.
     * @param subCommands   The SubCommands containing the reference information.
     * @return              The RefSect1 element as a String.
     */
    private String toRefSect1(String scriptName, Collection<SubCommand> subCommands) {
        if (subCommands.isEmpty()) {
            return "";
        }

        Map<String, Object> map = new HashMap<>();
        map.put("name", scriptName);
        map.put("title", REF_TITLE_SUBCOMMANDS.get());
        map.put("info", getDocSubcommandsDescriptionSupplement());
        map.put("intro", REF_INTRO_SUBCOMMANDS.get(scriptName));
        if (scriptName.equals("dsconfig")) {
            // Break dsconfig into multiple pages, so use only the list here.
            map.put("isItemizedList", true);
        }
        List<String> scUsageList = new ArrayList<>();
        for (SubCommand subCommand : subCommands) {
            if (subCommand.isHidden()) {
                continue;
            }
            if (scriptName.equals("dsconfig")) {
                scUsageList.add(getSubCommandListItem(scriptName, subCommand));
            } else {
                scUsageList.add(toRefSect2(scriptName, subCommand));
            }
        }
        map.put("subcommands", scUsageList);

        StringBuilder sb = new StringBuilder();
        applyTemplate(sb, "refSect1.ftl", map);
        return sb.toString();
    }

    /**
     * Returns a DocBook XML ListItem element linking to the subcommand page.
     * @param scriptName    The name of this script.
     * @param subCommand    The SubCommand to reference.
     * @return A DocBook XML ListItem element linking to the subcommand page.
     */
    private String getSubCommandListItem(String scriptName, SubCommand subCommand) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", scriptName + "-" + subCommand.getName());
        map.put("name", scriptName + " " + subCommand.getName());
        map.put("description", eolToNewPara(subCommand.getDescription()));
        StringBuilder sb = new StringBuilder();
        applyTemplate(sb, "dscfgListItem.ftl", map);
        return sb.toString();
    }

    /**
     * Returns a generated DocBook XML RefSect2 element for a single subcommand to the StringBuilder.
     *
     * @param scriptName    The name of this script.
     * @param subCommand    The SubCommand containing reference information.
     * @return    The RefSect2 element as a String.
     */
    private String toRefSect2(String scriptName, SubCommand subCommand) {
        // Model for a FreeMarker template.
        Map<String, Object> map = new HashMap<>();
        map.put("id", scriptName + "-" + subCommand.getName());
        final String name = scriptName + " " + subCommand.getName();
        map.put("name", name);
        map.put("description", eolToNewPara(subCommand.getDescription()));
        map.put("optionsTitle", REF_TITLE_OPTIONS.get());
        map.put("optionsIntro", REF_INTRO_OPTIONS.get(name));

        // If there is a supplement to the description for this subcommand,
        // then it is already DocBook XML, so use it as is.
        map.put("info", subCommand.getDocDescriptionSupplement());
        setSubCommandOptionsInfo(map, subCommand);

        StringBuilder sb = new StringBuilder();
        applyTemplate(sb, "refSect2.ftl", map);
        return sb.toString();
    }

    /**
     * Sets information for the subcommand options in the map.
     * <br>
     * The map is expected to be used in a FreeMarker template to generate docs.
     *
     * @param map           The map in which to set the information.
     * @param subCommand    The subcommand containing the information.
     */
    private void setSubCommandOptionsInfo(Map<String, Object> map, SubCommand subCommand) {
        if (!subCommand.getArguments().isEmpty()) {
            List<Map<String, Object>> options = new LinkedList<>();
            String nameOption = null;
            for (Argument a : subCommand.getArguments()) {
                if (a.isHidden()) {
                    continue;
                }
                // Return a generic FQDN for localhost as the default hostname
                // in reference documentation.
                if (isHostNameArgument(a)) {
                    a.setDefaultValue("localhost.localdomain");
                }

                Map<String, Object> option = new HashMap<>();
                String optionSynopsis = getOptionSynopsis(a);
                option.put("synopsis", optionSynopsis);
                option.put("description", eolToNewPara(a.getDescription()));
                Map<String, Object> info = new HashMap<>();
                if (subCommandUsageHandler != null) {
                    if (!doesHandleProperties(a)) {
                        nameOption = "<option>" + optionSynopsis + "</option>";
                    }

                    // Let this build its own arbitrarily formatted additional info.
                    info.put("usage", subCommandUsageHandler.getArgumentAdditionalInfo(subCommand, a, nameOption));
                } else {
                    String defaultValue = a.getDefaultValue();
                    info.put("default", defaultValue != null ? REF_DEFAULT.get(defaultValue) : null);

                    // If there is a supplement to the description for this argument,
                    // then it is already DocBook XML, so use it as is.
                    info.put("doc", a.getDocDescriptionSupplement());
                }
                option.put("info", info);
                options.add(option);
            }
            map.put("options", options);
        }

        if (subCommandUsageHandler != null) {
            map.put("propertiesInfo", subCommandUsageHandler.getProperties(subCommand));
        }
    }

    /**
     * Appends a generated DocBook XML RefEntry element for each subcommand to the StringBuilder.
     * <br>
     * The RefEntry elements are separated with a marker:
     * {@code @@@scriptName + "-" + subCommand.getName() + @@@}.
     *
     * @param builder       Append the RefEntry elements to this.
     * @param scriptName    The name of the tool with subcommands.
     * @param subCommands   SubCommands containing reference information.
     */
    private void appendSubCommandPages(StringBuilder builder, String scriptName, Collection<SubCommand> subCommands) {
        for (SubCommand subCommand : subCommands) {
            if (subCommand.isHidden()) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("marker", "@@@" + scriptName + "-" + subCommand.getName() + "@@@");
            map.put("locale", Locale.getDefault().getLanguage());
            map.put("year", new SimpleDateFormat("yyyy").format(new Date()));
            map.put("id", scriptName + "-" + subCommand.getName());
            map.put("name", scriptName + " " + subCommand.getName());
            map.put("purpose", eolToNewPara(subCommand.getDescription()));
            map.put("args", INFO_SUBCMDPARSER_OPTIONS.get());
            map.put("descTitle", REF_TITLE_DESCRIPTION.get());
            map.put("description", eolToNewPara(subCommand.getDescription()));
            map.put("info", subCommand.getDocDescriptionSupplement());
            map.put("optionsTitle", REF_TITLE_OPTIONS.get());
            map.put("optionsIntro", REF_INTRO_OPTIONS.get(scriptName + " " + subCommand.getName()));
            setSubCommandOptionsInfo(map, subCommand);
            applyTemplate(builder, "dscfgSubcommand.ftl", map);
        }
        appendSubCommandReference(builder, scriptName, subCommands);
    }

    /**
     * Appends a generated DocBook XML Reference element XIncluding subcommands.
     *
     * @param builder       Append the Reference element to this.
     * @param scriptName    The name of the tool with subcommands.
     * @param subCommands   SubCommands containing reference information.
     */
    private void appendSubCommandReference(StringBuilder builder,
                                           String scriptName,
                                           Collection<SubCommand> subCommands) {
        Map<String, Object> map = new HashMap<>();
        map.put("marker", "@@@" + scriptName + "-subcommands-ref" + "@@@");
        map.put("name", scriptName);
        map.put("locale", Locale.getDefault().getLanguage());
        map.put("title", REF_PART_TITLE_SUBCOMMANDS.get(scriptName));
        map.put("partintro", REF_PART_INTRO_SUBCOMMANDS.get(scriptName));
        List<Map<String, Object>> commands = new LinkedList<>();
        for (SubCommand subCommand : subCommands) {
            Map<String, Object> scMap = new HashMap<>();
            scMap.put("id", scriptName + "-" + subCommand.getName());
            commands.add(scMap);
        }
        map.put("subcommands", commands);
        applyTemplate(builder, "dscfgReference.ftl", map);
    }
}
