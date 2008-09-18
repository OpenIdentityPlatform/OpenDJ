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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.testng.annotations.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;

/**
 * Installation Tester.
 */
@Test(groups = {"slow"}, sequential=true)
public class InstallationTest extends QuickSetupTestCase {

  Installation installation;

  @BeforeClass
  public void setUp() throws Exception {
    installation = TestUtilities.getInstallation();
  }

  /**
   * Tests to make sure installation is valid.
   */
  @Test(enabled = false)
  public void testValidateRootDirectory() {
    Installation.validateRootDirectory(TestUtilities.getQuickSetupTestServerRootDir());
  }

  /**
   * Tests that installation root directory is available.
   */
  @Test(enabled = false)
  public void testGetRootDirectory() {
    assertNotNull(installation.getRootDirectory());
  }

  /**
   * Tests that the installation root directory can be set.
   */
  @Test(enabled = false)
  public void testSetRootDirectory() {
    File root = installation.getRootDirectory();
    installation.setRootDirectory(root);
  }

  /**
   * Tests that the installation root is valid.
   */
  @Test(enabled = false)
  public void testIsValid() {
    assertTrue(installation.isValid(installation.getRootDirectory()));
    assertTrue(installation.isValid(installation.getInstanceDirectory()));
  }

  /**
   * Tests that an installation directory missing required directories
   * is considered invalid.
   */
  @Test(enabled = false)
  public void testIsValid2() {
    assertTrue(installation.isValid(installation.getRootDirectory()));
    assertTrue(installation.isValid(installation.getInstanceDirectory()));
    File x = new File(installation.getRootDirectory(), "x");
    for (String reqDirName : Installation.REQUIRED_DIRECTORIES) {
      File reqDir = new File(installation.getRootDirectory(), reqDirName);
      try {
        assertTrue(reqDir.renameTo(x));
        assertFalse(installation.isValid(installation.getRootDirectory()));
        assertFalse(installation.isValid(installation.getInstanceDirectory()));
        assertNotNull(installation.getInvalidityReason());
      } finally {
        x.renameTo(reqDir);
      }
    }
  }

  /**
   * Tests the configuration is available.
   */
  @Test(enabled = false)
  public void testGetCurrentConfiguration() {
    assertNotNull(installation.getCurrentConfiguration());
  }

  /**
   * Tests the base configuration is available.
   */
  @Test(enabled = false)
  public void testGetBaseConfiguration() throws ApplicationException {
    assertNotNull(installation.getBaseConfiguration());
  }

  /**
   * Tests the status is available.
   */
  @Test(enabled = false)
  public void testGetStatus() {
    assertNotNull(installation.getStatus());
  }

  /**
   * Tests the lib directory is available.
   */
  @Test(enabled = false)
  public void testGetLibrariesDirectory() {
    assertExistentFile(installation.getLibrariesDirectory());
  }

  /**
   * Tests the schema concat file is available.
   */
  @Test(enabled = false)
  public void testGetSchemaConcatFile() {
    assertNonexistentFile(installation.getSchemaConcatFile());
  }

  /**
   * Tests the base schema file is available.
   */
  @Test(enabled = false)
  public void testGetBaseSchemaFile() throws ApplicationException {
    assertExistentFile(installation.getBaseSchemaFile());
  }

  /**
   * Tests the base config file is available.
   */
  @Test(enabled = false)
  public void testGetBaseConfigurationFile() throws ApplicationException {
    assertExistentFile(installation.getBaseConfigurationFile());
  }

  /**
   * Tests the SVN rev number is discernable.
   */
  @Test(enabled = false)
  public void testGetSvnRev() throws ApplicationException {
    assertNotNull(installation.getSvnRev());
  }

  /**
   * Tests the config file is available.
   */
  @Test(enabled = false)
  public void testGetCurrentConfigurationFile() {
    assertExistentFile(installation.getCurrentConfigurationFile());
  }

  /**
   * Tests the bin/bat directory is available and platform appropriate.
   */
  @Test(enabled = false)
  public void testGetBinariesDirectory() {
    File binariesDir;
    assertExistentFile(binariesDir = installation.getBinariesDirectory());
    if (File.separator.equals("\\")) {
      assertTrue(binariesDir.getName().endsWith(
              Installation.WINDOWS_BINARIES_PATH_RELATIVE));
    } else {
      assertTrue(binariesDir.getName().endsWith(
              Installation.UNIX_BINARIES_PATH_RELATIVE));
    }
  }

  /**
   * Tests the db directory is available.
   */
  @Test(enabled = false)
  public void testGetDatabasesDirectory() {
    assertExistentFile(installation.getDatabasesDirectory());
  }

  /**
   * Tests the backup directory is available.
   */
  @Test(enabled = false)
  public void testGetBackupDirectory() {
    assertExistentFile(installation.getBackupDirectory());
  }

  /**
   * Tests the config directory is available.
   */
  @Test(enabled = false)
  public void testGetConfigurationDirectory() {
    assertExistentFile(installation.getConfigurationDirectory());
  }

  /**
   * Tests the logs directory is available.
   */
  @Test(enabled = false)
  public void testGetLogsDirectory() {
    assertExistentFile(installation.getLogsDirectory());
  }

  /**
   * Tests the locks directory is available.
   */
  @Test(enabled = false)
  public void testGetLocksDirectory() {
    assertExistentFile(installation.getLocksDirectory());
  }

  /**
   * Tests the tmp directory is available.
   */
  @Test(enabled = false)
  public void testGetTemporaryDirectory() {
    assertNonexistentFile(installation.getTemporaryDirectory());
  }

  /**
   * Tests the history directory is available.
   */
  @Test(enabled = false)
  public void testGetHistoryDirectory() {
    assertNonexistentFile(installation.getHistoryDirectory());
  }

  /**
   * Tests a historical backup directory can be created.
   */
  @Test(enabled = false)
  public void testCreateHistoryBackupDirectory() throws IOException {
    assertExistentFile(installation.createHistoryBackupDirectory());
    assertExistentFile(installation.getHistoryDirectory());
    assertTrue(installation.getHistoryDirectory().exists());
  }

  /**
   * Tests the history log file is available.
   */
  @Test(enabled = false)
  public void testGetHistoryLogFile() {
    assertNonexistentFile(installation.getHistoryLogFile());
  }

  /**
   * Tests the config upgrade directory is available.
   */
  @Test(enabled = false)
  public void testGetConfigurationUpgradeDirectory() {
    assertExistentFile(installation.getConfigurationUpgradeDirectory());
  }

  /**
   * Tests the tmp/upgrade directory is available.
   */
  @Test(enabled = false)
  public void testGetTemporaryUpgradeDirectory() {
    assertNonexistentFile(installation.getTemporaryUpgradeDirectory());
  }

  /**
   * Tests getting a command file works.
   */
  @Test(enabled = false)
  public void testGetCommandFile() {
    assertExistentFile(installation.getCommandFile(
            Installation.UNIX_START_FILE_NAME));
  }

  /**
   * Tests the start server command is available.
   */
  @Test(enabled = false)
  public void testGetServerStartCommandFile() {
    assertExistentFile(installation.getServerStartCommandFile());
  }

  /**
   * Tests the stop server command is available.
   */
  @Test(enabled = false)
  public void testGetServerStopCommandFile() {
    assertExistentFile(installation.getServerStopCommandFile());
  }

  /**
   * Tests the ldif directory is available.
   */
  @Test(enabled = false)
  public void testGetLdifDirectory() {
    assertExistentFile(installation.getLdifDirectory());
  }

  /**
   * Tests the quicksetup jar is available.
   */
  @Test(enabled = false)
  public void testGetQuicksetupJarFile() {
    assertExistentFile(installation.getQuicksetupJarFile());
  }

  /**
   * Tests the OpenDS jar is available.
   */
  @Test(enabled = false)
  public void testGetOpenDSJarFile() {
    assertExistentFile(installation.getOpenDSJarFile());
  }

  /**
   * Tests the uninstall file is available.
   */
  @Test(enabled = false)
  public void testGetUninstallBatFile() {
    assertExistentFile(installation.getUninstallBatFile());
  }

  /**
   * Tests the status panel command file is available.
   */
  @Test(enabled = false)
  public void testGetStatusPanelCommandFile() {
    assertExistentFile(installation.getStatusPanelCommandFile());
  }

  /**
   * Tests the build information is discernable.
   */
  @Test(enabled = false)
  public void testGetBuildInformation() throws ApplicationException {
    assertNotNull(installation.getBuildInformation());
  }

  /**
   * Tests the build information is discernable.
   */
  @Test(enabled = false)
  public void testGetBuildInformation1() throws ApplicationException {
    assertNotNull(installation.getBuildInformation(true));
    assertNotNull(installation.getBuildInformation(false));
  }

  /**
   * Test string representation is possible.
   */
  @Test(enabled = false)
  public void testToString() {
    assertNotNull(installation.toString());
  }

  private void assertExistentFile(File f) {
    assertNotNull(f);
    assertTrue(f.exists());
  }

  private void assertNonexistentFile(File f) {
    assertNotNull(f);
  }

}
