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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opends.quicksetup.util.FileManager;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import com.forgerock.opendj.util.OperatingSystem;

@SuppressWarnings("javadoc")
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

  public static void initServer()
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

  public static Installation getInstallation() {
    return new Installation(getQuickSetupTestServerRootDir(),getQuickSetupTestServerRootDir());
  }

  private static void setupServer() throws IOException, InterruptedException {
    int[] ports = TestCaseUtils.findFreePorts(2);
    ldapPort = ports[0];
    jmxPort = ports[1];

    List<String> args = new ArrayList<>();
    File root = getQuickSetupTestServerRootDir();
    String filename = OperatingSystem.isUnixBased() ? "setup" : "setup.bat";
    args.add(new File(root, filename).getPath());
    args.add("--cli");
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
      ByteStringBuilder stdOut = new ByteStringBuilder();
      ByteStringBuilder stdErr = new ByteStringBuilder();
      while(stdOut.appendBytes(p.getInputStream(), 512) > 0) {}
      while(stdErr.appendBytes(p.getErrorStream(), 512) > 0) {}
      throw new IllegalStateException(
          "setup server process failed:\n" +
          "exit value: " + p.exitValue() + "\n" +
          "stdout contents: " + stdOut + "\n" +
          "stderr contents: " + stdErr);
    }
  }

  public static void stopServer() throws ApplicationException {
    ServerController controller = new ServerController(getInstallation());
    controller.stopServer();
  }
//-Dorg.opends.server.ServerRoot=/Users/vharseko/git/OpenIdentityPlatform/OpenAM/OpenDJ/opendj-server-legacy/target/unit-tests/package-instance -Dorg.opends.quicksetup.Root=/Users/vharseko/git/OpenIdentityPlatform/OpenAM/OpenDJ/opendj-server-legacy/target/package/opendj
  public static File getInstallPackageFile() throws FileNotFoundException {
    File installPackageFile = null;
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT,System.getProperty("user.dir"));
    File   buildDir  = new File(buildRoot, "target");
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

  public static File getQuickSetupTestWorkspace() {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   buildDir  = new File(buildRoot, "build");
    File   unitRootDir  = new File(buildDir, "unit-tests");
    return new File(unitRootDir, "quicksetup");
  }

  public static File getQuickSetupTestServerRootDir() {
    return new File(getQuickSetupTestWorkspace(), "OpenDS");
  }
}
