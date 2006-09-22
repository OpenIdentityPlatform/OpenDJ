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
    #@TestSuiteName             Frontend RFC2251 Modify RDNs
    #@TestSuitePurpose          Test the RFC2251 standards regarding modify RDNs
    #@TestSuiteID               RFC2251 Modify RDNs
    #@TestSuiteGroup            RFC2251 Modify RDNs
    #@TestGroup                 RFC2251 Modify RDNs
    #@TestScript                FrontendRFC2251_modifyrdns.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Frontend functional tests for modify RDNs.
 */
@Test
public class FrontendRFC2251_modifyrdns extends FrontendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 1
    #@TestID                    FrontendUserModifyRDN1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Modify the RDN for an existing entry and retain the old RDN.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify7_checkentry" })
  public void testFrontendUserModifyRDN1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 1");
    String datafile=integration_test_home + "/frontend/data/modrdn/modrdn_1.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN1.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 1 check entry
    #@TestID                    FrontendUserModifyRDN1_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
                                uid, with value, kwintersmith.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the entry that was modified in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN1" })
  public void testFrontendUserModifyRDN1_check(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 1 check entries");
    String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=kwinterssmith"};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN1_check.txt");
    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 2
    #@TestID                    FrontendUserModifyRDN2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Modify the RDN for an existing entry and delete the old RDN.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN1_check" })
  public void testFrontendUserModifyRDN2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 2");
    String datafile=integration_test_home + "/frontend/data/modrdn/modrdn_2.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN2.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 2 check entry
    #@TestID                    FrontendUserModifyRDN2_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
                                uid, with value, kwintersmith.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the entry that was modified in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN2" })
  public void testFrontendUserModifyRDN2_check(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 2 check entries");
    String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=kwinterssmith"};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN2_check.txt");
    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 3
    #@TestID                    FrontendUserModifyRDN3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 32
*/
/**
 *  Modify the RDN for non-existent entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN2_check" })
  public void testFrontendUserModifyRDN3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 3");
    String datafile=integration_test_home + "/frontend/data/modrdn/modrdn_3.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN3.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modify RDNs
    #@TestName                  Frontend User Modify RDN 4
    #@TestID                    FrontendUserModifyRDN4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 68
*/
/**
 *  Modify the RDN for an existing entry to the RDN of another existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN3" })
  public void testFrontendUserModifyRDN4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 4");
    String datafile=integration_test_home + "/frontend/data/modrdn/modrdn_4.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModifyRDN4.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

}
