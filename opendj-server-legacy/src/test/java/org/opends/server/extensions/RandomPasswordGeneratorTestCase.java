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
package org.opends.server.extensions;

import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.RandomPasswordGeneratorCfgDefn;
import org.opends.server.types.Entry;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.DN;
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
  @BeforeClass
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
  @Test
  public void testDefaultConfiguration()
         throws Exception
  {
    Entry configEntry = DirectoryServer.getEntry(
        DN.valueOf("cn=Random Password Generator,cn=Password Generators,cn=config"));
    assertNotNull(configEntry);

    RandomPasswordGenerator generator = InitializationUtils.initializePasswordGenerator(
        new RandomPasswordGenerator(), configEntry, RandomPasswordGeneratorCfgDefn.getInstance());
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
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "ds-cfg-password-character-set:",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "ds-cfg-password-character-set: foo:",
      "ds-cfg-password-format: foo:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "ds-cfg-password-character-set: foo:efgh",
      "ds-cfg-password-format: foo:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
      "ds-cfg-password-character-set: foo:abcd",
      "ds-cfg-password-format: bar:8",
      "",
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-generator",
      "objectClass: ds-cfg-random-password-generator",
      "cn: Random Password Generator",
      "ds-cfg-java-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator",
      "ds-cfg-enabled: true",
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
    InitializationUtils.initializePasswordGenerator(
        new RandomPasswordGenerator(), entry, RandomPasswordGeneratorCfgDefn.getInstance());
  }
}
