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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import org.opends.server.types.DN;



/**
 * This class provides an implementation of a {@code java.net.Socket}
 * object that can be used to facilitate internal communication with
 * the Directory Server through third-party LDAP APIs that provide the
 * ability to use a custom socket factory when creating connections.
 * Whenever data is written over the socket, it is decoded as LDAP
 * communication and converted to an appropriate internal operation,
 * which the server then processes and converts the response back to
 * an LDAP encoding.
 * <BR><BR>
 * Note that this implementation only supports those operations which
 * can be performed in the Directory Server via internal operations.
 * This includes add, compare, delete, modify, modify DN, and search
 * operations, and some types of extended operations.  Special support
 * has been added for simple bind operations to function properly, but
 * SASL binds are not supported.  Abandon and unbind operations are
 * not supported, nor are the cancel or StartTLS extended operations.
 * Only clear-text LDAP communication may be used.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class InternalLDAPSocket
       extends Socket
{
  // Indicates whether this socket is closed.
  private boolean closed;

  // The value that the client has requested for SO_KEEPALIVE.
  private boolean keepAlive;

  // The value that the client has requested for OOBINLINE.
  private boolean oobInline;

  // The value that the client has requested for SO_REUSEADDR.
  private boolean reuseAddress;

  // The value that the client has requested for TCP_NODELAY.
  private boolean tcpNoDelay;

  // The value that the client has requested for SO_LINGER.
  private int lingerDuration;

  // The value that the client has requested for SO_RCVBUF.
  private int receiveBufferSize;

  // The value that the client has requested for SO_SNDBUF.
  private int sendBufferSize;

  // The value that the client has requested for SO_TIMEOUT.
  private int timeout;

  // The value that the client has requested for the traffic class.
  private int trafficClass;

  // The internal client connection used to perform the internal
  // operations.  It will be null until it is first used.
  private InternalClientConnection conn;

  // The input stream associated with this internal LDAP socket.
  private InternalLDAPInputStream inputStream;

  // The output stream associated with this internal LDAP socket.
  private InternalLDAPOutputStream outputStream;



  /**
   * Creates a new internal LDAP socket.
   */
  public InternalLDAPSocket()
  {
    closed            = false;
    keepAlive         = true;
    oobInline         = true;
    reuseAddress      = true;
    tcpNoDelay        = true;
    lingerDuration    = 0;
    receiveBufferSize = 1024;
    sendBufferSize    = 1024;
    timeout           = 0;
    trafficClass      = 0;
    conn              = null;
    inputStream       = new InternalLDAPInputStream(this);
    outputStream      = new InternalLDAPOutputStream(this);
  }



  /**
   * Retrieves the internal client connection used to back this
   * internal LDAP socket.
   *
   * @return  The internal client connection used to back this
   *          internal LDAP socket.
   *
   * @throws  IOException  If there is a problem obtaining the
   *                       connection.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  synchronized InternalClientConnection getConnection()
               throws IOException
  {
    if (conn == null)
    {
      try
      {
        conn = new InternalClientConnection(DN.nullDN());
      }
      catch (Exception e)
      {
        // This should never happen.
        throw new IOException(e.getMessage(), e);
      }
    }

    return conn;
  }



  /**
   * Sets the internal client connection used to back this internal
   * LDAP socket.
   *
   * @param  conn  The internal client connection used to back this
   *               internal LDAP socket.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  synchronized void setConnection(InternalClientConnection conn)
  {
    this.conn = conn;
  }



  /**
   * Binds the socket to a local address.  This does nothing, since
   * there is no actual network communication performed by this
   * socket implementation.
   *
   * @param  bindpoint  The socket address to which to bind.
   */
  @Override()
  public void bind(SocketAddress bindpoint)
  {
    // No implementation is required.
  }



  /**
   * Closes this socket.  This will make it unavailable for use.
   */
  @Override()
  public synchronized void close()
  {
    try
    {
      inputStream.closeInternal();
    } catch (Exception e) {}

    try
    {
      outputStream.closeInternal();
    } catch (Exception e) {}

    closed       = true;
    inputStream  = null;
    outputStream = null;
  }



  /**
   * Connects this socket to the specified remote endpoint.  This will
   * make the connection available again if it has been previously
   * closed.  The provided address is irrelevant, as it will always be
   * an internal connection.
   *
   * @param  endpoint  The address of the remote endpoint.
   */
  @Override()
  public synchronized void connect(SocketAddress endpoint)
  {
    closed       = false;
    inputStream  = new InternalLDAPInputStream(this);
    outputStream = new InternalLDAPOutputStream(this);
  }



  /**
   * Connects this socket to the specified remote endpoint.  This does
   * nothing, since there is no actual network communication performed
   * by this socket implementation.
   *
   * @param  endpoint  The address of the remote endpoint.
   * @param  timeout   The maximum length of time in milliseconds to
   *                   wait for the connection to be established.
   */
  @Override()
  public void connect(SocketAddress endpoint, int timeout)
  {
    closed       = false;
    inputStream  = new InternalLDAPInputStream(this);
    outputStream = new InternalLDAPOutputStream(this);
  }



  /**
   * Retrieves the socket channel associated with this socket.  This
   * method always returns {@code null} since this implementation does
   * not support use with NIO channels.
   *
   * @return  {@code null} because this implementation does not
   *          support use with NIO channels.
   */
  @Override()
  public SocketChannel getChannel()
  {
    // This implementation does not support use with NIO channels.
    return null;
  }



  /**
   * Retrieves the address to which this socket is connected.  The
   * address returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return The address to which this socket is connected.
   */
  @Override()
  public InetAddress getInetAddress()
  {
    try
    {
      return InetAddress.getLocalHost();
    }
    catch (Exception e)
    {
      // This should not happen.
      return null;
    }
  }



  /**
   * Retrieves the input stream for this socket.
   *
   * @return  The input stream for this socket.
   */
  @Override()
  public InternalLDAPInputStream getInputStream()
  {
    return inputStream;
  }



  /**
   * Indicates whether SO_KEEPALIVE is enabled.  This implementation
   * will return {@code true} by default, but if its value is changed
   * using {@code setKeepalive} then that value will be returned.
   * This setting has no effect in this socket implementation.
   *
   * @return  {@code true} if SO_KEEPALIVE is enabled, or
   *          {@code false} if not.
   */
  @Override()
  public boolean getKeepAlive()
  {
    return keepAlive;
  }



  /**
   * Retrieves the local address to which this socket is bound.  The
   * address returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return The local address to which this socket is bound.
   */
  @Override()
  public InetAddress getLocalAddress()
  {
    try
    {
      return InetAddress.getLocalHost();
    }
    catch (Exception e)
    {
      // This should not happen.
      return null;
    }
  }



  /**
   * Retrieves the local port to which this socket is bound.  The
   * value returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return  The local port to which this socket is bound.
   */
  @Override()
  public int getLocalPort()
  {
    return 389;
  }



  /**
   * Retrieves the local socket address to which this socket is bound.
   * The value returned is meaningless, since there is no actual
   * network communication performed by this socket implementation.
   *
   * @return  The local socket address to which this socket is bound.
   */
  @Override()
  public SocketAddress getLocalSocketAddress()
  {
    try
    {
      return new InetSocketAddress(getLocalAddress(), getLocalPort());
    }
    catch (Exception e)
    {
      // This should not happen.
      return null;
    }
  }



  /**
   * Indicates whether OOBINLINE is enabled.  This implementation will
   * return {@code true} by default, but if its value is changed
   * using {@code setOOBInline} then that value will be returned.
   * This setting has no effect in this socket implementation.
   *
   * @return  {@code true} if OOBINLINE is enabled, or {@code false}
   *          if it is not.
   */
  @Override()
  public boolean getOOBInline()
  {
    return oobInline;
  }



  /**
   * Retrieves the output stream for this socket.
   *
   * @return  The output stream for this socket.
   */
  @Override()
  public InternalLDAPOutputStream getOutputStream()
  {
    return outputStream;
  }



  /**
   * Retrieves the remote port to which this socket is connected.  The
   * value returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return  The remote port to which this socket is connected.
   */
  @Override()
  public int getPort()
  {
    return 389;
  }



  /**
   * Retrieves the value of the SO_RCVBUF option for this socket.  The
   * value returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return  The value of the SO_RCVBUF option for this socket.
   */
  @Override()
  public int getReceiveBufferSize()
  {
    return receiveBufferSize;
  }



  /**
   * Retrieves the remote socket address to which this socket is
   * connected.  The value returned is meaningless, since there is no
   * actual network communication performed by this socket
   * implementation.
   *
   * @return  The remote socket address to which this socket is
   *          connected.
   */
  @Override()
  public SocketAddress getRemoteSocketAddress()
  {
    try
    {
      return new InetSocketAddress(getInetAddress(), getPort());
    }
    catch (Exception e)
    {
      // This should not happen.
      return null;
    }
  }



  /**
   * Indicates whether SO_REUSEADDR is enabled.  This implementation
   * will return {@code true} by default, but if its value is changed
   * using {@code setReuseAddress} then that value will be returned.
   * This setting has no effect in this socket implementation.
   *
   * @return  {@code true} if SO_REUSEADDR is enabled, or
   *          {@code false} if it is not.
   */
  @Override()
  public boolean getReuseAddress()
  {
    return reuseAddress;
  }



  /**
   * Retrieves the value of the SO_SNDBUF option for this socket.  The
   * value returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return  The value of the SO_SNDBUF option for this socket.
   */
  @Override()
  public int getSendBufferSize()
  {
    return sendBufferSize;
  }



  /**
   * Retrieves the value of the SO_LINGER option for this socket.  The
   * value returned is meaningless, since there is no actual network
   * communication performed by this socket implementation.
   *
   * @return  The value of the SO_LINGER option for this socket.
   */
  @Override()
  public int getSoLinger()
  {
    return lingerDuration;
  }



  /**
   * Retrieves the value of the SO_TIMEOUT option for this socket.
   * The value returned is meaningless, since there is no actual
   * network communication performed by this socket implementation.
   *
   * @return  The value of the SO_TIMEOUT option for this socket.
   */
  @Override()
  public int getSoTimeout()
  {
    return timeout;
  }



  /**
   * Indicates whether TCP_NODELAY is enabled.  This implementation
   * will return {@code true} by default, but if its value is changed
   * using {@code setTcpNoDelay} then that value will be returned.
   * This setting has no effect in this socket implementation.
   *
   * @return  {@code true} if TCP_NODELAY is enabled, or {@code false}
   *          if it is not.
   */
  @Override()
  public boolean getTcpNoDelay()
  {
    return tcpNoDelay;
  }



  /**
   * Retrieves the traffic class for this socket.  The value returned
   * will be meaningless, since there is no actual network
   * communication performed by this socket.
   *
   * @return  The traffic class for this socket.
   */
  @Override()
  public int getTrafficClass()
  {
    return trafficClass;
  }



  /**
   * Indicates whether this socket is bound to a local address.  This
   * method will always return {@code true} to indicate that it is
   * bound.
   *
   * @return  {@code true} to indicate that the socket is bound to a
   *          local address.
   */
  @Override()
  public boolean isBound()
  {
    return true;
  }



  /**
   * Indicates whether this socket is closed.  This method will always
   * return {@code false} to indicate that it is not closed.
   *
   * @return  {@code false} to indicate that the socket is not closed.
   */
  @Override()
  public boolean isClosed()
  {
    return closed;
  }



  /**
   * Indicates whether this socket is connected to both local and
   * remote endpoints.  This method will always return {@code true} to
   * indicate that it is connected.
   *
   * @return  {@code true} to indicate that the socket is connected.
   */
  @Override()
  public boolean isConnected()
  {
    return (! closed);
  }



  /**
   * Indicates whether the input side of this socket has been closed.
   * This method will always return {@code false} to indicate that it
   * is not closed.
   *
   * @return  {@code false} to indicate that the input side of this
   *          socket is not closed.
   */
  @Override()
  public boolean isInputShutdown()
  {
    return closed;
  }



  /**
   * Indicates whether the output side of this socket has been closed.
   * This method will always return {@code false} to indicate that it
   * is not closed.
   *
   * @return  {@code false} to indicate that the output side of this
   *          socket is not closed.
   */
  @Override()
  public boolean isOutputShutdown()
  {
    return closed;
  }



  /**
   * Sends a single byte of urgent data over this socket.
   *
   * @param  data  The data to be sent.
   *
   * @throws  IOException  If a problem occurs while trying to write
   *                       the provided data over this socket.
   */
  @Override()
  public void sendUrgentData(int data)
         throws IOException
  {
    getOutputStream().write(data);
  }



  /**
   * Sets the value of SO_KEEPALIVE for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  on  The value to use for the SO_KEEPALIVE option.
   */
  @Override()
  public void setKeepAlive(boolean on)
  {
    keepAlive = on;
  }



  /**
   * Sets the value of OOBINLINE for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  on  The value to use for the OOBINLINE option.
   */
  @Override()
  public void setOOBInline(boolean on)
  {
    oobInline = on;
  }



  /**
   * Sets the provided performance preferences for this socket.  This
   * will not affect anything, since there is no actual network
   * communication performed by this socket.
   *
   * @param  connectionTime  An {@code int} expressing the relative
   *                         importance of a short connection time.
   * @param  latency         An {@code int} expressing the relative
   *                         importance of low latency.
   * @param  bandwidth       An {@code int} expressing the relative
   *                         importance of high bandwidth.
   */
  @Override()
  public void setPerformancePreferences(int connectionTime,
                                        int latency, int bandwidth)
  {
    // No implementation is required.
  }



  /**
   * Sets the value of SO_RCVBUF for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  size  The value to use for the SO_RCVBUF option.
   */
  @Override()
  public void setReceiveBufferSize(int size)
  {
    receiveBufferSize = size;
  }



  /**
   * Sets the value of SO_REUSEADDR for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  on  The value to use for the SO_REUSEADDR option.
   */
  @Override()
  public void setReuseAddress(boolean on)
  {
    reuseAddress = on;
  }



  /**
   * Sets the value of SO_SNDBUF for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  size  The value to use for the SO_SNDBUF option.
   */
  @Override()
  public void setSendBufferSize(int size)
  {
    sendBufferSize = size;
  }



  /**
   * Sets the value of SO_LINGER for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  on      Indicates whether to enable the linger option.
   * @param  linger  The length of time in milliseconds to allow the
   *                 connection to linger.
   */
  @Override()
  public void setSoLinger(boolean on, int linger)
  {
    lingerDuration = linger;
  }



  /**
   * Sets the value of SO_TIMEOUT for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  timeout  The value to use for the SO_TIMEOUT option.
   */
  @Override()
  public void setSoTimeout(int timeout)
  {
    this.timeout = timeout;
  }



  /**
   * Sets the value of TCP_NODELAY for this socket.  This will not
   * affect anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  on  The value to use for the TCP_NODELAY option.
   */
  @Override()
  public void setTcpNoDelay(boolean on)
  {
    tcpNoDelay = on;
  }



  /**
   * Sets the traffic class for this socket.  This will not affect
   * anything, since there is no actual network communication
   * performed by this socket.
   *
   * @param  tc  The value to use for the traffic class.
   */
  @Override()
  public void setTrafficClass(int tc)
  {
    trafficClass = tc;
  }



  /**
   * Shuts down the input side of this socket.  This will have the
   * effect of closing the entire socket.
   */
  @Override()
  public void shutdownInput()
  {
    close();
  }



  /**
   * Shuts down the output side of this socket.  This will have the
   * effect of closing the entire socket.
   */
  @Override()
  public void shutdownOutput()
  {
    close();
  }



  /**
   * Retrieves a string representation of this internal LDAP socket.
   *
   * @return  A string representation of this internal LDAP socket.
   */
  @Override()
  public String toString()
  {
    return "InternalLDAPSocket";
  }
}

