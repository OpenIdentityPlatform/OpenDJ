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
import org.opends.server.core.DirectoryServer;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A Text Writer which writes log records asynchronously to
 * character-based stream.
 */
public class AsyncronousTextWriter
    implements ServerShutdownListener, TextWriter
{
  /**
   * The wrapped Text Writer.
   */
  private final TextWriter writer;

  /** Queue to store unpublished records. */
  private final LinkedBlockingQueue<String> queue;

  private String name;
  private boolean stopRequested;
  private WriterThread writerThread;

  private boolean autoFlush;

  /**
   * Construct a new AsyncronousTextWriter wrapper.
   *
   * @param name      the name of the thread.
   * @param capacity      the size of the queue before it gets flushed.
   * @param autoFlush indicates if the underlying writer should be flushed
   *                  after the queue is flushed.
   * @param writer    a character stream used for output.
   */
  public AsyncronousTextWriter(String name, int capacity, boolean autoFlush,
                               TextWriter writer)
  {
    this.name = name;
    this.autoFlush = autoFlush;
    this.writer = writer;

    this.queue = new LinkedBlockingQueue<String>(capacity);
    this.writerThread = null;
    this.stopRequested = false;

    writerThread = new WriterThread();
    writerThread.start();

    DirectoryServer.registerShutdownListener(this);
  }

  /**
   * The publisher thread is responsible for emptying the queue of log records
   * waiting to published.
   */
  private class WriterThread extends DirectoryThread
  {
    public WriterThread()
    {
      super(name);
    }
    /**
     * the run method of the writerThread. Run until queue is empty
     * AND we've been asked to terminate
     */
    public void run()
    {
      String message = null;
      while (!isShuttingDown() || !queue.isEmpty()) {
        try
        {
          message = queue.poll(10, TimeUnit.SECONDS);
          if(message != null)
          {
            do
            {
              writer.writeRecord(message);
              message = queue.poll();
            }
            while(message != null);

            if(autoFlush)
            {
              flush();
            }
          }
        }
        catch (InterruptedException ex) {
          // Ignore. We'll rerun the loop
          // and presumably fall out.
        }
      }
    }
  }

  // Method needs to be synchronized with _shutdown mutator, as we don't
  // want shutdown to start after we check for it, but before we queue
  // request.
  private synchronized void writeAsynchronously(String record)
  {
    // If shutting down reject, otherwise publish (if we have a publisher!)
    while (!isShuttingDown())
    {
      // Put request on queue for writer
      try
      {
        queue.put(record);
        break;
      }
      catch(InterruptedException e)
      {
        // We expect this to happen. Just ignore it and hopefully
        // drop out in the next try.
      }
    }
  }

  /**
   * Write the log record asyncronously.
   *
   * @param record the log record to write.
   */
  public void writeRecord(String record)
  {
    // No writer?  Off to the bit bucket.
    if (writer != null) {
      writeAsynchronously(record);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void flush()
  {
    writer.flush();
  }

  /**
   * {@inheritDoc}
   */
  public long getBytesWritten()
  {
    return writer.getBytesWritten();
  }

  /**
   * Retrieves the wrapped writer.
   *
   * @return The wrapped writer used by this asyncronous writer.
   */
  public TextWriter getWrappedWriter()
  {
    return writer;
  }

  /**
   * {@inheritDoc}
   */
  public String getShutdownListenerName()
  {
    return "AsyncronousTextWriter Thread " + name;
  }

  /**
   * {@inheritDoc}
   */
  public void processServerShutdown(String reason)
  {
    // Don't shutdown the wrapped writer on server shutdown as it
    // might get more write requests before the log publishers are
    // manually shutdown just before the server process exists.
    shutdown(false);
  }

  /**
   * Queries whether the publisher is in shutdown mode.
   */
  private boolean isShuttingDown()
  {
    return stopRequested;
  }

  /**
   * {@inheritDoc}
   */
  public void shutdown()
  {
    shutdown(true);
  }

  /**
   * Releases any resources held by the writer.
   *
   * @param shutdownWrapped If the wrapped writer should be closed as well.
   */
  public void shutdown(boolean shutdownWrapped)
  {
    stopRequested = true;

    // Wait for publisher thread to terminate
    while (writerThread != null && writerThread.isAlive()) {
      try {
        // Interrupt the thread if its blocking
        writerThread.interrupt();
        writerThread.join();
      }
      catch (InterruptedException ex) {
        // Ignore; we gotta wait..
      }
    }

    // The writer writerThread SHOULD have drained the queue.
    // If not, handle outstanding requests ourselves,
    // and push them to the writer.
    while (!queue.isEmpty()) {
      String message = queue.poll();
      writer.writeRecord(message);
    }

    // Shutdown the wrapped writer.
    if (shutdownWrapped && writer != null) writer.shutdown();

    DirectoryServer.deregisterShutdownListener(this);
  }

  /**
   * Set the auto flush setting for this writer.
   *
   * @param autoFlush If the writer should flush the buffer after every line.
   */
  public void setAutoFlush(boolean autoFlush)
  {
    this.autoFlush = autoFlush;
  }
}
