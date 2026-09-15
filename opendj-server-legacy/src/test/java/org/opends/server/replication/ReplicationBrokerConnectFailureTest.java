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
package org.opends.server.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ReplicationMessages.*;

import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.DummyReplicationDomain;
import org.opends.server.replication.service.ReplicationBroker;
import org.testng.annotations.Test;

/**
 * Tests what a directory server logs when it cannot connect to any replication server.
 */
@SuppressWarnings("javadoc")
public class ReplicationBrokerConnectFailureTest extends ReplicationTestCase
{
  /**
   * Tests that a directory server which reaches no replication server at all names the
   * reason it reached none.
   * <p>
   * The cause is built for every replication server contacted, but it used to be logged
   * only for the elected one, and no server is ever elected when none of them answers: a
   * rejected certificate, a refused connection and a wrong port then all read as "unable
   * to connect to any replication servers".
   */
  @Test
  public void aBrokerWhichReachesNoReplicationServerNamesTheCause() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(TestCaseUtils.TEST_ROOT_DN_STRING);
    final int serverId = 4021;
    // Free, and left free: nothing must listen on it for this test to be about a failure.
    final int deadPort = TestCaseUtils.findFreePorts(1)[0];
    final String deadServer = "127.0.0.1:" + deadPort;

    final DomainFakeCfg config = newFakeCfg(baseDN, serverId, deadPort);
    final ReplicationBroker broker = new ReplicationBroker(
        new DummyReplicationDomain(0), new ServerState(), config, getReplSessionSecurity());
    try
    {
      final List<String> records = errorLogRecordsOf(() -> {
        broker.start();
        return null;
      });

      assertThat(broker.isConnected()).as("nothing listens on " + deadServer).isFalse();
      final String logged = records.toString();
      assertThat(logged)
          .as("the directory server should name why it could not connect to " + deadServer)
          .contains(WARN_NO_CHANGELOG_SERVER_LISTENING.get(serverId, deadServer, baseDN).toString());
      assertThat(logged)
          .as("the summary of the outage should still be logged next to the cause")
          .contains(WARN_NO_AVAILABLE_CHANGELOGS.get(serverId, baseDN).toString());
      /*
       * A replication server which was only contacted is reported as a warning, and the
       * one this broker elects keeps the error it was reported with. No server answers
       * here, so none is elected and every record about one is a warning: the severity is
       * part of what this reports, not a detail of how, and asserting the text alone would
       * leave the split which decides it untested.
       */
      assertThat(recordOf(records, WARN_NO_CHANGELOG_SERVER_LISTENING.get(serverId, deadServer, baseDN)))
          .as("the cause of a replication server which was only contacted is a warning")
          .contains("severity=WARNING");
    }
    finally
    {
      stop(broker);
    }
  }
}
