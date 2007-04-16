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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.SchemaTestMatchingRule;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



/**
 * Tests invocation of the import and export tasks, but does not aim to
 * thoroughly test the underlying backend implementations.
 */
public class AddSchemaFileTaskTestCase
       extends TasksTestCase
{
  /**
   * Make sure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Attempts to add a new file to the server schema where the file exists and
   * has valid contents.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddValidSchemaFile()
         throws Exception
  {
    // Get the last modified timestamp from the schema and then sleep for two
    // milliseconds to make sure that any potential updates to the last
    // modification time that it won't have any chance of happening in the same
    // millisecond as the last update.
    long beforeModifyTimestamp =
              DirectoryServer.getSchema().getYoungestModificationTime();
    Thread.sleep(2);


    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testAddValidSchemaFileMatch",
                                    "1.3.6.1.4.1.26027.1.999.23");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    String[] fileLines =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddvalidschemafileat-oid " +
           "NAME 'testAddValidSchemaFileAT' )",
      "objectClasses: ( testaddvalidschemafileoc-oid " +
           "NAME 'testAddValidSchemaFileOC' STRUCTURAL " +
           "MUST testAddValidSchemaFileAT )",
      "nameForms: ( testaddvalidschemafilenf-oid " +
           "NAME 'testAddValidSchemaFileNF' OC testAddValidSchemaFileOC " +
           "MUST testAddValidSchemaFileAT )",
      "dITContentRules: ( testaddvalidschemafileoc-oid " +
           "NAME 'testAddValidSchemaFileDCR' MAY description )",
      "dITStructureRules: ( 999016 NAME 'testAddValidSchemaFileDSR' " +
           "FORM testAddValidSchemaFileNF )",
      "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.23 " +
           "NAME 'testAddValidSchemaFileMRU' APPLIES testAddValidSchemaFileAT )"
    };

    File validFile = new File(schemaDirectory, "05-single-valid.ldif");
    BufferedWriter writer = new BufferedWriter(new FileWriter(validFile));
    for (String line : fileLines)
    {
      writer.write(line);
      writer.newLine();
    }
    writer.close();

    String taskDNStr =
         "ds-task-id=add-single-valid-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-single-valid-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-single-valid.ldif");
    assertEquals(resultCode, 0);

    Task task = getCompletedTask(DN.decode(taskDNStr));
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }



  /**
   * Attempts to add multiple new files to the server schema where the files
   * exist and have valid contents.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMultipleValidSchemaFiles()
         throws Exception
  {
    // Get the last modified timestamp from the schema and then sleep for two
    // milliseconds to make sure that any potential updates to the last
    // modification time that it won't have any chance of happening in the same
    // millisecond as the last update.
    long beforeModifyTimestamp =
              DirectoryServer.getSchema().getYoungestModificationTime();
    Thread.sleep(2);


    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();


    SchemaTestMatchingRule matchingRule1 =
         new SchemaTestMatchingRule("testAddMultipleValidSchemaFiles1Match",
                                    "1.3.6.1.4.1.26027.1.999.24");
    DirectoryServer.registerMatchingRule(matchingRule1, false);

    String[] fileLines1 =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddmultiplevalidschemafiles1at-oid " +
           "NAME 'testAddMultipleValidSchemaFiles1AT' )",
      "objectClasses: ( testaddmultiplevalidschemafiles1oc-oid " +
           "NAME 'testAddMultipleValidSchemaFiles1OC' STRUCTURAL " +
           "MUST testAddMultipleValidSchemaFiles1AT )",
      "nameForms: ( testaddmultiplevalidschemafiles1nf-oid " +
           "NAME 'testAddMultipleValidSchemaFiles1NF' " +
           "OC testAddMultipleValidSchemaFiles1OC " +
           "MUST testAddMultipleValidSchemaFiles1AT )",
      "dITContentRules: ( testaddmultiplevalidschemafiles1oc-oid " +
           "NAME 'testAddMultipleValidSchemaFiles1DCR' MAY description )",
      "dITStructureRules: ( 999017 " +
           "NAME 'testAddMultipleValidSchemaFiles1DSR' " +
           "FORM testAddMultipleValidSchemaFiles1NF )",
      "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.24 " +
           "NAME 'testAddMultipleValidSchemaFiles1MRU' " +
           "APPLIES testAddMultipleValidSchemaFiles1AT )"
    };

    File validFile1 = new File(schemaDirectory, "05-multiple-valid-1.ldif");
    BufferedWriter writer1 = new BufferedWriter(new FileWriter(validFile1));
    for (String line : fileLines1)
    {
      writer1.write(line);
      writer1.newLine();
    }
    writer1.close();


    SchemaTestMatchingRule matchingRule2 =
         new SchemaTestMatchingRule("testAddMultipleValidSchemaFiles2Match",
                                    "1.3.6.1.4.1.26027.1.999.25");
    DirectoryServer.registerMatchingRule(matchingRule2, false);

    String[] fileLines2 =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddmultiplevalidschemafiles2at-oid " +
           "NAME 'testAddMultipleValidSchemaFiles2AT' )",
      "objectClasses: ( testaddmultiplevalidschemafiles2oc-oid " +
           "NAME 'testAddMultipleValidSchemaFiles2OC' STRUCTURAL " +
           "MUST testAddMultipleValidSchemaFiles2AT )",
      "nameForms: ( testaddmultiplevalidschemafiles2nf-oid " +
           "NAME 'testAddMultipleValidSchemaFiles2NF' " +
           "OC testAddMultipleValidSchemaFiles2OC " +
           "MUST testAddMultipleValidSchemaFiles2AT )",
      "dITContentRules: ( testaddmultiplevalidschemafiles2oc-oid " +
           "NAME 'testAddMultipleValidSchemaFiles2DCR' MAY description )",
      "dITStructureRules: ( 999018 " +
           "NAME 'testAddMultipleValidSchemaFiles2DSR' " +
           "FORM testAddMultipleValidSchemaFiles2NF )",
      "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.25 " +
           "NAME 'testAddMultipleValidSchemaFiles2MRU' " +
           "APPLIES testAddMultipleValidSchemaFiles2AT )"
    };

    File validFile2 = new File(schemaDirectory, "05-multiple-valid-2.ldif");
    BufferedWriter writer2 = new BufferedWriter(new FileWriter(validFile2));
    for (String line : fileLines2)
    {
      writer2.write(line);
      writer2.newLine();
    }
    writer2.close();


    String taskDNStr =
         "ds-task-id=add-multiple-valid-files,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-multiple-valid-files",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-multiple-valid-1.ldif",
         "ds-task-schema-file-name: 05-multiple-valid-2.ldif");
    assertEquals(resultCode, 0);

    Task task = getCompletedTask(DN.decode(taskDNStr));
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }



  /**
   * Attempts to add a new file to the server schema in which the task entry
   * does not specify the name of the file to add.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMissingSchemaFileNames()
         throws Exception
  {
    String taskDNStr =
         "ds-task-id=add-missing-file-names,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "ds-task-id: add-missing-file-names",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask");
    assertFalse(resultCode == 0);
  }



  /**
   * Attempts to add a new file to the server schema in which the file does not
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMissingSchemaFile()
         throws Exception
  {
    String taskDNStr =
         "ds-task-id=add-missing-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-missing-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-missing.ldif");
    assertFalse(resultCode == 0);
  }



  /**
   * Attempts to add a new file to the server schema in which the file exists
   * and is empty.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddEmptySchemaFile()
         throws Exception
  {
    // Get the last modified timestamp from the schema and then sleep for two
    // milliseconds to make sure that any potential updates to the last
    // modification time that it won't have any chance of happening in the same
    // millisecond as the last update.
    long beforeModifyTimestamp =
              DirectoryServer.getSchema().getYoungestModificationTime();
    Thread.sleep(2);


    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    File emptyFile = new File(schemaDirectory, "05-empty.ldif");
    emptyFile.createNewFile();

    String taskDNStr = "ds-task-id=add-empty-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-empty-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-empty.ldif");
    assertEquals(resultCode, 0);

    Task task = getCompletedTask(DN.decode(taskDNStr));
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }



  /**
   * Attempts to add a new file to the server schema in which the file exists
   * but does not contain a valid schema definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddInvalidSchemaFile()
         throws Exception
  {
    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    File invalidFile = new File(schemaDirectory, "05-invalid.ldif");
    BufferedWriter writer = new BufferedWriter(new FileWriter(invalidFile));
    writer.write("invalid");
    writer.close();

    String taskDNStr =
         "ds-task-id=add-invalid-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-invalid-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-invalid.ldif");
    assertFalse(resultCode == 0);
    invalidFile.delete();
  }



  /**
   * Retrieves the specified task from the server, waiting for it to finish all
   * the running its going to do before returning.
   *
   * @param  taskEntryDN  The DN of the entry for the task to retrieve.
   *
   * @return  The requested task entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private Task getCompletedTask(DN taskEntryDN)
          throws Exception
  {
    TaskBackend taskBackend =
         (TaskBackend) DirectoryServer.getBackend(DN.decode("cn=tasks"));
    Task task = taskBackend.getScheduledTask(taskEntryDN);
    if (task == null)
    {
      long stopWaitingTime = System.currentTimeMillis() + 10000L;
      while ((task == null) && (System.currentTimeMillis() < stopWaitingTime))
      {
        Thread.sleep(10);
        task = taskBackend.getScheduledTask(taskEntryDN);
      }
    }

    if (task == null)
    {
      throw new AssertionError("There is no such task " +
                               taskEntryDN.toString());
    }

    if (! TaskState.isDone(task.getTaskState()))
    {
      long stopWaitingTime = System.currentTimeMillis() + 20000L;
      while ((! TaskState.isDone(task.getTaskState())) &&
             (System.currentTimeMillis() < stopWaitingTime))
      {
        Thread.sleep(10);
      }
    }

    if (! TaskState.isDone(task.getTaskState()))
    {
      throw new AssertionError("Task " + taskEntryDN.toString() +
                               " did not complete in a timely manner.");
    }

    return task;
  }
}

