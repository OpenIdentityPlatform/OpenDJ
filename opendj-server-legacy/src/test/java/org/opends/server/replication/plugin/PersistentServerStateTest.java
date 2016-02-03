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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerState;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Test the PersistentServerState class.
 */
@SuppressWarnings("javadoc")
public class PersistentServerStateTest extends ReplicationTestCase
{
  /**
   * The suffix for which we want to test the PersistentServerState class.
   */
  @DataProvider(name = "suffix")
  public Object[][] suffixData() {
    return new Object[][] {
       {TEST_ROOT_DN_STRING},
       {"cn=schema"}
    };
  }

  /**
   * Test that the PersistentServerState class is able to store and
   * retrieve ServerState to persistent storage.
   */
  @Test(dataProvider = "suffix")
  public void persistentServerStateTest(String dn) throws Exception
  {
    /*
     * Create a new PersistentServerState,
     * update it with 2 new csns with 2 different server Ids
     * save it
     *
     * Then creates a new PersistentServerState and check that the
     * 2 csns have been saved in this new PersistentServerState.
     */
    DN baseDn = DN.valueOf(dn);
    ServerState origState = new ServerState();
    PersistentServerState state =
        new PersistentServerState(baseDn,  1, origState);
    CSNGenerator gen1 = new CSNGenerator(1, origState);
    CSNGenerator gen2 = new CSNGenerator(2, origState);

    CSN csn1 = gen1.newCSN();
    CSN csn2 = gen2.newCSN();

    assertEquals(state.update(csn1), true);
    assertEquals(state.update(csn2), true);
    state.save();

    PersistentServerState stateSaved =
        new PersistentServerState(baseDn, 1, new ServerState());
    CSN csn1Saved = stateSaved.getMaxCSN(1);
    CSN csn2Saved = stateSaved.getMaxCSN(2);

    assertEquals(csn1Saved, csn1,
        "csn1 has not been saved or loaded correctly for " + dn);
    assertEquals(csn2Saved, csn2,
        "csn2 has not been saved or loaded correctly for " + dn);

    state.clear();
    stateSaved = new PersistentServerState(baseDn, 1, new ServerState());
    csn1Saved = stateSaved.getMaxCSN(1);
    assertNull(csn1Saved, "csn1 has not been saved after clear for " + dn);
  }
}
