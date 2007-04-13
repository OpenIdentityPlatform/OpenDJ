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
package org.opends.server.extensions;



import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.GroupManager;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;

import static org.testng.Assert.*;



/**
 * A set of test cases for the virtual static group implementation and the
 * member virtual attribute provider.
 */
public class VirtualStaticGroupTestCase
       extends ExtensionsTestCase
{
  /**
   * The lines comprising the LDIF test data.
   */
  private static final String[] LDIF_LINES =
  {
    "dn: ou=People,o=test",
    "objectClass: top",
    "objectClass: organizationalUnit",
    "ou: People",
    "",
    "dn: uid=test.1,ou=People,o=test",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "uid: test.1",
    "givenName: Test",
    "sn: 1",
    "cn: Test 1",
    "userPassword: password",
    "",
    "dn: uid=test.2,ou=People,o=test",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "uid: test.2",
    "givenName: Test",
    "sn: 2",
    "cn: Test 2",
    "userPassword: password",
    "",
    "dn: uid=test.3,ou=People,o=test",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "uid: test.3",
    "givenName: Test",
    "sn: 3",
    "cn: Test 3",
    "userPassword: password",
    "",
    "dn: uid=test.4,ou=People,o=test",
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "uid: test.4",
    "givenName: Test",
    "sn: 4",
    "cn: Test 4",
    "userPassword: password",
    "",
    "dn: ou=Groups,o=test",
    "objectClass: top",
    "objectClass: organizationalUnit",
    "ou: Groups",
    "",
    "dn: cn=Dynamic All Users,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfURLs",
    "cn: Dynamic All Users",
    "memberURL: ldap:///ou=People,o=test??sub?(objectClass=person)",
    "",
    "dn: cn=Dynamic One User,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfURLs",
    "cn: Dynamic One User",
    "memberURL: ldap:///ou=People,o=test??sub?(&(objectClass=person)(sn=4))",
    "",
    "dn: cn=Static member List,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "cn: Static member List",
    "member: uid=test.1,ou=People,o=test",
    "member: uid=test.3,ou=People,o=test",
    "",
    "dn: cn=Static uniqueMember List,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfUniqueNames",
    "cn: Static uniqueMember List",
    "uniqueMember: uid=test.2,ou=People,o=test",
    "uniqueMember: uid=test.3,ou=People,o=test",
    "uniqueMember: uid=no-such-user,ou=People,o=test",
    "",
    "dn: cn=Virtual member All Users,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual member All Users",
    "ds-target-group-dn: cn=Dynamic All Users,ou=Groups,o=test",
    "",
    "dn: cn=Virtual uniqueMember All Users,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfUniqueNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual uniqueMember All Users",
    "ds-target-group-dn: cn=Dynamic All Users,ou=Groups,o=test",
    "",
    "dn: cn=Virtual member One User,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual member One User",
    "ds-target-group-dn: cn=Dynamic One User,ou=Groups,o=test",
    "",
    "dn: cn=Virtual uniqueMember One User,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfUniqueNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual uniqueMember One User",
    "ds-target-group-dn: cn=Dynamic One User,ou=Groups,o=test",
    "",
    "dn: cn=Virtual Static member List,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual Static member List",
    "ds-target-group-dn: cn=Static member List,ou=Groups,o=test",
    "",
    "dn: cn=Virtual Static uniqueMember List,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfUniqueNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual Static uniqueMember List",
    "ds-target-group-dn: cn=Static uniqueMember List,ou=Groups,o=test",
    "",
    "dn: cn=Crossover member Static Group,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfUniqueNames",
    "objectClass: ds-virtual-static-group",
    "cn: Crossover member Static Group",
    "ds-target-group-dn: cn=Static member List,ou=Groups,o=test",
    "",
    "dn: cn=Crossover uniqueMember Static Group,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "objectClass: ds-virtual-static-group",
    "cn: Crossover uniqueMember Static Group",
    "ds-target-group-dn: cn=Static uniqueMember List,ou=Groups,o=test",
    "",
    "dn: cn=Virtual Nonexistent,ou=Groups,o=test",
    "objectClass: top",
    "objectClass: groupOfNames",
    "objectClass: ds-virtual-static-group",
    "cn: Virtual Nonexistent",
    "ds-target-group-dn: cn=Nonexistent,ou=Groups,o=test"
  };



  // The attribute type for the member attribute.
  private AttributeType memberType;

  // The attribute type for the uniqueMember attribute.
  private AttributeType uniqueMemberType;

  // The server group manager.
  private GroupManager groupManager;

  // The DNs of the various entries in the data set.
  private DN u1;
  private DN u2;
  private DN u3;
  private DN u4;
  private DN da;
  private DN d1;
  private DN sm;
  private DN su;
  private DN vmda;
  private DN vuda;
  private DN vmd1;
  private DN vud1;
  private DN vsm;
  private DN vsu;
  private DN vcm;
  private DN vcu;
  private DN vn;
  private DN ne;



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

    memberType = DirectoryServer.getAttributeType("member", false);
    assertNotNull(memberType);

    uniqueMemberType = DirectoryServer.getAttributeType("uniquemember", false);
    assertNotNull(uniqueMemberType);

    groupManager = DirectoryServer.getGroupManager();

    u1 = DN.decode("uid=test.1,ou=People,o=test");
    u2 = DN.decode("uid=test.2,ou=People,o=test");
    u3 = DN.decode("uid=test.3,ou=People,o=test");
    u4 = DN.decode("uid=test.4,ou=People,o=test");
    da = DN.decode("cn=Dynamic All Users,ou=Groups,o=test");
    d1 = DN.decode("cn=Dynamic One User,ou=Groups,o=test");
    sm = DN.decode("cn=Static member List,ou=Groups,o=test");
    su = DN.decode("cn=Static uniqueMember List,ou=Groups,o=test");
    vmda = DN.decode("cn=Virtual member All Users,ou=Groups,o=test");
    vuda = DN.decode("cn=Virtual uniqueMember All Users,ou=Groups,o=test");
    vmd1 = DN.decode("cn=Virtual member One User,ou=Groups,o=test");
    vud1 = DN.decode("cn=Virtual uniqueMember One User,ou=Groups,o=test");
    vsm = DN.decode("cn=Virtual Static member List,ou=Groups,o=test");
    vsu = DN.decode("cn=Virtual Static uniqueMember List,ou=Groups,o=test");
    vcm = DN.decode("cn=Crossover member Static Group,ou=Groups,o=test");
    vcu = DN.decode("cn=Crossover uniqueMember Static Group,ou=Groups,o=test");
    vn = DN.decode("cn=Virtual Nonexistent,ou=Groups,o=test");
    ne = DN.decode("cn=Nonexistent,ou=Groups,o=test");
  }



  /**
   * Tests creating a new instance of a virtual static group from a valid entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCreateValidGroup()
         throws Exception
  {
    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=Valid Virtual Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "objectClass: ds-virtual-static-group",
      "cn: Valid Virtual Static Group",
      "ds-target-group-dn: cn=Static member List,ou=Groups,o=test");

    VirtualStaticGroup groupImplementation = new VirtualStaticGroup();
    VirtualStaticGroup groupInstance = groupImplementation.newInstance(entry);
    assertNotNull(groupInstance);
    groupImplementation.finalizeGroupImplementation();
  }



  /**
   * Retrieves a set of invalid vittual static group definition entries.
   *
   * @return  A set of invalid virtul static group definition entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidGroups")
  public Object[][] getInvalidGroupDefinitions()
         throws Exception
  {
    List<Entry> groupEntries = TestCaseUtils.makeEntries(
      "dn: cn=Not a Virtual Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Not a Virtual Static Group",
      "member: uid=test.1,ou=People,o=test",
      "",
      "dn: cn=No Target,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "objectClass: ds-virtual-static-group",
      "cn: No Target",
      "",
      "dn: cn=Invalid Target,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "objectClass: ds-virtual-static-group",
      "cn: Invalid Target",
      "ds-target-group-dn: invalid");

    Object[][] entryArray = new Object[groupEntries.size()][1];
    for (int i=0; i < entryArray.length; i++)
    {
      entryArray[i][0] = groupEntries.get(i);
    }

    return entryArray;
  }



  /**
   * Tests creating a new instance of a virtual static group from an invalid
   * entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidGroups",
        expectedExceptions = { DirectoryException.class })
  public void testCreateInvalidGroup(Entry entry)
         throws Exception
  {
    VirtualStaticGroup groupImplementation = new VirtualStaticGroup();
    try
    {
      VirtualStaticGroup groupInstance = groupImplementation.newInstance(entry);
    }
    finally
    {
      groupImplementation.finalizeGroupImplementation();
    }
  }



  /**
   * Performs general tests of the group API for virtual static groups with a
   * group that has a real target group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGroupAPI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualStaticGroup g =
         (VirtualStaticGroup) groupManager.getGroupInstance(vmda);
    assertNotNull(g);
    assertTrue(g.isMember(u1));

    assertNotNull(g.getGroupDefinitionFilter());
    assertEquals(g.getGroupDN(), vmda);
    assertEquals(g.getTargetGroupDN(), da);
    assertFalse(g.supportsNestedGroups());
    assertTrue(g.getNestedGroupDNs().isEmpty());
    assertFalse(g.mayAlterMemberList());

    Entry entry = DirectoryServer.getEntry(u1);
    assertTrue(g.isMember(entry));

    MemberList memberList = g.getMembers();
    assertTrue(memberList.hasMoreMembers());
    assertNotNull(memberList.nextMemberDN());
    assertNotNull(memberList.nextMemberEntry());
    assertNotNull(memberList.nextMemberDN());
    assertNotNull(memberList.nextMemberDN());
    assertFalse(memberList.hasMoreMembers());

    SearchFilter filter = SearchFilter.createFilterFromString("(sn=1)");
    memberList = g.getMembers(DN.decode("o=test"), SearchScope.WHOLE_SUBTREE,
                              filter);
    assertTrue(memberList.hasMoreMembers());
    assertNotNull(memberList.nextMemberDN());
    assertFalse(memberList.hasMoreMembers());

    try
    {
      g.addNestedGroup(d1);
      fail("Expected an exception from addNestedGroupDN");
    } catch (Exception e) {}

    try
    {
      g.removeNestedGroup(d1);
      fail("Expected an exception from removeNestedGroupDN");
    } catch (Exception e) {}

    try
    {
      g.addMember(entry);
      fail("Expected an exception from addMember");
    } catch (Exception e) {}

    try
    {
      g.removeMember(u1);
      fail("Expected an exception from removeMember");
    } catch (Exception e) {}

    assertNotNull(g.toString());

    cleanUp();
  }



  /**
   * Performs general tests of the group API for virtual static groups with a
   * group that has a nonexistent target group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGroupAPINonexistent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualStaticGroup g =
         (VirtualStaticGroup) groupManager.getGroupInstance(vn);
    assertNotNull(g);

    assertNotNull(g.getGroupDefinitionFilter());
    assertEquals(g.getGroupDN(), vn);
    assertEquals(g.getTargetGroupDN(), ne);
    assertFalse(g.supportsNestedGroups());
    assertTrue(g.getNestedGroupDNs().isEmpty());
    assertFalse(g.mayAlterMemberList());

    Entry entry = DirectoryServer.getEntry(u1);

    try
    {
      g.isMember(u1);
      fail("Expected an exception from isMember(DN)");
    } catch (Exception e) {}

    try
    {
      g.isMember(entry);
      fail("Expected an exception from isMember(Entry)");
    } catch (Exception e) {}

    try
    {
      g.getMembers();
      fail("Expected an exception from getMembers()");
    } catch (Exception e) {}

    try
    {
      SearchFilter filter = SearchFilter.createFilterFromString("(sn=1)");
      g.getMembers(DN.decode("o=test"), SearchScope.WHOLE_SUBTREE, filter);
      fail("Expected an exception from getMembers(base, scope, filter)");
    } catch (Exception e) {}

    try
    {
      g.addNestedGroup(d1);
      fail("Expected an exception from addNestedGroupDN");
    } catch (Exception e) {}

    try
    {
      g.removeNestedGroup(d1);
      fail("Expected an exception from removeNestedGroupDN");
    } catch (Exception e) {}

    try
    {
      g.addMember(entry);
      fail("Expected an exception from addMember");
    } catch (Exception e) {}

    try
    {
      g.removeMember(u1);
      fail("Expected an exception from removeMember");
    } catch (Exception e) {}

    assertNotNull(g.toString());

    cleanUp();
  }



  /**
   * Tests the behavior of the virtual static group with a dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualGroupDynamicGroupWithMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualStaticGroup g =
         (VirtualStaticGroup) groupManager.getGroupInstance(vmda);
    assertNotNull(g);
    assertTrue(g.isMember(u1));
    assertTrue(g.isMember(u2));
    assertTrue(g.isMember(u3));
    assertTrue(g.isMember(u4));

    cleanUp();
  }



  /**
   * Tests the behavior of the virtual static group with a static group based on
   * the member attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualGroupStaticGroupWithMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualStaticGroup g =
         (VirtualStaticGroup) groupManager.getGroupInstance(vsm);
    assertNotNull(g);
    assertTrue(g.isMember(u1));
    assertFalse(g.isMember(u2));
    assertTrue(g.isMember(u3));
    assertFalse(g.isMember(u4));

    cleanUp();
  }



  /**
   * Tests the behavior of the virtual static group with a static group based on
   * the uniqueMember attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualGroupStaticGroupWithUniqueMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualStaticGroup g =
         (VirtualStaticGroup) groupManager.getGroupInstance(vsu);
    assertNotNull(g);
    assertFalse(g.isMember(u1));
    assertTrue(g.isMember(u2));
    assertTrue(g.isMember(u3));
    assertFalse(g.isMember(u4));

    cleanUp();
  }



  /**
   * Performs general tests of the virtual attribute provider API for the member
   * virtual attribute with a target group that exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualAttributeAPI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualAttributeRule rule = null;
    for (VirtualAttributeRule r : DirectoryServer.getVirtualAttributes())
    {
      if (r.getAttributeType().equals(memberType))
      {
        rule = r;
        break;
      }
    }
    assertNotNull(rule);

    MemberVirtualAttributeProvider provider =
         (MemberVirtualAttributeProvider) rule.getProvider();
    assertNotNull(provider);

    Entry entry = DirectoryServer.getEntry(vsm);
    assertNotNull(entry);

    assertTrue(provider.isMultiValued());

    LinkedHashSet<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertFalse(values.isEmpty());
    assertTrue(provider.hasValue(entry, rule));
    assertTrue(provider.hasValue(entry, rule,
                    new AttributeValue(memberType, u1.toString())));
    assertFalse(provider.hasValue(entry, rule,
                    new AttributeValue(memberType, ne.toString())));
    assertTrue(provider.hasAnyValue(entry, rule, values));
    assertFalse(provider.hasAnyValue(entry, rule,
                                     Collections.<AttributeValue>emptySet()));
    assertEquals(provider.matchesSubstring(entry, rule, null, null, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.greaterThanOrEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.lessThanOrEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.approximatelyEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), null, DN.decode("o=test"),
                  SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString(
                       "(member=" + u1.toString() + ")"),
                  null, null);
    assertFalse(provider.isSearchable(rule, searchOperation));

    provider.processSearch(rule, searchOperation);
    assertFalse(searchOperation.getResultCode() == ResultCode.SUCCESS);

    cleanUp();
  }



  /**
   * Performs general tests of the virtual attribute provider API for the member
   * virtual attribute with a target group that does not exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualAttributeAPINonexistent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    VirtualAttributeRule rule = null;
    for (VirtualAttributeRule r : DirectoryServer.getVirtualAttributes())
    {
      if (r.getAttributeType().equals(memberType))
      {
        rule = r;
        break;
      }
    }
    assertNotNull(rule);

    MemberVirtualAttributeProvider provider =
         (MemberVirtualAttributeProvider) rule.getProvider();
    assertNotNull(provider);

    Entry entry = DirectoryServer.getEntry(vn);
    assertNotNull(entry);

    assertTrue(provider.isMultiValued());

    LinkedHashSet<AttributeValue> values = provider.getValues(entry, rule);
    assertNotNull(values);
    assertTrue(values.isEmpty());
    assertFalse(provider.hasValue(entry, rule));
    assertFalse(provider.hasValue(entry, rule,
                    new AttributeValue(memberType, u1.toString())));
    assertFalse(provider.hasValue(entry, rule,
                    new AttributeValue(memberType, ne.toString())));
    assertFalse(provider.hasAnyValue(entry, rule, values));
    assertFalse(provider.hasAnyValue(entry, rule,
                                     Collections.<AttributeValue>emptySet()));
    assertEquals(provider.matchesSubstring(entry, rule, null, null, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.greaterThanOrEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.lessThanOrEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);
    assertEquals(provider.approximatelyEqualTo(entry, rule, null),
                 ConditionResult.UNDEFINED);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), null, DN.decode("o=test"),
                  SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString(
                       "(member=" + u1.toString() + ")"),
                  null, null);
    assertFalse(provider.isSearchable(rule, searchOperation));

    provider.processSearch(rule, searchOperation);
    assertFalse(searchOperation.getResultCode() == ResultCode.SUCCESS);

    cleanUp();
  }



  /**
   * Tests the behavior of the member virtual attribute with a dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualAttrDynamicGroupWithMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    Entry e = DirectoryServer.getEntry(vmda);
    assertNotNull(e);
    assertTrue(e.hasAttribute(memberType));

    Attribute a = e.getAttribute(memberType).get(0);
    assertEquals(a.getValues().size(), 4);

    AttributeValue v = new AttributeValue(memberType, u1.toString());
    assertTrue(a.hasValue(v));

    cleanUp();
  }



  /**
   * Tests the behavior of the member virtual attribute with a dynamic group.
   * The target dynamic group will initially have only one memberURL which
   * matches only one user, but will then be updated on the fly to contain a
   * second URL that matches all users.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testVirtualAttrDynamicGroupWithUpdatedMemberURLs()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    Entry e = DirectoryServer.getEntry(vmd1);
    assertNotNull(e);
    assertTrue(e.hasAttribute(memberType));

    Attribute a = e.getAttribute(memberType).get(0);
    assertEquals(a.getValues().size(), 1);

    AttributeValue v = new AttributeValue(memberType, u4.toString());
    assertTrue(a.hasValue(v));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedList<Modification> mods = new LinkedList<Modification>();
    mods.add(new Modification(ModificationType.ADD,
         new Attribute("memberurl",
                       "ldap:///o=test??sub?(objectClass=person)")));
    ModifyOperation modifyOperation = conn.processModify(d1, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    a = e.getAttribute(memberType).get(0);
    assertEquals(a.getValues().size(), 4);
    assertTrue(a.hasValue(v));

    cleanUp();
  }



  /**
   * Tests the behavior of the member virtual attribute with different settings
   * for the "allow retrieving membership" attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowRetrievingMembership()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(LDIF_LINES);

    Entry e = DirectoryServer.getEntry(vmd1);
    assertNotNull(e);
    assertTrue(e.hasAttribute(memberType));

    Attribute a = e.getAttribute(memberType).get(0);
    assertEquals(a.getValues().size(), 1);

    AttributeValue v = new AttributeValue(memberType, u4.toString());
    assertTrue(a.hasValue(v));


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedList<Modification> mods = new LinkedList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
         new Attribute("ds-cfg-allow-retrieving-membership", "false")));
    DN definitionDN =
         DN.decode("cn=Virtual Static member,cn=Virtual Attributes,cn=config");
    ModifyOperation modifyOperation = conn.processModify(definitionDN, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    e = DirectoryServer.getEntry(vmd1);
    assertNotNull(e);
    assertTrue(e.hasAttribute(memberType));

    a = e.getAttribute(memberType).get(0);
    assertEquals(a.getValues().size(), 0);

    v = new AttributeValue(memberType, u4.toString());
    assertTrue(a.hasValue(v));


    mods = new LinkedList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
         new Attribute("ds-cfg-allow-retrieving-membership", "true")));
    modifyOperation = conn.processModify(definitionDN, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    cleanUp();
  }



  /**
   * Removes all of the groups that have been added to the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void cleanUp()
          throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("ou=Groups,dc=example,dc=com"),
              SearchScope.SINGLE_LEVEL,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    for (Entry e : searchOperation.getSearchEntries())
    {
      conn.processDelete(e.getDN());
    }
  }
}

