/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the governing structure rule virtual attribute
 * provider.
 */
public class GoverningStructureRuleVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  /** The attribute type for the governingStructureRule attribute. */
  private AttributeType governingStructureRuleType;



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
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

    governingStructureRuleType = getGoverningStructureRuleAttributeType();

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
    assertEquals(resultCode, 0);
  }



  /**
   * Ensures that the schema is cleaned up.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
 @AfterClass
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
    assertEquals(resultCode, 0);
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
      new Object[] { DN.valueOf("o=test") },
      new Object[] { DN.valueOf("dc=example,dc=com") }
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
        {DN.valueOf("o=test"), "22"},
        {DN.valueOf("dc=example,dc=com"), "21"},
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
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(governingStructureRuleType));

    List<Attribute> attrList = e.getAttribute(governingStructureRuleType);
    assertThat(attrList).isNotEmpty();
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOfUtf8(ruleId)));
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
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, governingStructureRuleType);
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
    ExtensionTestUtils.testSearchNoAttrs(entryDN, governingStructureRuleType);
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
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, governingStructureRuleType);
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
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, governingStructureRuleType);
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
    ExtensionTestUtils.testSearchAttr(entryDN, "governingStructureRule", governingStructureRuleType);
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
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, governingStructureRuleType);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "governingstructurerule=" + oc)
        .addAttribute("governingStructureRule");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(governingStructureRule=1)")
        .addAttribute("governingStructureRuleType");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = Requests.newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("governingStructureRuleType")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = Requests.newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("governingStructureRule")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(governingStructureRuleType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
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
  @Test
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

    Attribute values = provider.getValues(entry, getRule(provider));
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(ByteString.valueOfUtf8("22")));
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectclass: organization",
      "o: test");
    entry.processVirtualAttributes();

    assertTrue(provider.hasValue(entry, getRule(provider), ByteString.valueOfUtf8("22")));
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
    GoverningStructureRuleVirtualAttributeProvider provider =
         new GoverningStructureRuleVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    assertFalse(provider.hasValue(entry, getRule(provider), ByteString.valueOfUtf8("1")));
  }


  private VirtualAttributeRule getRule(VirtualAttributeProvider<?> provider)
  {
    return new VirtualAttributeRule(governingStructureRuleType, provider,
                  Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                  Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
  }
}
