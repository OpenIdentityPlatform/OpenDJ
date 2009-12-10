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

import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.*;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.ConcurrentQueuePool;
import org.opends.sdk.ldap.LDAPDecoder;


/**
 * Abstract LDAP transport.
 */
public abstract class AbstractLDAPTransport
{
  private class ASN1ReaderPool extends
      ConcurrentQueuePool<ASN1StreamReader>
  {
    @Override
    public ASN1StreamReader newInstance()
    {
      return new ASN1StreamReader(maxASN1ElementSize);
    }
  }



  private class ASN1WriterPool extends
      ConcurrentQueuePool<ASN1StreamWriter>
  {
    @Override
    public ASN1StreamWriter newInstance()
    {
      return new ASN1StreamWriter();
    }
  }



  private class DefaultFilterChainFactory implements
      PatternFilterChainFactory
  {
    private FilterChain defaultFilterChain;



    private DefaultFilterChainFactory()
    {
      this.defaultFilterChain = new DefaultFilterChain(this);
      this.defaultFilterChain.add(new MonitorFilter());
      this.defaultFilterChain.add(new TransportFilter());
      this.defaultFilterChain.add(new LDAPFilter());
    }



    public FilterChain create()
    {
      FilterChain filterChain = new DefaultFilterChain(this);
      filterChain.addAll(defaultFilterChain);
      return filterChain;
    }



    public FilterChain getFilterChainPattern()
    {
      return defaultFilterChain;
    }



    public void release(FilterChain chain)
    {
      // TODO: Nothing yet.
    }



    public void setFilterChainPattern(FilterChain chain)
    {
      defaultFilterChain = chain;
    }
  }



  private class LDAPFilter extends FilterAdapter
  {
    @Override
    public NextAction handleRead(FilterChainContext ctx,
        NextAction nextAction) throws IOException
    {
      Connection<?> connection = ctx.getConnection();
      StreamReader streamReader = ctx.getStreamReader();
      LDAPMessageHandler handler = getMessageHandler(connection);
      ASN1StreamReader asn1Reader = getASN1Reader(streamReader);

      try
      {
        do
        {
          LDAPDecoder.decode(asn1Reader, handler);
        }
        while (asn1Reader.hasNextElement());
      }
      finally
      {
        releaseASN1Reader(asn1Reader);
      }

      return nextAction;
    }
  }



  private class MonitorFilter extends FilterAdapter
  {
    @Override
    public void exceptionOccurred(FilterChainContext ctx,
        Throwable error)
    {
      Connection<?> connection = ctx.getConnection();
      if (!connection.isOpen())
      {
        // Grizzly doens't not deregister the read interest from the
        // selector so closing the connection results in an
        // EOFException.
        // Just ignore errors on closed connections.
        return;
      }
      LDAPMessageHandler handler = getMessageHandler(connection);
      handler.handleException(error);
    }



    @Override
    public NextAction handleClose(FilterChainContext ctx,
        NextAction nextAction) throws IOException
    {
      Connection<?> connection = ctx.getConnection();
      removeMessageHandler(connection);
      return nextAction;
    }
  }

  private final PatternFilterChainFactory defaultFilterChainFactory;

  private final int maxASN1ElementSize = 0;

  private final ASN1ReaderPool asn1ReaderPool;

  private final ASN1WriterPool asn1WriterPool;



  protected AbstractLDAPTransport()
  {
    this.defaultFilterChainFactory = new DefaultFilterChainFactory();

    this.asn1ReaderPool = new ASN1ReaderPool();
    this.asn1WriterPool = new ASN1WriterPool();
  }



  public ASN1StreamWriter getASN1Writer(StreamWriter streamWriter)
  {
    ASN1StreamWriter asn1Writer = asn1WriterPool.poll();
    asn1Writer.setStreamWriter(streamWriter);
    return asn1Writer;
  }



  public PatternFilterChainFactory getDefaultFilterChainFactory()
  {
    return defaultFilterChainFactory;
  }



  public void releaseASN1Writer(ASN1StreamWriter asn1Writer)
  {
    asn1WriterPool.offer(asn1Writer);
  }



  protected abstract LDAPMessageHandler getMessageHandler(
      Connection<?> connection);



  protected abstract void removeMessageHandler(Connection<?> connection);



  private ASN1StreamReader getASN1Reader(StreamReader streamReader)
  {
    ASN1StreamReader asn1Reader = asn1ReaderPool.poll();
    asn1Reader.setStreamReader(streamReader);
    return asn1Reader;
  }



  private void releaseASN1Reader(ASN1StreamReader asn1Reader)
  {
    asn1ReaderPool.offer(asn1Reader);
  }
}
