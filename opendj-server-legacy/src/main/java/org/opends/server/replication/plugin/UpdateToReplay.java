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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.protocol.LDAPUpdateMsg;

/**
 * A delivery in the shared replay queue, associating an update with its domain.
 * Cancellation belongs to this delivery, so an enqueue or claim failure can release
 * it without acquiring the pending-changes lock or cancelling a newer retry.
 */
public class UpdateToReplay
{
  private final LDAPUpdateMsg updateMessage;
  private final LDAPReplicationDomain replicationDomain;
  private volatile boolean cancelled;

  /**
   * Construct the object associating the update message with the replication
   * domain that must be used to replay it (the on it comes from).
   * @param updateMessage The update message
   * @param replicationDomain The replication domain to use for replaying the
   * change from the update message
   */
  public UpdateToReplay(LDAPUpdateMsg updateMessage,
    LDAPReplicationDomain replicationDomain)
  {
    this.updateMessage = updateMessage;
    this.replicationDomain = replicationDomain;
  }

  /**
   * Releases a failed hand-off without a lock or allocation, even on an OOME road.
   * A stale cancellation affects only this delivery, never the retry which replaced it.
   */
  void cancel()
  {
    cancelled = true;
    if (replicationDomain != null)
    {
      replicationDomain.requestSessionRestart();
    }
  }

  boolean isCancelled()
  {
    return cancelled;
  }

  /**
   * Getter for update message.
   * @return The update message
   */
  public LDAPUpdateMsg getUpdateMessage()
  {
    return updateMessage;
  }

  /**
   * Getter for replication domain.
   * @return The replication domain
   */
  public LDAPReplicationDomain getReplicationDomain()
  {
    return replicationDomain;
  }
}
