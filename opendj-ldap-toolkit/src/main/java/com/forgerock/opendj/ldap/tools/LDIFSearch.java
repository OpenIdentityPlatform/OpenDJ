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
 *      Copyright 2012-2013 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.filterExitCode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * This utility can be used to perform search operations against data in an LDIF
 * file.
 */
public final class LDIFSearch extends ConsoleApplication {

    /**
     * The main method for LDIFSearch tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new LDIFSearch().run(args);
        System.exit(filterExitCode(retCode));
    }

    private LDIFSearch() {
        // Nothing to do.
    }

    private int run(final String[] args) {
        // Create the command-line argument parser for use with this
        // program.

        final LocalizableMessage toolDescription = INFO_LDIFSEARCH_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(LDIFSearch.class.getName(), toolDescription, false, true, 1, 0,
                        "source [filter] [attributes ...]");

        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        final BooleanArgument typesOnly;
        final IntegerArgument timeLimit;
        final StringArgument filename;
        final StringArgument baseDN;
        final MultiChoiceArgument<SearchScope> searchScope;
        final IntegerArgument sizeLimit;

        try {
            outputFilename =
                    new StringArgument("outputFilename", OPTION_SHORT_OUTPUT_LDIF_FILENAME,
                            OPTION_LONG_OUTPUT_LDIF_FILENAME, false, false, true,
                            INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get(), "stdout", null,
                            INFO_LDIFSEARCH_DESCRIPTION_OUTPUT_FILENAME
                                    .get(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()));
            argParser.addArgument(outputFilename);

            baseDN =
                    new StringArgument("baseDN", OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN, true,
                            false, true, INFO_BASEDN_PLACEHOLDER.get(), null, null,
                            INFO_SEARCH_DESCRIPTION_BASEDN.get());
            baseDN.setPropertyName(OPTION_LONG_BASEDN);
            argParser.addArgument(baseDN);

            searchScope =
                    new MultiChoiceArgument<SearchScope>("searchScope", 's', "searchScope", false,
                            true, INFO_SEARCH_SCOPE_PLACEHOLDER.get(), SearchScope.values(), false,
                            INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get());
            searchScope.setPropertyName("searchScope");
            searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
            argParser.addArgument(searchScope);

            filename =
                    new StringArgument("filename", OPTION_SHORT_FILENAME, OPTION_LONG_FILENAME,
                            false, false, true, INFO_FILE_PLACEHOLDER.get(), null, null,
                            INFO_SEARCH_DESCRIPTION_FILENAME.get());
            searchScope.setPropertyName(OPTION_LONG_FILENAME);
            argParser.addArgument(filename);

            typesOnly =
                    new BooleanArgument("typesOnly", 'A', "typesOnly", INFO_DESCRIPTION_TYPES_ONLY
                            .get());
            typesOnly.setPropertyName("typesOnly");
            argParser.addArgument(typesOnly);

            sizeLimit =
                    new IntegerArgument("sizeLimit", 'z', "sizeLimit", false, false, true,
                            INFO_SIZE_LIMIT_PLACEHOLDER.get(), 0, null,
                            INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get());
            sizeLimit.setPropertyName("sizeLimit");
            argParser.addArgument(sizeLimit);

            timeLimit =
                    new IntegerArgument("timeLimit", 'l', "timeLimit", false, false, true,
                            INFO_TIME_LIMIT_PLACEHOLDER.get(), 0, null,
                            INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get());
            timeLimit.setPropertyName("timeLimit");
            argParser.addArgument(timeLimit);

            showUsage =
                    new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                            INFO_DESCRIPTION_SHOWUSAGE.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information,
            // then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return ResultCode.SUCCESS.intValue();
            }
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<Filter> filters = new LinkedList<Filter>();
        final List<String> attributes = new LinkedList<String>();
        final List<String> trailingArguments = argParser.getTrailingArguments();
        if (trailingArguments.size() > 1) {
            final List<String> filterAndAttributeStrings =
                    trailingArguments.subList(1, trailingArguments.size());

            // the list of trailing arguments should be structured as follow:
            // - If a filter file is present, trailing arguments are
            // considered as attributes
            // - If filter file is not present, the first trailing argument is
            // considered the filter, the other as attributes.
            if (!filename.isPresent()) {
                final String filterString = filterAndAttributeStrings.remove(0);
                try {
                    filters.add(Filter.valueOf(filterString));
                } catch (final LocalizedIllegalArgumentException e) {
                    println(e.getMessageObject());
                    return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
                }
            }
            // The rest are attributes
            for (final String s : filterAndAttributeStrings) {
                attributes.add(s);
            }
        }

        if (filename.isPresent()) {
            // Read the filter strings.
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(filename.getValue()));
                String line = null;

                while ((line = in.readLine()) != null) {
                    if (line.trim().equals("")) {
                        // ignore empty lines.
                        continue;
                    }
                    final Filter ldapFilter = Filter.valueOf(line);
                    filters.add(ldapFilter);
                }
            } catch (final LocalizedIllegalArgumentException e) {
                println(e.getMessageObject());
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            } catch (final IOException e) {
                println(LocalizableMessage.raw(e.toString()));
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            } finally {
                closeIfNotNull(in);
            }
        }

        if (filters.isEmpty()) {
            println(ERR_SEARCH_NO_FILTERS.get());
            println(argParser.getUsageMessage());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final SearchRequest search;
        try {
            final SearchScope scope = searchScope.getTypedValue();
            search =
                    Requests.newSearchRequest(DN.valueOf(baseDN.getValue()), scope, filters.get(0),
                            attributes.toArray(new String[attributes.size()])).setTypesOnly(
                            typesOnly.isPresent()).setTimeLimit(timeLimit.getIntValue())
                            .setSizeLimit(sizeLimit.getIntValue());
        } catch (final ArgumentException e) {
            println(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        } catch (final LocalizedIllegalArgumentException e) {
            println(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        InputStream sourceInputStream = null;
        OutputStream outputStream = null;
        LDIFEntryReader sourceReader = null;
        LDIFEntryWriter outputWriter = null;

        try {
            // First source file.
            if (!trailingArguments.get(0).equals("-")) {
                try {
                    sourceInputStream = new FileInputStream(trailingArguments.get(0));
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(trailingArguments.get(0), e
                                    .getLocalizedMessage());
                    println(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Output file.
            if (outputFilename.isPresent() && !outputFilename.getValue().equals("-")) {
                try {
                    outputStream = new FileOutputStream(outputFilename.getValue());
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_WRITE.get(outputFilename.getValue(), e
                                    .getLocalizedMessage());
                    println(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Default to stdin/stdout for all streams if not specified.
            if (sourceInputStream == null) {
                // Command line parameter was "-".
                sourceInputStream = System.in;
            }

            if (outputStream == null) {
                outputStream = System.out;
            }

            // Perform the search.
            sourceReader = new LDIFEntryReader(sourceInputStream);
            outputWriter = new LDIFEntryWriter(outputStream);
            LDIF.copyTo(LDIF.search(sourceReader, search), outputWriter);
        } catch (final IOException e) {
            if (e instanceof LocalizableException) {
                println(ERR_LDIFSEARCH_FAILED.get(((LocalizableException) e).getMessageObject()));
            } else {
                println(ERR_LDIFSEARCH_FAILED.get(e.getLocalizedMessage()));
            }
            return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
        } finally {
            closeIfNotNull(sourceReader, outputWriter);
            closeIfNotNull(sourceInputStream, outputStream);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
