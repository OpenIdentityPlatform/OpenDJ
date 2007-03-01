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
package org.opends.server.integration.security;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Security Bob Tests
    #@TestSuitePurpose          Perform Bob tests through secure port 636
    #@TestSuiteID               Security Bob Tests
    #@TestSuiteGroup            Security Bob Tests
    #@TestGroup                 Security Bob Tests
    #@TestScript                JKSBobTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the SSL JKS bob tests.
 */
@Test
public class JKSBobTests extends JKSTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Bob Tests
    #@TestName                  JKS Bob Test 1
    #@TestID                    JKSBobTest1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0 
*/
/**
 *  Add the entries by ssl that are needed for all the Security Tests.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup5" })
  public void testJKSBobTest1(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 1");
    String datafile = integration_test_home + "/security/data/jks_startup.ldif";
    String jks_add_args[] = {"-a", "-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest1.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Bob Tests
    #@TestName                  JKS Bob Test 2
    #@TestID                    JKSBobTest2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0 
*/
/**
 *  Add an RDN to an existing entry through an SSL connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest1" })
  public void testJKSBobTest2(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 2");
    String datafile = integration_test_home + "/security/data/modrdn/a1_modrdn.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest2.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Bob Tests
    #@TestName                  JKS Bob Test 3
    #@TestID                    JKSBobTest3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0 
*/
/**
 *  Add a jpeg photo to an existing entry through an SSL connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest2" })
  public void testJKSBobTest3(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 3");
    String datafile = integration_test_home + "/security/data/add/bin_a1_in.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest3.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Bob Tests
    #@TestName                  JKS Bob Test 4
    #@TestID                    JKSBobTest4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0 
*/
/**
 *  Replace a jpeg photo in an existing entry through an SSL connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest3" })
  public void testJKSBobTest4(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 4");
    String datafile = integration_test_home + "/security/data/mod/bin_a1_mod.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest4.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Bob Tests
    #@TestName                  JKS Bob Test 5
    #@TestID                    JKSBobTest5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0 
*/
/**
 *  Delete an existing entry through an SSL connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest4" })
  public void testJKSBobTest5(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 5");
    String datafile = integration_test_home + "/security/data/del/bin_a1_out.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
   
    ds_output.redirectOutput(logDir, "JKSBobTest5.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
