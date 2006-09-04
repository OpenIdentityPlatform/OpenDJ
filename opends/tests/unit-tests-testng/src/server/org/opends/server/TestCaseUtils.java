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
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.loggers.Error;
import org.opends.server.loggers.Debug;

/**
 * This class defines some utility functions which can be used by test
 * cases.
 */
public final class TestCaseUtils {
  /**
   * The name of the system property that can be used to set the fully-qualified
   * name of the Java class to use as the config handler.
   */
  public static final String PROPERTY_CONFIG_CLASS =
       "org.opends.server.ConfigClass";

  /**
   * The name of the system property that can be used to set the path to the
   * Directory Server configuration file.
   */
  public static final String PROPERTY_CONFIG_FILE =
       "org.opends.server.ConfigFile";

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
   * @throws  InitializationException  If a problem occurs while starting the
   *                                   server.
   *
   * @throws  ConfigException  If there is a problem with the server
   *                           configuration.
   */
  public static void startServer()
         throws InitializationException, ConfigException
  {
    if (serverStarted)
    {
      return;
    }

    String configClass = System.getProperty(PROPERTY_CONFIG_CLASS);
    String configFile  = System.getProperty(PROPERTY_CONFIG_FILE);

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
        dst.mkdir();
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
   * Prevent instantiation.
   */
  private TestCaseUtils() {
    // No implementation.
  }
}
