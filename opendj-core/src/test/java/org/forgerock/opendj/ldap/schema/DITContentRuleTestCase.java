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
 *      Copyright 2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static java.util.Collections.*;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;

import java.util.Collections;

import org.testng.annotations.Test;

public class DITContentRuleTestCase extends AbstractSchemaTestCase {

    /** Adds a DIT content rule on "device" structural object class. */
    @Test
    public void testValidDITContentRule() throws Exception {
        final Schema schema = new SchemaBuilder(getCoreSchema())
                .buildDITContentRule("2.5.6.14")
                .names(singletonList("devicecontentrule"))
                .description("Content rule desc")
                .auxiliaryObjectClasses(Collections.<String> emptyList())
                .optionalAttributes(Collections.<String> emptyList())
                .prohibitedAttributes(singletonList("serialNumber"))
                .requiredAttributes(singletonList("owner"))
                .addToSchema()
                .toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        final DITContentRule cr = schema.getDITContentRule("devicecontentrule");
        assertThat(cr).isNotNull();
        assertThat(cr.getStructuralClassOID()).isEqualTo("2.5.6.14");
        assertThat(cr.getNames()).containsOnly("devicecontentrule");
        assertThat(cr.getAuxiliaryClasses()).hasSize(0);
        assertThat(cr.getOptionalAttributes()).hasSize(0);
        assertThat(cr.getProhibitedAttributes()).hasSize(1);
        assertThat(cr.getRequiredAttributes()).hasSize(1);
        assertThat(cr.getDescription()).isEqualTo("Content rule desc");
        assertThat(cr.isObsolete()).isFalse();
    }

    /** Adds a DIT content rule on "organization" object class and then uses it to create another one. **/
    @Test
    public void testCopyConstructor() throws Exception {
        final Schema schema = new SchemaBuilder(getCoreSchema())
                .buildDITContentRule("2.5.6.4")
                .names(singletonList("organizationcontentrule"))
                .description("Content rule desc")
                .auxiliaryObjectClasses(singletonList("2.5.6.22"))
                .optionalAttributes(singletonList("searchGuide"))
                .prohibitedAttributes(singletonList("postOfficeBox"))
                .requiredAttributes(singletonList("telephoneNumber"))
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final Schema schemaCopy = new SchemaBuilder(getCoreSchema())
                .buildDITContentRule(schema.getDITContentRule("organizationcontentrule"))
                .names("organizationcontentrule-copy")
                .addToSchema()
                .toSchema();
        assertThat(schemaCopy.getWarnings()).isEmpty();

        final DITContentRule crCopy = schemaCopy.getDITContentRule("organizationcontentrule-copy");
        assertThat(crCopy).isNotNull();
        assertThat(crCopy.getStructuralClassOID()).isEqualTo("2.5.6.4");
        assertThat(crCopy.getNames()).containsOnly("organizationcontentrule", "organizationcontentrule-copy");
        assertThat(crCopy.getAuxiliaryClasses()).hasSize(1);
        assertThat(crCopy.getOptionalAttributes()).hasSize(1);
        assertThat(crCopy.getProhibitedAttributes()).hasSize(1);
        assertThat(crCopy.getRequiredAttributes()).hasSize(1);
        assertThat(crCopy.getDescription()).isEqualTo("Content rule desc");
        assertThat(crCopy.isObsolete()).isFalse();
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testBuilderDoesNotAllowOverwrite() throws Exception {
        final SchemaBuilder schemaBuilder = new SchemaBuilder(getCoreSchema())
                                           .buildDITContentRule("2.5.6.9")
                                           .addToSchema();
        schemaBuilder.buildDITContentRule("2.5.6.9")
                    .addToSchema();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBuilderDoesNotAllowNullStructuralOCOID() throws Exception {
        new SchemaBuilder(getCoreSchema())
                .buildDITContentRule((String) null)
                .addToSchema();
    }

    @Test
    public void testBuilderRemoveAll() throws Exception {
        DITContentRule.Builder crBuilder = new SchemaBuilder(getCoreSchema())
                .buildDITContentRule("2.5.6.1")
                .names(singletonList("shouldBeRemoved"))
                .description("My content rule")
                .auxiliaryObjectClasses(singletonList("shouldBeRemoved"))
                .optionalAttributes(singletonList("shouldBeRemoved"))
                .prohibitedAttributes(singletonList("shouldBeRemoved"))
                .requiredAttributes(singletonList("shouldBeRemoved"));

        Schema schema = crBuilder.removeAllNames()
                .removeAllAuxiliaryObjectClasses()
                .removeAllOptionalAttributes()
                .removeAllProhibitedAttributes()
                .removeAllRequiredAttributes()
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        DITContentRule cr = schema.getDITContentRule(schema.getObjectClass("2.5.6.1"));
        assertThat(cr.getNames()).isEmpty();
        assertThat(cr.getAuxiliaryClasses()).isEmpty();
        assertThat(cr.getOptionalAttributes()).isEmpty();
        assertThat(cr.getProhibitedAttributes()).isEmpty();
        assertThat(cr.getRequiredAttributes()).isEmpty();
    }

    @Test
    public void testBuilderRemove() throws Exception {
        DITContentRule.Builder crBuilder = new SchemaBuilder(getCoreSchema())
                .buildDITContentRule("2.5.6.1")
                .names(singletonList("shouldBeRemoved"))
                .description("My content rule")
                .auxiliaryObjectClasses(singletonList("shouldBeRemoved"))
                .optionalAttributes(singletonList("shouldBeRemoved"))
                .prohibitedAttributes(singletonList("shouldBeRemoved"))
                .requiredAttributes(singletonList("shouldBeRemoved"));

        Schema schema = crBuilder.removeName("shouldBeRemoved")
                .removeAuxiliaryObjectClass("shouldBeRemoved")
                .removeOptionalAttribute("shouldBeRemoved")
                .removeProhibitedAttribute("shouldBeRemoved")
                .removeRequiredAttribute("shouldBeRemoved")
                .addToSchema()
                .toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        DITContentRule cr = schema.getDITContentRule(schema.getObjectClass("2.5.6.1"));
        assertThat(cr.getNames()).isEmpty();
        assertThat(cr.getAuxiliaryClasses()).isEmpty();
        assertThat(cr.getOptionalAttributes()).isEmpty();
        assertThat(cr.getProhibitedAttributes()).isEmpty();
        assertThat(cr.getRequiredAttributes()).isEmpty();
    }
}
