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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;

/**
 * Represents information about the license file. NOTE: the license file
 * location must be kept in sync with build.xml and
 * org.opends.server.tools.upgrade.LicenseFile.
 */
public class LicenseFile
{
  private static final String INSTALL_ROOT_SYSTEM_PROPERTY = "INSTALL_ROOT";
  /** The license file name in Legal directory. */
  private static final String LICENSE_FILE_NAME = "Forgerock_License.txt";
  /** The Legal folder which contains license file. */
  private static final String LEGAL_FOLDER_NAME = "legal-notices";
  /** The accepted license file name. */
  private static final String ACCEPTED_LICENSE_FILE_NAME = "licenseAccepted";

  /** Get the directory in which legal files are stored. */
  private static String getInstallDirectory() {
    String installDirName = System.getProperty(INSTALL_ROOT_SYSTEM_PROPERTY);
    if (installDirName == null)
    {
      installDirName = System.getenv(INSTALL_ROOT_SYSTEM_PROPERTY);
    }
    if (installDirName == null)
    {
      installDirName = ".";
    }
    return installDirName;
  }

  /** Get the directory in which approved legal files are stored. */
  private static String getInstanceLegalDirectory()
  {
    String instanceLegalDirName = Utils.getInstancePathFromInstallPath(getInstallDirectory())
        + File.separator + LEGAL_FOLDER_NAME;
    File instanceLegalDir = new File(instanceLegalDirName);
    if (!instanceLegalDir.exists())
    {
      instanceLegalDir.mkdir();
    }
    return instanceLegalDirName;
  }

  /** The File object related to the license file. */
  private static File licenceFile;
  /** The license file approval state. */
  private static boolean approved;
  /** Returns the license file name. */
  private static String getName()
  {
    return getInstallDirectory() + File.separator + LEGAL_FOLDER_NAME + File.separator + LICENSE_FILE_NAME;
  }

  /** Returns the license file object. */
  private static File getFile()
  {
    if (licenceFile == null)
    {
      licenceFile = new File(getName());
    }
    return licenceFile;
  }

  /**
   * Checks if the license file exists.
   *
   * @return <CODE>true</CODE> if the license file exists in the Legal directory
   *         in the top level installation directory <CODE>false</CODE>
   *         otherwise.
   */
  public static boolean exists()
  {
    return getFile().exists();
  }

  /**
   * Get the textual contents of the license file.
   *
   * @return the textual contents of the license file.
   */
  public static String getText()
  {
    // Reads the inputstream content.
    try (InputStream input = new FileInputStream(getFile());
        BufferedReader br = new BufferedReader(new InputStreamReader(input)))
    {
      final StringBuilder sb = new StringBuilder();
      String read;
      while ((read = br.readLine()) != null)
      {
        sb.append(read);
        sb.append(ServerConstants.EOL);
      }
      return sb.toString();
    }
    catch (IOException ioe)
    {
      // Should not happen
      return "";
    }
  }

  /**
   * Get the license approval status.
   *
   * @return <CODE>true</CODE> if the license has been accepted by the user
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean getApproval()
  {
    return approved;
  }

  /**
   * Sets the license approval status.
   *
   * @param approved
   *          the license approval status
   */
  public static void setApproval(boolean approved)
  {
    LicenseFile.approved = approved;
  }

  /**
   * Creates a file - in the legal folder from the specified directory; which
   * indicates that the license has been approved.
   *
   * @param installationPath
   *          The server installation's path.
   */
  public static void createFileLicenseApproved(final String installationPath)
  {
    if (getApproval() && installationPath != null)
    {
      String instanceDirname = Utils.getInstancePathFromInstallPath(installationPath);
      String instanceLegalDirName = instanceDirname + File.separator + LEGAL_FOLDER_NAME;
      File instanceLegalDir = new File(instanceLegalDirName);

      try
      {
        if (!instanceLegalDir.exists())
        {
          instanceLegalDir.mkdir();
        }
        new File(instanceLegalDir, ACCEPTED_LICENSE_FILE_NAME).createNewFile();
      }
      catch (IOException e)
      {
        // do nothing
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
    return new File(getInstanceLegalDirectory(), ACCEPTED_LICENSE_FILE_NAME).exists();
  }
}
