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
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;



import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.responses.ExtendedResult;



/**
 * An LDAP client which has connected to a {@link ServerConnectionFactory}. An
 * LDAP client context can be used to query information about the client's
 * connection such as their network address, as well as managing the state of
 * the connection.
 */
public interface LDAPClientContext
{

  /**
   * Disconnects the client without sending a disconnect notification.
   * <p>
   * <b>Server connections:</b> invoking this method causes
   * {@link ServerConnection#handleConnectionDisconnected
   * handleConnectionDisconnected} to be called before this method returns.
   */
  void disconnect();



  /**
   * Disconnects the client and sends a disconnect notification, if possible,
   * containing the provided result code and diagnostic message.
   * <p>
   * <b>Server connections:</b> invoking this method causes
   * {@link ServerConnection#handleConnectionDisconnected
   * handleConnectionDisconnected} to be called before this method returns.
   *
   * @param resultCode
   *          The result code which should be included with the disconnect
   *          notification.
   * @param message
   *          The diagnostic message, which may be empty or {@code null}
   *          indicating that none was provided.
   */
  void disconnect(ResultCode resultCode, String message);



  /**
   * Returns the {@code InetSocketAddress} associated with the local system.
   *
   * @return The {@code InetSocketAddress} associated with the local system.
   */
  InetSocketAddress getLocalAddress();



  /**
   * Returns the {@code InetSocketAddress} associated with the remote system.
   *
   * @return The {@code InetSocketAddress} associated with the remote system.
   */
  InetSocketAddress getPeerAddress();



  /**
   * Returns the strongest cipher strength currently in use by the underlying
   * connection.
   *
   * @return The strongest cipher strength currently in use by the underlying
   *         connection.
   */
  int getSecurityStrengthFactor();



  /**
   * Returns {@code true} if the underlying connection has been closed as a
   * result of a client disconnect, a fatal connection error, or a server-side
   * {@link #disconnect}.
   * <p>
   * This method provides a polling mechanism which can be used by synchronous
   * request handler implementations to detect connection termination.
   * <p>
   * <b>Server connections:</b> this method will always return {@code true} when
   * called from within {@link ServerConnection#handleConnectionClosed
   * handleConnectionClosed},
   * {@link ServerConnection#handleConnectionDisconnected
   * handleConnectionDisconnected}, or
   * {@link ServerConnection#handleConnectionError handleConnectionError}.
   *
   * @return {@code true} if the underlying connection has been closed.
   */
  boolean isClosed();



  /**
   * Sends an unsolicited notification to the client.
   *
   * @param notification
   *          The notification to send.
   */
  void sendUnsolicitedNotification(ExtendedResult notification);



  /**
   * Starts the SASL integrity and/or confidentiality protection layer on the
   * underlying connection if possible.
   *
   * @param bindContext
   *          The negotiated bind context that can be used to encode and decode
   *          data on the connection.
   */
  void startSASL(ConnectionSecurityLayer bindContext);



  /**
   * Starts the TLS/SSL security layer on the underlying connection if possible.
   *
   * @param sslContext
   *          The {@code SSLContext} which should be used to secure the
   * @param protocols
   *          Names of all the protocols to enable or {@code null} to use the
   *          default protocols.
   * @param suites
   *          Names of all the suites to enable or {@code null} to use the
   *          default cipher suites.
   * @param wantClientAuth
   *          Set to {@code true} if client authentication is requested, or
   *          {@code false} if no client authentication is desired.
   * @param needClientAuth
   *          Set to {@code true} if client authentication is required, or
   *          {@code false} if no client authentication is desired.
   */
  void startTLS(SSLContext sslContext, String[] protocols, String[] suites,
      boolean wantClientAuth, boolean needClientAuth);
}
