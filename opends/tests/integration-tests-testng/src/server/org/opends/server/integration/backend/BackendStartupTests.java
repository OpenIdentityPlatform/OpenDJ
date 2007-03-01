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
package org.opends.server.integration.backend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Backend Setup
    #@TestSuitePurpose          Setup methods for the Backend test suites
    #@TestSuiteID               Setup
    #@TestSuiteGroup            Backend
    #@TestGroup                 Backend
    #@TestScript                BackendStartupTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Backend startup.
 */
@Test
public class BackendStartupTests extends BackendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Setup
    #@TestName                  Backend Setup 1
    #@TestID                    BackendStartup1
    #@TestPreamble
    #@TestSteps                 Client calls static method ImportLDIF.mainImportLDIF()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if ImportLDIF.mainImportLDIF() returns 0
*/
/**
 *  Add entries that are required for the Backend Tests.
 *
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "dsee_home", "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest5" })
  public void testBackendStartup1(String dsee_home, String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backend Startup test 1");
    String datafile = integration_test_home + "/backend/data/backend_start.ldif";
    String import_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", datafile};

    stopOpenDS(dsee_home, port);

    ds_output.redirectOutput(logDir, "BackendStartup1.txt");
    int retCode = ImportLDIF.mainImportLDIF(import_args);
    ds_output.resetOutput();
    int expCode = 0;

    if(retCode == expCode)
    {
      if(startOpenDS(dsee_home, hostname, port, bindDN, bindPW, logDir) != 0)
      {
	retCode = 999;
      }
    }
    compareExitCode(retCode, expCode);
  }
  
}
