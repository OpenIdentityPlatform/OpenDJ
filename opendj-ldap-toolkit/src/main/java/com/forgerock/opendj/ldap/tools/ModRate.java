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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;

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
            public FutureResult<?> performOperation(final Connection connection,
                    final DataSource[] dataSources, final long startTime) {
                if (dataSources != null) {
                    data = DataSource.generateData(dataSources, data);
                }
                mr = newModifyRequest(data);
                return connection.modifyAsync(mr, null, new UpdateStatsResultHandler<Result>(
                        startTime));
            }

            private ModifyRequest newModifyRequest(final Object[] data) {
                String formattedString;
                int colonPos;
                ModifyRequest mr;
                if (data == null) {
                    mr = Requests.newModifyRequest(baseDN);
                } else {
                    mr = Requests.newModifyRequest(String.format(baseDN, data));
                }
                for (final String modString : modStrings) {
                    if (data == null) {
                        formattedString = modString;
                    } else {
                        formattedString = String.format(modString, data);
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

        private ModifyPerformanceRunner(final ArgumentParser argParser, final ConsoleApplication app)
                throws ArgumentException {
            super(argParser, app, false, false, false);
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection,
                final ConnectionFactory connectionFactory) {
            return new ModifyWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread() {
            return new StatsThread(new String[0]);
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
        final LocalizableMessage toolDescription = INFO_MODRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(ModRate.class.getName(), toolDescription, false, true, 1, 0,
                        "[(attribute:value format string) ...]");
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
            runner = new ModifyPerformanceRunner(argParser, this);
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

            baseDN =
                    new StringArgument("targetDN", OPTION_SHORT_BASEDN, OPTION_LONG_TARGETDN, true,
                            false, true, INFO_TARGETDN_PLACEHOLDER.get(), null, null,
                            INFO_MODRATE_TOOL_DESCRIPTION_TARGETDN.get());
            baseDN.setPropertyName(OPTION_LONG_BASEDN);
            argParser.addArgument(baseDN);

            verbose =
                    new BooleanArgument("verbose", 'v', "verbose", INFO_DESCRIPTION_VERBOSE.get());
            verbose.setPropertyName("verbose");
            argParser.addArgument(verbose);

            showUsage =
                    new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                            INFO_DESCRIPTION_SHOWUSAGE.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());

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

            connectionFactory = connectionFactoryProvider.getAuthenticatedConnectionFactory();
            runner.validate();
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        runner.modStrings =
                argParser.getTrailingArguments().toArray(
                        new String[argParser.getTrailingArguments().size()]);
        runner.baseDN = baseDN.getValue();

        try {

            // Try it out to make sure the format string and data sources
            // match.
            final Object[] data = DataSource.generateData(runner.getDataSources(), null);
            for (final String modString : runner.modStrings) {
                String.format(modString, data);
            }
            String.format(runner.baseDN, data);
        } catch (final Exception ex1) {
            println(LocalizableMessage.raw("Error formatting filter or base DN: " + ex1.toString()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        return runner.run(connectionFactory);
    }
}
