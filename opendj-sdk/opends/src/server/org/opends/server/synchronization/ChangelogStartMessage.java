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
package org.opends.server.synchronization;

import java.io.Serializable;

import org.opends.server.types.DN;
import org.opends.server.core.DirectoryException;

/**
 * Message sent by a changelog server to another changelog server at Startup.
 */
public class ChangelogStartMessage extends SynchronizationMessage implements
    Serializable
{
  private static final long serialVersionUID = -5871385537169856856L;

  private short serverId;
  private String serverURL;
  private String baseDn = null;
  private ServerState serverState;

  /**
   * Create a ChangelogStartMessage.
   *
   * @param serverId changelog server id
   * @param serverURL changelog server URL
   * @param baseDn base DN for which the ChangelogStartMessage is created.
   * @param serverState our ServerState for this baseDn.
   */
  public ChangelogStartMessage(short serverId, String serverURL, DN baseDn,
                               ServerState serverState)
  {
    this.serverId = serverId;
    this.serverURL = serverURL;
    if (baseDn != null)
      this.baseDn = baseDn.toNormalizedString();
    else
      this.baseDn = null;
    this.serverState = serverState;
  }

  /**
   * Get the Server Id.
   * @return the server id
   */
  public short getServerId()
  {
    return this.serverId;
  }

  /**
   * Set the server URL.
   * @return the server URL
   */
  public String getServerURL()
  {
    return this.serverURL;
  }

  /**
   * Get the base DN from this ChangelogStartMessage.
   *
   * @return the base DN from this ChangelogStartMessage.
   */
  public DN getBaseDn()
  {
    if (baseDn == null)
      return null;
    try
    {
      return DN.decode(baseDn);
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * Get the serverState.
   * @return Returns the serverState.
   */
  public ServerState getServerState()
  {
    return this.serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UpdateMessage processReceive(SynchronizationDomain domain)
  {
    // This is currently not used.
    return null;
  }


}
