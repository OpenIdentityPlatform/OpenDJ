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
package org.opends.server.integration.bob;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Bob Setup
    #@TestSuitePurpose          Setup methods for the Bob test suites
    #@TestSuiteID               Setup 
    #@TestSuiteGroup            Bob
    #@TestGroup                 Bob/Setup
    #@TestScript                BobStartup.java
    #@TestHTMLLink              blahblah
*/
/**
 * This class contains the TestNG tests for the Bob startup.
 */
@Test
public class BobStartupTests extends BobTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Bob Setup
    #@TestName                  Bob Setup
    #@TestID                    BobStartup1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add entries that are required for the Bob Tests.
 *
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
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.quickstart.QuickstartAddTests.testQuickstartAdd2" })
  public void testBobStartup1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Startup test 1");

    String datafile = integration_test_home + "/bob/data/setup/bob_start.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "BobStartup1.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
}
