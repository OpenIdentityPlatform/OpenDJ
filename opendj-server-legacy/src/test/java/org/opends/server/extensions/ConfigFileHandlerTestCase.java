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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.extensions;



import java.io.File;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.DN;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the config file handler.
 */
public class ConfigFileHandlerTestCase
       extends ExtensionsTestCase
{
  /**
   * Makes sure that the server is running before performing any tests.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String buildDir = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
            buildRoot + File.separator + "build");
    String startOKFile = buildDir + File.separator +
                         "unit-tests" + File.separator + "package-instance" +
                         File.separator + "config" + File.separator +
                         "config.ldif.startok";

    assertTrue(new File(startOKFile).exists(),
               startOKFile + " does not exist but it should");
    assertFalse(new File(startOKFile + ".tmp").exists(),
                startOKFile + ".tmp exists but should not");
    assertFalse(new File(startOKFile + ".old").exists(),
                startOKFile + ".old exists but should not");
  }



  /**
   * Tests to verify that attempts to change the structural object class of a
   * config entry will be rejected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testChangingStructuralClass()
         throws Exception
  {
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: cn=config",
      "changetype: modify",
      "replace: objectClass",
      "objectClass: top",
      "objectClass: device",
      "objectClass: extensibleObject"
    );

    assertFalse(resultCode == 0);
  }



  /**
   * Tests to ensure that none of the configuration entries are using the
   * extensibleObject object class.
   */
  @Test
  public void testNoExtensibleObjects()
         throws Exception
  {
    ArrayList<DN> violatingDNs = new ArrayList<>();
    recursivelyTestNoExtensibleObjects(
         DirectoryServer.getConfigHandler().getConfigRootEntry(), violatingDNs);

    if (! violatingDNs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("The extensibleObject object class is not allowed for use in the server configuration.")
             .append(EOL);
      message.append("Configuration entries containing the extensibleObject object class:").append(EOL);
      for (DN dn : violatingDNs)
      {
        message.append("- ").append(dn).append(EOL);
      }

      throw new AssertionError(message.toString());
    }
  }



  /**
   * Tests that the provided configuration entry does not contain the
   * extensibleObject object class, and neither do any of its subordinate
   * entries.
   *
   * @param  configEntry   The configuration entry to be checked.
   * @param  violatingDNs  A list to which the DN of any entry containing the
   *                       extensibleObject class should be added.
   */
  private void recursivelyTestNoExtensibleObjects(ConfigEntry configEntry,
                                                  ArrayList<DN> violatingDNs)
  {
    if (configEntry.hasObjectClass("extensibleObject"))
    {
      violatingDNs.add(configEntry.getDN());
    }

    for (ConfigEntry ce : configEntry.getChildren().values())
    {
      recursivelyTestNoExtensibleObjects(ce, violatingDNs);
    }
  }
}

