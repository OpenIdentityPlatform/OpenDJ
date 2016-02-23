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
 * Copyright 2013 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpProbe;

/**
 * Probe that collect some statistics on the HTTP server: bytes read and bytes
 * written to the HTTP connection. We are using
 * {@link #onDataReceivedEvent(Connection, Buffer)} and
 * {@link #onDataSentEvent(Connection, Buffer)} because they are the only ones
 * that really output the number of bytes sent on the wire, including data
 * formatting for the HTTP protocol (chunk size, etc.).
 * <p>
 * Use
 * <code>curl "http://localhost:8080/users?_queryFilter=true&_prettyPrint=true"
 * --trace-ascii output.txt</code> to trace the client-server communication.
 * </p>
 */
@SuppressWarnings("rawtypes")
final class HTTPStatsProbe extends HttpProbe.Adapter
{
  private final HTTPStatistics statTracker;

  /**
   * Constructs and object from this class.
   *
   * @param statTracker
   *          the statistic tracker
   */
  public HTTPStatsProbe(HTTPStatistics statTracker)
  {
    this.statTracker = statTracker;
  }

  /** {@inheritDoc} */
  @Override
  public void onDataSentEvent(Connection connection, Buffer buffer)
  {
    this.statTracker.updateBytesWritten(buffer.limit());
  }

  /** {@inheritDoc} */
  @Override
  public void onDataReceivedEvent(Connection connection, Buffer buffer)
  {
    this.statTracker.updateBytesRead(buffer.limit());
  }

}
