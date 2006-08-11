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
 * This class contains the TestNG tests for the Frontend functional tests for searches.
 */
@Test
public class FrontendRFC2251_searches extends FrontendTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_modifyrdns.testFrontendUserModifyRDN4" })
  public void testFrontendUserSearch1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 1");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=scarter"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch1.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch1" })
  public void testFrontendUserSearch2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 2");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-s", "base", "-b", "uid=scarter,ou=People,o=frontend tests,dc=example,dc=com", "uid=scarter"};

    ds_output.redirectOutput(logDir, "FrontendUserSearch2.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch2" })
  public void testFrontendUserSearch3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 3");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-s", "sub", "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=scarter"};

    ds_output.redirectOutput(logDir, "FrontendUserSearch3.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch3" })
  public void testFrontendUserSearch4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 4");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-A", "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=scarter"};

    ds_output.redirectOutput(logDir, "FrontendUserSearch4.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch4" })
  public void testFrontendUserSearch5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 5");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(&(uid=scarter)(l=Sunnyvale))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch5.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch5" })
  public void testFrontendUserSearch6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 6");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(&(uid=scarter)(l=Grenoble))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch6.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch6" })
  public void testFrontendUserSearch7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 7");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(|(uid=scarter)(l=Grenoble))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch7.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch7" })
  public void testFrontendUserSearch8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 8");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(|(uid=scarter)(l=Cupertino))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch8.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch8" })
  public void testFrontendUserSearch9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 9");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(&(uid=scarter)(!(l=Cupertino)))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch9.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch9" })
  public void testFrontendUserSearch10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 10");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "uid=sc*r"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch10.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_searches.testFrontendUserSearch10" })
  public void testFrontendUserSearch11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 11");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=example,dc=com", "(&(uid=scarter)(roomnumber<=4700))"};
  
    ds_output.redirectOutput(logDir, "FrontendUserSearch11.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
