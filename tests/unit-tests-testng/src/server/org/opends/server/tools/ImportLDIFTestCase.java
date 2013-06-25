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
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import static org.testng.Assert.*;
import java.io.*;
import java.util.*;

import org.testng.annotations.AfterClass;

import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.DN;
import org.opends.server.types.Attribute;



public class ImportLDIFTestCase extends ToolsTestCase
{

  private File tempDir;

  private String ldifFilePath;

  String configFilePath;

  private String homeDirName;

  private String beID;

  private String baseDN = "dc=example,dc=com";



  /**
   * Ensures that the ldif file is created with the entry.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.restartServer();
    beID = "userRoot";
    configFilePath = DirectoryServer.getConfigFile();
    TaskUtils.disableBackend(beID);

    String entry = "dn: dc=example,dc=com\n" + "objectclass: domain\n"
        + "objectclass: top\n" + "dc: example\n\n"
        + "dn: uid=user.0,dc=example,dc=com\n" + "objectClass: person\n"
        + "objectClass: inetorgperson\n"
        + "objectClass: organizationalPerson\n" + "objectClass: top\n"
        + "givenName: Aaccf\n" + "sn: Amar\n" + "cn: Aaccf Amar\n"
        + "employeeNumber: 0\n" + "uid: user.0\n"
        + "mail: user.0@example.com\n" + "userPassword: password\n"
        + "telephoneNumber: +1 380-535-2354\n"
        + "description: This is the description for Aaccf Amar\n"
        + "creatorsName: cn=Import\n" + "modifiersName: cn=Import\n";

    tempDir = TestCaseUtils.createTemporaryDirectory("importLDIFtest");
    homeDirName = tempDir.getAbsolutePath();
    ldifFilePath = homeDirName + File.separator + "entries.ldif";
    FileOutputStream ldifFile = new FileOutputStream(ldifFilePath);
    PrintStream writer = new PrintStream(ldifFile);
    writer.println(entry);
    writer.close();
    ldifFile.close();
  }



  /**
   * Tests an import of LDIF with only the operational attributes
   * included.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportIncludeOnlyOperational() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
        "-f",
        configFilePath,
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-R",
        rejectFilePath,
        "-n",
        beID,
        "-i",
        "+"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    // Expecting a non-empty reject file.
    assertRejectedFile(reject, false);
  }



  /**
   * Tests an import of LDIF with only thel user attributes included.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportIncludeOnlyUser() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
        "-f",
        configFilePath,
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-R",
        rejectFilePath,
        "-n",
        beID,
        "-i",
        "*"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    // Expecting an empty reject file.
    assertRejectedFile(reject, true);

    Attribute[] opAttr =
    {
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import")
    };
    // operational attributes shouldn't be present.
    assertEntry(opAttr, false);
  }



  /**
   * Tests a simple Import LDIF with none of the attributes excluded
   * or included. It is expected to import the entry(ies) with all the
   * attributes in the ldif file.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportDefault() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    // Reject file should be empty.
    assertRejectedFile(reject, true);
    // check the presence of some random attributes.
    Attribute[] attr =
    {
        Attributes.create("description",
            "This is the description for Aaccf Amar"),
        Attributes.create("mail", "user.0@example.com"),
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import")
    };
    assertEntry(attr, true);
  }



  /**
   * Tests a simple Import LDIF using base DN with none of the
   * attributes excluded or included. It is expected to import the
   * entry(ies) with all the attributes in the ldif file.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportDefaultBaseDN() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-b",
        baseDN,
        "-R",
        rejectFilePath
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    // Reject file should be empty.
    assertRejectedFile(reject, true);
    // check the presence of some random attributes.
    Attribute[] attr =
    {
        Attributes.create("description",
            "This is the description for Aaccf Amar"),
        Attributes.create("mail", "user.0@example.com"),
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import")
    };
    assertEntry(attr, true);
  }



  /**
   * Tests an import of LDIF with all the user attributes included but
   * "description"
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFIncludeUserExcludeDescription() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-i",
        "*",
        "-e",
        "description"
    };

    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, true);
    Attribute[] attr =
    {
      Attributes.create("description",
          "This is the description for Aaccf Amar")
    };
    assertEntry(attr, false);
  }



  /**
   * Tests an import of LDIF with all user attributes excluded option.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFExcludeUser() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "*"
    };

    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, false);
  }



  /**
   * Tests an import of LDIF with all the operational attributes
   * excluded option.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFExcludeOperational() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "+"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, true);
    Attribute[] attrs =
    {
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import")
    };
    assertEntry(attrs, false);
  }



  /**
   * Tests an import of LDIF with all user attributes and one
   * operational attribute included..
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFUserAndOperational() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-i",
        "*",
        "-i",
        "creatorsname"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, true);
    Attribute[] attrs =
    {
      Attributes.create("creatorsname", "cn=Import")
    };
    assertEntry(attrs, true);
  }



  /**
   * Tests an import of LDIF with select user and operational
   * attributes included..
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFSelectiveIncludeAttributes() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-i",
        "cn",
        "-i",
        "uid",
        "-i",
        "dc",
        "-i",
        "sn",
        "-i",
        "creatorsname"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, true);
    Attribute[] attrsPr =
    {
      Attributes.create("creatorsname", "cn=Import")
    };
    assertEntry(attrsPr, true);
    Attribute[] attrsAb =
    {
        Attributes.create("givenname", "Aaccf"),
        Attributes.create("employeenumber", "0")
    };
    assertEntry(attrsAb, false);
  }



  /**
   * Tests an import of LDIF with select user and operational
   * attributes encluded..
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFSelectiveExcludeAttributes() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
        "-f",
        DirectoryServer.getConfigFile(),
        "--noPropertiesFile",
        "-l",
        ldifFilePath,
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "givenName",
        "-e",
        "creatorsname"
    };
    assertEquals(
        ImportLDIF.mainImportLDIF(args, false, System.out, System.err), 0);
    assertRejectedFile(reject, true);
    Attribute[] attrsPr =
    {
        Attributes.create("modifiersname", "cn=Import"),
        Attributes.create("employeenumber", "0")
    };
    assertEntry(attrsPr, true);
    Attribute[] attrsAb =
    {
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("givenname", "Aaccf")
    };
    assertEntry(attrsAb, false);
  }



  /**
   * Utility method which is called by the testcase for asserting the
   * rejected file.
   *
   * @param reject
   *          The file to be asserted
   * @param shouldBeEmpty
   *          whether the file should be empty.
   */
  private void assertRejectedFile(File reject, boolean shouldBeEmpty)
  {
    if (shouldBeEmpty)
    {
      assertEquals(reject.length(), 0);
    }
    else
    {
      assertFalse(reject.length() == 0);
    }
    reject.delete();
  }



  /**
   * Utility method which is called by the testcase for asserting the
   * imported entry.
   *
   * @param attrs
   *          The array of attributes to be asserted.
   * @param assertType
   *          the boolean flag for assert type.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private void assertEntry(Attribute[] attrs, boolean assertType)
      throws Exception
  {
    if (attrs != null && attrs.length > 0)
    {
      TaskUtils.enableBackend(beID);
      Entry entry = DirectoryServer.getEntry(DN
          .decode(" uid=user.0,dc=example,dc=com"));
      TaskUtils.disableBackend(beID);
      assertNotNull(entry);
      List<Attribute> list = entry.getAttributes();
      for (Attribute a : attrs)
      {
        if (assertType)
        {
          assertTrue(list.contains(a));
        }
        else
        {
          assertFalse(list.contains(a));
        }
      }
    }
  }



  /**
   * Clean up method.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanUp() throws Exception
  {
    // reinstate the backend.
    TaskUtils.enableBackend(beID);
    TestCaseUtils.deleteDirectory(tempDir);
  }
}
