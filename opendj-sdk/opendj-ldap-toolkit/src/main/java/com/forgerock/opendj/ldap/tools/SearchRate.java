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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A load generation tool that can be used to load a Directory Server with
 * Search requests using one or more LDAP connections.
 */
public final class SearchRate extends ConsoleApplication {
    private final class SearchPerformanceRunner extends PerformanceRunner {
        private final class SearchStatsHandler extends UpdateStatsResultHandler<Result> implements
                SearchResultHandler {
            private SearchStatsHandler(final long startTime) {
                super(startTime);
            }

            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                entryRecentCount.getAndIncrement();
                return true;
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                return true;
            }
        }

        private final class SearchStatsThread extends StatsThread {
            private final String[] extraColumn = new String[1];

            private SearchStatsThread() {
                super("Entries/Srch");
            }

            @Override
            void resetStats() {
                super.resetStats();
                entryRecentCount.set(0);
            }

            @Override
            String[] getAdditionalColumns() {
                final int entryCount = entryRecentCount.getAndSet(0);
                extraColumn[0] = getDivisionResult(entryCount, intervalSuccessCount, 1, "0.0");
                return extraColumn;
            }
        }

        private final class SearchWorkerThread extends WorkerThread {
            private SearchRequest sr;
            private Object[] data;

            private SearchWorkerThread(final Connection connection,
                    final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public Promise<?, LdapException> performOperation(final Connection connection,
                    final DataSource[] dataSources, final long startTime) {
                if (sr == null) {
                    if (dataSources == null) {
                        sr = Requests.newSearchRequest(baseDN, scope, filter, attributes);
                    } else {
                        data = DataSource.generateData(dataSources, data);
                        sr =
                                Requests.newSearchRequest(String.format(baseDN, data), scope,
                                        String.format(filter, data), attributes);
                    }
                    sr.setDereferenceAliasesPolicy(dereferencesAliasesPolicy);
                } else if (dataSources != null) {
                    data = DataSource.generateData(dataSources, data);
                    sr.setFilter(String.format(filter, data));
                    sr.setName(String.format(baseDN, data));
                }

                final SearchStatsHandler handler = new SearchStatsHandler(startTime);
                incrementIterationCount();
                return connection.searchAsync(sr, handler).thenOnResult(handler).thenOnException(handler);
            }
        }

        private String filter;
        private String baseDN;
        private SearchScope scope;
        private DereferenceAliasesPolicy dereferencesAliasesPolicy;
        private String[] attributes;

        private SearchPerformanceRunner(final PerformanceRunnerOptions options)
                throws ArgumentException {
            super(options);
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection,
                final ConnectionFactory connectionFactory) {
            return new SearchWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread() {
            return new SearchStatsThread();
        }
    }

    /**
     * The main method for SearchRate tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */

    public static void main(final String[] args) {
        final int retCode = new SearchRate().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;

    private BooleanArgument scriptFriendly;

    private final AtomicInteger entryRecentCount = new AtomicInteger();

    private SearchRate() {
        // Nothing to do.
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isScriptFriendly() {
        return scriptFriendly.isPresent();
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    private int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_SEARCHRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(SearchRate.class.getName(), toolDescription, false, true, 1, 0,
                        "[filter format string] [attributes ...]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_SEARCHRATE.get());
        argParser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RATE_TOOLS.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        SearchPerformanceRunner runner;

        StringArgument baseDN;
        MultiChoiceArgument<SearchScope> searchScope;
        MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
        BooleanArgument showUsage;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;
        try {
            Utils.setDefaultPerfToolProperties();

            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            runner = new SearchPerformanceRunner(new PerformanceRunnerOptions(argParser, this));

            propertiesFileArgument = CommonArguments.getPropertiesFile();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = CommonArguments.getNoPropertiesFile();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            showUsage = CommonArguments.getShowUsage();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());

            baseDN =
                    new StringArgument("baseDN", OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN, true,
                            false, true, INFO_BASEDN_PLACEHOLDER.get(), null, null,
                            INFO_SEARCHRATE_TOOL_DESCRIPTION_BASEDN.get());
            baseDN.setPropertyName(OPTION_LONG_BASEDN);
            argParser.addArgument(baseDN);

            searchScope = CommonArguments.getSearchScope();
            argParser.addArgument(searchScope);

            dereferencePolicy =
                    new MultiChoiceArgument<>("derefpolicy", 'a',
                            "dereferencePolicy", false, true, INFO_DEREFERENCE_POLICE_PLACEHOLDER
                                    .get(), DereferenceAliasesPolicy.values(), false,
                            INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get());
            dereferencePolicy.setPropertyName("dereferencePolicy");
            dereferencePolicy.setDefaultValue(DereferenceAliasesPolicy.NEVER);
            argParser.addArgument(dereferencePolicy);

            verbose = CommonArguments.getVerbose();
            argParser.addArgument(verbose);

            scriptFriendly =
                    new BooleanArgument("scriptFriendly", 'S', "scriptFriendly",
                            INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
            scriptFriendly.setPropertyName("scriptFriendly");
            argParser.addArgument(scriptFriendly);
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
            errPrintln(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information,
            // then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getAuthenticatedConnectionFactory();
            runner.setBindRequest(connectionFactoryProvider.getBindRequest());
            runner.validate();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<String> attributes = new LinkedList<>();
        final ArrayList<String> filterAndAttributeStrings = argParser.getTrailingArguments();
        if (!filterAndAttributeStrings.isEmpty()) {
            /* The list of trailing arguments should be structured as follow:
             the first trailing argument is considered the filter, the other as attributes.*/
            runner.filter = filterAndAttributeStrings.remove(0);
            // The rest are attributes
            attributes.addAll(filterAndAttributeStrings);
        }
        runner.attributes = attributes.toArray(new String[attributes.size()]);
        runner.baseDN = baseDN.getValue();
        try {
            runner.scope = searchScope.getTypedValue();
            runner.dereferencesAliasesPolicy = dereferencePolicy.getTypedValue();
        } catch (final ArgumentException ex1) {
            errPrintln(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        try {
            /* Try it out to make sure the format string and data sources match. */
            final Object[] data = DataSource.generateData(runner.getDataSources(), null);
            String.format(runner.filter, data);
            String.format(runner.baseDN, data);
        } catch (final Exception ex1) {
            errPrintln(LocalizableMessage.raw("Error formatting filter or base DN: " + ex1));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        return runner.run(connectionFactory);
    }
}
