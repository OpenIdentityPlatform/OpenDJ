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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


/*
 * This set of tests test the resource limits.
 */
public class ResourceLimitsTest extends DirectoryServerTestCase {
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
  public void setUp()
    throws Exception
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
   * @throws DirectoryException when there was a problem creating the connection
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnections()
          throws DirectoryException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    ResourceLimits limits = new ResourceLimits(null);
    limits.setMaxConnections(1);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    boolean check = limits.checkLimits(conn1, null, true, messages);
    assertTrue(check);

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    check = limits.checkLimits(conn2, null, true, messages);
    assertFalse(check);

    limits.removeConnection(conn1);
    check = limits.checkLimits(conn2, null, true, messages);
    assertTrue(check);

    limits.removeConnection(conn2);
  }

  /**
   * Tests the max number of connections from same IP resource limit.
   * @throws DirectoryException when there was a problem creating the connection
   */
  @Test (groups = "virtual")
  public void testMaxNumberOfConnectionsFromSameIp()
          throws DirectoryException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    ResourceLimits limits = new ResourceLimits(null);
    limits.setMaxConnectionsFromSameIP(1);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    boolean check = limits.checkLimits(conn1, null, true, messages);
    assertTrue(check);

    InternalClientConnection conn2 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn2);
    check = limits.checkLimits(conn2, null, true, messages);
    assertFalse(check);

    limits.removeConnection(conn1);
    check = limits.checkLimits(conn2, null, true, messages);
    assertTrue(check);

    limits.removeConnection(conn2);
  }

  /**
   * Tests the min substring length.
   * @param minLength minimum search filter substring length
   * @param searchFilter the search filter to test
   * @param success boolean indicating the expected result
   * @throws DirectoryException when there was a problem creating the connection
   * @throws LDAPException when there was a problem decoding the filter
   */
  @Test (dataProvider = "SearchFilterSet", groups = "virtual")
  public void testMinSubstringLength(
          int minLength,
          String searchFilter,
          boolean success)
          throws DirectoryException, LDAPException
  {
    List<Message> messages = new ArrayList<Message>();
    ResourceLimits limits = new ResourceLimits(null);
    limits.setMinSearchSubstringLength(minLength);

    InternalClientConnection conn1 = new InternalClientConnection(DN.NULL_DN);
    limits.addConnection(conn1);

    InternalSearchOperation search = conn1.processSearch(
        DN.decode("dc=example,dc=com"),
        SearchScope.BASE_OBJECT,
        LDAPFilter.decode(searchFilter).toSearchFilter());

    boolean check = limits.checkLimits(conn1, search, true, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
    limits.removeConnection(conn1);
  }


}
