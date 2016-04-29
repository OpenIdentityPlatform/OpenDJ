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
 * Copyright 2011 profiq s.r.o.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.AttributeCleanupPluginCfgDefn;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/** Tests for the attribute cleanup plugin. */
@SuppressWarnings("javadoc")
public class AttributeCleanupPluginTestCase extends PluginTestCase
{

  @BeforeClass
  public void startServer() throws Exception
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

    return toArrayArray(entries);
  }

  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
    throws Exception
  {
    AttributeCleanupPlugin plugin = initializePlugin(e);
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

    return toArrayArray(entries);
  }

  private Object[][] toArrayArray(List<Entry> entries)
  {
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
    AttributeCleanupPlugin plugin = initializePlugin(e);
    plugin.finalizePlugin();
  }


/**
   * Verifies the attribute renaming in the incoming ADD operation.
   *
   * @throws Exception in case of bugs.
   */
  @Test
  public void testRenameAttributesForAddOperation() throws Exception
  {
    // Configure the plugin to rename incoming 'cn' attributes to 'description'.
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

    AttributeCleanupPlugin plugin = initializePlugin(confEntry);

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
    AddOperationBasis addOperation = add("dn: uid=test,dc=example,dc=com",
        RawAttribute.create("objectClass", toByteStrings("top", "person", "organizationalperson", "inetorgperson")),
        RawAttribute.create("uid", "test"),
        RawAttribute.create("cn", "Name Surname"),
        RawAttribute.create("sn", "Surname"));


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
        List<ByteString> attrVals = rawAttr.getValues();
        assertEquals("Name Surname", attrVals.get(0).toString());
        plugin.finalizePlugin();
        return;
      }
    }

    fail();
  }

  private ArrayList<ByteString> toByteStrings(String... strings)
  {
    ArrayList<ByteString> results = new ArrayList<>(strings.length);
    for (String s : strings)
    {
      results.add(ByteString.valueOfUtf8(s));
    }
    return results;
  }

  private AddOperationBasis add(String entryDN, RawAttribute... rawAttributes)
  {
    return new AddOperationBasis(getRootConnection(), 1, 1, null,
        ByteString.valueOfUtf8(entryDN), Arrays.asList(rawAttributes));
  }

  /**
   * Verifies the attribute removal in the incoming ADD request.
   * @throws Exception in case of bugs.
   */
  @Test
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

    AttributeCleanupPlugin plugin = initializePlugin(confEntry);

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
    AddOperationBasis addOperation = add("dn: uid=test,dc=example,dc=com",
        RawAttribute.create("objectClass", toByteStrings("top", "person", "organizationalperson", "inetorgperson")),
        RawAttribute.create("uid", "test"),
        RawAttribute.create("cn", "Name Surname"),
        RawAttribute.create("sn", "Surname"),
        RawAttribute.create("modifyTimeStamp", "2011091212400000Z"),
        RawAttribute.create("createTimeStamp", "2011091212400000Z"));


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
      assertFalse(rawAttr.getAttributeType().equalsIgnoreCase("modifyTimeStamp")
          || rawAttr.getAttributeType().equalsIgnoreCase("createTimeStamp"),
          "Attribute '" + rawAttr.getAttributeType() + "' exists and it shouldn't");
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
  @Test
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

    AttributeCleanupPlugin plugin = initializePlugin(confEntry);

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
    ModifyOperationBasis modifyOperation = modify("dn: uid=test,dc=example,dc=com",
        newRawModification(REPLACE, "modifyTimeStamp", "2011091212400000Z"),
        newRawModification(REPLACE, "createTimeStamp", "2011091212400000Z"));

    /* Process the request. The result should be SUCCESS and the server
     * should stop the processing.
     */
    PluginResult.PreParse res = plugin.doPreParse(modifyOperation);
    assertFalse(res.continueProcessing());
    assertSame(res.getResultCode(), ResultCode.SUCCESS);

    plugin.finalizePlugin();
  }

  private AttributeCleanupPlugin initializePlugin(Entry confEntry) throws ConfigException, InitializationException {
    return InitializationUtils.initializePlugin(
        new AttributeCleanupPlugin(), confEntry, AttributeCleanupPluginCfgDefn.getInstance());
  }

  private ModifyOperationBasis modify(String entryDN, RawModification... rawMods)
  {
    return new ModifyOperationBasis(
        getRootConnection(), 1, 1, null, ByteString.valueOfUtf8(entryDN), newArrayList(rawMods));
  }

  /**
   * Verify the attribute renaming for a MODIFY operation.
   * @throws Exception in case of a bug.
   */
  @Test
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

    AttributeCleanupPlugin plugin = initializePlugin(confEntry);

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
    ModifyOperationBasis modifyOperation = modify("dn: uid=test,dc=example,dc=com",
        newRawModification(REPLACE, "cn", "Test User"),
        newRawModification(REPLACE, "sn", "User"),
        newRawModification(REPLACE, "modifyTimeStamp", "2011091212400000Z"),
        newRawModification(REPLACE, "createTimeStamp", "2011091212400000Z"));

    /* Process the MODIFY operation making sure the remaining number of
     * modifications is 2 and that the '*TimeStamp' modifications are
     * removed.
     */
    plugin.doPreParse(modifyOperation);

    assertEquals(modifyOperation.getRawModifications().size(), 2);

    List<RawModification> rawMods = modifyOperation.getRawModifications();
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

  private RawModification newRawModification(ModificationType modType, String attrName, String attrValue)
  {
    return RawModification.create(modType, attrName, attrValue);
  }


  /**
   * Verify the attribute renaming for the MODIFY operation.
   */
  @Test
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

    AttributeCleanupPlugin plugin = initializePlugin(confEntry);

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
    ModifyOperationBasis modifyOperation = modify("dn: uid=test,dc=example,dc=com",
        newRawModification(REPLACE, "cn", "Test User"),
        newRawModification(REPLACE, "sn", "User"),
        newRawModification(REPLACE, "modifyTimeStamp", "2011091212400000Z"));

    /* Process the MODIFY operation. */
    PluginResult.PreParse res = plugin.doPreParse(modifyOperation);
    assertTrue(res.continueProcessing());

    /* Verify that the attribute has been properly renamed by comparing
     * the value of the attribute 'description' with the original value
     * of the 'modifyTimeStamp' attribute.
     */
    List<RawModification> rawMods = modifyOperation.getRawModifications();
    assertNotNull(rawMods);
    for(RawModification rawMod : rawMods )
    {
      RawAttribute modAttr = rawMod.getAttribute();
      if (modAttr.getAttributeType().equalsIgnoreCase("description"))
      {
        List<ByteString> descrValues = modAttr.getValues();
        assertEquals("2011091212400000Z", descrValues.get(0).toString());
        plugin.finalizePlugin();
        return;
      }
      assertFalse(modAttr.getAttributeType().equalsIgnoreCase("modifyTimeStamp"),
          "modifyTimeStamp shouldn't exist but it does.");
    }

    fail();
  }

}
