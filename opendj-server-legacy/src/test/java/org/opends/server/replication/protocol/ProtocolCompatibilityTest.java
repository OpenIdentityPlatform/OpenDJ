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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.AssuredMode.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the conversions between the various protocol versions.
 */
@SuppressWarnings("javadoc")
public class ProtocolCompatibilityTest extends ReplicationTestCase {

  short REPLICATION_PROTOCOL_VLAST = ProtocolVersion.getCurrentVersion();

  /** Set up the environment for performing the tests in this class. */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
  }

  /** Clean up the environment. */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    super.classCleanUp();
  }

  @DataProvider(name="createReplServerStartData")
  public Object[][] createReplServerStartData() throws Exception
  {
    final DN baseDN1 = DN.valueOf("o=test");
    final ServerState state1 = new ServerState();
    state1.update(new CSN(0, 0,0));

    final DN baseDN2 = DN.valueOf("dc=example,dc=com");
    final ServerState state2 = new ServerState();
    state2.update(new CSN(75, 5,263));

    return new Object[][]
    {
      {  1, baseDN1,   0, "localhost:8989",   state1,    0L, (byte)  0,    0},
      { 16, baseDN2, 100, "anotherHost:1025", state2, 1245L, (byte) 25, 3456},
    };
  }

  /**
   * Test that various combinations of ReplServerStartMsg encoding and decoding
   * using protocol VLAST and V2 are working.
   */
  @Test(dataProvider="createReplServerStartData")
  public void replServerStartMsgTestVLASTV2(int serverId, DN baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    // TODO: replServerStartMsgTestV3V2 as soon as V3 will have any incompatibility with V2
  }

  @Test(dataProvider="createReplServerStartData")
  public void replServerStartMsgTestVLASTV1(int serverId, DN baseDN, int window,
        String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    // Create message with no version.
    ReplServerStartMsg msg = new ReplServerStartMsg(serverId,
        url, baseDN, window, state, genId, true, groupId, degTh);

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
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getCSN(1), newMsg.getServerState().getCSN(1));
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());

    // Check default value for only post V1 fields
    assertEquals(newMsg.getGroupId(), (byte) -1);
    assertEquals(newMsg.getDegradedStatusThreshold(), -1);

    // Set again only post V1 fields
    newMsg.setGroupId(groupId);
    newMsg.setDegradedStatusThreshold(degTh);

    // Serialize in VLAST msg
    ReplServerStartMsg vlastMsg = new ReplServerStartMsg(newMsg.getBytes(getCurrentVersion()));

    // Check original version of message
    assertEquals(vlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original V3 message (V3 fields)
    assertEquals(msg.getGenerationId(), vlastMsg.getGenerationId());
    assertEquals(msg.getServerId(), vlastMsg.getServerId());
    assertEquals(msg.getServerURL(), vlastMsg.getServerURL());
    assertEquals(msg.getBaseDN(), vlastMsg.getBaseDN());
    assertEquals(msg.getWindowSize(), vlastMsg.getWindowSize());
    assertEquals(msg.getServerState().getCSN(1), vlastMsg.getServerState().getCSN(1));
    assertEquals(msg.getSSLEncryption(), vlastMsg.getSSLEncryption());
    assertEquals(msg.getGroupId(), vlastMsg.getGroupId());
    assertEquals(msg.getDegradedStatusThreshold(), vlastMsg.getDegradedStatusThreshold());
  }

  @DataProvider(name = "createAddData")
  public Object[][] createAddData()
  {
    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = newArrayList(eattr1, eattr2);

    return new Object[][] {
      {"dc=example,dc=com", false, AssuredMode.SAFE_DATA_MODE, (byte)0, null},
      {"o=test", true, AssuredMode.SAFE_READ_MODE, (byte)1, entryAttrList},
      {"o=group,dc=example,dc=com", true, AssuredMode.SAFE_READ_MODE, (byte)3, entryAttrList}};
  }

  @Test(dataProvider = "createAddData")
  public void addMsgTestVLASTV2(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
      throws Exception
  {
    // TODO: addMsgTest as soon as V3 will have any incompatibility with V2
  }

  @Test(dataProvider = "createAddData")
  public void addMsgTestVLASTV1(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
      throws Exception
  {
    final DN dn = DN.valueOf(rawDN);

    // Create VLAST message
    Attribute objectClass = Attributes.create(getObjectClassAttributeType(), "organization");
    List<Attribute> userAttributes = newArrayList(Attributes.create("o", "com"));
    List<Attribute> operationalAttributes = newArrayList(Attributes.create("creatorsName", "dc=creator"));

    CSN csn = new CSN(TimeThread.getTime(), 123, 45);

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

    // Check version of message
    assertEquals(msg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    AddMsg newMsg = (AddMsg)ReplicationMsg.generateMsg(
        v1MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getEntryUUID(), msg.getEntryUUID());
    assertEquals(newMsg.getDN(), msg.getDN());
    assertEquals(newMsg.getCSN(), msg.getCSN());
    assertEquals(newMsg.isAssured(), msg.isAssured());
    assertEquals(newMsg.getParentEntryUUID(), msg.getParentEntryUUID());

    // Create an add operation from each message to compare attributes (kept encoded in messages)
    Operation op = msg.createOperation(connection, dn);
    Operation generatedOperation = newMsg.createOperation(connection, dn);

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

    // Check default value for only VLAST fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)1);
    assertEquals(newMsg.getEclIncludes().size(), 0);

    // Set again only VLAST fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);
    if (entryAttrList != null)
    {
      newMsg.setEclIncludes(entryAttrList);
    }

    // Serialize in VLAST msg
    AddMsg vlastMsg = (AddMsg)ReplicationMsg.generateMsg(
        newMsg.getBytes(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(vlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getEntryUUID(), vlastMsg.getEntryUUID());
    assertEquals(msg.getDN(), vlastMsg.getDN());
    assertEquals(msg.getCSN(), vlastMsg.getCSN());
    assertEquals(msg.getParentEntryUUID(), vlastMsg.getParentEntryUUID());
    assertEquals(msg.isAssured(), vlastMsg.isAssured());
    assertEquals(msg.getAssuredMode(), vlastMsg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), vlastMsg.getSafeDataLevel());

    // Get ECL entry attributes
    assertAttributesEqual(vlastMsg.getEclIncludes(), entryAttrList);

    //        Create an add operation from each message to compare attributes (kept encoded in messages)
    op = msg.createOperation(connection, dn);
    generatedOperation = vlastMsg.createOperation(connection, dn);

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

  private void assertAttributesEqual(List<RawAttribute> actualAttrs,
      List<Attribute> expectedAttrs) throws LDAPException
  {
    if (expectedAttrs == null)
    {
      assertThat(actualAttrs).isEmpty();
    }
    else
    {
      assertThat(actualAttrs).hasSize(expectedAttrs.size());
      for (int i = 0; i < expectedAttrs.size(); i++)
      {
        final Attribute expectedAttr = expectedAttrs.get(i);
        final Attribute actualAttr = actualAttrs.get(i).toAttribute();
        assertThat(expectedAttr.getAttributeDescription()).isEqualTo(actualAttr.getAttributeDescription());
        assertThat(expectedAttr.toString()).isEqualToIgnoringCase(actualAttr.toString());
      }
    }
  }

  /**
   * Build some data for the DeleteMsg test below.
   */
  @DataProvider(name = "createDeleteData")
  public Object[][] createDeleteData()
  {
    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = newArrayList(eattr1, eattr2);

    return new Object[][] {
      {"dc=example,dc=com", false, AssuredMode.SAFE_DATA_MODE, (byte)0, null},
      {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn", true, AssuredMode.SAFE_READ_MODE, (byte)1, entryAttrList},
      {"o=group,dc=example,dc=com", true, AssuredMode.SAFE_READ_MODE, (byte)3, entryAttrList}};
  }

  /**
   * Test that various combinations of DeleteMsg encoding and decoding
   * using protocol V2 and VLAST are working.
   */
  @Test(dataProvider = "createDeleteData")
  public void deleteMsgTestVLASTV2(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
      throws Exception
  {
    // TODO: deleteMsgTestVLASTV2 as soon as V3 will have any incompatibility with V2
  }

  /**
   * Test that various combinations of DeleteMsg encoding and decoding
   * using protocol V1 and VLAST are working.
   */
  @Test(dataProvider = "createDeleteData")
  public void deleteMsgTestVLASTV1(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
      throws Exception
  {
    final DN dn = DN.valueOf(rawDN);

    CSN csn = new CSN(TimeThread.getTime(), 123, 45);
    DeleteMsg msg = new DeleteMsg(dn, csn, "thisIsaUniqueID");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    // Check version of message
    assertEquals(msg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    DeleteMsg newMsg = (DeleteMsg)ReplicationMsg.generateMsg(
        v1MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getEntryUUID(), msg.getEntryUUID());
    assertEquals(newMsg.getDN(), msg.getDN());
    assertEquals(newMsg.getCSN(), msg.getCSN());
    assertEquals(newMsg.isAssured(), msg.isAssured());

    // Check default value for only VLAST fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)1);
    assertEquals(newMsg.getEclIncludes().size(), 0);

    // Set again only VLAST fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);
    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      newMsg.setEclIncludes(entryAttrList);
    }

    // Serialize in VLAST msg
    DeleteMsg vlastMsg = (DeleteMsg)ReplicationMsg.generateMsg(
        newMsg.getBytes(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(vlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getEntryUUID(), vlastMsg.getEntryUUID());
    assertEquals(msg.getDN(), vlastMsg.getDN());
    assertEquals(msg.getCSN(), vlastMsg.getCSN());
    assertEquals(msg.isAssured(), vlastMsg.isAssured());
    assertEquals(msg.getAssuredMode(), vlastMsg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), vlastMsg.getSafeDataLevel());

    // Get ECL entry attributes
    assertAttributesEqual(vlastMsg.getEclIncludes(), entryAttrList);
  }

  /**
   * Build some data for the ModifyMsg test below.
   */
  @DataProvider(name = "createModifyData")
  public Object[][] createModifyData() {
    CSN csn1 = new CSN(1, 0, 1);
    CSN csn2 = new CSN(TimeThread.getTime(), 123, 45);

    Modification mod1 = new Modification(REPLACE, Attributes.create("description", "new value"));
    List<Modification> mods1 = newArrayList(mod1);

    Modification mod2 = new Modification(DELETE, Attributes.empty("description"));
    List<Modification> mods2 = newArrayList(mod1, mod2);

    AttributeBuilder builder = new AttributeBuilder(getDescriptionAttributeType());
    builder.add("string");
    builder.add("value");
    builder.add("again");
    List<Modification> mods3 = newArrayList(new Modification(ADD, builder.toAttribute()));

    List<Modification> mods4 = new ArrayList<>();
    for (int i = 0; i < 10; i++)
    {
      Attribute attr = Attributes.create("description", "string" + i);
      mods4.add(new Modification(ADD, attr));
    }

    Modification mod5 = new Modification(REPLACE, Attributes.create("namingcontexts", "o=test"));
    List<Modification> mods5 = newArrayList(mod5);

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = newArrayList(eattr1, eattr2);

    // @Checkstyle:off
    return new Object[][] {
        { csn1, "dc=test", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte) 0, null },
        { csn2, "dc=cn2", mods1, true, AssuredMode.SAFE_READ_MODE, (byte) 1, entryAttrList },
        { csn2, "dc=test with a much longer dn in case this would "
               + "make a difference", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { csn2, "dc=test, cn=with a, o=more complex, ou=dn", mods1, false, AssuredMode.SAFE_READ_MODE, (byte) 5, entryAttrList },
        { csn2, "cn=use\\, backslash", mods1, true, AssuredMode.SAFE_READ_MODE, (byte) 3, null },
        { csn2, "dc=test with several mod", mods2, false, AssuredMode.SAFE_DATA_MODE, (byte) 16, entryAttrList },
        { csn2, "dc=test with several values", mods3, false, AssuredMode.SAFE_READ_MODE, (byte) 3, null },
        { csn2, "dc=test with long mod", mods4, true, AssuredMode.SAFE_READ_MODE, (byte) 120, entryAttrList },
        { csn2, "dc=testDsaOperation", mods5, true, AssuredMode.SAFE_DATA_MODE, (byte) 99, null },
        };
    // @Checkstyle:on
  }

  /**
   * Test that various combinations of ModifyMsg encoding and decoding
   * using protocol V1 and V2 are working.
   */
  @Test(dataProvider = "createModifyData")
  public void modifyMsgTestVLASTV2(CSN csn,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel,
                               List<Attribute> entryAttrList)
         throws Exception
  {
    // TODO: modifyMsgTestVLASTV2 as soon as V3 will have any incompatibility with V2
  }

  /**
   * Test that various combinations of ModifyMsg encoding and decoding
   * using protocol V1 and VLAST are working.
   */
  @Test(enabled=false,dataProvider = "createModifyData")
  public void modifyMsgTestVLASTV1(CSN csn,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel,
                               List<Attribute> entryAttrList)
         throws Exception
  {
    // Create VLAST message
    DN dn = DN.valueOf(rawdn);
    ModifyMsg origVlastMsg = new ModifyMsg(csn, dn, mods, "fakeuniqueid");

    origVlastMsg.setAssured(isAssured);
    origVlastMsg.setAssuredMode(assuredMode);
    origVlastMsg.setSafeDataLevel(safeDataLevel);
    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      origVlastMsg.setEclIncludes(entryAttrList);
    }

    // Check version of message
    assertEquals(origVlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Serialize in V1
    byte[] v1MsgBytes = origVlastMsg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    ModifyMsg newv1Msg = (ModifyMsg)ReplicationMsg.generateMsg(
        v1MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(newv1Msg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newv1Msg.getEntryUUID(), origVlastMsg.getEntryUUID());
    assertEquals(newv1Msg.getDN(), origVlastMsg.getDN());
    assertEquals(newv1Msg.getCSN(), origVlastMsg.getCSN());
    assertEquals(newv1Msg.isAssured(), origVlastMsg.isAssured());

    // Create a modify operation from each message to compare mods (kept encoded in messages)
    Operation opFromOrigVlast = origVlastMsg.createOperation(connection);
    Operation opFromV1 = newv1Msg.createOperation(connection);

    assertEquals(opFromOrigVlast.getClass(), ModifyOperationBasis.class);
    assertEquals(opFromV1.getClass(), ModifyOperationBasis.class);

    ModifyOperationBasis modOpBasisFromOrigVlast = (ModifyOperationBasis) opFromOrigVlast;
    ModifyOperationBasis genModOpBasisFromV1 = (ModifyOperationBasis) opFromV1;

    assertEquals(modOpBasisFromOrigVlast.getRawEntryDN(), genModOpBasisFromV1.getRawEntryDN());
    assertEquals( modOpBasisFromOrigVlast.getAttachment(SYNCHROCONTEXT),
                  genModOpBasisFromV1.getAttachment(SYNCHROCONTEXT));
    List<Modification> modsvlast = modOpBasisFromOrigVlast.getModifications();
    List<Modification> modsv1 = genModOpBasisFromV1.getModifications();

    assertEquals(modsvlast, modsv1);

    // Check default value for only VLAST fields
    assertEquals(newv1Msg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newv1Msg.getSafeDataLevel(), (byte)1);

    // Set again only VLAST fields
    newv1Msg.setAssuredMode(assuredMode);
    newv1Msg.setSafeDataLevel(safeDataLevel);
    if (entryAttrList != null)
    {
      newv1Msg.setEclIncludes(entryAttrList);
    }

    // Serialize in VLAST msg
    ModifyMsg generatedVlastMsg = (ModifyMsg)ReplicationMsg.generateMsg(
        newv1Msg.getBytes(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(generatedVlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(origVlastMsg.getEntryUUID(), generatedVlastMsg.getEntryUUID());
    assertEquals(origVlastMsg.getDN(), generatedVlastMsg.getDN());
    assertEquals(origVlastMsg.getCSN(), generatedVlastMsg.getCSN());
    assertEquals(origVlastMsg.isAssured(), generatedVlastMsg.isAssured());
    assertEquals(origVlastMsg.getAssuredMode(), generatedVlastMsg.getAssuredMode());
    assertEquals(origVlastMsg.getSafeDataLevel(), generatedVlastMsg.getSafeDataLevel());
    assertEquals(origVlastMsg.getSafeDataLevel(), generatedVlastMsg.getSafeDataLevel());
    // Get ECL entry attributes
    assertAttributesEqual(generatedVlastMsg.getEclIncludes(), entryAttrList);

    // Create a modify operation from each message to compare mods (kept encoded in messages)
    opFromOrigVlast = origVlastMsg.createOperation(connection);
    Operation opFromGeneratedVlastMsg = generatedVlastMsg.createOperation(connection);

    assertEquals(opFromOrigVlast.getClass(), ModifyOperationBasis.class);
    assertEquals(opFromGeneratedVlastMsg.getClass(), ModifyOperationBasis.class);

    modOpBasisFromOrigVlast = (ModifyOperationBasis) opFromOrigVlast;
    ModifyOperationBasis modOpBasisFromGeneratedVlast = (ModifyOperationBasis) opFromGeneratedVlastMsg;

    assertEquals(modOpBasisFromOrigVlast.getRawEntryDN(),
        modOpBasisFromGeneratedVlast.getRawEntryDN());
    assertEquals( modOpBasisFromOrigVlast.getAttachment(SYNCHROCONTEXT),
        modOpBasisFromGeneratedVlast.getAttachment(SYNCHROCONTEXT));
    assertEquals(modOpBasisFromOrigVlast.getModifications(),
        modOpBasisFromGeneratedVlast.getModifications());
  }

  @DataProvider(name = "createModifyDnData")
  public Object[][] createModifyDnData() {

    AttributeType type = DirectoryServer.getSchema().getAttributeType("description");

    Modification mod1 = new Modification(REPLACE, Attributes.create("description", "new value"));
    List<Modification> mods1 = newArrayList(mod1);

    Modification mod2 = new Modification(DELETE, Attributes.empty("description"));
    List<Modification> mods2 = newArrayList(mod1, mod2);

    AttributeBuilder builder = new AttributeBuilder(type);
    builder.add("string");
    builder.add("value");
    builder.add("again");
    Modification mod3 = new Modification(ADD, builder.toAttribute());
    List<Modification> mods3 = newArrayList(mod3);

    List<Modification> mods4 = new ArrayList<>();
    for (int i = 0; i < 10; i++)
    {
      mods4.add(new Modification(ADD, Attributes.create("description", "string" + i)));
    }

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = newArrayList(eattr1, eattr2);

    // @Checkstyle:off
    return new Object[][] {
        {"dc=test,dc=com", "dc=new", "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", false, "dc=change", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0, null},
        {"dc=test,dc=com", "dc=new", "33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444", true, "dc=change", mods2, true, AssuredMode.SAFE_READ_MODE, (byte)1, entryAttrList},
        {"dc=test,dc=com", "dc=new", "55555555-5555-5555-5555-555555555555", "66666666-6666-6666-6666-666666666666", false, null, mods3, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn",
                   "dc=new", "77777777-7777-7777-7777-777777777777", "88888888-8888-8888-8888-888888888888",true, null, mods4, true, AssuredMode.SAFE_DATA_MODE, (byte)99, entryAttrList},
        };
    // @Checkstyle:on
  }

  /**
   * Test that various combinations of ModifyDnMsg encoding and decoding
   * using protocol VLAST and V2 are working.
   */
  @Test(dataProvider = "createModifyDnData")
  public void modifyDnMsgTestVLASTV2(String rawDN, String newRdn, String uid, String newParentUid,
                                   boolean deleteOldRdn, String newSuperior,
                                   List<Modification> mods, boolean isAssured,
                                   AssuredMode assuredMode, byte safeDataLevel,
                                   List<Attribute> entryAttrList)
         throws Exception
  {
    // TODO: modifyMsgTestVLASTV2 as soon as V3 will have any incompatibility with V2
  }

  /**
   * Test that various combinations of ModifyDnMsg encoding and decoding
   * using protocol VLAST and V1 are working.
   */
  @Test(dataProvider = "createModifyDnData")
  public void modifyDnMsgTestVLASTV1(String rawDN, String newRdn, String uid, String newParentUid,
                                   boolean deleteOldRdn, String newSuperior,
                                   List<Modification> mods, boolean isAssured,
                                   AssuredMode assuredMode, byte safeDataLevel,
                                   List<Attribute> entryAttrList)
         throws Exception
  {
    final DN dn = DN.valueOf(rawDN);

    // Create VLAST message
    CSN csn = new CSN(TimeThread.getTime(), 596, 13);
    ModifyDNMsg msg = new ModifyDNMsg(dn, csn, uid,
                     newParentUid, deleteOldRdn,
                     newSuperior, newRdn, mods);

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    // Check version of message
    assertEquals(msg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Serialize in V1
    byte[] v1MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Un-serialize V1 message
    ModifyDNMsg newMsg = (ModifyDNMsg)ReplicationMsg.generateMsg(
        v1MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(newMsg.getVersion(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check fields common to both versions
    assertEquals(newMsg.getEntryUUID(), msg.getEntryUUID());
    assertEquals(newMsg.getDN(), msg.getDN());
    assertEquals(newMsg.getCSN(), msg.getCSN());
    assertEquals(newMsg.isAssured(), msg.isAssured());
    assertEquals(newMsg.getNewRDN(), msg.getNewRDN());
    assertEquals(newMsg.getNewSuperior(), msg.getNewSuperior());
    assertEquals(newMsg.getNewSuperiorEntryUUID(), msg.getNewSuperiorEntryUUID());
    assertEquals(newMsg.deleteOldRdn(), msg.deleteOldRdn());

    // Create a modDn operation from each message to compare fields)
    Operation op = msg.createOperation(connection);
    Operation generatedOperation = newMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyDNOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyDNOperationBasis.class);
    ModifyDNOperationBasis modDnOpBasis = (ModifyDNOperationBasis) op;
    ModifyDNOperationBasis genModDnOpBasis = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(modDnOpBasis.getRawEntryDN(), genModDnOpBasis.getRawEntryDN());
    assertEquals( modDnOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModDnOpBasis.getAttachment(SYNCHROCONTEXT));

    // Check default value for only VLAST fields
    assertEquals(newMsg.getAssuredMode(), AssuredMode.SAFE_DATA_MODE);
    assertEquals(newMsg.getSafeDataLevel(), (byte)1);
    assertEquals(modDnOpBasis.getModifications(), mods);
    assertNull(genModDnOpBasis.getModifications());

    // Set again only VLAST fields
    newMsg.setAssuredMode(assuredMode);
    newMsg.setSafeDataLevel(safeDataLevel);
    newMsg.setMods(mods);
    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      newMsg.setEclIncludes(entryAttrList);
    }

    // Serialize in VLAST msg
    ModifyDNMsg vlastMsg = (ModifyDNMsg)ReplicationMsg.generateMsg(
        newMsg.getBytes(), ProtocolVersion.REPLICATION_PROTOCOL_V1);

    // Check original version of message
    assertEquals(vlastMsg.getVersion(), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getEntryUUID(), vlastMsg.getEntryUUID());
    assertEquals(msg.getDN(), vlastMsg.getDN());
    assertEquals(msg.getCSN(), vlastMsg.getCSN());
    assertEquals(msg.isAssured(), vlastMsg.isAssured());
    assertEquals(msg.getAssuredMode(), vlastMsg.getAssuredMode());
    assertEquals(msg.getSafeDataLevel(), vlastMsg.getSafeDataLevel());
    assertEquals(msg.getNewRDN(), vlastMsg.getNewRDN());
    assertEquals(msg.getNewSuperior(), vlastMsg.getNewSuperior());
    assertEquals(msg.getNewSuperiorEntryUUID(), vlastMsg.getNewSuperiorEntryUUID());
    assertEquals(msg.deleteOldRdn(), vlastMsg.deleteOldRdn());

    // Get ECL entry attributes
    assertAttributesEqual(vlastMsg.getEclIncludes(), entryAttrList);

    // Create a modDn operation from each message to compare mods (kept encoded in messages)
    op = msg.createOperation(connection);
    generatedOperation = vlastMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyDNOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyDNOperationBasis.class);
    modDnOpBasis = (ModifyDNOperationBasis) op;
    genModDnOpBasis = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(modDnOpBasis.getRawEntryDN(), genModDnOpBasis.getRawEntryDN());
    assertEquals( modDnOpBasis.getAttachment(SYNCHROCONTEXT),
                  genModDnOpBasis.getAttachment(SYNCHROCONTEXT));
    assertEquals(modDnOpBasis.getModifications(), genModDnOpBasis.getModifications());
  }

  private static byte[] hexStringToByteArray(String s)
  {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2)
    {
      data[i / 2] =
        (byte) ((Character.digit(s.charAt(i), 16) << 4)
               + Character.digit(s.charAt(i+1), 16));
    }
    return data;
  }

  @DataProvider(name = "createOldUpdateData")
  public Object[][] createOldUpdateData()
  {
    return new Object[][] {
        {"1603303030303030303030303030303030313030303130303030303030300064633" +
         "d746573740066616b65756e69717565696400000200301f0a0102301a040b646573" +
         "6372697074696f6e310b04096e65772076616c756500",
          ModifyMsg.class, new CSN(1, 0, 1), "dc=test" },
        {"1803303030303031323366313238343132303030326430303030303037620064633" +
         "d636f6d00756e69717565696400000201",
            DeleteMsg.class, new CSN(0x123f1284120L,123,45), "dc=com"},
        {"1803303030303031323366313238343132303030326430303030303037620064633" +
         "d64656c6574652c64633d616e2c64633d656e7472792c64633d776974682c64633d" +
         "612c64633d6c6f6e6720646e00756e69717565696400000201",
            DeleteMsg.class, new CSN(0x123f1284120L,123,45),
            "dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn"},
        {"1903303030303031323366313238613762333030326430303030303037620064633" +
         "d746573742c64633d636f6d00756e6971756569640000020164633d6e6577006463" +
         "3d6368616e6765006e6577706172656e7449640000301f0a0102301a040b6465736" +
         "372697074696f6e310b04096e65772076616c756500",
            ModifyDNMsg.class, new CSN(0x123f128a7b3L,123,45), "dc=test,dc=com"},
        {"1703303030303031323366313239333431323030326430303030303037620064633" +
         "d6578616d706c652c64633d636f6d0074686973497361556e697175654944000002" +
         "00706172656e74556e69717565496400301d040b6f626a656374436c617373310e0" +
         "40c6f7267616e697a6174696f6e300a04016f31050403636f6d301c040c63726561" +
         "746f72736e616d65310c040a64633d63726561746f72",
            AddMsg.class, new CSN(0x123f1293412L,123,45), "dc=example,dc=com"}
        };
  }

  /**
   * The goal of this test is to verify that we keep the compatibility with
   * older version of the replication protocol when doing new developments.
   *
   * This test checks that the current code is able to decode
   * ReplicationMessages formatted by the older versions of the code
   * and is able to format PDU in the same way.
   *
   * The data provider generates arguments containing a pre-formatted
   * UpdateMsg and the corresponding data.
   */
  @Test(dataProvider = "createOldUpdateData")
  public void createOldUpdate(String encodedString, Class<?> msgType, CSN csn, String dn)
      throws Exception
  {
    LDAPUpdateMsg msg = (LDAPUpdateMsg) ReplicationMsg.generateMsg(
        hexStringToByteArray(encodedString), ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getDN(), DN.valueOf(dn));
    assertEquals(msg.getCSN(), csn);
    assertEquals(msg.getClass(), msgType);
    BigInteger bi = new BigInteger(msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3));
    assertEquals(bi.toString(16), encodedString);
  }

  @DataProvider(name = "createOldServerStartData")
  public Object[][] createOldServerStartData()
  {
    return new Object[][] { {
        "14"
            + byteToHex((byte) ProtocolVersion.getCurrentVersion())
            + "31323438001f6f3d74657374003136006675726f6e0030003000"
            + "300030003130300031303000747275650032363300303030303030303030303030303034"
            + "623031303730303030303030350000", 16, "o=test", (byte) 31, } };
  }

  @Test(dataProvider = "createOldServerStartData")
  public void oldServerStartPDUs(
      String oldPdu, int serverId, String dn, byte groupId) throws Exception
  {
    // This test only checks serverId dn and groupId
    // It would be nice to complete it with checks for ServerState and other
    // parameters
    ServerStartMsg msg = new ServerStartMsg(hexStringToByteArray(oldPdu));
    assertEquals(msg.getServerId(), serverId);
    assertEquals(msg.getBaseDN(), DN.valueOf(dn));
    assertEquals(msg.getGroupId(), groupId);
    BigInteger bi = new BigInteger(msg.getBytes(getCurrentVersion()));
    assertEquals(bi.toString(16), oldPdu);
  }

  @DataProvider(name = "createOldReplServerStartData")
  public Object[][] createOldReplServerStartData()
  {
    return new Object[][] {
        {"15033132343500196f3d7465737400313600616e6f74686572486f73" +
          "743a31303235003130300074727565003334353600323633003030303030303030303030" +
          "30303034623031303730303030303030350000",
          16, "o=test", (byte) 25}
    };
  }
  @Test(dataProvider = "createOldReplServerStartData")
  public void oldReplServerStartPDUs(
      String oldPdu, int serverId, String dn, byte groupId) throws Exception
  {
    // This is a ServerStartMSg with ServerId=16, baseDN=o=test and groupID=31
    // For now this test only checks those parameters.
    // It would be nice to complete it with checks for ServerState and other
    // parameters.
    ReplServerStartMsg msg = new ReplServerStartMsg(hexStringToByteArray(oldPdu));
    assertEquals(msg.getServerId(), serverId);
    assertEquals(msg.getBaseDN(), DN.valueOf(dn));
    assertEquals(msg.getGroupId(), groupId);
    BigInteger bi = new BigInteger(msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3));
    assertEquals(bi.toString(16), oldPdu);
  }

  @DataProvider(name = "createoldAckMsgData")
  public Object[][] createoldAckMsgData()
  {
    List<Integer> fservers4 = newArrayList(100, 2000, 30000);

    return new Object[][] {
        {"05303030303031323366316535383832383030326430303030303037" +
          "6200010101313030003230303000333030303000",
          new CSN(0x123f1e58828L, 123, 45), true, fservers4 }
    };
  }
  @Test(dataProvider = "createoldAckMsgData")
  public void oldAckMsgPDUs(String oldPdu, CSN csn,
      boolean hasTimeout, ArrayList<Integer> failedServers) throws Exception
  {
    AckMsg msg = new AckMsg(hexStringToByteArray(oldPdu));
    assertEquals(msg.getCSN(), csn);
    assertEquals(msg.hasTimeout(), hasTimeout);
    assertEquals(msg.getFailedServers(), failedServers);
    // We use V4 here because these PDU have not changed since 2.0.
    //BigInteger bi = new BigInteger(msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V4));
    //assertEquals(bi.toString(16), oldPdu);
  }

  @DataProvider(name = "createStartSessionData")
  public Object[][] createStartSessionData()
  {
    return new Object[][] {
        {"1b010102016c6461703a2f2f6c6461702e69706c616e65742e636f6d2f6f3d74657" +
         "3743f3f7375623f28736e3d4a656e73656e29006c646170733a2f2f6c6461702e69" +
         "706c616e65742e636f6d3a343034312f7569643d626a656e73656e2c6f753d50656" +
         "f706c652c6f3d746573743f636e2c6d61696c2c74656c6570686f6e654e756d62657200",
        ServerStatus.NORMAL_STATUS, true, AssuredMode.SAFE_DATA_MODE, (byte)1 },
        { "1b0200017b6c6461703a2f2f6c6461702e6578616d706c652e636f6d2f6f3d7465" +
          "73743f6f626a656374436c6173733f6f6e65006c6461703a2f2f686f73742e6578" +
          "616d706c652e636f6d2f6f753d70656f706c652c6f3d746573743f3f3f28736e3d612a2900",
         ServerStatus.DEGRADED_STATUS, false, AssuredMode.SAFE_READ_MODE, (byte)123 }
    };
  }

  @Test(dataProvider = "createStartSessionData")
  public void oldStartSessionPDUs(String pdu, ServerStatus status,
      boolean assured, AssuredMode assuredMode, byte level) throws Exception
  {
    StartSessionMsg msg = new StartSessionMsg(hexStringToByteArray(pdu),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getStatus(), status);
    assertEquals(msg.isAssured(), assured);
    assertEquals(msg.getAssuredMode(), assuredMode);
    assertEquals(msg.getSafeDataLevel(), level);
    BigInteger bi = new BigInteger(msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3));
    assertEquals(bi.toString(16), pdu);
  }

  @DataProvider
  public Object[][] createTopologyData() throws Exception
  {
    List<String> urls1 = newArrayList(
        "ldap://ldap.iplanet.com/o=test??sub?(sn=Jensen)",
        "ldaps://ldap.iplanet.com:4041/uid=bjensen,ou=People,o=test?cn,mail,telephoneNumber");

    List<String> urls2 = newArrayList();

    List<String> urls3 = newArrayList(
        "ldaps://host:port/dc=foo??sub?(sn=One Entry)");

    List<String> urls4 = newArrayList(
        "ldaps://host:port/dc=foobar1??sub?(sn=Another Entry 1)",
        "ldaps://host:port/dc=foobar2??sub?(sn=Another Entry 2)");

    DSInfo dsInfo1 = new DSInfo(13, "", 26, 154631, ServerStatus.FULL_UPDATE_STATUS,
      false, SAFE_DATA_MODE, (byte)12, (byte)132, urls1, new HashSet<String>(), new HashSet<String>(), (short)-1);
    DSInfo dsInfo2 = new DSInfo(-436, "", 493, -227896, ServerStatus.DEGRADED_STATUS,
      true,  SAFE_READ_MODE, (byte)-7, (byte)-265, urls2, new HashSet<String>(), new HashSet<String>(), (short)-1);
    DSInfo dsInfo3 = new DSInfo(2436, "", 591, 0, ServerStatus.NORMAL_STATUS,
      false, SAFE_READ_MODE, (byte)17, (byte)0, urls3, new HashSet<String>(), new HashSet<String>(), (short)-1);
    DSInfo dsInfo4 = new DSInfo(415, "", 146, 0, ServerStatus.BAD_GEN_ID_STATUS,
      true,  SAFE_DATA_MODE, (byte)2, (byte)15, urls4, new HashSet<String>(), new HashSet<String>(), (short)-1);

    Set<DSInfo> dsList1 = newHashSet(dsInfo1);
    Set<DSInfo> dsList2 = newHashSet();
    Set<DSInfo> dsList3 = newHashSet(dsInfo2);
    Set<DSInfo> dsList4 = newHashSet(dsInfo4, dsInfo3, dsInfo2, dsInfo1);

    RSInfo rsInfo1 = new RSInfo(4527, null, 45316, (byte)103, 1);
    RSInfo rsInfo2 = new RSInfo(4527, null, 0, (byte)0, 1);
    RSInfo rsInfo3 = new RSInfo(0, null, -21113, (byte)98, 1);

    List<RSInfo> rsList1 = newArrayList(rsInfo1);
    List<RSInfo> rsList2 = newArrayList(rsInfo1, rsInfo2, rsInfo3);

    return new Object[][] {
      {"1a01313300323600313534363331000300020c84026c6461703a2f2f6c6461702e697" +
       "06c616e65742e636f6d2f6f3d746573743f3f7375623f28736e3d4a656e73656e2900" +
       "6c646170733a2f2f6c6461702e69706c616e65742e636f6d3a343034312f7569643d6" +
       "26a656e73656e2c6f753d50656f706c652c6f3d746573743f636e2c6d61696c2c7465" +
       "6c6570686f6e654e756d6265720001343532370034353331360067",dsList1, rsList1},
      {"1a0003343532370034353331360067343532370030000030002d32313131330062", dsList2, rsList2},
      {"1a012d34333600343933002d32323738393600020101f9f70001343532370034353331360067", dsList3, rsList1},
      {"1a012d34333600343933002d32323738393600020101f9f70000", dsList3, newArrayList()},
      {"1a0001343532370034353331360067", newHashSet(), rsList1},
      {"1a0000", newHashSet(), newArrayList()},
      {"1a0434313500313436003000040102020f026c646170733a2f2f686f73743a706f727" +
       "42f64633d666f6f626172313f3f7375623f28736e3d416e6f7468657220456e747279" +
       "203129006c646170733a2f2f686f73743a706f72742f64633d666f6f626172323f3f7" +
       "375623f28736e3d416e6f7468657220456e7472792032290032343336003539310030" +
       "000100011100016c646170733a2f2f686f73743a706f72742f64633d666f6f3f3f737" +
       "5623f28736e3d4f6e6520456e74727929002d34333600343933002d32323738393600" +
       "020101f9f700313300323600313534363331000300020c84026c6461703a2f2f6c646" +
       "1702e69706c616e65742e636f6d2f6f3d746573743f3f7375623f28736e3d4a656e73" +
       "656e29006c646170733a2f2f6c6461702e69706c616e65742e636f6d3a343034312f7" +
       "569643d626a656e73656e2c6f753d50656f706c652c6f3d746573743f636e2c6d6169" +
       "6c2c74656c6570686f6e654e756d62657200033435323700343533313600673435323" +
       "70030000030002d32313131330062", dsList4, rsList2}
    };
  }

  @Test(dataProvider = "createTopologyData")
  public void oldTopologyPDUs(String oldPdu, Set<DSInfo> dsList, List<RSInfo> rsList)
         throws Exception
  {
    TopologyMsg msg = new TopologyMsg(hexStringToByteArray(oldPdu),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    Assertions.assertThat(new HashSet<DSInfo>(msg.getReplicaInfos().values()))
        .isEqualTo(dsList);
    assertEquals(msg.getRsInfos(), rsList);
    if (msg.getReplicaInfos().values().equals(dsList))
    {
      // Unfortunately this check does not work when the order of the
      // replicaInfos collection is not exactly the same as the dsList
      BigInteger bi = new BigInteger(msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3));
      assertEquals(bi.toString(16), oldPdu);
    }
  }

  @DataProvider(name="createEntryMsgData")
  public Object[][] createEntryMsgData() throws Exception
  {
    int sid = 1;
    int dest = 2;
    byte[] entryBytes = "12".getBytes();
    int pos = 0;
    int length = 2;
    int msgid = 14;

    return new Object[][] { { sid, dest, entryBytes, pos, length, msgid } };
  }

  /**
   * Test that various combinations of EntryMsg encoding and decoding
   * using protocol VLAST and V3 are working.
   */
  @Test(enabled=true, dataProvider="createEntryMsgData")
  public void entryMsgTestVLASTV3(int sid, int dest, byte[] entryBytes,
      int pos, int length, int msgId) throws Exception
  {
    // Create VLAST message
    EntryMsg msg = new EntryMsg(sid, dest, entryBytes, pos, length, msgId);

    // Serialize in V3
    byte[] v3MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Un-serialize V3 message
    EntryMsg newMsg = new EntryMsg(v3MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Check fields common to both versions
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getEntryBytes(), newMsg.getEntryBytes());

    // Check default value for only post V3 fields
    assertEquals(newMsg.getMsgId(), -1);

    // Set again only post V3 fields
    newMsg.setMsgId(msgId);

    // Serialize in VLAST
    EntryMsg vlastMsg = new EntryMsg(newMsg.getBytes(getCurrentVersion()),REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getSenderID(), vlastMsg.getSenderID());
    assertEquals(msg.getDestination(), vlastMsg.getDestination());
    assertEquals(msg.getEntryBytes(), vlastMsg.getEntryBytes());
    assertEquals(msg.getMsgId(), vlastMsg.getMsgId());
  }

  @DataProvider(name="createErrorMsgData")
  public Object[][] createErrorMsgData() throws Exception
  {
    int sender = 1;
    int dest = 2;
    LocalizableMessage message = ERR_UNKNOWN_TYPE.get("toto");
    return new Object[][] { { sender, dest, message } };
  }

  /**
   * Test that various combinations of ErrorMsg encoding and decoding
   * using protocol VLAST and V3 are working.
   */
  @Test(enabled=true, dataProvider="createErrorMsgData")
  public void errorMsgTestVLASTV3(int sender, int dest, LocalizableMessage message)
      throws Exception
  {
    // Create VLAST message
    ErrorMsg msg = new ErrorMsg(sender, dest, message);
    long creatTime = msg.getCreationTime();

    // Serialize in V3
    byte[] v3MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Un-serialize V3 message
    ErrorMsg newMsg = new ErrorMsg(v3MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Check fields common to both versions
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getMsgID(), newMsg.getMsgID());

    // Set again only post V3 fields
    newMsg.setCreationTime(creatTime);

    // Serialize in VLAST
    ErrorMsg vlastMsg = new ErrorMsg(newMsg.getBytes(getCurrentVersion()),
        REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getSenderID(), vlastMsg.getSenderID());
    assertEquals(msg.getDestination(), vlastMsg.getDestination());
    assertEquals(msg.getMsgID(), vlastMsg.getMsgID());
    assertEquals(msg.getCreationTime(), vlastMsg.getCreationTime());
  }

  @DataProvider(name="createInitializationRequestMsgData")
  public Object[][] createInitializationRequestMsgData() throws Exception
  {
    int sender = 1;
    int dest = 2;
    DN baseDN = DN.valueOf("dc=whatever");
    int initWindow = 22;
    return new Object[][]
    {
      { sender, dest, baseDN, initWindow },
    };
  }

  /**
   * Test that various combinations of ErrorMsg encoding and decoding
   * using protocol VLAST and V3 are working.
   */
  @Test(enabled=true, dataProvider="createInitializationRequestMsgData")
  public void initializationRequestMsgTestVLASTV3(int sender, int dest,
      DN baseDN, int initWindow) throws Exception
  {
    // Create VLAST message
    InitializeRequestMsg msg = new InitializeRequestMsg(baseDN, sender, dest, initWindow);

    // Serialize in V3
    byte[] v3MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Un-serialize V3 message
    InitializeRequestMsg newMsg = new InitializeRequestMsg(v3MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Check fields common to both versions
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());

    // Check default value for only post V3 fields
    assertEquals(newMsg.getInitWindow(), 0);

    // Set again only post V3 fields
    newMsg.setInitWindow(initWindow);

    // Serialize in VLAST
    InitializeRequestMsg vlastMsg = new InitializeRequestMsg(
        newMsg.getBytes(getCurrentVersion()), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getSenderID(), vlastMsg.getSenderID());
    assertEquals(msg.getDestination(), vlastMsg.getDestination());
    assertEquals(msg.getBaseDN(), vlastMsg.getBaseDN());
    assertEquals(msg.getInitWindow(), vlastMsg.getInitWindow());
  }

  @DataProvider(name="createInitializeTargetMsgData")
  public Object[][] createInitializeTargetMsgData() throws Exception
  {
    int sender = 1;
    int dest = 2;
    int initiator = 3;
    DN baseDN = DN.valueOf("dc=whatever");
    int entryCount = 56;
    int initWindow = 22;
    return new Object[][]
    {
      { sender, dest, initiator, baseDN, entryCount, initWindow },
    };
  }

  /**
   * Test that various combinations of ErrorMsg encoding and decoding
   * using protocol VLAST and V3 are working.
   */
  @Test(enabled=true, dataProvider="createInitializeTargetMsgData")
  public void initializeTargetMsgTestVLASTV3(int sender, int dest,
      int initiator, DN baseDN, int entryCount, int initWindow)
      throws Exception
  {
    // Create VLAST message
    InitializeTargetMsg msg = new InitializeTargetMsg(baseDN, sender, dest,
        initiator, entryCount, initWindow);

    // Serialize in V3
    byte[] v3MsgBytes = msg.getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Un-serialize V3 message
    InitializeTargetMsg newMsg = new InitializeTargetMsg(v3MsgBytes, ProtocolVersion.REPLICATION_PROTOCOL_V3);

    // Check fields common to both versions
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getBaseDN(), newMsg.getBaseDN());
    assertEquals(msg.getEntryCount(), newMsg.getEntryCount());
    assertEquals(msg.getInitiatorID(), newMsg.getInitiatorID());

    // Check default value for only post V3 fields
    assertEquals(newMsg.getInitWindow(), 0);

    // Set again only post V3 fields
    newMsg.setInitWindow(initWindow);

    // Serialize in VLAST
    InitializeTargetMsg vlastMsg = new InitializeTargetMsg(
        newMsg.getBytes(getCurrentVersion()), REPLICATION_PROTOCOL_VLAST);

    // Check we retrieve original VLAST message (VLAST fields)
    assertEquals(msg.getSenderID(), vlastMsg.getSenderID());
    assertEquals(msg.getDestination(), vlastMsg.getDestination());
    assertEquals(msg.getBaseDN(), vlastMsg.getBaseDN());
    assertEquals(msg.getEntryCount(), vlastMsg.getEntryCount());
    assertEquals(msg.getInitiatorID(), vlastMsg.getInitiatorID());
    assertEquals(msg.getInitWindow(), vlastMsg.getInitWindow());
  }

  @DataProvider(name = "createEntryMsgV3")
  public Object[][] createEntryMsgV3()
  {
    // @Checkstyle:off
    return new Object[][] {
        {"0c32003100646e3a206f753d50656f706c652c64633d6578616d706c652c64633d636f6d0a6f626a656374436c6173733a20746f700a6f626a656374436c6173733a206f7267616e697a6174696f6e616c556e69740a6f753a2050656f706c650a656e747279555549443a2032313131313131312d313131312d313131312d313131312d3131313131313131313131320a0a00",
          1, 2}};
    // @Checkstyle:on
  }
  @Test(dataProvider = "createEntryMsgV3")
  public void entryMsgPDUV3(String pduV3, int dest, int sender) throws Exception
  {
    // this msg is changed by V4, so we want to test that V>3 server can
    // build a V>3 version when it receives a V3 PDU from a V3 server.
    EntryMsg msg = new EntryMsg(hexStringToByteArray(pduV3),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getDestination(), dest);
    assertEquals(msg.getSenderID(), sender);
    assertEquals(msg.getMsgId(), -1);
    // we should test EntryBytes
  }

  @DataProvider(name = "createErrorMsgV3")
  public Object[][] createErrorMsgV3()
  {
    // @Checkstyle:off
    return new Object[][] {
        {"0e380039003135313338383933004f6e207375666669782064633d6578616d706c652c64633d636f6d2c207265706c69636174696f6e2073657276657220392070726573656e7465642067656e65726174696f6e2049443d2d31207768656e2065787065637465642067656e65726174696f6e2049443d343800",
          9, 8, "On suffix dc=example,dc=com, replication server 9 presented generation ID=-1 when expected generation ID=48"}};
    // @Checkstyle:on
  }
  @Test(dataProvider = "createErrorMsgV3")
  public void errorMsgPDUV3(
      String pduV3, int dest, int sender, String errorDetails) throws Exception
  {
    // this msg is changed by V4, so we want to test that V>3 server can
    // build a V>3 version when it receives a V3 PDU from a V3 server.
    ErrorMsg msg = new ErrorMsg(hexStringToByteArray(pduV3),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getDestination(), dest);
    assertEquals(msg.getSenderID(), sender);
    assertEquals(msg.getDetails().toString(), errorDetails);
  }

  @DataProvider
  public Object[][] createInitializeTargetMsgV3()
  {
    return new Object[][] {
        {"0b320064633d6578616d706c652c64633d636f6d00310032003400",
          "dc=example,dc=com", 2, 1, 4}};
  }
  @Test(dataProvider = "createInitializeTargetMsgV3")
  public void initializeTargetMsgPDUV3(
      String pduV3, String baseDN, int dest, int sender, int entryCount) throws Exception
  {
    // this msg is changed by V4, so we want to test that V>3 server can
    // build a V>3 version when it receives a V3 PDU from a V3 server.
    InitializeTargetMsg msg = new InitializeTargetMsg(hexStringToByteArray(pduV3),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getDestination(), dest);
    assertEquals(msg.getSenderID(), sender);
    assertEquals(msg.getBaseDN().toString(), baseDN);
    assertEquals(msg.getEntryCount(), entryCount);
  }

  @DataProvider
  public Object[][] createInitializeRequestMsgV3()
  {
    return new Object[][] {
        {"0a64633d6578616d706c652c64633d636f6d0032003100",
          "dc=example,dc=com", 1, 2}};
  }
  @Test(dataProvider = "createInitializeRequestMsgV3")
  public void initializeRequestMsgPDUV3(
      String pduV3, String baseDN, int dest, int sender) throws Exception
  {
    // this msg is changed by V4, so we want to test that V>3 server can
    // build a V>3 version when it receives a V3 PDU from a V3 server.
    InitializeRequestMsg msg = new InitializeRequestMsg(hexStringToByteArray(pduV3),
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    assertEquals(msg.getDestination(), dest);
    assertEquals(msg.getSenderID(), sender);
    assertEquals(msg.getBaseDN().toString(), baseDN);
  }
}
