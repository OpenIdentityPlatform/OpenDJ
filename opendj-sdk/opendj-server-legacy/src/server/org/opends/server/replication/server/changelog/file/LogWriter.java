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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.SyncFailedException;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.loggers.MeteredStream;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;

/**
 * A writer on a log file.
 */
class LogWriter extends OutputStream
{
  /** The file to write in. */
  private final File file;

  /** The stream to write data in the file, capable of counting bytes written. */
  private final MeteredStream stream;

  /** The file descriptor on the file. */
  private final FileDescriptor fileDescriptor;

  /**
   * Creates a writer on the provided file.
   *
   * @param file
   *          The file to write.
   * @throws ChangelogException
   *            If a problem occurs at creation.
   */
  public LogWriter(final File file) throws ChangelogException
  {
    this.file = file;
    try
    {
      FileOutputStream fos = new FileOutputStream(file, true);
      this.stream = new MeteredStream(fos, file.length());
      this.fileDescriptor = fos.getFD();
    }
    catch (Exception e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_OPEN_LOG_FILE.get(file.getPath()));
    }
  }

  /**
   * Returns the file used by this writer.
   *
   * @return the file
   */
  File getFile()
  {
    return file;
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

  /**
   * Returns the number of bytes written in the underlying file.
   *
   * @return the number of bytes
   */
  public long getBytesWritten()
  {
    return stream.getBytesWritten();
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
    StaticUtils.close(stream);
  }

}
