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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the entryDN virtual attribute provider.
 */
public class EntryDNVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  /** The attribute type for the entryDN attribute. */
  private AttributeType entryDNType;



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend("userRoot", "dc=example,dc=com");

    entryDNType = DirectoryServer.getAttributeType("entrydn");
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
      new Object[] { DN.valueOf("") },
      new Object[] { DN.valueOf("o=test") },
      new Object[] { DN.valueOf("dc=example,dc=com") },
      new Object[] { DN.valueOf("cn=config") },
      new Object[] { DN.valueOf("cn=schema") },
      new Object[] { DN.valueOf("cn=tasks") },
      new Object[] { DN.valueOf("cn=monitor") },
      new Object[] { DN.valueOf("cn=backups") }
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
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryDNType));

    List<Attribute> attrList = e.getAttribute(entryDNType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOf(entryDN.toString())));
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
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, entryDNType);
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
    ExtensionTestUtils.testSearchNoAttrs(entryDN, entryDNType);
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
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, entryDNType);
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
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, entryDNType);
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
    ExtensionTestUtils.testSearchAttr(entryDN, "entrydn", entryDNType);
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
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, entryDNType);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(entryDN=" + entryDN + ")")
        .addAttribute("entrydn");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(entryDN=cn=Not A Match)")
        .addAttribute("entrydn");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("entrydn")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("entrydn")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryDNType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
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
  @Test
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

    Attribute values = provider.getValues(entry, getRule(provider));
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(ByteString.valueOf("o=test")));
  }



  /**
   * Tests the {@code hasValue} method variant that doesn't take a specific
   * value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    assertTrue(provider.hasValue(entry, getRule(provider)));
  }



  /**
   * Tests the {@code hasValue} method variant that takes a specific value when
   * the provided value is a match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    assertTrue(provider.hasValue(entry, getRule(provider), ByteString.valueOf("o=test")));
  }



  private VirtualAttributeRule getRule(VirtualAttributeProvider<?> provider)
  {
    return new VirtualAttributeRule(entryDNType, provider,
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
  }



  /**
   * Tests the {@code hasValue} method variant that takes a specific value when
   * the provided value is not a match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    assertFalse(provider.hasValue(entry, getRule(provider), ByteString.valueOf("o=not test")));
  }



  /**
   * Tests the {@code matchesSubstring} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    LinkedList<ByteString> subAny = newLinkedList(ByteString.valueOf("="));

    assertEquals(provider.matchesSubstring(entry, getRule(provider), null, subAny, null),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code greaterThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ByteString value = ByteString.valueOf("o=test2");
    assertEquals(provider.greaterThanOrEqualTo(entry, getRule(provider), value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code lessThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ByteString value = ByteString.valueOf("o=test2");
    assertEquals(provider.lessThanOrEqualTo(entry, getRule(provider), value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code approximatelyEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    ByteString value = ByteString.valueOf("o=test2");
    assertEquals(provider.approximatelyEqualTo(entry, getRule(provider), value),
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

    VirtualAttributeRule rule = getRule(provider);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE, filterString);
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);
    // This attribute is searchable for either pre-indexed or not
    assertEquals(provider.isSearchable(rule, searchOperation, false),
                 isSearchable);
    assertEquals(provider.isSearchable(rule, searchOperation, true),
                 isSearchable);
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

    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    VirtualAttributeRule rule = getRule(provider);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE, filterString);
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);
    LocalBackendSearchOperation localSearch = new LocalBackendSearchOperation(searchOperation);
    provider.processSearch(rule, localSearch);

    assertEquals(searchOperation.getSearchEntries().size(), shouldMatch ? 1 : 0);
  }
}

