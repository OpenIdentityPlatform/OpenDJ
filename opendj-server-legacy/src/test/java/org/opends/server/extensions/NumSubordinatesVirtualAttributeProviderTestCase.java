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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
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
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class NumSubordinatesVirtualAttributeProviderTestCase extends DirectoryServerTestCase {
  /** The attribute type for the numSubordinates attribute. */
  private AttributeType numSubordinatesType;
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
    TestCaseUtils.restartServer();

    numSubordinatesType = TestCaseUtils.getServerContext().getSchema().getAttributeType("numsubordinates");

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
   * Retrieves a set of entry DNs for use in testing the numSubordinates virtual
   * attribute.
   *
   * @return  A set of entry DNs for use in testing the numSubordinates virtual
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
      new Object[] { DN.valueOf("dc=example,dc=com"), 2 },
      new Object[] { DN.valueOf("ou=People,dc=example,dc=com"), 3 },
      new Object[] { DN.valueOf("ou=Employees,ou=People,dc=example,dc=com"), 1 },
      new Object[] { DN.valueOf("ou=Buildings,dc=example,dc=com"), 0 },
      new Object[] { DN.valueOf("uid=user.0,ou=People,dc=example,dc=com"), 0 },
      new Object[] { DN.valueOf("uid=user.1,ou=People,dc=example,dc=com"), 0 },
      new Object[] { DN.valueOf("uid=user.2,ou=Employees,ou=People" +
                               ",dc=example,dc=com"), 0 },
//      new Object[] { DN.decode("cn=monitor"),
//          DirectoryServer.getMonitorProviders().size() },
// Disable test on # of backends. Some might be disabled, falsing count.
//      new Object[] { DN.decode("cn=Backends,cn=config"),
//          DirectoryServer.getBackends().size() },
      new Object[] { DN.valueOf("cn=Work Queue,cn=config"), 0 },
      new Object[] { DN.valueOf("cn=tasks"), 2 },
      new Object[] { DN.valueOf("cn=backups"), 0 }
    };
  }

  /**
   * Tests the {@code getEntry} method for the specified entry to ensure that
   * the entry returned includes the numSubordinates operational attribute
   * with the correct value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   * @param count The number of subordinates the entry should have.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs", singleThreaded = true)
  public void testGetEntry(DN entryDN, int count)
      throws Exception
  {
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(numSubordinatesType));

    List<Attribute> attrList = e.getAllAttributes(numSubordinatesType);
    assertThat(attrList).isNotEmpty();
    for (Attribute a : attrList)
    {
      assertFalse(a.isEmpty());
      assertEquals(a.size(), 1);
      assertTrue(a.contains(ByteString.valueOfUtf8(String.valueOf(count))));
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
  public void testSearchEmptyAttrs(DN entryDN, int count)
      throws Exception
  {
    ExtensionTestUtils.testSearchEmptyAttrs(entryDN, numSubordinatesType);
  }


  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is not included when the list of requested
   * attributes is "1.1", meaning no attributes.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchNoAttrs(DN entryDN, int count)
         throws Exception
  {
    ExtensionTestUtils.testSearchNoAttrs(entryDN, numSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is not included when all user attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllUserAttrs(DN entryDN, int count)
         throws Exception
  {
    ExtensionTestUtils.testSearchAllUserAttrs(entryDN, numSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when all operational attributes are
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchAllOperationalAttrs(DN entryDN, int count)
         throws Exception
  {
    ExtensionTestUtils.testSearchAllOperationalAttrs(entryDN, numSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when that attribute is specifically
   * requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttr(DN entryDN, int count)
         throws Exception
  {
    ExtensionTestUtils.testSearchAttr(entryDN, "numSubordinates", numSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is not included when it is not in the list of
   * attributes that is explicitly requested.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchExcludenumSubordinatesAttr(DN entryDN, int count)
         throws Exception
  {
    ExtensionTestUtils.testSearchExcludeAttr(entryDN, numSubordinatesType);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when that attribute is specifically
   * requested and the numSubordinates attribute is used in the search filter with a
   * matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrInMatchingFilter(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(numSubordinates=" + count + ")")
            .addAttribute("numSubordinates");
    assertNumSubordinatesAttributeFound(request, true);
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * no entries are returned when the numSubordinates attribute is used in the search
   * filter with a non-matching value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrInNonMatchingFilter(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(numSubordinates=wrong)").addAttribute("numSubordinates");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 0);
  }



  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is not included when that attribute is specifically
   * requested and the real attributes only control is included in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrRealAttrsOnly(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("numSubordinates")
        .addControl(new LDAPControl(OID_REAL_ATTRS_ONLY, true));
    assertNumSubordinatesAttributeFound(request, false);
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when that attribute is specifically
   * requested and the virtual attributes only control is included
   * in the request.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrVirtualAttrsOnly(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute("numSubordinates")
        .addControl(new LDAPControl(OID_VIRTUAL_ATTRS_ONLY, true));
    assertNumSubordinatesAttributeFound(request, true);
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when that attribute is specifically
   * requested and the numSubordinates attribute is used in the search filter with a
   * greater than value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrInGTEFilter(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(numSubordinates>=" + count + ")")
            .addAttribute("numSubordinates");
    assertNumSubordinatesAttributeFound(request, true);
  }

  /**
   * Performs an internal search to retrieve the specified entry, ensuring that
   * the numSubordinates attribute is included when that attribute is specifically
   * requested and the numSubordinates attribute is used in the search filter with a
   * less than value.
   *
   * @param  entryDN  The DN of the entry to retrieve and verify.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testEntryDNs")
  public void testSearchnumSubordinatesAttrInLTEFilter(DN entryDN, int count)
         throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(numSubordinates<=" + count + ")")
            .addAttribute("numSubordinates");
    assertNumSubordinatesAttributeFound(request, true);
  }

  private void assertNumSubordinatesAttributeFound(final SearchRequest request, boolean expected)
  {
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertEquals(e.hasAttribute(numSubordinatesType), expected);
  }

  /** Tests the {@code isMultiValued} method. */
  @Test
  public void testIsMultiValued()
  {
    assertFalse(new NumSubordinatesVirtualAttributeProvider().isMultiValued());
  }
}
