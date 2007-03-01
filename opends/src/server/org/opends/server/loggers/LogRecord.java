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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

/**
 * A LogRecord is reponsible for passing log messages from the individual
 * Loggers to the LogPublishers.
 */
public class LogRecord
{
  /**
   * Non-localized raw message text.
   */
  private String message;

  /**
   * The logger that generated the record.
   */
  private Logger logger;

  /**
   * The object that generated the record.
   */
  private Object caller;

  /**
   * Construct a LogRecord with the given message value.
   *
   * All other properties will be initialized to "null".
   *
   * @param msg  the raw non-localized logging message (may be null).
   */
  public LogRecord(String msg)
  {
    this(null, null, msg);
  }

  /**
   * Construct a LogRecord with the given source logger
   * and message values.
   *
   * @param logger  the source logger (may be null).
   * @param msg     the raw non-localized logging message.
   */
  public LogRecord(Logger logger, String msg)
  {
    this(null, logger, msg);
  }

  /**
   * Construct a LogRecord with the given source object, source logger
   * and message values.
   *
   * All other properties will be initialized to "null".
   *
   * @param caller  the source object (may be null).
   * @param logger  the source logger (may be null).
   * @param msg     the raw non-localized logging message (may be null).
   */
  public LogRecord(Object caller, Logger logger, String msg)
  {
    this.caller = caller;
    this.logger = logger;
    this.message = msg;
  }

  /**
   * Get the source Logger.
   *
   * @return source logger (may be null).
   */
  public Logger getLogger()
  {
    return logger;
  }

  /**
   * Set the source Logger.
   *
   * @param logger logger object that generated the record.
   */
  public void setLogger(Logger logger)
  {
    this.logger = logger;
  }

  /**
   * Get the "raw" log message, before localization or formatting.
   * <p>
   * May be null, which is equivalent to the empty string "".
   * with the localized value.
   *
   * @return the raw message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Set the "raw" log message, before localization or formatting.
   *
   * @param message the raw message string (may be null).
   */
  public void setMessage(String message) {
    this.message = message;
  }

    /**
   * Get the source caller.
   * <p>
   * Source caller is the object that generated this log record
   *
   * @return the source caller
   */
  public Object getCaller() {
    return caller;
  }

  /**
   * Set the source caller.
   * <p>
   * Source caller is the object that generated this log record
   *
   * @param caller source caller
   */
  public void setCaller(Object caller) {
    this.caller = caller;
  }
}
