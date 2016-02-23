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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.util;



import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryEnvironmentConfig;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the EmbeddedUtils class.
 */
public class EmbeddedUtilsTestCase
       extends UtilTestCase
{
  /**
   * Ensures that the Directory Server is running before running any tests.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();
    assertTrue(EmbeddedUtils.isRunning());
  }



  /**
   * Make sure that the server gets restarted by the
   * {@code TestCaseUtils.restartServer} method because it does a few things to
   * the server that aren't covered in the out-of-the-box configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanUp()
         throws Exception
  {
    TestCaseUtils.restartServer();
  }



  /**
   * Tests the ability to use EmbeddedUtils to restart the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testRestartServer()
         throws Exception
  {
    assertTrue(EmbeddedUtils.isRunning());

    DirectoryEnvironmentConfig environmentConfig =
         DirectoryServer.getEnvironmentConfig();
    assertNotNull(environmentConfig);

    EmbeddedUtils.restartServer(getClass().getName(),
                                LocalizableMessage.raw("testRestartServer"),
                                environmentConfig);

    assertTrue(EmbeddedUtils.isRunning());
  }



  /**
   * Tests the ability to use EmbeddedUtils to stop and then subsequently start
   * the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testStopAndStartServer()
         throws Exception
  {
    assertTrue(EmbeddedUtils.isRunning());

    DirectoryEnvironmentConfig environmentConfig =
         DirectoryServer.getEnvironmentConfig();
    assertNotNull(environmentConfig);

    EmbeddedUtils.stopServer(getClass().getName(),
                             LocalizableMessage.raw("testStopAndStartServer"));
    assertFalse(EmbeddedUtils.isRunning());

    EmbeddedUtils.startServer(environmentConfig);
    assertTrue(EmbeddedUtils.isRunning());
  }
}

