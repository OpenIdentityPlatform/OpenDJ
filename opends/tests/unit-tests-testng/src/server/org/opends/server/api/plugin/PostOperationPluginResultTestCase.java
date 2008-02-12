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
package org.opends.server.api.plugin;



import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.*;



/**
 * A set of test cases for the post-operation plugin result type.
 */
public class PostOperationPluginResultTestCase
       extends PluginAPITestCase
{
  /**
   * Retrieves a set of post-operation plugin result instances.
   *
   * @return  A set of post-operation plugin result instances.
   */
  @DataProvider(name = "instances")
  public Object[][] getInstances()
  {
    return new Object[][]
    {
      new Object[] { PostOperationPluginResult.SUCCESS },
      new Object[] { new PostOperationPluginResult(false, false) },
      new Object[] { new PostOperationPluginResult(true, false) },
      new Object[] { new PostOperationPluginResult(false, true) },
      new Object[] { new PostOperationPluginResult(true, true) },
    };
  }



  /**
   * Tests the <CODE>connectionTerminated</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testConnectionTerminated(PostOperationPluginResult result)
  {
    result.connectionTerminated();
  }



  /**
   * Tests the <CODE>continuePluginProcessing</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testContinuePluginProcessing(PostOperationPluginResult result)
  {
    result.continuePluginProcessing();
  }



  /**
   * Tests the <CODE>toString</CODE> method.
   *
   * @param  result  The result instance to test.
   */
  @Test(dataProvider = "instances")
  public void testToString(PostOperationPluginResult result)
  {
    assertNotNull(result.toString());
  }
}

