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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import static org.opends.server.synchronization.protocol.OperationContext.*;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.protocol.ModifyContext;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.synchronization.protocol.UpdateMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;

/*
 * Test the conflict resolution for modify operations As a consequence,
 * this will also test the Historical.java Class This is still a work in
 * progress. currently implemented tests - check that an replace with a
 * smaller csn is ignored should test : - conflict with multi-valued
 * attributes - conflict with single-valued attributes - conflict with
 * options - conflict with binary attributes - Replace, add, delete
 * attribute, delete attribute value - conflict on the objectclass
 * attribute
 */

public class ModifyConflictTest
    extends SynchronizationTestCase
{

  /**
   * Test that conflict between a modify-replace and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void replaceAndAdd() throws Exception
  {
    DN dn = DN.decode("dc=com");
    Map<ObjectClass, String> objectClasses = new HashMap<ObjectClass, String>();
    ObjectClass org = DirectoryServer.getObjectClass("organization");
    objectClasses.put(org, "organization");

    Entry entry = new Entry(dn, objectClasses, null, null);

    // Construct a new random UUID. and add it into the entry
    UUID uuid = UUID.randomUUID();

    // Create the att values list of uuid
    LinkedHashSet<AttributeValue> valuesUuid =
      new LinkedHashSet<AttributeValue>(1);
    valuesUuid.add(new AttributeValue(Historical.entryuuidAttrType,
        new ASN1OctetString(uuid.toString())));
    ArrayList<Attribute> uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = new Attribute(Historical.entryuuidAttrType,
        "entryUUID", valuesUuid);
    uuidList.add(uuidAttr);

    /*
     * Add the uuid in the entry
     */
    Map<AttributeType, List<Attribute>> operationalAttributes = entry
        .getOperationalAttributes();
    operationalAttributes.put(Historical.entryuuidAttrType, uuidList);

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a modify-replace done at time t10
     */
    testModify(entry, hist, "description", ModificationType.REPLACE,
        "init value", 10, true);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 1, false);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, "description", ModificationType.ADD, "new value",
        11, true);

  }

  /*
   * helper function.
   */
  private void testHistoricalAndFake(
      Historical hist, Entry entry)
  {

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = Historical.getEntryUuid(entry);

    // Get the Entry uuid in String format
    List<Attribute> uuidAttrs = entry
        .getOperationalAttribute(Historical.entryuuidAttrType);
    uuidAttrs.get(0).getValues().iterator().next().toString();

    if (uuidAttrs != null)
    {
      if (uuidAttrs.size() > 0)
      {
        Attribute att = uuidAttrs.get(0);
        String retrievedUuid = (att.getValues().iterator().next()).toString();
        assertTrue(retrievedUuid.equals(uuid));
      }
    }


    // Test FakeOperation
    try
    {
      Iterable<FakeOperation> fks = Historical.generateFakeOperations(entry);
      if (fks.iterator().hasNext())
      {
        FakeOperation fk = fks.iterator().next();
        assertTrue(new FakeOperationComparator().compare(fk, fk) == 0);
        assertTrue(new FakeOperationComparator().compare(null , fk) < 0);
        SynchronizationMessage generatedMsg = fk.generateMessage() ;
        if (generatedMsg instanceof UpdateMessage)
        {
          UpdateMessage new_name = (UpdateMessage) generatedMsg;
          assertEquals(new_name.getUniqueId(),uuid);

        }

      }

    }
    catch (RuntimeException e)
    {
      assertTrue(false) ;
    }
  }

  /*
   * helper function.
   */
  private void testHistorical(
      Historical hist, AddOperation addOp)
  {

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = Historical.getEntryUuid(addOp);

    // Get the op uuid in String format
    List<Attribute> uuidAttrs = addOp.getOperationalAttributes().get(
        Historical.entryuuidAttrType);
    uuidAttrs.get(0).getValues().iterator().next().toString();

    if (uuidAttrs != null)
    {
      if (uuidAttrs.size() > 0)
      {
        Attribute att = uuidAttrs.get(0);
        String retrievedUuid = (att.getValues().iterator().next()).toString();
        assertTrue(retrievedUuid.equals(uuid));
      }
    }
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndAdd() throws Exception
  {
    /*
     * Objectclass and DN do not have any impact on the modifty conflict
     * resolution for the description attribute. Always use the same values
     * for all these tests.
     */
    DN dn = DN.decode("dc=com");
    Map<ObjectClass, String> objectClasses = new HashMap<ObjectClass, String>();
    ObjectClass org = DirectoryServer.getObjectClass("organization");
    objectClasses.put(org, "organization");

    /*
     * start with a new entry with an empty description
     */
    Entry entry = new Entry(dn, objectClasses, null, null);

    // Construct a new random UUID. and add it into the entry
    UUID uuid = UUID.randomUUID();

    // Create the att values list
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(
        1);
    values.add(new AttributeValue(Historical.entryuuidAttrType,
        new ASN1OctetString(uuid.toString())));
    ArrayList<Attribute> uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = new Attribute(Historical.entryuuidAttrType,
        "entryUUID", values);
    uuidList.add(uuidAttr);

    /*
     * Add the uuid in the entry
     */
    Map<AttributeType, List<Attribute>> operationalAttributes = entry
        .getOperationalAttributes();

    operationalAttributes.put(Historical.entryuuidAttrType, uuidList);


    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a delete of the whole description attribute done at time
     * t10
     */
    testModify(entry, hist, "description", ModificationType.DELETE, null, 10,
        true);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 1, false);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, "description", ModificationType.ADD, "new value",
        11, true);

  }

  /**
   * Test that conflict between a modify-add and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void addAndAdd() throws Exception
  {
    /*
     * Objectclass and DN do not have any impact on the modifty conflict
     * resolution for the description attribute. Always use the same values
     * for all these tests.
     */
    DN dn = DN.decode("dc=com");
    Map<ObjectClass, String> objectClasses = new HashMap<ObjectClass, String>();
    ObjectClass org = DirectoryServer.getObjectClass("organization");
    objectClasses.put(org, "organization");

    /*
     * start with a new entry with an empty description
     */
    Entry entry = new Entry(dn, objectClasses, null, null);

    // Construct a new random UUID. and add it into the entry
    UUID uuid = UUID.randomUUID();

    // Create the att values list
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(
        1);
    values.add(new AttributeValue(Historical.entryuuidAttrType,
        new ASN1OctetString(uuid.toString())));
    ArrayList<Attribute> uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = new Attribute(Historical.entryuuidAttrType,
        "entryUUID", values);
    uuidList.add(uuidAttr);

    /*
     * Add the uuid in the entry
     */
    Map<AttributeType, List<Attribute>> operationalAttributes = entry
        .getOperationalAttributes();

    operationalAttributes.put(Historical.entryuuidAttrType, uuidList);

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "init value", 10, true);
    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 1, true);

    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous add. conflict
     * resolution should keep it
     */
    testModify(entry, hist, "description", ModificationType.ADD, "new value",
        11, true);
  }

  /*
   * helper function.
   */
  private void testModify(Entry entry,
      Historical hist, String attrName,
      ModificationType modType, String value,
      int date, boolean keepChangeResult)
  {
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    ChangeNumber t = new ChangeNumber(date, (short) 0, (short) 0);

    /* create AttributeType description that will be used for this test */
    AttributeType attrType =
      DirectoryServer.getAttributeType(attrName, true);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    if (value != null)
      values.add(new AttributeValue(attrType, value));
    Attribute attr = new Attribute(attrType, attrName, values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(modType, attr);
    mods.add(mod);

    ModifyOperation modOp = new ModifyOperation(connection, 1, 1, null,
        entry.getDN(), mods);
    ModifyContext ctx = new ModifyContext(t, "uniqueId");
    modOp.setAttachment(SYNCHROCONTEXT, ctx);

    hist.replayOperation(modOp, entry);
    if (modType == ModificationType.ADD)
    {
      AddOperation addOp = new AddOperation(connection, 1, 1, null, entry
          .getDN(), entry.getObjectClasses(), entry.getUserAttributes(),
          entry.getOperationalAttributes());
      testHistorical(hist, addOp);
    }
    else
    {
      testHistoricalAndFake(hist, entry);
    }
    /*
     * The last older change should have been detected as conflicting and
     * should be removed by the conflict resolution code.
     */
    if (keepChangeResult)
    {
      assertTrue(mods.contains(mod));
      assertEquals(1, mods.size());
    }
    else
    {
      assertFalse(mods.contains(mod));
      assertEquals(0, mods.size());
    }
  }
}
