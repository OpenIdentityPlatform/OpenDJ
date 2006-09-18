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
package org.opends.server.plugins;



import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.PasswordPolicyImportPluginTestCase class.
 */
public class PasswordPolicyImportPluginTestCase
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
         "dn: cn=Password Policy Import,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "cn: Password Policy Import",
         "ds-cfg-plugin-class: org.opends.server.plugins." +
              "PasswordPolicyImportPlugin",
         "ds-cfg-plugin-enabled: true",
         "ds-cfg-plugin-type: ldifImport");

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


    ConfigEntry parentEntry =
         DirectoryServer.getConfigEntry(DN.decode("cn=Plugins,cn=config"));
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    PasswordPolicyImportPlugin plugin = new PasswordPolicyImportPlugin();
    plugin.initializePlugin(DirectoryServer.getInstance(), pluginTypes,
                            configEntry);
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
      if (s.equalsIgnoreCase("ldifimport"))
      {
        continue;
      }

      Entry e = TestCaseUtils.makeEntry(
           "dn: cn=Password Policy Import,cn=Plugins,cn=config",
           "objectClass: top",
           "objectClass: ds-cfg-plugin",
           "cn: Password Policy Import",
           "ds-cfg-plugin-class: org.opends.server.plugins." +
                "PasswordPolicyImportPlugin",
           "ds-cfg-plugin-enabled: true",
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
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
        }
      }
    }


    ConfigEntry parentEntry =
         DirectoryServer.getConfigEntry(DN.decode("cn=Plugins,cn=config"));
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    PasswordPolicyImportPlugin plugin = new PasswordPolicyImportPlugin();
    plugin.initializePlugin(DirectoryServer.getInstance(), pluginTypes,
                            configEntry);
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

    DN dn = DN.decode("cn=Password Policy Import,cn=plugins,cn=config");
    PasswordPolicyImportPlugin plugin =
         (PasswordPolicyImportPlugin)
         DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);

    String[] entryLines =
    {
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test",
      "",
      "dn: uid=test.user1,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user1",
      "givenName: Test",
      "sn: User1",
      "cn: Test User1",
      "userPassword: password",
      "",
      "dn: uid=test.user2,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user2",
      "givenName: Test",
      "sn: User2",
      "cn: Test User2",
      "userPassword: password",
      "pwdPolicySubentry: cn=SSHA512 UserPassword Policy," +
           "cn=Password Policies,cn=config",
      "",
      "dn: uid=test.user3,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: authPasswordObject",
      "uid: test.user3",
      "givenName: Test",
      "sn: User3",
      "cn: Test User3",
      "authPassword: password",
      "pwdPolicySubentry: cn=SHA1 AuthPassword Policy,cn=Password Policies," +
           "cn=config"
    };

    String ldifString = TestCaseUtils.makeLdif(entryLines);
    ByteArrayInputStream bais =
         new ByteArrayInputStream(ldifString.getBytes("UTF-8"));
    LDIFImportConfig importConfig = new LDIFImportConfig(bais);

    for (Entry e : TestCaseUtils.makeEntries(entryLines))
    {
      plugin.doLDIFImport(importConfig, e);
    }
  }
}

