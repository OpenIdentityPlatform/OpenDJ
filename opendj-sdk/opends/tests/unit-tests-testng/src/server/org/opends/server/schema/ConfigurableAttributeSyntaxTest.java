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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import static org.testng.Assert.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.TelephoneNumberAttributeSyntaxCfgDefn;
import org.opends.server.admin.std.server.TelephoneNumberAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ConfigurableAttributeSyntaxTest extends SchemaTestCase
{
  /**
   * Build the data for the test of the hasAcceptableValue Methods
   * of the class extending the AttributeSyntax class and having
   * some configuration capabilities.
   */
  @DataProvider(name="acceptableValues")
  public Object[][] createSyntaxTest() throws Exception
  {
    // some config object used later in the test
    ConfigEntry strictConfig = new ConfigEntry(TestCaseUtils.makeEntry(
        "dn: cn=Telephone Number,cn=Syntaxes,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-telephone-number-attribute-syntax",
        "objectClass: ds-cfg-attribute-syntax",
        "ds-cfg-strict-telephone-number-format: true",
        "ds-cfg-syntax-enabled: true",
        "ds-cfg-syntax-class: org.opends.server.schema.TelephoneNumberSyntax",
        "cn: Telephone Number"
         ), null);

    ConfigEntry relaxedConfig = new ConfigEntry(TestCaseUtils.makeEntry(
        "dn: cn=Telephone Number,cn=Syntaxes,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-telephone-number-attribute-syntax",
        "objectClass: ds-cfg-attribute-syntax",
        "ds-cfg-strict-telephone-number-format: false",
        "ds-cfg-syntax-enabled: true",
        "ds-cfg-syntax-class: org.opends.server.schema.TelephoneNumberSyntax",
        "cn: Telephone Number"
         ), null);

    // fill this table with tables containing :
    // - the configEntry that must be applied before the test.
    // - the name of the Syntax to test.
    // - a value that must be tested for correctness.
    // - a boolean indicating if the value is correct.
    return new Object[][] {
         {strictConfig, SYNTAX_TELEPHONE_OID, "+61 3 9896 7830", true},
         {strictConfig, SYNTAX_TELEPHONE_OID, "+1 512 315 0280", true},
         {strictConfig, SYNTAX_TELEPHONE_OID, "+1-512-315-0280", true},
         {strictConfig, SYNTAX_TELEPHONE_OID, "3 9896 7830", false},
         {strictConfig, SYNTAX_TELEPHONE_OID, "+1+512 315 0280", false},
         {strictConfig, SYNTAX_TELEPHONE_OID, "+1x512x315x0280", false},
         {strictConfig, SYNTAX_TELEPHONE_OID, "   ", false},
         {strictConfig, SYNTAX_TELEPHONE_OID, "", false},


         {relaxedConfig, SYNTAX_TELEPHONE_OID, "+1+512 315 0280", true},
         {relaxedConfig, SYNTAX_TELEPHONE_OID, "+1x512x315x0280", true},
         {strictConfig, SYNTAX_TELEPHONE_OID, "   ", false},
         {strictConfig, SYNTAX_TELEPHONE_OID, "", false},
    };
  }

  /**
   * Test the hasAcceptableConfiguration, applyNewConfiguration and
   * valueIsAcceptable methods.
   */
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(ConfigEntry config, String oid, String value,
      Boolean result) throws Exception
  {
    TelephoneNumberAttributeSyntaxCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              TelephoneNumberAttributeSyntaxCfgDefn.getInstance(),
              config.getEntry());

    TelephoneNumberSyntax syntax =
         (TelephoneNumberSyntax) DirectoryServer.getAttributeSyntax(oid, false);

    // apply the configuration.
    ArrayList<String> unacceptableReasons = new ArrayList<String>();
    assertTrue(syntax.isConfigurationChangeAcceptable(configuration,
                                                      unacceptableReasons));
    ConfigChangeResult configResult =
         syntax.applyConfigurationChange(configuration);
    assertEquals(configResult.getResultCode(), ResultCode.SUCCESS);

    // check the syntax of the given value.
    Boolean liveResult = syntax.valueIsAcceptable(
        new ASN1OctetString(value), new StringBuilder());
    assertEquals(result, liveResult);

    // call the getters to increase code coverage...
    syntax.getApproximateMatchingRule();
    syntax.getDescription();
    syntax.getEqualityMatchingRule();
    syntax.getOID();
    syntax.getOrderingMatchingRule();
    syntax.getSubstringMatchingRule();
    syntax.getSyntaxName();
    syntax.toString();
  }
}
