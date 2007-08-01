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
package org.opends.server.admin.client.cli;



import java.io.File;
import java.io.FileWriter;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;

import static org.testng.Assert.*;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;



/**
 * A set of test cases for the dsservice tool.
 */
public class DsframeworkTestCase
{
  // The path to a file containing an invalid bind password.
  private String invalidPasswordFile;

  // The path to a file containing a valid bind password.
  private String validPasswordFile;



  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void before()
         throws Exception
  {
    TestCaseUtils.startServer();

    File pwFile = File.createTempFile("valid-bind-password-", ".txt");
    pwFile.deleteOnExit();
    FileWriter fileWriter = new FileWriter(pwFile);
    fileWriter.write("password" + System.getProperty("line.separator"));
    fileWriter.close();
    validPasswordFile = pwFile.getAbsolutePath();

    pwFile = File.createTempFile("invalid-bind-password-", ".txt");
    pwFile.deleteOnExit();
    fileWriter = new FileWriter(pwFile);
    fileWriter.write("wrongPassword" + System.getProperty("line.separator"));
    fileWriter.close();
    invalidPasswordFile = pwFile.getAbsolutePath();

    String[] args =
    {
      "create-ads",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "--backendName", "admin"
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }

  /**
   * Ensures ADS is removed.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void afterClass()
         throws Exception
  {
    String[] args =
    {
      "delete-ads",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "--backendName", "admin"
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-groups with a malformed bind DN.
   */
  @Test()
  public void testMalformedBindDN()
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "malformed",
      "-w", "password"
    };

    assertFalse(DsFrameworkCliMain.mainCLI(args, false, null, null)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-groups with a nonexistent bind DN.
   */
  @Test()
  public void testNonExistentBindDN()
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Does Not Exist",
      "-w", "password"
    };

    assertFalse(DsFrameworkCliMain.mainCLI(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-groups with an invalid password.
   */
  @Test()
  public void testInvalidBindPassword()
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "wrongPassword"
    };

    assertFalse(DsFrameworkCliMain.mainCLI(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }




  /**
   * Tests list-groups with a valid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-groups with an invalid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInvalidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j",invalidPasswordFile
    };

    assertFalse(DsFrameworkCliMain.mainCLI(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests a list-groups over SSL using blind trust.
   */
  @Test()
  public void testListGroupsSSLBlindTrust()
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-w", "password",
      "-Z",
      "-X"
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests a list-groups over SSL using a trust store.
   */
  @Test()
  public void testListGroupsSSLTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-w", "password",
      "-Z",
      "-P", trustStorePath
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests a list-groups using StartTLS with blind trust.
   */
  @Test()
  public void testListGroupsStartTLSBlindTrust()
  {
    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "-q",
      "-X"
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, null, System.err),
        SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests a list-groups using StartTLS with a trust store.
   */
  @Test()
  public void testListGroupsStartTLSTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "list-groups",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "-q",
      "-P", trustStorePath
    };

    assertEquals(DsFrameworkCliMain.mainCLI(args, false, null, System.err),
        SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests the dsservice with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(DsFrameworkCliMain.mainCLI(args, false, null, null),
        SUCCESSFUL.getReturnCode());

    args = new String[] { "-H" };
    assertEquals(DsFrameworkCliMain.mainCLI(args, false, null, null),
        SUCCESSFUL.getReturnCode());

    args = new String[] { "-?" };
    assertEquals(DsFrameworkCliMain.mainCLI(args, false, null, null),
        SUCCESSFUL.getReturnCode());
  }
}

