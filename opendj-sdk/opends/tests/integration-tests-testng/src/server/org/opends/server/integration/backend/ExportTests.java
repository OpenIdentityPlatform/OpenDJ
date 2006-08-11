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
import java.io.*;

/**
 * This class contains the TestNG tests for the Backend functional tests for export
 */
@Test
public class ExportTests extends BackendTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackendStartupTests.testBackendStartup1" })
  public void testExport1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 1");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_1_and_2.out"};

    ds_output.redirectOutput(logDir, "ExportTest1.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport1" })
  public void testExport2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 2");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_1_and_2.out", "--appendToLDIF"};

    ds_output.redirectOutput(logDir, "ExportTest2.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport2" })
  public void testExport3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 3");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_3.out", "--includeAttribute", "telephoneNumber"};

    ds_output.redirectOutput(logDir, "ExportTest3.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport3" })
  public void testExport4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 4");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_4.out", "--includeAttribute", "telephonenumber", "--includeAttribute", "mail", "--includeAttribute", "roomnumber"};

    ds_output.redirectOutput(logDir, "ExportTest4.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport4" })
  public void testExport5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 5");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_5.out", "--excludeAttribute", "telephonenumber"};

    ds_output.redirectOutput(logDir, "ExportTest5.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport5" })
  public void testExport6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 6");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_6.out", "--excludeAttribute", "telephonenumber", "--excludeAttribute", "mail", "--excludeAttribute", "roomnumber"};

    ds_output.redirectOutput(logDir, "ExportTest6.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport6" })
  public void testExport7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 7");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_7.out", "--includeFilter", "(&(uid=jwalker)(roomnumber=*))"};

    ds_output.redirectOutput(logDir, "ExportTest7.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport7" })
  public void testExport8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 8");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_8.out", "--includeFilter", "(&(uid=jwalker)(roomnumber=*))", "--includeFilter", "(&(uid=jwalker)(l=Cupertino))", "--includeFilter", "(&(uid=jwallace)(roomnumber=*))"};

    ds_output.redirectOutput(logDir, "ExportTest8.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport8" })
  public void testExport9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 9");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_9.out", "--excludeFilter", "(&(uid=jwalker)(roomnumber=*))"};

    ds_output.redirectOutput(logDir, "ExportTest9.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport9" })
  public void testExport10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 10");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_10.out", "--excludeFilter", "(&(uid=jwalker)(roomnumber=*))", "--excludeFilter", "(&(uid=jwalker)(l=Cupertino))", "--excludeFilter", "(&(uid=jwallace)(roomnumber=*))"};

    ds_output.redirectOutput(logDir, "ExportTest10.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport10" })
  public void testExport11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 11");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_11.out", "--includeBranch", "o=backend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "ExportTest11.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport11" })
  public void testExport12(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 12");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_12.out", "--excludeBranch", "ou=People,o=backend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "ExportTest12.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport12" })
  public void testExport13(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 13");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_13.out", "--excludeFilter", "(&(uid=jwalker)(roomnumber=*))", "--includeAttribute", "telephonenumber", "--includeBranch", "o=backend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "ExportTest13.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport13" })
  public void testExport14(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 14");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_14.out", "--includeFilter", "(&(uid=jwalker)(roomnumber=*))", "--excludeAttribute", "telephonenumber", "--excludeBranch", "ou=groups,o=backend tests,dc=example,dc=com"};

    ds_output.redirectOutput(logDir, "ExportTest14.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport14" })
  public void testExport15(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 15");
    String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--ldifFile", "/tmp/export_test_15.out", "--compressLDIF"};

    ds_output.redirectOutput(logDir, "ExportTest15.txt");
    int retCode = ExportLDIF.mainExportLDIF(export_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
