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
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.printPasswordPolicyResults;
import static org.forgerock.util.Utils.closeSilently;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordReader;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;
import org.forgerock.opendj.ldif.EntryWriter;
import org.forgerock.opendj.ldif.LDIFChangeRecordReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A tool that can be used to issue update (Add/Delete/Modify/ModifyDN) requests
 * to the Directory Server.
 */
public final class LDAPModify extends ConsoleApplication {
    private class VisitorImpl implements ChangeRecordVisitor<Integer, java.lang.Void> {
        @Override
        public Integer visitChangeRecord(final Void aVoid, final AddRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "ADD";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (connection != null) {
                try {
                    Result r = connection.add(change);
                    printResult(opType, change.getName().toString(), r);
                    return r.getResultCode().intValue();
                } catch (final LdapException ere) {
                    return printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final DeleteRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "DELETE";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (connection != null) {
                try {
                    Result r = connection.delete(change);
                    printResult(opType, change.getName().toString(), r);
                    return r.getResultCode().intValue();
                } catch (final LdapException ere) {
                    return printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final ModifyDNRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "MODIFY DN";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (connection != null) {
                try {
                    Result r = connection.modifyDN(change);
                    printResult(opType, change.getName().toString(), r);
                    return r.getResultCode().intValue();
                } catch (final LdapException ere) {
                    return printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final ModifyRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "MODIFY";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (connection != null) {
                try {
                    Result r = connection.modify(change);
                    printResult(opType, change.getName().toString(), r);
                    return r.getResultCode().intValue();
                } catch (final LdapException ere) {
                    return printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

        private void printResult(final String operationType, final String name, final Result r) {
            if (r.getResultCode() != ResultCode.SUCCESS && r.getResultCode() != ResultCode.REFERRAL) {
                final LocalizableMessage msg = INFO_OPERATION_FAILED.get(operationType);
                errPrintln(msg);
                errPrintln(ERR_TOOL_RESULT_CODE.get(r.getResultCode().intValue(), r.getResultCode()));
                if (r.getDiagnosticMessage() != null && r.getDiagnosticMessage().length() > 0) {
                    errPrintln(LocalizableMessage.raw(r.getDiagnosticMessage()));
                }
                if (r.getMatchedDN() != null && r.getMatchedDN().length() > 0) {
                    errPrintln(ERR_TOOL_MATCHED_DN.get(r.getMatchedDN()));
                }
            } else {
                println(INFO_OPERATION_SUCCESSFUL.get(operationType, name));
                if (r.getDiagnosticMessage() != null && r.getDiagnosticMessage().length() > 0) {
                    errPrintln(LocalizableMessage.raw(r.getDiagnosticMessage()));
                }
                if (r.getReferralURIs() != null) {
                    for (final String uri : r.getReferralURIs()) {
                        println(LocalizableMessage.raw(uri));
                    }
                }
            }

            try {
                final PreReadResponseControl control =
                        r.getControl(PreReadResponseControl.DECODER, new DecodeOptions());
                if (control != null) {
                    println(INFO_LDAPMODIFY_PREREAD_ENTRY.get());
                    writer.writeEntry(control.getEntry());
                }
            } catch (final DecodeException de) {
                errPrintln(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
            } catch (final IOException ioe) {
                throw new RuntimeException(ioe);
            }

            try {
                final PostReadResponseControl control =
                        r.getControl(PostReadResponseControl.DECODER, new DecodeOptions());
                if (control != null) {
                    println(INFO_LDAPMODIFY_POSTREAD_ENTRY.get());
                    writer.writeEntry(control.getEntry());
                }
            } catch (final DecodeException de) {
                errPrintln(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
            } catch (final IOException ioe) {
                throw new RuntimeException(ioe);
            }

            // TODO: CSN control
        }
    }

    /**
     * The main method for LDAPModify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new LDAPModify().run(args);
        System.exit(filterExitCode(retCode));
    }

    private Connection connection;

    private EntryWriter writer;

    private Collection<Control> controls;

    private BooleanArgument verbose;

    private LDAPModify() {
        // Nothing to do.
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPMODIFY_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(LDAPModify.class.getName(), toolDescription, false);
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPMODIFY.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        BindRequest bindRequest;

        BooleanArgument continueOnError;
        // TODO: Remove this due to new LDIF reader api?
        BooleanArgument defaultAdd;
        BooleanArgument noop;
        BooleanArgument showUsage;
        IntegerArgument version;
        StringArgument assertionFilter;
        StringArgument controlStr;
        StringArgument encodingStr;
        StringArgument filename;
        StringArgument postReadAttributes;
        StringArgument preReadAttributes;
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

            defaultAdd =
                    new BooleanArgument("defaultAdd", 'a', "defaultAdd",
                            INFO_MODIFY_DESCRIPTION_DEFAULT_ADD.get());
            argParser.addArgument(defaultAdd);

            filename =
                    new StringArgument("filename", OPTION_SHORT_FILENAME, OPTION_LONG_FILENAME,
                            false, false, true, INFO_FILE_PLACEHOLDER.get(), null, null,
                            INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get());
            filename.setPropertyName(OPTION_LONG_FILENAME);
            argParser.addArgument(filename);

            proxyAuthzID =
                    new StringArgument("proxy_authzid", OPTION_SHORT_PROXYAUTHID,
                            OPTION_LONG_PROXYAUTHID, false, false, true,
                            INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_PROXY_AUTHZID.get());
            proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
            argParser.addArgument(proxyAuthzID);

            assertionFilter =
                    new StringArgument("assertionfilter", null, OPTION_LONG_ASSERTION_FILE, false,
                            false, true, INFO_ASSERTION_FILTER_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_ASSERTION_FILTER.get());
            assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
            argParser.addArgument(assertionFilter);

            preReadAttributes =
                    new StringArgument("prereadattrs", null, "preReadAttributes", false, false,
                            true, INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_PREREAD_ATTRS.get());
            preReadAttributes.setPropertyName("preReadAttributes");
            argParser.addArgument(preReadAttributes);

            postReadAttributes =
                    new StringArgument("postreadattrs", null, "postReadAttributes", false, false,
                            true, INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_POSTREAD_ATTRS.get());
            postReadAttributes.setPropertyName("postReadAttributes");
            argParser.addArgument(postReadAttributes);

            controlStr =
                    new StringArgument("control", 'J', "control", false, true, true,
                            INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
                            INFO_DESCRIPTION_CONTROLS.get());
            controlStr.setPropertyName("control");
            argParser.addArgument(controlStr);

            version = CommonArguments.getLdapVersion();
            argParser.addArgument(version);

            encodingStr =
                    new StringArgument("encoding", 'i', "encoding", false, false, true,
                            INFO_ENCODING_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_ENCODING
                                    .get());
            encodingStr.setPropertyName("encoding");
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
                errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(versionNumber)));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        } catch (final ArgumentException ae) {
            errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(version.getValue()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        controls = new LinkedList<>();
        if (controlStr.isPresent()) {
            for (final String ctrlString : controlStr.getValues()) {
                try {
                    final Control ctrl = Utils.getControl(ctrlString);
                    controls.add(ctrl);
                } catch (final DecodeException de) {
                    errPrintln(ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
                    ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        }

        if (proxyAuthzID.isPresent()) {
            final Control proxyControl =
                    ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue());
            controls.add(proxyControl);
        }

        if (assertionFilter.isPresent()) {
            final String filterString = assertionFilter.getValue();
            Filter filter;
            try {
                filter = Filter.valueOf(filterString);

                // FIXME -- Change this to the correct OID when the official one
                // is assigned.
                final Control assertionControl = AssertionRequestControl.newControl(true, filter);
                controls.add(assertionControl);
            } catch (final LocalizedIllegalArgumentException le) {
                errPrintln(ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        if (preReadAttributes.isPresent()) {
            final String valueStr = preReadAttributes.getValue();
            final StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
            final List<String> attributes = new LinkedList<>();
            while (tokenizer.hasMoreTokens()) {
                attributes.add(tokenizer.nextToken());
            }
            controls.add(PreReadRequestControl.newControl(true, attributes));
        }

        if (postReadAttributes.isPresent()) {
            final String valueStr = postReadAttributes.getValue();
            final StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
            final List<String> attributes = new LinkedList<>();
            while (tokenizer.hasMoreTokens()) {
                attributes.add(tokenizer.nextToken());
            }
            final PostReadRequestControl control =
                    PostReadRequestControl.newControl(true, attributes);
            controls.add(control);
        }

        writer = new LDIFEntryWriter(getOutputStream());
        final VisitorImpl visitor = new VisitorImpl();
        ChangeRecordReader reader = null;
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

            if (filename.isPresent()) {
                try {
                    reader = new LDIFChangeRecordReader(new FileInputStream(filename.getValue()));
                } catch (final Exception e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(filename.getValue(), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            } else {
                reader = new LDIFChangeRecordReader(getInputStream());
            }

            try {
                while (reader.hasNext()) {
                    final ChangeRecord cr = reader.readChangeRecord();
                    final int result = cr.accept(visitor, null);
                    if (result != 0 && !continueOnError.isPresent()) {
                        return result;
                    }
                }
            } catch (final IOException ioe) {
                errPrintln(ERR_LDIF_FILE_READ_ERROR.get(filename.getValue(), ioe.getLocalizedMessage()));
                return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
            }
        } finally {
            closeSilently(reader, connection);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
