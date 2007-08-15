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

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  static private final Logger LOG =
          Logger.getLogger(CurrentInstallStatus.class.getName());

  private boolean isInstalled;

  private boolean canOverwriteCurrentInstall;

  private Message installationMsg;

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
      Installation installation = Installation.getLocal();
      boolean dbFileExists = false;
      ArrayList<Message> msgs = new ArrayList<Message>();

      if (installation.getStatus().isServerRunning())
      {
        msgs.add(INFO_INSTALLSTATUS_SERVERRUNNING.get(
                String.valueOf(getPort())));
      }

      if (dbFilesExist())
      {
        dbFileExists = true;
        msgs.add(INFO_INSTALLSTATUS_DBFILEEXIST.get());
      }

      if (isConfigFileModified())
      {
        msgs.add(INFO_INSTALLSTATUS_CONFIGFILEMODIFIED.get());
      }
      canOverwriteCurrentInstall = (msgs.size() == 1) && dbFileExists;
      isInstalled = msgs.size() > 0;
      if (canOverwriteCurrentInstall)
      {
        installationMsg =
          INFO_INSTALLSTATUS_CANOVERWRITECURRENTINSTALL_MSG.get();
      }
      else if (isInstalled)
      {
        MessageBuilder buf = new MessageBuilder();
        buf.append("<ul>");
        for (Message msg : msgs)
        {
          buf.append("\n<li>");
          buf.append(msg);
          buf.append("</li>");
        }
        buf.append("</ul>");
        installationMsg = INFO_INSTALLSTATUS_INSTALLED.get( buf.toString() );
      }
    }
    if (!isInstalled)
    {
      installationMsg = INFO_INSTALLSTATUS_NOT_INSTALLED.get();
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
  public Message getInstallationMsg()
  {
    return installationMsg;
  }

  private int getPort()
  {
    int port = -1;
    try {
      port = Installation.getLocal().getCurrentConfiguration().
              getPort();
    } catch (IOException ioe) {
      LOG.log(Level.INFO, "Failed to get port", ioe);
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

    File dbDir = Installation.getLocal().getDatabasesDirectory();
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
    boolean mod = false;
    try {
      mod = Installation.getLocal().getCurrentConfiguration()
              .hasBeenModified();
    } catch (IOException ioe) {
      LOG.log(Level.INFO, "failed to determine if config modified", ioe);
    }
    return mod;
  }

}
