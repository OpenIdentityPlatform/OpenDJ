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
package org.opends.server.replication.server;

import org.opends.server.replication.common.ChangeNumber;

/**
 * This class is used to store acks for update messages coming from
 * other replication servers.
 */
public class ReplServerAckMessageList extends AckMessageList
{
  private short replicationServerId;
  private ReplicationServerDomain replicationServerDomain;

  /**
   * Creates a new AckMessageList for a given ChangeNumber.
   *
   * @param changeNumber The ChangeNumber for which the ack list is created.
   * @param numExpectedAcks The number of acks waited before acking the
   *                        original change.
   * @param replicationServerId The Identifier of the replication server
   *                          from which the change was received.
   * @param replicationServerDomain The ReplicationServerDomain from which he
   *                         change was received.
   */
  public ReplServerAckMessageList(ChangeNumber changeNumber,
                                 int numExpectedAcks,
                                 short replicationServerId,
                                ReplicationServerDomain replicationServerDomain)
  {
    super(changeNumber, numExpectedAcks);
    this.replicationServerId = replicationServerId;
    this.replicationServerDomain = replicationServerDomain;
  }

  /**
   * Get the Identifier of the replication server from which we received the
   * change.
   * @return Returns the Identifier of the replication server from which we
   *         received the change.
   */
  public short getReplicationServerId()
  {
    return replicationServerId;
  }

  /**
   * Get the replicationServerDomain of the replication server from which we
   * received the change.
   * @return Returns the replicationServerDomain of the replication server from
   *         which we received the change .
   */
  public ReplicationServerDomain getChangelogCache()
  {
    return replicationServerDomain;
  }


}
