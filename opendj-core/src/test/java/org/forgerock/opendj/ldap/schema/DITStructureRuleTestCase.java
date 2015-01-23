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

import org.testng.annotations.Test;

public class DITStructureRuleTestCase extends AbstractSchemaTestCase {

    private static final String NAME_FORM_TEST_OID = "1.2.3.4";

    /** Adds a valid DIT structure rule on the "" name form. */
    @Test
    public void testValidDITStructureRule() {
        final SchemaBuilder builder = getTestSchema()
               .buildDITStructureRule(42)
               .names("DIT structure rule test", "DIT structure rule for person")
               .nameForm(NAME_FORM_TEST_OID)
               .description("My DIT structure rule")
               .extraProperties("property name", "property value")
               .addToSchema();

        final Schema schema = builder.buildDITStructureRule(43)
               .names("DIT structure rule child test")
               .nameForm(NAME_FORM_TEST_OID)
               .superiorRules(42)
               .description("My DIT structure rule child")
               .extraProperties("property name", "property value")
               .addToSchema()
               .toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        final DITStructureRule sr = schema.getDITStructureRule(42);
        assertThat(sr).isNotNull();
        assertThat(sr.getRuleID()).isEqualTo(42);
        assertThat(sr.getNames()).containsOnly("DIT structure rule test", "DIT structure rule for person");
        assertThat(sr.getDescription()).isEqualTo("My DIT structure rule");
        assertThat(sr.getNameForm().getOID()).isEqualTo(NAME_FORM_TEST_OID);
        assertThat(sr.getExtraProperties()).includes(entry("property name", singletonList("property value")));
        assertThat(sr.getSuperiorRules()).isEmpty();
        assertThat(sr.isObsolete()).isFalse();

        final DITStructureRule srChild = schema.getDITStructureRule(43);
        assertThat(srChild).isNotNull();
        assertThat(srChild.getRuleID()).isEqualTo(43);
        assertThat(srChild.getNames()).containsOnly("DIT structure rule child test");
        assertThat(srChild.getDescription()).isEqualTo("My DIT structure rule child");
        assertThat(srChild.getNameForm().getOID()).isEqualTo(NAME_FORM_TEST_OID);
        assertThat(srChild.getExtraProperties()).includes(entry("property name", singletonList("property value")));
        assertThat(srChild.getSuperiorRules()).containsOnly(sr);
        assertThat(srChild.isObsolete()).isFalse();
    }

    @Test
    public void testCopyConstructor() throws Exception {
        final Schema schema = getTestSchema()
                .buildDITStructureRule(42)
                .names("DIT structure rule test")
                .nameForm(NAME_FORM_TEST_OID)
                .description("My DIT structure rule")
                .addToSchema()
                .toSchema();

        final Schema schemaCopy = getTestSchema()
                .buildDITStructureRule(schema.getDITStructureRule(42))
                .ruleID(43)
                .names("DIT structure rule test - copy")
                .addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schemaCopy.getWarnings()).isEmpty();

        final DITStructureRule srCopy = schemaCopy.getDITStructureRule(43);
        assertThat(srCopy).isNotNull();
        assertThat(srCopy.getRuleID()).isEqualTo(43);
        assertThat(srCopy.getNames()).containsOnly("DIT structure rule test", "DIT structure rule test - copy");
        assertThat(srCopy.getNameForm().getOID()).isEqualTo(NAME_FORM_TEST_OID);
        assertThat(srCopy.getDescription()).isEmpty();
        assertThat(srCopy.isObsolete()).isFalse();
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testBuilderDoesNotAllowOverwrite() throws Exception {
        final SchemaBuilder builder = getTestSchema()
                .buildDITStructureRule(42)
                .nameForm(NAME_FORM_TEST_OID)
                .addToSchema();

        builder.buildDITStructureRule(42)
               .nameForm(NAME_FORM_TEST_OID)
               .addToSchema()
               .toSchema();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBuilderDoesNotAllowNullNameForm() throws Exception {
        getTestSchema().buildDITStructureRule(42)
                       .nameForm(null)
                       .addToSchema();
    }

    @Test
    public void testBuilderRemoveAll() throws Exception {
        DITStructureRule.Builder srBuilder = getTestSchema()
                .buildDITStructureRule(42)
                .names("DIT structure rule test", "DIT structure rule for person")
                .nameForm(NAME_FORM_TEST_OID)
                .description("My DIT structure rule")
                .superiorRules(1, 2, 3, 4)
                .extraProperties("property name", "property value");

        Schema schema = srBuilder.removeAllNames()
                .removeAllSuperiorRules()
                .removeAllExtraProperties()
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        DITStructureRule sr = schema.getDITStructureRule(42);
        assertThat(sr.getNames()).isEmpty();
        assertThat(sr.getExtraProperties()).isEmpty();
        assertThat(sr.getSuperiorRules()).isEmpty();
    }

    @Test
    public void testBuilderRemove() throws Exception {
        DITStructureRule.Builder srBuilder = getTestSchema()
                .buildDITStructureRule(42)
                .names("DIT structure rule test", "should be removed")
                .nameForm(NAME_FORM_TEST_OID)
                .description("My DIT structure rule")
                .superiorRules(1)
                .extraProperties("property name", "property value");

        Schema schema = srBuilder.removeName("should be removed")
                .removeSuperiorRule(1)
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        DITStructureRule sr = schema.getDITStructureRule(42);
        assertThat(sr.getNames()).containsOnly("DIT structure rule test");
        assertThat(sr.getSuperiorRules()).isEmpty();
    }

    private SchemaBuilder getTestSchema() {
        return new SchemaBuilder(Schema.getCoreSchema())
                .buildNameForm(NAME_FORM_TEST_OID)
                .structuralObjectClassOID("person")
                .requiredAttributes("sn", "cn")
                .addToSchema();
    }
}
