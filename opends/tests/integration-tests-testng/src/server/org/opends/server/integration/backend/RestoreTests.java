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

/**
 * This class contains the TestNG tests for the Backend functional tests for restore
 */
@Test
public class RestoreTests extends BackendTests
{
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ImportTasksTests.testImportTasks1" })
  public void testRestore1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 1");
    String datafile = integration_test_home + "/backend/data/restore";
    String restore_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backupDirectory", datafile};

    stopOpenDS(dsee_home);

    ds_output.redirectOutput(logDir, "RestoreTest1.txt");
    int retCode = RestoreDB.mainRestoreDB(restore_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);

    startOpenDS(dsee_home);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.RestoreTests.testRestore1" })
  public void testRestore2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 2");
    String datafile = integration_test_home + "/backend/data/restore.compressed";
    String restore_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backupDirectory", datafile};

    stopOpenDS(dsee_home);

    ds_output.redirectOutput(logDir, "RestoreTest.compressed.txt");
    int retCode = RestoreDB.mainRestoreDB(restore_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);

    startOpenDS(dsee_home);
  }

}
