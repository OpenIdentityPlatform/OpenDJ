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
import java.util.Calendar;
import java.util.GregorianCalendar;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Backend Restore Tests
    #@TestSuitePurpose          Test the restore functionality for OpenDS
    #@TestSuiteID               Restore Tests
    #@TestSuiteGroup            Restore
    #@TestGroup                 Backend
    #@TestScript                RestoreTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Backend functional tests for restore
 */
@Test
public class RestoreTests extends BackendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Restore Tests
    #@TestName                  Restore 1
    #@TestID                    Restore1
    #@TestPreamble		The OpenDS is stopped.
    #@TestSteps                 Client calls static method RestoreDB.mainRestoreDB()
                                with the parameters, --configClass, --configFileHandler,
                                and --backupDirectory.
    #@TestPostamble		The OpenDs is started. 
    #@TestResult                Success if RestoreDB.mainRestoreDB() returns 0
*/
/**
 *  Restore data to OpenDS.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTasksTests.testImportTasks1" })
  public void testRestore1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 1");
    String datafile = integration_test_home + "/backend/data/restore";
    String restore_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backupDirectory", datafile};

    stopOpenDS(dsee_home, port);

    ds_output.redirectOutput(logDir, "RestoreTest1.txt");
    int retCode = RestoreDB.mainRestoreDB(restore_args);
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

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Restore Tests
    #@TestName                  Restore 2
    #@TestID                    Restore2
    #@TestPreamble		The OpenDS is stopped.
    #@TestSteps                 Client calls static method RestoreDB.mainRestoreDB()
                                with the parameters, --configClass, --configFileHandler,
                                --backupDirectory, --isCompressed.
    #@TestPostamble		The OpenDs is started. 
    #@TestResult                Success if RestoreDB.mainRestoreDB() returns 0
*/
/**
 *  Restore compressed data to OpenDS.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.RestoreTests.testRestore1" })
  public void testRestore2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 2");
    String datafile = integration_test_home + "/backend/data/restore.compressed";
    String restore_args[] = {"--configClass", "org.opends.server.extensions.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backupDirectory", datafile};

    stopOpenDS(dsee_home, port);

    ds_output.redirectOutput(logDir, "RestoreTest.compressed.txt");
    int retCode = RestoreDB.mainRestoreDB(restore_args);
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

}
