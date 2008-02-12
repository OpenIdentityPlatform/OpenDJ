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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import static org.opends.server.util.ServerConstants.OID_SERVER_SIDE_SORT_RESPONSE_CONTROL;
import static org.opends.server.util.ServerConstants.OID_VLV_RESPONSE_CONTROL;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import java.util.*;

public class TestVLVIndex extends DirectoryServerTestCase {
  SortOrder sortOrder;

  private  String beID="indexRoot";
  private BackendImpl be;

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

  // The DN for suffix
  DN suffixDN;

  TreeSet<SortValues> expectedSortedValues;

  @BeforeClass
  public void setUp() throws Exception {
    TestCaseUtils.startServer();

    SortKey[] sortKeys = new SortKey[3];
    sortKeys[0] = new SortKey(DirectoryServer.getAttributeType("givenname"), true);
    sortKeys[1] = new SortKey(DirectoryServer.getAttributeType("sn"),
                              false);
    sortKeys[2] = new SortKey(
        DirectoryServer.getAttributeType("uid"), true);
    sortOrder = new SortOrder(sortKeys);

    aaccfJohnsonDN    = DN.decode("uid=aaccf.johnson,dc=vlvtest,dc=com");
    aaronZimmermanDN  = DN.decode("uid=aaron.zimmerman,dc=vlvtest,dc=com");
    albertSmithDN     = DN.decode("uid=albert.smith,dc=vlvtest,dc=com");
    albertZimmermanDN = DN.decode("uid=albert.zimmerman,dc=vlvtest,dc=com");
    lowercaseMcGeeDN  = DN.decode("uid=lowercase.mcgee,dc=vlvtest,dc=com");
    margaretJonesDN   = DN.decode("uid=margaret.jones,dc=vlvtest,dc=com");
    maryJonesDN       = DN.decode("uid=mary.jones,dc=vlvtest,dc=com");
    samZweckDN        = DN.decode("uid=sam.zweck,dc=vlvtest,dc=com");
    zorroDN           = DN.decode("uid=zorro,dc=vlvtest,dc=com");
    suffixDN          = DN.decode("dc=vlvtest,dc=com");

    expectedSortedValues = new TreeSet<SortValues>();
  }

  @AfterClass
  public void cleanUp() throws Exception {
  }

  /**
   * Populates the JE DB with a set of test data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void populateDB()
      throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
        "dn: dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: domain",
        "",
        "dn: uid=albert.zimmerman,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: albert.zimmerman",
        "givenName: Albert",
        "sn: Zimmerman",
        "cn: Albert Zimmerman",
        "",
        "dn: uid=albert.smith,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: albert.smith",
        "givenName: Albert",
        "sn: Smith",
        "cn: Albert Smith",
        "",
        "dn: uid=aaron.zimmerman,dc=vlvtest,dc=com",
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
        "dn: uid=mary.jones,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: mary.jones",
        "givenName: Mary",
        "sn: Jones",
        "cn: Mary Jones",
        "",
        "dn: uid=margaret.jones,dc=vlvtest,dc=com",
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
        "dn: uid=aaccf.johnson,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: aaccf.johnson",
        "givenName: Aaccf",
        "sn: Johnson",
        "cn: Aaccf Johnson",
        "",
        "dn: uid=sam.zweck,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: sam.zweck",
        "givenName: Sam",
        "sn: Zweck",
        "cn: Sam Zweck",
        "",
        "dn: uid=lowercase.mcgee,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: lowercase.mcgee",
        "givenName: lowercase",
        "sn: mcgee",
        "cn: lowercase mcgee",
        "",
        "dn: uid=zorro,dc=vlvtest,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: zorro",
        "sn: Zorro",
        "cn: Zorro"
    );

    long id = 1;
    for(Entry entry : entries)
    {
      TestCaseUtils.addEntry(entry);
      expectedSortedValues.add(new SortValues(new EntryID(id), entry,
                                              sortOrder));
      id++;
    }
  }


  @Test
  public void testAdd() throws Exception
  {
    populateDB();
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(DN.decode("dc=vlvtest,dc=com"));

    for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if(vlvIndex.getName().contains("testvlvindex"))
      {


        SortValuesSet svs1 =
            vlvIndex.getSortValuesSet(null, 0,
                                      expectedSortedValues.first().getValues());

        assertNotNull(svs1);
        assertEquals(svs1.size(), 4);

        SortValuesSet svs2 =
            vlvIndex.getSortValuesSet(null, 0,
                                      expectedSortedValues.last().getValues());

        assertNotNull(svs2);
        assertEquals(svs2.size(), 6);

        int i = 0;
        for(SortValues values : expectedSortedValues)
        {
          for(int j = 0; j < values.getValues().length; j++)
          {
            byte[] value;
            if(i < 4)
            {
              value = svs1.getValue(i * 3 + j);
            }
            else
            {
              value = svs2.getValue((i - 4) * 3 + j);
            }
            byte[] oValue = null;
            if(values.getValues()[j] != null)
            {
              oValue = values.getValues()[j].getNormalizedValueBytes();
            }
            assertEquals(value, oValue);
          }
          i++;
        }
      }
    }
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetOneOffset()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, 1, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, bigger
    expectedDNOrder.add(albertSmithDN);     // Albert, smaller sn

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 1);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of zero, which should be treated like
   * an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetZeroOffset()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, 0, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, bigger
    expectedDNOrder.add(albertSmithDN);     // Albert, smaller sn

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 1);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset that isn't at the beginning of the
   * result set but is still completely within the bounds of that set.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetThreeOffset()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, 3, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, bigger
    expectedDNOrder.add(albertSmithDN);     // Albert, smaller sn
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 3);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control with a negative
   * target offset.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetNegativeOffset()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, -1, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
    }

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(),
                 LDAPResultCode.OFFSET_RANGE_ERROR);
  }

  /**
   * Tests performing an internal search using the VLV control with an offset of
   * one but a beforeCount that puts the start position at a negative value.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetNegativeStartPosition()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(3, 3, 1, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
    }

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), LDAPResultCode.SUCCESS);
  }

  /**
   * Tests performing an internal search using the VLV control with a start
   * start position beyond the end of the result set.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetStartPositionTooHigh()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(3, 3, 30, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();

    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
    }

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), LDAPResultCode.SUCCESS);
    assertEquals(vlvResponse.getTargetPosition(), 11);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control with a start
   * start position within the bounds of the list but not enough remaining
   * entries to meet the afterCount
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetIncompleteAfterCount()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 4, 7, 0));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 7);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value before any actual value in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueBeforeAll()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, new ASN1OctetString("a")));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 1);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the first value
   * in the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueMatchesFirst()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3,
                                              new ASN1OctetString("aaccf")));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 1);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the third value
   * in the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueMatchesThird()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3,
                                              new ASN1OctetString("albert")));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 3);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the third value
   * in the list and includes a nonzero before count.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueMatchesThirdWithBeforeCount()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(1, 3,
                                              new ASN1OctetString("albert")));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = ServerSideSortResponseControl.decodeControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
      else
      {
        fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 3);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that is after all values in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueAfterAll()
      throws Exception
  {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl(sortOrder));
    requestControls.add(new VLVRequestControl(0, 3, new ASN1OctetString("zz")));

    InternalSearchOperation internalSearch =
        new InternalSearchOperation(conn, conn.nextOperationID(),
                                    conn.nextMessageID(), requestControls,
                                    DN.decode("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE,
                                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                                    SearchFilter.createFilterFromString("(objectClass=*)"),
                                    null, null);

    internalSearch.run();

    // It will be successful because the control isn't critical.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    // Null values for given name are still bigger then zz
    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      returnedDNOrder.add(e.getDN());
    }

    assertEquals(returnedDNOrder, expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = VLVResponseControl.decodeControl(c);
      }
    }

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(),
                 LDAPResultCode.SUCCESS);
    assertEquals(vlvResponse.getTargetPosition(), 9);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

}
