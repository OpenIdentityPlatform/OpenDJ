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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import static org.testng.AssertJUnit.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.SubtreeSpecificationSet;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RFC3672SubtreeSpecification;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.schema.RFC3672SubtreeSpecificationSyntax;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;

/**
 * This class defines a set of tests for the {@link Entry} class.
 * <p>
 * At the moment this test suite only tests the getAttributeValue and
 * getAttributeValues methods.
 */
public final class TestEntry extends TypesTestCase {

  /**
   * Create an entry with the specified single attribute type and value.
   *
   * @param type
   *          The attribute type.
   * @param value
   *          The attribute value.
   * @return The test entry.
   */
  private Entry createTestEntry(AttributeType type, String value) {
    String[] values = new String[1];
    values[0] = value;

    return createTestEntry(type, values);
  }

  /**
   * Create an entry with the specified attribute type and values.
   *
   * @param type
   *          The attribute type.
   * @param values
   *          The array of attribute values.
   * @return The test entry.
   */
  private Entry createTestEntry(AttributeType type, String[] values) {
    // Construct entry DN.
    DN entryDN;
    try {
      entryDN = DN.decode("dc=example, dc=com");
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }

    // Get default object classes.
    ObjectClass top = DirectoryServer.getObjectClass("top");
    if (top == null) {
      throw new RuntimeException("Unable to resolve object class top");
    }

    ObjectClass extensible = DirectoryServer
        .getObjectClass("extensibleobject");
    if (extensible == null) {
      throw new RuntimeException(
          "Unable to resolve object class extensibleObject");
    }

    HashMap<ObjectClass, String> objectClasses =
      new HashMap<ObjectClass, String>();
    objectClasses.put(top, top.getNameOrOID());
    objectClasses.put(extensible, extensible.getNameOrOID());

    // Construct the empty entry.
    Entry testEntry = new Entry(entryDN, objectClasses, null, null);

    // Now add the attribute.
    LinkedHashSet<AttributeValue> attributeValues =
      new LinkedHashSet<AttributeValue>();
    for (String value : values) {
      AttributeValue attributeValue = new AttributeValue(type, value);
      attributeValues.add(attributeValue);
    }
    Attribute attribute = new Attribute(type, type.getNameOrOID(),
        attributeValues);
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(attribute);
    testEntry.putAttribute(type, attributes);

    return testEntry;
  }

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll start
    // the server.
    TestCaseUtils.startServer();
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValueNotFound() throws Exception {
    AttributeType type1 = DirectoryServer.getAttributeType("description");
    AttributeType type2 = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type1, "hello world");

    assertEquals(null, entry
        .getAttributeValue(type2, BooleanSyntax.DECODER));
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValueBooleanTrue() throws Exception {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "true");

    assertEquals(Boolean.TRUE, entry.getAttributeValue(type,
        BooleanSyntax.DECODER));
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValueBooleanFalse() throws Exception {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "false");

    assertEquals(Boolean.FALSE, entry.getAttributeValue(type,
        BooleanSyntax.DECODER));
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = DirectoryException.class)
  public void testGetAttributeValueBooleanBad() throws Exception {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "bad-value");
    entry.getAttributeValue(type, BooleanSyntax.DECODER);
    throw new RuntimeException(
         "An illegal boolean value did not throw an exception");
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValuesInteger() throws Exception {
    AttributeType type = DirectoryServer
        .getAttributeType("supportedldapversion");
    String[] values = new String[] { "-4", "-2", "0", "1", "3" };

    HashSet<Integer> expected = new HashSet<Integer>();
    for (String value : values) {
      expected.add(Integer.valueOf(value));
    }

    Entry entry = createTestEntry(type, values);
    HashSet<Integer> result = new HashSet<Integer>();
    entry.getAttributeValues(type, IntegerSyntax.DECODER, result);

    assertEquals(expected, result);
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = DirectoryException.class)
  public void testGetAttributeValueIntegerBad() throws Exception {
    AttributeType type = DirectoryServer
        .getAttributeType("supportedldapversion");
    String[] values = new String[] { "-4", "-2", "xxx", "1", "3" };

    HashSet<Integer> result = new HashSet<Integer>();
    Entry entry = createTestEntry(type, values);
    entry.getAttributeValues(type, IntegerSyntax.DECODER, result);
    throw new RuntimeException(
         "An illegal integer value did not throw an exception");
  }

  /**
   * Test the
   * {@link Entry#getAttributeValue(AttributeType, AttributeValueDecoder)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeValuesRFC3672SubtreeSpecification()
      throws Exception {
    // Define a dummy attribute type, in case there is not one already
    // in the core schema.
    String string = "( 2.5.18.6 NAME 'subtreeSpecification' "
        + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.45 )";

    AttributeType type = AttributeTypeSyntax.decodeAttributeType(
        new ASN1OctetString(string), DirectoryServer.getSchema());

    // Test values.
    String[] values = new String[] { "{ }",
        "{ base \"dc=example, dc=com\", minimum 1, maximum 2 }",
        "{ base \"dc=example, dc=com\", maximum 1 }",
        "{ base \"dc=example, dc=com\", maximum 2 }" };

    // Relative to the root DN.
    DN rootDN = new DN();

    SubtreeSpecificationSet expected = new SubtreeSpecificationSet();
    for (String value : values) {
      expected.add(RFC3672SubtreeSpecification.valueOf(rootDN, value));
    }

    Entry entry = createTestEntry(type, values);
    SubtreeSpecificationSet result = new SubtreeSpecificationSet();
    entry.getAttributeValues(type, RFC3672SubtreeSpecificationSyntax
        .createAttributeValueDecoder(rootDN), result);

    assertEquals(expected, result);
  }
}
