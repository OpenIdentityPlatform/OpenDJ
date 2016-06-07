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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the entryUUID virtual attribute provider.
 */
public class EntryUUIDVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  private static final AttributeType entryUUIDType = CoreSchema.getEntryUUIDAttributeType();

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
      new Object[] { DN.valueOf("") },
      new Object[] { DN.valueOf("cn=config") },
      new Object[] { DN.valueOf("cn=schema") },
      new Object[] { DN.valueOf("cn=tasks") },
      new Object[] { DN.valueOf("cn=monitor") },
      new Object[] { DN.valueOf("cn=backups") },
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
    String uuidString = entryDN.toUUID().toString();

    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));

    List<Attribute> attrList = e.getAttribute(entryUUIDType);
    assertThat(attrList).isNotEmpty();
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOfUtf8(uuidString)));
    }
  }



  /**
   * Tests the {@code getEntry} method for a user entry that should have a real
   * entryUUID value added by a plugin.  In this case, the entryUUID value
   * should be a random UUID and not one generated based on the entry's DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetUserEntry()
         throws Exception
  {
    String uuidString = DN.valueOf("dc=example,dc=com").toUUID().toString();

    TestCaseUtils.clearBackend("userRoot");

    Entry e = TestCaseUtils.addEntry(
      "dn: dc=example,dc=com",
      "objectClass: top",
      "objectClass: domain",
      "dc: example");
    assertTrue(e.hasAttribute(entryUUIDType));

    List<Attribute> attrList = e.getAttribute(entryUUIDType);
    assertThat(attrList).isNotEmpty();
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertFalse(a.contains(ByteString.valueOfUtf8(uuidString)));
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
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, entryUUIDType);
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
    ExtensionTestUtils.testSearchNoAttrs(entryDN, entryUUIDType);
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
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, entryUUIDType);
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
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, entryUUIDType);
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
    ExtensionTestUtils.testSearchAttr(entryDN, "entryuuid", entryUUIDType);
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
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, entryUUIDType);
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
    String uuidString = entryDN.toUUID().toString();

    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(entryUUID=" + uuidString + ")")
        .addAttribute("entryuuid");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(entryUUID=wrong)")
        .addAttribute("entryuuid");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
  public void testSearchEntryUUIDAttrRealAttrsOnly(DN entryDN) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("entryuuid")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
  public void testSearchEntryUUIDAttrVirtualAttrsOnly(DN entryDN) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("entryuuid")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(entryUUIDType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
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
  @Test
  public void testGetValues() throws Exception
  {
    String uuidString = DN.valueOf("o=test").toUUID().toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = makeEntry();
    VirtualAttributeRule rule = getRule(provider);

    Attribute values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertEquals(values.size(), 1);
    assertTrue(values.contains(ByteString.valueOfUtf8(uuidString)));
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
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = makeEntry();
    VirtualAttributeRule rule = getRule(provider);
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
    String uuidString = DN.valueOf("o=test").toUUID().toString();

    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = makeEntry();
    VirtualAttributeRule rule = getRule(provider);
    assertTrue(provider.hasValue(entry, rule, ByteString.valueOfUtf8(uuidString)));
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
    EntryUUIDVirtualAttributeProvider provider =
         new EntryUUIDVirtualAttributeProvider();

    Entry entry = makeEntry();
    VirtualAttributeRule rule = getRule(provider);
    assertFalse(provider.hasValue(entry, rule, ByteString.valueOfUtf8("wrong")));
  }

  private Entry makeEntry() throws Exception
  {
    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();
    return entry;
  }

  private VirtualAttributeRule getRule(EntryUUIDVirtualAttributeProvider provider)
  {
    return new VirtualAttributeRule(entryUUIDType, provider,
              Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
              Collections.<DN>emptySet(),
              Collections.<SearchFilter>emptySet(),
              VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
  }
}
