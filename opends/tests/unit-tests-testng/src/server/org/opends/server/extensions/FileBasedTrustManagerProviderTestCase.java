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



import java.io.File;
import java.io.FileWriter;
import java.util.List;
import javax.net.ssl.TrustManager;

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

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the file-based trust manager provider.
 */
public class FileBasedTrustManagerProviderTestCase
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

    FileWriter writer = new FileWriter(DirectoryServer.getServerRoot() +
                                       File.separator + "config" +
                                       File.separator + "server.pin");
    writer.write("password" + EOL);
    writer.close();

    writer = new FileWriter(DirectoryServer.getServerRoot() + File.separator +
                            "config" + File.separator + "empty");
    writer.close();

    System.setProperty("org.opends.server.trustStorePIN", "password");
  }



  /**
   * Retrieves a set of valid configurations that can be used to
   * initialize the file-based trust manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/server.pin",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: org.opends.server.trustStorePIN",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "ds-cfg-trust-store-type: JKS");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an valid configurations.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testVvalidConfigs(Entry e)
         throws Exception
  {
    DN parentDN = DN.decode("cn=SSL,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    FileBasedTrustManagerProvider provider = new FileBasedTrustManagerProvider();
    provider.initializeTrustManagerProvider(configEntry);
    provider.finalizeTrustManagerProvider();
  }



  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize the file-based trust manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/nosuchfile",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/nosuchfile",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/empty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: nosuchproperty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-environment-variable: nosuchenv",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-trust-manager-provider-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-trust-manager-provider-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "ds-cfg-trust-store-type: invalid");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an invalid configuration.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigs(Entry e)
         throws Exception
  {
    DN parentDN = DN.decode("cn=SSL,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    FileBasedTrustManagerProvider provider =
         new FileBasedTrustManagerProvider();
    provider.initializeTrustManagerProvider(configEntry);
    for (StringBuilder sb : e.toLDIF())
    {
      System.err.println(sb.toString());
    }
  }
}

