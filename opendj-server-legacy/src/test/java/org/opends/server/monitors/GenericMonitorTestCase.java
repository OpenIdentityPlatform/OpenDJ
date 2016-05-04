/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.monitors;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.Entry;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.DN;

import static org.testng.Assert.*;



/**
 * An abstract base class for all monitor test cases in which most tests are
 * performed in a generic manner.
 */
public abstract class GenericMonitorTestCase
       extends MonitorTestCase
{
  private final String configEntryName;
  protected Entry configEntry;

  GenericMonitorTestCase(String dnString)
  {
    configEntryName = dnString;
  }

  @BeforeClass
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();
    if (configEntryName != null)
    {
      configEntry = DirectoryServer.getEntry(DN.valueOf(configEntryName));
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
  protected abstract MonitorProvider getMonitorInstance()
         throws Exception;



  /**
   * Creates an instance of the stack trace monitor and performs basic
   * initialization for it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
  @Test
  public void testGetMonitorInstanceName()
         throws Exception
  {
    getMonitorInstance().getMonitorInstanceName();
  }



  /**
   * Tests the <CODE>getMonitorData</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetMonitorData()
         throws Exception
  {
    getMonitorInstance().getMonitorData();
  }


  /**
   * We need to drop all of the memory that we can.
   */
  @AfterClass
  public void cleanupMem()
  {
    configEntry = null;
  }
}

