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
package org.opends.server.api;



import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;



/**
 * This class defines an API that may be used to encode and decode
 * data for communication with clients over a secure channel (e.g.,
 * SSL/TLS, Kerberos confidentiality, etc.).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=true,
     mayInvoke=true)
public abstract class ConnectionSecurityProvider
{
  /**
   * Initializes this connection security provider using the
   * information in the provided configuration entry.
   *
   * @param  configEntry  The entry that contains the configuration
   *                      for this connection security provider.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           an acceptable configuration for this
   *                           security provider.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the provided
   *                                   configuration.
   */
  public abstract void initializeConnectionSecurityProvider(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that may be necessary for this
   * connection security provider.
   */
  public abstract void finalizeConnectionSecurityProvider();



  /**
   * Retrieves the name used to identify this security mechanism.
   *
   * @return  The name used to identify this security mechanism.
   */
  public abstract String getSecurityMechanismName();



  /**
   * Indicates whether client connections using this connection
   * security provider should be considered secure.
   *
   * @return  {@code true} if client connections using this connection
   *          security provider should be considered secure, or
   *          {@code false} if not.
   */
  public abstract boolean isSecure();

 /**
  * Indicates whether the security provider is active or not. Some
  * security providers (DIGEST-MD5, GSSAPI) perform
  * confidentiality/integrity processing of messages and require
  * several handshakes to setup.
  *
  * @return  {@code true} if the security provider is active, or,
  *          {@code false} if not.
  */
  public abstract boolean isActive();


  /**
   * Creates a new instance of this connection security provider that
   * will be used to encode and decode all communication on the
   * provided client connection.
   *
   * @param  clientConnection  The client connection with which this
   *                           security provider will be associated.
   * @param  socketChannel     The socket channel that may be used to
   *                           communicate with the client.
   *
   * @return  The created connection security provider instance.
   *
   * @throws  DirectoryException  If a problem occurs while creating a
   *                              new instance of this security
   *                              provider for the given client
   *                              connection.
   */
  public abstract ConnectionSecurityProvider
                       newInstance(ClientConnection clientConnection,
                                   SocketChannel socketChannel)
         throws DirectoryException;



  /**
   * Indicates that the associated client connection is being closed
   * and that this security provider should perform any necessary
   * processing to deal with that.  If it is indicated that the
   * connection is still valid, then the security provider may attempt
   * to communicate with the client to perform a graceful shutdown.
   *
   * @param  connectionValid  Indicates whether the Directory Server
   *                          believes that the client connection is
   *                          still valid and may be used for
   *                          communication with the client.  Note
   *                          that this may be inaccurate, or that the
   *                          state of the connection may change
   *                          during the course of this method, so the
   *                          security provider must be able to handle
   *                          failures if they arise.
   */
  public abstract void disconnect(boolean connectionValid);



  /**
   * Retrieves the size in bytes that the client should use for the
   * byte buffer meant to hold clear-text data read from or to be
   * written to the client.
   *
   * @return  The size in bytes that the client should use for the
   *          byte buffer meant to hold clear-text data read from or
   *          to be written to the client.
   */
  public abstract int getClearBufferSize();



  /**
   * Retrieves the size in bytes that the client should use for the
   * byte buffer meant to hold encoded data read from or to be written
   * to the client.
   *
   * @return  The size in bytes that the client should use for the
   *          byte buffer meant to hold encoded data read from or to
   *          be written to the client.
   */
  public abstract int getEncodedBufferSize();



  /**
   * Reads data from a client connection, performing any necessary
   * negotiation in the process.  Whenever any clear-text data has
   * been obtained, then the connection security provider should make
   * that available to the client by calling the
   * {@code ClientConnection.processDataRead} method.
   *
   * @return  {@code true} if all the data in the provided buffer was
   *          processed and the client connection can remain
   *          established, or {@code false} if a decoding error
   *          occurred and requests from this client should no longer
   *          be processed.  Note that if this method does return
   *          {@code false}, then it must have already disconnected
   *          the client.
   *
   * @throws  DirectoryException  If a problem occurs while reading
   *                              data from the client.
   */
  public abstract boolean readData()
         throws DirectoryException;



  /**
   * Writes the data contained in the provided clear-text buffer to
   * the client, performing any necessary encoding in the process.  It
   * must be capable of dealing with input buffers that are larger
   * than the value returned by the {@code getClearBufferSize} method.
   * When this method returns, the provided buffer should be in its
   * original state with regard to the position and limit.
   *
   * @param  clearData  The buffer containing the clear-text data to
   *                    write to the client.
   *
   * @return  {@code true} if all the data in the provided buffer was
   *          written to the client and the connection may remain
   *          established, or {@code false} if a problem occurred and
   *          the client connection is no longer valid.  Note that if
   *          this method does return {@code false}, then it must have
   *          already disconnected the client.
   */
  public abstract boolean writeData(ByteBuffer clearData);
}

