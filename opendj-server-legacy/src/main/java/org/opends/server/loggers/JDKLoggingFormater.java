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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.opends.server.util.StaticUtils;

/**
 * A formatter to replace default format of java.util.logging loggers.
 * <p>
 * With JDK 7+, it is possible to pass in the format from the
 * "java.util.logging.SimpleFormatter.format" parameter to the JVM. Use the
 * parameter instead of this class when JDK6 is not supported any more.
 */
public final class JDKLoggingFormater extends Formatter
{

  /** Use one formatter per thread as DateFormat is not thread-safe. */
  private static final ThreadLocal<DateFormat> DATE_FORMAT =
      new ThreadLocal<DateFormat>()
      {
        @Override
        protected DateFormat initialValue()
        {
          return new SimpleDateFormat("[dd/MM/yyyy:HH:mm:ss Z]");
        }
      };

  @Override
  public String format(LogRecord record)
  {
    StringBuilder b = new StringBuilder();
    b.append(DATE_FORMAT.get().format(new Date(record.getMillis())));
    b.append(" category=").append(LoggingCategoryNames.getCategoryName(record.getLoggerName()));
    b.append(" seq=").append(record.getSequenceNumber());
    b.append(" severity=").append(record.getLevel());
    b.append(" msg=").append(record.getMessage());
    if (record.getThrown() != null)
    {
      b.append(" exception=").append(
          StaticUtils.stackTraceToSingleLineString(record.getThrown()));
    }
    b.append("\n");
    return b.toString();
  }

}
