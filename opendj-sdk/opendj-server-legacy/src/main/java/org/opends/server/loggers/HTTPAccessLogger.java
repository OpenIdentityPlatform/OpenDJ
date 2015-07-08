/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;

import java.util.Collection;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.HTTPAccessLogPublisherCfgDefn;
import org.opends.server.admin.std.server.HTTPAccessLogPublisherCfg;

/**
 * This class defines the wrapper that will invoke all registered HTTP access
 * loggers for each type of request received or response sent.
 */
public class HTTPAccessLogger extends AbstractLogger
<HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg>, HTTPAccessLogPublisherCfg>
{

  private static LoggerStorage
  <HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg>, HTTPAccessLogPublisherCfg>
  loggerStorage = new LoggerStorage<>();

  /** The singleton instance of this class for configuration purposes. */
  private static final HTTPAccessLogger instance = new HTTPAccessLogger();

  /**
   * The constructor for this class.
   */
  private HTTPAccessLogger()
  {
    super((Class) HTTPAccessLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_HTTP_ACCESS_LOGGER_CLASS);
  }

  /** {@inheritDoc} */
  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return HTTPAccessLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

  /** {@inheritDoc} */
  @Override
  protected Collection<HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg>> getLogPublishers()
  {
    return loggerStorage.getLogPublishers();
  }

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static HTTPAccessLogger getInstance()
  {
    return instance;
  }

  /**
   * Returns all the registered HTTP access log publishers.
   *
   * @return a Collection of {@link HTTPAccessLogPublisher} objects
   */
  public static Collection<HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg>>
      getHTTPAccessLogPublishers()
  {
    return loggerStorage.getLogPublishers();
  }

  /**
   * Logs the given HTTPRequestInfo.
   *
   * @param requestInfo
   *          the HTTP request info to log
   */
  public static void logRequestInfo(HTTPRequestInfo requestInfo)
  {
    for (HTTPAccessLogPublisher<?> publisher : loggerStorage.getLogPublishers())
    {
      publisher.logRequestInfo(requestInfo);
    }
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void addLogPublisher(
      HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized boolean removeLogPublisher(
      HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg> publisher)
  {
    return loggerStorage.removeLogPublisher(publisher);
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
  }

}
