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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.replication.server;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import java.util.TreeMap;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;

import static org.opends.messages.ReplicationMessages.*;

/**
 * This class is used to build ordered lists of UpdateMsg.
 * The order is defined by the order of the CSN of the UpdateMsg.
 * @ThreadSafe
 */
public class MsgQueue
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private TreeMap<CSN, UpdateMsg> map = new TreeMap<>();
  /**
   * FIXME JNR to be investigated:
   * I strongly suspect that we could replace this field
   * by using the synchronized keyword on each method.
   * However, MessageHandler is weirdly synchronizing on msgQueue field
   * even though it is touching the lateQueue field (?!?).
   */
  private final Object lock = new Object();

  /** The total number of bytes for all the message in the queue. */
  private int bytesCount;

  /**
   * Return the first UpdateMsg in the MsgQueue.
   *
   * @return The first UpdateMsg in the MsgQueue.
   */
  public UpdateMsg first()
  {
    synchronized (lock)
    {
      return map.get(map.firstKey());
    }
  }

  /**
   * Returns the number of elements in this MsgQueue.
   *
   * @return The number of elements in this MsgQueue.
   */
  public int count()
  {
    synchronized (lock)
    {
      return map.size();
    }
  }

  /**
   * Returns the number of bytes in this MsgQueue.
   *
   * @return The number of bytes in this MsgQueue.
   */
  public int bytesCount()
  {
    synchronized (lock)
    {
      return bytesCount;
    }
  }

  /**
   * Returns <tt>true</tt> if this MsgQueue contains no UpdateMsg.
   *
   * @return <tt>true</tt> if this MsgQueue contains no UpdateMsg.
   */
  public boolean isEmpty()
  {
    synchronized (lock)
    {
      return map.isEmpty();
    }
  }


  /**
   * Add an UpdateMsg to this MessageQueue.
   *
   * @param update The UpdateMsg to add to this MessageQueue.
   */
  public void add(UpdateMsg update)
  {
    synchronized (lock)
    {
      final UpdateMsg msgSameCSN = map.put(update.getCSN(), update);
      if (msgSameCSN != null)
      {
        try
        {
          if (msgSameCSN.getBytes().length != update.getBytes().length
              || msgSameCSN.isAssured() != update.isAssured()
              || msgSameCSN.getVersion() != update.getVersion())
          {
            // Adding 2 msgs with the same CSN is ok only when
            // the 2 msgs are the same
            bytesCount += update.size() - msgSameCSN.size();
            logger.error(ERR_RSQUEUE_DIFFERENT_MSGS_WITH_SAME_CN, msgSameCSN.getCSN(), msgSameCSN, update);
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
      else
      {
        // it is really an ADD
        bytesCount += update.size();
      }
    }
  }

  /**
   * Get and remove the first UpdateMsg in this MessageQueue.
   *
   * @return The first UpdateMsg in this MessageQueue.
   */
  public UpdateMsg removeFirst()
  {
    synchronized (lock)
    {
      // FIXME JNR replace next 2 lines with just that one:
      // final UpdateMsg update = map.pollFirstEntry().getValue();
      final UpdateMsg update = map.get(map.firstKey());
      map.remove(update.getCSN());
      bytesCount -= update.size();
      if (map.isEmpty() && bytesCount != 0)
      {
        // should never happen
        logger.error(ERR_BYTE_COUNT, bytesCount);
        bytesCount = 0;
      }
      return update;
    }
  }

  /**
   * Returns <tt>true</tt> if this map contains an UpdateMsg
   * with the same CSN as the given UpdateMsg.
   *
   * @param msg UpdateMsg whose presence in this queue is to be tested.
   *
   * @return <tt>true</tt> if this map contains an UpdateMsg
   *         with the same CSN as the given UpdateMsg.
   *
   */
  public boolean contains(UpdateMsg msg)
  {
    synchronized (lock)
    {
      return map.containsKey(msg.getCSN());
    }
  }

  /**
   * Removes all UpdateMsg form this queue.
   */
  public void clear()
  {
    synchronized (lock)
    {
      map.clear();
      bytesCount = 0;
    }
  }

  /**
   * Consumes all the messages in this queue up to and including the passed in
   * message. If the passed in message is not contained in the current queue,
   * then all messages will be removed from it.
   *
   * @param finalMsg
   *          the final message to reach when consuming messages from this queue
   */
  public void consumeUpTo(UpdateMsg finalMsg)
  {
    // FIXME this code could be more efficient if the msgQueue could call the
    // following code (to be tested):
    // if (!map.containsKey(finalMsg.getCSN())) {
    // map.clear();
    // } else {
    // map.headMap(finalMsg.getCSN(), true).clear();
    // }

    final CSN finalCSN = finalMsg.getCSN();
    UpdateMsg msg;
    do
    {
      msg = removeFirst();
    }
    while (!finalCSN.equals(msg.getCSN()));
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " bytesCount=" + bytesCount + " queue="
        + map.values();
  }
}
