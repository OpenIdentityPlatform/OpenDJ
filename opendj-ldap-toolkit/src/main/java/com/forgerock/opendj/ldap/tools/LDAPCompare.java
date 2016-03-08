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
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.readBytesFromFile;
import static com.forgerock.opendj.ldap.tools.Utils.addControlsToRequest;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.readAssertionControl;
import static com.forgerock.opendj.ldap.tools.Utils.ensureLdapProtocolVersionIsSupported;
import static com.forgerock.opendj.ldap.tools.Utils.getConnection;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
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
import org.forgerock.opendj.ldap.responses.Result;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/** A tool that can be used to issue Compare requests to the Directory Server. */
public final class LDAPCompare extends ConsoleApplication {
    /**
     * The main method for LDAPModify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final LDAPCompare ldapCompare = new LDAPCompare();
        int retCode;
        try {
            retCode = ldapCompare.run(args);
        } catch (final LDAPToolException e) {
            e.printErrorMessage(ldapCompare);
            retCode = e.getResultCode();
        }
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;

    private LDAPCompare() {
        // Nothing to do.
    }

    /**
     * Constructor to allow tests.
     *
     * @param out output stream of console application
     * @param err error stream of console application
     */
    LDAPCompare(PrintStream out, PrintStream err) {
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

    private int executeCompare(final CompareRequest request, final Connection connection) {
        final String dnStr = request.getName().toString();
        println(INFO_PROCESSING_COMPARE_OPERATION.get(
            request.getAttributeDescription().toString(), request.getAssertionValueAsString(), dnStr));
        if (connection != null) {
            try {
                Result result = connection.compare(request);
                if (ResultCode.COMPARE_FALSE == result.getResultCode()) {
                    println(INFO_COMPARE_OPERATION_RESULT_FALSE.get(dnStr));
                } else {
                    println(INFO_COMPARE_OPERATION_RESULT_TRUE.get(dnStr));
                }
            } catch (final LdapException e) {
                return printErrorMessage(this, e, ERR_LDAP_COMPARE_FAILED);
            }
        }
        return ResultCode.SUCCESS.intValue();
    }

    int run(final String[] args) throws LDAPToolException {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPCOMPARE_TOOL_DESCRIPTION.get();
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPCompare.class.getName())
                .toolDescription(toolDescription)
                .trailingArguments(1, "attribute:value [DN ...]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPCOMPARE.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        BooleanArgument continueOnError;
        BooleanArgument dryRun;
        BooleanArgument showUsage;
        IntegerArgument ldapProtocolVersion;
        StringArgument assertionFilter;
        StringArgument controlStr;
        StringArgument encodingStr;
        StringArgument filename;
        StringArgument proxyAuthzID;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;
        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            filename = filenameArgument(INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get());
            argParser.addArgument(filename);

            proxyAuthzID = proxyAuthIdArgument();
            argParser.addArgument(proxyAuthzID);

            assertionFilter =
                    StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                            .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                            .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            controlStr = controlArgument();
            argParser.addArgument(controlStr);

            ldapProtocolVersion = ldapVersionArgument();
            argParser.addArgument(ldapProtocolVersion);

            encodingStr = encodingArgument();
            argParser.addArgument(encodingStr);

            continueOnError = continueOnErrorArgument();
            argParser.addArgument(continueOnError);

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
        ensureLdapProtocolVersionIsSupported(ldapProtocolVersion);

        final List<String> dnStrings = new ArrayList<>();
        final List<String> attrAndDNStrings = argParser.getTrailingArguments();

        if (attrAndDNStrings.isEmpty()) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_LDAPCOMPARE_NO_ATTR.get());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // First element should be an attribute string.
        final String attributeString = attrAndDNStrings.remove(0);
        // Rest are DN strings
        dnStrings.addAll(attrAndDNStrings);

        // If no DNs were provided, then exit with an error.
        if (dnStrings.isEmpty() && !filename.isPresent()) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_LDAPCOMPARE_NO_DNS.get());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        /* If trailing DNs were provided and the filename argument was also
         provided, exit with an error.*/
        if (!dnStrings.isEmpty() && filename.isPresent()) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_LDAPCOMPARE_FILENAME_AND_DNS.get());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // parse the attribute string
        final int idx = attributeString.indexOf(":");
        if (idx == -1) {
            argParser.displayMessageAndUsageReference(
                getErrStream(), ERR_LDAPCOMPARE_INVALID_ATTR_STRING.get(attributeString));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
        final String attributeType = attributeString.substring(0, idx);
        ByteString attributeVal;
        final String remainder = attributeString.substring(idx + 1, attributeString.length());
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

        BufferedReader rdr = null;
        if (!filename.isPresent() && dnStrings.isEmpty()) {
            // Read from stdin.
            rdr = new BufferedReader(new InputStreamReader(System.in));
        } else if (filename.isPresent()) {
            try {
                rdr = new BufferedReader(new FileReader(filename.getValue()));
            } catch (final FileNotFoundException t) {
                throw newToolParamException(
                        t, ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename.getValue(), t.toString()));
            }
        }

        try (final Connection connection = getConnection(argParser.getConnectionFactory(),
                                                         argParser.getBindRequest(),
                                                         dryRun,
                                                         this)) {
            int result;
            if (rdr == null) {
                for (final String dn : dnStrings) {
                    compare.setName(dn);
                    result = executeCompare(compare, connection);
                    if (result != 0 && !continueOnError.isPresent()) {
                        return result;
                    }
                }
            } else {
                String dn;
                try {
                    while ((dn = rdr.readLine()) != null) {
                        compare.setName(dn);
                        result = executeCompare(compare, connection);
                        if (result != 0 && !continueOnError.isPresent()) {
                            return result;
                        }
                    }
                } catch (final IOException ioe) {
                    throw newToolParamException(
                            ioe, ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename.getValue(), ioe.toString()));
                }
            }
        } finally {
            closeSilently(rdr);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
