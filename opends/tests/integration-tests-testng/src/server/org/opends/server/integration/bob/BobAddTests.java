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
package org.opends.server.integration.bob;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/* 
    Place suite-specific test information here.
    #@TestSuiteName		Bob Add Tests
    #@TestSuitePurpose		Test the add functionality for OpenDS
    #@TestSuiteID		Add Tests
    #@TestSuiteGroup		Bob
    #@TestGroup  		Bob
    #@TestScript  		BobAddTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Bob adds.
*/
@Test
public class BobAddTests extends BobTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 1
    #@TestID    		BobAdd1
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a small jpeg photo to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobStartupTests.testBobStartup1" })
  public void testBobAdd1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 1");
    String datafile = integration_test_home + "/bob/data/add/bin_a1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest1.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 2
    #@TestID    		BobAdd2
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a large jpeg photo to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd1" })
  public void testBobAdd2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 2");
    String datafile = integration_test_home + "/bob/data/add/bin_a2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest2.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 3
    #@TestID    		BobAdd3
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a small jpeg photo.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd2" })
  public void testBobAdd3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 3");
    String datafile = integration_test_home + "/bob/data/add/bin_b1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest3.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 4
    #@TestID    		BobAdd4
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a large jpeg photo.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd3" })
  public void testBobAdd4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 4");
    String datafile = integration_test_home + "/bob/data/add/bin_b2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest4.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 5
    #@TestID    		BobAdd5
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a small jpeg photo to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd4" })
  public void testBobAdd5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 5");
    String datafile = integration_test_home + "/bob/data/add/bin_c1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest5.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 6
    #@TestID    		BobAdd6
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a large jpeg photo to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd5" })
  public void testBobAdd6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 6");
    String datafile = integration_test_home + "/bob/data/add/bin_c2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest6.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 7
    #@TestID    		BobAdd7
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a small jpeg photo.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd6" })
  public void testBobAdd7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 7");
    String datafile = integration_test_home + "/bob/data/add/bin_d1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest7.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 8
    #@TestID    		BobAdd8
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a large jpeg photo.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd7" })
  public void testBobAdd8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 8");
    String datafile = integration_test_home + "/bob/data/add/bin_d2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest8.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 9
    #@TestID    		BobAdd9
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short labeleduri to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd8" })
  public void testBobAdd9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 9");
    String datafile = integration_test_home + "/bob/data/add/ces_a1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest9.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 10
    #@TestID    		BobAdd10
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long labeleduri to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd9" })
  public void testBobAdd10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 10");
    String datafile = integration_test_home + "/bob/data/add/ces_a2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest10.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 11
    #@TestID    		BobAdd11
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a short labeleduri.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd10" })
  public void testBobAdd11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 11");
    String datafile = integration_test_home + "/bob/data/add/ces_b1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest11.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 12
    #@TestID    		BobAdd12
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a long labeleduri.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd11" })
  public void testBobAdd12(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 12");
    String datafile = integration_test_home + "/bob/data/add/ces_b2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest12.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 13
    #@TestID    		BobAdd13
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short labeleduri to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd12" })
  public void testBobAdd13(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 13");
    String datafile = integration_test_home + "/bob/data/add/ces_c1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest13.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 14
    #@TestID    		BobAdd14
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long labeleduri to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd13" })
  public void testBobAdd14(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 14");
    String datafile = integration_test_home + "/bob/data/add/ces_c2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest14.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 15
    #@TestID    		BobAdd15
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a short labeleduri.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd14" })
  public void testBobAdd15(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 15");
    String datafile = integration_test_home + "/bob/data/add/ces_d1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest15.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 16
    #@TestID    		BobAdd16
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a long labeleduri.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd15" })
  public void testBobAdd16(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 16");
    String datafile = integration_test_home + "/bob/data/add/ces_d2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest16.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 17
    #@TestID    		BobAdd17
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short description to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd16" })
  public void testBobAdd17(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 17");
    String datafile = integration_test_home + "/bob/data/add/cis_a1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest17.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 18
    #@TestID    		BobAdd18
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long description to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd17" })
  public void testBobAdd18(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 18");
    String datafile = integration_test_home + "/bob/data/add/cis_a2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest18.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 19
    #@TestID    		BobAdd19
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a short description.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd18" })
  public void testBobAdd19(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 19");
    String datafile = integration_test_home + "/bob/data/add/cis_b1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest19.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 20
    #@TestID    		BobAdd20
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a long description.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd19" })
  public void testBobAdd20(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 20");
    String datafile = integration_test_home + "/bob/data/add/cis_b2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest20.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 21
    #@TestID    		BobAdd21
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short description to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd20" })
  public void testBobAdd21(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 21");
    String datafile = integration_test_home + "/bob/data/add/cis_c1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest21.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 22
    #@TestID    		BobAdd22
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long description to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd21" })
  public void testBobAdd22(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 22");
    String datafile = integration_test_home + "/bob/data/add/cis_c2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest22.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 23
    #@TestID    		BobAdd23
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a short description.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd22" })
  public void testBobAdd23(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 23");
    String datafile = integration_test_home + "/bob/data/add/cis_d1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest23.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 24
    #@TestID    		BobAdd24
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a long description.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd23" })
  public void testBobAdd24(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 24");
    String datafile = integration_test_home + "/bob/data/add/cis_d2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest24.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 25
    #@TestID    		BobAdd25
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short seeAlso to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd24" })
  public void testBobAdd25(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 25");
    String datafile = integration_test_home + "/bob/data/add/dn_a1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest25.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 26
    #@TestID    		BobAdd26
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long seeAlso to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd25" })
  public void testBobAdd26(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 26");
    String datafile = integration_test_home + "/bob/data/add/dn_a2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest26.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 27
    #@TestID    		BobAdd27
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a short seeAlso.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd26" })
  public void testBobAdd27(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 27");
    String datafile = integration_test_home + "/bob/data/add/dn_b1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest27.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 28
    #@TestID    		BobAdd28
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a long seeAlso.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd27" })
  public void testBobAdd28(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 28");
    String datafile = integration_test_home + "/bob/data/add/dn_b2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest28.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 29
    #@TestID    		BobAdd29
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short seeAlso to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd28" })
  public void testBobAdd29(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 29");
    String datafile = integration_test_home + "/bob/data/add/dn_c1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest29.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 30
    #@TestID    		BobAdd30
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long seeAlso to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd29" })
  public void testBobAdd30(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 30");
    String datafile = integration_test_home + "/bob/data/add/dn_c2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest30.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 31
    #@TestID    		BobAdd31
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a short seeAlso.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd30" })
  public void testBobAdd31(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 31");
    String datafile = integration_test_home + "/bob/data/add/dn_d1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest31.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 32
    #@TestID    		BobAdd32
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a long seeAlso.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd31" })
  public void testBobAdd32(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 32");
    String datafile = integration_test_home + "/bob/data/add/dn_d2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest32.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 33
    #@TestID    		BobAdd33
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short pager number to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd32" })
  public void testBobAdd33(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 33");
    String datafile = integration_test_home + "/bob/data/add/tel_a1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest33.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 34
    #@TestID    		BobAdd34
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long pager number to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd33" })
  public void testBobAdd34(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 34");
    String datafile = integration_test_home + "/bob/data/add/tel_a2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest34.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 35
    #@TestID    		BobAdd35
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a short pager number.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd34" })
  public void testBobAdd35(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 35");
    String datafile = integration_test_home + "/bob/data/add/tel_b1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest35.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 36
    #@TestID    		BobAdd36
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a new entry as objectclass=inetorgperson that includes a long pager number.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd35" })
  public void testBobAdd36(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 36");
    String datafile = integration_test_home + "/bob/data/add/tel_b2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest36.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 37
    #@TestID    		BobAdd37
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a short pager number to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd36" })
  public void testBobAdd37(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 37");
    String datafile = integration_test_home + "/bob/data/add/tel_c1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest37.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 38
    #@TestID    		BobAdd38
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a long pager number to each of five existing entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd37" })
  public void testBobAdd38(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 38");
    String datafile = integration_test_home + "/bob/data/add/tel_c2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest38.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 39
    #@TestID    		BobAdd39
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a short pager number.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd38" })
  public void testBobAdd39(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 39");
    String datafile = integration_test_home + "/bob/data/add/tel_d1_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest39.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 40
    #@TestID    		BobAdd40
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add five new entries as objectclass=inetorgperson each of which
 *  includes a long pager number.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd39" })
  public void testBobAdd40(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 40");
    String datafile = integration_test_home + "/bob/data/add/tel_d2_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest40.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 41
    #@TestID    		BobAdd41
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a jpeg photo attribute with no value to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd40" })
  public void testBobAdd41(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 41");
    String datafile = integration_test_home + "/bob/data/add/bin_a3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest41.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 42
    #@TestID    		BobAdd42
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 0
*/
/**
 *  Add a jpeg photo attribute with an ascii value to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd41" })
  public void testBobAdd42(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 42");
    String datafile = integration_test_home + "/bob/data/add/bin_a4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest42.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 43
    #@TestID    		BobAdd43
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a duplicate entry as objectclass=inetorgperson that includes a jpeg
 *  photo with no attribute.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd42" })
  public void testBobAdd43(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 43");
    String datafile = integration_test_home + "/bob/data/add/bin_b3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest43.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 44
    #@TestID    		BobAdd44
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a duplicate entry as objectclass=inetorgperson that includes a jpeg
 *  photo with an ascii value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd43" })
  public void testBobAdd44(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 44");
    String datafile = integration_test_home + "/bob/data/add/bin_b4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest44.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }
/*
  The following two methods result in binary data being echoed to stand error or standard out.
  This behavior is not a bug, but is also not desired. These tests are commented out for now. 
*/
/*
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd44" })
  public void testBobAdd45(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 45");
    String datafile = integration_test_home + "/bob/data/add/bin_c3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest45.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    retCode = LDAPModify.mainModify(bob_args);
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd45" })
  public void testBobAdd46(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 46");
    String datafile = integration_test_home + "/bob/data/add/bin_c4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest46.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    retCode = LDAPModify.mainModify(bob_args);
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }
*/
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 47
    #@TestID    		BobAdd47
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a set of entries where the first entry is a duplicate to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd44" })
  public void testBobAdd47(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 47");
    String datafile = integration_test_home + "/bob/data/add/bin_d3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest47.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 48
    #@TestID    		BobAdd48
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a set of entries where the third  entry is a duplicate to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd47" })
  public void testBobAdd48(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 48");
    String datafile = integration_test_home + "/bob/data/add/bin_d4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest48.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 49
    #@TestID    		BobAdd49
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 21
*/
/**
 *  Add a description with no value to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd48" })
  public void testBobAdd49(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 49");
    String datafile = integration_test_home + "/bob/data/add/cis_a3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest49.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 21;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 50
    #@TestID    		BobAdd50
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 1
*/
/**
 *  Add a description with a binary value to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd49" })
  public void testBobAdd50(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 50");
    String datafile = integration_test_home + "/bob/data/add/cis_a4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest50.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 51
    #@TestID    		BobAdd51
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a new entry with a description that has no value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd50" })
  public void testBobAdd51(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 51");
    String datafile = integration_test_home + "/bob/data/add/cis_b3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest51.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 52
    #@TestID    		BobAdd52
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 1
*/
/**
 *  Add a new entry with a description that has a binary value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd51" })
  public void testBobAdd52(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 52");
    String datafile = integration_test_home + "/bob/data/add/cis_b4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest52.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 53
    #@TestID    		BobAdd53
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 20
*/
/**
 *  Add a duplicate desciption to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd52" })
  public void testBobAdd53(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 53");
    String datafile = integration_test_home + "/bob/data/add/cis_c3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest53.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 54
    #@TestID    		BobAdd54
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 1
*/
/**
 *  Add a set of entries where the fourth entry has a binary value for the description.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd53" })
  public void testBobAdd54(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 54");
    String datafile = integration_test_home + "/bob/data/add/cis_c4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest54.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 55
    #@TestID    		BobAdd55
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a set of duplicate entries where the descriptions have no value.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd54" })
  public void testBobAdd55(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 55");
    String datafile = integration_test_home + "/bob/data/add/cis_d3_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest55.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker		Bob Add Tests
    #@TestName  		Bob Add 56
    #@TestID    		BobAdd56
    #@TestPreamble
    #@TestSteps  		Client calls static method LDAPModify.mainModify()
				with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult		Success if OpenDS returns 68
*/
/**
 *  Add a set of duplicate entries where the descriptions have very long values.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobAddTests.testBobAdd55" })
  public void testBobAdd56(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 56");
    String datafile = integration_test_home + "/bob/data/add/cis_d4_in.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobAddTest56.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

}
