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

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.types.Attribute;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.Test;

/**
 * Test that disabling a replication domain leaves the position it persisted in
 * the base entry alone.
 */
@SuppressWarnings("javadoc")
public class DisabledDomainServerStateTest extends ReplicationTestCase
{
  /** The attribute the domain checkpoints its ServerState to. */
  private static final String REPLICATION_STATE = "ds-sync-state";

  /**
   * An import or a restore task disables the domain, and the server can be shut
   * down before the task enables it back. The last save the state checkpointer
   * runs on its way out must not write the emptied state over the CSNs the base
   * entry carries: the domain would come back up with no position at all, and
   * would have everything the replication server still holds replayed to it.
   */
  @Test
  public void disabledDomainKeepsItsPersistedStateOnShutdown() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    LDAPReplicationDomain domain = null;
    try
    {
      // No replication server is listening: the domain accepts the changes
      // anyway, which is all this test needs it to do.
      final SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:" + TestCaseUtils.findFreePort());
      final DomainFakeCfg domainConf = new DomainFakeCfg(baseDN, 1, replServers);
      domainConf.setHeartbeatInterval(100000);
      domainConf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // A change of our own gives the domain a position to lose.
      final ModifyOperation op = getRootConnection().processModify(
          modifyRequest(baseDN, REPLACE, "description", "test"));
      assertEquals(op.getResultCode(), ResultCode.SUCCESS, op.getAdditionalLogItems().toString());

      // Checkpoint it, the way the state checkpointer does every second.
      domain.backupStart();
      final List<String> checkpointed = persistedState(baseDN);
      assertFalse(checkpointed.isEmpty(), "the change was not checkpointed to " + REPLICATION_STATE);

      domain.disable();

      // Shutting the domain down runs the last save of the checkpointer.
      MultimasterReplication.deleteDomain(baseDN);
      domain = null;

      assertEquals(persistedState(baseDN), checkpointed,
          "disabling the domain dropped the position persisted in " + REPLICATION_STATE);
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
    }
  }

  /** Returns the values {@code ds-sync-state} carries on the base entry. */
  private List<String> persistedState(DN baseDN)
  {
    final SearchRequest request =
        newSearchRequest(baseDN, SearchScope.BASE_OBJECT).addAttribute(REPLICATION_STATE);
    final InternalSearchOperation search = getRootConnection().processSearch(request);
    assertEquals(search.getResultCode(), ResultCode.SUCCESS, search.getErrorMessage().toString());

    final SearchResultEntry entry = search.getSearchEntries().getFirst();
    final List<String> values = new ArrayList<>();
    for (Attribute attr : entry.getAllAttributes(REPLICATION_STATE))
    {
      for (ByteString value : attr)
      {
        values.add(value.toString());
      }
    }
    return values;
  }
}
