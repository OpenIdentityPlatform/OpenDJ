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
package org.opends.server.synchronization.common;

import org.opends.server.util.TimeThread;

/**
 * This class defines a structure that is used for storing the
 * last change numbers generated on this server or received from other servers
 * and generating new changenumbers that are guaranteed to be larger than
 * all the previously seen or generated change numbers.
 */
public class ChangeNumberGenerator
{
  private long lastTime;
  private int seqnum = 0;
  private short serverId;

  /**
   * Create a new ChangeNumber Generator.
   * @param id id to use when creating change numbers
   * @param timestamp time to start with
   */
  public ChangeNumberGenerator(short id, long timestamp)
  {
    lastTime = timestamp;
    serverId = id;
  }

  /**
   * Generate a new ChangeNumber.
   *
   * @return the generated ChangeNUmber
   */
  public ChangeNumber NewChangeNumber()
  {
    /* TODO : we probably don't need a time stamp with a 1 msec accuracy */
    long curTime = TimeThread.getTime();

    synchronized(this)
    {
      if (curTime > lastTime)
      {
        seqnum = 0;
        lastTime = curTime;
      }
      else
      {
        seqnum++;
        if (seqnum > 0xFFFF)
        {
          lastTime++;
          seqnum = 0;
        }
      }
    }

    return new ChangeNumber(lastTime, seqnum, serverId);
  }

  /**
   * Adjust the lastTime and seqnum of this Changenumber generator with
   * a ChangeNumber that we have received from another server.
   * This is necessary because we need that the changenumber generated
   * after processing an update received from other hosts to be larger
   * than the received changenumber
   *
   * @param number the ChangeNumber to adjust with
   */
  public void adjust(ChangeNumber number)
  {
    long rcvdTime = number.getTime();
    int rcvdSeqnum = number.getSeqnum();

    /* need to synchronize with NewChangeNumber method so that we
     * protect writing of seqnum and lastTime fields
     */
    synchronized(this)
    {
      if (lastTime > rcvdTime)
        return;
      if (lastTime == rcvdTime)
      {
        if (seqnum < rcvdSeqnum)
        {
          seqnum = rcvdSeqnum;
        }
        return;
      }
      if (lastTime < rcvdTime)
      {
        lastTime = rcvdTime;
        seqnum = rcvdSeqnum;
      }
    }
  }
}
