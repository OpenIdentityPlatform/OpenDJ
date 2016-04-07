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
 * Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2014 Manuel Gaupp
 */

package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.schema.Syntax.Builder;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

/**
 * This class tests the Syntax class.
 */
@SuppressWarnings("javadoc")
public class SyntaxTestCase extends AbstractSchemaTestCase {

    @Test
    public final void testBuilderCreatesCustomSyntax() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-TEST", "1", "2", "3")
                .implementation(new DirectoryStringSyntaxImpl())
                .addToSchema()
            .toSchema();
        // @formatter:on
        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-TEST")).hasSize(3);
        assertThat(syntax.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(syntax.toString()).isEqualTo("( 1.9.1.2.3 DESC 'Security Label' X-TEST ( '1' '2' '3' ) )");
        assertThat(syntax.isHumanReadable()).isTrue();
        assertThat(syntax.isBEREncodingRequired()).isFalse();
    }

    /**
     * Tests that unrecognized syntaxes are automatically substituted with the default syntax during building.
     */
    @Test
    public final void testBuilderSubstitutesUnknownSyntaxWithDefaultSyntax() {
        final SchemaBuilder sb = new SchemaBuilder(Schema.getCoreSchema());
        sb.buildSyntax("1.2.3.4.5").addToSchema();
        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).hasSize(1);
        final Syntax syntax = schema.getSyntax("1.2.3.4.5");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEmpty();
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * Tests that unrecognized syntaxes are automatically substituted with the default syntax and matching rule.
     */
    @Test
    public final void testDefaultSyntaxSubstitution() {
        final Syntax syntax = Schema.getCoreSchema().getSyntax("1.2.3.4.5");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEmpty();
        // Dynamically created syntaxes include the X-SUBST extension.
        assertThat(syntax.getExtraProperties().get("X-SUBST").get(0)).isEqualTo("1.3.6.1.4.1.1466.115.121.1.40");
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesNotAllowEmptyOid() {
        new SchemaBuilder(Schema.getCoreSchema()).buildSyntax("").addToSchema();
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesNotAllowNullOid() {
        new SchemaBuilder(Schema.getCoreSchema()).buildSyntax((String) null).addToSchema();
    }

    /**
     * When syntax is missing, the default one is set. Actual default is OctetString.(case match)
     */
    @Test
    public final void testBuilderAllowsNullSyntax() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").implementation(null).addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
        assertThat(syntax.toString()).isEqualTo("( 1.9.1.2.3 )");
    }

    /**
     * When syntax is missing, the default one is set. Actual default is OctetString.(case match)
     */
    @Test
    public final void testBuilderAllowsNoSyntax() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
        assertThat(syntax.toString()).isEqualTo("( 1.9.1.2.3 )");
    }

    /**
     * When syntax is missing, the default one is set. Actual default is set to directory string
     * (1.3.6.1.4.1.1466.115.121.1.15) - matchingRules of caseIgnoreMatch and caseIgnoreSubstringsMatch.
     */
    @Test
    public final void testBuilderAllowsNoSyntaxCaseWhereDefaultSyntaxIsChanged() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .setOption(DEFAULT_SYNTAX_OID, "1.3.6.1.4.1.1466.115.121.1.15")
                .buildSyntax("1.9.1.2.3").addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(syntax.toString()).isEqualTo("( 1.9.1.2.3 )");
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsNoDescription() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").addToSchema()
                .toSchema();

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsNullDescription() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").description(null).addToSchema()
                .toSchema();

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsEmptyDescription() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").description("").addToSchema()
                .toSchema();

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
    }

    /**
     * Extra properties is not a mandatory field.
     */
    @Test
    public final void testBuilderAllowsNoExtraProperties() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3").addToSchema()
                .toSchema();

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getExtraProperties().isEmpty()).isTrue();
    }

    /**
     * Extra properties set to null is not allowed.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testBuilderDoesNotAllowNullExtraProperties() {
        new SchemaBuilder(Schema.getCoreSchema()).buildSyntax("1.9.1.2.3").extraProperties(null);
    }

    /**
     * Removes all the extra properties.
     */
    @Test
    public final void testBuilderRemoveExtraProperties() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3")
                .extraProperties("X-ENUM", "1", "2", "3").removeAllExtraProperties().addToSchema()
                .toSchema();

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getExtraProperties().isEmpty()).isTrue();
    }

    /**
     * Removes specified extra properties.
     */
    @Test
    public final void testBuilderRemoveSpecifiedExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .extraProperties("X-ORIGIN", "Sam Carter")
                .removeExtraProperty("X-ENUM", "top-secret")
                .addToSchema()
            .toSchema();
        // @formatter:on

        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getExtraProperties().isEmpty()).isFalse();
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(2);
        assertThat(syntax.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(1);
    }

    /**
     * Sets a syntax using a string definition.
     */
    @Test
    public final void testAddingBERSyntaxDefinition() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        final String definition = "( 1.3.6.1.4.1.1466.115.121.1.8 DESC 'X.509 Certificate' )";

        sb.addSyntax(definition, true);

        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.3.6.1.4.1.1466.115.121.1.8");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("X.509 Certificate");
        assertThat(syntax.getExtraProperties().isEmpty()).isTrue();
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("certificateExactMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
        assertThat(syntax.isBEREncodingRequired()).isTrue();
        assertThat(syntax.isHumanReadable()).isFalse();
    }

    /**
     * Sets a syntax using a string definition.
     */
    @Test
    public final void testAddingASyntaxDefinitionStringOverride() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        final String definition = "( 1.3.6.1.4.1.4203.1.1.2 DESC 'Authentication Password Syntaxe'"
                + " X-ORIGIN 'RFC 4512' )";

        sb.addSyntax(definition, true);

        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.3.6.1.4.1.4203.1.1.2");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Authentication Password Syntaxe");
        assertThat(syntax.getExtraProperties().isEmpty()).isFalse();
        assertThat(syntax.getExtraProperties().get("X-ORIGIN").get(0)).isEqualTo("RFC 4512");
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("authPasswordExactMatch");
        assertThat(syntax.getOrderingMatchingRule()).isNull();
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * Sets a syntax using a string definition.
     */
    @Test
    public final void testAddingUnknownSyntaxDefinitionString() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        final String definition = "( 1.3.6.1.4.1.4203.1.1.9999 DESC 'Custom Authentication Password'"
                + " X-ORIGIN 'None' )";

        sb.addSyntax(definition, false);

        final Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isNotEmpty();
        final Syntax syntax = schema.getSyntax("1.3.6.1.4.1.4203.1.1.9999");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Custom Authentication Password");
        assertThat(syntax.getExtraProperties().isEmpty()).isFalse();
        assertThat(syntax.getExtraProperties().get("X-ORIGIN").get(0)).isEqualTo("None");
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * Duplicates a syntax.
     */
    @Test
    public final void testBuilderDuplicatesExistingSyntax() {
        final Schema schema1 = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.2.3.4.5.6").description("v1").addToSchema()
                .toSchema();

        final Syntax syntax1 = schema1.getSyntax("1.2.3.4.5.6");
        assertThat(syntax1.getDescription()).isEqualTo("v1");

        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax(syntax1).description("v2").addToSchema()
                .toSchema();

        final Syntax syntax2 = schema2.getSyntax("1.2.3.4.5.6");
        assertThat(syntax2.getDescription()).isEqualTo("v2");
        assertThat(syntax2.toString()).isEqualTo("( 1.2.3.4.5.6 DESC 'v2' )");
    }

    /**
     * Another duplicated syntax example.
     */
    @Test
    public final void testBuilderDuplicatesSyntax() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final Builder nfb = new Builder("1.2.3.4.5.9999", sb);
        nfb.description("Description of the new syntax")
            .extraProperties("X-ORIGIN", "SyntaxCheckingTestCase")
            .addToSchema();

        Schema schema = sb.toSchema();
        assertThat(schema.getSyntaxes()).isNotEmpty();
        final Syntax syntax = schema.getSyntax("1.2.3.4.5.9999");
        assertThat(syntax.getDescription()).isEqualTo("Description of the new syntax");

        sb.buildSyntax(syntax)
            .oid("1.2.3.4.5.99996")
            .extraProperties("X-ORIGIN", "Unknown")
            .addToSchema();
        schema = sb.toSchema();
        assertThat(schema.getSyntaxes()).isNotEmpty();
        // The duplicated syntax.
        final Syntax dolly = schema.getSyntax("1.2.3.4.5.99996");
        assertThat(dolly.getDescription()).isEqualTo("Description of the new syntax");
        assertThat(dolly.getExtraProperties().size()).isEqualTo(1);
        assertThat(dolly.getExtraProperties().get("X-ORIGIN").get(0)).isEqualTo("SyntaxCheckingTestCase");
        assertThat(dolly.getExtraProperties().get("X-ORIGIN").get(1)).isEqualTo("Unknown");
        assertThat(dolly.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(2);

        // The original hasn't changed.
        final Syntax originalSyntax = schema.getSyntax("1.2.3.4.5.9999");
        assertThat(originalSyntax.getDescription()).isEqualTo("Description of the new syntax");
        assertThat(originalSyntax.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(1);
    }

    /**
     * Equality between syntaxes.
     */
    @Test
    public final void testBuilderSyntaxesEqualsTrue() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3")
                    .description("Security Label")
                    .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                    .implementation(new DirectoryStringSyntaxImpl())
                    .addToSchema()
                .toSchema();
        // @formatter:on

        final Syntax syntax1 = schema.getSyntax("1.9.1.2.3");

        // @formatter:off
        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3")
                    .description("Security Label")
                    .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                    .implementation(new DirectoryStringSyntaxImpl())
                    .addToSchema()
                .toSchema();
        // @formatter:on
        final Syntax syntax2 = schema2.getSyntax("1.9.1.2.3");

        assertThat(syntax1).isEqualTo(syntax2);
    }

    /**
     * Equality between syntaxes.
     */
    @Test
    public final void testBuilderSyntaxesEqualsFalse() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3")
                    .description("Security Label")
                    .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                    .implementation(new DirectoryStringSyntaxImpl())
                    .addToSchema()
                .toSchema();
        // @formatter:on

        final Syntax syntax1 = schema.getSyntax("1.9.1.2.3");

        // @formatter:off
        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.4")
                    .description("Security Label")
                    .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                    .implementation(new DirectoryStringSyntaxImpl())
                    .addToSchema()
                .toSchema();
        // @formatter:on
        final Syntax syntax2 = schema2.getSyntax("1.9.1.2.4");

        assertThat(syntax1).isNotEqualTo(syntax2);
    }

    /**
     * Equality between builder and definition.
     */
    @Test
    public final void testBuilderEqualityReturnsTrueBetweenBuilderAndDefinition() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
            .description("Security Label")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax1 = schema.getSyntax("1.9.1.2.3");

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);

        final String definition =
                "( 1.9.1.2.3 DESC 'Security Label' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )";

        sb2.addSyntax(definition, false);
        final Syntax syntax2 = sb2.toSchema().getSyntax("1.9.1.2.3");
        assertThat(syntax1).isEqualTo(syntax2);
    }

    /**
     * Equality between builder and definition fails.
     */
    @Test
    public final void testBuilderEqualityReturnsFalseBetweenBuilderAndDefinition() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
            .description("Security Label")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax1 = schema.getSyntax("1.9.1.2.3");

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);
        final String definition =
                "( 1.9.1.2.4 DESC 'Security Label II' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )";

        sb2.addSyntax(definition, false);

        final Syntax syntax2 = sb2.toSchema().getSyntax("1.9.1.2.4");
        assertThat(syntax1).isNotEqualTo(syntax2);
    }

    /**
     * The builder allows to create chained syntaxes.
     */
    @Test
    public final void testBuilderCreatesSyntaxesUsingChainingMethods() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-TEST", "1", "2", "3")
                .implementation(new DirectoryStringSyntaxImpl())
                .addToSchema()
            .buildSyntax("1.9.1.2.4")
                .description("Security Label II")
                .extraProperties("X-TEST", "private")
                .addToSchema()
            .buildSyntax("non-implemented-syntax-oid")
                .description("Not Implemented in OpenDJ")
                .extraProperties("X-SUBST", "1.3.6.1.4.1.1466.115.121.1.15")
                .implementation(null)
                .addToSchema()
            .buildSyntax("1.3.6.1.4.1.4203.1.1.2")
                .description("Authentication Password Syntax")
                .extraProperties("X-ORIGIN", "RFC 4512")
                .implementation(new OctetStringSyntaxImpl())
                .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on


        // Warning should be found as the syntax implementation for s2 is not specified.
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getDefaultSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.40"); // OctetString OID.

        // First
        final Syntax s1 = schema.getSyntax("1.9.1.2.3");
        assertThat(s1.getDescription()).isEqualTo("Security Label");
        assertThat(s1.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(s1.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(s1.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(s1.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(s1.getExtraProperties().get("X-TEST")).hasSize(3);

        // Second
        final Syntax s2 = schema.getSyntax("1.9.1.2.4");
        assertThat(s2.getDescription()).isEqualTo("Security Label II");
        assertThat(s2.getExtraProperties().get("X-TEST")).hasSize(1);
        assertThat(s2.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(s2.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(s2.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(s2.getSubstringMatchingRule()).isEqualTo(null);

        // Third
        final Syntax s3 = schema.getSyntax("non-implemented-syntax-oid");
        assertThat(s3.getDescription()).isEqualTo("Not Implemented in OpenDJ");
        assertThat(s3.getExtraProperties().get("X-SUBST").size()).isEqualTo(1);
        // The default syntax is substitute as directory string.
        assertThat(s1.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(s1.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(s1.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(s1.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");

        // Last
        final Syntax s4 = schema.getSyntax("1.3.6.1.4.1.4203.1.1.2");
        assertThat(s4.getDescription()).isEqualTo("Authentication Password Syntax");
        assertThat(s4.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(s4.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(s4.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(s4.getSubstringMatchingRule()).isEqualTo(null);
        assertThat(s4.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(1);
    }
}
