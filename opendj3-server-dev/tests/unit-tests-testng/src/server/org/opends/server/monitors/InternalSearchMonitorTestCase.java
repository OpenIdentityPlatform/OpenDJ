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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.monitors;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.types.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;

import static org.testng.Assert.*;

/**
 * Interacts with the Directory Server monitor providers by retrieving the
 * monitor entries with internal searches.
 */
public class InternalSearchMonitorTestCase
       extends MonitorTestCase
{
  private static TestMonitorProvider testMonitorProvider = new TestMonitorProvider();

  /**
   * Ensures that the Directory Server is started.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    DirectoryServer.registerMonitorProvider(testMonitorProvider);
  }



  @AfterClass()
  public void deregisterTestMonitor()
  {
    DirectoryServer.deregisterMonitorProvider(testMonitorProvider);
  }

  /**
   * Uses an internal subtree search to retrieve the monitor entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithSubtreeMonitorSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.valueOf("cn=monitor"), SearchScope.WHOLE_SUBTREE,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS,
        "Failed to search cn=monitor subtree. Got error message: " + searchOperation.getErrorMessage());
  }



  /**
   * Retrieves the names of the monitor providers registered with the server.
   *
   * @return  The names of the monitor providers registered with the server.
   */
  @DataProvider(name = "monitorNames")
  public Object[][] getMonitorNames()
  {
    ArrayList<String> monitorNames = new ArrayList<String>();
    for (String name : DirectoryServer.getMonitorProviders().keySet())
    {
      monitorNames.add(name);
    }

    Object[][] nameArray = new Object[monitorNames.size()][1];
    for (int i=0; i < nameArray.length; i++)
    {
      nameArray[i] = new Object[] { monitorNames.get(i) };
    }

    return nameArray;
  }



  /**
   * Uses a set of internal base-level searches to retrieve the monitor entries.
   *
   * @param  monitorName  The name of the monitor entry to retrieve.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "monitorNames")
  public void testWithBaseObjectMonitorSearch(String monitorName)
         throws Exception
  {
    // could be more than one level
    DN monitorDN = DN.valueOf("cn="+monitorName+",cn=monitor");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(monitorDN,
              SearchScope.BASE_OBJECT,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS,
        "Failed to read " + monitorDN + " entry. Got error message: " + searchOperation.getErrorMessage());
  }

  /**
   * Uses an internal subtree search to retrieve the monitor entries, then
   * verifies that the resulting entry DNs can be used to get the same
   * entries with a base object search.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithSubtreeAndBaseMonitorSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.valueOf("cn=monitor"), SearchScope.WHOLE_SUBTREE,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS,
        "Failed to search cn=monitor subtree. Got error message: " + searchOperation.getErrorMessage());

    for (SearchResultEntry sre : searchOperation.getSearchEntries())
    {
      SearchFilter filter =
           SearchFilter.createFilterFromString("(objectClass=*)");
      InternalSearchOperation readOperation =
           conn.processSearch(sre.getName(), SearchScope.BASE_OBJECT, filter);
      assertEquals(readOperation.getResultCode(), ResultCode.SUCCESS,
          "Failed to read " + sre.getName() + " entry. Got error message: " + readOperation.getErrorMessage());
    }
  }

}

