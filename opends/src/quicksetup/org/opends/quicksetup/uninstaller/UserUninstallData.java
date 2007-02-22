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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.uninstaller;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is used to provide a data model for the different parameters
 * that the user can provide in the uninstall wizard.
 *
 */
public class UserUninstallData
{
  private Set<String> externalDbsToRemove = new HashSet<String>();
  private Set<String> externalLogsToRemove = new HashSet<String>();
  private boolean removeDatabases;
  private boolean removeLogs;
  private boolean removeLibrariesAndTools;
  private boolean removeBackups;
  private boolean removeLDIFs;
  private boolean removeConfigurationAndSchema;

  private boolean stopServer;

  /**
   * Sets the database directories located outside the installation which must
   * be removed.
   * @param dbPaths the directories of the database files.
   */
  public void setExternalDbsToRemove(Set<String> dbPaths)
  {
    externalDbsToRemove.clear();
    externalDbsToRemove.addAll(dbPaths);
  }

  /**
   * Returns the list of databases located outside the installation that must
   * be removed.
   * @return the list of databases located outside the installation that must
   * be removed.
   */
  public Set<String> getExternalDbsToRemove()
  {
    return new HashSet<String>(externalDbsToRemove);
  }

  /**
   * Sets the log files located outside the installation which must
   * be removed.
   * @param logFiles the log files.
   */
  public void setExternalLogsToRemove(Set<String> logFiles)
  {
    externalLogsToRemove.clear();
    externalLogsToRemove.addAll(logFiles);
  }
  /**
   * Returns the list of log files located outside the installation that must
   * be removed.
   * @return the list of log files located outside the installation that must
   * be removed.
   */
  public Set<String> getExternalLogsToRemove()
  {
    return new HashSet<String>(externalLogsToRemove);
  }

  /**
   * Returns whether the user wants to remove libraries and tools or not.
   * @return <CODE>true</CODE> if the user wants to remove the libraries and
   * tools and <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveLibrariesAndTools()
  {
    return removeLibrariesAndTools;
  }

  /**
   * Sets whether to remove libraries and tools or not.
   * @param removeLibrariesAndTools remove libraries and tools or not.
   */
  public void setRemoveLibrariesAndTools(boolean removeLibrariesAndTools)
  {
    this.removeLibrariesAndTools = removeLibrariesAndTools;
  }


  /**
   * Sets whether to remove databases or not.
   * @param removeDatabases remove databases or not.
   */
  public void setRemoveDatabases(boolean removeDatabases)
  {
    this.removeDatabases = removeDatabases;
  }

  /**
   * Returns whether the user wants to remove databases or not.
   * @return <CODE>true</CODE> if the user wants to remove the databases and
   * <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveDatabases()
  {
    return removeDatabases;
  }

  /**
   * Sets whether to remove backups or not.
   * @param removeBackups remove backups or not.
   */
  public void setRemoveBackups(boolean removeBackups)
  {
    this.removeBackups = removeBackups;
  }

  /**
   * Returns whether the user wants to remove backups or not.
   * @return <CODE>true</CODE> if the user wants to remove the backups and
   * <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveBackups()
  {
    return removeBackups;
  }

  /**
   * Sets whether to remove log files or not.
   * @param removeLogs remove log files or not.
   */
  public void setRemoveLogs(boolean removeLogs)
  {
    this.removeLogs = removeLogs;
  }

  /**
   * Returns whether the user wants to remove logs or not.
   * @return <CODE>true</CODE> if the user wants to remove the log files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveLogs()
  {
    return removeLogs;
  }

  /**
   * Sets whether to remove LDIF files or not.
   * @param removeLDIFs remove LDIF files or not.
   */
  public void setRemoveLDIFs(boolean removeLDIFs)
  {
    this.removeLDIFs = removeLDIFs;
  }

  /**
   * Returns whether the user wants to remove LDIF files or not.
   * @return <CODE>true</CODE> if the user wants to remove the LDIF files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveLDIFs()
  {
    return removeLDIFs;
  }

  /**
   * Sets whether to remove configuration and schema files or not.
   * @param removeConfigurationAndSchema remove configuration and schema files
   * or not.
   */
  public void setRemoveConfigurationAndSchema(
      boolean removeConfigurationAndSchema)
  {
    this.removeConfigurationAndSchema = removeConfigurationAndSchema;
  }

  /**
   * Returns whether the user wants to remove configuration and schema files or
   * not.
   * @return <CODE>true</CODE> if the user wants to remove the configuration
   * and schema files and <CODE>false</CODE> otherwise.
   */
  public boolean getRemoveConfigurationAndSchema()
  {
    return removeConfigurationAndSchema;
  }

  /**
   * Sets whether to stop the server or not.
   * @param stopServer stop the server or not.
   */
  public void setStopServer(boolean stopServer)
  {
    this.stopServer = stopServer;
  }

  /**
   * Returns whether the user wants to stop the server or not.
   * @return <CODE>true</CODE> if the user wants to stop the server and <CODE>\
   * false</CODE> otherwise.
   */
  public boolean getStopServer()
  {
    return stopServer;
  }
}

