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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigFileHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.loggers.Error;
import org.opends.server.loggers.Debug;

import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines some utility functions which can be used by test
 * cases.
 */
public final class TestCaseUtils {
  /**
   * The name of the system property that specifies the server build root.
   */
  public static final String PROPERTY_BUILD_ROOT =
       "org.opends.server.BuildRoot";

  /**
   * Indicates whether the server has already been started.
   */
  private static boolean serverStarted = false;

  /**
   * Starts the Directory Server so that it will be available for use while
   * running the unit tests.  This will only actually start the server once, so
   * subsequent attempts to start it will be ignored because it will already be
   * available.
   *
   * @throws  IOException  If a problem occurs while interacting with the
   *                       filesystem to prepare the test package root.
   *
   * @throws  InitializationException  If a problem occurs while starting the
   *                                   server.
   *
   * @throws  ConfigException  If there is a problem with the server
   *                           configuration.
   */
  public static void startServer()
         throws IOException, InitializationException, ConfigException
  {
    if (serverStarted)
    {
      return;
    }

    // Get the build root and use it to create a test package directory.
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   testRoot  = new File(buildRoot + File.separator + "build" +
                                File.separator + "unit-tests" + File.separator +
                                "package");
    File   testSrcRoot = new File(buildRoot + File.separator + "tests" +
                                  File.separator + "unit-tests-testng");

    if (testRoot.exists())
    {
      deleteDirectory(testRoot);
    }
    testRoot.mkdirs();

    String[] subDirectories = { "bak", "changelogDb", "classes", "config", "db",
                                "ldif", "lib", "locks", "logs" };
    for (String s : subDirectories)
    {
      new File(testRoot, s).mkdir();
    }


    // Copy the configuration, schema, and MakeLDIF resources into the
    // appropriate place under the test package.
    File resourceDir   = new File(buildRoot, "resource");
    File testResourceDir = new File(testSrcRoot, "resource");
    File testConfigDir = new File(testRoot, "config");

    copyDirectory(new File(resourceDir, "config"), testConfigDir);
    copyDirectory(new File(resourceDir, "schema"),
                  new File(testConfigDir, "schema"));
    copyDirectory(new File(resourceDir, "MakeLDIF"),
                  new File(testConfigDir, "MakeLDIF"));
    copyFile     (new File(testResourceDir, "config-changes.ldif"),
                  new File(testConfigDir, "config-changes.ldif"));


    // Actually start the server and set a variable that will prevent us from
    // needing to do it again.
    System.setProperty(PROPERTY_SERVER_ROOT, testRoot.getAbsolutePath());
    System.setProperty(PROPERTY_FORCE_DAEMON_THREADS, "true");

    String configClass = ConfigFileHandler.class.getName();
    String configFile  = testConfigDir.getAbsolutePath() + File.separator +
                         "config.ldif";

    DirectoryServer directoryServer = DirectoryServer.getInstance();
    directoryServer.bootstrapServer();
    directoryServer.initializeConfiguration(configClass, configFile);
    Error.removeAllErrorLoggers(false);
    Debug.removeAllDebugLoggers(false);
    directoryServer.startServer();
    serverStarted = true;
  }

  /**
   * Create a temporary directory with the specified prefix.
   *
   * @param prefix
   *          The directory prefix.
   * @return The temporary directory.
   * @throws IOException
   *           If the temporary directory could not be created.
   */
  public static File createTemporaryDirectory(String prefix)
      throws IOException {
    File tempDirectory = File.createTempFile(prefix, null);

    if (!tempDirectory.delete()) {
      throw new IOException("Unable to delete temporary file: "
          + tempDirectory);
    }

    if (!tempDirectory.mkdir()) {
      throw new IOException("Unable to create temporary directory: "
          + tempDirectory);
    }

    return tempDirectory;
  }

  /**
   * Copy a directory and its contents.
   *
   * @param src
   *          The name of the directory to copy.
   * @param dst
   *          The name of the destination directory.
   * @throws IOException
   *           If the directory could not be copied.
   */
  public static void copyDirectory(File src, File dst) throws IOException {
    if (src.isDirectory()) {
      // Create the destination directory if it does not exist.
      if (!dst.exists()) {
        dst.mkdirs();
      }

      // Recursively copy sub-directories and files.
      for (String child : src.list()) {
        copyDirectory(new File(src, child), new File(dst, child));
      }
    } else {
      copyFile(src, dst);
    }
  }

  /**
   * Delete a directory and its contents.
   *
   * @param dir
   *          The name of the directory to delete.
   * @throws IOException
   *           If the directory could not be deleted.
   */
  public static void deleteDirectory(File dir) throws IOException {
    if (dir.isDirectory()) {
      // Recursively delete sub-directories and files.
      for (String child : dir.list()) {
        deleteDirectory(new File(dir, child));
      }
    }

    dir.delete();
  }

  /**
   * Copy a file.
   *
   * @param src
   *          The name of the source file.
   * @param dst
   *          The name of the destination file.
   * @throws IOException
   *           If the file could not be copied.
   */
  public static void copyFile(File src, File dst) throws IOException {
    InputStream in = new FileInputStream(src);
    OutputStream out = new FileOutputStream(dst);

    // Transfer bytes from in to out
    byte[] buf = new byte[8192];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }
    in.close();
    out.close();
  }

  /**
   * Get the LDAP port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static long getServerLdapPort()
  {
    return 32389;
  }

  /**
   * Get the JMX port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static long getServerJmxPort()
  {
    return 33689;
  }

  /**
   * Method for getting a file from the test resources directory.
   *
   * @return The directory as a File
   */
  public static File getTestResource(String filename)
  {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   testResourceDir = new File(buildRoot + File.separator + "tests" +
                                      File.separator + "unit-tests-testng" +
                                      File.separator + "resource");

    return new File(testResourceDir, filename);
  }

  /**
   * Prevent instantiation.
   */
  private TestCaseUtils() {
    // No implementation.
  }
}
