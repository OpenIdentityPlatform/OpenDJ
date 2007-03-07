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
package org.opends.server.loggers;

import org.opends.server.api.LogPublisher;

import java.util.List;

/**
 *  A Logger is the entry point into a message distribution
 * system.  The Logger receives messages from an external source, optionally
 * filters out undesired messages using a RecordFilter, and
 * sends them to a LogPublisher for further distribution.
 * Any logging exceptions encountered will be sent to a
 * LoggerErrorHandler.
 */
public abstract class Logger
{
 /**
   * Whether the debug logger is enabled or disabled.
   */
  protected boolean enabled;

  /**
   * The logging error handler.
   */
  protected LoggerErrorHandler handler;

  /**
   * The set of publishers.
   */
  protected List<LogPublisher> publishers;

  /**
   * Construct a new logger object.
   *
   * @param config the logger configuration to use when construting the new
   *               logger object.
   */
  protected Logger(LoggerConfiguration config)
  {
    this.enabled = config.getEnabled();
    this.publishers = config.getPublishers();
    this.handler = config.getErrorHandler();
  }

  /**
   * Publish a record to all the registered publishers.
   *
   * @param record The log record to publish.
   */
  public void publishRecord(LogRecord record)
  {
    for(LogPublisher p : publishers)
    {
      p.publish(record, handler);
    }
  }

  /**
   * Update this logger with the provided configuration.
   *
   * @param config the new configuration to use for this logger.
   */
  protected void updateConfiguration(LoggerConfiguration config)
  {
    boolean newEnabled = config.getEnabled();
    if(enabled && !newEnabled)
    {
      //it is now disabled. Close all publishers if any.
      for(LogPublisher publisher : publishers)
      {
        publisher.shutdown();
        publishers.remove(publisher);
      }
    }

    if(newEnabled)
    {
      List<LogPublisher> newPublishers = config.getPublishers();
      for(LogPublisher oldPublisher : publishers)
      {
        if(!newPublishers.contains(oldPublisher))
        {
          //A publisher was removed. Make sure to close it before removing it.
          oldPublisher.shutdown();
        }
      }
      this.publishers = config.getPublishers();
      this.handler = config.getErrorHandler();
    }
  }
}
