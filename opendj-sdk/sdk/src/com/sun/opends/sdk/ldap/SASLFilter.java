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

package com.sun.opends.sdk.ldap;



import java.io.IOException;

import javax.security.sasl.SaslException;

import org.opends.sdk.sasl.SASLContext;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.StreamTransformerFilter;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.threadpool.WorkerThread;



/**
 * SASL filter adapter.
 */
public final class SASLFilter extends FilterAdapter implements
    StreamTransformerFilter
{
  private static SASLFilter SINGLETON = new SASLFilter();

  private static final String SASL_CONTEXT_ATTR_NAME = "SASLContextAttr";

  private static final String SASL_INCOMING_BUFFER_NAME = "SASLIncomingBufferAttr";

  private static final String SASL_OUTGOING_BUFFER_NAME = "SASLOutgoingBufferAttr";



  public static SASLFilter getInstance(SASLContext saslContext,
      Connection<?> connection)
  {
    SINGLETON.saslContextAttribute.set(connection, saslContext);
    return SINGLETON;
  }



  private final Attribute<SASLContext> saslContextAttribute;

  private final Attribute<byte[]> saslIncomingBufferAttribute;

  private final Attribute<byte[]> saslOutgoingBufferAttribute;



  private SASLFilter()
  {
    AttributeBuilder attrBuilder = getAttributeBuilder();
    saslContextAttribute = attrBuilder
        .createAttribute(SASL_CONTEXT_ATTR_NAME);
    saslIncomingBufferAttribute = attrBuilder
        .createAttribute(SASL_INCOMING_BUFFER_NAME);
    saslOutgoingBufferAttribute = attrBuilder
        .createAttribute(SASL_OUTGOING_BUFFER_NAME);
  }



  public StreamReader getStreamReader(StreamReader parentStreamReader)
  {
    return new SASLStreamReader(parentStreamReader, this);
  }



  public StreamWriter getStreamWriter(StreamWriter parentStreamWriter)
  {
    return new SASLStreamWriter(parentStreamWriter, this);
  }



  /**
   * Wraps {@link com.sun.grizzly.filterchain.FilterChainContext}
   * default {@link com.sun.grizzly.streams.StreamReader} and
   * {@link com.sun.grizzly.streams.StreamWriter} with SASL aware ones.
   */
  @Override
  public NextAction handleRead(FilterChainContext ctx,
      NextAction nextAction) throws IOException
  {
    StreamReader parentReader = ctx.getStreamReader();
    StreamWriter parentWriter = ctx.getStreamWriter();

    SASLStreamReader saslStreamReader = new SASLStreamReader(
        parentReader, this);
    SASLStreamWriter saslStreamWriter = new SASLStreamWriter(
        parentWriter, this);

    ctx.setStreamReader(saslStreamReader);
    ctx.setStreamWriter(saslStreamWriter);

    saslStreamReader.pull();

    if (!saslStreamReader.hasAvailableData())
    {
      nextAction = ctx.getStopAction();
    }
    return nextAction;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public NextAction handleWrite(FilterChainContext ctx,
      NextAction nextAction) throws IOException
  {
    StreamWriter writer = ctx.getStreamWriter();

    Object message = ctx.getMessage();

    if (message instanceof Buffer<?>)
    {
      writer.writeBuffer((Buffer<?>) message);
    }
    writer.flush();

    return nextAction;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public NextAction postClose(FilterChainContext ctx,
      NextAction nextAction) throws IOException
  {
    saslContextAttribute.remove(ctx.getConnection());
    saslIncomingBufferAttribute.remove(ctx.getConnection());
    saslOutgoingBufferAttribute.remove(ctx.getConnection());
    return nextAction;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public NextAction postRead(FilterChainContext ctx,
      NextAction nextAction) throws IOException
  {
    SASLStreamReader saslStreamReader = (SASLStreamReader) ctx
        .getStreamReader();
    SASLStreamWriter saslStreamWriter = (SASLStreamWriter) ctx
        .getStreamWriter();

    ctx.setStreamReader(saslStreamReader.getUnderlyingReader());
    ctx.setStreamWriter(saslStreamWriter.getUnderlyingWriter());

    return nextAction;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public NextAction postWrite(FilterChainContext ctx,
      NextAction nextAction) throws IOException
  {
    return nextAction;
  }



  public byte[] unwrap(Buffer<?> incoming, Connection<?> connection)
      throws SaslException
  {
    SASLContext saslClient = saslContextAttribute.get(connection);
    byte[] incomingBuffer = obtainIncomingBuffer(incoming.capacity(),
        connection);
    int remaining = incoming.remaining();

    incoming.get(incomingBuffer, 0, remaining);
    return saslClient.unwrap(incomingBuffer, 0, remaining);
  }



  public byte[] wrap(Buffer<?> outgoing, Connection<?> connection)
      throws SaslException
  {
    SASLContext saslClient = saslContextAttribute.get(connection);
    byte[] outgoingBuffer = obtainOutgoingBuffer(outgoing.capacity(),
        connection);
    int remaining = outgoing.remaining();

    outgoing.get(outgoingBuffer, 0, remaining);
    return saslClient.wrap(outgoingBuffer, 0, remaining);
  }



  protected final AttributeBuilder getAttributeBuilder()
  {
    return Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
  }



  /**
   * Obtaining incoming buffer
   *
   * @param size
   *          The size of the buffer to allocate
   * @param state
   *          State storage
   * @return incoming buffer
   */
  protected final byte[] obtainIncomingBuffer(int size,
      AttributeStorage state)
  {

    // #1 - Try to get buffer from the attribute storage (connection)
    byte[] buffer = saslIncomingBufferAttribute.get(state);

    if ((buffer != null) && (buffer.length >= size))
    {
      return buffer;
    }

    // #2 - Try to get buffer from the WorkerThread (if possible)
    Thread currentThread = Thread.currentThread();
    boolean isWorkingThread = (currentThread instanceof WorkerThread);

    if (isWorkingThread)
    {
      WorkerThread workerThread = (WorkerThread) currentThread;
      buffer = saslIncomingBufferAttribute.get(workerThread);
    }

    if ((buffer != null) && (buffer.length >= size))
    {
      return buffer;
    }

    // #3 - Allocate new buffer
    buffer = new byte[size];

    if (isWorkingThread)
    {
      WorkerThread workerThread = (WorkerThread) currentThread;
      saslIncomingBufferAttribute.set(workerThread, buffer);
    }
    else
    {
      saslIncomingBufferAttribute.set(state, buffer);
    }

    return buffer;
  }



  /**
   * Obtaining outgoing buffer
   *
   * @param size
   *          The size of the buffer to allocate
   * @param state
   *          State storage
   * @return Outgoing buffer
   */
  protected final byte[] obtainOutgoingBuffer(int size,
      AttributeStorage state)
  {

    // #1 - Try to get buffer from the attribute storage (connection)
    byte[] buffer = saslOutgoingBufferAttribute.get(state);

    if ((buffer != null) && (buffer.length >= size))
    {
      return buffer;
    }

    // #2 - Try to get buffer from the WorkerThread (if possible)
    Thread currentThread = Thread.currentThread();
    boolean isWorkingThread = (currentThread instanceof WorkerThread);

    if (isWorkingThread)
    {
      WorkerThread workerThread = (WorkerThread) currentThread;
      buffer = saslOutgoingBufferAttribute.get(workerThread);
    }

    if ((buffer != null) && (buffer.length >= size))
    {
      return buffer;
    }

    // #3 - Allocate new buffer
    buffer = new byte[size];

    if (isWorkingThread)
    {
      WorkerThread workerThread = (WorkerThread) currentThread;
      saslOutgoingBufferAttribute.set(workerThread, buffer);
    }
    else
    {
      saslOutgoingBufferAttribute.set(state, buffer);
    }

    return buffer;
  }
}
