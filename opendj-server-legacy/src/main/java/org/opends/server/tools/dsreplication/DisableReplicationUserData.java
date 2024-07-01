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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import org.forgerock.opendj.ldap.DN;

/**
 * This class is used to store the information provided by the user to
 * disable replication.  It is required because when we are in interactive
 * mode the ReplicationCliArgumentParser is not enough.
 */
public class DisableReplicationUserData extends MonoServerReplicationUserData
{
  private DN bindDn;
  private String bindPwd;
  private boolean disableReplicationServer;
  private boolean disableAll;

  /**
   * Returns the bind DN to be used to connect to the server if no Administrator
   * has been defined.
   * @return the bind DN to be used to connect to the server if no Administrator
   * has been defined.
   */
  public DN getBindDn()
  {
    return bindDn;
  }

  /**
   * Sets the bind DN to be used to connect to the server if no Administrator
   * has been defined.
   * @param bindDn the bind DN to be used.
   */
  public void setBindDn(DN bindDn)
  {
    this.bindDn = bindDn;
  }

  /**
   * Returns the password to be used to connect to the server if no
   * Administrator has been defined.
   * @return the password to be used to connect to the server if no
   * Administrator has been defined.
   */
  public String getBindPwd()
  {
    return bindPwd;
  }

  /**
   * Sets the password to be used to connect to the server if no Administrator
   * has been defined.
   * @param bindPwd the password to be used.
   */
  public void setBindPwd(String bindPwd)
  {
    this.bindPwd = bindPwd;
  }

  /**
   * Tells whether the user wants to disable all the replication from the
   * server.
   * @return <CODE>true</CODE> if the user wants to disable all replication
   * from the server and <CODE>false</CODE> otherwise.
   */
  public boolean disableAll()
  {
    return disableAll;
  }

  /**
   * Sets whether the user wants to disable all the replication from the
   * server.
   * @param disableAll whether the user wants to disable all the replication
   * from the server.
   */
  public void setDisableAll(boolean disableAll)
  {
    this.disableAll = disableAll;
  }

  /**
   * Tells whether the user asked to disable the replication server in the
   * server.
   * @return <CODE>true</CODE> if the user wants to disable replication server
   * in the server and <CODE>false</CODE> otherwise.
   */
  public boolean disableReplicationServer()
  {
    return disableReplicationServer;
  }

  /**
   * Sets whether the user asked to disable the replication server in the
   * server.
   * @param disableReplicationServer whether the user asked to disable the
   * replication server in the server.
   */
  public void setDisableReplicationServer(boolean disableReplicationServer)
  {
    this.disableReplicationServer = disableReplicationServer;
  }
}
