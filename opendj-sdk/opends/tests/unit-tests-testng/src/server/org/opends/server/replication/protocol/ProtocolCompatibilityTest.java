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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.replication.protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
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
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Test the conversions between the various protocol versions.
 */
public class ProtocolCompatibilityTest extends ReplicationTestCase {

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
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    super.classCleanUp();
    // Do not disturb other tests
    ProtocolVersion.resetCurrentVersion();
  }

  @DataProvider(name="createReplServerStartData")
  public Object [][] createReplServerStartData() throws Exception
  {
    DN baseDN = DN.decode("o=test");
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0,(short)0));
    Object[] set1 = new Object[] {(short)1, baseDN, 0, "localhost:8989", state, 0L, (byte)0, 0};

    baseDN = DN.decode("dc=example,dc=com");
    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5,(short)263));
    Object[] set2 = new Object[] {(short)16, baseDN, 100, "anotherHost:1025", state, 1245L, (byte)25, 3456};

    return new Object [][] { set1, set2 };
  }

  /**
   * Test that various combinations of ReplServerStartMsg encoding and decoding
   * using protocol V1 and V2 are working.
   */
  @Test(dataProvider="createReplServerStartData")
  public void replServerStartMsgTest(short serverId, DN baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    // Create V2 message
    ReplServerStartMsg msg = new ReplServerStartMsg(serverId,
        url, baseDN, window, state, ProtocolVersion.getCurrentVersion(), genId, true, groupId, degTh);

    // Check version of message
    assertEquals(msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    ReplServerStartMsg newMsg = new ReplServerStartMsg(v1MsgBytes);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber((short)1),
        newMsg.getServerState().getMaxChangeNumber((short)1));
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());

    // Check default value for only V2 fields
    assertEquals(newMsg.getGroupId(), (byte) -1);
    assertEquals(newMsg.getDegradedStatusThreshold(), -1);

    // Set again only V2 fields
    newMsg.setGroupId(groupId);
    newMsg.setDegradedStatusThreshold(degTh);

    // Serialize in V2 msg
    ReplServerStartMsg v2Msg = new ReplServerStartMsg(newMsg.getBytes());

    // Check original version of message
    assertEquals(v2Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Check we retrieve original V2 message (V2 fields)
    assertEquals(msg.getGenerationId(), v2Msg.getGenerationId());
    assertEquals(msg.getServerId(), v2Msg.getServerId());
    assertEquals(msg.getServerURL(), v2Msg.getServerURL());
    assertEquals(msg.getBaseDn(), v2Msg.getBaseDn());
    assertEquals(msg.getWindowSize(), v2Msg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber((short)1),
        v2Msg.getServerState().getMaxChangeNumber((short)1));
    assertEquals(msg.getSSLEncryption(), v2Msg.getSSLEncryption());
    assertEquals(msg.getGroupId(), v2Msg.getGroupId());
    assertEquals(msg.getDegradedStatusThreshold(), v2Msg.getDegradedStatusThreshold());
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
    // Create V2 message
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
    attr = Attributes.create("creatorsName", "dc=creator");
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

    // Check version of message
    assertEquals(msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    AddMsg newMsg = (AddMsg)ReplicationMsg.generateMsg(v1MsgBytes);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getUniqueId(), msg.getUniqueId());
    assertEquals(newMsg.getDn(), msg.getDn());
    assertEquals(newMsg.getChangeNumber(), msg.getChangeNumber());
    assertEquals(newMsg.isAssured(), msg.isAssured());
    assertEquals(newMsg.getParentUid(), msg.getParentUid());

    //        Create an add operation from each message to compare attributes (kept encoded in messages)
    Operation op = msg.createOperation(connection, rawDN);
    Operation generatedOperation = newMsg.createOperation(connection, rawDN);

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

    // Check default value for only V2 fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)-1);

    // Set again only V2 fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);

    // Serialize in V2 msg
    AddMsg v2Msg = (AddMsg)ReplicationMsg.generateMsg(newMsg.getBytes());

    // Check original version of message
    assertEquals(v2Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Check we retrieve original V2 message (V2 fields)
    assertEquals(msg.getUniqueId(), v2Msg.getUniqueId());
    assertEquals(msg.getDn(), v2Msg.getDn());
    assertEquals(msg.getChangeNumber(), v2Msg.getChangeNumber());
    assertEquals(msg.getParentUid(), v2Msg.getParentUid());
    assertEquals(msg.isAssured(), v2Msg.isAssured());
    assertEquals(msg.getAssuredMode(), v2Msg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), v2Msg.getSafeDataLevel());

    //        Create an add operation from each message to compare attributes (kept encoded in messages)
    op = msg.createOperation(connection, rawDN);
    generatedOperation = v2Msg.createOperation(connection, rawDN);

    assertEquals(op.getClass(), AddOperationBasis.class);
    assertEquals(generatedOperation.getClass(), AddOperationBasis.class);
    addOpBasis = (AddOperationBasis) op;
    genAddOpBasis = (AddOperationBasis) generatedOperation;

    assertEquals(addOpBasis.getRawEntryDN(), genAddOpBasis.getRawEntryDN());
    assertEquals( addOpBasis.getAttachment(SYNCHROCONTEXT),
                  genAddOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(addOpBasis.getObjectClasses(), genAddOpBasis.getObjectClasses());
    assertEquals(addOpBasis.getOperationalAttributes(), genAddOpBasis.getOperationalAttributes());
    assertEquals(addOpBasis.getUserAttributes(), genAddOpBasis.getUserAttributes());
  }

  /**
   * Build some data for the DeleteMsg test below.
   */
  @DataProvider(name = "createDeleteData")
  public Object[][] createDeleteData() {
    return new Object[][] {
      {"dc=example,dc=com", false, AssuredMode.SAFE_DATA_MODE, (byte)0},
      {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn", true, AssuredMode.SAFE_READ_MODE, (byte)1},
      {"o=group,dc=example,dc=com", true, AssuredMode.SAFE_READ_MODE, (byte)3}};
  }

  /**
   * Test that various combinations of DeleteMsg encoding and decoding
   * using protocol V1 and V2 are working.
   */
  @Test(dataProvider = "createDeleteData")
  public void deleteMsgTest(String rawDN, boolean isAssured, AssuredMode assuredMode,
    byte safeDataLevel)
         throws Exception
  {
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
      (short) 123, (short) 45);
    DeleteMsg msg = new DeleteMsg(rawDN, cn, "thisIsaUniqueID");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Check version of message
    assertEquals(msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    DeleteMsg newMsg = (DeleteMsg)ReplicationMsg.generateMsg(v1MsgBytes);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getUniqueId(), msg.getUniqueId());
    assertEquals(newMsg.getDn(), msg.getDn());
    assertEquals(newMsg.getChangeNumber(), msg.getChangeNumber());
    assertEquals(newMsg.isAssured(), msg.isAssured());

    // Check default value for only V2 fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)-1);

    // Set again only V2 fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);

    // Serialize in V2 msg
    DeleteMsg v2Msg = (DeleteMsg)ReplicationMsg.generateMsg(newMsg.getBytes());

    // Check original version of message
    assertEquals(v2Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Check we retrieve original V2 message (V2 fields)
    assertEquals(msg.getUniqueId(), v2Msg.getUniqueId());
    assertEquals(msg.getDn(), v2Msg.getDn());
    assertEquals(msg.getChangeNumber(), v2Msg.getChangeNumber());
    assertEquals(msg.isAssured(), v2Msg.isAssured());
    assertEquals(msg.getAssuredMode(), v2Msg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), v2Msg.getSafeDataLevel());
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

    Attribute attr5 = Attributes.create("namingcontexts", "o=test");
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
   * Test that various combinations of ModifyMsg encoding and decoding
   * using protocol V1 and V2 are working.
   */
  @Test(dataProvider = "createModifyData")
  public void modifyMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel)
         throws Exception
  {
    // Create V2 message
    DN dn = DN.decode(rawdn);
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Check version of message
    assertEquals(msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    ModifyMsg newMsg = (ModifyMsg)ReplicationMsg.generateMsg(v1MsgBytes);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getUniqueId(), msg.getUniqueId());
    assertEquals(newMsg.getDn(), msg.getDn());
    assertEquals(newMsg.getChangeNumber(), msg.getChangeNumber());
    assertEquals(newMsg.isAssured(), msg.isAssured());

    //        Create a modify operation from each message to compare mods (kept encoded in messages)
    Operation op = msg.createOperation(connection);
    Operation generatedOperation = newMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyOperationBasis.class);
    ModifyOperationBasis modOpBasis = (ModifyOperationBasis) op;
    ModifyOperationBasis genModOpBasis = (ModifyOperationBasis) generatedOperation;

    assertEquals(modOpBasis.getRawEntryDN(), genModOpBasis.getRawEntryDN());
    assertEquals( modOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(modOpBasis.getModifications(), genModOpBasis.getModifications());

    // Check default value for only V2 fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)-1);

    // Set again only V2 fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);

    // Serialize in V2 msg
    ModifyMsg v2Msg = (ModifyMsg)ReplicationMsg.generateMsg(newMsg.getBytes());

    // Check original version of message
    assertEquals(v2Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Check we retrieve original V2 message (V2 fields)
    assertEquals(msg.getUniqueId(), v2Msg.getUniqueId());
    assertEquals(msg.getDn(), v2Msg.getDn());
    assertEquals(msg.getChangeNumber(), v2Msg.getChangeNumber());
    assertEquals(msg.isAssured(), v2Msg.isAssured());
    assertEquals(msg.getAssuredMode(), v2Msg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), v2Msg.getSafeDataLevel());

    //        Create a modify operation from each message to compare mods (kept encoded in messages)
    op = msg.createOperation(connection);
    generatedOperation = v2Msg.createOperation(connection);

    assertEquals(op.getClass(), ModifyOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyOperationBasis.class);
    modOpBasis = (ModifyOperationBasis) op;
    genModOpBasis = (ModifyOperationBasis) generatedOperation;

    assertEquals(modOpBasis.getRawEntryDN(), genModOpBasis.getRawEntryDN());
    assertEquals( modOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(modOpBasis.getModifications(), genModOpBasis.getModifications());
  }
  
  @DataProvider(name = "createModifyDnData")
  public Object[][] createModifyDnData() {
    
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
    
    return new Object[][] {
        {"dc=test,dc=com", "dc=new", "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", false, "dc=change", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0},
        {"dc=test,dc=com", "dc=new", "33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444", true, "dc=change", mods2, true, AssuredMode.SAFE_READ_MODE, (byte)1},
        {"dc=test,dc=com", "dc=new", "55555555-5555-5555-5555-555555555555", "66666666-6666-6666-6666-666666666666", false, null, mods3, true, AssuredMode.SAFE_READ_MODE, (byte)3},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn",
                   "dc=new", "77777777-7777-7777-7777-777777777777", "88888888-8888-8888-8888-888888888888",true, null, mods4, true, AssuredMode.SAFE_DATA_MODE, (byte)99},
        };
  }

  /**
   * Test that various combinations of ModifyDnMsg encoding and decoding
   * using protocol V1 and V2 are working.
   */
  @Test(dataProvider = "createModifyDnData")
  public void modifyDnMsgTest(String rawDN, String newRdn, String uid, String newParentUid,
                                   boolean deleteOldRdn, String newSuperior,
                                   List<Modification> mods, boolean isAssured,
                                   AssuredMode assuredMode, byte safeDataLevel)
         throws Exception
  {
    // Create V2 message
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 596, (short) 13);
    ModifyDNMsg msg = new ModifyDNMsg(rawDN, cn, uid,
                     newParentUid, deleteOldRdn,
                     newSuperior, newRdn, mods);

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Check version of message
    assertEquals(msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    ModifyDNMsg newMsg = (ModifyDNMsg)ReplicationMsg.generateMsg(v1MsgBytes);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getUniqueId(), msg.getUniqueId());
    assertEquals(newMsg.getDn(), msg.getDn());
    assertEquals(newMsg.getChangeNumber(), msg.getChangeNumber());
    assertEquals(newMsg.isAssured(), msg.isAssured());
    assertEquals(newMsg.getNewRDN(), msg.getNewRDN());
    assertEquals(newMsg.getNewSuperior(), msg.getNewSuperior());
    assertEquals(newMsg.getNewSuperiorId(), msg.getNewSuperiorId());
    assertEquals(newMsg.deleteOldRdn(), msg.deleteOldRdn());

    //        Create a modDn operation from each message to compare fields)
    Operation op = msg.createOperation(connection);
    Operation generatedOperation = newMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyDNOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyDNOperationBasis.class);
    ModifyDNOperationBasis modDnOpBasis = (ModifyDNOperationBasis) op;
    ModifyDNOperationBasis genModDnOpBasis = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(modDnOpBasis.getRawEntryDN(), genModDnOpBasis.getRawEntryDN());
    assertEquals( modDnOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModDnOpBasis.getAttachment(SYNCHROCONTEXT));

    // Check default value for only V2 fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)-1);
    assertEquals(modDnOpBasis.getModifications(), mods);
    assertTrue(genModDnOpBasis.getModifications() == null);

    // Set again only V2 fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);
    newMsg.setMods(mods);

    // Serialize in V2 msg
    ModifyDNMsg v2Msg = (ModifyDNMsg)ReplicationMsg.generateMsg(newMsg.getBytes());

    // Check original version of message
    assertEquals(v2Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V2);

    // Check we retrieve original V2 message (V2 fields)
    assertEquals(msg.getUniqueId(), v2Msg.getUniqueId());
    assertEquals(msg.getDn(), v2Msg.getDn());
    assertEquals(msg.getChangeNumber(), v2Msg.getChangeNumber());
    assertEquals(msg.isAssured(), v2Msg.isAssured());
    assertEquals(msg.getAssuredMode(), v2Msg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), v2Msg.getSafeDataLevel());
    assertEquals(msg.getNewRDN(), v2Msg.getNewRDN());
    assertEquals(msg.getNewSuperior(), v2Msg.getNewSuperior());
    assertEquals(msg.getNewSuperiorId(), v2Msg.getNewSuperiorId());
    assertEquals(msg.deleteOldRdn(), v2Msg.deleteOldRdn());

    //        Create a modDn operation from each message to compare mods (kept encoded in messages)
    op = msg.createOperation(connection);
    generatedOperation = v2Msg.createOperation(connection);

    assertEquals(op.getClass(), ModifyDNOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyDNOperationBasis.class);
    modDnOpBasis = (ModifyDNOperationBasis) op;
    genModDnOpBasis = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(modDnOpBasis.getRawEntryDN(), genModDnOpBasis.getRawEntryDN());
    assertEquals( modDnOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModDnOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(modDnOpBasis.getModifications(), genModDnOpBasis.getModifications());
  }
}