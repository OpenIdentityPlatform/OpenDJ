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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.webstart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.opends.quicksetup.installer.InstallException;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.UserInstallData;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

/**
 * This is an implementation of the Installer class that is used to install
 * the Directory Server using Web Start.
 *
 * It just takes a UserInstallData object and based on that installs OpenDS.
 *
 *
 * This object has as parameter a WebStartDownloader object that is downloading
 * some jar files.  Until the WebStartDownloader has not finished downloading
 * the jar files will be on the InstallProgressStep.DOWNLOADING step because
 * we require all the jar files to be downloaded in order to install and
 * configure the Directory Server.
 *
 * Based on the Java properties set through the OpenDSQuickSetup.jnlp file this
 * class will retrieve the zip file containing the install, unzip it and extract
 * it in the path specified by the user and that is contained in the
 * UserInstallData object.
 *
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The notification
 * will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 * However it is the most appropriate part of the code to generate well
 * formatted messages.  So it generates HTML messages in the
 * ProgressUpdateEvent and to do so uses the UIFactory method.
 *
 * TODO pass an object in the constructor that would generate the messages.
 * The problem of this approach is that the resulting interface of this object
 * may be quite complex and could impact the lisibility of the code.
 *
 */
public class WebStartInstaller extends Installer implements JnlpProperties
{
  private HashMap<InstallProgressStep, Integer> hmRatio =
      new HashMap<InstallProgressStep, Integer>();

  private HashMap<InstallProgressStep, String> hmSummary =
      new HashMap<InstallProgressStep, String>();

  private InstallProgressStep status;

  private WebStartDownloader loader;

  /**
   * WebStartInstaller constructor.
   * @param userData the UserInstallData with the parameters provided by the
   * user.
   * @param loader the WebStartLoader that is used to download the remote jar
   * files.
   */
  public WebStartInstaller(UserInstallData userData,
      WebStartDownloader loader)
  {
    super(userData);
    this.loader = loader;
    initMaps();
    status = InstallProgressStep.NOT_STARTED;
  }

  /**
   * {@inheritDoc}
   */
  public void start()
  {
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        doInstall();
      }
    });
    t.start();
  }

  /**
   * {@inheritDoc}
   */
  protected InstallProgressStep getStatus()
  {
    return status;
  }

  /**
   * Actually performs the start in this thread.  The thread is blocked.
   *
   */
  private void doInstall()
  {
    try
    {
      PrintStream err = new ErrorPrintStream();
      PrintStream out = new OutputPrintStream();
      System.setErr(err);
      System.setOut(out);

      status = InstallProgressStep.DOWNLOADING;

      InputStream in =
          getZipInputStream(getRatio(InstallProgressStep.EXTRACTING));
      notifyListeners(UIFactory.HTML_SEPARATOR);

      status = InstallProgressStep.EXTRACTING;
      extractZipFiles(in, getRatio(InstallProgressStep.EXTRACTING),
          getRatio(InstallProgressStep.CONFIGURING_SERVER));
      notifyListeners(UIFactory.HTML_SEPARATOR);

      status = InstallProgressStep.CONFIGURING_SERVER;
      configureServer();

      switch (getUserData().getDataOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        status = InstallProgressStep.CREATING_BASE_ENTRY;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        status = InstallProgressStep.IMPORTING_LDIF;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        status = InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        importAutomaticallyGenerated();
        break;
      }

      if (getUserData().getStartServer())
      {
        notifyListeners(UIFactory.HTML_SEPARATOR);
        status = InstallProgressStep.STARTING_SERVER;
        startServer();
      }

      status = InstallProgressStep.FINISHED_SUCCESSFULLY;
      notifyListeners(null);

    } catch (InstallException ex)
    {
      if (ex.getCause() != null)
      {
        ex.getCause().printStackTrace();
      }
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      String html = getHtmlError(ex, true);
      notifyListeners(html);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected Integer getRatio(InstallProgressStep status)
  {
    return hmRatio.get(status);
  }

  /**
   * {@inheritDoc}
   */
  protected String getSummary(InstallProgressStep status)
  {
    return hmSummary.get(status);
  }

  /**
   * Initialize the different map used in this class.
   *
   */
  private void initMaps()
  {
    initSummaryMap(hmSummary);

    /*
     * hmTime contains the relative time that takes for each task to be
     * accomplished. For instance if downloading takes twice the time of
     * extracting, the value for downloading will be the double of the value for
     * extracting.
     */
    HashMap<InstallProgressStep, Integer> hmTime =
        new HashMap<InstallProgressStep, Integer>();
    hmTime.put(InstallProgressStep.DOWNLOADING, 15);
    hmTime.put(InstallProgressStep.EXTRACTING, 15);
    hmTime.put(InstallProgressStep.CONFIGURING_SERVER, 5);
    hmTime.put(InstallProgressStep.CREATING_BASE_ENTRY, 10);
    hmTime.put(InstallProgressStep.IMPORTING_LDIF, 20);
    hmTime.put(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        20);
    hmTime.put(InstallProgressStep.STARTING_SERVER, 10);

    int totalTime = 0;
    ArrayList<InstallProgressStep> steps =
        new ArrayList<InstallProgressStep>();
    totalTime += hmTime.get(InstallProgressStep.DOWNLOADING);
    steps.add(InstallProgressStep.DOWNLOADING);
    totalTime += hmTime.get(InstallProgressStep.EXTRACTING);
    steps.add(InstallProgressStep.EXTRACTING);
    totalTime += hmTime.get(InstallProgressStep.CONFIGURING_SERVER);
    steps.add(InstallProgressStep.CONFIGURING_SERVER);
    switch (getUserData().getDataOptions().getType())
    {
    case CREATE_BASE_ENTRY:
      steps.add(InstallProgressStep.CREATING_BASE_ENTRY);
      totalTime += hmTime.get(InstallProgressStep.CREATING_BASE_ENTRY);
      break;
    case IMPORT_FROM_LDIF_FILE:
      steps.add(InstallProgressStep.IMPORTING_LDIF);
      totalTime += hmTime.get(InstallProgressStep.IMPORTING_LDIF);
      break;
    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      steps.add(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
      totalTime +=hmTime.get(
              InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
      break;
    }
    if (getUserData().getStartServer())
    {
      totalTime += hmTime.get(InstallProgressStep.STARTING_SERVER);
      steps.add(InstallProgressStep.STARTING_SERVER);
    }

    int cumulatedTime = 0;
    for (InstallProgressStep s : steps)
    {
      Integer statusTime = hmTime.get(s);
      hmRatio.put(s, (100 * cumulatedTime) / totalTime);
      if (statusTime != null)
      {
        cumulatedTime += statusTime;
      }
    }

    hmRatio.put(InstallProgressStep.FINISHED_SUCCESSFULLY, 100);
    hmRatio.put(InstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  private InputStream getZipInputStream(Integer maxRatio)
      throws InstallException
  {
    notifyListeners(getHtmlWithPoints(getMsg("progress-downloading")));
    InputStream in = null;

    waitForLoader(maxRatio);

    in =
      Installer.class.getClassLoader().getResourceAsStream(getZipFileName());

    if (in == null)
    {
      // Retry once
      loader.start(true);
      waitForLoader(maxRatio);
      in =
          Installer.class.getClassLoader()
              .getResourceAsStream(getZipFileName());

      if (in == null)
      {
        throw new InstallException(InstallException.Type.DOWNLOAD_ERROR,
            getMsg("error-zipinputstreamnull"), null);
      }
    }

    notifyListeners(getHtmlDone());
    return in;
  }

  /**
   * Waits for the loader to be finished.  Every time we have an update in the
   * percentage that is downloaded we notify the listeners of this.
   *
   * @param maxRatio is the integer value that tells us which is the max ratio
   * that corresponds to the download.  It is used to calculate how the global
   * installation ratio changes when the download ratio increases.  For instance
   * if we suppose that the download takes 25 % of the total installation
   * process, then maxRatio will be 25.  When the download is complete this
   * method will send a notification to the ProgressUpdateListeners with a ratio
   * of 25 %.
   *
   */
  private void waitForLoader(Integer maxRatio) throws InstallException
  {
    int lastPercentage = -1;
    while (!loader.isFinished() && (loader.getException() == null))
    {
      // Pool until is over
      int perc = loader.getDownloadPercentage();
      if (perc != lastPercentage)
      {
        lastPercentage = perc;
        int ratio = (perc * maxRatio) / 100;
        String[] arg =
          { String.valueOf(perc) };
        String summary = getMsg("downloading-ratio", arg);
        hmSummary.put(InstallProgressStep.DOWNLOADING, summary);
        notifyListeners(ratio, summary, null);

        try
        {
          Thread.sleep(300);
        } catch (Exception ex)
        {
        }
      }
    }

    if (loader.getException() != null)
    {
      throw loader.getException();
    }
  }

  /**
   * This method extracts the zip file.
   * @param is the inputstream with the contents of the zip file.
   * @param minRatio the value of the ratio in the install that corresponds to
   * the moment where we start extracting the zip files.  Used to update
   * properly the install progress ratio.
   * @param maxRatio the value of the ratio in the installation that corresponds
   * to the moment where we finished extracting the last zip file.  Used to
   * update properly the install progress ratio.
   * @throws InstallException if an error occurs.
   */
  private void extractZipFiles(InputStream is, int minRatio, int maxRatio)
      throws InstallException
  {
    ZipInputStream zipIn = new ZipInputStream(is);
    String basePath = getUserData().getServerLocation();

    int nEntries = 1;

    /* This map is updated in the copyZipEntry method with the permissions
     * of the files that have been copied.  Once all the files have
     * been copied to the file system we will update the file permissions of
     * these files.  This is done this way to group the number of calls to
     * Runtime.exec (which is required to update the file system permissions).
     */
    Map<String, ArrayList<String>> permissions =
        new HashMap<String, ArrayList<String>>();

    String zipFirstPath = null;
    try
    {
      ZipEntry entry = zipIn.getNextEntry();
      while (entry != null)
      {
        int ratioBeforeCompleted = minRatio
        + ((nEntries - 1) * (maxRatio - minRatio) / getNumberZipEntries());
        int ratioWhenCompleted =
          minRatio + (nEntries * (maxRatio - minRatio) / getNumberZipEntries());

        if (nEntries == 1)
        {
          zipFirstPath = entry.getName();
        } else
        {
          try
          {
            copyZipEntry(entry, basePath, zipFirstPath, zipIn,
                ratioBeforeCompleted, ratioWhenCompleted, permissions);

          } catch (IOException ioe)
          {
            String[] arg =
              { entry.getName() };
            String errorMsg = getExceptionMsg("error-copying", arg, ioe);

            throw new InstallException(InstallException.Type.FILE_SYSTEM_ERROR,
                errorMsg, ioe);
          }
        }

        zipIn.closeEntry();
        entry = zipIn.getNextEntry();
        nEntries++;
      }

      if (Utils.isUnix())
      {
        // Change the permissions for UNIX systems
        for (String perm : permissions.keySet())
        {
          ArrayList<String> paths = permissions.get(perm);
          try
          {
            int result = Utils.setPermissionsUnix(paths, perm);
            if (result != 0)
            {
              throw new IOException("Could not set permissions on files "
                  + paths + ".  The chmod error code was: " + result);
            }
          } catch (InterruptedException ie)
          {
            IOException ioe =
                new IOException("Could not set permissions on files " + paths
                    + ".  The chmod call returned an InterruptedException.");
            ioe.initCause(ie);
            throw ioe;
          }
        }
      }

    } catch (IOException ioe)
    {
      String[] arg =
        { getZipFileName() };
      String errorMsg = getExceptionMsg("error-zip-stream", arg, ioe);
      throw new InstallException(InstallException.Type.FILE_SYSTEM_ERROR,
          errorMsg, ioe);
    }
  }

  /**
   * Copies a zip entry in the file system.
   * @param entry the ZipEntry object.
   * @param basePath the basePath (the installation path)
   * @param zipFirstPath the first zip file path.  This is required because the
   * zip file contain a directory of type
   * 'OpenDS-(major version).(minor version)' that we want to get rid of.  The
   * first zip file path corresponds to this path.
   * @param is the ZipInputStream that contains the contents to be copied.
   * @param ratioBeforeCompleted the progress ratio before the zip file is
   * copied.
   * @param ratioWhenCompleted the progress ratio after the zip file is
   * copied.
   * @param permissions an ArrayList with permissions whose contents will be
   * updated.
   * @throws IOException if an error occurs.
   */
  private void copyZipEntry(ZipEntry entry, String basePath,
      String zipFirstPath, ZipInputStream is, int ratioBeforeCompleted,
      int ratioWhenCompleted, Map<String, ArrayList<String>> permissions)
      throws IOException
  {
    String entryName = entry.getName();
    // Get rid of the zipFirstPath
    if (entryName.startsWith(zipFirstPath))
    {
      entryName = entryName.substring(zipFirstPath.length());
    }
    String path = Utils.getPath(basePath, entryName);

    notifyListeners(ratioBeforeCompleted, getSummary(getStatus()),
        getHtmlWithPoints(getMsg("progress-extracting", new String[]
          { path })));
    if (Utils.createParentPath(path))
    {
      if (entry.isDirectory())
      {
        String perm = getDirectoryFileSystemPermissions(path);
        ArrayList<String> list = permissions.get(perm);
        if (list == null)
        {
          list = new ArrayList<String>();
        }
        list.add(path);
        permissions.put(perm, list);

        if (!Utils.createDirectory(path))
        {
          throw new IOException("Could not create path: " + path);
        }
      } else
      {
        String perm = getFileSystemPermissions(path);
        ArrayList<String> list = permissions.get(perm);
        if (list == null)
        {
          list = new ArrayList<String>();
        }
        list.add(path);
        Utils.createFile(path, is);
      }
    } else
    {
      throw new IOException("Could not create parent path: " + path);
    }
    notifyListeners(ratioWhenCompleted, getSummary(getStatus()),
        getHtmlDone() + LINE_BREAK);
  }

  /**
   * {@inheritDoc}
   */
  protected String getOpenDSClassPath()
  {
    StringBuffer buf = new StringBuffer();
    String[] jars = getOpenDSJarPaths();
    for (int i = 0; i < jars.length; i++)
    {
      if (i != 0)
      {
        buf.append(System.getProperty("path.separator"));
      }
      buf.append(jars[i]);
    }
    return buf.toString();
  }

  /**
   * Returns the jar file paths in the installation.  This is used to launch
   * command lines that require a classpath.
   * @return the jar file paths in the installation.
   */
  private String[] getOpenDSJarPaths()
  {
    String[] jarPaths = new String[OPEN_DS_JAR_RELATIVE_PATHS.length];
    File parentDir = new File(getUserData().getServerLocation());
    for (int i = 0; i < jarPaths.length; i++)
    {
      File f = new File(parentDir, OPEN_DS_JAR_RELATIVE_PATHS[i]);
      jarPaths[i] = f.getAbsolutePath();
    }
    return jarPaths;
  }

  /**
   * Returns the name of the zip file name that contains all the installation.
   * @return the name of the zip file name that contains all the installation.
   */
  private String getZipFileName()
  {
    // Passed as a java option in the JNLP file
    return System.getProperty(ZIP_FILE_NAME);
  }

  /**
   * Returns the file system permissions for a directory.
   * @param path the directory for which we want the file permissions.
   * @return the file system permissions for the directory.
   */
  private String getDirectoryFileSystemPermissions(String path)
  {
    // TODO We should get this dynamically during build?
    return "755";
  }

  /**
   * Returns the file system permissions for a file.
   * @param path the file for which we want the file permissions.
   * @return the file system permissions for the file.
   */
  private String getFileSystemPermissions(String path)
  {
    // TODO We should get this dynamically during build?
    String perm;

    File file = new File(path);
    if (file.getParent().endsWith(File.separator + "bin"))
    {
      if (path.endsWith(".bat"))
      {
        perm = "644";
      } else
      {
        perm = "755";
      }
    } else if (path.endsWith(".sh"))
    {
      perm = "755";
    } else
    {
      perm = "644";
    }
    return perm;
  }

  /**
   * Returns the number of entries contained in the zip file.  This is used to
   * update properly the progress bar ratio.
   * @return the number of entries contained in the zip file.
   */
  private int getNumberZipEntries()
  {
    // TODO  we should get this dynamically during build
    return 83;
  }

  /**
   * {@inheritDoc}
   */
  protected String getConfigFilePath()
  {
    String fullInstallPath = getUserData().getServerLocation();

    return Utils.getPath(fullInstallPath, CONFIG_PATH_RELATIVE);
  }

  /**
   * {@inheritDoc}
   */
  protected String getBinariesPath()
  {
    String fullInstallPath = getUserData().getServerLocation();

    return Utils.getPath(fullInstallPath, BINARIES_PATH_RELATIVE);
  }
}
