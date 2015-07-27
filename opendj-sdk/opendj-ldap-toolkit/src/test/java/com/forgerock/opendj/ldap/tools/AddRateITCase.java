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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AddRateITCase extends ToolsITCase {

    private static final String TEMPLATE_NAME = "addrate.template";
    private static final String ADD_PERCENT_TEXT = "Add%";
    private static final int THROUGHPUT_COLUMN = 1;
    private static final int ERR_PER_SEC_COLUMN_NUMBER = 8;

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

    private String[] commonsArgs() {
        return new String[] {
            "-h", TestCaseUtils.getServerSocketAddress().getHostName(),
            "-p", Integer.toString(TestCaseUtils.getServerSocketAddress().getPort()),
            "-c", "1", "-t", "1", "-i", "1", "-m", "100", "-S"};
    }

    public String[] args(String[] startLine, String... args) {
        String[] res = new String[startLine.length + args.length];

        System.arraycopy(startLine, 0, res, 0, startLine.length);
        System.arraycopy(args, 0, res, startLine.length, args.length);

        return res;
    }

    @DataProvider
    public Object[][] invalidAddRateArgs() throws Exception {
        return new Object[][] {
            // Should report inconsistent use of options
            { args("-C", "off", "-a", "3", TEMPLATE_NAME), ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON.get() },
            { args("-C", "off", "-s", "30000", TEMPLATE_NAME), ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON.get() },
            { args("-C", "fifo", "-a", "3", "-s", "20000", TEMPLATE_NAME), ERR_ADDRATE_THRESHOLD_SIZE_AND_AGE.get() },
            { args("-C", "random", "-a", "3", TEMPLATE_NAME), ERR_ADDRATE_DELMODE_RAND_THRESHOLD_AGE.get() },
            { args("-s", "999", TEMPLATE_NAME), ERR_ADDRATE_SIZE_THRESHOLD_LOWER_THAN_ITERATIONS.get() },
            { args("-42"), INFO_GLOBAL_HELP_REFERENCE.get("java " + AddRate.class.getCanonicalName()) }
        };
    }

    @Test(dataProvider = "invalidAddRateArgs")
    public void addRateExceptions(String[] arguments, Object expectedErr) throws Exception {
        AddRate addRate = new AddRate(outStream, errStream);
        int retCode = addRate.run(args(commonsArgs(), arguments));
        checkOuputStreams(out, err, "", expectedErr);
        assertThat(retCode).isNotEqualTo(0);
    }

    @Test
    public void addRateDisplaysHelp() throws Exception {
        AddRate addRate = new AddRate(outStream, errStream);
        int retCode = addRate.run(args("-H"));
        checkOuputStreams(out, err, INFO_ADDRATE_TOOL_DESCRIPTION.get(), "");
        assertThat(retCode).isEqualTo(0);
    }

    @Test(timeOut = 10000)
    public void addRateSimpleRun() throws Exception {
        AddRate addRate = new AddRate(outStream, errStream);
        int retCode = addRate.run(args(commonsArgs(), "-s", "10", TEMPLATE_NAME));
        checkOuputStreams(out, err, ADD_PERCENT_TEXT, "");
        assertThat(retCode).isEqualTo(0);
        String outContent = out.toString();

        if (outContent.contains(ADD_PERCENT_TEXT)) {
            // Check that there was no error
            String[] addRateResLines = outContent.split(System.getProperty("line.separator"));
            // Skip header line
            for (int i = 1; i < addRateResLines.length; i++) {
                String[] lineData = addRateResLines[i].split(",");
                assertThat(lineData[ERR_PER_SEC_COLUMN_NUMBER].trim()).isEqualTo("0.0");
                assertThat(lineData[THROUGHPUT_COLUMN].trim()).isNotEqualTo("0.0");
            }
        }
    }

}
