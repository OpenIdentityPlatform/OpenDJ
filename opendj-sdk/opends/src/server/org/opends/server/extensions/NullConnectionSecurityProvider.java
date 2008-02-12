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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import org.opends.server.types.DebugLogLevel;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a connection security provider that
 * does not actually provide any security for the communication process.  Any
 * data read or written will be assumed to be clear text.
 */
public class NullConnectionSecurityProvider
       extends ConnectionSecurityProvider
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The buffer size in bytes that will be used for data on this connection.
   */
  private static final int BUFFER_SIZE = 4096;



  // The buffer that will be used when reading clear-text data.
  private ByteBuffer clearBuffer;

  // The client connection with which this security provider is associated.
  private ClientConnection clientConnection;

  // The socket channel that may be used to communicate with the client.
  private SocketChannel socketChannel;



  /**
   * Creates a new instance of this connection security provider.  Note that
   * no initialization should be done here, since it should all be done in the
   * <CODE>initializeConnectionSecurityProvider</CODE> method.  Also note that
   * this instance should only be used to create new instances that are
   * associated with specific client connections.  This instance itself should
   * not be used to attempt secure communication with the client.
   */
  public NullConnectionSecurityProvider()
  {
    super();
  }



  /**
   * Creates a new instance of this connection security provider that will be
   * associated with the provided client connection.
   *
   * @param  clientConnection  The client connection with which this connection
   *                           security provider should be associated.
   * @param  socketChannel     The socket channel that may be used to
   *                           communicate with the client.
   */
  protected NullConnectionSecurityProvider(ClientConnection clientConnection,
                                           SocketChannel socketChannel)
  {
    super();


    this.clientConnection = clientConnection;
    this.socketChannel    = socketChannel;

    clearBuffer = ByteBuffer.allocate(BUFFER_SIZE);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeConnectionSecurityProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    clearBuffer      = null;
    clientConnection = null;
    socketChannel    = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeConnectionSecurityProvider()
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getSecurityMechanismName()
  {
    return "NULL";
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure()
  {
    // This is not a secure provider.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConnectionSecurityProvider newInstance(ClientConnection
                                                      clientConnection,
                                                SocketChannel socketChannel)
         throws DirectoryException
  {
    return new NullConnectionSecurityProvider(clientConnection,
                                              socketChannel);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void disconnect(boolean connectionValid)
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int getClearBufferSize()
  {
    return BUFFER_SIZE;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int getEncodedBufferSize()
  {
    return BUFFER_SIZE;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean readData()
  {
    clearBuffer.clear();
    while (true)
    {
      try
      {
        int bytesRead = socketChannel.read(clearBuffer);
        clearBuffer.flip();

        if (bytesRead < 0)
        {
          // The connection has been closed by the client.  Disconnect and
          // return.
          clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT, false,
                                      null);
          return false;
        }
        else if (bytesRead == 0)
        {
          // We have read all the data that there is to read right now (or there
          // wasn't any in the first place).  Just return and wait for future
          // notification.
          return true;
        }
        else
        {
          // We have read data from the client.  Since there is no actual
          // security on this connection, then just deal with it as-is.
          if (! clientConnection.processDataRead(clearBuffer))
          {
            return false;
          }
        }
      }
      catch (IOException ioe)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
        }

        // An error occurred while trying to read data from the client.
        // Disconnect and return.
        clientConnection.disconnect(DisconnectReason.IO_ERROR, false, null);
        return false;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // An unexpected error occurred.  Disconnect and return.
        clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                    ERR_NULL_SECURITY_PROVIDER_READ_ERROR.get(
                                      getExceptionMessage(e)));
        return false;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean writeData(ByteBuffer clearData)
  {
    int position = clearData.position();
    int limit    = clearData.limit();

    try
    {
      while (clearData.hasRemaining())
      {
        int bytesWritten = socketChannel.write(clearData);
        if (bytesWritten < 0)
        {
          // The client connection has been closed.  Disconnect and return.
          clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT, false,
                                      null);
          return false;
        }
        else if (bytesWritten == 0)
        {
          // This can happen if the server can't send data to the client (e.g.,
          // because the client is blocked or there is a network problem.  In
          // that case, then use a selector to perform the write, timing out and
          // terminating the client connection if necessary.
          return writeWithTimeout(clientConnection, socketChannel, clearData);
        }
      }

      return true;
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
      }

      // An error occurred while trying to write data to the client.  Disconnect
      // and return.
      clientConnection.disconnect(DisconnectReason.IO_ERROR, false, null);
      return false;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // An unexpected error occurred.  Disconnect and return.
      clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                  ERR_NULL_SECURITY_PROVIDER_WRITE_ERROR.get(
                                  getExceptionMessage(e)));
      return false;
    }
    finally
    {
      clearData.position(position);
      clearData.limit(limit);
    }
  }



  /**
   * Writes the contents of the provided buffer to the client, terminating the
   * connection if the write is unsuccessful for too long (e.g., if the client
   * is unresponsive or there is a network problem).  If possible, it will
   * attempt to use the selector returned by the
   * {@code ClientConnection.getWriteSelector} method, but it is capable of
   * working even if that method returns {@code null}.
   * <BR><BR>
   * Note that this method has been written in a generic manner so that other
   * connection security providers can use it to send data to the client,
   * provided that the given buffer contains the appropriate pre-encoded
   * information.
   * <BR><BR>
   * Also note that the original position and limit values will not be
   * preserved, so if that is important to the caller, then it should record
   * them before calling this method and restore them after it returns.
   *
   * @param  clientConnection  The client connection to which the data is to be
   *                           written.
   * @param  socketChannel     The socket channel over which to write the data.
   * @param  buffer            The data to be written to the client.
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer was
   *          written to the client and the connection may remain established,
   *          or <CODE>false</CODE> if a problem occurred and the client
   *          connection is no longer valid.  Note that if this method does
   *          return <CODE>false</CODE>, then it must have already disconnected
   *          the client.
   *
   * @throws  IOException  If a problem occurs while attempting to write data
   *                       to the client.  The caller will be responsible for
   *                       catching this and terminating the client connection.
   */
  public static boolean writeWithTimeout(ClientConnection clientConnection,
                                         SocketChannel socketChannel,
                                         ByteBuffer buffer)
         throws IOException
  {
    long startTime = System.currentTimeMillis();
    long waitTime  = clientConnection.getMaxBlockedWriteTimeLimit();
    if (waitTime <= 0)
    {
      // We won't support an infinite time limit, so fall back to using
      // five minutes, which is a very long timeout given that we're
      // blocking a worker thread.
      waitTime = 300000L;
    }

    long stopTime = startTime + waitTime;


    Selector selector = clientConnection.getWriteSelector();
    if (selector == null)
    {
      // The client connection does not provide a selector, so we'll fall back
      // to a more inefficient way that will work without a selector.
      while (buffer.hasRemaining() && (System.currentTimeMillis() < stopTime))
      {
        if (socketChannel.write(buffer) < 0)
        {
          // The client connection has been closed.  Disconnect and return.
          clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT, false,
                                      null);
          return false;
        }
      }

      if (buffer.hasRemaining())
      {
        // If we've gotten here, then the write timed out.  Terminate the client
        // connection.
        clientConnection.disconnect(DisconnectReason.IO_TIMEOUT, false, null);
        return false;
      }

      return true;
    }


    // Register with the selector for handling write operations.
    SelectionKey key = socketChannel.register(selector, SelectionKey.OP_WRITE);

    try
    {
      selector.select(waitTime);
      while (buffer.hasRemaining())
      {
        long currentTime = System.currentTimeMillis();
        if (currentTime >= stopTime)
        {
          // We've been blocked for too long.  Terminate the client connection.
          clientConnection.disconnect(DisconnectReason.IO_TIMEOUT, false, null);
          return false;
        }
        else
        {
          waitTime = stopTime - currentTime;
        }

        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext())
        {
          SelectionKey k = iterator.next();
          if (k.isWritable())
          {
            int bytesWritten = socketChannel.write(buffer);
            if (bytesWritten < 0)
            {
              // The client connection has been closed.  Disconnect and return.
              clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false, null);
              return false;
            }

            iterator.remove();
          }
        }

        if (buffer.hasRemaining())
        {
          selector.select(waitTime);
        }
      }

      return true;
    }
    finally
    {
      if (key.isValid())
      {
        key.cancel();
        selector.selectNow();
      }
    }
  }
}

