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
package org.opends.server.integration.core;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Search Time Limit
    #@TestSuitePurpose          To check that the search time limit is enforced.
    #@TestSuiteID               Search Time Limit
    #@TestSuiteGroup            Core
    #@TestGroup                 Core
    #@TestScript                CoreSearchTimeLimitTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Core functional tests for search size limits.
 */
@Test
public class CoreSearchTimeLimitTests extends CoreTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 1
    #@TestID                    CoreSearchTimeLimit1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (1) against 1150 entries.
                                The client binds as cn=Directory Manager.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 3
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a search time limit defined in the command line and the search time is exceeeded.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreStartupTests.testCoreStartup2" })
  public void testCoreSearchTimeLimit1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 1");
    String core_args[] = {"-l", "1", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit1.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 2
    #@TestID                    CoreSearchTimeLimit2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (100) against 1150 entries.
                                The client binds as cn=Directory Manager.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a search time limit defined in the command line and the search time 
 *  is not exceeeded.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit1" })
  public void testCoreSearchTimeLimit2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 2");
    String core_args[] = {"-l", "100", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
 
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit2.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 3
    #@TestID                    CoreSearchTimeLimit3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filenename to the appropriate ldif file.
                                The ds-cfg-time-limit parameter is changed to
                                1 under cn=config.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Change the server-wide search time limit to 1.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit2" })
  public void testCoreSearchTimeLimit3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 3");
    String datafile = integration_test_home + "/core/data/mod_timelimit.ldif";
    String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit3.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 4
    #@TestID                    CoreSearchTimeLimit4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (1) against 1150 entries.
                                The client binds as anonymous.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 3
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a search time limit defined by the server-wide parameter,
 *  ds-config-time-limit, and the search time limit is exceeded.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit3" })
  public void testCoreSearchTimeLimit4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 4");
    String core_args_anon[] = {"-h", hostname, "-p", port, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit4.txt");
    int retCode = LDAPSearch.mainSearch(core_args_anon);
    ds_output.resetOutput();
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 5
    #@TestID                    CoreSearchTimeLimit5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filenename to the appropriate ldif file.
                                The ds-cfg-time-limit parameter is changed to
                                60 under cn=config.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Change the server-wide search time limit to 60.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit4" })
  public void testCoreSearchTimeLimit5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 5");
    String datafile = integration_test_home + "/core/data/mod_timelimit2.ldif";
    String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit5.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 6
    #@TestID                    CoreSearchTimeLimit6
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (60) against 1150 entries.
                                The client binds as anonymous.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a search time limit defined by the server-wide parameter,
 *  ds-config-time-limit, and the search time limit is not exceeded.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit5" })
  public void testCoreSearchTimeLimit6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 6");
    String core_args_anon[] = {"-h", hostname, "-p", port, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit6.txt");
    int retCode = LDAPSearch.mainSearch(core_args_anon);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 7
    #@TestID                    CoreSearchTimeLimit7
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filenename to the appropriate ldif file.
                                The ds-rlim-time-limit parameter is changed to
                                1 under cn=Directory Manager,cn=Root,cn=config.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Change the user search time limit for cn=Directory Manager to 1.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit6" })
  public void testCoreSearchTimeLimit7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 7");
    String datafile = integration_test_home + "/core/data/mod_timelimit3.ldif";
    String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit7.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 8
    #@TestID                    CoreSearchTimeLimit8
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (1) for user,
				cn=Directory Manager, against 1150 entries.
                                The client binds as cn=Directory Manager.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 3
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a user search time limit defined by the user-specific parameter,
 *  ds-rlim-time-limit, and the search time limit is exceeded.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit7" })
  public void testCoreSearchTimeLimit8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 8");
    String core_args_nolimit[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit8.txt");
    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    ds_output.resetOutput();
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 9
    #@TestID                    CoreSearchTimeLimit9
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (1) for user,
				cn=Directory Manager, against 1150 entries.
                                The client binds as anonymous.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a user search time limit defined by the user-specific parameter,
 *  ds-rlim-time-limit, and the search is conducted as a different user.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit8" })
  public void testCoreSearchTimeLimit9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 9");
    String core_args_anon[] = {"-h", hostname, "-p", port, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit9.txt");
    int retCode = LDAPSearch.mainSearch(core_args_anon);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 10
    #@TestID                    CoreSearchTimeLimit10
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filenename to the appropriate ldif file.
                                The ds-rlim-time-limit parameter is changed to
                                60 under cn=Directory Manager,cn=Root,cn=config.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Change the user search time limit for cn=Directory Manager to 60.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit9" })
  public void testCoreSearchTimeLimit10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 10");
    String datafile = integration_test_home + "/core/data/mod_timelimit4.ldif";
    String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit10.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Search Time Limit
    #@TestName                  Search Time Limit 11
    #@TestID                    CoreSearchTimeLimit11
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with time limit (60) for user,
				cn=Directory Manager, against 1150 entries.
                                The client binds as cn=Directory Manager.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a user search time limit defined by the user-specific parameter,
 *  ds-rlim-time-limit, equal to 60 and the search is conducted against 1150 entries.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreSearchTimeLimitTests.testCoreSearchTimeLimit10" })
  public void testCoreSearchTimeLimit11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 11");
    String core_args_nolimit[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=example,dc=com", "objectclass=*"};
  
    ds_output.redirectOutput(logDir, "CoreSearchTimeLimit11.txt");
    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
