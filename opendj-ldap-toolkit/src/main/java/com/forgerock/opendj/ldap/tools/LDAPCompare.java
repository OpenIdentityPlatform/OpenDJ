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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.readBytesFromFile;
import static com.forgerock.opendj.ldap.tools.Utils.addControlsToRequest;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.readAssertionControl;
import static com.forgerock.opendj.ldap.tools.Utils.getConnection;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;

import java.io.PrintStream;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.Requests;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.StringArgument;
import org.forgerock.util.annotations.VisibleForTesting;

/** A tool that can be used to issue Compare requests to the Directory Server. */
public final class LDAPCompare extends ToolConsoleApplication {

    /**
     * The main method for ldapcompare tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDAPCompare(System.out, System.err), args);
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
        return runTool(new LDAPCompare(out, err), args);
    }

    private BooleanArgument verbose;

    @VisibleForTesting
    LDAPCompare(final PrintStream out, final PrintStream err) {
      super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPCOMPARE_TOOL_DESCRIPTION.get();
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPCompare.class.getName())
                .toolDescription(toolDescription)
                .trailingArguments(2, 2, "attribute:value DN")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPCOMPARE.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        BooleanArgument dryRun;
        BooleanArgument showUsage;
        StringArgument assertionFilter;
        StringArgument controlStr;
        StringArgument proxyAuthzID;
        StringArgument propertiesFileArgument;
        BooleanArgument useCompareResultCode;
        BooleanArgument noPropertiesFileArgument;
        BooleanArgument scriptFriendly;

        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            proxyAuthzID = proxyAuthIdArgument();
            argParser.addArgument(proxyAuthzID);

            assertionFilter = StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                                            .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                                            .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                                            .buildAndAddToParser(argParser);

            useCompareResultCode = BooleanArgument.builder("useCompareResultCode")
                                                  .shortIdentifier('m')
                                                  .description(INFO_LDAPCOMPARE_DESCRIPTION_USE_COMPARE_RESULT.get())
                                                  .buildAndAddToParser(argParser);

            controlStr = controlArgument();
            argParser.addArgument(controlStr);

            dryRun = noOpArgument();
            argParser.addArgument(dryRun);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            scriptFriendly = scriptFriendlySdkArgument();
            argParser.addArgument(scriptFriendly);

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

        final List<String> trailingArguments = argParser.getTrailingArguments();
        final String attribute = trailingArguments.get(0);
        final String dn = trailingArguments.get(1);

        // Parse the attribute string
        final int idx = attribute.indexOf(":");
        if (idx == -1) {
            argParser.displayMessageAndUsageReference(
                    getErrStream(), ERR_LDAPCOMPARE_INVALID_ATTR_STRING.get(attribute));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
        final String attributeType = attribute.substring(0, idx);
        ByteString attributeVal;
        final String remainder = attribute.substring(idx + 1, attribute.length());
        if (remainder.length() > 0) {
            final char nextChar = remainder.charAt(0);
            if (nextChar == ':') {
                final String base64 = remainder.substring(1, remainder.length());
                try {
                    attributeVal = ByteString.valueOfBase64(base64);
                } catch (final LocalizedIllegalArgumentException e) {
                    throw newToolParamException(e, INFO_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE.get());
                }
            } else if (nextChar == '<') {
                try {
                    final String filePath = remainder.substring(1, remainder.length());
                    attributeVal = ByteString.wrap(readBytesFromFile(filePath));
                } catch (final Exception e) {
                    throw newToolParamException(
                            e, INFO_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE.get(String.valueOf(e)));
                }
            } else {
                attributeVal = ByteString.valueOfUtf8(remainder);
            }
        } else {
            attributeVal = ByteString.valueOfUtf8(remainder);
        }

        final CompareRequest compare = Requests.newCompareRequest("", attributeType, attributeVal);
        addControlsToRequest(compare, readControls(controlStr));

        if (proxyAuthzID.isPresent()) {
            compare.addControl(ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue()));
        }

        if (assertionFilter.isPresent()) {
            compare.addControl(readAssertionControl(assertionFilter.getValue()));
        }

        try (final Connection connection = getConnection(argParser.getConnectionFactory(),
                                                         argParser.getBindRequest(),
                                                         dryRun,
                                                         this)) {
            compare.setName(dn);
            final int compareResultCode = executeCompare(compare, connection, scriptFriendly.isPresent());
            final boolean compareTrue = compareResultCode == ResultCode.COMPARE_TRUE.intValue();
            return !useCompareResultCode.isPresent() && compareTrue ? ResultCode.SUCCESS.intValue()
                                                                    : compareResultCode;
        }
    }

    private int executeCompare(
            final CompareRequest request, final Connection connection, final boolean isScriptFriendly) {
        final String dnStr = request.getName().toString();
        if (!isScriptFriendly) {
            println(INFO_PROCESSING_COMPARE_OPERATION.get(
                    request.getAttributeDescription().toString(), request.getAssertionValueAsString(), dnStr));
        }

        if (connection == null) {
            // Dry run. There nothing more to check on client side.
            return ResultCode.COMPARE_TRUE.intValue();
        }

        try {
            final ResultCode resultCode = connection.compare(request).getResultCode();
            if (!isScriptFriendly) {
                println(ResultCode.COMPARE_FALSE == resultCode ? INFO_COMPARE_OPERATION_RESULT_FALSE.get(dnStr)
                                                               : INFO_COMPARE_OPERATION_RESULT_TRUE.get(dnStr));
            }
            return resultCode.intValue();
        } catch (final LdapException e) {
            return printErrorMessage(this, e, ERR_LDAP_COMPARE_FAILED);
        }
    }
}
