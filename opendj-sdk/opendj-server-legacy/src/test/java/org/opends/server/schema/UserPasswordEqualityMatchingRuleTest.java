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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SaltedMD5PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedMD5PasswordStorageSchemeCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.SaltedMD5PasswordStorageScheme;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.types.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


@SuppressWarnings("javadoc")
public class UserPasswordEqualityMatchingRuleTest extends SchemaTestCase
{

  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object[][] {
        {"password", "password", true},
    };
  }

  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {};
  }

  private Object[] generateValues(String password) throws Exception
  {
    ByteString bytePassword = ByteString.valueOf(password);
    SaltedMD5PasswordStorageScheme scheme = new SaltedMD5PasswordStorageScheme();

    ConfigEntry configEntry = DirectoryServer.getConfigEntry(
           DN.valueOf("cn=Salted MD5,cn=Password Storage Schemes,cn=config"));

    SaltedMD5PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedMD5PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry());

    scheme.initializePasswordStorageScheme(configuration);

    ByteString encodedAuthPassword =
         scheme.encodePasswordWithScheme(bytePassword);

     return new Object[] { encodedAuthPassword.toString(), password, true };
  }

  @DataProvider(name="valuesMatch")
  public Object[][] createValuesMatch()
  {
    try
    {
      return new Object[][] {
        generateValues("password"),
        {"password", "something else", false},
        {"password", "{wong}password", false},
        {"password", "{SMD5}wrong",    false}
      };
    }
    catch (Exception e)
    {
      return new Object[][] {};
    }
  }

  @Test(dataProvider= "equalityMatchingRuleInvalidValues", expectedExceptions = { DecodeException.class })
  public void equalityMatchingRulesInvalidValues(String value) throws Exception
  {
    getRule().normalizeAttributeValue(ByteString.valueOf(value));
  }

  /**
   * Test the valuesMatch method used for extensible filters.
   */
  @Test(dataProvider= "valuesMatch")
  public void testValuesMatch(String value1, String value2, Boolean result) throws Exception
  {
    MatchingRule rule = getRule();

    ByteString normalizedValue1 = rule.normalizeAttributeValue(ByteString.valueOf(value1));
    Assertion assertion = rule.getAssertion(ByteString.valueOf(value2));

    ConditionResult liveResult = assertion.matches(normalizedValue1);
    assertEquals(liveResult, ConditionResult.valueOf(result));
  }

  private MatchingRule getRule()
  {
    UserPasswordEqualityMatchingRuleFactory factory = new UserPasswordEqualityMatchingRuleFactory();
    try
    {
      factory.initializeMatchingRule(null);
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return factory.getMatchingRules().iterator().next();
  }
}

