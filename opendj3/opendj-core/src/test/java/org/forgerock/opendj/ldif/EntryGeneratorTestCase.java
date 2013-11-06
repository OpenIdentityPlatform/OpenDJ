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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.getTestFilePath;
import static org.forgerock.opendj.ldif.EntryGenerator.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EntryGeneratorTestCase extends SdkTestCase {

    private static final String TEMPLATE_FILE_PATH = "org/forgerock/opendj/ldif/example.template";
    private String resourcePath;
    private Schema schema;

    @BeforeClass
    public void setUp() throws Exception {
        // path of directory in src/main/resources must be obtained from a file
        // otherwise it may search in the wrong directory
        resourcePath = new File(getTestFilePath(TEMPLATE_FILE_PATH)).getParent();
        schema = Schema.getDefaultSchema();
    }

    @Test
    public void testReaderWithTemplateFile() throws Exception {
        String templatePath = getTestFilePath(TEMPLATE_FILE_PATH);
        EntryGenerator reader = newReader(templatePath).setResourcePath(resourcePath).build();

        checkReader(reader, 10000);
        reader.close();
    }

    @Test
    public void testReaderWithTemplateStream() throws Exception {
        InputStream stream = new FileInputStream(
                new File(getTestFilePath(TEMPLATE_FILE_PATH)));
        EntryGenerator reader = newReader(stream).setResourcePath(resourcePath).build();

        checkReader(reader, 10000);
        reader.close();
    }


    @Test
    public void testReaderWithTemplateLines() throws Exception {
        EntryGenerator reader = newReader(
                "define suffix=dc=example,dc=com",
                "define maildomain=example.com",
                "define numusers=2",
                "",
                "branch: [suffix]",
                "",
                "branch: ou=People,[suffix]",
                "subordinateTemplate: person:[numusers]",
                "",
                "template: person",
                "rdnAttr: uid",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: <first>",
                "sn: <last>",
                "cn: {givenName} {sn}",
                "initials: {givenName:1}<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
                "employeeNumber: <sequential:0>",
                "uid: user.{employeeNumber}",
                "mail: {uid}@[maildomain]",
                "userPassword: password",
                "telephoneNumber: <random:telephone>",
                "homePhone: <random:telephone>",
                "pager: <random:telephone>",
                "mobile: <random:telephone>",
                "street: <random:numeric:5> <file:streets> Street",
                "l: <file:cities>",
                "st: <file:states>",
                "postalCode: <random:numeric:5>",
                "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
                "description: This is the description for {cn}.")
                .setResourcePath(resourcePath).build();

        checkReader(reader, 2);
        reader.close();
    }

    /**
     * Check the content of the reader for basic case.
     *
     * Expecting 2 entries and then numberOfUsers entries.
     */
    private void checkReader(EntryGenerator reader, int numberOfUsers) throws Exception {
        assertThat(reader.hasNext()).isTrue();
        assertThat(reader.readEntry().getName().toString()).isEqualTo("dc=example,dc=com");
        assertThat(reader.hasNext()).isTrue();
        assertThat(reader.readEntry().getName().toString()).isEqualTo("ou=People,dc=example,dc=com");
        for (int i = 0; i < numberOfUsers; i++) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.readEntry().getName().toString()).
                isEqualTo("uid=user." + i + ",ou=People,dc=example,dc=com");
        }
        assertThat(reader.hasNext()).as("should have no more entries").isFalse();
    }

    @Test(expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = ".*Could not find template file unknown.*")
    public void testMissingTemplateFile() throws Exception {
        newReader("unknown").setResourcePath(resourcePath).build();
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Cannot find file streets.*")
    public void testMissingResourceFile() throws Exception {
        // fail to find first resource file which is 'streets'
        newReader(getTestFilePath(TEMPLATE_FILE_PATH)).setResourcePath("unknown").build();
    }

    /**
     * Test to show that reporting an error about an uninitialized variable when
     * generating templates reports the correct line.
     */
    @Test()
    public void testParseFileTemplate() throws Exception {
        String[] lines = {
        /* 0 */"template: template",
        /* 1 */"a: {missingVar}",
        /* 2 */"a: b",
        /* 3 */"a: c",
        /* 4 */"",
        /* 5 */"template: template2", };

        // Test must show "missingVar" missing on line 1.
        // Previous behaviour showed "missingVar" on line 5.

        TemplateFile templateFile = new TemplateFile(schema, resourcePath);
        List<LocalizableMessage> warns = new ArrayList<LocalizableMessage>();

        try {
            templateFile.parse(lines, warns);
            failWasExpected(DecodeException.class);
        } catch (DecodeException e) {
            LocalizableMessage expected = ERR_ENTRY_GENERATOR_TAG_UNDEFINED_ATTRIBUTE.get("missingVar", 1);
            assertThat(e.getMessage()).isEqualTo(expected.toString());
        }
    }

    @DataProvider(name = "validTemplates")
    public Object[][] createTestTemplates() {
        return new Object[][] {
            { "CurlyBracket",
              new String[] { "template: templateWithEscape", "rdnAttr: uid", "uid: testEntry",
                  "cn: I\\{Foo\\}F" }
            },
            { "AngleBracket",
              new String[] { "template: templateWithEscape", "rdnAttr: uid", "uid: testEntry",
                  "sn: \\<Bar\\>" }
            },
            { "SquareBracket",
              new String[] { "template: templateWithEscape", "rdnAttr: uid", "uid: testEntry",
                  "description: \\[TEST\\]" } },
            { "BackSlash",
              new String[] { "template: templateWithEscape", "rdnAttr: uid", "uid: testEntry",
                  "description: Foo \\\\ Bar" } },
            { "EscapedAlpha",
              new String[] { "template: templateWithEscape", "rdnAttr: uid", "uid: testEntry",
                  "description: Foo \\\\Bar" } },
            { "Normal Variable",
              new String[] { "template: templateNormal", "rdnAttr: uid", "uid: testEntry", "sn: {uid}" } },
            { "Constant",
              new String[] { "define foo=Test123", "", "template: templateConstant", "rdnAttr: uid",
                  "uid: testEntry", "sn: {uid}", "cn: [foo]" }
            },
        };
    }

    /**
     * Test for parsing escaped character in templates
     */
    @Test(dataProvider = "validTemplates")
    public void testParsingEscapeCharInTemplate(String testName, String[] lines) throws Exception {
        TemplateFile templateFile = new TemplateFile(schema, resourcePath);
        List<LocalizableMessage> warns = new ArrayList<LocalizableMessage>();
        templateFile.parse(lines, warns);
        assertThat(warns).isEmpty();
    }

    @DataProvider(name = "templatesToTestEscapeChars")
    public Object[][] createTemplatesToTestSpecialChars() {
        return new Object[][] {
            {
                "Curly",
                new String[] {
                    "branch: dc=test", "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "cn: I\\{ Foo \\}F" },
                "cn", // Attribute to test
                "I{ Foo }F", // Expected value
            },
            {
                "Angle",
                new String[] {
                    "branch: dc=test",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "sn: \\< Bar \\>" },
                "sn", // Attribute to test
                "< Bar >", // Expected value
            },
            {
                "Square",
                new String[] {
                    "branch: dc=test",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "description: \\[TEST\\]" },
                "description", // Attribute to test
                "[TEST]", // Expected value
            },
            {
                "BackSlash",
                new String[] {
                    "branch: dc=test",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "displayName: Foo \\\\ Bar" },
                "displayname", // Attribute to test
                "Foo \\ Bar", // Expected value
            },
            {
                "MultipleSquare",
                new String[] {
                    "define top=dc=com",
                    "define container=ou=group",
                    "",
                    "branch: dc=test,[top]",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "manager: cn=Bar,[container],dc=test,[top]",
                    "" },
                "manager", // Attribute to test
                "cn=Bar,ou=group,dc=test,dc=com", // Expected value
            },
            {
                "MixedSquare",
                new String[] {
                    "define top=dc=com",
                    "define container=ou=group",
                    "", "branch: dc=test,[top]",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "description: test [container] \\[[top]\\]",
                    "", },
                "description", // Attribute to test
                "test ou=group [dc=com]", // Expected value
            },
            {
                "NoConstantBecauseEscaped",
                new String[] {
                    "define top=dc=com",
                    "define container=ou=group",
                    "", "branch: dc=test,[top]",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "description: test \\[top]",
                    "", },
                "description", // Attribute to test
                "test [top]", // Expected value
            },
            {
                "NoConstantBecauseStrangeChar",
                new String[] {
                    "define top=dc=com",
                    "define container=ou=group",
                    "",
                    "branch: dc=test,[top]",
                    "subordinateTemplate: templateWithEscape:1",
                    "",
                    "template: templateWithEscape",
                    "rdnAttr: uid",
                    "objectclass: inetOrgPerson",
                    "uid: testEntry",
                    "description: test [group \\[top]",
                    "", },
                "description", // Attribute to test
                "test [group [top]", // Expected value
            },
        /*
         * If adding a test, please copy and reuse template code down below {
         * "", new String[]{ "template: templateWithEscape", "rdnAttr: uid",
         * "uid: testEntry", "cn: I\\{Foo\\}F"}, "", // Attribute to test "", //
         * Expected value }
         */
        };
    }

    /**
     * Test for escaped characters in templates
     */
    @Test(dataProvider = "templatesToTestEscapeChars", dependsOnMethods = { "testParsingEscapeCharInTemplate" })
    public void testEscapeCharsFromTemplate(String testName, String[] lines, String attrName, String expectedValue)
            throws Exception {
        EntryGenerator reader = newReader(lines).setResourcePath(resourcePath).build();
        Entry topEntry = reader.readEntry();
        Entry entry = reader.readEntry();
        reader.close();

        assertThat(topEntry).isNotNull();
        assertThat(entry).isNotNull();
        assertThat(entry.getAttribute(attrName).firstValueAsString()).isEqualTo(expectedValue);
    }

    /**
     * Test template that combines escaped characters and variables
     */
    @Test(dependsOnMethods = { "testParsingEscapeCharInTemplate" })
    public void testCombineEscapeCharInTemplate() throws Exception {
        String[] lines = {
            "branch: dc=test",
            "subordinateTemplate: templateWithEscape:1",
            "",
            "template: templateWithEscape",
            "rdnAttr: uid",
            "objectclass: inetOrgPerson",
            "uid: testEntry",
            "sn: Bar",
            // The value below combines variable, randoms and escaped chars.
            // The resulting value is "Foo <?>{1}Bar" where ? is a letter
            // from [A-Z].
            "cn: Foo \\<<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>\\>\\{1\\}{sn}", "", };

        EntryGenerator reader = newReader(lines).setResourcePath(resourcePath).build();
        Entry topEntry = reader.readEntry();
        Entry entry = reader.readEntry();
        reader.close();

        assertThat(topEntry).isNotNull();
        assertThat(entry).isNotNull();
        assertThat(entry.getAttribute("cn").firstValueAsString()).matches("Foo <[A-Z]>\\{1\\}Bar");
    }
}
