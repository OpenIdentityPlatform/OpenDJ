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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.protocol.UpdateMessage;

/**
 * This is a bag class to hold an update to replay in the queue of updates to
 * be replayed by the replay threads.
 * It associates an update message to replay with the matching
 * ReplicationDomain.
 */
public class UpdateToReplay
{
  private UpdateMessage updateMessage = null;
  private ReplicationDomain replicationDomain = null;

  /**
   * Construct the object associating the update message with the replication
   * domain that must be used to replay it (the on it comes from).
   * @param updateMessage The update message
   * @param replicationDomain The replication domain to use for replaying the
   * change from the update message
   */
  public UpdateToReplay(UpdateMessage updateMessage,
    ReplicationDomain replicationDomain)
  {
    this.updateMessage = updateMessage;
    this.replicationDomain = replicationDomain;
  }

  /**
   * Getter for update message.
   * @return The update message
   */
  public UpdateMessage getUpdateMessage()
  {
    return updateMessage;
  }

  /**
   * Getter for replication domain.
   * @return The replication domain
   */
  public ReplicationDomain getReplicationDomain()
  {
    return replicationDomain;
  }
}
