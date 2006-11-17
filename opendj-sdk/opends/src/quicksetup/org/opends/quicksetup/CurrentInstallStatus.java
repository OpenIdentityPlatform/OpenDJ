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

package org.opends.quicksetup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.naming.NamingException;

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
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

  private String installationMsg;

  private String configFileContents;

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
      ArrayList<String> msgs = new ArrayList<String>();

      if (isServerRunning())
      {
        if (isConfigFileModified() || (getPort() != 389))
        {
          /*
           * If the config file is not modified and the port is 389 the common
           * case is that there is already a server running on the default port,
           * so it does not correspond to this install.
           */
          msgs.add(getMsg("installstatus-serverrunning", new String[]
            { String.valueOf(getPort()) }));
        }
      }

      if (dbFilesExist())
      {
        msgs.add(getMsg("installstatus-dbfileexist"));
      }

      if (isConfigFileModified())
      {
        msgs.add(getMsg("installstatus-configfilemodified"));
      }
      isInstalled = msgs.size() > 0;
      if (isInstalled)
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
   * Provides the config file path (path to config.ldif file).
   *
   * @return the config file path.
   */
  private String getConfigFilePath()
  {
    return OfflineInstaller.CONFIG_FILE_NAME;
  }

  /**
   * Provides the LDAP port as is specified in the config.ldif file.
   *
   * @return the LDAP port specified in the config.ldif file.
   */
  private int getPort()
  {
    int port = -1;

    int index = getConfigFileContents().indexOf("cn=ldap connection handler");

    if (index != -1)
    {
      String portAttr = "ds-cfg-listen-port:";
      int index1 = getConfigFileContents().indexOf(portAttr, index);
      if (index1 != -1)
      {
        int index2 =
            getConfigFileContents().indexOf(
                System.getProperty("line.separator"), index1);
        if (index2 != -1)
        {
          String sPort =
              getConfigFileContents().substring(portAttr.length() + index1,
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
   * Indicates whether there is the server is running in the localhost on the
   * LDAP port specified in config.ldif file.
   *
   * @return <CODE>true</CODE> if the server is running, or <CODE>false</CODE>
   *         if not.
   */
  private boolean isServerRunning()
  {
    boolean isServerRunning = false;

    int port = getPort();

    if (port > 0)
    {
      String ldapURL = "ldap://localhost:" + port;

      try
      {
        Utils.createLdapContext(ldapURL, null, null, 3000, null);
        isServerRunning = true;
      } catch (NamingException ne)
      {
      }
    }
    return isServerRunning;
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
    File dbDir = new File(OfflineInstaller.FULL_INSTALL_PATH, "db");
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
        BufferedReader in =
            new BufferedReader(new FileReader(getConfigFilePath()));
        String line;
        // We do not care about encoding: we are just interested in the ports
        while ((line = in.readLine()) != null)
        {
          buf.append(line + System.getProperty("line.separator"));
        }
        configFileContents = buf.toString().toLowerCase();
      } catch (IOException ioe)
      {
      }
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
}
