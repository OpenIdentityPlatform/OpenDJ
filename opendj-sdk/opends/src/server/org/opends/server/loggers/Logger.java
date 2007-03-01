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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import org.opends.server.api.LogPublisher;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 *  A Logger is the entry point into a message distribution
 * system.  The Logger receives messages from an external source, optionally
 * filters out undesired messages using a RecordFilter, and
 * sends them to a LogPublisher for further distribution.
 * Any logging exceptions encountered will be sent to a
 * LoggingErrorHandler.
 */
public abstract class Logger
{
  /**
   * The logging error handler.
   */
  protected LogErrorHandler handler;

  /**
   * The set of publishers.
   */
  protected CopyOnWriteArrayList<LogPublisher> publishers;

  /**
   * A mutex that will be used to provide threadsafe access to methods
   * changing the set of defined publishers.
   */
  protected ReentrantLock publisherMutex;

  /**
   * Construct a new logger object.
   *
   * @param handler the error handler to use when an error occurs.
   */
  protected Logger(LogErrorHandler handler)
  {
    this.publishers = new CopyOnWriteArrayList<LogPublisher>();
    this.publisherMutex = new ReentrantLock();

    this.handler = handler;
  }

  /**
   * Publish a record to all the registered publishers.
   *
   * @param record The log record to publish.
   */
  protected void publishRecord(LogRecord record)
  {
    for(LogPublisher p : publishers)
    {
      p.publish(record, handler);
    }
  }

  /**
   * Adds a new publisher to which log records should be sent.
   *
   * @param publisher The publisher to which records should be sent.
   */
  protected void addPublisher(LogPublisher publisher)
  {
    publisherMutex.lock();

    try
    {
      for (LogPublisher p : publishers)
      {
        if (p.equals(publisher))
        {
          return;
        }
      }

      publishers.add(publisher);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      publisherMutex.unlock();
    }
  }

  /**
   * Removes the provided publisher so records will no longer be sent to it.
   *
   * @param publisher The publisher to remove.
   */
  protected void removePublisher(LogPublisher publisher)
  {
      publisherMutex.lock();

    try
    {
      publishers.remove(publisher);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      publisherMutex.unlock();
    }
  }

  /**
   * Removes all publishers so records are not sent anywhere.
   *
   * @param closePublishers whether to close the publishers when removing them.
   */
  protected void removeAllPublishers(boolean closePublishers)
  {
    publisherMutex.lock();

    try
    {
      if(closePublishers)
      {
        LogPublisher[] pubs = new LogPublisher[publishers.size()];
        publishers.toArray(pubs);

        publishers.clear();

        for(LogPublisher pub : pubs)
        {
          pub.shutdown();
        }
      }
          else
    {
      publishers.clear();
    }
    }
    catch(Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      publisherMutex.unlock();
    }
  }
}
