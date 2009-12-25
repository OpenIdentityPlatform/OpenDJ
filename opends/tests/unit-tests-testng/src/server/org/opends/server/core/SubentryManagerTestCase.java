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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
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

import static org.testng.Assert.*;

public class SubentryManagerTestCase extends CoreTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE =
          "ou=Test SubEntry Manager," + SUFFIX;

  private Entry testEntry;
  private Entry ldapSubentry;
  private Entry collectiveSubentry;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearJEBackend(false, "userRoot", SUFFIX);

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
         "title: Sales, Director"
    );
    AddOperation addOperation =
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
}
