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
 *      Portions copyright 2011-2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.filterExitCode;
import static com.forgerock.opendj.util.StaticUtils.closeSilently;

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
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
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

/**
 * A tool that can be used to issue update (Add/Delete/Modify/ModifyDN) requests
 * to the Directory Server.
 */
public final class LDAPModify extends ConsoleApplication {
    private class VisitorImpl implements ChangeRecordVisitor<Integer, java.lang.Void> {
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
                } catch (final ErrorResultException ere) {
                    return Utils.printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

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
                } catch (final ErrorResultException ere) {
                    return Utils.printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

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
                } catch (final ErrorResultException ere) {
                    return Utils.printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

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
                } catch (final ErrorResultException ere) {
                    return Utils.printErrorMessage(LDAPModify.this, ere);
                }
            }
            return ResultCode.SUCCESS.intValue();
        }

        private void printResult(final String operationType, final String name, final Result r) {
            if (r.getResultCode() != ResultCode.SUCCESS && r.getResultCode() != ResultCode.REFERRAL) {
                final LocalizableMessage msg = INFO_OPERATION_FAILED.get(operationType);
                println(msg);
                println(ERR_TOOL_RESULT_CODE.get(r.getResultCode().intValue(), r.getResultCode()
                        .toString()));
                if ((r.getDiagnosticMessage() != null) && (r.getDiagnosticMessage().length() > 0)) {
                    println(LocalizableMessage.raw(r.getDiagnosticMessage()));
                }
                if (r.getMatchedDN() != null && r.getMatchedDN().length() > 0) {
                    println(ERR_TOOL_MATCHED_DN.get(r.getMatchedDN()));
                }
            } else {
                final LocalizableMessage msg = INFO_OPERATION_SUCCESSFUL.get(operationType, name);
                println(msg);
                if ((r.getDiagnosticMessage() != null) && (r.getDiagnosticMessage().length() > 0)) {
                    println(LocalizableMessage.raw(r.getDiagnosticMessage()));
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
                println(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
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
                println(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    @SuppressWarnings("resource")
    private int run(final String[] args) {
        // Create the command-line argument parser for use with this
        // program.
        final LocalizableMessage toolDescription = INFO_LDAPMODIFY_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(LDAPModify.class.getName(), toolDescription, false);
        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;

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

            version =
                    new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                            OPTION_LONG_PROTOCOL_VERSION, false, false, true,
                            INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
                            INFO_DESCRIPTION_VERSION.get());
            version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
            argParser.addArgument(version);

            encodingStr =
                    new StringArgument("encoding", 'i', "encoding", false, false, true,
                            INFO_ENCODING_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_ENCODING
                                    .get());
            encodingStr.setPropertyName("encoding");
            argParser.addArgument(encodingStr);

            continueOnError =
                    new BooleanArgument("continueOnError", 'c', "continueOnError",
                            INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
            continueOnError.setPropertyName("continueOnError");
            argParser.addArgument(continueOnError);

            noop =
                    new BooleanArgument("no-op", OPTION_SHORT_DRYRUN, OPTION_LONG_DRYRUN,
                            INFO_DESCRIPTION_NOOP.get());
            noop.setPropertyName(OPTION_LONG_DRYRUN);
            argParser.addArgument(noop);

            verbose =
                    new BooleanArgument("verbose", 'v', "verbose", INFO_DESCRIPTION_VERBOSE.get());
            verbose.setPropertyName("verbose");
            argParser.addArgument(verbose);

            showUsage =
                    new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                            INFO_DESCRIPTION_SHOWUSAGE.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
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
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        try {
            final int versionNumber = version.getIntValue();
            if (versionNumber != 2 && versionNumber != 3) {
                println(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(versionNumber)));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        } catch (final ArgumentException ae) {
            println(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(version.getValue())));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // modifyOptions.setShowOperations(noop.isPresent());
        // modifyOptions.setVerbose(verbose.isPresent());
        // modifyOptions.setContinueOnError(continueOnError.isPresent());
        // modifyOptions.setEncoding(encodingStr.getValue());
        // modifyOptions.setDefaultAdd(defaultAdd.isPresent());

        controls = new LinkedList<Control>();
        if (controlStr.isPresent()) {
            for (final String ctrlString : controlStr.getValues()) {
                try {
                    final Control ctrl = Utils.getControl(ctrlString);
                    controls.add(ctrl);
                } catch (final DecodeException de) {
                    final LocalizableMessage message =
                            ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
                    println(message);
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
                final LocalizableMessage message =
                        ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage());
                println(message);
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        if (preReadAttributes.isPresent()) {
            final String valueStr = preReadAttributes.getValue();
            final StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
            final List<String> attributes = new LinkedList<String>();
            while (tokenizer.hasMoreTokens()) {
                attributes.add(tokenizer.nextToken());
            }
            final PreReadRequestControl control =
                    PreReadRequestControl.newControl(true, attributes);
            controls.add(control);
        }

        if (postReadAttributes.isPresent()) {
            final String valueStr = postReadAttributes.getValue();
            final StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
            final List<String> attributes = new LinkedList<String>();
            while (tokenizer.hasMoreTokens()) {
                attributes.add(tokenizer.nextToken());
            }
            final PostReadRequestControl control =
                    PostReadRequestControl.newControl(true, attributes);
            controls.add(control);
        }

        if (!noop.isPresent()) {
            try {
                connection = connectionFactory.getConnection();
            } catch (final ErrorResultException ere) {
                return Utils.printErrorMessage(this, ere);
            }
        }

        Utils.printPasswordPolicyResults(this, connection);

        writer = new LDIFEntryWriter(getOutputStream());
        final VisitorImpl visitor = new VisitorImpl();
        ChangeRecordReader reader = null;
        try {
            if (filename.isPresent()) {
                try {
                    reader = new LDIFChangeRecordReader(new FileInputStream(filename.getValue()));
                } catch (final Exception e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(filename.getValue(), e
                                    .getLocalizedMessage());
                    println(message);
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
                final LocalizableMessage message =
                        ERR_LDIF_FILE_READ_ERROR
                                .get(filename.getValue(), ioe.getLocalizedMessage());
                println(message);
                return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
            }
        } finally {
            closeSilently(reader, connection);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
