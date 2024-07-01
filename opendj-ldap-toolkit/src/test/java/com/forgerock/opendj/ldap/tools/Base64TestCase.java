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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_FILEARG_NO_SUCH_FILE;
import static com.forgerock.opendj.cli.CliMessages.ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID;
import static com.forgerock.opendj.cli.CliMessages.ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID;
import static com.forgerock.opendj.cli.CliMessages.ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_BASE64_DECODE_INVALID_LENGTH;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_BASE64_ERROR_DECODING_RAW_DATA;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class defines a set of tests for the {@link com.forgerock.opendj.ldap.tools.Base64} class. */
@Test
public final class Base64TestCase extends ToolsTestCase {

    private static final boolean ENCODE = true;
    private static final boolean DECODE = false;

    @BeforeMethod
    private void testSetup() {
        refreshStream();
    }

    @AfterMethod
    private void testTearDown() {
        closeStream();
    }

    @Test
    public void testNoSubcommandProvided() throws Exception {
        final int res = runTool();
        assertThat(res).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(err.toString()).contains(ToolsMessages.ERR_BASE64_NO_SUBCOMMAND_SPECIFIED.get().toString());
        assertThat(out.toString()).isEmpty();
    }

    @DataProvider
    public Object[][] invalidArgumentsEncode() throws Exception {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        addValueNeededShortArgs(argLists, reasonList, "d", "f", "o");

        argLists.add(args("-I"));
        reasonList.add(ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.get("I"));

        argLists.add(args("--invalidLongArgument"));
        reasonList.add(ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID.get("invalidLongArgument"));

        argLists.add(args("--rawData"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("rawdata"));

        argLists.add(args("--rawDataFile"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("rawdatafile"));

        argLists.add(args("--toEncodedFile"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("toencodedfile"));

        argLists.add(args("-f", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "rawDataFile"));

        argLists.add(args("--rawDataFile", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "rawDataFile"));
        return toDataProviderArray(argLists, reasonList);
    }

    @Override
    @Test(dataProvider = "invalidArgumentsEncode")
    public void testInvalidArguments(final String[] args, String invalidReason) throws LDAPToolException {
        assertThat(testBase64(ENCODE, args)).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(invalidReason);
        assertThat(out.toString()).isEmpty();
    }

    @DataProvider
    public Object[][] invalidArgumentsDecode() throws Exception {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        addValueNeededShortArgs(argLists, reasonList, "d", "f", "o");

        argLists.add(args("-I"));
        reasonList.add(ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.get("I"));

        argLists.add(args("--invalidLongArgument"));
        reasonList.add(ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID.get("invalidLongArgument"));

        argLists.add(args("--encodedData"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("encodeddata"));

        argLists.add(args("--encodedDataFile"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("encodeddatafile"));

        argLists.add(args("--toRawFile"));
        reasonList.add(ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get("torawfile"));

        argLists.add(args("-f", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "encodedDataFile"));

        argLists.add(args("--encodedDataFile", "no.such.file"));
        reasonList.add(ERR_FILEARG_NO_SUCH_FILE.get("no.such.file", "encodedDataFile"));
        return toDataProviderArray(argLists, reasonList);
    }

    @DataProvider
    public Object[][] invalidEncodedDataLength() {
        // FIXME: fix cases ==== and ==x=
        return new Object[][] { { "=" }, { "==" }, { "===" }, { "A" }, { "AA" }, { "AAA" } };
    }

    @Test(dataProvider = "invalidEncodedDataLength")
    public void testDecodeInvalidDataLength(final String encodedData) throws Exception {
        final int res = testBase64(DECODE, "-d", encodedData);
        assertThat(res).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(ERR_BASE64_DECODE_INVALID_LENGTH.get(encodedData));
        assertThat(out.toString()).isEmpty();
    }

    @DataProvider
    public Object[][] invalidEncodedData() {
        return new Object[][] {
            { "AA`=" }, { "AA~=" }, { "AA!=" }, { "AA@=" },
            { "AA#=" }, { "AA$=" }, { "AA%=" }, { "AA^=" },
            { "AA*=" }, { "AA(=" }, { "AA)=" }, { "AA_=" },
            { "AA-=" }, { "AA{=" }, { "AA}=" }, { "AA|=" },
            { "AA[=" }, { "AA]=" }, { "AA\\=" }, { "AA;=" },
            { "AA'=" }, { "AA\"=" }, { "AA:=" }, { "AA,=" },
            { "AA.=" }, { "AA<=" }, { "AA>=" }, { "AA?=" },
            { "AA;=" }
        };
    }

    @Test(dataProvider = "invalidEncodedData")
    public void testDecodeInvalidData(final String encodedData) throws Exception {
        final int res = testBase64(DECODE, "-d", encodedData);
        assertThat(res).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(ERR_BASE64_ERROR_DECODING_RAW_DATA.get(encodedData));
        assertThat(out.toString()).isEmpty();
    }

    /**
     * Base 64 valid test data provider.
     *
     * @return Returns an array of decoded and valid encoded base64 data.
     */
    @DataProvider
    public Object[][] validData() {
        return new Object[][] {
            { "dont panic", "ZG9udCBwYW5pYw==" },

            { "Time is an illusion. Lunchtime doubly so.",
              "VGltZSBpcyBhbiBpbGx1c2lvbi4gTHVuY2h0aW1lIGRvdWJseSBzby4=" },

            { "I would far rather be happy than right any day.",
              "SSB3b3VsZCBmYXIgcmF0aGVyIGJlIGhhcHB5IHRoYW4gcmlnaHQgYW55IGRheS4=" },

            { "This must be Thursday, said Arthur to himself, sinking low over his beer. "
                      + "I never could get the hang of Thursdays",
              "VGhpcyBtdXN0IGJlIFRodXJzZGF5LCBzYWlkIEFydGh1ciB0byBoaW1zZWxmLCBzaW5raW5nIGxvdyBvdmVyIGhpcyBiZWVy"
                      + "LiBJIG5ldmVyIGNvdWxkIGdldCB0aGUgaGFuZyBvZiBUaHVyc2RheXM=" },
        };
    }

    @Test(dataProvider = "validData")
    public void testEncodeRawData(final String clearData, final String encodedData) throws Exception {
        testValidData(ENCODE, clearData, encodedData);
    }

    @Test(dataProvider = "validData")
    public void testDecodeRawData(final String encodedData, final String clearData) throws Exception {
        testValidData(DECODE, clearData, encodedData);
    }

    @Test(dataProvider = "validData")
    public void testEncodeDataWithFiles(final String clearData, final String encodedData) throws Exception {
        testValidDataWithFiles(ENCODE, clearData, encodedData);
    }

    @Test(dataProvider = "validData")
    public void testDecodeDataWithFiles(final String encodedData, final String clearData) throws Exception {
        testValidDataWithFiles(DECODE, clearData, encodedData);
    }

    private void testValidData(final boolean mode, final String dataIn, final String dataOut) throws LDAPToolException {
        final int res = testBase64(mode, "-d", dataIn);
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        assertThat(out.toString()).contains(dataOut);
    }

    private void testValidDataWithFiles(final boolean mode, final String dataIn, final String dataOut)
            throws Exception {
        final File dataInFile = File.createTempFile("Base64TestCase", ".data");
        Files.write(dataInFile.toPath(), dataIn.getBytes());
        final String dataOutFilePath = ToolsTestUtils.createTempFile();
        final int res = testBase64(mode, "-f", dataInFile.getAbsolutePath(), "-o", dataOutFilePath);
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).isEmpty();
        assertThat(readFirstLine(dataOutFilePath)).isEqualTo(dataOut);
    }

    private String readFirstLine(final String resultFilePath) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new FileReader(resultFilePath))) {
            return reader.readLine();
        }
    }

    private Integer testBase64(final boolean encode, final String... args) throws LDAPToolException {
        final List<String> arguments = new ArrayList<>();
        arguments.add(encode ? "encode" : "decode");
        arguments.addAll(Arrays.asList(args));
        return runTool(arguments.toArray(new String[arguments.size()]));
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new Base64(outStream, errStream);
    }
}
