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
 *      Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.schema.NameForm.Builder;
import org.testng.annotations.Test;

/**
 * This class tests the NameForm class. The name form builder can be only used
 * with the schema builder.
 */
@SuppressWarnings("javadoc")
public class NameFormTestCase extends AbstractSchemaTestCase {

    /**
     * Creates a new form using the required parameters only (oid, structural
     * OID and required attributes).
     */
    @Test
    public final void testCreatesANewFormWithOnlyRequiredParameters() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                    .structuralObjectClassOID("person")
                    .requiredAttributes("sn", "cn") // ("cn, sn") is not supported.
                    .addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms().size()).isGreaterThan(0);

        for (final NameForm nf : schema.getNameForms()) {
            assertThat(nf.hasName("hasAName ?")).isFalse();
            assertThat(nf.getNameOrOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
            assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
            assertThat(nf.toString()).isEqualTo("( 1.3.6.1.4.1.1466.115.121.1.35 OC person MUST ( sn $ cn ) )");
        }
    }

    /**
     * Creates a new form with a name.
     */
    @Test
    public final void testCreatesANewFormWithAName() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                    .structuralObjectClassOID("person")
                    .names("MyNewForm")
                    .requiredAttributes("sn", "cn")
                    .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms().size()).isGreaterThan(0);

        for (final NameForm nf : schema.getNameForms()) {

            assertThat(nf.hasName("hasAName ?")).isFalse();
            assertThat(nf.getNameOrOID()).isEqualTo("MyNewForm");
            assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

            assertThat(nf.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' OC person MUST ( sn $ cn ) )");
        }
    }

    /**
     * Creates a new form with optional attributes OID.
     */
    @Test
    public final void testCreatesANewFormWithOptionalAttributesOid() {

        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .structuralObjectClassOID("person")
                .names("MyNewForm")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("owner")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms().size()).isGreaterThan(0);

        for (final NameForm nf : schema.getNameForms()) {
            assertThat(nf.hasName("hasAName ?")).isFalse();
            assertThat(nf.getNameOrOID()).isEqualTo("MyNewForm");
            assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
            assertThat(nf.getOptionalAttributes().toString()).contains("owner");

            assertThat(nf.toString()).isEqualTo(
                    "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' OC person MUST ( sn $ cn ) MAY owner )");
        }
    }

    /**
     * Creates a new form with ExtraProperties.
     */
    @Test
    public final void testCreatesANewNameFormWithExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .structuralObjectClassOID("person")
                .names("MyNewForm")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("owner")
                .extraProperties("X-ORIGIN", "RFC xxx")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms().size()).isGreaterThan(0);

        for (final NameForm nf : schema.getNameForms()) {
            assertThat(nf.hasName("hasAName ?")).isFalse();
            assertThat(nf.getNameOrOID()).isEqualTo("MyNewForm");
            assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
            assertThat(nf.getExtraProperties().get("X-ORIGIN").get(0)).isEqualTo("RFC xxx");
            assertThat(nf.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' OC person "
                + "MUST ( sn $ cn ) MAY owner X-ORIGIN 'RFC xxx' )");
        }
    }

    /**
     * When required attributes are absents, the builder sends exception. Here,
     * the OID is missing. An exception is expected.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowNullOid() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm((String) null)
                .description("This is a description")
                .names("name1")
                .names("name2", "name3")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .structuralObjectClassOID("person")
                .requiredAttributes("sn, cn")
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * When required attributes are absents, the builder sends an exception.
     * Here, the structural class OID is missing.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowNullStructuralClassOid() {

        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn, cn")
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * When required attributes are absents, the builder sends an exception.
     * Here, the required attributes OID is missing.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = java.lang.IllegalArgumentException.class)
    public final void testBuilderDoesntAllowEmptyRequiredAttributes() {

        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes()
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * When required attributes are absents, the builder sends an exception.
     * Here, the required attribute is missing.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowNullRequiredAttributes() {

        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * Optional attributes shouldn't be equals to null. Exception expected.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testBuilderDoesntAllowNullOptionalAttributes() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .structuralObjectClassOID("person")
                .requiredAttributes("sn, cn")
                .requiredAttributes((String[]) null)
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * By default optional attributes are empty.
     *
     * @throws SchemaException
     */
    @Test
    public final void testBuilderAllowsEmptyOptionalAttributes() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                // .optionalAttributeOIDs("") empty by default.
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * Allows removing non-existent attributes without errors.
     *
     * @throws SchemaException
     */
    @Test
    public final void testBuilderAllowRemovingNonexistentAttributes() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("This is a description")
                .names("MyNewForm")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .structuralObjectClassOID("person")
                .requiredAttributes("sn")
                .removeRequiredAttribute("unknown")
                .removeOptionalAttribute("optionalunknown")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();

        assertThat(schema.getNameForms()).isNotEmpty();
        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(nf.getRequiredAttributes().size()).isEqualTo(1);
        assertThat(nf.getRequiredAttributes().iterator().next().getNameOrOID()).isEqualTo("sn");
        assertThat(nf.getOptionalAttributes()).isEmpty();
    }

    /**
     * Verifying the schema builder allows to add directly a definition. The
     * name form is created as well.
     */
    @Test
    public final void testNameFormDefinition() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        // @formatter:off
        final String nameFormDefinition = "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' "
                + "DESC 'Description of the new form' "
                + "OC person MUST ( sn $ cn ) "
                + "MAY ( description $ uid ) "
                + "X-SCHEMA-FILE 'NameFormCheckingTestCase' "
                + "X-ORIGIN 'NameFormCheckingTestCase' )";
        // @formatter:on

        // Add the nameForm to the schemaBuilder.
        sb.addNameForm(nameFormDefinition, false);
        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        assertThat(schema.getNameForms()).isNotEmpty();
        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(nf.getExtraProperties()).isNotEmpty();

        // @formatter:off
        assertThat(nf.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' "
                + "DESC 'Description of the new form' "
                + "OC person MUST ( sn $ cn ) "
                + "MAY ( description $ uid ) "
                + "X-SCHEMA-FILE 'NameFormCheckingTestCase' "
                + "X-ORIGIN 'NameFormCheckingTestCase' )");
        // @formatter:on
    }

    /**
     * Required attributes are missing in the following definition.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testNameFormDefinitionDoesntAllowMissingAttributes() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        // @formatter:off
        final String nameFormDefinition = "( 1.3.6.1.4.1.1466.115.121.1.35 NAME 'MyNewForm' "
                + "DESC 'Description of the new form' "
                + "OC person "
                + "MAY ( description $ uid ) "
                + "X-SCHEMA-FILE 'NameFormCheckingTestCase' "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' "
                + "X-ORIGIN 'NameFormCheckingTestCase' )";
        // @formatter:on

        // Add the nameForm to the schemaBuilder.
        sb.addNameForm(nameFormDefinition, false);
        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
    }

    /**
     * Duplicates a name form using the schema builder.
     *
     * @throws SchemaException
     */
    @Test
    public final void testDuplicatesTheNameForm() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final Builder nfb = new Builder("1.3.6.1.4.1.1466.115.121.1.35", sb);
        nfb.description("Description of the new form")
            .names("MyNewForm")
            .structuralObjectClassOID("person")
            .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
            .requiredAttributes("sn", "cn")
            .optionalAttributes("description", "uid")
            .addToSchema();

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms()).isNotEmpty();
        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

        sb.buildNameForm(nf)
            .names("Dolly")
            .oid("1.3.6.1.4.1.1466.115.121.1.36")
            .addToSchemaOverwrite();
        schema = sb.toSchema();
        assertThat(schema.getNameForms()).isNotEmpty();
        assertThat(schema.getNameForms().size()).isEqualTo(2);
        assertThat(schema.getWarnings()).isEmpty();

        final Iterator<NameForm> i = schema.getNameForms().iterator();
        i.next(); // Jump the first element (== nf)
        final NameForm dolly = i.next(); // Our new cloned NameForm.
        assertThat(dolly.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.36"); // With the new OID !
        assertThat(dolly.getNames().size()).isEqualTo(2);
    }

    /**
     * Duplicates a name form using the schema builder.
     * The duplicate name form contains an inappropriate structural class OID which made the build fails.
     * <p>Warning from schema is : <pre>
     * "The name form description "MyNewForm" is associated with a structural object class
     * "wrongStructuralOID" which is not defined in the schema".</pre>
     *
     * @throws SchemaException
     */
    @Test
    public final void testDuplicatesTheNameFormFails() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final Builder nfb = new Builder("1.3.6.1.4.1.1466.115.121.1.35", sb);
        nfb.description("Description of the new form")
            .names("MyNewForm")
            .structuralObjectClassOID("person")
            .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
            .requiredAttributes("sn", "cn")
            .optionalAttributes("description", "uid")
            .addToSchema();

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms()).isNotEmpty();
        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

        sb.buildNameForm(nf)
            .names("Dolly")
            .oid("1.3.6.1.4.1.1466.115.121.1.36")
            .structuralObjectClassOID("wrongStructuralOID")
            .addToSchemaOverwrite();
        schema = sb.toSchema();
        assertThat(schema.getNameForms().size()).isEqualTo(1); // MyNewForm
        // The duplicate name form is  not created and the schema contains warnings about.
        assertThat(schema.getWarnings()).isNotEmpty();
    }

    /**
     * Compare two same name forms using the equal function.
     */
    @Test
    public final void testNameFormEqualsTrue() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .names("TheNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        final NameForm nf1 = schema.getNameForms().iterator().next();

        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
            .names("MyNewForm")
            .structuralObjectClassOID("person")
            .requiredAttributes("sn", "cn")
            .addToSchema().toSchema();
        final NameForm nf2 = schema2.getNameForm("MyNewForm");

        assertThat(nf1.equals(nf2)).isTrue();
    }

    /**
     * Equals between two name forms fails.
     */
    @Test
    public final void testNameFormEqualsFalse() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .names("TheNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on
        final NameForm nf1 = schema.getNameForms().iterator().next();

        // @formatter:off
        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.36")
                .description("Description of the new form")
                .names("MyNewForm")
                .names("TheNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(nf1.equals(schema2.getNameForms().iterator().next())).isFalse();
    }

    /**
     * Testing to add a name form using the definition.
     *
     * @throws SchemaException
     */
    @Test
    public final void testCreateFormUsingDefinitionAndSchemaBuilder() {
        final SchemaBuilder sb = new SchemaBuilder();

        // @formatter:off
        sb.addSchema(Schema.getCoreSchema(), false)
            .addObjectClass(
                "( mycustomobjectclass-oid NAME 'myCustomObjectClassOC' SUP top "
                + "STRUCTURAL MUST cn X-ORIGIN 'NameFormTestCase')", false)
            .addNameForm(
                "( mycustomnameform-oid NAME 'myCustomNameForm' OC myCustomObjectClassOC "
                + "MUST cn X-ORIGIN 'NameFormTestCase' )",
                false)
            .toSchema();
        // @formatter:on

        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameFormsWithName("mycustomnameform")).isNotNull();
        for (final NameForm o : schema.getNameForms()) {
            assertThat(o.getNameOrOID()).isEqualTo("myCustomNameForm");
            assertThat(o.getOID()).isEqualTo("mycustomnameform-oid");
            assertThat(o.getStructuralClass().getOID()).isEqualTo("mycustomobjectclass-oid");
        }
    }

    /**
     * Compare two same name forms using the equal function. One created by the
     * name form builder, the other by the schema builder directly using the
     * definition.
     */
    @Test
    public final void testNameFormEqualityReturnsTrueBetweenBuilderAndDefinition() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final NameForm nf1 = schema.getNameForms().iterator().next();

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);

        // @formatter:off
        sb2.addNameForm(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME ( 'MyNewForm' ) "
                + "DESC 'Description of the new form' "
                + "OC person MUST ( sn $ cn ) "
                + "MAY ( description $ uid ) "
                + "X-ORIGIN 'NameFormCheckingTestCase' )", false);
        // @formatter:on

        final NameForm nf2 = sb2.toSchema().getNameForm("MyNewForm");

        assertThat(nf1.equals(nf2)).isTrue();
    }

    /**
     * Compare two same name forms using the equal function. One created by the
     * name form builder, the other by the schema builder directly using the
     * definition with different OID.
     */
    @Test
    public final void testNameFormEqualityReturnsFalseBetweenBuilderAndDefinition() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final NameForm nf1 = schema.getNameForms().iterator().next();

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);

        // @formatter:off
        sb2.addNameForm(
                "( 1.3.6.1.4.1.1466.115.121.1.36 NAME ( 'MyNewForm' ) " // OID changed.
                + "DESC 'Description of the new form' "
                + "OC person MUST ( sn $ cn ) "
                + "MAY ( description $ uid ) "
                + "X-ORIGIN 'NameFormCheckingTestCase' )", false);
        // @formatter:on

        final NameForm nf2 = sb2.toSchema().getNameForm("MyNewForm");
        // Equals is only based on the OID.
        assertThat(nf1.equals(nf2)).isFalse();
    }

    /**
     * Validates a name form using an abstract object class instead of an
     * structural object class and throws an error.
     *
     * @throws SchemaException
     */
    @Test
    public final void testNameFormValidateDoesntAllowAbstractObjectClass() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("mynewform-oid")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("top")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getNameForms()).isEmpty();
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains(
                "This object class exists in the schema but is defined as ABSTRACT rather than structural");
        // output is : The name form description "MyNewForm" is associated with the "top" object class.
        // This object class exists in the schema but is defined as ABSTRACT rather than structural
    }

    /**
     * Creates a name form using the appropriate structural object class.
     *
     * @throws SchemaException
     */
    @Test
    public final void testNameFormValidateAllowsStructuralObjectClass() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("mynewform-oid")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getNameForms()).isNotEmpty();
        assertThat(schema.getWarnings()).isEmpty();

        assertThat(schema.getNameForms().iterator().next().getOID()).isEqualTo("mynewform-oid");
        assertThat(schema.getNameForms().iterator().next().getNames().get(0)).isEqualTo("MyNewForm");
    }

    /**
     * Adds multiple attributes... e.g : name form containing multiple
     * extra-properties, requiredAttributes, optional attributes, names...
     *
     * @throws SchemaException
     */
    @Test
    public final void testBuildsANewFormWithMultipleAttributes() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("0.0.1.2.3")
                .description("multipleAttributes Test description")
                .names("multipleAttributes")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase2")
                .requiredAttributes("sn", "cn") // ("cn, sn") is not supported.
                .requiredAttributes("uid")
                .optionalAttributes("owner")
                .optionalAttributes("l")
                .names("Rock")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms()).isNotEmpty();

        for (final NameForm nf : schema.getNameForms()) {

            assertThat(nf.getDescription()).isEqualTo("multipleAttributes Test description");
            assertThat(nf.getOID()).isEqualTo("0.0.1.2.3");

            assertThat(nf.getNames().get(0)).isEqualTo("multipleAttributes");
            assertThat(nf.getNames().get(1)).isEqualTo("Rock");
            assertThat(nf.getExtraProperties().get("X-ORIGIN").get(0))
                    .isEqualTo("NameFormCheckingTestCase");
            assertThat(nf.getExtraProperties().get("X-ORIGIN").get(1)).isEqualTo(
                    "NameFormCheckingTestCase2");

            assertThat(nf.getStructuralClass().getNameOrOID()).isEqualTo("person");

            // RequiredAttributes is accessible only after validate
            for (final AttributeType att : nf.getRequiredAttributes()) {
                assertThat(
                        att.getNameOrOID().contains("cn") || att.getNameOrOID().contains("sn")
                                || att.getNameOrOID().contains("uid")).isTrue();
            }
            // OptionalAttributes is accessible only after validate
            for (final AttributeType att : nf.getOptionalAttributes()) {
                assertThat(att.getNameOrOID().contains("owner") || att.getNameOrOID().contains("l"))
                        .isTrue();
            }
        }
    }

    /**
     * Using the schema builder for adding new name forms. Allows methods
     * chaining.
     * <p>
     * e.g : (SchemaBuilder) <code>
     * scb.addNameForm("1.2.3").build(true).addAttributeType
     * (...).build(false).addNameForm(...)...etc.
     * </code>
     * <p>
     * N.B : NameForm is validated when the SchemaBuilder is building a Schema.
     * If the NameForm is not valid, the SchemaBuilder just remove the invalid
     * NameForm.
     *
     * @throws SchemaException
     */
    @Test
    public final void testCreatesANewFormUsingChainingMethods() {
        final Map<String, List<String>> extraProperties = new TreeMap<>();
        final List<String> extra = new ArrayList<>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.2.3")
                .description("NF1's description")
                .names("theFirstNameForm")
                .structuralObjectClassOID("person")
                .extraProperties(extraProperties)
                .requiredAttributes("uid")
                .optionalAttributes("sn")
                .addToSchemaOverwrite()
            .buildNameForm("4.4.4")
                .description("NF2's description")
                .names("theSecondNameForm")
                .structuralObjectClassOID("person")
                .extraProperties(extraProperties)
                .requiredAttributes("uid")
                .requiredAttributes("sn")
                .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on

        // First name form
        final NameForm first = schema.getNameForm("theFirstNameForm");
        assertThat(first.getOID()).isEqualTo("1.2.3");
        assertThat(first.getDescription()).isEqualTo("NF1's description");
        assertThat(first.getRequiredAttributes()).isNotEmpty();
        assertThat(first.getOptionalAttributes()).isNotEmpty();
        assertThat(first.getStructuralClass().getNameOrOID()).isEqualTo("person");

        // Second name form
        final NameForm second = schema.getNameForm("theSecondNameForm");
        assertThat(second.getOID()).isEqualTo("4.4.4");
        assertThat(second.getDescription()).isEqualTo("NF2's description");
        assertThat(second.getRequiredAttributes()).isNotEmpty();
        assertThat(second.getOptionalAttributes()).isEmpty();
    }

    /**
     * Remove functions uses on names / required attribute.
     *
     * @throws SchemaException
     */
    @Test
    public final void testCreatesNewFormAndRemovesAttributes() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("0.0.1.2.3")
                .description("multipleAttributes Test description")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase")
                .extraProperties("X-ORIGIN", "NameFormCheckingTestCase2")
                .requiredAttributes("sn", "cn")
                .requiredAttributes("uid")
                .optionalAttributes("givenName")
                .optionalAttributes("l")
                .names("nameform1")
                .names("nameform2")
                .names("nameform3")
                .removeName("nameform2")
                .removeRequiredAttribute("cn")
                .removeOptionalAttribute("l")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getNameForms()).isNotEmpty();
        final NameForm nf = schema.getNameForms().iterator().next();

        assertThat(nf.getNames()).hasSize(2);
        assertThat(nf.getNames()).contains("nameform1");
        assertThat(nf.getNames()).contains("nameform3");

        assertThat(nf.getRequiredAttributes().size()).isEqualTo(2);
        assertThat(nf.getRequiredAttributes().toString()).contains("'sn'");
        assertThat(nf.getRequiredAttributes().toString()).contains("uid");

        assertThat(nf.getOptionalAttributes().size()).isEqualTo(1);
    }

    /**
     * Trying to remove attributes from a duplicated name form.
     *
     * @throws SchemaException
     */
    @Test
    public final void testDuplicatesNameFormAndRemovesAttributes() {

        // @formatter:off
        Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormTestCase", "Forgerock", "extra")
                .extraProperties("FROM", "NameFormTestCase")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms()).isNotEmpty();

        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(nf.getRequiredAttributes().size()).isEqualTo(2);
        assertThat(nf.getOptionalAttributes().size()).isEqualTo(2);

        // @formatter:off.
        SchemaBuilder sb = new SchemaBuilder(Schema.getCoreSchema());
        Builder nfBuilder = new Builder(nf, sb)
                    .names("Dolly")
                    .oid("1.3.6.1.4.1.1466.115.121.1.36")
                    .removeOptionalAttribute("uid")
                    .removeOptionalAttribute("nonExistentUid")
                    .requiredAttributes("street")
                    .removeRequiredAttribute("sn")
                    .removeExtraProperty("X-ORIGIN", "extra")
                    .removeExtraProperty("X-ORIGIN", "Forgerock")
                    .removeExtraProperty("FROM");

        // @formatter:on
        sb.addSchema(schema, true);
        sb.addSchema(nfBuilder.addToSchemaOverwrite().toSchema(), true);
        Schema finalSchema =  sb.toSchema();

        assertThat(finalSchema.getNameForms()).isNotEmpty();
        assertThat(finalSchema.getNameForms().size()).isEqualTo(2);
        assertThat(finalSchema.getWarnings()).isEmpty();

        final Iterator<NameForm> i = finalSchema.getNameForms().iterator();
        i.next(); // Jump the first element (== nf)
        final NameForm dolly = i.next();
        assertThat(dolly.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.36");

        assertThat(dolly.getRequiredAttributes().size()).isEqualTo(2);
        assertThat(dolly.getRequiredAttributes().toString()).contains("street");
        assertThat(dolly.getRequiredAttributes().toString()).contains("cn");

        assertThat(dolly.getOptionalAttributes().size()).isEqualTo(1);
        assertThat(dolly.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(1);
        assertThat(dolly.getExtraProperties().get("FROM")).isNull();
    }

    /**
     * Clears attributes from a duplicated name form.
     *
     * @throws SchemaException
     */
    @Test
    public final void testDuplicatesNameFormAndClears() {

        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final Builder nfb = new Builder("1.3.6.1.4.1.1466.115.121.1.35", sb);
        nfb.description("Description of the new form")
            .names("MyNewForm")
            .structuralObjectClassOID("person")
            .extraProperties("X-ORIGIN", "NameFormTestCase", "Forgerock", "extra")
            .extraProperties("FROM", "NameFormTestCase")
            .requiredAttributes("sn", "cn")
            .optionalAttributes("description", "uid")
            .addToSchema();

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms()).isNotEmpty();

        final NameForm nf = schema.getNameForms().iterator().next();
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(nf.getRequiredAttributes()).hasSize(2);
        assertThat(nf.getOptionalAttributes()).hasSize(2);
        assertThat(nf.getExtraProperties()).hasSize(2);

        sb.buildNameForm(nf)
            .removeAllNames()
            .names("Dolly")
            .removeName("thisOneDoesntExist")
            .oid("1.3.6.1.4.1.1466.115.121.1.36")
            .removeAllOptionalAttributes()
            .removeAllExtraProperties()
            .removeAllRequiredAttributes()
            .requiredAttributes("businessCategory")
            .addToSchemaOverwrite();
        schema = sb.toSchema();
        assertThat(schema.getNameForms()).isNotEmpty();
        assertThat(schema.getNameForms().size()).isEqualTo(2);
        assertThat(schema.getWarnings()).isEmpty();

        final Iterator<NameForm> i = schema.getNameForms().iterator();
        i.next(); // Jump the first element (== nf)
        final NameForm dolly = i.next();
        assertThat(dolly.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.36");

        assertThat(dolly.getNames().size()).isEqualTo(1);
        assertThat(dolly.getNames().get(0)).isEqualTo("Dolly");
        assertThat(dolly.getRequiredAttributes().size()).isEqualTo(1);
        assertThat(dolly.getRequiredAttributes().iterator().next().getOID()).isEqualTo("2.5.4.15");
        assertThat(dolly.getRequiredAttributes().iterator().next().getNameOrOID()).isEqualTo("businessCategory");
        assertThat(dolly.getOptionalAttributes()).isEmpty();
        assertThat(dolly.getExtraProperties()).isEmpty();
    }

    /**
     * Adds several name forms to the same schema builder.
     *
     * @throws SchemaException
     */
    @Test
    public final void testAddsSeveralFormsToSchemaBuilder() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.35")
                .description("Description of the new form")
                .names("MyNewForm")
                .structuralObjectClassOID("person")
                .extraProperties("X-ORIGIN", "NameFormTestCase", "Forgerock", "extra")
                .requiredAttributes("sn", "cn")
                .optionalAttributes("description", "uid")
                .addToSchema()
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.36")
                .description("Description of the second form")
                .names("SecondForm")
                .structuralObjectClassOID("organization")
                .extraProperties("X-ORIGIN", "NameFormTestCase2")
                .requiredAttributes("name")
                .optionalAttributes("owner")
                .addToSchema()
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.37")
                .description("Description of the third form")
                .names("ThirdForm")
                .structuralObjectClassOID("groupOfNames")
                .extraProperties("X-ORIGIN", "NameFormTestCase3", "ForgeRock")
                .requiredAttributes("sn", "l")
                .optionalAttributes("description", "uid")
                .description("Description of the third form")
                .addToSchema()
                // we overwritten the third name form.
            .buildNameForm("1.3.6.1.4.1.1466.115.121.1.37")
                .names("ThirdFormOverwritten")
                .structuralObjectClassOID("groupOfNames")
                .extraProperties("X-ORIGIN", "RFC 2252")
                .requiredAttributes("sn", "l")
                .optionalAttributes("description", "uid")
                .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameForms().size()).isEqualTo(3);
        assertThat(schema.getNameForm("MyNewForm").getOID()).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(schema.getNameForm("SecondForm").getOID()).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.36");
        // The third form is completely overwritten.
        assertThat(schema.getNameForm("ThirdFormOverwritten").getOID()).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.37");
        assertThat(schema.getNameForm("ThirdFormOverwritten").getDescription()).isEmpty();
        assertThat(schema.getNameForm("ThirdFormOverwritten").getExtraProperties().get("X-ORIGIN").get(0))
                .isEqualTo("RFC 2252");
    }
}
