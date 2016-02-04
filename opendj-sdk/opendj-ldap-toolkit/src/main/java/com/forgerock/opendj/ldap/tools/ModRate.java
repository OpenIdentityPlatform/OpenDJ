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
 *      Portions Copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.StringArgument;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

/**
 * A load generation tool that can be used to load a Directory Server with
 * Modify requests using one or more LDAP connections.
 */
public final class ModRate extends ConsoleApplication {
    private static final class ModifyPerformanceRunner extends PerformanceRunner {
        private final class ModifyWorkerThread extends WorkerThread {
            private ModifyRequest mr;
            private Object[] data;

            private ModifyWorkerThread(final Connection connection,
                    final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public Promise<?, LdapException> performOperation(final Connection connection,
                    final DataSource[] dataSources, final long currentTimeNs) {
                if (dataSources != null) {
                    data = DataSource.generateData(dataSources, data);
                }
                mr = newModifyRequest(data);
                LdapResultHandler<Result> modRes = new UpdateStatsResultHandler<>(currentTimeNs);

                incrementIterationCount();
                return connection.modifyAsync(mr).thenOnResult(modRes).thenOnException(modRes);
            }

            private ModifyRequest newModifyRequest(final Object[] data) {
                String formattedString;
                int colonPos;
                ModifyRequest mr;
                if (data != null) {
                    mr = Requests.newModifyRequest(String.format(baseDN, data));
                } else {
                    mr = Requests.newModifyRequest(baseDN);
                }
                for (final String modString : modStrings) {
                    if (data != null) {
                        formattedString = String.format(modString, data);
                    } else {
                        formattedString = modString;
                    }
                    colonPos = formattedString.indexOf(':');
                    if (colonPos > 0) {
                        mr.addModification(ModificationType.REPLACE, formattedString.substring(0,
                                colonPos), formattedString.substring(colonPos + 1));
                    }
                }
                return mr;
            }
        }

        private String baseDN;
        private String[] modStrings;

        private ModifyPerformanceRunner(final PerformanceRunnerOptions options)
                throws ArgumentException {
            super(options);
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection,
                final ConnectionFactory connectionFactory) {
            return new ModifyWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread(final PerformanceRunner performanceRunner, final ConsoleApplication app) {
            return new StatsThread(performanceRunner, app);
        }
    }

    /**
     * The main method for ModRate tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new ModRate().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;
    private BooleanArgument scriptFriendly;

    private ModRate() {
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
        // Creates the command-line argument parser for use with this program
        final LocalizableMessage toolDescription = INFO_MODRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(ModRate.class.getName(), toolDescription, false, true, 1, 0,
                        "[(attribute:value format string) ...]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_MODRATE.get());
        argParser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RATE_TOOLS.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        ModifyPerformanceRunner runner;

        BooleanArgument showUsage;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;
        StringArgument baseDN;
        try {
            Utils.setDefaultPerfToolProperties();

            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            runner = new ModifyPerformanceRunner(new PerformanceRunnerOptions(argParser, this));

            propertiesFileArgument = propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            baseDN =
                    StringArgument.builder(OPTION_LONG_TARGETDN)
                            .shortIdentifier(OPTION_SHORT_BASEDN)
                            .description(INFO_MODRATE_TOOL_DESCRIPTION_TARGETDN.get())
                            .required()
                            .valuePlaceholder(INFO_TARGETDN_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());

            scriptFriendly =
                    BooleanArgument.builder("scriptFriendly")
                            .shortIdentifier('S')
                            .description(INFO_DESCRIPTION_SCRIPT_FRIENDLY.get())
                            .buildAndAddToParser(argParser);
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

            connectionFactory = connectionFactoryProvider.getAuthenticatedConnectionFactory();
            runner.setBindRequest(connectionFactoryProvider.getBindRequest());
            runner.validate();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        runner.modStrings =
                argParser.getTrailingArguments().toArray(
                        new String[argParser.getTrailingArguments().size()]);
        runner.baseDN = baseDN.getValue();

        try {
            /* Try it out to make sure the format string and data sources match. */
            final Object[] data = DataSource.generateData(runner.getDataSources(), null);
            for (final String modString : runner.modStrings) {
                String.format(modString, data);
            }
            String.format(runner.baseDN, data);
        } catch (final Exception ex1) {
            errPrintln(LocalizableMessage.raw("Error formatting filter or base DN: " + ex1));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        return runner.run(connectionFactory);
    }
}
