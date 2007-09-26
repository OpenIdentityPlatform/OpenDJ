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
package org.opends.server.plugins;



import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.EntryUUIDPluginCfgDefn;
import org.opends.server.admin.std.server.EntryUUIDPluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



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
  @BeforeClass()
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
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
      }
    }


    EntryUUIDPluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              EntryUUIDPluginCfgDefn.getInstance(), e);

    EntryUUIDPlugin plugin = new EntryUUIDPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }



  /**
   * Tests the process of initializing the server with valid configurations but
   * without the entryUUID attribute type defined in the server.
   *
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigsWithoutSchema(Entry e)
         throws Exception
  {
    AttributeType entryUUIDType = DirectoryConfig.getAttributeType("entryuuid",
                                                                   false);
    DirectoryServer.deregisterAttributeType(entryUUIDType);


    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
      }
    }


    EntryUUIDPluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              EntryUUIDPluginCfgDefn.getInstance(), e);

    EntryUUIDPlugin plugin = new EntryUUIDPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();


    DirectoryServer.registerAttributeType(entryUUIDType, false);
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
    ArrayList<Entry> entries = new ArrayList<Entry>();

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
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
      }
    }


    EntryUUIDPluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              EntryUUIDPluginCfgDefn.getInstance(), e);

    EntryUUIDPlugin plugin = new EntryUUIDPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
  }



  /**
   * Tests the <CODE>doLDIFImport</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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

    DN dn = DN.decode("cn=Entry UUID,cn=plugins,cn=config");
    EntryUUIDPlugin plugin =
         (EntryUUIDPlugin)
         DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);
    plugin.doLDIFImport(importConfig, e);

    assertNotNull(e.getAttribute("entryuuid"));
  }



  /**
   * Tests the <CODE>doLDIFImport</CODE> method with an entry that already has
   * the entryUUID operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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

    DN dn = DN.decode("cn=Entry UUID,cn=plugins,cn=config");
    EntryUUIDPlugin plugin =
         (EntryUUIDPlugin)
         DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);
    plugin.doLDIFImport(importConfig, e);

    assertNotNull(e.getAttribute("entryuuid"));
  }



  /**
   * Tests the <CODE>doPreOperationAdd</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreOperationAdd()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    e = DirectoryConfig.getEntry(e.getDN());
    assertNotNull(e);
    assertNotNull(e.getAttribute("entryuuid"));
  }



  /**
   * Tests the <CODE>doPreOperationAdd</CODE> method with an entry that already
   * has the entryUUID operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreOperationAddWithExistingUUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test",
                                      "entryUUID: " + UUID.randomUUID());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    e = DirectoryConfig.getEntry(e.getDN());
    assertNotNull(e);
    assertNotNull(e.getAttribute("entryuuid"));
  }
}

