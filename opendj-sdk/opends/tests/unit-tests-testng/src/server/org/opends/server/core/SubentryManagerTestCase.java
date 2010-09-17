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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import org.opends.server.api.SubtreeSpecification;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.tools.LDAPDelete;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SubEntry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

public class SubentryManagerTestCase extends CoreTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE_RDN = "ou=Test SubEntry Manager";
  private static final String BASE = BASE_RDN + "," + SUFFIX;

  private Entry testEntry;
  private Entry ldapSubentry;
  private Entry collectiveSubentry;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearJEBackend(false, "userRoot", SUFFIX);
    addTestEntries();
  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.deleteEntry(collectiveSubentry.getDN());
    TestCaseUtils.deleteEntry(ldapSubentry.getDN());

    SubentryManager manager = DirectoryServer.getSubentryManager();
    assertNotNull(manager);
    List<SubEntry> subentryList = manager.getSubentries(testEntry.getDN());
    assertNotNull(subentryList);
    assertTrue(subentryList.isEmpty());
    subentryList = manager.getCollectiveSubentries(testEntry.getDN());
    assertNotNull(subentryList);
    assertTrue(subentryList.isEmpty());

    TestCaseUtils.clearJEBackend(false, "userRoot", SUFFIX);
  }

  @BeforeMethod
  public void setUpQuiesceServer()
  {
    TestCaseUtils.quiesceServer();
  }

  @Test
  public void testSubentryManager() throws Exception
  {
    SubentryManager manager = DirectoryServer.getSubentryManager();
    assertNotNull(manager);
    List<SubEntry> subentryList = manager.getSubentries(testEntry.getDN());
    assertNotNull(subentryList);
    assertEquals(subentryList.size(), 1);
    assertEquals(subentryList.get(0).getDN(), ldapSubentry.getDN());
    subentryList.clear();
    subentryList = manager.getSubentries(testEntry);
    assertNotNull(subentryList);
    assertEquals(subentryList.size(), 1);
    assertEquals(subentryList.get(0).getEntry(), ldapSubentry);
    subentryList.clear();
    subentryList = manager.getCollectiveSubentries(testEntry.getDN());
    assertNotNull(subentryList);
    assertEquals(subentryList.size(), 1);
    assertEquals(subentryList.get(0).getDN(), collectiveSubentry.getDN());
    subentryList.clear();
    subentryList = manager.getCollectiveSubentries(testEntry);
    assertNotNull(subentryList);
    assertEquals(subentryList.size(), 1);
    assertEquals(subentryList.get(0).getEntry(), collectiveSubentry);
  }

  @Test
  public void testCollectiveAttributes() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("real-overrides-virtual"));
    LDAPAttribute attr = new LDAPAttribute(
            "collectiveConflictBehavior", values);
    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf(
         collectiveSubentry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    Entry entry = DirectoryServer.getEntry(testEntry.getDN());
    AttributeType attrType = DirectoryServer.getAttributeType(
            "c-l");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "Savoie")));

    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "fr")));
  }

  @Test
  public void testInheritedCollectiveAttributes() throws Exception
  {
    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add test inherited from DN collective subentry.
    Entry collectiveDNInheritedSubentry = TestCaseUtils.makeEntry(
         "dn: cn=Inherited From DN Collective Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "objectClass: inheritedCollectiveAttributeSubentry",
         "objectClass: inheritedFromDNCollectiveAttributeSubentry",
         "inheritFromDNAttribute: manager",
         "inheritAttribute: postalAddress",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Inherited From DN Collective Subentry");
    AddOperation addOperation =
         connection.processAdd(collectiveDNInheritedSubentry.getDN(),
             collectiveDNInheritedSubentry.getObjectClasses(),
             collectiveDNInheritedSubentry.getUserAttributes(),
             collectiveDNInheritedSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(
            collectiveDNInheritedSubentry.getDN()));

    // Add test inherited from RDN collective subentry.
    Entry collectiveRDNInheritedSubentry = TestCaseUtils.makeEntry(
         "dn: cn=Inherited From RDN Collective Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "objectClass: inheritedCollectiveAttributeSubentry",
         "objectClass: inheritedFromRDNCollectiveAttributeSubentry",
         "inheritFromBaseRDN: " + BASE_RDN,
         "inheritFromRDNAttribute: title",
         "inheritFromRDNType: cn",
         "inheritAttribute: telephoneNumber",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Inherited From RDN Collective Subentry");
    addOperation =
         connection.processAdd(collectiveRDNInheritedSubentry.getDN(),
             collectiveRDNInheritedSubentry.getObjectClasses(),
             collectiveRDNInheritedSubentry.getUserAttributes(),
             collectiveRDNInheritedSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(
            collectiveRDNInheritedSubentry.getDN()));

    // Test Inherited Collective Attributes on test entry.
    Entry entry = DirectoryServer.getEntry(testEntry.getDN());
    AttributeType attrType = DirectoryServer.getAttributeType(
            "postaladdress");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType,
            "Sub City, Collective Street, AK 47")));

    attrType = DirectoryServer.getAttributeType(
            "telephonenumber");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType,
            "+1 999 999 9999")));

    // Cleanup.
    TestCaseUtils.deleteEntry(collectiveRDNInheritedSubentry.getDN());
    TestCaseUtils.deleteEntry(collectiveDNInheritedSubentry.getDN());
  }

  @Test
  public void testCollectiveAttributeConflict() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("ja"));
    LDAPAttribute attr = new LDAPAttribute(
            "preferredLanguage", values);
    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // real-overrides-virtual.
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("real-overrides-virtual"));
    attr = new LDAPAttribute(
            "collectiveConflictBehavior", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         collectiveSubentry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    Entry entry = DirectoryServer.getEntry(testEntry.getDN());
    AttributeType attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "ja")));
    assertFalse(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "fr")));

    // virtual-overrides-real.
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("virtual-overrides-real"));
    attr = new LDAPAttribute(
            "collectiveConflictBehavior", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         collectiveSubentry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    entry = DirectoryServer.getEntry(testEntry.getDN());
    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "fr")));
    assertFalse(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "ja")));

    // merge-real-and-virtual.
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("merge-real-and-virtual"));
    attr = new LDAPAttribute(
            "collectiveConflictBehavior", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         collectiveSubentry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    entry = DirectoryServer.getEntry(testEntry.getDN());
    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "ja")));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "fr")));

    // cleanup.
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("ja"));
    attr = new LDAPAttribute(
            "preferredLanguage", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  @Test
  public void testCollectiveAttributeSubentries() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("collectiveAttributeSubentries");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf(testEntry.getDN().toString()),
              SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(objectclass=*)"),
              attributes, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    AttributeType attrType = DirectoryServer.getAttributeType(
            "collectiveattributesubentries");
    assertTrue(searchOperation.getSearchEntries().getFirst().hasValue(
            attrType, null, AttributeValues.create(attrType,
            collectiveSubentry.getDN().toNormalizedString())));
  }

  @Test
  public void testCollectiveExclusions() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("real-overrides-virtual"));
    LDAPAttribute attr = new LDAPAttribute(
            "collectiveConflictBehavior", values);
    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf(
         collectiveSubentry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("c-l"));
    attr = new LDAPAttribute("collectiveExclusions", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    Entry entry = DirectoryServer.getEntry(testEntry.getDN());
    AttributeType attrType = DirectoryServer.getAttributeType(
            "c-l");
    assertFalse(entry.hasAttribute(attrType));
    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "fr")));

    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("preferredLanguage"));
    attr = new LDAPAttribute("collectiveExclusions", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    entry = DirectoryServer.getEntry(testEntry.getDN());
    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertFalse(entry.hasAttribute(attrType));
    attrType = DirectoryServer.getAttributeType(
            "c-l");
    assertTrue(entry.hasAttribute(attrType));
    assertTrue(entry.hasValue(attrType, null,
            AttributeValues.create(attrType, "Savoie")));

    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("excludeAllCollectiveAttributes"));
    attr = new LDAPAttribute("collectiveExclusions", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    entry = DirectoryServer.getEntry(testEntry.getDN());
    attrType = DirectoryServer.getAttributeType(
            "preferredlanguage");
    assertFalse(entry.hasAttribute(attrType));
    attrType = DirectoryServer.getAttributeType(
            "c-l");
    assertFalse(entry.hasAttribute(attrType));

    // cleanup.
    values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("excludeAllCollectiveAttributes"));
    attr = new LDAPAttribute("collectiveExclusions", values);
    mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));
    modifyOperation =
         conn.processModify(ByteString.valueOf(
         testEntry.getDN().toNormalizedString()), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  @Test
  public void testSubtreeDelete() throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_SUBTREE_DELETE_CONTROL + ":true",
      "--noPropertiesFile",
      SUFFIX
    };
    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);

    assertTrue(DirectoryServer.getSubentryManager().getCollectiveSubentries(
            DN.decode("uid=rogasawara," + BASE)).isEmpty());

    assertTrue(DirectoryServer.getSubentryManager().getSubentries(
            DN.decode("uid=rogasawara," + BASE)).isEmpty());

    // Re-add entries.
    addTestEntries();
  }

  @Test
  public void testSubtreeModify() throws Exception
  {
    String OLDBASE = "ou=Test SubEntry Manager";
    String NEWBASE = "ou=New SubEntry Manager Base";

    String newPath = TestCaseUtils.createTempFile(
         "dn: " + BASE,
         "changetype: moddn",
         "newRDN: " + NEWBASE,
         "deleteOldRDN: 1");

    String[] newArgs =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "-f", newPath
    };
    assertEquals(LDAPModify.mainModify(newArgs, false, null, System.err), 0);

    assertNotNull(DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + NEWBASE + "," + SUFFIX)));
    assertTrue(DirectoryServer.getSubentryManager().getCollectiveSubentries(
          DN.decode("uid=rogasawara," + NEWBASE + "," + SUFFIX)).isEmpty());
    assertTrue(DirectoryServer.getSubentryManager().getSubentries(
          DN.decode("uid=rogasawara," + NEWBASE + "," + SUFFIX)).isEmpty());

    // Move it back.
    String oldPath = TestCaseUtils.createTempFile(
         "dn: " + NEWBASE + "," + SUFFIX,
         "changetype: moddn",
         "newRDN: " + OLDBASE,
         "deleteOldRDN: 1");
    String[] oldArgs =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "-f", oldPath
    };
    assertEquals(LDAPModify.mainModify(oldArgs, false, null, System.err), 0);

    assertNotNull(DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + OLDBASE + "," + SUFFIX)));
    assertFalse(DirectoryServer.getSubentryManager().getCollectiveSubentries(
          DN.decode("uid=rogasawara," + OLDBASE + "," + SUFFIX)).isEmpty());
    assertFalse(DirectoryServer.getSubentryManager().getSubentries(
          DN.decode("uid=rogasawara," + OLDBASE + "," + SUFFIX)).isEmpty());
  }

  @Test
  public void testExtendedSubtreeSpecification() throws Exception
  {
    // This one should have been added during test setup so just
    // do a quick check here to make sure it is available there.
    assertNotNull(DirectoryServer.getEntry(ldapSubentry.getDN()));

    // RFC3672 Spec test subentry.
    List<SubEntry> rfc3672SubList =
            DirectoryServer.getSubentryManager().getSubentries();
    for (SubEntry subentry : rfc3672SubList)
    {
      if (subentry.getDN().equals(ldapSubentry.getDN()))
      {
        SubtreeSpecification spec = subentry.getSubTreeSpecification();
        assertTrue(spec instanceof RFC3672SubtreeSpecification);
      }
    }

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add Relative Spec test subentry.
    Entry relativeSubentry = TestCaseUtils.makeEntry(
         "dn: cn=Relative Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "subtreeSpecification: {relativeBase \"ou=Test SubEntry Manager\"}",
         "cn: Subentry");
    AddOperation addOperation =
         connection.processAdd(relativeSubentry.getDN(),
                               relativeSubentry.getObjectClasses(),
                               relativeSubentry.getUserAttributes(),
                               relativeSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(relativeSubentry.getDN()));

    List<SubEntry> relativeSubList =
            DirectoryServer.getSubentryManager().getSubentries();
    for (SubEntry subentry : relativeSubList)
    {
      if (subentry.getDN().equals(relativeSubentry.getDN()))
      {
        SubtreeSpecification spec = subentry.getSubTreeSpecification();
        assertTrue(spec instanceof RelativeSubtreeSpecification);
      }
    }

    // Remove Relative Spec test subentry.
    TestCaseUtils.deleteEntry(relativeSubentry.getDN());
  }

  private void addTestEntries() throws Exception
  {
    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add suffix entry.
    DN suffixDN = DN.decode(SUFFIX);
    if (DirectoryServer.getEntry(suffixDN) == null)
    {
      Entry suffixEntry = StaticUtils.createEntry(suffixDN);
      AddOperation addOperation =
           connection.processAdd(suffixEntry.getDN(),
                                 suffixEntry.getObjectClasses(),
                                 suffixEntry.getUserAttributes(),
                                 suffixEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(suffixEntry.getDN()));
    }

    // Add base entry.
    DN baseDN = DN.decode(BASE);
    if (DirectoryServer.getEntry(baseDN) == null)
    {
      Entry baseEntry = StaticUtils.createEntry(baseDN);
      AddOperation addOperation =
           connection.processAdd(baseEntry.getDN(),
                                 baseEntry.getObjectClasses(),
                                 baseEntry.getUserAttributes(),
                                 baseEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(baseEntry.getDN()));
    }

    // Add role entry.
    Entry roleEntry = TestCaseUtils.makeEntry(
         "dn: cn=Sales," + BASE,
         "objectclass: top",
         "objectclass: organizationalRole",
         "postalAddress: Sub City, Collective Street, AK 47",
         "telephoneNumber: +1 999 999 9999",
         "cn: Sales"
    );
    AddOperation addOperation =
         connection.processAdd(roleEntry.getDN(),
                               roleEntry.getObjectClasses(),
                               roleEntry.getUserAttributes(),
                               roleEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(roleEntry.getDN()));

    // Add test entry.
    testEntry = TestCaseUtils.makeEntry(
         "dn: uid=rogasawara," + BASE,
         "objectclass: top",
         "objectclass: person",
         "objectclass: organizationalPerson",
         "objectclass: inetOrgPerson",
         "uid: rogasawara",
         "userpassword: password",
         "mail: rogasawara@example.com",
         "givenname: Rodney",
         "sn: Ogasawara",
         "cn: Rodney Ogasawara",
         "manager: cn=Sales," + BASE,
         "title: Sales"
    );
    addOperation =
         connection.processAdd(testEntry.getDN(),
                               testEntry.getObjectClasses(),
                               testEntry.getUserAttributes(),
                               testEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(testEntry.getDN()));

    // Add test subentry.
    ldapSubentry = TestCaseUtils.makeEntry(
         "dn: cn=Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Subentry");
    addOperation =
         connection.processAdd(ldapSubentry.getDN(),
                               ldapSubentry.getObjectClasses(),
                               ldapSubentry.getUserAttributes(),
                               ldapSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(ldapSubentry.getDN()));

    // Add test collective subentry.
    collectiveSubentry = TestCaseUtils.makeEntry(
         "dn: cn=Collective Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "objectClass: collectiveAttributeSubentry",
         "objectClass: extensibleObject",
         "c-l: Savoie",
         "preferredLanguage;collective: fr",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Collective Subentry");
    addOperation =
         connection.processAdd(collectiveSubentry.getDN(),
                               collectiveSubentry.getObjectClasses(),
                               collectiveSubentry.getUserAttributes(),
                               collectiveSubentry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(collectiveSubentry.getDN()));
  }
}
