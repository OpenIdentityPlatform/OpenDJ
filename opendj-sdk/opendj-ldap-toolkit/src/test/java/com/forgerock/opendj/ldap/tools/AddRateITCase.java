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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ADDRATE_DELMODE_RAND_THRESHOLD_AGE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ADDRATE_THRESHOLD_SIZE_AND_AGE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_ADDRATE_TOOL_DESCRIPTION;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.util.Utils.closeSilently;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AddRateITCase extends ToolsITCase {
    private static final String TEMPLATE_NAME = "addrate.template";
    private static final String ADD_PERCENT_TEXT = "Add%";
    private static final int THROUGHPUT_COLUMN = 1;
    private static final int ERR_PER_SEC_COLUMN_NUMBER = 8;

    @DataProvider
    public Object[][] addRateArgs() throws Exception {
        String[] commonArgs =
            args("-h", TestCaseUtils.getServerSocketAddress().getHostName(),
                "-p", Integer.toString(TestCaseUtils.getServerSocketAddress().getPort()),
                "-c", "1", "-t", "1", "-i", "1", "-m", "1000", "-S");

        return new Object[][] {
            // Check if the help message is correctly prompted
            { args("-H"), INFO_ADDRATE_TOOL_DESCRIPTION.get(), "" },

            // Should report inconsistent use of options
            { args(commonArgs, "-C", "off", "-a", "3", TEMPLATE_NAME), "",
                ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON.get() },
            { args(commonArgs, "-C", "off", "-s", "30000", TEMPLATE_NAME), "",
                ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON.get() },
            { args(commonArgs, "-C", "fifo", "-a", "3", "-s", "20000", TEMPLATE_NAME), "",
                ERR_ADDRATE_THRESHOLD_SIZE_AND_AGE.get() },
            { args(commonArgs, "-C", "random", "-a", "3", TEMPLATE_NAME), "",
                ERR_ADDRATE_DELMODE_RAND_THRESHOLD_AGE.get() },

            // Correct test case
            { args(commonArgs, "-s", "10", TEMPLATE_NAME), ADD_PERCENT_TEXT, "" },

        };
    }

    public String[] args(String[] startLine, String... args) {
        String[] res = new String[startLine.length + args.length];

        System.arraycopy(startLine, 0, res, 0, startLine.length);
        System.arraycopy(args, 0, res, startLine.length, args.length);

        return res;
    }

    @Test(dataProvider = "addRateArgs")
    public void testITAddRate(String[] arguments, Object expectedOut, Object expectedErr) throws Exception {
        ByteStringBuilder out = new ByteStringBuilder();
        ByteStringBuilder err = new ByteStringBuilder();

        PrintStream outStream = new PrintStream(out.asOutputStream());
        PrintStream errStream = new PrintStream(err.asOutputStream());

        try {
            AddRate addRate = new AddRate(outStream, errStream);
            addRate.run(arguments);

            checkOuputStreams(out, err, expectedOut, expectedErr);
            String outContent = out.toString();

            if (outContent.contains(ADD_PERCENT_TEXT)) {
                // Check that there was no error in search
                String[] addRateResLines = outContent.split(System.getProperty("line.separator"));
                // Skip header line
                for (int i = 1; i < addRateResLines.length; i++) {
                    String[] addhRateLineData = addRateResLines[i].split(",");
                    assertThat(addhRateLineData[ERR_PER_SEC_COLUMN_NUMBER].trim()).isEqualTo("0.0");
                    assertThat(addhRateLineData[THROUGHPUT_COLUMN].trim()).isNotEqualTo("0.0");
                }

            }
        } finally {
            closeSilently(outStream, errStream);
        }
    }

}
