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
 * This class contains the JUnit tests for the Frontend functional tests for binds.
 */
public class FrontendRFC2251_binds extends DirectoryServerAcceptanceTestCase
{
  public String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", " ", "-w", " ", "-b", "ou=People,o=frontend tests,dc=com", "objectclass=*"};
  public String frontend_anon_search_args[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=frontend tests,dc=com", "objectclass=*"};

  public FrontendRFC2251_binds(String name)
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

  public void testFrontendUserBind1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 1");

    int retCode = LDAPSearch.mainSearch(frontend_anon_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserBind2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 2");
    frontend_search_args[5] = "uid=scarter, ou=People, o=frontend tests,dc=com";
    frontend_search_args[7] = "sprain";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserBind3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 3");
    frontend_search_args[5] = "uid=scarter, ou=People, o=frontend tests,dc=com";
    frontend_search_args[7] = "badpwd";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserBind4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Bind test 4");
    frontend_search_args[5] = "uid=scarter, ou=People, o=frontend tests,dc=com";
    frontend_search_args[7] = " ";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

}
