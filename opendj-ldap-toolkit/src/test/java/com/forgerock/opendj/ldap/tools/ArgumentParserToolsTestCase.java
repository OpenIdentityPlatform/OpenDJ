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
 *  Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.INFO_GLOBAL_HELP_REFERENCE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ERROR_PARSING_ARGS;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ResultCode.CLIENT_SIDE_PARAM_ERROR;
import static org.forgerock.util.Utils.closeSilently;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class tests that help reference message is prompted for all tools when
 * no arguments are provided or if they failed to be parsed.
 */
@Test
public class ArgumentParserToolsTestCase extends ForgeRockTestCase {

    ByteStringBuilder out;
    ByteStringBuilder err;
    PrintStream outStream;
    PrintStream errStream;

    @BeforeMethod
    void refreshStream() {
        out = new ByteStringBuilder();
        err = new ByteStringBuilder();
        outStream = new PrintStream(out.asOutputStream());
        errStream = new PrintStream(err.asOutputStream());
    }

    @AfterMethod
    void closeStream() {
        closeSilently(outStream, errStream);
    }

    String errOnSingleLine() {
        return err.toString().replace(System.lineSeparator(), " ");
    }

    @DataProvider
    public Object[][] invalidArg() throws Exception {
        return new Object[][] { { new String[] { "-42" } } };
    }

    @DataProvider
    public Object[][] invalidArgs() throws Exception {
        return new Object[][] { { new String[] {} }, { new String[] { "-42" } } };
    }

    @Test(dataProvider = "invalidArg")
    public void testBase64(final String[] args) throws LDAPToolException {
        assertThat(runTool(new Base64(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArgs")
    public void testLDAPCompare(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDAPCompare(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArg")
    public void testLDAPDelete(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDAPDelete(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArg")
    public void testLDAPModify(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDAPModify(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArg")
    public void testLDAPPasswordModify(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDAPPasswordModify(outStream, errStream), args))
                  .isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArgs")
    public void testLDAPSearch(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDAPSearch(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArgs")
    public void testLDIFDiff(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDIFDiff(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArgs")
    public void testLDIFModify(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDIFModify(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArg")
    public void testLDIFSearch(final String[] args) throws LDAPToolException {
        assertThat(runTool(new LDIFSearch(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    @Test(dataProvider = "invalidArg")
    public void testMakeLdif(final String[] args) throws LDAPToolException {
        assertThat(runTool(new MakeLDIF(outStream, errStream), args)).isEqualTo(CLIENT_SIDE_PARAM_ERROR.intValue());
        assertToolFailsWithUsage();
    }

    private void assertToolFailsWithUsage() {
        assertThat(out.toString()).isEmpty();
        final String streamToCheck = errOnSingleLine();
        assertThat(streamToCheck).matches(".*" + INFO_GLOBAL_HELP_REFERENCE.get("(.*)") + ".*");
        assertThat(streamToCheck).contains(ERR_ERROR_PARSING_ARGS.get(""));
    }
}
