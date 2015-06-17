/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
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
class LicenseFile
{
  private static final String INSTALL_ROOT_SYSTEM_PROPERTY = "INSTALL_ROOT";

  /**
   * The license file name in Legal directory.
   */
  private static final String LICENSE_FILE_NAME = "Forgerock_License.txt";

  /**
   * The Legal folder which contains license file.
   */
  private static final String LEGAL_FOLDER_NAME = "legal-notices";

  /** List of possible folder of the accepted license file. */
  private static final String[] ACCEPTED_LICENSE_FOLDER_NAMES = new String[] { LEGAL_FOLDER_NAME, "Legal" };

  /**
   * The accepted license file name.
   */
  private static final String ACCEPTED_LICENSE_FILE_NAME = "licenseAccepted";

  /**
   * Try to find the local instance path from system property or environment. If
   * both are null, return the provided fallback value.
   */
  private static String getInstanceRootPathFromSystem(final String fallBackValue)
  {
    final String[] possibleValues = new String[] {
      System.getProperty(INSTALL_ROOT_SYSTEM_PROPERTY), System.getenv(INSTALL_ROOT_SYSTEM_PROPERTY) };
    for (final String value : possibleValues )
    {
      if (value != null)
      {
        return value;
      }
    }
    return fallBackValue;
  }

  /** Get the directory in which legal files are stored. */
  private static String getLegalDirectory()
  {
    return getInstanceRootPathFromSystem("") + File.separatorChar + LEGAL_FOLDER_NAME;
  }

  /**
   * Get the directory in which legal files are stored.
   */
  private static String getInstanceLegalDirectory()
  {
    final String instanceDirname = UpgradeUtils.getInstancePathFromInstallPath(getInstanceRootPathFromSystem("."));
    final String instanceLegalDirName = instanceDirname + File.separator + LEGAL_FOLDER_NAME;
    final File instanceLegalDir = new File(instanceLegalDirName);
    if (!instanceLegalDir.exists())
    {
      instanceLegalDir.mkdir();
    }
    return instanceLegalDirName;
  }

  /**
   * The File object related to the license file.
   */
  private static File licFile;

  /**
   * The license file approval state.
   */
  private static boolean approved;

  /**
   * Returns the license file name.
   */
  private static String getName()
  {
    return getLegalDirectory() + File.separatorChar + LICENSE_FILE_NAME;
  }

  /**
   * Returns the license file object.
   */
  private static File getFile()
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
   * @return <CODE>true</CODE> a license file exists in
   *         the Legal directory in the top level installation directory
   *         <CODE>false</CODE> otherwise.
   */
  static boolean exists()
  {
    return getFile().exists();
  }

  /**
   * Get the textual contents of the license file.
   *
   * @return the textual contents of the license file.
   */
  static String getText()
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
  static boolean getApproval()
  {
    return approved;
  }

  /**
   * Sets the license approval status.
   *
   * @param p_approved
   *          the license approval status
   */
  static void setApproval(boolean p_approved)
  {
    approved = p_approved;
  }

  /**
   * Create a file which indicates that the license has been approved.
   */
  static void createFileLicenseApproved()
  {
    if (getApproval())
    {
      try
      {
        new File(getInstanceLegalDirectory() + File.separatorChar
            + ACCEPTED_LICENSE_FILE_NAME).createNewFile();
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
  public static boolean isAlreadyApproved()
  {
    for (final String folderName : ACCEPTED_LICENSE_FOLDER_NAMES)
    {
      final File f = new File(getInstanceRootPathFromSystem(".") + File.separatorChar + folderName
          + File.separatorChar + ACCEPTED_LICENSE_FILE_NAME);
      if (f.exists())
      {
        return true;
      }
    }
    return false;
  }

}
