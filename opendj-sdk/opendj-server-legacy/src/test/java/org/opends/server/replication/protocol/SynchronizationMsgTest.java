/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.*;
import java.util.zip.DataFormatException;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.core.*;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.*;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Test the constructors, encoders and decoders of the replication protocol
 * PDUs classes (message classes).
 */
@SuppressWarnings("javadoc")
public class SynchronizationMsgTest extends ReplicationTestCase
{

  private DN TEST_ROOT_DN;

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
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
  }

  /**
   * Build some data for the ModifyMsg test below.
   */
  @DataProvider(name = "createModifyData")
  public Object[][] createModifyData() {
    CSN csn1 = new CSN(1,  0,  1);
    CSN csn2 = new CSN(TimeThread.getTime(), 123,  45);
    CSN csn3 = new CSN(TimeThread.getTime(), 67894123,  45678);

    AttributeType type = DirectoryServer.getAttributeType("description");

    Attribute attr1 = Attributes.create("description", "new value");
    Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
    List<Modification> mods1 = newList(mod1);

    Attribute attr2 = Attributes.empty("description");
    Modification mod2 = new Modification(ModificationType.DELETE, attr2);
    List<Modification> mods2 = newList(mod1, mod2);

    AttributeBuilder builder = new AttributeBuilder(type);
    builder.add("string");
    builder.add("value");
    builder.add("again");
    Attribute attr3 = builder.toAttribute();
    Modification mod3 = new Modification(ModificationType.ADD, attr3);
    List<Modification> mods3 = newList(mod3);

    List<Modification> mods4 = new ArrayList<>();
    for (int i = 0; i < 10; i++)
    {
      Attribute attr = Attributes.create("description", "string" + i);
      mods4.add(new Modification(ModificationType.ADD, attr));
    }

    Attribute attr5 = Attributes.create("namingcontexts", TEST_ROOT_DN_STRING);
    Modification mod5 = new Modification(ModificationType.REPLACE, attr5);
    List<Modification> mods5 = newList(mod5);

    List<Attribute> eclIncludes = getEntryAttributes();
    return new Object[][] {
        { csn1, "dc=test", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0, null},
        { csn2, "dc=cn2", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)1, eclIncludes},
        { csn2, "dc=test with a much longer dn in case this would "
               + "make a difference", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { csn2, "dc=test, cn=with a, o=more complex, ou=dn", mods1, false, AssuredMode.SAFE_READ_MODE, (byte)5, eclIncludes},
        { csn2, "cn=use\\, backslash", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { csn2, "dc=test with several mod", mods2, false, AssuredMode.SAFE_DATA_MODE, (byte)16, eclIncludes},
        { csn2, "dc=test with several values", mods3, false, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { csn2, "dc=test with long mod", mods4, true, AssuredMode.SAFE_READ_MODE, (byte)120, eclIncludes},
        { csn2, "dc=testDsaOperation", mods5, true, AssuredMode.SAFE_DATA_MODE, (byte)99, null},
        { csn3, "dc=serverIdLargerThan32767", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)1, null},
        };
  }

  /**
   * Create a ModifyMsg from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(enabled=true,dataProvider = "createModifyData")
  public void modifyMsgTest(CSN csn,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel,
                               List<Attribute> entryAttrList)
         throws Exception
  {
    DN dn = DN.valueOf(rawdn);
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    ModifyMsg msg = new ModifyMsg(csn, dn, mods, "fakeuniqueid");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry included attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), getCurrentVersion());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    assertEquals(msg.getCSN(), generatedMsg.getCSN());

    // Get ECL entry attributes
    assertAttributesEqual(generatedMsg.getEclIncludes(), entryAttrList);

    ModifyOperation mod1 = (ModifyOperation) msg.createOperation(connection);
    ModifyOperation mod2 = (ModifyOperation) generatedMsg.createOperation(connection);

    assertEquals(mod1.getRawEntryDN(), mod2.getRawEntryDN());
    assertEquals(mod1.getAttachment(SYNCHROCONTEXT),
                 mod2.getAttachment(SYNCHROCONTEXT));
    assertEquals(mod1.getModifications(), mod2.getModifications());
  }

  /**
   * Create an Update LocalizableMessage from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msgs match.
   */
  @Test(enabled=true,dataProvider = "createModifyData")
  public void updateMsgTest(CSN csn,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel ,
                               List<Attribute> entryAttrList)
         throws Exception
  {
    DN dn = DN.valueOf(rawdn);
    ModifyMsg msg = new ModifyMsg(csn, dn, mods, "fakeuniqueid");

    // Check isAssured
    assertFalse(msg.isAssured());
    msg.setAssured(isAssured);
    assertEquals(msg.isAssured(), isAssured);

    // Check assured mode
    assertEquals(msg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    msg.setAssuredMode(assuredMode);
    assertEquals(msg.getAssuredMode(), assuredMode);

    // Check safe data level
    assertEquals(msg.getSafeDataLevel(), 1);
    msg.setSafeDataLevel(safeDataLevel);
    assertEquals(msg.getSafeDataLevel(), safeDataLevel);

    // Check equals
    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), REPLICATION_PROTOCOL_V1);
    assertFalse(msg.equals(null));
    assertFalse(msg.equals(new Object()));

    // Check CSN
    assertEquals(msg, generatedMsg);

    // Check hashCode
    assertEquals(msg.hashCode(), generatedMsg.hashCode());

    // Check compareTo
    assertEquals(msg.compareTo(generatedMsg), 0);

    // Check Get / Set DN
    assertEquals(msg.getDN(), generatedMsg.getDN());

    DN fakeDN = DN.valueOf("cn=fake cn");
    msg.setDN(fakeDN) ;
    assertEquals(msg.getDN(), fakeDN) ;

    // Check uuid
    assertEquals(msg.getEntryUUID(), generatedMsg.getEntryUUID());

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
  public Object[][] createDeleteData()
  {
    List<Attribute> entryAttrList = getEntryAttributes();
    return new Object[][] {
        {"dc=com", entryAttrList, false},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn", null, true},
        };
  }

  private List<Attribute> getEntryAttributes()
  {
    return newList(
        Attributes.create("description", "eav description"),
        Attributes.create("namingcontexts", "eav naming contexts"));
  }

  /**
   * Create a Delete from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another DeleteMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(enabled=true,dataProvider = "createDeleteData")
  public void deleteMsgTest(String rawDN, List<Attribute> entryAttrList,
      boolean subtree)
  throws Exception
  {
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    DeleteOperation deleteOp =
      new DeleteOperationBasis(connection, 1, 1,null, DN.valueOf(rawDN));
    if (subtree)
    {
      deleteOp.addRequestControl(new SubtreeDeleteControl(false));
    }
    LocalBackendDeleteOperation op = new LocalBackendDeleteOperation(deleteOp);
    CSN csn = new CSN(TimeThread.getTime(), 123, 45);
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(csn, "uniqueid"));
    DeleteMsg msg = new DeleteMsg(op);
    assertEquals(msg.isSubtreeDelete(), subtree);
    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }
    msg.setInitiatorsName("johnny h");
    DeleteMsg generatedMsg = (DeleteMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), getCurrentVersion());

    assertEquals(msg.toString(), generatedMsg.toString());
    assertEquals(msg.getInitiatorsName(), generatedMsg.getInitiatorsName());
    assertEquals(msg.getCSN(), generatedMsg.getCSN());
    assertEquals(generatedMsg.isSubtreeDelete(), subtree);

    // Get ECL entry attributes
    assertAttributesEqual(generatedMsg.getEclIncludes(), entryAttrList);

    DeleteOperation mod2 = (DeleteOperation) generatedMsg.createOperation(connection);
    assertEquals(mod2.getRequestControl(SubtreeDeleteControl.DECODER) != null, subtree);
    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());

    // Create an update message from this op
    DeleteMsg updateMsg = (DeleteMsg) LDAPUpdateMsg.generateMsg(op);
    assertEquals(msg.getCSN(), updateMsg.getCSN());
    assertEquals(msg.isSubtreeDelete(), updateMsg.isSubtreeDelete());
  }

  @DataProvider(name = "createModifyDnData")
  public Object[][] createModifyDnData() {

    AttributeType type = DirectoryServer.getAttributeType("description");

    Attribute attr1 = Attributes.create("description", "new value");
    Modification mod1 = new Modification(ModificationType.REPLACE, attr1);
    List<Modification> mods1 = newList(mod1);

    Attribute attr2 = Attributes.empty("description");
    Modification mod2 = new Modification(ModificationType.DELETE, attr2);
    List<Modification> mods2 = newList(mod1, mod2);

    AttributeBuilder builder = new AttributeBuilder(type);
    builder.add("string");
    builder.add("value");
    builder.add("again");
    Attribute attr3 = builder.toAttribute();
    Modification mod3 = new Modification(ModificationType.ADD, attr3);
    List<Modification> mods3 = newList(mod3);

    List<Modification> mods4 = new ArrayList<>();
    for (int i = 0; i < 10; i++)
    {
      Attribute attr = Attributes.create("description", "string" + i);
      mods4.add(new Modification(ModificationType.ADD, attr));
    }

    List<Attribute> entryAttrList = getEntryAttributes();
    return new Object[][] {
        {"dc=test,dc=com", "dc=new", false, "dc=change", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0, entryAttrList},
        {"dc=test,dc=com", "dc=new", true, "dc=change", mods2, true, AssuredMode.SAFE_READ_MODE, (byte)1, null},
        // testNG does not like null argument so use "" for the newSuperior instead of null
        {"dc=test,dc=com", "dc=new", false, "", mods3, true, AssuredMode.SAFE_READ_MODE, (byte)3, entryAttrList},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn",
                   "dc=new", true, "", mods4, true, AssuredMode.SAFE_DATA_MODE, (byte)99, null},
        };
  }

  @Test(enabled=true,dataProvider = "createModifyDnData")
  public void modifyDnMsgTest(String rawDN, String newRdn,
                                   boolean deleteOldRdn, String newSuperior,
                                   List<Modification> mods, boolean isAssured,
                                   AssuredMode assuredMode,
                               byte safeDataLevel, List<Attribute> entryAttrList)
         throws Exception
  {
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();
    ModifyDNOperation op = new ModifyDNOperationBasis(connection, 1, 1, null,
                  DN.valueOf(rawDN), RDN.decode(newRdn), deleteOldRdn,
                  (newSuperior.length() != 0 ? DN.valueOf(newSuperior) : null));

    CSN csn = new CSN(TimeThread.getTime(), 123,  45);
    op.setAttachment(SYNCHROCONTEXT,
        new ModifyDnContext(csn, "uniqueid", "newparentId"));
    LocalBackendModifyDNOperation localOp =
      new LocalBackendModifyDNOperation(op);
    for (Modification mod : mods)
    {
      localOp.addModification(mod);
    }

    ModifyDNMsg msg = new ModifyDNMsg(localOp);
    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    ModifyDNMsg generatedMsg = (ModifyDNMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), getCurrentVersion());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    // Get ECL entry attributes
    assertAttributesEqual(generatedMsg.getEclIncludes(), entryAttrList);

    ModifyDNOperation moddn1 = (ModifyDNOperation) msg.createOperation(connection);
    ModifyDNOperation moddn2 = (ModifyDNOperation) generatedMsg.createOperation(connection);

    assertEquals(msg.getCSN(), generatedMsg.getCSN());
    assertEquals(moddn1.getRawEntryDN(), moddn2.getRawEntryDN());
    assertEquals(moddn1.getRawNewRDN(), moddn2.getRawNewRDN());
    assertEquals(moddn1.deleteOldRDN(), moddn2.deleteOldRDN());
    assertEquals(moddn1.getRawNewSuperior(), moddn2.getRawNewSuperior());
    assertEquals(moddn1.getModifications(), moddn2.getModifications());

    // Create an update message from this op
    ModifyDNMsg updateMsg = (ModifyDNMsg) LDAPUpdateMsg.generateMsg(localOp);
    assertEquals(msg.getCSN(), updateMsg.getCSN());
  }

  @DataProvider(name = "createAddData")
  public Object[][] createAddData()
  {
    List<Attribute> entryAttrList = getEntryAttributes();
    return new Object[][] {
        {"dc=example,dc=com", false, AssuredMode.SAFE_DATA_MODE, (byte)0, entryAttrList},
        {"o=test", true, AssuredMode.SAFE_READ_MODE, (byte)1, null},
        {"o=group,dc=example,dc=com", true, AssuredMode.SAFE_READ_MODE, (byte)3, entryAttrList}};
  }

  @Test(enabled=true,dataProvider = "createAddData")
  public void addMsgTest(String rawDN, boolean isAssured, AssuredMode assuredMode,
    byte safeDataLevel, List<Attribute> entryAttrList)
         throws Exception
  {
    final DN dn = DN.valueOf(rawDN);

    Attribute objectClass = Attributes.create(DirectoryServer
        .getObjectClassAttributeType(), "organization");
    Map<ObjectClass, String> objectClassList = new HashMap<>();
    objectClassList.put(DirectoryServer.getObjectClass("organization"), "organization");

    Attribute attr = Attributes.create("o", "com");
    List<Attribute> userAttributes = newList(attr);
    Map<AttributeType, List<Attribute>> userAttList = new HashMap<>();
    userAttList.put(attr.getAttributeType(), userAttributes);


    attr = Attributes.create("creatorsname", "dc=creator");
    List<Attribute> operationalAttributes = newList(attr);
    Map<AttributeType, List<Attribute>> opList = new HashMap<>();
    opList.put(attr.getAttributeType(), operationalAttributes);

    CSN csn = new CSN(TimeThread.getTime(), 123,  45);

    AddMsg msg = new AddMsg(csn, dn, "thisIsaUniqueID", "parentUniqueId",
                            objectClass, userAttributes,
                            operationalAttributes);
    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    AddMsg generatedMsg = (AddMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), getCurrentVersion());
    assertEquals(generatedMsg.getBytes(), msg.getBytes());
    assertEquals(generatedMsg.toString(), msg.toString());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.getParentEntryUUID(), msg.getParentEntryUUID());
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    // Get ECL entry attributes
    assertAttributesEqual(generatedMsg.getEclIncludes(), entryAttrList);

    // Create an new Add Operation from the current addMsg
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    AddOperation addOp = msg.createOperation(connection, dn);
    AddOperation genAddOp = generatedMsg.createOperation(connection, dn);

    assertEquals(addOp.getRawEntryDN(), genAddOp.getRawEntryDN());
    assertEquals(addOp.getAttachment(SYNCHROCONTEXT), genAddOp.getAttachment(SYNCHROCONTEXT));
    assertEquals(addOp.getObjectClasses(), genAddOp.getObjectClasses());
    assertEquals(addOp.getOperationalAttributes(), genAddOp.getOperationalAttributes());
    assertEquals(addOp.getUserAttributes(), genAddOp.getUserAttributes());

    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());

    //Create an Add operation and generate and Add msg from it
    AddOperation addOpB = new AddOperationBasis(connection,
        1, 1, null, dn, objectClassList, userAttList, opList);
    LocalBackendAddOperation localAddOp = new LocalBackendAddOperation(addOpB);
    OperationContext opCtx = new AddContext(csn, "thisIsaUniqueID",
        "parentUniqueId");
    localAddOp.setAttachment(SYNCHROCONTEXT, opCtx);

    generatedMsg = new AddMsg(localAddOp);
    generatedMsg.setAssured(isAssured);
    generatedMsg.setAssuredMode(assuredMode);
    generatedMsg.setSafeDataLevel(safeDataLevel);

    if (entryAttrList != null)
    {
      generatedMsg.setEclIncludes(entryAttrList);
    }

    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());
    // TODO : should test that generated attributes match original attributes.


    // Create an update message from this op
    AddMsg updateMsg = (AddMsg) LDAPUpdateMsg.generateMsg(localAddOp);
    assertEquals(msg.getCSN(), updateMsg.getCSN());
  }

  private void assertAttributesEqual(List<RawAttribute> actualAttrs,
      List<Attribute> expectedAttrs) throws LDAPException
  {
    if (expectedAttrs == null)
    {
      Assertions.assertThat(actualAttrs).isEmpty();
      return;
    }

    Assertions.assertThat(actualAttrs).hasSize(expectedAttrs.size());
    for (int i = 0; i < expectedAttrs.size(); i++)
    {
      final Attribute expectedAttr = expectedAttrs.get(i);
      final Attribute actualAttr = actualAttrs.get(i).toAttribute();

      assertTrue(expectedAttr.getName().equalsIgnoreCase(actualAttr.getName()));
      assertTrue(expectedAttr.toString().equalsIgnoreCase(actualAttr.toString()),
          "Comparing: " + expectedAttr + " and " + actualAttr);
    }
  }

  /**
   * Build some data for the AckMsg test below.
   */
  @DataProvider(name = "createAckData")
  public Object[][] createAckData() {
    CSN csn1 = new CSN(1,  0,  1);
    CSN csn2 = new CSN(TimeThread.getTime(), 123, 45);
    CSN csn3 = new CSN(TimeThread.getTime(), 1234567, 45678);

    List<Integer> fservers1 = newList(12345, -12345, 31657, -28456, 0);
    List<Integer> fservers2 = newList();
    List<Integer> fservers3 = newList(0);
    List<Integer> fservers4 = newList(100, 2000, 30000, -100, -2000, -30000);

    return new Object[][] {
        {csn1, true, false, false, fservers1},
        {csn2, false, true, false, fservers2},
        {csn1, false, false, true, fservers3},
        {csn2, false, false, false, fservers4},
        {csn1, true, true, false, fservers1},
        {csn2, false, true, true, fservers2},
        {csn1, true, false, true, fservers3},
        {csn2, true, true, true, fservers4},
        {csn3, true, true, true, fservers4}
        };
  }

  @Test(enabled=true,dataProvider = "createAckData")
  public void ackMsgTest(CSN csn, boolean hasTimeout, boolean hasWrongStatus,
    boolean hasReplayError, List<Integer> failedServers)
         throws Exception
  {
    AckMsg msg1, msg2 ;

    // Constructor test (with CSN)
    // Check that retrieved CSN is OK
    msg1 = new AckMsg(csn);
    assertEquals(msg1.getCSN().compareTo(csn), 0);

    // Check default values for error info
    assertFalse(msg1.hasTimeout());
    assertFalse(msg1.hasWrongStatus());
    assertFalse(msg1.hasReplayError());
    assertEquals(msg1.getFailedServers().size(), 0);

    // Check constructor with error info
    msg1 = new  AckMsg(csn, hasTimeout, hasWrongStatus, hasReplayError, failedServers);
    assertEquals(msg1.getCSN().compareTo(csn), 0);
    assertEquals(msg1.hasTimeout(), hasTimeout);
    assertEquals(msg1.hasWrongStatus(), hasWrongStatus);
    assertEquals(msg1.hasReplayError(), hasReplayError);
    assertEquals(msg1.getFailedServers(), failedServers);

    // Constructor test (with byte[])
    msg2 = new  AckMsg(msg1.getBytes(getCurrentVersion()));
    assertEquals(msg2.getCSN().compareTo(csn), 0);
    assertEquals(msg1.hasTimeout(), msg2.hasTimeout());
    assertEquals(msg1.hasWrongStatus(), msg2.hasWrongStatus());
    assertEquals(msg1.hasReplayError(), msg2.hasReplayError());
    assertEquals(msg1.getFailedServers(), msg2.getFailedServers());

    // Check invalid bytes for constructor
    byte[] b = msg1.getBytes(getCurrentVersion());
    b[0] = ReplicationMsg.MSG_TYPE_ADD;
    try
    {
      // This should generated an exception
      msg2 = new  AckMsg(b);
      fail("Expected DataFormatException");
    }
    catch (DataFormatException expected)
    {
    }

    // Check that retrieved CSN is OK
    msg2 = (AckMsg) ReplicationMsg.generateMsg(
        msg1.getBytes(getCurrentVersion()), getCurrentVersion());
  }

  @DataProvider(name="createServerStartData")
  public Object[][] createServerStartData() throws Exception
  {
    DN baseDN = TEST_ROOT_DN;

    final ServerState state1 = new ServerState();
    state1.update(new CSN(0, 0,0));
    final ServerState state2 = new ServerState();
    state2.update(new CSN(75, 5,263));
    final ServerState state3 = new ServerState();
    state3.update(new CSN(75, 98573895,45263));

    return new Object[][]
    {
      {  1, baseDN,   0, state1,     0L, false, (byte) 0 },
      { 16, baseDN, 100, state2, 1248L, true,   (byte)31 },
      { 16, baseDN, 100, state3, 1248L, true,   (byte)31 }
    };
  }

  /**
   * Test that ServerStartMsg encoding and decoding works
   * by checking that : msg == new ServerStartMsg(msg.getBytes()).
   */
  @Test(enabled=true,dataProvider="createServerStartData")
  public void serverStartMsgTest(int serverId, DN baseDN, int window,
         ServerState state, long genId, boolean sslEncryption, byte groupId) throws Exception
  {
    ServerStartMsg msg = new ServerStartMsg(
        serverId, "localhost:1234", baseDN, window, window, state,
        genId, sslEncryption, groupId);
    ServerStartMsg newMsg = new ServerStartMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getHeartbeatInterval(), newMsg.getHeartbeatInterval());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertEquals(msg.getServerState().getCSN(1),
        newMsg.getServerState().getCSN(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getGroupId(), newMsg.getGroupId());
  }

  @DataProvider(name="createReplServerStartData")
  public Object[][] createReplServerStartData() throws Exception
  {
    DN baseDN = TEST_ROOT_DN;

    final ServerState state1 = new ServerState();
    state1.update(new CSN(0, 0,0));
    final ServerState state2 = new ServerState();
    state2.update(new CSN(75, 5,263));
    final ServerState state3 = new ServerState();
    state3.update(new CSN(75, 5, 45263));

    return new Object[][]
    {
      {1, baseDN, 0, "localhost:8989", state1, 0L, (byte)0, 0},
      {16, baseDN, 100, "anotherHost:1025", state2, 1245L, (byte)25, 3456},
      {16, baseDN, 100, "anotherHost:1025", state3, 1245L, (byte)25, 3456},
    };
  }

  /**
   * Test that ReplServerStartMsg encoding and decoding works
   * by checking that : msg == new ReplServerStartMsg(msg.getBytes()).
   */
  @Test(enabled=true,dataProvider="createReplServerStartData")
  public void replServerStartMsgTest(int serverId, DN baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    ReplServerStartMsg msg = new ReplServerStartMsg(serverId,
        url, baseDN, window, state, genId, true, groupId, degTh);
    ReplServerStartMsg newMsg = new ReplServerStartMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getCSN(1),
        newMsg.getServerState().getCSN(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertEquals(msg.getGroupId(), newMsg.getGroupId());
    assertEquals(msg.getDegradedStatusThreshold(),
                 newMsg.getDegradedStatusThreshold());
  }

  /**
   * Test that StopMsg encoding and decoding works
   * by checking that : msg == new StopMsg(msg.getBytes()).
   */
  @Test
  public void stopMsgTest() throws Exception
  {
    StopMsg msg = new StopMsg();
    new StopMsg(msg.getBytes(getCurrentVersion()));
  }

  @Test
  public void changeTimeHeartbeatMsgTest() throws Exception
  {
    final CSN csn = new CSN(System.currentTimeMillis(), 0, 42);
    final ChangeTimeHeartbeatMsg heartbeatMsg = new ChangeTimeHeartbeatMsg(csn);
    assertCTHearbeatMsg(heartbeatMsg, REPLICATION_PROTOCOL_V1);
    assertCTHearbeatMsg(heartbeatMsg, REPLICATION_PROTOCOL_V7);
  }

  private void assertCTHearbeatMsg(ChangeTimeHeartbeatMsg expectedMsg,
      short version) throws DataFormatException
  {
    final byte[] bytes = expectedMsg.getBytes(version);
    ChangeTimeHeartbeatMsg decodedMsg = new ChangeTimeHeartbeatMsg(bytes, version);
    assertEquals(decodedMsg.getCSN(), expectedMsg.getCSN());
  }

  @Test
  public void replicaOfflineMsgTest() throws Exception
  {
    final CSN csn = new CSN(System.currentTimeMillis(), 0, 42);
    final ReplicaOfflineMsg expectedMsg = new ReplicaOfflineMsg(csn);

    final byte[] bytes = expectedMsg.getBytes(REPLICATION_PROTOCOL_V8);
    ReplicaOfflineMsg decodedMsg = new ReplicaOfflineMsg(bytes);
    assertEquals(decodedMsg.getCSN(), expectedMsg.getCSN());
  }

  /**
   * Test that WindowMsg encoding and decoding works
   * by checking that : msg == new WindowMsg(msg.getBytes()).
   */
  @Test
  public void windowMsgTest() throws Exception
  {
    WindowMsg msg = new WindowMsg(123);
    WindowMsg newMsg = new WindowMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getNumAck(), newMsg.getNumAck());
  }

  /**
   * Test that WindowProbeMsg encoding and decoding works
   * by checking that : new WindowProbeMsg(msg.getBytes()) does not throws
   * an exception.
   */
  @Test
  public void windowProbeMsgTest() throws Exception
  {
    WindowProbeMsg msg = new WindowProbeMsg();
    new WindowProbeMsg(msg.getBytes(getCurrentVersion()));
  }

  @DataProvider
  public Object[][] createTopologyData() throws Exception
  {
    List<String> urls1 = newList(
        "ldap://ldap.iplanet.com/" + TEST_ROOT_DN_STRING + "??sub?(sn=Jensen)",
        "ldaps://ldap.iplanet.com:4041/uid=bjensen,ou=People,"
            + TEST_ROOT_DN_STRING + "?cn,mail,telephoneNumber");
    List<String> urls2 = newList();
    List<String> urls3 = newList("ldaps://host:port/dc=foo??sub?(sn=One Entry)");
    List<String> urls4 = newList(
        "ldaps://host:port/dc=foobar1??sub?(sn=Another Entry 1)",
        "ldaps://host:port/dc=foobar2??sub?(sn=Another Entry 2)");

    Set<String> a1 = newSet();
    Set<String> a2 = newSet("dc");
    Set<String> a3 = newSet("dc", "uid");
    Set<String> a4 = newSet();

    DSInfo dsInfo1 = new DSInfo(13, "dsHost1:111", 26, 154631, ServerStatus.FULL_UPDATE_STATUS,
      false, AssuredMode.SAFE_DATA_MODE, (byte)12, (byte)132, urls1, a1, a1, (short)1);
    DSInfo dsInfo2 = new DSInfo(-436, "dsHost2:222", 493, -227896, ServerStatus.DEGRADED_STATUS,
      true, AssuredMode.SAFE_READ_MODE, (byte)-7, (byte)-265, urls2, a2, a2, (short)2);
    DSInfo dsInfo3 = new DSInfo(2436, "dsHost3:333", 591, 0, ServerStatus.NORMAL_STATUS,
      false, AssuredMode.SAFE_READ_MODE, (byte)17, (byte)0, urls3, a3, a3, (short)3);
    DSInfo dsInfo4 = new DSInfo(415, "dsHost4:444", 146, 0, ServerStatus.BAD_GEN_ID_STATUS,
      true, AssuredMode.SAFE_DATA_MODE, (byte)2, (byte)15, urls4, a4, a4, (short)4);
    DSInfo dsInfo5 = new DSInfo(452436, "dsHost5:555", 45591, 0, ServerStatus.NORMAL_STATUS,
      false, AssuredMode.SAFE_READ_MODE, (byte)17, (byte)0, urls3, a1, a1, (short)5);

    List<DSInfo> dsList1 = newList(dsInfo1);
    List<DSInfo> dsList2 = newList();
    List<DSInfo> dsList3 = newList(dsInfo2);
    List<DSInfo> dsList4 = newList(dsInfo5, dsInfo4, dsInfo3, dsInfo2, dsInfo1);

    RSInfo rsInfo1 = new RSInfo(4527, "rsHost1:123", 45316, (byte)103, 1);
    RSInfo rsInfo2 = new RSInfo(4527, "rsHost2:456", 0, (byte)0, 1);
    RSInfo rsInfo3 = new RSInfo(0, "rsHost3:789", -21113, (byte)98, 1);
    RSInfo rsInfo4 = new RSInfo(45678, "rsHost4:1011", -21113, (byte)98, 1);

    List<RSInfo> rsList1 = newList(rsInfo1);
    List<RSInfo> rsList2 = newList(rsInfo1, rsInfo2, rsInfo3, rsInfo4);

    return new Object[][] {
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
  @Test(enabled = true, dataProvider = "createTopologyData")
  public void topologyMsgTest(List<DSInfo> dsList, List<RSInfo> rsList)
      throws Exception
  {
    TopologyMsg msg = new TopologyMsg(dsList, rsList);
    TopologyMsg newMsg = new TopologyMsg(msg.getBytes(getCurrentVersion()), getCurrentVersion());
    assertEquals(msg.getReplicaInfos(), newMsg.getReplicaInfos());
    assertEquals(msg.getRsInfos(), newMsg.getRsInfos());
  }

  /**
   * Provider for the StartSessionMsg test.
   */
  @DataProvider(name = "createStartSessionData")
  public Object[][] createStartSessionData()
  {
    List<String> urls1 = new ArrayList<>();
    urls1.add("ldap://ldap.iplanet.com/" + TEST_ROOT_DN_STRING + "??sub?(sn=Jensen)");
    urls1.add("ldaps://ldap.iplanet.com:4041/uid=bjensen,ou=People," +
      TEST_ROOT_DN_STRING + "?cn,mail,telephoneNumber");

    List<String> urls2 = new ArrayList<>();
    urls2.add("ldap://ldap.example.com/" + TEST_ROOT_DN_STRING + "?objectClass?one");
    urls2.add("ldap://host.example.com/ou=people," + TEST_ROOT_DN_STRING + "???(sn=a*)");

    List<String> urls3 = new ArrayList<>();
    urls3.add("ldaps://host:port/dc=foo??sub?(sn=John Doe)");
    urls3.add("ldap://jnumail1.state.ak.us/o=state.ak.us?mail,departmentnumber"
      + "?sub?(&(departmentnumber=04*) (l=Juneau))");

    List<String> urls4 = new ArrayList<>();

    List<String> urls5 = new ArrayList<>();
    urls5.add("ldaps://host:port/dc=foo??sub?(sn=One Entry)");

    List<String> urls6 = new ArrayList<>();
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=One Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Second Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Third Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Fourth Entry)");
    urls6.add("ldaps://host:port/dc=foo??sub?(sn=Fifth Entry)");

    Set<String> a1 = newSet();
    Set<String> a2 = newSet("dc");
    Set<String> a3 = newSet("dc", "uid");

    return new Object[][]{
      {ServerStatus.NORMAL_STATUS, urls1, true, AssuredMode.SAFE_DATA_MODE, (byte)1, a1},
      {ServerStatus.DEGRADED_STATUS, urls2, false, AssuredMode.SAFE_READ_MODE, (byte)123, a2},
      {ServerStatus.FULL_UPDATE_STATUS, urls3, false, AssuredMode.SAFE_DATA_MODE, (byte)111, a3},
      {ServerStatus.NORMAL_STATUS, urls4, true, AssuredMode.SAFE_READ_MODE, (byte)-1, a1},
      {ServerStatus.DEGRADED_STATUS, urls5, true, AssuredMode.SAFE_DATA_MODE, (byte)97, a2},
      {ServerStatus.FULL_UPDATE_STATUS, urls6, false, AssuredMode.SAFE_READ_MODE, (byte)-13, a3}
    };
  }

  /**
   * Test StartSessionMsg encoding and decoding.
   */
  @Test(enabled=true,dataProvider = "createStartSessionData")
  public void startSessionMsgTest(ServerStatus status, List<String> refUrls,
    boolean assuredFlag, AssuredMode assuredMode, byte safedataLevel,
    Set<String> attrs)
    throws Exception
  {
    StartSessionMsg msg = new StartSessionMsg(status, refUrls, assuredFlag,
      assuredMode, safedataLevel);
    msg.setEclIncludes(attrs, attrs);
    StartSessionMsg newMsg =
      new StartSessionMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getStatus(), newMsg.getStatus());
    assertEquals(msg.isAssured(), newMsg.isAssured());
    assertEquals(msg.getAssuredMode(), newMsg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), newMsg.getSafeDataLevel());
    assertEquals(msg.getReferralsURLs(), newMsg.getReferralsURLs());
    Assertions.assertThat(attrs).isEqualTo(newMsg.getEclIncludes());
    Assertions.assertThat(attrs).isEqualTo(newMsg.getEclIncludesForDeletes());
  }

  /**
   * Provider for the ChangeStatusMsg test.
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
  @Test(enabled=true,dataProvider = "createChangeStatusData")
  public void changeStatusMsgTest(ServerStatus reqStatus, ServerStatus newStatus)
    throws Exception
  {
    ChangeStatusMsg msg = new ChangeStatusMsg(reqStatus, newStatus);
    ChangeStatusMsg newMsg = new ChangeStatusMsg(msg.getBytes(getCurrentVersion()));
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
    HeartbeatMsg newMsg = new HeartbeatMsg(msg.getBytes(getCurrentVersion()));
    assertNotNull(newMsg);
  }

  /**
   * Test ResetGenerationIdMsg encoding and decoding.
   */
  @Test
  public void resetGenerationIdMsgTest() throws Exception
  {
    ResetGenerationIdMsg msg = new ResetGenerationIdMsg(23657);
    ResetGenerationIdMsg newMsg = new ResetGenerationIdMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
  }

  /**
   * Test MonitorRequestMsg encoding and decoding.
   */
  @Test
  public void monitorRequestMsgTest() throws Exception
  {
    MonitorRequestMsg msg = new MonitorRequestMsg(1,2);
    MonitorRequestMsg newMsg = new MonitorRequestMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(newMsg.getDestination(), 2);
    assertEquals(newMsg.getSenderID(), 1);
  }

  /**
   * Test MonitorMsg.
   */
  @Test
  public void monitorMsgTest() throws Exception
  {
    int sender = 2;
    int dest = 3;

    // RS State
    ServerState rsState = new ServerState();
    rsState.update(new CSN(1, 1, 1));
    rsState.update(new CSN(1, 1, 45678));

    // LS1 state
    ServerState s1 = new ServerState();
    int sid1 = 111;
    s1.update(new CSN(1, 1, sid1));

    // LS2 state
    ServerState s2 = new ServerState();
    int sid2 = 222;
    long now = 10;
    s2.update(new CSN(now, 123, sid2));

    // LS3 state
    ServerState s3 = new ServerState();
    int sid3 = 56789;
    s3.update(new CSN(now, 123, sid3));

    MonitorMsg msg = new MonitorMsg(sender, dest);
    msg.setReplServerDbState(rsState);
    msg.setServerState(sid1, s1, now+1, true);
    msg.setServerState(sid2, s2, now+2, true);
    msg.setServerState(sid3, s3, now+3, false);

    byte[] b = msg.getBytes(getCurrentVersion());
    MonitorMsg newMsg = new MonitorMsg(b, getCurrentVersion());

    assertEquals(rsState, msg.getReplServerDbState());
    assertEquals(newMsg.getReplServerDbState().toString(),
        msg.getReplServerDbState().toString());

    for (int sid : toIterable(newMsg.ldapIterator()))
    {
      ServerState s = newMsg.getLDAPServerState(sid);
      if (sid == sid1)
      {
        assertEquals(s.toString(), s1.toString());
        assertEquals(now + 1, newMsg.getLDAPApproxFirstMissingDate(sid));
      }
      else if (sid == sid2)
      {
        assertEquals(s.toString(), s2.toString());
        assertEquals(now + 2, newMsg.getLDAPApproxFirstMissingDate(sid));
      }
      else
      {
        fail("Bad sid" + sid);
      }
    }

    for (int sid : toIterable(newMsg.rsIterator()))
    {
      ServerState s = newMsg.getRSServerState(sid);
      if (sid == sid3)
      {
        assertEquals(s.toString(), s3.toString());
        assertEquals(now + 3, newMsg.getRSApproxFirstMissingDate(sid));
      }
      else
      {
        fail("Bad sid " + sid);
      }
    }

    assertEquals(newMsg.getSenderID(), msg.getSenderID());
    assertEquals(newMsg.getDestination(), msg.getDestination());
  }

  /**
   * Test that EntryMsg encoding and decoding works
   * by checking that : msg == new EntryMessageTest(msg.getBytes()).
   */
  @Test
  public void entryMsgTest() throws Exception
  {
    String taskInitFromS2 =
        "dn: ds-task-id=" + UUID.randomUUID() +
        ",cn=Scheduled Tasks,cn=Tasks\n" +
        "objectclass: top\n" +
        "objectclass: ds-task\n" +
        "objectclass: ds-task-initialize\n" +
        "ds-task-class-name: org.opends.server.replication.api.InitializeTask\n" +
        "ds-task-initialize-domain-dn: " + TEST_ROOT_DN_STRING  + "\n" +
        "ds-task-initialize-source: 1\n";
    int sender = 1;
    int target = 45678;
    byte[] entry = taskInitFromS2.getBytes();
    EntryMsg msg = new EntryMsg(sender, target, entry, 1);
    EntryMsg newMsg = new EntryMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getEntryBytes(), newMsg.getEntryBytes());
  }

  /**
   * Test that InitializeRequestMsg encoding and decoding works.
   */
  @Test
  public void initializeRequestMsgTest() throws Exception
  {
    int sender = 1;
    int target = 56789;
    InitializeRequestMsg msg = new InitializeRequestMsg(
        TEST_ROOT_DN, sender, target, 100);
    InitializeRequestMsg newMsg = new InitializeRequestMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());
  }

  /**
   * Test that InitializeTargetMsg encoding and decoding works.
   */
  @Test
  public void initializeTargetMsgTest() throws Exception
  {
    int senderID = 45678;
    int targetID = 2;
    int requestorID = 3;
    long entryCount = 4;
    int initWindow = 100;

    InitializeTargetMsg msg = new InitializeTargetMsg(
        TEST_ROOT_DN, senderID, targetID, requestorID, entryCount, initWindow);
    InitializeTargetMsg newMsg = new InitializeTargetMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getInitiatorID(), newMsg.getInitiatorID());
    assertEquals(msg.getEntryCount(), newMsg.getEntryCount());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());

    assertEquals(senderID, newMsg.getSenderID());
    assertEquals(targetID, newMsg.getDestination());
    assertEquals(requestorID, newMsg.getInitiatorID());
    assertEquals(entryCount, newMsg.getEntryCount());
    assertEquals(TEST_ROOT_DN, newMsg.getBaseDN());
  }

  /**
   * Test that DoneMsg encoding and decoding works.
   */
  @Test
  public void doneMsgTest() throws Exception
  {
    DoneMsg msg = new DoneMsg(1, 2);
    DoneMsg newMsg = new DoneMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
  }

  /**
   * Test that ErrorMsg encoding and decoding works.
   */
  @Test
  public void errorMsgTest() throws Exception
  {
    ErrorMsg msg = new ErrorMsg(1, 2, LocalizableMessage.raw("details"));
    ErrorMsg newMsg = new ErrorMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getMsgID(), newMsg.getMsgID());
    assertEquals(msg.getDetails(), newMsg.getDetails());
  }

  /**
   * Test Generic UpdateMsg.
   */
  @Test
  public void UpdateMsgTest() throws Exception
  {
    final String test = "string used for test";
    CSN csn = new CSN(1, 2, 39123);
    UpdateMsg msg = new UpdateMsg(csn, test.getBytes());
    UpdateMsg newMsg = new UpdateMsg(msg.getBytes());
    assertEquals(test.getBytes(), newMsg.getPayload());
  }

  private int perfRep = 100000;


  @Test(enabled=false,dataProvider = "createAddData")
  public void addMsgPerfs(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList) throws Exception
  {
    Map<ObjectClass, String> objectClassList = new HashMap<>();
    objectClassList.put(DirectoryServer.getObjectClass("organization"), "organization");

    Attribute attr = Attributes.create("o", "com");
    Map<AttributeType, List<Attribute>> userAttList = new HashMap<>();
    userAttList.put(attr.getAttributeType(), newList(attr));


    attr = Attributes.create("creatorsname", "dc=creator");
    Map<AttributeType, List<Attribute>> opList = new HashMap<>();
    opList.put(attr.getAttributeType(), newList(attr));

    CSN csn = new CSN(TimeThread.getTime(), 123, 45);
    DN dn = DN.valueOf(rawDN);

    long createop = 0;
    long createmsgfromop = 0;
    long encodemsg = 0;
    long getbytes = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      AddOperation addOpB = new AddOperationBasis(connection,
          1, 1, null, dn, objectClassList, userAttList, opList);
      LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpB);
      OperationContext opCtx = new AddContext(csn, "thisIsaUniqueID",
          "parentUniqueId");
      addOp.setAttachment(SYNCHROCONTEXT, opCtx);
      t2 = System.nanoTime();
      createop += t2 - t1;

      // create msg from op
      AddMsg generatedMsg = new AddMsg(addOp);
      t3 = System.nanoTime();
      createmsgfromop += t3 - t2;

      // set entry attr
      generatedMsg.setEclIncludes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += t31 - t3;

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += t4 - t31;

      // getBytes
      byte[] bytes = generatedMsg.getBytes(getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += t5 - t4;

      // getBytes
      new AddMsg(bytes);
      t6 = System.nanoTime();
      buildnew += t6 - t5;
    }

    System.out.println(
        "addMsgPerfs "
        + "createop\t"
        + "createmsgfromop\t"
        + "setentryattr\t"
        + "encodemsg\t"
        + "getbytes\t"
        + "buildnew\t");

    System.out.println(
        "addMsgPerfs "
        + createop/perfRep/1000.0 + " micros \t"
        + createmsgfromop/perfRep/1000.0 + " micros \t"
        + setentryattr/perfRep/1000.0 + " micros \t"
        + encodemsg/perfRep/1000.0 + " micros \t"
        + getbytes/perfRep/1000.0 + " micros \t"
        + buildnew/perfRep/1000.0 + " micros \t");
  }

  @Test(enabled=false,dataProvider = "createModifyData")
  public void modMsgPerfs(CSN csn, String rawdn, List<Modification> mods,
      boolean isAssured, AssuredMode assuredMode, byte safeDataLevel,
      List<Attribute> entryAttrList) throws Exception
  {
    CSN csn2 = new CSN(TimeThread.getTime(), 123, 45);
    DN dn = DN.valueOf(rawdn);

    long createop = 0;
    long createmsgfromop = 0;
    long encodemsg = 0;
    long getbytes = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      ModifyOperation modifyOpB = new ModifyOperationBasis(
          connection, 1, 1, null, dn, mods);
      LocalBackendModifyOperation modifyOp =
        new LocalBackendModifyOperation(modifyOpB);
      OperationContext opCtx = new ModifyContext(csn2, "thisIsaUniqueID");
      modifyOp.setAttachment(SYNCHROCONTEXT, opCtx);
      t2 = System.nanoTime();
      createop += t2 - t1;

      // create msg from op
      ModifyMsg generatedMsg = new ModifyMsg(modifyOp);
      t3 = System.nanoTime();
      createmsgfromop += t3 - t2;

      // set entry attr
      // generatedMsg.setEntryAttributes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += t31 - t3;

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += t4 - t31;

      // getBytes
      byte[] bytes = generatedMsg.getBytes(getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += t5 - t4;

      // getBytes
      new ModifyMsg(bytes);
      t6 = System.nanoTime();
      buildnew += t6 - t5;
    }

    System.out.println(
        "modMsgPerfs "
        + "createop\t"
        + "createmsgfromop\t"
        + "setentryattr\t"
        + "encodemsg\t"
        + "getbytes\t"
        + "buildnew\t");

    System.out.println(
        "modMsgPerfs "
        + createop/perfRep/1000.0 + " micros \t"
        + createmsgfromop/perfRep/1000.0 + " micros \t"
        + setentryattr/perfRep/1000.0 + " micros \t"
        + encodemsg/perfRep/1000.0 + " micros \t"
        + getbytes/perfRep/1000.0 + " micros \t"
        + buildnew/perfRep/1000.0 + " micros \t");
  }

  @Test(enabled=false,dataProvider = "createDeleteData")
  public void deleteMsgPerfs(String rawDN, List<Attribute> entryAttrList)
  throws Exception
  {
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();

    long createop = 0;
    long createmsgfromop = 0;
    long encodemsg = 0;
    long getbytes = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      DeleteOperation deleteOp =
        new DeleteOperationBasis(connection, 1, 1,null, DN.valueOf(rawDN));
      LocalBackendDeleteOperation op =
          new LocalBackendDeleteOperation(deleteOp);
      CSN csn = new CSN(TimeThread.getTime(), 123, 45);
      op.setAttachment(SYNCHROCONTEXT, new DeleteContext(csn, "uniqueid"));
      t2 = System.nanoTime();
      createop += t2 - t1;

      // create msg from op
      DeleteMsg generatedMsg = new DeleteMsg(op);
      t3 = System.nanoTime();
      createmsgfromop += t3 - t2;

      // set entry attr
      //generatedMsg.setEntryAttributes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += t31 - t3;

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += t4 - t31;

      // getBytes
      byte[] bytes = generatedMsg.getBytes(getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += t5 - t4;

      // getBytes
      new DeleteMsg(bytes);
      t6 = System.nanoTime();
      buildnew += t6 - t5;
    }

    System.out.println(
        "deleteMsgPerfs "
        + "createop\t"
        + "createmsgfromop\t"
        + "setentryattr\t"
        + "encodemsg\t"
        + "getbytes\t"
        + "buildnew\t");

    System.out.println(
        "deleteMsgPerfs "
        + createop/perfRep/1000.0 + " micros \t"
        + createmsgfromop/perfRep/1000.0 + " micros \t"
        + setentryattr/perfRep/1000.0 + " micros \t"
        + encodemsg/perfRep/1000.0 + " micros \t"
        + getbytes/perfRep/1000.0 + " micros \t"
        + buildnew/perfRep/1000.0 + " micros \t");
  }
}
