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
package org.opends.server.acceptance.frontend;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Frontend functional tests for searches.
 */
public class FrontendRFC2251_searches extends DirectoryServerAcceptanceTestCase
{
  public String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", " ", " "};
  public String frontend_anon_search_args_1param[] = {"-h", hostname, "-p", port, " ", "-b", " ", " "};
  public String frontend_anon_search_args_2param[] = {"-h", hostname, "-p", port, " ", " ", "-b", " ", " "};

  public FrontendRFC2251_searches(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();
  }

  public void tearDown() throws Exception
  {
    super.tearDown();
  }

  public void testFrontendUserSearch1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 1");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="uid=scarter";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 2");
    frontend_anon_search_args_2param[4]="-s";
    frontend_anon_search_args_2param[5]="base";
    frontend_anon_search_args_2param[7]="uid=scarter,ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args_2param[8]="uid=scarter";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 3");
    frontend_anon_search_args_2param[4]="-s";
    frontend_anon_search_args_2param[5]="sub";
    frontend_anon_search_args_2param[7]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args_2param[8]="uid=scarter";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 4");
    frontend_anon_search_args_1param[4]="-A";
    frontend_anon_search_args_1param[6]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args_1param[7]="uid=scarter";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 5");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(&(uid=scarter)(l=Sunnyvale))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 6");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(&(uid=scarter)(l=Grenoble))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 7");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(|(uid=scarter)(l=Grenoble))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 8");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(|(uid=scarter)(l=Cupertino))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 9");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(&(uid=scarter)(!(l=Cupertino)))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 10");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="uid=sc*r";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserSearch11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Search test 11");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="(&(uid=scarter)(roomnumber<=4700))";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
