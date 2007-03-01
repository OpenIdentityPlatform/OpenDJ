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
    #@TestSuiteName             Frontend RFC2251 Modifies
    #@TestSuitePurpose          Test the RFC2251 standards regarding modifies
    #@TestSuiteID               RFC2251 Modifies
    #@TestSuiteGroup            RFC2251 Modifies
    #@TestGroup                 RFC2251 Modifies
    #@TestScript                FrontendRFC2251_modifies.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Frontend functional tests for modifies.
 */
@Test
public class FrontendRFC2251_modifies extends FrontendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 1 precheck
    #@TestID                    FrontendUserModify1_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=scarter,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, Calfornia.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check an entry for a non-existent attribute which will be added 
 *  in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_compares.testFrontendUserCompare4" })
  public void testFrontendUserModify1_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=scarter,ou=People,o=frontend tests,dc=example,dc=com"};
     
    ds_output.redirectOutput(logDir, "FrontendUserModify1_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 1
    #@TestID                    FrontendUserModify1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an attribute to an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify1_precheck" })
  public void testFrontendUserModify1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1");
    String datafile = integration_test_home + "/frontend/data/mod/mod_1.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify1.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 1 check entry
    #@TestID                    FrontendUserModify1_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=scarter,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, Calfornia.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for  the attribute that was added in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify1" })
  public void testFrontendUserModify1_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=scarter,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify1_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 2 precheck
    #@TestID                    FrontendUserModify2_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=tmorris,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				mail, with value, tmorris@example.com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for an existing attribute which will be deleted 
 *  in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify1_checkentry" })
  public void testFrontendUserModify2_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "mail:tmorris@example.com", "uid=tmorris,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify2_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 2
    #@TestID                    FrontendUserModify2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Delete an attribute from an existing entry.  
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify2_precheck" })
  public void testFrontendUserModify2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2");
    String datafile = integration_test_home + "/frontend/data/mod/mod_2.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify2.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 2 check entry
    #@TestID                    FrontendUserModify2_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=tmorris,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				mail, with value, tmorris@example.com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check an entry for an attribute that was deleted in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify2" })
  public void testFrontendUserModify2_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "mail:tmorris@example.com", "uid=tmorris,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify2_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 3 precheck
    #@TestID                    FrontendUserModify3_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=kvaughn,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				l, with value, Sunnyvale.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for an existing attribute which will be replaced 
 *  in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify2_checkentry" })
  public void testFrontendUserModify3_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "l:Sunnyvale", "uid=kvaughan,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify3_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 3
    #@TestID                    FrontendUserModify3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Replace an attribute in an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify3_precheck" })
  public void testFrontendUserModify3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3");
    String datafile = integration_test_home + "/frontend/data/mod/mod_3.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify3.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 3 check entry
    #@TestID                    FrontendUserModify3_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=kvaughn,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				l, with value, Grenoble.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for an existing attribute which was replaced 
 *  in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify3" })
  public void testFrontendUserModify3_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "l:Grenoble", "uid=kvaughan,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify3_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 4 precheck
    #@TestID                    FrontendUserModify4_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=kvaughn,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, California.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check an entry for a non-existent attribute which will be added 
 *  in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify3_checkentry" })
  public void testFrontendUserModify4_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=kvaughan,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify4_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 4
    #@TestID                    FrontendUserModify4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an attribute to an existing entry by a replace operation.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify4_precheck" })
  public void testFrontendUserModify4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4");
    String datafile = integration_test_home + "/frontend/data/mod/mod_4.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify4.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 4 check entry
    #@TestID                    FrontendUserModify4_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=kvaughn,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, California.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for the attribute which was added 
 *  in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify4" })
  public void testFrontendUserModify4_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=kvaughan,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify4_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 5 precheck
    #@TestID                    FrontendUserModify5_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=abergin,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				mail, with value, abergin@example.com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for an existing attribute which will be deleted 
 *  in the next test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify4_checkentry" })
  public void testFrontendUserModify5_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "mail:abergin@example.com", "uid=abergin,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify5_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 5
    #@TestID                    FrontendUserModify5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Delete an attribute from an existing entry by a replace operation.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify5_precheck" })
  public void testFrontendUserModify5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5");
    String datafile = integration_test_home + "/frontend/data/mod/mod_5.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify5.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 5 check entry
    #@TestID                    FrontendUserModify5_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=abergin,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				mail, with value, abergin@example.com.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check an entry for an attribute which was deleted 
 *  in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify5" })
  public void testFrontendUserModify5_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "mail:abergin@example.com", "uid=abergin,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify5_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 6 precheck
    #@TestID                    FrontendUserModify6_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=dmiller,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, California.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check an entry for a non-existent attribute.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify5_checkentry" })
  public void testFrontendUserModify6_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=dmiller,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify6_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 6
    #@TestID                    FrontendUserModify6
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Modify a non-existent attribute in an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify6_precheck" })
  public void testFrontendUserModify6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6");
    String datafile = integration_test_home + "/frontend/data/mod/mod_6.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify6.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 6 check entry
    #@TestID                    FrontendUserModify6_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=dmiller,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				st, with value, California.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 16
*/
/**
 *  Check the entry was modified in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify6" })
  public void testFrontendUserModify6_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "st:California", "uid=dmiller,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify6_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 7 precheck
    #@TestID                    FrontendUserModify7_precheck
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=dmiller,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				uid, with value, dmiller.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check an entry for a required attribute.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify6_checkentry" })
  public void testFrontendUserModify7_precheck(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7 precheck");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "uid:dmiller", "uid=dmiller,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify7_precheck.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 7
    #@TestID                    FrontendUserModify7
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Delete a required attribute in an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify7_precheck" })
  public void testFrontendUserModify7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7");
    String datafile = integration_test_home + "/frontend/data/mod/mod_7.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserModify7.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();
    int expCode = 67;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2251 Modifies
    #@TestName                  Frontend User Modify 7 check entry
    #@TestID                    FrontendUserModify7_checkentry
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPCompare.mainCompare()
                                with base DN, uid=dmiller,ou=People,
                                o=frontend tests,dc=example,dc=com and attribute,
				uid, with value, dmiller.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the entry was modified in the last test.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifies.testFrontendUserModify7" })
  public void testFrontendUserModify7_checkentry(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7 check entry");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "uid:dmiller", "uid=dmiller,ou=People,o=frontend tests,dc=example,dc=com"};
    
    ds_output.redirectOutput(logDir, "FrontendUserModify7_checkentry.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
