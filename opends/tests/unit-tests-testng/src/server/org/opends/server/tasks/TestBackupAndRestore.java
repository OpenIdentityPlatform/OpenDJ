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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */


package org.opends.server.tasks;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.opends.server.api.TestTaskListener;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;

import static org.testng.Assert.*;

import java.util.UUID;
import java.io.File;

/**
 * Tests the backup and restore tasks.
 */
public class TestBackupAndRestore extends TasksTestCase
{
  @BeforeClass
  public final void setUp() throws Exception {
    TestCaseUtils.startServer();
    TestTaskListener.registerListeners();
  }


  @AfterClass
  public final void cleanUp() throws Exception {
    TestTaskListener.deregisterListeners();
  }


  /**
   * Backup and restore tasks test data provider.
   *
   * @return The array of tasks test data.  The first column is a task entry
   *  and the second column is the expected completed task state.
   */
  @DataProvider(name = "backups")
  public Object[][] createData() throws Exception
  {
    return new Object[][] {
         {
              // A valid backup task.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-all: TRUE"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         {
              // Incompatible settings of backup-directory-path and
              // incremental-base-id.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-incremental: TRUE",
                   "ds-task-backup-incremental-base-id: monday",
                   "ds-task-backup-all: TRUE"),
              TaskState.COMPLETED_WITH_ERRORS
         },
         {
              // Incompatible settings for backend-id and backup-all.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-backend-id: example",
                   "ds-task-backup-all: TRUE"),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Neither of backend-id or backup-all specified.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak"),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Incompatible settings for incremental and incremental-base-id.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-all: TRUE",
                   "ds-task-backup-incremental-base-id: monday",
                   "ds-task-backup-incremental: FALSE"),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Incompatible settings for hash and sign-hash.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-all: TRUE",
                   "ds-task-backup-hash: FALSE",
                   "ds-task-backup-sign-hash: TRUE"
                   ),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Specified backend does not support backup.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-backup",
                   "ds-task-class-name: org.opends.server.tasks.BackupTask",
                   "ds-backup-directory-path: bak",
                   "ds-task-backup-backend-id: monitor"),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // A valid restore task.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-restore",
                   "ds-task-class-name: org.opends.server.tasks.RestoreTask",
                   "ds-backup-directory-path: bak" + File.separator +
                        "userRoot"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         {
              // Non-existent restore directory-path.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-restore",
                   "ds-task-class-name: org.opends.server.tasks.RestoreTask",
                   "ds-backup-directory-path: missing"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Invalid restore directory-path.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-restore",
                   "ds-task-class-name: org.opends.server.tasks.RestoreTask",
                   "ds-backup-directory-path: bak"
              ),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Invalid restore backup-id.
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-restore",
                   "ds-task-class-name: org.opends.server.tasks.RestoreTask",
                   "ds-backup-directory-path: bak" + File.separator +
                        "userRoot",
                   "ds-backup-id: monday"
              ),
              TaskState.STOPPED_BY_ERROR
         },
    };
  }



  /**
   * Test that various backup and restore task definitions complete with the
   * expected state.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   */
  @Test(dataProvider = "backups")
  public void testBackups(Entry taskEntry, TaskState expectedState)
       throws Exception
  {
    int backupBeginCount  = TestTaskListener.backupBeginCount.get();
    int backupEndCount    = TestTaskListener.backupEndCount.get();
    int restoreBeginCount = TestTaskListener.restoreBeginCount.get();
    int restoreEndCount   = TestTaskListener.restoreEndCount.get();

    ObjectClass backupClass =
         DirectoryServer.getObjectClass("ds-task-backup", true);

    testTask(taskEntry, expectedState);
    if ((expectedState == TaskState.COMPLETED_SUCCESSFULLY) ||
        (expectedState == TaskState.COMPLETED_WITH_ERRORS))
    {
      if (taskEntry.hasObjectClass(backupClass))
      {
        // The backup task can back up multiple backends at the same time, so
        // we the count may be incremented by more than one in those cases.
        assertTrue(TestTaskListener.backupBeginCount.get() > backupBeginCount);
        assertTrue(TestTaskListener.backupEndCount.get() > backupEndCount);
        assertEquals(TestTaskListener.backupBeginCount.get(),
                     TestTaskListener.backupEndCount.get());
      }
      else
      {
        assertEquals(TestTaskListener.restoreBeginCount.get(),
                     (restoreBeginCount+1));
        assertEquals(TestTaskListener.restoreEndCount.get(),
                     (restoreEndCount+1));
        assertEquals(TestTaskListener.restoreBeginCount.get(),
                     TestTaskListener.restoreEndCount.get());
      }
    }
  }

}
