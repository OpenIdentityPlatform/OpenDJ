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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the structural object class virtual attribute
 * provider.
 */
public class StructuralObjectClassVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  /** The attribute type for the structuralobjectclass attribute. */
  private AttributeType structuralObjectClassType;



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

    structuralObjectClassType = DirectoryServer.getAttributeType("structuralobjectclass");
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
        {DN.valueOf("o=test"), "structuralObjectClass=organization"},
        {DN.valueOf("dc=example,dc=com"), "structuralObjectClass=domain"},
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
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(structuralObjectClassType));

    List<Attribute> attrList = e.getAttribute(structuralObjectClassType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOf(e.getStructuralObjectClass().getNameOrOID())));
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
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, structuralObjectClassType);
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
    ExtensionTestUtils.testSearchNoAttrs(entryDN, structuralObjectClassType);
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
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, structuralObjectClassType);
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
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, structuralObjectClassType);
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
    ExtensionTestUtils.testSearchAttr(entryDN, "structuralobjectclass", structuralObjectClassType);
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
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, structuralObjectClassType);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, oc)
        .addAttribute("structuralObjectClass");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(structuralObjectClass=abc)")
        .addAttribute("structuralObjectClass");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("structuralObjectClass")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("structuralObjectClass")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(structuralObjectClassType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
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
  @Test
  public void testGetValues() throws Exception
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
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    Attribute values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(ByteString.valueOf(entry.getStructuralObjectClass().getNameOrOID())));
  }



  /**
   * Tests the {@code hasValue} method variant that doesn't take a specific
   * value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testHasAnyValue() throws Exception
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
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
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
  @Test
  public void testHasMatchingValue() throws Exception
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
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertTrue(provider.hasValue(entry, rule,
        ByteString.valueOf(entry.getStructuralObjectClass().getNameOrOID())));
  }



  /**
   * Tests the {@code hasValue} method variant that takes a specific value when
   * the provided value is not a match.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testHasNonMatchingValue() throws Exception
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
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasValue(entry, rule, ByteString.valueOf("inetorgperson")));
  }
}
