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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.Utils.OBFUSCATED_VALUE;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * Class used to be able to generate the non interactive mode.
 */
public class CommandBuilder {
    private String commandName;
    private String subcommandName;
    private final ArrayList<Argument> args = new ArrayList<>();
    private final HashSet<Argument> obfuscatedArgs = new HashSet<>();

    /**
     * The separator used to link the lines of the resulting command-lines.
     */
    public static final String LINE_SEPARATOR;
    static {
        if (OperatingSystem.isWindows()) {
            LINE_SEPARATOR = " ";
        } else {
            LINE_SEPARATOR = " \\\n          ";
        }
    }

    /**
     * The separator used to link the lines of the resulting command-lines in HTML format.
     */
    public static final String HTML_LINE_SEPARATOR;
    static {
        if (OperatingSystem.isWindows()) {
            HTML_LINE_SEPARATOR = "&nbsp;";
        } else {
            HTML_LINE_SEPARATOR = "&nbsp;\\<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
        }
    }

    /**
     * The constructor for the CommandBuilder.
     *
     * @param commandName
     *            The command name.
     * @param subcommandName
     *            The sub command name.
     */
    public CommandBuilder(String commandName, String subcommandName) {
        this.commandName = commandName;
        this.subcommandName = subcommandName;
    }

    /**
     * Adds an argument to the list of the command builder.
     *
     * @param argument
     *            The argument to be added.
     */
    public void addArgument(final Argument argument) {
        // We use an ArrayList to be able to provide the possibility of updating
        // the position of the attributes.
        if (!args.contains(argument)) {
            args.add(argument);
        }
    }

    /**
     * Adds an argument whose values must be obfuscated (passwords for instance).
     *
     * @param argument
     *            The argument to be added.
     */
    public void addObfuscatedArgument(final Argument argument) {
        addArgument(argument);
        obfuscatedArgs.add(argument);
    }

    /**
     * Removes the provided argument from this CommandBuilder.
     *
     * @param argument
     *            The argument to be removed.
     * @return <CODE>true</CODE> if the attribute was present and removed and <CODE>false</CODE> otherwise.
     */
    public boolean removeArgument(final Argument argument) {
        obfuscatedArgs.remove(argument);
        return args.remove(argument);
    }

    /**
     * Appends the arguments of another command builder to this command builder.
     *
     * @param builder
     *            The CommandBuilder to append.
     */
    public void append(final CommandBuilder builder) {
        for (final Argument arg : builder.args) {
            if (builder.isObfuscated(arg)) {
                addObfuscatedArgument(arg);
            } else {
                addArgument(arg);
            }
        }
    }

    /**
     * Returns the String representation of this command builder (i.e. what we want to show to the user).
     *
     * @return The String representation of this command builder (i.e. what we want to show to the user).
     */
    public String toString() {
        return toString(false, LINE_SEPARATOR);
    }

    /**
     * Returns the String representation of this command builder (i.e. what we want to show to the user).
     *
     * @param lineSeparator
     *            The String to be used to separate lines of the command-builder.
     * @return The String representation of this command builder (i.e. what we want to show to the user).
     */
    public String toString(final String lineSeparator) {
        return toString(false, lineSeparator);
    }

    /**
     * Returns the String representation of this command builder (i.e. what we want to show to the user).
     *
     * @param showObfuscated
     *            Displays in clear the obfuscated values.
     * @param lineSeparator
     *            The String to be used to separate lines of the command-builder.
     * @return The String representation of this command builder (i.e. what we want to show to the user).
     */
    private String toString(final boolean showObfuscated, final String lineSeparator) {
        final StringBuilder builder = new StringBuilder();
        builder.append(commandName);
        if (subcommandName != null) {
            builder.append(" ").append(subcommandName);
        }
        for (final Argument arg : args) {
            // This CLI is always using SSL, and the argument has been removed from
            // the user interface
            if ("useSSL".equals(arg.getName())) {
                continue;
            }
            String argName;
            if (arg.getLongIdentifier() != null) {
                argName = "--" + arg.getLongIdentifier();
            } else {
                argName = "-" + arg.getShortIdentifier();
            }

            if (arg instanceof BooleanArgument) {
                builder.append(lineSeparator).append(argName);
            } else if (arg instanceof FileBasedArgument) {
                for (String value : ((FileBasedArgument) arg).getNameToValueMap().keySet()) {
                    builder.append(lineSeparator).append(argName).append(" ");
                    builder.append(getOutputValue(value, arg, showObfuscated));
                }
            } else {
                for (String value : arg.getValues()) {
                    builder.append(lineSeparator).append(argName).append(" ");
                    builder.append(getOutputValue(value, arg, showObfuscated));
                }
            }
        }
        return builder.toString();
    }

    private String getOutputValue(final String value, final Argument arg, final boolean showObfuscated) {
        if (isObfuscated(arg) && !showObfuscated) {
            return OBFUSCATED_VALUE;
        }
        return escapeValue(value);
    }

    /**
     * Clears the arguments.
     */
    public void clearArguments() {
        args.clear();
        obfuscatedArgs.clear();
    }

    /**
     * Returns the list of arguments.
     *
     * @return The list of arguments.
     */
    public List<Argument> getArguments() {
        return args;
    }

    /**
     * Tells whether the provided argument's values must be obfuscated or not.
     *
     * @param argument
     *            The argument to handle.
     * @return <CODE>true</CODE> if the attribute's values must be obfuscated and <CODE>false</CODE> otherwise.
     */
    public boolean isObfuscated(final Argument argument) {
        return obfuscatedArgs.contains(argument);
    }

    /** Chars that require special treatment when passing them to command-line. */
    private static final Set<Character> CHARSTOESCAPE = new TreeSet<>(Arrays.asList(
        ' ', '\t', '\n', '|', ';', '<', '>', '(', ')', '$', '`', '\\', '"', '\''));

    /**
     * This method simply takes a value and tries to transform it (with escape or '"') characters so that it can be used
     * in a command line.
     *
     * @param value
     *            The String to be treated.
     * @return The transformed value.
     */
    public static String escapeValue(String value) {
        final StringBuilder b = new StringBuilder();
        if (OperatingSystem.isUnix()) {
            for (int i = 0; i < value.length(); i++) {
                final char c = value.charAt(i);
                if (CHARSTOESCAPE.contains(c)) {
                    b.append('\\');
                }
                b.append(c);
            }
        } else {
            b.append('"').append(value).append('"');
        }
        return b.toString();
    }
}
