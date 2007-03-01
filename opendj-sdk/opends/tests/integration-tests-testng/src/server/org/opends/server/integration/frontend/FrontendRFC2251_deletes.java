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
package org.opends.server.integration.frontend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Frontend RFC2251 Deletes
    #@TestSuitePurpose          Test the RFC2251 standards regarding deletes
    #@TestSuiteID               RFC2251 Deletes
    #@TestSuiteGroup            RFC2251 Deletes
    #@TestGroup                 RFC2251 Deletes
    #@TestScript                FrontendRFC2251_deletes.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Frontend functional tests for deletes.
 */
@Test
public class FrontendRFC2251_deletes extends FrontendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 1 precheck
    #@TestID                    FrontendUserDelete1_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=jvedder,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check for the existence of the entry which will be deleted in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch11" })
  public void testFrontendUserDelete1_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1 precheck");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete1_precheck.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 1 
    #@TestID                    FrontendUserDelete1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPDelete.mainDelete()
				with base DN, uid=jvedder,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when a delete request is conducted
 *  for an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete1_precheck" })
  public void testFrontendUserDelete1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1");
    String frontend_del_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com"};
  
    ds_output.redirectOutput(logDir, "FrontendUserDelete1.txt");
    int retCode = LDAPDelete.mainDelete(frontend_del_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 1 check entry
    #@TestID                    FrontendUserDelete1_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=jvedder,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 32
*/
/**
 *  Check for the existence of the entry which was deleted in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete1" })
  public void testFrontendUserDelete1_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1 check entry");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete1_checkentry.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 2 precheck
    #@TestID                    FrontendUserDelete2_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=mtyler,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check for the existence of the entry as a precheck for the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete1_checkentry" })
  public void testFrontendUserDelete2_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2 precheck");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete2_precheck.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 2 
    #@TestID                    FrontendUserDelete2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPDelete.mainDelete()
				with base DN, uid=mtyler,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 66
*/
/**
 *  Check the response of OpenDS when a delete request is conducted
 *  for  branch.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete2_precheck" })
  public void testFrontendUserDelete2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2");
    String frontend_del_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "ou=People,ou=deletes,o=frontend tests,dc=example,dc=com"};
  
    ds_output.redirectOutput(logDir, "FrontendUserDelete2.txt");
    int retCode = LDAPDelete.mainDelete(frontend_del_args);
    ds_output.resetOutput();
    int expCode = 66;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 2 check entry
    #@TestID                    FrontendUserDelete2_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=mtyler,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check for the existence of the entry that should still be present
 *  after the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete2" })
  public void testFrontendUserDelete2_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2 check entry");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete2_checkentry.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 3 precheck
    #@TestID                    FrontendUserDelete3_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=mtyler,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check for the existence of the entry as a precheck for the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete2_checkentry" })
  public void testFrontendUserDelete3_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3 precheck");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete3_precheck.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 3 
    #@TestID                    FrontendUserDelete3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPDelete.mainDelete()
				with the parameter, -x, on ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when a delete request is conducted
 *  on a branck using the "-x" parameter.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete3_precheck" })
  public void testFrontendUserDelete3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3");
    String frontend_del_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-x", "ou=People,ou=deletes,o=frontend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete3.txt");
    int retCode = LDAPDelete.mainDelete(frontend_del_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Deletes
    #@TestName                  Frontend User Delete 3 check entry
    #@TestID                    FrontendUserDelete3_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
				with base DN, uid=mtyler,ou=People,ou=deletes,
				o=frontend tests,dc=example,dc=com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 32
*/
/**
 *  Check for the existence of the entry that should have been deleted
 *  after the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete3" })
  public void testFrontendUserDelete3_checkentries(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3 check entries");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=example,dc=com", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "FrontendUserDelete3_checkentry.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
