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

package org.opends.server.tasks;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Entry;
import org.opends.server.backends.task.TaskState;

import java.io.File;
import java.util.UUID;


/**
 * Tests invocation of the import and export tasks, but does not aim to
 * thoroughly test the underlying backend implementations.
 */
public class TestImportAndExport extends TasksTestCase
{
  /**
   * A makeldif template used to create some test entries.
   */
  private static String[] template = new String[] {
       "define suffix=dc=example,dc=com",
       "define maildomain=example.com",
       "define numusers=101",
       "",
       "branch: [suffix]",
       "",
       "branch: ou=People,[suffix]",
       "subordinateTemplate: person:[numusers]",
       "",
       "template: person",
       "rdnAttr: uid",
       "objectClass: top",
       "objectClass: person",
       "objectClass: organizationalPerson",
       "objectClass: inetOrgPerson",
       "givenName: <first>",
       "sn: <last>",
       "cn: {givenName} {sn}",
       "initials: {givenName:1}<random:chars:" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
       "employeeNumber: <sequential:0>",
       "uid: user.{employeeNumber}",
       "mail: {uid}@[maildomain]",
       "userPassword: password",
       "telephoneNumber: <random:telephone>",
       "homePhone: <random:telephone>",
       "pager: <random:telephone>",
       "mobile: <random:telephone>",
       "street: <random:numeric:5> <file:streets> Street",
       "l: <file:cities>",
       "st: <file:states>",
       "postalCode: <random:numeric:5>",
       "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
       "description: This is the description for {cn}.",
       ""};

  /**
   * A temporary LDIF file containing some test entries.
   */
  private File ldifFile;

  /**
   * A temporary file to contain rejected entries.
   */
  private File rejectFile;

  @BeforeClass
  public void setUp() throws Exception
  {
    // The server must be running for these tests.
    TestCaseUtils.startServer();

    // Create a temporary test LDIF file.
    ldifFile = File.createTempFile("import-test", ".ldif");
    String resourcePath = DirectoryServer.getServerRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);

    // Create a temporary rejects file.
    rejectFile = File.createTempFile("import-test-rejects", ".ldif");
  }

  @AfterClass
  public void tearDown()
  {
    ldifFile.delete();
    rejectFile.delete();
  }

  /**
   * Import and export tasks test data provider.
   *
   * @return The array of tasks test data.  The first column is a task entry
   *  and the second column is the expected completed task state.
   */
  @DataProvider(name = "importexport")
  public Object[][] createData() throws Exception
  {
    return new Object[][] {
         // A fairly simple, valid import task.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-reject-file: " + rejectFile.getPath(),
                   "ds-task-import-overwrite-rejects: TRUE",
                   "ds-task-import-exclude-attribute: description",
                   "ds-task-import-exclude-filter: (st=CA)",
                   "ds-task-import-exclude-branch: o=exclude,dc=example,dc=com"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         // A complex, valid import task.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-is-compressed: FALSE",
                   "ds-task-import-is-encrypted: FALSE",
                   "ds-task-import-reject-file: " + rejectFile.getPath(),
                   "ds-task-import-overwrite-rejects: FALSE",
                   "ds-task-import-append: TRUE",
                   "ds-task-import-replace-existing: TRUE",
                   "ds-task-import-skip-schema-validation: TRUE",
                   "ds-task-import-include-branch: dc=example,dc=com",
                   "ds-task-import-exclude-branch: o=exclude,dc=example,dc=com",
                   "ds-task-import-include-attribute: cn",
                   "ds-task-import-include-attribute: sn",
                   "ds-task-import-include-attribute: uid",
                   "ds-task-import-include-filter: (objectclass=*)"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         // LDIF file does not exist.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: doesnotexist",
                   "ds-task-import-backend-id: userRoot"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Invalid exclude filter.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-exclude-filter: ()"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Invalid include filter.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-include-filter: ()"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Backend id does not exist.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-backend-id: doesnotexist"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Backend does not support import.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-backend-id: monitor"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Backend does not handle include branch.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-include-branch: dc=opends,dc=org"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         // Rejects file is a directory.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-backend-id: userRoot",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-reject-file: " + ldifFile.getParent(),
                   "ds-task-import-overwrite-rejects: TRUE"
              ),
              TaskState.STOPPED_BY_ERROR
         },
    };
  }

  /**
   * Test that various import and export task definitions complete with the
   * expected state.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   */
  @Test(dataProvider = "importexport", groups = "slow")
  public void testImportExport(Entry taskEntry, TaskState expectedState)
       throws Exception
  {
    testTask(taskEntry, expectedState, 60);
  }

}
