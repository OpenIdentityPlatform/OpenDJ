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
package org.opends.server.monitors;



import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



/**
 * An abstract base class for all monitor test cases in which most tests are
 * performed in a generic manner.
 */
public abstract class GenericMonitorTestCase
       extends MonitorTestCase
{
  // The configuration entry for this test case.
  protected ConfigEntry configEntry;



  /**
   * Creates a new instance of this monitor test case.
   *
   * @param  dnString  The DN of the configuration entry for this test case, or
   *                   <CODE>null</CODE> if there is none.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  protected GenericMonitorTestCase(String dnString)
            throws Exception
  {
    super();

    TestCaseUtils.startServer();

    if (dnString != null)
    {
      DN dn = DN.decode(dnString);
      configEntry = DirectoryServer.getConfigEntry(dn);
      assertNotNull(configEntry);
    }
  }



  /**
   * Retrieves an initialized instance of the associated monitor provider.
   *
   * @return  An initialized instance of the associated monitor provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public abstract MonitorProvider getMonitorInstance()
         throws Exception;



  /**
   * Creates an instance of the stack trace monitor and performs basic
   * initialization for it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInitialization()
         throws Exception
  {
    MonitorProvider monitorProvider = getMonitorInstance();
    assertNotNull(monitorProvider);
  }



  /**
   * Tests the <CODE>getMonitorInstanceName</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMonitorInstanceName()
         throws Exception
  {
    getMonitorInstance().getMonitorInstanceName();
  }



  /**
   * Tests the <CODE>getUpdateInterval</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetUpdateInterval()
         throws Exception
  {
    getMonitorInstance().getUpdateInterval();
  }



  /**
   * Tests the <CODE>getUpdateMonitorData</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUpdateMonitorData()
         throws Exception
  {
    getMonitorInstance().updateMonitorData();
  }



  /**
   * Tests the <CODE>getMonitorData</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMonitorData()
         throws Exception
  {
    getMonitorInstance().getMonitorData();
  }
}

