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



import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test SchemaCompatOptions.
 */
public class SchemaCompatOptionsTest extends SchemaTestCase
{
  /**
   * Returns test data for valid attribute descriptions.
   *
   * @return The test data.
   */
  @DataProvider
  public Object[][] validAttributeDescriptions()
  {
    // @formatter:off
    return new Object[][] {
        // No options.
        { "cn", false },
        { "cn-xxx", false },
        { "cn", true },
        { "cn-xxx", true },
        { "cn_xxx", true },
        { "cn.xxx", true },
        // With options.
        { "cn;xxx", false },
        { "cn;xxx-yyy", false },
        { "cn;xxx", true },
        { "cn;xxx-yyy", true },
        { "cn;xxx_yyy", true },
        { "cn;xxx.yyy", true },
    };
    // @formatter:on
  }



  /**
   * Tests valid attribute description parsing behavior depends on compat
   * options.
   *
   * @param atd
   *          The attribute description to be parsed.
   * @param allowIllegalCharacters
   *          {@code true} if the attribute description requires the
   *          compatibility option to be set.
   */
  @Test(enabled = false, dataProvider = "validAttributeDescriptions")
  public void testValidAttributeDescriptions(String atd,
      boolean allowIllegalCharacters)
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema())
        .setSchemaCompatOptions(SchemaCompatOptions.defaultOptions()
            .allowMalformedNamesAndOptions(allowIllegalCharacters));
    Schema schema = builder.toSchema().nonStrict();
    AttributeDescription.valueOf(atd, schema);
  }



  /**
   * Returns test data for invalid attribute descriptions.
   *
   * @return The test data.
   */
  @DataProvider
  public Object[][] invalidAttributeDescriptions()
  {
    // @formatter:off
    return new Object[][] {
        // No options.
        { "cn+xxx", false }, // always invalid
        { "cn_xxx", false },
        { "cn.xxx", false },
        { "cn+xxx", true }, // always invalid
        // With options.
        { "cn;xxx+yyy", false }, // always invalid
        { "cn;xxx_yyy", false },
        { "cn;xxx.yyy", false },
        { "cn;xxx+yyy", true }, // always invalid
    };
    // @formatter:on
  }



  /**
   * Tests invalid attribute description parsing behavior depends on compat
   * options.
   *
   * @param atd
   *          The attribute description to be parsed.
   * @param allowIllegalCharacters
   *          {@code true} if the attribute description requires the
   *          compatibility option to be set.
   */
  @Test(enabled = false, dataProvider = "invalidAttributeDescriptions",
      expectedExceptions = LocalizedIllegalArgumentException.class)
  public void testinvalidAttributeDescriptions(String atd,
      boolean allowIllegalCharacters)
  {
    SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema())
        .setSchemaCompatOptions(SchemaCompatOptions.defaultOptions()
            .allowMalformedNamesAndOptions(allowIllegalCharacters));
    Schema schema = builder.toSchema().nonStrict();
    AttributeDescription.valueOf(atd, schema);
  }
}
