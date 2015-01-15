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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Attribute type tests.
 */
@SuppressWarnings("javadoc")
public class AttributeTypeTest extends AbstractSchemaElementTestCase {
    private final Schema schema;

    public AttributeTypeTest() throws Exception {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.buildAttributeType("1.2.1")
               .names(EMPTY_NAMES)
               .obsolete(true)
               .syntax("1.3.6.1.4.1.1466.115.121.1.27")
               .singleValue(true)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .addToSchema();

        builder.addAttributeType(
                "( 1.2.2 OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE "
                        + " COLLECTIVE X-ORIGIN ( 'Sun Java System Identity Management' "
                        + "'user defined' ) X-SCHEMA-FILE '98sunEmp.ldif')", false);

        builder.buildAttributeType("1.2.3")
               .names("testType")
               .superiorType("1.2.2")
               .syntax("1.3.6.1.4.1.1466.115.121.1.27")
               .collective(true)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .addToSchema();

        builder.addAttributeType("( 1.2.4 NAME 'testType' SUP 1.2.3 SINGLE-VALUE COLLECTIVE )", false);

        builder.buildAttributeType("1.2.5")
               .names("testType", "testnamealias", "anothernamealias")
               .equalityMatchingRule(EMR_CASE_IGNORE_LIST_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_LIST_OID)
               .approximateMatchingRule(AMR_DOUBLE_METAPHONE_OID)
               .syntax(SYNTAX_INTEGER_OID)
               .noUserModification(true)
               .usage(AttributeUsage.DSA_OPERATION)
               .addToSchema();

        builder.addAttributeType("( 1.2.6 NAME ( 'testType' 'testnamealias' 'anothernamealias1' ) "
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP anothernamealias"
                + " USAGE dSAOperation NO-USER-MODIFICATION )", false);

        schema = builder.toSchema();
        if (!schema.getWarnings().isEmpty()) {
            throw new Exception("Base schema not valid!");
        }
    }

    @DataProvider(name = "equalsTestData")
    public Object[][] createEqualsTestData() throws SchemaException, DecodeException {
        return new Object[][] {
            { schema.getAttributeType("1.2.3"), schema.getAttributeType("1.2.3"), true },
            { schema.getAttributeType("1.2.4"), schema.getAttributeType("1.2.3"), false } };
    }

    @Test
    public void testADSyntax() throws Exception {
        // AD uses single quotes around OIDs
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' " + " SUP '1.2.5' "
                + " EQUALITY 'caseIgnoreMatch' "
                + " SYNTAX '1.3.6.1.4.1.1466.115.121.1.15' USAGE dSAOperation )", false);
        Assert.assertTrue(builder.toSchema().getWarnings().isEmpty());
    }

    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testADSyntaxQuoteMismatch() throws Exception {
        // AD uses single quotes around OIDs
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' " + " SUP '1.2.5 "
                + " EQUALITY 'caseIgnoreMatch' "
                + " SYNTAX '1.3.6.1.4.1.1466.115.121.1.15' USAGE dSAOperation )", false);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    @Test
    public void testCollectiveOperational() throws Exception {
        // Collective can't be operational
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE "
                + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                + " SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                + " COLLECTIVE USAGE directoryOperation )", false);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    /**
     * Check constructor sets the default matching rules correctly.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testConstructorDefaultMatchingRules() throws Exception {
        final AttributeType type = schema.getAttributeType("1.2.1");

        final Syntax syntax = schema.getSyntax("1.3.6.1.4.1.1466.115.121.1.27");
        Assert.assertEquals(type.getApproximateMatchingRule(), syntax.getApproximateMatchingRule());
        Assert.assertEquals(type.getEqualityMatchingRule(), syntax.getEqualityMatchingRule());
        Assert.assertEquals(type.getOrderingMatchingRule(), syntax.getOrderingMatchingRule());
        Assert.assertEquals(type.getSubstringMatchingRule(), syntax.getSubstringMatchingRule());
    }

    /**
     * Check constructor sets the default usage correctly.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testConstructorDefaultUsage() throws Exception {
        final AttributeType d = schema.getAttributeType("1.2.2");

        Assert.assertEquals(d.getUsage(), AttributeUsage.USER_APPLICATIONS);
    }

    /**
     * Check constructor inherits the matching rules from the parent type when
     * required.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dependsOnMethods = "testConstructorMatchingRules")
    public void testConstructorInheritsMatchingRules() throws Exception {
        final AttributeType parent = schema.getAttributeType("1.2.5");

        final AttributeType child = schema.getAttributeType("1.2.6");

        Assert.assertEquals(parent.getApproximateMatchingRule(), child.getApproximateMatchingRule());
        Assert.assertEquals(parent.getEqualityMatchingRule(), child.getEqualityMatchingRule());
        // It should inherit ordering rule from parent's syntax since parent
        // didn't specify an ordering matching rule.
        Assert.assertEquals(parent.getSyntax().getOrderingMatchingRule(), child
                .getOrderingMatchingRule());
        Assert.assertEquals(parent.getSubstringMatchingRule(), child.getSubstringMatchingRule());
    }

    /**
     * Check constructor inherits the syntax from the parent type when required.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dependsOnMethods = "testConstructorSyntax")
    public void testConstructorInheritsSyntax() throws Exception {
        AttributeType parent = schema.getAttributeType("1.2.3");

        AttributeType child = schema.getAttributeType("1.2.4");

        Assert.assertEquals(parent.getSyntax(), child.getSyntax());

        parent = schema.getAttributeType("1.2.2");

        child = schema.getAttributeType("1.2.3");
        Assert.assertFalse(parent.getSyntax().equals(child.getSyntax()));

        // Make sure paren't s syntax was not inherited in this case
        child = schema.getAttributeType("1.2.6");
        Assert.assertEquals(child.getSyntax().getOID(), SYNTAX_DIRECTORY_STRING_OID);
    }

    /**
     * Check constructor sets the matching rules correctly.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testConstructorMatchingRules() throws Exception {
        final AttributeType type = schema.getAttributeType("1.2.5");

        Assert.assertEquals(type.getEqualityMatchingRule().getOID(), EMR_CASE_IGNORE_LIST_OID);
        Assert.assertEquals(type.getOrderingMatchingRule().getOID(), type.getSyntax()
                .getOrderingMatchingRule().getOID());
        Assert.assertEquals(type.getSubstringMatchingRule().getOID(), SMR_CASE_IGNORE_LIST_OID);
        Assert.assertEquals(type.getApproximateMatchingRule().getOID(), AMR_DOUBLE_METAPHONE_OID);
    }

    /**
     * Check that the primary name is added to the set of names.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testConstructorPrimaryName() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.3");

        Assert.assertTrue(d.hasName("testType"));
        Assert.assertFalse(d.hasName("xxx"));

        d = schema.getAttributeType("1.2.4");

        Assert.assertTrue(d.hasName("testType"));
        Assert.assertFalse(d.hasName("xxx"));

    }

    /**
     * Check constructor sets the syntax correctly.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testConstructorSyntax() throws Exception {
        final AttributeType d = schema.getAttributeType("1.2.2");

        Assert.assertEquals(d.getSyntax().getOID(), "1.3.6.1.4.1.1466.115.121.1.15");
    }

    /**
     * Check that the type names are accessible.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testConstructorTypeNames() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.5");

        Assert.assertTrue(d.hasName("testType"));
        Assert.assertTrue(d.hasName("testnamealias"));
        Assert.assertTrue(d.hasName("anothernamealias"));

        d = schema.getAttributeType("1.2.6");

        Assert.assertTrue(d.hasName("testType"));
        Assert.assertTrue(d.hasName("testnamealias"));
        Assert.assertTrue(d.hasName("anothernamealias1"));
    }

    /**
     * Check that the {@link AttributeType#getUsage()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testGetAttributeUsage() throws Exception {
        AttributeType type = schema.getAttributeType("1.2.1");
        Assert.assertEquals(type.getUsage(), AttributeUsage.USER_APPLICATIONS);
        type = schema.getAttributeType("1.2.6");
        Assert.assertEquals(type.getUsage(), AttributeUsage.DSA_OPERATION);
    }

    /**
     * Check that the {@link CommonSchemaElements#getNameOrOID()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetNameOrOIDReturnsOID() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.1");

        Assert.assertEquals(d.getNameOrOID(), "1.2.1");

        d = schema.getAttributeType("1.2.2");

        Assert.assertEquals(d.getNameOrOID(), "1.2.2");
    }

    /**
     * Check that the {@link CommonSchemaElements#getNameOrOID()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetNameOrOIDReturnsPrimaryName() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.3");
        Assert.assertEquals(d.getNameOrOID(), "testType");
        d = schema.getAttributeType("1.2.4");
        Assert.assertEquals(d.getNameOrOID(), "testType");
    }

    /**
     * Check that the {@link CommonSchemaElements#getNormalizedNames()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetNormalizedNames() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.5");
        Iterator<String> i = d.getNames().iterator();
        Assert.assertEquals(i.next(), "testType");
        Assert.assertEquals(i.next(), "testnamealias");
        Assert.assertEquals(i.next(), "anothernamealias");

        d = schema.getAttributeType("1.2.6");
        i = d.getNames().iterator();
        Assert.assertEquals(i.next(), "testType");
        Assert.assertEquals(i.next(), "testnamealias");
        Assert.assertEquals(i.next(), "anothernamealias1");
    }

    /**
     * Check that the {@link CommonSchemaElements#getOID()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetOID() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.3");
        Assert.assertEquals(d.getOID(), "1.2.3");
        d = schema.getAttributeType("1.2.4");
        Assert.assertEquals(d.getOID(), "1.2.4");

    }

    /**
     * Check that the {@link AttributeType#getSuperiorType()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testGetSuperiorType() throws Exception {
        AttributeType type = schema.getAttributeType("1.2.3");
        Assert.assertEquals(type.getSuperiorType().getOID(), "1.2.2");
        type = schema.getAttributeType("1.2.4");
        Assert.assertEquals(type.getSuperiorType().getOID(), "1.2.3");
    }

    /**
     * Check that the {@link CommonSchemaElements#hasNameOrOID(String)} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testHasNameOrOID() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.3");

        Assert.assertTrue(d.hasNameOrOID("testType"));
        Assert.assertTrue(d.hasNameOrOID("1.2.3"));
        Assert.assertFalse(d.hasNameOrOID("x.y.z"));
        d = schema.getAttributeType("1.2.4");

        Assert.assertTrue(d.hasNameOrOID("testType"));
        Assert.assertTrue(d.hasNameOrOID("1.2.4"));
        Assert.assertFalse(d.hasNameOrOID("x.y.z"));
    }

    @Test
    public void testInheritFromNonCollective() throws Exception {
        // Collective can't inherit from non-collective
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' "
                + " OBSOLETE SUP 1.2.5 "
                + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                + " SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                + " COLLECTIVE USAGE userApplications )", false);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    @Test
    public void testInheritFromUserAppUsage() throws Exception {
        // directoryOperation can't inherit from userApplications
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' "
                + " OBSOLETE SUP 1.2.1 "
                + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                + " SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                + " NO-USER-MODIFICATION USAGE directoryOperation )", false);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    /**
     * Check that the {@link AttributeType#isCollective()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testIsCollective() throws Exception {
        AttributeType type = schema.getAttributeType("1.2.2");
        Assert.assertTrue(type.isCollective());
        type = schema.getAttributeType("1.2.3");
        Assert.assertTrue(type.isCollective());
        type = schema.getAttributeType("1.2.6");
        Assert.assertFalse(type.isCollective());
        type = schema.getAttributeType("1.2.5");
        Assert.assertFalse(type.isCollective());
    }

    /**
     * Check that the {@link AttributeType#isNoUserModification()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testIsNoUserModification() throws Exception {
        AttributeType type = schema.getAttributeType("1.2.5");
        Assert.assertTrue(type.isNoUserModification());
        type = schema.getAttributeType("1.2.6");
        Assert.assertTrue(type.isNoUserModification());
        type = schema.getAttributeType("1.2.3");
        Assert.assertFalse(type.isNoUserModification());
        type = schema.getAttributeType("1.2.4");
        Assert.assertFalse(type.isNoUserModification());
    }

    /**
     * Check that the {@link CommonSchemaElements#isObsolete()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testIsObsolete() throws Exception {
        AttributeType d = schema.getAttributeType("1.2.3");
        Assert.assertFalse(d.isObsolete());
        d = schema.getAttributeType("1.2.4");
        Assert.assertFalse(d.isObsolete());

        d = schema.getAttributeType("1.2.1");
        Assert.assertTrue(d.isObsolete());
        d = schema.getAttributeType("1.2.2");
        Assert.assertTrue(d.isObsolete());
    }

    /**
     * Check that the {@link AttributeType#isSingleValue()} method.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public void testIsSingleValue() throws Exception {
        AttributeType type = schema.getAttributeType("1.2.1");
        Assert.assertTrue(type.isSingleValue());
        type = schema.getAttributeType("1.2.2");
        Assert.assertTrue(type.isSingleValue());
        type = schema.getAttributeType("1.2.5");
        Assert.assertFalse(type.isSingleValue());
        type = schema.getAttributeType("1.2.6");
        Assert.assertFalse(type.isSingleValue());
    }

    /**
     * Check that the simple constructor throws an NPE when mandatory parameters
     * are not specified.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoSupNorSyntax1() throws Exception {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.buildAttributeType("1.2.1")
               .names(EMPTY_NAMES)
               .obsolete(true)
               .usage(AttributeUsage.DSA_OPERATION)
               .addToSchema();

        builder.addAttributeType("( 1.2.2 OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE )", false);

    }

    /**
     * Check that the simple constructor throws an NPE when mandatory parameters
     * are not specified.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoSupNorSyntax2() throws Exception {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addAttributeType("( 1.2.2 OBSOLETE SINGLE-VALUE )", false);
    }

    @Test
    public void testNoUserModNonOperational() throws Exception {
        // NO-USER-MODIFICATION can't have non-operational usage
        final SchemaBuilder builder = new SchemaBuilder(schema);
        builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE "
                + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                + " SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                + " NO-USER-MODIFICATION USAGE userApplications )", false);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    protected SchemaElement getElement(final String description,
            final Map<String, List<String>> extraProperties) throws SchemaException {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.buildAttributeType("1.2.3")
               .names("testType")
               .description(description)
               .syntax("1.3.6.1.4.1.1466.115.121.1.27")
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(extraProperties)
               .addToSchema();

        return builder.toSchema().getAttributeType("1.2.3");
    }

}
