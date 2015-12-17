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
 *      Portions Copyright 2015 ForgeRock AS
 */

package org.opends.server.tools;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** LDIFSearch test cases. */
@SuppressWarnings("javadoc")
public class LDIFSearchTestCase extends ToolsTestCase {


  private String ldifFilePath;
  private String outLdifFilePath;
  private File tempDir;

  @BeforeClass
  public void setUp() throws Exception {
    TestCaseUtils.startServer();
    String entry =
            "dn: dc=example,dc=com\n" +
                    "objectclass: domain\n" +
                    "objectclass: top\n" +
                    "dc: example\n\n" +
                    "dn: uid=user.0,dc=example,dc=com\n" +
                    "objectClass: person\n" +
                    "objectClass: inetorgperson\n" +
                    "objectClass: organizationalPerson\n" +
                    "objectClass: top\n" +
                    "givenName: Aaccf\n" +
                    "sn: Amar\n" +
                    "cn: Aaccf Amar\n" +
                    "employeeNumber: 0\n" +
                    "uid: user.0\n" +
                    "mail: user.0@example.com\n" +
                    "userPassword: password\n" +
                    "telephoneNumber: 380-535-2354\n" +
                    "description: This is the description for Aaccf Amar\n" +
                    "creatorsName: Import\n" +
                    "modifiersName: Import\n";

    tempDir = TestCaseUtils.createTemporaryDirectory("LDIFSearchtest");
    String homeDirName = tempDir.getAbsolutePath();
    ldifFilePath =  homeDirName + File.separator + "entries.ldif";
    outLdifFilePath =  homeDirName + File.separator + "out.ldif";
    FileOutputStream ldifFile = new FileOutputStream(ldifFilePath);
    PrintStream writer = new PrintStream(ldifFile);
    writer.println(entry);
    writer.close();
    ldifFile.close();
  }


  /**
   * Clean up method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanUp() throws Exception
  {
     TestCaseUtils.deleteDirectory(tempDir);
  }


  /**
   * Test that objectclass is returned when both user attributes '*' and
   * operational attributes is specified '+'.
   *
   * @throws Exception The objectclass attribute is not returned.
   */
 @Test
  public void testLDIFSearchStarOps() throws Exception {
    String[] args =
    {
      "-b", "uid=user.0, dc=example,dc=com",
      "-l", ldifFilePath,
      "-o", outLdifFilePath,
      "-O",
      "(objectclass=*)",
      "*", "+"
    };
    assertEquals(LDIFSearch.mainSearch(args, false, System.out, System.err), 0);
    Entry e = readEntry();
    assertThat(e.getAttribute("objectclass")).isNotEmpty();
  }

  /**
   * Test that objectclass attribute is not returned when all operational
   * attributes  only is specified '+'.
   *
   * @throws Exception  The objectclass attribute is returned.
   */
  @Test
  public void testLDIFSearchOpsOnly() throws Exception {
    String[] args =
    {
      "-b", "uid=user.0, dc=example,dc=com",
      "-l", ldifFilePath,
      "-o", outLdifFilePath,
      "-O",
      "(objectclass=*)",
      "+"
    };
    assertEquals(LDIFSearch.mainSearch(args, false, System.out, System.err), 0);
    Entry e = readEntry();
    assertThat(e.getAttribute("objectclass")).isEmpty();
  }

  /**
   * Test that objectclass attribute is not returned when attributes are
   * requested to be returned along with all operational attributes '+'.
   *
   * @throws Exception  The objectclass attribute is returned or one of the
   * specified attributes is not returned.
   */
 @Test
  public void testLDIFSearchOpsAttrs() throws Exception {
    String[] args =
    {
      "-b", "uid=user.0, dc=example,dc=com",
      "-l", ldifFilePath,
      "-o", outLdifFilePath,
      "-O",
      "(objectclass=*)",
      "+", "mail", "uid"
    };
    assertEquals(LDIFSearch.mainSearch(args, false, System.out, System.err), 0);
    Entry e = readEntry();
    assertThat(e.getAttribute("objectclass")).isEmpty();
    assertThat(e.getAttribute("mail")).isNotEmpty();
    assertThat(e.getAttribute("uid")).isNotEmpty();
  }


  private Entry readEntry() throws IOException, LDIFException
  {
    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    try (LDIFReader reader = new LDIFReader(ldifConfig))
    {
      return reader.readEntry();
    }
  }

  /**
   * Test that objectclass attribute is not returned when specific attributes
   * are requested to be returned.
   *
   * @throws Exception  The objectclass attribute is returned or one of the
   * specified attributes is not returned.
   */
  @Test
  public void testLDIFSearchAttrsOnly() throws Exception {

    String[] args =
    {
      "-b", "uid=user.0, dc=example,dc=com",
      "-l", ldifFilePath,
      "-o", outLdifFilePath,
      "-O",
      "(objectclass=*)",
      "mail", "uid"
    };
    assertEquals(LDIFSearch.mainSearch(args, false, System.out, System.err), 0);
    Entry e = readEntry();
    assertThat(e.getAttribute("objectclass")).isEmpty();
    assertThat(e.getAttribute("mail")).isNotEmpty();
    assertThat(e.getAttribute("uid")).isNotEmpty();
  }
}
