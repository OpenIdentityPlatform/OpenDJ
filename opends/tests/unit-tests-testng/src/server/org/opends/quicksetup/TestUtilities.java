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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.OperatingSystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
public class TestUtilities {
    
  /**
   * The name of the system property that specifies the server build root.
   */
  public static final String PROPERTY_BUILD_ROOT =
          "org.opends.server.BuildRoot";

  public static final String DIRECTORY_MANAGER_PASSWORD = "password";

  public static Integer ldapPort;

  public static Integer jmxPort;

  private static boolean initialized;

  static public void initServer()
          throws IOException, ApplicationException, InterruptedException {
    File qsServerRoot = getQuickSetupTestServerRootDir();
    if (!initialized) {
      if (qsServerRoot.exists()) {
        stopServer();
        new FileManager().deleteRecursively(qsServerRoot);
      }
      ZipExtractor extractor = new ZipExtractor(getInstallPackageFile());
      extractor.extract(qsServerRoot);
      setupServer();
      initialized = true;
    }
  }

  static public Installation getInstallation() {
    return new Installation(getQuickSetupTestServerRootDir());
  }

  static private void setupServer() throws IOException, InterruptedException {
    ServerSocket ldapSocket = TestCaseUtils.bindFreePort();
    ldapPort = ldapSocket.getLocalPort();
    ldapSocket.close();

    ServerSocket jmxSocket = TestCaseUtils.bindFreePort();
    jmxPort = jmxSocket.getLocalPort();
    jmxSocket.close();

    List<String> args = new ArrayList<String>();
    File root = getQuickSetupTestServerRootDir();
    if (OperatingSystem.isUNIXBased(
            OperatingSystem.forName(System.getProperty("os.name")))) {
      args.add(new File(root, "setup").getPath());
    } else {
      args.add(new File(root, "setup.bat").getPath());
    }
    args.add("-n");
    args.add("-p");
    args.add(Integer.toString(ldapPort));
    args.add("-x");
    args.add(Integer.toString(jmxPort));
    args.add("-w");
    args.add(DIRECTORY_MANAGER_PASSWORD);
    args.add("-O");

    ProcessBuilder pb = new ProcessBuilder(args);
    Process p = pb.start();
    if (p.waitFor() != 0) {
      throw new IllegalStateException("setup server failed");
    }
  }

  static public void stopServer() throws ApplicationException {
    ServerController controller = new ServerController(getInstallation());
    controller.stopServer();
  }

  static public File getInstallPackageFile() throws FileNotFoundException {
    File installPackageFile = null;
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   buildDir  = new File(buildRoot, "build");
    File   packageDir = new File(buildDir, "package");
    if (!packageDir.exists()) {
      throw new FileNotFoundException("Package directory " + packageDir +
              " does not exist");
    }
    String[] files = packageDir.list();
    if (files != null) {
      for (String fileName : files) {
        if (fileName.endsWith(".zip")) {
          installPackageFile = new File(packageDir, fileName);
          break;
        }
      }
    } else {
      throw new FileNotFoundException("No files in " + packageDir);
    }
    return installPackageFile;
  }

  static public File getQuickSetupTestWorkspace() {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   buildDir  = new File(buildRoot, "build");
    File   unitRootDir  = new File(buildDir, "unit-tests");
    return new File(unitRootDir, "quicksetup");
  }

  static public File getQuickSetupTestServerRootDir() {
    return new File(getQuickSetupTestWorkspace(), "OpenDS");
  }
}
