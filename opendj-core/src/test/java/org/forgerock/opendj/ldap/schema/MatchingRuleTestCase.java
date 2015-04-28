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

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_AUTH_PASSWORD_EXACT_DESCRIPTION;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_AUTH_PASSWORD_EXACT_NAME;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_AUTH_PASSWORD_EXACT_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_AUTH_PASSWORD_OID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.testng.annotations.Test;

/**
 * This class tests the MatchingRule class. The matching rule builder can be only used with the schema builder.
 */
@SuppressWarnings("javadoc")
public class MatchingRuleTestCase extends AbstractSchemaTestCase {

    @Test
    public final void testCreatesBasicMatchingRule() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getDescription()).isEqualTo("Directory String");
        assertThat(mr.getDescription()).isEqualTo("An example of matching rule");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(mr.isObsolete()).isFalse();
    }

    @Test
    public final void testCreatesOverrideBasicMatchingRule() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID)
            .names(Collections.singletonList(EMR_AUTH_PASSWORD_EXACT_NAME))
            .description(EMR_AUTH_PASSWORD_EXACT_DESCRIPTION)
            .syntaxOID(SYNTAX_AUTH_PASSWORD_OID)
            .extraProperties("New extra propertie")
            .implementation(new AuthPasswordExactEqualityMatchingRuleImpl())
            .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID);
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isTrue();
        assertThat(mr.isObsolete()).isFalse();
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowEmptyOid() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule("")
            .names("ExampleMatch")
            .description("An example of matching rule")
            .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
            .extraProperties("LDAP Schema Update Procedures")
            .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
            .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on
    }

    /**
     * The builder requires an OID or throw an exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowNullOid() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule((String) null)
            .names("ExampleMatch")
            .description("An example of matching rule")
            .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
            .extraProperties("LDAP Schema Update Procedures")
            .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
            .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on
    }

    /**
     * When syntax is missing, the builder sends exception.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testBuilderDoesntAllowNullSyntax() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule("1.1.4.1")
            .names("ExampleMatch")
            .description("An example of matching rule")
            .extraProperties("LDAP Schema Update Procedures")
            .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
            .addToSchemaOverwrite()
            .toSchema();
        // @formatter:on
    }

    /**
     * Matching rule name is optional.
     */
    @Test
    public final void testBuilderAllowsEmptyName() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule("1.1.4.1")
            .description("An example of matching rule")
            .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
            .extraProperties("LDAP Schema Update Procedures")
            .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
            .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("1.1.4.1");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(mr.getNames().size()).isEqualTo(0);
        assertThat(mr.getNameOrOID()).isEqualTo("1.1.4.1");
    }

    /**
     * Multiple names can be set to the matching rule.
     */
    @Test
    public final void testBuilderAllowsMultipleNames() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("my new matching rule")
                .names("maching rule test")
                .names("exampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("1.1.4.1");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(mr.getNames().size()).isEqualTo(3);
        assertThat(mr.hasName("my new matching rule")).isTrue();
        assertThat(mr.hasName("maching rule test")).isTrue();
        assertThat(mr.hasName("exampleMatch")).isTrue();
    }

    /**
     * Name in optional for a matching rule. (RFC 4512)
     */
    @Test
    public final void testBuilderRemoveNames() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("my new matching rule")
                .names("maching rule test")
                .names("exampleMatch")
                .removeAllNames()
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("1.1.4.1");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(mr.getNames().size()).isEqualTo(0);
        assertThat(mr.getNameOrOID()).isEqualTo("1.1.4.1");
    }

    /**
     * The builder allows to remove selected name.
     */
    @Test
    public final void testBuilderRemoveSelectedName() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("my new matching rule")
                .names("maching rule test")
                .names("exampleMatch")
                .removeName("maching rule test")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("1.1.4.1");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(mr.getNames().size()).isEqualTo(2);
        assertThat(mr.hasName("my new matching rule")).isTrue();
        assertThat(mr.hasName("exampleMatch")).isTrue();
    }

    /**
     * The builder allows a missing description.
     */
    @Test
    public final void testBuilderAllowsNoDescription() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchemaOverwrite()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getDescription()).isEmpty();
        assertThat(mr.getDescription()).isEqualTo("");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
    }

    /**
     * The builder allows empty description.
     */
    @Test
    public final void testBuilderAllowsEmptyDescription() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchemaOverwrite()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getDescription()).isEmpty();
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(mr.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
    }

    /**
     * Extra properties is not a mandatory field.
     */
    @Test
    public final void testBuilderAllowsNoExtraProperties() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("Example match description")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchemaOverwrite()
                .toSchema();
        // @formatter:on
        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getDescription()).isEqualTo("Example match description");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties()).isEmpty();
    }

    /**
     * Extra properties set to null is not allowed.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testBuilderDoesntAllowNullExtraProperties() {
        // @formatter:off
        new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("Example match description")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15")
                .extraProperties(null)
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchemaOverwrite()
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
                .buildMatchingRule("1.1.4.1")
                .names("exampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .removeAllExtraProperties()
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("1.1.4.1");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(mr.getExtraProperties()).isEmpty();
        assertThat(mr.getNames().size()).isEqualTo(1);
        assertThat(mr.getNames().get(0)).isEqualTo("exampleMatch");
    }

    /**
     * If the implementation is not set, the schema will use the default matching rule for this one.
     */
    @Test
    public final void testBuilderAllowsNoImplementation() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("Example match description")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .addToSchemaOverwrite()
                .toSchema();
        // @formatter:on
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().contains("The default matching rule \"2.5.13.17\" will be used instead"));
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getDescription()).isEqualTo("Example match description");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
    }

    /**
     * If the implementation is null, the schema will use the default matching rule for this one.
     */
    @Test
    public final void testBuilderAllowsNullImplementation() {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("Example match description")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(null)
                .addToSchemaOverwrite()
                .toSchema();
        // @formatter:on
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(
                schema.getWarnings().toString()
                        .contains("The default matching rule \"2.5.13.17\" will be used instead")).isTrue();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr).isNotNull();
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.getDescription()).isEqualTo("Example match description");
        assertThat(mr.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
    }

    /**
     * Sets a matching rule using a string definition.
     */
    @Test
    public final void testAddingAMatchingRuleDefinitionStringNoOverride() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        final String definition = "( 1.1.4.1 NAME 'ExampleMatch' DESC 'An example of"
                + " Matching Rule' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )";

        sb.addMatchingRule(definition, false);

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(
                schema.getWarnings().toString()
                        .contains("The default matching rule \"2.5.13.17\" will be used instead")).isTrue();
        assertThat(schema.getMatchingRules()).isNotEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr.toString()).isEqualTo(definition);
        assertThat(mr.isObsolete()).isFalse();
    }

    /**
     * Sets a matching rule using a string definition.
     */
    @Test
    public final void testAddingAMatchingRuleDefinitionStringOverride() {
        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);

        final String definition = "( 2.5.13.0 NAME 'objectIdentifierMatch'"
                + " OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.38 )";

        sb.addMatchingRule(definition, true);

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getMatchingRules()).isNotEmpty();
        final MatchingRule mr = schema.getMatchingRule("objectIdentifierMatch");
        assertThat(mr.getOID()).isEqualTo("2.5.13.0");
        assertThat(mr.toString()).isEqualTo(definition);
        assertThat(mr.isObsolete()).isTrue();
    }


    /**
     * Duplicates an existing matching rule.
     */
    @Test
    public final void testDuplicatesExistingMatchingRule() {

        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final MatchingRule.Builder nfb = new MatchingRule.Builder("1.1.4.1", sb);
        nfb.description("This is a new matching rule")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .extraProperties("X-ORIGIN", "NO RFC")
                .implementation(new BooleanEqualityMatchingRuleImpl())
                .addToSchemaOverwrite();
        // @formatter:on

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr.getOID()).isEqualTo("1.1.4.1");

        // @formatter:off
        sb.buildMatchingRule(mr)
            .names("Dolly")
            .oid("2.5.13.0.1")
            .obsolete(true)
            .addToSchemaOverwrite();
        // @formatter:on

        schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule dolly = schema.getMatchingRule("Dolly");
        assertThat(dolly.getOID()).isEqualTo("2.5.13.0.1");
        assertThat(dolly.getSyntax().getDescription()).isEqualTo("Directory String");
        assertThat(dolly.getDescription()).isEqualTo("An example of matching rule");
        assertThat(dolly.getSyntax().getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(dolly.getExtraProperties().containsKey("New extra propertie")).isFalse();
        assertThat(dolly.getExtraProperties().containsKey("LDAP Schema Update Procedures")).isTrue();
        assertThat(dolly.getExtraProperties().containsKey("X-ORIGIN")).isTrue();
        assertThat(dolly.isObsolete()).isTrue();
    }

    /**
     * Equality between matching rules.
     */
    @Test
    public final void testMatchingRuleEqualsTrue() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        final MatchingRule mr1 = schema.getMatchingRule("ExampleMatch");

        // @formatter:off
        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("Second")
                .description("A second example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on
        final MatchingRule mr2 = schema2.getMatchingRule("Second");

        assertThat(mr2.equals(mr1)).isTrue();
    }

    /**
     * Equality between matching rules fails.
     */
    @Test
    public final void testMatchingRuleEqualsFalse() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        final MatchingRule mr1 = schema.getMatchingRule("ExampleMatch");

        // @formatter:off
        final Schema schema2 = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.2")
                .names("Second")
                .description("A second example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on
        final MatchingRule mr2 = schema2.getMatchingRule("Second");

        assertThat(mr2.equals(mr1)).isFalse();
    }

    /**
     * Verifies the builder definition.
     */
    @Test
    public final void testVerifyMatchingRuleDefinition() {

        final SchemaBuilder sb = new SchemaBuilder();
        sb.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final MatchingRule.Builder nfb = new MatchingRule.Builder("1.1.4.1", sb);
        nfb.description("This is a new matching rule")
                .names("ExampleMatch")
                .names("MyExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures", "RFC 2252")
                .extraProperties("X-ORIGIN", "NONE")
                .obsolete(true)
                .implementation(new BooleanEqualityMatchingRuleImpl())
                .addToSchemaOverwrite();
        // @formatter:on

        Schema schema = sb.toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final String definition = "( 1.1.4.1 NAME ( 'ExampleMatch' 'MyExampleMatch' ) "
                + "DESC 'An example of matching rule' OBSOLETE " + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 "
                + "LDAP Schema Update Procedures 'RFC 2252' X-ORIGIN 'NONE' )";

        final MatchingRule mr = schema.getMatchingRule("ExampleMatch");
        assertThat(mr.toString()).isEqualTo(definition);
    }

    /**
     * Equality between builder and definition.
     */
    @Test
    public final void testMatchingRuleEqualityReturnsTrueBetweenBuilderAndDefinition() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr1 = schema.getMatchingRule("ExampleMatch");

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final String definition = "( 1.1.4.1 NAME 'ExampleMatch2' DESC 'An example of"
                + " Matching Rule' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )";

        sb2.addMatchingRule(definition, false);
        // @formatter:on
        final MatchingRule mr2 = sb2.toSchema().getMatchingRule("ExampleMatch2");
        assertThat(mr1.equals(mr2)).isTrue();
    }

    /**
     * Equality between builder and definition fails.
     */
    @Test
    public final void testMatchingRuleEqualityReturnsTrueBetweenBuilderAndDefinitionFails() {

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .extraProperties("LDAP Schema Update Procedures")
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .addToSchema()
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRule mr1 = schema.getMatchingRule("ExampleMatch");

        final SchemaBuilder sb2 = new SchemaBuilder();
        sb2.addSchema(Schema.getCoreSchema(), false);
        // @formatter:off
        final String definition = "( 1.1.4.2 NAME 'ExampleMatch2' DESC 'An example of"
                + " Matching Rule' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )";

        sb2.addMatchingRule(definition, false);
        // @formatter:on
        final MatchingRule mr2 = sb2.toSchema().getMatchingRule("ExampleMatch2");
        assertThat(mr1.equals(mr2)).isFalse();
    }

    /**
     * The builder allows to create chained matching rules.
     */
    @Test
    public final void testCreatesMatchingRulesUsingChainingMethods() {
        final Map<String, List<String>> extraProperties = new TreeMap<>();
        final List<String> extra = new ArrayList<>();
        extra.add("Custom");
        extraProperties.put("X-ORIGIN", extra);

        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
            .buildMatchingRule("1.1.4.1")
                .names("ExampleMatch")
                .description("An example of matching rule")
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15") // DirectoryStringSyntax OID.
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
                .extraProperties(extraProperties)
                .addToSchema()
            .buildMatchingRule("1.1.4.9999")
                .names("SecondExampleMatch")
                .description("Another example of matching rule")
                .extraProperties(extraProperties)
                .syntaxOID("1.3.6.1.4.1.1466.115.121.1.15")
                .implementation(new BooleanEqualityMatchingRuleImpl())
                .addToSchema()
            .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();

        // First
        final MatchingRule mr1 = schema.getMatchingRule("ExampleMatch");
        assertThat(mr1.getOID()).isEqualTo("1.1.4.1");
        assertThat(mr1.getDescription()).isEqualTo("An example of matching rule");

        // Second
        final MatchingRule mr2 = schema.getMatchingRule("SecondExampleMatch");
        assertThat(mr2.getOID()).isEqualTo("1.1.4.9999");
        assertThat(mr2.getDescription()).isEqualTo("Another example of matching rule");

        assertThat(mr1.getExtraProperties()).isEqualTo(mr2.getExtraProperties());
    }
}
