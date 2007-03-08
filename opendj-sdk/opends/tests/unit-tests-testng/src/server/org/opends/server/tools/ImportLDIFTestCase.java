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

import static org.opends.server.config.ConfigConstants.DN_BACKEND_BASE;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.util.Base64;
import org.opends.server.api.Backend;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import static org.testng.Assert.*;
import java.io.*;
import java.util.*;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.tools.ToolsTestCase;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Entry;
import org.opends.server.types.DN;
import org.opends.server.types.Attribute;
import static org.testng.Assert.*;



public class ImportLDIFTestCase extends ToolsTestCase {

  private File tempDir;
  private String ldifFilePath;
  String configFilePath ;
  private BackendImpl backend;
  private String homeDirName;
  private ConfigEntry backendConfigEntry;
  private String beID;

  /**
  * Ensures that the ldif file is created with the entry.
  *
  * @throws  Exception  If an unexpected problem occurs.
  */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    beID = "userRoot";
	  configFilePath = DirectoryServer.getInstance().getConfigFile();
    backend = (BackendImpl)DirectoryServer.getBackend(beID);
    backendConfigEntry = TaskUtils.getConfigEntry(backend);
    TaskUtils.setBackendEnabled(backendConfigEntry, false);

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

    tempDir = TestCaseUtils.createTemporaryDirectory("importLDIFtest");
    homeDirName = tempDir.getAbsolutePath();
    ldifFilePath =  homeDirName + File.separator + "entries.ldif";
    FileOutputStream ldifFile = new FileOutputStream(ldifFilePath);
    PrintStream writer = new PrintStream(ldifFile);
    writer.println(entry);
    writer.close();
    ldifFile.close();
  }



  /**
   * Tests an  import of LDIF with only the operational attributes
   * included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportIncludeOnlyOperational() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
      "-f",configFilePath,
      "-l",ldifFilePath,
      "-R", rejectFilePath,
      "-n", beID,
      "-i", "+"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    //Expecting a non-empty reject file.
    assertRejectedFile(reject,false);
  }



  /**
   * Tests an  import of LDIF with only thel user attributes
   * included.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportIncludeOnlyUser()  throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
      "-f",configFilePath,
      "-l",ldifFilePath,
      "-R", rejectFilePath,
      "-n", beID,
      "-i", "*"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    //Expecting an empty reject file.
    assertRejectedFile(reject,true);

    Attribute[]  opAttr =
    {
      new Attribute ("creatorsname", "Import") ,
      new Attribute("modifiersname","Import")
     }    ;
    //operational attributes shouldn't be present.
    assertEntry(opAttr,false);
  }



  /**
   * Tests a simple Import LDIF with none of the attributes
   * excluded or included. It is expected to import the entry(ies)
   * with all the attributes in the ldif file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportDefault() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R", rejectFilePath
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false),0);
    //Reject file should be empty.
    assertRejectedFile(reject,true);
    //check the presence of some random attributes.
    Attribute[]  attr =
    {
      new Attribute ("description",
          "This is the description for Aaccf Amar"),
      new Attribute("mail","user.0@example.com"),
      new Attribute ("creatorsname", "Import") ,
      new Attribute("modifiersname","Import")
    }    ;
    assertEntry(attr,true);
  }



  /**
   * Tests an  import of LDIF with  all the user attributes included
   * but  "description"
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFIncludeUserExcludeDescription()
                          throws Exception
  {
   File reject = File.createTempFile("reject", ".ldif");
   String rejectFilePath = reject.getAbsolutePath();

    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-i","*",
      "-e", "description"
    };

    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,true);
    Attribute[] attr =
     {
       new Attribute ("description",
            "This is the description for Aaccf Amar")
    };
    assertEntry(attr,false);
  }



  /**
   * Tests an  import of LDIF with  all user attributes excluded option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFExcludeUser() throws Exception
  {
    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-e", "*"
    };

    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,false);
  }



  /**
   * Tests an  import of LDIF with  all the operational attributes excluded option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFExcludeOperational() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-e", "+"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,true);
    Attribute[] attrs = {
       new Attribute ("creatorsname", "Import") ,
       new Attribute("modifiersname","Import")
    };
    assertEntry(attrs,false);
  }



   /**
   * Tests an  import of LDIF with all user attributes  and
   * one operational attribute included..
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFUserAndOperational() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-i", "*",
      "-i","creatorsname"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,true);
    Attribute[] attrs = {
       new Attribute ("creatorsname", "Import")
    };
    assertEntry(attrs,true);
  }



   /**
   * Tests an  import of LDIF with select user and operational
   * attributes included..
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFSelectiveIncludeAttributes() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-i", "cn",
      "-i", "uid",
      "-i", "dc",
      "-i", "sn",
      "-i","creatorsname"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,true);
    Attribute[] attrsPr = {
       new Attribute ("creatorsname", "Import")
    };
    assertEntry(attrsPr,true);
    Attribute[] attrsAb = {
       new Attribute ("givenname", "Aaccf"),
       new Attribute("employeenumber","0")
    };
    assertEntry(attrsAb,false);
  }



  /**
   * Tests an  import of LDIF with select user and operational
   * attributes encluded..
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFSelectiveExcludeAttributes() throws Exception
  {

    File reject = File.createTempFile("reject", ".ldif");
    String rejectFilePath = reject.getAbsolutePath();
    String[] args =
    {
      "-f", DirectoryServer.getInstance().getConfigFile(),
      "-l",ldifFilePath,
      "-n", beID,
      "-R",rejectFilePath,
      "-e", "givenName",
      "-e","creatorsname"
    };
    assertEquals(ImportLDIF.mainImportLDIF(args,false), 0);
    assertRejectedFile(reject,true);
    Attribute[] attrsPr = {
       new Attribute ("modifiersname", "Import"),
       new Attribute("employeenumber","0")
    };
    assertEntry(attrsPr,true);
    Attribute[] attrsAb = {
       new Attribute ("creatorsname", "Import"),
       new Attribute("givenname","Aaccf")
    };
    assertEntry(attrsAb,false);
  }



  /**
   * Utility method which is called by the testcase for asserting
   * the rejected file.
   *
   * @param reject The file to be asserted
   * @param shouldBeEmpty whether the file should be empty.
   */
  private void assertRejectedFile(File reject, boolean shouldBeEmpty)
  {
    if(shouldBeEmpty)
    {
      assertEquals(reject.length(),0);
    }
    else
    {
      assertFalse(reject.length()==0);
    }
    reject.delete();
  }


  /**
   * Utility method which is called by the testcase for asserting
   * the imported entry.
   *
   * @param attrs The array of attributes to be asserted.
   * @param assertType the boolean flag for assert type.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void assertEntry(Attribute[] attrs,boolean assertType)  throws Exception
  {
    if(attrs != null && attrs.length > 0)
    {
      TaskUtils.setBackendEnabled(backendConfigEntry, true);
      Entry  entry = DirectoryServer.getEntry(DN.decode(
        " uid=user.0,dc=example,dc=com"));
      TaskUtils.setBackendEnabled(backendConfigEntry,false);
      assertNotNull(entry);
      List<Attribute> list = entry.getAttributes();
      for(int i=0;i<attrs.length;i++)
      {
        if(assertType)
        {
          assertTrue(list.contains(attrs[i]));
        }
        else
        {
          assertFalse(list.contains(attrs[i]));
        }
      }
    }
  }



  /**
   * Clean up method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanUp() throws Exception
  {
    //reinstate the backend.
    TaskUtils.setBackendEnabled(backendConfigEntry, true);
    TestCaseUtils.deleteDirectory(tempDir);
  }
}
