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
package org.opends.server.integration.core;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Core True/False Filters
    #@TestSuitePurpose          To check that true/false filters are correctly handled
    #@TestSuiteID               True/False Filters
    #@TestSuiteGroup            Core
    #@TestGroup                 Core
    #@TestScript                CoreTFFFiltersTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Core true/false filter tests. 
 */
@Test
public class CoreTFFiltersTests extends CoreTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Core True/False Filters
    #@TestName                  True/False Filters 1
    #@TestID                    CoreTFFilters1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                for entry, cn=version,cn=monitor, with the
				single ampersand filter.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a single ampersand character.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreVersionReportTests.testCoreVersionReport1" })
  public void testCoreTFFilters1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 1");
    String core_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=version,cn=monitor", "&"};

    ds_output.redirectOutput(logDir, "CoreTFFilters1.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Core True/False Filters
    #@TestName                  True/False Filters 2
    #@TestID                    CoreTFFilters2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                for entry, cn=version,cn=monitor, with the
				single pipe filter.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a single pipe character.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreTFFiltersTests.testCoreTFFilters1" })
  public void testCoreTFFilters2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 2");
    String core_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=version,cn=monitor", "|"};

    ds_output.redirectOutput(logDir, "CoreTFFilters2.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Core True/False Filters
    #@TestName                  True/False Filters 3
    #@TestID                    CoreTFFilters3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                for a non-existent entry with the
				single ampersand filter.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a single ampersand character for a non-existent entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreTFFiltersTests.testCoreTFFilters2" })
  public void testCoreTFFilters3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 3");
    String core_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=bad,cn=monitor", "&"};

    ds_output.redirectOutput(logDir, "CoreTFFilters3.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Core True/False Filters
    #@TestName                  True/False Filters 4
    #@TestID                    CoreTFFilters4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                for a non-existent entry with the
				single pipe filter.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Check the response of OpenDS when an ldap search request is conducted
 *  with a single pipe character for a non-existent entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreTFFiltersTests.testCoreTFFilters3" })
  public void testCoreTFFilters4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 4");
    String core_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=bad,cn=monitor", "|"};

    ds_output.redirectOutput(logDir, "CoreTFFilters4.txt");
    int retCode = LDAPSearch.mainSearch(core_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
