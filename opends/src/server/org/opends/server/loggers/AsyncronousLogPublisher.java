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

import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.LogPublisher;
import org.opends.server.core.DirectoryServer;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A LogPublisher which publishes log records asynchronously to a
 * wrapped LogPublisher.  In this way, any LogPublisher may be made to
 * behave asynchronously - i.e. callers of LogPublisher#publish(LogRecord) will
 * not block waiting for the record to be published.  Note that this
 * means that the ErrorHandler will receive logging exceptions asynchronously
 * as well.
 */
public class AsyncronousLogPublisher
    implements ServerShutdownListener, LogPublisher
{
  /** The wrapped LogPublisher. */
  private final LogPublisher publisher;

  /** Queue to store unpublished records. */
  private final ConcurrentLinkedQueue<PublishRequest> queue;

  private String name;
  private int interval;
  private boolean stopRequested;
  private PublisherThread publisherThread;

  /**
   *  Structure encapsulating a queued publish request.
   */
  private static class PublishRequest
  {
    public final LogRecord record;
    public final LoggerErrorHandler handler;

    PublishRequest(LogRecord record, LoggerErrorHandler handler)
    {
      this.record = record;
      this.handler = handler;
    }
  }

  /**
   * Construct a new AsyncronousLogPublisher wrapper.
   *
   * @param name      the name of the thread.
   * @param interval  the interval at which the queue will be flushed.
   * @param publisher the publisher to wrap around.
   */
  public AsyncronousLogPublisher(String name, int interval,
                                 LogPublisher publisher)
  {
    this.name = name;
    this.interval = interval;
    this.publisher = publisher;

    this.queue = new ConcurrentLinkedQueue<PublishRequest>();
    this.publisherThread = null;
    this.stopRequested = false;

    // We will lazily launch the publisherThread
    // to ensure initialization safety.

    DirectoryServer.registerShutdownListener(this);
  }

  /**
   * The publisher thread is responsible for emptying the queue of log records
   * waiting to published.
   */
  private class PublisherThread extends DirectoryThread
  {
    public PublisherThread()
    {
      super(name);
    }
    /**
     * the run method of the publisherThread. Run until queue is empty
     * AND we've been asked to terminate
     */
    public void run()
    {
      while (!isShuttingDown() || !queue.isEmpty()) {
        PublishRequest request= null;
        try {
          request= queue.poll();
          if (request != null) {
            publisher.publish(request.record, request.handler);
          }
          else
          {
            sleep(interval);
          }
        }
        catch (InterruptedException ex) {
          // Ignore. We'll rerun the loop
          // and presumably fall out.
        }
        catch (Throwable t) {
          // Forward exception to error handler
          if (request != null  && request.handler != null) {
            LoggerErrorHandler handler= request.handler;
            handler.handleError(request.record, t);
          }
        }
      }
    }
  }

  // Method needs to be synchronized with _shutdown mutator, as we don't
  // want shutdown to start after we check for it, but before we queue
  // request.
  private synchronized void publishAsynchronously(LogRecord record,
                                                  LoggerErrorHandler handler)
  {
    // If shutting down reject, otherwise publish (if we have a publisher!)
    if (isShuttingDown()) {
      if (handler != null) {
        handler.handleError(record, new Exception("Shutdown requested."));
      }
    }
    else {
      // Launch writer publisherThread if not running. Make sure start is
      // only called ONCE by a publisherThread.
      if (publisherThread == null) {
        publisherThread = new PublisherThread();
        publisherThread.start();
      }
      // Put request on queue for writer
      queue.add(new PublishRequest(record, handler));
    }
  }

  /**
   * Publish the log record asyncronously.
   *
   * @param record the log record to publish.
   * @param handler the error handler to use if an error occurs.
   */
  public void publish(LogRecord record, LoggerErrorHandler handler)
  {
    // No publisher?  Off to the bit bucket.
    if (publisher != null) {
      try {
        // Enqueue record; writer will pick it up later.
        publishAsynchronously(record, handler);
      }
      catch (Throwable t) {
        // Forward a logging exception to the error handler
        handler.handleError(record, t);
      }
    }
  }

  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  public String getShutdownListenerName()
  {
    return "AsyncronousLogPublisher Thread " + name;
  }

  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this shutdown listener should take any action necessary to prepare
   * for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public void processServerShutdown(String reason)
  {
    shutdown(true);
  }

  /**
   * Queries whether the publisher is in shutdown mode.
   */
  private synchronized boolean isShuttingDown()
  {
    return stopRequested;
  }

  /**
   * Tell the publisher to start shutting down.
   */
  private synchronized void startShutDown()
  {
    stopRequested = true;
  }

  /**
   * Shutdown the publisher.
   */
  public void shutdown()
  {
    shutdown(true);
    DirectoryServer.deregisterShutdownListener(this);
  }

  private void shutdown(boolean shutdownWrapped)
  {
    startShutDown();

    // Wait for publisher thread to terminate
    while (publisherThread != null && publisherThread.isAlive()) {
      try {
        publisherThread.join();
      }
      catch (InterruptedException ex) {
        // Ignore; we gotta wait..
      }
    }

    // The writer publisherThread SHOULD have drained the queue.
    // If not, handle outstanding requests ourselves,
    // indicating the request was not processed due to shutdown.
    while (!queue.isEmpty()) {
      PublishRequest request= queue.poll();
      if (request != null) {
        if (request.handler != null) {
          request.handler.handleError(request.record,
                                      new Exception("Shutdown requested."));
        }
      }
    }

    // Shutdown the wrapped publisher.
    if (shutdownWrapped && publisher != null) publisher.shutdown();
  }
}
