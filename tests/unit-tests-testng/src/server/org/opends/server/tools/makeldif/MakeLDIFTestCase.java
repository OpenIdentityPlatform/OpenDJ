/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2006 Brighton Consulting, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.tools.ToolsTestCase;
import org.opends.server.types.*;
import org.opends.server.util.LDIFReader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.opends.messages.ToolMessages.*;


/**
 * A set of test cases for the MakeLDIF tool.
 */
public class MakeLDIFTestCase
    extends ToolsTestCase
{
  private String resourcePath = null;

  @BeforeClass
  public void setUp() throws Exception
  {
    // The server must be running for these tests, so that
    // it can provide "getServerRoot()".
    TestCaseUtils.startServer();

    resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
  }

  /**
   * Test to show that reporting an error about an
   * uninitialized variable when generating templates reports the
   * correct line.
   */
  @Test()
  public void testParseTemplate() throws Exception
  {
    String[] lines =
    {
      /* 0 */ "template: template",
      /* 1 */ "a: {missingVar}",
      /* 2 */ "a: b",
      /* 3 */ "a: c",
      /* 4 */ "",
      /* 5 */ "template: template2",
    };

    // Test must show "missingVar" missing on line 1.
    // Previous behaviour showed "missingVar" on line 5.

    TemplateFile templateFile = new TemplateFile(resourcePath);
    List<Message> warns = new ArrayList<Message>();

    try
    {
      templateFile.parse(lines, warns);
    }
    catch (InitializationException e)
    {
      String msg = e.getMessage();
      Message msg_locale = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get("missingVar",1);
      assertTrue (msg.equals(msg_locale.toString()), msg);
    }
  }

  @DataProvider (name="validTemplates")
  public Object[][] createTestTemplates() {
    return new Object[][] {
        { "CurlyBracket",
          new String[] {
            "template: templateWithEscape",
            "rdnAttr: uid",
            "uid: testEntry",
            "cn: I\\{Foo\\}F"} },
        { "AngleBracket",
          new String[] {
            "template: templateWithEscape",
            "rdnAttr: uid",
            "uid: testEntry",
            "sn: \\<Bar\\>"} },
        { "SquareBracket",
            new String[] {
                "template: templateWithEscape",
                "rdnAttr: uid",
                "uid: testEntry",
                "description: \\[TEST\\]"} },
        { "BackSlash",
            new String[] {
                "template: templateWithEscape",
                "rdnAttr: uid",
                "uid: testEntry",
                "description: Foo \\\\ Bar"} },
        { "EscapedAlpha",
            new String[] {
                "template: templateWithEscape",
                "rdnAttr: uid",
                "uid: testEntry",
                "description: Foo \\\\Bar"} },
        { "Normal Variable",
            new String[] {
                "template: templateNormal",
                "rdnAttr: uid",
                "uid: testEntry",
                "sn: {uid}"} },
        { "Constant",
            new String[] {
                "define foo=Test123",
                "",
                "template: templateConstant",
                "rdnAttr: uid",
                "uid: testEntry",
                "sn: {uid}",
                "cn: [foo]"} },
    };
  }

  /**
   * Test for parsing escaped  character in templates
   */
  @Test(dataProvider = "validTemplates")
  public void testParsingEscapeCharInTemplate(String testName, String[] lines)
      throws Exception
  {
    TemplateFile templateFile = new TemplateFile(resourcePath);
    List<Message> warns = new ArrayList<Message>();
    templateFile.parse(lines, warns);
    assertTrue(warns.isEmpty(),"Warnings in parsing test template " + testName );
  }

  @DataProvider (name="templatesToTestLDIFOutput")
  public Object[][] createTemplatesToTestLDIFOutput() {
    return new Object[][]{
        {
            "Curly",
            new String[]{
                "branch: dc=test",
                "subordinateTemplate: templateWithEscape:1",
                "",
                "template: templateWithEscape",
                "rdnAttr: uid",
                "objectclass: inetOrgPerson",
                "uid: testEntry",
                "cn: I\\{ Foo \\}F"},
            "cn", // Attribute to test
            "I{ Foo }F", // Expected value
        },
        {
            "Angle",
            new String[]{
                "branch: dc=test",
                "subordinateTemplate: templateWithEscape:1",
                "",
                "template: templateWithEscape",
                "rdnAttr: uid",
                "objectclass: inetOrgPerson",
                "uid: testEntry",
                "sn: \\< Bar \\>"},
            "sn", // Attribute to test
            "< Bar >", // Expected value
        },
        {
            "Square",
            new String[]{
                "branch: dc=test",
                "subordinateTemplate: templateWithEscape:1",
                "",
                "template: templateWithEscape",
                "rdnAttr: uid",
                "objectclass: inetOrgPerson",
                "uid: testEntry",
                "description: \\[TEST\\]"},
            "description", // Attribute to test
            "[TEST]", // Expected value
        },
        {
            "BackSlash",
            new String[]{
                "branch: dc=test",
                "subordinateTemplate: templateWithEscape:1",
                "",
                "template: templateWithEscape",
                "rdnAttr: uid",
                "objectclass: inetOrgPerson",
                "uid: testEntry",
                "displayName: Foo \\\\ Bar"},
            "displayname", // Attribute to test
            "Foo \\ Bar", // Expected value
        },
        {
            "MultipleSquare",
            new String[]{
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
                ""},
            "manager", // Attribute to test
            "cn=Bar,ou=group,dc=test,dc=com", // Expected value
        },
        {
            "MixedSquare",
            new String[]{
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
                "description: test [container] \\[[top]\\]",
                "",},
            "description", // Attribute to test
            "test ou=group [dc=com]", // Expected value
        },
        {
            "NoConstantBecauseEscaped",
            new String[]{
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
                "description: test \\[top]",
                "",},
            "description", // Attribute to test
            "test [top]", // Expected value
        },
       {
            "NoConstantBecauseStrangeChar",
            new String[]{
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
                "",},
            "description", // Attribute to test
            "test [group [top]", // Expected value
        },
        /* If adding a test, please copy and reuse template code down below
        {
            "",
            new String[]{
                "template: templateWithEscape",
                "rdnAttr: uid",
                "uid: testEntry",
                "cn: I\\{Foo\\}F"},
            "", // Attribute to test
            "", // Expected value
        }
        */
    };
  }

  /**
   * Test for escaped characters in templates, check LDIF output
   */
  @Test(dataProvider="templatesToTestLDIFOutput", dependsOnMethods = { "testParsingEscapeCharInTemplate"})
  public void testLDIFOutputFromTemplate(String testName, String[] lines,
                                         String attrName, String expectedValue) throws Exception
  {
    File tmpFile = File.createTempFile(testName, "out.ldif");
    tmpFile.deleteOnExit();
    String outLdifFilePath = tmpFile.getAbsolutePath();

    LdifFileWriter.makeLdif(outLdifFilePath, resourcePath, lines);

    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry top = reader.readEntry();
    Entry e = reader.readEntry();
    reader.close();

    assertNotNull(top);
    assertNotNull(e);
    List<Attribute> attrs = e.getAttribute(attrName);
    assertFalse(attrs.isEmpty());
    Attribute a = attrs.get(0);
    Attribute expectedRes = Attributes.create(attrName, expectedValue);
    assertEquals(a, expectedRes);
  }

  /**
   * Test for escaped characters in templates, check LDIF output when
   * the templates combines escaped characters and variables
   */
  @Test(dependsOnMethods = { "testParsingEscapeCharInTemplate"})
  public void testOutputCombineEscapeCharInTemplate() throws Exception
  {
    String[] lines =
        {
            "branch: dc=test",
            "subordinateTemplate: templateWithEscape:1",
            "",
            "template: templateWithEscape",
            "rdnAttr: uid",
            "objectclass: inetOrgPerson",
            "uid: testEntry",
            "sn: Bar",
            // The value below combines variable, randoms and escaped chars.
            // The resulting value is "Foo <?>{1}Bar" where ? is a letter from [A-Z].
            "cn: Foo \\<<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>\\>\\{1\\}{sn}",
            "",
        };


    File tmpFile = File.createTempFile("combineEscapeChar", "out.ldif");
    tmpFile.deleteOnExit();
    String outLdifFilePath = tmpFile.getAbsolutePath();

    LdifFileWriter.makeLdif(outLdifFilePath, resourcePath, lines);

    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry top = reader.readEntry();
    Entry e = reader.readEntry();
    reader.close();

    assertNotNull(top);
    assertNotNull(e);
    List<Attribute> attrs = e.getAttribute("cn");
    assertFalse(attrs.isEmpty());
    Attribute a = attrs.get(0);
    assertTrue(a.iterator().next().toString().matches("Foo <[A-Z]>\\{1\\}Bar"),
        "cn value doesn't match the expected value");
  }
}

