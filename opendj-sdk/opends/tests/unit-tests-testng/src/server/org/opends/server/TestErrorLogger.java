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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server;



import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

import org.opends.server.api.ErrorLogger;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;



/**
 * This class provides an implementation of an error logger which will store all
 * messages logged in memory.  It provides methods to retrieve and clear the
 * sets of accumulated log messages.  It is only intended for use in the context
 * of the unit test framework, where it will provide a means of getting any
 * error log messages associated with failed test cases.
 */
public class TestErrorLogger
       extends ErrorLogger
{
  // The list that will hold the messages logged.
  private final LinkedList<String> messageList;



  /**
   * The singleton instance of this test access logger.
   */
  private static final TestErrorLogger SINGLETON = new TestErrorLogger();



  /**
   * Creates a new instance of this test error logger.
   */
  private TestErrorLogger()
  {
    super();

    messageList = new LinkedList<String>();
  }



  /**
   * Retrieves the singleton instance of this test error logger.
   *
   * @return  The singleton instance of this test error logger.
   */
  public static TestErrorLogger getInstance()
  {
    return SINGLETON;
  }



  /**
   * Retrieves the set of messages logged to this error logger since the last
   * time it was cleared.  The caller must not attempt to alter the list in any
   * way.
   *
   * @return  The set of messages logged to this error logger since the last
   *          time it was cleared.
   */
  public static List<String> getMessages()
  {
    return SINGLETON.messageList;
  }



  /**
   * Clears any messages currently stored by this logger.
   */
  public static void clear()
  {
    SINGLETON.messageList.clear();
  }


  /**
   * {@inheritDoc}
   */
  public void initializeErrorLogger(ConfigEntry configEntry)
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void closeErrorLogger()
  {
    messageList.clear();
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logError(ErrorLogCategory category,
                                    ErrorLogSeverity severity, String message,
                                    int errorID)
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append("category=\"");
    buffer.append(category);
    buffer.append("\" severity=\"");
    buffer.append(severity);
    buffer.append("\" msgID=");
    buffer.append(errorID);
    buffer.append(" message=\"");
    buffer.append(message);
    buffer.append("\"");

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized boolean equals(Object o)
  {
    return (this == o);
  }



  /**
   * {@inheritDoc}
   */
  public synchronized int hashCode()
  {
    return 1;
  }
}

