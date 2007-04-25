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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.util.LinkedList;

import org.opends.server.replication.common.ChangeNumber;

/**
 * This class is used to store the list of acks received for
 * a Given Update Messages.
 *
 * The acks are kept only for the update that are marked, hopefully this
 * should be a limited number of updates and in all cases, LDAP servers
 * operations are going to be blocked waiting for these acks so they
 * won't be able to generate a huge number of such messages.
 *
 * Therefore, the amount of memory used keeping those changes is not a problem,
 */
public class AckMessageList
{
  // The ChangeNumber of the updates that was acked
  // or that is waiting acks
  private ChangeNumber changeNumber;

  // The list of serverIdentifiers for which acks have been received so far
  // can be empty when no acks have been received
  private LinkedList<Short> acks;

  private int numExpectedAcks;

  /**
   * Creates a new AckMessageList for a given ChangeNumber.
   *
   * @param changeNumber The ChangeNumber for which the ack list is created.
   * @param numExpectedAcks The number of acks waited before acking the
   *                        original change.
   */
  public AckMessageList(ChangeNumber changeNumber, int numExpectedAcks)
  {
    acks = new LinkedList<Short>();
    this.changeNumber = changeNumber;
    this.numExpectedAcks = numExpectedAcks;
  }

  /**
   * Get the ChangeNumber of this Ack Message List.
   * @return Returns the changeNumber.
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Add an ack from a given LDAP server to the ack list.
   *
   * @param serverId the identifier of the LDAP server.
   */
  public synchronized void addAck(short serverId)
  {
    acks.add(serverId);
  }

  /**
   * This method can be used to check if all acks have been received for the
   * ChangeNumber managed by this list.
   * @return A boolean indicating if all acks have been received for the
   *         ChangeNumber managed by this list.
   */
  public boolean completed()
  {
    return (acks.size() >= numExpectedAcks);
  }
}
