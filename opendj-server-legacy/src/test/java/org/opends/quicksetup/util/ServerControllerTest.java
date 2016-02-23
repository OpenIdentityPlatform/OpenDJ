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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import static org.testng.Assert.*;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupTestCase;
import org.opends.quicksetup.Status;
import org.opends.quicksetup.TestUtilities;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * ServerController Tester.
 */
@SuppressWarnings("javadoc")
@Test(groups = {"slow"})
public class ServerControllerTest extends QuickSetupTestCase {

  private ServerController controller;
  private Status status;

  @BeforeClass
  public void setUp() throws Exception {
    Installation installation = TestUtilities.getInstallation();
    controller = new ServerController(installation);
    status = installation.getStatus();
  }

  /**
   * Tests ability to stop the server.
   * @throws ApplicationException
   */
  @Test(enabled = false)
  public void testStopServer() throws ApplicationException {
    if (!status.isServerRunning()) {
      controller.startServer();
    }
    assertTrue(status.isServerRunning());
    controller.stopServer();
    assertFalse(status.isServerRunning());
  }

  /**
   * Tests ability to start the server.
   * @throws ApplicationException
   */
  @Test(enabled = false)
  public void testStartServer() throws ApplicationException {
    if (status.isServerRunning()) {
      controller.stopServer();
    }
    assertFalse(status.isServerRunning());
    controller.startServer();
    assertTrue(status.isServerRunning());
  }
}
