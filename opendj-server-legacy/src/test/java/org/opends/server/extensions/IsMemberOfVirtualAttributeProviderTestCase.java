/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the isMemberOf virtual attribute provider.
 */
@SuppressWarnings("javadoc")
public class IsMemberOfVirtualAttributeProviderTestCase
       extends ExtensionsTestCase
{
  /** The attribute type for the isMemberOf attribute. */
  private AttributeType isMemberOfType;



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
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
  @Test
  public void testStaticGroupMembershipMember() throws Exception
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

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.size(), 1);

      assertTrue(!a.isEmpty());
      assertTrue(a.contains(ByteString.valueOf("cn=test static group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=not a group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("invalid")));
    }

    delete("cn=test static group,ou=groups,o=test");
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of a static group based on the uniqueMember
   * attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStaticGroupMembershipUniqueMember() throws Exception
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

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.size(), 1);

      assertTrue(!a.isEmpty());
      assertTrue(a.contains(ByteString.valueOf("cn=test static group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=not a group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("invalid")));
    }

    delete("cn=test static group,ou=groups,o=test");
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of a dynamic group.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDynamicGroupMembership() throws Exception
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

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.size(), 1);

      assertTrue(!a.isEmpty());
      assertTrue(a.contains(ByteString.valueOf("cn=test dynamic group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=not a group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("invalid")));
    }

    delete("cn=test dynamic group,ou=groups,o=test");
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of multiple static groups.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMultipleStaticGroups() throws Exception
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

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.size(), 2);

      assertTrue(!a.isEmpty());
      assertTrue(a.contains(ByteString.valueOf("cn=test group 1,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=test group 2,ou=groups,o=test")));
      assertTrue(a.contains(ByteString.valueOf("cn=test group 3,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=not a group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("invalid")));
    }

    delete("cn=test group 1,ou=groups,o=test",
        "cn=test group 2,ou=groups,o=test",
        "cn=test group 3,ou=groups,o=test");
  }



  /**
   * Tests that the isMemberOf virtual attribute is properly generated for an
   * entry that is a member of multiple static and dynamic groups.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMultipleGroups() throws Exception
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

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(e);
    assertTrue(e.hasAttribute(isMemberOfType));
    for (Attribute a : e.getAttribute(isMemberOfType))
    {
      assertEquals(a.size(), 4);

      assertTrue(!a.isEmpty());
      assertTrue(a.contains(ByteString.valueOf("cn=test group 1,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=test group 2,ou=groups,o=test")));
      assertTrue(a.contains(ByteString.valueOf("cn=test group 3,ou=groups,o=test")));
      assertTrue(a.contains(ByteString.valueOf("cn=test group 4,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=test group 5,ou=groups,o=test")));
      assertTrue(a.contains(ByteString.valueOf("cn=test group 6,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("cn=not a group,ou=groups,o=test")));
      assertFalse(a.contains(ByteString.valueOf("invalid")));
    }

    delete("cn=test group 1,ou=groups,o=test",
        "cn=test group 2,ou=groups,o=test",
        "cn=test group 3,ou=groups,o=test",
        "cn=test group 4,ou=groups,o=test",
        "cn=test group 5,ou=groups,o=test",
        "cn=test group 6,ou=groups,o=test");
  }



  /**
   * Tests the {@code isMultiValued} method.
   */
  @Test
  public void testIsMultiValued()
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();
    assertTrue(provider.isMultiValued());
  }



  /**
   * Tests the {@code matchesSubstring} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMatchesSubstring() throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule = buildRule(provider);

    LinkedList<ByteString> subAny = new LinkedList<>();
    subAny.add(ByteString.valueOf("="));

    assertEquals(provider.matchesSubstring(entry, rule, null, subAny, null),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code greaterThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGreaterThanOrEqualTo() throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule = buildRule(provider);

    ByteString value = ByteString.valueOf("o=test2");
    assertEquals(provider.greaterThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code lessThanOrEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLessThanOrEqualTo() throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule = buildRule(provider);

    ByteString value = ByteString.valueOf("o=test2");
    assertEquals(provider.lessThanOrEqualTo(entry, rule, value),
                 ConditionResult.UNDEFINED);
  }



  /**
   * Tests the {@code approximatelyEqualTo} method to ensure that it returns a
   * result of "undefined".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testApproximatelyEqualTo() throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: o=test",
      "objectClass: top",
      "objectClass: organization",
      "o: test");
    entry.processVirtualAttributes();

    VirtualAttributeRule rule = buildRule(provider);

    ByteString value = ByteString.valueOf("o=test2");
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
      new Object[] { "(&(isMemberOf=invalid1)(isMemberOf=invalid2))", true, false },
      new Object[] { "(isMemberOf>=cn=Test Group 1,ou=Groups,o=test)", false, false },
      new Object[] { "(isMemberOf<=cn=Test Group 1,ou=Groups,o=test)", false, false },
      new Object[] { "(isMemberOf~=cn=Test Group 1,ou=Groups,o=test)", false, false },
      new Object[] { "(isMemberOf=cn=Test Group 1,ou=Groups,o=test)", true, true },
      new Object[] { "(isMemberOf=cn=Test Group 2,ou=Groups,o=test)", true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(givenName=test))", true, true },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=invalid))", true, false },
      new Object[] { "(&(isMemberOf=invalid)" +
                       "(isMemberOf=cn=Test Group 1,ou=Groups,o=test))", true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(givenName=not test))", true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 2,ou=Groups,o=test))", true, false },
      new Object[] { "(&(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 3,ou=Groups,o=test))", true, true },
      new Object[] { "(&(isMemberOf=cn=Test Group 2,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 4,ou=Groups,o=test))", true, false },
      new Object[] { "(|(isMemberOf=cn=Test Group 1,ou=Groups,o=test)" +
                       "(isMemberOf=cn=Test Group 3,ou=Groups,o=test))", false, false },
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
  public void testIsSearchable(String filterString, boolean isSearchable, boolean shouldMatch) throws Exception
  {
    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    VirtualAttributeRule rule = buildRule(provider);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE, filterString);
    InternalSearchOperation searchOperation = new InternalSearchOperation(
        getRootConnection(), nextOperationID(), nextMessageID(), request, null);
    assertEquals(provider.isSearchable(rule,
        new LocalBackendSearchOperation(searchOperation), false), isSearchable);
    // isMemberOf is not searchable with preIndexed set to true
    assertEquals(provider.isSearchable(rule,
        new LocalBackendSearchOperation(searchOperation), true), false);
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
  public void testProcessSearch(String filterString, boolean isSearchable, boolean shouldMatch) throws Exception
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

    Entry userEntry = DirectoryServer.getEntry(DN.valueOf("uid=test.user,ou=People,o=test"));
    assertNotNull(userEntry);

    IsMemberOfVirtualAttributeProvider provider =
         new IsMemberOfVirtualAttributeProvider();

    VirtualAttributeRule rule = buildRule(provider);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE, filterString);
    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation searchOperation = new InternalSearchOperation(
        conn, nextOperationID(), nextMessageID(), request, null);
    provider.processSearch(rule, new LocalBackendSearchOperation(searchOperation));

    boolean matchFound = false;
    for (Entry e : searchOperation.getSearchEntries())
    {
      if (e.getName().equals(userEntry.getName()))
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

    delete("cn=test group 1,ou=groups,o=test",
        "cn=test group 2,ou=groups,o=test",
        "cn=test group 3,ou=groups,o=test",
        "cn=test group 4,ou=groups,o=test");
  }

  /**
   * Tests the {@code processSearch} method when dealing with nested groups.
   * This is a test for issue OPENDJ-1586. Before the fix the method would
   * only return the direct members of the groups. Now it should return
   * all.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearchWithNestedGroup() throws Exception
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
        "member: cn=Test Group 1,ou=Groups,o=test");

    IsMemberOfVirtualAttributeProvider provider =
        new IsMemberOfVirtualAttributeProvider();

    VirtualAttributeRule rule = buildRule(provider);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.WHOLE_SUBTREE,
        "isMemberOf=cn=Test Group 3,ou=Groups,o=test");
    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation searchOperation = new InternalSearchOperation(
        conn, nextOperationID(), nextMessageID(), request, null);
    provider.processSearch(rule, new LocalBackendSearchOperation(searchOperation));

    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    assertEquals(entries.size(), 2, "Expecting 2 entries, but got " + entries.size());
    // First direct members
    assertEquals(entries.get(0).getName(), DN.valueOf("cn=test group 1,ou=groups,o=test"));
    // Then indirect members
    assertEquals(entries.get(1).getName(), DN.valueOf("uid=test.user,ou=people,o=test"));

    delete("cn=test group 1,ou=groups,o=test",
        "cn=test group 2,ou=groups,o=test",
        "cn=test group 3,ou=groups,o=test");
  }


  /**
   * Tests if a search using ismemberof works for a dynamic group with large
   * number of entries to simulate unindexed searches.
   */
  @Test
  public void testLargeDynamicGroupMembership() throws Exception
  {
    String SUFFIX=",dc=example,dc=com";
    StringBuilder builder = new StringBuilder();
    builder.append("dn: dc=example,dc=com");
    builder.append("\nobjectClass: domain");
    builder.append("\ndc: example");

    builder.append("\n\ndn: ou=People").append(SUFFIX);
    builder.append("\nobjectClass: organizationalunit");
    builder.append("\nou: People");
    //Go beyond ALL ID threshold.
    for(int i=0;i<4001;i++)
    {
      builder.append("\n\ndn: cn=user.").append(i)
             .append(",ou=People").append(SUFFIX);
      builder.append("\nobjectclass: person");
      builder.append("\ncn: user.").append(i);
      builder.append("\nsn: ").append(i);
    }

    //Add the group information.
    builder.append("\n\ndn: ou=Groups").append(SUFFIX);
    builder.append("\nobjectclass: organizationalunit");
    builder.append("\nou: Groups");

    //Dynamic group.
    builder.append("\n\ndn: cn=MyDGrp,ou=Groups").append(SUFFIX);
    builder.append("\nobjectClass: groupOfURLs");
    builder.append("\ncn: MyDGrp");
    builder.append("\nmemberURL: ldap:///ou=people").append(SUFFIX)
           .append("??sub?(objectclass=person)");
    TestCaseUtils.addEntries(builder.toString());
    //Verify the entry.
    Entry e = DirectoryServer.getEntry(DN.valueOf("cn=user.0,ou=People" + SUFFIX));
    assertNotNull(e);
    //Do an ldapsearch.


    String filter = "(&(objectclass=Person)"
        + "(isMemberOf=cn=MyDGrp,ou=groups,dc=example,dc=com))";
    SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, filter);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    assertTrue(entries.size()>4000);
  }

  private VirtualAttributeRule buildRule(IsMemberOfVirtualAttributeProvider provider)
  {
    return new VirtualAttributeRule(isMemberOfType, provider,
              Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
              Collections.<DN>emptySet(),
              Collections.<SearchFilter>emptySet(),
              VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
  }

  private void delete(String... dns) throws Exception
  {
    for (String dn : dns)
    {
      DeleteOperation deleteOperation = getRootConnection().processDelete(DN.valueOf(dn));
      assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    }
  }
}

