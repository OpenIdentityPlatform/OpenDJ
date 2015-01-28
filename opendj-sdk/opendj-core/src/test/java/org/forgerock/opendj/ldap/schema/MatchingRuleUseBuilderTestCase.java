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
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static java.util.Collections.*;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.MapAssert.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.testng.annotations.Test;

public class MatchingRuleUseBuilderTestCase extends AbstractSchemaTestCase {

    @Test
    public void testValidMatchingRuleUse() {
        final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse(EMR_CASE_EXACT_OID)
                .names("Matching rule use test")
                .description("Matching rule use description")
                .attributes("2.5.4.40", "2.5.4.52", "2.5.4.53")
                .extraProperties("property name", "property value")
                .addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        final MatchingRuleUse mru = schema.getMatchingRuleUse(EMR_CASE_EXACT_OID);
        assertThat(mru).isNotNull();
        assertThat(mru.getMatchingRuleOID()).isEqualTo(EMR_CASE_EXACT_OID);
        assertThat(mru.getNames()).containsOnly("Matching rule use test");
        assertThat(mru.getDescription()).isEqualTo("Matching rule use description");
        assertThat(mru.getAttributes()).containsOnly(schema.getAttributeType("2.5.4.40"),
                                                     schema.getAttributeType("2.5.4.52"),
                                                     schema.getAttributeType("2.5.4.53"));
        assertThat(mru.getExtraProperties()).includes(entry("property name", singletonList("property value")));
        assertThat(mru.isObsolete()).isFalse();
    }

    @Test
    public void testCopyConstructor() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse(EMR_BIT_STRING_OID)
                .description("Matching rule use description")
                .names("Matching rule use test")
                .attributes("2.5.4.40")
                .extraProperties("property name", "property value")
                .addToSchema();
        final Schema schema = builder.toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final Schema schemaCopy = builder.buildMatchingRuleUse(schema.getMatchingRuleUse(EMR_BIT_STRING_OID))
                .oid(EMR_OCTET_STRING_OID)
                .names("Matching rule use test copy")
                .attributes("2.5.4.53")
                .addToSchema()
                .toSchema();
        assertThat(schemaCopy.getWarnings()).isEmpty();

        final MatchingRuleUse mru = schemaCopy.getMatchingRuleUse(EMR_OCTET_STRING_OID);
        assertThat(mru).isNotNull();
        assertThat(mru.getMatchingRuleOID()).isEqualTo(EMR_OCTET_STRING_OID);
        assertThat(mru.getNames()).containsOnly("Matching rule use test", "Matching rule use test copy");
        assertThat(mru.getDescription()).isEqualTo("Matching rule use description");
        assertThat(mru.getAttributes()).containsOnly(schema.getAttributeType("2.5.4.40"),
                                                     schema.getAttributeType("2.5.4.53"));
        assertThat(mru.getExtraProperties()).includes(entry("property name", singletonList("property value")));
        assertThat(mru.isObsolete()).isFalse();
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testBuilderDoesNotAllowOverwrite() throws Exception {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse(EMR_BIT_STRING_OID)
                .names("Matching rule use test")
                .attributes("2.5.4.40")
                .addToSchema();

        builder.buildMatchingRuleUse(EMR_BIT_STRING_OID)
               .addToSchema()
               .toSchema();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBuilderDoesNotAllowNullMatchingRuleOID() throws Exception {
        new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse((String) null)
                .addToSchema();
    }

    @Test
    public void testBuilderRemoveAll() throws Exception {
        final MatchingRuleUse.Builder builder = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse(EMR_BIT_STRING_OID)
                .description("Matching rule use description")
                .names("Matching rule use test")
                .attributes("2.5.4.40", "2.5.4.52")
                .extraProperties("property name", "property value");

        final Schema schema = builder.removeAllNames()
                .removeAllAttributes()
                .removeAllExtraProperties()
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final MatchingRuleUse mru = schema.getMatchingRuleUse(EMR_BIT_STRING_OID);
        assertThat(mru.getNames()).isEmpty();
        assertThat(mru.getAttributes()).isEmpty();
        assertThat(mru.getExtraProperties()).isEmpty();
    }

    @Test
    public void testBuilderRemove() throws Exception {
        final MatchingRuleUse.Builder builder = new SchemaBuilder(Schema.getCoreSchema())
                .buildMatchingRuleUse(EMR_OCTET_STRING_OID)
                .description("Matching rule use description")
                .names("Matching rule use test", "I should not be in the schema")
                .attributes("2.5.4.52", "I should not be in the schema")
                .extraProperties("property name", "property value");

        final Schema schema = builder.removeName("I should not be in the schema")
                .removeAttribute("I should not be in the schema")
                .removeExtraProperty("property name")
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final MatchingRuleUse mru = schema.getMatchingRuleUse(EMR_OCTET_STRING_OID);
        assertThat(mru.getNames()).containsOnly("Matching rule use test");
        assertThat(mru.getAttributes()).containsOnly(schema.getAttributeType("2.5.4.52"));
        assertThat(mru.getExtraProperties()).isEmpty();
    }

}
