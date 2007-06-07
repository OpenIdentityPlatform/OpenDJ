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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.VirtualAttributeRule;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * A set of test cases for the entryUUID virtual attribute provider.
 */
public class EntryUUIDVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  // The attribute type for the entryUUID attribute.
  private AttributeType entryUUIDType;



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    entryUUIDType = DirectoryServer.getAttributeType("entryuuid", false);
    assertNotNull(entryUUIDType);
  }



  /**
   * Retrieves a set of entry DNs for use in testing the entryUUID virtual
   * attribute.
   *
   * @return  A set of entry DNs for use in testing the entryUUID virtual
   *          attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testEntryDNs")
  public Object[][] getTestEntryDNs()
         throws Exception
  {
    return new Object[][]
    {
      new Object[] { DN.decode("") },
      new Object[] { DN.decode("cn=config") },
      new Object[] { DN.decode("cn=schema") },
      new Object[] { DN.decode("cn=tasks") },
      new Object[] { DN.decode("cn=monitor") },
      new Object[] { DN.decode("cn=backups") }
    };
  }



  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the entryUUID operational attribute
   * with the correct value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testGetEntry(DN entryDN)
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(
                             getBytes(entryDN.toNormalizedString())).toString();

    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));

    List<Attribute> attrList = e.getAttribute(entryUUIDType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(a.hasValue());
      assertEquals(a.getValues().size(), 1);
      assertTrue(a.hasValue(new AttributeValue(entryUUIDType, uuidString)));
    }
  }



  /**
   * Tests the {@code getEntry} method for a user entry that should have a real
   * entryUUID value added by a plugin.  In this case, the entryUUID value
   * should be a random UUID and not one generated based on the entry's DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testGetUserEntry()
         throws Exception
  {
    String uuidString =
         UUID.nameUUIDFromBytes(getBytes("dc=example,dc=com")).toString();

    TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");

    Entry e = TestCaseUtils.makeEntry(
      "dn: dc=example,dc=com",
      "objectClass: top",
      "objectClass: domain",
      "dc: example");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    e = DirectoryServer.getEntry(e.getDN());
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));

    List<Attribute> attrList = e.getAttribute(entryUUIDType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(a.hasValue());
      assertEquals(a.getValues().size(), 1);
      assertFalse(a.hasValue(new AttributeValue(entryUUIDType, uuidString)));
    }
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is not included when the list of attributes
   * requested is empty (defaulting to all user attributes).
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEmptyAttrs(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT, filter);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is not included when the list of requested
   * attributes is "1.1", meaning no attributes.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchNoAttrs(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("1.1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is not included when all user attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllUserAttrs(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("*");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is included when all operational attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllOperationalAttrs(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("+");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is included when that attribute is specifically
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryUUIDAttr(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entryuuid");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is not included when it is not in the list of
   * attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludeEntryUUIDAttr(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("objectClass");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is included when that attribute is specifically
   * requested and the entryUUID attribute is used in the search filter with a
   * matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryUUIDAttrInMatchingFilter(DN entryDN)
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(
                             getBytes(entryDN.toNormalizedString())).toString();

    SearchFilter filter =
         SearchFilter.createFilterFromString("(entryUUID=" + uuidString + ")");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entryuuid");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the entryUUID attribute is used in the search
   * filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryUUIDAttrInNonMatchingFilter(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(entryUUID=wrong)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entryuuid");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 0);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is not included when that attribute is specifically
   * requested and the real attributes only control is included in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryUUIDAttrRealAttrsOnly(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entryuuid");

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new Control(OID_REAL_ATTRS_ONLY, true));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), requestControls,
                                     entryDN, SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, attrList, null);
    searchOperation.run();
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryUUIDType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryUUID attribute is included when that attribute is specifically
   * requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryUUIDAttrVirtualAttrsOnly(DN entryDN)
         throws Exception
  {
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entryuuid");

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new Control(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), requestControls,
                                     entryDN, SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, attrList, null);
    searchOperation.run();
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();
    assertFalse(provider.isMultiValued());
  }



  /**
   * Tests the {@code getValues} method for an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValues()
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(getBytes("o=test")).toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(new AttributeValue(entryUUIDType, uuidString)));
  }



  /**
   * Tests the {@code hasValue} method variant that doesn't take a specific
   * value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValue()
         throws Exception
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule));
  }



  /**
   * Tests the {@code hasValue} method variant that takes a specific value when
   * the provided value is a match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasMatchingValue()
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(getBytes("o=test")).toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule,
                                 new AttributeValue(entryUUIDType,
                                                    uuidString)));
  }



  /**
   * Tests the {@code hasValue} method variant that takes a specific value when
   * the provided value is not a match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasNonMatchingValue()
         throws Exception
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasValue(entry, rule,
                     new AttributeValue(entryUUIDType, "wrong")));
  }



  /**
   * Tests the {@code hasAnyValue} method with an empty set of values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueEmptySet()
         throws Exception
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasAnyValue(entry, rule,
                                     Collections.<AttributeValue>emptySet()));
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing only
   * the correct value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueOnlyCorrect()
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(getBytes("o=test")).toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryUUIDType, uuidString));

    assertTrue(provider.hasAnyValue(entry, rule, values));
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing only
   * an incorrect value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueOnlyIncorrect()
         throws Exception
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryUUIDType, "wrong"));

    assertFalse(provider.hasAnyValue(entry, rule, values));
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing the
   * correct value as well as multiple incorrect values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueIncludesCorrect()
         throws Exception
  {
    String uuidString = UUID.nameUUIDFromBytes(getBytes("o=test")).toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(new AttributeValue(entryUUIDType, uuidString));
    values.add(new AttributeValue(entryUUIDType, "wrong"));
    values.add(new AttributeValue(entryUUIDType, "also wrong"));

    assertTrue(provider.hasAnyValue(entry, rule, values));
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of multiple values, none of
   * which are correct.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueMissingCorrect()
         throws Exception
  {
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryUUIDType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(new AttributeValue(entryUUIDType, "wrong"));
    values.add(new AttributeValue(entryUUIDType, "also wrong"));
    values.add(new AttributeValue(entryUUIDType, "still wrong"));

    assertFalse(provider.hasAnyValue(entry, rule, values));
  }
}

