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
 *      Copyright 2011 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;



import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import static org.testng.Assert.*;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;



/**
 * A set of test cases for the dsservice tool.
 */
public class DsconfigOptionsTestCase extends DirectoryServerTestCase {


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
   * Tests that multiple  "--set" option cannot be used with a singlevalued
   * property
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMultipleSetSingleValuedProperty()
         throws Exception
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

     assertFalse(DSConfig.main(args, false, System.out, System.err)
        == SUCCESSFUL.getReturnCode());


  }

  /**
   * Tests that multiple  "--set" option are allowed to be used with a multivalued
   * property (see OPENDJ-255)
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMultipleSetMultiValuedProperty()
         throws Exception
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

     assertEquals(DSConfig.main(args, false, System.out, System.err),
        SUCCESSFUL.getReturnCode());


  }



}

