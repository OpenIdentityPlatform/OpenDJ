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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.getTestFilePath;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Utils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EntryGeneratorTestCase extends SdkTestCase {

    private static final String BASIC_TEMPLATE_PATH = "org/forgerock/opendj/ldif/example.template";
    private static final String SUBTEMPLATES_TEMPLATE_PATH = "org/forgerock/opendj/ldif/people_and_groups.template";
    private String resourcePath;
    private Schema schema;

    @BeforeClass
    public void setUp() throws Exception {
        // path of directory in src/main/resources must be obtained from a file
        // otherwise it may search in the wrong directory
        resourcePath = new File(getTestFilePath(BASIC_TEMPLATE_PATH)).getParent();
        schema = Schema.getDefaultSchema();
    }

    /**
     * This test is a facility to print generated entries to stdout and should
     * always be disabled.
     * Turn it on locally if you need to see the output.
     */
    @Test(enabled = false)
    public void printEntriesToStdOut() throws Exception {
        String path = SUBTEMPLATES_TEMPLATE_PATH;
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator(getTestFilePath(path)).setResourcePath(resourcePath);
            while (generator.hasNext()) {
                System.out.println(generator.readEntry());
            }
        } finally {
            Utils.closeSilently(generator);
        }

    }

    @Test
    public void testCreateWithDefaultTemplateFile() throws Exception {
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator();
            assertThat(generator.hasNext()).isTrue();
        } finally {
            Utils.closeSilently(generator);
        }
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Could not find template file unknown.*")
    public void testCreateWithMissingTemplateFile() throws Exception {
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator("unknown/path");
            generator.hasNext();
        } finally {
            Utils.closeSilently(generator);
        }
    }

    @Test
    public void testCreateWithSetConstants() throws Exception {
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator().setConstant("numusers", 1);
            generator.readEntry();
            generator.readEntry();
            assertThat(generator.readEntry().getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
            assertThat(generator.hasNext()).as("should have no more entries").isFalse();
        } finally {
            Utils.closeSilently(generator);
        }
    }

    @DataProvider(name = "generators")
    public Object[][] createGenerators() throws Exception {
        Object[][] generators = new Object[3][2];

        String templatePath = getTestFilePath(BASIC_TEMPLATE_PATH);
        generators[0][0] = new EntryGenerator(templatePath).setResourcePath(resourcePath);
        generators[0][1] = 10000;

        InputStream stream = new FileInputStream(
                new File(templatePath));
        generators[1][0] = new EntryGenerator(stream).setResourcePath(resourcePath);
        generators[1][1] = 10000;

        generators[2][0] = new EntryGenerator(
                "define suffix=dc=example,dc=com",
                "define maildomain=example.com",
                "define numusers=2",
                "",
                "branch: [suffix]",
                "objectClass: top",
                "objectClass: domainComponent",
                "",
                "branch: ou=People,[suffix]",
                "objectClass: top",
                "objectClass: organizationalUnit",
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
                .setResourcePath(resourcePath);
        generators[2][1] = 2;

        return generators;
    }

    /**
     * Test the generated DNs.
     *
     * Expecting 2 entries and then numberOfUsers entries.
     */
    @Test(dataProvider = "generators")
    public void testGeneratedDNs(EntryGenerator generator, int numberOfUsers) throws Exception {
        try {
            assertThat(generator.hasNext()).isTrue();
            assertThat(generator.readEntry().getName().toString()).isEqualTo("dc=example,dc=com");
            assertThat(generator.hasNext()).isTrue();
            assertThat(generator.readEntry().getName().toString()).isEqualTo("ou=People,dc=example,dc=com");
            for (int i = 0; i < numberOfUsers; i++) {
                assertThat(generator.hasNext()).isTrue();
                assertThat(generator.readEntry().getName().toString()).
                    isEqualTo("uid=user." + i + ",ou=People,dc=example,dc=com");
            }
            assertThat(generator.hasNext()).as("should have no more entries").isFalse();
        } finally {
            Utils.closeSilently(generator);
        }
    }

    /**
     * Test the complete content of top entry.
     */
    @Test(dataProvider = "generators")
    public void testTopEntry(EntryGenerator generator, int numberOfUsers) throws Exception {
        try {
            Entry topEntry = generator.readEntry();
            assertThat(topEntry.getName().toString()).isEqualTo("dc=example,dc=com");

            Attribute dcAttribute = topEntry.getAttribute(getDCAttributeType().getNameOrOID());
            assertThat(dcAttribute).isNotNull();
            assertThat(dcAttribute.firstValueAsString()).isEqualTo("example");

            checkEntryObjectClasses(topEntry, "top", "domainComponent");
        } finally {
            Utils.closeSilently(generator);

        }
    }

    /**
     * Test the complete content of second entry.
     */
    @Test(dataProvider = "generators")
    public void testSecondEntry(EntryGenerator generator, int numberOfUsers) throws Exception {
        try {
            generator.readEntry(); // skip top entry
            Entry entry = generator.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("ou=People,dc=example,dc=com");

            Attribute dcAttribute = entry.getAttribute(getOUAttributeType().getNameOrOID());
            assertThat(dcAttribute).isNotNull();
            assertThat(dcAttribute.firstValueAsString()).isEqualTo("People");

            checkEntryObjectClasses(entry, "top", "organizationalUnit");
        } finally {
            Utils.closeSilently(generator);
        }
    }

    /**
     * Test the complete content of first user entry.
     */
    @Test(dataProvider = "generators")
    public void testFirstUserEntry(EntryGenerator generator, int numberOfUsers) throws Exception {
        try {
            generator.readEntry(); // skip top entry
            generator.readEntry(); // skip ou entry
            Entry entry = generator.readEntry();
            assertThat(entry.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");

            checkPresenceOfAttributes(entry, "givenName", "sn", "cn", "initials",
                    "employeeNumber", "uid", "mail", "userPassword", "telephoneNumber",
                    "homePhone", "pager", "mobile", "street", "l", "st", "postalCode",
                    "postalAddress", "description");
            assertThat(entry.getAttribute("cn").firstValueAsString()).isEqualTo(
                    entry.getAttribute("givenName").firstValueAsString() + " "
                    + entry.getAttribute("sn").firstValueAsString());

            checkEntryObjectClasses(entry, "top", "person", "organizationalPerson", "inetOrgPerson");
        } finally {
            Utils.closeSilently(generator);
        }
    }

    private void checkEntryObjectClasses(Entry entry, String...objectClasses) {
        Attribute ocAttribute = entry.getAttribute(getObjectClassAttributeType().getNameOrOID());
        assertThat(ocAttribute).isNotNull();
        Iterator<ByteString> it = ocAttribute.iterator();
        for (int i = 0; i < objectClasses.length; i++) {
            assertThat(it.next().toString()).isEqualTo(objectClasses[i]);
        }
        assertThat(it.hasNext()).isFalse();
    }

    private void checkPresenceOfAttributes(Entry entry, String... attributes) {
        for (int i = 0; i < attributes.length; i++) {
            assertThat(entry.getAttribute(attributes[i])).isNotNull();
        }
    }

    /**
     * Test a template with subtemplates, ensuring all expected DNs are generated.
     */
    @Test
    public void testTemplateWithSubTemplates() throws Exception {
        int numberOfUsers = 10;
        int numberOfGroups = 5;
        int numberOfOUs = 10;
        EntryGenerator generator = new EntryGenerator(
                "define suffix=dc=example,dc=com",
                "define maildomain=example.com",
                "define numusers=" + numberOfUsers,
                "define numous=" + numberOfOUs,
                "define numgroup=" + numberOfGroups,
                "",
                "branch: [suffix]",
                "subordinateTemplate: ous:[numous]",
                "",
                "template: ous",
                "subordinateTemplate: People:1",
                "subordinateTemplate: Groups:1",
                "rdnAttr: ou",
                "objectclass: top",
                "objectclass: organizationalUnit",
                "ou: Organization_<sequential:1>",
                "",
                "template: People",
                "rdnAttr: ou",
                "subordinateTemplate: person:[numusers]",
                "objectclass: top",
                "objectclass: organizationalUnit",
                "ou: People",
                "",
                "template: Groups",
                "subordinateTemplate: groupOfName:[numgroup]",
                "rdnAttr: ou",
                "objectclass: top",
                "objectclass: organizationalUnit",
                "ou: Groups",
                "",
                "template: person",
                "rdnAttr: uid",
                "objectClass: top",
                "objectClass: inetOrgPerson",
                "cn: <first> <last>",
                "employeeNumber: <sequential:0>",
                "uid: user.{employeeNumber}",
                "",
                "template: groupOfName",
                "rdnAttr: cn",
                "objectClass: top",
                "objectClass: groupOfNames",
                "cn: Group_<sequential:1>"
        ).setResourcePath(resourcePath);

        try {
            assertThat(generator.readEntry().getName().toString()).isEqualTo("dc=example,dc=com");
            int countUsers = 0;
            int countGroups = 1;
            for (int i = 1; i <= numberOfOUs; i++) {
                String dnOU = "ou=Organization_" + i + ",dc=example,dc=com";
                assertThat(generator.readEntry().getName().toString()).isEqualTo(dnOU);
                assertThat(generator.readEntry().getName().toString()).isEqualTo("ou=People," + dnOU);
                for (int j = countUsers; j < countUsers + numberOfUsers; j++) {
                    assertThat(generator.readEntry().getName().toString()).isEqualTo(
                            "uid=user." + j + ",ou=People," + dnOU);

                }
                countUsers += numberOfUsers;
                assertThat(generator.readEntry().getName().toString()).isEqualTo("ou=Groups," + dnOU);
                for (int j = countGroups; j < countGroups + numberOfGroups; j++) {
                    assertThat(generator.readEntry().getName().toString()).isEqualTo(
                            "cn=Group_" + j + ",ou=Groups," + dnOU);
                }
                countGroups += numberOfGroups;
            }
            assertThat(generator.hasNext()).isFalse();
        } finally {
            Utils.closeSilently(generator);
        }
    }

    /**
     * Test to show that reporting an error about an uninitialized variable when
     * generating templates reports the correct line.
     */
    @Test
    public void testMissingVariableErrorReport() throws Exception {
        String[] lines = {
        /* 0 */"template: template",
        /* 1 */"a: {missingVar}",
        /* 2 */"a: b",
        /* 3 */"a: c",
        /* 4 */"",
        /* 5 */"template: template2", };

        // Test must show "missingVar" missing on line 1.
        // Previous behaviour showed "missingVar" on line 5.

        TemplateFile templateFile = new TemplateFile(schema, null, resourcePath);
        List<LocalizableMessage> warns = new ArrayList<>();

        try {
            templateFile.parse(lines, warns);
            TestCaseUtils.failWasExpected(DecodeException.class);
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

    /** Test for parsing escaped character in templates. */
    @Test(dataProvider = "validTemplates")
    public void testParsingEscapeCharInTemplate(String testName, String[] lines) throws Exception {
        TemplateFile templateFile = new TemplateFile(schema, null, resourcePath);
        List<LocalizableMessage> warns = new ArrayList<>();
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
     * Test for escaped characters in templates.
     */
    @Test(dataProvider = "templatesToTestEscapeChars", dependsOnMethods = { "testParsingEscapeCharInTemplate" })
    public void testEscapeCharsFromTemplate(String testName, String[] lines, String attrName, String expectedValue)
            throws Exception {
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator(lines).setResourcePath(resourcePath);
            Entry topEntry = generator.readEntry();
            Entry entry = generator.readEntry();

            assertThat(topEntry).isNotNull();
            assertThat(entry).isNotNull();
            assertThat(entry.getAttribute(attrName).firstValueAsString()).isEqualTo(expectedValue);
        } finally {
            Utils.closeSilently(generator);
        }
    }

    /**
     * Test template that combines escaped characters and variables.
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
            "cn: Foo \\<<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>\\>\\{1\\}{sn}",
            "" };
        EntryGenerator generator = null;
        try {
            generator = new EntryGenerator(lines).setResourcePath(resourcePath);
            Entry topEntry = generator.readEntry();
            Entry entry = generator.readEntry();

            assertThat(topEntry).isNotNull();
            assertThat(entry).isNotNull();
            assertThat(entry.getAttribute("cn").firstValueAsString()).matches("Foo <[A-Z]>\\{1\\}Bar");
        } finally {
            Utils.closeSilently(generator);
        }
    }
}
