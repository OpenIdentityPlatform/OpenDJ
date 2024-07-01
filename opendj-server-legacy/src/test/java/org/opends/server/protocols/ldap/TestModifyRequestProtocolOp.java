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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.util.Utils;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawModification;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.ModifyRequestProtocolOp class.
 */
public class TestModifyRequestProtocolOp extends LdapTestCase
{
  /**
   * The protocol op type for modify requests.
   */
  public static final byte OP_TYPE_MODIFY_REQUEST = 0x66;



  /**
   * The protocol op type for modify responses.
   */
  public static final byte OP_TYPE_MODIFY_RESPONSE = 0x67;


  /**
   * The DN for modify requests in this test case.
   */
  private static final ByteString dn =
      ByteString.valueOfUtf8("dc=example,dc=com");

  /**
   * The alternative DN for add requests in this test case.
   */
  private static final ByteString dnAlt =
      ByteString.valueOfUtf8("dc=sun,dc=com");

  /**
   * Generate modifications for use in test cases. Attributes will have names
   * like "testAttributeN" where N is the number of the attribute. Modification
   * types will be random.
   *
   * @param numAttributes Number of attributes to generate. 0 will return
   *                      a empty list.
   * @return              The generate attributes.
   *
   */
  private List<RawModification> generateModifications(int numAttributes)
  {
    List<RawModification> modifies = new ArrayList<>();
    for(int i = 0; i < numAttributes; i++)
    {
      LDAPAttribute attribute = new LDAPAttribute("testAttribute" + i);
      modifies.add(new LDAPModification(toModificationType(i), attribute));
    }
    return modifies;
  }

  private ModificationType toModificationType(int i)
  {
    switch(i % 4)
    {
    case 0:
      return ModificationType.ADD;
    case 1:
      return ModificationType.DELETE;
    case 2:
      return ModificationType.REPLACE;
    case 3:
      return ModificationType.INCREMENT;
    default:
      return ModificationType.ADD;
    }
  }

  private Boolean modificationsEquals(List<RawModification> modifies1,
                                      List<RawModification> modifies2)
  {
    if(modifies1.size() != modifies2.size())
    {
      return false;
    }

    for(int i = 0; i < modifies1.size(); i++)
    {
      RawModification modify1 = modifies1.get(i);
      RawModification modify2 = modifies2.get(i);
      if(!modify1.getAttribute().getAttributeType().equals(
          modify2.getAttribute().getAttributeType()))
      {
        System.out.println(modify1.getAttribute());
        System.out.println(modify2.getAttribute());
        System.out.println("attribute !=");
        return false;
      }
      if(!modify1.getModificationType().equals(modify2.getModificationType()))
      {
        System.out.println("mod type !=");
        return false;
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
    ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(dn);
    assertEquals(modifyRequest.getType(), OP_TYPE_MODIFY_REQUEST);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(dn);
    assertEquals(modifyRequest.getProtocolOpName(), "Modify Request");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    ModifyRequestProtocolOp modifyRequest;
    List<RawModification> modifications;

    //Test to make sure the constructor with dn param works.
    modifyRequest = new ModifyRequestProtocolOp(dn);
    assertEquals(modifyRequest.getDN(), dn);
    assertNotNull(modifyRequest.getModifications());
    assertEquals(modifyRequest.getModifications().size(), 0);

    //Test to make sure the constructor with dn and attribute params works.
    modifications = generateModifications(10);
    modifyRequest = new ModifyRequestProtocolOp(dn, modifications);
    assertEquals(modifyRequest.getDN(), dn);
    assertEquals(modifyRequest.getModifications(), modifications);

    //Test to make sure the constructor with dn and attribute params works with
    //null attributes.
    modifyRequest = new ModifyRequestProtocolOp(dn, null);
    assertEquals(modifyRequest.getDN(), dn);
    assertNotNull(modifyRequest.getModifications());
    assertEquals(modifyRequest.getModifications().size(), 0);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
    writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when invalid modifies in element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElement() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
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
    writer.writeStartSequence(OP_TYPE_MODIFY_RESPONSE);
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
    ModifyRequestProtocolOp modifyEncoded;

    modifyEncoded = new ModifyRequestProtocolOp(null, null);
    modifyEncoded.write(writer);

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
    ModifyRequestProtocolOp modifyEncoded;
    ModifyRequestProtocolOp modifyDecoded;
    List<RawModification> modifies;


    //Test case for a full encode decode operation with normal params.
    modifies = generateModifications(10);
    modifyEncoded = new ModifyRequestProtocolOp(dn, modifies);
    modifyEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getType(), OP_TYPE_MODIFY_REQUEST);
    assertEquals(modifyEncoded.getDN(), modifyDecoded.getDN());
    assertTrue(modificationsEquals(modifyEncoded.getModifications(),
                                   modifyDecoded.getModifications()));

    //Test case for a full encode decode operation with large modifications.
    modifies = generateModifications(100);
    modifyEncoded = new ModifyRequestProtocolOp(dn, modifies);
    builder.clear();
    modifyEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getDN(), modifyDecoded.getDN());
    assertTrue(modificationsEquals(modifyEncoded.getModifications(),
                                   modifyDecoded.getModifications()));

    //Test case for a full encode decode operation with no attributes.
    modifyEncoded = new ModifyRequestProtocolOp(dn, null);
    builder.clear();
    modifyEncoded.write(writer);
    reader = ASN1.getReader(builder.toByteString());
    modifyDecoded = (ModifyRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(modifyEncoded.getDN(), modifyDecoded.getDN());
    assertTrue(modificationsEquals(modifyEncoded.getModifications(),
                                   modifyDecoded.getModifications()));
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
    StringBuilder key = new StringBuilder();
    List<RawModification> modifications = generateModifications(10);
    ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(dn, modifications);
    modifyRequest.toString(buffer);

    key.append("ModifyRequest(dn=").append(dn).append(", mods={");
    Utils.joinAsString(key, ", ", modifications);
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
    StringBuilder key = new StringBuilder();

    int numModifications = 10;
    int indent = 5;
    List<RawModification> modifications = generateModifications(numModifications);
    ModifyRequestProtocolOp modifyRequest =
        new ModifyRequestProtocolOp(dn, modifications);
    modifyRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i = 0; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Modify Request");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  DN:  ");
    key.append(dn);

    key.append(EOL);
    key.append("  Modifications:");
    key.append(EOL);

    for (RawModification modify : modifications)
    {
      modify.toString(key, indent+4);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
