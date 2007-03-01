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

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Sequence;
import static org.opends.server.util.ServerConstants.EOL;
import org.opends.server.types.ModificationType;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Random;

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
  private static final ASN1OctetString dn =
      new ASN1OctetString("dc=example,dc=com");

  /**
   * The alternative DN for add requests in this test case.
   */
  private static final ASN1OctetString dnAlt =
      new ASN1OctetString("dc=sun,dc=com");

  /**
   * Generate modifications for use in test cases. Attributes will have names
   * like "testAttributeN" where N is the number of the attribute. Modification
   * types will be random.
   *
   * @param numAttributes Number of attributes to generate. 0 will return
   *                      a empty list.
   * @param prefix        String to prefix the attribute values
   * @return              The generate attributes.
   *
   */
  private ArrayList<LDAPModification> generateModifications(int numAttributes,
                                                            String prefix)
  {
    ArrayList<LDAPModification> modifies = new ArrayList<LDAPModification>();
    LDAPAttribute attribute;
    ModificationType modificationType;
    Random randomGenerator = new Random(0);
    int i, j;

    for(i = 0; i < numAttributes; i++)
    {
      attribute = new LDAPAttribute("testAttribute"+i);
      switch(i % 4)
      {
        case 0 : modificationType = ModificationType.ADD;
          break;
        case 1 : modificationType = ModificationType.DELETE;
          break;
        case 2 : modificationType = ModificationType.REPLACE;
          break;
        case 3 : modificationType = ModificationType.INCREMENT;
          break;
        default : modificationType = ModificationType.ADD;
      }

      modifies.add(new LDAPModification(modificationType, attribute));
    }

    return modifies;
  }

  private Boolean modificationsEquals(ArrayList<LDAPModification> modifies1,
                                      ArrayList<LDAPModification> modifies2)
  {
    if(modifies1.size() != modifies2.size())
    {
      return false;
    }

    int i, j;
    LDAPModification modify1;
    LDAPModification modify2;

    for(i = 0; i < modifies1.size(); i++)
    {
      modify1 = modifies1.get(i);
      modify2 = modifies2.get(i);
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
    ArrayList<LDAPModification> modifications;

    //Test to make sure the constructor with dn param works.
    modifyRequest = new ModifyRequestProtocolOp(dn);
    assertEquals(modifyRequest.getDN(), dn);
    assertNotNull(modifyRequest.getModifications());
    assertEquals(modifyRequest.getModifications().size(), 0);

    //Test to make sure the constructor with dn and attribute params works.
    modifications = generateModifications(10, "test");
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
   * Test to make sure that setter methods work.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testSetMethods() throws Exception
  {
    ModifyRequestProtocolOp modifyRequest;

    modifyRequest = new ModifyRequestProtocolOp(dn);
    assertEquals(modifyRequest.getDN(), dn);
    modifyRequest.setDN(dnAlt);
    assertEquals(modifyRequest.getDN(), dnAlt);

  }

  /**
   * Test the decode method when an null element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeNullElement() throws Exception
  {
    ModifyRequestProtocolOp.decodeModifyRequest(null);
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
    ModifyRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_REQUEST,
                                                    elements));
  }

  /**
   * Test the decode method when invalid modifies in element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidElement() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    ArrayList<ASN1Element> attrElements =
         new ArrayList<ASN1Element>();
    elements.add(new ASN1Sequence(attrElements));
    elements.add(dn);
    ModifyRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_REQUEST,
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
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    ArrayList<ASN1Element> attrElements =
         new ArrayList<ASN1Element>();
    elements.add(dn);
    elements.add(new ASN1Sequence(attrElements));
    ModifyRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_MODIFY_RESPONSE,
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
    ModifyRequestProtocolOp modifyEncoded;
    ModifyRequestProtocolOp modifyDecoded;
    ASN1Element element;

    modifyEncoded = new ModifyRequestProtocolOp(null, null);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyRequestProtocolOp)ModifyRequestProtocolOp.decode(
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
    ModifyRequestProtocolOp modifyEncoded;
    ModifyRequestProtocolOp modifyDecoded;
    ASN1Element element;
    ArrayList<LDAPModification> modifies;


    //Test case for a full encode decode operation with normal params.
    modifies = generateModifications(10,"test");
    modifyEncoded = new ModifyRequestProtocolOp(dn, modifies);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyRequestProtocolOp)ModifyRequestProtocolOp.decode(
        element);

    assertEquals(modifyEncoded.getType(), OP_TYPE_MODIFY_REQUEST);
    assertEquals(modifyEncoded.getDN(), modifyDecoded.getDN());
    assertTrue(modificationsEquals(modifyEncoded.getModifications(),
                                   modifyDecoded.getModifications()));

    //Test case for a full encode decode operation with large modifications.
    modifies = generateModifications(100,"test");
    modifyEncoded = new ModifyRequestProtocolOp(dn, modifies);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyRequestProtocolOp)ModifyRequestProtocolOp.decode(
        element);

    assertEquals(modifyEncoded.getDN(), modifyDecoded.getDN());
    assertTrue(modificationsEquals(modifyEncoded.getModifications(),
                                   modifyDecoded.getModifications()));

    //Test case for a full encode decode operation with no attributes.
    modifyEncoded = new ModifyRequestProtocolOp(dn, null);
    element = modifyEncoded.encode();
    modifyDecoded = (ModifyRequestProtocolOp)ModifyRequestProtocolOp.decode(
        element);

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
    ModifyRequestProtocolOp modifyRequest;
    ArrayList<LDAPModification> modifications;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();
    int i;
    int numModifications;

    numModifications = 10;
    modifications = generateModifications(numModifications, "test");
    modifyRequest = new ModifyRequestProtocolOp(dn, modifications);
    modifyRequest.toString(buffer);

    key.append("ModifyRequest(dn="+dn+", mods={");
    for(i = 0; i < numModifications; i++)
    {
      modifications.get(i).toString(key);
      if(i < numModifications - 1)
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
    ModifyRequestProtocolOp modifyRequest;
    ArrayList<LDAPModification> modifications;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();
    int i;
    int numModifications,  indent;

    numModifications = 10;
    indent = 5;
    modifications = generateModifications(numModifications, "test");
    modifyRequest = new ModifyRequestProtocolOp(dn, modifications);
    modifyRequest.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Modify Request");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  DN:  ");
    dn.toString(key);
    key.append(EOL);

    key.append("  Modifications:");
    key.append(EOL);

    for (LDAPModification modify : modifications)
    {
      modify.toString(key, indent+4);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}
