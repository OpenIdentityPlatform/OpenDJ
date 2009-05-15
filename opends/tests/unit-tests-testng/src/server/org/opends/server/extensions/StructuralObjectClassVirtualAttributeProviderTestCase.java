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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.VirtualAttributeRule;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * A set of test cases for the structural object class virtual attribute
 * provider.
 */
public class StructuralObjectClassVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  // The attribute type for the structuralobjectclass attribute.
  private AttributeType structuralObjectClassType;



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

    structuralObjectClassType =
         DirectoryServer.getAttributeType("structuralobjectclass", false);
    assertNotNull(structuralObjectClassType);
  }



  /**
   * Retrieves a set of entry DNs for use in testing the
   * structuralObjectClassType virtual attribute.
   *
   * @return  A set of entry DNs for use in testing the structuralobjectclass
   *          virtual attribute.
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
   * Retrieves a set of entry DNs and corresponding structural object classes
   * for use in testing the structuralObjectClassType virtual attribute.
   *
   * @return  A set of entry DNs and oc for use in testing the
   *           structuralobjectclass virtual attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testDNOC")
  public Object[][] getTestEntryDNOC()
         throws Exception
  {
    return new Object[][] {  
        {DN.decode("o=test"), "structuralObjectClass=organization"},
        {DN.decode("dc=example,dc=com"), "structuralObjectClass=domain"},
    };
  }
  
  
  
  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the structuralObjectClass operational
   * attribute with the correct value.
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
    assertTrue(e.hasAttribute(structuralObjectClassType));

    List<Attribute> attrList = e.getAttribute(structuralObjectClassType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(!a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(AttributeValues.create(structuralObjectClassType,
                              e.getStructuralObjectClass().getNameOrOID())));
    }
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is not included when the list of attributes
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
    assertFalse(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is not included when the list of requested
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
    assertFalse(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is not included when all user attributes
   * are requested.
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
    assertFalse(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is included when all operational attributes
   * are requested.
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
    assertTrue(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is included when that attribute is
   * specifically requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchStructuralOCAttr(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("structuralobjectclass");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is not included when it is not in the list
   * of attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludeStructuralOCAttr(DN entryDN)
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
    assertFalse(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is included when that attribute is
   * specifically requested and the structuralObjectClass attribute is used in the
   * search filter with a matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testDNOC")
  public void testSearchStructuralOCAttrInMatchingFilter(DN entryDN,String oc)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString(oc);
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("structuralObjectClass");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the structuralObjectClass attribute is used in the
   * search filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchStructuralOCAttrInNonMatchingFilter(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(structuralObjectClass=abc)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("structuralObjectClass");

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
   * the structuralObjectClass attribute is not included when that attribute is
   * specifically requested and the real attributes only control is included in
   * the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchStructuralOCAttrRealAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("structuralObjectClass");

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new LDAPControl(OID_REAL_ATTRS_ONLY, true));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(conn, InternalClientConnection
            .nextOperationID(), InternalClientConnection
            .nextMessageID(), requestControls, entryDN,
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList, null);
    searchOperation.run();
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the structuralObjectClass attribute is included when that attribute is
   * specifically requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchStructuralOCAttrVirtualAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("structuralObjectClass");

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(conn, InternalClientConnection
            .nextOperationID(), InternalClientConnection
            .nextMessageID(), requestControls, entryDN,
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList, null);
    searchOperation.run();
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    Set<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(AttributeValues.create(structuralObjectClassType,
                                  entry.getStructuralObjectClass().getNameOrOID())));
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule,
        AttributeValues.create(structuralObjectClassType,
                          entry.getStructuralObjectClass().getNameOrOID())));
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasValue(entry, rule,
        AttributeValues.create(structuralObjectClassType,
                                        "inetorgperson")));
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(AttributeValues.create(structuralObjectClassType, "organization"));

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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(AttributeValues.create(structuralObjectClassType, "inetorgperson"));

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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(AttributeValues.create(structuralObjectClassType, "organization"));
    values.add(AttributeValues.create(structuralObjectClassType, "inetorgperson"));
    values.add(AttributeValues.create(structuralObjectClassType,
                                  "top"));

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
    StructuralObjectClassVirtualAttributeProvider provider =
         new StructuralObjectClassVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(structuralObjectClassType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(AttributeValues.create(structuralObjectClassType, "inetorgperson"));
    values.add(AttributeValues.create(structuralObjectClassType,
                                  "top"));
    values.add(AttributeValues.create(structuralObjectClassType,
                                  "domain"));

    assertFalse(provider.hasAnyValue(entry, rule, values));
  }
}

