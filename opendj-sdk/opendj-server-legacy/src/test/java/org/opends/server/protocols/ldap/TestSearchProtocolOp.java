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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.types.LDAPException;
import org.testng.annotations.Test;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.testng.Assert.*;

/**
 * Test class for LDAP Search protocol operation classes.
 */
@SuppressWarnings("javadoc")
public class TestSearchProtocolOp extends LdapTestCase
{
  private ByteString baseDN = ByteString.valueOf("dc=example,dc=COM");
  private SearchScope scope = SearchScope.WHOLE_SUBTREE;
  private DereferenceAliasesPolicy dereferencePolicy =
      DereferenceAliasesPolicy.IN_SEARCHING;
  private int sizeLimit = Integer.MAX_VALUE;
  private int timeLimit = Integer.MAX_VALUE;
  private boolean typesOnly = true;
  private LDAPFilter filter = LDAPFilter.objectClassPresent();
  private LinkedHashSet<String> attributes = new LinkedHashSet<>(
      Arrays.asList("description", "cn", "cn;optionA"));

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

  @Test
  public void testSearchRequestToString() throws Exception
  {
    SearchRequestProtocolOp protocolOp = buildSearchRequestProtocolOp();
    StringBuilder sb = new StringBuilder();
    protocolOp.toString(sb);
    protocolOp.toString(sb, 1);
  }

  @Test
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
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
    writer.writeNull(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

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
