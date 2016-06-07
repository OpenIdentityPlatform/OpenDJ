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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.api.TestTaskListener.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/**
 * Tests the backup and restore tasks.
 */
@SuppressWarnings("javadoc")
public class TestBackupAndRestore extends TasksTestCase
{
  @BeforeClass
  public final void setUp() throws Exception {
    TestCaseUtils.startServer();
    registerListeners();
  }

  @AfterClass
  public final void cleanUp() throws Exception {
    deregisterListeners();
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
              TestCaseUtils.makeEntry(backupTask(
                  "ds-task-backup-all: TRUE")),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         {
              // Incompatible settings of backup-directory-path and
              // incremental-base-id.
              TestCaseUtils.makeEntry(backupTask(
                  "ds-task-backup-all: TRUE",
                  "ds-task-backup-incremental: TRUE",
                  "ds-task-backup-incremental-base-id: monday")),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Incompatible settings for backend-id and backup-all.
              TestCaseUtils.makeEntry(backupTask(
                  "ds-task-backup-all: TRUE",
                  "ds-task-backup-backend-id: example")),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Neither of backend-id or backup-all specified.
              TestCaseUtils.makeEntry(backupTask()),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Incompatible settings for incremental and incremental-base-id.
              TestCaseUtils.makeEntry(backupTask(
                   "ds-task-backup-all: TRUE",
                   "ds-task-backup-incremental: FALSE",
                   "ds-task-backup-incremental-base-id: monday")),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Incompatible settings for hash and sign-hash.
              TestCaseUtils.makeEntry(backupTask(
                   "ds-task-backup-all: TRUE",
                   "ds-task-backup-hash: FALSE",
                   "ds-task-backup-sign-hash: TRUE")),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Specified backend does not support backup.
              TestCaseUtils.makeEntry(backupTask(
                   "ds-task-backup-backend-id: monitor")),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // A valid restore task.
              TestCaseUtils.makeEntry(restoreTask(
                   "ds-backup-directory-path: bak" + File.separator + "userRoot")),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         {
               // Restore a SchemaBackend
               TestCaseUtils.makeEntry(restoreTask(
               "ds-backup-directory-path: bak" + File.separator + "schema")),
               TaskState.COMPLETED_SUCCESSFULLY
         },
         {
              // Non-existent restore directory-path.
              TestCaseUtils.makeEntry(restoreTask(
                   "ds-backup-directory-path: missing"
              )),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Invalid restore directory-path.
              TestCaseUtils.makeEntry(restoreTask(
                   "ds-backup-directory-path: bak"
              )),
              TaskState.STOPPED_BY_ERROR
         },
         {
              // Invalid restore backup-id.
              TestCaseUtils.makeEntry(restoreTask(
                   "ds-backup-directory-path: bak" + File.separator + "userRoot",
                   "ds-backup-id: monday"
              )),
              TaskState.STOPPED_BY_ERROR
         },
    };
  }

  private String[] backupTask(String... additionalLdif)
  {
    final ArrayList<String> l = newArrayList(
        "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-backup",
        "ds-task-class-name: org.opends.server.tasks.BackupTask",
        "ds-backup-directory-path: bak");
    l.addAll(Arrays.asList(additionalLdif));
    return l.toArray(new String[0]);
  }

  private String[] restoreTask(String... additionalLdif)
  {
    final ArrayList<String> l = newArrayList(
        "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-restore",
        "ds-task-class-name: org.opends.server.tasks.RestoreTask");
    l.addAll(Arrays.asList(additionalLdif));
    return l.toArray(new String[0]);
  }

  /**
   * Test that various backup and restore task definitions complete with the
   * expected state.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   */
  @Test(dataProvider = "backups")
  public void testBackups(Entry taskEntry, TaskState expectedState) throws Exception
  {
    final int backupBeginCountStart = backupBeginCount.get();
    final int backupEndCountStart = backupEndCount.get();
    final int restoreBeginCountStart = restoreBeginCount.get();
    final int restoreEndCountStart = restoreEndCount.get();

    ObjectClass backupClass = DirectoryServer.getSchema().getObjectClass("ds-task-backup");

    testTask(taskEntry, expectedState, 30);
    if (expectedState == TaskState.COMPLETED_SUCCESSFULLY ||
        expectedState == TaskState.COMPLETED_WITH_ERRORS)
    {
      if (taskEntry.hasObjectClass(backupClass))
      {
        // The backup task can back up multiple backends at the same time, so
        // we the count may be incremented by more than one in those cases.
        assertThat(backupBeginCount.get()).isGreaterThan(backupBeginCountStart);
        assertThat(backupEndCount.get()).isGreaterThan(backupEndCountStart);
        assertEquals(backupBeginCount.get(), backupEndCount.get());
      }
      else
      {
        assertEquals(restoreBeginCount.get(), restoreBeginCountStart + 1);
        assertEquals(restoreEndCount.get(), restoreEndCountStart + 1);
        assertEquals(restoreBeginCount.get(), restoreEndCount.get());
      }
    }
  }

}
