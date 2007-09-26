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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */


package org.opends.server.plugins;

import org.testng.annotations.*;
import static org.testng.Assert.assertEquals;
import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.config.ConfigException;
import org.opends.server.admin.std.server.UniqueAttributePluginCfg;
import org.opends.server.admin.std.meta.UniqueAttributePluginCfgDefn;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.api.plugin.PluginType;

import java.util.List;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;


/**
 * Unit test to test the unique attribute plugin.
 */

public class UniqueAttributePluginTestCase extends PluginTestCase {

  private DN uidConfigDN;
  private DN testConfigDN;
  private String dsConfigAttrType="ds-cfg-type";
  private String dsConfigBaseDN="ds-cfg-base-dn";

  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.restartServer();
    TestCaseUtils.initializeTestBackend(true);
    
    //Add entries to two backends to test public naming context.
    addTestEntries("o=test", 't');
    TestCaseUtils.clearJEBackend(true,"userRoot", "dc=example,dc=com");
    addTestEntries("dc=example,dc=com", 'x');
    uidConfigDN=DN.decode("cn=UID Unique Attribute ,cn=Plugins,cn=config");
    testConfigDN=DN.decode("cn=Test Unique Attribute,cn=Plugins,cn=config");
  }


  /**
   * Clears configuration information before each method run.
   *
   * @throws Exception If an unexpected problem occurs.
   */
  @BeforeMethod
  public void clearConfigEntries() throws Exception {
    deleteAttrsFromEntry(uidConfigDN, dsConfigBaseDN);
    deleteAttrsFromEntry(testConfigDN, dsConfigBaseDN);
    //Put an attribute type there that won't impact the rest of the unit
    //tests.
    replaceAttrInEntry(uidConfigDN, dsConfigAttrType,"oncRpcNumber");
    replaceAttrInEntry(testConfigDN, dsConfigAttrType,"bootParameter");
  }


  /**
   * Clears things up after the unit test is completed.
   *
   * @throws Exception If an unexpected problem occurs.
   */
  @AfterClass
  public void tearDown() throws Exception {
    clearConfigEntries();
    TestCaseUtils.clearJEBackend(false,"userRoot", "dc=example,dc=com");
  }


  /**
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the plugin.
   *
   * @return An array of config entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
          throws Exception
  {
  List<Entry> entries = TestCaseUtils.makeEntries(
        "dn: cn=UID Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UID Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: uid",
        "",
        "dn: cn=mail Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: mail Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: mail",
        "ds-cfg-type: sn",
        "",
        "dn: cn=phone Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: phone Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: telephoneNumber",
        "ds-cfg-type: mobile",
        "ds-cfg-type: facsimileTelephoneNumber",
        "ds-cfg-base-dn: dc=example,dc=com",
        "ds-cfg-base-dn: o=test",
        "",
        "dn: cn=UID0 Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UUID0 Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-type: uid",
        "",
        "dn: cn=UID1 Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UUID1 Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-type: uid",
        "",
        "dn: cn=UID2 Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UUID2 Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: uid");
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
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList){
      for (AttributeValue v : a.getValues())
        pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
    }
    UniqueAttributePluginCfg configuration =
            AdminTestCaseUtils.getConfiguration(
                    UniqueAttributePluginCfgDefn.getInstance(), e);

    UniqueAttributePlugin plugin = new UniqueAttributePlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }

  /**
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the plugin.

   * @return An array of config entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInValidConfigs()
          throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
        "dn: cn=UID Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UID Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "",
        "dn: cn=UID Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: UID Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: ldifImport",
        "",
        "dn: cn=phone Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "cn: phone Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: telephone",
        "ds-cfg-type: mobile",
        "ds-cfg-type: fax",
        "ds-cfg-base-dn: dc=example,dc=com",
        "",
        "dn: cn=phone Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: phone Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: telephone",
        "ds-cfg-type: mobile",
        "ds-cfg-type: fax",
        "ds-cfg-base-dn: dc=example,dc=com",
        "ds-cfg-base-dn: badDN",
        "",
        "dn: cn=phone Unique Attribute,cn=Plugins,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-plugin",
        "objectClass: ds-cfg-unique-attribute-plugin",
        "cn: phone Unique Attribute",
        "ds-cfg-java-class: org.opends.server.plugins.UniqueAttributePlugin",
        "ds-cfg-enabled: true",
        "ds-cfg-plugin-type: preOperationAdd",
        "ds-cfg-plugin-type: preOperationModify",
        "ds-cfg-plugin-type: preOperationModifyDN",
        "ds-cfg-type: telephone",
        "ds-cfg-type: mobile",
        "ds-cfg-type: badattribute",
        "ds-cfg-base-dn: dc=example,dc=com" );

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }


  /**
   * Tests the process of initializing the server with invalid configurations.
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
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
        pluginTypes.add(PluginType.forName(v.getStringValue().toLowerCase()));
    }
    UniqueAttributePluginCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              UniqueAttributePluginCfgDefn.getInstance(), e);
    UniqueAttributePlugin plugin = new UniqueAttributePlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }

  /**
   * Test modify DN operation with various scenerios. See method comments.
   *
   * @throws Exception If an unexpected result occurs.
   */
  @Test()
  public void testModDNOperation() throws Exception {
    //Add an entry under the new superior DN that has a value for uid
    //that will be tested for.
     Entry e = makeEntry("cn=test user, ou=new people,o=test");
     addAttribute(e, "uid", "3user.3");
     addEntry(e, ResultCode.SUCCESS);
    //Setup uid attribute to be unique. Test using public naming contexts
    //for base DNs.
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"uid");
    //Rename with new rdn, should fail, there is an entry already with that
    //uid value.
    doModDN(DN.decode("uid=3user.3, ou=people, o=test"), RDN.decode("uid=4"),
                      false, null, ResultCode.CONSTRAINT_VIOLATION);
    //Rename with multi-valued RDN, should fail there is an entry already with
    //that uid value.
    doModDN(DN.decode("uid=3user.3, ou=people, o=test"),
                      RDN.decode("sn=xx+uid=4"),
                      false, null, ResultCode.CONSTRAINT_VIOLATION);
    //Now add a base dn to be unique under, so new superior move can be tested.
    replaceAttrInEntry(uidConfigDN,dsConfigBaseDN,"ou=new people,o=test");


    //Try to move the entry to a new superior.
    //Should fail, there is an entry under the new superior already with
    //that uid value.
    doModDN(DN.decode("uid=3user.3, ou=people, o=test"),
                      RDN.decode("uid=3user.3"), false,
                       DN.decode("ou=new people, o=test"),
                       ResultCode.CONSTRAINT_VIOLATION);
   //Test again with different superior, should succeed, new superior DN is
   //not in base DN scope.
   doModDN(DN.decode("uid=3user.3, ou=people, o=test"),
                      RDN.decode("uid=3user.3"), false,
                       DN.decode("ou=new people1, o=test"),
                       ResultCode.SUCCESS);
  }

  /**
   *  Test various modifcation scenerios using a configuration with no base
   * DNs defined. Use default of public naming contexts for base DNs.
   *
   * @throws Exception If an unexpected result occurs.
   */
  @Test()
  public void testModOperationNameContexts() throws Exception {
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mail");
    LinkedList<Modification> mods = new LinkedList<Modification>();
    addMods(mods,"mail",ModificationType.REPLACE,"userx@test","userxx@test",
           "user1t@test");
    //Fail because user1t@test already exists under "o=people,o=test".
    doMods(mods, DN.decode("uid=5user.5,ou=People,o=test"),
           ResultCode.CONSTRAINT_VIOLATION);
    mods.clear();
    addMods(mods,"pager",ModificationType.ADD,"2-999-1234","1-999-5678");
    addMods(mods,"mail",ModificationType.ADD,"userx@test","userxx@test",
           "user1t@test");
    //Fail because user1t@test already exists under "o=people,o=test".
    doMods(mods, DN.decode("uid=5user.5,ou=People,o=test"),
           ResultCode.CONSTRAINT_VIOLATION);
    mods.clear();
    addMods(mods,"pager",ModificationType.ADD,"2-999-1234","1-999-5678");
    addMods(mods,"mail",ModificationType.REPLACE,"userx@test","userxx@test",
           "user1t@test");
    //Ok because adding mail value user1t@test to entry that already
    //contains mail value user1t@test.
    doMods(mods, DN.decode("uid=1user.1,ou=People,o=test"),
           ResultCode.SUCCESS);
    mods.clear();
    //Replace employeenumber as the unique attribute.
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"employeenumber");
    addMods(mods,"employeenumber",ModificationType.INCREMENT,"1");
    //Test modify increment extension.
    //Fail because incremented value of employeenumber (2) already exists.
    doMods(mods, DN.decode("uid=1user.1,ou=People,o=test"),
           ResultCode.CONSTRAINT_VIOLATION);
  }


  /**
   * Test setting the plugins up to get DSEE behavior. Basically two or more
   * base DNs can have the same value, but not within the trees. This uses two
   * plugins to accomplish this.
   *
   * @throws Exception If an unexpected result occurs.
   */
  @Test()
  public void testDseeCompatAdd() throws Exception {
    //Set up one plugin with mail attribute and a suffix.
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mail");
    replaceAttrInEntry(uidConfigDN,dsConfigBaseDN,"ou=People,o=test");
    //Set up another plugin with the mail attribute and a different suffix.
    replaceAttrInEntry(testConfigDN,dsConfigAttrType,"mail");
    replaceAttrInEntry(testConfigDN,dsConfigBaseDN,"ou=People1,o=test");
    //Add two entries with same mail attribute value into the different
    //base DNs.
    Entry e1 = makeEntry("cn=test user1, ou=People,o=test");
    addAttribute(e1, "mail", "mailtest@test");
    addEntry(e1, ResultCode.SUCCESS);
    Entry e2 = makeEntry("cn=test user2, ou=People1,o=test");
    addAttribute(e2, "mail", "mailtest@test");
    addEntry(e2, ResultCode.SUCCESS);
    //Now try to add two more entries with the same mail attribute value.
    Entry e3 = makeEntry("cn=test user3, ou=People,o=test");
    addAttribute(e3, "mail", "mailtest@test");
    addEntry(e3, ResultCode.CONSTRAINT_VIOLATION);
    Entry e4 = makeEntry("cn=test user4, ou=People1,o=test");
    addAttribute(e4, "mail", "mailtest@test");
    addEntry(e4, ResultCode.CONSTRAINT_VIOLATION);
  }

  /**
   * Test various add operation scenerios using defined base DNs.
   * See comments in method.
   *
   * @throws Exception If an unexpected result occurs.
   */
  @Test()
  public void testAddOperation() throws Exception {
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mail");
    replaceAttrInEntry(uidConfigDN,dsConfigBaseDN,"ou=People1,o=test",
                       "ou=People, o=test");
    Entry e = makeEntry("cn=test user, ou=People,o=test");
    addAttribute(e, "mail", "user1t@test");
    //Fail because mail attribute already exists under "ou=people,o=test".
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    delAttribute(e, "mail");
    //Replace mobile, pager, telephonenumber to config.
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mobile",
                       "pager","telephonenumber");
    addAttribute(e, "mobile", "1-999-1234","1-999-5678","1-444-9012");
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    e.setDN(DN.decode("cn=test user, ou=People,o=test"));
    //Fail because "2-333-9012" already exists in "ou=people,o=test" in
    //telephonenumber attribute.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    delAttribute(e, "mobile");
    addAttribute(e, "pager", "2-111-1234","1-999-5678","1-999-9012");
    //Fail because "2-111-9012" already exists in "ou=people1,o=test" in
    //mobile attribute.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    //Test two plugin configuration. Add mail attribute to second plugin
    //instance, leave the first instance as it is.
    replaceAttrInEntry(testConfigDN,dsConfigAttrType,"mail");
    //Add suffix to second plugin.
    replaceAttrInEntry(testConfigDN,dsConfigBaseDN,"ou=People,o=test");
    delAttribute(e, "pager");
    //Add some values that will pass the first plugin.
    addAttribute(e, "telephonenumber", "2-999-1234","1-999-5678","1-999-9012");
    //Add a value that will fail the second plugin.
    addAttribute(e, "mail", "user1t@test");
    //Should pass frirail through second plugin configuration.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
  }


  /**
   * Test attempting to add entries using a configuration with no base
   * DNs defined. Use default of public naming contexts for base DNs.
   *
   * @throws Exception If an unexpected result occurs.
   */
  @Test()
  public void testAddOperationNameContext() throws Exception {
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mail");
    Entry e = makeEntry("cn=test user, ou=People,o=test");
    addAttribute(e, "mail", "user77x@test");
    //Fail because mail value "user77x@test" is a value under the
    //"dc=example,dc=com" naming context.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    delAttribute(e, "mail");
    replaceAttrInEntry(uidConfigDN,dsConfigAttrType,"mobile",
                  "pager","telephonenumber");
    addAttribute(e, "mobile", "1-999-1234","1-999-5678","2-777-9012");
    //Fail because "2-777-9012"  is a telephone value under the
    //"dc=example,dc=com" naming context.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    e.setDN(DN.decode("cn=test user, ou=People,o=test"));
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
    delAttribute(e, "mobile");
    addAttribute(e, "pager", "2-777-1234","1-999-5678","1-999-9012");
    //Fail because "2-777-9012"  is a telephone value under the
    //"dc=example,dc=com" naming context.
    addEntry(e, ResultCode.CONSTRAINT_VIOLATION);
  }


  /**
   * Create entries under the specified suffix and add them to the server.
   * The character argument is used to make the mail attribute unique.
   *
   * @param suffix  The suffix to use in building the entries.
   * @param c Character used to make the mail attribute unique.
   * @throws Exception If a problem occurs.
   */
  private void addTestEntries(String suffix, char c) throws Exception {
    TestCaseUtils.addEntries(
            "dn: ou=People," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: People",
            "aci: (targetattr= \"*\")" +
                  "(version 3.0; acl \"allow all\";" +
                  "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: ou=People1," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: People1",
            "aci: (targetattr= \"*\")" +
                  "(version 3.0; acl \"allow all\";" +
                  "allow(all) userdn=\"ldap:///anyone\";)",
             "",
            "dn: ou=New People1," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: New People",
            "",
            "",
            "dn: ou=New People," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: New People",
            "",
            "dn: uid=1user.1,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: 1User",
            "sn: 1",
            "cn: 1User 1",
            "userPassword: password",
            "mail: user1" + c +"@test",
            "employeeNumber: 1",
            "mobile: 1-111-1234",
            "pager: 1-111-5678",
            "telephoneNumber: 1-111-9012",
            "",
            "dn: uid=2user.2,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 2",
            "givenName: 2User",
            "sn: 2",
            "cn: User 2",
            "mail: user2" + c + "@test",
            "userPassword: password",
            "employeeNumber: 2",
            "mobile: 1-222-1234",
            "pager: 1-222-5678",
            "telephoneNumber: 1-222-9012",
            "",
            "dn: uid=3user.3,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 3",
            "givenName: 3User",
            "sn: 3",
            "cn: User 3",
            "mail: user3" + c + "@test",
            "userPassword: password",
            "employeeNumber: 3",
            "mobile: 1-333-1234",
            "pager: 1-333-5678",
            "telephoneNumber: 1-333-9012",
            "",
            "dn: uid=4user.4,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 4",
            "givenName: 4User",
            "sn: 4",
            "cn: User 4",
            "mail: user4" + c + "@test",
            "userPassword: password",
            "employeeNumber: 4",
            "mobile: 1-444-1234",
            "pager: 1-444-5678",
            "telephoneNumber: 1-444-9012",
            "",
            "dn: uid=5user.5,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 5",
            "givenName: 5User",
            "sn: 5",
            "cn: User 5",
            "mail: user5" + c + "@test",
            "userPassword: password",
            "employeeNumber: 5",
            "mobile: 1-555-1234",
            "pager: 1-555-5678",
            "telephoneNumber: 1-555-9012",
             "",
            "dn: uid=1user.1,ou=People1," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: 1User",
            "sn: 11",
            "cn: 1User 11",
            "userPassword: password",
            "mail: user11" + c + "@test",
            "employeeNumber: 111",
            "mobile: 2-111-1234",
            "pager: 2-111-5678",
            "telephoneNumber: 2-111-9012",
            "",
            "dn: uid=2user.22,ou=People1," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 2",
            "givenName: 2User",
            "sn: 22",
            "cn: User 22",
            "mail: user22" + c + "@test",
            "userPassword: password",
            "employeeNumber: 222",
            "mobile: 2-222-1234",
            "pager: 2-222-5678",
            "telephoneNumber: 2-222-9012",
            "",
            "dn: uid=3user.33,ou=People1," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 33",
            "givenName: 3User",
            "sn: 3",
            "cn: User 33",
            "mail: user33" + c + "@test",
            "userPassword: password",
            "employeeNumber: 333",
            "mobile: 2-333-1234",
            "pager: 2-333-5678",
            "telephoneNumber: 2-333-9012"
    );
    //Add an additional entry if the suffix is "dc=example,dc=com".
    if(suffix.equals("dc=example,dc=com")) {
      TestCaseUtils.addEntries(
            "dn: uid=2user.77,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 2",
            "givenName: 2User",
            "sn: 22",
            "cn: User 22",
            "mail: user77" + c + "@test",
            "userPassword: password",
            "employeeNumber: 777",
            "mobile: 2-777-1234",
            "pager: 2-777-5678",
            "telephoneNumber: 2-777-9012"
      );
    }
  }

  /**
   * Remove the attributes specified by the attribute type strings from the
   * entry corresponding to the dn argument.
   *
   * @param dn The entry to remove the attributes from.
   * @param attrTypeStrings The attribute type string list to remove from the
   *                        entry.
   * @throws Exception  If an error occurs.
   */
  private void
  deleteAttrsFromEntry(DN dn, String... attrTypeStrings) throws Exception {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    for(String attrTypeString : attrTypeStrings) {
     AttributeType attrType = getAttrType(attrTypeString);
     mods.add(new Modification(ModificationType.DELETE,
              new Attribute(attrType)));
    }
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    conn.processModify(dn, mods);
  }

  /**
   * Attempt to add an attribute of attribute type string to the entry
   * specified by the dn argument. The values to use in the attribute creation
   * is specified by the variable argument list.
   *
   * @param dn  The dn of the entry to add the attribute.
   * @param attrTypeString  The attribute type string.
   * @param attrValStrings  The values of the attribute.
   */
  private void
  addAttrToEntry(DN dn, String attrTypeString, String... attrValStrings) {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    LinkedHashSet<AttributeValue> attrValues =
                                            new LinkedHashSet<AttributeValue>();
    AttributeType attrType = getAttrType(attrTypeString);
    for(String valString : attrValStrings)
      attrValues.add(new AttributeValue(attrType, valString));
    Attribute attr = new Attribute(attrType, attrTypeString, attrValues);
    mods.add(new Modification(ModificationType.ADD, attr));
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    conn.processModify(dn, mods);
  }

  private void
  replaceAttrInEntry(DN dn, String attrTypeString, String... attrValStrings) {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    LinkedHashSet<AttributeValue> attrValues =
                                            new LinkedHashSet<AttributeValue>();
    AttributeType attrType = getAttrType(attrTypeString);
    for(String valString : attrValStrings)
      attrValues.add(new AttributeValue(attrType, valString));
    Attribute attr = new Attribute(attrType, attrTypeString, attrValues);
    mods.add(new Modification(ModificationType.REPLACE, attr));
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    conn.processModify(dn, mods);
  }


  /**
   * Try to add an entry to the server checking for the expected return
   * code.
   *
   * @param e  The entry to add.
   * @param rc The expected return code.
   * @throws Exception If an error occurs.
   */
  private void addEntry(Entry e, ResultCode rc) throws Exception {
        InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e);
    assertEquals(addOperation.getResultCode(), rc);
  }

  /**
   * Make a entry with the specified dn.
   *
   * @param dn The dn of the entry.
   * @return The created entry.
   * @throws Exception  If the entry can't be created.
   */
  private Entry makeEntry(String dn) throws Exception {
      return TestCaseUtils.makeEntry(
            "dn: " + dn,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: 1User",
            "sn: 1",
            "cn: 1User 1"
    );
  }

  /**
   * Remove an attribute from the specified entry.
   *
   * @param entry  The entry to remove the attribute from.
   * @param attrTypeString The attribute type string to remove.
   */
  private void delAttribute(Entry entry, String attrTypeString) {
    entry.removeAttribute(getAttrType(attrTypeString));
  }

  /**
   * Add an attribute to an entry with specified values.
   *
   * @param entry  The entry to add the attribute to.
   * @param attrTypeString The attribute type string name.
   * @param attrValues The values use in building the attribute.
   */
  private void
  addAttribute(Entry entry, String attrTypeString, String... attrValues) {
    LinkedHashSet<AttributeValue> values=new LinkedHashSet<AttributeValue>();
    AttributeType attrType=getAttrType(attrTypeString);
    for(String attrValue : attrValues) {
      AttributeValue value = new AttributeValue(attrType, attrValue);
      values.add(value);
    }
    entry.addAttribute(new Attribute(attrType, attrTypeString, values), null);
  }

  /**
   * Add a new modification for attribute type string and values of modification
   * type to a list of modifications.
   *
   * @param mods The modification list to add to.
   * @param attrTypeString The attribute type string name.
   * @param modificationType The modification type.
   * @param attrValues The values to build the modification from.
   */
  private void
  addMods(LinkedList<Modification> mods, String attrTypeString,
          ModificationType modificationType, String... attrValues) {
    LinkedHashSet<AttributeValue> values=new LinkedHashSet<AttributeValue>();
    AttributeType attrType=getAttrType(attrTypeString);
    for(String attrValue : attrValues) {
      AttributeValue value = new AttributeValue(attrType, attrValue);
      values.add(value);
    }
    mods.add(new Modification(modificationType,
             new Attribute(attrType, attrTypeString, values)));
  }

  /**
   * Return the attribute type corresponding to the attribute type string.
   *
   * @param attrTypeString  The attribute type string name.
   *
   * @return  An attribute type object pertaining to the string.
   */
  private AttributeType getAttrType(String attrTypeString) {
        AttributeType attrType =
            DirectoryServer.getAttributeType(attrTypeString);
    if (attrType == null)
      attrType = DirectoryServer.getDefaultAttributeType(attrTypeString);
    return attrType;
  }

  /**
   * Perform modify operation with list of modifications. Expect return code
   * of value rc.
   *
   * @param mods  The modification list to use.
   * @param dn The DN of the entry to modify.
   * @param rc The expected return code.
   */
  private void
  doMods(LinkedList<Modification> mods, DN dn, ResultCode rc ) {
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
            conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(),  rc);
  }

  /**
   *  Perform modify DN operation. Expect return value of rc.
   *
   * @param dn  The DN to renmame or move.
   * @param rdn RDN value.
   * @param delOld Delete old flag.
   * @param newSuperior New superior to move to.
   * @param rc Expected return code from operation.
   */
  private void
  doModDN(DN dn, RDN rdn, boolean delOld, DN newSuperior, ResultCode rc) {
        InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
            conn.processModifyDN(dn, rdn, delOld, newSuperior);
    assertEquals(modifyDNOperation.getResultCode(),  rc);
  }
}
