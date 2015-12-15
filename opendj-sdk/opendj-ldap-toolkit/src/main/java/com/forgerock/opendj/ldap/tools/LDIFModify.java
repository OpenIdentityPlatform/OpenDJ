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
 *      Copyright 2012-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_LONG_OUTPUT_LDIF_FILENAME;
import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_SHORT_OUTPUT_LDIF_FILENAME;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static org.forgerock.util.Utils.closeSilently;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A tool that can be used to issue update (Add/Delete/Modify/ModifyDN) requests
 * to a set of entries contained in an LDIF file.
 */
public final class LDIFModify extends ConsoleApplication {
    /**
     * The main method for LDIFModify tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new LDIFModify().run(args);
        System.exit(filterExitCode(retCode));
    }

    private LDIFModify() {
        // Nothing to do.
    }

    private int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDIFMODIFY_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser = new ArgumentParser(
            LDIFModify.class.getName(), toolDescription, false, true, 1, 2, "source [changes]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDIFMODIFY.get());

        final BooleanArgument continueOnError;
        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        try {
            outputFilename =
                    new StringArgument("outputFilename", OPTION_SHORT_OUTPUT_LDIF_FILENAME,
                            OPTION_LONG_OUTPUT_LDIF_FILENAME, false, false, true,
                            INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get(), "stdout", null,
                            INFO_LDIFMODIFY_DESCRIPTION_OUTPUT_FILENAME
                                    .get(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()));
            argParser.addArgument(outputFilename);

            continueOnError = CommonArguments.getContinueOnError();
            argParser.addArgument(continueOnError);

            showUsage = CommonArguments.getShowUsage();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
            errPrintln(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information,
            // then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return ResultCode.SUCCESS.intValue();
            }
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        InputStream sourceInputStream = null;
        InputStream changesInputStream = null;
        OutputStream outputStream = null;
        LDIFEntryReader sourceReader = null;
        LDIFChangeRecordReader changesReader = null;
        LDIFEntryWriter outputWriter = null;

        try {
            // First source file.
            final List<String> trailingArguments = argParser.getTrailingArguments();
            if (!"-".equals(trailingArguments.get(0))) {
                try {
                    sourceInputStream = new FileInputStream(trailingArguments.get(0));
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(trailingArguments.get(0), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Patch file.
            if (trailingArguments.size() > 1 && !"-".equals(trailingArguments.get(1))) {
                try {
                    changesInputStream = new FileInputStream(trailingArguments.get(1));
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(trailingArguments.get(1), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Output file.
            if (outputFilename.isPresent() && !"-".equals(outputFilename.getValue())) {
                try {
                    outputStream = new FileOutputStream(outputFilename.getValue());
                } catch (final FileNotFoundException e) {
                    final LocalizableMessage message =
                            ERR_LDIF_FILE_CANNOT_OPEN_FOR_WRITE.get(outputFilename.getValue(), e
                                    .getLocalizedMessage());
                    errPrintln(message);
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            // Default to stdin/stdout for all streams if not specified.
            if (sourceInputStream == null) {
                // Command line parameter was "-".
                sourceInputStream = System.in;
            }

            if (changesInputStream == null) {
                changesInputStream = System.in;
            }

            if (outputStream == null) {
                outputStream = System.out;
            }

            /* Check that we are not attempting to read both the source and changes from stdin. */
            if (sourceInputStream == changesInputStream) {
                final LocalizableMessage message = ERR_LDIFMODIFY_MULTIPLE_USES_OF_STDIN.get();
                errPrintln(message);
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }

            // Apply the changes.
            sourceReader = new LDIFEntryReader(sourceInputStream);
            changesReader = new LDIFChangeRecordReader(changesInputStream);
            outputWriter = new LDIFEntryWriter(outputStream);

            final RejectedChangeRecordListener listener = new RejectedChangeRecordListener() {
                @Override
                public Entry handleDuplicateEntry(final AddRequest change, final Entry existingEntry)
                        throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleDuplicateEntry(change,
                                existingEntry);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                    return change;
                }

                @Override
                public Entry handleDuplicateEntry(final ModifyDNRequest change,
                        final Entry existingEntry, final Entry renamedEntry) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleDuplicateEntry(change,
                                existingEntry, renamedEntry);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                    return renamedEntry;
                }

                @Override
                public void handleRejectedChangeRecord(final AddRequest change,
                        final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change,
                                reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(final DeleteRequest change,
                        final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change,
                                reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(final ModifyDNRequest change,
                        final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change,
                                reason);
                    } catch (final DecodeException e) {
                        logErrorOrFail(e);
                    }
                }

                @Override
                public void handleRejectedChangeRecord(final ModifyRequest change,
                        final LocalizableMessage reason) throws DecodeException {
                    try {
                        RejectedChangeRecordListener.FAIL_FAST.handleRejectedChangeRecord(change,
                                reason);
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
                errPrintln(ERR_LDIFMODIFY_PATCH_FAILED.get(((LocalizableException) e)
                        .getMessageObject()));
            } else {
                errPrintln(ERR_LDIFMODIFY_PATCH_FAILED.get(e.getLocalizedMessage()));
            }
            return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
        } finally {
            closeSilently(sourceReader, changesReader, outputWriter);
            closeSilently(sourceInputStream, changesInputStream, outputStream);
        }

        return ResultCode.SUCCESS.intValue();
    }
}
