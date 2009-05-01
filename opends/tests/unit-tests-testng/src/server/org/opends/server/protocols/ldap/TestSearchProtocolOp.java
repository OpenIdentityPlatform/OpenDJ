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


package org.opends.server.protocols.ldap;

import org.testng.annotations.Test;
import org.opends.server.types.*;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.UNIVERSAL_OCTET_STRING_TYPE;
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
    ByteString baseDN = ByteString.valueOf("dc=example,dc=COM");
    SearchScope scope = SearchScope.WHOLE_SUBTREE;
    DereferencePolicy dereferencePolicy = DereferencePolicy.DEREF_IN_SEARCHING;
    int sizeLimit = Integer.MAX_VALUE;
    int timeLimit = Integer.MAX_VALUE;
    boolean typesOnly = true;
    LDAPFilter filter;
    String[] attrArray = new String[]
         {
              "description", "cn", "cn;optionA"
         };
    LinkedHashSet<String> attributes =
         new LinkedHashSet<String>(Arrays.asList(attrArray));

  public TestSearchProtocolOp() throws Exception
  {
    filter = LDAPFilter.decode("(objectClass=*)");
  }

  /**
   * Create a test search request protocol op.
   * @return A test search request protocol op.
   * @throws LDAPException If the test object could not be created.
   */
  private SearchRequestProtocolOp buildSearchRequestProtocolOp()
       throws LDAPException
  {
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
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);

    // Construct a protocol op.
    SearchRequestProtocolOp protocolOp = buildSearchRequestProtocolOp();

    // Encode to ASN1.
    protocolOp.write(writer);

    // Decode to a new protocol op.
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    ProtocolOp decodedProtocolOp = LDAPReader.readProtocolOp(reader);

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
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeInteger(OP_TYPE_SEARCH_REQUEST, 0);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * This should succeed since we are ignoring trailing SEQUENCE
   * components.
   */
  @Test
  public void testInvalidSearchRequestTooManyElements() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeBoolean(true);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());

    ProtocolOp decodedProtocolOp = LDAPReader.readProtocolOp(reader);

    // Make sure the protocol op is the correct type.
    assertTrue(decodedProtocolOp instanceof SearchRequestProtocolOp);
    SearchRequestProtocolOp searchOp =
         (SearchRequestProtocolOp)decodedProtocolOp;

    // Check that the fields have not been changed during encode and decode.
    assertTrue(baseDN.equals(searchOp.getBaseDN()));
    assertTrue(scope.equals(searchOp.getScope()));
    assertTrue(dereferencePolicy.
         equals(searchOp.getDereferencePolicy()));
    assertTrue(sizeLimit == searchOp.getSizeLimit());
    assertTrue(timeLimit == searchOp.getTimeLimit());
    assertTrue(filter.toString().equals(
         searchOp.getFilter().toString()));
    // Check that the attributes are in the correct order (comparing the sets
    // directly does not guarantee this).
    assertTrue(Arrays.equals(attributes.toArray(),
                             searchOp.getAttributes().toArray()));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestTooFewElements() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestScope() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(9);
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestDerefPolicy() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(9);
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement1() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement2() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement3() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement4() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement5() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);
    filter.write(writer);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement6() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    writer.writeNull(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    for(String attribute : attributes)
    {
      writer.writeOctetString(attribute);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSearchRequestElement7() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    writer.writeOctetString(baseDN);
    writer.writeInteger(scope.intValue());
    writer.writeInteger(dereferencePolicy.intValue());
    writer.writeInteger(sizeLimit);
    writer.writeInteger(timeLimit);
    writer.writeBoolean(typesOnly);
    filter.write(writer);

    writer.writeInteger(0);

    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

}
