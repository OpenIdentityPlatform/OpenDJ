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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import java.net.ServerSocket;

import org.opends.server.api.DirectoryThread;

/**
 * This Class is used to create a thread that is responsible for listening
 * on the Replication Server thread and accept new incoming connections
 * from other replication servers or from LDAP servers.
 */
public class ReplicationServerListenThread extends DirectoryThread
{
  /**
   * The Replication Server that created this thread.
   */
  private final ReplicationServer server;

  /** The socket this thread accepts connections on, and whose closing stops it. */
  private final ServerSocket listenSocket;

  /**
   * Creates a new instance of this directory thread with the
   * specified name.
   *
   * @param  server      The ReplicationServer that will be called to
   *                     handle the connections.
   * @param  listenSocket The bound socket this thread will accept connections on.
   */
  public ReplicationServerListenThread(ReplicationServer server, ServerSocket listenSocket)
  {
    super("Replication server RS(" + server.getServerId()
        + ") connection listener on port "
        + listenSocket.getLocalPort());
    this.server = server;
    this.listenSocket = listenSocket;
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    server.runListen(listenSocket);
  }
}
