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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012 Delta-Victor Consultants
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.build.tools;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import static org.opends.build.tools.Utilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;

/**
 * Generates an RPM spec file.
 */
public class GenerateRpm extends Task
{

  private File topDir;
  private String topDirAbsolutePath;
  private String sourceDirName;
  private File destFile;
  private String prefix;
  private String productName;
  private String referenceURL;
  private String shortName;
  private String version;
  private String release;
  private boolean overwrite;
  private StringBuilder sb;

  private final String filePrefix = "%{_prefix}";
  private final String dirPrefix = "%dir %{_prefix}";

  /**
   * Sets the top directory for the rpm build.
   *
   * @param topDir
   *          File representing top directory for rpm build directory
   */
  public void setTopDir(File topDir)
  {
    this.topDir = topDir;
    topDirAbsolutePath = topDir.getAbsolutePath();
  }

  /**
   * Sets the prefix for the RPM.
   *
   * @param prefix
   *          Used for package relocation
   */
  public void setPrefix(String prefix)
  {
    this.prefix = prefix;
  }

  /**
   * Sets the short description for the RPM.
   *
   * @param productName
   *          The RPM short description
   */
  public void setproductName(String productName)
  {
    this.productName = productName;
  }

  /**
   * Sets the application name for the RPM.
   *
   * @param shortName
   *          The RPM application name
   */
  public void setshortName(String shortName)
  {
    this.shortName = shortName;
  }

  /**
   * Sets the reference URL for the RPM.
   *
   * @param referenceURL
   *          The RPM reference URL
   */
  public void setreferenceURL(String referenceURL)
  {
    this.referenceURL = referenceURL;
  }

  /**
   * Sets the name of the source directory.
   *
   * @param sourceDirName
   *          name of the source directory.
   */
  public void setSourceDirName(String sourceDirName)
  {
    this.sourceDirName = sourceDirName;
  }

  /**
   * Sets the RPM spec file that will be generated.
   *
   * @param dest
   *          The spec file
   */
  public void setSpecFileName(File dest)
  {
    this.destFile = dest;
  }

  /**
   * Sets the version number.
   *
   * @param version
   *          The version number
   */
  public void setVersion(String version)
  {
    this.version = version;
  }

  /**
   * Sets the release number.
   *
   * @param release
   *          The release number
   */
  public void setRelease(String release)
  {
    this.release = release;
  }

  /**
   * Indicates when true that an existing destination file will be overwritten.
   *
   * @param o
   *          boolean where true means overwrite
   */
  public void setOverwrite(boolean o)
  {
    this.overwrite = o;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute() throws BuildException
  {

    try
    {
      if (!topDir.exists())
      {
        throw new BuildException("directory " + topDir.getName()
            + " does not exist");
      }
      if (!topDir.isDirectory())
      {
        throw new BuildException(topDir.getName() + " is not a directory");
      }

      if (destFile.exists())
      {
        if (this.overwrite)
        {
          destFile.delete();
          log("Regenerating " + destFile.getName() + " from "
              + topDir.getName());
        }
        else
        {
          log(destFile.getName() + " has not been regenerated");
        }
      }

      sb = new StringBuilder();
      final File rootDir = new File(sourceDirName);
      final String opendsDir = rootDir.getName();
      final File[] listFiles = rootDir.listFiles(new PkgFileFilter());

      // Generate the package information
      sb.append("%define _topdir " + topDirAbsolutePath + EOL);
      sb.append("%define _arch noarch" + EOL);
      sb.append("%define _prefix " + prefix + EOL);
      sb.append("%define _pre " + opendsDir + EOL);
      sb.append("%define __os_install_post %{nil}" + EOL);
      sb.append(EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Header" + EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Short Description" + EOL);
      sb.append("Summary: " + productName + EOL);
      sb.append("# Application Name" + EOL);
      sb.append("Name: " + shortName + EOL);
      sb.append("# Application Version" + EOL);
      sb.append("Version: " + version + EOL);
      sb.append("# Packaging Revision" + EOL);
      // If VERSION_QUALIFIER is empty, need to remove '.' char.
      if (release.endsWith("."))
      {
        release = release.substring(0, release.length() - 1);
      }
      sb.append("Release: " + release + EOL);
      sb.append("# Software Licenced Under" + EOL);
      sb.append("License: CDDL" + EOL);
      sb.append("# RPM Group" + EOL);
      sb.append("Group: Applications/Network" + EOL);
      sb.append("# Link to Application web site" + EOL);
      sb.append("URL: " + referenceURL + EOL);
      sb.append("# Distributing Organisation" + EOL);
      sb.append("Vendor: ForgeRock AS" + EOL);
      sb.append("# Build Architecture" + EOL);
      sb.append("BuildArch: noarch" + EOL);
      sb.append(EOL);
      sb.append("Requires: jre >= 1.6" + EOL);
      sb.append(EOL);
      sb.append("# Long Description" + EOL);
      sb.append("%Description" + EOL);
      sb.append("OpenDJ LDAP Server" + EOL);
      sb.append("OpenDJ is an LDAPv3 compliant directory service, developed for the Java"
          + EOL);
      sb.append("platform, providing a high performance, highly available and secure store"
          + EOL);
      sb.append("for the identities managed by enterprises. Its easy installation process,"
          + EOL);
      sb.append("combined with the power of the Java platform makes OpenDJ one of the"
          + EOL);
      sb.append("simplest and fastest directory servers to deploy and manage."
          + EOL);
      sb.append(EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Pre & Post Install" + EOL);
      sb.append("# =========================" + EOL);
      sb.append("# If the first argument to %pre is 1, the RPM operation is an initial"
          + EOL);
      sb.append("#  installation. If the argument to %pre is 2, the operation is an upgrade"
          + EOL);
      sb.append("#  from an existing version to a new one." + EOL);
      sb.append("# Similarly, the arguments to a %post are 1 and 2 for a new installation"
          + EOL);
      sb.append("#  and upgrade, respectively. (%pre and %post aren't executed during"
          + EOL);
      sb.append("#  an uninstallation.)" + EOL);
      sb.append(EOL);
      sb.append("# Pre Install" + EOL);
      sb.append("%pre" + EOL);
      sb.append("if [ \"$1\" == \"1\" ]; then" + EOL);
      sb.append("    echo \"Pre Install - initial install\"" + EOL);
      sb.append("else if [ \"$1\" == \"2\" ] ; then" + EOL);
      sb.append("# Only if the instance has been configured" + EOL);
      sb.append("    if [ -f %{_prefix}/config/buildinfo ] && [ \"$(ls -A %{_prefix}/config/archived-configs)\" ]" + EOL);
      sb.append("    then" + EOL);
      sb.append("        echo \"Pre Install - upgrade install\"" + EOL);
      sb.append("# If the server is running before upgrade, creates a file flag" + EOL);
      sb.append("        if [ -f %{_prefix}/logs/server.pid ] " + EOL);
      sb.append("        then" + EOL);
      sb.append("            touch %{_prefix}/logs/status" + EOL);
      sb.append("        fi" + EOL);
      sb.append("        stopds=$(%{_prefix}/bin/stop-ds)" + EOL);
      sb.append("        echo $stopds" + EOL);
      sb.append("        fi" + EOL);
      sb.append("    fi" + EOL);
      sb.append("fi" + EOL);
      sb.append(EOL);
      sb.append("# Post Install" + EOL);
      sb.append("%post" + EOL);
      sb.append("if [ \"$1\" == \"1\" ] ; then" + EOL);
      sb.append("    echo \"Post Install - initial install\"" + EOL);
      sb.append("    [[ `java -version 2>&1 | /bin/sed 's/java version \"\\(.*\\)\\.\\(.*\\)\\.\\(.*\\)\\_\\(.*\\)\\.*\"/\\1\\2\\3\\4/; 1q'` < 16022 ]] && echo \"WARNING - For best server performance, use at least Java 1.6.0_22, which includes a major security fix for TLS.\""
          + EOL);
      sb.append("    echo \"\"" + EOL);
      sb.append("else if [ \"$1\" == \"2\" ] ; then" + EOL);
      sb.append("    echo \"Post Install - upgrade install\"" + EOL);
      sb.append("# Only if the instance has been configured" + EOL);
      sb.append("    if [ -f %{_prefix}/config/buildinfo ] && [ \"$(ls -A %{_prefix}/config/archived-configs)\" ]" + EOL);
      sb.append("    then" + EOL);
      // Starts the upgrade. The new files are automatically imported
      // by rpm manager, which compares files between last & actual version.
      // Copies / deletes files depending of new package.
      sb.append("        %{_prefix}/./upgrade -n --acceptLicense" + EOL);
      sb.append("# Upgrade ok " + EOL);
      sb.append("        if [ \"$?\" == \"0\" ] ; then " + EOL);
      sb.append("# Checks the server status flag for restart. " + EOL);
      sb.append("            if [ -f %{_prefix}/logs/status ] " + EOL);
      sb.append("            then" + EOL);
      sb.append("                echo \"\"" + EOL);
      sb.append("                echo \"Restarting server...\" " + EOL);
      sb.append("                %{_prefix}/./bin/start-ds " + EOL);
      sb.append("                echo \"\"" + EOL);
      sb.append("                rm -f %{_prefix}/logs/status " + EOL);
      sb.append("            fi" + EOL);
      sb.append("        fi" + EOL);
      sb.append("# Upgrade fails, needs user interaction (eg. manual mode)" + EOL);
      sb.append("        if [ \"$?\" == \"2\" ] ; then " + EOL);
      sb.append("            exit \"0\" " + EOL);
      sb.append("        fi " + EOL);
      sb.append("    else" + EOL);
      sb.append("        echo \"Instance is not configured. Upgrade aborded.\"" + EOL);
      sb.append("        exit -1" + EOL);
      sb.append("    fi" + EOL);
      sb.append("    fi " + EOL);
      sb.append("fi" + EOL);
      sb.append(EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Pre & Post Uninstall" + EOL);
      sb.append("# =========================" + EOL);
      sb.append("# If the first argument to %preun and %postun is 0, the action is"
          + EOL);
      sb.append("#  uninstallation." + EOL);
      sb.append("# If the first argument to %preun and %postun is 1, the action is an upgrade."
          + EOL);
      sb.append("# Pre Uninstall" + EOL);
      sb.append("%preun" + EOL);
      sb.append("if [ \"$1\" == \"0\" ] ; then" + EOL);
      sb.append("    echo \"Pre Uninstall - uninstall\"" + EOL);
      sb.append("# Only if the instance has been configured" + EOL);
      sb.append("    if [ -f %{_prefix}/config/buildinfo ] && [ \"$(ls -A %{_prefix}/config/archived-configs)\" ]" + EOL);
      sb.append("    then" + EOL);
      sb.append("	%{_prefix}/bin/stop-ds" + EOL);
      sb.append("    fi" + EOL);
      sb.append("else if [ \"$1\" == \"1\" ] ; then" + EOL);
      sb.append("    echo \"Pre Uninstall - upgrade uninstall\"" + EOL);
      sb.append("    fi" + EOL);
      sb.append("fi" + EOL);
      sb.append("# Post Uninstall" + EOL);
      sb.append("%postun" + EOL);
      sb.append("if [ \"$1\" == \"0\" ] ; then" + EOL);
      sb.append("    echo \"Post Uninstall - uninstall\"" + EOL);
      sb.append("    echo \"OpenDJ successfully removed.\"" + EOL);
      sb.append("else if [ \"$1\" == \"1\" ] ; then" + EOL);
      sb.append("    echo \"Post Uninstall - upgrade uninstall\"" + EOL);
      sb.append("    fi" + EOL);
      sb.append("fi" + EOL);
      sb.append(EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Prepare, Build, Install" + EOL);
      sb.append("# =========================" + EOL);
      sb.append("# %prep" + EOL);
      sb.append(EOL);
      sb.append("# %build" + EOL);
      sb.append(EOL);
      sb.append("%install" + EOL);
      sb.append("mkdir -p $RPM_BUILD_ROOT%{_prefix}" + EOL);
      sb.append("cd $RPM_BUILD_ROOT%{_prefix}" + EOL);
      for (final File f : listFiles)
      {
        sb.append("cp -r " + f.getPath() + " ." + EOL);
      }
      sb.append(EOL);
      sb.append("# =========================" + EOL);
      sb.append("# Files Layout" + EOL);
      sb.append("# =========================" + EOL);
      sb.append("%files" + EOL);
      sb.append(dirPrefix + EOL);
      // In order to handle upgrades, we need to start
      // with the contents of the rootDir rather than
      // the actual directory, because the
      // rootDir has the OpenDJ version number included
      // into it
      for (final File build : listFiles)
      {
        generatedLevel("", build);
      }

      // flush the spec file.
      PrintWriter destWriter = new PrintWriter(destFile);
      destWriter.print(sb.toString());
      destWriter.close();
    }
    catch (Exception e)
    {
      // Don't leave a malformed file laying around. Delete
      // it so it will be forced to be regenerated.
      if (destFile.exists())
      {
        destFile.deleteOnExit();
      }
      e.printStackTrace();
      throw new BuildException("Error processing " + topDir + ":  "
          + e.getMessage());
    }
  }

  private void generatedLevel(String parent, File source)
  {
    if (source.isDirectory())
    {
      sb.append(dirPrefix + parent + "/" + source.getName());
      sb.append(EOL);
      for (File child : source.listFiles())
      {
        generatedLevel(parent + "/" + source.getName(), child);
      }
    }
    else
    {
      sb.append(filePrefix + parent + "/" + source.getName());
      sb.append(EOL);
    }
  }

  /**
   * A file filter for the rpm. Excludes all '.bat', '.exe' and '.app' files.
   */
  static final class PkgFileFilter implements FileFilter
  {
    /** {@inheritDoc} */
    public boolean accept(File file)
    {
      final String fileName = file.getName().toLowerCase();
      if (file.isDirectory()
          && (fileName.equals("bat") || fileName.endsWith(".app") || fileName
              .endsWith(".bat")))
      {
        return false;
      }
      else if (file.isFile())
      {
        if (fileName.endsWith(".app") || fileName.endsWith(".bat")
            || fileName.endsWith(".exe"))
        {
          return false;
        }
      }
      return true;
    }
  }
}
