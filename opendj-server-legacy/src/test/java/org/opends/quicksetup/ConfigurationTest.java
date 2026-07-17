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
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.quicksetup;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.io.IOException;
import java.util.Set;

/**
 * Configuration Tester.
 */
@SuppressWarnings("javadoc")
@Test(sequential=true)
public class ConfigurationTest extends QuickSetupTestCase {

  private Configuration config;

  @BeforeClass
  public void setUp() throws Exception {
    config = TestUtilities.getInstallation().getCurrentConfiguration();
  }

  @Test
  public void testGetDirectoryManagerDns() throws IOException {
    Set<String> dns = config.getDirectoryManagerDns();
    assertFalse(dns.isEmpty());
  }

  @Test
  public void testGetPort() throws IOException {
    assertEquals(TestUtilities.ldapPort, (Integer) config.getPort());
  }

  @Test
  public void testGetLogPaths() throws IOException {
    // TODO: something more useful
    config.getLogPaths();
  }

  @Test
  public void testHasBeenModified() throws IOException {
    assertTrue(config.hasBeenModified());
  }

  @Test
  public void testGetOutsideLogs() throws IOException {
    // TODO: something more useful
    config.getOutsideLogs();
  }

  @Test
  public void testGetOutsideDbs() throws IOException {
    // TODO: something more useful
    config.getOutsideDbs();
  }

  @Test
  public void testGetContents() throws IOException {
    assertNotNull(config.getContents());
  }

  @Test
  public void testGetDatabasePaths() throws IOException {
    assertFalse(config.getDatabasePaths().isEmpty());
  }

  @Test
  public void testLoad() {
    //TODO:  need way to verify reload
  }
}
