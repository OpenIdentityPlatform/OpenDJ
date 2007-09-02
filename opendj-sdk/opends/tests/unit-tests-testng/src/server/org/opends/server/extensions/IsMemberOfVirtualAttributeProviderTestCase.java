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
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the isMemberOf virtual attribute provider.
 */
public class IsMemberOfVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  // The attribute type for the isMemberOf attribute.
  private AttributeType isMemberOfType;



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

    isMemberOfType = DirectoryServer.getAttributeType("ismemberof", false);
    assertNotNull(isMemberOfType);
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of a static group based on the member attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupMembershipMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.getValues().size(), 1);

      assertTrue(a.hasValue());
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test static group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=not a group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType, "invalid")));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of a static group based on the uniqueMember
   * attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStaticGroupMembershipUniqueMember()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Static Group",
      "uniqueMember: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.getValues().size(), 1);

      assertTrue(a.hasValue());
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test static group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=not a group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType, "invalid")));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of a dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDynamicGroupMembership()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Dynamic Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Dynamic Group",
      "memberURL: ldap:///ou=People,o=test??sub?(sn=user)");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.getValues().size(), 1);

      assertTrue(a.hasValue());
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                      "cn=test dynamic group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=not a group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType, "invalid")));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(
              DN.decode("cn=test dynamic group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of multiple static groups.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMultipleStaticGroups()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: uid=test.user2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user2",
      "givenName: Test",
      "sn: User2",
      "cn: Test User2",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Group 1,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 1",
      "member: uid=test.user,ou=People,o=test",
      "",
      "dn: cn=Test Group 2,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 2",
      "member: uid=test.user2,ou=People,o=test",
      "",
      "dn: cn=Test Group 3,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 3",
      "member: uid=test.user,ou=People,o=test",
      "member: uid=test.user2,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.getValues().size(), 2);

      assertTrue(a.hasValue());
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 1,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=test group 2,ou=groups,o=test")));
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 3,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=not a group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType, "invalid")));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test group 1,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 2,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 3,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of multiple static and dynamic groups.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMultipleGroups()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: uid=test.user2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user2",
      "givenName: Test",
      "sn: User2",
      "cn: Test User2",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Group 1,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 1",
      "member: uid=test.user,ou=People,o=test",
      "",
      "dn: cn=Test Group 2,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 2",
      "member: uid=test.user2,ou=People,o=test",
      "",
      "dn: cn=Test Group 3,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 3",
      "member: uid=test.user,ou=People,o=test",
      "member: uid=test.user2,ou=People,o=test",
      "",
      "dn: cn=Test Group 4,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group 4",
      "memberURL: ldap:///o=test??sub?(uid=test.user)",
      "",
      "dn: cn=Test Group 5,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group 5",
      "memberURL: ldap:///o=test??sub?(uid=test.user1)",
      "",
      "dn: cn=Test Group 6,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfURLs",
      "cn: Test Group 6",
      "memberURL: ldap:///o=test??sub?(givenName=test)");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.getValues().size(), 4);

      assertTrue(a.hasValue());
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 1,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=test group 2,ou=groups,o=test")));
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 3,ou=groups,o=test")));
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 4,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=test group 5,ou=groups,o=test")));
      assertTrue(a.hasValue(new AttributeValue(isMemberOfType,
                                     "cn=test group 6,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType,
                                      "cn=not a group,ou=groups,o=test")));
      assertFalse(a.hasValue(new AttributeValue(isMemberOfType, "invalid")));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test group 1,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 2,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 3,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 4,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 5,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 6,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test()
  public void testIsMultiValued()
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    assertTrue(provider.isMultiValued());
  }



  /**
   * Tests the {@code hasAnyValue} method with an empty set of values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueEmptySet()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    assertFalse(provider.hasAnyValue(e, rule,
                                     Collections.<AttributeValue>emptySet()));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing only
   * the correct value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueOnlyCorrect()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test static group,ou=groups,o=test"));

    assertTrue(provider.hasAnyValue(e, rule, values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing only
   * an incorrect value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueOnlyIncorrect()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test dynamic group,ou=groups,o=test"));

    assertFalse(provider.hasAnyValue(e, rule, values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of values containing the
   * correct value as well as multiple incorrect values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueIncludesCorrect()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test static group,ou=groups,o=test"));
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test dynamic group,ou=groups,o=test"));

    assertTrue(provider.hasAnyValue(e, rule, values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code hasAnyValue} method with a set of multiple values, none of
   * which are correct.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasAnyValueMissingCorrect()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Static Group,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Static Group",
      "member: uid=test.user,ou=People,o=test");

    Entry e =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test nonstatic group,ou=groups,o=test"));
    values.add(new AttributeValue(isMemberOfType,
                                  "cn=test dynamic group,ou=groups,o=test"));

    assertFalse(provider.hasAnyValue(e, rule, values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test static group,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the {@code matchesSubstring} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMatchesSubstring()
         throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    LinkedList<ByteString> subAny = new LinkedList<ByteString>();
    subAny.add(ByteStringFactory.create("="));

    assertEquals(provider.matchesSubstring(entry, rule, null, subAny, null),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code greaterThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGreaterThanOrEqualTo()
         throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(isMemberOfType, "o=test2");
    assertEquals(provider.greaterThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code lessThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLessThanOrEqualTo()
         throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(isMemberOfType, "o=test2");
    assertEquals(provider.lessThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code approximatelyEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testApproximatelyEqualTo()
         throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    AttributeValue value = new AttributeValue(isMemberOfType, "o=test2");
    assertEquals(provider.approximatelyEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Retrieves a set of filters for use in testing searchability.  The returned
   * data will actually include three elements:
   * <OL>
   *   <LI>The string representation of the search filter to use</LI>
   *   <LI>An indication of whether it should be searchable</LI>
   *   <LI>An indication of whether the uid=test.user,ou=People,o=test entry
   *       should match</LI>
   * </OL>
   *
   * @return  A set of filters for use in testing searchability.
   */
  @DataProvider(name = "testFilters")
  public Object[][] getTestFilters()
  {
    return new Object[][]
    {
      new Object[] { "(isMemberOf=*)", false, false },
      new Object[] { "(isMemberOf=cn*)", false, false },
      new Object[] { "(isMemberOf=invalid)", true, false },
      new Object[] { "(&(isMemberOf=invalid1)(isMemberOf=invalid2))",
                     true, false },
      new Object[] { "(isMemberOf>=cn=Test Group 1,ou=Groups,o=test)",
                     false, false },
      new Object[] { "(isMemberOf<=cn=Test Group 1,ou=Groups,o=test)",
                     false, false },
      new Object[] { "(isMemberOf~=cn=Test Group 1,ou=Groups,o=test)",
                     false, false },
      new Object[] { "(isMemberOf=cn=Test Group 1,ou=Groups,o=test)",
                     true, true },
      new Object[] { "(isMemberOf=cn=Test Group 2,ou=Groups,o=test)",
                     true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(givenName=test))",
                     true, true },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=invalid))",
                     true, false },
      new Object[] { "(&(isMemberOf=invalid)" +
                       "(isMemberOf=cn=Test Group 1,ou=Groups,o=test))",
                     true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(givenName=not test))",
                     true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 2,ou=Groups,o=test))",
                     true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 3,ou=Groups,o=test))",
                     true, true },
      new Object[] { "(&(isMemberOf=cn=Test Group 2,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 4,ou=Groups,o=test))",
                     true, false },
      new Object[] { "(|(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 3,ou=Groups,o=test))",
                     false, false },
    };
  }



  /**
   * Tests the {@code isSearchable} method with the provided information.
   *
   * @param  filterString  The string representation of the search filter to use
   *                       for the test.
   * @param  isSearchable  Indicates whether a search with the given filter
   *                       should be considered searchable.
   * @param  shouldMatch   Indicates whether the provided filter should match
   *                       a minimal o=test entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testFilters")
  public void testIsSearchable(String filterString, boolean isSearchable,
                               boolean shouldMatch)
         throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    SearchFilter filter = SearchFilter.createFilterFromString(filterString);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), null,
                                     DN.decode("o=test"),
                                     SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, null, null);

    assertEquals(provider.isSearchable(rule, new LocalBackendSearchOperation(searchOperation)), isSearchable);
  }



  /**
   * Tests the {@code processSearch} method with the provided information.
   *
   * @param  filterString  The string representation of the search filter to use
   *                       for the test.
   * @param  isSearchable  Indicates whether a search with the given filter
   *                       should be considered searchable.
   * @param  shouldMatch   Indicates whether the provided filter should match
   *                       a minimal o=test entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testFilters")
  public void testProcessSearch(String filterString, boolean isSearchable,
                                boolean shouldMatch)
         throws Exception
  {
    if (! isSearchable)
    {
      return;
    }

    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "",
      "dn: uid=test.user2,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user2",
      "givenName: Test",
      "sn: User2",
      "cn: Test User2",
      "userPassword: password",
      "",
      "dn: uid=test.user3,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user3",
      "givenName: Test",
      "sn: User3",
      "cn: Test User3",
      "userPassword: password",
      "",
      "dn: ou=Groups,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: Groups",
      "",
      "dn: cn=Test Group 1,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 1",
      "member: uid=test.user,ou=People,o=test",
      "",
      "dn: cn=Test Group 2,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 2",
      "member: uid=test.user2,ou=People,o=test",
      "",
      "dn: cn=Test Group 3,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 3",
      "member: uid=test.user,ou=People,o=test",
      "member: uid=test.user2,ou=People,o=test",
      "",
      "dn: cn=Test Group 4,ou=Groups,o=test",
      "objectClass: top",
      "objectClass: groupOfNames",
      "cn: Test Group 4",
      "member: uid=test.user2,ou=People,o=test",
      "member: uid=test.user3,ou=People,o=test");

    Entry userEntry =
         DirectoryServer.getEntry(DN.decode("uid=test.user,ou=People,o=test"));
    assertNotNull(userEntry);

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    VirtualAttributeRule rule =
         new VirtualAttributeRule(isMemberOfType, provider,
                  Collections.<DN>emptySet(), Collections.<DN>emptySet(),
                  Collections.<SearchFilter>emptySet(),
                  VirtualAttributeCfgDefn.ConflictBehavior.
                       VIRTUAL_OVERRIDES_REAL);

    SearchFilter filter = SearchFilter.createFilterFromString(filterString);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), null,
                                     DN.decode("o=test"),
                                     SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, null, null);
    provider.processSearch(rule, new LocalBackendSearchOperation(searchOperation));

    boolean matchFound = false;
    for (Entry e : searchOperation.getSearchEntries())
    {
      if (e.getDN().equals(userEntry.getDN()))
      {
        if (matchFound)
        {
          fail("Multiple matches found for the same user.");
        }
        else
        {
          matchFound = true;
        }
      }
    }

    assertEquals(matchFound, shouldMatch);

    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test group 1,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 2,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 3,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
         conn.processDelete(DN.decode("cn=test group 4,ou=groups,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }
}

