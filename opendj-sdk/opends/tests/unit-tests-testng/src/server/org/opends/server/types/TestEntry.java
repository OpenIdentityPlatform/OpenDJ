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
package org.opends.server.types;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.util.StaticUtils;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.SubtreeSpecificationSet;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RFC3672SubtreeSpecification;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.schema.RFC3672SubtreeSpecificationSyntax;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

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
    AttributeBuilder builder = new AttributeBuilder(type);
    for (String value : values) {
      builder.add(value);
    }
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    attributes.add(builder.toAttribute());
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
        ByteString.valueOf(string),
        DirectoryServer.getSchema(), false);

    // Test values.
    String[] values = new String[] { "{ }",
        "{ base \"dc=example, dc=com\", minimum 1, maximum 2 }",
        "{ base \"dc=example, dc=com\", maximum 1 }",
        "{ base \"dc=example, dc=com\", maximum 2 }" };

    // Relative to the root DN.
    DN rootDN = DN.nullDN();

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



  /**
   * Tests the {@code hasAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertTrue(e.hasAttribute(ocType));
    assertTrue(e.hasAttribute(cnType));
    assertTrue(e.hasAttribute(nameType));
    assertFalse(e.hasAttribute(nameType, false));
    assertFalse(e.hasAttribute(uidType));
    assertTrue(e.hasAttribute(mnType));

    LinkedHashSet<String> options = null;
    assertTrue(e.hasAttribute(ocType, options));
    assertTrue(e.hasAttribute(cnType, options));
    assertTrue(e.hasAttribute(nameType, options));
    assertFalse(e.hasAttribute(nameType, options, false));
    assertFalse(e.hasAttribute(uidType, options));
    assertTrue(e.hasAttribute(mnType, options));

    options = new LinkedHashSet<String>();
    assertTrue(e.hasAttribute(ocType, options));
    assertTrue(e.hasAttribute(cnType, options));
    assertTrue(e.hasAttribute(nameType, options));
    assertFalse(e.hasAttribute(nameType, options, false));
    assertFalse(e.hasAttribute(uidType, options));
    assertTrue(e.hasAttribute(mnType, options));

    options.add("lang-en-US");
    assertFalse(e.hasAttribute(ocType, options));
    assertTrue(e.hasAttribute(cnType, options));
    assertTrue(e.hasAttribute(nameType, options));
    assertFalse(e.hasAttribute(nameType, options, false));
    assertFalse(e.hasAttribute(uidType, options));
    assertFalse(e.hasAttribute(mnType, options));

    options.add("lang-en-GB");
    assertFalse(e.hasAttribute(ocType, options));
    assertFalse(e.hasAttribute(cnType, options));
    assertFalse(e.hasAttribute(nameType, options));
    assertFalse(e.hasAttribute(nameType, options, false));
    assertFalse(e.hasAttribute(uidType, options));
    assertFalse(e.hasAttribute(mnType, options));

    options.clear();
    options.add("lang-en-GB");
    assertFalse(e.hasAttribute(ocType, options));
    assertFalse(e.hasAttribute(cnType, options));
    assertFalse(e.hasAttribute(nameType, options));
    assertFalse(e.hasAttribute(nameType, options, false));
    assertFalse(e.hasAttribute(uidType, options));
    assertFalse(e.hasAttribute(mnType, options));
  }



  /**
   * Tests the {@code hasUserAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasUserAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertFalse(e.hasUserAttribute(ocType));
    assertTrue(e.hasUserAttribute(cnType));
    assertTrue(e.hasUserAttribute(nameType));
    assertFalse(e.hasUserAttribute(uidType));
    assertFalse(e.hasUserAttribute(mnType));
  }



  /**
   * Tests the {@code hasOperationalAttribute} method variants to ensure that
   * they work properly for both attributes included directly, as well as
   * attributes included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasOperationalAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertFalse(e.hasOperationalAttribute(ocType));
    assertFalse(e.hasOperationalAttribute(cnType));
    assertFalse(e.hasOperationalAttribute(nameType));
    assertFalse(e.hasOperationalAttribute(uidType));
    assertTrue(e.hasOperationalAttribute(mnType));
  }



  /**
   * Tests the {@code getAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    List<Attribute> attrs = e.getAttribute(ocType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute(cnType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute(nameType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getAttribute(nameType, false);
    assertNull(attrs);

    attrs = e.getAttribute(uidType);
    assertNull(attrs);

    attrs = e.getAttribute(mnType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    attrs = e.getAttribute("objectclass");
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute("cn");
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute("uid");
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname");
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    LinkedHashSet<String> options = null;
    attrs = e.getAttribute(ocType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getAttribute(nameType, false, options);
    assertNull(attrs);

    attrs = e.getAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getAttribute(mnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    attrs = e.getAttribute("objectclass", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute("cn", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute("uid", options);
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    options = new LinkedHashSet<String>();
    attrs = e.getAttribute(ocType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getAttribute(nameType, false, options);
    assertNull(attrs);

    attrs = e.getAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getAttribute(mnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    attrs = e.getAttribute("objectclass", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute("cn", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getAttribute("uid", options);
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    options.add("lang-en-US");
    attrs = e.getAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 3);

    attrs = e.getAttribute(nameType, false, options);
    assertNull(attrs);

    attrs = e.getAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getAttribute(mnType, options);
    assertNull(attrs);


    attrs = e.getAttribute("objectclass", options);
    assertNull(attrs);

    attrs = e.getAttribute("cn", options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getAttribute("uid", options);
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname", options);
    assertNull(attrs);


    options.add("lang-en-GB");
    attrs = e.getAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getAttribute(mnType, options);
    assertNull(attrs);


    attrs = e.getAttribute("objectclass", options);
    assertNull(attrs);

    attrs = e.getAttribute("cn", options);
    assertNull(attrs);

    attrs = e.getAttribute("uid", options);
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname", options);
    assertNull(attrs);


    options.clear();
    options.add("lang-en-GB");
    attrs = e.getAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getAttribute(mnType, options);
    assertNull(attrs);


    attrs = e.getAttribute("objectclass", options);
    assertNull(attrs);

    attrs = e.getAttribute("cn", options);
    assertNull(attrs);

    attrs = e.getAttribute("uid", options);
    assertNull(attrs);

    attrs = e.getAttribute("modifiersname", options);
    assertNull(attrs);
  }



  /**
   * Tests the {@code getUserAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetUserAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    List<Attribute> attrs = e.getUserAttribute(ocType);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getUserAttribute(nameType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getUserAttribute(uidType);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType);
    assertNull(attrs);


    LinkedHashSet<String> options = null;
    attrs = e.getUserAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getUserAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getUserAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType, options);
    assertNull(attrs);


    options = new LinkedHashSet<String>();
    attrs = e.getUserAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 2);

    attrs = e.getUserAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 6);

    attrs = e.getUserAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType, options);
    assertNull(attrs);


    options.add("lang-en-US");
    attrs = e.getUserAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    attrs = e.getUserAttribute(nameType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 3);

    attrs = e.getUserAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType, options);
    assertNull(attrs);


    options.add("lang-en-GB");
    attrs = e.getUserAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType, options);
    assertNull(attrs);


    options.clear();
    options.add("lang-en-GB");
    attrs = e.getUserAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getUserAttribute(mnType, options);
    assertNull(attrs);
  }



  /**
   * Tests the {@code getOperationalAttribute} method variants to ensure that
   * they work properly for both attributes included directly, as well as
   * attributes included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetOperationalAttribute()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=Test User,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "cn;lang-en-US: Test User",
         "givenName: Test",
         "givenName;lang-en-US: Test",
         "sn: User",
         "sn;lang-en-US: User",
         "creatorsName: cn=Directory Manager",
         "createTimestamp: 20070101000000Z",
         "modifiersName: cn=Directory Manager",
         "modifyTimestamp: 20070101000001Z");

    assertTrue(e.conformsToSchema(null, false, false, false,
                                  new MessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    List<Attribute> attrs = e.getOperationalAttribute(ocType);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    LinkedHashSet<String> options = null;
    attrs = e.getOperationalAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    options = new LinkedHashSet<String>();
    attrs = e.getOperationalAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType, options);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);


    options.add("lang-en-US");
    attrs = e.getOperationalAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType, options);
    assertNull(attrs);


    options.add("lang-en-GB");
    attrs = e.getOperationalAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType, options);
    assertNull(attrs);


    options.clear();
    options.add("lang-en-GB");
    attrs = e.getOperationalAttribute(ocType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(cnType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(nameType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(uidType, options);
    assertNull(attrs);

    attrs = e.getOperationalAttribute(mnType, options);
    assertNull(attrs);
  }
}

