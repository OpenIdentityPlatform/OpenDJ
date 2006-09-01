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
 * This class contains the TestNG tests for the Frontend functional tests for syntax.
 */
@Test
public class FrontendRFC2252_syntax extends FrontendTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_standards.testFrontendUserStandard4" })
  public void testFrontendUserSyntax1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Syntax test 1");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=jvedder,ou=People,ou=syntax,o=frontend tests,dc=example,dc=com", "description=Mr. Vedder\\27s house is on the corner."};

    ds_output.redirectOutput(logDir, "FrontendUserSyntax1.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_syntax.testFrontendUserSyntax1" })
  public void testFrontendUserSyntax2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Syntax test 2");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=cnewport,ou=People,ou=syntax,o=frontend tests,dc=example,dc=com", "description=Mr. Newport has an \52address on the corner."};

    ds_output.redirectOutput(logDir, "FrontendUserSyntax2.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_syntax.testFrontendUserSyntax2" })
  public void testFrontendUserSyntax3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Syntax test 3");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=elott,ou=People,ou=syntax,o=frontend tests,dc=example,dc=com", "description=Mr. Lott lives in apartment \\23101 on the corner."};

    ds_output.redirectOutput(logDir, "FrontendUserSyntax3.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
