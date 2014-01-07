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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.core.networkgroups;

import java.util.ArrayList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.DN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * This set of tests test the resource limits.
 */
@SuppressWarnings("javadoc")
public class ResourceLimitsPolicyTest extends DirectoryServerTestCase {
  //===========================================================================
  //
  //                      B E F O R E    C L A S S
  //
  //===========================================================================

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }


  //===========================================================================
  //
  //                      D A T A    P R O V I D E R
  //
  //===========================================================================
  /**
   * Provides information to create a search filter. First parameter is
   * the min substring length, 2nd param the search filter, and last param
   * the expected return value (true=check success, false = check failure).
   */
  @DataProvider (name = "SearchFilterSet")
  public Object[][] initSearchFilterSet()
  {
    Object[][] myData = {
      // Presence filter
      { 5, "(cn=*)", true},
      // Substring filter
      { 5, "(cn=Dir*)", false },
      { 5, "(cn=Direc*)", true },
      { 5, "(cn=D*re*)", false },
      { 5, "(cn=D*re*t*y)", true },
      // NOT filter
      { 5, "(!(cn=Dir*))", false },
      { 5, "(!(cn=*ctory))", true},
      // AND filter
      { 5, "(&(objectclass=*)(cn=Dir*))", false },
      { 5, "(&(objectclass=*)(cn=Direc*))", true },
      // OR filter
      { 5, "(|(objectclass=*)(cn=Dir*))", false },
      { 5, "(|(objectclass=*)(cn=Direc*))",  true }
    };

    return myData;
  }


  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================

  /**
   * Tests the max number of connections resource limit.
   * @throws Exception If the test failed unexpectedly.
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnections()
          throws Exception
  {
    List<Message> messages = new ArrayList<Message>();

    ResourceLimitsPolicyFactory factory = new ResourceLimitsPolicyFactory();
    ResourceLimitsPolicy limits =
        factory.createQOSPolicy(new MockResourceLimitsQOSPolicyCfg()
              {

                @Override
                public int getMaxConnections()
                {
                  return 1;
                }

              });

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    assertTrue(limits.isAllowed(conn1, null, true, messages));

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    assertFalse(limits.isAllowed(conn2, null, true, messages));

    limits.removeConnection(conn1);
    assertTrue(limits.isAllowed(conn2, null, true, messages));

    limits.removeConnection(conn2);
  }

  /**
   * Tests the max number of connections from same IP resource limit.
   * @throws Exception If the test failed unexpectedly.
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnectionsFromSameIp()
          throws Exception
  {
    List<Message> messages = new ArrayList<Message>();

    ResourceLimitsPolicyFactory factory = new ResourceLimitsPolicyFactory();
    ResourceLimitsPolicy limits =
        factory.createQOSPolicy(new MockResourceLimitsQOSPolicyCfg()
              {

                @Override
                public int getMaxConnectionsFromSameIP()
                {
                  return 1;
                }

              });

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    assertTrue(limits.isAllowed(conn1, null, true, messages));

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    assertFalse(limits.isAllowed(conn2, null, true, messages));

    limits.removeConnection(conn1);
    assertTrue(limits.isAllowed(conn2, null, true, messages));

    limits.removeConnection(conn2);
  }

  /**
   * Tests the min substring length.
   * @param minLength minimum search filter substring length
   * @param searchFilter the search filter to test
   * @param success boolean indicating the expected result
   * @throws Exception If the test failed unexpectedly.
   */
  @Test (dataProvider = "SearchFilterSet", groups = "virtual")
  public void testMinSubstringLength(
          final int minLength,
          String searchFilter,
          boolean success)
          throws Exception
  {
    List<Message> messages = new ArrayList<Message>();

    ResourceLimitsPolicyFactory factory = new ResourceLimitsPolicyFactory();
    ResourceLimitsPolicy limits =
        factory.createQOSPolicy(new MockResourceLimitsQOSPolicyCfg()
              {

                @Override
                public int getMinSubstringLength()
                {
                  return minLength;
                }

              });

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    InternalSearchOperation search = conn1.processSearch(
        DN.valueOf("dc=example,dc=com"),
        SearchScope.BASE_OBJECT,
        LDAPFilter.decode(searchFilter).toSearchFilter());

    assertEquals(limits.isAllowed(conn1, search, true, messages), success);
    limits.removeConnection(conn1);
  }


  /**
   * Tests the 'max number of operations per interval' resource limit.
   * @throws Exception If the test failed unexpectedly.
   */
  @Test (groups = "virtual")
  public void testMaxThroughput()
          throws Exception
  {
    List<Message> messages = new ArrayList<Message>();
    final long interval = 1000; // Unit is milliseconds

    ResourceLimitsPolicyFactory factory = new ResourceLimitsPolicyFactory();
    ResourceLimitsPolicy limits = factory.createQOSPolicy(
      new MockResourceLimitsQOSPolicyCfg() {
        @Override
        public int getMaxOpsPerInterval()
        {
          return 1;
        }

        @Override
        public long getMaxOpsInterval()
        {
          return interval;
        }
      });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn);

    final DN dn = DN.valueOf("dc=example,dc=com");
    final SearchFilter all = SearchFilter.createFilterFromString("(objectclass=*)");

    // First operation is allowed
    InternalSearchOperation search1 =
        conn.processSearch(dn, SearchScope.BASE_OBJECT, all);
    assertTrue(limits.isAllowed(conn, search1, true, messages));

    // Second operation in the same interval is refused
    InternalSearchOperation search2 =
        conn.processSearch(dn, SearchScope.BASE_OBJECT, all);
    assertFalse(limits.isAllowed(conn, search2, true, messages));

    // Wait for the end of the interval => counters are reset
    Thread.sleep(interval);

    // The operation is allowed
    InternalSearchOperation search3 =
        conn.processSearch(dn, SearchScope.BASE_OBJECT, all);
    assertTrue(limits.isAllowed(conn, search3, true, messages));
  }

}
