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

import javax.servlet.http.HttpServletRequest;

/**
 * Contains the information required for logging the HTTP request.
 */
public class HTTPRequestInfo
{

  /** The client's host. */
  private final String remoteHost;
  /** The client's address. */
  private final String remoteAddress;
  /** The protocol used for this request. */
  private final String protocol;
  /** The HTTP method/verb used for this request. */
  private final String method;
  /** The query issued by the client. */
  private final String query;
  /** The user agent used by the client. */
  private final String userAgent;

  /** The username that was used to authenticate. */
  private String authUser;
  /** The HTTP status code returned to the client. */
  private volatile Integer statusCode;

  /**
   * Constructor for this class.
   *
   * @param request
   *          The {@link HttpServletRequest} for which to log the information
   */
  public HTTPRequestInfo(HttpServletRequest request)
  {
    this.remoteHost = request.getRemoteHost();
    this.remoteAddress = request.getRemoteAddr();
    this.method = request.getMethod();
    this.query = request.getRequestURI() + "/" + request.getQueryString();
    this.protocol = request.getProtocol();
    this.userAgent = request.getHeader("User-Agent");
  }

  /**
   * Returns the client's host.
   *
   * @return the remoteHost
   */
  public String getRemoteHost()
  {
    return remoteHost;
  }

  /**
   * Returns the client's address.
   *
   * @return the remoteAddress
   */
  public String getRemoteAddress()
  {
    return remoteAddress;
  }

  /**
   * Returns the protocol used for this request.
   *
   * @return the protocol
   */
  public String getProtocol()
  {
    return protocol;
  }

  /**
   * Returns the HTTP method/verb used for this request.
   *
   * @return the method
   */
  public String getMethod()
  {
    return method;
  }

  /**
   * Returns the query issued by the client.
   *
   * @return the query
   */
  public String getQuery()
  {
    return query;
  }

  /**
   * Returns the user agent used by the client.
   *
   * @return the userAgent
   */
  public String getUserAgent()
  {
    return userAgent;
  }

  /**
   * Returns the username that was used to authenticate.
   *
   * @return the authUser
   */
  public String getAuthUser()
  {
    return authUser;
  }

  /**
   * Sets the username that was used to authenticate.
   *
   * @param authUser
   *          the authUser to set
   */
  public void setAuthUser(String authUser)
  {
    this.authUser = authUser;
  }

  /**
   * Returns the HTTP status code returned to the client.
   *
   * @return the statusCode
   */
  public int getStatusCode()
  {
    return statusCode != null ? statusCode : 200;
  }

  /**
   * Logs the current request info in the HTTP access log.
   *
   * @param statusCode
   *          the HTTP status code that was returned to the client.
   */
  public void log(int statusCode)
  {
    if (this.statusCode == null)
    { // this request was not logged before
      this.statusCode = statusCode;
      HTTPAccessLogger.logRequestInfo(this);
    }
  }

}
