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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.util.Utils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.ModifyResponseProtocolOp class.
 */
public class TestModifyResponseProtocolOp extends LdapTestCase
{
  /**
   * The protocol op type for modify responses.
   */
  public static final byte OP_TYPE_MODIFY_RESPONSE = 0x67;

  /**
   * The result code for add result operations.
   */
  private static final int resultCode = 10;

  /**
   * The error message to use for add result operations.
   */
  private static final LocalizableMessage resultMsg = LocalizableMessage.raw("Test Successful");

/**
   * The DN to use for add result operations.
   */
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
    ModifyResponseProtocolOp modifyResponse = new ModifyResponseProtocolOp(
        resultCode);
    assertEquals(modifyResponse.getType(), OP_TYPE_MODIFY_RESPONSE);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    ModifyResponseProtocolOp modifyResponse = new ModifyResponseProtocolOp(
        resultCode);
    assertEquals(modifyResponse.getProtocolOpName(), "Modify Response");
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
    ModifyResponseProtocolOp modifyResponse = new ModifyResponseProtocolOp(resultCode);
    assertEquals(modifyResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code and error message
    //params works.
    modifyResponse = new ModifyResponseProtocolOp(resultCode, resultMsg);
    assertEquals(modifyResponse.getErrorMessage(), resultMsg);
    assertEquals(modifyResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code, message, dn, and
    //referral params works.
    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");

    modifyResponse = new ModifyResponseProtocolOp(resultCode, resultMsg, dn,
                                                  referralURLs);
    assertEquals(modifyResponse.getErrorMessage(), resultMsg);
    assertEquals(modifyResponse.getResultCode(), resultCode);
    assertEquals(modifyResponse.getMatchedDN(), dn);
    assertEquals(modifyResponse.getReferralURLs(), referralURLs);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    ModifyResponseProtocolOp modifyEncoded;
    ModifyResponseProtocolOp modifyDecoded;

    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");


    //Test case for a full encode decode operation with normal params.
    modifyEncoded = new ModifyResponseProtocolOp(resultCode, resultMsg, dn,
                                                 referralURLs);
    modifyEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyResponseProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getType(), OP_TYPE_MODIFY_RESPONSE);
    assertEquals(modifyEncoded.getMatchedDN().compareTo(
        modifyDecoded.getMatchedDN()),
                 0);
    assertEquals(modifyEncoded.getErrorMessage(),
                 modifyDecoded.getErrorMessage());
    assertEquals(modifyEncoded.getResultCode(), modifyDecoded.getResultCode());
    assertEquals(modifyEncoded.getReferralURLs(),
                 modifyDecoded.getReferralURLs());


    //Test case for a full encode decode operation with an empty DN params.
    modifyEncoded = new ModifyResponseProtocolOp(resultCode, resultMsg, DN.rootDN(),
                                                 referralURLs);
    builder.clear();
    modifyEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyResponseProtocolOp)LDAPReader.readProtocolOp(reader);
    assertNull(modifyDecoded.getMatchedDN());

    //Test case for a full empty referral url param.
    ArrayList<String> emptyReferralURLs = new ArrayList<>();
    modifyEncoded = new ModifyResponseProtocolOp(resultCode, resultMsg, dn,
                                                 emptyReferralURLs);
    builder.clear();
    modifyEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyResponseProtocolOp)LDAPReader.readProtocolOp(reader);
    assertNull(modifyDecoded.getReferralURLs());

    //Test case for a full encode decode operation with resultCode param only.
    modifyEncoded = new ModifyResponseProtocolOp(resultCode);
    builder.clear();
    modifyEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyResponseProtocolOp)LDAPReader.readProtocolOp(reader);

    assertNull(modifyDecoded.getMatchedDN());
    assertNull(modifyDecoded.getErrorMessage());
    assertEquals(modifyEncoded.getResultCode(), modifyDecoded.getResultCode());
    assertNull(modifyDecoded.getReferralURLs());
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

    ModifyResponseProtocolOp modifyResponse = new ModifyResponseProtocolOp(resultCode, resultMsg, dn, referralURLs);
    StringBuilder buffer = new StringBuilder();
    modifyResponse.toString(buffer);

    String key = "ModifyResponse(resultCode=" + resultCode
        + ", errorMessage=" + resultMsg
        + ", matchedDN=" + dn
        + ", referralURLs={" + joinAsString(", ", referralURLs) + "})";

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
    ModifyResponseProtocolOp modifyResponse;
    StringBuilder buffer = new StringBuilder();

    ArrayList<String> referralURLs = new ArrayList<>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");
    int indent = 5;
    int i;

    modifyResponse = new ModifyResponseProtocolOp(resultCode, resultMsg, dn, referralURLs);
    modifyResponse.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    StringBuilder key = new StringBuilder();
    key.append(indentBuf).append("Modify Response").append(EOL);
    key.append(indentBuf).append("  Result Code:  ").append(resultCode).append(EOL);
    key.append(indentBuf).append("  Error LocalizableMessage:  ").append(resultMsg).append(EOL);
    key.append(indentBuf).append("  Matched DN:  ").append(dn).append(EOL);

    key.append(indentBuf).append("  Referral URLs:  ").append(EOL);
    for (String url : referralURLs)
    {
      key.append(indentBuf).append("  ").append(url).append(EOL);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
