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
public class ImportTests9 extends BackendTests
{
/**
 *  Import data to OpenDS with three --excludeFilters parameters.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests8.testImport8_check2" })
  public void testImport9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9");
    String datafile = integration_test_home + "/backend/data/import.ldif.09";
    String import_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", datafile, "--excludeFilter", "(&(uid=prigden9)(telephonenumber=*))", "--excludeFilter", "(&(uid=prigden9)(l=Sunnyvale))", "--excludeFilter", "(&(uid=brigden9)(roomnumber=*))", "--append"};

    stopOpenDS(dsee_home, port);

    ds_output.redirectOutput(logDir, "ImportTest9.txt");
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

/**
 *  First verification search for the entries that were imported in the last test.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests9.testImport9" })
  public void testImport9_check(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 1");
    String base = "uid=trigden9, ou=People, o=test one, o=import tests, dc=example,dc=com";
    String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};

    ds_output.redirectOutput(logDir, "ImportTest9check1.txt");
    int retCode = LDAPSearch.mainSearch(search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Second verification search for the entries that were imported in the last test.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests9.testImport9_check" })
  public void testImport9_check2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 2");
    String base = "uid=prigden9, ou=People, o=test one, o=import tests, dc=example,dc=com";
    String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};

    ds_output.redirectOutput(logDir, "ImportTest9check2.txt");
    int retCode = LDAPSearch.mainSearch(search_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Third verification search for the entries that were imported in the last test.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTests9.testImport9_check2" })
  public void testImport9_check3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 3");
    String base = "uid=brigden9, ou=People, o=test one, o=import tests, dc=example,dc=com";
    String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};

    ds_output.redirectOutput(logDir, "ImportTest9check3.txt");
    int retCode = LDAPSearch.mainSearch(search_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
