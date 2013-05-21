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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers;

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
   * Returns the query issued by the client.
   *
   * @return the query
   */
  String getQuery();

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
   * Logs the current request info in the HTTP access log.
   *
   * @param statusCode
   *          the HTTP status code that was returned to the client.
   */
  void log(int statusCode);

}
