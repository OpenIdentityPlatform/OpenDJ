/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;



import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.defaultPolicy;
import static org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.ignoreAll;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.EntryResolver;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.Policy;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.testng.annotations.Test;



/**
 * Test schema validation using {@link Schema#validateEntry}.
 */
@Test
public class EntrySchemaCheckingTestCase extends SchemaTestCase
{
  /**
   * Ensures that the provided entry fails schema checking validation with
   * strict compliance enabled, but will pass in a more relaxed configuration.
   * <p>
   * Uses the default schema.
   *
   * @param e
   *          The entry to be tested.
   */
  private void failOnlyForStrictEvaluation(Entry e)
  {
    failOnlyForStrictEvaluation(e, Schema.getDefaultSchema());
  }



  /**
   * Ensures that the provided entry fails schema checking validation with
   * strict compliance enabled, but will pass in a more relaxed configuration.
   *
   * @param e
   *          The entry to be tested.
   * @param schema
   *          The schema to use.
   */
  private void failOnlyForStrictEvaluation(Entry e, Schema schema)
  {
    assertThat(
        conformsToSchema(e, schema, defaultPolicy()
            .requireSingleStructuralObjectClass(Policy.IGNORE), null)).isTrue();
    assertThat(
        conformsToSchema(e, schema, defaultPolicy()
            .requireSingleStructuralObjectClass(Policy.WARN), null)).isTrue();
    assertThat(
        conformsToSchema(e, schema, defaultPolicy()
            .requireSingleStructuralObjectClass(Policy.REJECT), null))
        .isFalse();
  }



  /**
   * Tests schema checking for an entry with a valid single structural
   * objectclass.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testValidSingleStructuralClass() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: domain",
        "dc: example");
    // @formatter:on

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry (not covered by a DIT content rule) with
   * a valid single structural objectclass as well as an auxiliary objectclass.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testValidSingleStructuralClassAndAuxiliaryClass()
      throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: organization",
        "objectClass: dcObject",
        "dc: example",
        "o: Example Org");
    // @formatter:on

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry that does not contain a structural
   * objectclass.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testNoStructuralClass() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: dcObject",
        "dc: example");
    // @formatter:on

    failOnlyForStrictEvaluation(e);
  }



  /**
   * Tests schema checking for an entry that contains multiple structural
   * objectclasses.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testMultipleStructuralClasses() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: account",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User");
    // @formatter:on

    failOnlyForStrictEvaluation(e);
  }



  /**
   * Tests schema checking for an entry that contains an undefined objectclass
   * with no other structural class.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testUndefinedStructuralObjectClass() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: xxxundefinedstructuralxxx",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isFalse();
  }



  /**
   * Tests schema checking for an entry that contains an undefined objectclass
   * as well as a valid structural class.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testUndefinedAuxiliaryObjectClass() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: device",
        "objectClass: xxxundefinedauxiliaryxxx",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isFalse();
  }



  /**
   * Tests schema checking for an entry that is missing an attribute required by
   * its structural object class.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testMissingAttributeRequiredByStructuralClass() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testmissingatrequiredbystructuraloc-oid "
                + "NAME 'testMissingATRequiredByStructuralOC' SUP top STRUCTURAL "
                + "MUST ( cn $ description ) "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testMissingATRequiredByStructuralOC",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isFalse();
  }



  private Entry newEntry(String... ldif)
  {
    return newEntry(Schema.getDefaultSchema(), ldif);
  }



  private Entry newEntry(Schema schema, String... ldif)
  {
    try
    {
      return new LDIFEntryReader(ldif).setSchema(schema).readEntry();
    }
    catch (Exception e)
    {
      throw new IllegalArgumentException(e);
    }
  }



  /**
   * Tests schema checking for an entry that is missing an attribute required by
   * an auxiliary object class.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testMissingAttributeRequiredByAuxiliaryClass() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testmissingatrequiredbyauxiliaryoc-oid "
                + "NAME 'testMissingATRequiredByAuxiliaryOC' SUP top AUXILIARY "
                + "MUST ( cn $ description ) "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: device",
        "objectClass: testMissingATRequiredByAuxiliaryOC",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isFalse();
  }



  /**
   * Tests schema checking for an entry that includes an attribute type that is
   * not allowed by any of its object classes.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testDisallowedAttributeType() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testdisallowedattributetypeoc-oid "
                + "NAME 'testDisallowedAttributeTypeOC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testDisallowedAttributeTypeOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isFalse();
  }



  private boolean conformsToSchema(final Entry entry,
      final SchemaValidationPolicy policy,
      final Collection<LocalizableMessage> errorMessages)
  {
    return conformsToSchema(entry, Schema.getDefaultSchema(), policy,
        errorMessages);
  }



  private boolean conformsToSchema(final Entry entry, final Schema schema,
      final SchemaValidationPolicy policy,
      final Collection<LocalizableMessage> errorMessages)
  {
    return schema.validateEntry(entry, policy, errorMessages);
  }



  /**
   * Tests schema checking for an entry that includes multiple values for a
   * multivalued attribute.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testMultipleValuesForMultiValuedAttribute() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: o=test",
        "objectClass: top",
        "objectClass: organization",
        "o: test",
        "o: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry that includes multiple values for a
   * single-valued attribute.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testMultipleValuesForSingleValuedAttribute() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: domain",
        "dc: example",
        "dc: foo");
    // @formatter:on

    assertThat(
        conformsToSchema(e, ignoreAll().checkAttributeValues(Policy.REJECT),
            null)).isFalse();
  }



  /**
   * Tests schema checking for an entry that includes multiple values for a
   * single-valued operational attribute.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testMultipleValuesForSingleValuedOperationalAttribute()
      throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: domain",
        "dc: example",
        "creatorsName: cn=Directory Manager",
        "creatorsName: cn=Another Manager");
    // @formatter:on

    assertThat(
        conformsToSchema(e, ignoreAll().checkAttributeValues(Policy.REJECT),
            null)).isFalse();
  }



  /**
   * Tests schema checking for an entry that contains structural and auxiliary
   * objectclasses where the auxiliary class is allowed by a DIT content rule.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testAuxiliaryClassAllowedByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testauxiliaryclassallowedbydcroc-oid "
                + "NAME 'testAuxiliaryClassAllowedByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addObjectClass(
            "( testauxiliaryclassallowedbydcrocaux-oid "
                + "NAME 'testAuxiliaryClassAllowedByDCROCAux' SUP top AUXILIARY "
                + "MAY description X-ORIGIN 'EntrySchemaCheckingTestCase')",
            false)
        .addDITContentRule(
            "( testauxiliaryclassallowedbydcroc-oid "
                + "NAME 'testAuxiliaryClassAllowedByDCR' "
                + "AUX testAuxiliaryClassAllowedByDCROCAux "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testAuxiliaryClassAllowedByDCROC",
        "objectClass: testAuxiliaryClassAllowedByDCROCAux",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry that contains structural and auxiliary
   * objectclasses where the auxiliary class is not allowed by the associated
   * DIT content rule.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testAuxiliaryClassNotAllowedByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testauxiliaryclassnotallowedbydcroc-oid "
                + "NAME 'testAuxiliaryClassNotAllowedByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addObjectClass(
            "( testauxiliaryclassnotallowedbydcrocaux-oid "
                + "NAME 'testAuxiliaryClassNotAllowedByDCROCAux' SUP top "
                + "AUXILIARY MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testauxiliaryclassnotallowedbydcroc-oid "
                + "NAME 'testAuxiliaryClassNotAllowedByDCR' "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testAuxiliaryClassNotAllowedByDCROC",
        "objectClass: testAuxiliaryClassNotAllowedByDCROCAux",
        "cn: test");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests schema checking for an entry covered by a DIT content rule to ensure
   * that attributes required by the DIT content rule are allowed even if not
   * directly allowed by any of the entry's objectclasses.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testAllowAttributeRequiredByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testallowatrequiredbydcroc-oid "
                + "NAME 'testAllowATRequiredByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testallowatrequiredbydcroc-oid "
                + "NAME 'testAllowATRequiredByDCR' MUST description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testAllowATRequiredByDCROC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry covered by a DIT content rule to ensure
   * that attributes required by the DIT content rule are required even if not
   * directly allowed by any of the entry's objectclasses.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testRequireAttributeRequiredByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testrequireatrequiredbydcroc-oid "
                + "NAME 'testRequireATRequiredByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testrequireatrequiredbydcroc-oid "
                + "NAME 'testRequireATRequiredByDCR' MUST description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testRequireATRequiredByDCROC",
        "cn: test");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests schema checking for an entry for which there is a DIT content rule
   * covering the structural objectclass but that DIT content rule is marked
   * OBSOLETE. In this case, any attribute types required by the DIT content
   * rule should not be required for the entry.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testDontRequireAttributeRequiredByObsoleteDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testdontrequireatrequiredbyobsoletedcroc-oid "
                + "NAME 'testDontRequireATRequiredByObsoleteDCROC' SUP top "
                + "STRUCTURAL MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')",
            false)
        .addDITContentRule(
            "( testdontrequireatrequiredbyobsoletedcroc-oid "
                + "NAME 'testDontRequireATRequiredByObsoleteDCR' OBSOLETE "
                + "MUST description X-ORIGIN 'EntrySchemaCheckingTestCase' )",
            false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testDontRequireATRequiredByObsoleteDCROC",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry covered by a DIT content rule to ensure
   * that attributes allowed by the DIT content rule are allowed even if not
   * directly allowed by any of the entry's objectclasses.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testAllowAttributeAllowedByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testallowatallowedbydcroc-oid "
                + "NAME 'testAllowATAllowedByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testallowatallowedbydcroc-oid "
                + "NAME 'testAllowATAllowedByDCR' MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testAllowATAllowedByDCROC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry covered by a DIT content rule to ensure
   * that attributes allowed by the DIT content rule are allowed but not
   * required if they are not required by any of the associated object classes.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testDontRequireAttributeAllowedByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testdontrequireatallowedbydcroc-oid "
                + "NAME 'testDontRequireATAllowedByDCROC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testdontrequireatallowedbydcroc-oid "
                + "NAME 'testDontRequireATAllowedByDCR' MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testDontRequireATAllowedByDCROC",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests schema checking for an entry covered by a DIT content rule to ensure
   * that attributes prohibited by the DIT content rule are not allowed even if
   * they are allowed by the associated object classes.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testDontAllowAttributeProhibitedByDCR() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testdontallowattributeprohibitedbydcroc-oid "
                + "NAME 'testDontAllowAttributeProhibitedByDCROC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addDITContentRule(
            "( testdontallowattributeprohibitedbydcroc-oid "
                + "NAME 'testDontAllowAttributeProhibitedByDCR' NOT description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testDontAllowAttributeProhibitedByDCROC",
        "cn: test",
        "description: foo");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * single-valued RDN component is compliant with that name form.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testSatisfiesSingleValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testsatisfiessinglevaluednameformoc-oid "
                + "NAME 'testSatisfiesSingleValuedNameFormOC' SUP top STRUCTURAL "
                + "MUST cn X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testsatisfiessinglevaluednameform-oid "
                + "NAME 'testSatisfiesSingleValuedNameForm' "
                + "OC testSatisfiesSingleValuedNameFormOC MUST cn "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testSatisfiesSingleValuedNameFormOC",
        "cn: test");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests that an entry covered by a name form will be rejected if its
   * single-valued RDN component violates that name form.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testViolatesSingleValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testviolatessinglevaluednameformoc-oid "
                + "NAME 'testViolatesSingleValuedNameFormOC' SUP top STRUCTURAL "
                + "MUST cn MAY description X-ORIGIN 'EntrySchemaCheckingTestCase')",
            false)
        .addNameForm(
            "( testviolatessinglevaluednameform-oid "
                + "NAME 'testViolatesSingleValuedNameForm' "
                + "OC testViolatesSingleValuedNameFormOC MUST cn "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: description=foo,o=test",
        "objectClass: top",
        "objectClass: testViolatesSingleValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests that an entry covered by a name form will be rejected if its
   * multivalued RDN component violates that name form which only allows a
   * single value.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testMVViolatesSingleValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testmvviolatessinglevaluednameformoc-oid "
                + "NAME 'testMVViolatesSingleValuedNameFormOC' SUP top STRUCTURAL "
                + "MUST cn MAY description X-ORIGIN 'EntrySchemaCheckingTestCase')",
            false)
        .addNameForm(
            "( testmvviolatessinglevaluednameform-oid "
                + "NAME 'testMVViolatesSingleValuedNameForm' "
                + "OC testMVViolatesSingleValuedNameFormOC MUST cn "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test+description=foo,o=test",
        "objectClass: top",
        "objectClass: testMVViolatesSingleValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests that an entry covered by a name form will not be rejected if its
   * single-valued RDN component violates that name form but the name form is
   * declared OBSOLETE.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testViolatesSingleValuedObsoleteNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testviolatessinglevaluedobsoletenameformoc-oid "
                + "NAME 'testViolatesSingleValuedObsoleteNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testviolatessinglevaluedobsoletenameform-oid "
                + "NAME 'testViolatesSingleValuedObsoleteNameForm' OBSOLETE "
                + "OC testViolatesSingleValuedObsoleteNameFormOC MUST cn "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: description=foo,o=test",
        "objectClass: top",
        "objectClass: testViolatesSingleValuedObsoleteNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * multivalued RDN component is compliant with that name form which requires
   * multiple values.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testSatisfiesRequiredMultiValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testsatisfiesrequiredmultivaluednameformoc-oid "
                + "NAME 'testSatisfiesRequiredMultiValuedNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testsatisfiesrequiredmultivaluednameform-oid "
                + "NAME 'testSatisfiesRequiredMultiValuedNameForm' "
                + "OC testSatisfiesRequiredMultiValuedNameFormOC "
                + "MUST ( cn $ description ) "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test+description=foo,o=test",
        "objectClass: top",
        "objectClass: testSatisfiesRequiredMultiValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * single-valued RDN component only contains one of the multiple required
   * attribute types.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testViolatesRequiredMultiValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testviolatesrequiredmultivaluednameformoc-oid "
                + "NAME 'testViolatesRequiredMultiValuedNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testviolatesrequiredmultivaluednameform-oid "
                + "NAME 'testViolatesRequiredMultiValuedNameForm' "
                + "OC testViolatesRequiredMultiValuedNameFormOC "
                + "MUST ( cn $ description ) "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testViolatesRequiredMultiValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * single-valued RDN component is compliant with that name form which requires
   * one value but allows other values.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testSVSatisfiesOptionalMultiValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testsvsatisfiesoptionalmultivaluednameformoc-oid "
                + "NAME 'testSVSatisfiesOptionalMultiValuedNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testsvsatisfiesoptionalmultivaluednameform-oid "
                + "NAME 'testSVSatisfiesOptionalMultiValuedNameForm' "
                + "OC testSVSatisfiesOptionalMultiValuedNameFormOC MUST cn "
                + "MAY description X-ORIGIN 'EntrySchemaCheckingTestCase' )",
            false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test,o=test",
        "objectClass: top",
        "objectClass: testSVSatisfiesOptionalMultiValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * multivalued RDN component is compliant with that name form which requires
   * one value but allows other values.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testMVSatisfiesOptionalMultiValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testmvsatisfiesoptionalmultivaluednameformoc-oid "
                + "NAME 'testMVSatisfiesOptionalMultiValuedNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testmvsatisfiesoptionalmultivaluednameform-oid "
                + "NAME 'testMVSatisfiesOptionalMultiValuedNameForm' "
                + "OC testMVSatisfiesOptionalMultiValuedNameFormOC MUST cn "
                + "MAY description X-ORIGIN 'EntrySchemaCheckingTestCase' )",
            false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=test+description=foo,o=test",
        "objectClass: top",
        "objectClass: testMVSatisfiesOptionalMultiValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    assertThat(conformsToSchema(e, schema, defaultPolicy(), null)).isTrue();
  }



  /**
   * Tests that an entry covered by a name form will be accepted if its
   * single-valued RDN component violates that name form which requires one
   * value but allows other values.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testSVViolatesOptionalMultiValuedNameForm() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
        .addObjectClass(
            "( testsvviolatesoptionalmultivaluednameformoc-oid "
                + "NAME 'testSVViolatesOptionalMultiValuedNameFormOC' SUP top "
                + "STRUCTURAL MUST cn MAY description "
                + "X-ORIGIN 'EntrySchemaCheckingTestCase')", false)
        .addNameForm(
            "( testsvviolatesoptionalmultivaluednameform-oid "
                + "NAME 'testSVViolatesOptionalMultiValuedNameForm' "
                + "OC testSVViolatesOptionalMultiValuedNameFormOC MUST cn "
                + "MAY description X-ORIGIN 'EntrySchemaCheckingTestCase' )",
            false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: description=foo,o=test",
        "objectClass: top",
        "objectClass: testSVViolatesOptionalMultiValuedNameFormOC",
        "cn: test",
        "description: foo");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);
  }



  /**
   * Performs various tests to ensure that the server appropriately enforces DIT
   * structure rule constraints.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testDITStructureRuleConstraints() throws Exception
  {
    Schema schema = new SchemaBuilder(Schema.getDefaultSchema())
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
            "( 999015 " + "NAME 'testDITStructureRuleConstraintsSub' "
                + "FORM testDITStructureRuleConstraintsSubNF SUP 999014 "
                + "X-ORIGIN 'SchemaBackendTestCase' )", false).toSchema();

    // @formatter:off
    Entry e = newEntry(schema,
        "dn: cn=child,ou=parent,o=test",
        "objectClass: top",
        "objectClass: testDITStructureRuleConstraintsSubOC",
        "cn: child");
    // @formatter:on

    failOnlyForStrictEvaluation(e, schema);

    // @formatter:off
    Entry p = newEntry(schema,
        "dn: ou=parent,o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: testDITStructureRuleConstraintsSupOC",
        "ou: parent");
    // @formatter:on

    assertThat(
        conformsToSchema(
            e,
            schema,
            defaultPolicy().checkDITStructureRules(Policy.REJECT,
                newResolver(p)), null)).isFalse();

    // @formatter:off
    e = newEntry(schema,
        "dn: cn=not below valid parent,o=test",
        "objectClass: top",
        "objectClass: testDITStructureRuleConstraintsSubOC",
        "cn: not below valid parent");
    // @formatter:on

    assertThat(
        conformsToSchema(
            e,
            schema,
            defaultPolicy().checkDITStructureRules(Policy.REJECT,
                newResolver(null)), null)).isFalse();

    // @formatter:off
    e = newEntry(schema,
        "dn: cn=invalid entry below parent covered by DSR,ou=parent,o=test",
        "objectClass: top", "objectClass: device",
        "cn: invalid entry below parent covered by DSR");
    // @formatter:on

    assertThat(
        conformsToSchema(
            e,
            schema,
            defaultPolicy().checkDITStructureRules(Policy.REJECT,
                newResolver(p)), null)).isFalse();
  }



  private EntryResolver newResolver(final Entry e)
  {
    return new EntryResolver()
    {

      public Entry getEntry(DN dn) throws ErrorResultException
      {
        if (e == null)
        {
          throw ErrorResultException.newErrorResult(ResultCode.NO_SUCH_OBJECT,
              "no such entry " + dn.toString());
        }

        assertThat((Object) dn).isEqualTo(e.getName());
        return e;
      }
    };
  }



  /**
   * Tests schema checking for an entry that includes an attribute not defined
   * in any objectClasses but the subtypes of the attribute are.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testInvalidSuperiorAttribute() throws Exception
  {
    // @formatter:off
    Entry e = newEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: account",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "name: foo");
    // @formatter:off

    assertThat(conformsToSchema(e, defaultPolicy(), null)).isFalse();
  }
}
