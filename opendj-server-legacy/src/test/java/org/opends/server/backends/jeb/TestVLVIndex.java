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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.*;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class TestVLVIndex extends DirectoryServerTestCase {
  private SortOrder sortOrder;

  private String beID="indexRoot";
  private BackendImpl be;

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
  /** The DN for suffix. */
  private DN suffixDN;

  private TreeSet<SortValues> expectedSortedValues;
  private List<Entry> entries;

  @BeforeClass
  public void setUp() throws Exception {
    TestCaseUtils.startServer();
    TestCaseUtils.enableBackend(beID);

    SortKey[] sortKeys = new SortKey[3];
    sortKeys[0] = new SortKey(DirectoryServer.getAttributeType("givenname"), true);
    sortKeys[1] = new SortKey(DirectoryServer.getAttributeType("sn"),
                              false);
    sortKeys[2] = new SortKey(
        DirectoryServer.getAttributeType("uid"), true);
    sortOrder = new SortOrder(sortKeys);

    aaccfJohnsonDN    = DN.valueOf("uid=aaccf.johnson,dc=vlvtest,dc=com");
    aaronZimmermanDN  = DN.valueOf("uid=aaron.zimmerman,dc=vlvtest,dc=com");
    albertSmithDN     = DN.valueOf("uid=albert.smith,dc=vlvtest,dc=com");
    albertZimmermanDN = DN.valueOf("uid=albert.zimmerman,dc=vlvtest,dc=com");
    lowercaseMcGeeDN  = DN.valueOf("uid=lowercase.mcgee,dc=vlvtest,dc=com");
    margaretJonesDN   = DN.valueOf("uid=margaret.jones,dc=vlvtest,dc=com");
    maryJonesDN       = DN.valueOf("uid=mary.jones,dc=vlvtest,dc=com");
    samZweckDN        = DN.valueOf("uid=sam.zweck,dc=vlvtest,dc=com");
    zorroDN           = DN.valueOf("uid=zorro,dc=vlvtest,dc=com");
    suffixDN          = DN.valueOf("dc=vlvtest,dc=com");

    expectedSortedValues = new TreeSet<>();

    entries = TestCaseUtils.makeEntries(
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
  }

  @AfterClass
  public void cleanUp() throws Exception {
      TestCaseUtils.clearJEBackend(beID);
      TestCaseUtils.disableBackend(beID);
  }

  /**
   * Populates the JE DB with a set of test data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void populateDB()
      throws Exception
  {
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
  public void testDel() throws Exception
  {
    populateDB();

    TestCaseUtils.deleteEntry(entries.get(1));
    TestCaseUtils.deleteEntry(entries.get(entries.size() - 1));

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(DN.valueOf("dc=vlvtest,dc=com"));

    for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if(vlvIndex.getName().contains("testvlvindex"))
      {
        SortValues sv1 = expectedSortedValues.first();
        SortValuesSet svs1 = getSortValuesSet(vlvIndex, sv1);
        assertEquals(svs1.size(), 3);

        SortValues sv2 = expectedSortedValues.last();
        SortValuesSet svs2 = getSortValuesSet(vlvIndex, sv2);
        assertEquals(svs2.size(), 5);
      }
    }

    for(int i = 2; i <= entries.size() - 2; i++)
    {
      TestCaseUtils.deleteEntry(entries.get(i));
    }
    // Delete the base entry
    TestCaseUtils.deleteEntry(entries.get(0));

    for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if(vlvIndex.getName().contains("testvlvindex"))
      {
        SortValues sv1 = expectedSortedValues.first();
        SortValuesSet svs1 = getSortValuesSet(vlvIndex, sv1);
        assertEquals(svs1.size(), 0);
        assertNull(svs1.getKeyBytes());

        SortValues sv2 = expectedSortedValues.last();
        SortValuesSet svs2 = getSortValuesSet(vlvIndex, sv2);
        assertEquals(svs2.size(), 0);
        assertNull(svs2.getKeyBytes());
      }
    }
  }

  private SortValuesSet getSortValuesSet(VLVIndex vlvIndex, SortValues sv)
      throws DirectoryException
  {
    SortValuesSet result =
        vlvIndex.getSortValuesSet(null, 0, sv.getValues(), sv.getTypes());
    assertNotNull(result);
    return result;
  }

  @Test( dependsOnMethods = { "testDel" } )
  public void testAdd() throws Exception
  {
    populateDB();
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(DN.valueOf("dc=vlvtest,dc=com"));

    for(VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if(vlvIndex.getName().contains("testvlvindex"))
      {
        SortValues sv1 = expectedSortedValues.first();
        SortValuesSet svs1 = getSortValuesSet(vlvIndex, sv1);
        assertEquals(svs1.size(), 4);

        SortValues sv2 = expectedSortedValues.last();
        SortValuesSet svs2 = getSortValuesSet(vlvIndex, sv2);
        assertEquals(svs2.size(), 6);

        int i = 0;
        for(SortValues values : expectedSortedValues)
        {
          ByteString[] attrValues = values.getValues();
          AttributeType[] attrTypes = values.getTypes();
          for(int j = 0; j < attrValues.length; j++)
          {
            ByteString value;
            if(i < 4)
            {
              value = svs1.getValue(i * 3 + j);
            }
            else
            {
              value = svs2.getValue((i - 4) * 3 + j);
            }
            ByteString oValue = null;
            if(attrValues[j] != null)
            {
              MatchingRule eqRule = attrTypes[j].getEqualityMatchingRule();
              oValue = eqRule.normalizeAttributeValue(attrValues[j]);
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
  public void testInternalSearchByOffsetOneOffset() throws Exception
  {
    SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, 1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder());

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 1);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  private ArrayList<DN> getDNs(InternalSearchOperation internalSearch)
  {
    ArrayList<DN> results = new ArrayList<>();
    for (Entry e : internalSearch.getSearchEntries())
    {
      results.add(e.getName());
    }
    return results;
  }

  private List<DN> expectedDNOrder()
  {
    return Arrays.asList(
        aaccfJohnsonDN,    // Aaccf
        aaronZimmermanDN,  // Aaron
        albertZimmermanDN, // Albert, bigger
        albertSmithDN);    // Albert, smaller sn
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an offset of zero, which should be treated like
   * an offset of one.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByOffsetZeroOffset() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, 0, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder());

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByOffsetThreeOffset() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, 3, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, bigger
    expectedDNOrder.add(albertSmithDN);     // Albert, smaller sn
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByOffsetNegativeOffset() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, -1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
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
  public void testInternalSearchByOffsetNegativeStartPosition() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(3, 3, 1, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because it's not a critical control.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
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
  public void testInternalSearchByOffsetStartPositionTooHigh() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(3, 3, 30, 0));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
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
  public void testInternalSearchByOffsetIncompleteAfterCount() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 4, 7, 0));

    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(maryJonesDN);       // Mary
    expectedDNOrder.add(samZweckDN);        // Sam
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
      }
    }

    assertNotNull(sortResponse);
    assertEquals(sortResponse.getResultCode(), 0);

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(), 0);
    assertEquals(vlvResponse.getTargetPosition(), 7);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

  private VLVResponseControl asVLVResponseControl(Control c) throws DirectoryException
  {
    if(c instanceof LDAPControl)
    {
      return VLVResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (VLVResponseControl) c;
  }

  private ServerSideSortResponseControl asServerSideSortResponseControl(Control c) throws DirectoryException
  {
    if(c instanceof LDAPControl)
    {
      return ServerSideSortResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (ServerSideSortResponseControl) c;
  }

  /**
   * Tests performing an internal search using the VLV control to retrieve a
   * subset of the entries using an assertion value before any actual value in
   * the list.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  @Test( dependsOnMethods = { "testAdd" } )
  public void testInternalSearchByValueBeforeAll() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("a")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder());

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByValueMatchesFirst() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("aaccf")));

    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder());

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByValueMatchesThird() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("albert")));

    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByValueMatchesThirdWithBeforeCount() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(1, 3, ByteString.valueOf("albert")));

    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(aaronZimmermanDN);  // Aaron
    expectedDNOrder.add(albertZimmermanDN); // Albert, lower entry ID
    expectedDNOrder.add(albertSmithDN);     // Albert, higher entry ID
    expectedDNOrder.add(lowercaseMcGeeDN);  // lowercase
    expectedDNOrder.add(margaretJonesDN);   // Maggie

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);
    assertEquals(responseControls.size(), 2);

    ServerSideSortResponseControl sortResponse = null;
    VLVResponseControl            vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        sortResponse = asServerSideSortResponseControl(c);
      }
      else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
      else
      {
        Assert.fail("Response control with unexpected OID " + c.getOID());
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
  public void testInternalSearchByValueAfterAll() throws Exception
  {
    final SearchRequest request = Requests.newSearchRequest(DN.valueOf("dc=vlvtest,dc=com"), SearchScope.WHOLE_SUBTREE)
        .addControl(new ServerSideSortRequestControl(sortOrder))
        .addControl(new VLVRequestControl(0, 3, ByteString.valueOf("zz")));
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    // It will be successful because the control isn't critical.
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);

    // Null values for given name are still bigger then zz
    ArrayList<DN> expectedDNOrder = new ArrayList<>();
    expectedDNOrder.add(zorroDN);           // No first name
    expectedDNOrder.add(suffixDN);          // No sort attributes

    assertThat(getDNs(internalSearch)).isEqualTo(expectedDNOrder);

    List<Control> responseControls = internalSearch.getResponseControls();
    assertNotNull(responseControls);

    VLVResponseControl vlvResponse  = null;
    for (Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        vlvResponse = asVLVResponseControl(c);
      }
    }

    assertNotNull(vlvResponse);
    assertEquals(vlvResponse.getVLVResultCode(),
                 LDAPResultCode.SUCCESS);
    assertEquals(vlvResponse.getTargetPosition(), 9);
    assertEquals(vlvResponse.getContentCount(), 10);
  }

}
