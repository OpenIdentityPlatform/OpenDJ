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
package org.opends.server.plugins;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.EntryUUIDPluginCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.EntryUUIDPlugin class.
 */
public class EntryUUIDPluginTestCase
       extends PluginTestCase
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
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Entry UUID,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-entry-uuid-plugin",
         "cn: Entry UUID",
         "ds-cfg-java-class: org.opends.server.plugins.EntryUUIDPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: ldifImport",
         "ds-cfg-plugin-type: preOperationAdd",
         "",
         "dn: cn=Entry UUID,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-entry-uuid-plugin",
         "cn: Entry UUID",
         "ds-cfg-java-class: org.opends.server.plugins.EntryUUIDPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: ldifImport",
         "",
         "dn: cn=Entry UUID,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-entry-uuid-plugin",
         "cn: Entry UUID",
         "ds-cfg-java-class: org.opends.server.plugins.EntryUUIDPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preOperationAdd");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    EntryUUIDPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();
  }



  /**
   * Tests the process of initializing the server with valid configurations but
   * without the entryUUID attribute type defined in the server.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigsWithoutSchema(Entry e)
         throws Exception
  {
    AttributeType entryUUIDType = CoreSchema.getEntryUUIDAttributeType();
    DirectoryServer.getSchema().deregisterAttributeType(entryUUIDType);

    EntryUUIDPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();


    DirectoryServer.getSchema().registerAttributeType(entryUUIDType, null, false);
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    ArrayList<Entry> entries = new ArrayList<>();

    for (String s : PluginType.getPluginTypeNames())
    {
      if (s.equalsIgnoreCase("ldifImport") ||
          s.equalsIgnoreCase("preOperationAdd"))
      {
        continue;
      }

      Entry e = TestCaseUtils.makeEntry(
           "dn: cn=Entry UUID,cn=Plugins,cn=config",
           "objectClass: top",
           "objectClass: ds-cfg-plugin",
           "objectClass: ds-cfg-entry-uuid-plugin",
           "cn: Entry UUID",
           "ds-cfg-java-class: org.opends.server.plugins.EntryUUIDPlugin",
           "ds-cfg-enabled: true",
           "ds-cfg-plugin-type: " + s);
      entries.add(e);
    }

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    initializePlugin(e);
  }

  private EntryUUIDPlugin initializePlugin(Entry e) throws ConfigException, InitializationException {
    return InitializationUtils.initializePlugin(new EntryUUIDPlugin(), e, EntryUUIDPluginCfgDefn.getInstance());
  }

  /**
   * Tests the <CODE>doLDIFImport</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoLDIFImport()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String ldifString = TestCaseUtils.makeLdif("dn: o=test",
                                               "objectClass: top",
                                               "objectClass: organization",
                                               "o: test");

    Entry e = TestCaseUtils.makeEntry("dn: o=test",
                                      "objectClass: top",
                                      "objectClass: organization",
                                      "o: test");

    ByteArrayInputStream bais =
         new ByteArrayInputStream(ldifString.getBytes("UTF-8"));
    LDIFImportConfig importConfig = new LDIFImportConfig(bais);

    DN dn = DN.valueOf("cn=Entry UUID,cn=plugins,cn=config");
    EntryUUIDPlugin plugin =
         (EntryUUIDPlugin)
         DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);
    plugin.doLDIFImport(importConfig, e);

    assertThat(e.getAttribute("entryuuid")).isNotEmpty();
  }



  /**
   * Tests the <CODE>doLDIFImport</CODE> method with an entry that already has
   * the entryUUID operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoLDIFImportWithExistingUUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String uuid = UUID.randomUUID().toString();

    String ldifString = TestCaseUtils.makeLdif("dn: o=test",
                                               "objectClass: top",
                                               "objectClass: organization",
                                               "o: test",
                                               "entryUUID: " + uuid);

    Entry e = TestCaseUtils.makeEntry("dn: o=test",
                                      "objectClass: top",
                                      "objectClass: organization",
                                      "o: test",
                                      "entryUUID: " + uuid);

    ByteArrayInputStream bais =
         new ByteArrayInputStream(ldifString.getBytes("UTF-8"));
    LDIFImportConfig importConfig = new LDIFImportConfig(bais);

    DN dn = DN.valueOf("cn=Entry UUID,cn=plugins,cn=config");
    EntryUUIDPlugin plugin =
         (EntryUUIDPlugin)
         DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);
    plugin.doLDIFImport(importConfig, e);

    assertThat(e.getAttribute("entryuuid")).isNotEmpty();
  }



  /**
   * Tests the <CODE>doPreOperationAdd</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreOperationAdd()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry e = TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");
    assertThat(e.getAttribute("entryuuid")).isNotEmpty();
  }



  /**
   * Tests the <CODE>doPreOperationAdd</CODE> method with an entry that already
   * has the entryUUID operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreOperationAddWithExistingUUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test",
                                      "entryUUID: " + UUID.randomUUID());
    assertThat(e.getAttribute("entryuuid")).isNotEmpty();
  }
}
