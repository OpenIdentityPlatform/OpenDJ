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

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import static org.opends.server.util.ServerConstants.EOL;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.ModifyDNRequestProtocolOp class.
 */
public class TestModifyDNRequestProtocolOp extends DirectoryServerTestCase {
  /**
   * The protocol op type for modify DN requests.
   */
  public static final byte OP_TYPE_MODIFY_DN_REQUEST = 0x6C;



  /**
   * The protocol op type for modify DN responses.
   */
  public static final byte OP_TYPE_MODIFY_DN_RESPONSE = 0x6D;

  /**
   * The DN for modify DN requests in this test case.
   */
  private static final ByteString dn =
      ByteString.valueOf("dc=example,dc=com");

  /**
   * The alt DN for modify DN requests in this test case.
   */
  private static final ByteString altDn =
      ByteString.valueOf("dc=alt,dc=example,dc=com");

  /**
   * The new DN for modify DN requests in this test case.
   */
  private static final ByteString newRdn =
      ByteString.valueOf("dc=example-new");

  /**
   * The alt new DN for modify DN requests in this test case.
   */
  private static final ByteString altNewRdn =
      ByteString.valueOf("ou=alt,dc=example-new");

  /**
   * The new superiour DN for modify DN requests in this test case.
   */
  private static final ByteString newSuperiorDn =
      ByteString.valueOf("dc=widget,dc=com");

  /**
   * The alt new superiour DN for modify DN requests in this test case.
   */
  private static final ByteString altNewSuperiorDn =
      ByteString.valueOf("dc=alt,dc=widget,dc=com");

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    ModifyDNRequestProtocolOp modifyRequest = new ModifyDNRequestProtocolOp(dn,
                                                                            newRdn, true);
    assertEquals(modifyRequest.getType(), OP_TYPE_MODIFY_DN_REQUEST);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    ModifyDNRequestProtocolOp modifyRequest = new ModifyDNRequestProtocolOp(dn,
                                                                            newRdn, true);
    assertEquals(modifyRequest.getProtocolOpName(), "Modify DN Request");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    ModifyDNRequestProtocolOp modifyRequest;

    modifyRequest = new ModifyDNRequestProtocolOp(dn, newRdn, true);
    assertEquals(modifyRequest.getEntryDN(), dn);
    assertEquals(modifyRequest.getNewRDN(), newRdn);
    assertEquals(modifyRequest.deleteOldRDN(), true);
    assertNull(modifyRequest.getNewSuperior());

    modifyRequest = new ModifyDNRequestProtocolOp(dn, newRdn, false,
                                                  newSuperiorDn);
    assertEquals(modifyRequest.getEntryDN(), dn);
    assertEquals(modifyRequest.getNewRDN(), newRdn);
    assertEquals(modifyRequest.getNewSuperior(), newSuperiorDn);
    assertEquals(modifyRequest.deleteOldRDN(), false);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when the wrong number of elements is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElementNum() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    writer.writeNull();
    writer.writeNull();
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
    writer.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    writer.writeNull();
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
    writer.writeStartSequence(OP_TYPE_MODIFY_DN_RESPONSE);
    writer.writeOctetString(dn);
    writer.writeOctetString(newRdn);
    writer.writeBoolean(true);
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
    ModifyDNRequestProtocolOp modifyEncoded;
    ModifyDNRequestProtocolOp modifyDecoded;

    modifyEncoded = new ModifyDNRequestProtocolOp(null, null, true);
    modifyEncoded.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyDNRequestProtocolOp)LDAPReader.readProtocolOp(reader);
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
    ModifyDNRequestProtocolOp modifyEncoded;
    ModifyDNRequestProtocolOp modifyDecoded;

    modifyEncoded = new ModifyDNRequestProtocolOp(dn, newRdn, true,
                                                  newSuperiorDn);
    modifyEncoded.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyDNRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getEntryDN(), modifyDecoded.getEntryDN());
    assertEquals(modifyEncoded.getNewRDN(), modifyDecoded.getNewRDN());
    assertEquals(modifyEncoded.getNewSuperior(), modifyDecoded.getNewSuperior());
    assertEquals(modifyEncoded.deleteOldRDN(), modifyDecoded.deleteOldRDN());

    builder.clear();
    modifyEncoded = new ModifyDNRequestProtocolOp(dn, newRdn, true);
    modifyEncoded.write(writer);

    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyDNRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getEntryDN(), modifyDecoded.getEntryDN());
    assertEquals(modifyEncoded.getNewRDN(), modifyDecoded.getNewRDN());
    assertEquals(modifyEncoded.getNewSuperior(), modifyDecoded.getNewSuperior());
    assertEquals(modifyEncoded.deleteOldRDN(), modifyDecoded.deleteOldRDN());
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    ModifyDNRequestProtocolOp modifyRequest;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();

    modifyRequest = new ModifyDNRequestProtocolOp(dn, newRdn, true,
                                                  newSuperiorDn);
    modifyRequest.toString(buffer);

    key.append("ModifyDNRequest(dn="+dn+", newRDN="+newRdn+", " +
        "deleteOldRDN="+true+", newSuperior="+newSuperiorDn+")");

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
    ModifyDNRequestProtocolOp modifyRequest;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();
    int i;
    int indent;

    indent = 5;
    modifyRequest = new ModifyDNRequestProtocolOp(dn, newRdn, true,
                                                  newSuperiorDn);
    modifyRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Modify DN Request");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Entry DN:  ");
    key.append(dn);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  New RDN:  ");
    key.append(newRdn);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Delete Old RDN:  ");
    key.append(true);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  New Superior:  ");
    key.append(newSuperiorDn);
    key.append(EOL);

    assertEquals(buffer.toString(), key.toString());
  }
}
