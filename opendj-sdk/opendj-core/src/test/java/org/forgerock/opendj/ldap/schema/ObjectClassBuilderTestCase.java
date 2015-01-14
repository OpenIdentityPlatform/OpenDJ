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

import java.util.List;
import java.util.Set;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Collections.*;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.MapAssert.*;
import static org.forgerock.opendj.ldap.schema.ObjectClassType.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

public class ObjectClassBuilderTestCase extends AbstractSchemaTestCase {

    @DataProvider
    Object[][] validObjectClasses() {
        // OID, obsolete, names, optional attributes, required attributes,
        // superior object classes, type, description,
        // extra property name, extra property values, overwrite
        return new Object[][] {
            // Basic object class
            { "1.2.3.4", false, singletonList("MyObjectClass"), emptySet(), singleton("cn"),
                singleton(TOP_OBJECTCLASS_NAME), STRUCTURAL, "MyObjectClass description.", "New extra property",
                "New extra value", false },
            // Allowed overrides existing core schema object class groupOfNames
            { "2.5.6.9", false, singletonList("groupOfFirstNames"), emptySet(), singleton("cn"),
                singleton(TOP_OBJECTCLASS_NAME), AUXILIARY, "MyObjectClass description.", "New extra property",
                "New extra value", true },
            // No name provided, should be validated
            { "1.2.3.4", false, emptyList(), singleton("name"), singleton("cn"),
                singleton(TOP_OBJECTCLASS_NAME), STRUCTURAL, "MyObjectClass description.", "New extra property",
                "New extra value", false },
            // Empty description, should be validated
            { "1.2.3.4", false, emptyList(), singleton("name"), singleton("cn"),
                singleton(TOP_OBJECTCLASS_NAME), STRUCTURAL, "", "New extra property",
                "New extra value", false },
        };
    }

    @Test(dataProvider = "validObjectClasses")
    public void testValidOCBuilder(final String oid, final boolean isObsolete, final List<String> names,
            final Set<String> optionalAttributeOIDs, final Set<String> requiredAttributesOIDs,
            final Set<String> superiorClassOIDs, final ObjectClassType type, final String description,
            final String extraPropertyName, final String extraPropertyValue, final boolean overwrite)
            throws Exception {
        final ObjectClass.Builder ocBuilder = new SchemaBuilder(getCoreSchema())
            .buildObjectClass(oid)
            .description(description)
            .obsolete(isObsolete)
            .names(names)
            .superiorObjectClasses(superiorClassOIDs)
            .requiredAttributes(requiredAttributesOIDs)
            .optionalAttributes(optionalAttributeOIDs)
            .type(type)
            .extraProperties(extraPropertyName, extraPropertyValue);

        final Schema schema = overwrite ? ocBuilder.addToSchemaOverwrite().toSchema()
                                        : ocBuilder.addToSchema().toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        final ObjectClass oc = schema.getObjectClass(oid);
        assertThat(oc).isNotNull();
        assertThat(oc.getOID()).isEqualTo(oid);
        assertThat(oc.getDescription()).isEqualTo(description);
        assertThat(oc.isObsolete()).isEqualTo(isObsolete);
        assertThat(oc.getNames()).containsOnly(names.toArray());
        assertSchemaElementsContainsAll(oc.getSuperiorClasses(), superiorClassOIDs);
        assertSchemaElementsContainsAll(oc.getRequiredAttributes(), requiredAttributesOIDs);
        assertSchemaElementsContainsAll(oc.getOptionalAttributes(), optionalAttributeOIDs);
        assertThat(oc.getObjectClassType()).isEqualTo(type);
        assertThat(oc.getExtraProperties()).includes(entry(extraPropertyName, singletonList(extraPropertyValue)));
    }

    @Test
    public void testOCBuilderDefaultValues() throws Exception {
        final SchemaBuilder sb = new SchemaBuilder(getCoreSchema());
        final ObjectClass.Builder ocBuilder = sb.buildObjectClass("1.1.1.42")
                .description("Default object class")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .names("defaultObjectClass");
        final Schema schema = ocBuilder.addToSchema().toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        final ObjectClass oc = schema.getObjectClass("defaultObjectClass");
        assertThat(oc).isNotNull();
        assertThat(oc.getOID()).isEqualTo("1.1.1.42");
        assertThat(oc.getDescription()).isEqualTo("Default object class");
        assertThat(oc.isObsolete()).isFalse();
        assertThat(oc.getNames()).containsOnly("defaultObjectClass");
        assertSchemaElementsContainsAll(oc.getSuperiorClasses(), TOP_OBJECTCLASS_NAME);
        final Set<AttributeType> topReqAttrs = schema.getObjectClass(TOP_OBJECTCLASS_NAME).getRequiredAttributes();
        assertThat(oc.getRequiredAttributes()).containsOnly(topReqAttrs.toArray());
        assertThat(oc.getOptionalAttributes()).isEmpty();
        assertThat(oc.getObjectClassType()).isEqualTo(STRUCTURAL);
        assertThat(oc.getExtraProperties()).isEmpty();
    }

    @Test
    public void testOCBuilderCopyConstructor() throws Exception {
        final SchemaBuilder sb = new SchemaBuilder(getCoreSchema());
        final ObjectClass.Builder ocBuilder = sb.buildObjectClass("1.1.1.42")
                .description("Object class to duplicate")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .names("ObjectClassToDuplicate")
                .requiredAttributes("name");
        final Schema schema = ocBuilder.addToSchema().toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        sb.buildObjectClass(schema.getObjectClass("ObjectClassToDuplicate"))
                .oid("1.1.1.43")
                .names("Copy")
                .obsolete(true)
                .addToSchemaOverwrite();
        final Schema schemaCopy = sb.toSchema();
        assertThat(schemaCopy.getWarnings()).isEmpty();

        final ObjectClass ocCopy = schemaCopy.getObjectClass("Copy");
        assertThat(ocCopy).isNotNull();
        assertThat(ocCopy.getOID()).isEqualTo("1.1.1.43");
        assertThat(ocCopy.getDescription()).isEqualTo("Object class to duplicate");
        assertThat(ocCopy.isObsolete()).isTrue();
        assertThat(ocCopy.getNames()).containsOnly("ObjectClassToDuplicate", "Copy");
        assertSchemaElementsContainsAll(ocCopy.getSuperiorClasses(), TOP_OBJECTCLASS_NAME);
        assertSchemaElementsContainsAll(ocCopy.getRequiredAttributes(), "name");
        assertThat(ocCopy.getOptionalAttributes()).isEmpty();
        assertThat(ocCopy.getObjectClassType()).isEqualTo(STRUCTURAL);
        assertThat(ocCopy.getExtraProperties()).isEmpty();
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testOCBuilderDoesNotAllowOverwrite() throws Exception {
        final ObjectClass.Builder ocBuilder = new SchemaBuilder(getCoreSchema())
            .buildObjectClass("2.5.6.9")
            .description("MyObjectClass description")
            .names("groupOfFirstNames")
            .requiredAttributes("cn")
            .type(AUXILIARY)
            .extraProperties("New extra property", "New extra value");

        ocBuilder.addToSchema().toSchema();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOCBuilderDoesNotAllowEmptyOID() throws Exception {
        final ObjectClass.Builder ocBuilder = new SchemaBuilder(getCoreSchema())
            .buildObjectClass("")
            .description("MyObjectClass description")
            .names("MyObjectClass")
            .requiredAttributes("cn")
            .extraProperties("New extra property", "New extra value");

        ocBuilder.addToSchema().toSchema();
    }

    private void assertSchemaElementsContainsAll(final Set<? extends SchemaElement> elements,
            final Set<String> namesOrOIDs) throws Exception {
        assertSchemaElementsContainsAll(elements, namesOrOIDs.toArray(new String[namesOrOIDs.size()]));
    }


    private void assertSchemaElementsContainsAll(final Set<? extends SchemaElement> elements,
            final String... namesOrOIDs) throws Exception {
        for (final String nameOrOID : namesOrOIDs) {
            assertThat(assertSchemaElementsContains(elements, nameOrOID)).isTrue();
        }
    }

    private boolean assertSchemaElementsContains(final Set<? extends SchemaElement> elements, final String nameOrOID) {
        for (final SchemaElement element : elements) {
            final String oid = element instanceof AttributeType ? ((AttributeType) element).getNameOrOID()
                                                            : ((ObjectClass) element).getNameOrOID();
            if (oid.equals(nameOrOID)) {
                return true;
            }
        }
        return false;
    }

}
