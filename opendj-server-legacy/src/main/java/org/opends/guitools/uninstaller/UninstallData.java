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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.uninstaller;

import java.io.IOException;
import java.util.Iterator;

import org.opends.admin.ads.ADSContext;
import org.opends.quicksetup.Configuration;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;

/**
 * This is a convenience class used to represent the current configuraton and
 * status of the server to know which kind of questions we must ask to the user
 * (the server is running, it is configured for replication, it contains an
 * ADS...).
 *
 * The difference with Installation class is that it provides read only
 * information that is computed in the constructor of the class and not
 * on demand.  This way we can construct the object outside the event thread
 * and then read it inside the event thread without blocking the display.
 */
public class UninstallData
{
  private boolean isServerRunning;
  private boolean isADS;
  private boolean isReplicationServer;
  private int replicationServerPort;

  /**
   * The constructor for UninstallData.
   * @param installation the object describing the installation.
   * @throws IOException if there was an error retrieving the current
   * installation configuration.
   */
  public UninstallData(Installation installation) throws IOException
  {
    isServerRunning = installation.getStatus().isServerRunning();
    Configuration conf = new Configuration(installation,
        installation.getCurrentConfigurationFile());
    Iterator<String> it = conf.getBaseDNs().iterator();
    while (it.hasNext() && !isADS)
    {
      isADS = Utils.areDnsEqual(it.next(),
          ADSContext.getAdministrationSuffixDN().toString());
    }
    isReplicationServer = conf.isReplicationServer();
    replicationServerPort = conf.getReplicationPort();
  }

  /**
   * Returns whether this server is configured as an ADS or not.
   * @return <CODE>true</CODE> if the server is configured as an ADS and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isADS() {
    return isADS;
  }

  /**
   * Returns whether this server is configured as a replication server or not.
   * @return <CODE>true</CODE> if the server is configured as a replication
   * server and <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationServer() {
    return isReplicationServer;
  }

  /**
   * Returns whether this server is running or not.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isServerRunning() {
    return isServerRunning;
  }

  /**
   * Returns the port of the replication server.  -1 if it is not defined.
   * @return the port of the replication server.  -1 if it is not defined.
   */
  public int getReplicationServerPort() {
    return replicationServerPort;
  }
}
