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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ImportLDIFTestCase extends ToolsTestCase
{

  private File tempDir;
  private String ldifFilePath;
  private String configFilePath;
  private String homeDirName;
  private String beID;
  private String baseDN = "dc=example,dc=com";

  /**
   * Ensures that the ldif file is created with the entry.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.restartServer();
    beID = "userRoot";
    configFilePath = DirectoryServer.getConfigFile();
    TaskUtils.disableBackend(beID);

    String entry =
        "dn: dc=example,dc=com\n"
        + "objectclass: domain\n"
        + "objectclass: top\n"
        + "dc: example\n"
        + "\n"
        + "dn: uid=user.0,dc=example,dc=com\n"
        + "objectClass: person\n"
        + "objectClass: inetorgperson\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: top\n"
        + "givenName: Aaccf\n"
        + "sn: Amar\n"
        + "cn: Aaccf Amar\n"
        + "employeeNumber: 0\n"
        + "uid: user.0\n"
        + "mail: user.0@example.com\n"
        + "userPassword: password\n"
        + "telephoneNumber: +1 380-535-2354\n"
        + "description: This is the description for Aaccf Amar\n"
        + "creatorsName: cn=Import\n"
        + "modifiersName: cn=Import\n";

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
        "-F",
        "-R",
        rejectFilePath,
        "-n",
        beID,
        "-i",
        "+"
    };
    assertEquals(importLDIF(args), 0);
    // Expecting a non-empty reject file.
    assertRejectedFile(reject, false);
  }



  /**
   * Tests an import of LDIF with only the user attributes included.
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
        "-F",
        "-R",
        rejectFilePath,
        "-n",
        beID,
        "-i",
        "*"
    };
    assertEquals(importLDIF(args), 0);
    // Expecting an empty reject file.
    assertRejectedFile(reject, true);

    // operational attributes shouldn't be present.
    assertEntry(false,
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import"));
  }



  /**
   * Tests a simple Import LDIF with none of the attributes excluded
   * or included. It is expected to import the entry(ies) with all the
   * attributes in the ldif file.
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath
    };
    assertEquals(importLDIF(args), 0);
    // Reject file should be empty.
    assertRejectedFile(reject, true);
    // check the presence of some random attributes.
    assertEntry(true,
        Attributes.create("description", "This is the description for Aaccf Amar"),
        Attributes.create("mail", "user.0@example.com"),
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import"));
  }



  /**
   * Tests a simple Import LDIF using base DN with none of the
   * attributes excluded or included. It is expected to import the
   * entry(ies) with all the attributes in the ldif file.
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
        "-F",
        "-b",
        baseDN,
        "-R",
        rejectFilePath
    };
    assertEquals(importLDIF(args), 0);
    // Reject file should be empty.
    assertRejectedFile(reject, true);
    // check the presence of some random attributes.
    assertEntry(true,
        Attributes.create("description", "This is the description for Aaccf Amar"),
        Attributes.create("mail", "user.0@example.com"),
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import"));
  }



  /**
   * Tests an import of LDIF with all the user attributes included but
   * "description".
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-i",
        "*",
        "-e",
        "description"
    };

    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, true);
    assertEntry(false,
        Attributes.create("description", "This is the description for Aaccf Amar"));
  }



  /**
   * Tests an import of LDIF with all user attributes excluded option.
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "*"
    };

    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, false);
  }



  /**
   * Tests an import of LDIF with all the operational attributes
   * excluded option.
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "+"
    };
    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, true);
    assertEntry(false,
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("modifiersname", "cn=Import"));
  }

  /**
   * Tests an import of LDIF with all user attributes and one
   * operational attribute included..
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-i",
        "*",
        "-i",
        "creatorsname"
    };
    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, true);
    assertEntry(true, Attributes.create("creatorsname", "cn=Import"));
  }



  /**
   * Tests an import of LDIF with select user and operational
   * attributes included..
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
        "-F",
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
    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, true);
    assertEntry(true,
        Attributes.create("creatorsname", "cn=Import"));
    assertEntry(false,
        Attributes.create("givenname", "Aaccf"),
        Attributes.create("employeenumber", "0"));
  }



  /**
   * Tests an import of LDIF with select user and operational attributes
   * included.
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
        "-F",
        "-n",
        beID,
        "-R",
        rejectFilePath,
        "-e",
        "givenName",
        "-e",
        "creatorsname"
    };
    assertEquals(importLDIF(args), 0);
    assertRejectedFile(reject, true);
    assertEntry(true,
        Attributes.create("modifiersname", "cn=Import"),
        Attributes.create("employeenumber", "0"));
    assertEntry(false,
        Attributes.create("creatorsname", "cn=Import"),
        Attributes.create("givenname", "Aaccf"));
  }

  private int importLDIF(String[] args)
  {
    return ImportLDIF.mainImportLDIF(args, false, System.out, System.err);
  }

  /**
   * Utility method which is called by the testcase for asserting the rejected
   * file.
   *
   * @param reject
   *          The file to be asserted
   * @param shouldBeEmpty
   *          whether the file should be empty.
   */
  private void assertRejectedFile(File reject, boolean shouldBeEmpty) throws Exception
  {
    try
    {
      final RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
      final String errorMsg = "Unexpected content in reject file:\n\n" + readFile(reject)
          + "\n\nThe backend was configured with the following base DNs: "
          + root.getBackend(beID).getBaseDN() + "\n\n";
      assertEquals(reject.length() == 0, shouldBeEmpty, errorMsg);
    }
    finally
    {
      reject.delete();
    }
  }



  /**
   * Utility method which is called by the testcase for asserting the imported
   * entry.
   * @param attrsShouldExistInEntry
   *          the boolean flag for assert type.
   * @param attrs
   *          The array of attributes to be asserted.
   */
  private void assertEntry(boolean attrsShouldExistInEntry, Attribute... attrs)
      throws Exception
  {
    if (attrs != null && attrs.length > 0)
    {
      TaskUtils.enableBackend(beID);
      Entry entry = DirectoryServer.getEntry(
          DN.valueOf(" uid=user.0,dc=example,dc=com"));
      TaskUtils.disableBackend(beID);
      assertNotNull(entry);
      for (Attribute a : attrs)
      {
        assertEquals(entry.getAttributes().contains(a), attrsShouldExistInEntry);
      }
    }
  }

  /** Clean up method. */
  @AfterClass
  public void cleanUp() throws Exception
  {
    // reinstate the backend.
    TaskUtils.enableBackend(beID);
    TestCaseUtils.deleteDirectory(tempDir);
  }
}
