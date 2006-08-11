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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server;

import static org.testng.Assert.*;
import org.testng.TestListenerAdapter;
import org.testng.ITestResult;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/**
 * This class contains the TestNG tests for the Schema startup.
 */
@Test
public class OpenDSTestListener extends TestListenerAdapter
{
  private int m_counter = 0;

  @Override
  public void onTestFailure(ITestResult tr)
  {
    log("FAIL");
  }

  @Override
  public void onTestSkipped(ITestResult tr)
  {
    log("SKIPPED");
  }

  @Override
  public void onTestSuccess(ITestResult tr)
  {
    log("PASS");
  }


  private void log(String str)
  {
    System.out.print(str + "\n");
  }
}
