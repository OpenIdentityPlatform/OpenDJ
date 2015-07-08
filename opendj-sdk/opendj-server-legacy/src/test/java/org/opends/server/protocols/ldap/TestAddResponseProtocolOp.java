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

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RDN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.AddResponseProtocolOp class.
 */
public class TestAddResponseProtocolOp extends DirectoryServerTestCase {
  /**
   * The protocol op type for add responses.
   */
  private static final byte OP_TYPE_ADD_RESPONSE = 0x69;

  /**
   * The result code for add result operations.
   */
  private static final int resultCode = 10;

  /**
   * The error message to use for add result operations.
   */
  private static final LocalizableMessage resultMsg = LocalizableMessage.raw("Test Successful");

  /** The DN to use for add result operations. */
  private DN dn;

  @BeforeClass
  public void setupDN() throws Exception
  {
    // Starts the server if not already started
    TestCaseUtils.startServer();

    //Setup the DN to use in the response tests.
    AttributeType attribute =
        DirectoryServer.getDefaultAttributeType("testAttribute");
    ByteString attributeValue = ByteString.valueOf("testValue");
    dn = new DN(new RDN[] { RDN.create(attribute, attributeValue) });
  }

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getType(), OP_TYPE_ADD_RESPONSE);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getProtocolOpName(), "Add Response");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    //Test to make sure the constructor with result code param works.
    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code and error message
    //params works.
    addResponse = new AddResponseProtocolOp(resultCode, resultMsg);
    assertEquals(addResponse.getErrorMessage(), resultMsg);
    assertEquals(addResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code, message, dn, and
    //referral params works.
    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    assertEquals(addResponse.getErrorMessage(), resultMsg);
    assertEquals(addResponse.getResultCode(), resultCode);
    assertEquals(addResponse.getMatchedDN(), dn);
    assertEquals(addResponse.getReferralURLs(), referralURLs);
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
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an element with a invalid result code is
   * passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidResultCode() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeOctetString("Invalid Data");
    writer.writeNull();
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an element with a invalid dn is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testDecodeInvalidDN() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeInteger(resultCode);
    writer.writeNull();
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an element with a invalid result message is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testDecodeInvalidResultMsg() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeInteger(resultCode);
    writer.writeOctetString(dn.toString());
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an element with a invalid referral URL is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testDecodeInvalidReferralURLs() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_ADD_RESPONSE);
    writer.writeInteger(resultCode);
    writer.writeOctetString(dn.toString());
    writer.writeOctetString(resultMsg.toString());
    writer.writeNull();
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
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
    AddResponseProtocolOp addEncoded;
    AddResponseProtocolOp addDecoded;

    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");


    //Test case for a full encode decode operation with normal params.
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    addEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(addEncoded.getType(), OP_TYPE_ADD_RESPONSE);
    assertEquals(addEncoded.getMatchedDN().compareTo(addDecoded.getMatchedDN()),
                 0);
    assertEquals(addEncoded.getErrorMessage(), addDecoded.getErrorMessage());
    assertEquals(addEncoded.getResultCode(), addDecoded.getResultCode());
    assertEquals(addEncoded.getReferralURLs(), addDecoded.getReferralURLs());


    //Test case for a full encode decode operation with an empty DN params.
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, DN.rootDN(),
                                           referralURLs);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);
    assertEquals(addDecoded.getMatchedDN(), null);

    //Test case for a full empty referral url param.
    ArrayList<String> emptyReferralURLs = new ArrayList<>();
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           emptyReferralURLs);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);
    assertTrue(addDecoded.getReferralURLs() == null);

    //Test case for a full encode decode operation with resultCode param only.
    addEncoded = new AddResponseProtocolOp(resultCode);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(addDecoded.getMatchedDN(), null);
    assertEquals(addDecoded.getErrorMessage(), null);
    assertEquals(addEncoded.getResultCode(), addDecoded.getResultCode());
    assertTrue(addDecoded.getReferralURLs() == null);
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    AddResponseProtocolOp addResponse;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();

    List<String> referralURLs = Arrays.asList(
        "ds1.example.com",
        "ds2.example.com",
        "ds3.example.com");

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn, referralURLs);
    addResponse.toString(buffer);

    key.append("AddResponse(resultCode=" + resultCode
        + ", " + "errorMessage=" + resultMsg + ", matchedDN=" + dn + ", "
        + "referralURLs={");
    join(key, referralURLs);
    key.append("})");

    assertEquals(buffer.toString(), key.toString());
  }

  static void join(StringBuilder sb, List<String> referralURLs)
  {
    Iterator<String> iterator = referralURLs.iterator();
    sb.append(iterator.next());
    while (iterator.hasNext())
    {
      sb.append(", ");
      sb.append(iterator.next());
    }
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringMultiLine() throws Exception
  {
    AddResponseProtocolOp addResponse;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();

    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");
    int indent = 5;
    int i;

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    addResponse.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Add Response");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Result Code:  ");
    key.append(resultCode);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Error Message:  ");
    key.append(resultMsg);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Matched DN:  ");
    key.append(dn.toString());
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Referral URLs:  ");
    key.append(EOL);

    for (String url : referralURLs)
    {
      key.append(indentBuf);
      key.append("  ");
      key.append(url);
      key.append(EOL);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
