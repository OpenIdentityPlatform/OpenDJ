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

/*
    Place suite-specific test information here.
    #@TestSuiteName             Backend Export Tasks Tests
    #@TestSuitePurpose          Test the backend functionality for OpenDS
    #@TestSuiteID               Export Tasks Tests
    #@TestSuiteGroup            Export Tasks
    #@TestGroup                 Backend
    #@TestScript                ExportTasksTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Backend functional tests for export
 */
@Test
public class ExportTasksTests extends BackendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Backend Export Tasks Tests
    #@TestName                  Export Tasks 1
    #@TestID                    ExportTasks1
    #@TestPreamble
    #@TestSteps                 An ldif file is created that describes the export task to be
				scheduled. The task is scheduled by adding the ldif file
				with the static method, LDAPModify.mainModify(). 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Export the data in OpenDS by scheduling a task.
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
 *  @param  exportDir              The directory where the export files will
 *                                 be placed.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir", "dsee_home", "exportDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.backend.ExportTests.testExport15" })
  public void testExportTasks1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir, String dsee_home, String exportDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Tasks Test 1");

    Writer output = null;
    try
    {
      String output_str = "dn: ds-task-id=2,cn=Scheduled Tasks,cn=tasks\n";
      output_str += "objectclass: top\n";
      output_str += "objectclass: ds-task\n";
      output_str += "objectclass: ds-task-export\n";
      output_str += "ds-task-id: 2\n";
      output_str += "ds-task-class-name: org.opends.server.tasks.ExportTask\n";
      output_str += "ds-task-export-ldif-file: " + exportDir + "/export_task.ldif\n";
      output_str += "ds-task-export-backend-id: userRoot\n";
      output_str += "ds-task-export-include-branch: o=backend tests,dc=example,dc=com\n";

      String export_task_file = integration_test_home + "/backend/data/add_task_export.ldif";
      output = new BufferedWriter(new FileWriter(export_task_file));
      output.write(output_str);
    }
    catch (Exception e)
    {
      System.out.println("Exception occurred while creating add_task_export.ldif file");
    }
    finally
    {
      if(output != null)
        output.close();
    }

    String datafile = integration_test_home + "/backend/data/add_task_export.ldif";
    String mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "ExportTasksTests1.txt");
    int retCode = LDAPModify.mainModify(mod_args);
    ds_output.resetOutput();
    if(retCode == 0)
    {
      System.out.println("Waiting for export task to finish....");
      Thread.sleep(20000);
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
