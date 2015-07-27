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
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_COMPARE_OPERATION_RESULT_FALSE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_COMPARE_OPERATION_RESULT_TRUE;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_LDAPCOMPARE_TOOL_DESCRIPTION;
import static org.forgerock.util.Utils.closeSilently;

import java.io.PrintStream;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LDAPCompareITCase extends ToolsITCase {

    private static final int NB_RAND_SIMPLE_COMPARE = 10;
    private static final int NB_OTHER_SIMPLE_SEARCH = 2;

    @DataProvider
    public Object[][] ldapCompareArgs() throws Exception {
        Object[][] data = new Object[NB_RAND_SIMPLE_COMPARE + NB_OTHER_SIMPLE_SEARCH][];
        Random rand = new Random();
        long[] randUIDs = new long[NB_RAND_SIMPLE_COMPARE];

        // Check if the help message is correctly prompted
        data[0] = new Object[] { args("--help"), INFO_LDAPCOMPARE_TOOL_DESCRIPTION.get(), "" };

        // Check if the help reference message is prompted if arguments failed to be parsed
        data[1] = new Object[] {
            args("-42"), "", INFO_GLOBAL_HELP_REFERENCE.get("java " + LDAPCompare.class.getCanonicalName()) };

        // Perform some basic comparison on random user from the test server
        for (int i = 0; i < NB_RAND_SIMPLE_COMPARE; i++) {
            randUIDs[i] = Math.round(rand.nextInt(1000));
        }

        for (int i = 0; i < NB_RAND_SIMPLE_COMPARE; i++) {
            long firstUID = randUIDs[i];
            // For first test, ensure that both uids are equals
            long secondUID = i == 0 ? firstUID : randUIDs[rand.nextInt(randUIDs.length)];

            data[i + NB_OTHER_SIMPLE_SEARCH] = produceLDAPCompareBasicTest(firstUID, secondUID);
        }

        return data;
    }

    private Object[] produceLDAPCompareBasicTest(long firstUID, long secondUID) {
        String uid = String.format("uid:user.%d", firstUID);
        String dn = String.format("uid=user.%d,ou=people,o=test", secondUID);
        LocalizableMessage messageToCheck = INFO_COMPARE_OPERATION_RESULT_FALSE.get(dn);

        if (firstUID == secondUID) {
            messageToCheck = INFO_COMPARE_OPERATION_RESULT_TRUE.get(dn);
        }

        return new Object[] {
            args("-h", TestCaseUtils.getServerSocketAddress().getHostName(), "-p",
                Integer.toString(TestCaseUtils.getServerSocketAddress().getPort()), uid, dn), messageToCheck, "" };
    }

    @Test(dataProvider = "ldapCompareArgs")
    public void testITLDAPSearch(String[] arguments, Object expectedOut, Object expectedErr) throws Exception {
        ByteStringBuilder out = new ByteStringBuilder();
        ByteStringBuilder err = new ByteStringBuilder();

        PrintStream outStream = new PrintStream(out.asOutputStream());
        PrintStream errStream = new PrintStream(err.asOutputStream());

        try {
            LDAPCompare ldapCompare = new LDAPCompare(outStream, errStream);

            ldapCompare.run(arguments);
            checkOuputStreams(out, err, expectedOut, expectedErr);
        } finally {
            closeSilently(outStream, errStream);
        }
    }
}
