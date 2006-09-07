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

/**
 * This class contains the TestNG tests for the Frontend functional tests for binds.
 */
@Test
public class FrontendRFC2251_binds extends FrontendTests
{
/**
 *  Check the response of OpenDS when a search request is conducted
 *  with an anonymous bind.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendStartupTests.testFrontendStartup1" })
  public void testFrontendUserBind1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 1");
    String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserBind1.txt");
    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Check the response of OpenDS when a search request is conducted
 *  as one of the users.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_binds.testFrontendUserBind1" })
  public void testFrontendUserBind2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 2");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "uid=scarter, ou=People, o=frontend tests,dc=example,dc=com", "-w", "sprain", "-b", "ou=People,o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserBind2.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Check the response of OpenDS when a search request is conducted
 *  when binding as one of the users but with a bad password.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_binds.testFrontendUserBind2" })
  public void testFrontendUserBind3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 3");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "uid=scarter, ou=People, o=frontend tests,dc=example,dc=com", "-w", "badpwd", "-b", "ou=People,o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserBind3.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/**
 *  Check the response of OpenDS when a search request is conducted
 *  when binding as one of the users but with a null password.
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
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_binds.testFrontendUserBind3" })
  public void testFrontendUserBind4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 4");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "uid=scarter, ou=People, o=frontend tests,dc=example,dc=com", "-w", " ", "-b", "ou=People,o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserBind4.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

}
