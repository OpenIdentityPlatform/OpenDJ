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
package org.opends.server.types;



import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.ServerConstants;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the
 * {@link org.opends.server.types.AttributeType} class.
 */
public final class TestAttributeType extends TestCommonSchemaElements {
  /**
   * Internal class to simplify construction of attribute types.
   */
  private static final class AttributeTypeBuilder extends
      SchemaDefinitionBuilder<AttributeType> {
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

    // Indicates whether this attribute type is declared
    // "single-value".
    private boolean isSingleValue;



    /**
     * {@inheritDoc}
     */
    protected void resetBuilder() {
      this.superiorType = null;
      this.syntax = null;
      this.approximateMatchingRule = null;
      this.equalityMatchingRule = null;
      this.orderingMatchingRule = null;
      this.substringMatchingRule = null;
      this.attributeUsage = AttributeUsage.USER_APPLICATIONS;
      this.isCollective = false;
      this.isNoUserModification = false;
      this.isSingleValue = false;
    }



    /**
     * Create a new attribute type builder.
     */
    public AttributeTypeBuilder() {
      super();
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
      super(primaryName, oid);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected AttributeType buildInstance(String primaryName,
        Collection<String> names, String oid, String description,
        boolean isObsolete, Map<String, List<String>> extraProperties) {

      StringBuilder definition = new StringBuilder();
      definition.append("( ");
      definition.append(oid);

      LinkedHashSet<String> nameSet = new LinkedHashSet<String>();
      if (primaryName != null)
      {
        nameSet.add(primaryName);
      }

      if (names != null)
      {
        for (String name : names)
        {
          nameSet.add(name);
        }
      }

      if (! nameSet.isEmpty())
      {
        if (nameSet.size() == 1)
        {
          definition.append(" NAME '");
          definition.append(nameSet.iterator().next());
          definition.append("'");
        }
        else
        {
          Iterator<String> iterator = nameSet.iterator();

          definition.append(" NAME ( '");
          definition.append(iterator.next());

          while (iterator.hasNext())
          {
            definition.append("' '");
            definition.append(iterator.next());
          }

          definition.append("' )");
        }
      }

      if (description != null)
      {
        definition.append(" DESC '");
        definition.append(description);
        definition.append("'");
      }

      if (isObsolete)
      {
        definition.append(" OBSOLETE");
      }

      if (superiorType != null)
      {
        definition.append(" SUP ");
        definition.append(superiorType.getNameOrOID());
      }

      if (equalityMatchingRule != null)
      {
        definition.append(" EQUALITY ");
        definition.append(equalityMatchingRule.getNameOrOID());
      }

      if (orderingMatchingRule != null)
      {
        definition.append(" ORDERING ");
        definition.append(orderingMatchingRule.getNameOrOID());
      }

      if (substringMatchingRule != null)
      {
        definition.append(" SUBSTR ");
        definition.append(substringMatchingRule.getNameOrOID());
      }

      if (syntax != null)
      {
        definition.append(" SYNTAX ");
        definition.append(syntax.getOID());
      }

      if (isSingleValue)
      {
        definition.append(" SINGLE-VALUE");
      }

      if (isCollective)
      {
        definition.append(" COLLECTIVE");
      }

      if (isNoUserModification)
      {
        definition.append(" NO-USER-MODIFICATIOn");
      }

      if (attributeUsage != null)
      {
        definition.append(" USAGE ");
        definition.append(attributeUsage.toString());
      }

      if (extraProperties != null)
      {
        for (String property : extraProperties.keySet())
        {
          List<String> values = extraProperties.get(property);
          if ((values == null) || values.isEmpty())
          {
            continue;
          }
          else if (values.size() == 1)
          {
            definition.append(" ");
            definition.append(property);
            definition.append(" '");
            definition.append(values.get(0));
            definition.append("'");
          }
          else
          {
            definition.append(" ");
            definition.append(property);
            definition.append(" (");
            for (String value : values)
            {
              definition.append(" '");
              definition.append(value);
              definition.append("'");
            }

            definition.append(" )");
          }
        }
      }

      definition.append(" )");


      return new AttributeType(definition.toString(), primaryName, names, oid,
                               description, superiorType, syntax,
                               approximateMatchingRule, equalityMatchingRule,
                               orderingMatchingRule, substringMatchingRule,
                               attributeUsage, isCollective,
                               isNoUserModification, isObsolete, isSingleValue,
                               extraProperties);
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
     * Set the isSingleValue.
     *
     * @param isSingleValue
     *          The isSingleValue.
     */
    public void setSingleValue(boolean isSingleValue) {
      this.isSingleValue = isSingleValue;
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
    new AttributeType(null, null, null, null, null, null, null, null,
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
    new AttributeType(null, null, null, null, null, null, null, null, null,
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
    AttributeType type = new AttributeType("", null, null, "1.2.3", null,
        null, null, null, null, null, null, null, false, false,
        false, false, null);

    Assert.assertNull(type.getPrimaryName());
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
    AttributeType type = builder.getInstance();

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
    AttributeType type = builder.getInstance();

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
    AttributeType type = builder.getInstance();

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
    AttributeType parent = builder.getInstance();

    builder.setPrimaryName("childType");
    builder.setOid("4.5.6");
    builder.setSuperiorType(parent);
    AttributeType child = builder.getInstance();

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
    AttributeType type = builder.getInstance();

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
    AttributeType type = builder.getInstance();

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

    AttributeType parent = builder.getInstance();

    builder.setPrimaryName("childType");
    builder.setOid("4.5.6");
    builder.setSuperiorType(parent);
    AttributeType child = builder.getInstance();

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
    AttributeType type = builder.getInstance();

    Assert.assertEquals(type.isObjectClassType(), result);
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
    AttributeType type = builder.getInstance();

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
    AttributeType type = builder.getInstance();

    ByteString b1 = new ASN1OctetString(value1);
    ByteString b2 = new ASN1OctetString(value2);

    ByteString r1 = type.normalize(b1);
    ByteString r2 = type.normalize(b2);

    Assert.assertEquals(r1.equals(r2), result);
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
    AttributeType type = builder.getInstance();

    Assert.assertFalse(type.isCollective());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setCollective(true);
    type = builder.getInstance();

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
    AttributeType type = builder.getInstance();

    Assert.assertFalse(type.isNoUserModification());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setNoUserModification(true);
    type = builder.getInstance();

    Assert.assertTrue(type.isNoUserModification());
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
    AttributeType type = builder.getInstance();

    Assert.assertFalse(type.isSingleValue());

    builder = new AttributeTypeBuilder("testType", "1.2.3");
    builder.setSingleValue(true);
    type = builder.getInstance();

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
    AttributeType type = builder.getInstance();
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
    AttributeType type = builder.getInstance();
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

    AttributeType type = builder.getInstance();
    Assert.assertEquals(type.toString(), "( 1.2.3 "
        + "NAME ( 'testType' 'anotherName' ) "
        + "EQUALITY booleanMatch "
        + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.7 " + "SINGLE-VALUE "
        + "USAGE directoryOperation " + "X-SCHEMA-FILE '/foo/bar' )");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected SchemaDefinitionBuilder getBuilder(String name, String oid) {
    return new AttributeTypeBuilder(name, oid);
  }
}
