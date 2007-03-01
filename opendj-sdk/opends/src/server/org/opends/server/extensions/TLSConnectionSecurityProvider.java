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
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.cert.Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SSLClientAuthPolicy;
import org.opends.server.util.SelectableCertificateKeyManager;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a connection security provider that
 * uses SSL/TLS to encrypt the communication to and from the client.  It uses
 * the <CODE>javax.net.ssl.SSLEngine</CODE> class to provide the actual SSL
 * communication layer, and the Directory Server key and trust store providers
 * to determine which key and trust stores to use.
 */
public class TLSConnectionSecurityProvider
       extends ConnectionSecurityProvider
{


  /**
   * The SSL context name that should be used for this TLS connection security
   * provider.
   */
  private static final String SSL_CONTEXT_INSTANCE_NAME = "TLS";



  // The buffer that will be used when reading clear-text data.
  private ByteBuffer clearInBuffer;

  // The buffer that will be used when writing clear-text data.
  private ByteBuffer clearOutBuffer;

  // The buffer that will be used when reading encrypted data.
  private ByteBuffer sslInBuffer;

  // The buffer that willa be used when writing encrypted data.
  private ByteBuffer sslOutBuffer;

  // The client connection with which this security provider is associated.
  private ClientConnection clientConnection;

  // The size in bytes that should be used for the buffer holding clear-text
  // data.
  private int clearBufferSize;

  // The size in bytes that should be used for the buffer holding the encrypted
  // data.
  private int sslBufferSize;

  // The socket channel that may be used to communicate with the client.
  private SocketChannel socketChannel;

  // The SSL client certificate policy.
  private SSLClientAuthPolicy sslClientAuthPolicy;

  // The SSL context that will be used for all SSL/TLS communication.
  private SSLContext sslContext;

  // The SSL engine that will be used for this connection.
  private SSLEngine sslEngine;

  // The set of cipher suites to allow.
  private String[] enabledCipherSuites;

  // The set of protocols to allow.
  private String[] enabledProtocols;



  /**
   * Creates a new instance of this connection security provider.  Note that
   * no initialization should be done here, since it should all be done in the
   * <CODE>initializeConnectionSecurityProvider</CODE> method.  Also note that
   * this instance should only be used to create new instances that are
   * associated with specific client connections.  This instance itself should
   * not be used to attempt secure communication with the client.
   */
  public TLSConnectionSecurityProvider()
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
   * @param  parentProvider    A reference to the parent TLS connection security
   *                           provider that is being used to create this
   *                           instance.
   */
  private TLSConnectionSecurityProvider(ClientConnection clientConnection,
                                        SocketChannel socketChannel,
                                        TLSConnectionSecurityProvider
                                             parentProvider)
          throws DirectoryException
  {
    super();


    this.clientConnection = clientConnection;
    this.socketChannel    = socketChannel;

    Socket socket = socketChannel.socket();
    InetAddress inetAddress = socketChannel.socket().getInetAddress();


    // Create an SSL session based on the configured key and trust stores in the
    // Directory Server.
    KeyManagerProvider keyManagerProvider =
         DirectoryServer.getKeyManagerProvider(
              clientConnection.getKeyManagerProviderDN());
    if (keyManagerProvider == null)
    {
      keyManagerProvider = new NullKeyManagerProvider();
    }

    TrustManagerProvider trustManagerProvider =
         DirectoryServer.getTrustManagerProvider(
              clientConnection.getTrustManagerProviderDN());
    if (trustManagerProvider == null)
    {
      trustManagerProvider = new NullTrustManagerProvider();
    }

    try
    {
      // FIXME -- Is it bad to create a new SSLContext for each connection?
      sslContext = SSLContext.getInstance(SSL_CONTEXT_INSTANCE_NAME);

      String alias = clientConnection.getCertificateAlias();
      if (alias == null)
      {
        sslContext.init(keyManagerProvider.getKeyManagers(),
                        trustManagerProvider.getTrustManagers(), null);
      }
      else
      {
        sslContext.init(SelectableCertificateKeyManager.wrap(
                             keyManagerProvider.getKeyManagers(), alias),
                        trustManagerProvider.getTrustManagers(), null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_TLS_SECURITY_PROVIDER_CANNOT_INITIALIZE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }

    sslEngine = sslContext.createSSLEngine(inetAddress.getHostName(),
                                           socket.getPort());
    sslEngine.setUseClientMode(false);

    enabledProtocols = parentProvider.enabledProtocols;
    if (enabledProtocols != null)
    {
      sslEngine.setEnabledProtocols(enabledProtocols);
    }

    enabledCipherSuites = parentProvider.enabledCipherSuites;
    if (enabledCipherSuites != null)
    {
      sslEngine.setEnabledCipherSuites(enabledCipherSuites);
    }

    sslClientAuthPolicy = parentProvider.sslClientAuthPolicy;
    if (sslClientAuthPolicy == null)
    {
      sslClientAuthPolicy = SSLClientAuthPolicy.OPTIONAL;
    }
    switch (sslClientAuthPolicy)
    {
      case REQUIRED:
        sslEngine.setWantClientAuth(true);
        sslEngine.setNeedClientAuth(true);
        break;

      case DISABLED:
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(false);
        break;

      case OPTIONAL:
      default:
        sslEngine.setNeedClientAuth(false);
        sslEngine.setWantClientAuth(true);
        break;
    }

    SSLSession sslSession = sslEngine.getSession();

    clearBufferSize = sslSession.getApplicationBufferSize();
    clearInBuffer   = ByteBuffer.allocate(clearBufferSize);
    clearOutBuffer  = ByteBuffer.allocate(clearBufferSize);

    sslBufferSize   = sslSession.getPacketBufferSize();
    sslInBuffer     = ByteBuffer.allocate(sslBufferSize);
    sslOutBuffer    = ByteBuffer.allocate(sslBufferSize);
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

    // Initialize default values for the connection-specific variables.
    clientConnection = null;
    socketChannel    = null;

    clearInBuffer    = null;
    clearOutBuffer   = null;
    sslInBuffer      = null;
    sslOutBuffer     = null;
    clearBufferSize  = -1;
    sslBufferSize    = -1;

    sslEngine        = null;

    enabledProtocols    = null;
    enabledCipherSuites = null;
    sslClientAuthPolicy = SSLClientAuthPolicy.OPTIONAL;
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

    return SSL_CONTEXT_INSTANCE_NAME;
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

    // This should be considered secure.
    return true;
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

    return new TLSConnectionSecurityProvider(clientConnection, socketChannel,
                                             this);
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

    if (connectionValid)
    {
      try
      {
        sslEngine.closeInbound();
        sslEngine.closeOutbound();

        while (true)
        {
          switch (sslEngine.getHandshakeStatus())
          {
            case FINISHED:
            case NOT_HANDSHAKING:
              // We don't need to do anything else.
              return;

            case NEED_TASK:
              // We need to process some task before continuing.
              Runnable task = sslEngine.getDelegatedTask();
              task.run();
              break;

            case NEED_WRAP:
              // We need to send data to the client.
              clearOutBuffer.clear();
              sslOutBuffer.clear();
              sslEngine.wrap(clearOutBuffer, sslOutBuffer);
              sslOutBuffer.flip();
              while (sslOutBuffer.hasRemaining())
              {
                socketChannel.write(sslOutBuffer);
              }
              break;

            case NEED_UNWRAP:
              // We need to read data from the client.  We can do this if it's
              // immediately available, but otherwise, ignore it because
              // otherwise it could chew up a lot of time.
              if (sslInBuffer.hasRemaining())
              {
                clearInBuffer.clear();
                sslEngine.unwrap(sslInBuffer, clearInBuffer);
                clearInBuffer.flip();
              }
              else
              {
                sslInBuffer.clear();
                clearInBuffer.clear();
                int bytesRead = socketChannel.read(sslInBuffer);
                if (bytesRead <= 0)
                {
                  return;
                }
                sslEngine.unwrap(sslInBuffer, clearInBuffer);
                clearInBuffer.flip();
              }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
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

    return clearBufferSize;
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

    return sslBufferSize;
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


    while (true)
    {
      try
      {
        sslInBuffer.clear();
        int bytesRead = socketChannel.read(sslInBuffer);
        sslInBuffer.flip();

        if (bytesRead < 0)
        {
          // The client connection has been closed.  Disconnect and return.
          clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT, false,
                                      -1);
          return false;
        }


        // See if there is any preliminary handshake work to do.
handshakeLoop:
        while (true)
        {
          switch (sslEngine.getHandshakeStatus())
          {
            case NEED_TASK:
              Runnable task = sslEngine.getDelegatedTask();
              task.run();
              break;

            case NEED_WRAP:
              clearOutBuffer.clear();
              sslOutBuffer.clear();
              sslEngine.wrap(clearOutBuffer, sslOutBuffer);
              sslOutBuffer.flip();

              while (sslOutBuffer.hasRemaining())
              {
                int bytesWritten = socketChannel.write(sslOutBuffer);
                if (bytesWritten < 0)
                {
                  // The client connection has been closed.  Disconnect and
                  // return.
                  clientConnection.disconnect(
                       DisconnectReason.CLIENT_DISCONNECT, false, -1);
                  return false;
                }
              }
              break;

            default:
              break handshakeLoop;
          }
        }

        if (bytesRead == 0)
        {
          // We don't have any data to process, and we've already done any
          // necessary handshaking, so we can break out and wait for more data
          // to arrive.
          return true;
        }


        // Read any SSL-encrypted data provided by the client.
        while (sslInBuffer.hasRemaining())
        {
          clearInBuffer.clear();
          SSLEngineResult unwrapResult = sslEngine.unwrap(sslInBuffer,
                                                          clearInBuffer);
          clearInBuffer.flip();

          switch (unwrapResult.getStatus())
          {
            case OK:
              // This is fine.
              break;

            case CLOSED:
              // The client connection (or at least the SSL side of it) has been
              // closed.
              // FIXME -- Allow for closing the SSL channel without closing the
              //          underlying connection.
              clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false, -1);
              return false;

            default:
              // This should not have happened.
              clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM,
                   false, MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_UNWRAP_STATUS,
                   String.valueOf(unwrapResult.getStatus()));
              return false;
          }

          switch (unwrapResult.getHandshakeStatus())
          {
            case NEED_TASK:
              Runnable task = sslEngine.getDelegatedTask();
              task.run();
              break;

            case NEED_WRAP:
              clearOutBuffer.clear();
              sslOutBuffer.clear();
              sslEngine.wrap(clearOutBuffer, sslOutBuffer);
              sslOutBuffer.flip();

              while (sslOutBuffer.hasRemaining())
              {
                int bytesWritten = socketChannel.write(sslOutBuffer);
                if (bytesWritten < 0)
                {
                  // The client connection has been closed.  Disconnect and
                  // return.
                  clientConnection.disconnect(
                       DisconnectReason.CLIENT_DISCONNECT, false, -1);
                  return false;
                }
              }
              break;
          }

          // If there is any clear-text data, then process it.
          if (! clientConnection.processDataRead(clearInBuffer))
          {
            // If this happens, then the client connection disconnect method
            // should have already been called, so we don't need to do it again.
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

        // An error occurred while trying to communicate with the client.
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

        // An unexpected error occurred while trying to process the data read.
        // Disconnect and return.
        clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                    MSGID_TLS_SECURITY_PROVIDER_READ_ERROR,
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

    int originalPosition = clearData.position();
    int originalLimit    = clearData.limit();
    int length           = originalLimit - originalPosition;

    try
    {
      if (length > clearBufferSize)
      {
        // There is more data to write than we can deal with in one chunk, so
        // break it up.
        int pos = originalPosition;
        int lim = originalPosition + clearBufferSize;

        while (pos < originalLimit)
        {
          clearData.position(pos);
          clearData.limit(lim);

          if (! writeInternal(clearData))
          {
            return false;
          }

          pos = lim;
          lim = Math.min(originalLimit, pos+clearBufferSize);
        }

        return true;
      }
      else
      {
        return writeInternal(clearData);
      }
    }
    finally
    {
      clearData.position(originalPosition);
      clearData.limit(originalLimit);
    }
  }



  /**
   * Writes the data contained in the provided clear-text buffer to the client,
   * performing any necessary encoding in the process.  The amount of data in
   * the provided buffer must be less than or equal to the value returned by the
   * <CODE>getClearBufferSize</CODE> method.
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
  private boolean writeInternal(ByteBuffer clearData)
  {
    try
    {
      // See if there is any preliminary handshake work to be done.
handshakeStatusLoop:
      while (true)
      {
        switch (sslEngine.getHandshakeStatus())
        {
          case NEED_TASK:
            Runnable task = sslEngine.getDelegatedTask();
            task.run();
            break;

          case NEED_WRAP:
            clearOutBuffer.clear();
            sslOutBuffer.clear();
            sslEngine.wrap(clearOutBuffer, sslOutBuffer);
            sslOutBuffer.flip();

            while (sslOutBuffer.hasRemaining())
            {
              int bytesWritten = socketChannel.write(sslOutBuffer);
              if (bytesWritten < 0)
              {
                // The client connection has been closed.  Disconnect and
                // return.
                clientConnection.disconnect(
                     DisconnectReason.CLIENT_DISCONNECT, false, -1);
                return false;
              }
            }
            break;

          case NEED_UNWRAP:
            // We need to read data from the client before the negotiation can
            // continue.  This is bad, because we don't know if there is data
            // available but we do know that we can't write until we have read.
            // See if there is something available for reading, and if not, then
            // we can't afford to wait for it because otherwise we would be
            // potentially blocking reads from other clients.  Our only recourse
            // is to close the connection.
            sslInBuffer.clear();
            clearInBuffer.clear();
            int bytesRead = socketChannel.read(sslInBuffer);
            if (bytesRead < 0)
            {
              // The client connection is already closed, so we don't need to
              // worry about it.
              clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false, -1);
              return false;
            }
            else if (bytesRead == 0)
            {
              // We didn't get the data that we need.  We'll have to disconnect
              // to avoid blocking other clients.
              clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM,
                   false, MSGID_TLS_SECURITY_PROVIDER_WRITE_NEEDS_UNWRAP);
              return false;
            }
            else
            {
              // We were lucky and got the data we were looking for, so read and
              // process it.
              sslEngine.unwrap(sslInBuffer, clearInBuffer);
              clearInBuffer.flip();
            }
            break;

          default:
            break handshakeStatusLoop;
        }
      }


      while (clearData.hasRemaining())
      {
        sslOutBuffer.clear();
        SSLEngineResult wrapResult = sslEngine.wrap(clearData, sslOutBuffer);
        sslOutBuffer.flip();

        switch (wrapResult.getStatus())
        {
          case OK:
            // This is fine.
            break;

          case CLOSED:
            // The client connection (or at least the SSL side of it) has been
            // closed.
            // FIXME -- Allow for closing the SSL channel without closing the
            //          underlying connection.
            clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                        false, -1);
            return false;

          default:
            // This should not have happened.
            clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM,
                 false, MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_WRAP_STATUS,
                 String.valueOf(wrapResult.getStatus()));
            return false;
        }

        switch (wrapResult.getHandshakeStatus())
        {
          case NEED_TASK:
            Runnable task = sslEngine.getDelegatedTask();
            task.run();
            break;

          case NEED_WRAP:
            // FIXME -- Could this overwrite the SSL out that we just wrapped?
            // FIXME -- Is this even a feasible result?
            clearOutBuffer.clear();
            sslOutBuffer.clear();
            sslEngine.wrap(clearOutBuffer, sslOutBuffer);
            sslOutBuffer.flip();

            while (sslOutBuffer.hasRemaining())
            {
              int bytesWritten = socketChannel.write(sslOutBuffer);
              if (bytesWritten < 0)
              {
                // The client connection has been closed.  Disconnect and
                // return.
                clientConnection.disconnect(
                     DisconnectReason.CLIENT_DISCONNECT, false, -1);
                return false;
              }
            }
            break;

          case NEED_UNWRAP:
            // We need to read data from the client before the negotiation can
            // continue.  This is bad, because we don't know if there is data
            // available but we do know that we can't write until we have read.
            // See if there is something available for reading, and if not, then
            // we can't afford to wait for it because otherwise we would be
            // potentially blocking reads from other clients.  Our only recourse
            // is to close the connection.
            sslInBuffer.clear();
            clearInBuffer.clear();
            int bytesRead = socketChannel.read(sslInBuffer);
            if (bytesRead < 0)
            {
              // The client connection is already closed, so we don't need to
              // worry about it.
              clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false, -1);
              return false;
            }
            else if (bytesRead == 0)
            {
              // We didn't get the data that we need.  We'll have to disconnect
              // to avoid blocking other clients.
              clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM,
                   false, MSGID_TLS_SECURITY_PROVIDER_WRITE_NEEDS_UNWRAP);
              return false;
            }
            else
            {
              // We were lucky and got the data we were looking for, so read and
              // process it.
              sslEngine.unwrap(sslInBuffer, clearInBuffer);
              clearInBuffer.flip();
            }
            break;
        }

        while (sslOutBuffer.hasRemaining())
        {
          int bytesWritten = socketChannel.write(sslOutBuffer);
          if (bytesWritten < 0)
          {
            // The client connection has been closed.
            clientConnection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                        false, -1);
            return false;
          }
        }
      }


      // If we've gotten here, then everything must have been written
      // successfully.
      return true;
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ioe);
      }

      // An error occurred while trying to communicate with the client.
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

      // An unexpected error occurred while trying to process the data read.
      // Disconnect and return.
      clientConnection.disconnect(DisconnectReason.SERVER_ERROR, true,
                                  MSGID_TLS_SECURITY_PROVIDER_WRITE_ERROR,
                                  stackTraceToSingleLineString(e));
      return false;
    }
  }



  /**
   * Retrieves the set of SSL protocols that will be allowed.
   *
   * @return  The set of SSL protocols that will be allowed, or
   *          <CODE>null</CODE> if the default set will be used.
   */
  public String[] getEnabledProtocols()
  {

    return enabledProtocols;
  }



  /**
   * Specifies the set of SSL protocols that will be allowed.
   *
   * @param  enabledProtocols  The set of SSL protocols that will be allowed, or
   *                           <CODE>null</CODE> if the default set will be
   *                           used.
   */
  public void setEnabledProtocols(String[] enabledProtocols)
  {

    this.enabledProtocols = enabledProtocols;
  }



  /**
   * Retrieves the set of SSL cipher suites that will be allowed.
   *
   * @return  The set of SSL cipher suites that will be allowed.
   */
  public String[] getEnabledCipherSuites()
  {

    return enabledCipherSuites;
  }



  /**
   * Specifies the set of SSL cipher suites that will be allowed.
   *
   * @param  enabledCipherSuites  The set of SSL cipher suites that will be
   *                              allowed.
   */
  public void setEnabledCipherSuites(String[] enabledCipherSuites)
  {

    this.enabledCipherSuites = enabledCipherSuites;
  }



  /**
   * Retrieves the policy that should be used for SSL client authentication.
   *
   * @return  The policy that should be used for SSL client authentication.
   */
  public SSLClientAuthPolicy getSSLClientAuthPolicy()
  {

    return sslClientAuthPolicy;
  }



  /**
   * Specifies the policy that should be used for SSL client authentication.
   *
   * @param  sslClientAuthPolicy  The policy that should be used for SSL client
   *                              authentication.
   */
  public void setSSLClientAuthPolicy(SSLClientAuthPolicy sslClientAuthPolicy)
  {

    this.sslClientAuthPolicy = sslClientAuthPolicy;
  }



  /**
   * Retrieves the SSL session associated with this client connection.
   *
   * @return  The SSL session associated with this client connection.
   */
  public SSLSession getSSLSession()
  {

    return sslEngine.getSession();
  }



  /**
   * Retrieves the certificate chain that the client presented to the server
   * during the handshake process.  The client's certificate will be the first
   * listed, followed by the certificates of any issuers in the chain.
   *
   * @return  The certificate chain that the client presented to the server
   *          during the handshake process, or <CODE>null</CODE> if the client
   *          did not present a certificate.
   */
  public Certificate[] getClientCertificateChain()
  {

    try
    {
      return sslEngine.getSession().getPeerCertificates();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      return null;
    }
  }
}

