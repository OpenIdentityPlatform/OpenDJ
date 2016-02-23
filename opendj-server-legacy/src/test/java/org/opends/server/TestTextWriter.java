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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server;

import org.opends.server.loggers.TextWriter;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

public class TestTextWriter implements TextWriter
{
  /** The list that will hold the messages logged. */
  private final LinkedList<String> messageList;

  public TestTextWriter()
  {
    messageList = new LinkedList<>();
  }

  public synchronized void writeRecord(String record)
  {
    messageList.add(record);
  }

  /** {@inheritDoc} */
  public void flush()
  {
    // No implementation is required.
  }

  /** {@inheritDoc} */
  public void shutdown()
  {
    messageList.clear();
  }

  /** {@inheritDoc} */
  public long getBytesWritten()
  {
    // No implementation is required. Just return 0;
    return 0;
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
    return new ArrayList<>(messageList);
  }

  /** Clears any messages currently stored by this logger. */
  public synchronized void clear()
  {
    messageList.clear();
  }
}
