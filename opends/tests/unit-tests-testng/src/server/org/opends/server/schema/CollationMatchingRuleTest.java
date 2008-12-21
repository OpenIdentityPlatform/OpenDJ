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


package org.opends.server.schema;

import java.util.List;


import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.*;
import static org.testng.Assert.*;


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
              new ASN1OctetString("uid=user,o=test"),
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
              new ASN1OctetString("uid=user,o=test"),
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
              new ASN1OctetString("uid=user,o=test"),
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
              new ASN1OctetString("uid=user,o=test"),
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
              new ASN1OctetString("uid=user,o=test"),
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
              new ASN1OctetString("uid=user,o=test"),
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
     *Québec.
     */
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null,
              new ASN1OctetString("uid=user,o=test"),
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
}
