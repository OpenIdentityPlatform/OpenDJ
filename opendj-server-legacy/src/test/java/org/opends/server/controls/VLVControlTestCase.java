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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.controls;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This class contains a number of test cases for the virtual list view request
 * and response controls.
 */
public class VLVControlTestCase
    extends ControlsTestCase
{
  /** The givenName attribute type. */
  private AttributeType givenNameType;
  /** The sn attribute type. */
  private AttributeType snType;

  /** The DN for "Aaccf Johnson". */
  private DN aaccfJohnsonDN;
  /** The DN for "Aaron Zimmerman". */
  private DN aaronZimmermanDN;
  /** The DN for "Albert Smith". */
  private DN albertSmithDN;
  /** The DN for "Albert Zimmerman". */
  private DN albertZimmermanDN;
  /** The DN for "lowercase mcgee". */
  private DN lowercaseMcGeeDN;
  /** The DN for "Mararet Jones". */
  private DN margaretJonesDN;
  /** The DN for "Mary Jones". */
  private DN maryJonesDN;
  /** The DN for "Sam Zweck". */
  private DN samZweckDN;
  /** The DN for "Zorro". */
  private DN zorroDN;



  /**
   * Make sure that the server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    givenNameType = DirectoryServer.getAttributeType("givenname");
    assertNotNull(givenNameType);

    snType = DirectoryServer.getAttributeType("sn");
    assertNotNull(snType);

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
  private void populateDB()
          throws Exception
  {
    TestCaseUtils.clearJEBackend("userRoot", "dc=example,dc=com");

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
  @Test
  public void testRequestConstructor1()
              throws Exception
  {
    VLVRequestControl vlvRequest = new VLVRequestControl(0, 9, 1, 0);

    assertEquals(vlvRequest.isCritical(), false);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());
  }



  /**
   * Tests the second constructor for the request control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor2NullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest = new VLVRequestControl(true, 0, 9, 1, 0, null);

    assertEquals(vlvRequest.isCritical(), true);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());
  }



  /**
   * Tests the second constructor for the request control with a non-null
   * context  ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor2NonNullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(true, 0, 9, 1, 0, ByteString.valueOf("foo"));

    assertEquals(vlvRequest.isCritical(), true);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getOffset(), 1);
    assertEquals(vlvRequest.getContentCount(), 0);
    assertNotNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_BYOFFSET);
    assertNull(vlvRequest.getGreaterThanOrEqualAssertion());
    assertNotNull(vlvRequest.toString());
  }



  /**
   * Tests the third constructor for the request control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor3()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(0, 9, ByteString.valueOf("a"));

    assertEquals(vlvRequest.isCritical(), false);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().toString(),
                 "a");
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());
  }



  /**
   * Tests the fourth constructor for the request control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor4NullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(true, 0, 9, ByteString.valueOf("a"), null);

    assertEquals(vlvRequest.isCritical(), true);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().toString(),
                 "a");
    assertNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());
  }



  /**
   * Tests the fourth constructor for the request control with a non-null
   * context ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRequestConstructor4NonNullContextID()
              throws Exception
  {
    VLVRequestControl vlvRequest =
         new VLVRequestControl(true, 0, 9, ByteString.valueOf("a"),
                               ByteString.valueOf("foo"));

    assertEquals(vlvRequest.isCritical(), true);
    assertEquals(vlvRequest.getBeforeCount(), 0);
    assertEquals(vlvRequest.getAfterCount(), 9);
    assertEquals(vlvRequest.getGreaterThanOrEqualAssertion().toString(),
                 "a");
    assertNotNull(vlvRequest.getContextID());
    assertEquals(vlvRequest.getTargetType(),
                 VLVRequestControl.TYPE_TARGET_GREATERTHANOREQUAL);
    assertNotNull(vlvRequest.toString());
  }

  /**
   * Tests the ASN.1 encoding for the response control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testASN1ValueEncoding()
         throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    VLVResponseControl vlvResponse = new VLVResponseControl(true, 0, 15, 0,
        ByteString.valueOf("foo"));
    vlvResponse.writeValue(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    // Should start as an octet string with a nested sequence
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
    reader.readStartSequence();
    // Should be an sequence start
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_SEQUENCE_TYPE);
    reader.readStartSequence();
    // Should be an integer with targetPosition
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_INTEGER_TYPE);
    assertEquals(reader.readInteger(), 0);
    // Should be an integer with contentCount
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_INTEGER_TYPE);
    assertEquals(reader.readInteger(), 15);
    // Should be an enumerated with virtualListViewResult
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_ENUMERATED_TYPE);
    assertEquals(reader.readEnumerated(), 0);
    // Should be an octet string with contextID
    assertEquals(reader.peekType(), ASN1.UNIVERSAL_OCTET_STRING_TYPE);
    assertEquals(reader.readOctetStringAsString(), "foo");
  }

  /**
   * Tests the first constructor for the response control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testResponseConstructor1()
         throws Exception
  {
    VLVResponseControl vlvResponse = new VLVResponseControl(0, 15, 0);

    assertEquals(vlvResponse.isCritical(), false);
    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());
  }



  /**
   * Tests the second constructor for the response control with a null context
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testResponseConstructor2NullContextID()
         throws Exception
  {
    VLVResponseControl vlvResponse = new VLVResponseControl(true, 0, 15, 0, null);

    assertEquals(vlvResponse.isCritical(), true);
    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());
  }



  /**
   * Tests the second constructor for the response control with a non-null
   * context ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testResponseConstructor2NonNullContextID()
         throws Exception
  {
    VLVResponseControl vlvResponse =
         new VLVResponseControl(true, 0, 15, 0, ByteString.valueOf("foo"));

    assertEquals(vlvResponse.isCritical(), true);
    assertEquals(vlvResponse.getTargetPosition(), 0);
    assertEquals(vlvResponse.getContentCount(), 15);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertNotNull(vlvResponse.getContextID());
    assertNotNull(vlvResponse.toString());
  }



  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchByOffsetOneOffset() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, 1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByOffsetZeroOffset()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, 0, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByOffsetThreeOffset()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, 3, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByOffsetNegativeOffset()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, -1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertEquals(vlvResponse.getVLVResultCode(),
                 LDAPResultCode.OFFSET_RANGE_ERROR);
  }



  /**
   * Tests performing an internal search using the VLV control with an offset of
   * one but a beforeCount that puts the start position at a negative value.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchByOffsetNegativeStartPosition()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(3, 3, 1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertEquals(vlvResponse.getVLVResultCode(), LDAPResultCode.SUCCESS);
  }



  /**
   * Tests performing an internal search using the VLV control with a start
   * start position beyond the end of the result set.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchByOffsetStartPositionTooHigh() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(3, 3, 30, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
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
  @Test
  public void testInternalSearchByOffsetIncompleteAfterCount() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, 7, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByValueBeforeAll()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("a")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByValueMatchesFirst()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("aaccf")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaccfJohnsonDN);    // Aaccf
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID

    ArrayList<DN> returnedDNOrder = getDNs(internalSearch.getSearchEntries());

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
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByValueMatchesThird()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("albert")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    ArrayList<DN> returnedDNOrder = getDNs(internalSearch.getSearchEntries());

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
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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
  @Test
  public void testInternalSearchByValueMatchesThirdWithBeforeCount()
         throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("givenName"))
        .addControl(new VLVRequestControl(1, 3, ByteString.valueOf("albert")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    assertEquals(getDNs(internalSearch.getSearchEntries()), expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = getServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = getVLVResponseControl(c);
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

  private ArrayList<DN> getDNs(LinkedList<SearchResultEntry> entries)
  {
    ArrayList<DN> results = new ArrayList<>();
    for (Entry e : entries)
    {
      results.add(e.getName());
    }
    return results;
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value that is after all values in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test
  public void testInternalSearchByValueAfterAll() throws Exception
  {
    populateDB();

    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl("sn"))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("zz")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because the control isn't critical.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertEquals(vlvResponse.getVLVResultCode(),
                 LDAPResultCode.SUCCESS);
    assertEquals(vlvResponse.getTargetPosition(), 10);
    assertEquals(vlvResponse.getContentCount(), 9);
  }

  private ServerSideSortResponseControl getServerSideSortResponseControl(Control c) throws DirectoryException
  {
    if (c instanceof LDAPControl)
    {
      return ServerSideSortResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (ServerSideSortResponseControl) c;
  }

  private VLVResponseControl getVLVResponseControl(List<Control> responseControls) throws DirectoryException
  {
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        return getVLVResponseControl(c);
      }
    }
    fail("Expected to find VLVResponseControl");
    return null;
  }

  private VLVResponseControl getVLVResponseControl(Control c) throws DirectoryException
  {
    if (c instanceof LDAPControl)
    {
      return VLVResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (VLVResponseControl) c;
  }
}
