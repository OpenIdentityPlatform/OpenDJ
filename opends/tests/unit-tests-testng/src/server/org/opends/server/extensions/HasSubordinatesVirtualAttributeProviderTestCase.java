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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;

import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;
import static org.opends.server.util.StaticUtils.getBytes;
import static org.opends.server.util.ServerConstants.OID_REAL_ATTRS_ONLY;
import static org.opends.server.util.ServerConstants.OID_VIRTUAL_ATTRS_ONLY;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.LinkedList;

public class HasSubordinatesVirtualAttributeProviderTestCase
{
      // The attribute type for the hasSubordinates attribute.
  private AttributeType hasSubordinatesType;

  private List<Entry> entries;

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

    hasSubordinatesType =
        DirectoryServer.getAttributeType("hassubordinates", false);
    assertNotNull(hasSubordinatesType);

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

    TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");

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
      new Object[] { DN.decode("dc=example,dc=com"), true },
      new Object[] { DN.decode("ou=People,dc=example,dc=com"), true },
      new Object[] { DN.decode("ou=Employees,ou=People,dc=example,dc=com"), true },
      new Object[] { DN.decode("ou=Buildings,dc=example,dc=com"), false },
      new Object[] { DN.decode("uid=user.0,ou=People,dc=example,dc=com"), false },
      new Object[] { DN.decode("uid=user.1,ou=People,dc=example,dc=com"), false },
      new Object[] { DN.decode("uid=user.2,ou=Employees,ou=People" +
            ",dc=example,dc=com"), false },
      new Object[] { DN.decode("cn=monitor"), true },
      new Object[] { DN.decode("cn=Backends,cn=config"), true },
      new Object[] { DN.decode("cn=Work Queue,cn=config"), false },
      new Object[] { DN.decode("cn=tasks"), true },
      new Object[] { DN.decode("cn=Recurring Tasks,cn=tasks"), false },
      new Object[] { DN.decode("cn=Scheduled Tasks,cn=tasks"), false },
      new Object[] { DN.decode("cn=backups"), false }
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
  @Test(dataProvider = "testEntryDNs")
  public void testGetEntry(DN entryDN, boolean hasSubs)
      throws Exception
  {
    Entry e = DirectoryServer.getEntry(entryDN);
    assertNotNull(e);
    assertTrue(e.hasAttribute(hasSubordinatesType));

    List<Attribute> attrList = e.getAttribute(hasSubordinatesType);
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());
    for (Attribute a : attrList)
    {
      assertTrue(a.hasValue());
      assertEquals(a.getValues().size(), 1);
      assertTrue(a.getValues().contains(new AttributeValue(
          ByteStringFactory.create(String.valueOf(hasSubs)),
          ByteStringFactory.create(String.valueOf(hasSubs)))));
      assertTrue(a.hasValue(new AttributeValue(hasSubordinatesType,
                                               String.valueOf(hasSubs))));
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
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT, filter);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(hasSubordinatesType));
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
    assertFalse(e.hasAttribute(hasSubordinatesType));
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
    assertFalse(e.hasAttribute(hasSubordinatesType));
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
    assertTrue(e.hasAttribute(hasSubordinatesType));
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
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("hasSubordinates");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(hasSubordinatesType));
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
    assertFalse(e.hasAttribute(hasSubordinatesType));
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
    SearchFilter filter =
         SearchFilter.createFilterFromString("(hasSubordinates=" + hasSubs + ")");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("hasSubordinates");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            filter, attrList);
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
    SearchFilter filter =
         SearchFilter.createFilterFromString("(hasSubordinates=wrong)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("hasSubordinates");

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
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("hasSubordinates");

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
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("hasSubordinates");

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
    assertTrue(e.hasAttribute(hasSubordinatesType));
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    NumSubordinatesVirtualAttributeProvider provider =
        new NumSubordinatesVirtualAttributeProvider();
    assertFalse(provider.isMultiValued());
  }
}
