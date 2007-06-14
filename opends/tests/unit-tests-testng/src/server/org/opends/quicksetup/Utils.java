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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

/**
 *
 */
public class Utils {
    
  /**
   * The name of the system property that specifies the server build root.
   */
  public static final String PROPERTY_BUILD_ROOT =
          "org.opends.server.BuildRoot";

  static public void extractServer()
          throws FileNotFoundException, ApplicationException {
    ZipExtractor extractor = new ZipExtractor(getInstallPackageFile());
    extractor.extract(getQuickSetupTestServerRootDir());
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

  static public File getQuickSetupTestServerRootDir() {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   buildDir  = new File(buildRoot, "build");
    File   unitRootDir  = new File(buildDir, "unit-tests");
    return new File(unitRootDir, "quicksetup");
  }

}
