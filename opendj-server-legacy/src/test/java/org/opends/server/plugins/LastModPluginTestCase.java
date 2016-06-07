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
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.LastModPluginCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.LastModPlugin class.
 */
public class LastModPluginTestCase
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
         "dn: cn=LastMod,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-last-mod-plugin",
         "cn: LastMod",
         "ds-cfg-java-class: org.opends.server.plugins.LastModPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preOperationAdd",
         "ds-cfg-plugin-type: preOperationModify",
         "ds-cfg-plugin-type: preOperationModifyDN",
         "",
         "dn: cn=LastMod,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-last-mod-plugin",
         "cn: LastMod",
         "ds-cfg-java-class: org.opends.server.plugins.LastModPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preOperationAdd",
         "",
         "dn: cn=LastMod,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-last-mod-plugin",
         "cn: LastMod",
         "ds-cfg-java-class: org.opends.server.plugins.LastModPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preOperationModify",
         "",
         "dn: cn=LastMod,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-last-mod-plugin",
         "cn: LastMod",
         "ds-cfg-java-class: org.opends.server.plugins.LastModPlugin",
         "ds-cfg-enabled: true",
         "ds-cfg-plugin-type: preOperationModifyDN");

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
    LastModPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();
  }

  private LastModPlugin initializePlugin(Entry e) throws ConfigException, InitializationException {
    return InitializationUtils.initializePlugin(new LastModPlugin(), e, LastModPluginCfgDefn.getInstance());
  }

  /**
   * Tests the process of initializing the server with valid configurations but
   * without the lastmod schema defined in the server.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigsWithoutSchema(Entry e)
         throws Exception
  {
    AttributeType ctType = getCreateTimestampAttributeType();
    AttributeType cnType = getCreatorsNameAttributeType();
    AttributeType mtType = getModifyTimestampAttributeType();
    AttributeType mnType = getModifiersNameAttributeType();

    DirectoryServer.getSchema().deregisterAttributeType(ctType);
    DirectoryServer.getSchema().deregisterAttributeType(cnType);
    DirectoryServer.getSchema().deregisterAttributeType(mtType);
    DirectoryServer.getSchema().deregisterAttributeType(mnType);


    LastModPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();


    DirectoryServer.getSchema().registerAttributeType(ctType, null, false);
    DirectoryServer.getSchema().registerAttributeType(cnType, null, false);
    DirectoryServer.getSchema().registerAttributeType(mtType, null, false);
    DirectoryServer.getSchema().registerAttributeType(mnType, null, false);
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
      if (s.equalsIgnoreCase("preOperationAdd") ||
          s.equalsIgnoreCase("preOperationModify") ||
          s.equalsIgnoreCase("preOperationModifyDN"))
      {
        continue;
      }

      Entry e = TestCaseUtils.makeEntry(
           "dn: cn=LastMod,cn=Plugins,cn=config",
           "objectClass: top",
           "objectClass: ds-cfg-plugin",
           "objectClass: ds-cfg-last-mod-plugin",
           "cn: LastMod",
           "ds-cfg-java-class: org.opends.server.plugins.LastModPlugin",
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
    LastModPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();
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
    assertThat(e.getAttribute("creatorsname")).isNotEmpty();
    assertThat(e.getAttribute("createtimestamp")).isNotEmpty();
  }



  /**
   * Tests the <CODE>doPreOperationModify</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreOperationModify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<Modification> mods = new ArrayList<>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create("description", "foo")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.valueOf("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    Entry e = DirectoryConfig.getEntry(DN.valueOf("o=test"));
    assertNotNull(e);
    assertThat(e.getAttribute("modifiersname")).isNotEmpty();
    assertThat(e.getAttribute("modifytimestamp")).isNotEmpty();
  }



  /**
   * Tests the <CODE>doPreOperationModifyDN</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreOperationModifyDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry e = TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    ModifyDNOperation modifyDNOperation =
        getRootConnection().processModifyDN(e.getName(), RDN.valueOf("cn=test2"), false);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    e = DirectoryConfig.getEntry(DN.valueOf("cn=test2,o=test"));
    assertNotNull(e);
    assertThat(e.getAttribute("modifiersname")).isNotEmpty();
    assertThat(e.getAttribute("modifytimestamp")).isNotEmpty();
  }
}
