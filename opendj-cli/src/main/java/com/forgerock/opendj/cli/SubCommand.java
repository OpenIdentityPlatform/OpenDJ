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
 *      Portions Copyright 2014-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class defines a data structure for holding information about a subcommand that may be used with the subcommand
 * argument parser. The subcommand has a name, a description, and a set of arguments.
 */
public class SubCommand implements DocDescriptionSupplement {
    /** Indicates whether this subCommand should be hidden in the usage information. */
    private boolean isHidden;

    /** The mapping between the short argument IDs and the arguments for this subcommand. */
    private final HashMap<Character, Argument> shortIDMap = new HashMap<>();
    /** The mapping between the long argument IDs and the arguments for this subcommand. */
    private final HashMap<String, Argument> longIDMap = new HashMap<>();
    /** The list of arguments associated with this subcommand. */
    private final LinkedList<Argument> arguments = new LinkedList<>();

    /** The description for this subcommand. */
    private LocalizableMessage description;
    /** The name of this subcommand. */
    private String name;

    /** The argument parser with which this subcommand is associated. */
    private SubCommandArgumentParser parser;

    /**
     * Indicates whether this parser will allow additional unnamed
     * arguments at the end of the list.
     */
    private boolean allowsTrailingArguments;

    /** The maximum number of unnamed trailing arguments that may be provided. */
    private int maxTrailingArguments;
    /** The minimum number of unnamed trailing arguments that may be provided. */
    private int minTrailingArguments;

    /**
     * The display name that will be used for the trailing arguments in
     * the usage information.
     */
    private String trailingArgsDisplayName;

    /**
     * Creates a new subcommand with the provided information. The subcommand will be automatically registered with the
     * associated parser.
     *
     * @param parser
     *            The argument parser with which this subcommand is associated.
     * @param name
     *            The name of this subcommand.
     * @param description
     *            The description of this subcommand.
     * @throws ArgumentException
     *             If the associated argument parser already has a subcommand with the same name.
     */
    public SubCommand(SubCommandArgumentParser parser, String name, LocalizableMessage description)
            throws ArgumentException {
        this(parser, name, false, 0, 0, null, description);
    }

    /**
     * Creates a new subcommand with the provided information. The subcommand will be automatically registered with the
     * associated parser.
     *
     * @param parser
     *            The argument parser with which this subcommand is associated.
     * @param name
     *            The name of this subcommand.
     * @param allowsTrailingArguments
     *            Indicates whether this parser allows unnamed trailing arguments to be provided.
     * @param minTrailingArguments
     *            The minimum number of unnamed trailing arguments that must be provided. A value less than or equal to
     *            zero indicates that no minimum will be enforced.
     * @param maxTrailingArguments
     *            The maximum number of unnamed trailing arguments that may be provided. A value less than or equal to
     *            zero indicates that no maximum will be enforced.
     * @param trailingArgsDisplayName
     *            The display name that should be used as a placeholder for unnamed trailing arguments in the generated
     *            usage information.
     * @param description
     *            The description of this subcommand.
     * @throws ArgumentException
     *             If the associated argument parser already has a subcommand with the same name.
     */
    public SubCommand(SubCommandArgumentParser parser, String name, boolean allowsTrailingArguments,
            int minTrailingArguments, int maxTrailingArguments, String trailingArgsDisplayName,
            LocalizableMessage description) throws ArgumentException {
        this.parser = parser;
        this.name = name;
        this.description = description;
        this.allowsTrailingArguments = allowsTrailingArguments;
        this.minTrailingArguments = minTrailingArguments;
        this.maxTrailingArguments = maxTrailingArguments;
        this.trailingArgsDisplayName = trailingArgsDisplayName;
        this.isHidden = false;

        String nameToCheck = name;
        if (parser.longArgumentsCaseSensitive()) {
            nameToCheck = toLowerCase(name);
        }

        if (parser.hasSubCommand(nameToCheck)) {
            LocalizableMessage message = ERR_ARG_SUBCOMMAND_DUPLICATE_SUBCOMMAND.get(name);
            throw new ArgumentException(message);
        }

        parser.addSubCommand(this);
    }

    /**
     * Retrieves the name of this subcommand.
     *
     * @return The name of this subcommand.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the description for this subcommand.
     *
     * @return The description for this subcommand.
     */
    public LocalizableMessage getDescription() {
        return description;
    }

    /**
     * A supplement to the description for this subcommand
     * intended for use in generated reference documentation.
     */
    private LocalizableMessage docDescriptionSupplement;

    /** {@inheritDoc} */
    public LocalizableMessage getDocDescriptionSupplement() {
        return docDescriptionSupplement != null ? docDescriptionSupplement : LocalizableMessage.EMPTY;
    }

    /** {@inheritDoc} */
    public void setDocDescriptionSupplement(final LocalizableMessage docDescriptionSupplement) {
        this.docDescriptionSupplement = docDescriptionSupplement;
    }

    /**
     * Retrieves the set of arguments for this subcommand.
     *
     * @return The set of arguments for this subcommand.
     */
    public LinkedList<Argument> getArguments() {
        return arguments;
    }

    /**
     * Retrieves the subcommand argument with the specified short identifier.
     *
     * @param shortID
     *            The short identifier of the argument to retrieve.
     * @return The subcommand argument with the specified short identifier, or <CODE>null</CODE> if there is none.
     */
    public Argument getArgument(Character shortID) {
        return shortIDMap.get(shortID);
    }

    /**
     * Retrieves the subcommand argument with the specified long identifier.
     *
     * @param longID
     *            The long identifier of the argument to retrieve.
     * @return The subcommand argument with the specified long identifier, or <CODE>null</CODE> if there is none.
     */
    public Argument getArgument(String longID) {
        return longIDMap.get(longID);
    }

    /**
     * Retrieves the subcommand argument with the specified name.
     *
     * @param name
     *            The name of the argument to retrieve.
     * @return The subcommand argument with the specified name, or <CODE>null</CODE> if there is no such argument.
     */
    public Argument getArgumentForName(String name) {
        for (Argument a : arguments) {
            if (a.getName().equals(name)) {
                return a;
            }
        }

        return null;
    }

    /**
     * Adds the provided argument for use with this subcommand.
     *
     * @param argument
     *            The argument to add for use with this subcommand.
     * @throws ArgumentException
     *             If either the short ID or long ID for the argument conflicts with that of another argument already
     *             associated with this subcommand.
     */
    public void addArgument(Argument argument) throws ArgumentException {
        String argumentName = argument.getName();
        for (Argument a : arguments) {
            if (argumentName.equals(a.getName())) {
                LocalizableMessage message = ERR_ARG_SUBCOMMAND_DUPLICATE_ARGUMENT_NAME.get(name, argumentName);
                throw new ArgumentException(message);
            }
        }

        if (parser.hasGlobalArgument(argumentName)) {
            LocalizableMessage message = ERR_ARG_SUBCOMMAND_ARGUMENT_GLOBAL_CONFLICT.get(argumentName, name);
            throw new ArgumentException(message);
        }

        Character shortID = argument.getShortIdentifier();
        if (shortID != null) {
            if (shortIDMap.containsKey(shortID)) {
                LocalizableMessage message = ERR_ARG_SUBCOMMAND_DUPLICATE_SHORT_ID.get(argumentName, name,
                        String.valueOf(shortID), shortIDMap.get(shortID).getName());
                throw new ArgumentException(message);
            }

            Argument arg = parser.getGlobalArgumentForShortID(shortID);
            if (arg != null) {
                LocalizableMessage message = ERR_ARG_SUBCOMMAND_ARGUMENT_SHORT_ID_GLOBAL_CONFLICT.get(argumentName,
                        name, String.valueOf(shortID), arg.getName());
                throw new ArgumentException(message);
            }
        }

        String longID = argument.getLongIdentifier();
        if (longID != null) {
            if (!parser.longArgumentsCaseSensitive()) {
                longID = toLowerCase(longID);
            }

            if (longIDMap.containsKey(longID)) {
                LocalizableMessage message = ERR_ARG_SUBCOMMAND_DUPLICATE_LONG_ID.get(argumentName, name,
                        argument.getLongIdentifier(), longIDMap.get(longID).getName());
                throw new ArgumentException(message);
            }

            Argument arg = parser.getGlobalArgumentForLongID(longID);
            if (arg != null) {
                LocalizableMessage message = ERR_ARG_SUBCOMMAND_ARGUMENT_LONG_ID_GLOBAL_CONFLICT.get(argumentName,
                        name, argument.getLongIdentifier(), arg.getName());
                throw new ArgumentException(message);
            }
        }

        arguments.add(argument);

        if (shortID != null) {
            shortIDMap.put(shortID, argument);
        }

        if (longID != null) {
            longIDMap.put(longID, argument);
        }
    }

    /**
     * Indicates whether this sub-command will allow unnamed trailing arguments. These will be arguments at the end of
     * the list that are not preceded by either a long or short identifier and will need to be manually parsed by the
     * application using this parser. Note that once an unnamed trailing argument has been identified, all remaining
     * arguments will be classified as such.
     *
     * @return <CODE>true</CODE> if this sub-command allows unnamed trailing arguments, or <CODE>false</CODE> if it does
     *         not.
     */
    public boolean allowsTrailingArguments() {
        return allowsTrailingArguments;
    }

    /**
     * Retrieves the minimum number of unnamed trailing arguments that must be provided.
     *
     * @return The minimum number of unnamed trailing arguments that must be provided, or a value less than or equal to
     *         zero if no minimum will be enforced.
     */
    public int getMinTrailingArguments() {
        return minTrailingArguments;
    }

    /**
     * Retrieves the maximum number of unnamed trailing arguments that may be provided.
     *
     * @return The maximum number of unnamed trailing arguments that may be provided, or a value less than or equal to
     *         zero if no maximum will be enforced.
     */
    public int getMaxTrailingArguments() {
        return maxTrailingArguments;
    }

    /**
     * Retrieves the trailing arguments display name.
     *
     * @return Returns the trailing arguments display name.
     */
    public String getTrailingArgumentsDisplayName() {
        return trailingArgsDisplayName;
    }

    /**
     * Retrieves the set of unnamed trailing arguments that were provided on the command line.
     *
     * @return The set of unnamed trailing arguments that were provided on the command line.
     */
    public ArrayList<String> getTrailingArguments() {
        return parser.getTrailingArguments();
    }

    /**
     * Indicates whether this subcommand should be hidden from the usage information.
     *
     * @return <CODE>true</CODE> if this subcommand should be hidden from the usage information, or <CODE>false</CODE>
     *         if not.
     */
    public boolean isHidden() {
        return isHidden;
    }

    /**
     * Specifies whether this subcommand should be hidden from the usage information.
     *
     * @param isHidden
     *            Indicates whether this subcommand should be hidden from the usage information.
     */
    public void setHidden(boolean isHidden) {
        this.isHidden = isHidden;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("(").append("name=").append(this.name);
        if (!longIDMap.isEmpty()) {
            sb.append(", longIDs=").append(longIDMap.keySet());
        }
        if (!shortIDMap.isEmpty()) {
            sb.append(", shortIDs=").append(shortIDMap.keySet());
        }
        sb.append(")");
        return sb.toString();
    }
}
