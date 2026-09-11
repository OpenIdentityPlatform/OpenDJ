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

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ReplicationMessages.ERR_UPDATING_GENERATION_ID;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a replication domain whose base entry does not exist keeps its bookkeeping out of
 * its own configuration entry.
 * <p>
 * Writing it there closes a lock cycle: the write goes through the configuration backend, which
 * holds its update lock while it calls the domain back, and the callback takes the very lock
 * {@code disable()} holds while it saves the state.
 */
@SuppressWarnings("javadoc")
public class StateWithoutBaseEntryTest extends ReplicationTestCase
{
  private static final String SYNC_STATE = "ds-sync-state";
  private static final String GENERATION_ID = "ds-sync-generation-id";
  /** Kept clear of the server id of the domain started below, which has a state of its own. */
  private static final int STATE_SERVER_ID = 42;

  private DN baseDN;

  @Override
  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception
  {
    super.setUp();

    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    /*
     * The suffix has a backend but no base entry, which is what a domain configured over a
     * backend waiting to be initialized by an import looks like. It is also what sends the
     * bookkeeping writes to the configuration entry.
     */
    TestCaseUtils.initializeTestBackend(false);

    final int replServerPort = TestCaseUtils.findFreePort();
    final String replServerLdif =
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: StateWithoutBaseEntryTest\n"
        + "ds-cfg-replication-server-id: 105\n";
    final String synchroServerLdif =
        "dn: cn=stateWithoutBaseEntryTest, cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: stateWithoutBaseEntryTest\n"
        + "ds-cfg-base-dn: " + baseDN + "\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: 1\n"
        + "ds-cfg-receive-status: true\n";

    configureReplication(replServerLdif, synchroServerLdif);
  }

  @Test
  public void aStateSaveWithoutABaseEntryWritesNothingToTheConfigurationEntry() throws Exception
  {
    final ServerState state = new ServerState();
    final PersistentServerState persistentState = new PersistentServerState(baseDN, STATE_SERVER_ID, state);
    assertThat(persistentState.update(new CSNGenerator(STATE_SERVER_ID, state).newCSN())).isTrue();

    persistentState.save();

    assertThat(domainConfigEntry().getAllAttributes(SYNC_STATE))
        .as("the state of a domain whose base entry is missing reached its configuration entry")
        .isEmpty();
  }

  @Test
  public void aDomainWithoutABaseEntryWritesNoGenerationIdToItsConfigurationEntry() throws Exception
  {
    /*
     * The domain computed and stored its generationId as it was started in setUp(), which is
     * enough to fail this before the fix whichever order the methods run in. The other store
     * this class provokes - the disable()/enable() below - has nowhere else to write either.
     */
    assertThat(domainConfigEntry().getAllAttributes(GENERATION_ID))
        .as("the generationId of a domain whose base entry is missing reached its configuration entry")
        .isEmpty();
  }

  @Test
  public void aBaseEntryWhichIsNotThereIsNoFailureToStoreTheGenerationId() throws Exception
  {
    final LDAPReplicationDomain domain = MultimasterReplication.findDomain(baseDN, null);
    assertThat(domain).as("the domain of this test is gone").isNotNull();
    // The record the error logger writes carries the id of the message rather than its text,
    // so what is looked for here does not depend on the locale the tests run under.
    final String failedWrite =
        "msgID=" + ERR_UPDATING_GENERATION_ID.get(baseDN, ResultCode.NO_SUCH_OBJECT.getName()).ordinal();

    TestCaseUtils.ERROR_TEXT_WRITER.clear();
    // Stores the generationId again, the way the end of an import does.
    domain.disable();
    domain.enable();

    assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
        .as("a base entry which is not there yet was reported as a failure to store the generationId")
        .noneMatch(record -> record.contains(failedWrite));
  }

  private Entry domainConfigEntry() throws Exception
  {
    final Entry configEntry = DirectoryServer.getEntry(synchroServerEntry.getName());
    assertThat(configEntry).as("the domain configuration entry is gone").isNotNull();
    return configEntry;
  }
}
