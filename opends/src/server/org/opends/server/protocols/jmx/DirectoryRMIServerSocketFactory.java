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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugVerbose;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIServerSocketFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A <code>DirectoryRMIServerSocketFactory</code> instance is used by the RMI
 * runtime in order to obtain server sockets for RMI calls via SSL.
 *
 * <p>
 * This class implements <code>RMIServerSocketFactory</code> over the Secure
 * Sockets Layer (SSL) or Transport Layer Security (TLS) protocols.
 * </p>
 */
public class DirectoryRMIServerSocketFactory implements
    RMIServerSocketFactory
{
  /**
   *  The SSL socket factory associated with the connector.
   */
  private SSLSocketFactory sslSocketFactory = null;

  /**
   * Indicate if we required the client authentication via SSL.
   */
  private final boolean needClientCertificate;


  /**
   * Constructs a new <code>DirectoryRMIServerSocketFactory</code> with the
   * specified SSL socket configuration.
   *
   * @param sslSocketFactory
   *            the SSL socket factory to be used by this factory
   *
   * @param needClientCertificate
   *            <code>true</code> to require client authentication on SSL
   *            connections accepted by server sockets created by this
   *            factory; <code>false</code> to not require client
   *            authentication.
   */
  public DirectoryRMIServerSocketFactory(SSLSocketFactory sslSocketFactory,
      boolean needClientCertificate)
  {
    //
    // Initialize the configuration parameters.
    this.needClientCertificate = needClientCertificate;
    this.sslSocketFactory = sslSocketFactory;
  }

  /**
   * <p>
   * Returns <code>true</code> if client authentication is required on SSL
   * connections accepted by server sockets created by this factory.
   * </p>
   *
   * @return <code>true</code> if client authentication is required
   *
   * @see SSLSocket#setNeedClientAuth
   */
  public final boolean getNeedClientCertificate()
  {
    return needClientCertificate;
  }

  /**
   * Creates a server socket that accepts SSL connections configured according
   * to this factory's SSL socket configuration parameters.
   *
   * @param port
   *            the port number the socket listens to
   *
   * @return a server socket
   *
   * @throws IOException
   *             if the socket cannot be created
   */
  public ServerSocket createServerSocket(int port) throws IOException
  {
    return new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
    {
      @Override
      public Socket accept() throws IOException
      {
        Socket socket = super.accept();
        if (debugEnabled())
        {
          debugVerbose("host/port: %s/%d",
                       socket.getInetAddress().getHostName(), socket.getPort());
        }
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
            socket,
            socket.getInetAddress().getHostName(),
            socket.getPort(),
            true);

        sslSocket.setUseClientMode(false);
        sslSocket.setNeedClientAuth(needClientCertificate);
        return sslSocket;
      }
    };

  }

  /**
   * <p>
   * Indicates whether some other object is "equal to" this one.
   * </p>
   *
   * <p>
   * Two <code>CacaoRMIServerSocketFactory</code> objects are equal if they
   * have been constructed with the same SSL socket configuration parameters.
   * </p>
   *
   * <p>
   * A subclass should override this method (as well as {@link #hashCode()})
   * if it adds instance state that affects equality.
   * </p>
   *
   * @param obj the reference object with which to compare.
   *
   * @return <code>true</code> if this object is the same as the obj
   *         argument <code>false</code> otherwise.
   */
  public boolean equals(Object obj)
  {
    if (obj == null)
      return false;
    if (obj == this)
      return true;
    if (!(obj instanceof DirectoryRMIServerSocketFactory))
      return false;
    DirectoryRMIServerSocketFactory that =
      (DirectoryRMIServerSocketFactory) obj;
    return (getClass().equals(that.getClass()) && checkParameters(that));
  }

  /**
   * Checks if inputs parameters are OK.
   * @param that the input parameter
   * @return true or false.
   */
  private boolean checkParameters(DirectoryRMIServerSocketFactory that)
  {

    if (needClientCertificate != that.needClientCertificate)
      return false;

    if (!sslSocketFactory.equals(that.sslSocketFactory))
      return false;

    return true;
  }

  /**
   * <p>Returns a hash code value for this
   * <code>CacaoRMIServerSocketFactory</code>.</p>
   *
   * @return a hash code value for this
   * <code>CacaoRMIServerSocketFactory</code>.
   */
  public int hashCode()
  {
    return getClass().hashCode()
        + (needClientCertificate ? Boolean.TRUE.hashCode() : Boolean.FALSE
            .hashCode()) + (sslSocketFactory.hashCode());
  }

}
