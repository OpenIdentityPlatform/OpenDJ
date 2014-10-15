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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.ResourceLimitsQOSPolicyCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.opends.server.protocols.internal.Requests.*;
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
    return new Object[][] {
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
  }


  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================

  /**
   * Tests the max number of connections resource limit.
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnections() throws Exception
  {
    final ResourceLimitsQOSPolicyCfg cfg = mock(ResourceLimitsQOSPolicyCfg.class);
    when(cfg.getMaxConnections()).thenReturn(1);
    final ResourceLimitsPolicy limits = createQOSPolicy(cfg);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    assertOperationIsAllowed(limits, conn1, null, true);

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    assertOperationIsAllowed(limits, conn2, null, false);

    limits.removeConnection(conn1);
    assertOperationIsAllowed(limits, conn2, null, true);

    limits.removeConnection(conn2);
  }

  /**
   * Tests the max number of connections from same IP resource limit.
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnectionsFromSameIp() throws Exception
  {
    final ResourceLimitsQOSPolicyCfg cfg = mock(ResourceLimitsQOSPolicyCfg.class);
    when(cfg.getMaxConnectionsFromSameIP()).thenReturn(1);
    final ResourceLimitsPolicy limits = createQOSPolicy(cfg);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    assertOperationIsAllowed(limits, conn1, null, true);

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    assertOperationIsAllowed(limits, conn2, null, false);

    limits.removeConnection(conn1);
    assertOperationIsAllowed(limits, conn2, null, true);

    limits.removeConnection(conn2);
  }

  /**
   * Tests the min substring length.
   * @param minLength minimum search filter substring length
   * @param searchFilter the search filter to test
   * @param success boolean indicating the expected result
   */
  @Test (dataProvider = "SearchFilterSet", groups = "virtual")
  public void testMinSubstringLength(
          final int minLength,
          String searchFilter,
          boolean success)
          throws Exception
  {
    final ResourceLimitsQOSPolicyCfg cfg = mock(ResourceLimitsQOSPolicyCfg.class);
    when(cfg.getMinSubstringLength()).thenReturn(minLength);
    final ResourceLimitsPolicy limits = createQOSPolicy(cfg);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    final SearchRequest request = newSearchRequest("dc=example,dc=com", SearchScope.BASE_OBJECT, searchFilter);
    InternalSearchOperation search = conn1.processSearch(request);

    assertOperationIsAllowed(limits, conn1, search, success);
    limits.removeConnection(conn1);
  }

  /**
   * Tests the 'max number of operations per interval' resource limit.
   */
  @Test (groups = "virtual")
  public void testMaxThroughput() throws Exception
  {
    final long interval = 1000; // Unit is milliseconds

    final ResourceLimitsQOSPolicyCfg cfg = mock(ResourceLimitsQOSPolicyCfg.class);
    when(cfg.getMaxOpsPerInterval()).thenReturn(1);
    when(cfg.getMaxOpsInterval()).thenReturn(interval);
    final ResourceLimitsPolicy limits = createQOSPolicy(cfg);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn);

    final SearchRequest request = newSearchRequest(DN.valueOf("dc=example,dc=com"), SearchScope.BASE_OBJECT);
    final InternalSearchOperation search1 = conn.processSearch(request);
    assertOperationIsAllowed(limits, conn, search1, true,
        "First operation should be allowed");

    final InternalSearchOperation search2 = conn.processSearch(request);
    assertOperationIsAllowed(limits, conn, search2, false,
        "Second operation in the same interval should be disallowed");

    // Wait for the end of the interval => counters are reset
    Thread.sleep(interval);

    final InternalSearchOperation search3 = conn.processSearch(request);
    assertOperationIsAllowed(limits, conn, search3, true,
        "Third operation should be allowed");
  }

  private void assertOperationIsAllowed(ResourceLimitsPolicy limits,
      ClientConnection conn, InternalSearchOperation operation, boolean expected)
  {
    assertOperationIsAllowed(limits, conn, operation, expected, null);
  }

  private void assertOperationIsAllowed(ResourceLimitsPolicy limits,
      ClientConnection conn, InternalSearchOperation operation,
      boolean expected, String assertMsg)
  {
    final String msg = assertMsg != null ? assertMsg :
      "Operation should be " + (expected ? "" : "dis") + "allowed";

    final List<LocalizableMessage> messages =
        new ArrayList<LocalizableMessage>();
    final boolean actual = limits.isAllowed(conn, operation, true, messages);
    assertEquals(actual, expected, msg + ". Messages=" + messages);
  }

  private ResourceLimitsPolicy createQOSPolicy(ResourceLimitsQOSPolicyCfg cfg) throws Exception
  {
    return new ResourceLimitsPolicyFactory().createQOSPolicy(cfg);
  }

}
