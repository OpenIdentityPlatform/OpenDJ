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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.types.LDAPException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import static org.opends.server.util.ServerConstants.EOL;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.CompareRequestProtocolOp class.
 */
public class TestCompareRequestProtocolOp extends LdapTestCase
{
  /**
   * The protocol op type for compare requests.
   */
  public static final byte OP_TYPE_COMPARE_REQUEST = 0x6E;

  /**
   * The protocol op type for compare responses.
   */
  public static final byte OP_TYPE_COMPARE_RESPONSE = 0x6F;

  /**
   * The DN for compare requests in this test case.
   */
  private static final ByteString dn =
      ByteString.valueOf("dc=example,dc=com");

  /**
   * The alternative DN for compare requests in this test case.
   */
  private static final ByteString dnAlt =
      ByteString.valueOf("dc=sun,dc=com");

  // The assertion value for this compare request.
  private ByteString assertionValue =
      ByteString.valueOf("=test");

  // The assertion value for this compare request.
  private ByteString assertionValueAlt =
      ByteString.valueOf("=testAlt");

  // The attribute type for this compare request.
  private String attributeType = "testAttribute";

  // The alternate attribute type for this compare request.
  private String attributeTypeAlt = "testAttributeAlt";

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
    CompareRequestProtocolOp compareRequest;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();
    int i;

    compareRequest = new CompareRequestProtocolOp(dn, attributeType,
                                                  assertionValue);
    compareRequest.toString(buffer);

    key.append("CompareRequest(dn="+dn+", attribute="+attributeType+", " +
        "value="+assertionValue+")");

    assertEquals(buffer.toString(), key.toString());
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringMultiLine() throws Exception
  {
    CompareRequestProtocolOp compareRequest;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();
    int i;
    int indent;

    indent = 5;
    compareRequest = new CompareRequestProtocolOp(dn, attributeType,
                                                  assertionValue);
    compareRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Compare Request");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Target DN:  ");
    key.append(dn);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Attribute Type:  ");
    key.append(attributeType);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Assertion Value:");
    key.append(EOL);
    assertionValue.toHexPlusAscii(key, indent+4);

    assertEquals(buffer.toString(), key.toString());
  }
}
