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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server;

import static org.opends.server.TestCaseUtils.copyFile;
import static org.opends.server.TestCaseUtils.createTemporaryDirectory;
import static org.opends.server.TestCaseUtils.deleteDirectory;

import java.io.File;
import java.io.IOException;

import org.opends.server.config.ConfigFileHandler;
import org.opends.server.core.DirectoryServer;

/**
 * This fixture makes sure that a directory server instance is available
 * with a configuration environment loaded from the source tree's
 * resource directory.
 * <p>
 * This fixture should be used by test cases which need a directory
 * server instance with a working configuration environment.
 */
public final class ConfigurationFixture {

  /**
   * A factory used to obtain the configuration fixture instance.
   */
  public static final FixtureFactory<ConfigurationFixture> FACTORY;

  static {
    FACTORY = new SingletonFixtureFactory<ConfigurationFixture>(
        new Factory());
  }

  // The name of the temporary instance root directory.
  private File instanceRoot;

  /**
   * Internal factory implementation.
   */
  private static final class Factory implements
      FixtureFactory<ConfigurationFixture> {

    // The configuration fixture instance.
    private ConfigurationFixture instance = null;

    /**
     * {@inheritDoc}
     */
    public ConfigurationFixture setUp() throws Exception {
      // This fixture requires the initial directory server fixture.
      InitialDirectoryServerFixture.FACTORY.setUp();

      // Create temporary config file structure.
      File tempDirectory = createTemporaryDirectory("ds7test");

      // Create the configuration directory.
      File configDirectory = new File(tempDirectory, "config");
      configDirectory.mkdir();

      // All files to be copied are taken from the resource directory.
      File configResourceDirectory = new File("resource/config");

      // Copy over the configuration file.
      File configFile = new File(configDirectory, "config.ldif");
      copyFile(new File(configResourceDirectory, "config.ldif"), configFile);

      DirectoryServer directoryServer = DirectoryServer.getInstance();
      directoryServer.initializeConfiguration(ConfigFileHandler.class
          .getName(), configFile.getAbsolutePath());

      instance = new ConfigurationFixture(tempDirectory);
      return instance;
    }

    /**
     * {@inheritDoc}
     */
    public void tearDown() throws Exception {
      // Clean up configuration directories.
      try {
        deleteDirectory(instance.getInstanceRoot());
      } catch (IOException e) {
        // Ignore errors.
      }

      instance = null;

      // Make sure resource held by the directory server fixture are
      // released.
      InitialDirectoryServerFixture.FACTORY.tearDown();
    }

  }

  /**
   * Create a configuration fixture.
   *
   * @param instanceRoot
   *          The name of the temporary instance root.
   */
  private ConfigurationFixture(File instanceRoot) {
    this.instanceRoot = instanceRoot;
  }

  /**
   * Get the temporary instance root.
   *
   * @return The temporary instance root.
   */
  public File getInstanceRoot() {
    return instanceRoot;
  }
}
