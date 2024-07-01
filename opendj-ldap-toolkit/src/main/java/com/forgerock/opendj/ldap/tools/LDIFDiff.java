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
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static com.forgerock.opendj.ldap.tools.Utils.computeWrapColumn;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolInputStream;
import static com.forgerock.opendj.ldap.tools.Utils.getLDIFToolOutputStream;
import static com.forgerock.opendj.ldap.tools.Utils.parseArguments;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import com.forgerock.opendj.cli.IntegerArgument;
import org.forgerock.i18n.LocalizableException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldif.ChangeRecordReader;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFChangeRecordWriter;
import org.forgerock.opendj.ldif.LDIFEntryReader;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * This utility can be used to compare two LDIF files and report the differences
 * in LDIF format.
 */
public final class LDIFDiff extends ToolConsoleApplication {

    static final int NO_DIFFERENCES_FOUND = 0;
    static final int DIFFERENCES_FOUND = 1;

    /**
     * The main method for ldifdiff tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDIFDiff(System.out, System.err), args);
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
        return runTool(new LDIFDiff(out, err), args);
    }

    @VisibleForTesting
    LDIFDiff(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        final ArgumentParser argParser = LDAPToolArgumentParser.builder(LDIFDiff.class.getName())
                .toolDescription(INFO_LDIFDIFF_TOOL_DESCRIPTION.get())
                .trailingArguments(2, "source target")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDIFDIFF.get());

        final BooleanArgument showUsage;
        final StringArgument outputFilename;
        final IntegerArgument wrapColumn;
        try {
            outputFilename =
                    StringArgument.builder(OPTION_LONG_OUTPUT_LDIF_FILENAME)
                            .shortIdentifier(OPTION_SHORT_OUTPUT_LDIF_FILENAME)
                            .description(INFO_LDIFDIFF_DESCRIPTION_OUTPUT_FILENAME.get(
                                    INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get()))
                            .defaultValue("stdout")
                            .valuePlaceholder(INFO_OUTPUT_LDIF_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            wrapColumn = wrapColumnArgument();
            argParser.addArgument(wrapColumn);

            showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        parseArguments(argParser, getErrorStream(), args);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        InputStream sourceInputStream = null;
        InputStream targetInputStream = null;
        OutputStream outputStream = null;

        try {
            final List<String> trailingArguments = argParser.getTrailingArguments();
            sourceInputStream = getLDIFToolInputStream(this, trailingArguments.get(0));
            targetInputStream = getLDIFToolInputStream(this, trailingArguments.get(1));
            outputStream = getLDIFToolOutputStream(this, outputFilename);

            if (System.in == sourceInputStream && System.in == targetInputStream) {
                throw newToolParamException(ERR_LDIFDIFF_MULTIPLE_USES_OF_STDIN.get());
            }

            try (LDIFEntryReader sourceReader = new LDIFEntryReader(sourceInputStream);
                 LDIFEntryReader targetReader = new LDIFEntryReader(targetInputStream);
                 LDIFChangeRecordWriter outputWriter = new LDIFChangeRecordWriter(outputStream)) {
                outputWriter.setWrapColumn(computeWrapColumn(wrapColumn));
                final ChangeRecordReader changes = LDIF.diff(sourceReader, targetReader);
                LDIF.copyTo(changes, outputWriter);
                if (outputWriter.containsChanges()) {
                    return DIFFERENCES_FOUND;
                } else {
                    outputWriter.writeComment(INFO_LDIFDIFF_NO_DIFFERENCES.get().toString());
                    return NO_DIFFERENCES_FOUND;
                }
            }
        } catch (final IOException e) {
            if (e instanceof LocalizableException) {
                errPrintln(ERR_LDIFDIFF_DIFF_FAILED.get(((LocalizableException) e).getMessageObject()));
            } else {
                errPrintln(ERR_LDIFDIFF_DIFF_FAILED.get(e.getLocalizedMessage()));
            }
            return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ae.getMessageObject());
        } finally {
            closeSilently(sourceInputStream, targetInputStream, outputStream);
        }
    }
}
