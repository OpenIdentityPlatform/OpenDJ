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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.io.IOException;
import java.util.Set;

/**
 * Configuration Tester.
 */
@Test(groups = {"slow"})
public class ConfigurationTest extends QuickSetupTestCase {

  Configuration config;

  @BeforeClass
  public void setUp() throws Exception {
    config = TestUtilities.getInstallation().getCurrentConfiguration();
  }

  @Test(enabled = false)
  public void testGetDirectoryManagerDns() throws IOException {
    Set<String> dns = config.getDirectoryManagerDns();
    assertTrue(dns.size() > 0);
  }

  @Test(enabled = false)
  public void testGetPort() throws IOException {
    assertTrue(TestUtilities.ldapPort.equals(config.getPort()));
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
    assertTrue(config.getDatabasePaths().size() > 0);
  }

  @Test(enabled = false)
  public void testLoad() {
    //TODO:  need way to verify reload
  }

}
