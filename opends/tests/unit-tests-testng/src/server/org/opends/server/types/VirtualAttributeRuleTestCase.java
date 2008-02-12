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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Collections;
import java.util.LinkedHashSet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.
            VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.extensions.EntryDNVirtualAttributeProvider;
import org.opends.server.protocols.internal.InternalClientConnection;

import static org.testng.Assert.*;



/**
 * This class provides a set of test cases for virtual attribute rules, which
 * link a virtual attribute provider implementation with an attribute type and a
 * set of criteria for identifying the entries with which that provider should
 * be used.
 */
public class VirtualAttributeRuleTestCase
       extends TypesTestCase
{
  // The attribute type for the entryDN attribute.
  private AttributeType entryDNType;



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

    entryDNType = DirectoryConfig.getAttributeType("entrydn", false);
    assertNotNull(entryDNType);
  }



  /**
   * Retrieves a set of virtual attribute rules that may be used for testing
   * purposes.  The return data will also include a Boolean value indicating
   * whether the rule would apply to a minimal "o=test" entry.
   *
   * @return  A set of virtual attribute rules that may be used for testing
   *          purposes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testRules")
  public Object[][] getVirtualAttributeRules()
         throws Exception
  {
    EntryDNVirtualAttributeProvider provider =
         new EntryDNVirtualAttributeProvider();

    LinkedHashSet<DN> dnSet1 = new LinkedHashSet<DN>(1);
    dnSet1.add(DN.decode("o=test"));

    LinkedHashSet<DN> dnSet2 = new LinkedHashSet<DN>(1);
    dnSet2.add(DN.decode("dc=example,dc=com"));

    LinkedHashSet<DN> dnSet3 = new LinkedHashSet<DN>(2);
    dnSet3.add(DN.decode("o=test"));
    dnSet3.add(DN.decode("dc=example,dc=com"));


    LinkedHashSet<DN> groupSet1 = new LinkedHashSet<DN>(1);
    groupSet1.add(DN.decode("cn=Test Group,o=test"));

    LinkedHashSet<DN> groupSet2 = new LinkedHashSet<DN>(1);
    groupSet2.add(DN.decode("cn=Example Group,o=test"));

    LinkedHashSet<DN> groupSet3= new LinkedHashSet<DN>(2);
    groupSet3.add(DN.decode("cn=Test Group,o=test"));
    groupSet3.add(DN.decode("cn=Example Group,o=test"));


    LinkedHashSet<SearchFilter> filterSet1 = new LinkedHashSet<SearchFilter>(1);
    filterSet1.add(SearchFilter.createFilterFromString("(objectClass=*)"));

    LinkedHashSet<SearchFilter> filterSet2 = new LinkedHashSet<SearchFilter>(1);
    filterSet2.add(SearchFilter.createFilterFromString("(o=test)"));

    LinkedHashSet<SearchFilter> filterSet3 = new LinkedHashSet<SearchFilter>(1);
    filterSet3.add(SearchFilter.createFilterFromString("(foo=bar)"));

    LinkedHashSet<SearchFilter> filterSet4 = new LinkedHashSet<SearchFilter>(2);
    filterSet4.add(SearchFilter.createFilterFromString("(o=test)"));
    filterSet4.add(SearchFilter.createFilterFromString("(foo=bar)"));

    return new Object[][]
    {
      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(),
                                 Collections.<DN>emptySet(),
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider, dnSet1,
                                 Collections.<DN>emptySet(),
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider, dnSet2,
                                 Collections.<DN>emptySet(),
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        false
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider, dnSet3,
                                 Collections.<DN>emptySet(),
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(), groupSet1,
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(), groupSet2,
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        false
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(), groupSet3,
                                 Collections.<SearchFilter>emptySet(),
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(),
                                 Collections.<DN>emptySet(), filterSet1,
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(),
                                 Collections.<DN>emptySet(), filterSet2,
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(),
                                 Collections.<DN>emptySet(), filterSet3,
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        false
      },

      new Object[]
      {
        new VirtualAttributeRule(entryDNType, provider,
                                 Collections.<DN>emptySet(),
                                 Collections.<DN>emptySet(), filterSet4,
                                 ConflictBehavior.VIRTUAL_OVERRIDES_REAL),
        true
      },
    };
  }



  /**
   * Tests the various getter methods in the virtual attribute rule class.
   *
   * @param  rule            The rule for which to perform the test.
   * @param  appliesToEntry  Indicates whether the provided rule applies to a
   *                         minimal "o=test" entry.
   */
  @Test(dataProvider = "testRules")
  public void testGetters(VirtualAttributeRule rule, boolean appliesToEntry)
  {
    assertEquals(rule.getAttributeType(), entryDNType);
    assertEquals(rule.getProvider().getClass().getName(),
                 EntryDNVirtualAttributeProvider.class.getName());
    assertNotNull(rule.getBaseDNs());
    assertNotNull(rule.getGroupDNs());
    assertNotNull(rule.getFilters());
    assertNotNull(rule.getConflictBehavior());
  }



  /**
   * Tests the {@code appliesToEntry} method.
   *
   * @param  rule            The rule for which to perform the test.
   * @param  appliesToEntry  Indicates whether the provided rule applies to a
   *                         minimal "o=test" entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testRules")
  public void testAppliesToEntry(VirtualAttributeRule rule,
                                 boolean appliesToEntry)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addGroups();
    assertEquals(rule.appliesToEntry(
                      DirectoryConfig.getEntry(DN.decode("o=test"))),
                 appliesToEntry);
    removeGroups();
  }



  /**
   * Tests the {@code toString} method.
   *
   * @param  rule            The rule for which to perform the test.
   * @param  appliesToEntry  Indicates whether the provided rule applies to a
   *                         minimal "o=test" entry.
   */
  @Test(dataProvider = "testRules")
  public void testToString(VirtualAttributeRule rule, boolean appliesToEntry)
  {
    String ruleString = rule.toString();
    assertNotNull(ruleString);
    assertTrue(ruleString.length() > 0);
  }



  /**
   * Adds a group to the server in which the "o=test" entry is a member.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void addGroups()
          throws Exception
  {
    TestCaseUtils.addEntries(
      "dn: cn=Test Group,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Test Group",
      "uniqueMember: o=test",
      "",
      "dn: cn=Example Group,o=test",
      "objectClass: top",
      "objectClass: groupOfUniqueNames",
      "cn: Example Group",
      "uniqueMember: dc=example,dc=com");
  }



  /**
   * Removes the test group from the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void removeGroups()
          throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.processDelete(DN.decode("cn=Test Group,o=Test"));
    conn.processDelete(DN.decode("cn=Example Group,o=Test"));
  }
}

