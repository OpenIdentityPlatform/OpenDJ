/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.protocol.LDAPUpdateMsg;

/**
 * This is a bag class to hold an update to replay in the queue of updates to
 * be replayed by the replay threads.
 * It associates an update message to replay with the matching
 * ReplicationDomain.
 */
public class UpdateToReplay
{
  private LDAPUpdateMsg updateMessage;
  private LDAPReplicationDomain replicationDomain;

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
