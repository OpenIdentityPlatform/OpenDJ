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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.MatchedValuesFilter;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.net.Socket;
import java.io.IOException;

public class SearchOperationTestCase extends OperationTestCase
{
  private Entry entry;
  private int ldapAttrCount;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add a test entry.
    entry = TestCaseUtils.makeEntry(
         "dn: uid=rogasawara,o=test",
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
    for (Attribute a : entry.getAttributes())
    {
      ldapAttrCount += a.getValues().size();
    }

    // The add operation changes the attributes, so let's duplicate the entry.
    Entry duplicateEntry = entry.duplicate();

    AddOperation addOperation =
         connection.processAdd(duplicateEntry.getDN(),
                               duplicateEntry.getObjectClasses(),
                               duplicateEntry.getUserAttributes(),
                               duplicateEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(entry.getDN()));

    // Add a test ldapsubentry.
    Entry ldapSubentry = TestCaseUtils.makeEntry(
         "dn: cn=subentry,o=test",
         "objectclass: ldapsubentry");
    addOperation =
         connection.processAdd(ldapSubentry.getDN(),
                               ldapSubentry.getObjectClasses(),
                               ldapSubentry.getUserAttributes(),
                               ldapSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(ldapSubentry.getDN()));
  }


  public Operation[] createTestOperations() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
         new SearchOperation(conn,
                             InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             new ASN1OctetString("o=test"),
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

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
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
       ArrayList<LDAPControl> controls)
       throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    try
    {
      ASN1Reader r = new ASN1Reader(s);
      ASN1Writer w = new ASN1Writer(s);
      r.setIOTimeout(1500000);

      bindAsManager(w, r);

      InvocationCounterPlugin.resetAllCounters();

      LDAPMessage message;
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      SearchResultEntryProtocolOp searchResultEntry =
           message.getSearchResultEntryProtocolOp();

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      SearchResultDoneProtocolOp searchResultDone =
           message.getSearchResultDoneProtocolOp();

      assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
      assertEquals(searchResultDone.getResultCode(), LDAPResultCode.SUCCESS);

      return searchResultEntry;
    }
    finally
    {
      s.close();
    }
  }

  private void bindAsManager(ASN1Writer w, ASN1Reader r)
       throws IOException, LDAPException, ASN1Exception, InterruptedException
  {
    InvocationCounterPlugin.resetAllCounters();
    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(
              new ASN1OctetString("cn=Directory Manager"),
              3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              null, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), entry.getObjectClasses());
    assertEquals(resultEntry.getUserAttributes().size(),
                 entry.getUserAttributes().size());
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
              new ASN1OctetString("o=test"),
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
                 entry.getUserAttributes().size() + 1);
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), entry.getObjectClasses());
    assertTrue(resultEntry.getOperationalAttributes().size() > 0);
    assertEquals(resultEntry.getUserAttributes().size(),
                 entry.getUserAttributes().size());
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
              new ASN1OctetString("o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=inetorgperson)"),
              attributes, null);

    Entry resultEntry = searchInternalForSingleEntry(searchOperation);

    assertEquals(resultEntry.getObjectClasses(), entry.getObjectClasses());
    assertEquals(resultEntry.getUserAttributes().size(),
                 entry.getUserAttributes().size());
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
              new ASN1OctetString("o=test"),
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
    ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
    controls.add(new LDAPControl(mvc));

    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(
              new ASN1OctetString("o=test"),
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

}
