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
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import static org.opends.server.replication.protocol.OperationContext.*;

import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.FakeOperation;
import org.opends.server.replication.plugin.FakeOperationComparator;
import org.opends.server.replication.plugin.Historical;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;

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
    extends ReplicationTestCase
{

  /**
   * Test that conflict between a modify-replace and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void replaceAndAdd() throws Exception
  {
    Entry entry = initializeEntry();

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
  
  /**
   * Test that conflict between a modify-replace and modify-add for
   * single-valued attributes are handled correctly.
   */
  @Test()
  public void replaceAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a modify-replace done at time t10
     */
    testModify(entry, hist, "displayname", ModificationType.REPLACE,
               "init value", 10, true);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
               "older value", 1, false);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
               "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should remove it
     */
    testModify(entry, hist, "displayname", ModificationType.ADD, "new value",
               11, false);

  }
  
  /**
   * Test that replace with null value is corrrectly seen as a delete
   * by doing first a replace with null, then add at at previous time
   * then check that the add was ignored
   */
  @Test()
  public void replaceWithNull() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a replace with null done at time t3
     */
    testModify(entry, hist, "displayname", ModificationType.REPLACE, null, 3,
        true);

    /*
     * Now simulate an add at an earlier date that the previous replace. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "older value", 1, false);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, "displayname", ModificationType.ADD, "new value",
        4, true);
  }

  /**
   * Test that conflict between modify-add and modify-replace for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void addAndReplace() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a modify-add done at time t10
     */
    testModify(entry, hist, "description", ModificationType.ADD,
        "init value", 10, true);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, "description", ModificationType.REPLACE,
        "older value", 1, true);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, "description", ModificationType.REPLACE,
        "older value", 2, true);

    /*
     * Now simulate a replace at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, "description", ModificationType.REPLACE,
        "new value", 11, true);

  }
  
  /**
   * Test that conflict between modify-add and modify-replace for
   * single-valued attributes are handled correctly.
   */
  @Test()
  public void addAndReplaceSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a modify-add done at time 2
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "init value", 2, true);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, "displayname", ModificationType.REPLACE,
        "older value", 1, true);

    /*
     * Now simulate a replace at a later date.
     * Conflict resolution should keept it.
     */
    testModify(entry, hist, "displayname", ModificationType.REPLACE,
        "older value", 3, true);

  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndAdd() throws Exception
  {
    Entry entry = initializeEntry();


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
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();


    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a delete of the whole description attribute done at time
     * t2
     */
    testModify(entry, hist, "displayname", ModificationType.DELETE, null, 3,
        true);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "older value", 1, false);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "older value", 2, false);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, "displayname", ModificationType.ADD, "new value",
        4, true);
  }
  
  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndReplace() throws Exception
  {
    Entry entry = initializeEntry();


    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a delete of the whole description attribute done at time
     * t4
     */
    testModify(entry, hist, "description", ModificationType.DELETE, null, 4,
        true);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, "description", ModificationType.REPLACE,
        "new value", 3, false);
  }
  
  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndReplaceSingle() throws Exception
  {
    Entry entry = initializeEntry();


    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a delete of the whole description attribute done at time
     * t4
     */
    testModify(entry, hist, "displayname", ModificationType.DELETE, null, 4,
        true);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, "displayname", ModificationType.REPLACE,
        "new value", 3, false);
  }

  /**
   * Test that conflict between a modify-add and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void addAndAdd() throws Exception
  {
    Entry entry = initializeEntry();

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
     * Now simulate an add with a value already existing.
     * The conflict resolution should remove this add.
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

  /**
   * Test that conflict between a modify-add and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test()
  public void addAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "init value", 10, true);
    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     * and that the previous value must be discarded, and therefore 
     * turn the add into a replace.
     */
    Modification mod = buildMod("displayname", ModificationType.ADD,
        "older value");
    List<Modification> mods = replayModify(entry, hist, mod, 1);
    
    /*
     * After replay the mods should contain only one mod,
     * the mod should now be a replace with the older value. 
     */
    testMods(mods, 1, ModificationType.REPLACE, "older value");

    /*
     * Now simulate a new value at a later date.
     * The conflict modify code should detect that there is already a value
     * and skip this change.
     */
    mod = buildMod("displayname", ModificationType.ADD, "new value");
    mods = replayModify(entry, hist, mod, 2);
    assertTrue(mods.isEmpty());
  }
  
  /**
   * Test that conflict between add delete and add are handled correctly.
   */
  @Test()
  public void addDelAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a add of the description attribute done at time t1
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "init value", 1, true);
    
    /*
     * simulate a del of the description attribute done at time t3
     * this should be processed normally
     */
    testModify(entry, hist, "displayname", ModificationType.DELETE,
        "init value", 3, true);
    
    /*
     * Now simulate another add, that would come from another master
     * and done at time t2 (between t1 and t2)
     * This add should not be processed.
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "second value", 2, false);
  }
  
  /**
   * Test that conflict between add, add and delete are handled correctly.
   * 
   * This test simulate the case where a simple add is done on a first server
   * and at a later date but before replication happens, a add followed by 
   * a delete of the second value is done on another server.
   * The test checks that the firs tvalue wins and stay in the entry.  
   */
  @Test()
  public void addAddDelSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    Historical hist = Historical.load(entry);

    /*
     * simulate a add of the description attribute done at time t1
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "first value", 1, true);
    
    /*
     * simulate a add of the description attribute done at time t2
     * with a second value. This should not work because there is already
     * a value
     */
    testModify(entry, hist, "displayname", ModificationType.ADD,
        "second value", 2, false);
    
    /*
     * Now simulate a delete of the second value.
     * The delete should not be accepted because it is done on a value
     * that did not get into the entry.
     */
    testModify(entry, hist, "displayname", ModificationType.DELETE,
        "second value", 2, false);
  }

  /**
   * Check that the mods given as first parameter match the next parameters.
   * 
   * @param mods The mods that must be tested.
   * @param size the size that the mods must have.
   * @param modType the type of Modification that the first mod of the 
   *                mods should have.   
   * @param value the value that the first mod of the mods should have.  
   */
  private void testMods(
      List<Modification> mods, int size, ModificationType modType, String value)
  {
    assertEquals(size, mods.size());
    Modification newMod = mods.get(0);
    assertTrue(newMod.getModificationType().equals(modType));
    AttributeValue val = newMod.getAttribute().getValues().iterator().next();
    assertEquals(val.getStringValue(), value);
  }

  /**
   * Create an initialize an entry that can be used for modify conflict
   * resolution tests.
   */
  private Entry initializeEntry() throws DirectoryException
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
     * start with a new entry with an empty attribute
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
    return entry;
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
        ReplicationMessage generatedMsg = fk.generateMessage() ;
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

  /**
   * 
   *
   */
  private void testModify(Entry entry,
      Historical hist, String attrName,
      ModificationType modType, String value,
      int date, boolean keepChangeResult)
  {
    Modification mod = buildMod(attrName, modType, value);
    List<Modification> mods = replayModify(entry, hist, mod, date);

    if (keepChangeResult)
    {
      /*
       * The last change should have been detected as newer and
       * should be kept by the conflict resolution code.
       */
      assertTrue(mods.contains(mod));
      assertEquals(1, mods.size());
    }
    else
    {
      /*
       * The last older change should have been detected as conflicting and
       * should be removed by the conflict resolution code.
       */
      assertFalse(mods.contains(mod));
      assertEquals(0, mods.size());
    }
  }

  /**
   * 
   */
  private List<Modification> replayModify(
      Entry entry, Historical hist, Modification mod, int date)
  {
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();
    ChangeNumber t = new ChangeNumber(date, (short) 0, (short) 0);

    List<Modification> mods = new ArrayList<Modification>();
    mods.add(mod);

    ModifyOperationBasis modOpBasis =
      new ModifyOperationBasis(connection, 1, 1, null, entry.getDN(), mods);
    LocalBackendModifyOperation modOp = new LocalBackendModifyOperation(modOpBasis);
    ModifyContext ctx = new ModifyContext(t, "uniqueId");
    modOp.setAttachment(SYNCHROCONTEXT, ctx);

    hist.replayOperation(modOp, entry);
    if (mod.getModificationType() == ModificationType.ADD)
    {
      AddOperationBasis addOpBasis =
        new AddOperationBasis(connection, 1, 1, null, entry
          .getDN(), entry.getObjectClasses(), entry.getUserAttributes(),
          entry.getOperationalAttributes());
      LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpBasis);
      testHistorical(hist, addOp);
    }
    else
    {
      testHistoricalAndFake(hist, entry);
    }

    /*
     * Check that the encoding decoding of historical information
     * works  by encoding decoding and checking that the result is the same
     * as the initial value.
     */
    entry.removeAttribute(Historical.historicalAttrType);
    entry.addAttribute(hist.encode(), null);
    Historical hist2 = Historical.load(entry);
    assertEquals(hist2.encode().toString(), hist.encode().toString());
    
    return mods;
  }

  private Modification buildMod(
      String attrName, ModificationType modType, String value)
  {
    /* create AttributeType that will be used for this test */
    AttributeType attrType =
      DirectoryServer.getAttributeType(attrName, true);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    if (value != null)
      values.add(new AttributeValue(attrType, value));
    Attribute attr = new Attribute(attrType, attrName, values);
    Modification mod = new Modification(modType, attr);
    
    return mod;
  }

  /**
   *
   */
  private void testHistorical(
      Historical hist, LocalBackendAddOperation addOp)
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
}
