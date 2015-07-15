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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import org.opends.server.types.LDAPException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import static org.opends.server.util.ServerConstants.EOL;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.CompareRequestProtocolOp class.
 */
public class TestCompareRequestProtocolOp extends LdapTestCase
{
  /** The protocol op type for compare requests. */
  public static final byte OP_TYPE_COMPARE_REQUEST = 0x6E;
  /** The protocol op type for compare responses. */
  public static final byte OP_TYPE_COMPARE_RESPONSE = 0x6F;

  /** The DN for compare requests in this test case. */
  private static final ByteString dn =
      ByteString.valueOf("dc=example,dc=com");

  /** The assertion value for this compare request. */
  private ByteString assertionValue = ByteString.valueOf("=test");

  /** The attribute type for this compare request. */
  private String attributeType = "testAttribute";

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    CompareRequestProtocolOp compareRequest = new CompareRequestProtocolOp(dn,
      attributeType, assertionValue);
    assertEquals(compareRequest.getType(), OP_TYPE_COMPARE_REQUEST);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    CompareRequestProtocolOp compareRequest = new CompareRequestProtocolOp(dn,
      attributeType, assertionValue);
    assertEquals(compareRequest.getProtocolOpName(), "Compare Request");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    CompareRequestProtocolOp compareRequest;

    //Test to make sure the constructor with dn param works.
    compareRequest = new CompareRequestProtocolOp(dn, attributeType,
                                                  assertionValue);
    assertEquals(compareRequest.getDN(), dn);
    assertEquals(compareRequest.getAssertionValue(), assertionValue);
    assertEquals(compareRequest.getAttributeType(), attributeType);
  }

  /**
   * Test the decode method when an null element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeNullElement() throws Exception
  {
    LDAPReader.readProtocolOp(null);
  }

  /**
   * Test the decode method when an empty element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeEmptyElement() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when invalid elements is passed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElement() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
    writer.writeNull();
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an element w/ wrong op type is passed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeWrongElementType() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
    writer.writeOctetString(dn);
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the encode and decode methods with null params
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = Exception.class)
  public void testNullEncodeDecode() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    CompareRequestProtocolOp compareEncoded;
    CompareRequestProtocolOp compareDecoded;

    compareEncoded = new CompareRequestProtocolOp(null, null, null);
    compareEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    compareDecoded = (CompareRequestProtocolOp)LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the encode and decode methods and corner cases.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testEncodeDecode() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    CompareRequestProtocolOp compareEncoded;
    CompareRequestProtocolOp compareDecoded;
    ArrayList<LDAPAttribute> attributes;


    //Test case for a full encode decode operation with normal params.
    compareEncoded = new CompareRequestProtocolOp(dn, attributeType,
                                                  assertionValue);
    compareEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    compareDecoded = (CompareRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(compareEncoded.getType(), OP_TYPE_COMPARE_REQUEST);
    assertEquals(compareEncoded.getDN(), compareDecoded.getDN());
    assertEquals(compareEncoded.getAttributeType(),
                 compareDecoded.getAttributeType());
    assertEquals(compareEncoded.getAssertionValue(),
                 compareDecoded.getAssertionValue());
  }


  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    CompareRequestProtocolOp  compareRequest = new CompareRequestProtocolOp(
        dn, attributeType, assertionValue);
    StringBuilder buffer = new StringBuilder();
    compareRequest.toString(buffer);

    String key = "CompareRequest(dn=" + dn
        + ", attribute=" + attributeType
        + ", value=" + assertionValue + ")";

    assertEquals(buffer.toString(), key);
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringMultiLine() throws Exception
  {
    int indent = 5;

    CompareRequestProtocolOp compareRequest = new CompareRequestProtocolOp(dn, attributeType, assertionValue);
    StringBuilder buffer = new StringBuilder();
    compareRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i = 0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    StringBuilder key = new StringBuilder();
    key.append(indentBuf).append("Compare Request").append(EOL);
    key.append(indentBuf).append("  Target DN:  ").append(dn).append(EOL);
    key.append(indentBuf).append("  Attribute Type:  ").append(attributeType).append(EOL);
    key.append(indentBuf).append("  Assertion Value:").append(EOL);
    key.append(assertionValue.toHexPlusAsciiString(indent+4));

    assertEquals(buffer.toString(), key.toString());
  }
}
