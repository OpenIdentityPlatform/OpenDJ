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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import static org.opends.server.replication.protocol.OperationContext.*;

import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import static org.opends.server.TestCaseUtils.*;

/*
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

public class ModifyConflictTest
    extends ReplicationTestCase
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
  @Test()
  public void replaceAndAdd() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:repl:init value");
    Attribute repl = builder.toAttribute();

    /*
     * simulate a modify-replace done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.REPLACE,
               "init value", 10, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
               "older value", 1, false);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
               "older value", 2, false);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "new value",
               11, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:repl:init value");
    builder.add(DESCRIPTION + ":000000000000000b000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":000000000000000a000000000000:repl:init value");
    Attribute repl = builder.toAttribute();

    /*
     * simulate a modify-replace done at time t10
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
               "init value", 10, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
               "older value", 1, false);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
               "older value", 2, false);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * Now simulate an add at a later date that the previous replace.
     * conflict resolution should remove it
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD, "new value",
               11, false);
    assertEquals(hist.encodeAndPurge(), repl);

    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    Attribute attr = attrs.get(0);
    assertEquals(1, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "init value"));
  }

  /**
   * Test that replace with null value is correctly seen as a delete
   * by doing first a replace with null, then add at at previous time
   * then check that the add was ignored
   */
  @Test()
  public void replaceWithNull() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a replace with null done at time t3
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE, null, 3,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous replace. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "older value", 1, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "older value", 2, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD, "new value",
        4, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000004000000000000:add:new value");
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a modify-add done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "init value", 10, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.REPLACE,
        "older value", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should remove it. (a second time to make
     * sure...)
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.REPLACE,
        "older value", 2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a replace at a later date that the previous replace.
     * conflict resolution should keep it
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.REPLACE,
        "new value", 11, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000b000000000000:repl:new value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a modify-add done at time 2
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "init value", 2, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a replace at an earlier date that the previous replace
     * conflict resolution should keep it.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "older value", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:older value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    Attribute attr = attrs.get(0);
    assertEquals(1, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "older value"));

    /*
     * Now simulate a replace at a later date.
     * Conflict resolution should keept it.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "newer value", 3, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:repl:newer value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    attrs = entry.getAttribute(DISPLAYNAME);
    attr = attrs.get(0);
    assertEquals(1, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "newer value"));

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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a delete of the whole description attribute done at time
     * t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, null, 10,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "older value", 1, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "older value", 2, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "new value",
        11, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000b000000000000:add:new value");
    builder.add(DESCRIPTION + ":000000000000000a000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that conflict between a delete-attribute value and
   * add attribute-values from 2 different servers are handled correctly.
   * This test was created to reproduce issue 3392.
   */
  @Test()
  public void delValueAndAddValue() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);


    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the description attribute value "value1"
     * done at time t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1",
        1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add of "value3" at time t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value3", 2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a delete of value "value1" at time t3
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1",
        3, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add of "value4" at time t4
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value4", 4, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000004000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that conflict between several delete-attribute value and
   * are handled correctly.
   * This test was created to reproduce and fix issue 3397.
   */
  @Test()
  public void delValueAndDelValue() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    //
    // Create an attribute with values value1, value2, value3 and value4 and
    // add this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);


    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the description attribute values
     *  "value1" and "value2" done at time t1
     */
    testModify(entry, hist, DESCRIPTION,
        buildModWith2Vals(DESCRIPTION, ModificationType.DELETE,
            "value1", "value2"),
            1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value2");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the description attribute values
     *  "value2" and "value3" done at time t1
     */
    testModify(entry, hist, DESCRIPTION,
        buildModWith2Vals(DESCRIPTION, ModificationType.DELETE,
            "value2", "value3"),
            2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value2");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value3");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    // Check that entry now only contains 1 attribute value  : "value1"
    List<Attribute> attrs = entry.getAttribute(DESCRIPTION);
    Attribute attr = attrs.get(0);
    assertEquals(1, attr.size());
  }

  /**
   * Test that conflict between a delete attribute and a delete
   * value on a single valued attribute works correctly.
   * This test was created to reproduce and fix issue 3399.
   */
  @Test()
  public void delAttributeAndDelValueSingle() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    //
    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    //
    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    Attribute attribute = Attributes.create(EMPLOYEENUMBER, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of attribute employeenumber.
     */
    testModify(
        entry, hist, EMPLOYEENUMBER, ModificationType.DELETE, null, 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(EMPLOYEENUMBER + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * now simulate a delete of value "value1"
     */
    testModify(
        entry, hist, EMPLOYEENUMBER, ModificationType.DELETE,
        "value1", 2, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(EMPLOYEENUMBER + ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that conflict between a delete attribute and a delete
   * value on a single valued attribute works correctly.
   * This test was created to reproduce and fix issue 3399.
   */
  @Test()
  public void delValueAndDelAttributeSingle() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    Attribute attribute = Attributes.create(EMPLOYEENUMBER, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * now simulate a delete of value "value1"
     */
    testModify(
        entry, hist, EMPLOYEENUMBER, ModificationType.DELETE,
        "value1", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(EMPLOYEENUMBER + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of attribute employeenumber.
     */
    testModify(
        entry, hist, EMPLOYEENUMBER, ModificationType.DELETE, null, 2, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(EMPLOYEENUMBER + ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that conflict between a delete-attribute value and
   * add attribute-values from 2 different servers
   * and replayed in the non-natural order are handled correctly.
   * This test was created to reproduce issue 3392.
   */
  @Test()
  public void delValueAndAddValueDisordered() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the description attribute value "value1"
     * done at time t3
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1",
        3, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add of "value3" at time t4
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value3", 4, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000004000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a delete of value "value1" at time t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1",
        1, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000004000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add of "value4" at time t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value4", 2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000003000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000004000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    Attribute attribute = Attributes.create(DISPLAYNAME, "value1");
    entry.addAttribute(attribute, duplicateValues);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of the whole description attribute done at time
     * t2
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 3,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "older value", 1, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at an earlier date that the previous delete. The
     * conflict resolution should detect that this add must be ignored. (a
     * second time to make sure that historical information is kept...)
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "older value", 2, false);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate an add at a later date that the previous delete.
     * conflict resolution should keep it
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD, "new value",
        4, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:attrDel");
    builder.add(DISPLAYNAME + ":0000000000000004000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000004000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a delete of the whole description attribute done at time
     * t4
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, null, 4,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.REPLACE,
        "new value", 3, false);
    assertEquals(hist.encodeAndPurge(), attrDel);
  }

  /**
   * Test that conflict between a modify-replace and a a modify-delete
   * with some attribute values is resolved correctly.
   * This test has been created to reproduce Issue 3397.
   */
  @Test()
  public void replaceAndDelete() throws Exception
  {
    // create an entry to use with conflicts tests.
    Entry entry = initializeEntry();

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
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
    testModify(entry, hist, DESCRIPTION, mod, 1, true);

    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:repl:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value2");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value3");
    assertEquals(hist.encodeAndPurge(),builder.toAttribute());

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

    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:repl:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value2");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(),builder.toAttribute());
  }

  /**
   * Test that conflict between a modify-replace and a a modify-delete
   * with some attribute values is resolved correctly when they are replayed
   * disorderly.
   * This test has been created to reproduce Issue 3397.
   */
  @Test()
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

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");
    builder.add("value4");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    // simulate a DELETE of the attribute values : value3 and value4
    // at time t2.
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

    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(),builder.toAttribute());

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

    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:repl:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value2");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value4");
    assertEquals(hist.encodeAndPurge(),builder.toAttribute());
  }

  /**
   * Test that conflict between a modify-delete-attribute and modify-add
   * for multi-valued attributes are handled correctly.
   */
  @Test()
  public void deleteAndReplaceSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // Create a single valued attribute with value : "value1"
    // add this attribute to the entry.
    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    Attribute attribute = Attributes.create(DISPLAYNAME, "value1");
    entry.addAttribute(attribute, duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000004000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a delete of the whole description attribute done at time
     * t4
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 4,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate a replace at an earlier date that the previous delete. The
     * conflict resolution should detect that this replace must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "new value", 3, false);
    assertEquals(hist.encodeAndPurge(), attrDel);
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
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "init value", 10, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "older value", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:older value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add with a value already existing.
     * The conflict resolution should remove this add.
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "init value", 13, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:older value");
    builder.add(DESCRIPTION + ":000000000000000d000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add at a later date that the previous add. conflict
     * resolution should keep it
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "new value",
        14, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:older value");
    builder.add(DESCRIPTION + ":000000000000000d000000000000:add:init value");
    builder.add(DESCRIPTION + ":000000000000000e000000000000:add:new value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that a DEL and ADD of the same attribute same value done
   * in a single operation correctly results in the value being kept.
   *
   *  This is not a conflict in the strict definition but does exert
   *  the conflict resolution code.
   */
  @Test()
  public void delAndAddSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "init value", 10, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a del and a add in the same operation
     */

    Attribute attr = Attributes.create(DESCRIPTION, "init value");
    Modification mod1 = new Modification(ModificationType.DELETE, attr);

    attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod2 = new Modification(ModificationType.ADD, attr);

    List<Modification> mods = new LinkedList<Modification>();
    mods.add(mod1);
    mods.add(mod2);

    replayModifies(entry, hist, mods, 11);
    assertEquals(mods.size(), 2,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(0), mod1,
      "DEL and ADD of the same attribute same value was not correct");
    assertEquals(mods.get(1), mod2,
      "DEL and ADD of the same attribute same value was not correct");
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000b000000000000:add:Init Value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
  }

  /**
   * Test that a DEL of one value and REPLACE with no value, of the same
   * attribute done in a single operation correctly results in the value
   * being kept.
   *
   * This is not a conflict in the strict definition but does exert
   * the conflict resolution code.
   */
  @Test()
  public void delAndReplaceSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000c000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "init value", 10, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a add of the description attribute done at time t10
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "second value", 11, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000a000000000000:add:init value");
    builder.add(DESCRIPTION + ":000000000000000b000000000000:add:second value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate a delete of one value and a replace with no value
     * in the same operation
     */

    Attribute attr = Attributes.create(DESCRIPTION, "init value");
    Modification mod1 = new Modification(ModificationType.DELETE, attr);

    Attribute attr2 = Attributes.empty(DESCRIPTION);
    Modification mod2 = new Modification(ModificationType.REPLACE, attr2);

    List<Modification> mods = new LinkedList<Modification>();
    mods.add(mod1);
    mods.add(mod2);

    List<Modification> mods2 = new LinkedList<Modification>(mods);
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
  @Test()
  public void addAndDelSameOp() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Now simulate a del and a add in the same operation
     */

    Attribute attr = Attributes.create(DESCRIPTION, "init value");
    Modification mod1 = new Modification(ModificationType.ADD, attr);

    attr = Attributes.create(DESCRIPTION, "Init Value");
    Modification mod2 = new Modification(ModificationType.DELETE, attr);

    List<Modification> mods = new LinkedList<Modification>();
    mods.add(mod1);
    mods.add(mod2);

    replayModifies(entry, hist, mods, 11);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":000000000000000b000000000000:del:Init Value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());
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
  @Test()
  public void addAndAddSameValues() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a add of the description attribute done at time 1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value1", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of the description attribute values
     *  "value1" and "value2" done at time 2
     */
    testModify(entry, hist, DESCRIPTION,
               buildModWith2Vals(DESCRIPTION, ModificationType.ADD, "value1",
                                 "value2"),
               2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value1");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value2");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    // Check that entry now only contains the 2 attribute values
    List<Attribute> attrs = entry.getAttribute(DESCRIPTION);
    Attribute attr = attrs.get(0);
    assertEquals(2, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "value1"));
    attr.contains(AttributeValues.create(attr.getAttributeType(), "value2"));


    // do the same as before but in reverse order
    entry = initializeEntry();

    // load historical from the entry
    hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate an add of the description attribute values
     *  "value1" and "value2" done at time 1
     */
    testModify(entry, hist, DESCRIPTION,
               buildModWith2Vals(DESCRIPTION, ModificationType.ADD, "value1",
                                 "value2"),
               1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value2");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a add of the description attribute done at time 1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD,
        "value1", 2, false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value2");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    // Check that entry now only contains the 2 attribute values
    attrs = entry.getAttribute(DESCRIPTION);
    attr = attrs.get(0);
    assertEquals(2, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "value1"));
    attr.contains(AttributeValues.create(attr.getAttributeType(), "value2"));
  }

  /**
   * Test that conflict between a modify-add and modify-add for
   * single-valued attributes are handled correctly.
   */
  @Test()
  public void addAndAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:older value");
    Attribute olderValue = builder.toAttribute();

    /*
     * simulate a add of the displayName attribute done at time t10
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "init value", 10, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":000000000000000a000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * Now simulate an add at an earlier date that the previous add. The
     * conflict resolution should detect that this add must be kept.
     * and that the previous value must be discarded, and therefore
     * turn the add into a replace.
     */
    Modification mod = buildMod(DISPLAYNAME, ModificationType.ADD,
        "older value");
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
  @Test()
  public void addDelAddSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000003000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * simulate a add of the displayName attribute done at time t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "init value", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:init value");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a del of the displayName attribute done at time t3
     * this should be processed normally
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE,
        "init value", 3, true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Now simulate another add, that would come from another master
     * and done at time t2 (between t1 and t2)
     * This add should not be processed.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "second value", 2, false);
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
  @Test()
  public void addAddDelSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:first value");
    Attribute firstValue = builder.toAttribute();

    /*
     * simulate a add of the displayName attribute done at time t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "first value", 1, true);
    assertEquals(hist.encodeAndPurge(), firstValue);

    /*
     * simulate a add of the displayName attribute done at time t2
     * with a second value. This should not work because there is already
     * a value
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "second value", 2, false);
    assertEquals(hist.encodeAndPurge(), firstValue);

    /*
     * Now simulate a delete of the second value.
     * The delete should not be accepted because it is done on a value
     * that did not get into the entry.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE,
        "second value", 2, false);
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
    assertTrue(newMod.getModificationType().equals(modType));
    AttributeValue val = newMod.getAttribute().iterator().next();
    assertEquals(val.getValue().toString(), value);
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
    DN dn = DN.decode(TEST_ROOT_DN_STRING);
    Map<ObjectClass, String> objectClasses = new HashMap<ObjectClass, String>();
    ObjectClass org = DirectoryServer.getObjectClass(ORGANIZATION);
    objectClasses.put(org, ORGANIZATION);

    /*
     * start with a new entry with an empty attribute
     */
    Entry entry = new Entry(dn, objectClasses, null, null);

    // Construct a new random UUID. and add it into the entry
    UUID uuid = UUID.randomUUID();

    // Create the att values list
    ArrayList<Attribute> uuidList = new ArrayList<Attribute>(1);
    Attribute uuidAttr = Attributes.create(entryuuidAttrType, uuid
        .toString());
    uuidList.add(uuidAttr);

    /*
     * Add the uuid in the entry
     */
    Map<AttributeType, List<Attribute>> operationalAttributes = entry
        .getOperationalAttributes();

    operationalAttributes.put(entryuuidAttrType, uuidList);
    return entry;
  }

  /*
   * helper function.
   */
  private void testHistoricalAndFake(
      EntryHistorical hist, Entry entry)
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = EntryHistorical.getEntryUUID(entry);

    // Get the Entry uuid in String format
    List<Attribute> uuidAttrs = entry
        .getOperationalAttribute(entryuuidAttrType);
    uuidAttrs.get(0).iterator().next().toString();

    if (uuidAttrs != null)
    {
      if (uuidAttrs.size() > 0)
      {
        Attribute att = uuidAttrs.get(0);
        String retrievedUuid = (att.iterator().next()).toString();
        assertTrue(retrievedUuid.equals(uuid));
      }
    }


    // Test FakeOperation
    try
    {
      Iterable<FakeOperation> fks = EntryHistorical.generateFakeOperations(entry);
      if (fks.iterator().hasNext())
      {
        FakeOperation fk = fks.iterator().next();
        assertTrue(new FakeOperationComparator().compare(fk, fk) == 0);
        assertTrue(new FakeOperationComparator().compare(null , fk) < 0);
        ReplicationMsg generatedMsg = fk.generateMessage() ;
        if (generatedMsg instanceof LDAPUpdateMsg)
        {
          LDAPUpdateMsg new_name = (LDAPUpdateMsg) generatedMsg;
          assertEquals(new_name.getEntryUUID(),uuid);

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
      EntryHistorical hist, String attrName,
      ModificationType modType, String value,
      int date, boolean keepChangeResult) throws DirectoryException
  {
    Modification mod = buildMod(attrName, modType, value);
    testModify(entry, hist, attrName, mod, date, keepChangeResult);
  }

  /**
   *
   */
  private void testModify(Entry entry,
      EntryHistorical hist, String attrName, Modification mod,
      int date, boolean keepChangeResult) throws DirectoryException
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

  /**
   *
   */

  private void replayModifies(
      Entry entry, EntryHistorical hist, List<Modification> mods, int date)
  {
    InternalClientConnection aConnection =
      InternalClientConnection.getRootConnection();
    ChangeNumber t = new ChangeNumber(date, 0, 0);

    ModifyOperationBasis modOpBasis =
      new ModifyOperationBasis(aConnection, 1, 1, null, entry.getDN(), mods);
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
    ChangeNumber t = new ChangeNumber(date, 0, 0);

    List<Modification> mods = new ArrayList<Modification>();
    mods.add(mod);

    ModifyOperationBasis modOpBasis =
      new ModifyOperationBasis(aConnection, 1, 1, null, entry.getDN(), mods);
    LocalBackendModifyOperation modOp = new LocalBackendModifyOperation(modOpBasis);
    ModifyContext ctx = new ModifyContext(t, "uniqueId");
    modOp.setAttachment(SYNCHROCONTEXT, ctx);

    hist.replayOperation(modOp, entry);
    if (mod.getModificationType() == ModificationType.ADD)
    {
      AddOperationBasis addOpBasis =
        new AddOperationBasis(aConnection, 1, 1, null, entry
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
    entry.removeAttribute(historicalAttrType);
    entry.addAttribute(hist.encodeAndPurge(), null);
    EntryHistorical hist2 = EntryHistorical.newInstanceFromEntry(entry);
    assertEquals(hist2.encodeAndPurge(), hist.encodeAndPurge());

    return mods;
  }

  private Modification buildMod(
      String attrName, ModificationType modType, String value)
  {
    /* create AttributeType that will be used for this test */
    Attribute attr;
    if (value != null) {
      attr = Attributes.create(attrName, value);
    } else {
      attr = Attributes.empty(attrName);
    }
    Modification mod = new Modification(modType, attr);

    return mod;
  }

  private Modification buildModWith2Vals(
      String attrName, ModificationType modType, String value1,  String value2)
  {
    AttributeBuilder builder = new AttributeBuilder(attrName);
    builder.add(value1);
    builder.add(value2);

    Modification mod = new Modification(modType, builder.toAttribute());
    return mod;

  }

  /**
   *
   */
  private void testHistorical(
      EntryHistorical hist, LocalBackendAddOperation addOp)
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(
          EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);

    // Get the historical uuid associated to the entry
    // (the one that needs to be tested)
    String uuid = EntryHistorical.getEntryUUID(addOp);

    // Get the op uuid in String format
    List<Attribute> uuidAttrs = addOp.getOperationalAttributes().get(
        entryuuidAttrType);
    uuidAttrs.get(0).iterator().next().toString();

    if (uuidAttrs != null)
    {
      if (uuidAttrs.size() > 0)
      {
        Attribute att = uuidAttrs.get(0);
        String retrievedUuid = (att.iterator().next()).toString();
        assertTrue(retrievedUuid.equals(uuid));
      }
    }
  }

    /**
   * Test that a single replicated modify operation, that contains a
   * modify-add of a value followed by modify-delete of that value
   * is handled properly.
   */
  @Test()
  public void addDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 1, true);

    /*
     * simulate a delete of same value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 1,
        true);

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-add of a value followed by modify-delete of the attribute
   * is handled properly.
   */
  @Test()
  public void addDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-add of a value followed by modify-delete
   * of the same value is handled properly.
   */
  @Test()
  public void replayAddDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 2,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Redo the same operations. This time, we expect them not to be applied.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 2, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:add:aValue");
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 2,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test we can del an existing value and add a new one, and then replay
   * a del of the same existing value and add of a different new one.
   */
  @Test()
  public void replayDelAddDifferent() throws Exception
  {
    Entry entry = initializeEntry();

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of same value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of new value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "value3", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of same value in the same operation done at time
     * t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1", 2,
        false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of new value in the same operation done at time
     * t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "value4", 2,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value3");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
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
  @Test()
  public void replayDelAddSame() throws Exception
  {
    Entry entry = initializeEntry();

    //
    // Create description with values value1 and value2 and add
    // this attribute to the entry.
    //
    AttributeBuilder builder = new AttributeBuilder(DESCRIPTION);
    builder.add("value1");
    builder.add("value2");
    builder.add("value3");

    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    entry.addAttribute(builder.toAttribute(), duplicateValues);

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * simulate a delete of a value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value1", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of new value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "value4", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value4");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of another value in the same operation done at time
     * t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.DELETE, "value2", 2,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000001000000000000:add:value4");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value2");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of already added value in the same operation done at time
     * t2
     */
    testModify(entry, hist, DESCRIPTION, ModificationType.ADD, "value4", 2,
        false);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DESCRIPTION + ":0000000000000001000000000000:del:value1");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:del:value2");
    builder.add(DESCRIPTION + ":0000000000000002000000000000:add:value4");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
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
  @Test()
  public void replayAddDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Redo the same operations. This time, we expect them not to be applied.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD,
        "aValue", 1, true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:aValue");
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


    /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of that value
   * is handled properly.
   */
  @Test()
  public void replaceDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of same value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of the attribute
   * is handled properly.
   */
  @Test()
  public void replaceDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-replace of a value followed by modify-delete
   * of the same value is handled properly.
   */
  @Test()
  public void replayReplaceDeleteSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:repl:aValue");
    Attribute repl = builder.toAttribute();
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000002000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 2, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 2,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Redo the same operations. This time, we expect them not to be applied.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 2, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 2,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }

  /**
   * Test that the replay of a single replicated modify operation,
   * that contains a modify-replace of a value followed by modify-delete
   * of the attribute is handled properly.
   */
  @Test()
  public void replayReplaceDeleteAttrSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:aValue");
    Attribute repl = builder.toAttribute();
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    Attribute attrDel = builder.toAttribute();

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /*
     * Redo the same operations. This time, we expect them not to be applied.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    assertEquals(hist.encodeAndPurge(), repl);

    /*
     * simulate a delete of the attribute in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    assertEquals(hist.encodeAndPurge(), attrDel);

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    assertNull(attrs);
  }


  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of that value,
   * followed by a modify-add of a new value is handled properly.
   */
  @Test()
  public void replaceDeleteAddSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of same value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, "aValue", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of new value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD, "NewValue", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:NewValue");
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have one value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    Attribute attr = attrs.get(0);
    assertEquals(1, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "NewValue"));
  }

  /**
   * Test that a single replicated modify operation, that contains a
   * modify-replace of a value followed by modify-delete of the attribute,
   * followed by a modify-add of a new value is handled properly.
   */
  @Test()
  public void replaceDeleteAttrAddSameOpSingle() throws Exception
  {
    Entry entry = initializeEntry();

    // load historical from the entry
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);

    /*
     * Add at time t1 that the previous delete. The
     * conflict resolution should detect that this add must be ignored.
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.REPLACE,
        "aValue", 1, true);
    AttributeBuilder builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:repl:aValue");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate a delete of same value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.DELETE, null, 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /*
     * simulate an add of new value in the same operation done at time
     * t1
     */
    testModify(entry, hist, DISPLAYNAME, ModificationType.ADD, "NewValue", 1,
        true);
    builder = new AttributeBuilder(SYNCHIST);
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:add:NewValue");
    builder.add(DISPLAYNAME + ":0000000000000001000000000000:attrDel");
    assertEquals(hist.encodeAndPurge(), builder.toAttribute());

    /* The entry should have no value */
    List<Attribute> attrs = entry.getAttribute(DISPLAYNAME);
    Attribute attr = attrs.get(0);
    assertEquals(1, attr.size());
    attr.contains(AttributeValues.create(attr.getAttributeType(), "NewValue"));
  }


}
