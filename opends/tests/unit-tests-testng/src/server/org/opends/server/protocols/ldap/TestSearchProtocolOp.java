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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */


package org.opends.server.protocols.ldap;

import org.testng.annotations.Test;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.SearchScope;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.testng.Assert.*;

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * Test class for LDAP Search protocol operation classes.
 */
public class TestSearchProtocolOp extends LdapTestCase
{
  /**
   * Create a test search request protocol op.
   * @return A test search request protocol op.
   * @throws LDAPException If the test object could not be created.
   */
  private SearchRequestProtocolOp buildSearchRequestProtocolOp()
       throws LDAPException
  {
    ASN1OctetString baseDN = new ASN1OctetString("dc=example,dc=COM");
    SearchScope scope = SearchScope.WHOLE_SUBTREE;
    DereferencePolicy dereferencePolicy = DereferencePolicy.DEREF_IN_SEARCHING;
    int sizeLimit = Integer.MAX_VALUE;
    int timeLimit = Integer.MAX_VALUE;
    boolean typesOnly = true;
    LDAPFilter filter = LDAPFilter.decode("(objectClass=*)");
    String[] attrArray = new String[]
         {
              "description", "cn", "cn;optionA"
         };
    LinkedHashSet<String> attributes =
         new LinkedHashSet<String>(Arrays.asList(attrArray));

    return new SearchRequestProtocolOp(baseDN,
                                       scope,
                                       dereferencePolicy,
                                       sizeLimit,
                                       timeLimit,
                                       typesOnly,
                                       filter,
                                       attributes);
  }

  @Test ()
  public void testSearchRequestToString() throws Exception
  {
    SearchRequestProtocolOp protocolOp = buildSearchRequestProtocolOp();
    StringBuilder sb = new StringBuilder();
    protocolOp.toString(sb);
    protocolOp.toString(sb, 1);
  }

  @Test ()
  public void testSearchRequestEncodeDecode() throws Exception
  {
    // Construct a protocol op.
    SearchRequestProtocolOp protocolOp = buildSearchRequestProtocolOp();

    // Encode to ASN1.
    ASN1Element element = protocolOp.encode();

    // Decode to a new protocol op.
    ProtocolOp decodedProtocolOp = ProtocolOp.decode(element);

    // Make sure the protocol op is the correct type.
    assertTrue(decodedProtocolOp instanceof SearchRequestProtocolOp);
    SearchRequestProtocolOp searchOp =
         (SearchRequestProtocolOp)decodedProtocolOp;

    // Check that the fields have not been changed during encode and decode.
    assertTrue(protocolOp.getBaseDN().equals(searchOp.getBaseDN()));
    assertTrue(protocolOp.getScope().equals(searchOp.getScope()));
    assertTrue(protocolOp.getDereferencePolicy().
         equals(searchOp.getDereferencePolicy()));
    assertTrue(protocolOp.getSizeLimit() == searchOp.getSizeLimit());
    assertTrue(protocolOp.getTimeLimit() == searchOp.getTimeLimit());
    assertTrue(protocolOp.getFilter().toString().equals(
         searchOp.getFilter().toString()));
    // Check that the attributes are in the correct order (comparing the sets
    // directly does not guarantee this).
    assertTrue(Arrays.equals(protocolOp.getAttributes().toArray(),
                             searchOp.getAttributes().toArray()));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestSequence() throws Exception
  {
    ProtocolOp.decode(new ASN1Integer(OP_TYPE_SEARCH_REQUEST, 0));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestTooManyElements() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.add(new ASN1Boolean(true));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestTooFewElements() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.remove(0);
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestScope() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(1, new ASN1Integer(9));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestDerefPolicy() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(2, new ASN1Integer(9));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement1() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(1, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement2() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(2, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement3() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(3, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement4() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(4, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement5() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(5, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement6() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(6, new ASN1OctetString());
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement7() throws Exception
  {
    ASN1Element element = buildSearchRequestProtocolOp().encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(7, new ASN1OctetString("cn"));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements));
  }

}
