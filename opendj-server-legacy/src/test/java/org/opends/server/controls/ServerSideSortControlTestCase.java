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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.SortKey;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This class contains a number of test cases for the server side sort request
 * and response controls.
 */
public class ServerSideSortControlTestCase
    extends ControlsTestCase
{
  /** The DN for "Aaccf Johnson". */
  DN aaccfJohnsonDN;
  /** The DN for "Aaron Zimmerman". */
  DN aaronZimmermanDN;
  /** The DN for "Albert Smith". */
  DN albertSmithDN;
  /** The DN for "Albert Zimmerman". */
  DN albertZimmermanDN;
  /** The DN for "lowercase mcgee". */
  DN lowercaseMcGeeDN;
  /** The DN for "Mararet Jones". */
  DN margaretJonesDN;
  /** The DN for "Mary Jones". */
  DN maryJonesDN;
  /** The DN for "Sam Zweck". */
  DN samZweckDN;
  /** The DN for "Zorro". */
  DN zorroDN;



  /**
   * Make sure that the server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();

    aaccfJohnsonDN    = DN.valueOf("uid=aaccf.johnson,dc=example,dc=com");
    aaronZimmermanDN  = DN.valueOf("uid=aaron.zimmerman,dc=example,dc=com");
    albertSmithDN     = DN.valueOf("uid=albert.smith,dc=example,dc=com");
    albertZimmermanDN = DN.valueOf("uid=albert.zimmerman,dc=example,dc=com");
    lowercaseMcGeeDN  = DN.valueOf("uid=lowercase.mcgee,dc=example,dc=com");
    margaretJonesDN   = DN.valueOf("uid=margaret.jones,dc=example,dc=com");
    maryJonesDN       = DN.valueOf("uid=mary.jones,dc=example,dc=com");
    samZweckDN        = DN.valueOf("uid=sam.zweck,dc=example,dc=com");
    zorroDN           = DN.valueOf("uid=zorro,dc=example,dc=com");
  }



  /**
   * Populates the JE DB with a set of test data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void populateDB() throws Exception
  {
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

    TestCaseUtils.addEntries(
      "dn: uid=albert.zimmerman,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: albert.zimmerman",
      "givenName: Albert",
      "sn: Zimmerman",
      "cn: Albert Zimmerman",
      "",
      "dn: uid=albert.smith,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: albert.smith",
      "givenName: Albert",
      "sn: Smith",
      "cn: Albert Smith",
      "",
      "dn: uid=aaron.zimmerman,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: albert.zimmerman",
      "givenName: Aaron",
      "givenName: Zeke",
      "sn: Zimmerman",
      "cn: Aaron Zimmerman",
      "",
      "dn: uid=mary.jones,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: mary.jones",
      "givenName: Mary",
      "sn: Jones",
      "cn: Mary Jones",
      "",
      "dn: uid=margaret.jones,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: margaret.jones",
      "givenName: Margaret",
      "givenName: Maggie",
      "sn: Jones",
      "sn: Smith",
      "cn: Maggie Jones-Smith",
      "",
      "dn: uid=aaccf.johnson,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: aaccf.johnson",
      "givenName: Aaccf",
      "sn: Johnson",
      "cn: Aaccf Johnson",
      "",
      "dn: uid=sam.zweck,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: sam.zweck",
      "givenName: Sam",
      "sn: Zweck",
      "cn: Sam Zweck",
      "",
      "dn: uid=lowercase.mcgee,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: lowercase.mcgee",
      "givenName: lowercase",
      "sn: mcgee",
      "cn: lowercase mcgee",
      "",
      "dn: uid=zorro,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: zorro",
      "sn: Zorro",
      "cn: Zorro"
    );
  }



  /**
   * Tests the first constructor for the request control with different sort
   * order values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor1() throws Exception
  {
    SortKey sortKey = new SortKey("givenName", false);
    List<SortKey> sortKeys = Arrays.asList(sortKey);
    new ServerSideSortRequestControl(sortKeys).toString();

    sortKey = new SortKey("givenName", true);
    sortKeys = Arrays.asList(sortKey);
    new ServerSideSortRequestControl(sortKeys).toString();

    sortKeys = Arrays.asList(
      new SortKey("sn", false),
      new SortKey("givenName", false));
    new ServerSideSortRequestControl(sortKeys).toString();
  }



  /**
   * Tests the second constructor for the request control with different sort
   * order strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor2() throws Exception
  {
    new ServerSideSortRequestControl("givenName").toString();
    new ServerSideSortRequestControl("givenName:caseIgnoreOrderingMatch").toString();
    new ServerSideSortRequestControl("+givenName").toString();
    new ServerSideSortRequestControl("-givenName").toString();
    new ServerSideSortRequestControl("givenName,sn").toString();
    new ServerSideSortRequestControl("givenName,+sn").toString();
    new ServerSideSortRequestControl("givenName,-sn").toString();
    new ServerSideSortRequestControl("+givenName,sn").toString();
    new ServerSideSortRequestControl("+givenName,+sn").toString();
    new ServerSideSortRequestControl("+givenName,-sn").toString();
    new ServerSideSortRequestControl("-givenName").toString();
    new ServerSideSortRequestControl("-givenName,+sn").toString();
    new ServerSideSortRequestControl("-givenName,-sn").toString();
    new ServerSideSortRequestControl("-givenName,-sn:caseExactOrderingMatch").toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameAscending()
         throws Exception
  {
    testInternalSearchWithSort("givenName",
        aaccfJohnsonDN,    // Aaccf
        aaronZimmermanDN,  // Aaron
        albertZimmermanDN, // Albert, lower entry ID
        albertSmithDN,     // Albert, higher entry ID
        lowercaseMcGeeDN,  // lowercase
        margaretJonesDN,   // Maggie
        maryJonesDN,       // Mary
        samZweckDN,        // Sam
        zorroDN);          // No first name
  }

  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName values using a specific
   * ordering matching rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameAscendingCaseExact() throws Exception
  {
    testInternalSearchWithSort("givenName:caseExactOrderingMatch",
        aaccfJohnsonDN,    // Aaccf
        aaronZimmermanDN,  // Aaron
        albertZimmermanDN, // Albert, lower entry ID
        albertSmithDN,     // Albert, higher entry ID
        margaretJonesDN,   // Maggie
        maryJonesDN,       // Mary
        samZweckDN,        // Sam
        lowercaseMcGeeDN,  // lowercase
        zorroDN);          // No first name
  }

  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of descending givenName values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameDescending() throws Exception
  {
    testInternalSearchWithSort("-givenName",
        zorroDN,           // No first name
        samZweckDN,        // Sam
        maryJonesDN,       // Mary
        margaretJonesDN,   // Margaret
        lowercaseMcGeeDN,  // lowercase
        albertZimmermanDN, // Albert, lower entry ID
        albertSmithDN,     // Albert, higher entry ID
        aaronZimmermanDN,  // Aaron
        aaccfJohnsonDN);   // Aaccf
  }

  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of descending givenName values using a specific
   * ordering matching rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameDescendingCaseExact() throws Exception
  {
    testInternalSearchWithSort("-givenName:caseExactOrderingMatch",
        zorroDN,           // No first name
        lowercaseMcGeeDN,  // lowercase
        samZweckDN,        // Sam
        maryJonesDN,       // Mary
        margaretJonesDN,   // Margaret
        albertZimmermanDN, // Albert, lower entry ID
        albertSmithDN,     // Albert, higher entry ID
        aaronZimmermanDN,  // Aaron
        aaccfJohnsonDN);   // Aaccf
  }

  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName and ascending sn values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameAscendingSnAscending() throws Exception
  {
    testInternalSearchWithSort("givenName,sn",
        aaccfJohnsonDN,    // Aaccf
        aaronZimmermanDN,  // Aaron
        albertSmithDN,     // Albert, lower sn
        albertZimmermanDN, // Albert, higher sn
        lowercaseMcGeeDN,  // lowercase
        margaretJonesDN,   // Maggie
        maryJonesDN,       // Mary
        samZweckDN,        // Sam
        zorroDN);          // No first name
  }

  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName and descending sn values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchGivenNameAscendingSnDescending()
         throws Exception
  {
    testInternalSearchWithSort("givenName,-sn",
        aaccfJohnsonDN,    // Aaccf
        aaronZimmermanDN,  // Aaron
        albertZimmermanDN, // Albert, higher sn
        albertSmithDN,     // Albert, lower sn
        lowercaseMcGeeDN,  // lowercase
        margaretJonesDN,   // Maggie
        maryJonesDN,       // Mary
        samZweckDN,        // Sam
        zorroDN);          // No first name
  }

  private void testInternalSearchWithSort(String sortOrderString, DN... expectedDNOrder) throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl(sortOrderString));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    assertEquals(getDNs(internalSearch.getSearchEntries()), Arrays.asList(expectedDNOrder));

    assertNoAttributeTypeForSort(internalSearch);
  }

  /**
   * Tests performing an internal search using the CRITICAL server-side sort control with
   * an undefined attribute type.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testCriticalSortWithUndefinedAttribute() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl(true, "undefined"));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
  }



  /**
   * Tests performing an internal search using the server-side sort control with
   * an undefined ordering rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchUndefinedOrderingRule() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl(true, "givenName:undefinedOrderingMatch"));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertNotEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Tests performing an internal search using the non-critical server-side
   * sort control to sort the entries
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testNonCriticalSortWithUndefinedAttribute() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl(false, "bad_sort:caseExactOrderingMatch"));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    ServerSideSortResponseControl responseControl = getServerSideSortResponseControl(internalSearch);
    assertEquals(responseControl.getResultCode(), 16);
  }

  private void assertNoAttributeTypeForSort(InternalSearchOperation internalSearch) throws Exception
  {
    ServerSideSortResponseControl responseControl = getServerSideSortResponseControl(internalSearch);
    assertEquals(responseControl.getResultCode(), ResultCode.SUCCESS.intValue());
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }

  private ServerSideSortResponseControl getServerSideSortResponseControl(InternalSearchOperation internalSearch)
      throws Exception
  {
    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    return getServerSideSortResponseControl(responseControls);
  }

  private ServerSideSortResponseControl getServerSideSortResponseControl(List<Control> responseControls)
      throws DirectoryException
  {
    Control c = responseControls.get(0);
    if (c instanceof ServerSideSortResponseControl)
    {
      return (ServerSideSortResponseControl) c;
    }
    return ServerSideSortResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
  }

  private ArrayList<DN> getDNs(LinkedList<SearchResultEntry> searchEntries)
  {
    ArrayList<DN> results = new ArrayList<>();
    for (Entry e : searchEntries)
    {
      results.add(e.getName());
    }
    return results;
  }
}
