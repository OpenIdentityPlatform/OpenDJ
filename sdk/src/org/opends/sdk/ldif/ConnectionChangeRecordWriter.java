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



import java.io.IOException;
import java.io.InterruptedIOException;

import org.opends.sdk.Connection;
import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ErrorResultIOException;
import org.opends.sdk.requests.AddRequest;
import org.opends.sdk.requests.DeleteRequest;
import org.opends.sdk.requests.ModifyDNRequest;
import org.opends.sdk.requests.ModifyRequest;
import org.opends.sdk.util.Validator;



/**
 * A {@code ConnectionChangeRecordWriter} is a bridge from {@code
 * Connection}s to {@code ChangeRecordWriter}s. A connection change
 * record writer writes change records by sending appropriate update
 * requests (Add, Delete, Modify, or ModifyDN) to an underlying
 * connection.
 * <p>
 * All update requests are performed synchronously, blocking until an
 * update result is received. If an update result indicates that an
 * update request has failed for some reason then the error result is
 * propagated to the caller using an {@code ErrorResultIOException}.
 * <p>
 * <b>Note:</b> comments are not supported by connection change record
 * writers. Attempts to write comments will be ignored.
 */
public final class ConnectionChangeRecordWriter implements
    ChangeRecordWriter
{
  private final Connection connection;



  /**
   * Creates a new connection change record writer whose destination is
   * the provided connection.
   *
   * @param connection
   *          The connection to use.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public ConnectionChangeRecordWriter(Connection connection)
      throws NullPointerException
  {
    Validator.ensureNotNull(connection);
    this.connection = connection;
  }



  /**
   * Closes this connection change record writer, including the
   * underlying connection. Closing a previously closed change record
   * writer has no effect.
   */
  public void close()
  {
    connection.close();
  }



  /**
   * Connection change record writers do not require flushing, so this
   * method has no effect.
   */
  public void flush()
  {
    // Do nothing.
  }



  /**
   * Writes the provided Add request to the underlying connection,
   * blocking until the request completes.
   *
   * @param change
   *          The {@code AddRequest} to be written.
   * @return A reference to this connection change record writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeChangeRecord(
      AddRequest change) throws ErrorResultIOException,
      InterruptedIOException, NullPointerException
  {
    Validator.ensureNotNull(change);
    try
    {
      connection.add(change);
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



  /**
   * Writes the provided change record to the underlying connection,
   * blocking until the request completes.
   *
   * @param change
   *          The change record to be written.
   * @return A reference to this connection change record writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeChangeRecord(
      ChangeRecord change) throws ErrorResultIOException,
      InterruptedIOException, NullPointerException
  {
    Validator.ensureNotNull(change);

    final IOException e = change.accept(ChangeRecordVisitorWriter
        .getInstance(), this);
    try
    {
      if (e != null)
      {
        throw e;
      }
    }
    catch (final ErrorResultIOException e1)
    {
      throw e1;
    }
    catch (InterruptedIOException e1)
    {
      throw e1;
    }
    catch (final IOException e1)
    {
      // Should not happen.
      throw new RuntimeException(e1);
    }
    return this;
  }



  /**
   * Writes the provided Delete request to the underlying connection,
   * blocking until the request completes.
   *
   * @param change
   *          The {@code DeleteRequest} to be written.
   * @return A reference to this connection change record writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeChangeRecord(
      DeleteRequest change) throws ErrorResultIOException,
      InterruptedIOException, NullPointerException
  {
    Validator.ensureNotNull(change);
    try
    {
      connection.delete(change);
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



  /**
   * Writes the provided ModifyDN request to the underlying connection,
   * blocking until the request completes.
   *
   * @param change
   *          The {@code ModifyDNRequest} to be written.
   * @return A reference to this connection change record writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeChangeRecord(
      ModifyDNRequest change) throws ErrorResultIOException,
      InterruptedIOException, NullPointerException
  {
    Validator.ensureNotNull(change);
    try
    {
      connection.modifyDN(change);
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



  /**
   * Writes the provided Modify request to the underlying connection,
   * blocking until the request completes.
   *
   * @param change
   *          The {@code ModifyRequest} to be written.
   * @return A reference to this connection change record writer.
   * @throws ErrorResultIOException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedIOException
   *           If the current thread was interrupted while waiting.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeChangeRecord(
      ModifyRequest change) throws ErrorResultIOException,
      InterruptedIOException, NullPointerException
  {
    Validator.ensureNotNull(change);
    try
    {
      connection.modify(change);
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



  /**
   * Connection change record writers do not support comments, so the
   * provided comment will be ignored.
   *
   * @param comment
   *          The {@code CharSequence} to be written as a comment.
   * @return A reference to this connection change record writer.
   * @throws NullPointerException
   *           If {@code comment} was {@code null}.
   */
  public ConnectionChangeRecordWriter writeComment(CharSequence comment)
  {
    Validator.ensureNotNull(comment);

    // Do nothing.
    return this;
  }

}
