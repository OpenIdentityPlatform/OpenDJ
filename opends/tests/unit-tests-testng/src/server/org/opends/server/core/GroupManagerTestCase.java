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
package org.opends.server.core;



import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.api.Group;
import org.opends.server.extensions.DynamicGroup;
import org.opends.server.extensions.StaticGroup;
import org.opends.server.extensions.VirtualStaticGroup;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;
import static org.testng.Assert.assertTrue;


/**
 * A set of test cases that involve the use of groups and the Directory Server
 * Group Manager.
 */
public class GroupManagerTestCase
       extends CoreTestCase
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

  @AfterClass()
  public void cleanUp() {
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
  }

  /**
   * Tests the {@code GroupManager.getGroupImplementations} method to ensure
   * that it contains the appropriate set of values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetGroupImplementations()
         throws Exception
  {
    GroupManager groupManager = DirectoryServer.getGroupManager();

    LinkedHashSet<Class> groupClasses = new LinkedHashSet<Class>();
    groupClasses.add(StaticGroup.class);
    groupClasses.add(DynamicGroup.class);
    groupClasses.add(VirtualStaticGroup.class);

    for (Group g : groupManager.getGroupImplementations())
    {
      assertTrue(groupClasses.remove(g.getClass()),
                 "Group class " + g.getClass() + " isn't registered");
    }

    assertTrue(groupClasses.isEmpty(),
               "Unexpected group class(es) registered:  " + groupClasses);
  }

  /**
   * Test static group nesting with some of the groups pointing to each
   * other in a circular fashion. Once this situation is detected the
   * membership check should return false.
   *
   * @throws Exception If an unexpected problem occurs.
   */

  @Test()
  public void testStaticGroupCircularNested() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
    addNestedGroupTestEntries();
    DN group1DN = DN.decode("cn=group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=group 3,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");
    DN user4DN = DN.decode("uid=user.4,ou=People,o=test");
    DN user5DN = DN.decode("uid=user.5,ou=People,o=test");
    Entry user1Entry = DirectoryServer.getEntry(user1DN);
    Entry user2Entry = DirectoryServer.getEntry(user2DN);
    Entry user3Entry = DirectoryServer.getEntry(user3DN);
    Entry user4Entry = DirectoryServer.getEntry(user4DN);
    Group group1Instance = groupManager.getGroupInstance(group1DN);
    Group group2Instance = groupManager.getGroupInstance(group2DN);
    Group group3Instance = groupManager.getGroupInstance(group3DN);
    assertNotNull(group1Instance);
    assertNotNull(group2Instance);
    assertNotNull(group3Instance);
    group1Instance.addNestedGroup(group2DN);
    group2Instance.addNestedGroup(group3DN);
    //Add circular nested group definition by adding group 1 to group 3
    //nested list. Group 1 contains group 2, which contains group 3, which
    //contains group 1.
    group3Instance.addNestedGroup(group1DN);
    group1Instance.addMember(user1Entry);
    group2Instance.addMember(user2Entry);
    group3Instance.addMember(user3Entry);
    group2Instance.addMember(user4Entry);
    //Search for DN not in any of the groups/
    assertFalse(group1Instance.isMember(user5DN));
  }

  /**
   * Test static group nesting wit one of the nested groups being a
   * dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupDynamicNested() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
    addNestedGroupTestEntries();
    DN group1DN = DN.decode("cn=group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=group 3,ou=Groups,o=test");
    DN group4DN = DN.decode("cn=group 4,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");
    DN user4DN = DN.decode("uid=user.4,ou=People,o=test");
    DN user5DN = DN.decode("uid=user.5,ou=People,o=test");
    Entry user1Entry = DirectoryServer.getEntry(user1DN);
    Entry user2Entry = DirectoryServer.getEntry(user2DN);
    Entry user3Entry = DirectoryServer.getEntry(user3DN);
    Entry user4Entry = DirectoryServer.getEntry(user4DN);
    //User 5 is not added to any group, it matches the URL of the dynamic
    //group "group 4".
    Group group1Instance = groupManager.getGroupInstance(group1DN);
    Group group2Instance = groupManager.getGroupInstance(group2DN);
    Group group3Instance = groupManager.getGroupInstance(group3DN);
    //Group 4 is a dynamic group.
    Group group4Instance = groupManager.getGroupInstance(group4DN);
    assertNotNull(group1Instance);
    assertNotNull(group2Instance);
    assertNotNull(group3Instance);
    assertNotNull(group4Instance);
    group1Instance.addNestedGroup(group2DN);
    group2Instance.addNestedGroup(group3DN);
    //Dynamic group 4 is added to nested list of group 3.
    group3Instance.addNestedGroup(group4DN);
    group1Instance.addMember(user1Entry);
    group2Instance.addMember(user2Entry);
    group3Instance.addMember(user3Entry);
    group2Instance.addMember(user4Entry);
    //Check membership of user 5 through group 1. User 5 is a member of the
    //dynamic group "group 4" which is nested.
    assertTrue(group1Instance.isMember(user5DN));
  }

  /**
   * Invokes membership and nested group APIs using a group instance that has
   * been changed by the group manager via ldap modify.
   *
   * @throws Exception If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupInstanceChange() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
    addNestedGroupTestEntries();
    DN group1DN = DN.decode("cn=group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=group 3,ou=Groups,o=test");
    DN group4DN = DN.decode("cn=group 4,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");
    DN user4DN = DN.decode("uid=user.4,ou=People,o=test");
    DN user5DN = DN.decode("uid=user.5,ou=People,o=test");
    Entry user1Entry = DirectoryServer.getEntry(user1DN);
    Entry user2Entry = DirectoryServer.getEntry(user2DN);
    Entry user3Entry = DirectoryServer.getEntry(user3DN);
    Entry user4Entry = DirectoryServer.getEntry(user4DN);
    Entry user5Entry = DirectoryServer.getEntry(user5DN);
    Group<? extends GroupImplementationCfg> group1Instance =
            (Group<? extends GroupImplementationCfg>)
                    groupManager.getGroupInstance(group1DN);
    assertNotNull(group1Instance);
    //Add even numbered groups.
    group1Instance.addNestedGroup(group2DN);
    group1Instance.addNestedGroup(group4DN);
    //Add even numbered members.
    group1Instance.addMember(user2Entry);
    group1Instance.addMember(user4Entry);
    //Switch things around, change groups and members to odd numbered nested
    //groups and odd numbered members via ldap modify.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    Attribute g1 = new Attribute("member", "cn=group 1,ou=Groups,o=test");
    Attribute g2 = new Attribute("member", "cn=group 2,ou=Groups,o=test");
    Attribute g3 = new Attribute("member", "cn=group 3,ou=Groups,o=test");
    Attribute g4 = new Attribute("member", "cn=group 4,ou=Groups,o=test");
    Attribute u1 = new Attribute("member", "uid=user.1,ou=People,o=test");
    Attribute u2 = new Attribute("member", "uid=user.2,ou=People,o=test");
    Attribute u3 = new Attribute("member", "uid=user.3,ou=People,o=test");
    Attribute u4 = new Attribute("member", "uid=user.4,ou=People,o=test");
    Attribute u5 = new Attribute("member", "uid=user.5,ou=People,o=test");
    //Delete even groups and users.
    mods.add(new Modification(ModificationType.DELETE, g2));
    mods.add(new Modification(ModificationType.DELETE, g4));
    mods.add(new Modification(ModificationType.DELETE, u2));
    mods.add(new Modification(ModificationType.DELETE, u4));
    //Add odd groups and users.
    mods.add(new Modification(ModificationType.ADD, g1));
    mods.add(new Modification(ModificationType.ADD, g3));
    mods.add(new Modification(ModificationType.ADD, u1));
    mods.add(new Modification(ModificationType.ADD, u3));
    mods.add(new Modification(ModificationType.ADD, u5));
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
            conn.processModify(group1Instance.getGroupDN(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    //Check that the user membership changes were picked up.
    assertFalse(group1Instance.isMember(user2Entry));
    assertFalse(group1Instance.isMember(user4Entry));
    assertTrue(group1Instance.isMember(user1Entry));
    assertTrue(group1Instance.isMember(user3Entry));
    assertTrue(group1Instance.isMember(user5Entry));
    assertFalse(group1Instance.isMember(group2DN));
    assertFalse(group1Instance.isMember(group4DN));
    assertTrue(group1Instance.isMember(group1DN));
    assertTrue(group1Instance.isMember(group3DN));
    //Check get members picked up everything.
    MemberList memberList = group1Instance.getMembers();
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberDN.equals(group1DN) || memberDN.equals(group3DN) ||
                                 memberDN.equals(user1DN) ||
                                 memberDN.equals(user3DN) ||
                                 memberDN.equals(user5DN));
    }
    //Check that the nested group changes were picked up.
    List<DN> nestedGroups=group1Instance.getNestedGroupDNs();
    assertFalse(nestedGroups.isEmpty());
    assertTrue(nestedGroups.contains(group1DN));
    assertFalse(nestedGroups.contains(group2DN));
    assertTrue(nestedGroups.contains(group3DN));
    assertFalse(nestedGroups.contains(group4DN));
  }

  /**
   * Invokes membership and nested group APIs using a group instance that has
   * been removed from the group manager via ldap delete.
   *
   * @throws Exception If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupInstanceInvalid() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
    addNestedGroupTestEntries();
    DN group1DN = DN.decode("cn=group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=group 3,ou=Groups,o=test");
    DN group4DN = DN.decode("cn=group 4,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    Entry user1Entry = DirectoryServer.getEntry(user1DN);
    Group<? extends GroupImplementationCfg> group1Instance =
            (Group<? extends GroupImplementationCfg>)
                    groupManager.getGroupInstance(group1DN);
    Group<? extends GroupImplementationCfg> group2Instance =
            (Group<? extends GroupImplementationCfg>)
                    groupManager.getGroupInstance(group2DN);
    assertNotNull(group1Instance);
    //Add some nested groups and members.
    group1Instance.addNestedGroup(group2DN);
    group1Instance.addMember(user1Entry);
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    //Delete the group.
    DeleteOperation deleteOperation = conn.processDelete(group1DN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(group1DN));
    //Membership check should throw an exception.
    try
    {
      group1Instance.isMember(user1DN);
      throw new AssertionError("Expected isMember to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
    //Nested groups should be empty.
    List<DN> nestedGroups=group1Instance.getNestedGroupDNs();
    assertTrue(nestedGroups.isEmpty());
    try
    {
      MemberList memberList=group1Instance.getMembers();
      throw new AssertionError("Expected getMembers to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
  }

  /**
   * Invokes nested group API methods on various nested group
   * scenerios.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupNestedAPI() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();
    addNestedGroupTestEntries();
    DN group1DN = DN.decode("cn=group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=group 3,ou=Groups,o=test");
    DN group4DN = DN.decode("cn=group 4,ou=Groups,o=test");
    DN bogusGroup = DN.decode("cn=bogus group,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");
    DN user4DN = DN.decode("uid=user.4,ou=People,o=test");
    DN user5DN = DN.decode("uid=user.5,ou=People,o=test");
    Entry user1Entry = DirectoryServer.getEntry(user1DN);
    Entry user2Entry = DirectoryServer.getEntry(user2DN);
    Entry user3Entry = DirectoryServer.getEntry(user3DN);
    Entry user4Entry = DirectoryServer.getEntry(user4DN);
    Entry user5Entry = DirectoryServer.getEntry(user5DN);
    //These casts are needed so there isn't a unchecked assignment
    //compile warning in the getNestedGroupDNs calls below.  Some IDEs
    //will give a unchecked cast warning.
    Group<? extends GroupImplementationCfg> group1Instance =
            (Group<? extends GroupImplementationCfg>)
                    groupManager.getGroupInstance(group1DN);
    Group<? extends GroupImplementationCfg> group2Instance =
            (Group<? extends GroupImplementationCfg>)
                    groupManager.getGroupInstance(group2DN);
    Group group3Instance = groupManager.getGroupInstance(group3DN);
    assertNotNull(group1Instance);
    assertNotNull(group2Instance);
    assertNotNull(group3Instance);
    //Add nested groups.
    group1Instance.addNestedGroup(group2DN);
    group2Instance.addNestedGroup(group3DN);
    //Add some members.
    group1Instance.addMember(user1Entry);
    group2Instance.addMember(user2Entry);
    group3Instance.addMember(user3Entry);
    group2Instance.addMember(user4Entry);
    group3Instance.addMember(user5Entry);
    //Check if group 3 shows up in the group 2 membership list.
    MemberList memberList = group2Instance.getMembers();
    boolean found=false;
    while (memberList.hasMoreMembers())
    {
      if( memberList.nextMemberDN().equals(group3DN)) {
        found=true;
        break;
      }
    }
    assertTrue(found);
    //Check membership via group 1 using nesting of group 2 and group 3.
    //User 5 is in group 3.
    assertTrue(group1Instance.isMember(user5DN));
    group2Instance.removeNestedGroup(group3DN);
    //Check group 3 is removed from group 2 membership list.
    memberList = group2Instance.getMembers();
    found=false;
    while (memberList.hasMoreMembers())
    {
      if(memberList.nextMemberDN().equals(user3DN)){
        found=true;
        break;
      }
    }
    assertFalse(found);
    //Check membership via group 1 should fail now, since nested group 3
    //was removed from group 2.
    assertFalse(group1Instance.isMember(user5DN));
    group2Instance.addNestedGroup(group3DN);
    //Check remove member call also removes DN from nested group list.
    group2Instance.removeMember(group3DN);
    assertFalse(group2Instance.getNestedGroupDNs().contains(group3DN));
    group1Instance.removeNestedGroup(group2DN);
    List<DN> nestedGroups=group1Instance.getNestedGroupDNs();
    assertTrue(nestedGroups.isEmpty());
    //Add nested groups to group 1
    group1Instance.addNestedGroup(group2DN);
    group1Instance.addNestedGroup(group3DN);
    group1Instance.addNestedGroup(group4DN);
    //Check get nested groups DNs list returns correct DN list.
    List<DN> nestedGroups1=group1Instance.getNestedGroupDNs();
    assertFalse(nestedGroups1.isEmpty());
    assertTrue(nestedGroups1.contains(group2DN));
    assertTrue(nestedGroups1.contains(group3DN));
    assertTrue(nestedGroups1.contains(group4DN));
    //Check removing a group not in the nested group list fails.
    try
    {
      group1Instance.removeNestedGroup(bogusGroup);
      throw new AssertionError("Expected removeNestedGroup to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
    //Check adding a nested group already in the nested group list fails.
    try
    {
      group1Instance.addNestedGroup(group2DN);
      throw new AssertionError("Expected addNestedGroup to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
    //Modify list via ldap modify.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    Attribute a2 = new Attribute("member", "cn=group 2,ou=Groups,o=test");
    Attribute a3 = new Attribute("member", "cn=group 1,ou=Groups,o=test");
    mods.add(new Modification(ModificationType.DELETE, a2));
    mods.add(new Modification(ModificationType.ADD, a3));
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
                        conn.processModify(group1Instance.getGroupDN(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    //Check removing a group already removed via ldap modify fails.
    try
    {
      group1Instance.removeNestedGroup(group2DN);
      throw new AssertionError("Expected removeNestedGroup to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
    //Check adding a group added via ldap modify fails.
    try
    {
      group1Instance.addNestedGroup(group1DN);
      throw new AssertionError("Expected addNestedGroup to fail but " +
              "it didn't");
    } catch (DirectoryException ex) {}
  }



  /**
   * Invokes general group API methods on a static group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGenericStaticGroupAPI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group of Unique Names",
      "member: uid=user.1,ou=People,o=test",
      "member: uid=user.2,ou=People,o=test");

    DN groupDN = DN.decode("cn=Test Group of Names,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertTrue(groupInstance.isMember(user2DN));
    assertFalse(groupInstance.isMember(user3DN));

    assertTrue(groupInstance.supportsNestedGroups());
    assertTrue(groupInstance.getNestedGroupDNs().isEmpty());

    try
    {
      groupInstance.addNestedGroup(DN.decode("uid=test,ou=People,o=test"));
    } catch (DirectoryException ex) {
           throw new AssertionError("Expected addNestedGroup to succeed but" +
                                    " it didn't");
    }

    try
    {
      groupInstance.removeNestedGroup(
           DN.decode("uid=test,ou=People,o=test"));
    } catch (DirectoryException ex) {
            throw new AssertionError("Expected removeNestedGroup to succeed " +
                    "but it didn't");
    }


    assertTrue(groupInstance.mayAlterMemberList());

    Entry user3Entry = DirectoryServer.getEntry(user3DN);
    groupInstance.addMember(user3Entry);
    assertTrue(groupInstance.isMember(user3DN));

    groupInstance.removeMember(user2DN);
    assertFalse(groupInstance.isMember(user2DN));

    groupInstance.toString(new StringBuilder());


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests that the server properly handles adding, deleting, and modifying a
   * static group based on the groupOfNames object class where that group
   * contains valid members.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidPopulatedGroupOfNames()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password");


    // Make sure that there aren't any groups registered with the server.
    assertFalse(groupManager.getGroupInstances().iterator().hasNext());


    // Add a new static group to the server and make sure it gets registered
    // with the group manager.
    TestCaseUtils.addEntry(
      "dn: cn=Test Group of Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group of Names",
      "member: uid=user.1,ou=People,o=test",
      "member: uid=user.2,ou=People,o=test");


    // Perform a basic set of validation on the group itself.
    DN groupDN = DN.decode("cn=Test Group of Names,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertTrue(groupInstance.isMember(user2DN));
    assertFalse(groupInstance.isMember(user3DN));

    MemberList memberList = groupInstance.getMembers();
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberDN.equals(user1DN) || memberDN.equals(user2DN));
    }

    SearchFilter filter = SearchFilter.createFilterFromString("(uid=user.1)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    assertTrue(memberList.hasMoreMembers());
    DN memberDN = memberList.nextMemberDN();
    assertTrue(memberDN.equals(user1DN));
    assertFalse(memberList.hasMoreMembers());

    filter = SearchFilter.createFilterFromString("(uid=user.3)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    assertFalse(memberList.hasMoreMembers());


    // Modify the group and make sure the group manager gets updated
    // accordingly.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    Attribute a2 = new Attribute("member", "uid=user.2,ou=People,o=test");
    Attribute a3 = new Attribute("member", "uid=user.3,ou=People,o=test");
    mods.add(new Modification(ModificationType.DELETE, a2));
    mods.add(new Modification(ModificationType.ADD, a3));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation = conn.processModify(groupDN, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertFalse(groupInstance.isMember(user2DN));
    assertTrue(groupInstance.isMember(user3DN));


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests that the server properly handles a groupOfNames object that doesn't
   * contain any members.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidEmptyGroupOfNames()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntry(
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups");


    // Make sure that there aren't any groups registered with the server.
    assertFalse(groupManager.getGroupInstances().iterator().hasNext());


    // Add a new static group to the server and make sure it gets registered
    // with the group manager.
    TestCaseUtils.addEntry(
      "dn: cn=Test Group of Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group of Names");


    // Make sure that the group exists but doesn't have any members.
    DN groupDN = DN.decode("cn=Test Group of Names,ou=Groups,o=test");
    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertFalse(groupInstance.getMembers().hasMoreMembers());


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests that the server properly handles adding, deleting, and modifying a
   * static group based on the groupOfUniqueNames object class where that group
   * contains valid members.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidPopulatedGroupOfUniqueNames()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password");


    // Make sure that there aren't any groups registered with the server.
    assertFalse(groupManager.getGroupInstances().iterator().hasNext());


    // Add a new static group to the server and make sure it gets registered
    // with the group manager.
    TestCaseUtils.addEntry(
      "dn: cn=Test Group of Unique Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Group of Unique Names",
      "uniqueMember: uid=user.1,ou=People,o=test",
      "uniqueMember: uid=user.2,ou=People,o=test");


    // Perform a basic set of validation on the group itself.
    DN groupDN = DN.decode("cn=Test Group of Unique Names,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertTrue(groupInstance.isMember(user2DN));
    assertFalse(groupInstance.isMember(user3DN));

    MemberList memberList = groupInstance.getMembers();
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberDN.equals(user1DN) || memberDN.equals(user2DN));
    }

    SearchFilter filter = SearchFilter.createFilterFromString("(uid=user.1)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    assertTrue(memberList.hasMoreMembers());
    DN memberDN = memberList.nextMemberDN();
    assertTrue(memberDN.equals(user1DN));
    assertFalse(memberList.hasMoreMembers());

    filter = SearchFilter.createFilterFromString("(uid=user.3)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    assertFalse(memberList.hasMoreMembers());


    // Modify the group and make sure the group manager gets updated
    // accordingly.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    Attribute a2 = new Attribute("uniquemember", "uid=user.2,ou=People,o=test");
    Attribute a3 = new Attribute("uniquemember", "uid=user.3,ou=People,o=test");
    mods.add(new Modification(ModificationType.DELETE, a2));
    mods.add(new Modification(ModificationType.ADD, a3));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation = conn.processModify(groupDN, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertFalse(groupInstance.isMember(user2DN));
    assertTrue(groupInstance.isMember(user3DN));


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests that the server properly handles a groupOfUniqueNames object that
   * doesn't contain any members.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidEmptyGroupOfUniqueNames()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntry(
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups");


    // Make sure that there aren't any groups registered with the server.
    assertFalse(groupManager.getGroupInstances().iterator().hasNext());


    // Add a new static group to the server and make sure it gets registered
    // with the group manager.
    TestCaseUtils.addEntry(
      "dn: cn=Test Group of Unique Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Group of Names");


    // Make sure that the group exists but doesn't have any members.
    DN groupDN = DN.decode("cn=Test Group of Unique Names,ou=Groups,o=test");
    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertFalse(groupInstance.getMembers().hasMoreMembers());


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Verifies that the group manager properly handles modify DN operations on
   * static group entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRenameStaticGroup()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password");


    // Make sure that there aren't any groups registered with the server.
    assertFalse(groupManager.getGroupInstances().iterator().hasNext());


    // Add a new static group to the server and make sure it gets registered
    // with the group manager.
    TestCaseUtils.addEntry(
      "dn: cn=Test Group of Unique Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Group of Unique Names",
      "uniqueMember: uid=user.1,ou=People,o=test");


    // Perform a basic set of validation on the group itself.
    DN groupDN = DN.decode("cn=Test Group of Unique Names,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));


    // Rename the group and make sure the old one no longer exists but the new
    // one does.
    RDN newRDN = RDN.decode("cn=Renamed Group");
    DN  newDN  = DN.decode("cn=Renamed Group,ou=Groups,o=test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(groupDN, newRDN, true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    groupInstance = groupManager.getGroupInstance(groupDN);
    assertNull(groupInstance);

    groupInstance = groupManager.getGroupInstance(newDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), newDN);
    assertTrue(groupInstance.isMember(user1DN));


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    DeleteOperation deleteOperation = conn.processDelete(newDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(newDN));
  }



  /**
   * Tests the methods related to static group membership in the
   * {@code ClientConnection} class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticClientConnectionMembership()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Group 1,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Group 1",
      "member: uid=user.1,ou=People,o=test",
      "member: uid=user.2,ou=People,o=test",
      "",
      "dn: cn=Group 2,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Group 3",
      "uniqueMember: uid=user.2,ou=People,o=test",
      "uniqueMember: uid=user.3,ou=People,o=test",
      "",
      "dn: cn=Group 3,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Group 3",
      "uniqueMember: uid=user.1,ou=People,o=test",
      "uniqueMember: uid=user.3,ou=People,o=test");


    // Perform basic validation on the groups.
    DN group1DN = DN.decode("cn=Group 1,ou=Groups,o=test");
    DN group2DN = DN.decode("cn=Group 2,ou=Groups,o=test");
    DN group3DN = DN.decode("cn=Group 3,ou=Groups,o=test");
    DN user1DN  = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN  = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN  = DN.decode("uid=user.3,ou=People,o=test");

    Group group1 = groupManager.getGroupInstance(group1DN);
    Group group2 = groupManager.getGroupInstance(group2DN);
    Group group3 = groupManager.getGroupInstance(group3DN);

    assertNotNull(group1);
    assertTrue(group1.isMember(user1DN));
    assertTrue(group1.isMember(user2DN));
    assertFalse(group1.isMember(user3DN));

    assertNotNull(group2);
    assertFalse(group2.isMember(user1DN));
    assertTrue(group2.isMember(user2DN));
    assertTrue(group2.isMember(user3DN));

    assertNotNull(group3);
    assertTrue(group3.isMember(user1DN));
    assertFalse(group3.isMember(user2DN));
    assertTrue(group3.isMember(user3DN));


    // Get a client connection authenticated as user1 and make sure it handles
    // group operations correctly.
    AuthenticationInfo authInfo = new AuthenticationInfo();
    InternalClientConnection conn0 = new InternalClientConnection(authInfo);
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn0, conn0.nextOperationID(),
                  conn0.nextMessageID(), null, DN.nullDN(),
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);

    assertFalse(conn0.isMemberOf(group1, null));
    assertFalse(conn0.isMemberOf(group2, null));
    assertFalse(conn0.isMemberOf(group3, null));

    assertFalse(conn0.isMemberOf(group1, searchOperation));
    assertFalse(conn0.isMemberOf(group2, searchOperation));
    assertFalse(conn0.isMemberOf(group3, searchOperation));

    Set<Group> groupSet = conn0.getGroups(null);
    assertTrue(groupSet.isEmpty());

    groupSet = conn0.getGroups(searchOperation);
    assertTrue(groupSet.isEmpty());


    // Get a client connection authenticated as user1 and make sure it handles
    // group operations correctly.
    authInfo = new AuthenticationInfo(DirectoryServer.getEntry(user1DN), false);
    InternalClientConnection conn1 = new InternalClientConnection(authInfo);
    searchOperation =
         new InternalSearchOperation(conn1, conn1.nextOperationID(),
                  conn1.nextMessageID(), null, DN.nullDN(),
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,  false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);

    assertTrue(conn1.isMemberOf(group1, null));
    assertFalse(conn1.isMemberOf(group2, null));
    assertTrue(conn1.isMemberOf(group3, null));

    assertTrue(conn1.isMemberOf(group1, searchOperation));
    assertFalse(conn1.isMemberOf(group2, searchOperation));
    assertTrue(conn1.isMemberOf(group3, searchOperation));

    groupSet = conn1.getGroups(null);
    assertTrue(groupSet.contains(group1));
    assertFalse(groupSet.contains(group2));
    assertTrue(groupSet.contains(group3));

    groupSet = conn1.getGroups(searchOperation);
    assertTrue(groupSet.contains(group1));
    assertFalse(groupSet.contains(group2));
    assertTrue(groupSet.contains(group3));


    // Get a client connection authenticated as user2 and make sure it handles
    // group operations correctly.
    authInfo = new AuthenticationInfo(DirectoryServer.getEntry(user2DN), false);
    InternalClientConnection conn2 = new InternalClientConnection(authInfo);
    searchOperation =
         new InternalSearchOperation(conn2, conn2.nextOperationID(),
                  conn2.nextMessageID(), null, DN.nullDN(),
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,  false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);

    assertTrue(conn2.isMemberOf(group1, null));
    assertTrue(conn2.isMemberOf(group2, null));
    assertFalse(conn2.isMemberOf(group3, null));

    assertTrue(conn2.isMemberOf(group1, searchOperation));
    assertTrue(conn2.isMemberOf(group2, searchOperation));
    assertFalse(conn2.isMemberOf(group3, searchOperation));

    groupSet = conn2.getGroups(null);
    assertTrue(groupSet.contains(group1));
    assertTrue(groupSet.contains(group2));
    assertFalse(groupSet.contains(group3));

    groupSet = conn2.getGroups(searchOperation);
    assertTrue(groupSet.contains(group1));
    assertTrue(groupSet.contains(group2));
    assertFalse(groupSet.contains(group3));


    // Get a client connection authenticated as user3 and make sure it handles
    // group operations correctly.
    authInfo = new AuthenticationInfo(DirectoryServer.getEntry(user3DN), false);
    InternalClientConnection conn3 = new InternalClientConnection(authInfo);
    searchOperation =
         new InternalSearchOperation(conn3, conn3.nextOperationID(),
                  conn3.nextMessageID(), null, DN.nullDN(),
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,  false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);

    assertFalse(conn3.isMemberOf(group1, null));
    assertTrue(conn3.isMemberOf(group2, null));
    assertTrue(conn3.isMemberOf(group3, null));

    assertFalse(conn3.isMemberOf(group1, searchOperation));
    assertTrue(conn3.isMemberOf(group2, searchOperation));
    assertTrue(conn3.isMemberOf(group3, searchOperation));

    groupSet = conn3.getGroups(null);
    assertFalse(groupSet.contains(group1));
    assertTrue(groupSet.contains(group2));
    assertTrue(groupSet.contains(group3));

    groupSet = conn3.getGroups(searchOperation);
    assertFalse(groupSet.contains(group1));
    assertTrue(groupSet.contains(group2));
    assertTrue(groupSet.contains(group3));


    // Delete all of the groups and make sure the group manager gets updated
    // accordingly.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(group1DN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(group1DN));

    deleteOperation = conn.processDelete(group2DN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(group2DN));

    deleteOperation = conn.processDelete(group3DN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(group3DN));
  }



  /**
   * Tests operations involving static group member lists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticMemberList()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of Unique Names,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Group of Unique Names",
      "uniqueMember: uid=user.1,ou=People,o=test",
      "uniqueMember: uid=user.2,ou=People,o=test",
      "uniqueMember: uid=user.3,ou=People,o=test",
      "uniqueMember: uid=nonexistentUser,ou=People,o=test");


    // Get the group instance.
    DN groupDN = DN.decode("cn=Test Group of Unique Names,ou=Groups,o=test");
    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);


    // Use a member list to iterate across the member DNs with no filter.
    MemberList memberList = groupInstance.getMembers();
    while (memberList.hasMoreMembers())
    {
      try
      {
        assertNotNull(memberList.nextMemberDN());
      } catch (MembershipException me) {}
    }
    assertNull(memberList.nextMemberDN());
    memberList.close();


    // Perform a filtered iteration across the member DNs.
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    while (memberList.hasMoreMembers())
    {
      try
      {
        assertNotNull(memberList.nextMemberDN());
      } catch (MembershipException me) {}
    }
    assertNull(memberList.nextMemberDN());
    memberList.close();


    // Use a member list to iterate across the member entries with no filter.
    memberList = groupInstance.getMembers();
    while (memberList.hasMoreMembers())
    {
      try
      {
        assertNotNull(memberList.nextMemberEntry());
      } catch (MembershipException me) {}
    }
    assertNull(memberList.nextMemberEntry());
    memberList.close();


    // Perform a filtered iteration across the member entries.
    filter = SearchFilter.createFilterFromString("(objectClass=*)");
    memberList = groupInstance.getMembers(DN.decode("o=test"),
                                          SearchScope.WHOLE_SUBTREE, filter);
    while (memberList.hasMoreMembers())
    {
      try
      {
        assertNotNull(memberList.nextMemberEntry());
      } catch (MembershipException me) {}
    }
    assertNull(memberList.nextMemberEntry());
    memberList.close();


    // Delete the group and make sure the group manager gets updated
    // accordingly.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Invokes general group API methods on a dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGenericDynamicGroupAPI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of URLs,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group of URLs",
      "memberURL: ldap:///o=test??sub?(sn<=2)");

    DN groupDN = DN.decode("cn=Test Group of URLs,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");
    DN user3DN = DN.decode("uid=user.3,ou=People,o=test");
    DN bogusDN = DN.decode("uid=bogus,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    assertEquals(groupInstance.getGroupDN(), groupDN);
    assertTrue(groupInstance.isMember(user1DN));
    assertTrue(groupInstance.isMember(user2DN));
    assertFalse(groupInstance.isMember(user3DN));
    assertFalse(groupInstance.isMember(bogusDN));

    assertFalse(groupInstance.supportsNestedGroups());
    assertTrue(groupInstance.getNestedGroupDNs().isEmpty());

    try
    {
      groupInstance.addNestedGroup(DN.decode("uid=test,ou=People,o=test"));
      throw new AssertionError("Expected addNestedGroup to fail but it " +
                               "didn't");
    } catch (UnsupportedOperationException uoe) {}

    try
    {
      groupInstance.removeNestedGroup(
           DN.decode("uid=test,ou=People,o=test"));
      throw new AssertionError("Expected removeNestedGroup to fail but " +
                               "it didn't");
    } catch (UnsupportedOperationException uoe) {}


    assertFalse(groupInstance.mayAlterMemberList());

    try
    {
      Entry user3Entry = DirectoryServer.getEntry(user3DN);
      groupInstance.addMember(user3Entry);
      throw new AssertionError("Expected addMember to fail but it didn't");
    } catch (UnsupportedOperationException uoe) {}

    try
    {
      groupInstance.removeMember(user2DN);
      throw new AssertionError("Expected removeMember to fail but it didn't");
    } catch (UnsupportedOperationException uoe) {}

    groupInstance.toString(new StringBuilder());


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests to ensure that an attempt to add a dynamic group with a malformed URL
   * will cause it to be decoded as a group but any operations attempted with it
   * will fail with an exception.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDynamicGroupMalformedURL()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: cn=Test Malformed URL,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Malformed URL",
      "memberURL: ldap:///o=test??sub?(malformed)");

    DN groupDN = DN.decode("cn=Test Malformed URL,ou=Groups,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);

    DynamicGroup dynamicGroup = (DynamicGroup) groupInstance;
    assertTrue(dynamicGroup.getMemberURLs().isEmpty());

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests the {@code getMembers()} method for a dynamic group, using the
   * variant that doesn't take any arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMembersSimple()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of URLs,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group of URLs",
      "memberURL: ldap:///o=test??sub?(sn<=2)");

    DN groupDN = DN.decode("cn=Test Group of URLs,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);


    LinkedHashSet<DN> memberSet = new LinkedHashSet<DN>();
    memberSet.add(user1DN);
    memberSet.add(user2DN);

    MemberList memberList = groupInstance.getMembers();
    assertNotNull(memberList);
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberSet.remove(memberDN),
                 "Returned unexpected member " + memberDN.toString());
    }
    memberList.close();
    assertTrue(memberSet.isEmpty(),
               "Expected member set to be empty but it was not:  " + memberSet);


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests the {@code getMembers()} method for a dynamic group, using the
   * variant that takes base, scope, and filter arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMembersComplex()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of URLs,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group of URLs",
      "memberURL: ldap:///o=test??sub?(sn<=2)");

    DN groupDN = DN.decode("cn=Test Group of URLs,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);


    LinkedHashSet<DN> memberSet = new LinkedHashSet<DN>();
    memberSet.add(user1DN);

    MemberList memberList = groupInstance.getMembers(
                                 DN.decode("ou=people,o=test"),
                                 SearchScope.SINGLE_LEVEL,
                                 SearchFilter.createFilterFromString("(sn=1)"));
    assertNotNull(memberList);
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberSet.remove(memberDN),
                 "Returned unexpected member " + memberDN.toString());
    }
    memberList.close();
    assertTrue(memberSet.isEmpty(),
               "Expected member set to be empty but it was not:  " + memberSet);


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests the {@code getMembers()} method for a dynamic group that contains
   * multiple member URLs containing non-overlapping criteria.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMembersMultipleDistinctURLs()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of URLs,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group of URLs",
      "memberURL: ldap:///o=test??sub?(sn=1)",
      "memberURL: ldap:///o=test??sub?(sn=2)");

    DN groupDN = DN.decode("cn=Test Group of URLs,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    groupInstance.toString();


    LinkedHashSet<DN> memberSet = new LinkedHashSet<DN>();
    memberSet.add(user1DN);
    memberSet.add(user2DN);

    MemberList memberList = groupInstance.getMembers();
    assertNotNull(memberList);
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberSet.remove(memberDN),
                 "Returned unexpected member " + memberDN.toString());
    }
    memberList.close();
    assertTrue(memberSet.isEmpty(),
               "Expected member set to be empty but it was not:  " + memberSet);


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }



  /**
   * Tests the {@code getMembers()} method for a dynamic group that contains
   * multiple member URLs containing overlapping criteria.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMembersMultipleOverlappingURLs()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");

    GroupManager groupManager = DirectoryServer.getGroupManager();
    groupManager.deregisterAllGroups();

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: cn=Test Group of URLs,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group of URLs",
      "memberURL: ldap:///dc=example,dc=com??sub?(cn=nonexistent)",
      "memberURL: ldap:///uid=user.2,ou=People,o=test??sub?(sn=2)",
      "memberURL: ldap:///o=test??sub?(sn=1)",
      "memberURL: ldap:///ou=People,o=test??subordinate?(!(sn=3))");

    DN groupDN = DN.decode("cn=Test Group of URLs,ou=Groups,o=test");
    DN user1DN = DN.decode("uid=user.1,ou=People,o=test");
    DN user2DN = DN.decode("uid=user.2,ou=People,o=test");

    Group groupInstance = groupManager.getGroupInstance(groupDN);
    assertNotNull(groupInstance);
    groupInstance.toString();


    LinkedHashSet<DN> memberSet = new LinkedHashSet<DN>();
    memberSet.add(user1DN);
    memberSet.add(user2DN);

    MemberList memberList =
         groupInstance.getMembers(DN.nullDN(), SearchScope.WHOLE_SUBTREE,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    assertNotNull(memberList);
    while (memberList.hasMoreMembers())
    {
      DN memberDN = memberList.nextMemberDN();
      assertTrue(memberSet.remove(memberDN),
                 "Returned unexpected member " + memberDN.toString());
    }
    memberList.close();
    assertTrue(memberSet.isEmpty(),
               "Expected member set to be empty but it was not:  " + memberSet);


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(groupDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(groupManager.getGroupInstance(groupDN));
  }

  /**
   * Adds nested group entries.
   *
   * @throws Exception If a problem adding the entries occurs.
   */
  private void addNestedGroupTestEntries() throws Exception {

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=group 1,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: group 1",
      "",
      "dn: cn=group 2,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: group 2",
      "",
      "dn: cn=group 3,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: group 3",
      "",
      "dn: cn=group 4,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: group 4",
      "memberURL: ldap:///ou=people,o=test??sub?(sn>=5)",
       "",
      "dn: uid=user.1,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "givenName: User",
      "sn: 1",
      "cn: User 1",
      "userPassword: password",
      "",
      "dn: uid=user.2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "givenName: User",
      "sn: 2",
      "cn: User 2",
      "userPassword: password",
      "",
      "dn: uid=user.3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "givenName: User",
      "sn: 3",
      "cn: User 3",
      "userPassword: password",
      "",
      "dn: uid=user.4,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.4",
      "givenName: User",
      "sn: 4",
      "cn: User 4",
      "userPassword: password",
      "",
      "dn: uid=user.5,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: user.5",
      "givenName: User",
      "sn: 5",
      "cn: User 5",
      "userPassword: password");
  }
}

