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

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.meta.FileBasedTrustManagerProviderCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    FileWriter writer = new FileWriter(DirectoryServer.getInstanceRoot() +
                                       File.separator + "config" +
                                       File.separator + "server.pin");
    writer.write("password" + EOL);
    writer.close();

    writer = new FileWriter(DirectoryServer.getInstanceRoot() + File.separator +
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
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/server.pin",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: org.opends.server.trustStorePIN",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
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
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(e);
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
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/nosuchfile",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/nosuchfile",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/empty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: nosuchproperty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-environment-variable: nosuchenv",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
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
    initializeTrustManagerProvider(e);
    for (StringBuilder sb : e.toLDIF())
    {
      System.err.println(sb.toString());
    }
  }

  private FileBasedTrustManagerProvider initializeTrustManagerProvider(Entry e) throws Exception {
    return InitializationUtils.initializeTrustManagerProvider(
        new FileBasedTrustManagerProvider(), e, FileBasedTrustManagerProviderCfgDefn.getInstance());
  }
}
