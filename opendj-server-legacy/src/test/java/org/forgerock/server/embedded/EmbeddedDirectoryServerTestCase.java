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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.server.embedded;

import static org.forgerock.opendj.server.embedded.ConfigParameters.configParams;
import static org.forgerock.opendj.server.embedded.ConnectionParameters.connectionParams;
import static org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer.manageEmbeddedDirectoryServer;
import static org.forgerock.opendj.server.embedded.ImportParameters.importParams;
import static org.forgerock.opendj.server.embedded.RebuildIndexParameters.rebuildIndexParams;
import static org.forgerock.opendj.server.embedded.SetupParameters.setupParams;
import static org.forgerock.opendj.server.embedded.UpgradeParameters.upgradeParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.TestCaseUtils.getServer;
import static org.testng.Assert.*;

import java.io.File;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.UtilTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for an embedded directory server.
 */
@Test(groups = "slow", sequential=true)
@SuppressWarnings("javadoc")
public class EmbeddedDirectoryServerTestCase extends UtilTestCase
{
  private static final String USER_ROOT = "userRoot";

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearBackend(USER_ROOT, "dc=example,dc=com");
    assertTrue(getServer().isRunning());
  }

  /**
   * Make sure that the server gets restarted by the {@code TestCaseUtils.restartServer} method
   * because it does a few things to the server that aren't covered in the out-of-the-box
   * configuration.
   */
  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.restartServer();
  }

  @Test
  public void testRestartServer() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    assertTrue(server.isRunning());

    DirectoryEnvironmentConfig environmentConfig = DirectoryServer.getEnvironmentConfig();
    assertNotNull(environmentConfig);

    server.restart(getClass().getName(), LocalizableMessage.raw("testRestartServer"));

    assertTrue(server.isRunning());
  }

  @Test
  public void testStopAndStartServer() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    assertTrue(server.isRunning());

    DirectoryEnvironmentConfig environmentConfig = DirectoryServer.getEnvironmentConfig();
    assertNotNull(environmentConfig);

    server.stop(getClass().getName(), LocalizableMessage.raw("testStopAndStartServer"));
    assertFalse(server.isRunning());

    server.start();
    assertTrue(server.isRunning());
  }

  @Test
  public void testReadConfigurationOffline() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping for read config test"));
    try
    {
      readConfiguration(server);
    }
    finally
    {
      server.start();
    }
  }

  private void readConfiguration(EmbeddedDirectoryServer server) throws Exception
  {
    try (ManagementContext config = server.getConfiguration())
    {
      SortedSet<DN> dns = config.getRootConfiguration().getBackend(USER_ROOT).getBaseDN();
      assertThat(dns).containsExactly(DN.valueOf("dc=example,dc=com"));
    }
  }

  @Test
  public void testReadConfigurationOnline() throws Exception
  {
    readConfiguration(getServer());
  }

  @Test
  public void testUpdateConfiguration() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    BackendConfigManager backendConfigManager = TestCaseUtils.getServerContext().getBackendConfigManager();
    assertTrue(backendConfigManager.hasLocalBackend(USER_ROOT));

    toggleBackendActivation(server, false);
    assertFalse(backendConfigManager.hasLocalBackend(USER_ROOT));

    // revert to initial configuration
    toggleBackendActivation(server, true);
    assertTrue(backendConfigManager.hasLocalBackend(USER_ROOT));
  }

  private void toggleBackendActivation(EmbeddedDirectoryServer server, final boolean enabled) throws Exception
  {
    try (ManagementContext config = server.getConfiguration())
    {
      BackendCfgClient backend = config.getRootConfiguration().getBackend(USER_ROOT);
      backend.setEnabled(enabled);
      backend.commit();
    }
  }

  /**
   * This test only ensures that the rebuild index process is correctly launched and does not fail.
   * There is no assertions.
   */
  @Test
  public void testRebuildIndexOffline() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping for rebuild index test"));
    try
    {
      server.rebuildIndex(rebuildIndexParams().baseDN("dc=example,dc=com"));
    }
    finally
    {
      server.start();
    }
  }

  /** Rebuild index is not available online. */
  @Test(expectedExceptions = EmbeddedDirectoryServerException.class)
  public void testRebuildIndexOnline() throws Exception
  {
    getServer().rebuildIndex(rebuildIndexParams().baseDN("dc=example,dc=com"));
  }

  /**
   * This test only ensures that the upgrade index is correctly launched and does not fail.
   * There is no assertions.
   */
  @Test
  public void testUpgradeOffline() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping for upgrade test"));
    try
    {
      server.upgrade(upgradeParams().isIgnoreErrors(false));
    }
    finally
    {
      server.start();
    }
  }

  /**
   * This test only ensures that the upgrade index is correctly launched and does not fail.
   * There is no assertions.
   */
  @Test
  public void testUpgradeOnline() throws Exception
  {
    getServer().upgrade(upgradeParams().isIgnoreErrors(false));
  }

  @Test
  public void testImportDataOnline() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.importLDIF(importParams()
        .backendId("userRoot")
        .ldifFile(TestCaseUtils.getTestResource("test-import-file.ldif").getPath()));
  }

  /** Import data is not implemented for offline use in EmbeddedDirectoryServer.*/
  @Test(expectedExceptions= EmbeddedDirectoryServerException.class)
  public void testImportDataOffline() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping for import data test"));
    try
    {
      server.importLDIF(importParams()
          .backendId("userRoot")
          .ldifFile(TestCaseUtils.getTestResource("test-import-file.ldif").getPath()));
    }
    finally
    {
      server.start();
    }
  }

  @Test
  public void testSetupFromArchive() throws Exception
  {
    EmbeddedDirectoryServer server = getServer();
    server.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping for setup from archive test"));
    try
    {
      File rootDir = TestCaseUtils.getUnitTestRootPath().toPath().resolve("embedded-setup").resolve("opendj").toFile();
      // ensure the test starts from a clean directory
      StaticUtils.recursiveDelete(rootDir);

      final int[] ports = TestCaseUtils.findFreePorts(3);
      EmbeddedDirectoryServer tempServer = manageEmbeddedDirectoryServer(
        configParams()
          .serverRootDirectory(rootDir.getPath())
          .configurationFile(rootDir.toPath().resolve("config").resolve("config.ldif").toString()),
        connectionParams()
          .bindDn("cn=Directory Manager")
          .bindPassword("password")
          .hostName("localhost")
          .ldapPort(ports[0])
          .adminPort(ports[1]),
         System.out,
         System.err);

      tempServer.extractArchiveForSetup(TestCaseUtils.getOpenDJArchivePath());
      tempServer.setup(
          setupParams()
            .backendType("pdb")
            .baseDn("dc=example,dc=com")
            .jmxPort(ports[2]));
      tempServer.start();
      tempServer.stop(getClass().getSimpleName(), LocalizableMessage.raw("stopping temp server for setup test"));
    }
    finally
    {
      server.start();
    }
  }
}