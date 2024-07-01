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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import org.forgerock.opendj.server.config.meta.SaltedMD5PasswordStorageSchemeCfgDefn;
import org.opends.server.types.Entry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.extensions.SaltedMD5PasswordStorageScheme;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.DN;
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
    ByteString bytePassword = ByteString.valueOfUtf8(password);

    Entry configEntry = DirectoryServer.getEntry(
           DN.valueOf("cn=Salted MD5,cn=Password Storage Schemes,cn=config"));

    SaltedMD5PasswordStorageScheme scheme = InitializationUtils.initializePasswordStorageScheme(
        new SaltedMD5PasswordStorageScheme(), configEntry, SaltedMD5PasswordStorageSchemeCfgDefn.getInstance());

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
    getRule().normalizeAttributeValue(ByteString.valueOfUtf8(value));
  }

  /**
   * Test the valuesMatch method used for extensible filters.
   */
  @Test(dataProvider= "valuesMatch")
  public void testValuesMatch(String value1, String value2, Boolean result) throws Exception
  {
    MatchingRule rule = getRule();

    ByteString normalizedValue1 = rule.normalizeAttributeValue(ByteString.valueOfUtf8(value1));
    Assertion assertion = rule.getAssertion(ByteString.valueOfUtf8(value2));

    ConditionResult liveResult = assertion.matches(normalizedValue1);
    assertEquals(liveResult, ConditionResult.valueOf(result));
  }

  private MatchingRule getRule() throws ConfigException
  {
    SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getCoreSchema());
    SchemaHandler.addServerSyntaxesAndMatchingRules(schemaBuilder);
    return schemaBuilder.toSchema().getMatchingRule(SchemaConstants.EMR_USER_PASSWORD_OID);
  }
}

