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
 * This class contains the TestNG tests for the Frontend functional tests for deletes.
 */
@Test
public class FrontendRFC2251_deletes extends FrontendTests
{
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
