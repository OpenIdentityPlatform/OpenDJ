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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import static org.opends.server.util.ServerConstants.OID_SERVER_SIDE_SORT_RESPONSE_CONTROL;
import static org.opends.server.util.ServerConstants.OID_VLV_RESPONSE_CONTROL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class contains a number of test cases for the virtual list view request
 * and response controls.
 */
public class VLVControlTestCase
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
   * Tests the first constructor for the request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor1()
              throws Exception
  {
    VLVRequestControl vlvRequest = new VLVRequestControl(0, 9, 1, 0);

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the second constructor for the request control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor2NullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest = new VLVRequestControl(0, 9, 1, 0, null);

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the second constructor for the request control with a non-null
   * context  ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor2NonNullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(0, 9, 1, 0, new ASN1OctetString("foo"));

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNotNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the third constructor for the request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor3()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(0, 9, new ASN1OctetString("a"));

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().stringValue(),
                 "a");
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the fourth constructor for the request control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor4NullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(0, 9, new ASN1OctetString("a"), null);

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().stringValue(),
                 "a");
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the fourth constructor for the request control with a non-null
   * context ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequestConstructor4NonNullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(0, 9, new ASN1OctetString("a"),
                               new ASN1OctetString("foo"));

    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().stringValue(),
                 "a");
    assertNotNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());

    assertNotNull(vlvRequest.decodeControl(vlvRequest));
  }



  /**
   * Tests the first constructor for the response control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResponseConstructor1()
         throws Exception
  {
    VLVResponseControl vlvResponse = new VLVResponseControl(0, 15, 0);

    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());

    assertNotNull(vlvResponse.decodeControl(vlvResponse));
  }



  /**
   * Tests the second constructor for the response control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResponseConstructor2NullContextID()
         throws Exception
  {
    VLVResponseControl vlvResponse = new VLVResponseControl(0, 15, 0, null);

    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());

    assertNotNull(vlvResponse.decodeControl(vlvResponse));
  }



  /**
   * Tests the second constructor for the response control with a non-null
   * context ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResponseConstructor2NonNullContextID()
         throws Exception
  {
    VLVResponseControl vlvResponse =
         new VLVResponseControl(0, 15, 0, new ASN1OctetString("foo"));

    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNotNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());

    assertNotNull(vlvResponse.decodeControl(vlvResponse));
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByOffsetOneOffset()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, 1, 0));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of zero, which should be treated like
   * an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByOffsetZeroOffset()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, 0, 0));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset that isn't at the beginning of the
   * result set but is still completely within the bounds of that set.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByOffsetThreeOffset()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, 3, 0));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control with a negative
   * target offset.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByOffsetNegativeOffset()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, -1, 0));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
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
  @Test()
  public void testInternalSearchByOffsetNegativeStartPosition()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(3, 3, 1, 0));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
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
  @Test()
  public void testInternalSearchByOffsetStartPositionTooHigh()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(3, 3, 30, 0));

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
    assertEquals(vlvResponse.getTargetPosition(), 10);
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control with a start
   * start position within the bounds of the list but not enough remaining
   * entries to meet the afterCount
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByOffsetIncompleteAfterCount()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, 7, 0));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value before any actual value in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByValueBeforeAll()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3, new ASN1OctetString("a")));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the first value
   * in the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByValueMatchesFirst()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3,
                                              new ASN1OctetString("aaccf")));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the third value
   * in the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByValueMatchesThird()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(0, 3,
                                              new ASN1OctetString("albert")));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that matches the third value
   * in the list and includes a nonzero before count.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByValueMatchesThirdWithBeforeCount()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("givenName"));
    requestControls.add(new VLVRequestControl(1, 3,
                                              new ASN1OctetString("albert")));

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
    assertEquals(vlvResponse.getContentCount(), 9);
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that is after all values in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test()
  public void testInternalSearchByValueAfterAll()
         throws Exception
  {
    populateDB();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("sn"));
    requestControls.add(new VLVRequestControl(0, 3, new ASN1OctetString("zz")));

    InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=person)"),
                  null, null);

    internalSearch.run();

    // It will be successful because the control isn't critical.
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
                 LDAPResultCode.SUCCESS);
    assertEquals(vlvResponse.getTargetPosition(), 10);
    assertEquals(vlvResponse.getContentCount(), 9);
  }
}

