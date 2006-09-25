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
package org.opends.server.integration.security;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Security TLS Tests
    #@TestSuitePurpose          Enable TLS using port 389; perform Bob tests through secure port 389
    #@TestSuiteID               Security TLS Tests
    #@TestSuiteGroup            Security TLS Tests
    #@TestGroup                 Security TLS Tests
    #@TestScript                JKSTLSTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the SSL JKS TLS tests.
 */
@Test
public class JKSTLSTests extends JKSTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 1
    #@TestID                    JKSTLSTest1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Enable startTLS by modifying the entry, 
 *  cn=LDAP Connection Handler,cn=Connection Handlers,cn=config.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSBobTests.testJKSBobTest5" })
  public void testJKSTLSTest1(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 1");
    String datafile = integration_test_home + "/security/data/startup/enable_TLS.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
   
    ds_output.redirectOutput(logDir, "JKSTLSTest1.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 2
    #@TestID                    JKSTLSTest2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Search for entries through the startTLS port.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The startTLS port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest1" })
  public void testJKSTLSTest2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 2");
    String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=example,dc=com", "-s", "base", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "JKSTLSTest2.txt"); 
    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 3
    #@TestID                    JKSTLSTest3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add a jpeg photo to an existing entry through a startTLS connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The startTLS port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest2" })
  public void testJKSTLSTest3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 3");
    String datafile = integration_test_home + "/security/data/add/bin_a1_tls_in.ldif";
    String jks_add_args[] = {"-q", "-a", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest3.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 4
    #@TestID                    JKSTLSTest4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Replace a jpeg photo in an existing entry through a startTLS connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The startTLS port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest3" })
  public void testJKSTLSTest4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 4");
    String datafile = integration_test_home + "/security/data/mod/bin_a1_tls_mod.ldif";
    String jks_mod_args[] = {"-q", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest4.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 5
    #@TestID                    JKSTLSTest5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Delete an existing entry through a startTLS connection.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The startTLS port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest4" })
  public void testJKSTLSTest5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 5");
    String datafile = integration_test_home + "/security/data/del/bin_a1_tls_out.ldif";
    String jks_mod_args[] = {"-q", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest5.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 6
    #@TestID                    JKSTLSTest6
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Disable startTLS by modifying the entry, 
 *  cn=LDAP Connection Handler,cn=Connection Handlers,cn=config.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest5" })
  public void testJKSTLSTest6(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 6");
    String datafile = integration_test_home + "/security/data/shutdown/disable_TLS.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest6.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security TLS Tests
    #@TestName                  JKS TLS Test 7
    #@TestID                    JKSTLSTest7
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 1
*/
/**
 *  Search for entries through the startTLS port which was disabled in the last test.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The startTLS port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSTLSTests.testJKSTLSTest6" })
  public void testJKSTLSTest7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 7");
    String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=example,dc=com", "-s", "base", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "JKSTLSTest7.txt"); 
    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

}
