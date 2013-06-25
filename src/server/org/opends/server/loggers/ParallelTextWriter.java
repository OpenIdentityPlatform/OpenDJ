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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;



import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.messages.Message;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;



/**
 * A Text Writer which writes log records asynchronously to
 * character-based stream. Note that this implementation is
 * parallel unbound ie there is no queue size cap imposed.
 */
public class ParallelTextWriter
    implements ServerShutdownListener, TextWriter
{
  /**
   * The wrapped Text Writer.
   */
  private final TextWriter writer;

  /** Queue to store unpublished records. */
  private final ConcurrentLinkedQueue<String> queue;

  private final Semaphore queueSemaphore = new Semaphore(0, false);

  private String name;
  private AtomicBoolean stopRequested;
  private WriterThread writerThread;

  private boolean autoFlush;

  /**
   * Construct a new ParallelTextWriter wrapper.
   *
   * @param name      the name of the thread.
   * @param autoFlush indicates if the underlying writer should be flushed
   *                  after the queue is flushed.
   * @param writer    a character stream used for output.
   */
  public ParallelTextWriter(String name, boolean autoFlush, TextWriter writer)
  {
    this.name = name;
    this.autoFlush = autoFlush;
    this.writer = writer;

    this.queue = new ConcurrentLinkedQueue<String>();
    this.writerThread = null;
    this.stopRequested = new AtomicBoolean(false);

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
    @Override
    public void run()
    {
      while (!stopRequested.get())
      {
        try
        {
          if (queueSemaphore.tryAcquire(10, TimeUnit.SECONDS))
          {
            for (int i = (queueSemaphore.drainPermits() + 1); i > 0; i--)
            {
              String message = queue.poll();
              if (message != null)
              {
                writer.writeRecord(message);
              }
              else
              {
                break;
              }
            }
            if (autoFlush)
            {
              flush();
            }
          }
        }
        catch (InterruptedException ex)
        {
          // Ignore. We'll rerun the loop
          // and presumably fall out.
        }
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
      while (!stopRequested.get())
      {
        // Put request on queue for writer
        queue.add(record);
        queueSemaphore.release();
        break;
      }
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
    return "ParallelTextWriter Thread " + name;
  }

  /**
   * {@inheritDoc}
   */
  public void processServerShutdown(Message reason)
  {
    // Don't shutdown the wrapped writer on server shutdown as it
    // might get more write requests before the log publishers are
    // manually shutdown just before the server process exists.
    shutdown(false);
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
    stopRequested.set(true);

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
