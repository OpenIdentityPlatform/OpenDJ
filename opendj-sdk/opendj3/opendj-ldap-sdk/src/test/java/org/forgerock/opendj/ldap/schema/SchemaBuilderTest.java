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
 *
 *      Copyright 2011 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;



import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.testng.annotations.Test;



/**
 * Test SchemaBuilder.
 */
public class SchemaBuilderTest extends SchemaTestCase
{

  /**
   * Tests that schema validation resolves dependencies between parent/child
   * attribute types regardless of the order in which they were added.
   */
  @Test
  public void testAttributeTypeDependenciesChildThenParent()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addAttributeType("( childtype-oid NAME 'childtype' SUP parenttype )",
            false)
        .addAttributeType(
            "( parenttype-oid NAME 'parenttype' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )",
            false).toSchema();
    assertThat(schema.getAttributeType("childtype").getSyntax()).isNotNull();
    assertThat(schema.getAttributeType("childtype").getSyntax().getOID())
        .isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
  }



  /**
   * Tests that schema validation resolves dependencies between parent/child
   * attribute types regardless of the order in which they were added.
   */
  @Test
  public void testAttributeTypeDependenciesParentThenChild()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addAttributeType(
            "( parenttype-oid NAME 'parenttype' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )",
            false)
        .addAttributeType("( childtype-oid NAME 'childtype' SUP parenttype )",
            false).toSchema();
    assertThat(schema.getAttributeType("childtype").getSyntax()).isNotNull();
    assertThat(schema.getAttributeType("childtype").getSyntax().getOID())
        .isEqualTo("1.3.6.1.4.1.1466.115.121.1.15");
  }



  /**
   * Tests that attribute types must have a syntax or a superior.
   */
  @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testAttributeTypeNoSuperiorNoSyntax()
  {
    new SchemaBuilder(Schema.getCoreSchema()).addAttributeType(
        "( parenttype-oid NAME 'parenttype' )", false);
  }



  /**
   * Tests that schema validation handles validation failures for superior
   * attribute types regardless of the order.
   */
  @Test
  public void testAttributeTypeSuperiorFailureChildThenParent()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addAttributeType("( childtype-oid NAME 'childtype' SUP parenttype )",
            false)
        .addAttributeType("( parenttype-oid NAME 'parenttype' SUP xxx )", false)
        .toSchema();

    try
    {
      schema.getAttributeType("childtype");
      fail("childtype should not be in the schema because its parent is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }

    try
    {
      schema.getAttributeType("parenttype");
      fail("parenttype should not be in the schema because it is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }
  }



  /**
   * Tests that schema validation handles validation failures for superior
   * attribute types regardless of the order.
   */
  @Test
  public void testAttributeTypeSuperiorFailureParentThenChild()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addAttributeType("( parenttype-oid NAME 'parenttype' SUP xxx )", false)
        .addAttributeType("( childtype-oid NAME 'childtype' SUP parenttype )",
            false).toSchema();

    try
    {
      schema.getAttributeType("childtype");
      fail("childtype should not be in the schema because its parent is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }

    try
    {
      schema.getAttributeType("parenttype");
      fail("parenttype should not be in the schema because it is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }
  }



  /**
   * Test for OPENDJ-156: Errors when parsing collective attribute definitions.
   */
  @Test
  public void testCollectiveAttribute()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addAttributeType(
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
  public void testCopyOnWriteNoChanges()
  {
    final Schema baseSchema = Schema.getCoreSchema();
    final Schema schema = new SchemaBuilder(baseSchema).toSchema();

    assertThat(schema).isSameAs(baseSchema);
  }



  /**
   * Tests that it is possible to create a schema which is based on another.
   */
  @Test
  public void testCopyOnWriteWithChanges()
  {
    final Schema baseSchema = Schema.getCoreSchema();
    final Schema schema = new SchemaBuilder(baseSchema).addAttributeType(
        "( testtype-oid NAME 'testtype' SUP name )", false).toSchema();
    assertThat(schema).isNotSameAs(baseSchema);
    assertThat(schema.getObjectClasses().containsAll(
        baseSchema.getObjectClasses()));
    assertThat(schema.getObjectClasses().size()).isEqualTo(
        baseSchema.getObjectClasses().size());
    assertThat(schema.getAttributeTypes().containsAll(
        baseSchema.getAttributeTypes()));
    assertThat(schema.getAttributeType("testtype")).isNotNull();
    assertThat(schema.getSchemaName()).isEqualTo(baseSchema.getSchemaName());
    assertThat(schema.allowMalformedNamesAndOptions()).isEqualTo(
        baseSchema.allowMalformedNamesAndOptions());
  }



  /**
   * Tests that it is possible to create an empty schema.
   */
  @Test
  public void testCreateEmptySchema()
  {
    final Schema schema = new SchemaBuilder().toSchema();
    assertThat(schema.getAttributeTypes()).isEmpty();
    assertThat(schema.getObjectClasses()).isEmpty();
    assertThat(schema.getSyntaxes()).isEmpty();
    assertThat(schema.getWarnings()).isEmpty();
    // Could go on...
  }



  /**
   * Tests that multiple consecutive invocations of toSchema return the exact
   * same schema.
   */
  @Test
  public void testMultipleToSchema1()
  {
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
  public void testMultipleToSchema2()
  {
    final SchemaBuilder builder = new SchemaBuilder()
        .addAttributeType(
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
  public void testObjectClassDependenciesChildThenParent()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addObjectClass(
            "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
            false)
        .addObjectClass(
            "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST cn )",
            false).toSchema();

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
  public void testObjectClassDependenciesParentThenChild()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
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
  public void testObjectClassSuperiorFailureChildThenParent()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addObjectClass(
            "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
            false)
        .addObjectClass(
            "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST xxx )",
            false).toSchema();

    try
    {
      schema.getObjectClass("childtype");
      fail("childtype should not be in the schema because its parent is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }

    try
    {
      schema.getObjectClass("parenttype");
      fail("parenttype should not be in the schema because it is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }
  }



  /**
   * Tests that schema validation handles validation failures for superior
   * object classes regardless of the order.
   */
  @Test
  public void testObjectClassSuperiorFailureParentThenChild()
  {
    final Schema schema = new SchemaBuilder(Schema.getCoreSchema())
        .addObjectClass(
            "( parenttype-oid NAME 'parenttype' SUP top STRUCTURAL MUST xxx )",
            false)
        .addObjectClass(
            "( childtype-oid NAME 'childtype' SUP parenttype STRUCTURAL MUST sn )",
            false).toSchema();

    try
    {
      schema.getObjectClass("childtype");
      fail("childtype should not be in the schema because its parent is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }

    try
    {
      schema.getObjectClass("parenttype");
      fail("parenttype should not be in the schema because it is invalid");
    }
    catch (final UnknownSchemaElementException e)
    {
      // Expected.
    }
  }



  /**
   * Tests that a schema builder can be re-used after toSchema has been called.
   */
  @Test
  public void testReuseSchemaBuilder()
  {
    final SchemaBuilder builder = new SchemaBuilder();
    final Schema schema1 = builder
        .addAttributeType(
            "( testtype1-oid NAME 'testtype1' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
            false).toSchema();

    final Schema schema2 = builder
        .addAttributeType(
            "( testtype2-oid NAME 'testtype2' SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 )",
            false).toSchema();
    assertThat(schema1).isNotSameAs(schema2);
    assertThat(schema1.getAttributeType("testtype1")).isNotNull();
    assertThat(schema1.hasAttributeType("testtype2")).isFalse();
    assertThat(schema2.getAttributeType("testtype1")).isNotNull();
    assertThat(schema2.getAttributeType("testtype2")).isNotNull();
  }
}
