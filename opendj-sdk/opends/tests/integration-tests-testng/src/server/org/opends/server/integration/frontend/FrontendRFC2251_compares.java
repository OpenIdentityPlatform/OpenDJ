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
package org.opends.server.integration.frontend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Frontend RFC2251 Compares
    #@TestSuitePurpose          Test the RFC2251 standards regarding compares
    #@TestSuiteID               RFC2251 Compares
    #@TestSuiteGroup            RFC2251 Compares
    #@TestGroup                 RFC2251 Compares
    #@TestScript                FrontendRFC2251_compares.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Frontend functional tests for compare.
 */
@Test
public class FrontendRFC2251_compares extends FrontendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Compares
    #@TestName                  Frontend User Compare 1
    #@TestID                    FrontendUserCompare1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare().
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when a compare request is conducted
 *  for an entry with one of its attributes and the attribute value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_binds.testFrontendUserBind4" })
  public void testFrontendUserCompare1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 1");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "l:Cupertino", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "FrontendUserCompare1.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Compares
    #@TestName                  Frontend User Compare 2
    #@TestID                    FrontendUserCompare2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare().
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when a compare request is conducted
 *  for an entry with one of its attributes and the wrong attribute value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_compares.testFrontendUserCompare1" })
  public void testFrontendUserCompare2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 2");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "l:Santa Clara", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "FrontendUserCompare2.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Compares
    #@TestName                  Frontend User Compare 3
    #@TestID                    FrontendUserCompare3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare().
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check the response of OpenDS when a compare request is conducted
 *  for an entry with a non-existent attribute and the attribute value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_compares.testFrontendUserCompare2" })
  public void testFrontendUserCompare3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 3");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "FrontendUserCompare3.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Compares
    #@TestName                  Frontend User Compare 4
    #@TestID                    FrontendUserCompare4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare().
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 32
*/
/**
 *  Check the response of OpenDS when a compare request is conducted
 *  for a non-existent entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_compares.testFrontendUserCompare3" })
  public void testFrontendUserCompare4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 4");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "l:Cupertino", "uid=badname,ou=People,o=frontend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "FrontendUserCompare4.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
