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
 * This class contains the JUnit tests for the Frontend functional tests for compare.
 */
public class FrontendRFC2251_compares extends DirectoryServerAcceptanceTestCase
{
  public String frontend_compare_args[] = {"-h", hostname, "-p", port, " ", " "};

  public FrontendRFC2251_compares(String name)
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

  public void testFrontendUserCompare1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 1");
    frontend_compare_args[4]="l:Cupertino";
    frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserCompare2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 2");
    frontend_compare_args[4]="l:Santa Clara";
    frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserCompare3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 3");
    frontend_compare_args[4]="st:California";
    frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 16;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserCompare4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Compare test 4");
    frontend_compare_args[4]="l:Cupertino";
    frontend_compare_args[5]="uid=badname,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
