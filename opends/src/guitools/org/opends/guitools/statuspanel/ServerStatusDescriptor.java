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

package org.opends.guitools.statuspanel;

import org.opends.messages.Message;

import java.io.File;
import java.util.Set;

/**
 * This is just a class used to provide a data model describing what the
 * StatusPanelDialog will show to the user.
 */
public class ServerStatusDescriptor
{
  private ServerStatus status;
  private int openConnections;
  private Set<DatabaseDescriptor> databases;
  private Set<ListenerDescriptor> listeners;
  private Set<String> administrativeUsers;
  private File installPath;
  private String openDSVersion;
  private String javaVersion;
  private Message errorMsg;
  private boolean isAuthenticated;

  /**
   * Enumeration indicating the status of the server.
   *
   */
  public enum ServerStatus
  {
    /**
     * Server Started.
     */
    STARTED,
    /**
     * Server Stopped.
     */
    STOPPED,
    /**
     * Server Starting.
     */
    STARTING,
    /**
     * Server Stopping.
     */
    STOPPING,
    /**
     * Status Unknown.
     */
    UNKNOWN
  }

  /**
   * Default constructor.
   */
  public ServerStatusDescriptor()
  {
  }

  /**
   * Return the administrative users.
   * @return the administrative users.
   */
  public Set<String> getAdministrativeUsers()
  {
    return administrativeUsers;
  }

  /**
   * Set the administrative users.
   * @param administrativeUsers the administrative users to set
   */
  public void setAdministrativeUsers(Set<String> administrativeUsers)
  {
    this.administrativeUsers = administrativeUsers;
  }

  /**
   * Return the install path where the server is installed.
   * @return the install path where the server is installed.
   */
  public File getInstallPath()
  {
    return installPath;
  }

  /**
   * Sets the install path where the server is installed.
   * @param installPath the install path where the server is installed.
   */
  public void setInstallPath(File installPath)
  {
    this.installPath = installPath;
  }

  /**
   * Return the java version used to run the server.
   * @return the java version used to run the server.
   */
  public String getJavaVersion()
  {
    return javaVersion;
  }

  /**
   * Set the java version used to run the server.
   * @param javaVersion the java version used to run the server.
   */
  public void setJavaVersion(String javaVersion)
  {
    this.javaVersion = javaVersion;
  }

  /**
   * Returns the number of open connection in the server.
   * @return the number of open connection in the server.
   */
  public int getOpenConnections()
  {
    return openConnections;
  }

  /**
   * Set the number of open connections.
   * @param openConnections the number of open connections.
   */
  public void setOpenConnections(int openConnections)
  {
    this.openConnections = openConnections;
  }

  /**
   * Returns the version of the server.
   * @return the version of the server.
   */
  public String getOpenDSVersion()
  {
    return openDSVersion;
  }

  /**
   * Sets the version of the server.
   * @param openDSVersion the version of the server.
   */
  public void setOpenDSVersion(String openDSVersion)
  {
    this.openDSVersion = openDSVersion;
  }

  /**
   * Returns the status of the server.
   * @return the status of the server.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Sets the status of the server.
   * @param status the status of the server.
   */
  public void setStatus(ServerStatus status)
  {
    this.status = status;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (this != o)
    {
      if (o instanceof ServerStatusDescriptor)
      {
        ServerStatusDescriptor desc = (ServerStatusDescriptor)o;
        equals = desc.getStatus() == getStatus();

        if (equals)
        {
          equals = desc.getOpenConnections() == getOpenConnections();
        }

        if (equals)
        {
          equals = desc.getInstallPath().equals(getInstallPath());
        }

        if (equals)
        {
          if (desc.getJavaVersion() == null)
          {
            equals = getJavaVersion() == null;
          }
          else
          {
            equals = desc.getJavaVersion().equals(getJavaVersion());
          }
        }

        if (equals)
        {
          equals = desc.getOpenDSVersion().equals(getOpenDSVersion());
        }

        if (equals)
        {
          equals = desc.getAdministrativeUsers().equals(
              getAdministrativeUsers());
        }

        if (equals)
        {
          equals = desc.getListeners().equals(getListeners());
        }

        if (equals)
        {
          equals = desc.getDatabases().equals(getDatabases());
        }

        if (equals)
        {
          if (desc.getErrorMessage() == null)
          {
            equals = getErrorMessage() == null;
          }
          else
          {
            equals = desc.getErrorMessage().equals(getErrorMessage());
          }
        }
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return status.hashCode() + openConnections +
    (String.valueOf(
        installPath+openDSVersion+javaVersion+errorMsg+isAuthenticated)).
        hashCode();
  }

  /**
   * Returns the error message that we encountered generating this server
   * status descriptor.
   * @return the error message that we encountered generating this server
   * status descriptor.
   */
  public Message getErrorMessage()
  {
    return errorMsg;
  }

  /**
   * Sets the error message that we encountered generating this server
   * status descriptor.
   * @param errorMsg the error message that we encountered generating this
   * server status descriptor.
   */
  public void setErrorMessage(Message errorMsg)
  {
    this.errorMsg = errorMsg;
  }

  /**
   * Return whether we were authenticated when retrieving the information of
   * this ServerStatusDescriptor.
   * @return <CODE>true</CODE> if we were authenticated when retrieving the
   * information of this ServerStatusDescriptor and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isAuthenticated()
  {
    return isAuthenticated;
  }

  /**
   * Sets whether we were authenticated when retrieving the information of
   * this ServerStatusDescriptor.
   * @param isAuthenticated whether we were authenticated when retrieving the
   * information of this ServerStatusDescriptor.
   */
  public void setAuthenticated(boolean isAuthenticated)
  {
    this.isAuthenticated = isAuthenticated;
  }

  /**
   * Returns the database descriptors of the server.
   * @return the database descriptors of the server.
   */
  public Set<DatabaseDescriptor> getDatabases()
  {
    return databases;
  }

  /**
   * Sets the database descriptors of the server.
   * @param databases the database descriptors to set.
   */
  public void setDatabases(Set<DatabaseDescriptor> databases)
  {
    this.databases = databases;
  }

  /**
   * Returns the listener descriptors of the server.
   * @return the listener descriptors of the server.
   */
  public Set<ListenerDescriptor> getListeners()
  {
    return listeners;
  }

  /**
   * Sets the listener descriptors of the server.
   * @param listeners the listener descriptors to set.
   */
  public void setListeners(Set<ListenerDescriptor> listeners)
  {
    this.listeners = listeners;
  }
}
