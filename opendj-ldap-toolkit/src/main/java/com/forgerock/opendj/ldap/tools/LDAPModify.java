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
import static com.forgerock.opendj.cli.CliMessages.INFO_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.getConnection;
import static com.forgerock.opendj.ldap.tools.Utils.printSuccessMessage;
import static com.forgerock.opendj.ldap.tools.Utils.readAssertionControl;
import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
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

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.StringArgument;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * A tool that can be used to issue update (Add/Delete/Modify/ModifyDN) requests
 * to the Directory Server.
 */
public final class LDAPModify extends ToolConsoleApplication {

    /**
     * The main method for ldapmodify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDAPModify(System.out, System.err), args);
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
        return runTool(new LDAPModify(out, err), args);
    }

    private final class VisitorImpl implements ChangeRecordVisitor<Integer, Void> {
        private final Connection connection;

        private VisitorImpl(final Connection connection) {
            this.connection = connection;
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final AddRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "ADD";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (dryRun()) {
                return ResultCode.SUCCESS.intValue();
            }

            try {
                Result r = connection.add(change);
                printResult(opType, change.getName().toString(), r);
                return r.getResultCode().intValue();
            } catch (final LdapException ere) {
                return printErrorMessage(LDAPModify.this, ere, ERR_LDAP_MODIFY_FAILED);
            }
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final DeleteRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "DELETE";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (dryRun()) {
                return ResultCode.SUCCESS.intValue();
            }

            try {
                Result r = connection.delete(change);
                printResult(opType, change.getName().toString(), r);
                return r.getResultCode().intValue();
            } catch (final LdapException ere) {
                return printErrorMessage(LDAPModify.this, ere, ERR_LDAP_MODIFY_FAILED);
            }
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final ModifyDNRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "MODIFY DN";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (dryRun()) {
                return ResultCode.SUCCESS.intValue();
            }

            try {
                Result r = connection.modifyDN(change);
                printResult(opType, change.getName().toString(), r);
                return r.getResultCode().intValue();
            } catch (final LdapException ere) {
                return printErrorMessage(LDAPModify.this, ere, ERR_LDAP_MODIFY_FAILED);
            }
        }

        @Override
        public Integer visitChangeRecord(final Void aVoid, final ModifyRequest change) {
            for (final Control control : controls) {
                change.addControl(control);
            }
            final String opType = "MODIFY";
            println(INFO_PROCESSING_OPERATION.get(opType, change.getName().toString()));
            if (dryRun()) {
                return ResultCode.SUCCESS.intValue();
            }

            try {
                Result r = connection.modify(change);
                printResult(opType, change.getName().toString(), r);
                return r.getResultCode().intValue();
            } catch (final LdapException ere) {
                return printErrorMessage(LDAPModify.this, ere, ERR_LDAP_MODIFY_FAILED);
            }
        }

        private void printResult(final String operationType, final String name, final Result r) {
            final ResultCode rc = r.getResultCode();
            if (ResultCode.SUCCESS != rc && ResultCode.REFERRAL != rc) {
                printErrorMessage(LDAPModify.this, r, ERR_LDAP_MODIFY_FAILED);
            } else {
                printSuccessMessage(LDAPModify.this, r, operationType, name);
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
        }

        private boolean dryRun() {
            return connection == null;
        }
        // TODO: CSN control
    }

    private EntryWriter writer;
    private Collection<Control> controls;
    private BooleanArgument verbose;

    @VisibleForTesting
    LDAPModify(final PrintStream out, final PrintStream err) {
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
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPModify.class.getName())
                .toolDescription(INFO_LDAPMODIFY_TOOL_DESCRIPTION.get())
                .trailingArguments("[changes_files ...]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPMODIFY.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        BooleanArgument continueOnError;
        BooleanArgument noop;
        BooleanArgument showUsage;
        BooleanArgument defaultAdd;
        StringArgument assertionFilter;
        StringArgument controlStr;
        StringArgument filename;
        StringArgument postReadAttributes;
        StringArgument preReadAttributes;
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

            filename =
                    StringArgument.builder(OPTION_LONG_FILENAME)
                            .shortIdentifier(OPTION_SHORT_FILENAME)
                            .description(INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get())
                            .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                            .hidden()
                            .buildAndAddToParser(argParser);

            proxyAuthzID =
                    StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                            .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                            .description(INFO_DESCRIPTION_PROXY_AUTHZID.get())
                            .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            assertionFilter =
                    StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                            .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                            .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            preReadAttributes =
                    StringArgument.builder("preReadAttributes")
                            .description(INFO_DESCRIPTION_PREREAD_ATTRS.get())
                            .valuePlaceholder(INFO_ATTRIBUTE_LIST_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            postReadAttributes =
                    StringArgument.builder("postReadAttributes")
                            .description(INFO_DESCRIPTION_POSTREAD_ATTRS.get())
                            .valuePlaceholder(INFO_ATTRIBUTE_LIST_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            controlStr = controlArgument();
            argParser.addArgument(controlStr);

            continueOnError = continueOnErrorArgument();
            argParser.addArgument(continueOnError);

            /* Legacy argument in ForgeRock's OpenDJ to avoid failing when running older scripts.
            The current behaviour is the opposite of Forgerock (defaultAdd false),
            as we treat records with no changetype as add operations by default. */
            defaultAdd = defaultAddArgument();
            argParser.addArgument(defaultAdd);

            noop = noOpArgument();
            argParser.addArgument(noop);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        argParser.parseArguments(args, getErrStream(), connectionFactoryProvider);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        controls = readControls(controlStr);
        if (proxyAuthzID.isPresent()) {
            controls.add(ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue()));
        }

        if (assertionFilter.isPresent()) {
            controls.add(readAssertionControl(assertionFilter.getValue()));
        }
        addReadAttributesToControl(controls, preReadAttributes, true);
        addReadAttributesToControl(controls, postReadAttributes, false);

        writer = new LDIFEntryWriter(getOutputStream());
        ChangeRecordReader reader = null;
        try (final Connection connection = getConnection(argParser.getConnectionFactory(),
                                                         argParser.getBindRequest(),
                                                         noop,
                                                         this)) {
            reader = createLDIFChangeRecordReader(filename, argParser.getTrailingArguments());
            try (final EntryWriter w = writer) {
                return processModify(connection, reader, continueOnError.isPresent());
            } catch (final IOException e) {
                throw newToolException(e, ResultCode.UNDEFINED, ERR_LDAP_MODIFY_WRITTING_ENTRIES.get(e.getMessage()));
            }
        } finally {
            closeSilently(reader);
        }
    }

    private int processModify(final Connection connection,
                              final ChangeRecordReader reader,
                              final boolean continueOnError) {
        final VisitorImpl visitor = new VisitorImpl(connection);
        while (true) {
            try {
                if (!reader.hasNext()) {
                    return ResultCode.SUCCESS.intValue();
                }
                final ChangeRecord cr = reader.readChangeRecord();
                final int result = cr.accept(visitor, null);
                if (result != 0 && !continueOnError) {
                    return result;
                }
            } catch (final IOException ioe) {
                errPrintln(ERR_LDIF_FILE_READ_ERROR.get(ioe.getLocalizedMessage()));
                if (!continueOnError) {
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        }
    }

    private ChangeRecordReader createLDIFChangeRecordReader(final StringArgument fileNameArg,
                                                            final List<String> trailingArgs)
            throws LDAPToolException {
        final boolean fileNameArgUsed = fileNameArg.isPresent();
        final boolean readFromStdinTokenUsed = trailingArgs.size() == 1
                                            && USE_SYSTEM_STREAM_TOKEN.equals(trailingArgs.get(0));
        final boolean readChangesFromStdin = readFromStdinTokenUsed || !fileNameArgUsed && trailingArgs.isEmpty();
        if (readChangesFromStdin) {
            return new LDIFChangeRecordReader(getInputStream());
        }

        final List<String> filesToRead = new ArrayList<>();
        if (fileNameArgUsed) {
            filesToRead.add(fileNameArg.getValue());
        }
        filesToRead.addAll(trailingArgs);
        return new LDIFChangeRecordReader(Utils.getLinesFromFiles(filesToRead));
    }

    private void addReadAttributesToControl(
            final Collection<Control> controls, final StringArgument attributesArg, final boolean preRead) {
        if (attributesArg.isPresent()) {
            final StringTokenizer tokenizer = new StringTokenizer(attributesArg.getValue(), ", ");
            final List<String> attributes = new LinkedList<>();
            while (tokenizer.hasMoreTokens()) {
                attributes.add(tokenizer.nextToken());
            }
            controls.add(preRead ? PreReadRequestControl.newControl(true, attributes)
                                 : PostReadRequestControl.newControl(true, attributes));
        }
    }
}
