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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Entry;
import org.opends.server.util.LDIFReader;
import java.io.*;
import java.util.Map;
import java.util.HashMap;


/**
 * LDIFSearch test cases.
 */
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
 @Test()
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
    assertEquals(LDIFSearch.mainSearch(args, false), 0);
    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry e=reader.readEntry();
    reader.close();
    assertNotNull(e.getAttribute("objectclass"));
  }

  /**
   * Test that objectclass attribute is not returned when all operational
   * attributes  only is specified '+'.
   *
   * @throws Exception  The objectclass attribute is returned.
   */
 @Test()
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
    assertEquals(LDIFSearch.mainSearch(args, false), 0);
    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry e=reader.readEntry();
    reader.close();
    assertNull(e.getAttribute("objectclass"));
  }

  /**
   * Test that objectclass attribute is not returned when attributes are
   * requested to be returned along with all operational attributes '+'.
   *
   * @throws Exception  The objectclass attribute is returned or one of the
   * specified attributes is not returned.
   */
 @Test()
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
    assertEquals(LDIFSearch.mainSearch(args, false), 0);
    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry e=reader.readEntry();
    reader.close();
    assertNull(e.getAttribute("objectclass"));
    assertNotNull(e.getAttribute("mail"));
    assertNotNull(e.getAttribute("uid"));
  }

  /**
   * Test that objectclass attribute is not returned when specific attributes
   * are requested to be returned.
   *
   * @throws Exception  The objectclass attribute is returned or one of the
   * specified attributes is not returned.
   */

 @Test()
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
    assertEquals(LDIFSearch.mainSearch(args, false), 0);
    LDIFImportConfig ldifConfig = new LDIFImportConfig(outLdifFilePath);
    ldifConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(ldifConfig);
    Entry e=reader.readEntry();
    reader.close();
    assertNull(e.getAttribute("objectclass"));
    assertNotNull(e.getAttribute("mail"));
    assertNotNull(e.getAttribute("uid"));
  }
}
