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
package org.opends.server.extensions;



import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.testng.Assert.*;



/**
 * A set of test cases for the random password generator.
 */
public class RandomPasswordGeneratorTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the password generator with the default configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDefaultConfiguration()
         throws Exception
  {
    DN dn = DN.decode("cn=Random Password Generator,cn=Password Generators," +
                      "cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(dn);
    assertNotNull(configEntry);

    RandomPasswordGenerator generator = new RandomPasswordGenerator();
    generator.initializePasswordGenerator(configEntry);
    assertNotNull(generator.generatePassword(null));
    generator.finalizePasswordGenerator();
  }



  /**
   * Retrieves a set of LDIF representations for invalid configuration entries.
   *
   * @return  A set of LDIF representations for invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigEntries")
  public Object[][] getInvalidConfigEntries()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set:",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set: foo:",
      "ds-cfg-password-format: foo:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "ds-cfg-password-character-set: foo:efgh",
      "ds-cfg-password-format: foo:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "ds-cfg-password-format: bar:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-password-generator-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "ds-cfg-password-format: foo:abcd"
    );


    Object[][] entryObjects = new Object[entries.size()][1];
    for (int i=0; i < entryObjects.length; i++)
    {
      entryObjects[i] = new Object[] { entries.get(i) };
    }

    return entryObjects;
  }



  /**
   * Tests with an invalid configuration entry.
   *
   * @param  entry  The invalid configuration entry to use for testing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigEntries",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigurations(Entry entry)
         throws Exception
  {
    String parentDNStr = "cn=Password Generators,cn=config";
    ConfigEntry parentEntry =
         DirectoryServer.getConfigEntry(DN.decode(parentDNStr));
    ConfigEntry configEntry = new ConfigEntry(entry, parentEntry);

    RandomPasswordGenerator generator = new RandomPasswordGenerator();
    generator.initializePasswordGenerator(configEntry);
  }
}

