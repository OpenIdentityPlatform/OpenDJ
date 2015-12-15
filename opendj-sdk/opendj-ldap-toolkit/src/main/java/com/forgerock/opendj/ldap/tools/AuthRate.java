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
import static com.forgerock.opendj.ldap.tools.Utils.*;

import java.io.PrintStream;
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
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
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
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;

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
                    final long searchWaitTimeNs = searchWaitRecentTimeNs.getAndSet(0);
                    extraColumn[0] = getDivisionResult(
                            100 * (intervalWaitTimeNs - searchWaitTimeNs), intervalWaitTimeNs, 1, "-");
                }
                return extraColumn;
            }
        }

        private final class BindUpdateStatsResultHandler extends UpdateStatsResultHandler<BindResult> {
            private BindUpdateStatsResultHandler(final long startTime) {
                super(startTime);
            }

            @Override
            public void handleException(final LdapException exception) {
                super.handleException(exception);

                if (exception.getResult().getResultCode() == ResultCode.INVALID_CREDENTIALS) {
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

            private BindWorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public Promise<?, LdapException> performOperation(final Connection connection,
                    final DataSource[] dataSources, final long startTime) {
                if (dataSources != null) {
                    data = DataSource.generateData(dataSources, data);
                    if (data.length == dataSources.length) {
                        final Object[] newData = new Object[data.length + 1];
                        System.arraycopy(data, 0, newData, 0, data.length);
                        data = newData;
                    }
                }

                Promise<BindResult, LdapException> returnedPromise;
                if (filter != null && baseDN != null) {
                    if (sr == null) {
                        if (dataSources != null) {
                            final String newBaseDN = String.format(baseDN, data);
                            final String newFilter = String.format(filter, data);
                            sr = Requests.newSearchRequest(newBaseDN, scope, newFilter, attributes);
                        } else {
                            sr = Requests.newSearchRequest(baseDN, scope, filter, attributes);
                        }
                        sr.setDereferenceAliasesPolicy(dereferencesAliasesPolicy);
                    } else if (dataSources != null) {
                        sr.setFilter(String.format(filter, data));
                        sr.setName(String.format(baseDN, data));
                    }

                    returnedPromise = connection.searchSingleEntryAsync(sr).thenAsync(
                            new AsyncFunction<SearchResultEntry, BindResult, LdapException>() {
                                @Override
                                public Promise<BindResult, LdapException> apply(SearchResultEntry result)
                                        throws LdapException {
                                    searchWaitRecentTimeNs.getAndAdd(System.nanoTime() - startTime);
                                    if (data == null) {
                                        data = new Object[1];
                                    }
                                    data[data.length - 1] = result.getName().toString();

                                    return performBind(connection, data);
                                }
                            });
                } else {
                    returnedPromise = performBind(connection, data);
                }

                incrementIterationCount();
                return returnedPromise.thenOnResult(new UpdateStatsResultHandler<BindResult>(startTime))
                                      .thenOnException(new BindUpdateStatsResultHandler(startTime));
            }

            private Promise<BindResult, LdapException> performBind(final Connection connection,
                final Object[] data) {
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
                    useInvalidPassword = p < invalidCredPercent;
                    break;
                }

                final BindRequest bindRequest = bindRequestTemplate;
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

                return connection.bindAsync(br);
            }
        }

        private final AtomicLong searchWaitRecentTimeNs = new AtomicLong();
        private final AtomicInteger invalidCredRecentCount = new AtomicInteger();
        private String filter;
        private String baseDN;
        private SearchScope scope;
        private DereferenceAliasesPolicy dereferencesAliasesPolicy;
        private String[] attributes;
        private int invalidCredPercent;
        /** Template of the bind requests which will be send to the remote server. */
        private BindRequest bindRequestTemplate;

        private BindPerformanceRunner(final PerformanceRunnerOptions options)
                throws ArgumentException {
            super(options);
        }

        private void setBindRequestTemplate(final BindRequest bindRequestTemplate) {
            this.bindRequestTemplate = bindRequestTemplate;
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
     * Constructor to allow tests.
     *
     * @param out
     *            output stream of console application
     * @param err
     *            error stream of console application
     */
    AuthRate(PrintStream out, PrintStream err) {
        super(out, err);
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

    int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_AUTHRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(AuthRate.class.getName(), toolDescription, false, true, 0, 0,
                        "[filter format string] [attributes ...]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_AUTHRATE.get());
        argParser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RATE_TOOLS.get());

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
            setDefaultPerfToolProperties();
            PerformanceRunnerOptions options = new PerformanceRunnerOptions(argParser, this);
            options.setSupportsRebind(false);
            options.setSupportsAsynchronousRequests(false);
            options.setSupportsMultipleThreadsPerConnection(false);

            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            runner = new BindPerformanceRunner(options);

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
                    new StringArgument("baseDN", OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN, false,
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

            invalidCredPercent =
                    new IntegerArgument("invalidPassword", 'I', "invalidPassword", false, false,
                            true, LocalizableMessage.raw("{invalidPassword}"), 0, null, true, 0,
                            true, 100, LocalizableMessage
                                    .raw("Percent of bind operations with simulated "
                                            + "invalid password"));
            invalidCredPercent.setPropertyName("invalidPassword");
            argParser.addArgument(invalidCredPercent);

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

            /* If we should just display usage or version information, then print it and exit. */
            if (argParser.usageOrVersionDisplayed()) {
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getUnauthenticatedConnectionFactory();
            final BindRequest bindRequestTemplate = connectionFactoryProvider.getBindRequest();
            if (bindRequestTemplate == null) {
                throw new ArgumentException(ERR_AUTHRATE_NO_BIND_DN_PROVIDED.get());
            }
            runner.setBindRequestTemplate(bindRequestTemplate);
            runner.validate();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<String> attributes = new LinkedList<>();
        final ArrayList<String> filterAndAttributeStrings = argParser.getTrailingArguments();
        if (!filterAndAttributeStrings.isEmpty()) {
             /*The list of trailing arguments should be structured as follow:
             the first trailing argument is considered the filter, the other
             as attributes.*/
            runner.filter = filterAndAttributeStrings.remove(0);
            // The rest are attributes
            attributes.addAll(filterAndAttributeStrings);
        }
        runner.attributes = attributes.toArray(new String[attributes.size()]);
        runner.baseDN = baseDN.getValue();
        try {
            runner.scope = searchScope.getTypedValue();
            runner.dereferencesAliasesPolicy = dereferencePolicy.getTypedValue();
            runner.invalidCredPercent = invalidCredPercent.getIntValue();
        } catch (final ArgumentException ex1) {
            errPrintln(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Try it out to make sure the format string and data sources match.
        final Object[] data = DataSource.generateData(runner.getDataSources(), null);
        try {
            if (runner.baseDN != null && runner.filter != null) {
                String.format(runner.filter, data);
                String.format(runner.baseDN, data);
            }
        } catch (final Exception ex1) {
            errPrintln(LocalizableMessage.raw("Error formatting filter or base DN: " + ex1));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        return runner.run(connectionFactory);
    }
}
