/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
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

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.testng.annotations.Test;



/**
 * Test SchemaBuilder.
 */
public class SchemaBuilderTest extends SchemaTestCase
{

  /**
   * Test for OPENDJ-156: Errors when parsing collective attribute definitions.
   */
  @Test
  public void testCollectiveAttribute()
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
    builder
        .addAttributeType(
            "( 2.5.4.11.1 NAME 'c-ou' SUP ou SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 COLLECTIVE X-ORIGIN 'RFC 3671' )",
            false);
    Schema schema = builder.toSchema();
    assertThat(schema.getWarnings()).isEmpty();
  }
}
