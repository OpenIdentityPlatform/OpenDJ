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
 *      Copyright 2014 ForgeRock AS.
 *      Portions Copyright 2014 Manuel Gaupp
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
    public final void testCreatesANewSyntax() {
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
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(syntax.toString()).isEqualTo(
                "( 1.9.1.2.3 DESC 'Security Label' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )");
        assertThat(syntax.isHumanReadable()).isTrue();
        assertThat(syntax.isBEREncodingRequired()).isFalse();
    }

    /**
     * Tests that unrecognized syntaxes are automatically substituted with the
     * default syntax during building.
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
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo(
                "octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * Tests that unrecognized syntaxes are automatically substituted with the
     * default syntax and matching rule.
     */
    @Test
    public final void testDefaultSyntaxSubstitution() {
        final Syntax syntax = Schema.getCoreSchema().getSyntax("1.2.3.4.5");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEmpty();
        // Dynamically created syntaxes include the X-SUBST extension.
        assertThat(syntax.getExtraProperties().get("X-SUBST").get(0)).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.40");
        assertThat(syntax.getApproximateMatchingRule()).isNull();
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo(
                "octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesNotAllowEmptyOid() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("")
            .description("Security Label")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema();
        // @formatter:on
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesNotAllowNullOid() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax((String) null)
            .description("Security Label")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema();
        // @formatter:on
    }

    /**
     * When syntax is missing, the default one is set. Actual default is OctetString.(case match)
     */
    @Test
    public final void testBuilderAllowsNullSyntax() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .implementation(null)
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
        assertThat(syntax.toString()).isEqualTo(
                "( 1.9.1.2.3 DESC 'Security Label' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )");
    }

    /**
     * When syntax is missing, the default one is set. Actual default is OctetString.(case match)
     */
    @Test
    public final void testBuilderAllowsNoSyntax() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
        assertThat(syntax.toString()).isEqualTo(
                "( 1.9.1.2.3 DESC 'Security Label' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )");
    }

    /**
     * When syntax is missing, the default one is set. Actual default is set to directory string
     * (1.3.6.1.4.1.1466.115.121.1.15) - matchingRules of caseIgnoreMatch and caseIgnoreSubstringsMatch.
     */
    @Test
    public final void testBuilderAllowsNoSyntaxCaseWhereDefaultSyntaxIsChanged() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .setOption(DEFAULT_SYNTAX_OID, "1.3.6.1.4.1.1466.115.121.1.15")
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("It will be substituted by the default syntax");
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(syntax.toString()).isEqualTo(
                "( 1.9.1.2.3 DESC 'Security Label' X-ENUM ( 'top-secret' 'secret' 'confidential' ) )");
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsNoDescription() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new OctetStringSyntaxImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsNullDescription() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
            .description(null)
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new OctetStringSyntaxImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsEmptyDescription() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
            .description("")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new OctetStringSyntaxImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
    }

    /**
     * Extra properties is not a mandatory field.
     */
    @Test
    public final void testBuilderAllowsNoExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .implementation(new OctetStringSyntaxImpl())
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().isEmpty()).isTrue();
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
    }

    /**
     * Extra properties set to null is not allowed.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testBuilderDoesNotAllowNullExtraProperties() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
                .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties(null)
                .implementation(new OctetStringSyntaxImpl())
                .addToSchema()
            .toSchema();
        // @formatter:on
    }

    /**
     * Removes all the extra properties.
     */
    @Test
    public final void testBuilderRemoveExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .implementation(new OctetStringSyntaxImpl())
                .removeAllExtraProperties()
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().isEmpty()).isTrue();
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
    }

    /**
     * Removes specified extra properties.
     */
    @Test
    public final void testBuilderRemoveSpecifiedExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildSyntax("1.9.1.2.3")
                .description("Security Label")
                .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
                .extraProperties("X-ORIGIN", "Sam Carter")
                .implementation(new OctetStringSyntaxImpl())
                .removeExtraProperty("X-ENUM", "top-secret")
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax).isNotNull();
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().isEmpty()).isFalse();
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(2);
        assertThat(syntax.getExtraProperties().get("X-ORIGIN").size()).isEqualTo(1);
        assertThat(syntax.getApproximateMatchingRule()).isEqualTo(null);
        assertThat(syntax.getEqualityMatchingRule().getNameOrOID()).isEqualTo("octetStringMatch");
        assertThat(syntax.getOrderingMatchingRule().getNameOrOID()).isEqualTo("octetStringOrderingMatch");
        assertThat(syntax.getSubstringMatchingRule()).isEqualTo(null);
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
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        // @formatter:off
        final Syntax.Builder syntaxBuilder = new Syntax.Builder("1.9.1.2.3", sb);
        syntaxBuilder.description("Security Label")
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema();
        // @formatter:on

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final Syntax syntax = schema.getSyntax("1.9.1.2.3");
        assertThat(syntax.getDescription()).isEqualTo("Security Label");
        assertThat(syntax.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);

        // @formatter:off
        sb.buildSyntax(syntax)
            .description("Security Label II")
            .extraProperties("X-ENUM", "private")
            .addToSchemaOverwrite();
        // @formatter:on

        schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final Syntax dolly = schema.getSyntax("1.9.1.2.3");
        assertThat(dolly.getDescription()).isEqualTo("Security Label II");
        assertThat(dolly.getExtraProperties().get("X-ENUM").size()).isEqualTo(4);
        assertThat(dolly.getApproximateMatchingRule().getNameOrOID()).isEqualTo("ds-mr-double-metaphone-approx");
        assertThat(dolly.getEqualityMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreMatch");
        assertThat(dolly.getOrderingMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(dolly.getSubstringMatchingRule().getNameOrOID()).isEqualTo("caseIgnoreSubstringsMatch");
        assertThat(dolly.toString()).isEqualTo(
                "( 1.9.1.2.3 DESC 'Security Label II' X-ENUM ( 'top-secret' 'secret' 'confidential' 'private' ) )");
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
            .extraProperties("X-ENUM", "top-secret", "secret", "confidential")
            .implementation(new DirectoryStringSyntaxImpl())
            .addToSchema()
            .buildSyntax("1.9.1.2.4")
            .description("Security Label II")
            .extraProperties("X-ENUM", "private")
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
        assertThat(s1.getExtraProperties().get("X-ENUM").size()).isEqualTo(3);

        // Second
        final Syntax s2 = schema.getSyntax("1.9.1.2.4");
        assertThat(s2.getDescription()).isEqualTo("Security Label II");
        assertThat(s2.getExtraProperties().get("X-ENUM").size()).isEqualTo(1);
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
