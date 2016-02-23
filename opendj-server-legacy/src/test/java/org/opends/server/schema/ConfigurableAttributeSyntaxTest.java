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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;

@RemoveOnceSDKSchemaIsUsed
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
        "ds-cfg-strict-format: true",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.schema.TelephoneNumberSyntax",
        "cn: Telephone Number"
         ), null);

    ConfigEntry relaxedConfig = new ConfigEntry(TestCaseUtils.makeEntry(
        "dn: cn=Telephone Number,cn=Syntaxes,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-telephone-number-attribute-syntax",
        "objectClass: ds-cfg-attribute-syntax",
        "ds-cfg-strict-format: false",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.schema.TelephoneNumberSyntax",
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
         {relaxedConfig, SYNTAX_TELEPHONE_OID, "   ", false},
         {relaxedConfig, SYNTAX_TELEPHONE_OID, "", false},
    };
  }

}
