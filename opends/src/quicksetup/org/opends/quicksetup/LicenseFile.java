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
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * Represents information about the license file. NOTE: the license file
 * location must be kept in sync with build.xml and
 * org.opends.server.tools.upgrade.LicenseFile.
 */
public class LicenseFile
{

  /**
   * The license file name in Legal directory.
   */
  private final static String LICENSE_FILE_NAME = "license_to_accept.txt";

  /**
   * The Legal folder which contains license file.
   */
  private final static String LEGAL_FOLDER_NAME = "Legal";

  /**
   * The accepted license file name.
   */
  private final static String ACCEPTED_LICENSE_FILE_NAME = "licenseAccepted";

  /**
   * Get the directory in which legal files are stored.
   */
  private static String getInstanceLegalDirectory()
  {
    String instanceLegalDirName;
    String installDirName = System.getProperty("INSTALL_ROOT");

    if (installDirName == null)
    {
      installDirName = System.getenv("INSTALL_ROOT");
    }

    if (installDirName == null)
    {
      installDirName = ".";
    }

    String instanceDirname =
        Utils.getInstancePathFromInstallPath(installDirName);
    instanceLegalDirName = instanceDirname + File.separator + LEGAL_FOLDER_NAME;
    File instanceLegalDir = new File(instanceLegalDirName);
    if (!instanceLegalDir.exists())
    {
      instanceLegalDir.mkdir();
    }
    return instanceLegalDirName;
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
    return getInstanceLegalDirectory() + File.separatorChar
        + LICENSE_FILE_NAME;
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
   * Returns the URL to the license file when using jnlp / java web start.
   */
  static private URL getWebStartLicenseFile()
  {
    final String licenseResource =
        LEGAL_FOLDER_NAME + File.separatorChar + LICENSE_FILE_NAME;
    return Thread.currentThread().getContextClassLoader().getResource(
        licenseResource);
  }

  /**
   * Checks if the license file exists.
   *
   * @return <CODE>true</CODE> if the license file exists in the Legal directory
   *         in the top level installation directory <CODE>false</CODE>
   *         otherwise.
   */
  static public boolean exists()
  {
    if (Utils.isWebStart())
    {
      return (getWebStartLicenseFile() != null);
    }
    else
    {
      return getFile().exists();
    }
  }

  /**
   * Get the textual contents of the license file.
   *
   * @return the textual contents of the license file.
   */
  static public String getText()
  {
    InputStream input = null;
    // Gets the inputstream of the license
    // From a file as the usual way,
    // from an URL if we use web start / jnlp.
    if (!Utils.isWebStart())
    {
      try
      {
        input = new FileInputStream(getFile());
      }
      catch (FileNotFoundException e)
      {
        // Should not happen
        return "";
      }
    }
    else
    {
      URL licenseURL = getWebStartLicenseFile();
      if (licenseURL != null)
      {
        try
        {
          input = licenseURL.openStream();
        }
        catch (Exception e)
        {
          // Should not happen
          return "";
        }
      }
    }
    // Reads the inputstream content.
    final StringBuilder sb = new StringBuilder();
    if (input != null)
    {
      try
      {
        final BufferedReader br =
            new BufferedReader(new InputStreamReader(input));
        String read = br.readLine();

        while (read != null)
        {
          sb.append(read);
          sb.append(ServerConstants.EOL);
          read = br.readLine();
        }
      }
      catch (IOException ioe)
      {
        // Should not happen
        return "";
      }
    }
    StaticUtils.close(input);

    return sb.toString();
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
   * Creates a file - in the legal folder from the specified directory; which
   * indicates that the license has been approved.
   *
   * @param installationPath
   *          The server installation's path.
   */
  static public void createFileLicenseApproved(final String installationPath)
  {
    if (getApproval() && installationPath != null)
    {
      try
      {
        new File(installationPath + File.separatorChar + LEGAL_FOLDER_NAME
            + File.separatorChar + ACCEPTED_LICENSE_FILE_NAME).createNewFile();
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
  static public boolean isAlreadyApproved()
  {
    final File f =
        new File(getInstanceLegalDirectory() + File.separatorChar
            + ACCEPTED_LICENSE_FILE_NAME);
    return f.exists();
  }

}
