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

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.*;

/**
 * A LoggerConfiguration encapsulates the information defining an
 * abstract log messaging system.  A LoggerConfiguration maintains at
 * least three things:
 * <ul>
 * <li>a destination to send log messages to (a LogPublisher).</li>
 * <li>an optional filter used to restrict the log messages sent
 * (a RecordFilter).</li>
 * <li>an error handler to be notified in the case of any logging
 * exceptions (a LoggerErrorHandler).</li>
 * </ul>
 *
 * A Logger will use this information to initialize the log messaging
 * system.  Additionally, a Logger will register with the LoggerConfiguration
 * object it used to allow the LoggerConfiguration to provide a single
 * point of configuration management.  On configuration changes, registered
 * Loggers will be notified.
 *
 */
public class LoggerConfiguration {

  /**
   * Whether the debug logger is enabled or disabled.
   */
  protected boolean enabled;

  /** The log destination for this configuration. */
  protected CopyOnWriteArrayList<LogPublisher> publishers;

  /**
   * A mutex that will be used to provide threadsafe access to methods
   * changing the set of defined publishers.
   */
  protected ReentrantLock publisherMutex;

  /**
   * The logging error handler.
   */
  protected LoggerErrorHandler handler;

  /** The record filter for this configuration. */
  //protected RecordFilter _filter;

  /** The loggers that need notification of configuration changes. */
  protected Set<Logger> loggers;

  /**
   * Creates a LoggerConfiguration describing an disabled logging system.
   *
   * @param handler the error handler to use for the logger configured by this
   *                configuration.
   */
  public LoggerConfiguration(LoggerErrorHandler handler)
  {
    this.enabled = false;
    this.publishers = new CopyOnWriteArrayList<LogPublisher>();
    this.publisherMutex = new ReentrantLock();
    this.handler = handler;
    this.loggers = new HashSet<Logger>();
  }

  /**
   * Enable or disable the debug logger.
   *
   * @param enable if the debug logger should be enabled.
   */
  public void setEnabled(boolean enable)
  {
    this.enabled = enable;
  }

  /**
   * Obtain the status of this logger singleton.
   *
   * @return the status of this logger.
   */
  public boolean getEnabled()
  {
    return enabled;
  }

  /**
   * Adds a new publisher to which log records should be sent.
   *
   * @param publisher The publisher to which records should be sent.
   */
  public void addPublisher(LogPublisher publisher)
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
  public void removePublisher(LogPublisher publisher)
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
  public void removeAllPublishers(boolean closePublishers)
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

  /**
   * Retrieves the set of publishers included in this configuration.
   *
   * @return the set of publishers included in this configuration.
   */
  public List<LogPublisher> getPublishers()
  {
    return Collections.unmodifiableList(publishers);
  }

  /**
   * Retrieves the error handler included in this configuration.
   *
   * @return the error handler used by this configuration.
   */
  public LoggerErrorHandler getErrorHandler()
  {
    return handler;
  }

  /**
   * Set an error handler for this configuration.
   *
   * @param handler the error handler to set for this configuration.
   */
  public void setErrorHandler(LoggerErrorHandler handler)
  {
    this.handler= handler;
    notifyLoggers();
  }

  /**
   * Request that a logger be notified of configuration changes.
   *
   * @param logger - The Logger interested in configuration change
   * notifications.
   */
  public synchronized void registerLogger(Logger logger)
  {
    loggers.add(logger);
  }

  /**
   * Request that a logger no longer be notifed of configuration changes.
   *
   * @param logger - The Logger no longer interested in configuration change
   * notifications.
   */
  public synchronized void deregisterLogger(Logger logger)
  {
    loggers.remove(logger);
  }

  /**
   * Notify all registered loggers that the configuration has changed.
   */
  protected synchronized void notifyLoggers()
  {
    for(Logger logger : loggers)
    {
      logger.updateConfiguration(this);
    }
  }
}
