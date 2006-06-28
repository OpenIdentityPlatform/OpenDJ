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
 * This class contains the JUnit tests for the Frontend functional tests for modifies.
 */
public class FrontendRFC2251_modifies extends DirectoryServerAcceptanceTestCase
{
  public String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", " "};
  public String frontend_compare_args[] = {"-h", hostname, "-p", port, " ", " "};
  public String frontend_datafiledir = acceptance_test_home + "/frontend/data";

  public FrontendRFC2251_modifies(String name)
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

  public void testFrontendUserModify1_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1 precheck");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=scarter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_1.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify1_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 1 check entry");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=scarter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify2_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2 precheck");
    frontend_compare_args[4]="mail:tmorris@example.com";
    frontend_compare_args[5]="uid=tmorris,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_2.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify2_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 2 check entry");
    frontend_compare_args[4]="mail:tmorris@example.com";
    frontend_compare_args[5]="uid=tmorris,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify3_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3 precheck");
    frontend_compare_args[4]="l:Sunnyvale";
    frontend_compare_args[5]="uid=kvaughan,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_3.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify3_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 3 check entry");
    frontend_compare_args[4]="l:Grenoble";
    frontend_compare_args[5]="uid=kvaughan,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify4_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4 precheck");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=kvaughan,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_4.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify4_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 4 check entry");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=kvaughan,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify5_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5 precheck");
    frontend_compare_args[4]="mail:abergin@example.com";
    frontend_compare_args[5]="uid=abergin,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_5.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify5_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 5 check entry");
    frontend_compare_args[4]="mail:abergin@example.com";
    frontend_compare_args[5]="uid=abergin,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify6_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6 precheck");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=dmiller,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_6.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify6_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 6 check entry");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=dmiller,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify7_precheck() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7 precheck");
    frontend_compare_args[4]="uid:dmiller";
    frontend_compare_args[5]="uid=dmiller,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7");
    frontend_mod_args[9]=frontend_datafiledir + "/mod/mod_7.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);
    int expCode = 67;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserModify7_checkentry() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Modify test 7 check entry");
    frontend_compare_args[4]="uid:dmiller";
    frontend_compare_args[5]="uid=dmiller,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
