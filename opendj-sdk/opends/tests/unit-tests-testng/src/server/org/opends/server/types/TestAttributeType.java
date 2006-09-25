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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.ServerConstants;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.types.AttributeType} class.
 */
public final class TestAttributeType extends TypesTestCase {
  /**
   * Internal class to simplify construction of attribute types.
   */
  private static final class AttributeTypeBuilder {
    // The primary name to use for this attribute type.
    private String primaryName;

    // The set of names for this attribute type.
    private List<String> typeNames;

    // The OID that may be used to reference this attribute type.
    private String oid;

    // The description for this attribute type.
    private String description;

    // The superior attribute type from which this attribute type
    // inherits.
    private AttributeType superiorType;

    // The syntax for this attribute type.
    private AttributeSyntax syntax;

    // The approximate matching rule for this attribute type.
    private ApproximateMatchingRule approximateMatchingRule;

    // The equality matching rule for this attribute type.
    private EqualityMatchingRule equalityMatchingRule;

    // The ordering matching rule for this attribute type.
    private OrderingMatchingRule orderingMatchingRule;

    // The substring matching rule for this attribute type.
    private SubstringMatchingRule substringMatchingRule;

    // The attribute usage for this attribute type.
    private AttributeUsage attributeUsage;

    // Indicates whether this attribute type is declared "collective".
    private boolean isCollective;

    // Indicates whether this attribute type is declared
    // "no-user-modification".
    private boolean isNoUserModification;

    // Indicates whether this attribute type is declared "obsolete".
    private boolean isObsolete;

    // Indicates whether this attribute type is declared
    // "single-value".
    private boolean isSingleValue;

    // The set of additional name-value pairs associated with this
    // attribute type definition.
    private Map<String, List<String>> extraProperties;

    // Reset the builder to its initial state.
    private void reset() {
      this.primaryName = null;
      this.typeNames = null;
      this.oid = null;
      this.description = null;
      this.superiorType = null;
      this.syntax = null;
      this.approximateMatchingRule = null;
      this.equalityMatchingRule = null;
      this.orderingMatchingRule = null;
      this.substringMatchingRule = null;
      this.attributeUsage = AttributeUsage.USER_APPLICATIONS;
      this.isCollective = false;
      this.isNoUserModification = false;
      this.isObsolete = false;
      this.isSingleValue = false;
      this.extraProperties = null;
    }

    /**
     * Create a new attribute type builder.
     */
    public AttributeTypeBuilder() {
      reset();
    }

    /**
     * Create a new attribute type builder.
     * 
     * @param primaryName
     *          The attribute type primary name.
     * @param oid
     *          The attribute type OID.
     */
    public AttributeTypeBuilder(String primaryName, String oid) {
      reset();

      this.primaryName = primaryName;
      this.oid = oid;
    }

    /**
     * Construct an attribute type based on the properties of the
     * builder.
     * 
     * @return The new attribute type.
     */
    public AttributeType getAttributeType() {
      if (oid == null) {
        throw new IllegalStateException("Null OID.");
      }

      AttributeType type = new AttributeType(primaryName, typeNames,
          oid, description, superiorType, syntax,
          approximateMatchingRule, equalityMatchingRule,
          orderingMatchingRule, substringMatchingRule,
          attributeUsage, isCollective, isNoUserModification,
          isObsolete, isSingleValue, extraProperties);

      // Reset the internal state.
      reset();

      return type;
    }

    /**
     * Set the approximateMatchingRule.
     * 
     * @param approximateMatchingRule
     *          The approximateMatchingRule.
     */
    public void setApproximateMatchingRule(
        ApproximateMatchingRule approximateMatchingRule) {
      this.approximateMatchingRule = approximateMatchingRule;
    }

    /**
     * Set the attributeUsage.
     * 
     * @param attributeUsage
     *          The attributeUsage.
     */
    public void setAttributeUsage(AttributeUsage attributeUsage) {
      this.attributeUsage = attributeUsage;
    }

    /**
     * Set the description.
     * 
     * @param description
     *          The description.
     */
    public void setDescription(String description) {
      this.description = description;
    }

    /**
     * Set the equalityMatchingRule.
     * 
     * @param equalityMatchingRule
     *          The equalityMatchingRule.
     */
    public void setEqualityMatchingRule(
        EqualityMatchingRule equalityMatchingRule) {
      this.equalityMatchingRule = equalityMatchingRule;
    }

    /**
     * Add extra property value(s).
     * 
     * @param name
     *          The name of the extra property.
     * @param values
     *          The value(s) of the extra property.
     */
    public void addExtraProperty(String name, String... values) {
      if (name == null) {
        throw new NullPointerException("Null extra property name");
      }

      if (values == null) {
        throw new NullPointerException("Null extra property values");
      }

      if (extraProperties == null) {
        extraProperties = new HashMap<String, List<String>>();
      }

      List<String> l = extraProperties.get(name);
      if (l == null) {
        l = new ArrayList<String>();
        extraProperties.put(name, l);
      }
      l.addAll(Arrays.asList(values));
    }

    /**
     * Set the isCollective.
     * 
     * @param isCollective
     *          The isCollective.
     */
    public void setCollective(boolean isCollective) {
      this.isCollective = isCollective;
    }

    /**
     * Set the isNoUserModification.
     * 
     * @param isNoUserModification
     *          The isNoUserModification.
     */
    public void setNoUserModification(boolean isNoUserModification) {
      this.isNoUserModification = isNoUserModification;
    }

    /**
     * Set the isObsolete.
     * 
     * @param isObsolete
     *          The isObsolete.
     */
    public void setObsolete(boolean isObsolete) {
      this.isObsolete = isObsolete;
    }

    /**
     * Set the isSingleValue.
     * 
     * @param isSingleValue
     *          The isSingleValue.
     */
    public void setSingleValue(boolean isSingleValue) {
      this.isSingleValue = isSingleValue;
    }

    /**
     * Set the oid.
     * 
     * @param oid
     *          The oid.
     */
    public void setOid(String oid) {
      if (oid == null) {
        throw new NullPointerException("Null OID");
      }

      this.oid = oid;
    }

    /**
     * Set the orderingMatchingRule.
     * 
     * @param orderingMatchingRule
     *          The orderingMatchingRule.
     */
    public void setOrderingMatchingRule(
        OrderingMatchingRule orderingMatchingRule) {
      this.orderingMatchingRule = orderingMatchingRule;
    }

    /**
     * Set the primaryName.
     * 
     * @param primaryName
     *          The primaryName.
     */
    public void setPrimaryName(String primaryName) {
      this.primaryName = primaryName;
    }

    /**
     * Set the substringMatchingRule.
     * 
     * @param substringMatchingRule
     *          The substringMatchingRule.
     */
    public void setSubstringMatchingRule(
        SubstringMatchingRule substringMatchingRule) {
      this.substringMatchingRule = substringMatchingRule;
    }

    /**
     * Set the superiorType.
     * 
     * @param superiorType
     *          The superiorType.
     */
    public void setSuperiorType(AttributeType superiorType) {
      this.superiorType = superiorType;
    }

    /**
     * Set the syntax.
     * 
     * @param syntax
     *          The syntax.
     */
    public void setSyntax(AttributeSyntax syntax) {
      this.syntax = syntax;
    }

    /**
     * Add attribute type name(s).
     * 
     * @param names
     *          The attribute type name(s) to add.
     */
    public void addTypeNames(String... names) {
      if (names == null) {
        throw new NullPointerException("Null names");
      }

      if (typeNames == null) {
        typeNames = new LinkedList<String>();
      }

      typeNames.addAll(Arrays.asList(names));
    }
  }

  /**
   * Once-only initialization.
   * 
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll
    // start the server.
    TestCaseUtils.startServer();
  }

  /**
   * Check that the simple constructor throws an NPE when mandatory
   * parameters are not specified.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testSimpleConstructorNPE() throws Exception {
    new AttributeType(null, null, null, null, null, null, null,
        false, false, false, false);
  }

  /**
   * Check that the complex constructor throws an NPE when mandatory
   * parameters are not specified.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testComplexConstructorNPE() throws Exception {
    new AttributeType(null, null, null, null, null, null, null, null,
        null, null, null, false, false, false, false, null);
  }

  /**
   * Check that the complex constructor does not throw an exception
   * when all optional parameters are not specified.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testComplexConstructorDefault() throws Exception {
    AttributeType type = new AttributeType(null, null, "1.2.3", null,
        null, null, null, null, null, null, null, false, false,
        false, false, null);

    Assert.assertNull(type.getPrimaryName());
  }

  /**
   * Check that the primary name is added to the set of names.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorPrimaryName() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertTrue(type.hasName("testtype"));
    Assert.assertFalse(type.hasName("xxx"));
  }

  /**
   * Check that the type names are accessible.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorTypeNames() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");

    builder.addTypeNames("testNameAlias", "anotherNameAlias");
    AttributeType type = builder.getAttributeType();

    Assert.assertTrue(type.hasName("testtype"));
    Assert.assertTrue(type.hasName("testnamealias"));
    Assert.assertTrue(type.hasName("anothernamealias"));
  }

  /**
   * Check constructor sets the default usage correctly.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefaultUsage() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertEquals(type.getUsage(),
        AttributeUsage.USER_APPLICATIONS);
  }

  /**
   * Check constructor sets the default syntax correctly.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefaultSyntax() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertEquals(type.getSyntax(), DirectoryServer
        .getDefaultAttributeSyntax());
  }

  /**
   * Check constructor sets the syntax correctly.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorSyntax() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    builder.setSyntax(DirectoryServer.getDefaultIntegerSyntax());
    AttributeType type = builder.getAttributeType();

    Assert.assertEquals(type.getSyntax(), DirectoryServer
        .getDefaultIntegerSyntax());
  }

  /**
   * Check constructor inherits the syntax from the parent type when
   * required.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testConstructorSyntax")
  public void testConstructorInheritsSyntax() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "parentType", "1.2.3");
    builder.setSyntax(DirectoryServer.getDefaultIntegerSyntax());
    AttributeType parent = builder.getAttributeType();

    builder.setPrimaryName("childType");
    builder.setOid("4.5.6");
    builder.setSuperiorType(parent);
    AttributeType child = builder.getAttributeType();

    Assert.assertEquals(parent.getSyntax(), child.getSyntax());
  }

  /**
   * Check constructor sets the default matching rules correctly.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefaultMatchingRules() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    AttributeSyntax syntax = DirectoryServer
        .getDefaultAttributeSyntax();
    Assert.assertEquals(type.getApproximateMatchingRule(), syntax
        .getApproximateMatchingRule());
    Assert.assertEquals(type.getEqualityMatchingRule(), syntax
        .getEqualityMatchingRule());
    Assert.assertEquals(type.getOrderingMatchingRule(), syntax
        .getOrderingMatchingRule());
    Assert.assertEquals(type.getSubstringMatchingRule(), syntax
        .getSubstringMatchingRule());
  }

  /**
   * Check constructor sets the matching rules correctly.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorMatchingRules() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeSyntax syntax = DirectoryServer.getDefaultStringSyntax();
    builder.setApproximateMatchingRule(syntax
        .getApproximateMatchingRule());
    builder.setEqualityMatchingRule(syntax.getEqualityMatchingRule());
    builder.setOrderingMatchingRule(syntax.getOrderingMatchingRule());
    builder.setSubstringMatchingRule(syntax
        .getSubstringMatchingRule());
    AttributeType type = builder.getAttributeType();

    Assert.assertEquals(type.getApproximateMatchingRule(), syntax
        .getApproximateMatchingRule());
    Assert.assertEquals(type.getEqualityMatchingRule(), syntax
        .getEqualityMatchingRule());
    Assert.assertEquals(type.getOrderingMatchingRule(), syntax
        .getOrderingMatchingRule());
    Assert.assertEquals(type.getSubstringMatchingRule(), syntax
        .getSubstringMatchingRule());
  }

  /**
   * Check constructor inherits the matching rules from the parent
   * type when required.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testConstructorMatchingRules")
  public void testConstructorInheritsMatchingRules() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "parentType", "1.2.3");
    AttributeSyntax syntax = DirectoryServer.getDefaultStringSyntax();
    builder.setApproximateMatchingRule(syntax
        .getApproximateMatchingRule());
    builder.setEqualityMatchingRule(syntax.getEqualityMatchingRule());
    builder.setOrderingMatchingRule(syntax.getOrderingMatchingRule());
    builder.setSubstringMatchingRule(syntax
        .getSubstringMatchingRule());

    AttributeType parent = builder.getAttributeType();

    builder.setPrimaryName("childType");
    builder.setOid("4.5.6");
    builder.setSuperiorType(parent);
    AttributeType child = builder.getAttributeType();

    Assert.assertEquals(parent.getApproximateMatchingRule(), child
        .getApproximateMatchingRule());
    Assert.assertEquals(parent.getEqualityMatchingRule(), child
        .getEqualityMatchingRule());
    Assert.assertEquals(parent.getOrderingMatchingRule(), child
        .getOrderingMatchingRule());
    Assert.assertEquals(parent.getSubstringMatchingRule(), child
        .getSubstringMatchingRule());
  }

  /**
   * Create test data for testing the
   * {@link AttributeType#isObjectClassType()} method.
   * 
   * @return Returns the array of test data.
   */
  @DataProvider(name = "isObjectClassTypeTestData")
  public Object[][] createIsObjectClassTypeTestData() {
    return new Object[][] { { "testType", false },
        { "objectclass", true }, { "objectClass", true },
        { "OBJECTCLASS", true } };
  }

  /**
   * Check that the objectClass attribute type is correctly
   * identified.
   * 
   * @param name
   *          The primary name.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isObjectClassTypeTestData")
  public void testIsObjectClassType(String name, boolean result)
      throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(name,
        "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertEquals(type.isObjectClassType(), result);
  }

  /**
   * Create test data for testing the
   * {@link AttributeType#equals(Object)} method.
   * 
   * @return Returns the array of test data.
   */
  @DataProvider(name = "equalsTestData")
  public Object[][] createEqualsTestData() {
    return new Object[][] {
        { "testType", "1.2.3", "testType", "1.2.3", true },
        { "testType", "1.2.3", "xxx", "1.2.3", true },
        { "testType", "1.2.3", "testType", "1.2.4", false },
        { "testType", "1.2.3", "xxx", "1.2.4", false } };
  }

  /**
   * Check that the equals operator works as expected.
   * 
   * @param name1
   *          The first primary name.
   * @param oid1
   *          The first oid.
   * @param name2
   *          The second primary name.
   * @param oid2
   *          The second oid.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "equalsTestData")
  public void testEquals(String name1, String oid1, String name2,
      String oid2, boolean result) throws Exception {
    AttributeTypeBuilder builder1 = new AttributeTypeBuilder(name1,
        oid1);
    AttributeType type1 = builder1.getAttributeType();

    AttributeTypeBuilder builder2 = new AttributeTypeBuilder(name2,
        oid2);
    AttributeType type2 = builder2.getAttributeType();

    Assert.assertEquals(type1.equals(type2), result);
    Assert.assertEquals(type2.equals(type1), result);
  }

  /**
   * Check that the hasCode method operator works as expected.
   * 
   * @param name1
   *          The first primary name.
   * @param oid1
   *          The first oid.
   * @param name2
   *          The second primary name.
   * @param oid2
   *          The second oid.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "equalsTestData")
  public void testHashCode(String name1, String oid1, String name2,
      String oid2, boolean result) throws Exception {
    AttributeTypeBuilder builder1 = new AttributeTypeBuilder(name1,
        oid1);
    AttributeType type1 = builder1.getAttributeType();

    AttributeTypeBuilder builder2 = new AttributeTypeBuilder(name2,
        oid2);
    AttributeType type2 = builder2.getAttributeType();

    Assert.assertEquals(type1.hashCode() == type2.hashCode(), result);
  }

  /**
   * Create test data for testing the
   * {@link AttributeType#generateHashCode(AttributeValue)} method.
   * 
   * @return Returns the array of test data.
   */
  @DataProvider(name = "generateHashCodeTestData")
  public Object[][] createGenerateHashCodeTestData() {
    return new Object[][] { { "one", "one", true },
        { "one", "ONE", true }, { "one", "  oNe  ", true },
        { "one two", " one  two  ", true },
        { "one two", "onetwo", false }, { "one", "two", false } };
  }

  /**
   * Check that the
   * {@link AttributeType#generateHashCode(AttributeValue)} method
   * works as expected.
   * 
   * @param value1
   *          The first test value.
   * @param value2
   *          The second test value.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "generateHashCodeTestData")
  public void testGenerateHashCodeTestData(String value1,
      String value2, boolean result) throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    builder.setSyntax(DirectoryServer.getDefaultStringSyntax());
    AttributeType type = builder.getAttributeType();

    AttributeValue av1 = new AttributeValue(type, value1);
    AttributeValue av2 = new AttributeValue(type, value2);

    int h1 = type.generateHashCode(av1);
    int h2 = type.generateHashCode(av2);

    Assert.assertEquals(h1 == h2, result);
  }

  /**
   * Check that the {@link AttributeType#normalize(ByteString)} method
   * works as expected.
   * 
   * @param value1
   *          The first test value.
   * @param value2
   *          The second test value.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "generateHashCodeTestData")
  public void testNormalize(String value1, String value2,
      boolean result) throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    builder.setSyntax(DirectoryServer.getDefaultStringSyntax());
    AttributeType type = builder.getAttributeType();

    ByteString b1 = new ASN1OctetString(value1);
    ByteString b2 = new ASN1OctetString(value2);

    ByteString r1 = type.normalize(b1);
    ByteString r2 = type.normalize(b2);

    Assert.assertEquals(r1.equals(r2), result);
  }

  /**
   * Check that the {@link AttributeType#getDescription()} method
   * returns <code>null</code> when there is no description.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetDescriptionDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertNull(type.getDescription());
  }

  /**
   * Check that the {@link AttributeType#getDescription()} method
   * returns a description.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetDescription() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    builder.setDescription("hello");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getDescription(), "hello");
  }

  /**
   * Check that the {@link AttributeType#getExtraProperty(String)}
   * method returns <code>null</code> when there is no property.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetExtraPropertyDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertNull(type.getExtraProperty("test"));
  }

  /**
   * Check that the {@link AttributeType#getExtraProperty(String)}
   * method returns values.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetExtraProperty() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    String[] expectedValues = new String[] { "one", "two" };
    builder.addExtraProperty("test", expectedValues);
    AttributeType type = builder.getAttributeType();

    Assert.assertNotNull(type.getExtraProperty("test"));
    int i = 0;
    for (String value : type.getExtraProperty("test")) {
      Assert.assertEquals(value, expectedValues[i]);
      i++;
    }
  }

  /**
   * Check that the {@link AttributeType#getExtraPropertyNames()}
   * method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetExtraPropertyNames() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder("test",
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertFalse(type.getExtraPropertyNames().iterator()
        .hasNext());
  }

  /**
   * Check that the {@link AttributeType#getNameOrOID()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNameOrOIDReturnsOID() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getNameOrOID(), "1.2.3");
  }

  /**
   * Check that the {@link AttributeType#getNameOrOID()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNameOrOIDReturnsPrimaryName() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getNameOrOID(), "testType");
  }

  /**
   * Check that the {@link AttributeType#getNameOrOID()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNameOrOIDReturnsOtherName() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    builder.addTypeNames("anotherName");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getNameOrOID(), "anotherName");
  }

  /**
   * Check that the {@link AttributeType#getNormalizedNames()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNormalizedNames() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    builder.addTypeNames("anotherName", "yetAnotherName");
    AttributeType type = builder.getAttributeType();

    boolean gotTestType = false;
    boolean gotAnotherName = false;
    boolean gotYetAnotherName = false;

    for (String name : type.getNormalizedNames()) {
      if (name.equals("testtype")) {
        gotTestType = true;
      } else if (name.equals("anothername")) {
        gotAnotherName = true;
      } else if (name.equals("yetanothername")) {
        gotYetAnotherName = true;
      } else {
        Assert.fail("Got unexpected normalized name: " + name);
      }
    }

    Assert.assertTrue(gotTestType && gotAnotherName
        && gotYetAnotherName);
  }

  /**
   * Check that the {@link AttributeType#getUserDefinedNames()}
   * method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetUserDefinedNames() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    builder.addTypeNames("anotherName", "yetAnotherName");
    AttributeType type = builder.getAttributeType();

    boolean gotTestType = false;
    boolean gotAnotherName = false;
    boolean gotYetAnotherName = false;

    for (String name : type.getUserDefinedNames()) {
      if (name.equals("testType")) {
        gotTestType = true;
      } else if (name.equals("anotherName")) {
        gotAnotherName = true;
      } else if (name.equals("yetAnotherName")) {
        gotYetAnotherName = true;
      } else {
        Assert.fail("Got unexpected user defined name: " + name);
      }
    }

    Assert.assertTrue(gotTestType && gotAnotherName
        && gotYetAnotherName);
  }

  /**
   * Check that the {@link AttributeType#getNormalizedPrimaryName()}
   * method returns <code>null</code> when there is no primary name.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNormalizedPrimaryNameDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertNull(type.getNormalizedPrimaryName());
  }

  /**
   * Check that the {@link AttributeType#getNormalizedPrimaryName()}
   * method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNormalizedPrimaryName() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getNormalizedPrimaryName(), "testtype");
  }

  /**
   * Check that the {@link AttributeType#getOID()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOID() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getOID(), "1.2.3");
  }

  /**
   * Check that the {@link AttributeType#getPrimaryName()} method
   * returns <code>null</code> when there is no primary name.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetPrimaryNameDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertNull(type.getPrimaryName());
  }

  /**
   * Check that the {@link AttributeType#getPrimaryName()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetPrimaryName() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getPrimaryName(), "testType");
  }

  /**
   * Check that the {@link AttributeType#getSchemaFile()} method
   * returns <code>null</code> when there is no schema file.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetSchemaFileDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertNull(type.getSchemaFile());
  }

  /**
   * Check that the {@link AttributeType#getSchemaFile()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetSchemaFile() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    builder.addExtraProperty(
        ServerConstants.SCHEMA_PROPERTY_FILENAME, "/foo/bar");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.getSchemaFile(), "/foo/bar");
  }

  /**
   * Check that the {@link AttributeType#hasNameOrOID(String)} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testHasNameOrOID() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertTrue(type.hasNameOrOID("testtype"));
    Assert.assertTrue(type.hasNameOrOID("1.2.3"));
    Assert.assertFalse(type.hasNameOrOID("x.y.z"));
  }

  /**
   * Check that the {@link AttributeType#isCollective()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsCollective() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertFalse(type.isCollective());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setCollective(true);
    type = builder.getAttributeType();

    Assert.assertTrue(type.isCollective());
  }

  /**
   * Check that the {@link AttributeType#isNoUserModification()}
   * method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNoUserModification() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertFalse(type.isNoUserModification());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setNoUserModification(true);
    type = builder.getAttributeType();

    Assert.assertTrue(type.isNoUserModification());
  }

  /**
   * Check that the {@link AttributeType#isObsolete()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsObsolete() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertFalse(type.isObsolete());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setObsolete(true);
    type = builder.getAttributeType();

    Assert.assertTrue(type.isObsolete());
  }

  /**
   * Check that the {@link AttributeType#isSingleValue()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsSingleValue() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    AttributeType type = builder.getAttributeType();

    Assert.assertFalse(type.isSingleValue());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setSingleValue(true);
    type = builder.getAttributeType();

    Assert.assertTrue(type.isSingleValue());
  }

  /**
   * Create test data for testing the
   * {@link AttributeType#isOperational()} method.
   * 
   * @return Returns the array of test data.
   */
  @DataProvider(name = "isOperationalTestData")
  public Object[][] createIsOperationalTestData() {
    return new Object[][] { { null, false },
        { AttributeUsage.USER_APPLICATIONS, false },
        { AttributeUsage.DIRECTORY_OPERATION, true },
        { AttributeUsage.DISTRIBUTED_OPERATION, true },
        { AttributeUsage.DSA_OPERATION, true } };
  }

  /**
   * Check that the {@link AttributeType#isOperational()} method.
   * 
   * @param usage
   *          The attribute usage.
   * @param result
   *          Expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isOperationalTestData")
  public void testIsOperational(AttributeUsage usage, boolean result)
      throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    builder.setAttributeUsage(usage);
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.isOperational(), result);
  }

  /**
   * Check that the {@link AttributeType#toString()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToStringDefault() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(null,
        "1.2.3");
    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.toString(), "( 1.2.3 "
        + "EQUALITY caseIgnoreMatch "
        + "ORDERING caseIgnoreOrderingMatch "
        + "SUBSTR caseIgnoreSubstringsMatch "
        + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 "
        + "USAGE userApplications )");
  }

  /**
   * Check that the {@link AttributeType#toString()} method.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToString() throws Exception {
    AttributeTypeBuilder builder = new AttributeTypeBuilder(
        "testType", "1.2.3");
    builder.addTypeNames("anotherName");
    builder.setAttributeUsage(AttributeUsage.DIRECTORY_OPERATION);
    builder.setSingleValue(true);
    builder.setSyntax(DirectoryServer.getDefaultBooleanSyntax());
    builder.addExtraProperty(
        ServerConstants.SCHEMA_PROPERTY_FILENAME, "/foo/bar");

    AttributeType type = builder.getAttributeType();
    Assert.assertEquals(type.toString(), "( 1.2.3 "
        + "NAME ( 'anotherName' 'testType' ) "
        + "EQUALITY booleanMatch "
        + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.7 " + "SINGLE-VALUE "
        + "USAGE directoryOperation " + "X-SCHEMA-FILE '/foo/bar' )");
  }
}
