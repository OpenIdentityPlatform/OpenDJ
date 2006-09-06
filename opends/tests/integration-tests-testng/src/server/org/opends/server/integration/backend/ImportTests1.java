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
package org.opends.server.integration.backend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/**
 * This class contains the TestNG tests for the Backend functional tests for import
 */
@Test
public class ImportTests1 extends BackendTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackupTasksTests.testBackupTasks1" })
  public void testImport1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1");
    String datafile = integration_test_home + "/backend/data/import.ldif.01";
    String import_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", datafile};

    stopOpenDS(dsee_home, port);

    ds_output.redirectOutput(logDir, "ImportTest1.txt");
    int retCode = ImportLDIF.mainImportLDIF(import_args);
    ds_output.resetOutput();
    int expCode = 0;

    if(retCode == expCode)
    {
      if(startOpenDS(dsee_home, hostname, port, bindDN, bindPW, logDir) != 0)
      {
	retCode = 999;
      }
    }
    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests1.testImport1" })
  public void testImport1_check(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1 check entries 1");
    String base = "uid=scarter, ou=People, o=test one, o=import tests, dc=example,dc=com"; 
    String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};

    ds_output.redirectOutput(logDir, "ImportTest1check1.txt");
    int retCode = LDAPSearch.mainSearch(search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests1.testImport1_check" })
  public void testImport1_check2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1 check entries 2");
    String base = "uid=scarter, ou=People, o=backend tests, dc=example,dc=com"; 
    String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};

    ds_output.redirectOutput(logDir, "ImportTest1check2.txt");
    int retCode = LDAPSearch.mainSearch(search_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
