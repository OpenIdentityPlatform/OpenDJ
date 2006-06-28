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
 * This class contains the JUnit tests for the Frontend functional tests for modify RDNs.
 */
public class FrontendRFC2251_modifyrdns extends DirectoryServerAcceptanceTestCase
{
  public String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", " "};
  public String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", " ", " "};

  public String frontend_datafiledir = acceptance_test_home + "/frontend/data";

  public FrontendRFC2251_modifyrdns(String name)
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

  public void testFrontendUserModifyRDN1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 1");
    frontend_mod_args[9]=frontend_datafiledir + "/modrdn/modrdn_1.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModifyRDN1_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 1 check entries");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="uid=kwinterssmith";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModifyRDN2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 2");
    frontend_mod_args[9]=frontend_datafiledir + "/modrdn/modrdn_2.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModifyRDN2_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 2 check entries");
    frontend_anon_search_args[5]="ou=People,o=frontend tests,dc=com";
    frontend_anon_search_args[6]="uid=kwinterssmith";

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModifyRDN3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 3");
    frontend_mod_args[9]=frontend_datafiledir + "/modrdn/modrdn_3.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModifyRDN4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify RDN test 4");
    frontend_mod_args[9]=frontend_datafiledir + "/modrdn/modrdn_4.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

}
