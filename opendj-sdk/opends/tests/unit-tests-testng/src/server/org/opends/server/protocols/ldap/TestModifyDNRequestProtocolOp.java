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

import org.opends.server.protocols.asn1.*;
import static org.opends.server.util.ServerConstants.EOL;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.ModifyDNRequestProtocolOp class.
 */
public class TestModifyDNRequestProtocolOp
{
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
  private static final ASN1OctetString dn =
      new ASN1OctetString("dc=example,dc=com");

  /**
   * The alt DN for modify DN requests in this test case.
   */
  private static final ASN1OctetString altDn =
      new ASN1OctetString("dc=alt,dc=example,dc=com");

  /**
   * The new DN for modify DN requests in this test case.
   */
  private static final ASN1OctetString newRdn =
      new ASN1OctetString("dc=example-new");

  /**
   * The alt new DN for modify DN requests in this test case.
   */
  private static final ASN1OctetString altNewRdn =
      new ASN1OctetString("ou=alt,dc=example-new");

  /**
   * The new superiour DN for modify DN requests in this test case.
   */
  private static final ASN1OctetString newSuperiorDn =
      new ASN1OctetString("dc=widget,dc=com");

  /**
   * The alt new superiour DN for modify DN requests in this test case.
   */
  private static final ASN1OctetString altNewSuperiorDn =
      new ASN1OctetString("dc=alt,dc=widget,dc=com");

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
   * Test to make sure that setter methods work.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testSetMethods() throws Exception
  {
    ModifyDNRequestProtocolOp modifyRequest;
    modifyRequest = new ModifyDNRequestProtocolOp(dn, newRdn, true,
                                                  newSuperiorDn);

    modifyRequest.setEntryDN(altDn);
    assertEquals(modifyRequest.getEntryDN(), altDn);

    modifyRequest.setNewRDN(altNewRdn);
    assertEquals(modifyRequest.getNewRDN(), altNewRdn);

    modifyRequest.setNewSuperior(altNewSuperiorDn);
    assertEquals(modifyRequest.getNewSuperior(), altNewSuperiorDn);

    modifyRequest.setDeleteOldRDN(false);
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
    ModifyDNRequestProtocolOp.decodeModifyDNRequest(null);
  }

  /**
   * Test the decode method when an empty element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeEmptyElement() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>();
    ModifyDNRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_DN_REQUEST,
                                                 elements));
  }

  /**
   * Test the decode method when the wrong number of elements is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElementNum() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Null());
    elements.add(new ASN1Null());
    ModifyDNRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_DN_REQUEST,
                                                 elements));
  }

  /**
   * Test the decode method when invalid attributes in element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElement() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(3);
    elements.add(new ASN1Null());
    elements.add(new ASN1Null());
    elements.add(new ASN1Null());
    ModifyDNRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_DN_REQUEST,
                                                 elements));
  }

  /**
   * Test the decode method when an element w/ wrong op type is passed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeWrongElementType() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(3);
    elements.add(dn);
    elements.add(newRdn);
    elements.add(new ASN1Boolean(true));
    ModifyDNRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_DN_RESPONSE,
                                                 elements));
  }

  /**
   * Test the encode and decode methods with null params
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = Exception.class)
  public void testNullEncodeDecode() throws Exception
  {
    ModifyDNRequestProtocolOp modifyEncoded;
    ModifyDNRequestProtocolOp modifyDecoded;
    ASN1Element element;

    modifyEncoded = new ModifyDNRequestProtocolOp(null, null, true);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyDNRequestProtocolOp)ModifyDNRequestProtocolOp.decode(
        element);
  }

  /**
   * Test the encode and decode methods and corner cases.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testEncodeDecode() throws Exception
  {
    ModifyDNRequestProtocolOp modifyEncoded;
    ModifyDNRequestProtocolOp modifyDecoded;
    ASN1Element element;

    modifyEncoded = new ModifyDNRequestProtocolOp(dn, newRdn, true,
                                                  newSuperiorDn);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyDNRequestProtocolOp)ModifyDNRequestProtocolOp.decode(
        element);

    assertEquals(modifyEncoded.getEntryDN(), modifyDecoded.getEntryDN());
    assertEquals(modifyEncoded.getNewRDN(), modifyDecoded.getNewRDN());
    assertEquals(modifyEncoded.getNewSuperior(), modifyDecoded.getNewSuperior());
    assertEquals(modifyEncoded.deleteOldRDN(), modifyDecoded.deleteOldRDN());

    modifyEncoded = new ModifyDNRequestProtocolOp(dn, newRdn, true);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyDNRequestProtocolOp)ModifyDNRequestProtocolOp.decode(
        element);

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
    dn.toString(key);
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
