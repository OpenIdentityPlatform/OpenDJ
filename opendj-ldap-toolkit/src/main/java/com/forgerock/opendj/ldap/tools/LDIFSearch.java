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
 * Copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolException;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static com.forgerock.opendj.ldap.tools.Utils.computeWrapColumn;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolInputStream;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolOutputStream;
import static com.forgerock.opendj.ldap.tools.Utils.parseArguments;
import static com.forgerock.opendj.ldap.tools.Utils.readFilterFromString;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;
import org.forgerock.util.annotations.VisibleForTesting;

/** This utility can be used to perform search operations against data in an LDIF file. */
public final class LDIFSearch extends ToolConsoleApplication {

    /**
     * The main method for ldifsearch tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDIFSearch(System.out, System.err), args);
    }

    /**
     * This method should be used to run this tool programmatically.
     * Output and errors will be printed on provided {@link PrintStream}.
     *
     * @param out
     *            The {@link PrintStream} to use to write tool output.
     * @param err
     *            The {@link PrintStream} to use to write tool errors.
     * @param args
     *            The arguments to use with this tool.
     * @return The code returned by the tool
     */
    public static int run(final PrintStream out, final PrintStream err, final String... args) {
        return runTool(new LDIFSearch(out, err), args);
    }

    @VisibleForTesting
    LDIFSearch(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        final ArgumentParser argParser = LDAPToolArgumentParser.builder(LDIFSearch.class.getName())
                .toolDescription(INFO_LDIFSEARCH_TOOL_DESCRIPTION.get())
                .trailingArgumentsUnbounded(2, "source filter [attributes ...]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDIFSEARCH.get());

        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        final BooleanArgument typesOnly;
        final IntegerArgument timeLimit;
        final StringArgument baseDN;
        final MultiChoiceArgument<SearchScope> searchScope;
        final IntegerArgument sizeLimit;
        final IntegerArgument wrapColumn;
        try {
            outputFilename =
                    StringArgument.builder(OPTION_LONG_OUTPUT_LDIF_FILENAME)
                            .shortIdentifier(OPTION_SHORT_OUTPUT_LDIF_FILENAME)
                            .description(INFO_LDIFSEARCH_DESCRIPTION_OUTPUT_FILENAME.get(
                                    INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()))
                            .defaultValue("stdout")
                            .valuePlaceholder(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            baseDN =
                    StringArgument.builder(OPTION_LONG_BASEDN)
                            .shortIdentifier(OPTION_SHORT_BASEDN)
                            .description(INFO_LDIFSEARCH_DESCRIPTION_BASEDN.get())
                            .defaultValue("") // Search for rootDSE if no base DN is provided
                            .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            searchScope = searchScopeArgument();
            argParser.addArgument(searchScope);

            typesOnly =
                    BooleanArgument.builder("typesOnly")
                            .shortIdentifier('A')
                            .description(INFO_DESCRIPTION_TYPES_ONLY.get())
                            .buildAndAddToParser(argParser);
            sizeLimit =
                    IntegerArgument.builder("sizeLimit")
                            .shortIdentifier('z')
                            .description(INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_SIZE_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            timeLimit =
                    IntegerArgument.builder("timeLimit")
                            .shortIdentifier('l')
                            .description(INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_TIME_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            wrapColumn = wrapColumnArgument();
            argParser.addArgument(wrapColumn);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        parseArguments(argParser, getErrorStream(), args);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        final List<String> trailingArguments = argParser.getTrailingArguments();
        final Filter filter = readFilterFromString(trailingArguments.get(1));
        final List<String> attributes = new LinkedList<>();
        if (trailingArguments.size() > 2) {
            attributes.addAll(trailingArguments.subList(2, trailingArguments.size()));
        }

        final SearchRequest search;
        try {
            final SearchScope scope = searchScope.getTypedValue();
            search =
                    Requests.newSearchRequest(DN.valueOf(baseDN.getValue()), scope, filter,
                            attributes.toArray(new String[attributes.size()])).setTypesOnly(
                            typesOnly.isPresent()).setTimeLimit(timeLimit.getIntValue())
                            .setSizeLimit(sizeLimit.getIntValue());
        } catch (final ArgumentException | LocalizedIllegalArgumentException e) {
            throw newToolParamException(e, e.getMessageObject());
        }

        try (final LDIFEntryReader sourceReader =
                     new LDIFEntryReader(getLDIFToolInputStream(this, trailingArguments.get(0)));
             final LDIFEntryWriter outputWriter = new LDIFEntryWriter(getLDIFToolOutputStream(this, outputFilename))) {
            outputWriter.setWrapColumn(computeWrapColumn(wrapColumn));
            LDIF.copyTo(LDIF.search(sourceReader, search), outputWriter);
        } catch (final LdapException e) {
            throw newToolException(
                    e, e.getResult().getResultCode(), ERR_LDIFSEARCH_FAILED.get(e.getLocalizedMessage()));
        } catch (final IOException e) {
            throw newToolException(e, ResultCode.CLIENT_SIDE_LOCAL_ERROR, ERR_LDIFSEARCH_FAILED.get(
                    e instanceof LocalizableException ? ((LocalizableException) e).getMessageObject()
                                                      : e.getLocalizedMessage()));
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ae.getMessageObject());
        }

        return ResultCode.SUCCESS.intValue();
    }
}
