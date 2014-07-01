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
 *      Portions copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.monitors;

import java.util.Iterator;
import java.util.Set;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.types.SearchFilter.*;
import static org.opends.server.types.SearchScope.*;
import static org.testng.Assert.*;

/**
 * Interacts with the Directory Server monitor providers by retrieving the
 * monitor entries with internal searches.
 */
@SuppressWarnings("javadoc")
public class InternalSearchMonitorTestCase
       extends MonitorTestCase
{
  static TestMonitorProvider testMonitorProvider = new TestMonitorProvider();

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
    InternalSearchOperation op = getRootConnection().processSearch(
        "cn=monitor", WHOLE_SUBTREE, "(objectClass=*)");
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
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
    final String monitorDN = "cn="+monitorName+",cn=monitor";
    InternalSearchOperation op = getRootConnection().processSearch(
        monitorDN, BASE_OBJECT, "(objectClass=*)");
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
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
    InternalSearchOperation searchOperation = conn.processSearch(
        "cn=monitor", WHOLE_SUBTREE, "(objectClass=*)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);

    for (SearchResultEntry sre : searchOperation.getSearchEntries())
    {
      final InternalSearchOperation op = conn.processSearch(
          sre.getDN(), BASE_OBJECT, createFilterFromString("(objectClass=*)"));
      assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    }
  }

}
