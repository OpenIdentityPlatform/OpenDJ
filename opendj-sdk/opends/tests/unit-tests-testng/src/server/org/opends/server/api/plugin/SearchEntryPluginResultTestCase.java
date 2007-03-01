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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api.plugin;



import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.*;



/**
 * A set of test cases for the search entry plugin result type.
 */
public class SearchEntryPluginResultTestCase
       extends PluginAPITestCase
{
  /**
   * Retrieves a set of search entry plugin result instances.
   *
   * @return  A set of search entry plugin result instances.
   */
  @DataProvider(name = "instances")
  public Object[][] getInstances()
  {
    return new Object[][]
    {
      new Object[] { SearchEntryPluginResult.SUCCESS },
      new Object[] { new SearchEntryPluginResult(false, false, false, false) },
      new Object[] { new SearchEntryPluginResult(true, false, false, false) },
      new Object[] { new SearchEntryPluginResult(false, true, false, false) },
      new Object[] { new SearchEntryPluginResult(false, false, true, false) },
      new Object[] { new SearchEntryPluginResult(false, false, false, true) },
      new Object[] { new SearchEntryPluginResult(true, true, false, false) },
      new Object[] { new SearchEntryPluginResult(true, false, true, false) },
      new Object[] { new SearchEntryPluginResult(true, false, false, true) },
      new Object[] { new SearchEntryPluginResult(false, true, true, false) },
      new Object[] { new SearchEntryPluginResult(false, true, false, true) },
      new Object[] { new SearchEntryPluginResult(false, false, true, true) },
      new Object[] { new SearchEntryPluginResult(true, true, true, false) },
      new Object[] { new SearchEntryPluginResult(true, true, false, true) },
      new Object[] { new SearchEntryPluginResult(true, false, true, true) },
      new Object[] { new SearchEntryPluginResult(false, true, true, true) },
      new Object[] { new SearchEntryPluginResult(true, true, true, true) }
    };
  }



  /**
   * Tests the <CODE>connectionTerminated</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testConnectionTerminated(SearchEntryPluginResult result)
  {
    result.connectionTerminated();
  }



  /**
   * Tests the <CODE>continuePluginProcessing</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testContinuePluginProcessing(SearchEntryPluginResult result)
  {
    result.continuePluginProcessing();
  }



  /**
   * Tests the <CODE>sendEntry</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testSendEntry(SearchEntryPluginResult result)
  {
    result.sendEntry();
  }



  /**
   * Tests the <CODE>continueSearch</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testContinueSearch(SearchEntryPluginResult result)
  {
    result.continueSearch();
  }




  /**
   * Tests the <CODE>toString</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testToString(SearchEntryPluginResult result)
  {
    assertNotNull(result.toString());
  }
}

