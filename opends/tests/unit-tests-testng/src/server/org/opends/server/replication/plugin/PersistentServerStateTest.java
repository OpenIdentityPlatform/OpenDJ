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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ServerState;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test the PersistentServerState class.
 */
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
    ServerState origState = new ServerState();
    PersistentServerState state =
      new PersistentServerState(baseDn,  1, origState);
    ChangeNumberGenerator gen1 =
      new ChangeNumberGenerator( 1, origState);
    ChangeNumberGenerator gen2 =
      new ChangeNumberGenerator( 2, origState);

    ChangeNumber cn1 = gen1.newChangeNumber();
    ChangeNumber cn2 = gen2.newChangeNumber();

    assertEquals(state.update(cn1), true);
    assertEquals(state.update(cn2), true);

    state.save();

    PersistentServerState stateSaved = new PersistentServerState(baseDn,  1);
    ChangeNumber cn1Saved = stateSaved.getMaxChangeNumber( 1);
    ChangeNumber cn2Saved = stateSaved.getMaxChangeNumber( 2);

    assertEquals(cn1Saved, cn1,
        "cn1 has not been saved or loaded correctly for " + dn);
    assertEquals(cn2Saved, cn2,
        "cn2 has not been saved or loaded correctly for " + dn);

    state.clear();
    stateSaved = new PersistentServerState(baseDn,  1);
    cn1Saved = stateSaved.getMaxChangeNumber( 1);
    assertEquals(cn1Saved, null,
        "cn1 has not been saved after clear for " + dn);

  }

  /**
   * Ensures that the Directory Server is able to
   * translate a ruv entry to a sever state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void translateRuvEntryTest()
         throws Exception
  {
    LDAPReplicationDomain replDomain = null;

    try
    {
      String RuvString =
        "dn: nsuniqueid=ffffffff-ffffffff-ffffffff-ffffffff, o=test\n"
        +"objectClass: top\n"
        +"objectClass: ldapsubentry\n"
        +"objectClass: extensibleobject\n"
        +"nsds50ruv: {replicageneration} 49098853000000010000\n"
        +"nsds50ruv: {replica 3 ldap://kawax:3389} 491d517b000000030000 "
        +"491d564a000000030000\n"
        +"nsds50ruv: {replica 1 ldap://kawax:1389} 490989e8000000010000 "
        +"490989e8000000010000\n"
        +"ds6ruv: {PRIO 3 ldap://kawax:3389}\n"
        +"ds6ruv: {PRIO 1 ldap://kawax:1389}\n"
        +"entryUUID: ffffffff-ffff-ffff-ffff-ffffffffffff\n";

      Entry RuvEntry = TestCaseUtils.entryFromLdifString(RuvString);
      AddOperationBasis addOp = new AddOperationBasis(InternalClientConnection.
          getRootConnection(), InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(), null, RuvEntry.getDN(),
          RuvEntry.getObjectClasses(), RuvEntry.getUserAttributes(),
          RuvEntry.getOperationalAttributes());

      addOp.setInternalOperation(true);
      addOp.run();

      assertTrue(addOp.getResultCode() == ResultCode.SUCCESS);

      DomainFakeCfg domainConf =
        new DomainFakeCfg("o=test", 1, "localhost:3389");
      replDomain = MultimasterReplication.createNewDomain(domainConf);
      replDomain.start();

      // Then check serverSate and GenId
      assertTrue(replDomain.getGenerationID() == 1225361491);

      ServerState state = replDomain.getServerState();
      assertTrue(state.getMaxChangeNumber( 1).
          compareTo(new ChangeNumber("0000011d4d42b240000100000000")) == 0);
      assertTrue(state.getMaxChangeNumber( 3).
          compareTo(new ChangeNumber("0000011d9a991110000300000000")) == 0);

    }
    finally
    {
      if (replDomain != null)
        MultimasterReplication.deleteDomain(DN.decode("o=test"));
    }
  }
}
