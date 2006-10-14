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
import java.util.Calendar;
import java.util.GregorianCalendar;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Backend Backup Tests
    #@TestSuitePurpose          Test the backup functionality for OpenDS
    #@TestSuiteID               Backup Tests
    #@TestSuiteGroup            Backup
    #@TestGroup                 Backend
    #@TestScript                BackupTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Backend functional tests for backup
 */
@Test
public class BackupTests extends BackendTests
{
  public String backup_id = null;

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Backup Tests
    #@TestName                  Backup 1
    #@TestID                    Backup1
    #@TestPreamble
    #@TestSteps                 Client calls static method BackUpDB.mainBackUpDB()
                                with the parameters, --configClass, --configFileHandler,
                                --backendID, and --backupDirectory.
    #@TestPostamble
    #@TestResult                Success if BackUpDB.mainBackUpDB() returns 0
*/
/**
 *  Create a backup of the data in OpenDS.
 *
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
 *  @param  backupDir              The directory where the backup files will
 *                                 be placed.
*/
  @Parameters({ "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTasksTests.testExportTasks1" })
  public void testBackup1(String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 1");
    String datafile = backupDir + "/backup1";
    String backup_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--backupDirectory", datafile};

    ds_output.redirectOutput(logDir, "BackupTest1.txt");
    int retCode = BackUpDB.mainBackUpDB(backup_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Backup Tests
    #@TestName                  Backup 2
    #@TestID                    Backup2
    #@TestPreamble
    #@TestSteps                 Client calls static method BackUpDB.mainBackUpDB()
                                with the parameters, --configClass, --configFileHandler,
                                --backendID, --backupDirectory and --incremental.
    #@TestPostamble
    #@TestResult                Success if BackUpDB.mainBackUpDB() returns 0
*/
/**
 *  Create an incremental backup of the data in OpenDS.
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
 *  @param  backupDir              The directory where the backup files will
 *                                 be placed.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackupTests.testBackup1" })
  public void testBackup2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 2");
    String datafile = integration_test_home + "/backend/data/mods.ldif";
    String backup_mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BackupTest2_premod.txt");
    LDAPModify.mainModify(backup_mod_args);
    ds_output.resetOutput();

    datafile = backupDir + "/backup1";
    String backup_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--backupDirectory", datafile, "--incremental"};

    ds_output.redirectOutput(logDir, "BackupTest2.txt");
    int retCode = BackUpDB.mainBackUpDB(backup_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Backup Tests
    #@TestName                  Backup 3
    #@TestID                    Backup3
    #@TestPreamble
    #@TestSteps                 Client calls static method BackUpDB.mainBackUpDB()
                                with the parameters, --configClass, --configFileHandler,
                                --backendID, --backupDirectory and --backupID.
    #@TestPostamble
    #@TestResult                Success if BackUpDB.mainBackUpDB() returns 0
*/
/**
 *  Create a backup of the data in OpenDS with a backup id.
 *
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
 *  @param  backupDir              The directory where the backup files will
 *                                 be placed.
*/
  @Parameters({ "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackupTests.testBackup2" })
  public void testBackup3(String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 3");
    GregorianCalendar cal = new GregorianCalendar();
    backup_id = Integer.toString(cal.get(Calendar.MILLISECOND));
    String datafile = backupDir + "/backup2";
    String backup_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--backupDirectory", datafile, "--backupID", backup_id};
    
    ds_output.redirectOutput(logDir, "BackupTest3.txt");
    int retCode = BackUpDB.mainBackUpDB(backup_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Backup Tests
    #@TestName                  Backup 4
    #@TestID                    Backup4
    #@TestPreamble
    #@TestSteps                 Client calls static method BackUpDB.mainBackUpDB()
                                with the parameters, --configClass, --configFileHandler,
                                --backendID, --backupDirectory, --incremental, 
				and --incrementalBaseID.
    #@TestPostamble
    #@TestResult                Success if BackUpDB.mainBackUpDB() returns 0
*/
/**
 *  Create an incremental backup of the data in OpenDS with a backup id.
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
 *  @param  backupDir              The directory where the backup files will
 *                                 be placed.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackupTests.testBackup3" })
  public void testBackup4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 4");
    GregorianCalendar cal = new GregorianCalendar();

    String datafile = integration_test_home + "/backend/data/mods2.ldif";
    String backup_mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BackupTest4_premod.txt");
    LDAPModify.mainModify(backup_mod_args);
    ds_output.resetOutput();

    datafile = backupDir + "/backup2";
    String backup_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--backupDirectory", datafile, "--incremental", "--incrementalBaseID", backup_id};

    ds_output.redirectOutput(logDir, "BackupTest4.txt");
    int retCode = BackUpDB.mainBackUpDB(backup_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Backup Tests
    #@TestName                  Backup 5
    #@TestID                    Backup5
    #@TestPreamble
    #@TestSteps                 Client calls static method BackUpDB.mainBackUpDB()
                                with the parameters, --configClass, --configFileHandler,
				and --compress.
    #@TestPostamble
    #@TestResult                Success if BackUpDB.mainBackUpDB() returns 0
*/
/**
 *  Create a compressed backup of the data in OpenDS. 
 *
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
 *  @param  backupDir              The directory where the backup files will
 *                                 be placed.
*/
  @Parameters({ "integration_test_home", "logDir", "dsee_home", "backupDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.BackupTests.testBackup4" })
  public void testBackup5(String integration_test_home, String logDir, String dsee_home, String backupDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 5");
    String datafile = backupDir + "/backup1";
    String backup_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "userRoot", "--backupDirectory", datafile, "--compress"};
 
    ds_output.redirectOutput(logDir, "BackupTest5.txt");
    int retCode = BackUpDB.mainBackUpDB(backup_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }


}
