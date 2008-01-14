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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.build.tools;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import static org.opends.build.tools.Utilities.*;

import java.io.File;
import java.io.PrintWriter;

/**
 * Generates an RPM spec file.
 */
public class GenerateRpm extends Task {

  private File topDir;
  private String topDirAbsolutePath;
  private String sourceDirName;
  private File destFile;
  private String prefix;
  private String version;
  private String release;
  private boolean overwrite;
  private StringBuilder sb;

  private final String filePrefix="%{_prefix}";
  private final String dirPrefix="%dir %{_prefix}";


  /**
   * Sets the top directory for the rpm build.
   * @param topDir File representing top directory for rpm build directory
   */
  public void setTopDir(File topDir) {
    this.topDir = topDir;
    topDirAbsolutePath = topDir.getAbsolutePath();
  }

  /**
   * Sets the prefix for the RPM.
   * @param prefix Used for package relocation
   */
  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  /**
   * Sets the name of the source directory.
   * @param sourceDirName name of the source directory.
   */
  public void setSourceDirName(String sourceDirName) {
    this.sourceDirName = sourceDirName;
  }


  /**
   * Sets the RPM spec file that will be generated.
   * @param dest The spec file
   *
   */
  public void setSpecFileName(File dest) {
    this.destFile = dest;
  }

  /**
   * Sets the version number.
   * @param version The version number
   *
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Sets the release number.
   * @param release The release number
   *
   */
  public void setRelease(String release) {
    this.release = release;
  }

  /**
   * Indicates when true that an existing destination
   * file will be overwritten.
   * @param o boolean where true means overwrite
   */
  public void setOverwrite(boolean o) {
    this.overwrite = o;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute() throws BuildException {

    try {
      if (!topDir.exists()) {
        throw new BuildException("directory " + topDir.getName()
            + " does not exist");
      }
      if (!topDir.isDirectory()) {
        throw new BuildException(topDir.getName() + " is not a directory");
      }

      if (destFile.exists()) {
        if (this.overwrite) {
          destFile.delete();
          log("Regenerating " + destFile.getName() + " from "
              + topDir.getName());
        } else {
          log(destFile.getName() + " has not been regenerated");
        }
      }

      sb = new StringBuilder();
      File rootDir = new File(sourceDirName);
      String opendsDir = rootDir.getName();

      // Generate the package information
      sb.append("Summary            : OpenDS Directory Server"           + EOL);
      sb.append("Name               : opends"                            + EOL);
      sb.append("Version            : " + version                        + EOL);
      sb.append("Release            : " + release                        + EOL);
      sb.append("License            : CDDL"                              + EOL);
      sb.append("Group              : Applications/Network"              + EOL);
      sb.append("URL                : https://opends.org"                + EOL);
      sb.append(                                                           EOL);
      sb.append("BuildArchitectures : noarch"                            + EOL);
      sb.append("BuildRoot          : " + topDirAbsolutePath + "/SOURCES"+ EOL);
      sb.append("Prefix             : " + prefix                         + EOL);
      sb.append(                                                           EOL);
      sb.append("%define _prefix " + prefix                              + EOL);
      sb.append(                                                           EOL);
      sb.append("%Description"                                           + EOL);
      sb.append("OpenDS Directory Server"                                + EOL);
      sb.append(                                                           EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("# pre/post installation"                                + EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("# The order is:"                                        + EOL);
      sb.append("#     1. %pre new"                                      + EOL);
      sb.append("#     2. install new"                                   + EOL);
      sb.append("#     3. %post new"                                     + EOL);
      sb.append("#     4. %preun old"                                    + EOL);
      sb.append("#     5. delete old"                                    + EOL);
      sb.append("#     6. %postun old"                                   + EOL);
      sb.append("# Note: \"$1 equals \"1\" it means \"fresh install"     + EOL);
      sb.append(                                                           EOL);
      sb.append("# PRE INSTALL"                                          + EOL);
      sb.append("%pre"                                                   + EOL);
      sb.append("if [ \"$1\" != \"1\" ]; then"                           + EOL);
      sb.append("echo \"  This version of the OpenDS RPM does not work"  + EOL);
      sb.append("echo \"  with the standard RPM upgrade mechanism"       + EOL);
      sb.append("echo \"  (rpm -U or rpm -F)."                           + EOL);
      sb.append("echo \"  To perform an upgrade, use the OpenDS upgrade" + EOL);
      sb.append("echo \"  tool included in the package delivery."        + EOL);
      sb.append("echo \"  For more information about the upgrade process"+ EOL);
      sb.append("echo \"  with RPM see https://www.opends.org/wiki//page/OpendsRPM."+ EOL);
      sb.append("exit 1"                                                 + EOL);
      sb.append("fi"                                                     + EOL);
      sb.append(                                                           EOL);
      sb.append("# POST INSTALL"                                         + EOL);
      sb.append("%post"                                                  + EOL);
      sb.append(                                                           EOL);
      sb.append("# PRE UNINSTALL"                                        + EOL);
      sb.append("%preun"                                                 + EOL);
      sb.append("${RPM_INSTALL_PREFIX}/OpenDS-1.0.0/bin/stop-ds"         + EOL);
      sb.append(                                                           EOL);
      sb.append("# POST UNINSTALL"                                       + EOL);
      sb.append("%postun"                                                + EOL);
      sb.append("rm -rf ${RPM_INSTALL_PREFIX}/" + opendsDir              + EOL);
      sb.append("rmdir --ignore-fail-on-non-empty ${RPM_INSTALL_PREFIX}" + EOL);
      sb.append(                                                           EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("# Prepare, Build, Install"                              + EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("%prep"                                                  + EOL);
      sb.append("cd "+ topDirAbsolutePath +"/SOURCES" + prefix           +
          " ; cp -r " + sourceDirName + " ."                             + EOL);
      sb.append("%build"                                                 + EOL);
      sb.append("%install"                                               + EOL);
      sb.append(                                                           EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("# FILES LAYOUT"                                         + EOL);
      sb.append("# ========================="                            + EOL);
      sb.append("%files"                                                 + EOL);
      sb.append(dirPrefix                                                + EOL);
      generatedLevel("", rootDir);

      // flush the spec file.
      PrintWriter destWriter = new PrintWriter(destFile);
      destWriter.print(sb.toString());
      destWriter.close();
    } catch (Exception e) {
      // Don't leave a malformed file laying around. Delete
      // it so it will be forced to be regenerated.
      if (destFile.exists()) {
        destFile.deleteOnExit();
      }
      e.printStackTrace();
      throw new BuildException("Error processing " + topDir + ":  "
          + e.getMessage());
    }
  }

  private void generatedLevel (String parent, File source )
  {
    if (source.isDirectory())
    {
      sb.append(dirPrefix + parent + "/" +source.getName());
      sb.append(EOL);
      for (File child : source.listFiles())
      {
        generatedLevel( parent + "/" +source.getName(), child);
      }
    }
    else
    {
      sb.append(filePrefix + parent + "/" +source.getName());
      sb.append(EOL);
    }
  }
}
