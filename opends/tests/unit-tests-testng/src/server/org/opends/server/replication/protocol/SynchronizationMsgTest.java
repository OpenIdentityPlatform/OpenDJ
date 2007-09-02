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
package org.opends.server.replication.protocol;

import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.AddContext;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ReplServerStartMessage;
import org.opends.server.replication.protocol.DeleteContext;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.ServerStartMessage;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.replication.protocol.WindowMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the contructors, encoders and decoders of the synchronization
 * AckMsg, ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class SynchronizationMsgTest extends ReplicationTestCase
{
  /**
   * Build some data for the ModifyMsg test below.
   */
  @DataProvider(name = "modifyEncodeDecode")
  public Object[][] createData() {
    ChangeNumber cn1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);

    AttributeType type = DirectoryServer.getAttributeType("description");

    Attribute attr1 = new Attribute("description", "new value");
    Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
    List<Modification> mods1 = new ArrayList<Modification>();
    mods1.add(mod1);

    Attribute attr2 =
      new Attribute(DirectoryServer.getAttributeType("description", true));
    Modification mod2 = new Modification(ModificationType.DELETE, attr2);
    List<Modification> mods2 = new ArrayList<Modification>();
    mods2.add(mod1);
    mods2.add(mod2);

    List<Modification> mods3 = new ArrayList<Modification>();
    LinkedHashSet<AttributeValue> values3 =
                      new LinkedHashSet<AttributeValue>();
    values3.add(new AttributeValue(type, "string"));
    values3.add(new AttributeValue(type, "value"));
    values3.add(new AttributeValue(type, "again"));
    Attribute attr3 = new Attribute(type, "description", values3);
    Modification mod3 = new Modification(ModificationType.ADD, attr3);
    mods3.add(mod3);

    List<Modification> mods4 = new ArrayList<Modification>();
    for (int i =0; i< 10; i++)
    {
      LinkedHashSet<AttributeValue> values =
                      new LinkedHashSet<AttributeValue>();
      values.add(new AttributeValue(type, "string" + String.valueOf(i)));
      Attribute attr = new Attribute(type, "description", values);
      Modification mod = new Modification(ModificationType.ADD, attr);
      mods4.add(mod);
    }

    Attribute attr5 = new Attribute("namingcontexts", "dc=example");
    Modification mod5 = new Modification(ModificationType.REPLACE, attr5);
    List<Modification> mods5 = new ArrayList<Modification>();
    mods5.add(mod5);

    return new Object[][] {
        { cn1, "dc=test", mods1},
        { cn2, "dc=cn2", mods1},
        { cn2, "dc=test with a much longer dn in case this would "
               + "make a difference", mods1},
        { cn2, "dc=test, cn=with a, o=more complex, ou=dn", mods1},
        { cn2, "cn=use\\, backslash", mods1},
        { cn2, "dc=test with several mod", mods2},
        { cn2, "dc=test with several values", mods3},
        { cn2, "dc=test with long mod", mods4},
        { cn2, "dc=testDsaOperation", mods5},
        };
  }


  /**
   * Create a ModifyMsg from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(dataProvider = "modifyEncodeDecode")
  public void modifyEncodeDecode(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods)
         throws Exception
  {
    DN dn = DN.decode(rawdn);
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");
    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMessage
        .generateMsg(msg.getBytes());


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
    /*
     * TODO : test that the generated mod equals the original mod.
     */

  }

  /**
   * Create a Update Message from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(dataProvider = "modifyEncodeDecode")
  public void updateMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods)
         throws Exception
  {
    DN dn = DN.decode(rawdn);
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");

    // Check uuid
    assertEquals("fakeuniqueid", msg.getUniqueId());

    // Check isAssured
    assertFalse(msg.isAssured());
    msg.setAssured();
    assertTrue(msg.isAssured());

    // Check equals
    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMessage
        .generateMsg(msg.getBytes());
    assertFalse(msg.equals(null));
    assertFalse(msg.equals(new Object()));
    assertTrue(msg.equals(generatedMsg));

    // Check hashCode
    assertEquals(msg.hashCode(), generatedMsg.hashCode());

    // Check compareTo
    assertEquals(msg.compareTo(generatedMsg), 0);

    // Check Get / Set DN
    assertTrue(dn.equals(DN.decode(msg.getDn())));

    String fakeDN = "cn=fake cn";
    msg.setDn(fakeDN) ;
    assertEquals(msg.getDn(), fakeDN) ;

  }

  /**
   * Build some data for the DeleteMsg test below.
   */
  @DataProvider(name = "deleteEncodeDecode")
  public Object[][] createDelData() {
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
  @Test(dataProvider = "deleteEncodeDecode")
  public void deleteEncodeDecode(String rawDN)
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
    DeleteMsg generatedMsg = (DeleteMsg) ReplicationMessage
        .generateMsg(msg.getBytes());

    assertEquals(msg.toString(), generatedMsg.toString());

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());

    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(generatedOperation.getClass(), DeleteOperationBasis.class);

    DeleteOperationBasis mod2 = (DeleteOperationBasis) generatedOperation;

    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());

    // Create an update message from this op
    DeleteMsg updateMsg = (DeleteMsg) UpdateMessage.generateMsg(op, true);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  @DataProvider(name = "modifyDnEncodeDecode")
  public Object[][] createModifyDnData() {
    return new Object[][] {
        {"dc=test,dc=com", "dc=new", false, "dc=change"},
        {"dc=test,dc=com", "dc=new", true, "dc=change"},
        // testNG does not like null argument so use "" for the newSuperior
        // instead of null
        {"dc=test,dc=com", "dc=new", false, ""},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn",
                   "dc=new",true, ""},
        };
  }

  @Test(dataProvider = "modifyDnEncodeDecode")
  public void modifyDnEncodeDecode(String rawDN, String newRdn,
                                   boolean deleteOldRdn, String newSuperior)
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
    ModifyDNMsg generatedMsg = (ModifyDNMsg) ReplicationMessage
        .generateMsg(msg.getBytes());
    Operation generatedOperation = generatedMsg.createOperation(connection);
    ModifyDNOperationBasis mod2 = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());
    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());
    assertEquals(op.getRawNewRDN(), mod2.getRawNewRDN());
    assertEquals(op.deleteOldRDN(), mod2.deleteOldRDN());
    assertEquals(op.getRawNewSuperior(), mod2.getRawNewSuperior());

    // Create an update message from this op
    ModifyDNMsg updateMsg = (ModifyDNMsg) UpdateMessage.generateMsg(localOp,
        true);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  @DataProvider(name = "addEncodeDecode")
  public Object[][] createAddData() {
    return new Object[][] {
        {"dc=test,dc=com"},
        };
  }

  @Test(dataProvider = "addEncodeDecode")
  public void addEncodeDecode(String rawDN)
         throws Exception
  {
    LinkedHashSet<AttributeValue> ocValues =
      new LinkedHashSet<AttributeValue>();
    ocValues.add(
        new AttributeValue(DirectoryServer.getObjectClassAttributeType(),
        "organization"));
    Attribute objectClass =
      new Attribute(DirectoryServer.getObjectClassAttributeType(),
        "objectClass", ocValues);
    HashMap<ObjectClass,String> objectClassList=
      new HashMap<ObjectClass,String>();
    objectClassList.put(DirectoryServer.getObjectClass("organization"),
        "organization");

    AttributeType org = DirectoryServer.getAttributeType("o", true);
    ArrayList<Attribute> userAttributes = new ArrayList<Attribute>(1);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(org, "com"));
    Attribute attr = new Attribute(org, "o", values);
    userAttributes.add(attr);
    HashMap<AttributeType,List<Attribute>> userAttList=
      new HashMap<AttributeType,List<Attribute>>();
    userAttList.put(org,userAttributes);


    ArrayList<Attribute> operationalAttributes = new ArrayList<Attribute>(1);
    org = DirectoryServer.getAttributeType("creatorsname", true);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(org, "dc=creator"));
    attr = new Attribute(org, "creatorsname", values);
    operationalAttributes.add(attr);
    HashMap<AttributeType,List<Attribute>> opList=
      new HashMap<AttributeType,List<Attribute>>();
    opList.put(org,operationalAttributes);

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 123, (short) 45);

    AddMsg msg = new AddMsg(cn, rawDN, "thisIsaUniqueID", "parentUniqueId",
                            objectClass, userAttributes,
                            operationalAttributes);
    AddMsg generatedMsg = (AddMsg) ReplicationMessage.generateMsg(msg
        .getBytes());
    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());
    // TODO : should test that generated attributes match original attributes.

    // Create an new Add Operation from the current addMsg
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    msg.createOperation(connection, rawDN) ;
    // TODO : should test that generated attributes match original attributes.
    // List<LDAPAttribute> rawAtt = addOp.getRawAttributes();


    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());

    //Create an Add operation and generate and Add msg from it
    DN dn = DN.decode(rawDN);

    AddOperation addOpBasis = new AddOperationBasis(connection,
        (long) 1, 1, null, dn, objectClassList, userAttList, opList);
    LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpBasis);
    OperationContext opCtx = new AddContext(cn, "thisIsaUniqueID",
        "parentUniqueId");
    addOp.setAttachment(SYNCHROCONTEXT, opCtx);

    generatedMsg = new AddMsg(addOp);
    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());
    // TODO : should test that generated attributes match original attributes.


    // Create an update message from this op
    AddMsg updateMsg = (AddMsg) UpdateMessage.generateMsg(addOp, true);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());


  }

  /**
   * Build some data for the AckMsg test below.
   */
  @DataProvider(name = "ackMsg")
  public Object[][] createAckData() {
    ChangeNumber cn1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);

    return new Object[][] {
        {cn1},
        {cn2}
        };
  }

  @Test(dataProvider = "ackMsg")
  public void ackMsg(ChangeNumber cn)
         throws Exception
  {
    AckMessage msg1, msg2 ;

    // Consctructor test (with ChangeNumber)
    // Chech that retrieved CN is OK
    msg1 = new  AckMessage(cn);
    assertEquals(msg1.getChangeNumber().compareTo(cn), 0);

    // Consctructor test (with byte[])
    // Check that retrieved CN is OK
    msg2 = new  AckMessage(msg1.getBytes());
    assertEquals(msg2.getChangeNumber().compareTo(cn), 0);

    // Check invalid bytes for constructor
    byte[] b = msg1.getBytes();
    b[0] = ReplicationMessage.MSG_TYPE_ADD_REQUEST ;
    try
    {
      // This should generated an exception
      msg2 = new  AckMessage(b);
      assertTrue(false);
    }
    catch (DataFormatException e)
    {
      assertTrue(true);
    }

    // Check that retrieved CN is OK
    msg2 = (AckMessage) ReplicationMessage.generateMsg(msg1.getBytes());
  }

  @DataProvider(name="serverStart")
  public Object [][] createServerStartMessageTestData() throws Exception
  {
    DN baseDN = DN.decode("dc=example, dc=com");
    ServerState state = new ServerState();
    return new Object [][] { {(short)1, baseDN, 100, state} };
  }
  /**
   * Test that ServerStartMessage encoding and decoding works
   * by checking that : msg == new ServerStartMessage(msg.getBytes()).
   */
  @Test(dataProvider="serverStart")
  public void serverStartMessageTest(short serverId, DN baseDN, int window,
         ServerState state) throws Exception
  {
    state.update(new ChangeNumber((long)1, 1,(short)1));
    ServerStartMessage msg = new ServerStartMessage(serverId, baseDN,
        window, window, window, window, window, window, state, (short)1, 
        (long)1, true);
    ServerStartMessage newMsg = new ServerStartMessage(msg.getBytes());
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
  }

  @DataProvider(name="changelogStart")
  public Object [][] createChangelogStartMessageTestData() throws Exception
  {
    DN baseDN = DN.decode("dc=example, dc=com");
    ServerState state = new ServerState();
    return new Object [][] { {(short)1, baseDN, 100, "localhost:8989", state} };
  }

  /**
   * Test that changelogStartMessage encoding and decoding works
   * by checking that : msg == new ReplServerStartMessage(msg.getBytes()).
   */
  @Test(dataProvider="changelogStart")
  public void replserverStartMessageTest(short serverId, DN baseDN, int window,
         String url, ServerState state) throws Exception
  {
    state.update(new ChangeNumber((long)1, 1,(short)1));
    ReplServerStartMessage msg = new ReplServerStartMessage(serverId,
        url, baseDN, window, state, (short)1, (long)1, true);
    ReplServerStartMessage newMsg = new ReplServerStartMessage(msg.getBytes());
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber((short)1),
        newMsg.getServerState().getMaxChangeNumber((short)1));
    assertEquals(msg.getVersion(), newMsg.getVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
  }

  /**
   * Test that WindowMessageTest encoding and decoding works
   * by checking that : msg == new WindowMessage(msg.getBytes()).
   */
  @Test()
  public void windowMessageTest() throws Exception
  {
    WindowMessage msg = new WindowMessage(123);
    WindowMessage newMsg = new WindowMessage(msg.getBytes());
    assertEquals(msg.getNumAck(), newMsg.getNumAck());
  }

  /**
   * Test that WindowProbe encoding and decoding works
   * by checking that : new WindowProbe(msg.getBytes()) does not throws
   * an exception.
   */
  @Test()
  public void WindowProbeTest() throws Exception
  {
    WindowProbe msg = new WindowProbe();
    new WindowProbe(msg.getBytes());
  }

  /**
   * Test ReplServerInfoMessage encoding and decoding.
   */
  @Test()
  public void replServerInfoMessageTest() throws Exception
  {
    List<String> connectedServers = new ArrayList<String>(0);
    connectedServers.add("s1");
    connectedServers.add("s2");
    ReplServerInfoMessage msg = 
      new ReplServerInfoMessage(connectedServers, 13);
    ReplServerInfoMessage newMsg = new ReplServerInfoMessage(msg.getBytes());
    assertEquals(msg.getConnectedServers(), newMsg.getConnectedServers());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
  }

  /**
   * Test that EntryMessage encoding and decoding works
   * by checking that : msg == new EntryMessageTest(msg.getBytes()).
   */
  @Test()
  public void entryMessageTest() throws Exception
  {
    String taskInitFromS2 = new String(
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks\n" +
        "objectclass: top\n" +
        "objectclass: ds-task\n" +
        "objectclass: ds-task-initialize\n" +
        "ds-task-class-name: org.opends.server.tasks.InitializeTask" +
        "ds-task-initialize-domain-dn: dc=example,dc=com" +
        "ds-task-initialize-source: 1");
    short sender = 1;
    short target = 2;
    byte[] entry = taskInitFromS2.getBytes();
    EntryMessage msg = new EntryMessage(sender, target, entry);
    EntryMessage newMsg = new EntryMessage(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getEntryBytes(), newMsg.getEntryBytes());
  }

  /**
   * Test that InitializeRequestMessage encoding and decoding works
   */
  @Test()
  public void initializeRequestMessageTest() throws Exception
  {
    short sender = 1;
    short target = 2;
    InitializeRequestMessage msg = new InitializeRequestMessage(
        DN.decode("dc=example"), sender, target);
    InitializeRequestMessage newMsg = new InitializeRequestMessage(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertTrue(msg.getBaseDn().equals(newMsg.getBaseDn()));
  }

  /**
   * Test that InitializeTargetMessage encoding and decoding works
   */
  @Test()
  public void initializeTargetMessageTest() throws Exception
  {
    short senderID = 1;
    short targetID = 2;
    short requestorID = 3;
    long entryCount = 4;
    DN baseDN = DN.decode("dc=example");

    InitializeTargetMessage msg = new InitializeTargetMessage(
        baseDN, senderID, targetID, requestorID, entryCount);
    InitializeTargetMessage newMsg = new InitializeTargetMessage(msg.getBytes());
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
   * Test that DoneMessage encoding and decoding works
   */
  @Test()
  public void doneMessageTest() throws Exception
  {
    DoneMessage msg = new DoneMessage((short)1, (short)2);
    DoneMessage newMsg = new DoneMessage(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
  }

  /**
   * Test that ErrorMessage encoding and decoding works
   */
  @Test()
  public void errorMessageTest() throws Exception
  {
    ErrorMessage msg = new ErrorMessage((short)1, (short)2, Message.raw("details"));
    ErrorMessage newMsg = new ErrorMessage(msg.getBytes());
    assertEquals(msg.getsenderID(), newMsg.getsenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getMsgID(), newMsg.getMsgID());
    assertEquals(msg.getDetails(), newMsg.getDetails());
  }
}
