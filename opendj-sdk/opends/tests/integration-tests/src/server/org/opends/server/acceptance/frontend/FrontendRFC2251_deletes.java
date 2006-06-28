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
 * This class contains the JUnit tests for the Frontend functional tests for deletes.
 */
public class FrontendRFC2251_deletes extends DirectoryServerAcceptanceTestCase
{
  public String frontend_del_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", " "};
  public String frontend_del_args_1param[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", " ", " "};
  public String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", " ", "(objectclass=*)"};

  public FrontendRFC2251_deletes(String name)
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

  public void testFrontendUserDelete1_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1 precheck");
    frontend_search_args[9]="uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1");
    frontend_del_args[8]="uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPDelete.mainDelete(frontend_del_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete1_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 1 check entry");
    frontend_search_args[9]="uid=jvedder,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete2_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2 precheck");
    frontend_search_args[9]="uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2");
    frontend_del_args[8]="ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPDelete.mainDelete(frontend_del_args);
    int expCode = 66;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete2_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 2 check entry");
    frontend_search_args[9]="uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete3_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3 precheck");
    frontend_search_args[9]="uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3");
    frontend_del_args_1param[8]="-x";
    frontend_del_args_1param[9]="ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPDelete.mainDelete(frontend_del_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserDelete3_checkentries() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Delete test 3 check entries");
    frontend_search_args[9]="uid=mtyler,ou=People,ou=deletes,o=frontend tests,dc=com";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
