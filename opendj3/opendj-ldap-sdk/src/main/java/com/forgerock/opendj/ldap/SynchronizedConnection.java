/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap;



import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.monitoring.MonitoringConfig;



/**
 * A Grizzly connection which synchronizes write requests in order to workaround
 * issue GRIZZLY-1191 (http://java.net/jira/browse/GRIZZLY-1191). See OPENDJ-422
 * (https://bugster.forgerock.org/jira/browse/OPENDJ-422) for more information.
 * <p>
 * This class should be removed once we issue GRIZZLY-422 is resolved and/or we
 * move to non-blocking IO (requires Grizzly 2.2).
 */
final class SynchronizedConnection<L> implements Connection<L>
{
  private final Connection<L> connection;
  private final Object writeLock = new Object();



  /**
   * Returns a synchronized view of the provided Grizzly connection.
   *
   * @param connection
   *          The Grizzly connection to be synchronized.
   * @return The synchronized view of the provided Grizzly connection.
   */
  static <L> SynchronizedConnection<L> synchronizeConnection(
      Connection<L> connection)
  {
    if (connection instanceof SynchronizedConnection)
    {
      return (SynchronizedConnection<L>) connection;
    }
    else
    {
      return new SynchronizedConnection<L>(connection);
    }
  }



  private SynchronizedConnection(Connection<L> connection)
  {
    this.connection = connection;
  }



  /**
   * Returns the underlying unsynchronized connection.
   *
   * @return The underlying unsynchronized connection.
   */
  Connection<L> getUnsynchronizedConnection()
  {
    return connection;
  }



  public <M> GrizzlyFuture<WriteResult<M, L>> write(M message)
      throws IOException
  {
    synchronized (writeLock)
    {
      return connection.write(message);
    }
  }



  public <M> GrizzlyFuture<ReadResult<M, L>> read() throws IOException
  {
    return connection.read();
  }



  public AttributeHolder getAttributes()
  {
    return connection.getAttributes();
  }



  public <M> GrizzlyFuture<ReadResult<M, L>> read(
      CompletionHandler<ReadResult<M, L>> completionHandler) throws IOException
  {
    return connection.read(completionHandler);
  }



  public Transport getTransport()
  {
    return connection.getTransport();
  }



  public <M> GrizzlyFuture<WriteResult<M, L>> write(M message,
      CompletionHandler<WriteResult<M, L>> completionHandler)
      throws IOException
  {
    synchronized (writeLock)
    {
      return connection.write(message, completionHandler);
    }
  }



  public boolean isOpen()
  {
    return connection.isOpen();
  }



  public void configureBlocking(boolean isBlocking)
  {
    connection.configureBlocking(isBlocking);
  }



  public <M> GrizzlyFuture<WriteResult<M, L>> write(L dstAddress, M message,
      CompletionHandler<WriteResult<M, L>> completionHandler)
      throws IOException
  {
    synchronized (writeLock)
    {
      return connection.write(dstAddress, message, completionHandler);
    }
  }



  public boolean isBlocking()
  {
    return connection.isBlocking();
  }



  public void configureStandalone(boolean isStandalone)
  {
    connection.configureStandalone(isStandalone);
  }



  public boolean isStandalone()
  {
    return connection.isStandalone();
  }



  @SuppressWarnings("rawtypes")
  public Processor obtainProcessor(IOEvent ioEvent)
  {
    return connection.obtainProcessor(ioEvent);
  }



  @SuppressWarnings("rawtypes")
  public Processor getProcessor()
  {
    return connection.getProcessor();
  }



  @SuppressWarnings("rawtypes")
  public void setProcessor(Processor preferableProcessor)
  {
    connection.setProcessor(preferableProcessor);
  }



  public ProcessorSelector getProcessorSelector()
  {
    return connection.getProcessorSelector();
  }



  public void setProcessorSelector(ProcessorSelector preferableProcessorSelector)
  {
    connection.setProcessorSelector(preferableProcessorSelector);
  }



  public L getPeerAddress()
  {
    return connection.getPeerAddress();
  }



  public L getLocalAddress()
  {
    return connection.getLocalAddress();
  }



  @SuppressWarnings("rawtypes")
  public GrizzlyFuture<Connection> close() throws IOException
  {
    synchronized (writeLock)
    {
      return connection.close();
    }
  }



  @SuppressWarnings("rawtypes")
  public GrizzlyFuture<Connection> close(
      CompletionHandler<Connection> completionHandler) throws IOException
  {
    synchronized (writeLock)
    {
      return connection.close(completionHandler);
    }
  }



  public int getReadBufferSize()
  {
    return connection.getReadBufferSize();
  }



  public void setReadBufferSize(int readBufferSize)
  {
    connection.setReadBufferSize(readBufferSize);
  }



  public int getWriteBufferSize()
  {
    return connection.getWriteBufferSize();
  }



  public void setWriteBufferSize(int writeBufferSize)
  {
    connection.setWriteBufferSize(writeBufferSize);
  }



  public long getReadTimeout(TimeUnit timeUnit)
  {
    return connection.getReadTimeout(timeUnit);
  }



  public void setReadTimeout(long timeout, TimeUnit timeUnit)
  {
    connection.setReadTimeout(timeout, timeUnit);
  }



  public long getWriteTimeout(TimeUnit timeUnit)
  {
    return connection.getWriteTimeout(timeUnit);
  }



  public void setWriteTimeout(long timeout, TimeUnit timeUnit)
  {
    connection.setWriteTimeout(timeout, timeUnit);
  }



  public void enableIOEvent(IOEvent ioEvent) throws IOException
  {
    connection.enableIOEvent(ioEvent);
  }



  public void disableIOEvent(IOEvent ioEvent) throws IOException
  {
    connection.disableIOEvent(ioEvent);
  }



  public MonitoringConfig<ConnectionProbe> getMonitoringConfig()
  {
    return connection.getMonitoringConfig();
  }



  public void addCloseListener(
      org.glassfish.grizzly.Connection.CloseListener closeListener)
  {
    connection.addCloseListener(closeListener);
  }



  public boolean removeCloseListener(
      org.glassfish.grizzly.Connection.CloseListener closeListener)
  {
    return connection.removeCloseListener(closeListener);
  }



  public void notifyConnectionError(Throwable error)
  {
    connection.notifyConnectionError(error);
  }



  public String toString()
  {
    return connection.toString();
  }

}
