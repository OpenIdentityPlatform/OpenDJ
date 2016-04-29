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
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.LDAPAttributeDescriptionListPluginCfgDefn;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-enabled: true",
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
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    LDAPADListPlugin plugin = initializePlugin0(e);
    plugin.finalizePlugin();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs() throws Exception
  {
    ArrayList<Entry> entries = new ArrayList<>();
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=LDAP Attribute Description List,cn=Plugins,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-plugin",
         "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
         "cn: LDAP Attribute Description List",
         "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
         "ds-cfg-enabled: true");
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
           "objectClass: ds-cfg-ldap-attribute-description-list-plugin",
           "cn: LDAP Attribute Description List",
           "ds-cfg-java-class: org.opends.server.plugins.LDAPADListPlugin",
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
    LDAPADListPlugin plugin = initializePlugin0(e);
    plugin.finalizePlugin();
  }

  private LDAPADListPlugin initializePlugin0(Entry e) throws ConfigException, InitializationException {
    return InitializationUtils.initializePlugin(
        new LDAPADListPlugin(), e, LDAPAttributeDescriptionListPluginCfgDefn.getInstance());
  }

  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an empty attribute
   * list.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithEmptyAttrList()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT);
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains a standard attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addAttribute("o");
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addAttribute("@organization");
    assertAttributeOExists(request);
  }



  /**
   * Tests the <CODE>doPreParseSearch</CODE> method with an attribute list that
   * contains an undefined objectclass name.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDoPreParseSearchWithRequestedUndefinedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final SearchRequest request =
        newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT).addAttribute("@undefined");
    assertAttributeOExists(request);
  }

  private void assertAttributeOExists(final SearchRequest request)
  {
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());

    Entry e = searchOperation.getSearchEntries().get(0);
    assertThat(e.getAttribute("o")).isNotEmpty();
  }
}
