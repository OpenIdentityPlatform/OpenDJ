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
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.readBytesFromFile;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.printPasswordPolicyResults;
import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
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
        final int retCode = new LDAPCompare().run(args);
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
        println(INFO_PROCESSING_COMPARE_OPERATION.get(request.getAttributeDescription().toString(),
                request.getAssertionValueAsString(), request.getName().toString()));
        if (connection != null) {
            try {
                Result result = connection.compare(request);
                if (result.getResultCode() == ResultCode.COMPARE_FALSE) {
                    println(INFO_COMPARE_OPERATION_RESULT_FALSE.get(request.getName().toString()));
                } else {
                    println(INFO_COMPARE_OPERATION_RESULT_TRUE.get(request.getName().toString()));
                }
            } catch (final LdapException ere) {
                final LocalizableMessage msg = INFO_OPERATION_FAILED.get("COMPARE");
                errPrintln(msg);
                final Result r = ere.getResult();
                errPrintln(ERR_TOOL_RESULT_CODE.get(r.getResultCode().intValue(), r.getResultCode()
                        .toString()));
                if (r.getDiagnosticMessage() != null && r.getDiagnosticMessage().length() > 0) {
                    errPrintln(LocalizableMessage.raw(r.getDiagnosticMessage()));
                }
                if (r.getMatchedDN() != null && r.getMatchedDN().length() > 0) {
                    errPrintln(ERR_TOOL_MATCHED_DN.get(r.getMatchedDN()));
                }
                return r.getResultCode().intValue();
            }
        }
        return ResultCode.SUCCESS.intValue();
    }

    int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPCOMPARE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser = new ArgumentParser(
            LDAPCompare.class.getName(), toolDescription, false, true, 1, 0, "attribute:value [DN ...]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPCOMPARE.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        BindRequest bindRequest;

        BooleanArgument continueOnError;
        BooleanArgument noop;
        BooleanArgument showUsage;
        IntegerArgument version;
        StringArgument assertionFilter;
        StringArgument controlStr;
        StringArgument encodingStr;
        StringArgument filename;
        StringArgument proxyAuthzID;
        StringArgument propertiesFileArgument;
        BooleanArgument noPropertiesFileArgument;
        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            propertiesFileArgument = CommonArguments.getPropertiesFile();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            noPropertiesFileArgument = CommonArguments.getNoPropertiesFile();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            filename = CommonArguments.getFilename(INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get());
            argParser.addArgument(filename);

            proxyAuthzID = CommonArguments.getProxyAuthId();
            argParser.addArgument(proxyAuthzID);

            assertionFilter =
                    new StringArgument("assertionfilter", null, OPTION_LONG_ASSERTION_FILE, false,
                            false, true, INFO_ASSERTION_FILTER_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_ASSERTION_FILTER.get());
            assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
            argParser.addArgument(assertionFilter);

            controlStr = CommonArguments.getControl();
            argParser.addArgument(controlStr);

            version = CommonArguments.getLdapVersion();
            argParser.addArgument(version);

            encodingStr = CommonArguments.getEncoding();
            argParser.addArgument(encodingStr);

            continueOnError = CommonArguments.getContinueOnError();
            argParser.addArgument(continueOnError);

            noop = CommonArguments.getNoOp();
            argParser.addArgument(noop);

            verbose = CommonArguments.getVerbose();
            argParser.addArgument(verbose);

            showUsage = CommonArguments.getShowUsage();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information, then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getUnauthenticatedConnectionFactory();
            bindRequest = connectionFactoryProvider.getBindRequest();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        try {
            final int versionNumber = version.getIntValue();
            if (versionNumber != 2 && versionNumber != 3) {
                argParser.displayMessageAndUsageReference(
                    getErrStream(), ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(versionNumber)));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(
                getErrStream(), ERR_DESCRIPTION_INVALID_VERSION.get(version.getValue()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final ArrayList<String> dnStrings = new ArrayList<>();
        final ArrayList<String> attrAndDNStrings = argParser.getTrailingArguments();

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
                    errPrintln(INFO_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE.get());
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            } else if (nextChar == '<') {
                try {
                    final String filePath = remainder.substring(1, remainder.length());
                    attributeVal = ByteString.wrap(readBytesFromFile(filePath));
                } catch (final Exception e) {
                    errPrintln(INFO_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE.get(String
                            .valueOf(e)));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            } else {
                attributeVal = ByteString.valueOfUtf8(remainder);
            }
        } else {
            attributeVal = ByteString.valueOfUtf8(remainder);
        }

        final CompareRequest compare = Requests.newCompareRequest("", attributeType, attributeVal);

        if (controlStr.isPresent()) {
            for (final String ctrlString : controlStr.getValues()) {
                try {
                    final Control ctrl = Utils.getControl(ctrlString);
                    compare.addControl(ctrl);
                } catch (final DecodeException de) {
                    errPrintln(ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        }

        if (proxyAuthzID.isPresent()) {
            final Control proxyControl =
                    ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue());
            compare.addControl(proxyControl);
        }

        if (assertionFilter.isPresent()) {
            final String filterString = assertionFilter.getValue();
            Filter filter;
            try {
                filter = Filter.valueOf(filterString);

                // FIXME -- Change this to the correct OID when the official one
                // is assigned.
                final Control assertionControl = AssertionRequestControl.newControl(true, filter);
                compare.addControl(assertionControl);
            } catch (final LocalizedIllegalArgumentException le) {
                errPrintln(ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        BufferedReader rdr = null;
        if (!filename.isPresent() && dnStrings.isEmpty()) {
            // Read from stdin.
            rdr = new BufferedReader(new InputStreamReader(System.in));
        } else if (filename.isPresent()) {
            try {
                rdr = new BufferedReader(new FileReader(filename.getValue()));
            } catch (final FileNotFoundException t) {
                errPrintln(ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename.getValue(), t.toString()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        Connection connection = null;
        try {
            if (!noop.isPresent()) {
                try {
                    connection = connectionFactory.getConnection();
                    if (bindRequest != null) {
                        printPasswordPolicyResults(this, connection.bind(bindRequest));
                    }
                } catch (final LdapException ere) {
                    return printErrorMessage(this, ere);
                }
            }

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
                    errPrintln(ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename.getValue(), ioe
                            .toString()));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        } finally {
            closeSilently(connection, rdr);
        }

        return 0;
    }
}
