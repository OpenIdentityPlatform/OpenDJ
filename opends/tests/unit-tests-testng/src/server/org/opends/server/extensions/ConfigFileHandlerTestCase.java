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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;

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
  @BeforeClass()
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();
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
    int resultCode = TestCaseUtils.applyModifications(
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
    ArrayList<DN> violatingDNs = new ArrayList<DN>();
    recursivelyTestNoExtensibleObjects(
         DirectoryServer.getConfigHandler().getConfigRootEntry(), violatingDNs);

    if (! violatingDNs.isEmpty())
    {
      StringBuilder message = new StringBuilder();
      message.append("The extensibleObject object class is not allowed for " +
                     "use in the server configuration.");
      message.append(EOL);
      message.append("Configuration entries containing the extensibleObject " +
                     "object class:");
      message.append(EOL);
      for (DN dn : violatingDNs)
      {
        message.append("- ");
        message.append(dn.toString());
        message.append(EOL);
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

