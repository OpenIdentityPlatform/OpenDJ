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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import org.opends.sdk.responses.ExtendedResult;



/**
 * An LDAP client which has connected to a {@link ServerConnectionFactory}. The
 * LDAP client context can be used to query information about the client's
 * connection such as their network address, as well as managing the state of
 * the connection.
 */
public interface LDAPClientContext
{
  /**
   * Disconnects the client and optionally sends a disconnect notification.
   *
   * @param sendNotification
   *          {@code true} to send a disconnect notification, or {@code false}
   *          otherwise.
   */
  void disconnect(boolean sendNotification);



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
   *          connection.
   */
  void startTLS(SSLContext sslContext);
}
