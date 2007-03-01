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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.plugin;

import static org.testng.Assert.assertEquals;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.common.ChangeNumberGenerator;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the PersistentServerState class.
 */
public class PersistentStateTest extends SynchronizationTestCase
{
  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *         If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    /*
     * start the server and create the dc=exmaple,dc=xom entry if it does not
     * exist yet.
     */
    TestCaseUtils.startServer();
    String topEntry = "dn: dc=example,dc=com\n" + "objectClass: top\n"
    + "objectClass: domain\n";

    connection = InternalClientConnection.getRootConnection();
    Entry entry = TestCaseUtils.entryFromLdifString(topEntry);
    AddOperation addOp = new AddOperation(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
  }

  /**
   * The suffix for which we want to test the PersistentServerState class.
   */
  @DataProvider(name = "suffix")
  public Object[][] suffixData() {
    return new Object[][] {
       {"dc=example,dc=com"},
       {"cn=schema"}
    };
  }

  /**
   * Test that the PersistentServerState class is able to store and
   * retrieve ServerState to persistent storage.
   */
  @Test(dataProvider = "suffix")
  public void persistenServerStateTest(String dn)
         throws Exception
  {
    /*
     * Create a new PersitentServerState,
     * update it with 2 new ChangeNumbers with 2 different server Ids
     * save it
     *
     * Then creates a new PersistentServerState and check that the
     * 2 ChangeNumbers have been saved in this new PersistentServerState.
     */
    DN baseDn = DN.decode(dn);
    PersistentServerState state = new PersistentServerState(baseDn);
    ChangeNumberGenerator gen1 = new ChangeNumberGenerator((short) 1, state);
    ChangeNumberGenerator gen2 = new ChangeNumberGenerator((short) 2, state);

    ChangeNumber cn1 = gen1.NewChangeNumber();
    ChangeNumber cn2 = gen2.NewChangeNumber();

    state.update(cn1);
    state.update(cn2);

    state.save();

    PersistentServerState stateSaved = new PersistentServerState(baseDn);
    ChangeNumber cn1Saved = stateSaved.getMaxChangeNumber((short) 1);
    ChangeNumber cn2Saved = stateSaved.getMaxChangeNumber((short) 2);

    assertEquals(cn1Saved, cn1,
        "cn1 has not been saved or loaded correctly for " + dn);
    assertEquals(cn2Saved, cn2,
        "cn2 has not been saved or loaded correctly for " + dn);

  }
}
