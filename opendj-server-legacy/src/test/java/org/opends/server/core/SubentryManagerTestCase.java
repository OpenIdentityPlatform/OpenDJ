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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.tools.LDAPDelete;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SubEntry;
import org.opends.server.types.SubtreeSpecification;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class SubentryManagerTestCase extends CoreTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE_RDN = "ou=Test SubEntry Manager";
  private static final String BASE = BASE_RDN + "," + SUFFIX;

  private Entry testEntry;
  private Entry ldapSubentry;
  private Entry legacyLdapSubentry;
  private Entry collectiveSubentry;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearBackend("userRoot");
    addTestEntries();
  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.deleteEntry(collectiveSubentry.getName());
    TestCaseUtils.deleteEntry(ldapSubentry.getName());
    TestCaseUtils.deleteEntry(legacyLdapSubentry.getName());

    SubentryManager manager = DirectoryServer.getSubentryManager();
    assertNotNull(manager);
    List<SubEntry> subentryList = manager.getSubentries(testEntry.getName());
    assertThat(subentryList).isEmpty();
    subentryList = manager.getCollectiveSubentries(testEntry.getName());
    assertThat(subentryList).isEmpty();

    TestCaseUtils.clearBackend("userRoot");
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

    List<SubEntry> subentryList = manager.getSubentries(testEntry.getName());
    assertThat(getDns(subentryList)).containsExactly(ldapSubentry.getName(), legacyLdapSubentry.getName());

    subentryList = manager.getSubentries(testEntry);
    assertThat(getEntries(subentryList)).containsExactly(ldapSubentry, legacyLdapSubentry);

    subentryList = manager.getCollectiveSubentries(testEntry.getName());
    assertThat(getDns(subentryList)).containsExactly(collectiveSubentry.getName());

    // Other unit tests may have modified the sub-entry causing it to contain
    // modify timestamps, etc, so get a fresh copy.
    Entry refreshedCollectiveSubentry = DirectoryServer.getEntry(collectiveSubentry.getName());
    subentryList = manager.getCollectiveSubentries(testEntry);
    assertThat(getEntries(subentryList)).containsExactly(refreshedCollectiveSubentry);

    Entry refreshedLegacyLdapSubentry = DirectoryServer.getEntry(legacyLdapSubentry.getName());
    subentryList = manager.getSubentries(legacyLdapSubentry.getName().parent());
    assertThat(getEntries(subentryList)).containsExactly(refreshedLegacyLdapSubentry);

    subentryList = manager.getSubentries(legacyLdapSubentry.getName().parent().parent());
    assertThat(subentryList).isEmpty();
  }

  private List<Entry> getEntries(List<SubEntry> subentries)
  {
    final List<Entry> results = new ArrayList<>();
    for (SubEntry subEntry : subentries)
    {
      results.add(subEntry.getEntry());
    }
    return results;
  }

  private List<DN> getDns(List<SubEntry> subentries)
  {
    final List<DN> results = new ArrayList<>();
    for (SubEntry subEntry : subentries)
    {
      results.add(subEntry.getDN());
    }
    return results;
  }

  @Test
  public void testCollectiveAttributes() throws Exception
  {
    try
    {
      replaceAttribute(collectiveSubentry, "collectiveConflictBehavior", "real-overrides-virtual");
      hasValues(testEntry.getName(), "c-l", "Savoie");
      hasValues(testEntry.getName(), "preferredlanguage", "fr");
    }
    finally
    {
      deleteAttribute(collectiveSubentry, "collectiveConflictBehavior");
    }
  }



  /**
   * Tests that collective attributes can be applied to entries which match
   * subtree specification using virtual attributes.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testCollectiveAttributesWorkWithVirtualAttributes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
        "dn: ou=people,o=test",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: people",
        "",
        "dn: cn=description collective attribute,ou=people,o=test",
        "objectClass: top",
        "objectClass: subentry",
        "objectClass: collectiveAttributeSubentry",
        "objectClass: extensibleObject",
        "subtreeSpecification: {base \"\", specificationFilter \"(isMemberOf=cn=collective users,ou=people,o=test)\"}",
        "cn: description collective attribute",
        "description;collective: inherited description",
        "",
        "dn: cn=collective users,ou=people,o=test",
        "objectClass: top",
        "objectClass: groupOfNames",
        "cn: ca users",
        "member: uid=collective user,ou=people,o=test",
        "",
        "dn: uid=collective user,ou=people,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: collective user",
        "givenName: collective",
        "sn: user",
        "cn: collective user",
        "userPassword: password",
        "",
        "dn: uid=normal user,ou=people,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: normal user",
        "givenName: normal",
        "sn: user",
        "cn: normal user",
        "userPassword: password"
    );

    try
    {
      // Normal user will not inherit the collective description attribute.
      Entry e = DirectoryServer.getEntry(DN.valueOf("uid=normal user,ou=people,o=test"));
      assertNotNull(e);

      List<Attribute> description = e.getAttribute("description");
      assertThat(description).isEmpty();

      // Collective user will inherit the collective description attribute.
      e = DirectoryServer.getEntry(DN.valueOf("uid=collective user,ou=people,o=test"));
      assertNotNull(e);

      description = e.getAttribute("description");
      assertThat(description).hasSize(1);
      Attribute attribute = description.get(0);
      assertEquals(attribute.size(), 1);
      assertFalse(attribute.getAttributeDescription().hasOptions());
      assertTrue(attribute.contains(ByteString.valueOfUtf8("inherited description")));
    }
    finally
    {
      // Clear sub-entry and groups from test backend.
      TestCaseUtils.deleteEntry(DN.valueOf("cn=description collective attribute,ou=people,o=test"));
      TestCaseUtils.deleteEntry(DN.valueOf("cn=collective users,ou=people,o=test"));
    }
  }

  @Test
  public void testInheritedCollectiveAttributes() throws Exception
  {
    // Add test inherited from DN collective subentry.
    Entry collectiveDNInheritedSubentry = addEntry(
         "dn: cn=Inherited From DN Collective Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "objectClass: inheritedCollectiveAttributeSubentry",
         "objectClass: inheritedFromDNCollectiveAttributeSubentry",
         "inheritFromDNAttribute: manager",
         "inheritAttribute: postalAddress",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Inherited From DN Collective Subentry");

    // Add test inherited from RDN collective subentry.
    Entry collectiveRDNInheritedSubentry = addEntry(
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

    // Test Inherited Collective Attributes on test entry.
    hasValues(testEntry.getName(), "postaladdress", "Sub City, Collective Street, AK 47");
    hasValues(testEntry.getName(), "telephonenumber", "+1 999 999 9999");

    // Cleanup.
    TestCaseUtils.deleteEntry(collectiveRDNInheritedSubentry.getName());
    TestCaseUtils.deleteEntry(collectiveDNInheritedSubentry.getName());
  }

  @Test
  public void testCollectiveAttributeConflict() throws Exception
  {
    try
    {
      DN dn = testEntry.getName();
      replaceAttribute(testEntry, "preferredLanguage", "ja");

      // real-overrides-virtual.
      replaceAttribute(collectiveSubentry, "collectiveConflictBehavior", "real-overrides-virtual");
      hasValues(dn, "preferredlanguage", "ja");
      doesNotHaveValues(dn, "preferredlanguage", "fr");

      // virtual-overrides-real.
      replaceAttribute(collectiveSubentry, "collectiveConflictBehavior", "virtual-overrides-real");
      hasValues(dn, "preferredlanguage", "fr");
      doesNotHaveValues(dn, "preferredlanguage", "ja");

      // merge-real-and-virtual.
      replaceAttribute(collectiveSubentry, "collectiveConflictBehavior", "merge-real-and-virtual");
      hasValues(dn, "preferredlanguage", "ja", "fr");
    }
    finally
    {
      // cleanup.
      deleteAttribute(testEntry, "preferredLanguage");
      deleteAttribute(collectiveSubentry, "collectiveConflictBehavior");
    }
  }

  private void hasValues(DN dn, String attrName, String... values) throws DirectoryException
  {
    Entry entry = DirectoryServer.getEntry(dn);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attrName);
    assertTrue(entry.hasAttribute(attrType));
    for (String value : values)
    {
      assertTrue(entry.hasValue(attrType, ByteString.valueOfUtf8(value)));
    }
  }

  private void doesNotHaveValues(DN dn, String attrName, String... values) throws DirectoryException
  {
    Entry entry = DirectoryServer.getEntry(dn);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attrName);
    assertTrue(entry.hasAttribute(attrType));
    for (String value : values)
    {
      assertFalse(entry.hasValue(attrType, ByteString.valueOfUtf8(value)));
    }
  }

  private void hasNoAttribute(DN dn, String attrName) throws Exception
  {
    Entry entry = DirectoryServer.getEntry(dn);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attrName);
    assertFalse(entry.hasAttribute(attrType));
  }

  @Test
  public void testCollectiveAttributeSubentries() throws Exception
  {
    SearchRequest request = newSearchRequest(testEntry.getName(), SearchScope.BASE_OBJECT)
        .addAttribute("collectiveAttributeSubentries");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType("collectiveattributesubentries");
    Entry e = searchOperation.getSearchEntries().getFirst();
    assertTrue(e.hasValue(attrType, ByteString.valueOfObject(collectiveSubentry.getName())));
  }

  @Test
  public void testCollectiveExclusions() throws Exception
  {
    try
    {
      DN dn = testEntry.getName();

      replaceAttribute(collectiveSubentry, "collectiveConflictBehavior", "real-overrides-virtual");
      replaceAttribute(testEntry, "collectiveExclusions", "c-l");
      hasNoAttribute(dn, "c-l");
      hasValues(dn, "preferredlanguage", "fr");

      replaceAttribute(testEntry, "collectiveExclusions", "preferredLanguage");
      hasNoAttribute(dn, "preferredlanguage");
      hasValues(dn, "c-l", "Savoie");

      replaceAttribute(testEntry, "collectiveExclusions", "excludeAllCollectiveAttributes");
      hasNoAttribute(dn, "preferredlanguage");
      hasNoAttribute(dn, "c-l");
    }
    finally
    {
      // cleanup.
      deleteAttribute(testEntry, "collectiveExclusions");
      deleteAttribute(collectiveSubentry, "collectiveConflictBehavior");
    }
  }

  private void deleteAttribute(Entry e, String attrType)
  {
    List<Modification> mods = newModifications(DELETE, attrType);
    ModifyOperation modifyOperation = getRootConnection().processModify(e.getName(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private void replaceAttribute(Entry e, String attrType, String newValue)
  {
    List<Modification> mods = newModifications(REPLACE, attrType, newValue);
    ModifyOperation modifyOperation = getRootConnection().processModify(e.getName(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private List<Modification> newModifications(ModificationType modType, String attrType, String... values)
  {
    return newArrayList(new Modification(modType, Attributes.create(attrType, values)));
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
            DN.valueOf("uid=rogasawara," + BASE)).isEmpty());
    assertTrue(DirectoryServer.getSubentryManager().getSubentries(
            DN.valueOf("uid=rogasawara," + BASE)).isEmpty());

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

    assertNotNull(DirectoryServer.getEntry(DN.valueOf(
            "uid=rogasawara," + NEWBASE + "," + SUFFIX)));
    assertTrue(DirectoryServer.getSubentryManager().getCollectiveSubentries(
          DN.valueOf("uid=rogasawara," + NEWBASE + "," + SUFFIX)).isEmpty());

    // The legacyLdapSubentry should still apply.
    assertEquals(DirectoryServer.getSubentryManager().getSubentries(
          DN.valueOf("uid=rogasawara," + NEWBASE + "," + SUFFIX)).size(), 1);

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

    assertNotNull(DirectoryServer.getEntry(DN.valueOf(
            "uid=rogasawara," + OLDBASE + "," + SUFFIX)));
    assertFalse(DirectoryServer.getSubentryManager().getCollectiveSubentries(
          DN.valueOf("uid=rogasawara," + OLDBASE + "," + SUFFIX)).isEmpty());
    assertFalse(DirectoryServer.getSubentryManager().getSubentries(
          DN.valueOf("uid=rogasawara," + OLDBASE + "," + SUFFIX)).isEmpty());
  }

  @Test
  public void testExtendedSubtreeSpecification() throws Exception
  {
    // This one should have been added during test setup so just
    // do a quick check here to make sure it is available there.
    assertNotNull(DirectoryServer.getEntry(ldapSubentry.getName()));

    // RFC3672 Spec test subentry.
    List<SubEntry> rfc3672SubList =
            DirectoryServer.getSubentryManager().getSubentries();
    for (SubEntry subentry : rfc3672SubList)
    {
      if (subentry.getDN().equals(ldapSubentry.getName()))
      {
        SubtreeSpecification spec = subentry.getSubTreeSpecification();
        assertNull(spec.getRefinements());
      }
    }

    // Add Relative Spec test subentry.
    Entry relativeSubentry = addEntry(
         "dn: cn=Relative Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\", specificationFilter \"(objectClass=*)\"}",
         "cn: Subentry");

    List<SubEntry> relativeSubList =
            DirectoryServer.getSubentryManager().getSubentries();
    for (SubEntry subentry : relativeSubList)
    {
      if (subentry.getDN().equals(relativeSubentry.getName()))
      {
        SubtreeSpecification spec = subentry.getSubTreeSpecification();
        assertTrue(spec.getRefinements() instanceof SubtreeSpecification.FilterRefinement);
      }
    }

    // Remove Relative Spec test subentry.
    TestCaseUtils.deleteEntry(relativeSubentry.getName());
  }

  private void addTestEntries() throws Exception
  {
    // Add suffix entry.
    DN suffixDN = DN.valueOf(SUFFIX);
    if (DirectoryServer.getEntry(suffixDN) == null)
    {
      addEntry(StaticUtils.createEntry(suffixDN));
    }

    // Add base entry.
    DN baseDN = DN.valueOf(BASE);
    if (DirectoryServer.getEntry(baseDN) == null)
    {
      addEntry(StaticUtils.createEntry(baseDN));
    }

    // Add role entry.
    addEntry(
         "dn: cn=Sales," + BASE,
         "objectclass: top",
         "objectclass: organizationalRole",
         "postalAddress: Sub City, Collective Street, AK 47",
         "telephoneNumber: +1 999 999 9999",
         "cn: Sales"
    );

    // Add test entry.
    testEntry = addEntry(
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

    // Add test subentry.
    ldapSubentry = addEntry(
         "dn: cn=Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Subentry");

    // Add test legacy subentry.
    legacyLdapSubentry = addEntry(
         "dn: cn=Legacy Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: ldapSubentry",
         "cn: Legacy Subentry");

    // Add test collective subentry.
    collectiveSubentry = addEntry(
         "dn: cn=Collective Subentry," + SUFFIX,
         "objectClass: top",
         "objectclass: subentry",
         "objectClass: collectiveAttributeSubentry",
         "objectClass: extensibleObject",
         "c-l: Savoie",
         "preferredLanguage;collective: fr",
         "subtreeSpecification: {base \"ou=Test SubEntry Manager\"}",
         "cn: Collective Subentry");
  }
}
