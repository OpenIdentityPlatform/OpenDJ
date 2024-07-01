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
 * Copyright 2014-2015 ForgeRock AS.
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
