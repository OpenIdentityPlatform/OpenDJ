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
 *      Copyright 2013-2014 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.MAX_LINE_WIDTH;
import static com.forgerock.opendj.cli.Utils.wrapText;
import static org.forgerock.util.Utils.closeSilently;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MakeLDIFTestCase extends ToolsTestCase {

    @DataProvider(name = "validArguments")
    Object[][] createValidArguments() throws Exception {
        Object[][] data = new Object[][] {
            { args("-c", "numusers=1", "example.template"),
              // 2 base entries + users
              expectedErrOutput(INFO_MAKELDIF_PROCESSING_COMPLETE.get(3)) },

            { args("-c", "numusers=5", "example.template"),
              // 2 base entries + users
              expectedErrOutput(INFO_MAKELDIF_PROCESSING_COMPLETE.get(7)) },
        };
        return data;
    }

    @DataProvider(name = "invalidArguments")
    Object[][] createInValidArguments() throws Exception {
        Object[][] data = new Object[][] {
            { // check that usage is written to output when arguments are invalid
              args(),
              expectedErrOutput(INFO_MAKELDIF_TOOL_DESCRIPTION.get()) },

            { // check that there is an argument error when no arg provided
              args(),
              expectedErrOutput(ERR_ERROR_PARSING_ARGS.get("")) },

            { args("-r", "unknown/path" , "example.template"),
              expectedErrOutput(ERR_LDIF_GEN_TOOL_NO_SUCH_RESOURCE_DIRECTORY.get("unknown/path")) },

            { args("-o", "unknown/path" , "example.template"),
              expectedErrOutput(ERR_MAKELDIF_UNABLE_TO_CREATE_LDIF.get("unknown/path", "")) },

            { args("-s", "non-numeric" , "example.template"),
              expectedErrOutput(ERR_ERROR_PARSING_ARGS.get("")) },
        };
        return data;
    }

    @Test(dataProvider = "validArguments")
    public void testRunValidArguments(String[] arguments, LocalizableMessage expectedErrOutput)
            throws Exception {
        run(arguments, true, expectedErrOutput);
    }

    @Test(dataProvider = "invalidArguments")
    public void testRunInvalidArguments(String[] arguments, LocalizableMessage expectedErrOutput)
            throws Exception {
        run(arguments, false, expectedErrOutput);
    }

    private void run(String[] arguments, boolean expectsResults, LocalizableMessage expectedErrOutput)
            throws UnsupportedEncodingException {
        PrintStream outStream = null;
        PrintStream errStream = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outStream = new PrintStream(out);
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            errStream = new PrintStream(err);

            MakeLDIF makeLDIF = new MakeLDIF(outStream, errStream);
            makeLDIF.run(arguments);

            assertThat(out.size()).isGreaterThan(0);
            if (!expectsResults) {
                assertThat(err.size()).isEqualTo(0);
            }
            ByteArrayOutputStream std = expectsResults || makeLDIF.isInteractive() ? out : err;
            assertThat(std.toString("UTF-8")).contains(wrapText(expectedErrOutput, MAX_LINE_WIDTH));
        } finally {
            closeSilently(outStream, errStream);
        }
    }

    /** Arguments passed to the command */
    private String[] args(String...arguments) {
        return arguments;
    }

    /** A message the error output is expected to contain. */
    private LocalizableMessage expectedErrOutput(LocalizableMessage val) {
        return val;
    }


}
