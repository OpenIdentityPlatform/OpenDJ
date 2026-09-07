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
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.util.CollectionUtils.newHashSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.server.ReplicationServer.ConnectFailureReporter;
import org.opends.server.types.HostPort;
import org.testng.annotations.Test;

/** Tests for {@link ConnectFailureReporter}. */
@SuppressWarnings("javadoc")
public class ConnectFailureReporterTest extends DirectoryServerTestCase
{
  /*
   * Peers which differ by their port: HostPort resolves a host which is not "localhost"
   * through InetAddress.getByName and compares what it resolved, so two host names would
   * be one peer on a resolver which answers both, and a lookup each on one which answers
   * neither.
   */
  private static final HostPort PEER = HostPort.valueOf("localhost:8989");
  private static final HostPort OTHER_PEER = HostPort.valueOf("localhost:8990");
  private static final DN DOMAIN = DN.valueOf("dc=example,dc=com");
  private static final DN OTHER_DOMAIN = DN.valueOf("dc=other,dc=com");

  @Test
  public void theFirstFailureIsReported() throws Exception
  {
    assertThat(new ConnectFailureReporter().recordFailure(PEER, DOMAIN))
        .as("the first failure to connect to a peer is reported").isTrue();
  }

  @Test
  public void aPeerWhichStaysUnreachableIsReportedOnce() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);

    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("the connect thread retries every few seconds, and reporting each retry"
            + " would fill the error log for as long as the peer is down").isFalse();
  }

  @Test
  public void eachPeerAndDomainIsReportedOnItsOwn() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);

    assertThat(reporter.recordFailure(OTHER_PEER, DOMAIN))
        .as("another peer is another failure").isTrue();
    assertThat(reporter.recordFailure(PEER, OTHER_DOMAIN))
        .as("the same peer for another domain is another failure").isTrue();
  }

  @Test
  public void aConnectionEstablishedAfterAReportedFailureIsReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);

    assertThat(reporter.recordSuccess(PEER, DOMAIN))
        .as("the failure was reported, so the recovery has to be reported as well").isTrue();
  }

  @Test
  public void aConnectionEstablishedWithoutAReportedFailureIsNotReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    assertThat(reporter.recordSuccess(PEER, DOMAIN))
        .as("every connection the topology establishes would otherwise be reported").isFalse();
    reporter.recordFailure(OTHER_PEER, DOMAIN);
    assertThat(reporter.recordSuccess(PEER, DOMAIN))
        .as("the failure of another peer is not this peer's").isFalse();
  }

  @Test
  public void aPeerWhichLeavesTheConfigurationIsForgotten() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordFailure(OTHER_PEER, DOMAIN);

    reporter.retainAll(newHashSet(PEER), newHashSet(DOMAIN));

    assertThat(reporter.recordFailure(OTHER_PEER, DOMAIN))
        .as("nothing connects to a peer which is no longer configured, so nothing would"
            + " ever clear what was recorded for it: its next failure has to be reported").isTrue();
    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("a peer which is still configured keeps what was recorded for it").isFalse();
  }

  @Test
  public void aDomainWhichIsRemovedIsForgotten() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordFailure(PEER, OTHER_DOMAIN);

    reporter.retainAll(newHashSet(PEER), newHashSet(DOMAIN));

    assertThat(reporter.recordFailure(PEER, OTHER_DOMAIN))
        .as("the domain is gone, so the failure recorded for it can no longer be cleared").isTrue();
    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("the domain which remains keeps what was recorded for it").isFalse();
  }

  @Test
  public void aPeerFailingAgainAfterAConnectionIsReportedAgain() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordSuccess(PEER, DOMAIN);

    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("a peer which goes down again is a new failure, not the one already reported").isTrue();
  }
}
