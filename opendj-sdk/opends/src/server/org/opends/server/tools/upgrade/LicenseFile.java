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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.opends.server.tools.upgrade;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.opends.server.util.StaticUtils;

/**
 * Represents information about the license file.
 *
 * NOTE: the license file location must be kept in sync with build.xml and
 * org.opends.quicksetup.LicenseFile.
 */
public class LicenseFile
{
  /**
   * Get the directory in which legal files are stored.
   */
  private static String getLegalDirectory()
  {
    String installRootFromSystem = System.getProperty("INSTALL_ROOT");

    if (installRootFromSystem == null)
    {
      installRootFromSystem = System.getenv("INSTALL_ROOT");
    }

    if (installRootFromSystem == null)
    {
      installRootFromSystem = "";
    }

    return installRootFromSystem + File.separatorChar + "Legal";
  }

  /**
   * Get the directory in which legal files are stored.
   */
  private static String getInstanceLegalDirectory()
  {
    String installDirName = System.getProperty("INSTALL_ROOT");

    if (installDirName == null)
    {
      installDirName = System.getenv("INSTALL_ROOT");
    }

    if (installDirName == null)
    {
      installDirName = ".";
    }

    final String instanceDirname =
        UpgradeUtils.getInstancePathFromInstallPath(installDirName);
    StringBuilder instanceLegalDirName =
        new StringBuilder(instanceDirname).append(File.separator).append(
            "Legal");
    final File instanceLegalDir = new File(instanceLegalDirName.toString());
    if (!instanceLegalDir.exists())
    {
      instanceLegalDir.mkdir();
    }

    return instanceLegalDirName.toString();
  }

  /**
   * The File object related to the license file.
   */
  static private File licFile = null;

  /**
   * The license file approval state.
   */
  static private boolean approved = false;

  /**
   * Returns the license file name.
   */
  static private String getName()
  {
    return new StringBuilder(getLegalDirectory()).append(File.separatorChar)
        .append("license_to_accept.txt").toString();
  }

  /**
   * Returns the license file object.
   */
  static private File getFile()
  {
    if (licFile == null)
    {
      licFile = new File(getName());
    }

    return licFile;
  }

  /**
   * Checks if the license file exists.
   *
   * @return <CODE>true</CODE> a license file license_to_accept.txt exists in
   *         the Legal directory in the top level installation directory
   *         <CODE>false</CODE> otherwise.
   */
  static public boolean exists()
  {
    return getFile().exists();
  }

  /**
   * Get the textual contents of the license file.
   *
   * @return the textual contents of the license file.
   */
  static public String getText()
  {
    FileReader reader;

    try
    {
      reader = new FileReader(getFile());
    }
    catch (Exception e)
    {
      return "";
    }

    int fileLen = (int) getFile().length();

    char[] charArray = new char[fileLen];

    try
    {
      reader.read(charArray);
    }
    catch (IOException ioe)
    {
      System.out.println("Could not read license file");
    }
    finally
    {
      StaticUtils.close(reader);
    }

    return new String(charArray);
  }

  /**
   * Get the license approval status.
   *
   * @return <CODE>true</CODE> if the license has been accepted by the user
   *         <CODE>false</CODE> otherwise.
   */
  static public boolean getApproval()
  {
    return approved;
  }

  /**
   * Sets the license approval status.
   *
   * @param p_approved
   *          the license approval status
   */
  static public void setApproval(boolean p_approved)
  {
    approved = p_approved;
  }

  /**
   * Create a file which indicates that the license has been approved.
   */
  static public void createFileLicenseApproved()
  {
    if (getApproval())
    {
      try
      {
        new File(getInstanceLegalDirectory() + File.separatorChar
            + "licenseAccepted").createNewFile();
      }
      catch (IOException e)
      {
      }
    }
  }

  /**
   * Indicate if the license had already been approved..
   *
   * @return <CODE>true</CODE> if the license had already been approved by the
   *         user <CODE>false</CODE> otherwise.
   */
  static public boolean isAlreadyApproved()
  {
    File f =
        new File(getInstanceLegalDirectory() + File.separatorChar
            + "licenseAccepted");
    return f.exists();
  }

}
