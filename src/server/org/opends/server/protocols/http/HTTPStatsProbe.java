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
