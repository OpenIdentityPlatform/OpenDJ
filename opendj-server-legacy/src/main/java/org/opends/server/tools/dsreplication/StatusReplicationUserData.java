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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 */

package org.opends.server.tools.dsreplication;

/**
 * This class is used to store the information provided by the user to
 * show replication configuration.  It is required because when we are in
 * interactive mode the ReplicationCliArgumentParser is not enough.
 *
 */
public class StatusReplicationUserData extends MonoServerReplicationUserData
{
  private boolean scriptFriendly;

  /**
   * Whether we must display information in a script-friendly mode or not.
   * @return <CODE>true</CODE> if we must display the information in a
   * script-friendly mode and <CODE>false</CODE> otherwise.
   */
  public boolean isScriptFriendly()
  {
    return scriptFriendly;
  }

  /**
   * Sets whether we must display information in a script-friendly mode or not.
   * @param scriptFriendly whether we must display information in a
   * script-friendly mode or not.
   */
  public void setScriptFriendly(boolean scriptFriendly)
  {
    this.scriptFriendly = scriptFriendly;
  }
}

