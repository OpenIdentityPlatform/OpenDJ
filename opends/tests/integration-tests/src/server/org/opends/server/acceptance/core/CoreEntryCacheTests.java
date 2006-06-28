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
 * This class contains the JUnit tests for the Core version reporting tests. 
 */
public class CoreEntryCacheTests extends DirectoryServerAcceptanceTestCase
{
  public String core_args_search[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=version,cn=monitor", " "};
  public String core_args_mod[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String core_args_add[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String core_entrycachetests_datafiledir = acceptance_test_home + "/core/data";

  public CoreEntryCacheTests(String name)
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


  public void testCoreEntryCache1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 1");
    core_args_search[10] = "(|)";

    int retCode = LDAPSearch.mainSearch(core_args_search);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreEntryCache2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 2");
    String datafile = core_entrycachetests_datafiledir + "/mod_entrycache2.ldif";
    core_args_mod[9] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreEntryCache3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 3");
    String datafile = core_entrycachetests_datafiledir + "/mod_entrycache3.ldif";
    core_args_mod[9] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreEntryCache4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 4");
    String datafile = core_entrycachetests_datafiledir + "/mod_entrycache4.ldif";
    core_args_mod[9] = datafile;

    int retCode = LDAPModify.mainModify(core_args_mod);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testCoreEntryCache5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 5");
    String datafile = core_entrycachetests_datafiledir + "/mod_entrycache5.ldif";
    core_args_add[10] = datafile;

    int retCode = LDAPModify.mainModify(core_args_add);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreEntryCache6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 6");
    core_args_search[9] = "ou=People,o=core tests,dc=com";
    core_args_search[10] = "(objectclass=*)";

    int retCode = LDAPSearch.mainSearch(core_args_search);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
