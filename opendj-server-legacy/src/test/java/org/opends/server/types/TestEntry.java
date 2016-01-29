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
 *      Portions Copyright 2011-2016 ForgeRock AS
 */
package org.opends.server.types;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the {@link Entry} class.
 * <p>
 * At the moment this test suite only tests the parseAttribute method.
 */
@SuppressWarnings("javadoc")
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
    String[] values = new String[] { value };
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
      entryDN = DN.valueOf("dc=example, dc=com");
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }

    // Get default object classes.
    ObjectClass top = DirectoryServer.getObjectClass("top");
    if (top == null) {
      throw new RuntimeException("Unable to resolve object class top");
    }

    ObjectClass extensible = DirectoryServer.getObjectClass("extensibleobject");
    if (extensible == null) {
      throw new RuntimeException(
          "Unable to resolve object class extensibleObject");
    }

    HashMap<ObjectClass, String> objectClasses = new HashMap<>();
    objectClasses.put(top, top.getNameOrOID());
    objectClasses.put(extensible, extensible.getNameOrOID());

    // Construct the empty entry.
    Entry testEntry = new Entry(entryDN, objectClasses, null, null);

    // Now add the attribute.
    Attribute attr = Attributes.create(type.getNameOrOID(), values);
    testEntry.putAttribute(type, newArrayList(attr));
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
   * Test the {@link Entry#parseAttribute(String)} method.
   */
  @Test
  public void testParseAttributeNotFound() throws Exception {
    AttributeType type1 = DirectoryServer.getAttributeType("description");
    AttributeType type2 = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type1, "hello world");

    assertEquals(null, entry.parseAttribute(type2.getNameOrOID()).asString());
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   */
  @Test
  public void testParseAttributeBooleanTrue() throws Exception {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "true");

    assertEquals(entry.parseAttribute(type.getNameOrOID()).asBoolean(), Boolean.TRUE);
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   */
  @Test
  public void testParseAttributeBooleanFalse() throws Exception
  {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "false");

    assertEquals(entry.parseAttribute(type.getNameOrOID()).asBoolean(), Boolean.FALSE);
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   */
  @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testParseAttributeBooleanBad() throws Exception
  {
    AttributeType type = DirectoryServer.getAttributeType("inheritable");

    Entry entry = createTestEntry(type, "bad-value");
    entry.parseAttribute(type.getNameOrOID()).asBoolean();
    throw new RuntimeException(
         "An illegal boolean value did not throw an exception");
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testParseAttributesInteger() throws Exception
  {
    AttributeType type = DirectoryServer.getAttributeType("supportedldapversion");
    String[] values = new String[] { "-4", "-2", "0", "1", "3" };

    HashSet<Integer> expected = new HashSet<>();
    for (String value : values) {
      expected.add(Integer.valueOf(value));
    }

    Entry entry = createTestEntry(type, values);
    Set<Integer> result =
        entry.parseAttribute("supportedldapversion").asSetOfInteger();
    Assertions.assertThat(result).isEqualTo(expected);
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testParseAttributeIntegerBad() throws Exception
  {
    AttributeType type = DirectoryServer.getAttributeType("supportedldapversion");
    String[] values = new String[] { "-4", "-2", "xxx", "1", "3" };

    Entry entry = createTestEntry(type, values);
    entry.parseAttribute("supportedldapversion").asSetOfInteger();
  }

  /**
   * Test the {@link Entry#parseAttribute(String)} method.
   */
  @Test
  public void testParseAttributesSubtreeSpecification()
      throws Exception {
    // Define a dummy attribute type, in case there is not one already
    // in the core schema.
    String string = "( 2.5.18.6 NAME 'subtreeSpecification' "
        + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.45 )";

    AttributeType type = DirectoryServer.getSchema().parseAttributeType(string);

    // Test values.
    String[] values = new String[] { "{ }",
        "{ base \"dc=example, dc=com\", minimum 1, maximum 2 }",
        "{ base \"dc=example, dc=com\", maximum 1 }",
        "{ base \"dc=example, dc=com\", maximum 2 }" };

    // Relative to the root DN.
    DN rootDN = DN.rootDN();

    Set<SubtreeSpecification> expected = new HashSet<>();
    for (String value : values) {
      expected.add(SubtreeSpecification.valueOf(rootDN, value));
    }

    Entry entry = createTestEntry(type, values);
    Set<SubtreeSpecification> result = new HashSet<>();
    List<Attribute> attributes = entry.getAttribute(type, true);
    for (Attribute a : attributes)
    {
      for (ByteString value : a)
      {
        result.add(SubtreeSpecification.valueOf(rootDN, value.toString()));
      }
    }
    assertEquals(expected, result);
  }



  /**
   * Tests the {@code hasAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
                                  new LocalizableMessageBuilder()));

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

    options = new LinkedHashSet<>();
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
  @Test
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
                                  new LocalizableMessageBuilder()));

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
  @Test
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
                                  new LocalizableMessageBuilder()));

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
  @Test
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
                                  new LocalizableMessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertThat(e.getAttribute(ocType)).hasSize(1);
    assertThat(e.getAttribute(cnType)).hasSize(2);
    assertThat(e.getAttribute(nameType)).hasSize(6);

    assertThat(e.getAttribute(nameType, false)).isEmpty();
    assertThat(e.getAttribute(uidType)).isEmpty();
    assertThat(e.getAttribute(mnType)).hasSize(1);
    assertThat(e.getAttribute("objectclass")).hasSize(1);
    assertThat(e.getAttribute("cn")).hasSize(2);
    assertThat(e.getAttribute("uid")).isEmpty();
    assertThat(e.getAttribute("modifiersname")).hasSize(1);

    LinkedHashSet<String> options = null;
    assertThat(e.getAttribute(ocType, options)).hasSize(1);
    assertThat(e.getAttribute(cnType, options)).hasSize(2);
    assertThat(e.getAttribute(nameType, options)).hasSize(6);

    assertThat(e.getAttribute(uidType, options)).isEmpty();
    assertThat(e.getAttribute(mnType, options)).hasSize(1);

    options = new LinkedHashSet<>();
    assertThat(e.getAttribute(ocType, options)).hasSize(1);
    assertThat(e.getAttribute(cnType, options)).hasSize(2);
    assertThat(e.getAttribute(nameType, options)).hasSize(6);

    assertThat(e.getAttribute(uidType, options)).isEmpty();
    assertThat(e.getAttribute(mnType, options)).hasSize(1);

    options.add("lang-en-US");
    assertThat(e.getAttribute(ocType, options)).isEmpty();
    assertThat(e.getAttribute(cnType, options)).hasSize(1);
    assertThat(e.getAttribute(nameType, options)).hasSize(3);

    assertThat(e.getAttribute(uidType, options)).isEmpty();
    assertThat(e.getAttribute(mnType, options)).isEmpty();

    options.add("lang-en-GB");
    assertThat(e.getAttribute(ocType, options)).isEmpty();
    assertThat(e.getAttribute(cnType, options)).isEmpty();
    assertThat(e.getAttribute(nameType, options)).isEmpty();
    assertThat(e.getAttribute(uidType, options)).isEmpty();
    assertThat(e.getAttribute(mnType, options)).isEmpty();

    options.clear();
    options.add("lang-en-GB");
    assertThat(e.getAttribute(ocType, options)).isEmpty();
    assertThat(e.getAttribute(cnType, options)).isEmpty();
    assertThat(e.getAttribute(nameType, options)).isEmpty();
    assertThat(e.getAttribute(uidType, options)).isEmpty();
    assertThat(e.getAttribute(mnType, options)).isEmpty();
  }



  /**
   * Tests the {@code getUserAttribute} method variants to ensure that they work
   * properly for both attributes included directly, as well as attributes
   * included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
                                  new LocalizableMessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertThat(e.getUserAttribute(ocType)).isEmpty();
    assertThat(e.getUserAttribute(cnType)).hasSize(2);
    assertThat(e.getUserAttribute(nameType)).hasSize(6);
    assertThat(e.getUserAttribute(uidType)).isEmpty();
    assertThat(e.getUserAttribute(mnType)).isEmpty();
  }



  /**
   * Tests the {@code getOperationalAttribute} method variants to ensure that
   * they work properly for both attributes included directly, as well as
   * attributes included as subtypes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
                                  new LocalizableMessageBuilder()));

    AttributeType ocType   = DirectoryServer.getAttributeType("objectclass");
    AttributeType cnType   = DirectoryServer.getAttributeType("cn");
    AttributeType nameType = DirectoryServer.getAttributeType("name");
    AttributeType uidType  = DirectoryServer.getAttributeType("uid");
    AttributeType mnType   = DirectoryServer.getAttributeType("modifiersname");

    assertThat(e.getOperationalAttribute(ocType)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType)).hasSize(1);

    LinkedHashSet<String> options = null;
    assertThat(e.getOperationalAttribute(ocType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType, options)).hasSize(1);

    options = new LinkedHashSet<>();
    assertThat(e.getOperationalAttribute(ocType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType, options)).hasSize(1);

    options.add("lang-en-US");
    assertThat(e.getOperationalAttribute(ocType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType, options)).isEmpty();

    options.add("lang-en-GB");
    assertThat(e.getOperationalAttribute(ocType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType, options)).isEmpty();

    options.clear();
    options.add("lang-en-GB");
    assertThat(e.getOperationalAttribute(ocType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(cnType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(nameType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(uidType, options)).isEmpty();
    assertThat(e.getOperationalAttribute(mnType, options)).isEmpty();
  }
}
