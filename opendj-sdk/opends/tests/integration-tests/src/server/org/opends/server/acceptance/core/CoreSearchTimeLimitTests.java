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
package org.opends.server.acceptance.core;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Core functional tests for search size limits.
 */
public class CoreSearchTimeLimitTests extends DirectoryServerAcceptanceTestCase
{
  public String core_args[] = {"-l", " ", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=com", "objectclass=*"};
  public String core_args_nolimit[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "o=core tests,dc=com", "objectclass=*"};
  public String core_args_anon[] = {"-h", hostname, "-p", port, "-b", "o=core tests,dc=com", "objectclass=*"};
  public String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String core_datafiledir = acceptance_test_home + "/core/data";

  public CoreSearchTimeLimitTests(String name)
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


  public void testCoreSearchTimeLimit1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 1");
    core_args[1] = "1";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Time Limit test 2");
    core_args[1] = "100";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Reconfigure the server-wide search time limit
  public void testCoreSearchTimeLimit3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 3");
    String datafile = core_datafiledir + "/mod_timelimit.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 4");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

  // Return the server-wide search time limit to default value, 60
  public void testCoreSearchTimeLimit5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 5");
    String datafile = core_datafiledir + "/mod_timelimit2.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 6");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Reconfigure the user cn=Directory manager search time limit
  public void testCoreSearchTimeLimit7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 7");
    String datafile = core_datafiledir + "/mod_timelimit3.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 8");

    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    int expCode = 3;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 9");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Return the user cn=Directory manager search time limit to the deault, -1
  public void testCoreSearchTimeLimit10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 10");
    String datafile = core_datafiledir + "/mod_timelimit4.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchTimeLimit11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core test 11");

    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
