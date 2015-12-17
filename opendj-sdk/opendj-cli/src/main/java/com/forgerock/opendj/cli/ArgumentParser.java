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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.DocGenerationHelper.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Utils;

/**
 * This class defines a utility that can be used to deal with command-line
 * arguments for applications in a CLIP-compliant manner using either short
 * one-character or longer word-based arguments. It is also integrated with the
 * Directory Server message catalog so that it can display messages in an
 * internationalizable format, can automatically generate usage information,
 * can detect conflicts between arguments, and can interact with a properties
 * file to obtain default values for arguments there if they are not specified
 * on the command-line.
 */
public class ArgumentParser implements ToolRefDocContainer {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The name of the OpenDJ configuration direction in the user home
     * directory.
     */
    public static final String DEFAULT_OPENDJ_CONFIG_DIR = ".opendj";
    /** The default properties file name. */
    public static final String DEFAULT_OPENDJ_PROPERTIES_FILE_NAME = "tools";
    /** The default properties file extension. */
    public static final String DEFAULT_OPENDJ_PROPERTIES_FILE_EXTENSION = ".properties";
    /** The name of a command-line script used to launch a tool. */
    public static final String PROPERTY_SCRIPT_NAME = "com.forgerock.opendj.ldap.tools.scriptName";
    /** The legacy name of a command-line script used to launch a tool. */
    public static final String PROPERTY_SCRIPT_NAME_LEGACY = "org.opends.server.scriptName";

    /** The argument that will be used to indicate the file properties. */
    private StringArgument filePropertiesPathArgument;
    /** The argument that will be used to indicate that we'll not look for default properties file. */
    private BooleanArgument noPropertiesFileArgument;
    /** The argument that will be used to trigger the display of usage information. */
    private Argument usageArgument;
    /** The argument that will be used to trigger the display of the OpenDJ version. */
    private Argument versionArgument;

    /** The set of unnamed trailing arguments that were provided for this parser. */
    private final ArrayList<String> trailingArguments = new ArrayList<>();

    /**
     * Indicates whether this parser will allow additional unnamed arguments at
     * the end of the list.
     */
    private final boolean allowsTrailingArguments;
    /** Indicates whether long arguments should be treated in a case-sensitive manner. */
    private final boolean longArgumentsCaseSensitive;
    /** Indicates whether the usage or version information has been displayed. */
    private boolean usageOrVersionDisplayed;
    /** Indicates whether the version argument was provided. */
    private boolean versionPresent;

    /** The handler to call to print the product version. */
    private VersionHandler versionHandler = new VersionHandler() {
        @Override
        public void printVersion() {
            // display nothing at all
        }

        @Override
        public String toString() {
            return "<no version displayed>";
        }
    };

    /** The set of arguments defined for this parser, referenced by short ID. */
    private final HashMap<Character, Argument> shortIDMap = new HashMap<>();
    /** The set of arguments defined for this parser, referenced by long ID. */
    private final HashMap<String, Argument> longIDMap = new HashMap<>();
    /** The set of arguments defined for this parser, referenced by argument name. */
    private final HashMap<String, Argument> argumentMap = new HashMap<>();
    /** The total set of arguments defined for this parser. */
    private final LinkedList<Argument> argumentList = new LinkedList<>();

    /** The maximum number of unnamed trailing arguments that may be provided. */
    private final int maxTrailingArguments;
    /** The minimum number of unnamed trailing arguments that may be provided. */
    private final int minTrailingArguments;

    /** The output stream to which usage information should be printed. */
    private OutputStream usageOutputStream = System.out;

    /**
     * The fully-qualified name of the Java class that should be invoked to
     * launch the program with which this argument parser is associated.
     */
    private final String mainClassName;

    /**
     * A human-readable description for the tool, which will be included when
     * displaying usage information.
     */
    private final LocalizableMessage toolDescription;
    /** A short description for this tool, suitable in a man page summary line. */
    private LocalizableMessage shortToolDescription;

    /** The display name that will be used for the trailing arguments in the usage information. */
    private final String trailingArgsDisplayName;

    /** The raw set of command-line arguments that were provided. */
    private String[] rawArguments;

    /** Set of argument groups. */
    protected final Set<ArgumentGroup> argumentGroups = new TreeSet<>();

    /**
     * Group for arguments that have not been explicitly grouped. These will
     * appear at the top of the usage statement without a header.
     */
    private final ArgumentGroup defaultArgGroup = new ArgumentGroup(
            LocalizableMessage.EMPTY, Integer.MAX_VALUE);

    /**
     * Group for arguments that are related to connection through LDAP. This
     * includes options like the bind DN, the port, etc.
     */
    final ArgumentGroup ldapArgGroup = new ArgumentGroup(
            INFO_DESCRIPTION_LDAP_CONNECTION_ARGS.get(), Integer.MIN_VALUE + 2);

    /**
     * Group for arguments that are related to utility input/output like
     * properties file, no-prompt etc. These will appear toward the bottom of
     * the usage statement.
     */
    protected final ArgumentGroup ioArgGroup = new ArgumentGroup(
            INFO_DESCRIPTION_IO_ARGS.get(), Integer.MIN_VALUE + 1);

    /**
     * Group for arguments that are general like help, version etc. These will
     * appear at the end of the usage statement.
     */
    private final ArgumentGroup generalArgGroup = new ArgumentGroup(
            INFO_DESCRIPTION_GENERAL_ARGS.get(), Integer.MIN_VALUE);

    private static final String INDENT = "    ";

    /**
     * Creates a new instance of this argument parser with no arguments. Unnamed
     * trailing arguments will not be allowed.
     *
     * @param mainClassName
     *            The fully-qualified name of the Java class that should be
     *            invoked to launch the program with which this argument parser
     *            is associated.
     * @param toolDescription
     *            A human-readable description for the tool, which will be
     *            included when displaying usage information.
     * @param longArgumentsCaseSensitive
     *            Indicates whether long arguments should be treated in a
     *            case-sensitive manner.
     */
    public ArgumentParser(final String mainClassName, final LocalizableMessage toolDescription,
            final boolean longArgumentsCaseSensitive) {
        this.mainClassName = mainClassName;
        this.toolDescription = toolDescription;
        this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;

        allowsTrailingArguments = false;
        trailingArgsDisplayName = null;
        maxTrailingArguments = 0;
        minTrailingArguments = 0;
        initGroups();
    }

    /**
     * Creates a new instance of this argument parser with no arguments that may
     * or may not be allowed to have unnamed trailing arguments.
     *
     * @param mainClassName
     *            The fully-qualified name of the Java class that should be
     *            invoked to launch the program with which this argument parser
     *            is associated.
     * @param toolDescription
     *            A human-readable description for the tool, which will be
     *            included when displaying usage information.
     * @param longArgumentsCaseSensitive
     *            Indicates whether long arguments should be treated in a
     *            case-sensitive manner.
     * @param allowsTrailingArguments
     *            Indicates whether this parser allows unnamed trailing
     *            arguments to be provided.
     * @param minTrailingArguments
     *            The minimum number of unnamed trailing arguments that must be
     *            provided. A value less than or equal to zero indicates that no
     *            minimum will be enforced.
     * @param maxTrailingArguments
     *            The maximum number of unnamed trailing arguments that may be
     *            provided. A value less than or equal to zero indicates that no
     *            maximum will be enforced.
     * @param trailingArgsDisplayName
     *            The display name that should be used as a placeholder for
     *            unnamed trailing arguments in the generated usage information.
     */
    public ArgumentParser(final String mainClassName, final LocalizableMessage toolDescription,
            final boolean longArgumentsCaseSensitive, final boolean allowsTrailingArguments,
            final int minTrailingArguments, final int maxTrailingArguments,
            final String trailingArgsDisplayName) {
        this.mainClassName = mainClassName;
        this.toolDescription = toolDescription;
        this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;
        this.allowsTrailingArguments = allowsTrailingArguments;
        this.minTrailingArguments = minTrailingArguments;
        this.maxTrailingArguments = maxTrailingArguments;
        this.trailingArgsDisplayName = trailingArgsDisplayName;

        initGroups();
    }

    /**
     * Adds the provided argument to the set of arguments handled by this
     * parser.
     *
     * @param argument
     *            The argument to be added.
     * @throws ArgumentException
     *             If the provided argument conflicts with another argument that
     *             has already been defined.
     */
    public void addArgument(final Argument argument) throws ArgumentException {
        addArgument(argument, null);
    }

    /**
     * Adds the provided argument to the set of arguments handled by this
     * parser.
     *
     * @param argument
     *            The argument to be added.
     * @param group
     *            The argument group to which the argument belongs.
     * @throws ArgumentException
     *             If the provided argument conflicts with another argument that
     *             has already been defined.
     */
    public void addArgument(final Argument argument, ArgumentGroup group) throws ArgumentException {
        final Character shortID = argument.getShortIdentifier();
        if (shortID != null && shortIDMap.containsKey(shortID)) {
            final String conflictingName = shortIDMap.get(shortID).getName();
            throw new ArgumentException(
                    ERR_ARGPARSER_DUPLICATE_SHORT_ID.get(argument.getName(), shortID, conflictingName));
        }

        // JNR: what is the requirement for the following code?
        if (versionArgument != null
                && shortID != null
                && shortID.equals(versionArgument.getShortIdentifier())) {
            // Update the version argument to not display its short identifier.
            try {
                versionArgument = getVersionArgument(false);
                // JNR: why not call addGeneralArgument(versionArgument) here?
                this.generalArgGroup.addArgument(versionArgument);
            } catch (final ArgumentException e) {
                // ignore
            }
        }

        String longID = argument.getLongIdentifier();
        if (longID != null) {
            if (!longArgumentsCaseSensitive) {
                longID = toLowerCase(longID);
            }
            if (longIDMap.containsKey(longID)) {
                final String conflictingName = longIDMap.get(longID).getName();
                throw new ArgumentException(ERR_ARGPARSER_DUPLICATE_LONG_ID.get(
                    argument.getName(), argument.getLongIdentifier(), conflictingName));
            }
        }

        if (shortID != null) {
            shortIDMap.put(shortID, argument);
        }

        if (longID != null) {
            longIDMap.put(longID, argument);
        }

        argumentList.add(argument);

        if (group == null) {
            group = getStandardGroup(argument);
        }
        group.addArgument(argument);
        argumentGroups.add(group);
    }

    private BooleanArgument getVersionArgument(final boolean displayShortIdentifier) throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_PRODUCT_VERSION, displayShortIdentifier ? OPTION_SHORT_PRODUCT_VERSION
                : null, OPTION_LONG_PRODUCT_VERSION, INFO_DESCRIPTION_PRODUCT_VERSION.get());
    }

    /**
     * Adds the provided argument to the set of arguments handled by this parser
     * and puts the argument in the default group.
     *
     * @param argument
     *            The argument to be added.
     * @throws ArgumentException
     *             If the provided argument conflicts with another argument that
     *             has already been defined.
     */
    protected void addDefaultArgument(final Argument argument) throws ArgumentException {
        addArgument(argument, defaultArgGroup);
    }

    /**
     * Adds the provided argument to the set of arguments handled by this parser
     * and puts the argument in the LDAP connection group.
     *
     * @param argument
     *            The argument to be added.
     * @throws ArgumentException
     *             If the provided argument conflicts with another argument that
     *             has already been defined.
     */
    public void addLdapConnectionArgument(final Argument argument) throws ArgumentException {
        addArgument(argument, ldapArgGroup);
    }

    /**
     * Indicates whether this parser will allow unnamed trailing arguments.
     * These will be arguments at the end of the list that are not preceded by
     * either a long or short identifier and will need to be manually parsed by
     * the application using this parser. Note that once an unnamed trailing
     * argument has been identified, all remaining arguments will be classified
     * as such.
     *
     * @return <CODE>true</CODE> if this parser allows unnamed trailing
     *         arguments, or <CODE>false</CODE> if it does not.
     */
    boolean allowsTrailingArguments() {
        return allowsTrailingArguments;
    }

    /**
     * Check if we have a properties file.
     *
     * @return The properties found in the properties file or null.
     * @throws ArgumentException
     *             If a problem was encountered while parsing the provided
     *             arguments.
     */
    Properties checkExternalProperties() throws ArgumentException {
        // We don't look for properties file.
        if (noPropertiesFileArgument != null && noPropertiesFileArgument.isPresent()) {
            return null;
        }

        // Check if we have a properties file argument
        if (filePropertiesPathArgument == null) {
            return null;
        }

        // check if the properties file argument has been set. If not
        // look for default location.
        String propertiesFilePath = null;
        if (filePropertiesPathArgument.isPresent()) {
            propertiesFilePath = filePropertiesPathArgument.getValue();
        } else {
            // Check in "user home"/.opendj directory
            final String userDir = System.getProperty("user.home");
            propertiesFilePath =
                    findPropertiesFile(userDir + File.separator + DEFAULT_OPENDJ_CONFIG_DIR);
        }

        // We don't have a properties file location
        if (propertiesFilePath == null) {
            return null;
        }

        // We have a location for the properties file.
        final Properties argumentProperties = new Properties();
        final String scriptName = getScriptName();
        try {
            final Properties p = new Properties();
            final FileInputStream fis = new FileInputStream(propertiesFilePath);
            p.load(fis);
            fis.close();

            for (final Enumeration<?> e = p.propertyNames(); e.hasMoreElements();) {
                final String currentPropertyName = (String) e.nextElement();
                String propertyName = currentPropertyName;

                // Property name form <script name>.<property name> has the
                // precedence to <property name>
                if (scriptName != null) {
                    if (currentPropertyName.startsWith(scriptName)) {
                        propertyName = currentPropertyName.substring(scriptName.length() + 1);
                    } else if (p.containsKey(scriptName + "." + currentPropertyName)) {
                        continue;
                    }
                }
                argumentProperties.setProperty(propertyName.toLowerCase(), p
                        .getProperty(currentPropertyName));
            }
        } catch (final Exception e) {
            final LocalizableMessage message =
                    ERR_ARGPARSER_CANNOT_READ_PROPERTIES_FILE.get(propertiesFilePath, getExceptionMessage(e));
            throw new ArgumentException(message, e);
        }
        return argumentProperties;
    }

    /**
     * Retrieves the argument with the specified name.
     *
     * @param name
     *            The name of the argument to retrieve.
     * @return The argument with the specified name, or <CODE>null</CODE> if
     *         there is no such argument.
     */
    Argument getArgument(final String name) {
        return argumentMap.get(name);
    }

    /**
     * Retrieves the argument with the specified long identifier.
     *
     * @param longID
     *            The long identifier of the argument to retrieve.
     * @return The argument with the specified long identifier, or
     *         <CODE>null</CODE> if there is no such argument.
     */
    public Argument getArgumentForLongID(final String longID) {
        return longIDMap.get(longID);
    }

    /**
     * Retrieves the argument with the specified short identifier.
     *
     * @param shortID
     *            The short ID for the argument to retrieve.
     * @return The argument with the specified short identifier, or
     *         <CODE>null</CODE> if there is no such argument.
     */
    Argument getArgumentForShortID(final Character shortID) {
        return shortIDMap.get(shortID);
    }

    /**
     * Retrieves the list of all arguments that have been defined for this
     * argument parser.
     *
     * @return The list of all arguments that have been defined for this
     *         argument parser.
     */
    public LinkedList<Argument> getArgumentList() {
        return argumentList;
    }

    /**
     * Retrieves the set of arguments mapped by the long identifier that may be
     * used to reference them. Note that arguments that do not have a long
     * identifier will not be present in this list.
     *
     * @return The set of arguments mapped by the long identifier that may be
     *         used to reference them.
     */
    HashMap<String, Argument> getArgumentsByLongID() {
        return longIDMap;
    }

    /**
     * Retrieves the set of arguments mapped by the short identifier that may be
     * used to reference them. Note that arguments that do not have a short
     * identifier will not be present in this list.
     *
     * @return The set of arguments mapped by the short identifier that may be
     *         used to reference them.
     */
    HashMap<Character, Argument> getArgumentsByShortID() {
        return shortIDMap;
    }

    /**
     * Retrieves the fully-qualified name of the Java class that should be
     * invoked to launch the program with which this argument parser is
     * associated.
     *
     * @return The fully-qualified name of the Java class that should be invoked
     *         to launch the program with which this argument parser is
     *         associated.
     */
    String getMainClassName() {
        return mainClassName;
    }

    /**
     * Retrieves the maximum number of unnamed trailing arguments that may be
     * provided.
     *
     * @return The maximum number of unnamed trailing arguments that may be
     *         provided, or a value less than or equal to zero if no maximum
     *         will be enforced.
     */
    int getMaxTrailingArguments() {
        return maxTrailingArguments;
    }

    /**
     * Retrieves the minimum number of unnamed trailing arguments that must be
     * provided.
     *
     * @return The minimum number of unnamed trailing arguments that must be
     *         provided, or a value less than or equal to zero if no minimum
     *         will be enforced.
     */
    int getMinTrailingArguments() {
        return minTrailingArguments;
    }

    /**
     * Retrieves the raw set of arguments that were provided.
     *
     * @return The raw set of arguments that were provided, or <CODE>null</CODE>
     *         if the argument list has not yet been parsed.
     */
    String[] getRawArguments() {
        return rawArguments;
    }

    /**
     * Given an argument, returns an appropriate group. Arguments may be part of
     * one of the special groups or the default group.
     *
     * @param argument
     *            for which a group is requested
     * @return argument group appropriate for <code>argument</code>
     */
    ArgumentGroup getStandardGroup(final Argument argument) {
        if (isInputOutputArgument(argument)) {
            return ioArgGroup;
        } else if (isGeneralArgument(argument)) {
            return generalArgGroup;
        } else if (isLdapConnectionArgument(argument)) {
            return ldapArgGroup;
        } else {
            return defaultArgGroup;
        }
    }

    /**
     * Retrieves a human-readable description for this tool, which should be
     * included at the top of the command-line usage information.
     *
     * @return A human-readable description for this tool, or {@code null} if
     *         none is available.
     */
    LocalizableMessage getToolDescription() {
        return toolDescription;
    }

    @Override
    public LocalizableMessage getShortToolDescription() {
        return shortToolDescription != null ? shortToolDescription : LocalizableMessage.EMPTY;
    }

    @Override
    public void setShortToolDescription(final LocalizableMessage shortDescription) {
        this.shortToolDescription = shortDescription;
    }

    /**
     * A supplement to the description for this tool
     * intended for use in generated reference documentation.
     */
    private DocDescriptionSupplement docToolDescriptionSupplement;

    @Override
    public LocalizableMessage getDocToolDescriptionSupplement() {
        this.docToolDescriptionSupplement =
                constructIfNull(this.docToolDescriptionSupplement);
        return this.docToolDescriptionSupplement.getDocDescriptionSupplement();
    }

    @Override
    public void setDocToolDescriptionSupplement(final LocalizableMessage supplement) {
        this.docToolDescriptionSupplement =
                constructIfNull(this.docToolDescriptionSupplement);
        this.docToolDescriptionSupplement.setDocDescriptionSupplement(supplement);
    }

    /**
     * A supplement to the description for all subcommands of this tool,
     * intended for use in generated reference documentation.
     */
    private class DocSubcommandsDescriptionSupplement implements DocDescriptionSupplement {
        /** A supplement to the description intended for use in generated reference documentation. */
        private LocalizableMessage docDescriptionSupplement;

        @Override
        public LocalizableMessage getDocDescriptionSupplement() {
            return docDescriptionSupplement != null ? docDescriptionSupplement : LocalizableMessage.EMPTY;
        }

        @Override
        public void setDocDescriptionSupplement(final LocalizableMessage docDescriptionSupplement) {
            this.docDescriptionSupplement = docDescriptionSupplement;
        }
    }

    private DocDescriptionSupplement docSubcommandsDescriptionSupplement;

    @Override
    public LocalizableMessage getDocSubcommandsDescriptionSupplement() {
        this.docSubcommandsDescriptionSupplement =
                constructIfNull(this.docSubcommandsDescriptionSupplement);
        return this.docSubcommandsDescriptionSupplement.getDocDescriptionSupplement();
    }

    @Override
    public void setDocSubcommandsDescriptionSupplement(final LocalizableMessage supplement) {
        this.docSubcommandsDescriptionSupplement =
                constructIfNull(this.docSubcommandsDescriptionSupplement);
        this.docSubcommandsDescriptionSupplement.setDocDescriptionSupplement(supplement);
    }

    private DocDescriptionSupplement constructIfNull(DocDescriptionSupplement supplement) {
        if (supplement != null) {
            return supplement;
        }
        return new DocSubcommandsDescriptionSupplement();
    }

    /**
     * Retrieves the set of unnamed trailing arguments that were provided on the
     * command line.
     *
     * @return The set of unnamed trailing arguments that were provided on the
     *         command line.
     */
    public ArrayList<String> getTrailingArguments() {
        return trailingArguments;
    }

    /**
     * Retrieves a string containing usage information based on the defined
     * arguments.
     *
     * @return A string containing usage information based on the defined
     *         arguments.
     */
    public String getUsage() {
        final StringBuilder buffer = new StringBuilder();
        usageOrVersionDisplayed = true;
        if (System.getProperty("org.forgerock.opendj.gendoc") != null) {
            toRefEntry(buffer, getSynopsisArgs(), argumentList);
        } else {
            getUsage(buffer);
        }
        return buffer.toString();
    }

    /**
     * Return the list of arguments for the generated reference documentation.
     *
     * @return  The list of arguments for the generated reference documentation.
     */
    String getSynopsisArgs() {
        if (allowsTrailingArguments()) {
            if (trailingArgsDisplayName != null) {
                return trailingArgsDisplayName;
            } else {
                return INFO_ARGPARSER_USAGE_TRAILINGARGS.get().toString();
            }
        }
        return null;
    }

    /**
     * Appends a generated DocBook XML RefEntry (man page) to the StringBuilder.
     *
     * @param builder       Append the RefEntry element to this.
     * @param synopsisArgs  List of arguments for the command synopsis.
     * @param argList       List of (global) arguments for this tool.
     */
    void toRefEntry(StringBuilder builder, String synopsisArgs, List<Argument> argList) {
        final String scriptName = getScriptName();
        if (scriptName == null) {
            throw new RuntimeException("The script name should have been set via the environment property '"
                    + PROPERTY_SCRIPT_NAME + "'.");
        }

        final Map<String, Object> map = new HashMap<>();
        map.put("locale", Locale.getDefault().getLanguage());
        map.put("year", new SimpleDateFormat("yyyy").format(new Date()));
        map.put("name", scriptName);
        map.put("shortDesc", getShortToolDescription());
        map.put("descTitle", REF_TITLE_DESCRIPTION.get());
        map.put("args", synopsisArgs);
        map.put("description", eolToNewPara(getToolDescription()));
        map.put("info", getDocToolDescriptionSupplement());
        if (!argList.isEmpty()) {
            map.put("optionSection", getOptionsRefSect1(scriptName));
        }
        map.put("subcommands", null);
        map.put("trailingSectionString", System.getProperty("org.forgerock.opendj.gendoc.trailing"));
        applyTemplate(builder, "refEntry.ftl", map);
    }

    /**
     * Returns a String with line separators replaced by {@code &lt;/para>&lt;para>}.
     * @param input String in which to replace line separators.
     * @return A String with line separators replaced by {@code &lt;/para>&lt;para>}.
     */
    String eolToNewPara(final LocalizableMessage input) {
        return input.toString().replaceAll(EOL, "</para><para>");
    }

    /**
     * Returns a generated DocBook XML RefSect1 element for all command options.
     * @param scriptName    The name of this script.
     * @return              The RefSect1 element as a String.
     */
    protected String getOptionsRefSect1(String scriptName) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", scriptName);
        map.put("title", REF_TITLE_OPTIONS.get());
        map.put("intro", REF_INTRO_OPTIONS.get(scriptName));

        Argument helpArgument = null;
        final boolean printHeaders = printUsageGroupHeaders();
        List<Map<String, Object>> groups = new LinkedList<>();
        for (final ArgumentGroup argGroup : argumentGroups) {
            Map<String, Object> group = new HashMap<>();

            // Add the group's description if any
            if (argGroup.containsArguments() && printHeaders) {
                LocalizableMessage description = argGroup.getDescription();
                if (description != LocalizableMessage.EMPTY) {
                    group.put("description", eolToNewPara(description));
                } else {
                    group.put("description", INFO_SUBCMDPARSER_WHERE_OPTIONS_INCLUDE.get());
                }
            }

            List<Map<String, Object>> options = new LinkedList<>();
            final SortedSet<Argument> args = sortArguments(argGroup.getArguments());
            for (final Argument a : args) {
                if (a.isHidden()) {
                    continue;
                }

                // Return a generic FQDN for localhost as the default hostname
                // in reference documentation.
                if (isHostNameArgument(a)) {
                    a.setDefaultValue("localhost.localdomain");
                }

                // Return a generic message as default backend type depends on the server distribution.
                if (a.getName().equalsIgnoreCase(OPTION_LONG_BACKEND_TYPE)) {
                    a.setDefaultValue(REF_DEFAULT_BACKEND_TYPE.get().toString());
                }

                // The help argument should be added at the end.
                if (isUsageArgument(a)) {
                    helpArgument = a;
                    continue;
                }

                options.add(getArgumentMap(a));
            }
            group.put("options", options);
            if (!options.isEmpty()) {
                groups.add(group);
            }
        }
        if (helpArgument != null) {
            Map<String, Object> helpGroup = new HashMap<>();
            helpGroup.put("description", null);
            List<Map<String, Object>> options = new LinkedList<>();
            options.add(getArgumentMap(helpArgument));
            helpGroup.put("options", options);
            groups.add(helpGroup);
        }
        map.put("groups", groups);

        StringBuilder sb = new StringBuilder();
        applyTemplate(sb, "optionsRefSect1.ftl", map);
        return sb.toString();
    }

    /**
     * Returns true if this argument is for setting a hostname.
     * @param a The argument.
     * @return true if this argument is for setting a hostname.
     */
    boolean isHostNameArgument(final Argument a) {
        final String name = a.getName();
        return name.equalsIgnoreCase(OPTION_LONG_HOST)
                || name.equalsIgnoreCase(OPTION_LONG_REFERENCED_HOST_NAME)
                || name.equalsIgnoreCase("host1")
                || name.equalsIgnoreCase("host2")
                || name.equalsIgnoreCase("hostSource")
                || name.equalsIgnoreCase("hostDestination");
    }

    /**
     * Returns a map containing information about an argument option.
     * @param   a   The argument
     * @return      A map containing information about an argument option
     */
    private Map<String, Object> getArgumentMap(final Argument a) {
        Map<String, Object> option = new HashMap<>();
        option.put("synopsis", getOptionSynopsis(a));
        option.put("description", eolToNewPara(a.getDescription()));
        String dv = a.getDefaultValue();
        option.put("default", dv != null ? REF_DEFAULT.get(dv) : null);
        option.put("info", a.getDocDescriptionSupplement());
        return option;
    }

    /**
     * Writes message to the usage output stream.
     *
     * @param message the message to write
     */
    void writeToUsageOutputStream(CharSequence message) {
        try {
            usageOutputStream.write(getBytes(message.toString()));
        } catch (final Exception e) {
            logger.traceException(e);
        }
    }

    /**
     * Appends usage information based on the defined arguments to the provided
     * buffer.
     *
     * @param buffer
     *            The buffer to which the usage information should be appended.
     */
    private void getUsage(final StringBuilder buffer) {
        buffer.append(getLocalizableScriptName());
        if (allowsTrailingArguments) {
            buffer.append(" ");
            if (trailingArgsDisplayName != null) {
                buffer.append(trailingArgsDisplayName);
            } else {
                buffer.append(INFO_ARGPARSER_USAGE_TRAILINGARGS.get());
            }
        }
        buffer.append(EOL);
        buffer.append(EOL);
        if (toolDescription != null && toolDescription.length() > 0) {
            buffer.append(wrapText(toolDescription.toString(), MAX_LINE_WIDTH - 1));
            buffer.append(EOL);
            buffer.append(EOL);
        }
        buffer.append(INFO_SUBCMDPARSER_WHERE_OPTIONS_INCLUDE.get());
        buffer.append(EOL);
        buffer.append(EOL);

        Argument helpArgument = null;

        final boolean printHeaders = printUsageGroupHeaders();
        for (final ArgumentGroup argGroup : argumentGroups) {
            if (argGroup.containsArguments() && printHeaders) {
                // Print the groups description if any
                final LocalizableMessage groupDesc = argGroup.getDescription();
                if (groupDesc != null && !LocalizableMessage.EMPTY.equals(groupDesc)) {
                    buffer.append(EOL);
                    buffer.append(wrapText(groupDesc.toString(), MAX_LINE_WIDTH - 1));
                    buffer.append(EOL);
                    buffer.append(EOL);
                }
            }

            final SortedSet<Argument> args = sortArguments(argGroup.getArguments());
            for (final Argument a : args) {
                if (a.isHidden()) {
                    continue;
                }

                // Help argument should be printed at the end
                if (isUsageArgument(a)) {
                    helpArgument = a;
                    continue;
                }
                printArgumentUsage(a, buffer);
            }
        }
        if (helpArgument != null) {
            printArgumentUsage(helpArgument, buffer);
        } else {
            buffer.append(EOL);
            buffer.append("-?");
            buffer.append(EOL);
        }
    }

    /**
     * Sorts arguments by identifier, lowercase options first then uppercase.
     *
     * @param arguments     The arguments to sort.
     * @return              The set of arguments in sorted order.
     */
    SortedSet<Argument> sortArguments(final List<Argument> arguments) {
        final SortedSet<Argument> result = new TreeSet<>(new Comparator<Argument>() {

            @Override
            public int compare(final Argument o1, final Argument o2) {
                final String s1 = getIdentifier(o1);
                final String s2 = getIdentifier(o2);
                final int res = s1.compareToIgnoreCase(s2);
                if (res != 0) {
                    return res;
                }
                // Lowercase options first then uppercase.
                return -s1.compareTo(s2);
            }

            private String getIdentifier(final Argument o1) {
                if (o1.getShortIdentifier() != null) {
                    return o1.getShortIdentifier().toString();
                }
                return o1.getLongIdentifier();
            }

        });
        result.addAll(arguments);
        return result;
    }

    /**
     * Returns the script name or a Java equivalent command-line string.
     *
     * @return the script name or a Java equivalent command-line string
     */
    String getScriptNameOrJava() {
        final String scriptName = getScriptName();
        if (scriptName != null && scriptName.length() != 0) {
            return scriptName;
        }
        return "java " + getMainClassName();
    }

    /**
     * Returns the script name as a {@link LocalizableMessage}.
     *
     * @return the script name as a {@link LocalizableMessage}
     */
    LocalizableMessage getLocalizableScriptName() {
        final String scriptName = getScriptName();
        if (scriptName == null || scriptName.length() == 0) {
            return INFO_ARGPARSER_USAGE_JAVA_CLASSNAME.get(mainClassName);
        }
        return INFO_ARGPARSER_USAGE_JAVA_SCRIPTNAME.get(scriptName);
    }

    /**
     * Returns the script name if set.
     *
     * @return the script name, or {@code null} if not set
     */
    String getScriptName() {
        final String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
        if (scriptName != null && scriptName.length() != 0) {
            return scriptName;
        }
        final String legacyScriptName = System.getProperty(PROPERTY_SCRIPT_NAME_LEGACY);
        if (legacyScriptName != null && legacyScriptName.length() != 0) {
            return legacyScriptName;
        }
        return null;
    }

    /**
     * Returns the usage argument.
     *
     * @return the usageArgument
     */
    Argument getUsageArgument() {
        return usageArgument;
    }

    /**
     * Returns whether the provided argument is the usage argument.
     *
     * @param a the argument to test
     * @return true if the provided argument is the usage argument, false otherwise
     */
    boolean isUsageArgument(final Argument a) {
        return usageArgument != null && usageArgument.getName().equals(a.getName());
    }

    /**
     * Retrieves a message containing usage information based on the defined
     * arguments.
     *
     * @return A string containing usage information based on the defined
     *         arguments.
     */
    public LocalizableMessage getUsageMessage() {
        // TODO: rework getUsage(OutputStream) to work with messages framework
        return LocalizableMessage.raw(getUsage());
    }

    /** Prints the version. */
    void printVersion() {
        versionPresent = true;
        usageOrVersionDisplayed = true;
        versionHandler.printVersion();
    }

    /**
     * Returns whether the usage argument was provided or not. This method
     * should be called after a call to parseArguments.
     *
     * @return <CODE>true</CODE> if the usage argument was provided and
     *         <CODE>false</CODE> otherwise.
     */
    public boolean isUsageArgumentPresent() {
        return usageArgument != null && usageArgument.isPresent();
    }

    /**
     * Returns whether the version argument was provided or not. This method
     * should be called after a call to parseArguments.
     *
     * @return <CODE>true</CODE> if the version argument was provided and
     *         <CODE>false</CODE> otherwise.
     */
    public boolean isVersionArgumentPresent() {
        return versionPresent;
    }

    /**
     * Indicates whether subcommand names and long argument strings should be treated in a case-sensitive manner.
     *
     * @return <CODE>true</CODE> if subcommand names and long argument strings should be treated in a case-sensitive
     *         manner, or <CODE>false</CODE> if they should not.
     */
    boolean longArgumentsCaseSensitive() {
        return longArgumentsCaseSensitive;
    }

    /**
     * Parses the provided set of arguments and updates the information
     * associated with this parser accordingly.
     *
     * @param rawArguments
     *            The raw set of arguments to parse.
     * @throws ArgumentException
     *             If a problem was encountered while parsing the provided
     *             arguments.
     */
    public void parseArguments(final String[] rawArguments) throws ArgumentException {
        parseArguments(rawArguments, null);
    }

    /**
     * Parses the provided set of arguments and updates the information
     * associated with this parser accordingly. Default values for unspecified
     * arguments may be read from the specified properties if any are provided.
     *
     * @param rawArguments
     *            The set of raw arguments to parse.
     * @param argumentProperties
     *            A set of properties that may be used to provide default values
     *            for arguments not included in the given raw arguments.
     * @throws ArgumentException
     *             If a problem was encountered while parsing the provided
     *             arguments.
     */
    public void parseArguments(final String[] rawArguments, Properties argumentProperties)
            throws ArgumentException {
        this.rawArguments = rawArguments;

        boolean inTrailingArgs = false;

        final int numArguments = rawArguments.length;
        for (int i = 0; i < numArguments; i++) {
            final String arg = rawArguments[i];

            if (inTrailingArgs) {
                trailingArguments.add(arg);
                if (maxTrailingArguments > 0 && trailingArguments.size() > maxTrailingArguments) {
                    final LocalizableMessage message =
                            ERR_ARGPARSER_TOO_MANY_TRAILING_ARGS.get(maxTrailingArguments);
                    throw new ArgumentException(message);
                }

                continue;
            }

            if (arg.equals("--")) {
                // This is a special indicator that we have reached the end of
                // the named arguments and that everything that follows after
                // this should be considered trailing arguments.
                inTrailingArgs = true;
            } else if (arg.startsWith("--")) {
                // This indicates that we are using the long name to reference
                // the argument. It may be in any of the following forms:
                // --name
                // --name value
                // --name=value

                String argName = arg.substring(2);
                String argValue = null;
                final int equalPos = argName.indexOf('=');
                if (equalPos < 0) {
                    // This is fine. The value is not part of the argument name token.
                } else if (equalPos == 0) {
                    // The argument starts with "--=", which is not acceptable.
                    throw new ArgumentException(ERR_ARGPARSER_LONG_ARG_WITHOUT_NAME.get(arg));
                } else {
                    // The argument is in the form --name=value, so parse them both out.
                    argValue = argName.substring(equalPos + 1);
                    argName = argName.substring(0, equalPos);
                }

                // If we're not case-sensitive, then convert the name to lowercase.
                final String origArgName = argName;
                if (!longArgumentsCaseSensitive) {
                    argName = toLowerCase(argName);
                }

                // Get the argument with the specified name.
                final Argument a = longIDMap.get(argName);
                if (a == null) {
                    if (OPTION_LONG_HELP.equals(argName)) {
                        // "--help" will always be interpreted as requesting usage information.
                        writeToUsageOutputStream(getUsage());
                        return;
                    } else if (OPTION_LONG_PRODUCT_VERSION.equals(argName)) {
                        // "--version" will always be interpreted as requesting version information.
                        printVersion();
                        return;
                    } else {
                        // There is no such argument registered.
                        throw new ArgumentException(
                                ERR_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID.get(origArgName));
                    }
                } else {
                    a.setPresent(true);

                    // If this is the usage argument, then immediately stop and
                    // print usage information.
                    if (isUsageArgument(a)) {
                        writeToUsageOutputStream(getUsage());
                        return;
                    }
                }

                // See if the argument takes a value. If so, then make sure one
                // was provided. If not, then make sure none was provided.
                if (a.needsValue()) {
                    if (argValue == null) {
                        if ((i + 1) == numArguments) {
                            throw new ArgumentException(
                                    ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get(origArgName));
                        }

                        argValue = rawArguments[++i];
                    }

                    final LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
                    if (!a.valueIsAcceptable(argValue, invalidReason)) {
                        throw new ArgumentException(
                                ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.get(argValue,
                                        origArgName, invalidReason));
                    }

                    // If the argument already has a value, then make sure it is
                    // acceptable to have more than one.
                    if (a.hasValue() && !a.isMultiValued()) {
                        throw new ArgumentException(ERR_ARGPARSER_NOT_MULTIVALUED_FOR_LONG_ID.get(origArgName));
                    }

                    a.addValue(argValue);
                } else if (argValue != null) {
                    throw new ArgumentException(
                            ERR_ARGPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE.get(origArgName));
                }
            } else if (arg.startsWith("-")) {
                // This indicates that we are using the 1-character name to
                // reference the argument. It may be in any of the following
                // forms:
                // -n
                // -nvalue
                // -n value
                if (arg.equals("-")) {
                    throw new ArgumentException(ERR_ARGPARSER_INVALID_DASH_AS_ARGUMENT.get());
                }

                final char argCharacter = arg.charAt(1);
                String argValue;
                if (arg.length() > 2) {
                    argValue = arg.substring(2);
                } else {
                    argValue = null;
                }

                // Get the argument with the specified short ID.
                final Argument a = shortIDMap.get(argCharacter);
                if (a == null) {
                    if (argCharacter == '?') {
                        writeToUsageOutputStream(getUsage());
                        return;
                    } else if (versionHandler != null && argCharacter == OPTION_SHORT_PRODUCT_VERSION
                            && !shortIDMap.containsKey(OPTION_SHORT_PRODUCT_VERSION)) {
                        printVersion();
                        return;
                    } else {
                        // There is no such argument registered.
                        throw new ArgumentException(ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID.get(argCharacter));
                    }
                } else {
                    a.setPresent(true);

                    // If this is the usage argument, then immediately stop and
                    // print usage information.
                    if (isUsageArgument(a)) {
                        writeToUsageOutputStream(getUsage());
                        return;
                    }
                }

                // See if the argument takes a value. If so, then make sure one
                // was provided. If not, then make sure none was provided.
                if (a.needsValue()) {
                    if (argValue == null) {
                        if ((i + 1) == numArguments) {
                            throw new ArgumentException(
                                    ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.get(argCharacter));
                        }

                        argValue = rawArguments[++i];
                    }

                    final LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
                    if (!a.valueIsAcceptable(argValue, invalidReason)) {
                        throw new ArgumentException(ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID.get(
                                argValue, argCharacter, invalidReason));
                    }

                    // If the argument already has a value, then make sure it is
                    // acceptable to have more than one.
                    if (a.hasValue() && !a.isMultiValued()) {
                        throw new ArgumentException(ERR_ARGPARSER_NOT_MULTIVALUED_FOR_SHORT_ID.get(argCharacter));
                    }

                    a.addValue(argValue);
                } else if (argValue != null) {
                    // If we've gotten here, then it means that we're in a scenario like
                    // "-abc" where "a" is a valid argument that doesn't take a value.
                    // However, this could still be valid if all remaining characters
                    // in the value are also valid argument characters that don't take values.
                    final int valueLength = argValue.length();
                    for (int j = 0; j < valueLength; j++) {
                        final char c = argValue.charAt(j);
                        final Argument b = shortIDMap.get(c);
                        if (b == null) {
                            // There is no such argument registered.
                            throw new ArgumentException(
                                    ERR_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID.get(argCharacter));
                        } else if (b.needsValue()) {
                            // This means we're in a scenario like "-abc"
                            // where b is a valid argument that takes a value.
                            // We don't support that.
                            throw new ArgumentException(
                                    ERR_ARGPARSER_CANT_MIX_ARGS_WITH_VALUES.get(argCharacter, argValue, c));
                        } else {
                            b.setPresent(true);

                            // If this is the usage argument,
                            // then immediately stop and print usage information.
                            if (isUsageArgument(b)) {
                                writeToUsageOutputStream(getUsage());
                                return;
                            }
                        }
                    }
                }
            } else if (allowsTrailingArguments) {
                // It doesn't start with a dash, so it must be a trailing
                // argument if that is acceptable.
                inTrailingArgs = true;
                trailingArguments.add(arg);
            } else {
                // It doesn't start with a dash and we don't allow trailing
                // arguments, so this is illegal.
                throw new ArgumentException(ERR_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT.get(arg));
            }
        }

        // If we allow trailing arguments and there is a minimum number,
        // then make sure at least that many were provided.
        if (allowsTrailingArguments
                && minTrailingArguments > 0
                && trailingArguments.size() < minTrailingArguments) {
            throw new ArgumentException(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(minTrailingArguments));
        }

        // If we don't have the argumentProperties, try to load a properties file
        if (argumentProperties == null) {
            argumentProperties = checkExternalProperties();
        }

        // Iterate through all of the arguments.
        // For any that were not provided on the command line,
        // see if there is an alternate default that can be used.
        // For cases where there is not, see that argument is required.
        normalizeArguments(argumentProperties, argumentList);
    }

    /**
     * Parses the provided set of arguments and updates the information
     * associated with this parser accordingly. Default values for unspecified
     * arguments may be read from the specified properties file.
     *
     * @param rawArguments
     *            The set of raw arguments to parse.
     * @param propertiesFile
     *            The path to the properties file to use to obtain default
     *            values for unspecified properties.
     * @param requirePropertiesFile
     *            Indicates whether the parsing should fail if the provided
     *            properties file does not exist or is not accessible.
     * @throws ArgumentException
     *             If a problem was encountered while parsing the provided
     *             arguments or interacting with the properties file.
     */
    public void parseArguments(final String[] rawArguments, final String propertiesFile,
            final boolean requirePropertiesFile) throws ArgumentException {
        this.rawArguments = rawArguments;

        Properties argumentProperties = null;

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(propertiesFile);
            final Properties p = new Properties();
            p.load(fis);
            argumentProperties = p;
        } catch (final Exception e) {
            if (requirePropertiesFile) {
                final LocalizableMessage message =
                        ERR_ARGPARSER_CANNOT_READ_PROPERTIES_FILE.get(propertiesFile, getExceptionMessage(e));
                throw new ArgumentException(message, e);
            }
        } finally {
            Utils.closeSilently(fis);
        }

        parseArguments(rawArguments, argumentProperties);
    }

    /**
     * Indicates whether or not argument group description headers should be
     * printed.
     *
     * @return boolean where true means print the descriptions
     */
    boolean printUsageGroupHeaders() {
        // If there is only a single group then we won't print them.
        int groupsContainingArgs = 0;
        for (final ArgumentGroup argGroup : argumentGroups) {
            if (argGroup.containsNonHiddenArguments()) {
                groupsContainingArgs++;
            }
        }
        return groupsContainingArgs > 1;
    }

    /**
     * Sets the usage group description for the default argument group.
     *
     * @param description
     *            for the default group
     */
    void setDefaultArgumentGroupDescription(final LocalizableMessage description) {
        this.defaultArgGroup.setDescription(description);
    }

    /**
     * Sets the provided argument which will be used to identify the file
     * properties.
     *
     * @param argument
     *            The argument which will be used to identify the file
     *            properties.
     */
    public void setFilePropertiesArgument(final StringArgument argument) {
        filePropertiesPathArgument = argument;
    }

    /**
     * Sets the usage group description for the general argument group.
     *
     * @param description
     *            for the general group
     */
    void setGeneralArgumentGroupDescription(final LocalizableMessage description) {
        this.generalArgGroup.setDescription(description);
    }

    /**
     * Sets the usage group description for the input/output argument group.
     *
     * @param description
     *            for the input/output group
     */
    void setInputOutputArgumentGroupDescription(final LocalizableMessage description) {
        this.ioArgGroup.setDescription(description);
    }

    /**
     * Sets the usage group description for the LDAP argument group.
     *
     * @param description
     *            for the LDAP group
     */
    void setLdapArgumentGroupDescription(final LocalizableMessage description) {
        this.ldapArgGroup.setDescription(description);
    }

    /**
     * Sets the provided argument which will be used to identify the file
     * properties.
     *
     * @param argument
     *            The argument which will be used to indicate if we have to look
     *            for properties file.
     */
    public void setNoPropertiesFileArgument(final BooleanArgument argument) {
        noPropertiesFileArgument = argument;
    }

    /**
     * Sets the raw set of arguments.
     *
     * @param rawArguments the raw set of arguments to set
     */
    void setRawArguments(String[] rawArguments) {
        this.rawArguments = rawArguments;
    }

    /**
     * Sets the provided argument as one which will automatically trigger the
     * output of usage information if it is provided on the command line and no
     * further argument validation will be performed. Note that the caller will
     * still need to add this argument to the parser with the
     * <CODE>addArgument</CODE> method, and the argument should not be required
     * and should not take a value. Also, the caller will still need to check
     * for the presence of the usage argument after calling
     * <CODE>parseArguments</CODE> to know that no further processing will be
     * required.
     *
     * @param argument
     *            The argument whose presence should automatically trigger the
     *            display of usage information.
     */
    public void setUsageArgument(final Argument argument) {
        usageArgument = argument;
        usageOutputStream = System.out;
    }

    /**
     * Sets the provided argument as one which will automatically trigger the
     * output of usage information if it is provided on the command line and no
     * further argument validation will be performed. Note that the caller will
     * still need to add this argument to the parser with the
     * <CODE>addArgument</CODE> method, and the argument should not be required
     * and should not take a value. Also, the caller will still need to check
     * for the presence of the usage argument after calling
     * <CODE>parseArguments</CODE> to know that no further processing will be
     * required.
     *
     * @param argument
     *            The argument whose presence should automatically trigger the
     *            display of usage information.
     * @param outputStream
     *            The output stream to which the usage information should be
     *            written.
     */
    public void setUsageArgument(final Argument argument, final OutputStream outputStream) {
        usageArgument = argument;
        usageOutputStream = outputStream;
    }

    /**
     * Sets whether the usage or version displayed.
     *
     * @param usageOrVersionDisplayed the usageOrVersionDisplayed to set
     */
    public void setUsageOrVersionDisplayed(boolean usageOrVersionDisplayed) {
        this.usageOrVersionDisplayed = usageOrVersionDisplayed;
    }

    /**
     * Sets the version handler which will be used to display the product version.
     *
     * @param handler
     *            The version handler.
     */
    public void setVersionHandler(final VersionHandler handler) {
        versionHandler = handler;
    }

    /**
     * Indicates whether the version or the usage information has been displayed
     * to the end user either by an explicit argument like "-H" or "--help", or
     * by a built-in argument like "-?".
     *
     * @return {@code true} if the usage information has been displayed, or
     *         {@code false} if not.
     */
    public boolean usageOrVersionDisplayed() {
        return usageOrVersionDisplayed;
    }

    /**
     * Get the absolute path of the properties file.
     *
     * @param directory
     *            The location in which we should look for properties file
     * @return The absolute path of the properties file or null
     */
    private String findPropertiesFile(final String directory) {
        // Look for the tools properties file
        final File f =
                new File(directory, DEFAULT_OPENDJ_PROPERTIES_FILE_NAME
                        + DEFAULT_OPENDJ_PROPERTIES_FILE_EXTENSION);
        if (f.exists() && f.canRead()) {
            return f.getAbsolutePath();
        }
        return null;
    }

    private void initGroups() {
        this.argumentGroups.add(defaultArgGroup);
        this.argumentGroups.add(ldapArgGroup);
        this.argumentGroups.add(generalArgGroup);
        this.argumentGroups.add(ioArgGroup);

        try {
            versionArgument = getVersionArgument(true);
            // JNR: why not call addGeneralArgument(versionArgument) here?
            this.generalArgGroup.addArgument(versionArgument);
        } catch (final ArgumentException e) {
            // ignore
        }
    }

    private boolean isGeneralArgument(final Argument arg) {
        if (arg != null) {
            final String longId = arg.getLongIdentifier();
            return OPTION_LONG_HELP.equals(longId) || OPTION_LONG_PRODUCT_VERSION.equals(longId);
        }
        return false;
    }

    private boolean isInputOutputArgument(final Argument arg) {
        if (arg != null) {
            final String longId = arg.getLongIdentifier();
            return OPTION_LONG_VERBOSE.equals(longId)
                            || OPTION_LONG_QUIET.equals(longId)
                            || OPTION_LONG_NO_PROMPT.equals(longId)
                            || OPTION_LONG_PROP_FILE_PATH.equals(longId)
                            || OPTION_LONG_NO_PROP_FILE.equals(longId)
                            || OPTION_LONG_SCRIPT_FRIENDLY.equals(longId)
                            || OPTION_LONG_DONT_WRAP.equals(longId)
                            || OPTION_LONG_ENCODING.equals(longId)
                            || OPTION_LONG_BATCH_FILE_PATH.equals(longId);
        }
        return false;
    }

    private boolean isLdapConnectionArgument(final Argument arg) {
        if (arg != null) {
            final String longId = arg.getLongIdentifier();
            return OPTION_LONG_USE_SSL.equals(longId)
                            || OPTION_LONG_START_TLS.equals(longId)
                            || OPTION_LONG_HOST.equals(longId)
                            || OPTION_LONG_PORT.equals(longId)
                            || OPTION_LONG_BINDDN.equals(longId)
                            || OPTION_LONG_BINDPWD.equals(longId)
                            || OPTION_LONG_BINDPWD_FILE.equals(longId)
                            || OPTION_LONG_SASLOPTION.equals(longId)
                            || OPTION_LONG_TRUSTALL.equals(longId)
                            || OPTION_LONG_TRUSTSTOREPATH.equals(longId)
                            || OPTION_LONG_TRUSTSTORE_PWD.equals(longId)
                            || OPTION_LONG_TRUSTSTORE_PWD_FILE.equals(longId)
                            || OPTION_LONG_KEYSTOREPATH.equals(longId)
                            || OPTION_LONG_KEYSTORE_PWD.equals(longId)
                            || OPTION_LONG_KEYSTORE_PWD_FILE.equals(longId)
                            || OPTION_LONG_CERT_NICKNAME.equals(longId)
                            || OPTION_LONG_REFERENCED_HOST_NAME.equals(longId)
                            || OPTION_LONG_ADMIN_UID.equals(longId)
                            || OPTION_LONG_REPORT_AUTHZ_ID.equals(longId)
                            || OPTION_LONG_USE_PW_POLICY_CTL.equals(longId)
                            || OPTION_LONG_USE_SASL_EXTERNAL.equals(longId)
                            || OPTION_LONG_PROTOCOL_VERSION.equals(longId);
        }
        return false;
    }

    /**
     * Appends argument usage information to the provided buffer.
     *
     * @param a
     *            The argument to handle.
     * @param buffer
     *            The buffer to which the usage information should be appended.
     */
    private void printArgumentUsage(final Argument a, final StringBuilder buffer) {
        printLineForShortLongArgument(a, buffer);

        // Write one or more lines with the description of the argument.
        // We will indent the description five characters and try our best to wrap
        // at or before column 79 so it will be friendly to 80-column displays.
        final int indentLength = INDENT.length();
        buffer.append(wrapText(a.getDescription(), MAX_LINE_WIDTH, indentLength));
        buffer.append(EOL);

        if (a.needsValue() && a.getDefaultValue() != null && a.getDefaultValue().length() > 0) {
            buffer.append(INDENT);
            buffer.append(INFO_ARGPARSER_USAGE_DEFAULT_VALUE.get(a.getDefaultValue()));
            buffer.append(EOL);
        }
    }

    /**
     * Appends a line with the short and/or long identifiers that may be used for the argument to the provided string
     * builder.
     *
     * @param a the argument for which to print a line
     * @param buffer the string builder where to append the line
     */
    void printLineForShortLongArgument(final Argument a, final StringBuilder buffer) {
        final Character shortID = a.getShortIdentifier();
        final String longID = a.getLongIdentifier();
        if (shortID != null) {
            if (isUsageArgument(a)) {
                buffer.append("-?, ");
            }

            buffer.append("-");
            buffer.append(shortID.charValue());

            if (a.needsValue() && longID == null) {
                buffer.append(" ");
                buffer.append(a.getValuePlaceholder());
            }

            if (longID != null) {
                final StringBuilder newBuffer = new StringBuilder();
                newBuffer.append(", --");
                newBuffer.append(longID);

                if (a.needsValue()) {
                    newBuffer.append(" ");
                    newBuffer.append(a.getValuePlaceholder());
                }

                final int currentLength = buffer.length();
                final int lineLength = (buffer.length() - currentLength) + newBuffer.length();
                if (lineLength > MAX_LINE_WIDTH) {
                    buffer.append(EOL);
                }
                buffer.append(newBuffer);
            }

            buffer.append(EOL);
        } else if (longID != null) {
            if (isUsageArgument(a)) {
                buffer.append("-?, ");
            }
            buffer.append("--");
            buffer.append(longID);

            if (a.needsValue()) {
                buffer.append(" ");
                buffer.append(a.getValuePlaceholder());
            }

            buffer.append(EOL);
        }
    }

    void normalizeArguments(final Properties argumentProperties, final List<Argument> arguments)
            throws ArgumentException {
        for (final Argument a : arguments) {
            if (!a.isPresent()
                    // See if there is a value in the properties that can be used
                    && argumentProperties != null && a.getPropertyName() != null) {
                final String value = argumentProperties.getProperty(a.getPropertyName().toLowerCase());
                final LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
                if (value != null) {
                    boolean addValue = (a instanceof BooleanArgument) ? true
                            : a.valueIsAcceptable(value, invalidReason);
                    if (addValue) {
                        a.addValue(value);
                        if (a.needsValue()) {
                            a.setPresent(true);
                        }
                        a.setValueSetByProperty(true);
                    }
                }
            }

            if (!a.isPresent() && a.needsValue()) {
                // See if the argument defines a default.
                if (a.getDefaultValue() != null) {
                    a.addValue(a.getDefaultValue());
                }

                // If there is still no value and the argument is required, then that's a problem.
                if (!a.hasValue() && a.isRequired()) {
                    throw new ArgumentException(ERR_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG.get(a.getName()));
                }
            }
        }
    }

    /**
     * Displays the provided message on the provided stream followed by a help usage reference.
     *
     * @param printStream
     *            The stream to print error message and help reference message.
     * @param message
     *            The error message to print.
     */
    public void displayMessageAndUsageReference(final PrintStream printStream, final LocalizableMessage message) {
        printWrappedText(printStream, message);
        printStream.println();
        printWrappedText(printStream, getHelpUsageReference());
    }

    /**
     * Retrieves a string describing how the user can get more help.
     *
     * @return A string describing how the user can get more help.
     */
    public LocalizableMessage getHelpUsageReference() {
        setUsageOrVersionDisplayed(true);

        LocalizableMessageBuilder buffer = new LocalizableMessageBuilder();
        buffer.append(INFO_GLOBAL_HELP_REFERENCE.get(getScriptNameOrJava()));
        buffer.append(EOL);
        return buffer.toMessage();
    }

    /**
     * Get the password which has to be used for the command without prompting the user. If no password was specified,
     * return null.
     *
     * @param clearArg
     *            The password StringArgument argument.
     * @param fileArg
     *            The password FileBased argument.
     * @return The password stored into the specified file on by the command line argument, or null it if not specified.
     */
    public static String getBindPassword(StringArgument clearArg, FileBasedArgument fileArg) {
        if (clearArg.isPresent()) {
            return clearArg.getValue();
        } else if (fileArg.isPresent()) {
            return fileArg.getValue();
        }
        return null;
    }
}
