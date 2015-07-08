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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;

import java.io.File;
import java.io.FileWriter;

import org.forgerock.opendj.config.dsconfig.DSConfig;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.forgerock.opendj.cli.ReturnCode.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the DS service tool.
 */
public class DsconfigLdapConnectionTestCase extends DirectoryServerTestCase {
  /** The path to a file containing an invalid bind password. */
  private String invalidPasswordFile;

  /** The path to a file containing a valid bind password. */
  private String validPasswordFile;



  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
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
  @AfterClass
  public void afterClass()
         throws Exception
  {
  }

  /**
   * Tests list-list-connection-handlers with a malformed bind DN.
   */
  @Test
  public void testMalformedBindDN()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "malformed",
      "-w", "password",
      "-X"
    };

    assertFalse(DSConfig.main(args, System.out, System.err) == SUCCESS.get());
  }

  /**
   * Tests list-connection-handlers with a nonexistent bind DN.
   */
  @Test
  public void testNonExistentBindDN()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Does Not Exist",
      "-w", "password",
      "-X"
    };

    assertFalse(DSConfig.main(args, System.out, System.err) == SUCCESS.get());
  }
  
  /**
   *  --bindPassword and the --bindPasswordFile arguments can not be provided 
   *  together.
   */
  @Test
  public void testConflictualArgumentsPasswordAndFilePassword()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-j", validPasswordFile,
      "-X"
    };

    assertEquals(DSConfig.main(args, System.out, System.err), CONFLICTING_ARGS.get());
  }

  /** Quiet mode and verbose arguments can not be provided together. */
  @Test
  public void testConflictualArgumentsQuietAndVerbose()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-v",
      "-X"
    };

    assertEquals(DSConfig.main(args, System.out, System.err), CONFLICTING_ARGS.get());
  }
  
  /**
   * Tests list-connection-handlers with an invalid password.
   */
  @Test
  public void testInvalidBindPassword()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-w", "wrongPassword",
      "-X"
    };

    assertFalse(DSConfig.main(args, System.out, System.err) == SUCCESS.get());
  }

  /**
   * Tests list-connection-handlers with an valid password.
   */
  @Test
  public void testValidBindPassword()
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-X"
    };

    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());
  }


  /**
   * Tests list-connection-handlers with a valid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testValidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-j", validPasswordFile,
      "-X"
    };

    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());
  }

  /**
   * Tests list-connection-handlers with an invalid password read from a file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testInvalidPasswordFromFile()
         throws Exception
  {
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-j",invalidPasswordFile,
      "-X"
    };

    assertFalse(DSConfig.main(args, System.out, System.err) == SUCCESS.get());
  }
  
  /**
   * Tests list-connection-handlers over SSL using a trust store.
   */
  @Test
  public void testListConnectionHandlersSSLTrustStore()
  {
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "admin-truststore";
    
    String[] args =
    {
      "-n",
      "--noPropertiesFile",
      "-Q",
      "list-connection-handlers",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-P", trustStorePath
    };

    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());
  }


  /**
   * Tests the dsconfig with the "--help" option.
   */
  @Test
  public void testHelp()
  {
    String[] args = { "--noPropertiesFile", "--help" };
    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());

    args = new String[] { "--noPropertiesFile", "-H" };
    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());

    args = new String[] { "--noPropertiesFile", "-?" };
    assertEquals(DSConfig.main(args, System.out, System.err), SUCCESS.get());
  }
}

