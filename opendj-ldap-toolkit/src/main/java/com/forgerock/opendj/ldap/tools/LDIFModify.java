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
 * Copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_LONG_OUTPUT_LDIF_FILENAME;
import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_SHORT_OUTPUT_LDIF_FILENAME;
import static com.forgerock.opendj.cli.ArgumentConstants.USE_SYSTEM_STREAM_TOKEN;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.computeWrapColumn;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolInputStream;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolOutputStream;
import static com.forgerock.opendj.ldap.tools.Utils.parseArguments;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static org.forgerock.util.Utils.closeSilently;
import static com.forgerock.opendj.cli.CommonArguments.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFChangeRecordReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.opendj.ldif.RejectedChangeRecordListener;
import org.forgerock.util.annotations.VisibleForTesting;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A tool that can be used to issue update (Add/Delete/Modify/ModifyDN) requests
 * to a set of entries contained in an LDIF file.
 */
public final class LDIFModify extends ToolConsoleApplication {

    /**
     * The main method for ldifmodify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDIFModify(System.out, System.err), args);
    }

    /**
     * This method should be used to run this tool programmatically.
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
        return runTool(new LDIFModify(out, err), args);
    }

    @VisibleForTesting
    LDIFModify(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        // Create the command-line argument parser for use with this program.
        final ArgumentParser argParser = LDAPToolArgumentParser.builder(LDIFModify.class.getName())
                .toolDescription(INFO_LDIFMODIFY_TOOL_DESCRIPTION.get())
                .trailingArgumentsUnbounded(1, "source_file [changes_files...]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDIFMODIFY.get());

        final BooleanArgument continueOnError;
        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        final IntegerArgument wrapColumn;
        try {
            outputFilename =
                    StringArgument.builder(OPTION_LONG_OUTPUT_LDIF_FILENAME)
                            .shortIdentifier(OPTION_SHORT_OUTPUT_LDIF_FILENAME)
                            .description(INFO_LDIFMODIFY_DESCRIPTION_OUTPUT_FILENAME.get(
                                    INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()))
                            .defaultValue("stdout")
                            .valuePlaceholder(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            continueOnError = continueOnErrorArgument();
            argParser.addArgument(continueOnError);

            wrapColumn = wrapColumnArgument();
            argParser.addArgument(wrapColumn);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        parseArguments(argParser, getErrStream(), args);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        final List<String> trailingArguments = argParser.getTrailingArguments();
        LDIFEntryReader sourceReader = null;
        LDIFChangeRecordReader changesReader = null;
        try (InputStream sourceInputStream = getLDIFToolInputStream(this, trailingArguments.get(0));
             OutputStream outputStream = getLDIFToolOutputStream(this, outputFilename);
             LDIFEntryWriter outputWriter = new LDIFEntryWriter(outputStream)) {
            outputWriter.setWrapColumn(computeWrapColumn(wrapColumn));
            final int nbTrailingArgs = trailingArguments.size();
            final boolean readChangesFromStdin = nbTrailingArgs == 1
                    || (nbTrailingArgs == 2 && USE_SYSTEM_STREAM_TOKEN.equals(trailingArguments.get(1)));
            if (getInputStream() == sourceInputStream && readChangesFromStdin) {
                throw newToolParamException(ERR_LDIFMODIFY_MULTIPLE_USES_OF_STDIN.get());
            }

            sourceReader = new LDIFEntryReader(sourceInputStream);
            if (readChangesFromStdin) {
                changesReader = new LDIFChangeRecordReader(getInputStream());
            } else {
                changesReader = new LDIFChangeRecordReader(
                        Utils.getLinesFromFiles(trailingArguments.subList(1, nbTrailingArgs)));
            }

            final RejectedChangeRecordListener listener = new RejectedChangeRecordListener() {
                @Override
                public Entry handleDuplicateEntry(final AddRequest change, final Entry existingEntry)
                        throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleDuplicateEntry(change, existingEntry);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                    return change;
                }

                @Override
                public Entry handleDuplicateEntry(final ModifyDNRequest change,
                        final Entry existingEntry, final Entry renamedEntry) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleDuplicateEntry(
                                change, existingEntry, renamedEntry);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                    return renamedEntry;
                }

                @Override
                public void handleRejectedChangeRecord(
                        final AddRequest change, final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change, reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(
                        final DeleteRequest change, final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change, reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(
                        final ModifyDNRequest change, final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change, reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(
                        final ModifyRequest change, final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change, reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                private void logErrorOrFail(final DecodeException e) throws DecodeException {
                    if (continueOnError.isPresent()) {
                        errPrintln(e.getMessageObject());
                    } else {
                        throw e;
                    }
                }
            };

            LDIF.copyTo(LDIF.patch(sourceReader, changesReader, listener), outputWriter);
        } catch (final IOException e) {
            if (e instanceof LocalizableException) {
                errPrintln(ERR_LDIFMODIFY_PATCH_FAILED.get(((LocalizableException) e).getMessageObject()));
            } else {
                errPrintln(ERR_LDIFMODIFY_PATCH_FAILED.get(e.getLocalizedMessage()));
            }
            return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ae.getMessageObject());
        } finally {
            closeSilently(sourceReader, changesReader);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
