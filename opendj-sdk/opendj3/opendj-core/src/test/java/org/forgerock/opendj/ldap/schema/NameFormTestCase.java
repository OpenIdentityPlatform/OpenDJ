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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.Test;

/**
 * This class tests the NameForm class.
 */
@SuppressWarnings("javadoc")
public class NameFormTestCase extends SchemaTestCase {

    /**
     * NameForm doesn't allow null OID.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testCreateFormDoesntAllowNullOid() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        new NameForm(null, names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on
    }

    /**
     * NameForm doesn't allow null structuralClassOID.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testCreateFormDoesntAllowNullStructuralClassOID() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        names.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        new NameForm("mynewform-oid", names, "Description of the new form", false,
                null, requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on
    }

    /**
     * NameForm doesn't allow null requiredAttributeOIDs.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testCreateFormDoesntAllowNullRequiredAttributeOIDs() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", null, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on
    }

    /**
     * NameForm doesn't allow null requiredAttributeOIDs.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public final void testCreateFormDoesntAllowEmptyRequiredAttributeOIDs() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();

        // @formatter:off
        new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on
    }

    /**
     * NameForm doesn't allow null requiredAttributeOIDs.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testCreateFormDoesntAllowNullOptionalAttributeOIDs() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, null, extraProperties, null);
        // @formatter:on
    }

    /**
     * Create a new form and compare the result as string with the expected
     * usual form.
     */
    @Test()
    public final void testCreateNewFormWithUniqueName() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        NameForm nf = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);

        assertThat(nf.hasName("MyNewForm")).isTrue();
        assertThat(nf.getOID().toString()).isEqualTo("mynewform-oid");

        assertThat(nf.toString()).isEqualTo(
                "( mynewform-oid NAME 'MyNewForm' DESC 'Description of the new form'"
                + " OC mynewform-oid MUST ( cn $ sn ) X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on
    }

    /**
     * Create a new form without name(s).
     */
    @Test()
    public final void testCreateNewFormWithOnlyOid() {

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        NameForm nf = new NameForm("1.3.6.1.4.1.1466.115.121.1.35", new ArrayList<String>(),
                "Description of the new form", false, "mynewform-oid", requiredAttributeOIDs,
                Collections.<String> emptySet(), extraProperties, null);

        assertThat(nf.hasName("hasAName ?")).isFalse();
        assertThat(nf.getNameOrOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");
        assertThat(nf.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

        assertThat(nf.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 DESC 'Description of the new form'"
                + " OC mynewform-oid MUST ( cn $ sn ) X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on
    }

    /**
     * Create a new form and compare the result as string with the expected
     * usual form.
     */
    @Test()
    public final void testCreateNewForm() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        NameForm nf = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(),
                extraProperties, null);

        assertThat(nf.toString()).isEqualTo(
                "( mynewform-oid NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form' OC mynewform-oid"
                + " MUST ( cn $ sn ) X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on
    }

    /**
     * Create a new form and compare the result as string with the expected
     * usual form.
     */
    @Test()
    public final void testCreateNewFormWithOptionalAttributesOid() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf.toString()).isEqualTo(
                "( mynewform-oid NAME 'MyNewForm' DESC 'Description of the new form'"
                + " OC mynewform-oid MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on
    }

    /**
     * Adds a new form which is containing an OID not provided by the schema.
     * Exception expected : The name form description "MyNewForm" is associated
     * with a structural object class "mynewform-oid" which is not defined in
     * the schema.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = SchemaException.class)
    public final void testNameFormValidateDoesntAllowUnknowNewStructuralObject()
            throws SchemaException {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf1 = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf1.toString()).isEqualTo(
                "( mynewform-oid NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form'"
                + " OC mynewform-oid"
                + " MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on

        List<LocalizableMessage> warnings = new ArrayList<LocalizableMessage>();
        nf1.validate(Schema.getDefaultSchema(), warnings);
    }

    /**
     * Validate a nameForm using an abstract object class instead of an
     * structural object class throws an error.
     *
     * @throws SchemaException
     */
    @Test(expectedExceptions = SchemaException.class)
    public final void testNameFormValidateDoesntAllowAbstractObjectClass() throws SchemaException {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf1 = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "top", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf1.toString()).isEqualTo(
                "( mynewform-oid NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form'"
                + " OC top"
                + " MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on

        List<LocalizableMessage> warnings = new ArrayList<LocalizableMessage>();
        nf1.validate(Schema.getDefaultSchema(), warnings);
    }

    /**
     * Validate a new form without warnings.
     *
     * @throws SchemaException
     */
    @Test()
    public final void testNameFormValidate() throws SchemaException {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf1 = new NameForm("1.3.6.1.4.1.1466.115.121.1.35", names, "Description of the new form", false,
                "person", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf1.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form'"
                // Structural Object class, contained in the core schema:
                + " OC person"
                + " MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on

        List<LocalizableMessage> warnings = new ArrayList<LocalizableMessage>();
        nf1.validate(Schema.getCoreSchema(), warnings);

        assertThat(warnings).isEmpty();
    }

    /**
     * Compare two same nameForm using the equal function.
     */
    @Test()
    public final void testNameFormEqualsTrue() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        NameForm nf1 = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);

        NameForm nf2 = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on

        assertThat(nf1.equals(nf2)).isTrue();
    }

    /**
     * Equals between two 'nameforms' fails.
     */
    @Test()
    public final void testNameFormEqualsFalse() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // @formatter:off
        NameForm nf1 = new NameForm("mynewform-oid", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);

        NameForm nf2 = new NameForm("mynewform-oid2", names, "Description of the new form", false,
                "mynewform-oid", requiredAttributeOIDs, Collections.<String> emptySet(), extraProperties, null);
        // @formatter:on

        assertThat(nf1.getOID()).isEqualTo("mynewform-oid");
        assertThat(nf2.getOID()).isEqualTo("mynewform-oid2");
        // fails if oid is different.
        assertThat(nf1.equals(nf2)).isFalse();
    }

    /**
     * Duplicating a form without validating it doesn't copy OptionalAttributes
     * and RequiredAttributes.
     */
    @Test()
    public final void testNameFormDuplicateDoesntDuplicateAllAttributeWithoutValidateIt() {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        // The set of optional attribute types
        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf1 = new NameForm("1.3.6.1.4.1.1466.115.121.1.35", names, "Description of the new form", false,
                "person", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf1.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form'"
                // Structural Object class, contained in the core schema:
                + " OC person"
                + " MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on

        // Duplicating the 'nameform'.
        NameForm nf2 = nf1.duplicate();

        // Checking if the attributes are the same :
        assertThat(nf2.getDescription()).isEqualTo(nf1.getDescription());
        assertThat(nf2.getDescription()).isEqualTo("Description of the new form");

        assertThat(nf2.getExtraPropertyNames()).isEqualTo(nf1.getExtraPropertyNames());
        assertThat(nf2.getExtraPropertyNames().iterator().next()).isEqualTo("X-ORIGIN");

        assertThat(nf2.getNameOrOID()).isEqualTo(nf1.getNameOrOID());
        assertThat(nf2.getNameOrOID()).isEqualTo("MyNewForm");

        assertThat(nf2.getOID()).isEqualTo(nf1.getOID());
        assertThat(nf2.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

        // Required and optional attributes are empty.
        assertThat(nf2.getOptionalAttributes()).isEmpty();
        assertThat(nf2.getOptionalAttributes()).isEqualTo(nf1.getOptionalAttributes());

        assertThat(nf2.getRequiredAttributes()).isEmpty();
        assertThat(nf2.getRequiredAttributes()).isEqualTo(nf1.getRequiredAttributes());

        assertThat(nf2.getStructuralClass()).isEqualTo(nf1.getStructuralClass());
    }

    /**
     * Duplicating a form succeeds after a schema validation.
     *
     * @throws SchemaException
     */
    @Test()
    public final void testNameFormDuplicateSucceedAfterValidation() throws SchemaException {

        // The set of user defined names for this definition.
        List<String> names = new ArrayList<String>();
        names.add("MyNewForm");
        names.add("TheNewForm");

        // An optional set of extensions for the name form ( X-ORIGIN / X-SCHEMA-FILE)
        Map<String, List<String>> extraProperties = new TreeMap<String, List<String>>();
        List<String> extra = new ArrayList<String>();
        extra.add("EntrySchemaCheckingTestCase");
        extraProperties.put("X-ORIGIN", extra);

        // The set of required attribute types for this name form.
        Set<String> requiredAttributeOIDs = new TreeSet<String>();
        requiredAttributeOIDs.add("sn");
        requiredAttributeOIDs.add("cn");

        Set<String> optionalAttributeOIDs = new TreeSet<String>();
        optionalAttributeOIDs.add("description");
        optionalAttributeOIDs.add("uid");

        // @formatter:off
        NameForm nf1 = new NameForm("1.3.6.1.4.1.1466.115.121.1.35", names, "Description of the new form", false,
                "person", requiredAttributeOIDs, optionalAttributeOIDs, extraProperties, null);

        assertThat(nf1.toString()).isEqualTo(
                "( 1.3.6.1.4.1.1466.115.121.1.35 NAME ( 'MyNewForm' 'TheNewForm' )"
                + " DESC 'Description of the new form'"
                // Structural Object class, contained in the core schema:
                + " OC person"
                + " MUST ( cn $ sn )"
                + " MAY ( description $ uid )"
                + " X-ORIGIN 'EntrySchemaCheckingTestCase' )");
        // @formatter:on

        List<LocalizableMessage> warnings1 = new ArrayList<LocalizableMessage>();
        nf1.validate(Schema.getCoreSchema(), warnings1);

        // Duplicating the 'nameform'.
        NameForm nf2 = nf1.duplicate();

        // Required and optional attributes are empty :
        assertThat(nf2.getOptionalAttributes()).isEmpty();
        assertThat(nf2.getRequiredAttributes()).isEmpty();

        List<LocalizableMessage> warnings2 = new ArrayList<LocalizableMessage>();
        nf2.validate(Schema.getCoreSchema(), warnings2);

        // Checking if the attributes are the same :
        assertThat(nf2.getDescription()).isEqualTo(nf1.getDescription());
        assertThat(nf2.getDescription()).isEqualTo("Description of the new form");

        assertThat(nf2.getExtraPropertyNames()).isEqualTo(nf1.getExtraPropertyNames());
        assertThat(nf2.getExtraPropertyNames().iterator().next()).isEqualTo("X-ORIGIN");

        assertThat(nf2.getNameOrOID()).isEqualTo(nf1.getNameOrOID());
        assertThat(nf2.getNameOrOID()).isEqualTo("MyNewForm");

        assertThat(nf2.getOID()).isEqualTo(nf1.getOID());
        assertThat(nf2.getOID()).isEqualTo("1.3.6.1.4.1.1466.115.121.1.35");

        // Required and optional attributes are not empty :
        assertThat(nf2.getOptionalAttributes()).isNotEmpty();
        assertThat(nf2.getOptionalAttributes()).isEqualTo(nf1.getOptionalAttributes());

        assertThat(nf2.getRequiredAttributes()).isNotEmpty();
        assertThat(nf2.getRequiredAttributes()).isEqualTo(nf1.getRequiredAttributes());

        assertThat(nf2.getStructuralClass()).isEqualTo(nf1.getStructuralClass());
    }
}
