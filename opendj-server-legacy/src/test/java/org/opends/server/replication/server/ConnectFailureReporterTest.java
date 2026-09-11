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

    assertThat(reporter.recordConnected(PEER, DOMAIN))
        .as("the failure was reported, so the recovery has to be reported as well").isTrue();
  }

  @Test
  public void aConnectionEstablishedWithoutAReportedFailureIsNotReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    assertThat(reporter.recordConnected(PEER, DOMAIN))
        .as("every connection the topology establishes would otherwise be reported").isFalse();
    reporter.recordFailure(OTHER_PEER, DOMAIN);
    assertThat(reporter.recordConnected(PEER, DOMAIN))
        .as("the failure of another peer is not this peer's").isFalse();
  }

  @Test
  public void aPeerWhichAnswersWithoutASessionAfterAnOutageIsReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);

    assertThat(reporter.recordReachableWithoutSession(PEER, DOMAIN))
        .as("a peer which was reported unreachable and now answers is no longer unreachable,"
            + " and what it is instead is not what the outage said").isTrue();
  }

  @Test
  public void aPeerWhichAnswersWithoutASessionWithoutAnOutageIsNotReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    assertThat(reporter.recordReachableWithoutSession(PEER, DOMAIN))
        .as("nothing was reported about this peer, so there is no outage to close: an abort"
            + " which reports itself is the handshake's to report").isFalse();
  }

  @Test
  public void aPeerWhichKeepsAnsweringWithoutASessionIsReportedOnce() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordReachableWithoutSession(PEER, DOMAIN);

    assertThat(reporter.recordReachableWithoutSession(PEER, DOMAIN))
        .as("the connect thread retries every few seconds, and a peer which aborts every"
            + " handshake would otherwise be reported on each of them").isFalse();
  }

  /**
   * The recovery which follows an answer without a session is the one a present-or-absent
   * record cannot report: the answer would have consumed the record, and the handshake which
   * completes seconds later would find nothing left to close. A peer restarting takes that
   * path -- its port answers before its domains are up -- and the operator would be left with
   * a warning saying no change is replicated over a connection which is replicating.
   */
  @Test
  public void aSessionEstablishedAfterAnAnswerWithoutOneIsReported() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordReachableWithoutSession(PEER, DOMAIN);

    assertThat(reporter.recordConnected(PEER, DOMAIN))
        .as("the last thing reported about this peer said it had no session, so the session"
            + " it now has has to be reported").isTrue();
  }

  @Test
  public void aPeerWhichGoesDownAfterAnsweringWithoutASessionIsReportedAgain() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordReachableWithoutSession(PEER, DOMAIN);

    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("a peer which answered and now does not is unreachable again, which is not what"
            + " the answer without a session reported").isTrue();
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
    reporter.recordConnected(PEER, DOMAIN);

    assertThat(reporter.recordFailure(PEER, DOMAIN))
        .as("a peer which goes down again is a new failure, not the one already reported").isTrue();
  }

  /**
   * A session is established for one domain at a time, and what a connection ends is the
   * outage of the domain it was made for. Forgetting the peer instead leaves the domains
   * which are still down looking like domains nothing was reported about: each of them is
   * reported down a second time on the next pass of the connect thread, and reported
   * recovered on the pass after that, for as long as they stay down.
   */
  @Test
  public void aConnectionForOneDomainLeavesTheOtherDomainsOfThePeerRecorded() throws Exception
  {
    final ConnectFailureReporter reporter = new ConnectFailureReporter();

    reporter.recordFailure(PEER, DOMAIN);
    reporter.recordFailure(PEER, OTHER_DOMAIN);

    reporter.recordConnected(PEER, DOMAIN);

    assertThat(reporter.recordFailure(PEER, OTHER_DOMAIN))
        .as("the other domain of that peer is still down, and its failure is the one already"
            + " reported rather than a new one").isFalse();
    assertThat(reporter.recordConnected(PEER, OTHER_DOMAIN))
        .as("the outage reported for the other domain is still open, so the connection which"
            + " ends it is still to be reported").isTrue();
  }
}
