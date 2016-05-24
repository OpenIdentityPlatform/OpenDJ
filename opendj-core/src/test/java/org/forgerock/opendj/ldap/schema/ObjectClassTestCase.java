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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EXTENSIBLE_OBJECT_OBJECTCLASS_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.TOP_OBJECTCLASS_OID;
import static org.forgerock.opendj.ldap.schema.Schema.getCoreSchema;
import static org.fest.assertions.Assertions.*;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ObjectClassTestCase extends AbstractSchemaTestCase {

    @Test
    public void extensibleObjectShouldAcceptPlaceholderAttribute() {
        Schema schema = getCoreSchema();
        ObjectClass extensibleObject = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        AttributeType attributeType = schema.getAttributeType("dummy");
        assertThat(attributeType.isPlaceHolder()).isTrue();
        assertThat(extensibleObject.isRequired(attributeType)).isFalse();
        assertThat(extensibleObject.isOptional(attributeType)).isTrue();
        assertThat(extensibleObject.isRequiredOrOptional(attributeType)).isTrue();
    }

    @Test
    public void extensibleObjectShouldAcceptAnyAttribute() {
        Schema schema = getCoreSchema();
        ObjectClass extensibleObject = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        AttributeType cn = schema.getAttributeType("cn");
        assertThat(cn.isPlaceHolder()).isFalse();
        assertThat(extensibleObject.isRequired(cn)).isFalse();
        assertThat(extensibleObject.isOptional(cn)).isTrue();
        assertThat(extensibleObject.isRequiredOrOptional(cn)).isTrue();
    }

    @Test
    public void testNames() throws Exception {
        ObjectClass oc = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType", "testNameAlias", "anotherNameAlias")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc.hasName("testType")).isTrue();
        assertThat(oc.hasName("testNameAlias")).isTrue();
        assertThat(oc.hasName("anotherNameAlias")).isTrue();
        assertThat(oc.hasName("unknownAlias")).isFalse();
        assertThat(oc.getNames()).containsOnly("testType", "testNameAlias", "anotherNameAlias");
    }

    @Test
    public void testNameOrOIDReturnsOIDWhenNoName() throws Exception {
        ObjectClass oc = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc.getNameOrOID()).isEqualTo("1.2.3");
    }

    @Test
    public void testNameOrOIDReturnsPrimaryNameWhenOneOrMoreNames() throws Exception {
        ObjectClass oc1 = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType")
            .addToSchema().toSchema().getObjectClass("1.2.3");
        ObjectClass oc2 = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType", "testAlias")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc1.getNameOrOID()).isEqualTo("testType");
        assertThat(oc2.getNameOrOID()).isEqualTo("testType");
    }

    @DataProvider
    public final Object[][] equalsData() {
        return new Object[][] {
            // name1 and oid1 for first object class, name2 and oid2 for second object class, should be equal ?
            { "testType", "1.2.3", "testType", "1.2.3", true },
            { "testType", "1.2.3", "xxx", "1.2.3", true },
            { "testType", "1.2.3", "testType", "1.2.4", false },
            { "testType", "1.2.3", "xxx", "1.2.4", false } };
    }

    @Test(dataProvider = "equalsData")
    public final void testEquals(String name1, String oid1, String name2, String oid2, boolean shouldBeEqual)
            throws Exception {
        ObjectClass oc1 = new SchemaBuilder(schema())
            .buildObjectClass(oid1)
            .names(name1)
            .addToSchema().toSchema().getObjectClass(oid1);
        ObjectClass oc2 = new SchemaBuilder(schema())
            .buildObjectClass(oid2)
            .names(name2)
            .addToSchema().toSchema().getObjectClass(oid2);

        if (shouldBeEqual) {
            assertThat(oc1).isEqualTo(oc2);
            assertThat(oc2).isEqualTo(oc1);
        } else {
            assertThat(oc1).isNotEqualTo(oc2);
            assertThat(oc2).isNotEqualTo(oc1);
        }
    }

    @Test
    public void testGetOptionalAttributesNoSuperiorNoAttribute() throws Exception {
        final ObjectClass.Builder ocBuilder = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType");
        ObjectClass oc = ocBuilder.addToSchema().toSchema().getObjectClass("1.2.3");
        assertThat(oc.getOptionalAttributes()).isEmpty();
        assertThat(oc.getDeclaredOptionalAttributes()).isEmpty();
    }

    @Test
    public void testGetOptionalAttributesNoSuperior() throws Exception {
        Schema schema = schema();
        ObjectClass oc = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType")
            .optionalAttributes("at1", "at2", "at3")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc.getOptionalAttributes()).containsOnly(attrs(schema, "at1", "at2", "at3"));
        assertThat(oc.getDeclaredOptionalAttributes()).containsOnly((attrs(schema, "at1", "at2", "at3")));

    }

    @Test
    public void testGetOptionalAttributeOneSuperiorNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .optionalAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getOptionalAttributes()).containsOnly(attrs(schema, "at1", "at2", "at3"));
        assertThat(child.getDeclaredOptionalAttributes()).isEmpty();

    }

    @Test
    public void testGetOptionalAttributeMultipleSuperiorsNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .optionalAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .optionalAttributes("at4", "at5", "at6")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Set<AttributeType> attributes = child.getOptionalAttributes();
        assertThat(attributes).containsOnly(attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6"));
    }

    @Test
    public void testGetOptionalAttributeOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .optionalAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .optionalAttributes("at4", "at5", "at6")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getOptionalAttributes()).containsOnly(attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6"));
        assertThat(child.getDeclaredOptionalAttributes()).containsOnly((attrs(schema, "at4", "at5", "at6")));
    }

    @Test
    public void testGetOptionalAttributeMultipleSuperiors() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .optionalAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .optionalAttributes("at4", "at5", "at6")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .optionalAttributes("at7", "at8", "at9")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getOptionalAttributes()).containsOnly(
                attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6", "at7", "at8", "at9"));
        assertThat(child.getDeclaredOptionalAttributes()).containsOnly((attrs(schema, "at7", "at8", "at9")));
    }

    @Test
    public void testGetRequiredAttributesNoSuperiorNoAttribute() throws Exception {
        final ObjectClass.Builder ocBuilder = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType");
        ObjectClass oc = ocBuilder.addToSchema().toSchema().getObjectClass("1.2.3");
        assertThat(oc.getRequiredAttributes()).isEmpty();
        assertThat(oc.getDeclaredRequiredAttributes()).isEmpty();
    }

    @Test
    public void testGetRequiredAttributesNoSuperior() throws Exception {
        Schema schema = schema();
        ObjectClass oc = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType")
            .requiredAttributes("at1", "at2", "at3")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc.getRequiredAttributes()).containsOnly((attrs(schema, "at1", "at2", "at3")));
        assertThat(oc.getDeclaredRequiredAttributes()).containsOnly((attrs(schema, "at1", "at2", "at3")));
    }

    @Test
    public void testGetRequiredAttributeOneSuperiorNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getRequiredAttributes()).containsOnly((attrs(schema, "at1", "at2", "at3")));
        assertThat(child.getDeclaredRequiredAttributes()).isEmpty();
    }

    @Test
    public void testGetRequiredAttributeMultipleSuperiorsNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .requiredAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .requiredAttributes("at4", "at5", "at6")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getRequiredAttributes()).containsOnly(
                (attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6")));
        assertThat(child.getDeclaredRequiredAttributes()).isEmpty();
    }

    @Test
    public void testGetRequiredAttributeOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .requiredAttributes("at4", "at5", "at6")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getRequiredAttributes()).containsOnly(attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6"));
        assertThat(child.getDeclaredRequiredAttributes()).containsOnly(attrs(schema, "at4", "at5", "at6"));
    }

    @Test
    public void testGetRequiredAttributeMultipleSuperiors() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .requiredAttributes("at1", "at2", "at3")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .requiredAttributes("at4", "at5", "at6")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .requiredAttributes("at7", "at8", "at9")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getRequiredAttributes()).containsOnly(
                attrs(schema, "at1", "at2", "at3", "at4", "at5", "at6", "at7", "at8", "at9"));
        assertThat(child.getDeclaredRequiredAttributes()).containsOnly(attrs(schema, "at7", "at8", "at9"));
    }

    @Test
    public void testGetSuperiorClassNoSuperiorDefined() throws Exception {
        Schema schema = schema();
        ObjectClass oc = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3")
            .names("testType")
            .addToSchema().toSchema().getObjectClass("1.2.3");

        assertThat(oc.getSuperiorClasses())
            .as("\"top\" should be added to superior classes for STRUCTURAL object classes")
            .containsOnly(schema.getObjectClass(TOP_OBJECTCLASS_OID));
        assertThat(oc.toString())
            .as("toString() should return the initial definition, without top")
            .isEqualTo("( 1.2.3 NAME 'testType' )");
    }

    @Test
    public void testGetSuperiorClassWithSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        assertThat(child.getSuperiorClasses()).containsOnly(objectClasses(schema, "parent"));
        assertThat(child.toString()).isEqualTo("( 1.2.3.2 NAME 'child' SUP parent )");
    }

    @Test
    public void testGetSuperiorClassWithMultipleSuperiors() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();

        ObjectClass child = schema.getObjectClass("child");
        assertThat(child.getSuperiorClasses()).containsOnly(objectClasses(schema, "parent1", "parent2"));
    }

    @Test
    public void testStructuralIsDescendantOfTopDespiteNoSuperiorDeclared() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("testType2")
            .addToSchema().toSchema();
        ObjectClass testType1 = schema.getObjectClass("testType1");
        ObjectClass testType2 = schema.getObjectClass("testType2");

        assertThat(testType1.isDescendantOf(testType2)).isFalse();
        assertThat(testType1.isDescendantOf(schema.getObjectClass(TOP_OBJECTCLASS_OID))).isTrue();
    }

    @Test
    public void testIsDescendantOfWithSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("grandParent")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent")
            .superiorObjectClasses("grandParent")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass grandParent = schema.getObjectClass("grandParent");
        ObjectClass parent = schema.getObjectClass("parent");
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(parent.isDescendantOf(grandParent));
        Assert.assertTrue(child.isDescendantOf(parent));
        Assert.assertTrue(child.isDescendantOf(grandParent));

        Assert.assertFalse(child.isDescendantOf(child));
        Assert.assertFalse(parent.isDescendantOf(child));
        Assert.assertFalse(grandParent.isDescendantOf(child));
    }

    @Test
    public void testIsDescendantOfWithMultipleSuperiors() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("grandParent")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent1")
            .superiorObjectClasses("grandParent")
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("parent2")
            .addToSchema()

            .buildObjectClass("1.2.3.4")
            .names("child")
            .superiorObjectClasses("parent1", "parent2")
            .addToSchema().toSchema();
        ObjectClass grandParent = schema.getObjectClass("grandParent");
        ObjectClass parent1 = schema.getObjectClass("parent1");
        ObjectClass parent2 = schema.getObjectClass("parent2");
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(parent1.isDescendantOf(grandParent));
        Assert.assertTrue(child.isDescendantOf(parent1));
        Assert.assertTrue(child.isDescendantOf(parent2));
        Assert.assertTrue(child.isDescendantOf(grandParent));

        Assert.assertFalse(child.isDescendantOf(child));
        Assert.assertFalse(parent1.isDescendantOf(child));
        Assert.assertFalse(parent1.isDescendantOf(parent2));
        Assert.assertFalse(parent2.isDescendantOf(grandParent));
        Assert.assertFalse(grandParent.isDescendantOf(child));
    }

    @Test
    public void testIsOptionalNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertFalse(oc.isOptional(schema.getAttributeType("at1")));
    }

    @Test
    public void testIsOptionalNoSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .optionalAttributes("at1")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertTrue(oc.isOptional(schema.getAttributeType("at1")));
        Assert.assertFalse(oc.isOptional(schema.getAttributeType("at2")));
    }

    @Test
    public void testIsOptionalNoAttributeWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .optionalAttributes("at1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isOptional(schema.getAttributeType("at1")));
        Assert.assertFalse(child.isOptional(schema.getAttributeType("at2")));
    }

    @Test
    public void testIsOptionalWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .optionalAttributes("at1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .optionalAttributes("at2")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(child.isOptional(schema.getAttributeType("at2")));
        Assert.assertFalse(child.isOptional(schema.getAttributeType("at3")));
    }

    @Test
    public void testIsOptionalExtensible() throws Exception {
        Schema schema = schema();
        ObjectClass oc = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        Assert.assertTrue(oc.isOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(oc.isOptional(schema.getAttributeType("at2")));
    }

    @Test
    public void testIsRequiredNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertFalse(oc.isRequired(schema.getAttributeType("at1")));
    }

    @Test
    public void testIsRequiredNoSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .requiredAttributes("at1")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertTrue(oc.isRequired(schema.getAttributeType("at1")));
        Assert.assertFalse(oc.isRequired(schema.getAttributeType("at2")));
    }

    @Test
    public void testIsRequiredNoAttributeWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isRequired(schema.getAttributeType("at1")));
        Assert.assertFalse(child.isRequired(schema.getAttributeType("at2")));
    }

    @Test
    public void testIsRequiredWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .requiredAttributes("at2")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isRequired(schema.getAttributeType("at1")));
        Assert.assertTrue(child.isRequired(schema.getAttributeType("at2")));
        Assert.assertFalse(child.isRequired(schema.getAttributeType("at3")));
    }

    @Test
    public void testIsRequiredOrOptionalNoAttribute() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertFalse(oc.isRequiredOrOptional(schema.getAttributeType("at1")));
    }

    @Test
    public void testIsRequiredOrOptionalNoSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("testType")
            .requiredAttributes("at1")
            .optionalAttributes("at2")
            .addToSchema().toSchema();
        ObjectClass oc = schema.getObjectClass("testType");

        Assert.assertTrue(oc.isRequiredOrOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(oc.isRequiredOrOptional(schema.getAttributeType("at2")));
        Assert.assertFalse(oc.isRequiredOrOptional(schema.getAttributeType("at3")));
    }

    @Test
    public void testIsRequiredOrOptionalNoAttributeWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1")
            .optionalAttributes("at2")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at2")));
        Assert.assertFalse(child.isRequiredOrOptional(schema.getAttributeType("at3")));
    }

    @Test
    public void testIsRequiredOrOptionalWithOneSuperior() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent")
            .requiredAttributes("at1")
            .optionalAttributes("at2")
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("child")
            .requiredAttributes("at3")
            .optionalAttributes("at4")
            .superiorObjectClasses("parent")
            .addToSchema().toSchema();
        ObjectClass child = schema.getObjectClass("child");

        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at2")));
        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at3")));
        Assert.assertTrue(child.isRequiredOrOptional(schema.getAttributeType("at4")));
        Assert.assertFalse(child.isRequiredOrOptional(schema.getAttributeType("at5")));
    }

    @Test
    public void testIsRequiredOrOptionalExtensible() throws Exception {
        Schema schema = schema();
        ObjectClass oc = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        Assert.assertTrue(oc.isRequiredOrOptional(schema.getAttributeType("at1")));
        Assert.assertTrue(oc.isRequiredOrOptional(schema.getAttributeType("at2")));
    }

    /** Test data for testing different combinations of superiors. */
    @DataProvider
    public Object[][] superiorData() throws Exception {
        Schema schema = new SchemaBuilder(schema())
            .buildObjectClass("1.2.3.1")
            .names("parent1")
            .type(ObjectClassType.ABSTRACT)
            .addToSchema()

            .buildObjectClass("1.2.3.2")
            .names("parent2")
            .type(ObjectClassType.ABSTRACT)
            .addToSchema()

            .buildObjectClass("1.2.3.3")
            .names("parent3")
            .type(ObjectClassType.STRUCTURAL)
            .addToSchema()

            .buildObjectClass("1.2.3.4")
            .names("parent4")
            .type(ObjectClassType.STRUCTURAL)
            .addToSchema()

            .buildObjectClass("1.2.3.5")
            .names("parent5")
            .type(ObjectClassType.AUXILIARY)
            .addToSchema()

            .buildObjectClass("1.2.3.6")
            .names("parent6")
            .type(ObjectClassType.AUXILIARY)
            .addToSchema().toSchema();

        return new Object[][] {
            // parent 1 name, parent2 name, type of child, schema
            { "parent1", "parent2", ObjectClassType.ABSTRACT, schema },
            { "parent3", "parent4", ObjectClassType.STRUCTURAL, schema },
            { "parent5", "parent6", ObjectClassType.AUXILIARY, schema }
        };
    }

    @Test(dataProvider = "superiorData")
    public void testMultipleSuperiors(String parent1, String parent2, ObjectClassType type, Schema schema)
            throws Exception {
        ObjectClass child = new SchemaBuilder(schema)
            .buildObjectClass("1.2.3.7")
            .names("child")
            .type(type)
            .superiorObjectClasses(parent1, parent2)
            .addToSchema().toSchema().getObjectClass("child");

        assertThat(child.getSuperiorClasses()).hasSize(2);
    }

    /** Returns a schema initialized with new attributes types "at1", "at2", ..., "at9". */
    private Schema schema() throws Exception {
        SchemaBuilder sb = new SchemaBuilder(getCoreSchema());
        for (int i = 1; i <= 9; i++) {
            sb.buildAttributeType("1.2.3.4." + i).names("at" + i).addToSchema();
        }
        return sb.toSchema();
    }

    /** Returns attributes types from the provided schema by names (as Object[] due to usage in assertions). */
    private Object[] attrs(Schema schema, String... names) {
        AttributeType[] attrs = new AttributeType[names.length];
        int i = 0;
        for (String name : names) {
            attrs[i++] = schema.getAttributeType(name);
        }
        return attrs;
    }

    /** Returns object classes from the provided schema by names (as Object[] due to usage in assertions). */
    private Object[] objectClasses(Schema schema, String... names) {
        ObjectClass[] attrs = new ObjectClass[names.length];
        int i = 0;
        for (String name : names) {
            attrs[i++] = schema.getObjectClass(name);
        }
        return attrs;
    }
}
