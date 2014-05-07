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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.opends.server.loggers.debug.DebugLogger.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.util.HashMap;
import java.util.Map;

import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.ByteString;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;

/**
 * A writer on a log file.
 * <p>
 * The writer is cached in order to have a single writer per file in the JVM.
 */
class LogWriter extends OutputStream
{
  /** The cache of log writers. There is a single writer per file in the JVM.  */
  private static final Map<File, LogWriter> logWritersCache = new HashMap<File, LogWriter>();

  /** The exclusive lock used to acquire or close a log writer. */
  private static final Object lock = new Object();

  /** The file to write in. */
  private final File file;

  /** The stream to write data in the file. */
  private final BufferedOutputStream stream;

  /** The file descriptor on the file. */
  private final FileDescriptor fileDescriptor;

  /** The number of references on this writer. */
  private int referenceCount;

  /**
   * Creates a writer on the provided file.
   *
   * @param file
   *          The file to write.
   * @param stream
   *          The stream to write in the file.
   * @param fileDescriptor
   *          The descriptor on the file.
   */
  private LogWriter(final File file, BufferedOutputStream stream, FileDescriptor fileDescriptor)
      throws ChangelogException
  {
    this.file = file;
    this.stream = stream;
    this.fileDescriptor = fileDescriptor;
    this.referenceCount = 1;
  }

  /**
   * Returns a log writer on the provided file, creating it if necessary.
   *
   * @param file
   *            The log file to write in.
   * @return the log writer
   * @throws ChangelogException
   *            If a problem occurs.
   */
  public static LogWriter acquireWriter(File file) throws ChangelogException
  {
    synchronized (lock)
    {
      LogWriter logWriter = logWritersCache.get(file);
      if (logWriter == null)
      {
        try
        {
          final FileOutputStream stream = new FileOutputStream(file, true);
          logWriter = new LogWriter(file, new BufferedOutputStream(stream), stream.getFD());
        }
        catch (Exception e)
        {
          throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_OPEN_LOG_FILE.get(file.getPath()));
        }
        logWritersCache.put(file, logWriter);
      }
      else
      {
        logWriter.incrementRefCounter();
      }
      return logWriter;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void write(int b) throws IOException
  {
    stream.write(b);
  }

  /** {@inheritDoc} */
  @Override
  public void write(byte[] b) throws IOException
  {
    stream.write(b);
  }

  /** {@inheritDoc} */
  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    stream.write(b, off, len);
  }

  /**
   * Writes the provided byte string to the underlying output stream of this writer.
   *
   * @param bs
   *            The byte string to write.
   * @throws IOException
   *           if an I/O error occurs. In particular, an IOException may be
   *           thrown if the output stream has been closed.
   */
  public void write(ByteString bs) throws IOException
  {
    bs.copyTo(stream);
  }

  /** {@inheritDoc} */
  @Override
  public void flush() throws IOException
  {
    stream.flush();
  }

  /**
   * Synchronize all modifications to the file to the underlying device.
   *
   * @throws SyncFailedException
   *            If synchronization fails.
   */
  void sync() throws SyncFailedException {
    fileDescriptor.sync();
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    synchronized (lock)
    {
      LogWriter writer = logWritersCache.get(file);
      if (writer == null)
      {
        // writer is already closed
        return;
      }
      // counter == 0 should never happen
      if (referenceCount == 0 || referenceCount == 1)
      {
        StaticUtils.close(stream);
        logWritersCache.remove(file);
        referenceCount = 0;
      }
      else
      {
        referenceCount--;
      }
    }
  }

  private void incrementRefCounter()
  {
    referenceCount++;
  }


}
