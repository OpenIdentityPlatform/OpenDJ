/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.net.URI;

/**
 * Contains the information required for logging the HTTP request.
 */
public interface HTTPRequestInfo
{

  /**
   * Returns the server's host.
   *
   * @return the serverAddress
   */
  String getServerAddress();

  /**
   * Returns the server's host.
   *
   * @return the serverHost
   */
  String getServerHost();

  /**
   * Returns the server's port.
   *
   * @return the serverPort
   */
  int getServerPort();

  /**
   * Returns the client's address.
   *
   * @return the clientAddress
   */
  String getClientAddress();

  /**
   * Returns the client's host.
   *
   * @return the clientHost
   */
  String getClientHost();

  /**
   * Returns the client's port.
   *
   * @return the clientPort
   */
  int getClientPort();

  /**
   * Returns the protocol used for this request.
   *
   * @return the protocol
   */
  String getProtocol();

  /**
   * Returns the HTTP method/verb used for this request.
   *
   * @return the method
   */
  String getMethod();

  /**
   * Returns the URI issued by the client.
   *
   * @return the URI
   */
  URI getUri();

  /**
   * Returns the user agent used by the client.
   *
   * @return the userAgent
   */
  String getUserAgent();

  /**
   * Returns the username that was used to authenticate.
   *
   * @return the authUser
   */
  String getAuthUser();

  /**
   * Sets the username that was used to authenticate.
   *
   * @param authUser
   *          the authUser to set
   */
  void setAuthUser(String authUser);

  /**
   * Returns the HTTP status code returned to the client.
   *
   * @return the statusCode
   */
  int getStatusCode();

  /**
   * Returns the unique identifier that has been assigned to the client
   * connection for this HTTP request.
   *
   * @return The unique identifier that has been assigned to the client
   *         connection for this HTTP request
   */
  long getConnectionID();

  /**
   * Returns the total processing time for this HTTP request.
   *
   * @return the total processing time for this HTTP request
   */
  long getTotalProcessingTime();

  /**
   * Returns the transactionId for this request.
   *
   * @return the transactionId
   */
  String getTransactionId();

  /**
   * Logs the current request info in the HTTP access log.
   *
   * @param statusCode
   *          the HTTP status code that was returned to the client.
   */
  void log(int statusCode);

}
