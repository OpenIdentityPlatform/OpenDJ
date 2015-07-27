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

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ERROR_PARSING_ARGS;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_RESULT_CODE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_LDAPSEARCH_TOOL_DESCRIPTION;
import static org.forgerock.util.Utils.closeSilently;

import java.io.PrintStream;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Simple integration tests to check the ldapsearch command.
 */
@SuppressWarnings("javadoc")
public class LDAPSearchITCase extends ToolsITCase {
    private static final int NB_RAND_SIMPLE_SEARCH = 10;
    private static final int NB_OTHER_SIMPLE_SEARCH = 3;

    @DataProvider
    public Object[][] ldapSearchArgs() throws Exception {
        Object[][] data = new Object[NB_RAND_SIMPLE_SEARCH + NB_OTHER_SIMPLE_SEARCH][];

        // Check if the help message is correctly prompted
        data[0] = new Object[] { args("--help"), INFO_LDAPSEARCH_TOOL_DESCRIPTION.get(), "" };

        // Check that there is a error message if no arguments were given to the
        // ldapsearch command
        data[1] = new Object[] { args(""), "", ERR_ERROR_PARSING_ARGS.get("") };

        // Check if the help reference message is prompted if arguments failed to be parsed
        data[2] = new Object[] {
            args("-42"), "", INFO_GLOBAL_HELP_REFERENCE.get("java " + LDAPSearch.class.getCanonicalName()) };

        // Perform some basic ldapsearch for random user in the test server
        for (int i = 0; i < NB_RAND_SIMPLE_SEARCH; i++) {
            long userID = new Random().nextInt(1000);
            data[i + NB_OTHER_SIMPLE_SEARCH] = produceLDAPSearchBasicTestCase(userID);
        }

        return data;
    }

    private Object[] produceLDAPSearchBasicTestCase(long userID) {
        String dn = String.format("uid=user.%d,ou=people,o=test", userID);
        LocalizableMessage matchEntryCnt = INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT.get(1);
        LocalizableMessage resultSuccess =
            ERR_TOOL_RESULT_CODE.get(ResultCode.SUCCESS.intValue(), ResultCode.SUCCESS.getName().toString());
        return new Object[] {
            args("--countEntries", "-h", TestCaseUtils.getServerSocketAddress().getHostName(), "-p",
                Integer.toString(TestCaseUtils.getServerSocketAddress().getPort()), "-b", dn, "(uid=user.%d)", "uid"),
            matchEntryCnt, resultSuccess };
    }

    @Test(dataProvider = "ldapSearchArgs")
    public void testITLDAPSearch(String[] arguments, Object expectedOut, Object expectedErr) throws Exception {
        ByteStringBuilder out = new ByteStringBuilder();
        ByteStringBuilder err = new ByteStringBuilder();

        PrintStream outStream = new PrintStream(out.asOutputStream());
        PrintStream errStream = new PrintStream(err.asOutputStream());
        try {
            LDAPSearch ldapSearch = new LDAPSearch(outStream, errStream);

            ldapSearch.run(arguments, false);
            checkOuputStreams(out, err, expectedOut, expectedErr);
        } finally {
            closeSilently(outStream, errStream);
        }
    }

}
