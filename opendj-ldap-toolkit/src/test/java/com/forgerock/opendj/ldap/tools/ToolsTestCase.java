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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.util.Utils.closeSilently;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * An abstract class that all tools unit tests should extend. A tool represents
 * the classes found directly under the package com.forgerock.opendj.ldap.tools.
 */
@Test
public abstract class ToolsTestCase extends ForgeRockTestCase {

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

    /**
     * Tests the LDAPSearch tool with sets of invalid arguments.
     *
     * @param args
     *         The set of arguments to use for the LDAPDelete tool.
     * @param invalidReason
     *         The reason the provided set of arguments is invalid.
     */
    @Test(dataProvider = "invalidArgs")
    public void testInvalidArguments(String[] args, String invalidReason) throws Exception {
        assertThat(runTool(args)).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(invalidReason);
        assertThat(out.toString()).isEmpty();
    }

    /** Tests the LDAPSearch tool with the "--help" option. */
    @Test
    public void testHelp() throws Exception {
        final int success = ResultCode.SUCCESS.intValue();
        assertThat(runTool("--help")).isEqualTo(success);
        assertThat(runTool("-H")).isEqualTo(success);
        assertThat(runTool("-?")).isEqualTo(success);
    }

    String errOnSingleLine() {
        return err.toString().replace(System.lineSeparator(), " ");
    }

    int runTool(final String... args) {
        return Utils.runTool(createInstance(), args);
    }

    abstract ToolConsoleApplication createInstance();
}
