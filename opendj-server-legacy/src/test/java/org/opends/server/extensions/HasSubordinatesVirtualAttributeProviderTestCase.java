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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class HasSubordinatesVirtualAttributeProviderTestCase extends DirectoryServerTestCase {
  /** The attribute type for the hasSubordinates attribute. */
  private AttributeType hasSubordinatesType;

  private List<Entry> entries;

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

    hasSubordinatesType = getServerContext().getSchema().getAttributeType("hassubordinates");

    entries = TestCaseUtils.makeEntries(
        "dn: dc=example,dc=com",
        "objectclass: top",
        "objectclass: domain",
        "dc: example",
        "",
        "dn: ou=People,dc=example,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: People",
        "",
        "dn: ou=Employees,ou=People,dc=example,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: Employees",
        "",
        "dn: ou=Buildings,dc=example,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: Buildings",
        "",
        "dn: uid=user.0,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aaccf",
        "sn: Amar",
        "cn: Aaccf Amar",
        "initials: AQA",
        "employeeNumber: 0",
        "uid: user.0",
        "mail: user.0@example.com",
        "userPassword: password",
        "telephoneNumber: 380-535-2354",
        "homePhone: 707-626-3913",
        "pager: 456-345-7750",
        "mobile: 366-674-7274",
        "street: 99262 Eleventh Street",
        "l: Salem",
        "st: NM",
        "postalCode: 36530",
        "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
        "description: This is the description for Aaccf Amar.",
        "",
        "dn: uid=user.1,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aaren",
        "sn: Atp",
        "cn: Aaren Atp",
        "initials: APA",
        "employeeNumber: 1",
        "uid: user.1",
        "mail: user.1@example.com",
        "userPassword: password",
        "telephoneNumber: 643-278-6134",
        "homePhone: 546-786-4099",
        "pager: 508-261-3187",
        "mobile: 377-267-7824",
        "street: 78113 Fifth Street",
        "l: Chico",
        "st: TN",
        "postalCode: 72322",
        "postalAddress: Aaren Atp$78113 Fifth Street$Chico, TN  72322",
        "description: This is the description for Aaren Atp.",
        "",
        "dn: uid=user.2,ou=Employees,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aarika",
        "sn: Atpco",
        "cn: Aarika Atpco",
        "initials: ARA",
        "employeeNumber: 2",
        "uid: user.2",
        "mail: user.2@example.com",
        "userPassword: password",
        "telephoneNumber: 547-504-3498",
        "homePhone: 955-899-7308",
        "pager: 710-832-9316",
        "mobile: 688-388-4525",
        "street: 59208 Elm Street",
        "l: Youngstown",
        "st: HI",
        "postalCode: 57377",
        "postalAddress: Aarika Atpco$59208 Elm Street$Youngstown, HI  57377",
        "description: This is the description for Aarika Atpco.");

    TestCaseUtils.clearBackend("userRoot");

    TestCaseUtils.addEntries(entries);
  }

    /**
   * Retrieves a set of entry DNs for use in testing the hasSubordinates virtual
   * attribute.
   *
   * @return  A set of entry DNs for use in testing the hasSubordinates virtual
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
      new Object[] { DN.valueOf("dc=example,dc=com"), true },
      new Object[] { DN.valueOf("ou=People,dc=example,dc=com"), true },
      new Object[] { DN.valueOf("ou=Employees,ou=People,dc=example,dc=com"), true },
      new Object[] { DN.valueOf("ou=Buildings,dc=example,dc=com"), false },
      new Object[] { DN.valueOf("uid=user.0,ou=People,dc=example,dc=com"), false },
      new Object[] { DN.valueOf("uid=user.1,ou=People,dc=example,dc=com"), false },
      new Object[] { DN.valueOf("uid=user.2,ou=Employees,ou=People" +
            ",dc=example,dc=com"), false },
      new Object[] { DN.valueOf("cn=monitor"), true },
      new Object[] { DN.valueOf("cn=Backends,cn=config"), true },
      new Object[] { DN.valueOf("cn=Work Queue,cn=config"), false },
      new Object[] { DN.valueOf("cn=tasks"), true },
      new Object[] { DN.valueOf("cn=Recurring Tasks,cn=tasks"), false },
      new Object[] { DN.valueOf("cn=backups"), false }
    };
  }

  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the hasSubordinates operational attribute
   * with the correct value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   * @param hasSubs Whether this entry has any subs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs", singleThreaded = true)
  public void testGetEntry(DN entryDN, boolean hasSubs)
      throws Exception
  {
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(hasSubordinatesType));

    List<Attribute> attrList = e.getAllAttributes(hasSubordinatesType);
    assertThat(attrList).isNotEmpty();
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOfUtf8(toUpperCase(String.valueOf(hasSubs)))));
    }
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is not included when the list of attributes
   * requested is empty (defaulting to all user attributes).
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchEmptyAttrs(DN entryDN, boolean hasSubs)
      throws Exception
  {
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, hasSubordinatesType);
  }


  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is not included when the list of requested
   * attributes is "1.1", meaning no attributes.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchNoAttrs(DN entryDN, boolean hasSubs)
         throws Exception
  {
    ExtensionTestUtils.testSearchNoAttrs(entryDN, hasSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is not included when all user attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllUserAttrs(DN entryDN, boolean hasSubs)
         throws Exception
  {
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, hasSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is included when all operational attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllOperationalAttrs(DN entryDN, boolean hasSubs)
         throws Exception
  {
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, hasSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is included when that attribute is specifically
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchhasSubordinatesAttr(DN entryDN, boolean hasSubs)
         throws Exception
  {
    ExtensionTestUtils.testSearchAttr(entryDN, "hasSubordinates", hasSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is not included when it is not in the list of
   * attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludehasSubordinatesAttr(DN entryDN, boolean hasSubs)
         throws Exception
  {
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, hasSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is included when that attribute is specifically
   * requested and the hasSubordinates attribute is used in the search filter with a
   * matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchhasSubordinatesAttrInMatchingFilter(DN entryDN, boolean hasSubs)
         throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, BASE_OBJECT, "(hasSubordinates=" + hasSubs + ")")
        .addAttribute("hasSubordinates");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(hasSubordinatesType));
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the hasSubordinates attribute is used in the search
   * filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchhasSubordinatesAttrInNonMatchingFilter(DN entryDN, boolean hasSubs)
         throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, BASE_OBJECT, "(hasSubordinates=wrong)")
        .addAttribute("hasSubordinates");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 0);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is not included when that attribute is specifically
   * requested and the real attributes only control is included in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchhasSubordinatesAttrRealAttrsOnly(DN entryDN, boolean hasSubs)
         throws Exception
  {
    SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("hasSubordinates")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(hasSubordinatesType));
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the hasSubordinates attribute is included when that attribute is specifically
   * requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchhasSubordinatesAttrVirtualAttrsOnly(DN entryDN, boolean hasSubs)
         throws Exception
  {
    SearchRequest request = Requests.newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("hasSubordinates")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));

    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation searchOperation = conn.processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(hasSubordinatesType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
  public void testIsMultiValued()
  {
    NumSubordinatesVirtualAttributeProvider provider =
        new NumSubordinatesVirtualAttributeProvider();
    assertFalse(provider.isMultiValued());
  }
}
