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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;

import java.util.Collection;

import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.server.config.meta.HTTPAccessLogPublisherCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPAccessLogPublisherCfg;

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

  /** The constructor for this class. */
  private HTTPAccessLogger()
  {
    super((Class) HTTPAccessLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_HTTP_ACCESS_LOGGER_CLASS);
  }

  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return HTTPAccessLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

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

  @Override
  public final synchronized void addLogPublisher(
      HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
  }

  @Override
  public final synchronized boolean removeLogPublisher(
      HTTPAccessLogPublisher<HTTPAccessLogPublisherCfg> publisher)
  {
    return loggerStorage.removeLogPublisher(publisher);
  }

  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
    // Access logger may have not been fully initialized
    if (getServerContext() != null && getServerContext().getCommonAudit() != null)
    {
      getServerContext().getCommonAudit().shutdown();
    }
  }

}
