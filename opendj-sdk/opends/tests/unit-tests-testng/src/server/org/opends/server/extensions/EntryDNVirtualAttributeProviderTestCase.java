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
package org.opends.server.extensions;



import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the entryDN virtual attribute provider.
 */
public class EntryDNVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  // The attribute type for the entryDN attribute.
  private AttributeType entryDNType;



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

    entryDNType = DirectoryServer.getAttributeType("entrydn", false);
    assertNotNull(entryDNType);
  }



  /**
   * Retrieves a set of entry DNs for use in testing the entryDN virtual
   * attribute.
   *
   * @return  A set of entry DNs for use in testing the entryDN virtual
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
      new Object[] { DN.decode("o=test") },
      new Object[] { DN.decode("dc=example,dc=com") },
      new Object[] { DN.decode("cn=config") },
      new Object[] { DN.decode("cn=schema") },
      new Object[] { DN.decode("cn=tasks") },
      new Object[] { DN.decode("cn=monitor") },
      new Object[] { DN.decode("cn=backups") }
    };
  }



  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the entryDN operational attribute with the
   * correct value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testGetEntry(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryDNType));

    List<Attribute> attrList = e.getAttribute(entryDNType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(a.hasValue());
      assertEquals(a.getValues().size(), 1);
      assertTrue(a.hasValue(new AttributeValue(entryDNType,
                                               entryDN.toNormalizedString())));
    }
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is not included when the list of attributes requested
   * is empty (defaulting to all user attributes).
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEmptyAttrs(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT, filter);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is not included when the list of requested attributes
   * is "1.1", meaning no attributes.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchNoAttrs(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

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
    assertFalse(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is not included when all user attributes are
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
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

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
    assertFalse(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is included when all operational attributes are
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
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

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
    assertTrue(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is included when the entryDN attribute is
   * specifically requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryDNAttr(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entrydn");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is not included when it is not in the list of
   * attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludeEntryDNAttr(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

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
    assertFalse(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is included when the entryDN attribute is
   * specifically requested and the entryDN attribute is used in the search
   * filter with a matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryDNAttrInMatchingFilter(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(entryDN=" + entryDN.toString() +
                                             ")");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entrydn");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the entryDN attribute is used in the search
   * filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryDNAttrInNonMatchingFilter(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(entryDN=cn=Not A Match)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entrydn");

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
   * the entryDN attribute is not included when the entryDN attribute is
   * specifically requested and the real attributes only control is included in
   * the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryDNAttrRealAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entrydn");

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
    assertFalse(e.hasAttribute(entryDNType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the entryDN attribute is included when the entryDN attribute is
   * specifically requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEntryDNAttrVirtualAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("entrydn");

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
    assertTrue(e.hasAttribute(entryDNType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(new AttributeValue(entryDNType, "o=test")));
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule,
                                 new AttributeValue(entryDNType, "o=test")));
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasValue(entry, rule,
                     new AttributeValue(entryDNType, "o=not test")));
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryDNType, "o=test"));

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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(new AttributeValue(entryDNType, "o=not test"));

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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(new AttributeValue(entryDNType, "o=test"));
    values.add(new AttributeValue(entryDNType, "o=not test"));
    values.add(new AttributeValue(entryDNType, "o=not test either"));

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
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(new AttributeValue(entryDNType, "o=not test"));
    values.add(new AttributeValue(entryDNType, "o=not test either"));
    values.add(new AttributeValue(entryDNType, "o=still not test"));

    assertFalse(provider.hasAnyValue(entry, rule, values));
  }



  /**
   * Tests the {@code matchesSubstring} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMatchesSubstring()
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedList<ByteString> subAny = new LinkedList<ByteString>();
    subAny.add(ByteStringFactory.create("="));

    assertEquals(provider.matchesSubstring(entry, rule, null, subAny, null),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code greaterThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGreaterThanOrEqualTo()
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(entryDNType, "o=test2");
    assertEquals(provider.greaterThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code lessThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLessThanOrEqualTo()
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(entryDNType, "o=test2");
    assertEquals(provider.lessThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code approximatelyEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testApproximatelyEqualTo()
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(entryDNType, "o=test2");
    assertEquals(provider.approximatelyEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Retrieves a set of filters for use in testing searchability.  The returned
   * data will actually include three elements:
   * <OL>
   *   <LI>The string representation of the search filter to use</LI>
   *   <LI>An indication of whether it should be searchable</LI>
   *   <LI>An indication of whether a minimal o=test entry should match</LI>
   * </OL>
   *
   * @return  A set of filters for use in testing searchability.
   */
  @DataProvider(name = "testFilters")
  public Object[][] getTestFilters()
  {
    return new Object[][]
    {
      new Object[] { "(entryDN=o=test)", true, true },
      new Object[] { "(entryDN=o=not test)", true, false },
      new Object[] { "(o=test)", false, false },
      new Object[] { "(entryDN=*)", false, false },
      new Object[] { "(&(objectClass=*)(entryDN=o=test))", true, true },
      new Object[] { "(&(entryDN=o=test)(entryDN=o=not test))", true, false },
      new Object[] { "(|(objectClass=*)(entryDN=o=test))", false, false },
      new Object[] { "(|(entryDN=o=test)(entryDN=o=not test))", true, true },
      new Object[] { "(&(|(entryDN=o=test)(entryDN=o=not test))" +
                       "(&(objectClass=top)(|(objectClass=organization)" +
                                            "(objectClass=domain)))" +
                       "(|(o=test)(o=not test)))", true, true }
    };
  }



  /**
   * Tests the {@code isSearchable} method with the provided information.
   *
   * @param  filterString  The string representation of the search filter to use
   *                       for the test.
   * @param  isSearchable  Indicates whether a search with the given filter
   *                       should be considered searchable.
   * @param  shouldMatch   Indicates whether the provided filter should match
   *                       a minimal o=test entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testFilters")
  public void testIsSearchable(String filterString, boolean isSearchable,
                               boolean shouldMatch)
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    SearchFilter filter = SearchFilter.createFilterFromString(filterString);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), null,
                                     DN.decode("o=test"),
                                     SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, null, null);

    assertEquals(provider.isSearchable(rule, searchOperation), isSearchable);
  }



  /**
   * Tests the {@code processSearch} method with the provided information.
   *
   * @param  filterString  The string representation of the search filter to use
   *                       for the test.
   * @param  isSearchable  Indicates whether a search with the given filter
   *                       should be considered searchable.
   * @param  shouldMatch   Indicates whether the provided filter should match
   *                       a minimal o=test entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testFilters")
  public void testProcessSearch(String filterString, boolean isSearchable,
                                boolean shouldMatch)
         throws Exception
  {
    if (! isSearchable)
    {
      return;
    }

    TestCaseUtils.initializeTestBackend(true);

    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    SearchFilter filter = SearchFilter.createFilterFromString(filterString);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), null,
                                     DN.decode("o=test"),
                                     SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, null, null);
    LocalBackendSearchOperation localSearch =
      new LocalBackendSearchOperation(searchOperation);
    
    provider.processSearch(rule, localSearch);

    if (shouldMatch)
    {
      assertEquals(searchOperation.getSearchEntries().size(), 1);
    }
    else
    {
      assertEquals(searchOperation.getSearchEntries().size(), 0);
    }
  }
}

