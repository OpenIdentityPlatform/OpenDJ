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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldif;



import java.io.InterruptedIOException;

import org.opends.sdk.Connection;
import org.opends.sdk.Entry;
import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ErrorResultIOException;
import org.opends.sdk.util.Validator;



/**
 * A {@code ConnectionEntryWriter} is a bridge from {@code Connection}s
 * to {@code EntryWriter}s. A connection entry writer writes entries by
 * sending Add requests to an underlying connection.
 * <p>
 * All Add requests are performed synchronously, blocking until an Add
 * result is received. If an Add result indicates that an Add request
 * has failed for some reason then the error result is propagated to the
 * caller using an {@code ErrorResultIOException}.
 * <p>
 * <b>Note:</b> comments are not supported by connection change record
 * writers. Attempts to write comments will be ignored.
 */
public final class ConnectionEntryWriter implements EntryWriter
{
  private final Connection connection;



  /**
   * Creates a new connection entry writer whose destination is the
   * provided connection.
   *
   * @param connection
   *          The connection to use.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public ConnectionEntryWriter(Connection connection)
      throws NullPointerException
  {
    Validator.ensureNotNull(connection);
    this.connection = connection;
  }



  /**
   * Closes this connection entry writer, including the underlying
   * connection. Closing a previously closed entry writer has no effect.
   */
  public void close()
  {
    connection.close();
  }



  /**
   * Connection entry writers do not require flushing, so this method
   * has no effect.
   */
  public void flush()
  {
    // Do nothing.
  }



  /**
   * Connection entry writers do not support comments, so the provided
   * comment will be ignored.
   *
   * @param comment
   *          The {@code CharSequence} to be written as a comment.
   * @return A reference to this connection entry writer.
   * @throws NullPointerException
   *           If {@code comment} was {@code null}.
   */
  public ConnectionEntryWriter writeComment(CharSequence comment)
  {
    Validator.ensureNotNull(comment);

    // Do nothing.
    return this;
  }



  /**
   * Writes an entry to the underlying connection using an Add request,
   * blocking until the request completes.
   *
   * @param entry
   *          The {@code Entry} to be written.
   * @return A reference to this connection entry writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code entry} was {@code null}.
   */
  public ConnectionEntryWriter writeEntry(Entry entry)
      throws ErrorResultIOException, InterruptedIOException,
      NullPointerException
  {
    Validator.ensureNotNull(entry);
    try
    {
      connection.add(entry);
    }
    catch (final ErrorResultException e)
    {
      throw new ErrorResultIOException(e);
    }
    catch (InterruptedException e)
    {
      throw new InterruptedIOException(e.getMessage());
    }
    return this;
  }

}
