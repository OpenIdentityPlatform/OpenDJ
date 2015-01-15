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

import static com.forgerock.opendj.cli.Utils.MAX_LINE_WIDTH;
import static com.forgerock.opendj.cli.Utils.wrapText;
import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Class used for the toolkit integration tests.
 */
@SuppressWarnings("javadoc")
public abstract class ToolsITCase extends ForgeRockTestCase {

    @BeforeClass
    void setUp() throws Exception {
        TestCaseUtils.startServer();
    }

    @AfterClass
    void tearDown() throws Exception {
        TestCaseUtils.stopServer();
    }

    /**
     * Check both out and err outputs streams.
     *
     * @param out
     *            output stream from the toolkit application
     * @param err
     *            error stream from the toolkit application
     * @param expectedOutput
     *            String or LocalizedMessage expected on output
     * @param expectedError
     *            String or LocalizedMessage expected on error output
     * @throws Exception
     */
    protected void checkOuputStreams(ByteStringBuilder out, ByteStringBuilder err, Object expectedOutput,
        Object expectedError) throws Exception {
        // Check error output
        checkOutputStream(out, expectedOutput);
        checkOutputStream(err, expectedError);
    }

    protected void checkOutputStream(ByteStringBuilder out, Object expectedOutput) {
        String lineSeparator = System.getProperty("line.separator");
        String toCompare = expectedOutput.toString();

        if (expectedOutput instanceof LocalizableMessage) {
            toCompare = wrapText((LocalizableMessage) expectedOutput, MAX_LINE_WIDTH);
        }

        toCompare = toCompare.replace(lineSeparator, " ");

        if (toCompare.isEmpty()) {
            assertThat(out.toString().length()).isEqualTo(0);
        } else {
            assertThat(out.toString().replace(lineSeparator, " ")).contains(toCompare);
        }

    }

    /** Arguments passed to the command. */
    protected String[] args(String... arguments) {
        return arguments;
    }
}
