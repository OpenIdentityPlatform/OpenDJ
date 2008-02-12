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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SortKey;
import org.opends.server.types.SortOrder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class contains a number of test cases for the server side sort request
 * and response controls.
 */
public class ServerSideSortControlTestCase
    extends ControlsTestCase
{
  // The givenName attribute type.
  private AttributeType givenNameType;

  // The sn attribute type.
  private AttributeType snType;

  // The DN for "Aaccf Johnson"
  DN aaccfJohnsonDN;

  // The DN for "Aaron Zimmerman"
  DN aaronZimmermanDN;

  // The DN for "Albert Smith"
  DN albertSmithDN;

  // The DN for "Albert Zimmerman"
  DN albertZimmermanDN;

  // The DN for "lowercase mcgee"
  DN lowercaseMcGeeDN;

  // The DN for "Mararet Jones"
  DN margaretJonesDN;

  // The DN for "Mary Jones"
  DN maryJonesDN;

  // The DN for "Sam Zweck"
  DN samZweckDN;

  // The DN for "Zorro"
  DN zorroDN;



  /**
   * Make sure that the server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    givenNameType = DirectoryServer.getAttributeType("givenname", false);
    assertNotNull(givenNameType);

    snType = DirectoryServer.getAttributeType("sn", false);
    assertNotNull(snType);

    aaccfJohnsonDN    = DN.decode("uid=aaccf.johnson,dc=example,dc=com");
    aaronZimmermanDN  = DN.decode("uid=aaron.zimmerman,dc=example,dc=com");
    albertSmithDN     = DN.decode("uid=albert.smith,dc=example,dc=com");
    albertZimmermanDN = DN.decode("uid=albert.zimmerman,dc=example,dc=com");
    lowercaseMcGeeDN  = DN.decode("uid=lowercase.mcgee,dc=example,dc=com");
    margaretJonesDN   = DN.decode("uid=margaret.jones,dc=example,dc=com");
    maryJonesDN       = DN.decode("uid=mary.jones,dc=example,dc=com");
    samZweckDN        = DN.decode("uid=sam.zweck,dc=example,dc=com");
    zorroDN           = DN.decode("uid=zorro,dc=example,dc=com");
  }



  /**
   * Populates the JE DB with a set of test data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void populateDB()
          throws Exception
  {
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

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
  @Test()
  public void testRequestConstructor1()
              throws Exception
  {
    SortKey sortKey = new SortKey(givenNameType, true);
    SortOrder sortOrder = new SortOrder(sortKey);
    new ServerSideSortRequestControl(sortOrder).toString();
    sortKey.toString();
    sortOrder.toString();

    sortKey = new SortKey(givenNameType, false);
    sortOrder = new SortOrder(sortKey);
    new ServerSideSortRequestControl(sortOrder).toString();
    sortKey.toString();
    sortOrder.toString();

    SortKey[] sortKeys =
    {
      new SortKey(snType, true),
      new SortKey(givenNameType, true)
    };
    sortOrder = new SortOrder(sortKeys);
    new ServerSideSortRequestControl(sortOrder).toString();
    sortOrder.toString();
  }



  /**
   * Tests the second constructor for the request control with different sort
   * order strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor2()
              throws Exception
  {
    new ServerSideSortRequestControl("givenName").toString();
    new ServerSideSortRequestControl("givenName:caseIgnoreOrderingMatch").
             toString();
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
    new ServerSideSortRequestControl("-givenName,-sn:caseExactOrderingMatch").
             toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameAscending()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName values using a specific
   * ordering matching rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameAscendingCaseExact()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(
                                 "givenName:caseExactOrderingMatch"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(margaretJonesDN);   // Maggie
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of descending givenName values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameDescending()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("-givenName"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaronZimmermanDN);  // Zeke
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(margaretJonesDN);   // Margaret
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of descending givenName values using a specific
   * ordering matching rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameDescendingCaseExact()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(
                                 "-givenName:caseExactOrderingMatch"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(aaronZimmermanDN);  // Zeke
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(margaretJonesDN);   // Margaret
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName and ascending sn values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameAscendingSnAscending()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName,sn"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertSmithDN);     // Albert, lower sn
    expectedDNOrder.add(albertZimmermanDN); // Albert, higher sn
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control to
   * sort the entries in order of ascending givenName and descending sn values.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchGivenNameAscendingSnDescending()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName,-sn"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, higher sn
    expectedDNOrder.add(albertSmithDN);     // Albert, lower sn
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 1);

    ServerSideSortResponseControl responseControl =
         ServerSideSortResponseControl.decodeControl(responseControls.get(0));
    assertEquals(responseControl.getResultCode(), 0);
    assertNull(responseControl.getAttributeType());
    responseControl.toString();
  }



  /**
   * Tests performing an internal search using the server-side sort control with
   * an undefined attribute type.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchUndefinedAttribute()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("undefined"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertFalse(internalSearch.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests performing an internal search using the server-side sort control with
   * an undefined ordering rule.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchUndefinedOrderingRule()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(
                                 "givenName:undefinedOrderingMatch"));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();
    assertFalse(internalSearch.getResultCode() == ResultCode.SUCCESS);
  }
}

