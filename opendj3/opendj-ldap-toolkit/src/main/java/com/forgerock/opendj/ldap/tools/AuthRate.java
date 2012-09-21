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
 *      Portions copyright 2011 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.filterExitCode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.ExternalSASLBindRequest;
import org.forgerock.opendj.ldap.requests.GSSAPISASLBindRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

import com.forgerock.opendj.util.RecursiveFutureResult;

/**
 * A load generation tool that can be used to load a Directory Server with Bind
 * requests using one or more LDAP connections.
 */
public final class AuthRate extends ConsoleApplication {
    private final class BindPerformanceRunner extends PerformanceRunner {
        private final class BindStatsThread extends StatsThread {
            private final String[] extraColumn;

            private BindStatsThread(final boolean extraFieldRequired) {
                super(extraFieldRequired ? new String[] { "bind time %" } : new String[0]);
                extraColumn = new String[extraFieldRequired ? 1 : 0];
            }

            @Override
            String[] getAdditionalColumns() {
                invalidCredRecentCount.set(0);
                if (extraColumn.length != 0) {
                    final long searchWaitTime = searchWaitRecentTime.getAndSet(0);
                    extraColumn[0] =
                            String.format("%.1f",
                                    ((float) (waitTime - searchWaitTime) / waitTime) * 100.0);
                }
                return extraColumn;
            }
        }

        private final class BindUpdateStatsResultHandler extends
                UpdateStatsResultHandler<BindResult> {
            private BindUpdateStatsResultHandler(final long startTime) {
                super(startTime);
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                super.handleErrorResult(error);

                if (error.getResult().getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                    invalidCredRecentCount.getAndIncrement();
                }
            }
        }

        private final class BindWorkerThread extends WorkerThread {
            private SearchRequest sr;
            private BindRequest br;
            private Object[] data;
            private final char[] invalidPassword = "invalid-password".toCharArray();

            private final ThreadLocal<Random> rng = new ThreadLocal<Random>() {

                @Override
                protected Random initialValue() {
                    return new Random();
                }

            };

            private BindWorkerThread(final Connection connection,
                    final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public FutureResult<?> performOperation(final Connection connection,
                    final DataSource[] dataSources, final long startTime) {
                if (dataSources != null) {
                    data = DataSource.generateData(dataSources, data);
                    if (data.length == dataSources.length) {
                        final Object[] newData = new Object[data.length + 1];
                        System.arraycopy(data, 0, newData, 0, data.length);
                        data = newData;
                    }
                }
                if (filter != null && baseDN != null) {
                    if (sr == null) {
                        if (dataSources == null) {
                            sr = Requests.newSearchRequest(baseDN, scope, filter, attributes);
                        } else {
                            sr =
                                    Requests.newSearchRequest(String.format(baseDN, data), scope,
                                            String.format(filter, data), attributes);
                        }
                        sr.setDereferenceAliasesPolicy(dereferencesAliasesPolicy);
                    } else if (dataSources != null) {
                        sr.setFilter(String.format(filter, data));
                        sr.setName(String.format(baseDN, data));
                    }

                    final RecursiveFutureResult<SearchResultEntry, BindResult> future =
                            new RecursiveFutureResult<SearchResultEntry, BindResult>(
                                    new BindUpdateStatsResultHandler(startTime)) {
                                @Override
                                protected FutureResult<? extends BindResult> chainResult(
                                        final SearchResultEntry innerResult,
                                        final ResultHandler<? super BindResult> resultHandler)
                                        throws ErrorResultException {
                                    searchWaitRecentTime.getAndAdd(System.nanoTime() - startTime);
                                    if (data == null) {
                                        data = new Object[1];
                                    }
                                    data[data.length - 1] = innerResult.getName().toString();
                                    return performBind(connection, data, resultHandler);
                                }
                            };
                    connection.searchSingleEntryAsync(sr, future);
                    return future;
                } else {
                    return performBind(connection, data,
                            new BindUpdateStatsResultHandler(startTime));
                }
            }

            private FutureResult<BindResult> performBind(final Connection connection,
                    final Object[] data, final ResultHandler<? super BindResult> handler) {
                final boolean useInvalidPassword;

                // Avoid rng if possible.
                switch (invalidCredPercent) {
                case 0:
                    useInvalidPassword = false;
                    break;
                case 100:
                    useInvalidPassword = true;
                    break;
                default:
                    final Random r = rng.get();
                    final int p = r.nextInt(100);
                    useInvalidPassword = (p < invalidCredPercent);
                    break;
                }

                if (bindRequest instanceof SimpleBindRequest) {
                    final SimpleBindRequest o = (SimpleBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfSimpleBindRequest(o);
                    }

                    final SimpleBindRequest sbr = (SimpleBindRequest) br;
                    if (data != null && o.getName() != null) {
                        sbr.setName(String.format(o.getName(), data));
                    }
                    if (useInvalidPassword) {
                        sbr.setPassword(invalidPassword);
                    } else {
                        sbr.setPassword(o.getPassword());
                    }
                } else if (bindRequest instanceof DigestMD5SASLBindRequest) {
                    final DigestMD5SASLBindRequest o = (DigestMD5SASLBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfDigestMD5SASLBindRequest(o);
                    }

                    final DigestMD5SASLBindRequest sbr = (DigestMD5SASLBindRequest) br;
                    if (data != null) {
                        if (o.getAuthenticationID() != null) {
                            sbr.setAuthenticationID(String.format(o.getAuthenticationID(), data));
                        }
                        if (o.getAuthorizationID() != null) {
                            sbr.setAuthorizationID(String.format(o.getAuthorizationID(), data));
                        }
                    }
                    if (useInvalidPassword) {
                        sbr.setPassword(invalidPassword);
                    } else {
                        sbr.setPassword(o.getPassword());
                    }
                } else if (bindRequest instanceof CRAMMD5SASLBindRequest) {
                    final CRAMMD5SASLBindRequest o = (CRAMMD5SASLBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfCRAMMD5SASLBindRequest(o);
                    }

                    final CRAMMD5SASLBindRequest sbr = (CRAMMD5SASLBindRequest) br;
                    if (data != null && o.getAuthenticationID() != null) {
                        sbr.setAuthenticationID(String.format(o.getAuthenticationID(), data));
                    }
                    if (useInvalidPassword) {
                        sbr.setPassword(invalidPassword);
                    } else {
                        sbr.setPassword(o.getPassword());
                    }
                } else if (bindRequest instanceof GSSAPISASLBindRequest) {
                    final GSSAPISASLBindRequest o = (GSSAPISASLBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfGSSAPISASLBindRequest(o);
                    }

                    final GSSAPISASLBindRequest sbr = (GSSAPISASLBindRequest) br;
                    if (data != null) {
                        if (o.getAuthenticationID() != null) {
                            sbr.setAuthenticationID(String.format(o.getAuthenticationID(), data));
                        }
                        if (o.getAuthorizationID() != null) {
                            sbr.setAuthorizationID(String.format(o.getAuthorizationID(), data));
                        }
                    }
                    if (useInvalidPassword) {
                        sbr.setPassword(invalidPassword);
                    } else {
                        sbr.setPassword(o.getPassword());
                    }
                } else if (bindRequest instanceof ExternalSASLBindRequest) {
                    final ExternalSASLBindRequest o = (ExternalSASLBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfExternalSASLBindRequest(o);
                    }

                    final ExternalSASLBindRequest sbr = (ExternalSASLBindRequest) br;
                    if (data != null && o.getAuthorizationID() != null) {
                        sbr.setAuthorizationID(String.format(o.getAuthorizationID(), data));
                    }
                } else if (bindRequest instanceof PlainSASLBindRequest) {
                    final PlainSASLBindRequest o = (PlainSASLBindRequest) bindRequest;
                    if (br == null) {
                        br = Requests.copyOfPlainSASLBindRequest(o);
                    }

                    final PlainSASLBindRequest sbr = (PlainSASLBindRequest) br;
                    if (data != null) {
                        if (o.getAuthenticationID() != null) {
                            sbr.setAuthenticationID(String.format(o.getAuthenticationID(), data));
                        }
                        if (o.getAuthorizationID() != null) {
                            sbr.setAuthorizationID(String.format(o.getAuthorizationID(), data));
                        }
                    }
                    if (useInvalidPassword) {
                        sbr.setPassword(invalidPassword);
                    } else {
                        sbr.setPassword(o.getPassword());
                    }
                }

                return connection.bindAsync(br, null, handler);
            }
        }

        private final AtomicLong searchWaitRecentTime = new AtomicLong();
        private final AtomicInteger invalidCredRecentCount = new AtomicInteger();
        private String filter;
        private String baseDN;
        private SearchScope scope;
        private DereferenceAliasesPolicy dereferencesAliasesPolicy;
        private String[] attributes;
        private BindRequest bindRequest;
        private int invalidCredPercent;

        private BindPerformanceRunner(final ArgumentParser argParser, final ConsoleApplication app)
                throws ArgumentException {
            super(argParser, app, true, true, true);
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection,
                final ConnectionFactory connectionFactory) {
            return new BindWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread() {
            return new BindStatsThread(filter != null && baseDN != null);
        }
    }

    /**
     * The main method for AuthRate tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */

    public static void main(final String[] args) {
        final int retCode = new AuthRate().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;
    private BooleanArgument scriptFriendly;

    private AuthRate() {
        // Nothing to do.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isScriptFriendly() {
        return scriptFriendly.isPresent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    private int run(final String[] args) {
        // Create the command-line argument parser for use with this
        // program.
        final LocalizableMessage toolDescription = INFO_AUTHRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(AuthRate.class.getName(), toolDescription, false, true, 0, 0,
                        "[filter format string] [attributes ...]");

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        BindPerformanceRunner runner;

        StringArgument baseDN;
        MultiChoiceArgument<SearchScope> searchScope;
        MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
        BooleanArgument showUsage;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;
        IntegerArgument invalidCredPercent;

        try {
            Utils.setDefaultPerfToolProperties();

            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            runner = new BindPerformanceRunner(argParser, this);

            propertiesFileArgument =
                    new StringArgument("propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH,
                            false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_PROP_FILE_PATH.get());
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument =
                    new BooleanArgument("noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
                            INFO_DESCRIPTION_NO_PROP_FILE.get());
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            showUsage =
                    new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                            INFO_DESCRIPTION_SHOWUSAGE.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());

            baseDN =
                    new StringArgument("baseDN", OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN, false,
                            false, true, INFO_BASEDN_PLACEHOLDER.get(), null, null,
                            INFO_SEARCHRATE_TOOL_DESCRIPTION_BASEDN.get());
            baseDN.setPropertyName(OPTION_LONG_BASEDN);
            argParser.addArgument(baseDN);

            searchScope =
                    new MultiChoiceArgument<SearchScope>("searchScope", 's', "searchScope", false,
                            true, INFO_SEARCH_SCOPE_PLACEHOLDER.get(), SearchScope.values(), false,
                            INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get());
            searchScope.setPropertyName("searchScope");
            searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
            argParser.addArgument(searchScope);

            dereferencePolicy =
                    new MultiChoiceArgument<DereferenceAliasesPolicy>("derefpolicy", 'a',
                            "dereferencePolicy", false, true, INFO_DEREFERENCE_POLICE_PLACEHOLDER
                                    .get(), DereferenceAliasesPolicy.values(), false,
                            INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get());
            dereferencePolicy.setPropertyName("dereferencePolicy");
            dereferencePolicy.setDefaultValue(DereferenceAliasesPolicy.NEVER);
            argParser.addArgument(dereferencePolicy);

            invalidCredPercent =
                    new IntegerArgument("invalidPassword", 'I', "invalidPassword", false, false,
                            true, LocalizableMessage.raw("{invalidPassword}"), 0, null, true, 0,
                            true, 100, LocalizableMessage
                                    .raw("Percent of bind operations with simulated "
                                            + "invalid password"));
            invalidCredPercent.setPropertyName("invalidPassword");
            argParser.addArgument(invalidCredPercent);

            verbose =
                    new BooleanArgument("verbose", 'v', "verbose", INFO_DESCRIPTION_VERBOSE.get());
            verbose.setPropertyName("verbose");
            argParser.addArgument(verbose);

            scriptFriendly =
                    new BooleanArgument("scriptFriendly", 'S', "scriptFriendly",
                            INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
            scriptFriendly.setPropertyName("scriptFriendly");
            argParser.addArgument(scriptFriendly);
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
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getConnectionFactory();
            runner.validate();

            runner.bindRequest = connectionFactoryProvider.getBindRequest();
            if (runner.bindRequest == null) {
                throw new ArgumentException(LocalizableMessage
                        .raw("Authentication information must be provided to use this tool"));
            }
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<String> attributes = new LinkedList<String>();
        final ArrayList<String> filterAndAttributeStrings = argParser.getTrailingArguments();
        if (filterAndAttributeStrings.size() > 0) {
            // the list of trailing arguments should be structured as follow:
            // the first trailing argument is considered the filter, the other
            // as attributes.
            runner.filter = filterAndAttributeStrings.remove(0);

            // The rest are attributes
            for (final String s : filterAndAttributeStrings) {
                attributes.add(s);
            }
        }
        runner.attributes = attributes.toArray(new String[attributes.size()]);
        runner.baseDN = baseDN.getValue();
        try {
            runner.scope = searchScope.getTypedValue();
            runner.dereferencesAliasesPolicy = dereferencePolicy.getTypedValue();
            runner.invalidCredPercent = invalidCredPercent.getIntValue();
        } catch (final ArgumentException ex1) {
            println(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Try it out to make sure the format string and data sources
        // match.
        final Object[] data = DataSource.generateData(runner.getDataSources(), null);
        try {
            if (runner.baseDN != null && runner.filter != null) {
                String.format(runner.filter, data);
                String.format(runner.baseDN, data);
            }
        } catch (final Exception ex1) {
            println(LocalizableMessage.raw("Error formatting filter or base DN: " + ex1.toString()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        return runner.run(connectionFactory);
    }
}
