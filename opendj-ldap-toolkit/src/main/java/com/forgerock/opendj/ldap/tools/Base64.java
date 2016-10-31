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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_FILEARG_NO_SUCH_FILE;
import static com.forgerock.opendj.cli.CommonArguments.showUsageArgument;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.cli.Utils.throwIfArgumentsConflict;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_BASE64_DECODE_INVALID_LENGTH;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolException;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolExceptionAlreadyPrinted;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.parseArguments;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.annotations.VisibleForTesting;

/**
 * Tool that can be used for performing base64 encoding and decoding.
 * <p>
 * Base64 is a mechanism for encoding binary data in ASCII form by converting
 * sets of three bytes with eight significant bits each to sets of four bytes
 * with six significant bits each.
 */
public final class Base64 extends ToolConsoleApplication {

    /**
     * The main method for base64 tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new Base64(System.out, System.err), args);
    }

    @VisibleForTesting
    Base64(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    int run(final String... args) throws LDAPToolException {
        final SubCommandArgumentParser argParser =
                new SubCommandArgumentParser(Base64.class.getName(), INFO_BASE64_TOOL_DESCRIPTION.get(), false);
        argParser.setShortToolDescription(REF_SHORT_DESC_BASE64.get());
        argParser.setVersionHandler(newSdkVersionHandler());

        final BooleanArgument showUsage;

        final SubCommand decodeSubCommand;
        final StringArgument encodedData;
        final StringArgument encodedFile;
        final StringArgument toRawFile;

        final SubCommand encodeSubCommand;
        final StringArgument rawData;
        final StringArgument rawFile;
        final StringArgument toEncodedFile;

        try {
            decodeSubCommand = new SubCommand(argParser, "decode", INFO_BASE64_DECODE_DESCRIPTION.get());
            encodeSubCommand = new SubCommand(argParser, "encode", INFO_BASE64_ENCODE_DESCRIPTION.get());

            encodedData = StringArgument.builder("encodedData")
                    .shortIdentifier('d')
                    .description(INFO_BASE64_ENCODED_DATA_DESCRIPTION.get())
                    .valuePlaceholder(INFO_DATA_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(decodeSubCommand);

            encodedFile = StringArgument.builder("encodedDataFile")
                    .shortIdentifier('f')
                    .description(INFO_BASE64_ENCODED_FILE_DESCRIPTION.get())
                    .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(decodeSubCommand);

            toRawFile = StringArgument.builder("toRawFile")
                    .shortIdentifier('o')
                    .description(INFO_BASE64_TO_RAW_FILE_DESCRIPTION.get())
                    .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(decodeSubCommand);

            rawData = StringArgument.builder("rawData")
                    .shortIdentifier('d')
                    .description(INFO_BASE64_RAW_DATA_DESCRIPTION.get())
                    .valuePlaceholder(INFO_DATA_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(encodeSubCommand);

            rawFile = StringArgument.builder("rawDataFile")
                    .shortIdentifier('f')
                    .description(INFO_BASE64_RAW_FILE_DESCRIPTION.get())
                    .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(encodeSubCommand);

            toEncodedFile = StringArgument.builder("toEncodedFile")
                    .shortIdentifier('o')
                    .description(INFO_BASE64_TO_ENCODED_FILE_DESCRIPTION.get())
                    .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                    .buildAndAddToSubCommand(encodeSubCommand);

            final List<SubCommand> subCommandList = new ArrayList<>(2);
            subCommandList.add(decodeSubCommand);
            subCommandList.add(encodeSubCommand);

            showUsage = showUsageArgument();
            argParser.addGlobalArgument(showUsage);
            argParser.setUsageGroupArgument(showUsage, subCommandList);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        parseArguments(argParser, getErrStream(), args);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        try {
            throwIfArgumentsConflict(encodedData, encodedFile);
            throwIfArgumentsConflict(rawData, rawFile);
        } catch (final ArgumentException e) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(e.getMessage()));
            throw newToolParamException(e, e.getMessageObject());
        }

        final SubCommand subCommand = argParser.getSubCommand();
        if (subCommand == null) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_BASE64_NO_SUBCOMMAND_SPECIFIED.get());
            throw newToolExceptionAlreadyPrinted(null, ResultCode.CLIENT_SIDE_PARAM_ERROR);
        }

        if (subCommand.getName().equals(encodeSubCommand.getName())) {
            return encode(rawData, rawFile, toEncodedFile);
        } else if (subCommand.getName().equals(decodeSubCommand.getName())) {
            return decode(encodedData, encodedFile, toRawFile);
        } else {
            argParser.displayMessageAndUsageReference(
                    getErrStream(), ERR_BASE64_UNKNOWN_SUBCOMMAND.get(subCommand.getName()));
            throw newToolExceptionAlreadyPrinted(null, ResultCode.CLIENT_SIDE_PARAM_ERROR);
        }
    }

    private int encode(final StringArgument rawDataArg,
                       final StringArgument rawDataFilePathArg,
                       final StringArgument toEncodeFilePathArg) throws LDAPToolException {
        byte[] dataToEncode;
        if (rawDataArg.isPresent()) {
            try {
                dataToEncode = rawDataArg.getValue().getBytes("UTF-8");
            } catch (final UnsupportedEncodingException e) {
                throw newToolException(e, ResultCode.OPERATIONS_ERROR,
                                       ERR_BASE64_ERROR_DECODING_RAW_DATA.get(e.getMessage()));
            }
        } else {
            final boolean readFromFile = rawDataFilePathArg.isPresent();
            InputStream inputStream = null;
            final String rawDataFilePath = rawDataFilePathArg.getValue();
            try {
                inputStream = readFromFile ? new FileInputStream(rawDataFilePath) : getInputStream();
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    baos.write(buffer, 0, bytesRead);
                }

                dataToEncode = baos.toByteArray();
            } catch (final FileNotFoundException e) {
                throw newToolParamException(
                        e, ERR_FILEARG_NO_SUCH_FILE.get(rawDataFilePath, rawDataFilePathArg.getLongIdentifier()));
            } catch (final Exception e) {
                throw newToolException(e, ResultCode.OPERATIONS_ERROR,
                                       ERR_BASE64_CANNOT_READ_RAW_DATA.get(getExceptionMessage(e)));
            } finally {
                if (readFromFile) {
                    closeSilently(inputStream);
                }
            }
        }

        final String base64Data = org.forgerock.util.encode.Base64.encode(dataToEncode);
        if (toEncodeFilePathArg.isPresent()) {
            try (final BufferedWriter writer = new BufferedWriter(new FileWriter(toEncodeFilePathArg.getValue()))) {
                writer.write(base64Data);
                writer.newLine();
            } catch (final Exception e) {
                throw newToolException(e, ResultCode.OPERATIONS_ERROR,
                                       ERR_BASE64_CANNOT_WRITE_ENCODED_DATA.get(getExceptionMessage(e)));
            }
        } else {
            getOutputStream().println(base64Data);
        }
        return ResultCode.SUCCESS.intValue();
    }

    private int decode(final StringArgument encodedDataArg,
                       final StringArgument encodedDataFilePathArg,
                       final StringArgument toRawFilePathArg) throws LDAPToolException {
        String dataToDecode = null;
        if (encodedDataArg.isPresent()) {
            dataToDecode = encodedDataArg.getValue();
        } else {
            final boolean readFromFile = encodedDataFilePathArg.isPresent();
            BufferedReader reader = null;
            final String encodedDataFilePath = encodedDataFilePathArg.getValue();
            try {
                reader = new BufferedReader(readFromFile ? new FileReader(encodedDataFilePath)
                                                         : new InputStreamReader(System.in));
                final StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    final StringTokenizer tokenizer = new StringTokenizer(line);
                    while (tokenizer.hasMoreTokens()) {
                        buffer.append(tokenizer.nextToken());
                    }
                }

                dataToDecode = buffer.toString();
            } catch (final FileNotFoundException e) {
                throw newToolParamException(
                        e, ERR_FILEARG_NO_SUCH_FILE.get(encodedDataFilePath,
                                                        encodedDataFilePathArg.getLongIdentifier()));
            } catch (final Exception e) {
                throw newToolException(e, ResultCode.OPERATIONS_ERROR,
                                       ERR_BASE64_CANNOT_READ_ENCODED_DATA.get(getExceptionMessage(e)));
            } finally {
                if (readFromFile) {
                    closeSilently(reader);
                }
            }
        }

        if (dataToDecode.length() % 4 != 0) {
            throw newToolParamException(ERR_BASE64_DECODE_INVALID_LENGTH.get(dataToDecode));
        }

        final byte[] decodedData = org.forgerock.util.encode.Base64.decode(dataToDecode);
        if (decodedData == null) {
            throw newToolParamException(ERR_BASE64_ERROR_DECODING_RAW_DATA.get(dataToDecode));
        }

        try {
            if (toRawFilePathArg.isPresent()) {
                try (final FileOutputStream outputStream = new FileOutputStream(toRawFilePathArg.getValue())) {
                    outputStream.write(decodedData);
                }
            } else {
                try (final PrintStream outputPrintStream = getOutputStream()) {
                    outputPrintStream.write(decodedData);
                }
            }
        } catch (final Exception e) {
            throw newToolException(e, ResultCode.OPERATIONS_ERROR,
                                   ERR_BASE64_CANNOT_WRITE_RAW_DATA.get(getExceptionMessage(e)));
        }
        return ResultCode.SUCCESS.intValue();
    }
}
