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
 *      Portions Copyright 2015 ForgeRock AS.
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
@Test(groups = {"slow"})
public class ConfigurationTest extends QuickSetupTestCase {

  private Configuration config;

  @BeforeClass
  public void setUp() throws Exception {
    config = TestUtilities.getInstallation().getCurrentConfiguration();
  }

  @Test(enabled = false)
  public void testGetDirectoryManagerDns() throws IOException {
    Set<String> dns = config.getDirectoryManagerDns();
    assertFalse(dns.isEmpty());
  }

  @Test(enabled = false)
  public void testGetPort() throws IOException {
    assertEquals(TestUtilities.ldapPort, (Integer) config.getPort());
  }

  @Test(enabled = false)
  public void testGetLogPaths() throws IOException {
    // TODO: something more useful
    config.getLogPaths();
  }

  @Test(enabled = false)
  public void testHasBeenModified() throws IOException {
    assertTrue(config.hasBeenModified());
  }

  @Test(enabled = false)
  public void testGetOutsideLogs() throws IOException {
    // TODO: something more useful
    config.getOutsideLogs();
  }

  @Test(enabled = false)
  public void testGetOutsideDbs() throws IOException {
    // TODO: something more useful
    config.getOutsideDbs();
  }

  @Test(enabled = false)
  public void testGetContents() throws IOException {
    assertNotNull(config.getContents());
  }

  @Test(enabled = false)
  public void testGetDatabasePaths() throws IOException {
    assertFalse(config.getDatabasePaths().isEmpty());
  }

  @Test(enabled = false)
  public void testLoad() {
    //TODO:  need way to verify reload
  }
}
