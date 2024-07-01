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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;
import org.forgerock.i18n.LocalizableMessage;

import org.forgerock.opendj.ldap.DN;

import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;

/**
 * A LogPublisherErrorHandler is used for notification of exceptions which
 * occur during the publishing of a record.
 *
 * The advantage of using a handler is that we can handle exceptions
 * asynchronously (useful when dealing with an AsynchronousPublisher).
 */
class LogPublisherErrorHandler
{
  private DN publisherConfigDN;
  private boolean writeErroroccurred;

  /**
   * Construct a new log publisher error handler for a log publisher
   * with the provided configuration DN.
   *
   * @param publisherConfigDN The DN of the managed object for the
   * log publisher.
   */
  public LogPublisherErrorHandler(DN publisherConfigDN)
  {
    this.publisherConfigDN = publisherConfigDN;
  }

  /**
   * Handle an exception which occurred during the publishing
   * of a log record.
   * @param record - the record which was being published.
   * @param ex - the exception occurred.
   */
  public void handleWriteError(String record, Throwable ex)
  {
    if(!writeErroroccurred)
    {
      System.err.println(ERR_LOGGER_ERROR_WRITING_RECORD.get(
          publisherConfigDN, stackTraceToSingleLineString(ex)));
      writeErroroccurred = true;
    }
  }

  /**
   * Handle an exception which occurred while trying to open a log
   * file.
   * @param file - the file which was being opened.
   * @param ex - the exception occurred.
   */
  public void handleOpenError(File file, Throwable ex)
  {
    System.err.println(ERR_LOGGER_ERROR_OPENING_FILE.get(
        file, publisherConfigDN, stackTraceToSingleLineString(ex)));
  }

  /**
   * Handle an exception which occurred while trying to close a log
   * file.
   * @param ex - the exception occurred.
   */
  public void handleCloseError(Throwable ex)
  {
    System.err.println(ERR_LOGGER_ERROR_CLOSING_FILE.get(
        publisherConfigDN, stackTraceToSingleLineString(ex)));
  }

  /**
   * Handle an exception which occurred while trying to flush the
   * writer buffer.
   * @param ex - the exception occurred.
   */
  public void handleFlushError(Throwable ex)
  {
    System.err.println(ERR_LOGGER_ERROR_FLUSHING_BUFFER.get(
        publisherConfigDN, stackTraceToSingleLineString(ex)));
  }

  /**
   * Handle an exception which occurred while trying to list log files
   * in a directory.
   * @param retentionPolicy - the retention policy being enforced when
   *                          the exception occurred.
   * @param ex - the exception occurred.
   */
  public void handleDeleteError(RetentionPolicy retentionPolicy, Throwable ex)
  {
    LocalizableMessage msg = ERR_LOGGER_ERROR_ENFORCING_RETENTION_POLICY.get(
            retentionPolicy, publisherConfigDN,
            stackTraceToSingleLineString(ex));
    System.err.println(msg);
  }
}
