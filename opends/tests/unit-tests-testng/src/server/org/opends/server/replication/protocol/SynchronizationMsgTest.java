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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.replication.protocol.OperationContext.SYNCHROCONTEXT;
import static org.opends.server.replication.protocol.ProtocolVersion.getCurrentVersion;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;

import org.opends.messages.Message;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
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
import org.opends.server.types.RawAttribute;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.controls.SubtreeDeleteControl;
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
  }

  /**
   * Build some data for the ModifyMsg test below.
   */
  @DataProvider(name = "createModifyData")
  public Object[][] createModifyData() {
    ChangeNumber cn1 = new ChangeNumber(1,  0,  1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(), 123,  45);
    ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), 67894123,  45678);

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

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> eclIncludes = new ArrayList<Attribute>();
    eclIncludes.add(eattr1);
    eclIncludes.add(eattr2);

    return new Object[][] {
        { cn1, "dc=test", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0, null},
        { cn2, "dc=cn2", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)1, eclIncludes},
        { cn2, "dc=test with a much longer dn in case this would "
               + "make a difference", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { cn2, "dc=test, cn=with a, o=more complex, ou=dn", mods1, false, AssuredMode.SAFE_READ_MODE, (byte)5, eclIncludes},
        { cn2, "cn=use\\, backslash", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { cn2, "dc=test with several mod", mods2, false, AssuredMode.SAFE_DATA_MODE, (byte)16, eclIncludes},
        { cn2, "dc=test with several values", mods3, false, AssuredMode.SAFE_READ_MODE, (byte)3, null},
        { cn2, "dc=test with long mod", mods4, true, AssuredMode.SAFE_READ_MODE, (byte)120, eclIncludes},
        { cn2, "dc=testDsaOperation", mods5, true, AssuredMode.SAFE_DATA_MODE, (byte)99, null},
        { cn3, "dc=serverIdLargerThan32767", mods1, true, AssuredMode.SAFE_READ_MODE, (byte)1, null},
        };
  }

  /**
   * Create a ModifyMsg from the data provided above.
   * The call getBytes() to test the encoding of the Msg and
   * create another ModifyMsg from the encoded byte array.
   * Finally test that both Msg matches.
   */
  @Test(enabled=true,dataProvider = "createModifyData")
  public void modifyMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel,
                               List<Attribute> entryAttrList)
         throws Exception
  {
    DN dn = DN.decode(rawdn);
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods, "fakeuniqueid");

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry inlcuded attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), ProtocolVersion.getCurrentVersion());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());

    // Get ECL entry attributes
    ArrayList<RawAttribute> genAttrList = generatedMsg.getEclIncludes();
    if (entryAttrList==null)
      assertTrue(genAttrList.size()==0);
    else
    {
      assertTrue(genAttrList.size()==entryAttrList.size());
      int i=0;
      for (Attribute attr : entryAttrList)
      {
        assertTrue(attr.getName().equalsIgnoreCase(genAttrList.get(i).toAttribute().getName()));
        assertTrue(attr.toString().equalsIgnoreCase(genAttrList.get(i).toAttribute().toString()),
            "Comparing: " + attr.toString() + " and " + genAttrList.get(i).toAttribute().toString());
        i++;
      }
    }

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
  @Test(enabled=true,dataProvider = "createModifyData")
  public void updateMsgTest(ChangeNumber changeNumber,
                               String rawdn, List<Modification> mods,
                               boolean isAssured, AssuredMode assuredMode,
                               byte safeDataLevel ,
                               List<Attribute> entryAttrList)
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
    assertTrue(msg.getSafeDataLevel() == 1);
    msg.setSafeDataLevel(safeDataLevel);
    assertTrue(msg.getSafeDataLevel() == safeDataLevel);

    // Check equals
    ModifyMsg generatedMsg = (ModifyMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), ProtocolVersion.REPLICATION_PROTOCOL_V1);
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

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = new ArrayList<Attribute>();
    entryAttrList.add(eattr1);
    entryAttrList.add(eattr2);

    return new Object[][] {
        {"dc=com", entryAttrList, false},
        {"dc=delete,dc=an,dc=entry,dc=with,dc=a,dc=long dn", null, true},
        };
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
    DeleteOperationBasis opBasis =
      new DeleteOperationBasis(connection, 1, 1,null, DN.decode(rawDN));
    if (subtree)
    {
      opBasis.addRequestControl(new SubtreeDeleteControl(false));
    }
    LocalBackendDeleteOperation op = new LocalBackendDeleteOperation(opBasis);
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),123,  45);
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn, "uniqueid"));
    DeleteMsg msg = new DeleteMsg(op);
    assertTrue((msg.isSubtreeDelete()==subtree));
    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }
    msg.setInitiatorsName("johnny h");
    DeleteMsg generatedMsg = (DeleteMsg) ReplicationMsg.generateMsg(
        msg.getBytes(), ProtocolVersion.getCurrentVersion());

    assertEquals(msg.toString(), generatedMsg.toString());
    assertEquals(msg.getInitiatorsName(), generatedMsg.getInitiatorsName());
    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());
    assertEquals(generatedMsg.isSubtreeDelete(), subtree);

    // Get ECL entry attributes
    ArrayList<RawAttribute> genAttrList = generatedMsg.getEclIncludes();
    if (entryAttrList==null)
      assertTrue(genAttrList.size()==0);
    else
    {
      assertTrue(genAttrList.size()==entryAttrList.size());
      int i=0;
      for (Attribute attr : entryAttrList)
      {
        assertTrue(attr.getName().equalsIgnoreCase(genAttrList.get(i).toAttribute().getName()));
        assertTrue(attr.toString().equalsIgnoreCase(genAttrList.get(i).toAttribute().toString()),
            "Comparing: " + attr.toString() + " and " + genAttrList.get(i).toAttribute().toString());
        i++;
      }
    }

    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(generatedOperation.getClass(), DeleteOperationBasis.class);
    assertTrue(
        (subtree?(generatedOperation.getRequestControl(SubtreeDeleteControl.DECODER)!=null):
          (generatedOperation.getRequestControl(SubtreeDeleteControl.DECODER)==null)));

    DeleteOperationBasis mod2 = (DeleteOperationBasis) generatedOperation;

    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());

    // Create an update message from this op
    DeleteMsg updateMsg = (DeleteMsg) LDAPUpdateMsg.generateMsg(op);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
    assertEquals(msg.isSubtreeDelete(), updateMsg.isSubtreeDelete());
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

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = new ArrayList<Attribute>();
    entryAttrList.add(eattr1);
    entryAttrList.add(eattr2);


    return new Object[][] {
        {"dc=test,dc=com", "dc=new", false, "dc=change", mods1, false, AssuredMode.SAFE_DATA_MODE, (byte)0, entryAttrList},
        {"dc=test,dc=com", "dc=new", true, "dc=change", mods2, true, AssuredMode.SAFE_READ_MODE, (byte)1, null},
        // testNG does not like null argument so use "" for the newSuperior
        // instead of null
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
    ModifyDNOperationBasis op =
      new ModifyDNOperationBasis(connection, 1, 1, null,
                  DN.decode(rawDN), RDN.decode(newRdn), deleteOldRdn,
                  (newSuperior.length() != 0 ? DN.decode(newSuperior) : null));

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123,  45);
    op.setAttachment(SYNCHROCONTEXT,
        new ModifyDnContext(cn, "uniqueid", "newparentId"));
    LocalBackendModifyDNOperation localOp =
      new LocalBackendModifyDNOperation(op);
    for (Modification mod : mods)
      localOp.addModification(mod);
    ModifyDNMsg msg = new ModifyDNMsg(localOp);

    msg.setAssured(isAssured);
    msg.setAssuredMode(assuredMode);
    msg.setSafeDataLevel(safeDataLevel);

    // Set ECL entry attributes
    if (entryAttrList != null)
    {
      msg.setEclIncludes(entryAttrList);
    }

    ModifyDNMsg generatedMsg = (ModifyDNMsg) ReplicationMsg
        .generateMsg(msg.getBytes(), ProtocolVersion.getCurrentVersion());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    // Get ECL entry attributes
    ArrayList<RawAttribute> genAttrList = generatedMsg.getEclIncludes();
    if (entryAttrList==null)
      assertTrue(genAttrList.size()==0);
    else
    {
      assertTrue(genAttrList.size()==entryAttrList.size());
      int i=0;
      for (Attribute attr : entryAttrList)
      {
        assertTrue(attr.getName().equalsIgnoreCase(genAttrList.get(i).toAttribute().getName()));
        assertTrue(attr.toString().equalsIgnoreCase(genAttrList.get(i).toAttribute().toString()),
            "Comparing: " + attr.toString() + " and " + genAttrList.get(i).toAttribute().toString());
        i++;
      }
    }

    Operation oriOp = msg.createOperation(connection);
    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(oriOp.getClass(), ModifyDNOperationBasis.class);
    assertEquals(generatedOperation.getClass(), ModifyDNOperationBasis.class);

    ModifyDNOperationBasis moddn1 = (ModifyDNOperationBasis) oriOp;
    ModifyDNOperationBasis moddn2 = (ModifyDNOperationBasis) generatedOperation;

    assertEquals(msg.getChangeNumber(), generatedMsg.getChangeNumber());
    assertEquals(moddn1.getRawEntryDN(), moddn2.getRawEntryDN());
    assertEquals(moddn1.getRawNewRDN(), moddn2.getRawNewRDN());
    assertEquals(moddn1.deleteOldRDN(), moddn2.deleteOldRDN());
    assertEquals(moddn1.getRawNewSuperior(), moddn2.getRawNewSuperior());
    assertEquals(moddn1.getModifications(), moddn2.getModifications());

    // Create an update message from this op
    ModifyDNMsg updateMsg = (ModifyDNMsg) LDAPUpdateMsg.generateMsg(localOp);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  @DataProvider(name = "createAddData")
  public Object[][] createAddData()
  {

    // Entry attributes
    Attribute eattr1 = Attributes.create("description", "eav description");
    Attribute eattr2 = Attributes.create("namingcontexts", "eav naming contexts");
    List<Attribute> entryAttrList = new ArrayList<Attribute>();
    entryAttrList.add(eattr1);
    entryAttrList.add(eattr2);
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

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123,  45);

    AddMsg msg = new AddMsg(cn, rawDN, "thisIsaUniqueID", "parentUniqueId",
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

    AddMsg generatedMsg = (AddMsg) ReplicationMsg.generateMsg(msg
        .getBytes(), ProtocolVersion.getCurrentVersion());
    assertEquals(generatedMsg.getBytes(), msg.getBytes());
    assertEquals(generatedMsg.toString(), msg.toString());

    // Test that generated attributes match original attributes.
    assertEquals(generatedMsg.getParentEntryUUID(), msg.getParentEntryUUID());
    assertEquals(generatedMsg.isAssured(), isAssured);
    assertEquals(generatedMsg.getAssuredMode(), assuredMode);
    assertEquals(generatedMsg.getSafeDataLevel(), safeDataLevel);

    // Get ECL entry attributes
    ArrayList<RawAttribute> genAttrList = generatedMsg.getEclIncludes();
    if (entryAttrList==null)
      assertTrue(genAttrList.size()==0);
    else
    {
      assertTrue(genAttrList.size()==entryAttrList.size());
      int i=0;
      for (Attribute eattr : entryAttrList)
      {
        assertTrue(eattr.getName().equalsIgnoreCase(genAttrList.get(i).toAttribute().getName()));
        assertTrue(eattr.toString().equalsIgnoreCase(genAttrList.get(i).toAttribute().toString()),
            "Comparing: " + eattr.toString() + " and " + genAttrList.get(i).toAttribute().toString());
        i++;
      }
    }


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

    if (entryAttrList != null)
    {
      generatedMsg.setEclIncludes(entryAttrList);
    }

    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    assertEquals(msg.toString(), generatedMsg.toString());
    // TODO : should test that generated attributes match original attributes.


    // Create an update message from this op
    AddMsg updateMsg = (AddMsg) LDAPUpdateMsg.generateMsg(addOp);
    assertEquals(msg.getChangeNumber(), updateMsg.getChangeNumber());
  }

  /**
   * Build some data for the AckMsg test below.
   */
  @DataProvider(name = "createAckData")
  public Object[][] createAckData() {
    ChangeNumber cn1 = new ChangeNumber(1,  0,  1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(), 123, 45);
    ChangeNumber cn3 = new ChangeNumber(TimeThread.getTime(), 1234567, 45678);

    ArrayList<Integer> fservers1 = new ArrayList<Integer>();
    fservers1.add(12345);
    fservers1.add(-12345);
    fservers1.add(31657);
    fservers1.add(-28456);
    fservers1.add(0);
    ArrayList<Integer> fservers2 = new ArrayList<Integer>();
    ArrayList<Integer> fservers3 = new ArrayList<Integer>();
    fservers3.add(0);
    ArrayList<Integer> fservers4 = new ArrayList<Integer>();
    fservers4.add(100);
    fservers4.add(2000);
    fservers4.add(30000);
    fservers4.add(-100);
    fservers4.add(-2000);
    fservers4.add(-30000);

    return new Object[][] {
        {cn1, true, false, false, fservers1},
        {cn2, false, true, false, fservers2},
        {cn1, false, false, true, fservers3},
        {cn2, false, false, false, fservers4},
        {cn1, true, true, false, fservers1},
        {cn2, false, true, true, fservers2},
        {cn1, true, false, true, fservers3},
        {cn2, true, true, true, fservers4},
        {cn3, true, true, true, fservers4}
        };
  }

  @Test(enabled=true,dataProvider = "createAckData")
  public void ackMsgTest(ChangeNumber cn, boolean hasTimeout, boolean hasWrongStatus,
    boolean hasReplayError, List<Integer> failedServers)
         throws Exception
  {
    AckMsg msg1, msg2 ;

    // Constructor test (with ChangeNumber)
    // Check that retrieved CN is OK
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

    // Constructor test (with byte[])
    msg2 = new  AckMsg(msg1.getBytes(getCurrentVersion()));
    assertEquals(msg2.getChangeNumber().compareTo(cn), 0);
    assertTrue(msg1.hasTimeout() == msg2.hasTimeout());
    assertTrue(msg1.hasWrongStatus() == msg2.hasWrongStatus());
    assertTrue(msg1.hasReplayError() == msg2.hasReplayError());
    assertEquals(msg1.getFailedServers(), msg2.getFailedServers());

    // Check invalid bytes for constructor
    byte[] b = msg1.getBytes(getCurrentVersion());
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
    msg2 = (AckMsg) ReplicationMsg.generateMsg(
        msg1.getBytes(getCurrentVersion()), getCurrentVersion());
  }

  @Test(enabled=true)
  public void eclUpdateMsg()
         throws Exception
  {
    // create a msg to put in the eclupdatemsg
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();
    DeleteOperationBasis opBasis =
      new DeleteOperationBasis(connection, 1, 1,null, DN.decode("cn=t1"));
    LocalBackendDeleteOperation op = new LocalBackendDeleteOperation(opBasis);
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123,  45);
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn, "uniqueid"));
    DeleteMsg delmsg = new DeleteMsg(op);
    int draftcn = 21;

    String serviceId = "serviceid";

    // create a cookie
    MultiDomainServerState cookie =
      new MultiDomainServerState(
          "o=test:000001210b6f21e904b100000001 000001210b6f21e904b200000001;" +
          "o=test2:000001210b6f21e904b100000002 000001210b6f21e904b200000002;");

    // Constructor test
    ECLUpdateMsg msg1 = new ECLUpdateMsg(delmsg, cookie, serviceId, draftcn);
    assertTrue(msg1.getCookie().equalsTo(cookie));
    assertTrue(msg1.getServiceId().equalsIgnoreCase(serviceId));
    assertTrue((msg1.getDraftChangeNumber()==draftcn));
    DeleteMsg delmsg2 = (DeleteMsg)msg1.getUpdateMsg();
    assertTrue(delmsg.compareTo(delmsg2)==0);

    // Constructor test (with byte[])
    ECLUpdateMsg msg2 = new ECLUpdateMsg(msg1.getBytes(getCurrentVersion()));
    assertTrue(msg2.getCookie().equalsTo(msg2.getCookie()));
    assertTrue(msg2.getCookie().equalsTo(cookie));
    assertTrue(msg2.getServiceId().equalsIgnoreCase(msg1.getServiceId()));
    assertTrue(msg2.getServiceId().equalsIgnoreCase(serviceId));
    assertTrue(msg2.getDraftChangeNumber()==(msg1.getDraftChangeNumber()));
    assertTrue(msg2.getDraftChangeNumber()==draftcn);

    DeleteMsg delmsg1 = (DeleteMsg)msg1.getUpdateMsg();
    delmsg2 = (DeleteMsg)msg2.getUpdateMsg();
    assertTrue(delmsg2.compareTo(delmsg)==0);
    assertTrue(delmsg2.compareTo(delmsg1)==0);
  }

  @DataProvider(name="createServerStartData")
  public Object [][] createServerStartData() throws Exception
  {
    String baseDN = TEST_ROOT_DN_STRING;
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0,0));
    Object[] set1 = new Object[] {1, baseDN, 0, state, 0L, false, (byte)0};

    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5,263));
    Object[] set2 = new Object[] {16, baseDN, 100, state, 1248L, true, (byte)31};

    state = new ServerState();
    state.update(new ChangeNumber((long)75, 98573895,45263));
    Object[] set3 = new Object[] {16, baseDN, 100, state, 1248L, true, (byte)31};

    return new Object [][] { set1, set2, set3 };
  }

  /**
   * Test that ServerStartMsg encoding and decoding works
   * by checking that : msg == new ServerStartMsg(msg.getBytes()).
   */
  @Test(enabled=true,dataProvider="createServerStartData")
  public void serverStartMsgTest(int serverId, String baseDN, int window,
         ServerState state, long genId, boolean sslEncryption, byte groupId) throws Exception
  {
    ServerStartMsg msg = new ServerStartMsg(
        serverId, "localhost:1234", baseDN, window, window, state,
        genId, sslEncryption, groupId);
    ServerStartMsg newMsg = new ServerStartMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getHeartbeatInterval(), newMsg.getHeartbeatInterval());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertEquals(msg.getServerState().getMaxChangeNumber(1),
        newMsg.getServerState().getMaxChangeNumber(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
  }

  @DataProvider(name="createReplServerStartData")
  public Object [][] createReplServerStartData() throws Exception
  {
    String baseDN = TEST_ROOT_DN_STRING;
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0,0));
    Object[] set1 = new Object[] {1, baseDN, 0, "localhost:8989", state, 0L, (byte)0, 0};

    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5,263));
    Object[] set2 = new Object[] {16, baseDN, 100, "anotherHost:1025", state, 1245L, (byte)25, 3456};

    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5, 45263));
    Object[] set3 = new Object[] {16, baseDN, 100, "anotherHost:1025", state, 1245L, (byte)25, 3456};

    return new Object [][] { set1, set2, set3 };
  }

  /**
   * Test that ReplServerStartMsg encoding and decoding works
   * by checking that : msg == new ReplServerStartMsg(msg.getBytes()).
   */
  @Test(enabled=true,dataProvider="createReplServerStartData")
  public void replServerStartMsgTest(int serverId, String baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh) throws Exception
  {
    ReplServerStartMsg msg = new ReplServerStartMsg(serverId,
        url, baseDN, window, state, genId,
        true, groupId, degTh);
    ReplServerStartMsg newMsg = new ReplServerStartMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber(1),
        newMsg.getServerState().getMaxChangeNumber(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
    assertTrue(msg.getDegradedStatusThreshold() ==
      newMsg.getDegradedStatusThreshold());
  }

  @DataProvider(name="createReplServerStartDSData")
  public Object [][] createReplServerStartDSData() throws Exception
  {
    String baseDN = TEST_ROOT_DN_STRING;
    ServerState state = new ServerState();
    state.update(new ChangeNumber((long)0, 0, 0));
    Object[] set1 = new Object[] {1, baseDN, 0, "localhost:8989", state, 0L, (byte)0, 0, 0, 0};

    state = new ServerState();
    state.update(new ChangeNumber((long)75, 5, 263));
    Object[] set2 = new Object[] {16, baseDN, 100, "anotherHost:1025", state, 1245L, (byte)25, 3456, 3, 31512};

    state = new ServerState();
    state.update(new ChangeNumber((long)123, 5, 98));
    Object[] set3 = new Object[] {36, baseDN, 100, "anotherHostAgain:8017", state, 6841L, (byte)32, 2496, 630, 9524};

    return new Object [][] { set1, set2, set3 };
  }

  /**
   * Test that ReplServerStartDSMsg encoding and decoding works
   * by checking that : msg == new ReplServerStartMsg(msg.getBytes()).
   */
  @Test(dataProvider="createReplServerStartDSData")
  public void replServerStartDSMsgTest(int serverId, String baseDN, int window,
         String url, ServerState state, long genId, byte groupId, int degTh,
         int weight, int connectedDSNumber) throws Exception
  {
    ReplServerStartDSMsg msg = new ReplServerStartDSMsg(serverId,
        url, baseDN, window, state, genId,
        true, groupId, degTh, weight, connectedDSNumber);
    ReplServerStartDSMsg newMsg = new ReplServerStartDSMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerId(), newMsg.getServerId());
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getBaseDn(), newMsg.getBaseDn());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getServerState().getMaxChangeNumber(1),
        newMsg.getServerState().getMaxChangeNumber(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
    assertTrue(msg.getDegradedStatusThreshold() ==
      newMsg.getDegradedStatusThreshold());
    assertEquals(msg.getWeight(), newMsg.getWeight());
    assertEquals(msg.getConnectedDSNumber(), newMsg.getConnectedDSNumber());
  }

  /**
   * Test that StopMsg encoding and decoding works
   * by checking that : msg == new StopMsg(msg.getBytes()).
   */
  @Test
  public void stopMsgTest() throws Exception
  {
    StopMsg msg = new StopMsg();
    StopMsg newMsg = new StopMsg(msg.getBytes(getCurrentVersion()));
  }

  /**
   * Test that WindowMsg encoding and decoding works
   * by checking that : msg == new WindowMsg(msg.getBytes()).
   */
  @Test()
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
  @Test()
  public void windowProbeMsgTest() throws Exception
  {
    WindowProbeMsg msg = new WindowProbeMsg();
    new WindowProbeMsg(msg.getBytes(getCurrentVersion()));
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


    Set<String> a1 = new HashSet<String>();
    Set<String> a2 = new HashSet<String>();
    a2.add("dc");
    Set<String> a3 = new HashSet<String>();
    a3.add("dc");
    a3.add("uid");
    Set<String> a4 = new HashSet<String>();

    DSInfo dsInfo1 = new DSInfo(13, "dsHost1:111", 26, (long)154631, ServerStatus.FULL_UPDATE_STATUS,
      false, AssuredMode.SAFE_DATA_MODE, (byte)12, (byte)132, urls1, a1, a1, (short)1);

    DSInfo dsInfo2 = new DSInfo(-436, "dsHost2:222", 493, (long)-227896, ServerStatus.DEGRADED_STATUS,
      true, AssuredMode.SAFE_READ_MODE, (byte)-7, (byte)-265, urls2, a2, a2, (short)2);

    DSInfo dsInfo3 = new DSInfo(2436, "dsHost3:333", 591, (long)0, ServerStatus.NORMAL_STATUS,
      false, AssuredMode.SAFE_READ_MODE, (byte)17, (byte)0, urls3, a3, a3, (short)3);
    DSInfo dsInfo4 = new DSInfo(415, "dsHost4:444", 146, (long)0, ServerStatus.BAD_GEN_ID_STATUS,
      true, AssuredMode.SAFE_DATA_MODE, (byte)2, (byte)15, urls4, a4, a4, (short)4);

    DSInfo dsInfo5 = new DSInfo(452436, "dsHost5:555", 45591, (long)0, ServerStatus.NORMAL_STATUS,
        false, AssuredMode.SAFE_READ_MODE, (byte)17, (byte)0, urls3, a1, a1, (short)5);

    List<DSInfo> dsList1 = new ArrayList<DSInfo>();
    dsList1.add(dsInfo1);

    List<DSInfo> dsList2 = new ArrayList<DSInfo>();

    List<DSInfo> dsList3 = new ArrayList<DSInfo>();
    dsList3.add(dsInfo2);

    List<DSInfo> dsList4 = new ArrayList<DSInfo>();
    dsList4.add(dsInfo5);
    dsList4.add(dsInfo4);
    dsList4.add(dsInfo3);
    dsList4.add(dsInfo2);
    dsList4.add(dsInfo1);

    RSInfo rsInfo1 = new RSInfo(4527, "rsHost1:123", (long)45316, (byte)103, 1);

    RSInfo rsInfo2 = new RSInfo(4527, "rsHost2:456", (long)0, (byte)0, 1);

    RSInfo rsInfo3 = new RSInfo(0, "rsHost3:789", (long)-21113, (byte)98, 1);

    RSInfo rsInfo4 = new RSInfo(45678, "rsHost4:1011", (long)-21113, (byte)98, 1);

    List<RSInfo> rsList1 = new ArrayList<RSInfo>();
    rsList1.add(rsInfo1);

    List<RSInfo> rsList2 = new ArrayList<RSInfo>();
    rsList2.add(rsInfo1);
    rsList2.add(rsInfo2);
    rsList2.add(rsInfo3);
    rsList2.add(rsInfo4);

    return new Object [][] {
      {dsList1, rsList1, a1},
      {dsList2, rsList2, a2},
      {dsList3, rsList1, a3},
      {dsList3, null, null},
      {null, rsList1, a1},
      {null, null, a2},
      {dsList4, rsList2, a3}
    };
  }

  /**
   * Test TopologyMsg encoding and decoding.
   */
  @Test(enabled=true,dataProvider = "createTopologyData")
  public void topologyMsgTest(List<DSInfo> dsList, List<RSInfo> rsList,
      Set<String> attrs)
    throws Exception
  {
    TopologyMsg msg = new TopologyMsg(dsList, rsList);
    TopologyMsg newMsg = new TopologyMsg(msg.getBytes(getCurrentVersion()),
        ProtocolVersion.getCurrentVersion());
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

    Set<String> a1 = new HashSet<String>();
    Set<String> a2 = new HashSet<String>();
    a2.add("dc");
    Set<String> a3 = new HashSet<String>();
    a3.add("dc");
    a3.add("uid");

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
    assertTrue(msg.isAssured() == newMsg.isAssured());
    assertEquals(msg.getAssuredMode(), newMsg.getAssuredMode());
    assertTrue(msg.getSafeDataLevel() == newMsg.getSafeDataLevel());
    assertEquals(msg.getReferralsURLs(), newMsg.getReferralsURLs());
    assertTrue(attrs.equals(newMsg.getEclIncludes()));
    assertTrue(attrs.equals(newMsg.getEclIncludesForDeletes()));
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
  @Test()
  public void monitorMsgTest() throws Exception
  {
    int sender = 2;
    int dest = 3;

    // RS State
    ServerState rsState = new ServerState();
    ChangeNumber rscn1 = new ChangeNumber(1,  1,  1);
    ChangeNumber rscn2 = new ChangeNumber(1,  1,  45678);
    rsState.update(rscn1);
    rsState.update(rscn2);

    // LS1 state
    ServerState s1 = new ServerState();
    int sid1 = 111;
    ChangeNumber cn1 = new ChangeNumber(1,  1, sid1);
    s1.update(cn1);

    // LS2 state
    ServerState s2 = new ServerState();
    int sid2 = 222;
    Long now = ((Integer)10).longValue();
    ChangeNumber cn2 = new ChangeNumber(now, 123, sid2);
    s2.update(cn2);

    // LS3 state
    ServerState s3 = new ServerState();
    int sid3 = 56789;
    ChangeNumber cn3 = new ChangeNumber(now, 123, sid3);
    s3.update(cn3);

    MonitorMsg msg =
      new MonitorMsg(sender, dest);
    msg.setReplServerDbState(rsState);
    msg.setServerState(sid1, s1, now+1, true);
    msg.setServerState(sid2, s2, now+2, true);
    msg.setServerState(sid3, s3, now+3, false);

    byte[] b = msg.getBytes(getCurrentVersion());
    MonitorMsg newMsg = new MonitorMsg(b, ProtocolVersion.getCurrentVersion());

    assertEquals(rsState, msg.getReplServerDbState());
    assertEquals(newMsg.getReplServerDbState().toString(),
        msg.getReplServerDbState().toString());

    Iterator<Integer> it = newMsg.ldapIterator();
    while (it.hasNext())
    {
      int sid = it.next();
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

    Iterator<Integer> it2 = newMsg.rsIterator();
    while (it2.hasNext())
    {
      int sid = it2.next();
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

    assertEquals(newMsg.getSenderID(), msg.getSenderID());
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
        "ds-task-class-name: org.opends.server.replication.api.InitializeTask\n" +
        "ds-task-initialize-domain-dn: " + TEST_ROOT_DN_STRING  + "\n" +
        "ds-task-initialize-source: 1\n");
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
   * Test that InitializeRequestMsg encoding and decoding works
   */
  @Test()
  public void initializeRequestMsgTest() throws Exception
  {
    int sender = 1;
    int target = 56789;
    InitializeRequestMsg msg = new InitializeRequestMsg(
        TEST_ROOT_DN_STRING, sender, target, 100);
    InitializeRequestMsg newMsg = new InitializeRequestMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertTrue(msg.getBaseDn().equals(newMsg.getBaseDn()));
  }

  /**
   * Test that InitializeTargetMsg encoding and decoding works
   */
  @Test()
  public void initializeTargetMsgTest() throws Exception
  {
    int senderID = 45678;
    int targetID = 2;
    int requestorID = 3;
    long entryCount = 4;
    int initWindow = 100;

    InitializeTargetMsg msg = new InitializeTargetMsg(
        TEST_ROOT_DN_STRING, senderID, targetID, requestorID, entryCount, initWindow);
    InitializeTargetMsg newMsg = new InitializeTargetMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getInitiatorID(), newMsg.getInitiatorID());
    assertEquals(msg.getEntryCount(), newMsg.getEntryCount());
    assertTrue(msg.getBaseDN().equals(newMsg.getBaseDN())) ;

    assertEquals(senderID, newMsg.getSenderID());
    assertEquals(targetID, newMsg.getDestination());
    assertEquals(requestorID, newMsg.getInitiatorID());
    assertEquals(entryCount, newMsg.getEntryCount());
    assertTrue(TEST_ROOT_DN_STRING.equals(newMsg.getBaseDN())) ;

  }

  /**
   * Test that DoneMsg encoding and decoding works
   */
  @Test()
  public void doneMsgTest() throws Exception
  {
    DoneMsg msg = new DoneMsg(1, 2);
    DoneMsg newMsg = new DoneMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
  }

  /**
   * Test that ErrorMsg encoding and decoding works
   */
  @Test()
  public void errorMsgTest() throws Exception
  {
    ErrorMsg msg = new ErrorMsg(1, 2, Message.raw("details"));
    ErrorMsg newMsg = new ErrorMsg(msg.getBytes(getCurrentVersion()),getCurrentVersion());
    assertEquals(msg.getSenderID(), newMsg.getSenderID());
    assertEquals(msg.getDestination(), newMsg.getDestination());
    assertEquals(msg.getMsgID(), newMsg.getMsgID());
    assertEquals(msg.getDetails(), newMsg.getDetails());
  }

  /**
   * Test Generic UpdateMsg
   */
  @Test
  public void UpdateMsgTest() throws Exception
  {
    final String test = "string used for test";
    UpdateMsg msg =
      new UpdateMsg(
          new ChangeNumber((long) 1, 2 , 39123),
          test.getBytes());
    UpdateMsg newMsg = new UpdateMsg(msg.getBytes());
    assertEquals(test.getBytes(), newMsg.getPayload());
  }

  /**
   * Test that ServerStartMsg encoding and decoding works
   * by checking that : msg == new ServerStartMsg(msg.getBytes()).
   */
  @Test(enabled=true,dataProvider="createServerStartData")
  public void startECLMsgTest(int serverId, String baseDN, int window,
         ServerState state, long genId, boolean sslEncryption, byte groupId) throws Exception
  {
    ServerStartECLMsg msg = new ServerStartECLMsg(
        "localhost:1234", window, window, window, window, window, window, state,
        genId, sslEncryption, groupId);
    ServerStartECLMsg newMsg = new ServerStartECLMsg(msg.getBytes(getCurrentVersion()));
    assertEquals(msg.getServerURL(), newMsg.getServerURL());
    assertEquals(msg.getMaxReceiveDelay(), newMsg.getMaxReceiveDelay());
    assertEquals(msg.getMaxReceiveQueue(), newMsg.getMaxReceiveQueue());
    assertEquals(msg.getMaxSendDelay(), newMsg.getMaxSendDelay());
    assertEquals(msg.getMaxSendQueue(), newMsg.getMaxSendQueue());
    assertEquals(msg.getWindowSize(), newMsg.getWindowSize());
    assertEquals(msg.getHeartbeatInterval(), newMsg.getHeartbeatInterval());
    assertEquals(msg.getSSLEncryption(), newMsg.getSSLEncryption());
    assertEquals(msg.getServerState().getMaxChangeNumber(1),
        newMsg.getServerState().getMaxChangeNumber(1));
    assertEquals(newMsg.getVersion(), getCurrentVersion());
    assertEquals(msg.getGenerationId(), newMsg.getGenerationId());
    assertTrue(msg.getGroupId() == newMsg.getGroupId());
  }
  /**
   * Test StartSessionMsg encoding and decoding.
   */
  @Test()
  public void startECLSessionMsgTest()
    throws Exception
  {
    // data
    ChangeNumber changeNumber = new ChangeNumber(TimeThread.getTime(), 123,  45);
    String generalizedState = "fakegenstate";
    ServerState state = new ServerState();
    assertTrue(state.update(new ChangeNumber((long)75, 5,263)));
    short mode = 3;
    int firstDraftChangeNumber = 13;
    int lastDraftChangeNumber  = 14;
    String myopid = "fakeopid";
    // create original
    StartECLSessionMsg msg = new StartECLSessionMsg();
    msg.setChangeNumber(changeNumber);
    msg.setCrossDomainServerState(generalizedState);
    msg.setPersistent(StartECLSessionMsg.PERSISTENT);
    msg.setFirstDraftChangeNumber(firstDraftChangeNumber);
    msg.setLastDraftChangeNumber(lastDraftChangeNumber);
    msg.setECLRequestType(mode);
    msg.setOperationId(myopid);
    ArrayList<String> dns = new ArrayList<String>();
    String dn1 = "cn=admin data";
    String dn2 = "cn=config";
    dns.add(dn1);
    dns.add(dn2);
    msg.setExcludedDNs(dns);
    // create copy
    StartECLSessionMsg newMsg = new StartECLSessionMsg(msg.getBytes(getCurrentVersion()));
    // test equality between the two copies
    assertEquals(msg.getChangeNumber(), newMsg.getChangeNumber());
    assertTrue(msg.isPersistent() == newMsg.isPersistent());
    assertTrue(msg.getFirstDraftChangeNumber() == newMsg.getFirstDraftChangeNumber());
    assertEquals(msg.getECLRequestType(), newMsg.getECLRequestType());
    assertEquals(msg.getLastDraftChangeNumber(), newMsg.getLastDraftChangeNumber());
    assertTrue(
        msg.getCrossDomainServerState().equalsIgnoreCase(newMsg.getCrossDomainServerState()));
    assertTrue(
        msg.getOperationId().equalsIgnoreCase(newMsg.getOperationId()));
    ArrayList<String> dns2 = newMsg.getExcludedServiceIDs();
    assertTrue(dns2.size()==2);
    boolean dn1found=false,dn2found=false;
    for (String dn : dns2)
    {
      if (!dn1found) dn1found=(dn.compareTo(dn1)==0);
      if (!dn2found) dn2found=(dn.compareTo(dn2)==0);
    }
    assertTrue(dn1found);
    assertTrue(dn2found);
  }

  int perfRep = 100000;


  @Test(enabled=false,dataProvider = "createAddData")
  public void addMsgPerfs(String rawDN, boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
  throws Exception
  {
    long createop = 0;
    long createmsgfromop = 0;
    long encodemsg = 0;
    long getbytes = 0;
    long alld = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

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

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123, 45);
    DN dn = DN.decode(rawDN);

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      AddOperation addOpB = new AddOperationBasis(connection,
          (long) 1, 1, null, dn, objectClassList, userAttList, opList);
      LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpB);
      OperationContext opCtx = new AddContext(cn, "thisIsaUniqueID",
          "parentUniqueId");
      addOp.setAttachment(SYNCHROCONTEXT, opCtx);
      t2 = System.nanoTime();
      createop += (t2 - t1);

      // create msg from op
      AddMsg generatedMsg = new AddMsg(addOp);
      t3 = System.nanoTime();
      createmsgfromop += (t3 - t2);

      // set entry attr
      generatedMsg.setEclIncludes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += (t31 - t3);

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += (t4 - t31);

      // getBytes
      byte[] bytes = generatedMsg.getBytes(ProtocolVersion.getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += (t5 - t4);

      // getBytes
      new AddMsg(bytes);
      t6 = System.nanoTime();
      buildnew += (t6 - t5);

      alld += (t6 - t1);
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
  public void modMsgPerfs(ChangeNumber changeNumber,
      String rawdn, List<Modification> mods,
      boolean isAssured, AssuredMode assuredMode,
      byte safeDataLevel, List<Attribute> entryAttrList)
  throws Exception
  {
    long createop = 0;
    long createmsgfromop = 0;
    long encodemsg = 0;
    long getbytes = 0;
    long alld = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123, 45);
    DN dn = DN.decode(rawdn);

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      ModifyOperation modifyOpB = new ModifyOperationBasis(
          connection, (long)1, 1, null, dn, mods);
      LocalBackendModifyOperation modifyOp =
        new LocalBackendModifyOperation(modifyOpB);
      OperationContext opCtx = new ModifyContext(cn, "thisIsaUniqueID");
      modifyOp.setAttachment(SYNCHROCONTEXT, opCtx);
      t2 = System.nanoTime();
      createop += (t2 - t1);

      // create msg from op
      ModifyMsg generatedMsg = new ModifyMsg(modifyOp);
      t3 = System.nanoTime();
      createmsgfromop += (t3 - t2);

      // set entry attr
      // generatedMsg.setEntryAttributes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += (t31 - t3);

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += (t4 - t31);

      // getBytes
      byte[] bytes = generatedMsg.getBytes(ProtocolVersion.getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += (t5 - t4);

      // getBytes
      new ModifyMsg(bytes);
      t6 = System.nanoTime();
      buildnew += (t6 - t5);

      alld += (t6 - t1);
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
    long alld = 0;
    long setentryattr = 0;
    long buildnew = 0;
    long t1,t2,t3,t31,t4,t5,t6 = 0;

    for (int i=1;i<perfRep;i++)
    {
      t1 = System.nanoTime();

      // create op
      DeleteOperationBasis opBasis =
        new DeleteOperationBasis(connection, 1, 1,null, DN.decode(rawDN));
      LocalBackendDeleteOperation op = new LocalBackendDeleteOperation(opBasis);
      ChangeNumber cn = new ChangeNumber(TimeThread.getTime(), 123, 45);
      op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn, "uniqueid"));
      t2 = System.nanoTime();
      createop += (t2 - t1);

      // create msg from op
      DeleteMsg generatedMsg = new DeleteMsg(op);
      t3 = System.nanoTime();
      createmsgfromop += (t3 - t2);

      // set entry attr
      //generatedMsg.setEntryAttributes(entryAttrList);
      t31 = System.nanoTime();
      setentryattr += (t31 - t3);

      // encode msg
      generatedMsg.encode();
      t4 = System.nanoTime();
      encodemsg += (t4 - t31);

      // getBytes
      byte[] bytes = generatedMsg.getBytes(ProtocolVersion.getCurrentVersion());
      t5 = System.nanoTime();
      getbytes += (t5 - t4);

      // getBytes
      new DeleteMsg(bytes);
      t6 = System.nanoTime();
      buildnew += (t6 - t5);

      alld += (t6 - t1);
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
