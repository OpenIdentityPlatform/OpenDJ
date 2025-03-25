/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import static org.forgerock.util.Utils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.AddResponseProtocolOp class.
 */
public class TestAddResponseProtocolOp extends DirectoryServerTestCase {
  /** The protocol op type for add responses. */
  private static final byte OP_TYPE_ADD_RESPONSE = 0x69;
  /** The result code for add result operations. */
  private static final int resultCode = 10;
  /** The error message to use for add result operations. */
  private static final LocalizableMessage resultMsg = LocalizableMessage.raw("Test Successful");

  /** The DN to use for add result operations. */
  private DN dn;

  @BeforeClass
  public void setupDN() throws Exception
  {
    // Starts the server if not already started
    TestCaseUtils.startServer();

    //Setup the DN to use in the response tests.
    dn = DN.valueOf("testAttribute=testValue");
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
    assertNull(addDecoded.getMatchedDN());

    //Test case for a full empty referral url param.
    ArrayList<String> emptyReferralURLs = new ArrayList<>();
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           emptyReferralURLs);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);
    assertNull(addDecoded.getReferralURLs());

    //Test case for a full encode decode operation with resultCode param only.
    addEncoded = new AddResponseProtocolOp(resultCode);
    builder.clear();
    addEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    addDecoded = (AddResponseProtocolOp)LDAPReader.readProtocolOp(reader);

    assertNull(addDecoded.getMatchedDN());
    assertNull(addDecoded.getErrorMessage());
    assertEquals(addEncoded.getResultCode(), addDecoded.getResultCode());
    assertNull(addDecoded.getReferralURLs());
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    List<String> referralURLs = Arrays.asList(
        "ds1.example.com",
        "ds2.example.com",
        "ds3.example.com");

    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn, referralURLs);

    StringBuilder buffer = new StringBuilder();
    addResponse.toString(buffer);

    String key = "AddResponse(resultCode=" + resultCode
        + ", errorMessage=" + resultMsg
        + ", matchedDN=" + dn +
        ", referralURLs={" + joinAsString(", ", referralURLs) + "})";

    assertEquals(buffer.toString(), key);
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test(singleThreaded = true)
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

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn, referralURLs);
    addResponse.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf).append("Add Response").append(EOL);
    key.append(indentBuf).append("  Result Code:  ").append(resultCode).append(EOL);
    key.append(indentBuf).append("  Error Message:  ").append(resultMsg).append(EOL);
    key.append(indentBuf).append("  Matched DN:  ").append(dn).append(EOL);

    key.append(indentBuf).append("  Referral URLs:  ").append(EOL);
    for (String url : referralURLs)
    {
      key.append(indentBuf).append("  ").append(url).append(EOL);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
