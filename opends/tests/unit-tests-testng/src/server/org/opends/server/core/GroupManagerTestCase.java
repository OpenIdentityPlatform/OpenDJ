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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Group;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DereferencePolicy;
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

    assertTrue(groupManager.getGroupImplementations().iterator().hasNext());
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
    assertTrue(groupInstance.isMember(user1DN));
    assertTrue(groupInstance.isMember(user2DN));
    assertFalse(groupInstance.isMember(user3DN));

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
}

