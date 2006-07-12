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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.ModifyMsg;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.util.TimeThread;

import static org.opends.server.synchronization.SynchMessages.SYNCHRONIZATION;

/**
 * Test the contructors, encoders and decoders of the synchronization
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class SynchronizationMsgTest extends SynchronizationTestCase
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
    InternalClientConnection connection = new InternalClientConnection();
    ModifyMsg msg = new ModifyMsg(changeNumber, dn, mods);
    ModifyMsg generatedMsg = new ModifyMsg(msg.getBytes());

    assertEquals(msg.changeNumber, generatedMsg.changeNumber);

    Operation op = msg.createOperation(connection);
    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(op.getClass(), ModifyOperation.class);
    assertEquals(generatedOperation.getClass(), ModifyOperation.class);

    ModifyOperation mod1 = (ModifyOperation) op;
    ModifyOperation mod2 = (ModifyOperation) generatedOperation;

    assertEquals(mod1.getRawEntryDN(), mod2.getRawEntryDN());

    /*
     * TODO : test that the generated mod equals the original mod.
     */
  }

  /**
   * Build some data for the DeleteMsg test below.
   * @throws DirectoryException
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
    InternalClientConnection connection = new InternalClientConnection();
    DeleteOperation op = new DeleteOperation(connection, 1, 1,null,
                                             DN.decode(rawDN));
    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
      (short) 123, (short) 45);
    op.setAttachment(SYNCHRONIZATION, cn);
    DeleteMsg msg = new DeleteMsg(op);
    DeleteMsg generatedMsg = new DeleteMsg(msg.getBytes());

    assertEquals(msg.changeNumber, generatedMsg.changeNumber);

    Operation generatedOperation = generatedMsg.createOperation(connection);

    assertEquals(generatedOperation.getClass(), DeleteOperation.class);

    DeleteOperation mod2 = (DeleteOperation) generatedOperation;

    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());
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
    InternalClientConnection connection = new InternalClientConnection();
    ModifyDNOperation op =
      new ModifyDNOperation(connection, 1, 1, null,
                  DN.decode(rawDN), RDN.decode(newRdn), deleteOldRdn,
                  (newSuperior.length() != 0 ? DN.decode(newSuperior) : null));

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 123, (short) 45);
    op.setAttachment(SYNCHRONIZATION, cn);
    ModifyDNMsg msg = new ModifyDNMsg(op);
    ModifyDNMsg generatedMsg = new ModifyDNMsg(msg.getBytes());
    Operation generatedOperation = generatedMsg.createOperation(connection);
    ModifyDNOperation mod2 = (ModifyDNOperation) generatedOperation;

    assertEquals(msg.changeNumber, generatedMsg.changeNumber);
    assertEquals(op.getRawEntryDN(), mod2.getRawEntryDN());
    assertEquals(op.getRawNewRDN(), mod2.getRawNewRDN());
    assertEquals(op.deleteOldRDN(), mod2.deleteOldRDN());
    assertEquals(op.getRawNewSuperior(), mod2.getRawNewSuperior());
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

    AttributeType org = DirectoryServer.getAttributeType("o", true);
    ArrayList<Attribute> userAttributes = new ArrayList<Attribute>(1);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(org, "com"));
    Attribute attr = new Attribute(org, "o", values);
    userAttributes.add(attr);

    ArrayList<Attribute> operationalAttributes = new ArrayList<Attribute>(1);
    org = DirectoryServer.getAttributeType("creatorsname", true);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(org, "dc=creator"));
    attr = new Attribute(org, "creatorsname", values);
    operationalAttributes.add(attr);

    ChangeNumber cn = new ChangeNumber(TimeThread.getTime(),
                                      (short) 123, (short) 45);

    AddMsg msg = new AddMsg(cn, rawDN, objectClass, userAttributes,
                            operationalAttributes);
    AddMsg generatedMsg = new AddMsg(msg.getBytes());
    assertEquals(msg.getBytes(), generatedMsg.getBytes());
    // TODO : should test that generated attributes match original attributes.
  }
}
