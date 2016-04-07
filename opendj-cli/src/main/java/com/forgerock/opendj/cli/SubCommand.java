/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
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

    /** The display name that will be used for the trailing arguments in the usage information. */
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

    @Override
    public LocalizableMessage getDocDescriptionSupplement() {
        return docDescriptionSupplement != null ? docDescriptionSupplement : LocalizableMessage.EMPTY;
    }

    /**
     * Sets a supplement to the description intended for use in generated reference documentation.
     *
     * @param docDescriptionSupplement
     *            The supplement to the description for use in generated reference documentation.
     */
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
     * Retrieves the subcommand argument with the specified long identifier.
     *
     * @param longIdentifier
     *            The long identifier of the argument to retrieve.
     * @return The subcommand argument with the specified long identifier,
     *         or <CODE>null</CODE> if there is no such argument.
     */
    public Argument getArgumentForLongIdentifier(final String longIdentifier) {
        return longIDMap.get(parser.longArgumentsCaseSensitive() ? longIdentifier : toLowerCase(longIdentifier));
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
        final String argumentLongID = argument.getLongIdentifier();
        if (getArgumentForLongIdentifier(argumentLongID) != null) {
            throw new ArgumentException(ERR_ARG_SUBCOMMAND_DUPLICATE_ARGUMENT_NAME.get(name, argumentLongID));
        }

        if (parser.hasGlobalArgument(argumentLongID)) {
            throw new ArgumentException(ERR_ARG_SUBCOMMAND_ARGUMENT_GLOBAL_CONFLICT.get(argumentLongID, name));
        }

        Character shortID = argument.getShortIdentifier();
        if (shortID != null) {
            if (shortIDMap.containsKey(shortID)) {
                throw new ArgumentException(ERR_ARG_SUBCOMMAND_DUPLICATE_SHORT_ID.get(
                        argumentLongID, name, String.valueOf(shortID), shortIDMap.get(shortID).getLongIdentifier()));
            }

            Argument arg = parser.getGlobalArgumentForShortID(shortID);
            if (arg != null) {
                throw new ArgumentException(ERR_ARG_SUBCOMMAND_ARGUMENT_SHORT_ID_GLOBAL_CONFLICT.get(
                        argumentLongID, name, String.valueOf(shortID), arg.getLongIdentifier()));
            }
        }

        String longID = argument.getLongIdentifier();
        if (!parser.longArgumentsCaseSensitive()) {
            longID = toLowerCase(longID);
            if (longIDMap.containsKey(longID)) {
                throw new ArgumentException(ERR_ARG_SUBCOMMAND_DUPLICATE_LONG_ID.get(argumentLongID, name));
            }
        }

        Argument arg = parser.getGlobalArgumentForLongID(longID);
        if (arg != null) {
            throw new ArgumentException(ERR_ARG_SUBCOMMAND_ARGUMENT_LONG_ID_GLOBAL_CONFLICT.get(argumentLongID, name));
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
