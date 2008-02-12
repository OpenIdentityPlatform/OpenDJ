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
package org.opends.server.tools.dsconfig;



import java.io.File;
import java.io.FileWriter;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.DirectoryServer;

import static org.testng.Assert.*;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;



/**
 * A set of test cases for the dsservice tool.
 */
public class DsconfigLdapConnectionTestCase extends DirectoryServerTestCase {
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
  }

  /**
   * Ensures ADS is removed.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void afterClass()
         throws Exception
  {
  }

  /**
   * Tests list-list-connection-handlers with a malformed bind DN.
   */
  @Test()
  public void testMalformedBindDN()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "malformed",
      "-w", "password"
    };

    assertFalse(DSConfig.main(args, false, null, null)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-connection-handlers with a nonexistent bind DN.
   */
  @Test()
  public void testNonExistentBindDN()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Does Not Exist",
      "-w", "password"
    };

    assertFalse(DSConfig.main(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-connection-handlers with an invalid password.
   */
  @Test()
  public void testInvalidBindPassword()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "wrongPassword"
    };

    assertFalse(DSConfig.main(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }




  /**
   * Tests list-connection-handlers with a valid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
    };

    assertEquals(DSConfig.main(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests list-connection-handlers with an invalid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInvalidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-j",invalidPasswordFile
    };

    assertFalse(DSConfig.main(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests a list-connection-handlerss over SSL using blind trust.
   */
  @Test()
  public void testListConnectionHandlersSSLBlindTrust()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-w", "password",
      "-Z",
      "-X"
    };

    assertEquals(DSConfig.main(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests list-connection-handlers over SSL using a trust store.
   */
  @Test()
  public void testListConnectionHandlersSSLTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapsPort()),
      "-w", "password",
      "-Z",
      "-P", trustStorePath
    };

    assertEquals(DSConfig.main(args, false, System.out,
        System.err), SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests a list-connection-handlers using StartTLS with blind trust.
   */
  @Test()
  public void testListConnectionHandlersStartTLSBlindTrust()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "-q",
      "-X"
    };

    assertEquals(DSConfig.main(args, false, null, System.err),
        SUCCESSFUL.getReturnCode());
  }



  /**
   * Tests a list-connection-handlers using StartTLS with a trust store.
   */
  @Test()
  public void testListConnectionHandlersStartTLSTrustStore()
  {
    String trustStorePath = DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-w", "password",
      "-q",
      "-P", trustStorePath
    };

    assertEquals(DSConfig.main(args, false, null, System.err),
        SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests the dsconfig with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args = {"--noPropertiesFile","--help" };
    assertEquals(DSConfig.main(args, false, null, null),
        SUCCESSFUL.getReturnCode());

    args = new String[] { "--noPropertiesFile", "-H" };
    assertEquals(DSConfig.main(args, false, null, null),
        SUCCESSFUL.getReturnCode());

    args = new String[] { "--noPropertiesFile", "-?" };
    assertEquals(DSConfig.main(args, false, null, null),
        SUCCESSFUL.getReturnCode());
  }
}

