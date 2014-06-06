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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.common;

import org.opends.server.util.TimeThread;

/**
 * This class defines a structure that is used for storing the last {@link CSN}s
 * generated on this server or received from other servers and generating new
 * {@link CSN}s that are guaranteed to be larger than all the previously seen or
 * generated CSNs.
 */
public class CSNGenerator
{
  private long lastTime;
  /**
   * The sequence number allows to distinguish changes that have been done in
   * the same millisecond.
   *
   * @see #lastTime
   */
  private int seqnum;
  private final int serverId;

  /**
   * Create a new {@link CSNGenerator}.
   *
   * @param serverId
   *          id to use when creating {@link CSN}s.
   * @param timestamp
   *          time to start with.
   */
  public CSNGenerator(int serverId, long timestamp)
  {
    this.lastTime = timestamp;
    this.serverId = serverId;
    this.seqnum = 0;
  }

  /**
  * Create a new {@link CSNGenerator}.
  *
  * @param serverId serverId to use when creating {@link CSN}s.
  * @param state This generator will be created in a way that makes sure that
  *              all {@link CSN}s generated will be larger than all the
  *              {@link CSN}s currently in state.
  */
  public CSNGenerator(int serverId, ServerState state)
  {
    this.lastTime = TimeThread.getTime();
    for (CSN csn : state)
    {
      if (this.lastTime < csn.getTime())
      {
        this.lastTime = csn.getTime();
      }
      if (csn.getServerId() == serverId)
      {
        this.seqnum = csn.getSeqnum();
      }
    }
    this.serverId = serverId;
  }

  /**
   * Generate a new {@link CSN}.
   *
   * @return the generated {@link CSN}
   */
  public CSN newCSN()
  {
    long curTime = TimeThread.getTime();
    int mySeqnum;
    long myTime;

    synchronized(this)
    {
      if (curTime > lastTime)
      {
        lastTime = curTime;
      }

      if (++seqnum <= 0) // check no underflow happened
      {
        seqnum = 0;
        lastTime++;
      }
      mySeqnum = seqnum;
      myTime = lastTime;
    }

    return new CSN(myTime, mySeqnum, serverId);
  }

  /**
   * Adjust the lastTime of this {@link CSNGenerator} with a {@link CSN} that we
   * have received from another server.
   * <p>
   * This is necessary because we need that the {@link CSN} generated after
   * processing an update received from other hosts to be larger than the
   * received {@link CSN}
   *
   * @param number
   *          the {@link CSN} to adjust with
   */
  public void adjust(CSN number)
  {
    if (number==null)
    {
      synchronized(this)
      {
        lastTime = TimeThread.getTime();
        seqnum = 0;
      }
      return;
    }

    long rcvdTime = number.getTime();

    int changeServerId = number.getServerId();
    int changeSeqNum = number.getSeqnum();

    /*
     * need to synchronize with newCSN method so that we protect writing
     * lastTime fields
     */
    synchronized(this)
    {
      if (lastTime <= rcvdTime)
      {
        lastTime = ++rcvdTime;
      }

      if (serverId == changeServerId && seqnum < changeSeqNum)
      {
        seqnum = changeSeqNum;
      }
    }
  }

  /**
   * Adjust utility method that takes ServerState as a parameter.
   * @param state the ServerState to adjust with
   */
  public void adjust(ServerState state)
  {
    for (CSN csn : state)
    {
      adjust(csn);
    }
  }
}
