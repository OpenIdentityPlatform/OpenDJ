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

import java.util.Iterator;
import java.util.Set;

import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DN;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * Interacts with the Directory Server monitor providers by retrieving the
 * monitor entries with internal searches.
 */
@SuppressWarnings("javadoc")
public class InternalSearchMonitorTestCase
       extends MonitorTestCase
{
  private static TestMonitorProvider testMonitorProvider = new TestMonitorProvider();

  /**
   * Ensures that the Directory Server is started.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    DirectoryServer.registerMonitorProvider(testMonitorProvider);
  }

  @AfterClass
  public void deregisterTestMonitor()
  {
    DirectoryServer.deregisterMonitorProvider(testMonitorProvider);
  }

  /**
   * Uses an internal subtree search to retrieve the monitor entries.
   */
  @Test
  public void testWithSubtreeMonitorSearch() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf("cn=monitor"), WHOLE_SUBTREE);
    InternalSearchOperation op = getRootConnection().processSearch(request);
    assertEquals(op.getResultCode(), ResultCode.SUCCESS,
        "Failed to search cn=monitor subtree. Got error message: " + op.getErrorMessage());
  }



  /**
   * Retrieves the names of the monitor providers registered with the server.
   *
   * @return  The names of the monitor providers registered with the server.
   */
  @DataProvider(name = "monitorNames")
  public Object[][] getMonitorNames()
  {
    Set<String> monitorNames = DirectoryServer.getMonitorProviders().keySet();
    Iterator<String> it = monitorNames.iterator();

    Object[][] nameArray = new Object[monitorNames.size()][1];
    for (int i=0; i < nameArray.length; i++)
    {
      nameArray[i] = new Object[] { it.next() };
    }
    return nameArray;
  }



  /**
   * Uses a set of internal base-level searches to retrieve the monitor entries.
   *
   * @param  monitorName  The name of the monitor entry to retrieve.
   */
  @Test(dataProvider = "monitorNames")
  public void testWithBaseObjectMonitorSearch(String monitorName) throws Exception
  {
    // could be more than one level
    final DN monitorDN = DN.valueOf("cn=" + monitorName + ",cn=monitor");
    InternalSearchOperation op = getRootConnection().processSearch(newSearchRequest(monitorDN, BASE_OBJECT));
    assertEquals(op.getResultCode(), ResultCode.SUCCESS,
        "Failed to read " + monitorDN + " entry. Got error message: " + op.getErrorMessage());
  }

  /**
   * Uses an internal subtree search to retrieve the monitor entries, then
   * verifies that the resulting entry DNs can be used to get the same
   * entries with a base object search.
   */
  @Test
  public void testWithSubtreeAndBaseMonitorSearch() throws Exception
  {
    final InternalClientConnection conn = getRootConnection();
    final SearchRequest request = newSearchRequest(DN.valueOf("cn=monitor"), WHOLE_SUBTREE);
    InternalSearchOperation op = getRootConnection().processSearch(request);
    assertEquals(op.getResultCode(), ResultCode.SUCCESS,
        "Failed to search cn=monitor subtree. Got error message: " + op.getErrorMessage());

    for (SearchResultEntry sre : op.getSearchEntries())
    {
      final SearchRequest request2 = newSearchRequest(sre.getName(), BASE_OBJECT);
      final InternalSearchOperation op2 = conn.processSearch(request2);
      assertEquals(op2.getResultCode(), ResultCode.SUCCESS,
          "Failed to read " + sre.getName() + " entry. Got error message: " + op2.getErrorMessage());
    }
  }

}
