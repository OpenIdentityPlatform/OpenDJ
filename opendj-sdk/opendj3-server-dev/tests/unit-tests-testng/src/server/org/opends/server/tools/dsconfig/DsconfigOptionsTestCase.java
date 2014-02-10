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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.tools.JavaPropertiesTool.ErrorReturnCode.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the dsconfig tool.
 */
@SuppressWarnings("javadoc")
public class DsconfigOptionsTestCase extends DirectoryServerTestCase {

  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   */
  @BeforeClass()
  public void before() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * Ensures ADS is removed.
   */
  @AfterClass()
  public void afterClass() throws Exception
  {
  }

  /**
   * Tests that multiple  "--set" option cannot be used with a singlevalued
   * property
   */
  @Test()
  public void testMultipleSetSingleValuedProperty() throws Exception
  {
    String[] args =
    {
          "set-global-configuration-prop",
          "-p",String.valueOf(TestCaseUtils.getServerAdminPort()),
          "--trustAll",
          "--bindDN","cn=Directory Manager",
          "--bindPassword" , "password",
          "--no-prompt",
          "--set", "idle-time-limit:10000ms",
          "--set", "idle-time-limit:1000ms"
    };
    assertTrue(dsconfigMain(args) != SUCCESSFUL.getReturnCode());
  }

  /**
   * Tests that multiple  "--set" option are allowed to be used with a multivalued
   * property (see OPENDJ-255)
   */
  @Test()
  public void testMultipleSetMultiValuedProperty() throws Exception
  {
    String[] args =
    {
          "set-connection-handler-prop",
          "--handler-name", "LDAP Connection Handler",
          "-p",String.valueOf(TestCaseUtils.getServerAdminPort()),
          "--trustAll",
          "--bindDN","cn=Directory Manager",
          "--bindPassword" , "password",
          "--no-prompt",
          "--set", "denied-client:1.1.1.1",
          "--set", "denied-client:2.2.2.2"
    };
    assertEquals(dsconfigMain(args), SUCCESSFUL.getReturnCode());
  }

  @Test
  public void testGenerateDoc() throws Exception
  {
    System.setProperty("org.forgerock.opendj.gendoc", "true");
    String[] args = {
      "--no-prompt",
      "-?",
    };
    try
    {
      assertEquals(dsconfigMain(args), 1);
    }
    finally
    {
      System.clearProperty("org.forgerock.opendj.gendoc");
    }
  }

  private int dsconfigMain(String[] args)
  {
    return DSConfig.main(args, false, System.out, System.err);
  }

}
