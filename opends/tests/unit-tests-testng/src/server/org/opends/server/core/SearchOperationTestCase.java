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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import static org.testng.Assert.*;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.TestCaseUtils;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.MatchedValuesFilter;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.Attribute;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SearchOperationTestCase extends OperationTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE = "o=Test Core Search,dc=example,dc=com";

  private Entry testEntry;
  private int ldapAttrCount;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearJEBackend(true,"userRoot","dc=example,dc=com");

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the suffix entry.
    DN suffixDN = DN.decode(SUFFIX);
    if (DirectoryServer.getEntry(suffixDN) == null)
    {
      Entry suffixEntry = StaticUtils.createEntry(suffixDN);
      AddOperation addOperation =
           connection.processAdd(suffixEntry.getDN(),
                                 suffixEntry.getObjectClasses(),
                                 suffixEntry.getUserAttributes(),
                                 suffixEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(suffixEntry.getDN()));
    }

    // Add a search base entry.
    DN baseDN = DN.decode(BASE);
    if (DirectoryServer.getEntry(baseDN) == null)
    {
      Entry baseEntry = StaticUtils.createEntry(baseDN);
      AddOperation addOperation =
           connection.processAdd(baseEntry.getDN(),
                                 baseEntry.getObjectClasses(),
                                 baseEntry.getUserAttributes(),
                                 baseEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(baseEntry.getDN()));
    }

    // Add a test entry.
    testEntry = TestCaseUtils.makeEntry(
         "dn: uid=rogasawara," + BASE,
         "userpassword: password",
         "objectclass: top",
         "objectclass: person",
         "objectclass: organizationalPerson",
         "objectclass: inetOrgPerson",
         "uid: rogasawara",
         "mail: rogasawara@airius.co.jp",
         "givenname;lang-ja:: 44Ot44OJ44OL44O8",
         "sn;lang-ja:: 5bCP56yg5Y6f",
         "cn;lang-ja:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
         "title;lang-ja:: 5Za25qWt6YOoIOmDqOmVtw==",
         "preferredlanguage: ja",
         "givenname:: 44Ot44OJ44OL44O8",
         "sn:: 5bCP56yg5Y6f",
         "cn:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
         "title:: 5Za25qWt6YOoIOmDqOmVtw==",
         "givenname;lang-ja;phonetic:: 44KN44Gp44Gr44O8",
         "sn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJ",
         "cn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJIOOCjeOBqeOBq+ODvA==",
         "title;lang-ja;phonetic:: " +
              "44GI44GE44GO44KH44GG44G2IOOBtuOBoeOCh+OBhg==",
         "givenname;lang-en: Rodney",
         "sn;lang-en: Ogasawara",
         "cn;lang-en: Rodney Ogasawara",
         "title;lang-en: Sales, Director"
    );

    // Calculate the total number of LDAP attributes in this entry.
    ldapAttrCount = 1; // For the objectclass attribute.
    for (Attribute a : testEntry.getAttributes())
    {
      ldapAttrCount += a.size();
    }

    // The add operation changes the attributes, so let's duplicate the entry.
    Entry duplicateEntry = testEntry.duplicate(false);

    AddOperation addOperation =
         connection.processAdd(duplicateEntry.getDN(),
                               duplicateEntry.getObjectClasses(),
                               duplicateEntry.getUserAttributes(),
                               duplicateEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(testEntry.getDN()));

    // Add a test ldapsubentry.
    Entry ldapSubentry = TestCaseUtils.makeEntry(
         "dn: cn=subentry," + BASE,
         "objectclass: ldapsubentry");
    addOperation =
         connection.processAdd(ldapSubentry.getDN(),
                               ldapSubentry.getObjectClasses(),
                               ldapSubentry.getUserAttributes(),
                               ldapSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(ldapSubentry.getDN()));

    // Add a test referral entry.
    Entry referralEntry = TestCaseUtils.makeEntry(
         "dn: ou=People," + BASE,
         "objectclass: extensibleobject",
         "objectclass: referral",
         "ref: ldap://hostb/OU=People,O=MNN,C=US",
         "ref: ldap://hostc/OU=People,O=MNN,C=US");
    addOperation =
         connection.processAdd(referralEntry.getDN(),
                               referralEntry.getObjectClasses(),
                               referralEntry.getUserAttributes(),
                               referralEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(referralEntry.getDN()));
  }


  @Override
  protected Operation[] createTestOperations() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
         new SearchOperationBasis(conn,
                             InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             ByteString.valueOf(BASE),
                             SearchScope.WHOLE_SUBTREE,
                             DereferencePolicy.NEVER_DEREF_ALIASES,
                             -1,
                             -1,
                             false,
                             LDAPFilter.decode("(objectclass=*)"),
                             null)
    };
  }

  /**
   * Invokes a number of operation methods on the provided search operation
   * for which all processing has been completed.
   *
   * @param  searchOperation  The operation to be tested.
   */
  private void examineCompletedOperation(SearchOperation searchOperation)
  {
    assertTrue(searchOperation.getProcessingStartTime() > 0);
    assertTrue(searchOperation.getProcessingStopTime() > 0);
    assertTrue(searchOperation.getProcessingTime() >= 0);
    assertNotNull(searchOperation.getResponseLogElements());

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  private Entry searchInternalForSingleEntry(
       InternalSearchOperation searchOperation)
  {
    InvocationCounterPlugin.resetAllCounters();

    searchOperation.run();

    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(searchOperation);

    return searchOperation.getSearchEntries().getFirst();
  }

  private SearchResultEntryProtocolOp searchExternalForSingleEntry(
       SearchRequestProtocolOp searchRequest,
       ArrayList<Control> controls)
       throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    try
    {
      org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
      LDAPWriter w = new LDAPWriter(s);
      s.setSoTimeout(1500000);

      bindAsManager(w, r);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


      InvocationCounterPlugin.resetAllCounters();

      long searchRequests   = ldapStatistics.getSearchRequests();
      long searchEntries    = ldapStatistics.getSearchResultEntries();
      long searchReferences = ldapStatistics.getSearchResultReferences();
      long searchesDone     = ldapStatistics.getSearchResultsDone();

      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;
      while (searchResultDone == null && (message = r.readMessage()) != null)
      {
        switch (message.getProtocolOpType())
        {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            searchResultEntry = message.getSearchResultEntryProtocolOp();
            searchEntries++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
            searchReferences++;
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            searchResultDone = message.getSearchResultDoneProtocolOp();
            assertEquals(searchResultDone.getResultCode(),
                         LDAPResultCode.SUCCESS);
//            assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
            searchesDone++;
            break;
        }
      }

      TestCaseUtils.quiesceServer();
      assertEquals(ldapStatistics.getSearchRequests(), searchRequests+1);
      assertEquals(ldapStatistics.getSearchResultEntries(), searchEntries);
      assertEquals(ldapStatistics.getSearchResultReferences(),
                   searchReferences);
      assertEquals(ldapStatistics.getSearchResultsDone(), searchesDone);

      return searchResultEntry;
    }
    finally
    {
      s.close();
    }
  }

  private void bindAsManager(LDAPWriter w, org.opends.server.tools.LDAPReader r)
       throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
    // Since we are going to be watching the post-response count, we need to
    // wait for the server to become idle before kicking off the next request to
    // ensure that any remaining post-response processing from the previous
    // operation has completed.
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


    InvocationCounterPlugin.resetAllCounters();
    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(
              ByteString.valueOf("cn=Directory Manager"),
              3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
//    assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);
  }

  @Test
  public void testSearchInternal() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=*)"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 2);
    assertEquals(searchOperation.getReferencesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);

    examineCompletedOperation(searchOperation);
  }

  @Test
  public void testSearchInternalUnspecifiedAttributes() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), testEntry.getObjectClasses());

    // Search results contain objectClass as an attribute.
    assertEquals(resultEntry.getUserAttributes().size(),
                 testEntry.getUserAttributes().size() + 1);

    assertEquals(resultEntry.getOperationalAttributes().size(), 0);
  }

  @Test
  public void testSearchInternalUnspecifiedAttributesOmitValues()
       throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              true,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses().size(), 0);

    assertEquals(resultEntry.getUserAttributes().size(),
                 testEntry.getUserAttributes().size() + 1);
    assertEquals(resultEntry.getOperationalAttributes().size(), 0);
  }

  @Test
  public void testSearchInternalAllOperationalAttributes() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("+");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses().size(), 0);
    assertEquals(resultEntry.getUserAttributes().size(), 0);
    assertTrue(resultEntry.getOperationalAttributes().size() > 0);
  }

  @Test
  public void testSearchInternalAllUserAndOperationalAttributes()
       throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("*");
    attributes.add("+");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), testEntry.getObjectClasses());
    assertTrue(resultEntry.getOperationalAttributes().size() > 0);

    // Search results contain objectClass as an attribute.
    assertEquals(resultEntry.getUserAttributes().size(),
                 testEntry.getUserAttributes().size() + 1);
  }

  @Test
  public void testSearchInternalAllUserAttributesPlusSelectedOperational()
       throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("*");
    attributes.add("createtimestamp");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), testEntry.getObjectClasses());

    // Search results contain objectClass as an attribute.
    assertEquals(resultEntry.getUserAttributes().size(),
                 testEntry.getUserAttributes().size() + 1);

    assertEquals(resultEntry.getOperationalAttributes().size(), 1);
  }

  @Test
  public void testSearchInternalSelectedAttributes()
       throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("uid");
    attributes.add("createtimestamp");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses().size(), 0);
    assertEquals(resultEntry.getUserAttributes().size(), 1);
    assertEquals(resultEntry.getOperationalAttributes().size(), 1);
  }

  @Test
  public void testSearchExternalUnspecifiedAttributes() throws Exception
  {
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null);
    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);
    assertEquals(searchResultEntry.getAttributes().size(), ldapAttrCount);
  }

  @Test
  public void testSearchExternalAllUserAttributes() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("*");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);
    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);
    assertEquals(searchResultEntry.getAttributes().size(), ldapAttrCount);
  }

  @Test
  public void testSearchExternalUnspecifiedAttributesOmitValues()
       throws Exception
  {
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              true,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null);
    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);
    // The attributes will include the objectclass type.
    assertEquals(searchResultEntry.getAttributes().size(), ldapAttrCount);
    for (LDAPAttribute a : searchResultEntry.getAttributes())
    {
      assertEquals(a.getValues().size(), 0);
    }
  }

  @Test
  public void testSearchExternalAllUserAttributesOmitValues() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("*");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              true,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);
    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);
    // The attributes will include the objectclass type.
    assertEquals(searchResultEntry.getAttributes().size(), ldapAttrCount);
    for (LDAPAttribute a : searchResultEntry.getAttributes())
    {
      assertEquals(a.getValues().size(), 0);
    }
  }

  @Test
  public void testSearchExternalObjectClassAttribute() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("objectclass");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 1);
    assertEquals(searchResultEntry.getAttributes().
         getFirst().getValues().size(), 4);
  }

  @Test
  public void testSearchExternalObjectClassAttributeOmitValues()
       throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("objectclass");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              true,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 1);
    assertEquals(searchResultEntry.getAttributes().
         getFirst().getValues().size(), 0);
  }

  @Test
  public void testSearchExternalSelectedAttributes() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("uid");
    attributes.add("createtimestamp");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 2);
  }

  @Test
  public void testSearchExternalAttributeWithSubtypes() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("title");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 4);
  }

  @Test
  public void testSearchExternalAttributeWithSubtypesOmitValues()
       throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("title");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              true,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 4);
    for (LDAPAttribute a : searchResultEntry.getAttributes())
    {
      assertEquals(a.getValues().size(), 0);
    }
  }

  @Test
  public void testSearchExternalAttributeWithOptions() throws Exception
  {
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("title;lang-ja;phonetic");
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, null);

    assertEquals(searchResultEntry.getAttributes().size(), 1);
  }

  @Test
  public void testSearchExternalMatchedValues() throws Exception
  {
    // Add a matched values control.
    LDAPFilter ldapFilter = LDAPFilter.decode("(title=*director*)");
    MatchedValuesFilter matchedValuesFilter =
         MatchedValuesFilter.createFromLDAPFilter(ldapFilter);
    ArrayList<MatchedValuesFilter> filters =
         new ArrayList<MatchedValuesFilter>();
    filters.add(matchedValuesFilter);
    MatchedValuesControl mvc = new MatchedValuesControl(true, filters);
    ArrayList<Control> controls = new ArrayList<Control>();
    controls.add(mvc);

    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null);

    SearchResultEntryProtocolOp searchResultEntry =
         searchExternalForSingleEntry(searchRequest, controls);

    // Per RFC 3876, an attribute that has no values selected is returned
    // with an empty set of values.  We should therefore expect all the
    // attributes but only one value.
    int valueCount = 0;
    for (LDAPAttribute a : searchResultEntry.getAttributes())
    {
      valueCount += a.getValues().size();
    }
    assertEquals(valueCount, 1);
  }

  @Test
  public void testSearchInternalReferences() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(BASE),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null, null);

    searchOperation.run();

    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getReferencesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(searchOperation);

    List<SearchResultReference> references =
         searchOperation.getSearchReferences();
    assertEquals(references.size(), 1);

    List<String> referrals = references.get(0).getReferralURLs();
    assertEquals(referrals.size(), 2);
  }

  @Test
  public void testSearchInternalMatchedDN() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    TestCaseUtils.initializeTestBackend(true);
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf("ou=nonexistent,o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=*)"),
              null, null);

    searchOperation.run();

    assertEquals(searchOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertNotNull(searchOperation.getMatchedDN());
  }



  /**
   * Determines how attributes should be filtered in search operations.
   */
  private enum AttributeFilterType {
    DEFAULT, WILDCARDS, ENUMERATED;
  }



  /**
   * Returns test data for testSearchInternalAttributeFilters.
   *
   * @return The test data.
   */
  @DataProvider(name = "testSearchInternalAttributeFilters")
  public Object[][] createTestSearchInternalAttributeFiltersData()
  {
    // It was quicker to cut n paste...
    return new Object[][] {
        {AttributeFilterType.DEFAULT,     false, false, false},
        {AttributeFilterType.DEFAULT,     false, false, true},
        {AttributeFilterType.DEFAULT,     false, true,  false},
        {AttributeFilterType.DEFAULT,     false, true,  true},
        {AttributeFilterType.DEFAULT,     true,  false, false},
        {AttributeFilterType.DEFAULT,     true,  false, true},
        {AttributeFilterType.DEFAULT,     true,  true,  false},
        {AttributeFilterType.DEFAULT,     true,  true,  true},
        {AttributeFilterType.WILDCARDS,   false, false, false},
        {AttributeFilterType.WILDCARDS,   false, false, true},
        {AttributeFilterType.WILDCARDS,   false, true,  false},
        {AttributeFilterType.WILDCARDS,   false, true,  true},
        {AttributeFilterType.WILDCARDS,   true,  false, false},
        {AttributeFilterType.WILDCARDS,   true,  false, true},
        {AttributeFilterType.WILDCARDS,   true,  true,  false},
        {AttributeFilterType.WILDCARDS,   true,  true,  true},
        {AttributeFilterType.ENUMERATED,  false, false, false},
        {AttributeFilterType.ENUMERATED,  false, false, true},
        {AttributeFilterType.ENUMERATED,  false, true,  false},
        {AttributeFilterType.ENUMERATED,  false, true,  true},
        {AttributeFilterType.ENUMERATED,  true,  false, false},
        {AttributeFilterType.ENUMERATED,  true,  false, true},
        {AttributeFilterType.ENUMERATED,  true,  true,  false},
        {AttributeFilterType.ENUMERATED,  true,  true,  true},
    };
  }



  /**
   * Tests that attribute filtering is performed correctly for real and
   * virtual attributes when various combinations of typesOnly, and the
   * real-attributes-only and virtual-attributes-only controls are used
   * (issues 3446 and 3726).
   *
   * @param filterType
   *          Specifies how attributes should be filtered.
   * @param typesOnly
   *          Strip attribute values.
   * @param stripVirtualAttributes
   *          Strip virtual attributes.
   * @param stripRealAttributes
   *          Strip real attributes.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "testSearchInternalAttributeFilters")
  public void testSearchInternalAttributeFilters(
      AttributeFilterType filterType, boolean typesOnly,
      boolean stripVirtualAttributes, boolean stripRealAttributes)
      throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    // Real attributes (these are all user attributes).
    List<String> realAttrTypes =
        Arrays.asList("objectclass", "uid", "cn", "sn", "givenname",
            "userpassword");

    // Virtual attributes (these are all operational attributes).
    List<String> virtualAttrTypes =
        Arrays.asList("numsubordinates", "hassubordinates",
            "subschemasubentry", "entrydn", "ismemberof");

    String userDNString = "uid=test.user,o=test";
    DN userDN = DN.decode(userDNString);

    TestCaseUtils.addEntry("dn: " + userDNString,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password");

    Entry userEntry = DirectoryServer.getEntry(userDN);
    assertNotNull(userEntry);

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    switch (filterType)
    {
    case DEFAULT:
      // Only user attributes.
      attributes = null;
      break;
    case WILDCARDS:
      attributes.add("*");
      attributes.add("+");
      break;
    case ENUMERATED:
      attributes.addAll(realAttrTypes);
      attributes.addAll(virtualAttrTypes);
      break;
    }

    List<Control> controls = new LinkedList<Control>();

    if (stripRealAttributes)
    {
      controls.add(new LDAPControl(ServerConstants.OID_VIRTUAL_ATTRS_ONLY,
          false));
    }

    if (stripVirtualAttributes)
    {
      controls.add(new LDAPControl(ServerConstants.OID_REAL_ATTRS_ONLY,
          false));
    }

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    InternalSearchOperation search =
        conn.processSearch(userDNString, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, // Size limit
            0, // Time limit
            typesOnly, // Types only
            "(objectClass=*)", attributes, controls, null);

    assertEquals(search.getResultCode(), ResultCode.SUCCESS);

    LinkedList<SearchResultEntry> entries = search.getSearchEntries();
    assertEquals(entries.size(), 1);

    Entry entry = entries.getFirst();
    assertEquals(entry.getDN(), userDN);

    // Check real attributes.
    List<String> messages = new LinkedList<String>();
    for (String attrType : realAttrTypes)
    {
      List<Attribute> attrList = entry.getAttribute(attrType);

      if (stripRealAttributes)
      {
        if (attrList != null)
        {
          messages.add("Unexpected real attribute: " + attrType);
        }
      }
      else
      {
        if (attrList == null)
        {
          messages.add("Missing real attribute: " + attrType);
        }
        else
        {
          Attribute attr = attrList.get(0);
          if (typesOnly)
          {
            if (!attr.isEmpty())
            {
              messages.add("Unexpected non-empty real attribute: "
                  + attrType);
            }
          }
          else
          {
            if (attr.isEmpty())
            {
              messages.add("Unexpected empty real attribute: "
                  + attrType);
            }
          }
        }
      }
    }

    // Check virtual (operational) attributes.
    for (String attrType : virtualAttrTypes)
    {
      List<Attribute> attrList = entry.getAttribute(attrType);

      if (stripVirtualAttributes)
      {
        if (attrList != null)
        {
          messages.add("Unexpected virtual attribute: " + attrType);
        }
      }
      else if (filterType == AttributeFilterType.DEFAULT)
      {
        if (attrList != null)
        {
          messages.add("Unexpected operational attribute: " + attrType);
        }
      }
      else if (attrType.equals("ismemberof"))
      {
        // isMemberOf should never be returned as user is not in any
        // groups.
        if (attrList != null)
        {
          messages.add("Unexpected isMemberOf attribute");
        }
      }
      else
      {
        if (attrList == null)
        {
          messages.add("Missing virtual attribute: " + attrType);
        }
        else
        {
          Attribute attr = attrList.get(0);
          if (typesOnly)
          {
            if (!attr.isEmpty())
            {
              messages.add("Unexpected non-empty virtual attribute: "
                  + attrType);
            }
          }
          else
          {
            if (attr.isEmpty())
            {
              messages.add("Unexpected empty virtual attribute: "
                  + attrType);
            }
          }
        }
      }
    }

    assertTrue(messages.isEmpty(), "Entry invalid: " + messages);
  }



  /**
   * Returns test data for testSearchInternalUserAttributeNames.
   *
   * @return The test data.
   */
  @DataProvider(name = "testSearchInternalUserAttributeNames")
  public Object[][] createTestSearchInternalUserAttributeNamesData()
  {
    // First array is the requested attributes.
    // Second array is the expected attribute names in the entry.
    return new Object[][] {
        {
            Arrays.<String>asList(),
            Arrays.asList("objectClass", "cn", "cn;lang-fr") },
        {
            Arrays.asList("*", "+"),
            Arrays.asList("objectClass", "cn", "cn;lang-fr", "entryDN",
                "createTimestamp") },
        {
            Arrays.asList("objectClass", "cn", "cn;lang-fr", "entryDN",
                "createTimestamp"),
            Arrays.asList("objectClass", "cn", "cn;lang-fr", "entryDN",
                "createTimestamp") },
        {
            Arrays.asList("OBJECTCLASS", "commonName", "commonName;LANG-FR", "entrydn",
                "CREATETIMESTAMP"),
            Arrays.asList("OBJECTCLASS", "commonName",
                "commonName;LANG-FR", "entrydn", "CREATETIMESTAMP") },
        {
            Arrays.asList("*", "+", "OBJECTCLASS", "commonName",
                "commonName;LANG-FR", "entrydn", "CREATETIMESTAMP"),
            Arrays.asList("OBJECTCLASS", "commonName",
                "commonName;LANG-FR", "entrydn", "CREATETIMESTAMP") },
        { Arrays.asList("name"),
            Arrays.asList("givenName", "sn", "cn", "cn;lang-fr") },
        { Arrays.asList("name;lang-fr"), Arrays.asList("cn;lang-fr") },
        { Arrays.asList("name;LANG-FR"), Arrays.asList("cn;LANG-FR") }, };
  }



  /**
   * Tests that attributes are returned from internal searches using the
   * attribute name requested by the user.
   *
   * @param requestedAttributes
   *          The list of requested attributes names.
   * @param expectedAttributes
   *          The list of expected attribute names.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "testSearchInternalUserAttributeNames")
  public void testSearchInternalUserAttributeNames(
      List<String> requestedAttributes, List<String> expectedAttributes)
      throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String userDNString = "uid=test.user,o=test";
    DN userDN = DN.decode(userDNString);

    TestCaseUtils.addEntry("dn: " + userDNString,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "cn;lang-fr: Test Usager",
        "userPassword: password");

    Entry userEntry = DirectoryServer.getEntry(userDN);
    assertNotNull(userEntry);

    LinkedHashSet<String> attributes =
      new LinkedHashSet<String>(requestedAttributes);

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    InternalSearchOperation search =
      conn.processSearch(userDNString, SearchScope.BASE_OBJECT,
          DereferencePolicy.NEVER_DEREF_ALIASES, 0, // Size limit
          0, // Time limit
          false, // Types only
          "(objectClass=*)", attributes);

    assertEquals(search.getResultCode(), ResultCode.SUCCESS);

    LinkedList<SearchResultEntry> entries = search.getSearchEntries();
    assertEquals(entries.size(), 1);

    Entry entry = entries.getFirst();
    assertEquals(entry.getDN(), userDN);

    // Check all expected attributes are present and have
    // the user requested name.
    List<Attribute> attrList = entry.getAttributes();
    Set<String> actualNames = new HashSet<String>();
    for (Attribute attribute : attrList)
    {
      actualNames.add(attribute.getNameWithOptions());
    }

    assertTrue(actualNames.containsAll(expectedAttributes),
        "Expected: " + expectedAttributes + " got " + actualNames);
  }



  /**
   * Tests that attributes are returned from external searches using the
   * attribute name requested by the user.
   *
   * @param requestedAttributes
   *          The list of requested attributes names.
   * @param expectedAttributes
   *          The list of expected attribute names.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "testSearchInternalUserAttributeNames")
  public void testSearchExternalUserAttributeNames(
      List<String> requestedAttributes, List<String> expectedAttributes)
      throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String userDNString = "uid=test.user,o=test";
    DN userDN = DN.decode(userDNString);

    TestCaseUtils.addEntry("dn: " + userDNString,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "cn;lang-fr: Test Usager",
        "userPassword: password");

    Entry userEntry = DirectoryServer.getEntry(userDN);
    assertNotNull(userEntry);

    LinkedHashSet<String> attributes =
      new LinkedHashSet<String>(requestedAttributes);

    SearchRequestProtocolOp searchRequest =
      new SearchRequestProtocolOp(
          ByteString.valueOf(userDNString),
          SearchScope.BASE_OBJECT,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          false,
          LDAPFilter.decode("(objectclass=*)"),
          attributes);

    SearchResultEntryProtocolOp entry =
      searchExternalForSingleEntry(searchRequest, null);

    assertEquals(entry.getDN(), userDN);

    // Check all expected attributes are present and have
    // the user requested name.
    LinkedList<LDAPAttribute> attrList = entry.getAttributes();
    Set<String> actualNames = new HashSet<String>();
    for (LDAPAttribute attribute : attrList)
    {
      actualNames.add(attribute.getAttributeType());
    }

    assertTrue(actualNames.containsAll(expectedAttributes),
        "Expected: " + expectedAttributes + " got " + actualNames);
  }
}
