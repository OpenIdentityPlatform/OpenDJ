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



import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.LDAPADListPlugin class.
 */
public class LDAPADListPluginTestCase
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
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-plugin-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-plugin-enabled: true",
         "ds-cfg-plugin-type: preParseSearch");

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
         DirectoryConfig.getConfigEntry(DN.decode("cn=Plugins,cn=config"));
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    LDAPADListPlugin plugin = new LDAPADListPlugin();
    plugin.initializePlugin(pluginTypes, configEntry);
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
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-plugin-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-plugin-enabled: true");
    entries.add(e);

    for (String s : PluginType.getPluginTypeNames())
    {
      if (s.equalsIgnoreCase("preParseSearch"))
      {
        continue;
      }

      e = TestCaseUtils.makeEntry(
           "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
           "objectClass: top",
           "objectClass: ds-cfg-plugin",
           "cn: LDAP Attribute Description List",
           "ds-cfg-plugin-class: org.opends.server.plugins.LDAPADListPlugin",
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
         DirectoryConfig.getConfigEntry(DN.decode("cn=Plugins,cn=config"));
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    LDAPADListPlugin plugin = new LDAPADListPlugin();
    plugin.initializePlugin(pluginTypes, configEntry);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an empty attribute
   * list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreParseSearchWithEmptyAttrList()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LinkedHashSet<String> attrList = new LinkedHashSet<String>();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("o=test"), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"), attrList);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e.getAttribute("o"));
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains a standard attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreParseSearchWithRequestedAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LinkedHashSet<String> attrList = new LinkedHashSet<String>();
    attrList.add("o");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("o=test"), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"), attrList);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e.getAttribute("o"));
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreParseSearchWithRequestedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LinkedHashSet<String> attrList = new LinkedHashSet<String>();
    attrList.add("@organization");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("o=test"), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"), attrList);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e.getAttribute("o"));
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an undefined objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDoPreParseSearchWithRequestedUndefinedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LinkedHashSet<String> attrList = new LinkedHashSet<String>();
    attrList.add("@undefined");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("o=test"), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"), attrList);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e.getAttribute("o"));
  }
}

