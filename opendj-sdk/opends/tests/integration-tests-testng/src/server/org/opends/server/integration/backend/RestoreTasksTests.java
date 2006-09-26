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
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Backend Restore Tasks Tests
    #@TestSuitePurpose          Test the restore tasks functionality for OpenDS
    #@TestSuiteID               Restore Tasks Tests
    #@TestSuiteGroup            Restore Tasks
    #@TestGroup                 Backend
    #@TestScript                RestoreTasksTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Backend functional tests for restore
 */
@Test
public class RestoreTasksTests extends BackendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Restore Tasks Tests
    #@TestName                  Restore Tasks 1
    #@TestID                    RestoreTasks1
    #@TestPreamble
    #@TestSteps                 An ldif file is created that describes the restore task to be
                                scheduled. The task is scheduled by adding the ldif file
                                with the static method, LDAPModify.mainModify().
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Restore data in OpenDS by scheduling a task.
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
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.RestoreTests.testRestore2" })
  public void testRestoreTasks1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Tasks Test 1");

    Writer output = null;
    try
    {
      String output_str = "dn: ds-task-id=4,cn=Scheduled Tasks,cn=tasks\n";
      output_str += "objectclass: top\n";
      output_str += "objectclass: ds-task\n";
      output_str += "objectclass: ds-task-restore\n";
      output_str += "ds-task-id: 4\n";
      output_str += "ds-task-class-name: org.opends.server.tasks.RestoreTask\n";
      output_str += "ds-backup-directory-path: " + integration_test_home + "/backend/data/restore.task\n";

      String restore_task_file = integration_test_home + "/backend/data/add_task_restore.ldif";
      output = new BufferedWriter(new FileWriter(restore_task_file));
      output.write(output_str);
    }
    catch (Exception e)
    {
      System.out.println("Exception occurred while creating add_task_restores.ldif file");
    }
    finally
    {
      if(output != null)
        output.close();
    }

    String datafile = integration_test_home + "/backend/data/add_task_restore.ldif";
    String mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
    ds_output.redirectOutput(logDir, "RestoreTasksTests1.txt");
    int retCode = LDAPModify.mainModify(mod_args);
    ds_output.resetOutput();
    if(retCode == 0)
    {
      System.out.println("Waiting for restore task to finish....");
      Thread.sleep(20000);
      String base = "uid=scarter, ou=People, o=restore task test, o=restore tests, dc=example,dc=com";
      String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", base, "objectclass=*"};
      ds_output.redirectOutput(logDir, "RestoreTasksTests1_check.txt");
      retCode = LDAPSearch.mainSearch(search_args);
      ds_output.resetOutput();
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);

    stopOpenDS(dsee_home, port);
    System.out.println("All tests have completed.\nOpenDS has been stopped.");
  }

}
