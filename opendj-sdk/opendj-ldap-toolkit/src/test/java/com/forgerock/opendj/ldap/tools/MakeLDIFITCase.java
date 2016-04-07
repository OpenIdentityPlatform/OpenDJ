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
 * Copyright 2013-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.util.Utils.*;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_GLOBAL_HELP_REFERENCE;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MakeLDIFITCase extends ToolsITCase {

    private static final String TEMP_OUTPUT_FILE = ".temp_test_file.ldif";
    private static final String TEST_RESOURCE_PATH = "src/test/resources";
    private static final String VALID_TEMPLATE_FILE_PATH =
            Paths.get(TEST_RESOURCE_PATH, "valid_test_template.ldif").toString();
    private static final boolean SUCCESS = true;
    private static final boolean FAILURE = false;

    private ByteStringBuilder out;
    private ByteStringBuilder err;
    private PrintStream outStream;
    private PrintStream errStream;

    @BeforeMethod
    private void refreshStreams() {
        out = new ByteStringBuilder();
        err = new ByteStringBuilder();
        outStream = new PrintStream(out.asOutputStream());
        errStream = new PrintStream(err.asOutputStream());
    }

    @AfterMethod
    private void closeStreams() {
        closeSilently(outStream, errStream);
    }

    @DataProvider
    Object[][] validArguments() throws Exception {
        return new Object[][] {
            { // check that help message is displayed
              args("-H"),
              expectedOutput(INFO_MAKELDIF_TOOL_DESCRIPTION.get()) },

            { args("-c", "numusers=1", "example.template"),
              // 2 base entries + users
              expectedOutput(INFO_MAKELDIF_PROCESSING_COMPLETE.get(3)) },

            { args("-c", "numusers=5", "example.template"),
              // 2 base entries + users
              expectedOutput(INFO_MAKELDIF_PROCESSING_COMPLETE.get(7)) },
        };
    }

    @DataProvider
    Object[][] invalidArguments() throws Exception {
        return new Object[][] {
            { // check that usage is written to output when arguments are invalid
              args(),
              expectedOutput(INFO_GLOBAL_HELP_REFERENCE.get("java " + MakeLDIF.class.getCanonicalName())) },

            { // Check if the help reference message is prompted if arguments failed to be parsed
              args("-42"),
              expectedOutput(INFO_GLOBAL_HELP_REFERENCE.get("java " + MakeLDIF.class.getCanonicalName())) },

            { args("-r", "unknown/path" , "example.template"),
              expectedOutput(ERR_LDIF_GEN_TOOL_NO_SUCH_RESOURCE_DIRECTORY.get("unknown/path")) },

            { args("-o", "unknown/path" , "example.template"),
              expectedOutput(ERR_MAKELDIF_UNABLE_TO_CREATE_LDIF.get("unknown/path", "")) },

            { args("-s", "non-numeric" , "example.template"),
              expectedOutput(ERR_ERROR_PARSING_ARGS.get("")) },
        };
    }

    @Test(dataProvider = "validArguments")
    public void testMakeLDIFValidUseCases(final String[] arguments, final LocalizableMessage expectedOut)
            throws Exception {
        run(arguments, SUCCESS, expectedOut);
    }

    @Test(dataProvider = "invalidArguments")
    public void testMakeLDIFInvalidUseCases(final String[] arguments, final LocalizableMessage expectedErr)
            throws Exception {
        run(arguments, FAILURE, expectedErr);
    }

    /** See OPENDJ-2505 */
    @Test
    public void testMakeLDIFInvalidLineFolding() throws Exception {
        final LocalizableMessage expectedOutput = ERR_LDIF_GEN_TOOL_EXCEPTION_DURING_PARSE.get(
                ERR_TEMPLATE_FILE_INVALID_LEADING_SPACE.get(
                        27, " \"lineFoldingTest\":\\[\"This line should not be accepted by the parser\"\\],"));
        run(args("src/test/resources/invalid_test_template.ldif"), FAILURE, expectedOutput);
    }

    /** See OPENDJ-2505 */
    @Test
    public void testMakeLDIFSupportsLineFolding() throws Exception {
        final Path tempOutputFile = Paths.get(TEST_RESOURCE_PATH, TEMP_OUTPUT_FILE);
        run(args("-o", tempOutputFile.toString(), VALID_TEMPLATE_FILE_PATH),
                SUCCESS, INFO_MAKELDIF_PROCESSING_COMPLETE.get(2));
        assertFilesAreEquals(TEMP_OUTPUT_FILE, "expected_output.ldif");
        Files.delete(tempOutputFile);
    }

    /** See OPENDJ-2505 and OPENDJ-2754 */
    @Test
    public void testMakeLDIFSupportsLineFoldingAndLineWrapping() throws Exception {
        final Path tempOutputFile = Paths.get(TEST_RESOURCE_PATH, TEMP_OUTPUT_FILE);
        run(args("-o", tempOutputFile.toString(), "-w", "80", VALID_TEMPLATE_FILE_PATH),
                SUCCESS, INFO_MAKELDIF_PROCESSING_COMPLETE.get(2));
        assertFilesAreEquals(TEMP_OUTPUT_FILE, "expected_output_80_column.ldif");
        Files.delete(tempOutputFile);
    }

    private void assertFilesAreEquals(final String outputFile, final String expectedOutputFileName) throws IOException {
        assertThat(Files.readAllBytes(Paths.get(TEST_RESOURCE_PATH, outputFile))).isEqualTo(
                   Files.readAllBytes(Paths.get(TEST_RESOURCE_PATH, expectedOutputFileName)));
    }

    private void run(final String[] arguments, final boolean expectsSuccess, final LocalizableMessage expectedOutput)
            throws Exception {
        final MakeLDIF makeLDIF = new MakeLDIF(outStream, errStream);
        int retCode = makeLDIF.run(arguments);
        checkOuputStreams(out, err, expectedOutput, "");
        if (expectsSuccess) {
            assertThat(retCode).isEqualTo(0);
        } else {
            assertThat(retCode).isNotEqualTo(0);
        }
    }

    /** A message the error output is expected to contain. */
    private LocalizableMessage expectedOutput(LocalizableMessage val) {
        return val;
    }

}
