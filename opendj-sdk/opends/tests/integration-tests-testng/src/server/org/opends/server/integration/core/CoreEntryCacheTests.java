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
package org.opends.server.integration.core;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/**
 * This class contains the TestNG tests for the Core version reporting tests. 
 */
@Test
public class CoreEntryCacheTests extends CoreTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreTFFiltersTests.testCoreTFFilters4" })
  public void testCoreEntryCache1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 1");
    String core_args_search[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=version,cn=monitor", "|"};
  
    ds_output.redirectOutput(logDir, "CoreEntryCache1.txt");
    int retCode = LDAPSearch.mainSearch(core_args_search);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache1" })
  public void testCoreEntryCache2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 2");
    String datafile = integration_test_home + "/core/data/mod_entrycache2.ldif";
    String core_args_mod[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreEntryCache2.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache2" })
  public void testCoreEntryCache3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 3");
    String datafile = integration_test_home + "/core/data/mod_entrycache3.ldif";
    String core_args_mod[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
 
    ds_output.redirectOutput(logDir, "CoreEntryCache3.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache3" })
  public void testCoreEntryCache4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 4");
    String datafile = integration_test_home + "/core/data/mod_entrycache4.ldif";
    String core_args_mod[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreEntryCache4.txt");
    int retCode = LDAPModify.mainModify(core_args_mod);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache4" })
  public void testCoreEntryCache5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 5");
    String datafile = integration_test_home + "/core/data/mod_entrycache5.ldif";
    String core_args_add[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
  
    ds_output.redirectOutput(logDir, "CoreEntryCache5.txt");
    int retCode = LDAPModify.mainModify(core_args_add);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache5" })
  public void testCoreEntryCache6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Entry Cache test 6");
    String core_args_search[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "ou=People,o=core tests,dc=example,dc=com", "(objectclass=*)"};
  
    ds_output.redirectOutput(logDir, "CoreEntryCache6.txt");
    int retCode = LDAPSearch.mainSearch(core_args_search);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
