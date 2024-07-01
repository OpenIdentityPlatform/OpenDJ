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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.USE_SYSTEM_STREAM_TOKEN;
import static com.forgerock.opendj.cli.CommonArguments.continueOnErrorArgument;
import static com.forgerock.opendj.cli.CommonArguments.controlArgument;
import static com.forgerock.opendj.cli.CommonArguments.noOpArgument;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolException;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.noPropertiesFileArgument;
import static com.forgerock.opendj.cli.CommonArguments.propertiesFileArgument;
import static com.forgerock.opendj.cli.CommonArguments.showUsageArgument;
import static com.forgerock.opendj.cli.CommonArguments.verboseArgument;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.Utils.addControlsToRequest;
import static com.forgerock.opendj.ldap.tools.Utils.getConnection;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.printSuccessMessage;
import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static org.forgerock.i18n.LocalizableMessage.raw;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.StringArgument;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A tool that can be used to issue delete requests to the Directory Server. */
public final class LDAPDelete extends ToolConsoleApplication {

    /**
     * The main method for ldapdelete tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDAPDelete(System.out, System.err), args);
    }

    /**
     * This method should be used to run this ldap tool programmatically.
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
        return runTool(new LDAPDelete(out, err), args);
    }

    @VisibleForTesting
    LDAPDelete(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    private BooleanArgument verbose;

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    @Override
    int run(String... args) throws LDAPToolException {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPDELETE_TOOL_DESCRIPTION.get();
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPDelete.class.getName())
                .toolDescription(toolDescription)
                .trailingArguments(0, 1, "[DN]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPDELETE.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        final BooleanArgument continueOnError;
        final BooleanArgument deleteSubtree;
        final BooleanArgument dryRun;
        final BooleanArgument showUsage;
        final StringArgument controlStr;
        final StringArgument propertiesFileArgument;
        final BooleanArgument noPropertiesFileArgument;

        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            deleteSubtree =
                    BooleanArgument.builder("deleteSubtree")
                            .shortIdentifier('x')
                            .description(INFO_DELETE_DESCRIPTION_DELETE_SUBTREE.get())
                            .buildAndAddToParser(argParser);

            continueOnError = continueOnErrorArgument();
            argParser.addArgument(continueOnError);

            controlStr = controlArgument();
            argParser.addArgument(controlStr);

            dryRun = noOpArgument();
            argParser.addArgument(dryRun);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        argParser.parseArguments(args, getErrStream(), connectionFactoryProvider);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        final List<Control> controls = readControls(controlStr);
        if (deleteSubtree.isPresent()) {
            controls.add(SubtreeDeleteRequestControl.newControl(false));
        }

        try (final Connection connection = getConnection(argParser.getConnectionFactory(),
                                                         argParser.getBindRequest(),
                                                         dryRun,
                                                         this)) {
            final List<DeleteRequest> deleteRequests = createDeleteRequests(argParser.getTrailingArguments(), controls);

            for (final DeleteRequest deleteRequest : deleteRequests) {
                final String dnToRemove = deleteRequest.getName().toString();
                println(INFO_PROCESSING_OPERATION.get("DELETE", dnToRemove));
                if (!dryRun.isPresent()) {
                    Result result;
                    try {
                        result = connection.delete(deleteRequest);
                    } catch (final LdapException e) {
                        result = e.getResult();
                    }

                    final ResultCode resultCode = result.getResultCode();
                    if (ResultCode.SUCCESS != resultCode && ResultCode.REFERRAL != resultCode) {
                        printErrorMessage(this, result, ERR_LDAP_DELETE_FAILED);
                        println();
                        if (!continueOnError.isPresent()) {
                            return resultCode.intValue();
                        }
                    } else {
                        printSuccessMessage(this, result, "DELETE", dnToRemove);
                        println();
                    }
                }
            }
        }
        return ResultCode.SUCCESS.intValue();
    }

    private List<DeleteRequest> createDeleteRequests(final List<String> trailingArguments,
                                                     final List<Control> controls) throws LDAPToolException {
        try {
            if (!trailingArguments.isEmpty()
                    && !(trailingArguments.size() == 1 && USE_SYSTEM_STREAM_TOKEN.equals(trailingArguments.get(0)))) {
                return Collections.singletonList(createDeleteRequestForDn(trailingArguments.get(0), controls));
            }

            final List<DeleteRequest> deleteRequests = new ArrayList<>();
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(getInputStream()))) {
                String dn;
                while ((dn = reader.readLine()) != null) {
                    deleteRequests.add(createDeleteRequestForDn(dn, controls));
                }
                return deleteRequests;
            } catch (final IOException e) {
                throw newToolException(
                        e, ResultCode.UNDEFINED, ERR_LDAPDELETE_READING_STDIN.get(e.getLocalizedMessage()));
            }

        } catch (final IllegalArgumentException iae) {
            throw newToolException(iae, ResultCode.INVALID_DN_SYNTAX, raw(iae.getLocalizedMessage()));
        }
    }

    private DeleteRequest createDeleteRequestForDn(final String dnToRemove, final List<Control> controls)
            throws LDAPToolException {
        final DeleteRequest request = Requests.newDeleteRequest(dnToRemove);
        addControlsToRequest(request, controls);
        return request;
    }
}

