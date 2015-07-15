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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.Base64;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.AddRequestProtocolOp class.
 */
public class TestAddRequestProtocolOp extends LdapTestCase
{
  /** The protocol op type for add requests. */
  private static final byte OP_TYPE_ADD_REQUEST = 0x68;
  /** The protocol op type for add responses. */
  private static final byte OP_TYPE_ADD_RESPONSE = 0x69;
  /** The DN for add requests in this test case. */
  private static final ByteString dn = ByteString.valueOf("dc=example,dc=com");

  /**
   * Generate attributes for use in test cases. Attributes will have names
   * like "testAttributeN" where N is the number of the attribute. Values will
   * have the value of "testValueN.K" where N is the attribute number and K
   * is the value number.
   *
   * @param numAttributes Number of attributes to generate. 0 will return
   *                      a empty list.
   * @param numValues     Number of values to assign to each attribute. 0 will
   *                      assign a empty list of values to the attribute.
   * @param prefix        String to prefix the attribute values
   * @return              The generate attributes.
   *
   */
  private List<RawAttribute> generateAttributes(int numAttributes,
                                                      int numValues,
                                                      String prefix)
  {
    List<RawAttribute> attributes = new ArrayList<>();

    for (int i = 0; i < numAttributes; i++)
    {
      ArrayList<String> values = new ArrayList<>();
      for (int j = 0; j < numValues; j++)
      {
        values.add(prefix + "Value" + i + "." + j);
      }
      attributes.add(new LDAPAttribute("testAttribute" + i, values));
    }

    return attributes;
  }

  private Boolean attributesEquals(List<RawAttribute> attributes1,
                                   List<RawAttribute> attributes2)
  {
    if(attributes1.size() != attributes2.size())
    {
      return false;
    }

    for (int i = 0; i < attributes1.size(); i++)
    {
      RawAttribute attribute1 = attributes1.get(i);
      RawAttribute attribute2 = attributes2.get(i);
      if(!attribute1.getAttributeType().equals(attribute2.getAttributeType()))
      {
        return false;
      }
      if(attribute1.getValues().size() != attribute2.getValues().size())
      {
        return false;
      }
      List<ByteString> values1 = attribute1.getValues();
      List<ByteString> values2 = attribute2.getValues();
      for (int j = 0; j < values1.size(); j++)
      {
        if(!values1.get(j).equals(values2.get(j)))
        {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(dn);
    assertEquals(addRequest.getType(), OP_TYPE_ADD_REQUEST);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(dn);
    assertEquals(addRequest.getProtocolOpName(), "Add Request");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    AddRequestProtocolOp addRequest;
    List<RawAttribute> attributes;

    //Test to make sure the constructor with dn param works.
    addRequest = new AddRequestProtocolOp(dn);
    assertEquals(addRequest.getDN(), dn);
    assertNotNull(addRequest.getAttributes());
    assertEquals(addRequest.getAttributes().size(), 0);

    //Test to make sure the constructor with dn and attribute params works.
    attributes = generateAttributes(10, 5, "test");
    addRequest = new AddRequestProtocolOp(dn, attributes);
    assertEquals(addRequest.getDN(), dn);
    assertEquals(addRequest.getAttributes(), attributes);

    //Test to make sure the constructor with dn and attribute params works with
    //null attributes.
    addRequest = new AddRequestProtocolOp(dn, null);
    assertEquals(addRequest.getDN(), dn);
    assertNotNull(addRequest.getAttributes());
    assertEquals(addRequest.getAttributes().size(), 0);
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
    writer.writeStartSequence(OP_TYPE_ADD_REQUEST);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when invalid attributes in element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElement() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_ADD_REQUEST);
    writer.writeStartSequence();
    writer.writeEndSequence();
    writer.writeOctetString(dn);
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
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeOctetString(dn);
    writer.writeStartSequence();
    writer.writeEndSequence();
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

    AddRequestProtocolOp addEncoded = new AddRequestProtocolOp(null, null);
    addEncoded.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    AddRequestProtocolOp addDecoded =
        (AddRequestProtocolOp) LDAPReader.readProtocolOp(reader);
    assertEquals(addEncoded, addDecoded);
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
    AddRequestProtocolOp addEncoded;
    AddRequestProtocolOp addDecoded;
    List<RawAttribute> attributes;


    //Test case for a full encode decode operation with normal params.
    attributes = generateAttributes(10,5, "test");
    addEncoded = new AddRequestProtocolOp(dn, attributes);
    addEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(addEncoded.getType(), OP_TYPE_ADD_REQUEST);
    assertEquals(addEncoded.getDN(), addDecoded.getDN());
    assertTrue(attributesEquals(addEncoded.getAttributes(),
                                addDecoded.getAttributes()));

    //Test case for a full encode decode operation with large attributes.
    attributes = generateAttributes(100,50, "test");
    addEncoded = new AddRequestProtocolOp(dn, attributes);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(addEncoded.getDN(), addDecoded.getDN());
    assertTrue(attributesEquals(addEncoded.getAttributes(),
                                addDecoded.getAttributes()));

    //Test case for a full encode decode operation with no attributes.
    addEncoded = new AddRequestProtocolOp(dn, null);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(addEncoded.getDN(), addDecoded.getDN());
    assertTrue(attributesEquals(addEncoded.getAttributes(),
                                addDecoded.getAttributes()));
  }

  /**
   * Test toLDIF method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void testToLDIF() throws Exception
  {
    StringBuilder buffer = new StringBuilder();

    int numAttributes = 10;
    int numValues = 5;
    List<RawAttribute> attributes =
        generateAttributes(numAttributes, numValues, "test");
    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(dn, attributes);
    addRequest.toLDIF(buffer, 80);

    BufferedReader reader =
        new BufferedReader(new StringReader(buffer.toString()));
    String line = reader.readLine();
    assertEquals(line, "dn: "+dn);
    for (int i = 0; i < numAttributes; i++)
    {
      for (int j = 0; j < numValues; j++)
      {
        line = reader.readLine();
        assertEquals(line, "testAttribute"+i+": "+"testValue"+i+"."+j);
      }
    }
  }

  /**
   * Test toLDIF method with values that need base64 encoding.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void testToLDIFBase64() throws Exception
  {
    StringBuilder buffer = new StringBuilder();

    int numAttributes = 10;
    int numValues = 5;
    List<RawAttribute> attributes =
        generateAttributes(numAttributes, numValues, " test");

    ByteString dnNeedsBase64 = ByteString.valueOf("dc=example,dc=com ");

    AddRequestProtocolOp addRequest =
        new AddRequestProtocolOp(dnNeedsBase64, attributes);
    addRequest.toLDIF(buffer, 80);

    BufferedReader reader =
        new BufferedReader(new StringReader(buffer.toString()));
    String line = reader.readLine();
    assertEquals(line, "dn:: "+Base64.encode(dnNeedsBase64));
    for (int i = 0; i < numAttributes; i++)
    {
      for (int j = 0; j < numValues; j++)
      {
        line = reader.readLine();
        String expectedLine = " testValue" + i + "." + j;
        assertEquals(line, "testAttribute" + i + ":: "
            + Base64.encode(expectedLine.getBytes()));
      }
    }
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    StringBuilder buffer = new StringBuilder();

    int numAttributes = 10;
    int numValues = 5;
    List<RawAttribute> attributes =
        generateAttributes(numAttributes, numValues, "test");
    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(dn, attributes);
    addRequest.toString(buffer);

    StringBuilder key = new StringBuilder();
    key.append("AddRequest(dn=").append(dn).append(", attrs={");
    for (int i = 0; i < numAttributes; i++)
    {
      attributes.get(i).toString(key);
      if(i < numAttributes - 1)
      {
        key.append(", ");
      }
    }
    key.append("})");

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
    StringBuilder buffer = new StringBuilder();

    int numAttributes = 10;
    int numValues = 5;
    int indent = 5;
    List<RawAttribute> attributes =
        generateAttributes(numAttributes, numValues, "test");
    AddRequestProtocolOp addRequest = new AddRequestProtocolOp(dn, attributes);
    addRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i = 0; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    StringBuilder key = new StringBuilder();
    key.append(indentBuf).append("Add Request").append(EOL);
    key.append(indentBuf).append("  DN:  ").append(dn).append(EOL);
    key.append("  Attributes:").append(EOL);
    for (RawAttribute attribute : attributes)
    {
      attribute.toString(key, indent+4);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
