/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.SchemaTestMatchingRuleImpl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests invocation of the import and export tasks, but does not aim to
 * thoroughly test the underlying backend implementations.
 */
@SuppressWarnings("javadoc")
public class AddSchemaFileTaskTestCase extends TasksTestCase
{
  private static List<MatchingRule> matchingRulesToRemove = new ArrayList<>();

  /**
   * Make sure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @AfterClass
  public void deregisterMatchingRules() throws Exception
  {
    for (MatchingRule matchingRule : matchingRulesToRemove)
    {
      org.opends.server.types.Schema schema = DirectoryServer.getSchema();
      schema.deregisterMatchingRuleUse(schema.getMatchingRuleUse(matchingRule));
      schema.deregisterMatchingRule(matchingRule);
    }
  }

  private void registerNewMatchingRule(String name, String oid) throws DirectoryException
  {
    MatchingRule matchingRule = new SchemaBuilder(Schema.getCoreSchema())
        .buildMatchingRule(oid)
        .syntaxOID(CoreSchema.getDirectoryStringSyntax().getOID())
        .names(name)
        .implementation(new SchemaTestMatchingRuleImpl())
        .addToSchema()
        .toSchema()
        .getMatchingRule(oid);
    DirectoryServer.getSchema().registerMatchingRules(Arrays.asList(matchingRule), false);
    matchingRulesToRemove.add(matchingRule);
  }

  /**
   * Attempts to add a new file to the server schema where the file exists and
   * has valid contents.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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


    registerNewMatchingRule("testAddValidSchemaFileMatch", "1.3.6.1.4.1.26027.1.999.23");


    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    String[] fileLines =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddvalidschemafileat-oid " +
           "NAME 'testAddValidSchemaFileAT' SUP name)",
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
    writeLines(validFile, fileLines);

    String taskDNStr =
         "ds-task-id=add-single-valid-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(true,
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-single-valid-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-single-valid.ldif");
    assertEquals(resultCode, 0);

    waitTaskCompletedSuccessfully(DN.valueOf(taskDNStr));
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }

  /**
   * Attempts to add multiple new files to the server schema where the files
   * exist and have valid contents.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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

    registerNewMatchingRule("testAddMultipleValidSchemaFiles1Match", "1.3.6.1.4.1.26027.1.999.24");

    String[] fileLines1 =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddmultiplevalidschemafiles1at-oid " +
           "NAME 'testAddMultipleValidSchemaFiles1AT' SUP name)",
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

    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();
    File validFile1 = new File(schemaDirectory, "05-multiple-valid-1.ldif");
    writeLines(validFile1, fileLines1);

    registerNewMatchingRule("testAddMultipleValidSchemaFiles2Match", "1.3.6.1.4.1.26027.1.999.25");

    String[] fileLines2 =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( testaddmultiplevalidschemafiles2at-oid " +
           "NAME 'testAddMultipleValidSchemaFiles2AT' SUP name)",
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
    writeLines(validFile2, fileLines2);


    String taskDNStr =
         "ds-task-id=add-multiple-valid-files,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(true,
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

    waitTaskCompletedSuccessfully(DN.valueOf(taskDNStr));
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }

  private void writeLines(File file, String[] lines) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file)))
    {
      for (String line : lines)
      {
        writer.write(line);
        writer.newLine();
      }
    }
  }

  /**
   * Attempts to add a new file to the server schema in which the task entry
   * does not specify the name of the file to add.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMissingSchemaFileNames()
         throws Exception
  {
    String taskDNStr =
         "ds-task-id=add-missing-file-names,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(true,
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
  @Test
  public void testAddMissingSchemaFile()
         throws Exception
  {
    String taskDNStr =
         "ds-task-id=add-missing-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(true,
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
  @Test
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
    int resultCode = TestCaseUtils.applyModifications(true,
         "dn: " + taskDNStr,
         "changetype: add",
         "objectClass: top",
         "objectClass: ds-task",
         "objectClass: ds-task-add-schema-file",
         "ds-task-id: add-empty-file",
         "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
         "ds-task-schema-file-name: 05-empty.ldif");
    assertEquals(resultCode, 0);

    waitTaskCompletedSuccessfully(DN.valueOf(taskDNStr));
    assertFalse(DirectoryServer.getSchema().getYoungestModificationTime() ==
                     beforeModifyTimestamp);
  }

  /**
   * Attempts to add a new file to the server schema in which the file exists
   * but does not contain a valid schema definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddInvalidSchemaFile()
         throws Exception
  {
    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    File invalidFile = new File(schemaDirectory, "05-invalid.ldif");
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(invalidFile)))
    {
      writer.write("invalid");
    }

    String taskDNStr =
         "ds-task-id=add-invalid-file,cn=Scheduled Tasks,cn=Tasks";
    int resultCode = TestCaseUtils.applyModifications(true,
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
}
