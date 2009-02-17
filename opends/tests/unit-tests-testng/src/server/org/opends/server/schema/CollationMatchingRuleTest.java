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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */


package org.opends.server.schema;

import java.util.ArrayList;
import static org.testng.Assert.*;

import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


/**
 * This Test Class tests various collation matching rules.
 */
public final class CollationMatchingRuleTest
        extends SchemaTestCase
{

  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    user1 = DN.decode("cn=user1,dc=example,dc=com");
    user2 = DN.decode("cn=user2,dc=example,dc=com");
    user3 = DN.decode("cn=user3,dc=example,dc=com");
    user4 = DN.decode("cn=user4,dc=example,dc=com");
  }


  /**
   * Test to verify an ADD of an entry containing international characters.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void addEntry() throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
            "dn: uid=user,o=test",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetorgperson",
            "objectClass: top",
            "departmentNumber:: w4NiYzExMQ==",
            "carLicense:: w6liZTI=",
            "uid: user",
            "cn:: U8ODbmNoZXo=",
            "sn:: UXXDqWJlYw==");
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath,
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    assertEquals(err,0);
  }



  /**
   * Test to search the collation equality matching rule using OID.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationEqualityUsingOID() throws Exception
  {
    //Search the collation rule with OID of en and no suffix in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("cn:1.3.6.1.4.1.42.2.27.9.4.34.1:=sanchez"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation equality matching rule using language Tag.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationEqualityUsingLanguageTag() throws Exception
  {
    //Search the collation rule with language tag of en and no suffix
    //in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("cn:en:=sanchez"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation Less than matching rule using OID and suffix.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationLTUsingOIDSuffix() throws Exception
  {
    //Search the collation rule with OID of es and suffix in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.
              decode("departmentnumber:1.3.6.1.4.1.42.2.27.9.4.49.1.1:=abc120"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation Less than Equal to matching rule using
   * Language Tag and suffix.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationLTEUsingLanguageSuffix() throws Exception
  {
    //Search the collation rule with tag of fr and suffix in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.
              decode("carLicense:fr.2:=ebe2"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation Greater than matching rule using language
   * tag.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationGTUsingLanguage() throws Exception
  {
    //Search the collation rule with tag of fr in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.
              decode("carLicense:fr.5:=ebe1"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation Greater than Equal to matching rule using
   * language tag.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationGTEUsingLanguage() throws Exception
  {
    //Search the collation rule with tag of es and suffix in the filter.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.
              decode("departmentnumber:es.4:=abc111"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned.
    assertNotNull(e);
  }



  /**
   * Test to search the collation substring matching rule using
   * language tag and suffix.
   */
  @Test(dependsOnMethods = {"org.opends.server.schema."+
                "CollationMatchingRuleTest.addEntry"})
  public void searchCollationSubstring() throws Exception
  {
    /*Search the collation rule with tag of en and suffix in the filter.
     *It searches for string quebec against the value of sn which is
     * Qu\u00e9bec.
     */
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              ByteString.valueOf("uid=user,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.
              decode("sn:en.6:=*u*bec"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    //An entry must be returned for sn=quebec.
    assertNotNull(e);
  }

  private DN user1;
  private DN user2 ;
  private DN user3;
  private DN user4;


  /**
   * Test to verify the Sort control works well with the Collation
   * Less-than-equal-to matching rule and French Locale.
   */
  @Test()
  public void testSortControlLTERule() throws Exception
  {
    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(user4);
    expectedDNOrder.add(user3);
    expectedDNOrder.add(user2);
    expectedDNOrder.add(user1);
    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("displayname:fr"));
    ValidateSortControl(expectedDNOrder,
            requestControls,
            "displayname:fr-FR.6:=A*");
  }


  /**
   * Test to verify the Sort control works with Collation equality
   * matching rule and Spanish locale.
   */
  @Test()
  public void testSortControlEQRule() throws Exception
  {
    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(user4);
    expectedDNOrder.add(user3);
    expectedDNOrder.add(user2);
    expectedDNOrder.add(user1);
    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("displayname:es"));
    ValidateSortControl(expectedDNOrder,
            requestControls,
            "displayname:es.6:=A*");
  }


  /**
   * Test to verify the Sort control works with Collation greater
   * than matching rule and English locale in a descending order.
   */
  @Test()
  public void testSortControlGTRule() throws Exception
  {
    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(user1);
    expectedDNOrder.add(user2);
    expectedDNOrder.add(user3);
    expectedDNOrder.add(user4);
    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("-displayname:en"));
    ValidateSortControl(expectedDNOrder,
            requestControls,
            "displayname:en-US.6:=A*");
  }



  /**
   * Tests the Sort control with the VLV control using a collation equality
   * matching rule.
   */
  @Test()
  public void testVLVSortControl() throws Exception
  {
    ArrayList<DN> expectedDNOrder = new ArrayList<DN>();
    expectedDNOrder.add(user4);
    expectedDNOrder.add(user3);
    expectedDNOrder.add(user2);
    expectedDNOrder.add(user1);
    ArrayList<Control> requestControls = new ArrayList<Control>();
    requestControls.add(new ServerSideSortRequestControl("displayname:fr"));
    requestControls.add(new VLVRequestControl(0, 4, 1, 0));
    ValidateSortControl(expectedDNOrder,
            requestControls,
            "objectclass=inetOrgPerson");
  }



  private void ValidateSortControl(ArrayList<DN> expectedDNOrder,
          ArrayList<Control> requestControls,
          String searchFilter) throws Exception
  {
    try
    {
      populateEntriesForControl();
      InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

      InternalSearchOperation internalSearch =
         new InternalSearchOperation(conn, InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(), requestControls,
                  DN.decode("dc=example,dc=com"), SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString(searchFilter),
                  null, null);

      internalSearch.run();
      assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
      ArrayList<DN> returnedDNOrder = new ArrayList<DN>();
      for (Entry e : internalSearch.getSearchEntries())
      {
        returnedDNOrder.add(e.getDN());
      }
     assertEquals(returnedDNOrder, expectedDNOrder);
    }
    finally
    {
      TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");
    }
  }


  private void populateEntriesForControl() throws Exception
  {
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
      "dn: cn=user1,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: user1",
      "sn: user1",
      "uid: user1",
      "displayname:: QXVy4oiawqlsaWVuNA==",
      "",
      "dn: cn=user2,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: user2",
      "sn: user2",
      "uid: user2",
      "displayname:: QXVy4oiawqlsaWVuMw==",
      "",
      "dn: cn=user3,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: user3",
      "sn: user3",
      "uid: user3",
      "displayname:: QXVy4oiawqlsaWVuMg==",
      "",
      "dn: cn=user4,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: user4",
      "sn: user4",
      "uid: user4",
      "displayname:: QXVy4oiawqlsaWVuMQ=="
    );
  }
}
