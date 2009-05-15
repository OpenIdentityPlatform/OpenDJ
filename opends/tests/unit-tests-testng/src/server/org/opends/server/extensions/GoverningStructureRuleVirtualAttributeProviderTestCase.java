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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * A set of test cases for the governing structure rule virtual attribute
 * provider.
 */
public class GoverningStructureRuleVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  // The attribute type for the governingStructureRule attribute.
  private AttributeType governingStructureRuleType;



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

    governingStructureRuleType =
         DirectoryServer.getAttributeType("governingstructurerule", false);
    assertNotNull(governingStructureRuleType);
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: nameForms",
    "nameForms: ( domainNameForm-oid NAME 'domainNameForm' OC domain MUST ( dc ) )",
    "nameForms: ( organizationalNameForm-oid NAME 'organizationalNameForm' OC organization MUST ( o ) )",
    "-",
    "add: ditStructureRules",
    "dITStructureRules: ( 21 NAME 'domainStructureRule' FORM domainNameForm )",
    "dITStructureRules: ( 22 NAME 'organizationalStructureRule' FORM organizationalNameForm SUP 21 )"
    );
    assertTrue(resultCode == 0);
  }



  /**
   * Ensures that the schema is cleaned up.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
 @AfterClass()
 public void cleanup() throws Exception
 {
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "delete: ditStructureRules",
    "dITStructureRules: ( 22 NAME 'organizationalStructureRule' FORM organizationalNameForm SUP 21 )",
    "dITStructureRules: ( 21 NAME 'domainStructureRule' FORM domainNameForm )",
    "-",
    "delete: nameForms",
    "nameForms: ( domainNameForm-oid NAME 'domainNameForm' OC domain MUST ( dc ) )",
    "nameForms: ( organizationalNameForm-oid NAME 'organizationalNameForm' OC organization MUST ( o ) )"
     );
    assertTrue(resultCode == 0);
 }
  
  
  
  /**
   * Retrieves a set of entry DNs for use in testing the
   * governingStructureRule virtual attribute.
   *
   * @return  A set of entry DNs for use in testing the governingStructureRule
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
      new Object[] { DN.decode("o=test") },
      new Object[] { DN.decode("dc=example,dc=com") }
    };
  }



  /**
   * Retrieves a set of entry DNs and corresponding governing structure rule
   * ids for use in testing the governingStructureRule virtual attribute.
   *
   * @return  A set of entry DNs and id for use in testing the
   *           governingStructureRule virtual attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testDNRuleID")
  public Object[][] getTestEntryDNRuleID()
         throws Exception
  {
    return new Object[][] {
        {DN.decode("o=test"), "22"},
        {DN.decode("dc=example,dc=com"), "21"},
    };
  }



  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the governingStructureRule operational
   * attribute with the correct value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   * @param ruleId The rule id of the DITStructureRule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testDNRuleID")
  public void testGetEntry(DN entryDN,String ruleId)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(governingStructureRuleType));

    List<Attribute> attrList = e.getAttribute(governingStructureRuleType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(!a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(AttributeValues.create(governingStructureRuleType,
                              ruleId)));
    }
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is not included when the list of attributes
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
    assertFalse(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is not included when the list of requested
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
    assertFalse(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is not included when all user attributes
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
    assertFalse(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRuleType attribute is included when all operational attributes
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
    assertTrue(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is included when that attribute is
   * specifically requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchGoverningStructureRulesAttr(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("governingStructureRule");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is not included when it is not in the list
   * of attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludeGovStructRuleAttr(DN entryDN)
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
    assertFalse(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is included when that attribute is
   * specifically requested and the governingStructureRule attribute is used in the
   * search filter with a matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testDNRuleID")
  public void testSearchGovStructRuleInMatchingFilter(DN entryDN,String oc)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("governingstructurerule="+oc);
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("governingStructureRule");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the governingStructureRule attribute is used in the
   * search filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchGovStructRuleAttrInNonMatchingFilter(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(governingStructureRule=1)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("governingStructureRuleType");

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
   * the governingStructureRule attribute is not included when that attribute is
   * specifically requested and the real attributes only control is included in
   * the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchGovStructRuleAttrRealAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("governingStructureRuleType");

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
    assertFalse(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the governingStructureRule attribute is included when that attribute is
   * specifically requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchGovStructRuleAttrVirtualAttrsOnly(DN entryDN)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("governingStructureRule");

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
    assertTrue(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    Set<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(AttributeValues.create(governingStructureRuleType,
                                  "22")));
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule,
        AttributeValues.create(governingStructureRuleType,"22")));
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasValue(entry, rule,
        AttributeValues.create(governingStructureRuleType,
                                        "1")));
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(AttributeValues.create(governingStructureRuleType, "22"));

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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(1);
    values.add(AttributeValues.create(governingStructureRuleType, "1"));

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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(AttributeValues.create(governingStructureRuleType, "22"));
    values.add(AttributeValues.create(governingStructureRuleType, "1"));
    values.add(AttributeValues.create(governingStructureRuleType,"2"));

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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>(3);
    values.add(AttributeValues.create(governingStructureRuleType, "1"));
    values.add(AttributeValues.create(governingStructureRuleType, "2"));
    values.add(AttributeValues.create(governingStructureRuleType,"3"));

    assertFalse(provider.hasAnyValue(entry, rule, values));
  }
}

