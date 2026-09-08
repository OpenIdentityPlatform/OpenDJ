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

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.Test;

/**
 * Tests that a configuration change does not start the session of a domain which stopped
 * its own session: a domain which is shutting down, or whose data is being replaced, owns
 * its session and is the one which brings it back.
 */
@SuppressWarnings("javadoc")
public class SessionRestartTest extends ReplicationTestCase
{
  private static final int RS_ID = 601;
  private static final int DS_ID = 1;
  private static final int GROUP_ID = 1;

  @Test
  public void aConfigurationChangeDoesNotStartTheSessionOfADisabledDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestDisabledDb");

      final DomainFakeCfg domainCfg = newDomainCfg(baseDN, rsPort);
      domain = MultimasterReplication.createNewDomain(domainCfg);
      domain.start();
      assertTrue(domain.isConnected());

      // The data this domain replicates is about to be replaced by an import or a restore.
      domain.disable();
      assertFalse(domain.isConnected());

      changeEclIncludes(domain, domainCfg);

      assertFalse(domain.isConnected(),
          "a configuration change started the session of a disabled domain");
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  @Test
  public void aConfigurationChangeDoesNotStartTheSessionOfAShutDownDomain() throws Exception
  {
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    ReplicationServer replicationServer = null;
    LDAPReplicationDomain domain = null;
    try
    {
      final int rsPort = TestCaseUtils.findFreePort();
      replicationServer = createReplicationServer(rsPort, "sessionRestartTestShutdownDb");

      final DomainFakeCfg domainCfg = newDomainCfg(baseDN, rsPort);
      domain = MultimasterReplication.createNewDomain(domainCfg);
      domain.start();
      assertTrue(domain.isConnected());

      domain.shutdown();
      assertFalse(domain.isConnected());

      changeEclIncludes(domain, domainCfg);

      assertFalse(domain.isConnected(),
          "a configuration change started the session of a domain which has shut down");
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDN);
      }
      remove(replicationServer);
    }
  }

  /**
   * Applies a configuration change which changes the attributes the external changelog
   * includes. The domain hands it to {@code ExternalChangelogDomain}, which asks for the
   * session to be restarted so that the replication server hears the new list.
   */
  private void changeEclIncludes(LDAPReplicationDomain domain, DomainFakeCfg domainCfg)
      throws Exception
  {
    final SortedSet<String> eclIncludes = new TreeSet<>();
    eclIncludes.add("cn");
    domainCfg.setExternalChangelogDomain(
        new ExternalChangelogDomainFakeCfg(true, eclIncludes, new TreeSet<String>()));

    assertEquals(domain.applyConfigurationChange(domainCfg).getResultCode(), ResultCode.SUCCESS);
    // the change did reach the external changelog configuration of the domain
    assertThat(domain.getEclIncludes()).contains("cn");
  }

  private DomainFakeCfg newDomainCfg(DN baseDN, int rsPort)
  {
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    return new DomainFakeCfg(baseDN, DS_ID, replServers, GROUP_ID);
  }

  private ReplicationServer createReplicationServer(int rsPort, String dbDir) throws Exception
  {
    final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
        rsPort, dbDir, 0, RS_ID, 0, 100, new TreeSet<String>(), GROUP_ID, 1000, 5000);
    return new ReplicationServer(conf);
  }
}
