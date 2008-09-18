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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.server.tasks;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.AddOperation;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.api.TestTaskListener;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.backends.task.TaskState;

import static org.testng.Assert.*;

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
    String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, template);

    // Create a temporary rejects file.
    rejectFile = File.createTempFile("import-test-rejects", ".ldif");

    TestTaskListener.registerListeners();
  }

  @AfterClass
  public void tearDown()
  {
    ldifFile.delete();
    rejectFile.delete();
    TestTaskListener.deregisterListeners();
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
         // A fairly simple, valid import task using backend ID.
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
         // A fairly simple, valid import task using include base DN.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-include-branch: dc=example,dc=com",
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
         // A partial, valid import task.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-import",
                   "ds-task-class-name: org.opends.server.tasks.ImportTask",
                   "ds-task-import-include-branch: ou=people,dc=example,dc=com",
                   "ds-task-import-ldif-file: " + ldifFile.getPath(),
                   "ds-task-import-reject-file: " + rejectFile.getPath(),
                   "ds-task-import-overwrite-rejects: TRUE",
                   "ds-task-import-exclude-attribute: description",
                   "ds-task-import-exclude-filter: (st=CA)",
                   "ds-task-import-exclude-branch: o=exclude,dc=example,dc=com"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
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
                   "ds-task-import-ldif-file: doesnotexist",
                   "ds-task-import-backend-id: userRoot"
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
   * Import and export tasks bad test data provider.
   *
   * @return The array of tasks test data.  The first column is a task entry
   *  and the second column is the expected completed task state.
   */
  @DataProvider(name = "badimportexport")
  public Object[][] createBadData() throws Exception
  {
    return new Object[][] {
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
            ResultCode.UNWILLING_TO_PERFORM
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
            ResultCode.UNWILLING_TO_PERFORM
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
            ResultCode.UNWILLING_TO_PERFORM
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
            ResultCode.UNWILLING_TO_PERFORM
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
            ResultCode.UNWILLING_TO_PERFORM
        },
        // Not specifying a destination.
        {
            TestCaseUtils.makeEntry(
                "dn: ds-task-id=" + UUID.randomUUID() +
                    ",cn=Scheduled Tasks,cn=Tasks",
                "objectclass: top",
                "objectclass: ds-task",
                "objectclass: ds-task-import",
                "ds-task-class-name: org.opends.server.tasks.ImportTask",
                "ds-task-import-ldif-file: " + ldifFile.getPath()
            ),
            ResultCode.UNWILLING_TO_PERFORM
        }
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
    int exportBeginCount = TestTaskListener.exportBeginCount.get();
    int exportEndCount   = TestTaskListener.exportEndCount.get();
    int importBeginCount = TestTaskListener.importBeginCount.get();
    int importEndCount   = TestTaskListener.importEndCount.get();

    ObjectClass exportClass =
         DirectoryServer.getObjectClass("ds-task-export", true);

    testTask(taskEntry, expectedState, 60);
     if ((expectedState == TaskState.COMPLETED_SUCCESSFULLY) ||
        (expectedState == TaskState.COMPLETED_WITH_ERRORS))
    {
      if (taskEntry.hasObjectClass(exportClass))
      {
        assertEquals(TestTaskListener.exportBeginCount.get(),
                     (exportBeginCount+1));
        assertEquals(TestTaskListener.exportEndCount.get(), (exportEndCount+1));
        assertEquals(TestTaskListener.exportBeginCount.get(),
                     TestTaskListener.exportEndCount.get());
      }
      else
      {
        assertEquals(TestTaskListener.importBeginCount.get(),
                     (importBeginCount+1));
        assertEquals(TestTaskListener.importEndCount.get(), (importEndCount+1));
        assertEquals(TestTaskListener.importBeginCount.get(),
                     TestTaskListener.importEndCount.get());
      }
    }
 }

  /**
   * Add a task definition and check that it completes with the expected state.
   * @param taskEntry The task entry.
   * @param resultCode The expected result code of the task add.
   * @throws Exception If the test fails.
   */
  @Test(dataProvider = "badimportexport")
  public void testBadTask(Entry taskEntry, ResultCode resultCode)
      throws Exception
  {
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();

    // Add the task.
    AddOperation addOperation =
        connection.processAdd(taskEntry.getDN(),
                              taskEntry.getObjectClasses(),
                              taskEntry.getUserAttributes(),
                              taskEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), resultCode);
  }

}
