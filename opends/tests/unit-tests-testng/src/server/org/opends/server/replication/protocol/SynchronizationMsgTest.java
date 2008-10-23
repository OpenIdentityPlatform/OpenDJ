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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.DataFormatException;

import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.opends.messages.Message;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test the constructors, encoders and decoders of the replication protocol
 * PDUs classes (message classes)
 */
public class SynchronizationMsgTest extends ReplicationTestCase
{
  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    // Be sure we use the latest protocol version for these tests
    ProtocolVersion.resetCurrentVersion();
  }

  /**
   * Build some data for the ModifyMsg test below.
   */
  @DataProvider(name = "createModifyData")
  public Object[][] createModifyData() {
    ChangeNumber cn1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);

    AttributeType type = DirectoryServer.getAttributeType("description");

    Attribute attr1 = Attributes.create("description", "new value");
    Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
    List<Modification> mods1 = new ArrayList<Modification>();
    mods1.add(mod1);

    Attribute attr2 = Attributes.empty("description");
    Modification mod2 = new Modification(ModificationType.DELETE, attr2);
    List<Modification> mods2 = new ArrayList<Modification>();
    mods2.add(mod1);
    mods2.add(mod2);

    AttributeBuilder builder = new AttributeBuilder(type);
    List<Modification> mods3 = new ArrayList<Modification>();
    builder.add("string");
    builder.add("value");
    builder.add("again");
    Attribute attr3 = builder.toAttribute();
    Modification mod3 = new Modification(ModificationType.ADD, attr3);
    mods3.add(mod3);

    List<Modification> mods4 = new ArrayList<Modification>();
    for (int i = 0; i < 10; i++)
    {
      Attribute attr = Attributes.create("description", "string"
          + String.valueOf(i));
      Modification mod = new Modification(ModificationType.ADD, attr);
      mods4.add(mod);
    }

    Attribute attr5 = Attributes.create("namingcontexts", TEST_ROOT_DN_STRING);
    Modification mod5 = new Modification(ModificationType.REPLACE, attr5);
    List<Modification> mods5 = new ArrayList<Modification>();
    mods5.add(mod5);

    return new Object[][] {
        { cn1, "dc=test", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0},
        { cn2, "dc=cn2", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)1},
        { cn2, "dc=test with a much longer dn in case this would "
               + "make a difference", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3},
        { cn2, "dc=test, cn=with a, o=more complex, ou=dn", mods1, false, AssuredMode.SAFE_READ_MODE, (byte)5},
        { cn2, "cn=use\\, backslash", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3},
        { cn2, "dc=test with several mod", mods2, false, AssuredMode.SAFE_DATA_MODE, (byte)16},
        { cn2, "dc=test with several values", mods3, false, AssuredMode.SAFE_READ_MODE, (byte)3},
        { cn2, "dc=test with long mod", mods4, true, AssuredMode.SAFE_READ_MODE, (byte)120},
        { cn2, "dc=testDsaOperation", mods5, true, AssuredMode.SAFE_DATA_MODE, (byte)99},
        };
  }

  /**
   * Create a ModifyMsg from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(dataProvider = "createModifyData")
  public void modifyMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel)
         throws Exception
  {
    DN dn = DN.decode(rawdn);
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg
        .generateMsg(msg.getBytes());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());

    Operation op = msg.createOperation(connection);
    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyOperationBasis.class);

    ModifyOperationBasis mod1 = (ModifyOperationBasis) op;
    ModifyOperationBasis mod2 = (ModifyOperationBasis) generatedOperation;

    assertEquals(mod1.getRawEntryDN(), mod2.getRawEntryDN());
    assertEquals( mod1.getAttachment(SYNCHROCONTEXT),
                  mod2.getAttachment(SYNCHROCONTEXT));
    assertEquals(mod1.getModifications(), mod2.getModifications());
  }

  /**
   * Create an Update Message from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msgs match.
   */
  @Test(dataProvider = "createModifyData")
  public void updateMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel)
         throws Exception
  {
    DN dn = DN.decode(rawdn);
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");

    // Check isAssured
    assertFalse(msg.isAssured());
    msg.setAssured(isAssured);
    assertEquals(msg.isAssured(), isAssured);

    // Check assured mode
    assertEquals(msg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    msg.setAssuredMode(assuredMode);
    assertEquals(msg.getAssuredMode(), assuredMode);

    // Check safe data level
    assertTrue(msg.getSafeDataLevel() == -1);
    msg.setSafeDataLevel(safeDataLevel);
    assertTrue(msg.getSafeDataLevel() == safeDataLevel);

    // Check equals
    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg
        .generateMsg(msg.getBytes());
    assertFalse(msg.equals(null));
    assertFalse(msg.equals(new Object()));

    // Check change number
    assertTrue(msg.equals(generatedMsg));

    // Check hashCode
    assertEquals(msg.hashCode(), generatedMsg.hashCode());

    // Check compareTo
    assertEquals(msg.compareTo(generatedMsg), 0);

    // Check Get / Set DN
    assertTrue(DN.decode(msg.getDn()).equals(DN.decode(generatedMsg.getDn())));

    String fakeDN = "cn=fake cn";
    msg.setDn(fakeDN) ;
    assertEquals(msg.getDn(), fakeDN) ;

    // Check uuid
    assertEquals(msg.getUniqueId(), generatedMsg.getUniqueId());

    // Check assured flag
    assertEquals(msg.isAssured(), generatedMsg.isAssured());

    // Check assured mode
    assertEquals(msg.getAssuredMode(), generatedMsg.getAssuredMode());

    // Check safe data level
    assertEquals(msg.getSafeDataLevel(), generatedMsg.getSafeDataLevel());
}

  /**
   * Build some data for the DeleteMsg test below.
   */
  @DataProvider(name = "createDeleteData")
  public Object[][] createDeleteData() {
    return new Object[][] {
        {"dc=com"},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn"},
        };
  }

  /**
   * Create a Delete from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another DeleteMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(dataProvider = "createDeleteData")
  public void deleteMsgTest(String rawDN)
         throws Exception
  {
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    DeleteOperationBasis opBasis =
      new DeleteOperationBasis(connection, 1, 1,null, DN.decode(rawDN));
    LocalBackendDeleteOperation op = new LocalBackendDeleteOperation(opBasis);
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
      (short) 123, (short) 45);
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn, "uniqueid"));
    DeleteMsg msg = new DeleteMsg(op);
    DeleteMsg generatedMsg = (DeleteMsg) ReplicationMsg
        .generateMsg(msg.getBytes());

    assertEquals(msg.toString(), generatedMsg.toString());

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());

    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(generatedOperation.getClass(), DeleteOperationBasis.class);

    DeleteOperationBasis mod2 = (DeleteOperationBasis) generatedOperation;

    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());

    // Create an update message from this op
    DeleteMsg updateMsg = (DeleteMsg) UpdateMsg.generateMsg(op);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  @DataProvider(name = "createModifyDnData")
  public Object[][] createModifyDnData() {
    return new Object[][] {
        {"dc=test,dc=com", "dc=new", false, "dc=change", false, AssuredMode.SAFE_DATA_MODE, (byte)0},
        {"dc=test,dc=com", "dc=new", true, "dc=change", true, AssuredMode.SAFE_READ_MODE, (byte)1},
        // testNG does not like null argument so use "" for the newSuperior
        // instead of null
        {"dc=test,dc=com", "dc=new", false, "", true, AssuredMode.SAFE_READ_MODE, (byte)3},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn",
                   "dc=new",true, "", true, AssuredMode.SAFE_DATA_MODE, (byte)99},
        };
  }

  @Test(dataProvider = "createModifyDnData")
  public void modifyDnTest(String rawDN, String newRdn,
                                   boolean deleteOldRdn, String newSuperior,
                                   boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel)
         throws Exception
  {
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();
    ModifyDNOperationBasis op =
      new ModifyDNOperationBasis(connection, 1, 1, null,
                  DN.decode(rawDN), RDN.decode(newRdn), deleteOldRdn,
                  (newSuperior.length() != 0 ? DN.decode(newSuperior) : null));

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 123, (short) 45);
    op.setAttachment(SYNCHROCONTEXT,
        new ModifyDnContext(cn, "uniqueid", "newparentId"));
    LocalBackendModifyDNOperation localOp =
      new LocalBackendModifyDNOperation(op);
    ModifyDNMsg msg = new ModifyDNMsg(localOp);

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    ModifyDNMsg generatedMsg = (ModifyDNMsg) ReplicationMsg
        .generateMsg(msg.getBytes());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    Operation generatedOperation = generatedMsg.createOperation(connection);
    ModifyDNOperationBasis mod2 = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());
    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());
    assertEquals(op.getRawNewRDN(), mod2.getRawNewRDN());
    assertEquals(op.deleteOldRDN(), mod2.deleteOldRDN());
    assertEquals(op.getRawNewSuperior(), mod2.getRawNewSuperior());

    // Create an update message from this op
    ModifyDNMsg updateMsg = (ModifyDNMsg) UpdateMsg.generateMsg(localOp);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  @DataProvider(name = "createAddData")
  public Object[][] createAddData() {
    return new Object[][] {
      {"dc=example,dc=com", false, AssuredMode.SAFE_DATA_MODE, (byte)0},
      {"o=test", true, AssuredMode.SAFE_READ_MODE, (byte)1},
      {"o=group,dc=example,dc=com", true, AssuredMode.SAFE_READ_MODE, (byte)3}};
  }

  @Test(dataProvider = "createAddData")
  public void addMsgTest(String rawDN, boolean isAssured, AssuredMode assuredMode,
    byte safeDataLevel)
         throws Exception
  {
    Attribute objectClass = Attributes.create(DirectoryServer
        .getObjectClassAttributeType(), "organization");
    HashMap<ObjectClass, String> objectClassList = new HashMap<ObjectClass, String>();
    objectClassList.put(DirectoryServer.getObjectClass("organization"),
        "organization");

    ArrayList<Attribute> userAttributes = new ArrayList<Attribute>(1);
    Attribute attr = Attributes.create("o", "com");
    userAttributes.add(attr);
    HashMap<AttributeType, List<Attribute>> userAttList = new HashMap<AttributeType, List<Attribute>>();
    userAttList.put(attr.getAttributeType(), userAttributes);


    ArrayList<Attribute> operationalAttributes = new ArrayList<Attribute>(1);
    attr = Attributes.create("creatorsname", "dc=creator");
    operationalAttributes.add(attr);
    HashMap<AttributeType,List<Attribute>> opList=
      new HashMap<AttributeType,List<Attribute>>();
    opList.put(attr.getAttributeType(), operationalAttributes);

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 123, (short) 45);

    AddMsg msg = new AddMsg(cn, rawDN, "thisIsaUniqueID", "parentUniqueId",
                            objectClass, userAttributes,
                            operationalAttributes);

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    AddMsg generatedMsg = (AddMsg) ReplicationMsg.generateMsg(msg
        .getBytes());
    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.getParentUid(), msg.getParentUid());
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    // Create an new Add Operation from the current addMsg
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    Operation op = msg.createOperation(connection, rawDN);
    Operation generatedOperation = generatedMsg.createOperation(connection, rawDN);

    assertEquals(op.getClass(), AddOperationBasis.class);
    assertEquals(generatedOperation.getClass(), AddOperationBasis.class);

    AddOperationBasis addOpBasis = (AddOperationBasis) op;
    AddOperationBasis genAddOpBasis = (AddOperationBasis) generatedOperation;

    assertEquals(addOpBasis.getRawEntryDN(), genAddOpBasis.getRawEntryDN());
    assertEquals( addOpBasis.getAttachment(SYNCHROCONTEXT),
                  genAddOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(addOpBasis.getObjectClasses(), genAddOpBasis.getObjectClasses());
    assertEquals(addOpBasis.getOperationalAttributes(), genAddOpBasis.getOperationalAttributes());
    assertEquals(addOpBasis.getUserAttributes(), genAddOpBasis.getUserAttributes());

    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());

    //Create an Add operation and generate and Add msg from it
    DN dn = DN.decode(rawDN);

    AddOperation addOpB = new AddOperationBasis(connection,
        (long) 1, 1, null, dn, objectClassList, userAttList, opList);
    LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpB);
    OperationContext opCtx = new AddContext(cn, "thisIsaUniqueID",
        "parentUniqueId");
    addOp.setAttachment(SYNCHROCONTEXT, opCtx);

    generatedMsg = new AddMsg(addOp);

    generatedMsg.setAssured(isAssured);
    generatedMsg.setAssuredMode(assuredMode);
    generatedMsg.setSafeDataLevel(safeDataLevel);

    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());
    // TODO : should test that generated attributes match original attributes.


    // Create an update message from this op
    AddMsg updateMsg = (AddMsg) UpdateMsg.generateMsg(addOp);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  /**
   * Build some data for the AckMsg test below.
   */
  @DataProvider(name = "createAckData")
  public Object[][] createAckData() {
    ChangeNumber cn1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);

    ArrayList<Short> fservers1 = new ArrayList<Short>();
    fservers1.add(new Short((short)12345));
    fservers1.add(new Short((short)-12345));
    fservers1.add(new Short((short)31657));
    fservers1.add(new Short((short)-28456));
    fservers1.add(new Short((short)0));
    ArrayList<Short> fservers2 = new ArrayList<Short>();
    ArrayList<Short> fservers3 = new ArrayList<Short>();
    fservers3.add(new Short((short)0));
    ArrayList<Short> fservers4 = new ArrayList<Short>();
    fservers4.add(new Short((short)100));
    fservers4.add(new Short((short)2000));
    fservers4.add(new Short((short)30000));
    fservers4.add(new Short((short)-100));
    fservers4.add(new Short((short)-2000));
    fservers4.add(new Short((short)-30000));

    return new Object[][] {
        {cn1, true, false, false, fservers1},
        {cn2, false, true, false, fservers2},
        {cn1, false, false, true, fservers3},
        {cn2, false, false, false, fservers4},
        {cn1, true, true, false, fservers1},
        {cn2, false, true, true, fservers2},
        {cn1, true, false, true, fservers3},
        {cn2, true, true, true, fservers4}
        };
  }

  @Test(dataProvider = "createAckData")
  public void ackMsgTest(ChangeNumber cn, boolean hasTimeout, boolean hasWrongStatus,
    boolean hasReplayError, List<Short> failedServers)
         throws Exception
  {
    AckMsg msg1, msg2 ;

    // Consctructor test (with ChangeNumber)
    // Chech that retrieved CN is OK
    msg1 = new  AckMsg(cn);
    assertEquals(msg1.getChangeNumber().compareTo(cn), 0);

    // Check default values for error info
    assertFalse(msg1.hasTimeout());
    assertFalse(msg1.hasWrongStatus());
    assertFalse(msg1.hasReplayError());
    assertTrue(msg1.getFailedServers().size() == 0);

    // Check constructor with error info
    msg1 = new  AckMsg(cn, hasTimeout, hasWrongStatus, hasReplayError, failedServers);
    assertEquals(msg1.getChangeNumber().compareTo(cn), 0);
    assertTrue(msg1.hasTimeout() == hasTimeout);
    assertTrue(msg1.hasWrongStatus() == hasWrongStatus);
    assertTrue(msg1.hasReplayError() == hasReplayError);
    assertEquals(msg1.getFailedServers(), failedServers);

    // Consctructor test (with byte[])
    msg2 = new  AckMsg(msg1.getBytes());
    assertEquals(msg2.getChangeNumber().compareTo(cn), 0);
    assertTrue(msg1.hasTimeout() == msg2.hasTimeout());
    assertTrue(msg1.hasWrongStatus() == msg2.hasWrongStatus());
    assertTrue(msg1.hasReplayError() == msg2.hasReplayError());
    assertEquals(msg1.getFailedServers(), msg2.getFailedServers());

    // Check invalid bytes for constructor
    byte[] b = msg1.getBytes();
    b[0] = ReplicationMsg.MSG_TYPE_ADD;
    try
    {
      // This should generated an exception
      msg2 = new  AckMsg(b);
      assertTrue(false);
    }
    catch (DataFormatException e)
    {
      assertTrue(true);
    }

    // Check that retrieved CN is OK
    msg2 = (AckMsg) ReplicationMsg.generateMsg(msg1.getBytes());
  }

  @DataProvider(name="createServerStartData")
  public Object [][] createServerStartData() throws Exception
  {
    DN baseDN = DN.decode(TEST_ROOT_DN_STRING);
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0,(short)0));
    Object[] set1 = new Object[] {(short)1, baseDN, 0, state, 0L, false, (byte)0};

    baseDN = DN.decode(TEST_ROOT_DN_STRING);
    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5,(short)263));
    Object[] set2 = new Object[] {(short)16, baseDN, 100, state, 1248L, true, (byte)31};

    return new Object [][] { set1, set2 };
  }

  /**
   * Test that ServerStartMsg encoding and decoding works
   * by checking that : msg == new ServerStartMsg(msg.getBytes()).
   */
  @Test(dataProvider="createServerStartData")
  public void serverStartMsgTest(short serverId, DN baseDN, int window,
         ServerState state, long genId, boolean sslEncryption, byte groupId) throws Exception
  {
    ServerStartMsg msg = new ServerStartMsg(serverId, baseDN,
        window, window, window, window, window, window, state,
        ProtocolVersion.getCurrentVersion(), genId, sslEncryption, groupId);
    ServerStartMsg newMsg = new ServerStartMsg(msg.getBytes());
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getMaxReceiveDelay(), newMsg.getMaxReceiveDelay());
    assertEquals(msg.getMaxReceiveQueue(), newMsg.getMaxReceiveQueue());
    assertEquals(msg.getMaxSendDelay(), newMsg.getMaxSendDelay());
    assertEquals(msg.getMaxSendQueue(), newMsg.getMaxSendQueue());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getHeartbeatInterval(), newMsg.getHeartbeatInterval());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertEquals(msg.getServerState().getMaxChangeNumber((short)1),
        newMsg.getServerState().getMaxChangeNumber((short)1));
    assertEquals(msg.getVersion(), newMsg.getVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
  }

  @DataProvider(name="createReplServerStartData")
  public Object [][] createReplServerStartData() throws Exception
  {
    DN baseDN = DN.decode(TEST_ROOT_DN_STRING);
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0,(short)0));
    Object[] set1 = new Object[] {(short)1, baseDN, 0, "localhost:8989", state, 0L, (byte)0, 0};

    baseDN = DN.decode(TEST_ROOT_DN_STRING);
    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5,(short)263));
    Object[] set2 = new Object[] {(short)16, baseDN, 100, "anotherHost:1025", state, 1245L, (byte)25, 3456};

    return new Object [][] { set1, set2 };
  }

  /**
   * Test that ReplServerStartMsg encoding and decoding works
   * by checking that : msg == new ReplServerStartMsg(msg.getBytes()).
   */
  @Test(dataProvider="createReplServerStartData")
  public void replServerStartMsgTest(short serverId, DN baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    ReplServerStartMsg msg = new ReplServerStartMsg(serverId,
        url, baseDN, window, state, ProtocolVersion.getCurrentVersion(), genId,
        true, groupId, degTh);
    ReplServerStartMsg newMsg = new ReplServerStartMsg(msg.getBytes());
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber((short)1),
        newMsg.getServerState().getMaxChangeNumber((short)1));
    assertEquals(msg.getVersion(), newMsg.getVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
    assertTrue(msg.getDegradedStatusThreshold() ==
      newMsg.getDegradedStatusThreshold());
  }

  /**
   * Test that WindowMsg encoding and decoding works
   * by checking that : msg == new WindowMsg(msg.getBytes()).
   */
  @Test()
  public void windowMsgTest() throws Exception
  {
    WindowMsg msg = new WindowMsg(123);
    WindowMsg newMsg = new WindowMsg(msg.getBytes());
    assertEquals(msg.getNumAck(), newMsg.getNumAck());
  }

  /**
   * Test that WindowProbeMsg encoding and decoding works
   * by checking that : new WindowProbeMsg(msg.getBytes()) does not throws
   * an exception.
   */
  @Test()
  public void windowProbeMsgTest() throws Exception
  {
    WindowProbeMsg msg = new WindowProbeMsg();
    new WindowProbeMsg(msg.getBytes());
  }

  @DataProvider(name="createTopologyData")
  public Object [][] createTopologyData() throws Exception
  {
    List<String> urls1 = new ArrayList<String>();
    urls1.add("ldap://ldap.iplanet.com/" + TEST_ROOT_DN_STRING + "??sub?(sn=Jensen)");
    urls1.add("ldaps://ldap.iplanet.com:4041/uid=bjensen,ou=People," +
      TEST_ROOT_DN_STRING + "?cn,mail,telephoneNumber");

    List<String> urls2 = new ArrayList<String>();

    List<String> urls3 = new ArrayList<String>();
    urls3.add("ldaps://host:port/dc=foo??sub?(sn=One Entry)");
    
    List<String> urls4 = new ArrayList<String>();
    urls4.add("ldaps://host:port/dc=foobar1??sub?(sn=Another Entry 1)");
    urls4.add("ldaps://host:port/dc=foobar2??sub?(sn=Another Entry 2)");

    DSInfo dsInfo1 = new DSInfo((short)13, (short)26, (long)154631, ServerStatus.FULL_UPDATE_STATUS,
      false, AssuredMode.SAFE_DATA_MODE, (byte)12, (byte)132, urls1);

    DSInfo dsInfo2 = new DSInfo((short)-436, (short)493, (long)-227896, ServerStatus.DEGRADED_STATUS,
      true, AssuredMode.SAFE_READ_MODE, (byte)-7, (byte)-265, urls2);

    DSInfo dsInfo3 = new DSInfo((short)2436, (short)591, (long)0, ServerStatus.NORMAL_STATUS,
      false, AssuredMode.SAFE_READ_MODE, (byte)17, (byte)0, urls3);
    
    DSInfo dsInfo4 = new DSInfo((short)415, (short)146, (long)0, ServerStatus.BAD_GEN_ID_STATUS,
      true, AssuredMode.SAFE_DATA_MODE, (byte)2, (byte)15, urls4);

    List<DSInfo> dsList1 = new ArrayList<DSInfo>();
    dsList1.add(dsInfo1);

    List<DSInfo> dsList2 = new ArrayList<DSInfo>();

    List<DSInfo> dsList3 = new ArrayList<DSInfo>();
    dsList3.add(dsInfo2);

    List<DSInfo> dsList4 = new ArrayList<DSInfo>();
    dsList4.add(dsInfo4);
    dsList4.add(dsInfo3);
    dsList4.add(dsInfo2);
    dsList4.add(dsInfo1);

    RSInfo rsInfo1 = new RSInfo((short)4527, (long)45316, (byte)103);

    RSInfo rsInfo2 = new RSInfo((short)4527, (long)0, (byte)0);

    RSInfo rsInfo3 = new RSInfo((short)0, (long)-21113, (byte)98);

    List<RSInfo> rsList1 = new ArrayList<RSInfo>();
    rsList1.add(rsInfo1);

    List<RSInfo> rsList2 = new ArrayList<RSInfo>();
    rsList2.add(rsInfo1);
    rsList2.add(rsInfo2);
    rsList2.add(rsInfo3);

    return new Object [][] {
      {dsList1, rsList1},
      {dsList2, rsList2},
      {dsList3, rsList1},
      {dsList3, null},
      {null, rsList1},
      {null, null},
      {dsList4, rsList2}
    };
  }

  /**
   * Test TopologyMsg encoding and decoding.
   */
  @Test(dataProvider = "createTopologyData")
  public void topologyMsgTest(List<DSInfo> dsList, List<RSInfo> rsList)
    throws Exception
  {
    TopologyMsg msg = new TopologyMsg(dsList, rsList);
    TopologyMsg newMsg = new TopologyMsg(msg.getBytes());
    assertEquals(msg.getDsList(), newMsg.getDsList());
    assertEquals(msg.getRsList(), newMsg.getRsList());
  }

  /**
   * Provider for the StartSessionMsg test.
   */
  @DataProvider(name = "createStartSessionData")
  public Object[][] createStartSessionData()
  {
    List<String> urls1 = new ArrayList<String>();
    urls1.add("ldap://ldap.iplanet.com/" + TEST_ROOT_DN_STRING + "??sub?(sn=Jensen)");
    urls1.add("ldaps://ldap.iplanet.com:4041/uid=bjensen,ou=People," +
      TEST_ROOT_DN_STRING + "?cn,mail,telephoneNumber");

    List<String> urls2 = new ArrayList<String>();
    urls2.add("ldap://ldap.example.com/" + TEST_ROOT_DN_STRING + "?objectClass?one");
    urls2.add("ldap://host.example.com/ou=people," + TEST_ROOT_DN_STRING + "???(sn=a*)");

    List<String> urls3 = new ArrayList<String>();
    urls3.add("ldaps://host:port/dc=foo??sub?(sn=John Doe)");
    urls3.add("ldap://jnumail1.state.ak.us/o=state.ak.us?mail,departmentnumber"
      + "?sub?(&(departmentnumber=04*) (l=Juneau))");

    List<String> urls4 = new ArrayList<String>();

    List<String> urls5 = new ArrayList<String>();
    urls5.add("ldaps://host:port/dc=foo??sub?(sn=One Entry)");

    List<String> urls6 = new ArrayList<String>();
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=One Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Second Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Third Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Fourth Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Fifth Entry)");

    return new Object[][]{
      {ServerStatus.NORMAL_STATUS, urls1, true, AssuredMode.SAFE_DATA_MODE, (byte)1},
      {ServerStatus.DEGRADED_STATUS, urls2, false, AssuredMode.SAFE_READ_MODE, (byte)123},
      {ServerStatus.FULL_UPDATE_STATUS, urls3, false, AssuredMode.SAFE_DATA_MODE, (byte)111},
      {ServerStatus.NORMAL_STATUS, urls4, true, AssuredMode.SAFE_READ_MODE, (byte)-1},
      {ServerStatus.DEGRADED_STATUS, urls5, true, AssuredMode.SAFE_DATA_MODE, (byte)97},
      {ServerStatus.FULL_UPDATE_STATUS, urls6, false, AssuredMode.SAFE_READ_MODE, (byte)-13}
    };
  }

  /**
   * Test StartSessionMsg encoding and decoding.
   */
  @Test(dataProvider = "createStartSessionData")
  public void startSessionMsgTest(ServerStatus status, List<String> refUrls,
    boolean assuredFlag, AssuredMode assuredMode, byte safedataLevel)
    throws Exception
  {
    StartSessionMsg msg = new StartSessionMsg(status, refUrls, assuredFlag,
      assuredMode, safedataLevel);
    StartSessionMsg newMsg = new StartSessionMsg(msg.getBytes());
    assertEquals(msg.getStatus(), newMsg.getStatus());
    assertTrue(msg.isAssured() == newMsg.isAssured());
    assertEquals(msg.getAssuredMode(), newMsg.getAssuredMode());
    assertTrue(msg.getSafeDataLevel() == newMsg.getSafeDataLevel());
    assertEquals(msg.getReferralsURLs(), newMsg.getReferralsURLs());
  }
  
  /**
   * Provider for the ChangeStatusMsg test
   */
  @DataProvider(name = "createChangeStatusData")
  public Object[][] createChangeStatusData()
  {
    return new Object[][]{
      {ServerStatus.NORMAL_STATUS, ServerStatus.FULL_UPDATE_STATUS},
      {ServerStatus.DEGRADED_STATUS, ServerStatus.NORMAL_STATUS},
      {ServerStatus.FULL_UPDATE_STATUS, ServerStatus.DEGRADED_STATUS}
    };
  }

  /**
   * Test ChangeStatusMsg encoding and decoding.
   */
  @Test(dataProvider = "createChangeStatusData")
  public void changeStatusMsgTest(ServerStatus reqStatus, ServerStatus newStatus)
    throws Exception
  {
    ChangeStatusMsg msg = new ChangeStatusMsg(reqStatus, newStatus);
    ChangeStatusMsg newMsg = new ChangeStatusMsg(msg.getBytes());
    assertEquals(msg.getRequestedStatus(), newMsg.getRequestedStatus());
    assertEquals(msg.getNewStatus(), newMsg.getNewStatus());
  }

  /**
   * Test HeartbeatMsg encoding and decoding.
   */
  @Test
  public void heartbeatMsgTest() throws Exception
  {
    HeartbeatMsg msg = new HeartbeatMsg();
    HeartbeatMsg newMsg = new HeartbeatMsg(msg.getBytes());
  }

  /**
   * Test ResetGenerationIdMsg encoding and decoding.
   */
  @Test
  public void resetGenerationIdMsgTest() throws Exception
  {
    ResetGenerationIdMsg msg = new ResetGenerationIdMsg(23657);
    ResetGenerationIdMsg newMsg = new ResetGenerationIdMsg(msg.getBytes());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
  }
  
  /**
   * Test MonitorRequestMsg encoding and decoding.
   */
  @Test
  public void monitorRequestMsgTest() throws Exception
  {
    MonitorRequestMsg msg = new MonitorRequestMsg((short)1,(short)2);
    MonitorRequestMsg newMsg = new MonitorRequestMsg(msg.getBytes());
  }

  /**
   * Test MonitorMsg.
   */
  @Test()
  public void monitorMsgTest() throws Exception
  {
    short sender = 2;
    short dest = 3;

    // RS State
    ServerState rsState = new ServerState();
    ChangeNumber rscn1 = new ChangeNumber(1, (short) 1, (short) 1);
    ChangeNumber rscn2 = new ChangeNumber(1, (short) 1, (short) 2);
    rsState.update(rscn1);
    rsState.update(rscn2);

    // LS1 state
    ServerState s1 = new ServerState();
    short sid1 = 111;
    ChangeNumber cn1 = new ChangeNumber(1, (short) 1, sid1);
    s1.update(cn1);

    // LS2 state
    ServerState s2 = new ServerState();
    short sid2 = 222;
    Long now = TimeThread.getTime();
    ChangeNumber cn2 = new ChangeNumber(now,
                                       (short) 123, sid2);
    s2.update(cn2);

    // LS3 state
    ServerState s3 = new ServerState();
    short sid3 = 333;
    ChangeNumber cn3 = new ChangeNumber(now,
                                       (short) 123, sid3);
    s3.update(cn3);

    MonitorMsg msg =
      new MonitorMsg(sender, dest);
    msg.setReplServerDbState(rsState);
    msg.setServerState(sid1, s1, now+1, true);
    msg.setServerState(sid2, s2, now+2, true);
    msg.setServerState(sid3, s3, now+3, false);
    
    byte[] b = msg.getBytes();
    MonitorMsg newMsg = new MonitorMsg(b);

    assertEquals(rsState, msg.getReplServerDbState());
    assertEquals(newMsg.getReplServerDbState().toString(), 
        msg.getReplServerDbState().toString());
    
    Iterator<Short> it = newMsg.ldapIterator();
    while (it.hasNext())
    {
      short sid = it.next();
      ServerState s = newMsg.getLDAPServerState(sid);
      if (sid == sid1)
      {
        assertEquals(s.toString(), s1.toString(), "");
        assertEquals((Long)(now+1), newMsg.getLDAPApproxFirstMissingDate(sid), "");
      }
      else if (sid == sid2)
      {
        assertEquals(s.toString(), s2.toString());        
        assertEquals((Long)(now+2), newMsg.getLDAPApproxFirstMissingDate(sid), "");
      }
      else
      {
        fail("Bad sid" + sid);
      }
    }

    Iterator<Short> it2 = newMsg.rsIterator();
    while (it2.hasNext())
    {
      short sid = it2.next();
      ServerState s = newMsg.getRSServerState(sid);
      if (sid == sid3)
      {
        assertEquals(s.toString(), s3.toString(), "");
        assertEquals((Long)(now+3), newMsg.getRSApproxFirstMissingDate(sid), "");
      }
      else
      {
        fail("Bad sid " + sid);
      }
    }

    assertEquals(newMsg.getsenderID(), msg.getsenderID());
    assertEquals(newMsg.getDestination(), msg.getDestination());
  }

  /**
   * Test that EntryMsg encoding and decoding works
   * by checking that : msg == new EntryMessageTest(msg.getBytes()).
   */
  @Test()
  public void entryMsgTest() throws Exception
  {
    String taskInitFromS2 = new String(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks\n" +
        "objectclass: top\n" +
        "objectclass: ds-task\n" +
        "objectclass: ds-task-initialize\n" +
        "ds-task-class-name: org.opends.server.tasks.InitializeTask\n" +
        "ds-task-initialize-domain-dn: " + TEST_ROOT_DN_STRING  + "\n" +
        "ds-task-initialize-source: 1\n");
    short sender = 1;
    short target = 2;
    byte[] entry = taskInitFromS2.getBytes();
    EntryMsg msg = new EntryMsg(sender, target, entry);
    EntryMsg newMsg = new EntryMsg(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getEntryBytes(), newMsg.getEntryBytes());
  }

  /**
   * Test that InitializeRequestMsg encoding and decoding works
   */
  @Test()
  public void initializeRequestMsgTest() throws Exception
  {
    short sender = 1;
    short target = 2;
    InitializeRequestMsg msg = new InitializeRequestMsg(
        DN.decode(TEST_ROOT_DN_STRING), sender, target);
    InitializeRequestMsg newMsg = new InitializeRequestMsg(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertTrue(msg.getBaseDn().equals(newMsg.getBaseDn()));
  }

  /**
   * Test that InitializeTargetMsg encoding and decoding works
   */
  @Test()
  public void initializeTargetMsgTest() throws Exception
  {
    short senderID = 1;
    short targetID = 2;
    short requestorID = 3;
    long entryCount = 4;
    DN baseDN = DN.decode(TEST_ROOT_DN_STRING);

    InitializeTargetMsg msg = new InitializeTargetMsg(
        baseDN, senderID, targetID, requestorID, entryCount);
    InitializeTargetMsg newMsg = new InitializeTargetMsg(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getRequestorID(), newMsg.getRequestorID());
    assertEquals(msg.getEntryCount(), newMsg.getEntryCount());
    assertTrue(msg.getBaseDN().equals(newMsg.getBaseDN())) ;

    assertEquals(senderID, newMsg.getsenderID());
    assertEquals(targetID, newMsg.getDestination());
    assertEquals(requestorID, newMsg.getRequestorID());
    assertEquals(entryCount, newMsg.getEntryCount());
    assertTrue(baseDN.equals(newMsg.getBaseDN())) ;

  }

  /**
   * Test that DoneMsg encoding and decoding works
   */
  @Test()
  public void doneMsgTest() throws Exception
  {
    DoneMsg msg = new DoneMsg((short)1, (short)2);
    DoneMsg newMsg = new DoneMsg(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
  }

  /**
   * Test that ErrorMsg encoding and decoding works
   */
  @Test()
  public void errorMsgTest() throws Exception
  {
    ErrorMsg msg = new ErrorMsg((short)1, (short)2, Message.raw("details"));
    ErrorMsg newMsg = new ErrorMsg(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getMsgID(), newMsg.getMsgID());
    assertEquals(msg.getDetails(), newMsg.getDetails());
  }
}
