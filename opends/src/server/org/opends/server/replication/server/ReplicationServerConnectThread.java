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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import org.opends.server.api.DirectoryThread;

/**
 * This Class is used to create a thread that is responsible for
 * opening connection from this replication server to the other
 * Replication Servers.
 */
public class ReplicationServerConnectThread extends DirectoryThread
{
  /**
   * The Replication Server that created this thread.
   */
  private ReplicationServer server;

  /**
   * Creates a new instance of this directory thread with the
   * specified name.
   *
   * @param  threadName  The human-readable name to use for this
   *                     thread for debugging purposes.
   * @param  server      The ReplicationServer that will be called to
   *                     handle the connections.
   */
  public ReplicationServerConnectThread(
      String threadName, ReplicationServer server)
  {
    super(threadName);
    this.server = server;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run()
  {
    server.runConnect();
  }

}
