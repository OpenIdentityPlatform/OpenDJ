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
package org.opends.server.replication.common;

import static org.testng.Assert.*;

import java.util.Set;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the ServerState
 */
public class ServerStateTest extends ReplicationTestCase
{

  /**
   * Create ChangeNumber Data
   */
  @DataProvider(name = "changeNumberData")
  public Object[][] createChangeNumberData() {
    return new Object[][] {
       {new ChangeNumber(1, (short) 0, (short) 1)},
       {new ChangeNumber(TimeThread.getTime(), (short) 123, (short) 45)}
    };
  }

  /**
   * Create a new ServerState object
   */
  @Test(dataProvider = "changeNumberData")
  public void serverStateTest(ChangeNumber cn)
         throws Exception
  {
    // Check constructor
    ServerState serverState = new ServerState() ;

    // Check Load
    // serverState.loadState() ;
    // TODO Check result;

    // Check update
    assertFalse(serverState.update(null));
    assertTrue(serverState.update(cn));
    assertFalse(serverState.update(cn));
    ChangeNumber cn1, cn2, cn3;
    cn1 = new ChangeNumber(cn.getTime()+1,cn.getSeqnum(),cn.getServerId());
    cn2 = new ChangeNumber(cn1.getTime(),cn1.getSeqnum()+1,cn1.getServerId());
    cn3 = new ChangeNumber(cn2.getTime(),cn2.getSeqnum(),(short)(cn2.getServerId()+1));

    assertTrue(serverState.update(cn1)) ;
    assertTrue(serverState.update(cn2)) ;
    assertTrue(serverState.update(cn3)) ;

    // Check toStringSet
    ChangeNumber[] cns = {cn2,cn3};
    Set<String> stringSet = serverState.toStringSet();
    assertEquals(cns.length, stringSet.size());
    // TODO Check the value

    // Check getMaxChangeNumber
    assertEquals(cn2.compareTo(serverState.getMaxChangeNumber(cn2.getServerId())),0);
    assertEquals(cn3.compareTo(serverState.getMaxChangeNumber(cn3.getServerId())),0);

    // Check the toString
    String stringRep = serverState.toString();
    assertTrue(stringRep.contains(cn2.toString()));
    assertTrue(stringRep.contains(cn3.toString()));

    // Check getBytes
    byte[] b = serverState.getBytes();
    ServerState generatedServerState = new ServerState(b,0,b.length -1) ;



    assertEquals(b, generatedServerState.getBytes()) ;

  }

  /**
   * Create a new ServerState object
   */
  @Test(dataProvider = "changeNumberData")
  public void serverStateReloadTest(ChangeNumber cn)
  throws Exception
  {
    ChangeNumber cn1, cn3;
    cn1 = new ChangeNumber(cn.getTime()+1,cn.getSeqnum(),cn.getServerId());
    cn3 = new ChangeNumber(cn1.getTime(),cn1.getSeqnum(),(short)(cn1.getServerId()+1));

    ServerState state1 = new ServerState();
    state1.update(cn1);
    state1.update(cn3);

    ServerState state2 = new ServerState();
    state2.reload(state1);

    assertEquals(state1.toString(), state2.toString()) ;

  }

  /**
   * Ensures that the Directory Server is able to
   * translate a ruv entry to a sever state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void translateRuvEntryTest()
         throws Exception
  {

    Entry synchroServerEntry = null;

    String RuvString =
      "dn: nsuniqueid=ffffffff-ffffffff-ffffffff-ffffffff, o=test\n"
      +"objectClass: top\n"
      +"objectClass: extensibleobject\n"
      +"objectClass: ldapSubEntry\n"
      +"o: test\n"
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

    // Instantiate a Replication domain
    // suffix synchronized
    String synchroServerLdif =
      "dn: cn=o=test, cn=domains, cn=Multimaster Synchronization, cn=Synchronization Providers,cn=config\n"
      + "objectClass: top\n"
      + "objectClass: ds-cfg-replication-domain\n"
      + "cn: o=test\n"
      + "ds-cfg-base-dn: o=test\n"
      + "ds-cfg-replication-server: localhost:3389\n"
      + "ds-cfg-server-id: 1\n"
      + "ds-cfg-receive-status: true\n"
      + "ds-cfg-window-size: 10";

    // When adding the replicationDomain entry the checkRUVCompat
    // method is called and should translate the RuvEntry Added
    // into a serverState + generationId
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);
    DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
      "Unable to add the synchronized server");

    LDAPReplicationDomain replDomain = LDAPReplicationDomain.
    retrievesReplicationDomain(DN.decode("o=test"));

    // Then check serverSate and GenId
    assertTrue(replDomain.getGenerationID() == 1225361491);

    ServerState state = replDomain.getServerState();
    assertTrue(state.getMaxChangeNumber((short) 1).
        compareTo(new ChangeNumber("0000011d4d42b240000100000000")) == 0);
    assertTrue(state.getMaxChangeNumber((short) 3).
        compareTo(new ChangeNumber("0000011d9a991110000300000000")) == 0);

    // Remove the configuration entry
    DirectoryServer.getConfigHandler().deleteEntry(synchroServerEntry.getDN(),
        null);
    assertNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
      "Unable to remove the synchronized server");

  }

}
