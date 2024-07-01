/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsconfig;

import static com.forgerock.opendj.cli.ReturnCode.*;

import static org.testng.Assert.*;

import org.forgerock.opendj.config.dsconfig.DSConfig;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A set of test cases for the dsconfig tool.
 */
@SuppressWarnings("javadoc")
public class DsconfigOptionsTestCase extends DirectoryServerTestCase {

  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   */
  @BeforeClass
  public void before() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    TestCaseUtils.dsconfig(
        "delete-connection-handler",
        "--handler-name", "HTTP Connection Handler",
        "-f");
  }

  @Test
  public void testSetEnableHTTPConnectionHandler() {

    final String[] args =
    {
      "set-connection-handler-prop",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "--trustAll",
      "--bindDN","cn=Directory Manager",
      "--bindPassword" , "password",
      "--no-prompt",
      "--handler-name", "HTTP Connection Handler",
    };
    assertEquals(dsconfigMain(args), SUCCESS.get());
  }

  @Test
  public void testSetSASLHandler() {
    final String[] args =
    {
      "set-sasl-mechanism-handler-prop",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "--trustAll",
      "--bindDN","cn=Directory Manager",
      "--bindPassword" , "password",
      "--no-prompt",
      "--handler-name", "DIGEST-MD5",
      "--set", "server-fqdn:" + "127.0.0.1"
    };
    assertEquals(dsconfigMain(args), SUCCESS.get());

    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--reset", "server-fqdn",
            "--reset", "quality-of-protection");
  }


  @Test
  public void testSetMaxAllowedClientConnections() {
    final String[] args =
    {
      "set-global-configuration-prop",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "--trustAll",
      "--bindDN","cn=Directory Manager",
      "--bindPassword" , "password",
      "--no-prompt",
      "--set", "max-allowed-client-connections:32768"
    };
    assertEquals(dsconfigMain(args), SUCCESS.get());
  }

  @Test
  public void testSetReturnBindPassword() throws Exception
  {
    final String[] args =
    {
      "set-global-configuration-prop",
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "--trustAll",
      "--bindDN","cn=Directory Manager",
      "--bindPassword" , "password",
      "--no-prompt",
      "--set", "return-bind-error-messages:true"
    };
    assertEquals(dsconfigMain(args), SUCCESS.get());
  }


  /**
   * Tests that multiple "--set" option cannot be used with a single valued property.
   */
  @Test
  public void testMultipleSetSingleValuedProperty() throws Exception
  {
    final String[] args =
    {
          "set-global-configuration-prop",
          "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
          "--trustAll",
          "--bindDN","cn=Directory Manager",
          "--bindPassword" , "password",
          "--no-prompt",
          "--set", "idle-time-limit:10000ms",
          "--set", "idle-time-limit:1000ms"
    };
    assertTrue(dsconfigMain(args) != SUCCESS.get());
  }

  /**
   * Tests that multiple "--set" option are allowed to be used with a multivalued property (see
   * OPENDJ-255).
   */
  @Test
  public void testMultipleSetMultiValuedProperty() throws Exception
  {
    final String[] args =
    {
          "set-connection-handler-prop",
          "--handler-name", "LDAP Connection Handler",
          "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
          "--trustAll",
          "--bindDN","cn=Directory Manager",
          "--bindPassword" , "password",
          "--no-prompt",
          "--set", "denied-client:1.1.1.1",
          "--set", "denied-client:2.2.2.2"
    };
    assertEquals(dsconfigMain(args), SUCCESS.get());
  }

  @Test
  public void testGenerateDoc() throws Exception
  {
    System.setProperty("org.forgerock.opendj.gendoc", "true");
    System.setProperty("com.forgerock.opendj.ldap.tools.scriptName", "dsconfig");
    final String[] args = {
      "--no-prompt",
      "-?",
    };
    try
    {
      assertEquals(dsconfigMain(args), SUCCESS.get());
    }
    finally
    {
      System.clearProperty("org.forgerock.opendj.gendoc");
    }
  }

  private int dsconfigMain(String[] args)
  {
    return DSConfig.main(args, System.out, System.err);
  }

}
