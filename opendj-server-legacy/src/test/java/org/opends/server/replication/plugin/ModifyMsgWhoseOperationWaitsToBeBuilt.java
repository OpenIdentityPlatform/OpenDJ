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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.DataFormatException;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;

/**
 * A ModifyMsg whose replay waits, before anything is attempted, until the test lets it go.
 * <p>
 * The operation is built first thing by a replay, before any lock is taken and before the
 * change is checked against the ones it may depend on, so a replay which waits here holds
 * the change as one being replayed - listed, uncommitted and owned by its thread - for as
 * long as the test wants, while every other replay of the domain runs: a change which
 * follows this one on the same entry is parked as waiting for it, and nothing has failed,
 * so no session restart has been asked for. Let go, the replay runs to its end and commits
 * the change as any other.
 * <p>
 * Such a message can not travel the protocol: it is handed to the domain rather than
 * published, and replayed on a thread of the test.
 */
final class ModifyMsgWhoseOperationWaitsToBeBuilt extends ModifyMsg
{
  private final CountDownLatch letGo;

  /**
   * @param letGo the latch the replay waits on before the operation is built
   */
  ModifyMsgWhoseOperationWaitsToBeBuilt(
      CSN csn, DN dn, List<Modification> mods, String entryUUID, CountDownLatch letGo)
  {
    super(csn, dn, mods, entryUUID);
    this.letGo = letGo;
  }

  @Override
  public ModifyOperation createOperation(InternalClientConnection connection, DN newDN)
      throws LDAPException, IOException, DataFormatException
  {
    try
    {
      letGo.await();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while waiting to be let go", e);
    }
    return super.createOperation(connection, newDN);
  }
}
