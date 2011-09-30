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
 *      Copyright 2011 profiq s.r.o.
 */
package org.opends.server.plugins;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.AttributeCleanupPluginCfgDefn;
import org.opends.server.admin.std.server.AttributeCleanupPluginCfg;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for the attribute cleanup plugin.
 */
public class AttributeCleanupPluginTestCase extends PluginTestCase
{



  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs() throws Exception
  {

    List<Entry> entries = TestCaseUtils.makeEntries(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "",
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "ds-cfg-remove-inbound-attributes: modifyTimeStamp",
      "ds-cfg-remove-inbound-attributes: createTimeStamp",
      "",
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "ds-cfg-rename-inbound-attributes: cn:uid");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;

  }

  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
    throws Exception
  {
    HashSet<PluginType> pluginTypes = getPluginTypes(e);

    assertTrue(!pluginTypes.isEmpty());

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),e);

    assertNotNull(config);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);
    plugin.finalizePlugin();
  }



  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs() throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
      /* local attribute is not defined */

      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "ds-cfg-rename-inbound-attributes: cn:badAttr",
      "",

      /* duplicate attributes */

      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "ds-cfg-rename-inbound-attributes: cn:uid",
      "ds-cfg-rename-inbound-attributes: cn:description",
      "",

      /* self mapping */

      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin",
      "ds-cfg-rename-inbound-attributes: cn:cn");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class } )
  public void testInitializeWithInvalidConfigs(Entry e)
    throws ConfigException, InitializationException
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");

    assertNotNull(attrList);

    for (Attribute attr : attrList)
    {
      for (AttributeValue value : attr)
      {
        pluginTypes.add(
          PluginType.forName(
            value.getValue().toString().toLowerCase()));
      }
    }

    assertTrue(!pluginTypes.isEmpty());

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),e);

    assertNotNull(config);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);
    plugin.finalizePlugin();
  }


/**
   * Verifies the attribute renaming in the incoming ADD operation.
   *
   * @throws Exception in case of bugs.
   */
  @Test()
  public void testRenameAttributesForAddOperation() throws Exception
  {
    /* Configure the plugint to rename incoming 'cn' attributes to
     * 'description'.
     */
    Entry confEntry = TestCaseUtils.makeEntry(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-rename-inbound-attributes: cn:description",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin");

    HashSet<PluginType> pluginTypes = getPluginTypes(confEntry);

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),confEntry);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);

    /* Construct the ADD operation as follows:
     *
     * dn: uid=test,dc=example,dc=com
     * objectClass: top
     * objectClass: person
     * objectClass: organizationalPerson
     * objectClass: inetOrgPerson
     * uid: test
     * cn: Name Surname
     * sn: Surname
     */
    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("top"));
    values.add(ByteString.valueOf("person"));
    values.add(ByteString.valueOf("organizationalperson"));
    values.add(ByteString.valueOf("inetorgperson"));

    List<RawAttribute> rawAttributes = new ArrayList<RawAttribute>();

    rawAttributes.add(RawAttribute.create("objectClass", values));
    rawAttributes.add(RawAttribute.create("uid", "test"));
    rawAttributes.add(RawAttribute.create("cn", "Name Surname"));
    rawAttributes.add(RawAttribute.create("sn", "Surname"));

    AddOperationBasis addOperation =
      new AddOperationBasis(InternalClientConnection.getRootConnection(),
                            1,
                            1,
                            null,
                            ByteString.valueOf("dn: uid=test,dc=example,dc=com"),
                            rawAttributes);

    /* Process the operation. The processing should continue. */

    PluginResult.PreParse res = plugin.doPreParse(addOperation);

    assertTrue(res.continueProcessing());

    /* Verify that the 'cn' attribute has been renamed to 'description'
     * by getting the 'decription' attribute and matching the value with
     * the original 'cn' value.
     */

    List<RawAttribute> rawAttrs = addOperation.getRawAttributes();

    assertNotNull(rawAttrs);

    for(RawAttribute rawAttr : rawAttrs)
    {
      if(rawAttr.getAttributeType().equalsIgnoreCase("description"))
      {
        ArrayList<ByteString> attrVals = rawAttr.getValues();
        assertEquals("Name Surname", attrVals.get(0).toString());
        plugin.finalizePlugin();
        return;
      }
    }

    fail();

  }

  /**
   * Verifies the attribute removal in the incoming ADD request.
   * @throws Exception in case of bugs.
   */
  @Test()
  public void testRemoveAttributesForAddOperation() throws Exception
  {
    /* Configure the plugin to remove 'modifyTimeStamp' and
     * 'createTimeStamp' attributes from the incoming ADD requests.
     */

    Entry confEntry = TestCaseUtils.makeEntry(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-remove-inbound-attributes: modifyTimeStamp",
      "ds-cfg-remove-inbound-attributes: createTimeStamp",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin");

    HashSet<PluginType> pluginTypes = getPluginTypes(confEntry);

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),confEntry);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);

    /* Create the ADD operation as follows:
     *
     * dn: uid=test,dc=example,dc=com
     * objectClass: top
     * objectClass: person
     * objectClass: organizationalPerson
     * objectClass: inetOrgPerson
     * uid: test
     * cn: Name Surname
     * sn: Surname
     * modifyTimeStamp: 2011091212400000Z
     * createTimeStamp: 2011091212400000Z
     */

    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("top"));
    values.add(ByteString.valueOf("person"));
    values.add(ByteString.valueOf("organizationalperson"));
    values.add(ByteString.valueOf("inetorgperson"));

    List<RawAttribute> rawAttributes = new ArrayList<RawAttribute>();

    rawAttributes.add(RawAttribute.create("objectClass", values));
    rawAttributes.add(RawAttribute.create("uid", "test"));
    rawAttributes.add(RawAttribute.create("cn", "Name Surname"));
    rawAttributes.add(RawAttribute.create("sn", "Surname"));
    rawAttributes.add(RawAttribute.create("modifyTimeStamp", "2011091212400000Z"));
    rawAttributes.add(RawAttribute.create("createTimeStamp", "2011091212400000Z"));

    AddOperationBasis addOperation =
      new AddOperationBasis(InternalClientConnection.getRootConnection(),
                            1,
                            1,
                            null,
                            ByteString.valueOf("dn: uid=test,dc=example,dc=com"),
                            rawAttributes);

    /* Process the operation and expect the server to continue
     * processing the operation.
     */

    PluginResult.PreParse res = plugin.doPreParse(addOperation);

    assertTrue(res.continueProcessing());

    /* Verify that the '*TimeStamp' attributes have been removed. */

    List<RawAttribute> rawAttrs = addOperation.getRawAttributes();
    assertNotNull(rawAttrs);

    for(RawAttribute rawAttr : rawAttrs)
    {
      if(rawAttr.getAttributeType().equalsIgnoreCase("modifyTimeStamp")
         || rawAttr.getAttributeType().equalsIgnoreCase("createTimeStamp"))
      {
        fail("Attribute '" + rawAttr.getAttributeType() + "' exists and it shouldn't");
      }
    }

    plugin.finalizePlugin();
  }


  /**
   * In some cases the plugin might remove all attributes from the
   * incoming MODIFY request which would make the request invalid by the
   * LDAP standards. However, this is a special case and the request
   * should be silently dropped while the client should be notified of
   * SUCCESS.
   * @throws Exception in case of a bug.
   */
  @Test()
  public void testRemoveAttributesForModifyOperationInvalid() throws Exception
  {
    /* Configure the plugin to remove 'modifyTimeStamp' and
     * 'createTimeStamp' attributes from the incoming MODIFY request.
     */

    Entry confEntry = TestCaseUtils.makeEntry(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-remove-inbound-attributes: modifyTimeStamp",
      "ds-cfg-remove-inbound-attributes: createTimeStamp",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin");

    HashSet<PluginType> pluginTypes = getPluginTypes(confEntry);

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),confEntry);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);

    /* Create the MODIFY request as follows:
     *
     * dn: uid=test,dc=example,dc=com
     * changetype: modify
     * replace: modifyTimeStamp
     * modifyTimeStamp: 2011091212400000Z
     * -
     * replace: createTimeStamp
     * createTimeStamp: 2011091212400000Z
     * -
     */

    List<RawModification> rawMods= new ArrayList<RawModification>();

    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "modifyTimeStamp",
                                       "2011091212400000Z"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "createTimeStamp",
                                       "2011091212400000Z"));

    ModifyOperationBasis modifyOperation =
      new ModifyOperationBasis(InternalClientConnection.getRootConnection(),
                               1,
                               1,
                               null,
                               ByteString.valueOf("dn: uid=test,dc=example,dc=com"),
                               rawMods);

    /* Process the request. The result should be SUCCESS and the server
     * should stop the processing.
     */
    PluginResult.PreParse res = plugin.doPreParse(modifyOperation);
    assertFalse(res.continueProcessing());
    assertTrue(res.getResultCode() == ResultCode.SUCCESS);

    plugin.finalizePlugin();

  }

  /**
   * Verify the attribute renaming for a MODIFY operation.
   * @throws Exception in case of a bug.
   */
  @Test()
  public void testRemoveAttributesForModifyOperationValid() throws Exception
  {
    /* Configure the plugin to remove 'modifyTimeStamp' and
     * 'createTimeStamp' attributes from the incoming requests.
     */

    Entry confEntry = TestCaseUtils.makeEntry(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-remove-inbound-attributes: modifyTimeStamp",
      "ds-cfg-remove-inbound-attributes: createTimeStamp",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin");

    HashSet<PluginType> pluginTypes = getPluginTypes(confEntry);

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),confEntry);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);

    /* Create the MODIFY operation as follows:
     *
     * dn: uid=test,dc=example,dc=com
     * changetype: modify
     * replace: cn
     * cn: Test User
     * -
     * replace: sn
     * sn: User
     * -
     * replace: modifyTimeStamp
     * modifyTimeStamp: 2011091212400000Z
     * -
     * replace: createTimeStamp
     * createTimeStamp: 2011091212400000Z
     * -
     */

    List<RawModification> rawMods= new ArrayList<RawModification>();

    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "cn",
                                       "Test User"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "sn",
                                       "User"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "modifyTimeStamp",
                                       "2011091212400000Z"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "createTimeStamp",
                                       "2011091212400000Z"));

    ModifyOperationBasis modifyOperation =
      new ModifyOperationBasis(InternalClientConnection.getRootConnection(),
                               1,
                               1,
                               null,
                               ByteString.valueOf("dn: uid=test,dc=example,dc=com"),
                               rawMods);

    /* Process the MODIFY operation making sure the remaining number of
     * modifications is 2 and that the '*TimeStamp' modifications are
     * removed.
     */
    PluginResult.PreParse res = plugin.doPreParse(modifyOperation);

    assertTrue(modifyOperation.getRawModifications().size() == 2);

    rawMods = modifyOperation.getRawModifications();

    assertNotNull(rawMods);

    for(RawModification rawMod : rawMods )
    {
      RawAttribute modAttr = rawMod.getAttribute();
      if(modAttr.getAttributeType().equalsIgnoreCase("modifyTimeStamp")
         || modAttr.getAttributeType().equalsIgnoreCase("createTimeStamp"))
      {
        fail("Attribute '" + modAttr.getAttributeType()
             + "' exists and it shouldn't");
      }
    }

    plugin.finalizePlugin();

  }


  /**
   * Verify the attribute renaming for the MODIFY operation.
   * @throws Exception
   */
  @Test()
  public void testRenameAttributesForModifyOperation() throws Exception
  {
    /* Configure the plugin to rename the 'modifyTimeStamp' attribute to
     * 'description'.
     */
    Entry confEntry = TestCaseUtils.makeEntry(
      "dn: cn=Attribute Cleanup,cn=Plugins,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-plugin",
      "objectClass: ds-cfg-attribute-cleanup-plugin",
      "cn: Attribute Cleanup",
      "ds-cfg-enabled: true",
      "ds-cfg-plugin-type: preparseadd",
      "ds-cfg-plugin-type: preparsemodify",
      "ds-cfg-rename-inbound-attributes: modifyTimeStamp:description",
      "ds-cfg-java-class: org.opends.server.plugins.AttributeCleanupPlugin");

    HashSet<PluginType> pluginTypes = getPluginTypes(confEntry);

    AttributeCleanupPluginCfg config =
      AdminTestCaseUtils.getConfiguration(
        AttributeCleanupPluginCfgDefn.getInstance(),confEntry);

    AttributeCleanupPlugin plugin = new AttributeCleanupPlugin();

    plugin.initializePlugin(pluginTypes, config);

    /* Create the MODIFY operation as follows:
     *
     * dn: uid=test,dc=exampple,dc=com
     * changetype: modify
     * replace: cn
     * cn: Test User
     * -
     * replace: sn
     * sn: User
     * -
     * replace: modifyTimeStamp
     * modifyTimeStamp: 2011091212400000Z
     */
    List<RawModification> rawMods= new ArrayList<RawModification>();

    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "cn",
                                       "Test User"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "sn",
                                       "User"));
    rawMods.add(RawModification.create(ModificationType.REPLACE,
                                       "modifyTimeStamp",
                                       "2011091212400000Z"));

    ModifyOperationBasis modifyOperation =
      new ModifyOperationBasis(InternalClientConnection.getRootConnection(),
                               1,
                               1,
                               null,
                               ByteString.valueOf("dn: uid=test,dc=example,dc=com"),
                               rawMods);

    /* Process the MODIFY operation. */

    PluginResult.PreParse res = plugin.doPreParse(modifyOperation);

    assertTrue(res.continueProcessing());

    /* Verify that the attribute has been properly renamed by comparing
     * the value of the attribute 'description' with the original value
     * of the 'modifyTimeStamp' attribute.
     */
    rawMods = modifyOperation.getRawModifications();

    assertNotNull(rawMods);

    for(RawModification rawMod : rawMods )
    {
      RawAttribute modAttr = rawMod.getAttribute();
      if (modAttr.getAttributeType().equalsIgnoreCase("description"))
      {
        ArrayList<ByteString> descrValues = modAttr.getValues();
        assertEquals("2011091212400000Z", descrValues.get(0).toString());
        plugin.finalizePlugin();
        return;
      }
      if (modAttr.getAttributeType().equalsIgnoreCase("modifyTimeStamp"))
      {
        fail("modifyTimeStamp shouldn't exist but it does.");
      }
    }

    fail();


  }


  /**
   * Helper method to get the plugin types from the configuration entry.
   * @param e Configuration entry.
   * @return HashSet of plugin types.
   */
  private HashSet<PluginType> getPluginTypes(Entry e)
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");

    for(Attribute a : attrList)
    {
      for (AttributeValue v : a)
      {
      pluginTypes.add(
        PluginType.forName(v.getValue().toString().toLowerCase()));
      }
    }

    return pluginTypes;
  }

}
