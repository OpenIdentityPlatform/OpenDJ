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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.ByteArrayInputStream;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.types.DN;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFReader;

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
    String[] entryStrings =
    {
      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set:\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set: foo:\n" +
      "ds-cfg-password-format: foo:8\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set: foo:abcd\n" +
      "ds-cfg-password-character-set: foo:efgh\n" +
      "ds-cfg-password-format: foo:8\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set: foo:abcd\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set: foo:abcd\n" +
      "ds-cfg-password-format: bar:8\n",

      "dn: cn=Random Password Generator,cn=Password Generators,cn=config\n" +
      "objectClass: top\n" +
      "objectClass: ds-cfg-password-generator\n" +
      "objectClass: ds-cfg-random-password-generator\n" +
      "cn: Random Password Generator\n" +
      "ds-cfg-password-generator-class: " +
           "org.opends.server.extensions.RandomPasswordGenerator\n" +
      "ds-cfg-password-generator-enabled: true\n" +
      "ds-cfg-password-character-set: foo:abcd\n" +
      "ds-cfg-password-format: foo:abcd\n",
    };


    Object[][] entryObjects = new Object[entryStrings.length][1];
    for (int i=0; i < entryStrings.length; i++)
    {
      entryObjects[i] = new Object[] { entryStrings[i] };
    }
    return entryObjects;
  }



  /**
   * Tests with an invalid configuration entry.
   *
   * @param  ldifString  The LDIF representation of the configuration entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigEntries",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigurations(String ldifString)
         throws Exception
  {
    ByteArrayInputStream bais =
         new ByteArrayInputStream(ldifString.getBytes("UTF-8"));
    LDIFImportConfig importConfig = new LDIFImportConfig(bais);
    importConfig.setValidateSchema(false);
    LDIFReader reader = new LDIFReader(new LDIFImportConfig(bais));

    String parentDNStr = "cn=Password Generators,cn=config";
    ConfigEntry parentEntry =
         DirectoryServer.getConfigEntry(DN.decode(parentDNStr));
    ConfigEntry configEntry = new ConfigEntry(reader.readEntry(), parentEntry);

    RandomPasswordGenerator generator = new RandomPasswordGenerator();
    generator.initializePasswordGenerator(configEntry);
  }
}

