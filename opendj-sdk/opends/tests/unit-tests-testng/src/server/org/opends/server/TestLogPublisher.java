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
package org.opends.server;

import org.opends.server.api.LogPublisher;
import org.opends.server.loggers.LoggerErrorHandler;
import org.opends.server.loggers.TextLogFormatter;
import org.opends.server.loggers.LogRecord;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

/**
 * This class provides an implementation of an log publisher which will store
 * all messages logged in memory.  It provides methods to retrieve and clear the
 * sets of accumulated log messages.  It is only intended for use in the context
 * of the unit test framework, where it will provide a means of getting any
 * log messages associated with failed test cases.
 */
public class TestLogPublisher implements LogPublisher
{
  private TextLogFormatter formatter;

  // The list that will hold the messages logged.
  private final LinkedList<String> messageList;

  public TestLogPublisher(TextLogFormatter formatter)
  {
    this.messageList = new LinkedList<String>();
    this.formatter = formatter;
  }

  public synchronized void publish(LogRecord record,
                                   LoggerErrorHandler handler)
  {
    try
    {
      messageList.add(formatter.format(record));
    }
    catch(Throwable t)
    {
      if(handler != null)
      {
        handler.handleError(record, t);
      }
    }
  }

  public synchronized void shutdown()
  {
    messageList.clear();
  }

  /**
   * Retrieves a copy of the set of messages logged to this error logger since
   * the last time it was cleared.  A copy of the list is returned to avoid
   * a ConcurrentModificationException.
   *
   * @return  The set of messages logged to this error logger since the last
   *          time it was cleared.
   */
  public synchronized List<String> getMessages()
  {
      return new ArrayList<String>(messageList);
  }

  /**
   * Clears any messages currently stored by this logger.
   */
  public synchronized void clear()
  {
    messageList.clear();
  }
}
