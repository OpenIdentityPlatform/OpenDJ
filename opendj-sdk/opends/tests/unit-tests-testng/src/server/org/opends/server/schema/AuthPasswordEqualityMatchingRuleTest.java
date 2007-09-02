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

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SaltedMD5PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedMD5PasswordStorageSchemeCfg;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.SaltedMD5PasswordStorageScheme;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.testng.annotations.DataProvider;

import static org.opends.server.extensions.ExtensionsConstants.*;

/**
 * Test the AuthPasswordEqualityMatchingRule.
 */
public class AuthPasswordEqualityMatchingRuleTest extends
    EqualityMatchingRuleTest
{


  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  {
    return new Object[][] {
        {"password", "password", true},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {};
  }

  private Object[] generateValues(String password) throws Exception
  {
    ByteString bytePassword = new ASN1OctetString(password);
    SaltedMD5PasswordStorageScheme scheme = new SaltedMD5PasswordStorageScheme();

    ConfigEntry configEntry =
       DirectoryServer.getConfigEntry(
           DN.decode("cn=Salted MD5,cn=Password Storage Schemes,cn=config"));

    SaltedMD5PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedMD5PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);

    ByteString encodedAuthPassword = scheme.encodeAuthPassword(bytePassword);
    StringBuilder[] authPWComponents =
         AuthPasswordSyntax.decodeAuthPassword(
              encodedAuthPassword.stringValue());

     return new Object[] {
         AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5 + "$"
                 + authPWComponents[1].toString()
                 + "$"+ authPWComponents[2].toString(),
         password, true};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="valuesMatch")
  public Object[][] createValuesMatch()
  {
    try
    {
      return new Object[][] {
          generateValues("password"),
          {"password", "something else", false},
          {"password", AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5+"$something$else",
                      false},
          {"password", "scheme$something$else", false}
      };
    }
    catch (Exception e)
    {
      return new Object[][] {};
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new AuthPasswordEqualityMatchingRule();
  }
}

