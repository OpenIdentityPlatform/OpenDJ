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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;

/**
 * This class is used to know which is the status of the install. This class is
 * not used when we install Open DS using java web start. However it is required
 * when do an offline installation after the user has unzipped the zip file. The
 * main goal of the class is to help identifying whether there is already
 * something installed or not.
 *
 * This class assumes that we are running in the case of an offline install.
 */

public class CurrentInstallStatus
{
  private boolean isInstalled;

  private boolean canOverwriteCurrentInstall;

  private String installationMsg;

  private String configFileContents;

  private Set<String> directoryManagerDns;

  private Set<String> dbPaths;

  private Set<String> logPaths;

  private String ldapUrl;

  private String ldapsUrl;

  private static boolean lockPathInitialized;

  /**
   * The constructor of a CurrentInstallStatus object.
   *
   */
  public CurrentInstallStatus()
  {
    if (Utils.isWebStart())
    {
      isInstalled = false;
    } else
    {
      boolean dbFileExists = false;
      ArrayList<String> msgs = new ArrayList<String>();

      if (isServerRunning())
      {
        msgs.add(getMsg("installstatus-serverrunning", new String[]
            { String.valueOf(getPort()) }));
      }

      if (dbFilesExist())
      {
        dbFileExists = true;
        msgs.add(getMsg("installstatus-dbfileexist"));
      }

      if (isConfigFileModified())
      {
        msgs.add(getMsg("installstatus-configfilemodified"));
      }
      canOverwriteCurrentInstall = (msgs.size() == 1) && dbFileExists;
      isInstalled = msgs.size() > 0;
      if (canOverwriteCurrentInstall)
      {
        installationMsg =
          getMsg("installstatus-canoverwritecurrentinstall-msg");
      }
      else if (isInstalled)
      {
        StringBuffer buf = new StringBuffer();
        buf.append("<ul>");
        for (String msg : msgs)
        {
          buf.append("\n<li>").append(msg).append("</li>");
        }
        buf.append("</ul>");
        installationMsg = getMsg("installstatus-installed", new String[]
          { buf.toString() });
      }
    }
    if (!isInstalled)
    {
      installationMsg = getMsg("installstatus-not-installed");
    }
  }

  /**
   * Indicates whether there is something installed or not.
   *
   * @return <CODE>true</CODE> if there is something installed under the
   *         binaries that we are running, or <CODE>false</CODE> if not.
   */
  public boolean isInstalled()
  {
    return isInstalled;
  }

  /**
   * Indicates can overwrite current install.
   *
   * @return <CODE>true</CODE> if there is something installed under the
   *         binaries that we are running and we can overwrite it and
   *         <CODE>false</CODE> if not.
   */
  public boolean canOverwriteCurrentInstall()
  {
    return canOverwriteCurrentInstall;
  }

  /**
   * Provides a localized message to be displayed to the user in HTML format
   * informing of the installation status.
   *
   * @return an String in HTML format describing the status of the installation.
   */
  public String getInstallationMsg()
  {
    return installationMsg;
  }

  /**
   * Returns the list of directory manager dns as they appear in the
   * configuration file.
   *
   * @return the list of directory manager dns as they appear in the
   * configuration file.
   */
  public Set<String> getDirectoryManagerDns()
  {
    if (directoryManagerDns == null)
    {
      directoryManagerDns = new HashSet<String>();
      String directoryManagerDnAttr = "ds-cfg-alternate-bind-dn";
      updateSetWithValues(directoryManagerDns, directoryManagerDnAttr);
    }
    return directoryManagerDns;
  }

  /**
   * Returns the list of paths where the databases are installed as they appear
   * in the configuration file.
   *
   * @return the list of paths where the databases are installed as they appear
   * in the configuration file.
   */
  public Set<String> getDatabasePaths()
  {
    if (dbPaths == null)
    {
      dbPaths = new HashSet<String>();
      String dbFileAttr = "ds-cfg-backend-directory";
      updateSetWithValues(dbPaths, dbFileAttr);
    }
    return dbPaths;
  }

  /**
   * Returns the list of paths where the logs files are located as they appear
   * in the configuration file.
   *
   * @return the list of paths where the logs files are located as they appear
   * in the configuration file.
   */
  public Set<String> getLogPaths()
  {
    if (logPaths == null)
    {
      logPaths = new HashSet<String>();
      String logFileAttr = "ds-cfg-log-file";
      updateSetWithValues(logPaths, logFileAttr);
    }
    return logPaths;
  }

  /**
   * Returns if the server is running on the given path.
   * NOTE: this method is to be called only when the OpenDS.jar class has
   * already been loaded as it uses classes in that jar.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  public static boolean isServerRunning()
  {
    boolean isServerRunning;
    if (!lockPathInitialized)
    {
      String lockDirectory = Utils.getPath(Utils.getInstallPathFromClasspath(),
      org.opends.server.util.ServerConstants.LOCKS_DIRECTORY);

      System.setProperty(
        org.opends.server.util.ServerConstants.PROPERTY_LOCK_DIRECTORY,
        lockDirectory);
      lockPathInitialized = true;
    }
    String lockFile =
      org.opends.server.core.LockFileManager.getServerLockFileName();
    StringBuilder failureReason = new StringBuilder();
    try
    {
      if (org.opends.server.core.LockFileManager.acquireExclusiveLock(lockFile,
          failureReason))
      {
        org.opends.server.core.LockFileManager.releaseLock(lockFile,
            failureReason);
        isServerRunning = false;
      }
      else
      {
        isServerRunning = true;
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace();
      // Assume that if we cannot acquire the lock file the server is
      // running.
      isServerRunning = true;
    }
    return isServerRunning;
  }

  /**
   * Provides the ldap url to the server (assumes we are calling this locally).
   *
   * @return the ldap url to the server.
   */
  public String getLdapUrl()
  {
    if (ldapUrl == null)
    {
      if (getPort() != -1)
      {
        ldapUrl = "ldap://localhost:"+getPort();
      }
    }
    return ldapUrl;
  }

  /**
   * Provides the ldap secure url to the server (assumes we are calling this
   * locally).
   *
   * @return the ldap secure url to the server.
   */
  public String getLdapsUrl()
  {
    if (ldapsUrl == null)
    {
      if (getSecurePort() != -1)
      {
        ldapsUrl = "ldaps://localhost:"+getSecurePort();
      }
    }
    return ldapsUrl;
  }

  /**
   * Provides the config file path (path to config.ldif file).
   *
   * @return the config file path.
   */
  private String getConfigFilePath()
  {
    return Utils.getConfigFileFromClasspath();
  }

  /**
   * Provides the LDAP port as is specified in the config.ldif file.
   *
   * @return the LDAP port specified in the config.ldif file.
   */
  private int getPort()
  {
    return getPort("ds-cfg-listen-port");
  }

  /**
   * Provides the LDAP secure port as is specified in the config.ldif file.
   *
   * @return the LDAP secure port specified in the config.ldif file.
   */
  private int getSecurePort()
  {
    // TODO find out which is the attribute for this port.
    return getPort("ds-cfg-listen-secure-port");
  }

  private int getPort(String portAttr)
  {
    int port = -1;

    int index = getConfigFileContents().indexOf("cn=ldap connection handler");

    if (index != -1)
    {
      String attrWithPoints = portAttr+":";
      int index1 = getConfigFileContents().indexOf(attrWithPoints, index);
      if (index1 != -1)
      {
        int index2 =
            getConfigFileContents().indexOf(
                System.getProperty("line.separator"), index1);
        if (index2 != -1)
        {
          String sPort =
              getConfigFileContents().substring(attrWithPoints.length() +
                  index1,
                  index2).trim();
          try
          {
            port = Integer.parseInt(sPort);
          } catch (NumberFormatException nfe)
          {
          }
        }
      }
    }
    return port;
  }

  /**
   * Indicates whether there are database files under this installation.
   *
   * @return <CODE>true</CODE> if there are database files, or
   * <CODE>false</CODE> if not.
   */
  private boolean dbFilesExist()
  {
    boolean dbFilesExist = false;
    File dbDir = new File(Utils.getInstallPathFromClasspath(), "db");
    File[] children = dbDir.listFiles();
    if ((children != null) && (children.length > 0))
    {
      dbFilesExist = true;
    }
    return dbFilesExist;
  }

  /**
   * Indicates whether the config.ldif file has been modified (compared to what
   * we had in the zip file). This is used to know if we have configured the
   * current binaries or not.
   *
   * @return <CODE>true</CODE> if the config.ldif file has been modified, or
   *         <CODE>false</CODE> if not.
   */
  private boolean isConfigFileModified()
  {
    boolean isConfigFileModified = getPort() != 389;

    if (!isConfigFileModified)
    {
      // TODO: this is not really stable
      isConfigFileModified =
          getConfigFileContents().indexOf("# cddl header start") == -1;
    }

    return isConfigFileModified;
  }

  /**
   * Provides the contents of the config.ldif file in a String.
   *
   * @return a String representing the contents of the config.ldif file.
   */
  private String getConfigFileContents()
  {
    if (configFileContents == null)
    {
      StringBuffer buf = new StringBuffer();
      try
      {
        FileReader reader = new FileReader(getConfigFilePath());
        BufferedReader in = new BufferedReader(reader);
        String line;
        // We do not care about encoding: we are just interested in the ports
        while ((line = in.readLine()) != null)
        {
          buf.append(line + System.getProperty("line.separator"));
        }
        reader.close();
      } catch (IOException ioe)
      {
      }
      configFileContents = buf.toString().toLowerCase();
    }
    return configFileContents;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  private void updateSetWithValues(Set<String> set, String attrName)
  {
    attrName += ":";
    int index1 = getConfigFileContents().indexOf(attrName);
    while (index1 != -1)
    {
      int index2 = getConfigFileContents().indexOf(
            System.getProperty("line.separator"), index1);
      String value;
      if (index2 > (index1 + attrName.length()))
      {
        value = getConfigFileContents().substring(attrName.length() + index1,
              index2).trim();
      }
      else if (getConfigFileContents().length() > (index1 + attrName.length()))
      {
        // Assume end of file
        value = getConfigFileContents().substring(
            attrName.length() + index1).trim();
      }
      else
      {
        value = null;
      }

      if ((value != null) && (value.length() > 0))
      {
        set.add(value);
      }

      index1 = getConfigFileContents().indexOf(attrName,
          index1 + attrName.length());
    }
  }
}
