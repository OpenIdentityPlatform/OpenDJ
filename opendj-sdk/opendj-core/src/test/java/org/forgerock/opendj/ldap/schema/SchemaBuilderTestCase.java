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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.*;
import static org.fest.assertions.Fail.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.testng.annotations.Test;

/**
 * Test SchemaBuilder.
 */
@SuppressWarnings("javadoc")
public class SchemaBuilderTestCase extends AbstractSchemaTestCase {

    /**
     * Tests that schema validation resolves dependencies between parent/child
     * attribute types regardless of the order in which they were added.
     */
    @Test
    public void testAttributeTypeDependenciesChildThenParent() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                        .addAttributeType("( childtype-oid NAME 'childtype' SUP parenttype )",
                                false)
                        .addAttributeType(
                                "( parenttype-oid NAME 'parenttype' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )",
                                false).toSchema();
        assertThat(schema.getAttributeType("childtype").getSyntax()).isNotNull();
        assertThat(schema.getAttributeType("childtype").getSyntax().getOID()).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.15");
    }

    /**
     * Tests that schema validation resolves dependencies between parent/child
     * attribute types regardless of the order in which they were added.
     */
    @Test
    public void testAttributeTypeDependenciesParentThenChild() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                        .addAttributeType(
                                "( parenttype-oid NAME 'parenttype' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )",
                                false).addAttributeType(
                                "( childtype-oid NAME 'childtype' SUP parenttype )", false)
                        .toSchema();
        assertThat(schema.getAttributeType("childtype").getSyntax()).isNotNull();
        assertThat(schema.getAttributeType("childtype").getSyntax().getOID()).isEqualTo(
                "1.3.6.1.4.1.1466.115.121.1.15");
    }

    /**
     * Tests that attribute types must have a syntax or a superior.
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testAttributeTypeNoSuperiorNoSyntax() {
        new SchemaBuilder(Schema.getCoreSchema()).addAttributeType(
                "( parenttype-oid NAME 'parenttype' )", false);
    }

    /**
     * Tests that schema validation handles validation failures for superior
     * attribute types regardless of the order.
     */
    @Test
    public void testAttributeTypeSuperiorFailureChildThenParent() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).addAttributeType(
                        "( childtype-oid NAME 'childtype' SUP parenttype )", false)
                        .addAttributeType("( parenttype-oid NAME 'parenttype' SUP xxx )", false)
                        .toSchema();

        try {
            schema.getAttributeType("childtype");
            fail("childtype should not be in the schema because its parent is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }

        try {
            schema.getAttributeType("parenttype");
            fail("parenttype should not be in the schema because it is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }
    }

    /**
     * Tests that schema validation handles validation failures for superior
     * attribute types regardless of the order.
     */
    @Test
    public void testAttributeTypeSuperiorFailureParentThenChild() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).addAttributeType(
                        "( parenttype-oid NAME 'parenttype' SUP xxx )", false).addAttributeType(
                        "( childtype-oid NAME 'childtype' SUP parenttype )", false).toSchema();

        try {
            schema.getAttributeType("childtype");
            fail("childtype should not be in the schema because its parent is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }

        try {
            schema.getAttributeType("parenttype");
            fail("parenttype should not be in the schema because it is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }
    }

    /**
     * Test for OPENDJ-156: Errors when parsing collective attribute
     * definitions.
     */
    @Test
    public void testCollectiveAttribute() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).addAttributeType(
                        "( 2.5.4.11.1 NAME 'c-ou' "
                                + "SUP ou SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 "
                                + "COLLECTIVE X-ORIGIN 'RFC 3671' )", false).toSchema();
        assertThat(schema.getWarnings()).isEmpty();
    }

    /**
     * Tests that it is possible to create a schema which is an exact copy of
     * another and take advantage of copy on write.
     */
    @Test
    public void testCopyOnWriteNoChanges() {
        final Schema baseSchema = Schema.getCoreSchema();
        final Schema schema = new SchemaBuilder(baseSchema).toSchema();

        assertThat(schema).isSameAs(baseSchema);
    }

    /**
     * Tests that it is possible to create a schema which is based on another.
     */
    @Test
    public void testCopyOnWriteWithChanges() {

        final Schema baseSchema = Schema.getCoreSchema();
        final Schema schema =
                new SchemaBuilder(baseSchema).addAttributeType(
                        "( testtype-oid NAME 'testtype' SUP name )", false).toSchema();
        assertThat(schema).isNotSameAs(baseSchema);
        assertThat(schema.getObjectClasses().containsAll(baseSchema.getObjectClasses()));
        assertThat(schema.getObjectClasses().size())
                .isEqualTo(baseSchema.getObjectClasses().size());
        assertThat(schema.getAttributeTypes().containsAll(baseSchema.getAttributeTypes()));
        assertThat(schema.getAttributeType("testtype")).isNotNull();
        assertThat(schema.getSchemaName()).isEqualTo(baseSchema.getSchemaName());
        assertThat(schema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS))
                .isEqualTo(baseSchema.getOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS));
    }

    /**
     * Tests that it is possible to create an empty schema.
     */
    @Test
    public void testCreateEmptySchema() {
        final Schema schema = new SchemaBuilder().toSchema();
        assertThat(schema.getAttributeTypes()).isEmpty();
        assertThat(schema.getObjectClasses()).isEmpty();
        assertThat(schema.getSyntaxes()).isEmpty();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getDefaultSyntax()).isEqualTo(CoreSchema.getOctetStringSyntax());
        assertThat(schema.getDefaultMatchingRule()).isEqualTo(
                CoreSchema.getOctetStringMatchingRule());
        // Could go on...
    }

    /**
     * Tests that multiple consecutive invocations of toSchema return the exact
     * same schema.
     */
    @Test
    public void testMultipleToSchema1() {
        final Schema baseSchema = Schema.getCoreSchema();
        final SchemaBuilder builder = new SchemaBuilder(baseSchema);
        final Schema schema1 = builder.toSchema();
        final Schema schema2 = builder.toSchema();
        assertThat(schema1).isSameAs(baseSchema);
        assertThat(schema1).isSameAs(schema2);
    }

    /**
     * Tests that multiple consecutive invocations of toSchema return the exact
     * same schema.
     */
    @Test
    public void testMultipleToSchema2() {
        final SchemaBuilder builder =
                new SchemaBuilder().addAttributeType(
                        "( testtype-oid NAME 'testtype' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
                        false);
        final Schema schema1 = builder.toSchema();
        final Schema schema2 = builder.toSchema();
        assertThat(schema1).isSameAs(schema2);
        assertThat(schema1.getAttributeType("testtype")).isNotNull();
        assertThat(schema2.getAttributeType("testtype")).isNotNull();
    }

    /**
     * Tests that schema validation resolves dependencies between parent/child
     * object classes regardless of the order in which they were added.
     */
    @Test
    public void testObjectClassDependenciesChildThenParent() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).addObjectClass(
                        "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
                        false).addObjectClass(
                        "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST cn )", false)
                        .toSchema();

        final AttributeType cn = schema.getAttributeType("cn");
        final AttributeType sn = schema.getAttributeType("sn");

        assertThat(schema.getObjectClass("childtype").isRequired(cn)).isTrue();
        assertThat(schema.getObjectClass("childtype").isRequired(sn)).isTrue();
    }

    /**
     * Tests that schema validation resolves dependencies between parent/child
     * object classes regardless of the order in which they were added.
     */
    @Test
    public void testObjectClassDependenciesParentThenChild() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                        .addObjectClass(
                                "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST cn )",
                                false)
                        .addObjectClass(
                                "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
                                false).toSchema();

        final AttributeType cn = schema.getAttributeType("cn");
        final AttributeType sn = schema.getAttributeType("sn");

        assertThat(schema.getObjectClass("childtype").isRequired(cn)).isTrue();
        assertThat(schema.getObjectClass("childtype").isRequired(sn)).isTrue();
    }

    /**
     * Tests that schema validation handles validation failures for superior
     * object classes regardless of the order.
     */
    @Test
    public void testObjectClassSuperiorFailureChildThenParent() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).addObjectClass(
                        "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
                        false).addObjectClass(
                        "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST xxx )", false)
                        .toSchema();

        try {
            schema.getObjectClass("childtype");
            fail("childtype should not be in the schema because its parent is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }

        try {
            schema.getObjectClass("parenttype");
            fail("parenttype should not be in the schema because it is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }
    }

    /**
     * Tests that schema validation handles validation failures for superior
     * object classes regardless of the order.
     */
    @Test
    public void testObjectClassSuperiorFailureParentThenChild() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                        .addObjectClass(
                                "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST xxx )",
                                false)
                        .addObjectClass(
                                "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
                                false).toSchema();

        try {
            schema.getObjectClass("childtype");
            fail("childtype should not be in the schema because its parent is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }

        try {
            schema.getObjectClass("parenttype");
            fail("parenttype should not be in the schema because it is invalid");
        } catch (final UnknownSchemaElementException e) {
            // Expected.
        }
    }

    /**
     * Tests that a schema builder can be re-used after toSchema has been
     * called.
     */
    @Test
    public void testReuseSchemaBuilder() {
        final SchemaBuilder builder = new SchemaBuilder();
        final Schema schema1 =
                builder.addAttributeType(
                        "( testtype1-oid NAME 'testtype1' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
                        false).toSchema();

        final Schema schema2 =
                builder.addAttributeType(
                        "( testtype2-oid NAME 'testtype2' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
                        false).toSchema();
        assertThat(schema1).isNotSameAs(schema2);
        assertThat(schema1.getAttributeType("testtype1")).isNotNull();
        assertThat(schema1.hasAttributeType("testtype2")).isFalse();
        assertThat(schema2.getAttributeType("testtype1")).isNotNull();
        assertThat(schema2.getAttributeType("testtype2")).isNotNull();
    }

    /**
     * The SchemaBuilder (Entry) doesn't allow a null parameter. Throws a
     * NullPointerException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testSchemaBuilderDoesntAllowNullEntry() throws Exception {
        new SchemaBuilder((Entry) null);
    }

    /**
     * Test to build a schema with an entry.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryWithCoreSchema() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "attributeTypes: ( temporary-fake-attr-id NAME 'myCustomAttribute' EQUALITY"
                + " caseIgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
            "objectClasses: ( temporary-fake-oc-id NAME 'myCustomObjClass'"
                + " SUP top AUXILIARY MAY myCustomAttribute )",
            "modifiersName: cn=Directory Manager,cn=Root DNs,cn=config",
            "modifyTimestamp: 20110620095948Z"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder(e);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        builder.addSchema(Schema.getCoreSchema(), false);
        Schema schema = builder.toSchema();

        assertThat(schema.getAttributeType("myCustomAttribute")).isNotNull();
        assertThat(schema.getAttributeType("myCustomAttribute").getNameOrOID()).isEqualTo(
                "myCustomAttribute");
        assertThat(schema.getAttributeType("myCustomAttribute").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Directory String' X-ORIGIN 'RFC 4512' )");
        assertThat(schema.getAttributeType("myCustomAttribute").getUsage().toString()).isEqualTo(
                "userApplications");

        assertThat(schema.getObjectClassesWithName("myCustomObjClass")).isNotEmpty();
    }

    /**
     * Building a schema with an ldif entry but without a core schema throws an
     * UnknownSchemaElementException. We can read in the schema warning :
     * [...]The object class "myCustomObjClass" specifies the superior object
     * class "top" which is not defined in the schema (...).
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderWithEntryWithoutCoreSchema() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "attributeTypes: ( temporary-fake-attr-id NAME 'myCustomAttribute' EQUALITY"
                + " caseIgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
            "objectClasses: ( temporary-fake-oc-id NAME 'myCustomObjClass'"
                + " SUP top AUXILIARY MAY myCustomAttribute )",
            "modifiersName: cn=Directory Manager,cn=Root DNs,cn=config",
            "modifyTimestamp: 20110620095948Z"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder(e);

        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNotNull();

        Schema schema = builder.toSchema();
        // Warnings expected.
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getAttributeType("myCustomAttribute")).isNotNull();
    }

    /**
     * Adds an attribute to the schema, without a space between the usage and the
     * right paren.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAttributeWithoutSpace() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "attributeTypes: ( foo-oid NAME 'foo' SUP name DESC 'No trailing space' USAGE userApplications)"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();
        // No warnings
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getAttributeType("foo").getDescription()).isEqualTo("No trailing space");
    }

    /**
     * Adds an attribute to the schema, without a space between an extension and the
     * right paren.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAttributeExtensionWithoutSpace() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "attributeTypes: ( foo-oid NAME 'foo' SUP name DESC 'No trailing space' X-SCHEMA-FILE '99-test.ldif')"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();
        // No warnings
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getAttributeType("foo").getDescription()).isEqualTo("No trailing space");
    }

    /**
     * Adds a ldapsyntax to the schema. Ldapsyntaxes define allowable values can
     * be used for an attribute.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddLdapSyntax() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "ldapSyntaxes:"
                + " ( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Add a new ldapsyntax' )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();
        // No warnings
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getSyntaxes()).isNotEmpty();
        assertThat(schema.getSyntax("1.3.6.1.4.1.1466.115.121.1.15").getDescription()).isEqualTo(
                "Add a new ldapsyntax");
    }

    /**
     * Attempt to add a malformed ldap syntax. The schema is created but
     * contains several warnings about the encountered errors.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMalformedLdapSyntax() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "ldapSyntaxes:" // malformed syntax
                + " ( 1.3.6.1.4.1.1466.115.121.1.15 MALFORMEDTOKEN 'binary' )"

        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, false);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema sc = builder.toSchema();
        // The schema must contain a warning!
        assertThat(sc.getWarnings()).isNotEmpty();
        assertThat(sc.getWarnings().toString()).contains("illegal token \"MALFORMEDTOKEN\"");
    }

    /**
     * Add a matching rule use provided by the entry to the schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMatchingRuleUse() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "matchingRuleUse: ( 2.5.13.16 NAME 'bitStringMatch' APPLIES ( givenName $ surname ) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, false);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getMatchingRuleUses()).isNotEmpty();
        assertThat(schema.getMatchingRuleUse("bitStringMatch").toString()).isEqualTo(
                "( 2.5.13.16 NAME 'bitStringMatch' APPLIES ( givenName $ surname ) )");
        // The schema do not contain warnings.
        assertThat(schema.getWarnings()).isEmpty();
    }

    /**
     * Try to add a malformed Matching Rule Use provided by the entry. The
     * Matching Rule Use is not applied to the schema but it contains the
     * warnings about it.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMalformedMatchingRuleUse() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "matchingRuleUse: ( 2.5.13.16 NAM 'bitStringMatch' APPLIES ( givenName $ surname ) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, false);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getMatchingRuleUses()).isEmpty();
        // The schema must contain warnings ( : illegal token "NAM")
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("illegal token");
    }

    /**
     * Adds a matching rule via the entry to the new schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMatchingRule() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "matchingRules: ( 2.5.13.16 NAME 'bitStringMatch'"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.6 )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getMatchingRules()).isNotEmpty();
        assertThat(schema.getMatchingRule("bitStringMatch").toString()).isEqualTo(
                "( 2.5.13.16 NAME 'bitStringMatch' SYNTAX 1.3.6.1.4.1.1466.115.121.1.6 )");
        // The schema do not contain warnings.
        assertThat(schema.getWarnings()).isEmpty();
    }

    /**
     * Try to add a Matching Rule via the entry to the new schema but the
     * matchingRule is 'malformed'. Warnings can be found in the .getWarnings()
     * function.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMalformedMatchingRule() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "matchingRules: ( 2.5.13.16 NAME 'bitStringMatch'"
                + " SYNTAXE 1.3.6.1.4.1.1466.115.121.1.6 )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getMatchingRuleUses()).isEmpty();
        // The schema does contain warning (e.g : it contains an illegal token "SYNTAXE")
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("illegal token");
    }

    /**
     * Add a DITContentRule to the schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddDITContentRule() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "DITContentRules: ( 2.5.6.4 DESC 'content rule for organization' NOT "
                        + "( x121Address $ telexNumber ) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getCoreSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getDITContentRules()).isNotEmpty();
        assertThat(schema.getDITContentRule("2.5.6.4").toString())
                .isEqualTo(
                        "( 2.5.6.4 DESC 'content rule for organization' NOT ( x121Address $ telexNumber ) )");
        // The schema do not contain warnings.
        assertThat(schema.getWarnings()).isEmpty();
    }

    /**
     * Try to add a malformed DITContentRule to the schema. Warnings are added
     * to the schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMalformedDITContentRule() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "DITContentRules: ( 2.5.6.4 DESCS 'content rule for organization' NOT "
                    + "( x121Address $ telexNumber ) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getDefaultSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getDITContentRules()).isEmpty();
        // The schema does contain warnings(eg. it contains an illegal token "DESCS")
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains("illegal token");
    }

    /**
     * Adding a new objectclass to the schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddObjectClass() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "objectClasses:  ( 1.3.6.1.4.1.36733.2.1.1.15.1 NAME 'myNewClass'"
                + " SUP top MUST ( cn ) MAY (sn $ uid) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getDefaultSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getDITContentRules()).isEmpty();
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getObjectClass("myNewClass").getOID()).isEqualTo(
                "1.3.6.1.4.1.36733.2.1.1.15.1");
    }

    /**
     * Attempt to add a new object class to the schema but forget to declare the
     * linked attribute.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderWithEntryAddMalformedObjectClass() throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "objectClasses: ( 1.3.6.1.4.1.36733.2.1.1.15.1 NAME 'myNewClass'"
                + " SUP top MUST ( unknownAttribute ) MAY (sn $ uid) )"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(Schema.getDefaultSchema(), false);
        builder.addSchema(e, true);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        Schema schema = builder.toSchema();

        assertThat(schema.getDITContentRules()).isEmpty();
        // The schema does contain warnings :
        assertThat(schema.getWarnings()).isNotEmpty();
        assertThat(schema.getWarnings().toString()).contains(
                "\"unknownAttribute\" which is not defined in the schema");
    }

    /**
     * Adding a description in the attribute types.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAddAttributeWithEntryContainingDescriptionWithCoreSchema()
            throws Exception {
        // @formatter:off
        final String[] strEntry = {
            "dn: cn=schema",
            "objectClass: top",
            "objectClass: ldapSubentry",
            "objectClass: subschema",
            "cn: schema",
            "attributeTypes: ( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY"
                + " caseIgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
            "objectClasses: ( temporary-fake-oc-id NAME 'myCustomObjClass'"
                + " SUP top AUXILIARY MAY myCustomAttribute )",
            "modifiersName: cn=Directory Manager,cn=Root DNs,cn=config",
            "modifyTimestamp: 20110620095948Z"
        };
        // @formatter:on
        final Entry e = new LinkedHashMapEntry(strEntry);
        final SchemaBuilder builder = new SchemaBuilder(e);

        assertThat(e.getAttribute(Schema.ATTR_LDAP_SYNTAXES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_OBJECT_CLASSES)).isNotNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULE_USE)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_MATCHING_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_CONTENT_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES)).isNull();
        assertThat(e.getAttribute(Schema.ATTR_NAME_FORMS)).isNull();

        // Need to add the core schema for the standards attributes.
        builder.addSchema(Schema.getCoreSchema(), false);
        Schema schema = builder.toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getAttributeType("myCustomAttribute")).isNotNull();
        assertThat(schema.getAttributeType("myCustomAttribute").getNameOrOID()).isEqualTo(
                "myCustomAttribute");
        assertThat(schema.getAttributeType("myCustomAttribute").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Directory String' X-ORIGIN 'RFC 4512' )");
        assertThat(schema.getAttributeType("myCustomAttribute").getUsage().toString()).isEqualTo(
                "userApplications");
        assertThat(schema.getAttributeType("myCustomAttribute").getDescription())
                .isEqualTo("A short description");

        assertThat(schema.getObjectClassesWithName("myCustomObjClass")).isNotEmpty();
    }

    /**
     * Similar to the previous code, adding a description in the attribute
     * types. The description is properly inserted in the schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAddAttributeContainingDescriptionWithCoreSchema()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the customclass
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
        // Adding default core schema
        scBuild.addSchema(Schema.getCoreSchema(), false);
        Schema schema = scBuild.toSchema();

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getAttributeType("myCustomAttribute")).isNotNull();
        assertThat(schema.getAttributeType("myCustomAttribute").getNameOrOID()).isEqualTo(
                "myCustomAttribute");
        assertThat(schema.getAttributeType("myCustomAttribute").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Directory String' X-ORIGIN 'RFC 4512' )");
        assertThat(schema.getAttributeType("myCustomAttribute").getUsage().toString()).isEqualTo(
                "userApplications");
        assertThat(schema.getAttributeType("myCustomAttribute").getDescription())
                .isEqualTo("A short description");
        assertThat(schema.getObjectClassesWithName("myCustomObjClass")).isNotEmpty();
    }

    /**
     * Adding an attribute to the schema using a wrong 'USAGE' is impossible.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddAttributeDoesntAllowWrongUsage() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the customclass
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE wrongUsage )", false);
    }

    /**
     * The schema builder doesn't allow a null attribute type.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testSchemaBuilderAddAttributeTypeDoesntAllowNull() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the customclass
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType((String) null, false);
    }

    /**
     * The schema builder doesn't allow an empty attribute type.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddAttributeTypeDoesntAllowEmptyString() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the customclass
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(" ", false);

    }

    /**
     * Schema Builder doesn't allow to add attribute when left parenthesis is
     * missing.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddAttributeDoesntAllowLeftParenthesisMising()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getDefaultSchema());
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                " temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
    }

    /**
     * Schema Builder doesn't allow to add attribute when right parenthesis is
     * missing.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddAttributeDoesntAllowRightParenthesisMising()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getDefaultSchema());
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                " ( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications ",
                false);
    }

    /**
     * Add an attribute using the string definition and the constructor.
     * Verifying the equality between the two.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderCompareAddAttributeTypesSucceed() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getDefaultSchema());
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);

        Schema sc = scBuild.toSchema();
        assertThat(sc.getAttributeType("myCustomAttribute").getDescription()).isEqualTo(
                "A short description");
        assertThat(sc.getAttributeType("myCustomAttribute").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Directory String' X-ORIGIN 'RFC 4512' )");

        // Description changes in this builder :
        final SchemaBuilder scBuild2 = new SchemaBuilder(Schema.getDefaultSchema());
        scBuild2.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);

        scBuild2.buildAttributeType("temporary-fake-attr-id")
                .names("myCustomAttribute")
                .description("The new attribute type")
                .equalityMatchingRule("caseIgnoreOrderingMatch")
                .orderingMatchingRule("caseIgnoreOrderingMatch")
                .substringMatchingRule("caseIgnoreSubstringsMatch")
                .syntax("1.3.6.1.4.1.1466.115.121.1.15")
                .usage(AttributeUsage.USER_APPLICATIONS)
                .addToSchemaOverwrite();
        Schema sc2 = scBuild2.toSchema();

        assertThat(sc2.getAttributeType("myCustomAttribute").getDescription()).isEqualTo(
                "The new attribute type");
        assertThat(sc2.getAttributeType("myCustomAttribute").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.15 DESC 'Directory String' X-ORIGIN 'RFC 4512' )");

        assertThat(sc2.getAttributeType("myCustomAttribute")).isEqualTo(
                sc.getAttributeType("myCustomAttribute"));
    }

    /**
     * The objectClass defined in the schema builder need a left parenthesis.
     * RFC4512. Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddObjectClassDoesntAllowMalformedObjectClass()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Left parenthesis is missing underneath
        scBuild.addObjectClass(" temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
    }

    /**
     * The schema builder rejects malformed object class definition. Must throw
     * an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testSchemaBuilderAddObjectClassDoesntAllowMalformedObjectClassIllegalToken()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Wrong object class definition (AUXI)
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXI MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
    }

    /**
     * Rewrite an existing object class.
     */
    @Test
    public final void testSchemaBuilderAddObjectClass() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(getDefaultSchema());
        scBuild.buildObjectClass("2.5.6.14")
                .names("device")
                .description("New description for the new existing Object Class")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("seeAlso", "ou", "l", "description")
                .extraProperties(SCHEMA_PROPERTY_ORIGIN, "RFC 4519")
                .addToSchemaOverwrite();
        Schema sc = scBuild.toSchema();

        assertThat(sc.getWarnings()).isEmpty();
        assertThat(sc.getObjectClass("device").getOID()).isEqualTo("2.5.6.14");
        assertThat(sc.getObjectClass("device").getDescription()).isEqualTo(
                "New description for the new existing Object Class");
    }

    /**
     * The builder doesn't allow conflicting attributes marked as overwrite
     * false. Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingAttributesOverwriteFalse()
            throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();

        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);

        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
    }

    @Test
    public final void testSchemaBuilderWithAttributeUsageDifferentFromSuperior() {
        final SchemaBuilder scBuild = new SchemaBuilder();

        // directoryOperation can't inherit from userApplications
        scBuild.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                + " SUBSTR caseIgnoreSubstringsMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                + " NO-USER-MODIFICATION USAGE directoryOperation )", true);
        scBuild.addSchema(Schema.getCoreSchema(), false);
        Schema schema = scBuild.toSchema();
        assertThat(schema.getWarnings()).hasSize(1);
        assertThat(schema.getWarnings().toString()).contains("attribute usage directoryOperation is not the same");
    }

    /**
     * The builder allows to have twin or more attributes if it can overwrite
     * them. Only the last is kept. Attributes may have same OID and name.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingAttributesOverwriteTrue() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder();

        //@formatter:off
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                true);

        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'Another description' "
                + "EQUALITY objectIdentifierFirstComponentMatch"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.37"
                + " USAGE directoryOperation )",
                true);
        //@formatter:on

        scBuild.addSchema(Schema.getCoreSchema(), false);
        Schema schema = scBuild.toSchema();
        assertThat(schema.getWarnings()).isEmpty();

        assertThat(schema.getAttributeType("myCustomAttribute")).isNotNull();
        assertThat(schema.getAttributeType("myCustomAttribute").getOID()).isEqualTo(
                "temporary-fake-attr-id");
        assertThat(schema.getAttributeType("myCustomAttribute").getSyntax().toString())
                .isEqualTo(
                        "( 1.3.6.1.4.1.1466.115.121.1.37 DESC 'Object Class Description' X-ORIGIN 'RFC 4512' )");
        assertThat(schema.getAttributeType("myCustomAttribute").getUsage().toString()).isEqualTo(
                "directoryOperation");
    }

    /**
     * The builder doesn't allow conflicting DIT Structure Rules marked as
     * overwrite false. Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingDITOverwriteFalse() throws Exception {
        //@formatter:off
        new SchemaBuilder(Schema.getDefaultSchema())
                .addObjectClass(
                "( testditstructureruleconstraintssupoc-oid "
                    + "NAME 'testDITStructureRuleConstraintsSupOC' SUP top "
                    + "STRUCTURAL MUST ou X-ORIGIN 'SchemaBackendTestCase')", false)
                .addObjectClass(
                "( testditstructureruleconstraintssuboc-oid "
                + "NAME 'testDITStructureRuleConstraintsSubOC' SUP top "
                + "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')", false)
                .addNameForm(
                "( testditstructureruleconstraintsupsnf-oid "
                + "NAME 'testDITStructureRuleConstraintsSupNF' "
                + "OC testDITStructureRuleConstraintsSupOC MUST ou "
                + "X-ORIGIN 'SchemaBackendTestCase' )", false)
                .addNameForm(
                "( testditstructureruleconstraintsubsnf-oid "
                + "NAME 'testDITStructureRuleConstraintsSubNF' "
                + "OC testDITStructureRuleConstraintsSubOC MUST cn "
                + "X-ORIGIN 'SchemaBackendTestCase' )", false)
                .addDITStructureRule(
                "( 999014 " + "NAME 'testDITStructureRuleConstraintsSup' "
                + "FORM testDITStructureRuleConstraintsSupNF "
                + "X-ORIGIN 'SchemaBackendTestCase' )", false)
                .addDITStructureRule(
                "( 999014 " + "NAME 'testDITStructureRuleConstraintsSup' "
                + "DESC 'A short description' FORM testDITStructureRuleConstraintsSupNF "
                + "X-ORIGIN 'SchemaBackendTestCase' )", false)
            .toSchema();
        //@formatter:on
    }

    /**
     * The builder allows conflicting DIT Structure Rules marked as overwrite
     * true.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingDITStructureRuleOverwriteTrue()
            throws Exception {
        // @formatter:off
        final Schema schema =
            new SchemaBuilder(Schema.getDefaultSchema())
                    .addObjectClass(
                        "( testditstructureruleconstraintssupoc-oid "
                        + "NAME 'testDITStructureRuleConstraintsSupOC' SUP top "
                        + "STRUCTURAL MUST ou X-ORIGIN 'SchemaBackendTestCase')", false)
                    .addObjectClass(
                    "( testditstructureruleconstraintssuboc-oid "
                    + "NAME 'testDITStructureRuleConstraintsSubOC' SUP top "
                    + "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')", false)
                    .addNameForm(
                    "( testditstructureruleconstraintsupsnf-oid "
                        + "NAME 'testDITStructureRuleConstraintsSupNF' "
                        + "OC testDITStructureRuleConstraintsSupOC MUST ou "
                        + "X-ORIGIN 'SchemaBackendTestCase' )", false)
                    .addNameForm(
                    "( testditstructureruleconstraintsubsnf-oid "
                        + "NAME 'testDITStructureRuleConstraintsSubNF' "
                        + "OC testDITStructureRuleConstraintsSubOC MUST cn "
                        + "X-ORIGIN 'SchemaBackendTestCase' )", false)
                    .addDITStructureRule(
                    "( 999014 " + "NAME 'testDITStructureRuleConstraintsSup' "
                        + "FORM testDITStructureRuleConstraintsSupNF "
                        + "X-ORIGIN 'SchemaBackendTestCase' )", true)
                    .addDITStructureRule(
                    "( 999014 " + "NAME 'testDITStructureRuleConstraintsSup' "
                        + "DESC 'A short description' FORM testDITStructureRuleConstraintsSupNF "
                        + "X-ORIGIN 'SchemaBackendTestCase' )", true)
                    .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getDITStructureRulesWithName("testAllowATRequiredByDCR")).isNotNull();
        assertThat(schema.getDITStructureRule(999014).getDescription()).isEqualTo(
                "A short description");
        assertThat(schema.getDITStructureRule(999014).getNameOrRuleID()).isEqualTo(
                "testDITStructureRuleConstraintsSup");
    }

    /**
     * Add a content Rule with the builder.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAddDITContentRuleBuilder() throws Exception {
        // @formatter:off
        final Schema schema =
            new SchemaBuilder(Schema.getCoreSchema())
                .addObjectClass("( 2.16.840.1.113730.3.2.2 NAME 'myCustomObjClass"
                    + "' SUP top)", false)
                .addDITContentRule(
                    "( 2.16.840.1.113730.3.2.2"
                        + " NAME 'inetOPerson'"
                        + " DESC 'inetOrgPerson is defined in RFC2798'"
                        + ")", true)
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getDITContentRule("inetOPerson")).isNotNull();
        assertThat(schema.getDITContentRule("inetOPerson").getNameOrOID()).isEqualTo(
                "inetOPerson");
        assertThat(schema.getDITContentRule("inetOPerson").getDescription()).isEqualTo(
                "inetOrgPerson is defined in RFC2798");
    }

    /**
     * The builder doesn't allow conflicting DIT Structure Rules marked as
     * overwrite false. Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingDITContentRuleOverwriteFalse()
            throws Exception {
        // @formatter:off
        new SchemaBuilder(Schema.getDefaultSchema())
            .addObjectClass(
                "( testdontallowattributeprohibitedbydcroc-oid"
                    + " NAME 'testDontAllowAttributeProhibitedByDCROC' SUP top"
                    + " STRUCTURAL MUST cn MAY description"
                    + " X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
            .addDITContentRule(
                "( testdontallowattributeprohibitedbydcroc-oid"
                    + " NAME 'testDontAllowAttributeProhibitedByDCR' NOT description"
                    + " X-ORIGIN 'EntrySchemaCheckingTestCase2' )", false)
            .addDITContentRule(
                    "( testdontallowattributeprohibitedbydcroc-oid"
                        + " NAME 'testDontAllowAttributeProhibitedByDCR'"
                        + " DESC 'Ensure attributes prohibited' NOT description"
                        + " X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();
        // @formatter:on
    }

    /**
     * The builder allows conflicting DIT Content Rules marked as overwrite
     * true. Schema checking for an entry covered by a DIT content rule to
     * ensure that attributes prohibited by the DIT content rule are not allowed
     * even if they are allowed by the associated object classes. (cf.
     * EntrySchemaCheckingTestCase)
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingDITContentRuleOverwriteTrue()
            throws Exception {
        // @formatter:off
        final Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
            .addObjectClass(
                "( testdontallowattributeprohibitedbydcroc-oid"
                    + " NAME 'testDontAllowAttributeProhibitedByDCROC' SUP top"
                    + " STRUCTURAL MUST cn MAY description"
                    + " X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
            .addDITContentRule(
                "( testdontallowattributeprohibitedbydcroc-oid"
                    + " NAME 'testDontAllowAttributeProhibitedByDCR' NOT description"
                    + " X-ORIGIN 'EntrySchemaCheckingTestCase2' )", true)
            .addDITContentRule(
                    "( testdontallowattributeprohibitedbydcroc-oid"
                        + " NAME 'testDontAllowAttributeProhibitedByDCR'"
                        + " DESC 'Ensure attributes prohibited' NOT description"
                        + " X-ORIGIN 'EntrySchemaCheckingTestCase' )", true).toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getDITContentRule("testDontAllowAttributeProhibitedByDCR")).isNotNull();
        assertThat(
                schema.getDITContentRule("testDontAllowAttributeProhibitedByDCR").getDescription())
                .isEqualTo("Ensure attributes prohibited");
        assertThat(
                schema.getDITContentRule("testDontAllowAttributeProhibitedByDCR").getNameOrOID())
                .isEqualTo("testDontAllowAttributeProhibitedByDCR");
    }

    /**
     * The builder doesn't allow conflicting Matching Rules marked as overwrite
     * false. Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingMatchingRuleOverwriteFalse()
            throws Exception {
        // @formatter:off
        new SchemaBuilder(Schema.getDefaultSchema())
                .addMatchingRule(
                "( 2.5.13.16 NAME 'bitStringMatche'"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.7 )", false)
                .addMatchingRule(// Matching rules from RFC 2252
                        "( 2.5.13.16 NAME 'bitStringMatch'"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.6 )", false)
                .toSchema();
        // @formatter:on
    }

    /**
     * The builder allows conflicting Matching Rules marked as overwrite true.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingMatchingRuleOverwriteTrue()
            throws Exception {
        // @formatter:off
        final Schema schema =
            new SchemaBuilder(Schema.getDefaultSchema())
                    .addMatchingRule(
                    "( 2.5.13.16 NAME 'bitStringMatche'"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.7 )", true)
                    .addMatchingRule(// Matching rules from RFC 2252
                            "( 2.5.13.16 NAME 'bitStringMatch'"
                            + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.6 )", true)
                    .toSchema();
        // @formatter:on
        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getMatchingRule("bitStringMatch")).isNotNull();
        assertThat(schema.getMatchingRule("bitStringMatch").getOID()).isEqualTo("2.5.13.16");
        assertThat(schema.getMatchingRule("bitStringMatch").getSyntax().toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.6 DESC 'Bit String' X-ORIGIN 'RFC 4512' )");
    }

    /**
     * The builder doesn't allow conflicting Matching Rules marked as overwrite
     * false. Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingMatchingRuleUseOverwriteFalse()
            throws Exception {
        // @formatter:off
        new SchemaBuilder(Schema.getDefaultSchema())
            .addMatchingRuleUse(
                    "( 2.5.13.16 APPLIES ( givenName $ name ) )", false)
            .addMatchingRuleUse(
                        "( 2.5.13.16 APPLIES ( givenName $ surname ) )", false)
            .toSchema();
        // @formatter:on
    }

    /**
     * The builder allows conflicting Matching Rules marked as overwrite true.
     * Cf. RFC 4517 3.3.20.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingMatchingRuleUseOverwriteTrue()
            throws Exception {
        // @formatter:off
        final Schema schema =
            new SchemaBuilder(Schema.getDefaultSchema())
                .addMatchingRuleUse(
                    "( 2.5.13.16 NAME 'bitStringMatch' APPLIES ( givenName $ name ) )", true)
                .addMatchingRuleUse(
                    "( 2.5.13.16 NAME 'bitStringMatch' APPLIES ( givenName $ surname ) )", true)
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getMatchingRuleUsesWithName("bitStringMatch")).isNotEmpty();
        assertThat(schema.getMatchingRuleUses().size()).isEqualTo(1);

        for (MatchingRuleUse o : schema.getMatchingRuleUses()) {
            assertThat(o.getNameOrOID()).isEqualTo("bitStringMatch");
            assertThat(o.getMatchingRuleOID()).isEqualTo("2.5.13.16");
            assertThat(o.getMatchingRule().toString()).isEqualTo(
                    "( 2.5.13.16 NAME 'bitStringMatch' SYNTAX 1.3.6.1.4.1.1466.115.121.1.6"
                            + " X-ORIGIN 'RFC 4512' )");
        }
        assertThat(schema.getWarnings()).isEmpty();

    }

    /**
     * The builder doesn't allow conflicting NameForm marked as overwrite false.
     * Must throw a ConflictingSchemaElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public final void testSchemaBuilderDoesntAllowConflictingNameFormOverwriteFalse()
            throws Exception {
        // @formatter:off
        new SchemaBuilder(Schema.getDefaultSchema())
            .addNameForm(
                "( testviolatessinglevaluednameform-oid "
                    + "NAME 'testViolatesSingleValuedNameForm' "
                    + "OC testViolatesSingleValuedNameFormOC MUST cn "
                    + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false)
            .addNameForm(
                "( testviolatessinglevaluednameform-oid "
                    + "NAME 'testViolatesSingleValuedNameForm' "
                    + "OC testViolatesSingleValuedNameFormOC MUST cn "
                    + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false)
            .toSchema();
        // @formatter:on
    }

    /**
     * The builder allows conflicting Name Form marked as overwrite true.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAllowsConflictingNameFormOverwriteTrue() throws Exception {
        // @formatter:off
        final Schema schema =
            new SchemaBuilder(Schema.getDefaultSchema())
                .addObjectClass(
                    "( testviolatessinglevaluednameformoc-oid "
                            + "NAME 'testViolatesSingleValuedNameFormOC' SUP top STRUCTURAL "
                            + "MUST cn MAY description X-ORIGIN 'EntrySchemaCheckingTestCase')",
                    false)
                .addNameForm(
                    "( testviolatessinglevaluednameform-oid "
                        + "NAME 'testViolatesSingleValuedNameForm' "
                        + "OC testViolatesSingleValuedNameFormOC MUST sn "
                        + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", true)
                .addNameForm(
                    "( testviolatessinglevaluednameform-oid "
                        + "NAME 'testViolatesSingleValuedNameForm' "
                        + "OC testViolatesSingleValuedNameFormOC MUST cn "
                        + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", true)
                .toSchema();
        // @formatter:on

        assertThat(schema.getWarnings()).isEmpty();
        assertThat(schema.getNameFormsWithName("testViolatesSingleValuedNameForm")).isNotNull();
        for (NameForm o : schema.getNameForms()) {
            assertThat(o.getNameOrOID()).isEqualTo("testViolatesSingleValuedNameForm");
            assertThat(o.getOID()).isEqualTo("testviolatessinglevaluednameform-oid");
            assertThat(o.getStructuralClass().getOID()).isEqualTo(
                    "testviolatessinglevaluednameformoc-oid");
        }
    }

    /**
     * Use the schema builder to remove an attribute.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveAttributeType() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getDefaultSchema());
        scBuild.addObjectClass("( temporary-fake-oc-id NAME 'myCustomObjClass"
                + "' SUP top AUXILIARY MAY myCustomAttribute )", false);
        scBuild.addAttributeType(
                "( temporary-fake-attr-id NAME 'myCustomAttribute' DESC 'A short description' EQUALITY case"
                        + "IgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR caseIgnoreSubstrings"
                        + "Match SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 USAGE userApplications )",
                false);
        boolean isRemoved = scBuild.removeAttributeType("myCustomAttribute");
        assertThat(isRemoved).isTrue();
        Schema schema = scBuild.toSchema();
        // The following line throws an exception :
        assertThat(schema.getAttributeType("myCustomAttribute")).isNull();
    }

    /**
     * Use the schema builder to removing a non existent attribute type. Do
     * Nothing.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantAttributeType() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeAttributeType("wrongName");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a non existent syntax.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantSyntax() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeSyntax("1.3.6.1.4.1.14aa");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to remove a syntax.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveSyntax() throws Exception {

        assertThat(Schema.getCoreSchema().getSyntax("1.3.6.1.4.1.1466.115.121.1.15")).isNotNull();
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getDefaultSchema());

        boolean isRemoved = scBuild.removeSyntax("1.3.6.1.4.1.1466.115.121.1.15");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();
        assertThat(sc.getSyntax("1.3.6.1.4.1.1466.115.121.1.15")).isNull();
    }

    /**
     * Use the schema builder to remove a DIT Content Rule.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveDitContentRule() throws Exception {
        // @formatter:off
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        scBuild.addObjectClass("( 2.16.840.1.113730.3.2.2 NAME 'myCustomObjClass"
                + "' SUP top)", false);
        scBuild.addDITContentRule(
                    "( 2.16.840.1.113730.3.2.2"
                            + " NAME 'inetOPerson'"
                            + " DESC 'inetOrgPerson is defined in RFC2798'"
                            + ")", true);
        // @formatter:on

        boolean isRemoved = scBuild.removeDITContentRule("inetOPerson");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();
        for (DITContentRule dit : sc.getDITContentRules()) {
            assertThat(dit.getNameOrOID()).isNotEqualTo("inetOPerson");
        }
    }

    /**
     * Use the schema builder to removing a non existent DIT Content Rule.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantDitContentRule() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeDITContentRule("badDITContentRule");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a DIT Structure Rule.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveDitStructureRule() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        scBuild.addObjectClass(
                "( testditstructureruleconstraintssupoc-oid "
                        + "NAME 'testDITStructureRuleConstraintsSupOC' SUP top "
                        + "STRUCTURAL MUST ou X-ORIGIN 'SchemaBackendTestCase')", false)
                .addObjectClass(
                        "( testditstructureruleconstraintssuboc-oid "
                                + "NAME 'testDITStructureRuleConstraintsSubOC' SUP top "
                                + "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')", false)
                .addNameForm(
                        "( testditstructureruleconstraintsupsnf-oid "
                                + "NAME 'testDITStructureRuleConstraintsSupNF' "
                                + "OC testDITStructureRuleConstraintsSupOC MUST ou "
                                + "X-ORIGIN 'SchemaBackendTestCase' )", false).addNameForm(
                        "( testditstructureruleconstraintsubsnf-oid "
                                + "NAME 'testDITStructureRuleConstraintsSubNF' "
                                + "OC testDITStructureRuleConstraintsSubOC MUST cn "
                                + "X-ORIGIN 'SchemaBackendTestCase' )", false).addDITStructureRule(
                        "( 999014 " + "NAME 'testDITStructureRuleConstraintsSup' "
                                + "FORM testDITStructureRuleConstraintsSupNF "
                                + "X-ORIGIN 'SchemaBackendTestCase' )", true);

        boolean isRemoved = scBuild.removeDITStructureRule(999014);
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();

        assertThat(sc.getDITStructureRule(999014)).isNull();
    }

    /**
     * Use the schema builder to removing a non existent DIT Structure Rule.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantDitStructureRule() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeDITStructureRule(999014);
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a Matching Rule.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveMatchingRule() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        scBuild.addMatchingRule(
                // Matching rules from RFC 2252
                "( 2.5.13.16 NAME 'bitStringMatch'" + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.6 )",
                false);

        boolean isRemoved = scBuild.removeMatchingRule("bitStringMatch");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();

        assertThat(sc.getMatchingRule("bitStringMatch")).isNull();
    }

    /**
     * Use the schema builder to removing a non existent Matching Rule.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantMatchingRule() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeMatchingRule("bitStringMatchZ");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a Matching Rule Use.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveMatchingRuleUSe() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        scBuild.addMatchingRuleUse(
                "( 2.5.13.16 NAME 'bitStringMatch' APPLIES ( givenName $ surname ) )", false);

        boolean isRemoved = scBuild.removeMatchingRuleUse("bitStringMatch");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();

        assertThat(sc.getMatchingRuleUse("bitStringMatch")).isNull();
    }

    /**
     * Use the schema builder to removing a non existent Matching Rule Use.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantMatchingRuleUse() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeMatchingRuleUse("bitStringMatchZ");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a Name Form.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveNameForm() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        scBuild.addNameForm("( testviolatessinglevaluednameform-oid "
                + "NAME 'testViolatesSingleValuedNameForm' "
                + "OC testViolatesSingleValuedNameFormOC MUST cn "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false);

        boolean isRemoved = scBuild.removeNameForm("testViolatesSingleValuedNameForm");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();
        assertThat(sc.getNameForm("testViolatesSingleValuedNameForm")).isNull();
    }

    /**
     * Use the schema builder to removing a non existent Name Form.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantNameForm() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeNameForm("bitStringMatchZ");
        assertThat(isRemoved).isFalse();
    }

    /**
     * Use the schema builder to removing a Object class.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownSchemaElementException.class)
    public final void testSchemaBuilderRemoveObjectClass() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder();
        scBuild.addObjectClass("( 2.5.6.6 NAME 'person' SUP top STRUCTURAL MUST ( sn $ cn )"
                + " MAY ( userPassword $ telephoneNumber $ seeAlso $ description )"
                + " X-ORIGIN 'RFC 4519' )", false);

        boolean isRemoved = scBuild.removeObjectClass("person");
        assertThat(isRemoved).isTrue();
        Schema sc = scBuild.toSchema();
        assertThat(sc.getObjectClass("person")).isNull();
    }

    /**
     * Use the schema builder to removing a non existent Object class.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderRemoveInexistantObjectClass() throws Exception {
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        boolean isRemoved = scBuild.removeObjectClass("bitStringMatchZ");
        assertThat(isRemoved).isFalse();
    }

    /**
     * AddSchemaForEntry doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testSchemaBuilderAddSchemaForEntryDoesntAllowNull() throws Exception {

        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());
        scBuild.addSchemaForEntry(null, null, false);
    }

    /**
     * Try to addSchemaForEntry but the entry doesn't include the
     * subschemaSubentry attribute. Exception expected : The entry
     * uid=scarter,ou=People,dc=example,dc=com does not include a
     * subschemaSubentry attribute !
     *
     * @throws Exception
     */
    @Test(expectedExceptions = EntryNotFoundException.class)
    public final void testSchemaBuilderAddSchemaForEntryDoesntContainSubschemaMockConnection()
            throws Exception {
        Connection connection = mock(Connection.class);
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());

        // @formatter:off
        final String[] entry = {
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "objectClass: person",
            "objectClass: inetorgperson",
            "objectClass: organizationalperson",
            "objectClass: top",
            "postalAddress: Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
            "postalCode: 50369", "uid: user.0"
        };

        when(
            connection.searchSingleEntry((SearchRequest) any()))
                .thenReturn(Responses.newSearchResultEntry(entry));
        // @formatter:on

        scBuild.addSchemaForEntry(connection,
                DN.valueOf("uid=scarter,ou=People,dc=example,dc=com"), false);

    }

    /**
     * Retrieving an LDAP Server's schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAddSchemaForEntryMockConnection() throws Exception {
        Connection connection = mock(Connection.class);
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());

        // @formatter:off
        final String[] entry = {
            "# Search result entry: uid=bjensen,ou=People,dc=example,dc=com",
            "dn: uid=bjensen,ou=People,dc=example,dc=com",
            "subschemaSubentry: cn=schema",
            "entryDN: uid=bjensen,ou=people,dc=example,dc=com",
            "entryUUID: fc252fd9-b982-3ed6-b42a-c76d2546312c"
            // N.B : also works with previous example but needs the subschemaSubentry line.
        };

        // Send a search entry result :
        when(
            connection.searchSingleEntry((SearchRequest) any()))
                .thenReturn(Responses.newSearchResultEntry(entry));
        // @formatter:on

        scBuild.addSchemaForEntry(connection,
                DN.valueOf("uid=bjensen,ou=People,dc=example,dc=com"), false);

        Schema sc = scBuild.toSchema();
        // We retrieve the schema :
        assertThat(sc.getSyntaxes()).isNotNull();
        assertThat(sc.getAttributeTypes()).isNotNull();
        assertThat(sc.getAttributeTypes()).isNotEmpty();
        assertThat(sc.getObjectClasses()).isNotNull();
        assertThat(sc.getObjectClasses()).isNotEmpty();
        assertThat(sc.getMatchingRuleUses()).isNotNull();
        assertThat(sc.getMatchingRuleUses()).isEmpty();
        assertThat(sc.getMatchingRules()).isNotNull();
        assertThat(sc.getMatchingRules()).isNotEmpty();
        assertThat(sc.getDITContentRules()).isNotNull();
        assertThat(sc.getDITContentRules()).isEmpty();
        assertThat(sc.getDITStuctureRules()).isNotNull();
        assertThat(sc.getDITStuctureRules()).isEmpty();
        assertThat(sc.getNameForms()).isNotNull();
        assertThat(sc.getNameForms()).isEmpty();

        connection.close();
    }

    /**
     * Asynchronously retrieving an LDAP Server's schema.
     *
     * @throws Exception
     */
    @Test
    public final void testSchemaBuilderAddSchemaForEntryAsyncMockConnection() throws Exception {
        Connection connection = mock(Connection.class);
        final SchemaBuilder scBuild = new SchemaBuilder(Schema.getCoreSchema());

        // @formatter:off
        final String[] entry = {
            "# Search result entry: uid=bjensen,ou=People,dc=example,dc=com",
            "dn: uid=bjensen,ou=People,dc=example,dc=com",
            "subschemaSubentry: cn=schema",
            "entryDN: uid=bjensen,ou=people,dc=example,dc=com",
            "entryUUID: fc252fd9-b982-3ed6-b42a-c76d2546312c"
            // N.B : also works with previous example but needs the subschemaSubentry line.
        };

        // Send a search entry result promise :
        LdapPromise<SearchResultEntry> result =
                newSuccessfulLdapPromise(Responses.newSearchResultEntry(entry));
        when(connection.searchSingleEntryAsync((SearchRequest) any())).thenReturn(result);
        DN testDN = DN.valueOf("uid=bjensen,ou=People,dc=example,dc=com");
        // @formatter:on
        Schema sc = scBuild.addSchemaForEntryAsync(connection, testDN, false).getOrThrow().toSchema();

        // We retrieve the schema
        assertThat(sc.getSyntaxes()).isNotNull();
        assertThat(sc.getAttributeTypes()).isNotNull();
        assertThat(sc.getAttributeTypes()).isNotEmpty();
        assertThat(sc.getObjectClasses()).isNotNull();
        assertThat(sc.getObjectClasses()).isNotEmpty();
        assertThat(sc.getMatchingRuleUses()).isNotNull();
        assertThat(sc.getMatchingRuleUses()).isEmpty();
        assertThat(sc.getMatchingRules()).isNotNull();
        assertThat(sc.getMatchingRules()).isNotEmpty();
        assertThat(sc.getDITContentRules()).isNotNull();
        assertThat(sc.getDITContentRules()).isEmpty();
        assertThat(sc.getDITStuctureRules()).isNotNull();
        assertThat(sc.getDITStuctureRules()).isEmpty();
        assertThat(sc.getNameForms()).isNotNull();
        assertThat(sc.getNameForms()).isEmpty();

        connection.close();
    }

    @Test
    public void testDefaultSyntax() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).toSchema().asNonStrictSchema();
        assertThat(schema.getDefaultSyntax()).isEqualTo(CoreSchema.getOctetStringSyntax());
        assertThat(schema.getAttributeType("dummy").getSyntax()).isEqualTo(
                CoreSchema.getOctetStringSyntax());
    }

    @Test
    public void testOverrideDefaultSyntax() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                    .setOption(DEFAULT_SYNTAX_OID, getDirectoryStringSyntax().getOID())
                    .toSchema().asNonStrictSchema();
        assertThat(schema.getDefaultSyntax()).isEqualTo(getDirectoryStringSyntax());
        assertThat(schema.getAttributeType("dummy").getSyntax()).isEqualTo(getDirectoryStringSyntax());
    }

    @Test
    public void testDefaultMatchingRule() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema()).toSchema().asNonStrictSchema();
        assertThat(schema.getDefaultMatchingRule()).isEqualTo(
                CoreSchema.getOctetStringMatchingRule());
        assertThat(schema.getAttributeType("dummy").getEqualityMatchingRule()).isEqualTo(
                CoreSchema.getOctetStringMatchingRule());
    }

    @Test
    public void testOverrideMatchingRule() {
        final Schema schema =
                new SchemaBuilder(Schema.getCoreSchema())
                    .setOption(DEFAULT_MATCHING_RULE_OID, getCaseIgnoreMatchingRule().getOID())
                    .toSchema().asNonStrictSchema();
        assertThat(schema.getDefaultMatchingRule()).isEqualTo(
                CoreSchema.getCaseIgnoreMatchingRule());
        assertThat(schema.getAttributeType("dummy").getEqualityMatchingRule()).isEqualTo(
                CoreSchema.getCaseIgnoreMatchingRule());
    }

    @Test
    public void testDefaultSyntaxDefinedInSchema() {
        // The next line was triggering a NPE with OPENDJ-1252.
        final Schema schema =
                new SchemaBuilder().addSyntax("( 9.9.9 DESC 'Test Syntax' )", false).addSyntax(
                        CoreSchema.getOctetStringSyntax().toString(), false).toSchema();

        // Ensure that the substituted syntax is usable.
        assertThat(schema.getSyntax("9.9.9").valueIsAcceptable(ByteString.valueOfUtf8("test"), null))
                .isTrue();
    }

    @Test
    public void testDefaultMatchingRuleDefinedInSchema() throws DecodeException {
        final Schema schema =
                new SchemaBuilder().addSyntax(CoreSchema.getOctetStringSyntax().toString(), false)
                        .addMatchingRule(
                                "( 9.9.9 NAME 'testRule' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
                                false).addMatchingRule(
                                CoreSchema.getOctetStringMatchingRule().toString(), false)
                        .toSchema();

        // Ensure that the substituted rule is usable: was triggering a NPE with OPENDJ-1252.
        assertThat(
                schema.getMatchingRule("9.9.9").normalizeAttributeValue(ByteString.valueOfUtf8("test")))
                .isEqualTo(ByteString.valueOfUtf8("test"));
    }

}
