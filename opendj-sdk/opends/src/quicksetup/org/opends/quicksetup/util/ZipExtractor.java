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

package org.opends.quicksetup.util;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.ReturnCode;

import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for extracting the contents of a zip file and managing
 * the reporting of progress during extraction.
 */
public class ZipExtractor {

  static private final Logger LOG =
          Logger.getLogger(ZipExtractor.class.getName());

  /** Path separator for zip file entry names on Windows and *nix. */
  static private final char ZIP_ENTRY_NAME_SEP = '/';

  private InputStream is;
  private int minRatio;
  private int maxRatio;
  private int numberZipEntries;
  private String zipFileName;
  private Application application;

  /**
   * Creates an instance of an ZipExtractor.
   * @param zipFile File the zip file to extract
   * @throws FileNotFoundException if the specified file does not exist
   * @throws IllegalArgumentException if the zip file is not a zip file
   */
  public ZipExtractor(File zipFile)
    throws FileNotFoundException, IllegalArgumentException
  {
    this(zipFile, 0, 0, 1, null);
  }

  /**
   * Creates an instance of an ZipExtractor.
   * @param in InputStream for zip content
   * @param zipFileName name of the input zip file
   * @throws FileNotFoundException if the specified file does not exist
   * @throws IllegalArgumentException if the zip file is not a zip file
   */
  public ZipExtractor(InputStream in, String zipFileName)
    throws FileNotFoundException, IllegalArgumentException
  {
    this(in, 0, 0, 1, zipFileName, null);
  }

  /**
   * Creates an instance of an ZipExtractor.
   * @param zipFile File the zip file to extract
   * @param minRatio int indicating the max ration
   * @param maxRatio int indicating the min ration
   * @param numberZipEntries number of entries in the input stream
   * @param app application to be notified about progress
   * @throws FileNotFoundException if the specified file does not exist
   * @throws IllegalArgumentException if the zip file is not a zip file
   */
  public ZipExtractor(File zipFile, int minRatio, int maxRatio,
                                      int numberZipEntries,
                                      Application app)
    throws FileNotFoundException, IllegalArgumentException
  {
    this(new FileInputStream(zipFile),
      minRatio,
      maxRatio,
      numberZipEntries,
      zipFile.getName(),
      app);
    if (!zipFile.getName().endsWith(".zip")) {
      // TODO i18n
      throw new IllegalArgumentException("File must have extension .zip");
    }
  }

  /**
   * Creates an instance of an ZipExtractor.
   * @param is InputStream of zip file content
   * @param minRatio int indicating the max ration
   * @param maxRatio int indicating the min ration
   * @param numberZipEntries number of entries in the input stream
   * @param zipFileName name of the input zip file
   * @param app application to be notified about progress
   */
  public ZipExtractor(InputStream is, int minRatio, int maxRatio,
                                      int numberZipEntries,
                                      String zipFileName,
                                      Application app) {
    this.is = is;
    this.minRatio = minRatio;
    this.maxRatio = maxRatio;
    this.numberZipEntries = numberZipEntries;
    this.zipFileName = zipFileName;
    this.application = app;
  }

  /**
   * Performs the zip extraction.
   * @param destination File where the zip file will be extracted
   * @throws ApplicationException if something goes wrong
   */
  public void extract(File destination) throws ApplicationException {
    extract(Utils.getPath(destination));
  }

  /**
   * Performs the zip extraction.
   * @param destination File where the zip file will be extracted
   * @throws ApplicationException if something goes wrong
   */
  public void extract(String destination) throws ApplicationException {
    extract(destination, true);
  }

  /**
   * Performs the zip extraction.
   * @param destDir String representing the directory where the zip file will
   * be extracted
   * @param removeFirstPath when true removes each zip entry's initial path
   * when copied to the destination folder.  So for instance if the zip enty's
   * name was /OpenDS-0.8/some_file the file would appear in the destination
   * directory as 'some_file'.
   * @throws ApplicationException if something goes wrong
   */
  public void extract(String destDir, boolean removeFirstPath)
          throws ApplicationException
  {

    ZipInputStream zipIn = new ZipInputStream(is);
    int nEntries = 1;

    /* This map is updated in the copyZipEntry method with the permissions
     * of the files that have been copied.  Once all the files have
     * been copied to the file system we will update the file permissions of
     * these files.  This is done this way to group the number of calls to
     * Runtime.exec (which is required to update the file system permissions).
     */
    Map<String, ArrayList<String>> permissions =
        new HashMap<String, ArrayList<String>>();

    try {
      if(application != null)
         application.checkAbort();
      ZipEntry entry = zipIn.getNextEntry();
      while (entry != null) {
        if(application != null)
           application.checkAbort();
        int ratioBeforeCompleted = minRatio
                + ((nEntries - 1) * (maxRatio - minRatio) / numberZipEntries);
        int ratioWhenCompleted =
                minRatio + (nEntries * (maxRatio - minRatio) /
                        numberZipEntries);

        String name = entry.getName();
        if (name != null && removeFirstPath) {
          int sepPos = name.indexOf(ZIP_ENTRY_NAME_SEP);
          if (sepPos != -1) {
            name = name.substring(sepPos + 1);
          } else {
            LOG.log(Level.WARNING,
                    "zip entry name does not contain a path separator");
          }
        }
        if (name != null && name.length() > 0) {
          try {
            File destination = new File(destDir, name);
            copyZipEntry(entry, destination, zipIn,
                    ratioBeforeCompleted, ratioWhenCompleted, permissions);

          } catch (IOException ioe) {
            Message errorMsg =
                    Utils.getThrowableMsg(
                            INFO_ERROR_COPYING.get(entry.getName()), ioe);

            throw new ApplicationException(
                ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                    errorMsg, ioe);
          }
        }

        zipIn.closeEntry();
        entry = zipIn.getNextEntry();
        nEntries++;
      }

      if (Utils.isUnix()) {
        // Change the permissions for UNIX systems
        for (String perm : permissions.keySet()) {
          ArrayList<String> paths = permissions.get(perm);
          try {
            int result = Utils.setPermissionsUnix(paths, perm);
            if (result != 0) {
              throw new IOException("Could not set permissions on files "
                      + paths + ".  The chmod error code was: " + result);
            }
          } catch (InterruptedException ie) {
            IOException ioe =
                    new IOException("Could not set permissions on files " +
                            paths + ".  The chmod call returned an " +
                            "InterruptedException.");
            ioe.initCause(ie);
            throw ioe;
          }
        }
      }

    } catch (IOException ioe) {
      Message errorMsg =
              Utils.getThrowableMsg(
                      INFO_ERROR_ZIP_STREAM.get(zipFileName), ioe);
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          errorMsg, ioe);
    }
  }

  /**
    * Copies a zip entry in the file system.
    * @param entry the ZipEntry object.
    * @param destination File where the entry will be copied.
    * @param is the ZipInputStream that contains the contents to be copied.
    * @param ratioBeforeCompleted the progress ratio before the zip file is
    * copied.
    * @param ratioWhenCompleted the progress ratio after the zip file is
    * copied.
    * @param permissions an ArrayList with permissions whose contents will be
    * updated.
    * @throws IOException if an error occurs.
    */
  private void copyZipEntry(ZipEntry entry, File destination,
      ZipInputStream is, int ratioBeforeCompleted,
      int ratioWhenCompleted, Map<String, ArrayList<String>> permissions)
      throws IOException
  {
    if (application != null) {
      Message progressSummary =
              INFO_PROGRESS_EXTRACTING.get(Utils.getPath(destination));
      if (application.isVerbose())
      {
        application.notifyListenersWithPoints(ratioBeforeCompleted,
            progressSummary);
      }
      else
      {
        application.notifyListenersRatioChange(ratioBeforeCompleted);
      }
    }
    LOG.log(Level.INFO, "extracting " + Utils.getPath(destination));
    if (Utils.insureParentsExist(destination))
    {
      if (entry.isDirectory())
      {
        String perm = getDirectoryFileSystemPermissions(destination);
        ArrayList<String> list = permissions.get(perm);
        if (list == null)
        {
          list = new ArrayList<String>();
        }
        list.add(Utils.getPath(destination));
        permissions.put(perm, list);

        if (!Utils.createDirectory(destination))
        {
          throw new IOException("Could not create path: " + destination);
        }
      } else
      {
        String perm = Utils.getFileSystemPermissions(destination);
        ArrayList<String> list = permissions.get(perm);
        if (list == null)
        {
          list = new ArrayList<String>();
        }
        list.add(Utils.getPath(destination));
        permissions.put(perm, list);
        Utils.createFile(destination, is);
      }
    } else
    {
      throw new IOException("Could not create parent path: " + destination);
    }
    if (application != null) {
      if (application.isVerbose())
      {
        application.notifyListenersDone(ratioWhenCompleted);
      }
    }
  }

  /**
   * Returns the file system permissions for a directory.
   * @param path the directory for which we want the file permissions.
   * @return the file system permissions for the directory.
   */
  private String getDirectoryFileSystemPermissions(File path)
  {
    // TODO We should get this dynamically during build?
    return "755";
  }

}
