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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.ExtensionsMessages.*;
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
   * Initializes this connection security provider using the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The entry that contains the configuration for this
   *                      connection security provider.
   *
   * @throws  ConfigException  If the provided entry does not contain an
   *                           acceptable configuration for this security
   *                           provider.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the provided
   *                                   configuration.
   */
  public void initializeConnectionSecurityProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {

    clearBuffer      = null;
    clientConnection = null;
    socketChannel    = null;
  }



  /**
   * Performs any finalization that may be necessary for this connection
   * security provider.
   */
  public void finalizeConnectionSecurityProvider()
  {

    // No implementation is required.
  }



  /**
   * Retrieves the name used to identify this security mechanism.
   *
   * @return  The name used to identify this security mechanism.
   */
  public String getSecurityMechanismName()
  {

    return "NULL";
  }



  /**
   * Indicates whether client connections using this connection security
   * provider should be considered secure.
   *
   * @return  <CODE>true</CODE> if client connections using this connection
   *          security provider should be considered secure, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isSecure()
  {

    // This is not a secure provider.
    return false;
  }



  /**
   * Creates a new instance of this connection security provider that will be
   * used to encode and decode all communication on the provided client
   * connection.
   *
   * @param  clientConnection  The client connection with which this security
   *                           provider will be associated.
   * @param  socketChannel     The socket channel that may be used to
   *                           communicate with the client.
   *
   * @return  The created connection security provider instance.
   *
   * @throws  DirectoryException  If a problem occurs while creating a new
   *                              instance of this security provider for the
   *                              given client connection.
   */
  public ConnectionSecurityProvider newInstance(ClientConnection
                                                      clientConnection,
                                                SocketChannel socketChannel)
         throws DirectoryException
  {

    return new NullConnectionSecurityProvider(clientConnection,
                                              socketChannel);
  }



  /**
   * Indicates that the associated client connection is being closed and that
   * this security provider should perform any necessary processing to deal with
   * that.  If it is indicated that the connection is still valid, then the
   * security provider may attempt to communicate with the client to perform a
   * graceful shutdown.
   *
   * @param  connectionValid  Indicates whether the Directory Server believes
   *                          that the client connection is still valid and may
   *                          be used for communication with the client.  Note
   *                          that this may be inaccurate, or that the state of
   *                          the connection may change during the course of
   *                          this method, so the security provider must be able
   *                          to handle failures if they arise.
   */
  public void disconnect(boolean connectionValid)
  {

    // No implementation is required.
  }



  /**
   * Retrieves the size in bytes that the client should use for the byte buffer
   * meant to hold clear-text data read from or to be written to the client.
   *
   * @return  The size in bytes that the client should use for the byte buffer
   *          meant to hold clear-text data read from or to be written to the
   *          client.
   */
  public int getClearBufferSize()
  {

    return BUFFER_SIZE;
  }



  /**
   * Retrieves the size in bytes that the client should use for the byte buffer
   * meant to hold encoded data read from or to be written to the client.
   *
   * @return  The size in bytes that the client should use for the byte buffer
   *          meant to hold encoded data read from or to be written to the
   *          client.
   */
  public int getEncodedBufferSize()
  {

    return BUFFER_SIZE;
  }



  /**
   * Reads data from a client connection, performing any necessary negotiation
   * in the process.  Whenever any clear-text data has been obtained, then the
   * connection security provider should make that available to the client by
   * calling the <CODE>ClientConnection.processDataRead</CODE> method.
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer was
   *          processed and the client connection can remain established, or
   *          <CODE>false</CODE> if a decoding error occurred and requests from
   *          this client should no longer be processed.  Note that if this
   *          method does return <CODE>false</CODE>, then it must have already
   *          disconnected the client.
   */
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
                                      -1);
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
          debugCought(DebugLogLevel.ERROR, ioe);
        }

        // An error occurred while trying to read data from the client.
        // Disconnect and return.
        clientConnection.disconnect(DisconnectReason.IO_ERROR, false, -1);
        return false;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        // An unexpected error occurred.  Disconnect and return.
        clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                    MSGID_NULL_SECURITY_PROVIDER_READ_ERROR,
                                    stackTraceToSingleLineString(e));
        return false;
      }
    }
  }



  /**
   * Writes the data contained in the provided clear-text buffer to the client,
   * performing any necessary encoding in the process.  It must be capable of
   * dealing with input buffers that are larger than the value returned by the
   * <CODE>getClearBufferSize</CODE> method.  When this method returns, the
   * provided buffer should be in its original state with regard to the position
   * and limit.
   *
   * @param  clearData  The buffer containing the clear-text data to write to
   *                    the client.
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer was
   *          written to the client and the connection may remain established,
   *          or <CODE>false</CODE> if a problem occurred and the client
   *          connection is no longer valid.  Note that if this method does
   *          return <CODE>false</CODE>, then it must have already disconnected
   *          the client.
   */
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
                                      -1);
          return false;
        }
      }

      return true;
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ioe);
      }

      // An error occurred while trying to write data to the client.  Disconnect
      // and return.
      clientConnection.disconnect(DisconnectReason.IO_ERROR, false, -1);
      return false;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      // An unexpected error occurred.  Disconnect and return.
      clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                  MSGID_NULL_SECURITY_PROVIDER_WRITE_ERROR,
                                  stackTraceToSingleLineString(e));
      return false;
    }
    finally
    {
      clearData.position(position);
      clearData.limit(limit);
    }
  }
}

