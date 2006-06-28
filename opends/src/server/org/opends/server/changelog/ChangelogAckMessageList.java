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
package org.opends.server.changelog;

import org.opends.server.synchronization.ChangeNumber;

/**
 * This class is used to store acks for update messages coming from
 * other changelog servers.
 */
public class ChangelogAckMessageList extends AckMessageList
{
  private short changelogServerId;
  private ChangelogCache changelogCache;

  /**
   * Creates a new AckMessageList for a given ChangeNumber.
   *
   * @param changeNumber The ChangeNumber for which the ack list is created.
   * @param numExpectedAcks The number of acks waited before acking the
   *                        original change.
   * @param changelogServerId The Identifier of the changelog server
   *                          from which the change was received.
   * @param changelogCache The ChangelogCache from which he change was received.
   */
  public ChangelogAckMessageList(ChangeNumber changeNumber,
                                 int numExpectedAcks,
                                 short changelogServerId,
                                 ChangelogCache changelogCache)
  {
    super(changeNumber, numExpectedAcks);
    this.changelogServerId = changelogServerId;
    this.changelogCache = changelogCache;
  }

  /**
   * Get the Identifier of the changelog server from which we received the
   * change.
   * @return Returns the Identifier of the changelog server from which we
   *         received the change.
   */
  public short getChangelogServerId()
  {
    return changelogServerId;
  }

  /**
   * Get the changelogCache of the changelog server from which we received the
   * change.
   * @return Returns the changelogCache of the changelog server from which we
   *         received the change .
   */
  public ChangelogCache getChangelogCache()
  {
    return changelogCache;
  }


}
