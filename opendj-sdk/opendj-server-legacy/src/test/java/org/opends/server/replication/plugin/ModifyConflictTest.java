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
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.testng.Assert.*;

/**
 * Test the conflict resolution for modify operations As a consequence,
 * this will also test the Historical.java Class This is still a work in
 * progress. currently implemented tests - check that an replace with a
 * smaller csn is ignored should test : - conflict with multi-valued
 * attributes - conflict with single-valued attributes - conflict with
 * options - conflict with binary attributes - Replace, add, delete
 * attribute, delete attribute value - conflict on the objectclass
 * attribute.
 * Added tests for multiple mods on same attribute in the same modify operation.
 */
@SuppressWarnings("javadoc")
public class ModifyConflictTest extends ReplicationTestCase
{

  private static final String ORGANIZATION = "organization";
  private static final String DISPLAYNAME = "displayname";
  private static final String EMPLOYEENUMBER = "employeenumber";
  private static final String DESCRIPTION = "description";
  private static final String SYNCHIST = "ds-sync-hist";

  /**
   * Test that conflict between a modify-replace and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test
  public void replaceAndAdd() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute repl = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:repl:init value");

    // simulate a modify-replace done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.REPLACE, "init value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, 1, false, buildMod(DESCRIPTION, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, 2, false, buildMod(DESCRIPTION, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, 11, true, buildMod(DESCRIPTION, ModificationType.ADD, "new value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:repl:init value",
        ":000000000000000b000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a modify-replace and modify-add for
   * single-valued attributes are handled correctly.
   */
  @Test
  public void replaceAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute repl = buildSyncHist(DISPLAYNAME,
        ":000000000000000a000000000000:repl:init value");

    // simulate a modify-replace done at time t10
    testModify(entry, hist, 10, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "init value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, 1, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should remove it
     */
    testModify(entry, hist, 11, false, buildMod(DISPLAYNAME, ModificationType.ADD, "new value"));
    assertEquals(hist.encodeAndPurge(), repl);

    assertContainsOnlyValues(entry, DISPLAYNAME, "init value");
  }

  /**
   * Test that replace with null value is correctly seen as a delete
   * by doing first a replace with null, then add at at previous time
   * then check that the add was ignored.
   */
  @Test
  public void replaceWithNull() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000003000000000000:attrDel");

    // simulate a replace with null done at time t3
    testModify(entry, hist, 3, true, buildMod(DISPLAYNAME, ModificationType.REPLACE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous replace. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, 4, true, buildMod(DISPLAYNAME, ModificationType.ADD, "new value"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000004000000000000:add:new value",
        ":0000000000000003000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between modify-add and modify-replace for
   * multi-valued attributes are handled correctly.
   */
  @Test
  public void addAndReplace() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a modify-add done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.ADD, "init value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.REPLACE, "older value"));
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value",
        ":0000000000000001000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, 2, true, buildMod(DESCRIPTION, ModificationType.REPLACE, "older value"));
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value",
        ":0000000000000002000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate a replace at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, 11, true, buildMod(DESCRIPTION, ModificationType.REPLACE, "new value"));
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000b000000000000:repl:new value");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between modify-add and modify-replace for
   * single-valued attributes are handled correctly.
   */
  @Test
  public void addAndReplaceSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a modify-add done at time 2
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.ADD, "init value"));
    Attribute syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), syncHist);

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "older value"));
    syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), syncHist);

    assertContainsOnlyValues(entry, DISPLAYNAME, "older value");

    /*
     * Now simulate a replace at a later date.
     * Conflict resolution should keep it.
     */
    testModify(entry, hist, 3, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "newer value"));
    syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000003000000000000:repl:newer value");
    assertEquals(hist.encodeAndPurge(), syncHist);

    assertContainsOnlyValues(entry, DISPLAYNAME, "newer value");
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test
  public void deleteAndAdd() throws Exception
  {
    Entry entry = initializeEntry();


    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    Attribute attrDel = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:attrDel");

    // simulate a delete of the whole description attribute done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, false, buildMod(DESCRIPTION, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, 2, false, buildMod(DESCRIPTION, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, 11, true, buildMod(DESCRIPTION, ModificationType.ADD, "new value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
            ":000000000000000b000000000000:add:new value",
            ":000000000000000a000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a delete-attribute value and
   * add attribute-values from 2 different servers are handled correctly.
   * This test was created to reproduce issue 3392.
   */
  @Test
  public void delValueAndAddValue() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);


    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a delete of the description attribute value "value1" done at time t1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate an add of "value3" at time t2
    testModify(entry, hist, 2, true, buildMod(DESCRIPTION, ModificationType.ADD, "value3"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1",
        ":0000000000000002000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate a delete of value "value1" at time t3
    testModify(entry, hist, 3, false, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000002000000000000:add:value3",
        ":0000000000000003000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate an add of "value4" at time t4
    testModify(entry, hist, 4, true, buildMod(DESCRIPTION, ModificationType.ADD, "value4"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000002000000000000:add:value3",
        ":0000000000000003000000000000:del:value1",
        ":0000000000000004000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between several delete-attribute value and
   * are handled correctly.
   * This test was created to reproduce and fix issue 3397.
   */
  @Test
  public void delValueAndDelValue() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create an attribute with values value1, value2, value3 and value4 and
    // add this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);


    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the description attribute values
     *  "value1" and "value2" done at time t1
     */
    testModify(entry, hist, 1, true,
        buildMod(DESCRIPTION, ModificationType.DELETE, "value1", "value2"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1",
        ":0000000000000001000000000000:del:value2");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * simulate a delete of the description attribute values
     *  "value2" and "value3" done at time t1
     */
    testModify(entry, hist, 2, true,
        buildMod(DESCRIPTION, ModificationType.DELETE, "value2", "value3"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1",
        ":0000000000000002000000000000:del:value2",
        ":0000000000000002000000000000:del:value3");
    assertEquals(hist.encodeAndPurge(), attr);

    assertContainsOnlyValues(entry, DESCRIPTION, "value1");
  }

  /**
   * Test that conflict between a delete attribute and a delete
   * value on a single valued attribute works correctly.
   * This test was created to reproduce and fix issue 3399.
   */
  @Test
  public void delAttributeAndDelValueSingle() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<ByteString> duplicateValues = new LinkedList<>();
    Attribute attribute = Attributes.create(EMPLOYEENUMBER, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a delete of attribute employeenumber.
    testModify(entry, hist, 1, true, buildMod(EMPLOYEENUMBER, ModificationType.DELETE));
    Attribute attr = buildSyncHist(EMPLOYEENUMBER,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // now simulate a delete of value "value1"
    testModify(entry, hist, 2, false, buildMod(EMPLOYEENUMBER, ModificationType.DELETE, "value1"));
    attr = buildSyncHist(EMPLOYEENUMBER,
        ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a delete attribute and a delete
   * value on a single valued attribute works correctly.
   * This test was created to reproduce and fix issue 3399.
   */
  @Test
  public void delValueAndDelAttributeSingle() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<ByteString> duplicateValues = new LinkedList<>();
    Attribute attribute = Attributes.create(EMPLOYEENUMBER, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // now simulate a delete of value "value1"
    testModify(entry, hist, 1, true, buildMod(EMPLOYEENUMBER, ModificationType.DELETE, "value1"));
    Attribute attr = buildSyncHist(EMPLOYEENUMBER,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of attribute employeenumber.
    testModify(entry, hist, 2, false, buildMod(EMPLOYEENUMBER, ModificationType.DELETE));
    attr = buildSyncHist(EMPLOYEENUMBER,
        ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a delete-attribute value and
   * add attribute-values from 2 different servers
   * and replayed in the non-natural order are handled correctly.
   * This test was created to reproduce issue 3392.
   */
  @Test
  public void delValueAndAddValueDisordered() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the description attribute value "value1"
     * done at time t3
     */
    testModify(entry, hist, 3, true, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000003000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate an add of "value3" at time t4
    testModify(entry, hist, 4, true, buildMod(DESCRIPTION, ModificationType.ADD, "value3"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000003000000000000:del:value1",
        ":0000000000000004000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate a delete of value "value1" at time t1
    testModify(entry, hist, 1, false, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000003000000000000:del:value1",
        ":0000000000000004000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate an add of "value4" at time t2
    testModify(entry, hist, 2, true, buildMod(DESCRIPTION, ModificationType.ADD, "value4"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000003000000000000:del:value1",
        ":0000000000000004000000000000:add:value3",
        ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test
  public void deleteAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<ByteString> duplicateValues = new LinkedList<>();
    Attribute attribute = Attributes.create(DISPLAYNAME, "value1");
    entry.addAttribute(attribute, duplicateValues);
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000003000000000000:attrDel");

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a delete of the whole description attribute done at time t2
    testModify(entry, hist, 3, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.ADD, "older value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, 4, true, buildMod(DISPLAYNAME, ModificationType.ADD, "new value"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000003000000000000:attrDel",
        ":0000000000000004000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test
  public void deleteAndReplace() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DESCRIPTION,":0000000000000004000000000000:attrDel");

    // simulate a delete of the whole description attribute done at time t4
    testModify(entry, hist, 4, true, buildMod(DESCRIPTION, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, 3, false, buildMod(DESCRIPTION, ModificationType.REPLACE, "new value"));
    assertEquals(hist.encodeAndPurge(), attrDel);
  }

  /**
   * Test that conflict between a modify-replace and a a modify-delete
   * with some attribute values is resolved correctly.
   * This test has been created to reproduce Issue 3397.
   */
  @Test
  public void replaceAndDelete() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a REPLACE of the attribute with values : value1, value2, value3
    // at time t1.
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");

    Modification mod =
      new Modification(ModificationType.REPLACE, builder.toAttribute());
    testModify(entry, hist, 1, true, mod);

    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:repl:value1",
        ":0000000000000001000000000000:add:value2",
        ":0000000000000001000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a DELETE of the attribute values : value3 and value4
    // at time t2.
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value3");
    builder.add("value4");
    mod = new Modification(ModificationType.DELETE, builder.toAttribute());

    List<Modification> mods = replayModify(entry, hist, mod, 2);
    mod = mods.get(0);
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value3");
    assertEquals(mod.getAttribute(), builder.toAttribute());
    assertEquals(mod.getModificationType(), ModificationType.DELETE);

    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:repl:value1",
        ":0000000000000001000000000000:add:value2",
        ":0000000000000002000000000000:del:value3",
        ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a modify-replace and a a modify-delete
   * with some attribute values is resolved correctly when they are replayed
   * disorderly.
   * This test has been created to reproduce Issue 3397.
   */
  @Test
  public void replaceAndDeleteDisorder() throws Exception
  {
    AttributeType descriptionAttrType =
      DirectoryServer.getSchema().getAttributeType(DESCRIPTION);

    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // We will reuse these attributes a couple of times.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value3");
    builder.add("value4");
    Attribute values3and4 = builder.toAttribute();
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    Attribute values1and2 = builder.toAttribute();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a DELETE of the attribute values : value3 and value4 at time t2.
    Modification mod =
      new Modification(ModificationType.DELETE, values3and4);
    List<Modification> mods = replayModify(entry, hist, mod, 2);
    entry.applyModifications(mods);
    // check that the MOD is not altered by the replay mechanism.
    mod = mods.get(0);
    assertEquals(mod.getModificationType(), ModificationType.DELETE);
    assertEquals(mod.getAttribute(), values3and4);

    // check that the entry now contains value1 and value2 and no other values.
    Attribute resultEntryAttr = entry.getAttribute(descriptionAttrType).get(0);
    assertEquals(resultEntryAttr, values1and2);

    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000002000000000000:del:value3",
        ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a REPLACE of the attribute with values : value1, value2, value3
    // at time t1.
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");

    mod = new Modification(ModificationType.REPLACE, builder.toAttribute());
    mods = replayModify(entry, hist, mod, 1);
    entry.applyModifications(mods);
    mod = mods.get(0);
    // check that value3 has been removed from the MOD-REPLACE because
    // a later operation contains a MOD-DELETE of this value.
    assertEquals(mod.getModificationType(), ModificationType.REPLACE);
    assertEquals(mod.getAttribute(), values1and2);
    // check that the entry now contains value1 and value2 and no other values.
    assertEquals(resultEntryAttr, values1and2);

    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:repl:value1",
        ":0000000000000001000000000000:add:value2",
        ":0000000000000002000000000000:del:value3",
        ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test
  public void deleteAndReplaceSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<ByteString> duplicateValues = new LinkedList<>();
    Attribute attribute = Attributes.create(DISPLAYNAME, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000004000000000000:attrDel");

    // simulate a delete of the whole description attribute done at time t4
    testModify(entry, hist, 4, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, 3, false, buildMod(DISPLAYNAME, ModificationType.REPLACE, "new value"));
    assertEquals(hist.encodeAndPurge(), attrDel);
  }

  /**
   * Test that conflict between a modify-add and modify-add for
   * multi-valued attributes are handled correctly.
   */
  @Test
  public void addAndAdd() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a add of the description attribute done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.ADD, "init value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     */
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.ADD, "older value"));
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value",
        ":0000000000000001000000000000:add:older value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate an add with a value already existing.
     * The conflict resolution should remove this add.
     */
    testModify(entry, hist, 13, false, buildMod(DESCRIPTION, ModificationType.ADD, "init value"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:older value",
        ":000000000000000d000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate an add at a later date that the previous add. conflict
     * resolution should keep it
     */
    testModify(entry, hist, 14, true, buildMod(DESCRIPTION, ModificationType.ADD, "new value"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:older value",
        ":000000000000000d000000000000:add:init value",
        ":000000000000000e000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that a DEL and ADD of the same attribute same value done
   * in a single operation correctly results in the value being kept.
   *
   *  This is not a conflict in the strict definition but does exert
   *  the conflict resolution code.
   */
  @Test
  public void delAndAddSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a add of the description attribute done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.ADD, "Init Value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:Init Value");
    assertEquals(hist.encodeAndPurge(), attr);

    // Now simulate a del and a add in the same operation
    attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod1 = new Modification(ModificationType.DELETE, attr);

    attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod2 = new Modification(ModificationType.ADD, attr);

    List<Modification> mods = new LinkedList<>();
    mods.add(mod1);
    mods.add(mod2);

    replayModifies(entry, hist, mods, 11);
    assertEquals(mods.size(), 2,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(0), mod1,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(1), mod2,
      "DEL and ADD of the same attribute same value was not correct");
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000b000000000000:add:Init Value");
    assertEquals(hist.encodeAndPurge(), attr);
  }

  /**
   * Test that a DEL of one value and REPLACE with no value, of the same
   * attribute done in a single operation correctly results in the value
   * being kept.
   *
   * This is not a conflict in the strict definition but does exert
   * the conflict resolution code.
   */
  @Test
  public void delAndReplaceSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DESCRIPTION,
        ":000000000000000c000000000000:attrDel");

    // simulate a add of the description attribute done at time t10
    testModify(entry, hist, 10, true, buildMod(DESCRIPTION, ModificationType.ADD, "init value"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a add of the description attribute done at time t10
    testModify(entry, hist, 11, true, buildMod(DESCRIPTION, ModificationType.ADD, "second value"));
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000a000000000000:add:init value",
        ":000000000000000b000000000000:add:second value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate a delete of one value and a replace with no value
     * in the same operation
     */

    attr = Attributes.create(DESCRIPTION, "init value");
    Modification mod1 = new Modification(ModificationType.DELETE, attr);

    attr = Attributes.empty(DESCRIPTION);
    Modification mod2 = new Modification(ModificationType.REPLACE, attr);

    List<Modification> mods = new LinkedList<>();
    mods.add(mod1);
    mods.add(mod2);

    List<Modification> mods2 = new LinkedList<>(mods);
    replayModifies(entry, hist, mods, 12);
    assertEquals(hist.encodeAndPurge(), attrDel);
    assertEquals(mods.size(), 2,
      "DEL one value, del by Replace of the same attribute was not correct");
    assertEquals(mods.get(0), mod1,
      "DEL one value, del by Replace of the same attribute was not correct");
    assertEquals(mods.get(1), mod2,
      "DEL one value, del by Replace of the same attribute was not correct");

    // Replay the same modifs again
    replayModifies(entry, hist, mods2, 12);
    assertEquals(hist.encodeAndPurge(), attrDel);
    assertEquals(mods2.size(), 2,
      "DEL one value, del by Replace of the same attribute was not correct");
  }

  /**
   * Test that a ADD and DEL of the same attribute same value done
   * in a single operation correctly results in the value being kept.
   *
   * This is not a conflict in the strict definition but does exert
   *  the conflict resolution code.
   */
  @Test
  public void addAndDelSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // Now simulate a del and a add in the same operation
    Attribute attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod1 = new Modification(ModificationType.ADD, attr);

    attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod2 = new Modification(ModificationType.DELETE, attr);

    List<Modification> mods = new LinkedList<>();
    mods.add(mod1);
    mods.add(mod2);

    replayModifies(entry, hist, mods, 11);
    attr = buildSyncHist(DESCRIPTION,
        ":000000000000000b000000000000:del:Init Value");
    assertEquals(hist.encodeAndPurge(), attr);
    assertEquals(mods.size(), 2,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(0), mod1,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(1), mod2,
      "DEL and ADD of the same attribute same value was not correct");
  }

  /**
   * Test that conflict between two modify-add with for
   * multi-valued attributes are handled correctly when some of the values
   * are the same :
   *  - first ADD done with value1
   *  - second ADD done with value1 and value2
   * This test has been created to make sure that issue 3394 is fixed.
   */
  @Test
  public void addAndAddSameValues() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a add of the description attribute done at time 1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.ADD, "value1"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * simulate an add of the description attribute values
     *  "value1" and "value2" done at time 2
     */
    testModify(entry, hist, 2, true,
        buildMod(DESCRIPTION, ModificationType.ADD, "value1", "value2"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000002000000000000:add:value1",
        ":0000000000000002000000000000:add:value2");
    assertEquals(hist.encodeAndPurge(), attr);

    assertContainsOnlyValues(entry, DESCRIPTION, "value1", "value2");


    // do the same as before but in reverse order
    entry = initializeEntry();

    // load historical from the entry
    hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate an add of the description attribute values
     *  "value1" and "value2" done at time 1
     */
    testModify(entry, hist, 1, true,
        buildMod(DESCRIPTION, ModificationType.ADD, "value1", "value2"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value1",
        ":0000000000000001000000000000:add:value2");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a add of the description attribute done at time 1
    testModify(entry, hist, 2, false, buildMod(DESCRIPTION, ModificationType.ADD, "value1"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value2",
        ":0000000000000002000000000000:add:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    assertContainsOnlyValues(entry, DESCRIPTION, "value1", "value2");
  }

  /**
   * Test that conflict between a modify-add and modify-add for
   * single-valued attributes are handled correctly.
   */
  @Test
  public void addAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute olderValue = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:older value");

    // simulate a add of the displayName attribute done at time t10
    testModify(entry, hist, 10, true, buildMod(DISPLAYNAME, ModificationType.ADD, "init value"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     * and that the previous value must be discarded, and therefore
     * turn the add into a replace.
     */
    Modification mod = buildMod(DISPLAYNAME, ModificationType.ADD, "older value");
    List<Modification> mods = replayModify(entry, hist, mod, 1);
    assertEquals(hist.encodeAndPurge(), olderValue);

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
    mod = buildMod(DISPLAYNAME, ModificationType.ADD, "new value");
    mods = replayModify(entry, hist, mod, 2);
    assertEquals(hist.encodeAndPurge(), olderValue);
    assertTrue(mods.isEmpty());
  }

  /**
   * Test that conflict between add, delete and add on aingle valued attribute
   * are handled correctly.
   */
  @Test
  public void addDelAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000003000000000000:attrDel");

    // simulate a add of the displayName attribute done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "init value"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), attr);

    /*
     * simulate a del of the displayName attribute done at time t3
     * this should be processed normally
     */
    testModify(entry, hist, 3, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "init value"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate another add, that would come from another master
     * and done at time t2 (between t1 and t2)
     * This add should not be processed.
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.ADD, "second value"));
    assertEquals(hist.encodeAndPurge(), attrDel);
  }

  /**
   * Test that conflict between add, add and delete on single valued attributes
   * are handled correctly.
   *
   * This test simulate the case where a simple add is done on a first server
   * and at a later date but before replication happens, a add followed by
   * a delete of the second value is done on another server.
   * The test checks that the first value wins and stays in the entry.
   */
  @Test
  public void addAddDelSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute firstValue = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:first value");

    // simulate a add of the displayName attribute done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "first value"));
    assertEquals(hist.encodeAndPurge(), firstValue);

    /*
     * simulate a add of the displayName attribute done at time t2
     * with a second value. This should not work because there is already
     * a value
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.ADD, "second value"));
    assertEquals(hist.encodeAndPurge(), firstValue);

    /*
     * Now simulate a delete of the second value.
     * The delete should not be accepted because it is done on a value
     * that did not get into the entry.
     */
    testModify(entry, hist, 2, false, buildMod(DISPLAYNAME, ModificationType.DELETE, "second value"));
    assertEquals(hist.encodeAndPurge(), firstValue);
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
    assertEquals(newMod.getModificationType(), modType);
    ByteString val = newMod.getAttribute().iterator().next();
    assertEquals(val.toString(), value);
  }

  /**
   * Create an initialize an entry that can be used for modify conflict
   * resolution tests.
   */
  private Entry initializeEntry() throws DirectoryException
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(
          EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);

    /*
     * Objectclass and DN do not have any impact on the modify conflict
     * resolution for the description attribute. Always use the same values
     * for all these tests.
     */
    DN dn = DN.valueOf(TEST_ROOT_DN_STRING);
    Map<ObjectClass, String> objectClasses = new HashMap<>();
    ObjectClass org = DirectoryServer.getObjectClass(ORGANIZATION);
    objectClasses.put(org, ORGANIZATION);

    // start with a new entry with an empty attribute
    Entry entry = new Entry(dn, objectClasses, null, null);

    // Construct a new random UUID. and add it into the entry
    UUID uuid = UUID.randomUUID();

    // Create the att values list
    List<Attribute> uuidList = Attributes.createAsList(entryuuidAttrType, uuid.toString());

    // Add the uuid in the entry
    Map<AttributeType, List<Attribute>> operationalAttributes = entry.getOperationalAttributes();
    operationalAttributes.put(entryuuidAttrType, uuidList);
    return entry;
  }

  /**
   * Helper function.
   */
  private void testHistoricalAndFake(Entry entry)
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = EntryHistorical.getEntryUUID(entry);

    // Get the Entry uuid in String format
    List<Attribute> uuidAttrs = entry.getOperationalAttribute(entryuuidAttrType);
    String retrievedUuid = uuidAttrs.get(0).iterator().next().toString();
    assertEquals(retrievedUuid, uuid);


    // Test FakeOperation
    Iterable<FakeOperation> fks = EntryHistorical.generateFakeOperations(entry);
    if (fks.iterator().hasNext())
    {
      FakeOperation fk = fks.iterator().next();
      assertEquals(new FakeOperationComparator().compare(fk, fk), 0);
      assertTrue(new FakeOperationComparator().compare(null, fk) < 0);
      ReplicationMsg generatedMsg = fk.generateMessage();
      if (generatedMsg instanceof LDAPUpdateMsg)
      {
        LDAPUpdateMsg new_name = (LDAPUpdateMsg) generatedMsg;
        assertEquals(new_name.getEntryUUID(), uuid);
      }
    }
  }

  private void testModify(Entry entry, EntryHistorical hist, int date,
      boolean keepChangeResult, Modification mod) throws DirectoryException
  {
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
    entry.applyModifications(mods);
  }

  private void replayModifies(
      Entry entry, EntryHistorical hist, List<Modification> mods, int date)
  {
    InternalClientConnection aConnection =
      InternalClientConnection.getRootConnection();
    CSN t = new CSN(date, 0, 0);

    ModifyOperationBasis modOpBasis =
      new ModifyOperationBasis(aConnection, 1, 1, null, entry.getName(), mods);
    LocalBackendModifyOperation modOp = new LocalBackendModifyOperation(modOpBasis);
    ModifyContext ctx = new ModifyContext(t, "uniqueId");
    modOp.setAttachment(SYNCHROCONTEXT, ctx);

    hist.replayOperation(modOp, entry);
  }

  private List<Modification> replayModify(
      Entry entry, EntryHistorical hist, Modification mod, int date)
  {
    AttributeType historicalAttrType =
      DirectoryServer.getSchema().getAttributeType(
          EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

    InternalClientConnection aConnection =
      InternalClientConnection.getRootConnection();
    CSN t = new CSN(date, 0, 0);

    List<Modification> mods = new ArrayList<>();
    mods.add(mod);

    ModifyOperationBasis modOpBasis =
      new ModifyOperationBasis(aConnection, 1, 1, null, entry.getName(), mods);
    LocalBackendModifyOperation modOp = new LocalBackendModifyOperation(modOpBasis);
    ModifyContext ctx = new ModifyContext(t, "uniqueId");
    modOp.setAttachment(SYNCHROCONTEXT, ctx);

    hist.replayOperation(modOp, entry);
    if (mod.getModificationType() == ModificationType.ADD)
    {
      AddOperationBasis addOpBasis =
        new AddOperationBasis(aConnection, 1, 1, null, entry
          .getName(), entry.getObjectClasses(), entry.getUserAttributes(),
          entry.getOperationalAttributes());
      LocalBackendAddOperation addOp = new LocalBackendAddOperation(addOpBasis);
      testHistorical(addOp);
    }
    else
    {
      testHistoricalAndFake(entry);
    }

    /*
     * Check that the encoding decoding of historical information
     * works  by encoding decoding and checking that the result is the same
     * as the initial value.
     */
    entry.removeAttribute(historicalAttrType);
    entry.addAttribute(hist.encodeAndPurge(), null);
    EntryHistorical hist2 = EntryHistorical.newInstanceFromEntry(entry);
    assertEquals(hist2.encodeAndPurge(), hist.encodeAndPurge());

    return mods;
  }

  private Modification buildMod(String attrName, ModificationType modType,
      String... values)
  {
    Attribute attr;
    if (values.length == 0)
    {
      attr = Attributes.empty(attrName);
    }
    else
    {
      AttributeBuilder builder = new AttributeBuilder(attrName);
      for (String value : values)
      {
        builder.add(value);
      }
      attr = builder.toAttribute();
    }
    return new Modification(modType, attr);
  }

  private Attribute buildSyncHist(String attrName, String... values)
  {
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    for (String value : values)
    {
      builder.add(attrName + value);
    }
    return builder.toAttribute();
  }

  private void assertContainsOnlyValues(Entry entry, String attrName,
      String... expectedValues)
  {
    List<Attribute> attrs = entry.getAttribute(attrName);
    Attribute attr = attrs.get(0);
    assertEquals(expectedValues.length, attr.size());
    for (String value : expectedValues)
    {
      attr.contains(ByteString.valueOf(value));
    }
  }

  private void testHistorical(LocalBackendAddOperation addOp)
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(
          EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = EntryHistorical.getEntryUUID(addOp);

    // Get the op uuid in String format
    List<Attribute> uuidAttrs = addOp.getOperationalAttributes().get(entryuuidAttrType);
    String retrievedUuid = uuidAttrs.get(0).iterator().next().toString();
    assertEquals(retrievedUuid, uuid);
  }

    /**
   * Test that a single replicated modify operation, that contains a
   * modify-add of a value followed by modify-delete of that value
   * is handled properly.
   */
  @Test
  public void addDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));

    // simulate a delete of same value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-add of a value followed by modify-delete of the attribute
   * is handled properly.
   */
  @Test
  public void addDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-add of a value followed by modify-delete
   * of the same value is handled properly.
   */
  @Test
  public void replayAddDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:attrDel");

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // Redo the same operations. This time, we expect them not to be applied.
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:add:aValue",
        ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test we can del an existing value and add a new one, and then replay
   * a del of the same existing value and add of a different new one.
   */
  @Test
  public void replayDelAddDifferent() throws Exception
  {
    Entry entry = initializeEntry();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a delete of same value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate an add of new value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.ADD, "value3"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value3",
        ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of same value in the same operation done at time t2
    testModify(entry, hist, 2, false, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value3",
        ":0000000000000002000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate an add of new value in the same operation done at time t2
    testModify(entry, hist, 2, true, buildMod(DESCRIPTION, ModificationType.ADD, "value4"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value3",
        ":0000000000000002000000000000:del:value1",
        ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), attr);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DESCRIPTION);
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");
    assertEquals(attrs.get(0), builder.toAttribute());
  }

  /**
   * Test we can del an existing value and add a new one, and then replay
   * a del of another existing value and add of the same one.
   */
  @Test
  public void replayDelAddSame() throws Exception
  {
    Entry entry = initializeEntry();

    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");

    List<ByteString> duplicateValues = new LinkedList<>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a delete of a value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.DELETE, "value1"));
    Attribute attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate an add of new value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DESCRIPTION, ModificationType.ADD, "value4"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:add:value4",
        ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of another value in the same operation done at time t2
    testModify(entry, hist, 2, true, buildMod(DESCRIPTION, ModificationType.DELETE, "value2"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1",
        ":0000000000000001000000000000:add:value4",
        ":0000000000000002000000000000:del:value2");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate an add of already added value in the same operation done at time
    // t2
    testModify(entry, hist, 2, false, buildMod(DESCRIPTION, ModificationType.ADD, "value4"));
    attr = buildSyncHist(DESCRIPTION,
        ":0000000000000001000000000000:del:value1",
        ":0000000000000002000000000000:del:value2",
        ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), attr);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DESCRIPTION);
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value3");
    builder.add("value4");
    assertEquals(attrs.get(0), builder.toAttribute());
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-add of a value followed by modify-delete
   * of the attribute is handled properly.
   */
  @Test
  public void replayAddDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // Redo the same operations. This time, we expect them not to be applied.
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "aValue"));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:aValue",
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


    /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of that value
   * is handled properly.
   */
  @Test
  public void replaceDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of same value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of the attribute
   * is handled properly.
   */
  @Test
  public void replaceDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-replace of a value followed by modify-delete
   * of the same value is handled properly.
   */
  @Test
  public void replayReplaceDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute repl = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:repl:aValue");
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000002000000000000:attrDel");

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    assertEquals(hist.encodeAndPurge(), repl);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // Redo the same operations. This time, we expect them not to be applied.
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    assertEquals(hist.encodeAndPurge(), repl);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 2, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-replace of a value followed by modify-delete
   * of the attribute is handled properly.
   */
  @Test
  public void replayReplaceDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    Attribute repl = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:aValue");
    Attribute attrDel = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    assertEquals(hist.encodeAndPurge(), repl);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // Redo the same operations. This time, we expect them not to be applied.
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    assertEquals(hist.encodeAndPurge(), repl);

    // simulate a delete of the attribute in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    assertEquals(hist.encodeAndPurge(), attrDel);

    // The entry should have no value
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of that value,
   * followed by a modify-add of a new value is handled properly.
   */
  @Test
  public void replaceDeleteAddSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    Attribute syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), syncHist);

    // simulate a delete of same value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE, "aValue"));
    syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), syncHist);

    // simulate an add of new value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "NewValue"));
    syncHist = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:NewValue",
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), syncHist);

    assertContainsOnlyValues(entry, DISPLAYNAME, "NewValue");
  }

  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of the attribute,
   * followed by a modify-add of a new value is handled properly.
   */
  @Test
  public void replaceDeleteAttrAddSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.REPLACE, "aValue"));
    Attribute attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate a delete of same value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.DELETE));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    // simulate an add of new value in the same operation done at time t1
    testModify(entry, hist, 1, true, buildMod(DISPLAYNAME, ModificationType.ADD, "NewValue"));
    attr = buildSyncHist(DISPLAYNAME,
        ":0000000000000001000000000000:add:NewValue",
        ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), attr);

    assertContainsOnlyValues(entry, DISPLAYNAME, "NewValue");
  }

}
