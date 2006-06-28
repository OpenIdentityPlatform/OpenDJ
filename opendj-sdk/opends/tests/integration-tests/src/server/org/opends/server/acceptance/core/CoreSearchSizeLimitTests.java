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
public class CoreSearchSizeLimitTests extends DirectoryServerAcceptanceTestCase
{
  public String core_args[] = {"-z", " ", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "ou=People,o=core tests,dc=com", "objectclass=*"};
  public String core_args_nolimit[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "ou=People,o=core tests,dc=com", "objectclass=*"};
  public String core_args_anon[] = {"-h", hostname, "-p", port, "-b", "ou=People,o=core tests,dc=com", "objectclass=*"};
  public String core_args_mod[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String core_datafiledir = acceptance_test_home + "/core/data";

  public CoreSearchSizeLimitTests(String name)
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


  public void testCoreSearchSizeLimit1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 1");
    core_args[1] = "150";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 4;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 2");
    core_args[1] = "151";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Reconfigure the server-wide search size limit
  public void testCoreSearchSizeLimit3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 3");
    String datafile = core_datafiledir + "/mod_searchsizelimit.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 4");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 4;

    compareExitCode(retCode, expCode);
  }

  // Increase the server-wide search size limit to 10000
  public void testCoreSearchSizeLimit5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 5");
    String datafile = core_datafiledir + "/mod_searchsizelimit2.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 6");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Reconfigure the user cn=Directory manager search size limit
  public void testCoreSearchSizeLimit7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 7");
    String datafile = core_datafiledir + "/mod_searchsizelimit3.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 8");

    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    int expCode = 4;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 9");

    int retCode = LDAPSearch.mainSearch(core_args_anon);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  // Return the user cn=Directory manager search size limit to the deault, -1
  public void testCoreSearchSizeLimit10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 10");
    String datafile = core_datafiledir + "/mod_searchsizelimit4.ldif";
    core_args_mod[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreSearchSizeLimit11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Search Size Limit test 11");

    int retCode = LDAPSearch.mainSearch(core_args_nolimit);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
