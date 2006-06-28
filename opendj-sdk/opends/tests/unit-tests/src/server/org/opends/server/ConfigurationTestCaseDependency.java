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
import static org.opends.server.TestCaseUtils.deleteDirectory;

import java.io.File;
import java.io.IOException;

import org.opends.server.config.ConfigFileHandler;
import org.opends.server.core.DirectoryServer;

/**
 * This dependency makes sure that a directory server instance is
 * available with a configuration environment loaded from the source
 * tree's resource directory.
 * <p>
 * This dependency should be used by test cases which need a directory
 * server instance with a working configuration environment.
 * <p>
 * The dependency requires the
 * {@link org.opends.server.InitialDirectoryServerTestCaseDependency}.
 *
 * @author Matthew Swift
 */
public final class ConfigurationTestCaseDependency extends
    TestCaseDependency {

  // Flag used to prevent multiple initialization.
  private boolean isInitialized = false;

  // The name of the temporary directory used to contain the
  // configuration.
  private File tempDirectory = null;

  // The initial directory server dependency (required by this
  // dependency).
  private InitialDirectoryServerTestCaseDependency dependency;

  /**
   * Create a dependency which will make sure that the configuration
   * file located in the source tree's resource directory is loaded.
   *
   * @param dependency
   *          The initial directory server dependency which this
   *          dependency requires.
   */
  public ConfigurationTestCaseDependency(
      InitialDirectoryServerTestCaseDependency dependency) {
    this.dependency = dependency;
  }

  /**
   * {@inheritDoc}
   */
  public void setUp() throws Exception {
    if (isInitialized == false) {
      // Make sure that the initial server is available.
      dependency.setUp();

      // Create temporary config file structure.
      File tempDirectory = getTempDirectory();

      // Create the configuration directory.
      File configDirectory = new File(tempDirectory, "config");
      configDirectory.mkdir();

      // All files to be copied are taken from the resource directory.
      File resourceDirectory = new File("resource");
      File configResourceDirectory = new File("resource/config");

      // Copy over the configuration file.
      File configFile = new File(configDirectory, "config.ldif");
      copyFile(new File(configResourceDirectory, "config.ldif"), configFile);

      // Configuration is dependent on JMX to register an alert generator.
      DirectoryServer.initializeJMX();

      DirectoryServer directoryServer = DirectoryServer.getInstance();
      directoryServer.initializeConfiguration(ConfigFileHandler.class
          .getName(), configFile.getAbsolutePath());

      // Prevent multiple initialization.
      isInitialized = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void tearDown() throws Exception {
    if (tempDirectory != null) {
      // Clean up configuration directories.
      try {
        deleteDirectory(tempDirectory);
      } catch (IOException e) {
        // Ignore errors.
      }

      // Reset the dependency state.
      tempDirectory = null;
    }

    isInitialized = false;
  }

  /**
   * Create a temporary directory.
   *
   * @return The temporary directory.
   * @throws IOException
   *           If the temporary directory could not be created.
   */
  public File getTempDirectory() throws IOException {
    if (tempDirectory == null) {
      tempDirectory = File.createTempFile("ds7test", null);

      if (!tempDirectory.delete()) {
        throw new IOException("Unable to delete temporary file: "
            + tempDirectory);
      }

      if (!tempDirectory.mkdir()) {
        throw new IOException("Unable to create temporary directory: "
            + tempDirectory);
      }
    }

    return tempDirectory;
  }
}
