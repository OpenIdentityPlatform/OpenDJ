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

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** LDIFSearch test cases. */
@SuppressWarnings("javadoc")
@Test
public class LDIFSearchTestCase extends ToolsTestCase {

    private String ldifSearchSourceFilePath;
    private String ldifSearchOutputFilePath;

    @BeforeClass
    private void setUp() throws Exception {
        ldifSearchSourceFilePath = Paths.get("src", "test", "resources", "ldifsearch.ldif").toAbsolutePath().toString();
        ldifSearchOutputFilePath = TestCaseUtils.createTempFile();
    }

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize the LDAPSearch tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the LDAPSearch tool.
     */
    @DataProvider(name = "invalidArgs")
    public Object[][] getInvalidArgumentLists() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        argLists.add(args());
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(2));

        addValueNeededShortArgs(argLists, reasonList, "b", "l", "s", "z");
        addValueNeededLongArgs(argLists, reasonList, "baseDN", "timeLimit", "outputLDIF", "searchScope", "sizeLimit");

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void testLDIFSearchStarOps() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                ldifSearchSourceFilePath,
                                "(objectclass=*)",
                                "*", "+");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            while (reader.hasNext()) {
                assertThat(reader.readEntry().getAllAttributes("objectclass")).isNotEmpty();
            }
        }
    }

    @Test
    public void testLDIFSearchOperationalAttributesOnly() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                "-s", "sub",
                                ldifSearchSourceFilePath,
                                "(objectclass=*)",
                                "+");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            while (reader.hasNext()) {
                assertThat(reader.readEntry().getAllAttributes("objectclass")).isEmpty();
            }
        }
    }

    @Test
    public void testLDIFSearchUserAndOperationalAttributes() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                "-s", "subordinates",
                                ldifSearchSourceFilePath,
                                "(objectclass=*)",
                                "+", "mail", "uid");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            while (reader.hasNext()) {
                final Entry e = reader.readEntry();
                assertThat(e.getAllAttributes("objectclass")).isEmpty();
                assertThat(e.getAllAttributes("mail")).isNotEmpty();
                assertThat(e.getAllAttributes("uid")).isNotEmpty();
            }
        }
    }

    @Test
    public void testLDIFSearchAttrsOnly() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                "-s", "subordinates",
                                "-A",
                                ldifSearchSourceFilePath,
                                "(objectclass=*)",
                                "mail");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            while (reader.hasNext()) {
                final Entry e = reader.readEntry();
                assertThat(e.getAllAttributes("objectclass")).isEmpty();
                final Iterable<Attribute> mails = e.getAllAttributes("mail");
                assertThat(mails).isNotEmpty();
                for (final Attribute mail : mails) {
                    assertThat(mail.firstValueAsString()).isEmpty();
                }
            }
        }
    }

    @Test
    public void testLDIFSearchSizeLimit() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                "-s", "subordinates",
                                "-z", "10",
                                ldifSearchSourceFilePath,
                                "(objectclass=*)",
                                "mail", "uid", "initials");
        assertThat(res).isEqualTo(ResultCode.SIZE_LIMIT_EXCEEDED.intValue());
        int nbEntriesRead = 0;
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            while (reader.hasNext()) {
                nbEntriesRead++;
                final Entry e = reader.readEntry();
                assertThat(e.getAllAttributes("mail")).isNotEmpty();
                assertThat(e.getAllAttributes("initials")).isNotEmpty();
                assertThat(e.getAllAttributes("cn")).isEmpty();
            }
        }
        assertThat(nbEntriesRead).isEqualTo(10);
    }

    @Test
    public void testLDIFSearchWrapping() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                "-t", "80",
                                ldifSearchSourceFilePath,
                                "(uid=user.1)",
                                "mail", "uid", "description");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        assertThatUser1DescriptionIsReadable();
        assertThat(countLdifSearchOutputFileLines()).isEqualTo(7);
    }

    @Test
    public void testLDIFSearchNoWrapping() throws Exception {
        final int res = runTool("-b", "ou=ldifsearch,o=unit tests,dc=example,dc=com",
                                "-o", ldifSearchOutputFilePath,
                                ldifSearchSourceFilePath,
                                "(uid=user.1)",
                                "mail", "uid", "description");
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        assertThatUser1DescriptionIsReadable();
        assertThat(countLdifSearchOutputFileLines()).isEqualTo(5);
    }

    private int countLdifSearchOutputFileLines() throws Exception {
        try (final BufferedReader reader = new BufferedReader(new FileReader(ldifSearchOutputFilePath))) {
            int nbLines = 0;
            while (reader.readLine() != null) {
                nbLines++;
            }
            return nbLines;
        }
    }

    private void assertThatUser1DescriptionIsReadable() throws Exception {
        try (final LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifSearchOutputFilePath))) {
            final Entry e = reader.readEntry();
            assertThat(e.getAllAttributes("mail")).isNotEmpty();
            assertThat(e.getAllAttributes("uid")).isNotEmpty();
            final Iterable<Attribute> descriptions = e.getAllAttributes("description");
            assertThat(descriptions).isNotEmpty();
            for (final Attribute desc : descriptions) {
                assertThat(desc.firstValueAsString()).endsWith("to not wrap is specified.");
            }
            assertThat(reader.hasNext()).isFalse();
        }
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDIFSearch(outStream, errStream);
    }
}
