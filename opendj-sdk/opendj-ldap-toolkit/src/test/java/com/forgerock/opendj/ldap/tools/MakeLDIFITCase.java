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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_GLOBAL_HELP_REFERENCE;

import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MakeLDIFITCase extends ToolsITCase {

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
        run(arguments, true, expectedOut);
    }

    @Test(dataProvider = "invalidArguments")
    public void testMakeLDIFInvalidUseCases(final String[] arguments, final LocalizableMessage expectedErr)
            throws Exception {
        run(arguments, false, expectedErr);
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
