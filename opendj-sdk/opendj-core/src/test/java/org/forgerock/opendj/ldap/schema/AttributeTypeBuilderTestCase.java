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
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AttributeTypeBuilderTestCase extends AbstractSchemaTestCase {

    @DataProvider
    Object[][] validAttributeTypes() {
        // OID, names, description, obsolete, superior type, equalityMR,
        // orderingMR, substringMR, approximateMR, syntax, singleValue,
        // collective, noUserModification, attributeUsage, extraPropertyName,
        // extraPropertyValue
        return new Object[][] {
            // Basic attribute type
            { "1.2.3.4", singletonList("MyAttributeType"), "MyAttributeType description.", false, "2.5.4.0",
                EMR_CERTIFICATE_EXACT_OID, OMR_UUID_OID, SMR_CASE_IGNORE_LIST_OID, AMR_DOUBLE_METAPHONE_OID, null,
                false, false, false, AttributeUsage.USER_APPLICATIONS, "New extra property", "New extra value", false },
            // Allowed overrides existing core schema attribute type name
            { "2.5.4.41", singletonList("name"), "MyAttributeType description.", false, null,
                EMR_CERTIFICATE_EXACT_OID, null, SMR_CASE_IGNORE_LIST_OID, AMR_DOUBLE_METAPHONE_OID,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                "New extra property", "New extra value", true },
            // No name provided, should be validated
            { "1.2.3.4", emptyList(), "MyAttributeType description.", false, null, EMR_CASE_IGNORE_LIST_OID,
                OMR_CASE_IGNORE_OID, null, null, SYNTAX_DIRECTORY_STRING_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, "New extra property", "New extra value", false },
            // Empty description, should be validated
            { "1.2.3.4", singletonList("MyAttributeType"), "", false, null, EMR_CASE_IGNORE_LIST_OID, null, null,
                null, SYNTAX_DIRECTORY_STRING_OID, false, false, true, AttributeUsage.DIRECTORY_OPERATION,
                "New extra property", "New extra value", false },
        };
    }

    @Test(dataProvider = "validAttributeTypes")
    public void testValidAttributeTypeBuilder(final String oid, final List<String> names, final String description,
            final boolean obsolete, final String superiorType, final String equalityMatchingRule,
            final String orderingMatchingRule, final String substringMatchingRule,
            final String approximateMatchingRule, final String syntax, final boolean singleValue,
            final boolean collective, final boolean noUserModification, final AttributeUsage attributeUsage,
            final String extraPropertyName, String extraPropertyValue, final boolean overwrite) throws Exception {
        AttributeType.Builder atBuilder = new SchemaBuilder(getCoreSchema())
            .buildAttributeType(oid)
            .names(names)
            .description(description)
            .obsolete(obsolete)
            .superiorType(superiorType)
            .equalityMatchingRule(equalityMatchingRule)
            .orderingMatchingRule(orderingMatchingRule)
            .substringMatchingRule(substringMatchingRule)
            .approximateMatchingRule(approximateMatchingRule)
            .syntax(syntax)
            .singleValue(singleValue)
            .collective(collective)
            .noUserModification(noUserModification)
            .usage(attributeUsage)
            .extraProperties(extraPropertyName, extraPropertyValue);

        Schema schema = overwrite ? atBuilder.addToSchemaOverwrite().toSchema()
                                  : atBuilder.addToSchema().toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        final AttributeType at = schema.getAttributeType(oid);
        assertThat(at).isNotNull();
        assertThat(at.getOID()).isEqualTo(oid);
        assertThat(at.getNames()).containsOnly(names.toArray());
        assertThat(at.getDescription()).isEqualTo(description);
        assertThat(at.isObsolete()).isEqualTo(obsolete);
        assertThat(at.getExtraProperties()).includes(entry(extraPropertyName, singletonList(extraPropertyValue)));
    }

    @Test
    public void testAttributeTypeBuilderDefaultValues() throws Exception {
        final String testOID = "1.1.1.42";
        final SchemaBuilder sb = new SchemaBuilder(getCoreSchema());
        AttributeType.Builder ocBuilder = sb.buildAttributeType(testOID)
                                            .names("defaultAttributeType")
                                            .syntax(SYNTAX_OID_OID)
                                            .usage(AttributeUsage.USER_APPLICATIONS);

        Schema schema = ocBuilder.addToSchema().toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        AttributeType at = schema.getAttributeType(testOID);
        assertThat(at).isNotNull();
        assertThat(at.getOID()).isEqualTo(testOID);
        assertThat(at.getNames()).containsOnly("defaultAttributeType");
        assertThat(at.getDescription()).isEqualTo("");
        assertThat(at.isObsolete()).isFalse();
        assertThat(at.getSuperiorType()).isNull();
        assertThat(at.getSyntax().getOID()).isEqualTo(SYNTAX_OID_OID);
        assertThat(at.isSingleValue()).isFalse();
        assertThat(at.isCollective()).isFalse();
        assertThat(at.isNoUserModification()).isFalse();
        assertThat(at.getExtraProperties()).isEmpty();
    }

    @Test
    public void testAttributeTypeBuilderCopyConstructor() throws Exception {
        SchemaBuilder sb = new SchemaBuilder(getCoreSchema());
        AttributeType.Builder atBuilder = sb.buildAttributeType("1.1.1.42")
                                            .names("AttributeTypeToDuplicate")
                                            .description("Attribute type to duplicate")
                                            .usage(AttributeUsage.USER_APPLICATIONS)
                                            .syntax(SYNTAX_OID_OID);
        Schema schema = atBuilder.addToSchema().toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        sb.buildAttributeType(schema.getAttributeType("AttributeTypeToDuplicate"))
                .oid("1.1.1.43")
                .names("Copy")
                .obsolete(true)
                .addToSchemaOverwrite();
        Schema schemaCopy = sb.toSchema();
        assertThat(schemaCopy.getWarnings()).isEmpty();

        AttributeType atCopy = schemaCopy.getAttributeType("Copy");
        assertThat(atCopy).isNotNull();
        assertThat(atCopy.getOID()).isEqualTo("1.1.1.43");
        assertThat(atCopy.getDescription()).isEqualTo("Attribute type to duplicate");
        assertThat(atCopy.isObsolete()).isTrue();
        assertThat(atCopy.getNames()).containsOnly("AttributeTypeToDuplicate", "Copy");
        assertThat(atCopy.getExtraProperties()).isEmpty();
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testAttributeTypeBuilderDoesNotAllowOverwrite() throws Exception {
        AttributeType.Builder atBuilder = new SchemaBuilder(getCoreSchema())
            .buildAttributeType("2.5.4.25")
            .description("MyAttributeType description")
            .names("internationalISDNNumber")
            .syntax(SYNTAX_OID_OID)
            .usage(AttributeUsage.DSA_OPERATION)
            .extraProperties("New extra property", "New extra value");

        atBuilder.addToSchema().toSchema();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAttributeTypeBuilderDoesNotAllowEmptyOID() throws Exception {
        AttributeType.Builder atBuilder = new SchemaBuilder(getCoreSchema())
            .buildAttributeType("")
            .description("MyAttributeType description")
            .names("MyAttributeType")
            .syntax(SYNTAX_OID_DESCRIPTION)
            .usage(AttributeUsage.DIRECTORY_OPERATION)
            .extraProperties("New extra property", "New extra value");

        atBuilder.addToSchema().toSchema();
    }

}
