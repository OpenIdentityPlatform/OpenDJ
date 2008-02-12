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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.task;



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.BackendTestCase;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.TasksTestCase;
import org.opends.server.types.DN;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * A set of test cases that can be used to test the task backend.
 */
public class TaskBackendTestCase
       extends BackendTestCase
{
  /**
   * Ensures that the Directory Server is running, and that we are allowed to
   * schedule the dummy task.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--add", "allowed-task:org.opends.server.tasks.DummyTask");
  }



  /**
   * Remove the dummy task from the set of allowed tasks.
   */
  @AfterClass()
  public void cleanUp()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--remove", "allowed-task:org.opends.server.tasks.DummyTask");
  }



  /**
   * Tests to ensure that we can delete a task that is scheduled but hasn't
   * yet started.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeletePendingTask()
         throws Exception
  {
    // Schedule a task to start one hour from now that will simply sleep for
    // 30 seconds.
    String taskID = "testDeletePendingTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    long startTime = System.currentTimeMillis() + 3600000L;
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_UTC));
    String startTimeStr = dateFormat.format(new Date(startTime));

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask",
      "ds-task-scheduled-start-time: " + startTimeStr,
      "ds-task-dummy-sleep-time: 30000");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));

    Task task = TasksTestCase.getTask(DN.decode(taskDN));
    assertTrue(TaskState.isPending(task.getTaskState()));

    // Perform a modification to delete that task.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Tests to ensure that we cannot delete a task that is currently running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteRunningTask()
         throws Exception
  {
    // Schedule a task to start immediately that will simply sleep for 30
    // seconds.
    String taskID = "testDeleteRunningTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask",
      "ds-task-dummy-sleep-time: 30000");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));


    // Wait until we're sure that the task has started running.
    long startTime = System.currentTimeMillis();
    Task task = TasksTestCase.getTask(DN.decode(taskDN));
    while (TaskState.isPending(task.getTaskState()))
    {
      Thread.sleep(10);
      if (System.currentTimeMillis() > (startTime + 30000L))
      {
        throw new AssertionError("Waited too long for the task to start");
      }
    }

    assertTrue(TaskState.isRunning(task.getTaskState()));


    // Perform a modification to delete that task.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertFalse(resultCode == 0);
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Tests to ensure that we can delete a task that has completed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteCompletedTask()
         throws Exception
  {
    // Schedule a task to start immediately that will simply sleep for 30
    // seconds.
    String taskID = "testDeleteCompltedTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));


    // Wait until the task has completed.
    Task task = TasksTestCase.getCompletedTask(DN.decode(taskDN));
    assertTrue(TaskState.isDone(task.getTaskState()));


    // Perform a modification to delete that task.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Tests to ensure that we can modify a task that is scheduled but hasn't
   * yet started to change the task state as well as other attributes in the
   * task entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyPendingTask()
         throws Exception
  {
    // Schedule a task to start one hour from now that will simply sleep for
    // 30 seconds.
    String taskID = "testModifyPendingTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    long startTime = System.currentTimeMillis() + 3600000L;
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_UTC));
    String startTimeStr = dateFormat.format(new Date(startTime));

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask",
      "ds-task-scheduled-start-time: " + startTimeStr,
      "ds-task-dummy-sleep-time: 30000");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));

    Task task = TasksTestCase.getTask(DN.decode(taskDN));
    assertTrue(TaskState.isPending(task.getTaskState()));

    // Perform a modification to update a non-state attribute.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: modify",
      "add: description",
      "description: foo");
    assertEquals(resultCode, 0);


    // Perform a modification to update the task state.
    resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: modify",
      "replace: ds-task-state",
      "ds-task-state: " + TaskState.CANCELED_BEFORE_STARTING.toString());
    assertEquals(resultCode, 0);

    // Delete the task.
    resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Tests to ensure that we cannot modify a task that is currently running
   * other than to change its state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyRunningTask()
         throws Exception
  {
    // Schedule a task to start immediately that will simply sleep for 30
    // seconds.
    String taskID = "testModifyRunningTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask",
      "ds-task-dummy-sleep-time: 30000");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));


    // Wait until we're sure that the task has started running.
    long startTime = System.currentTimeMillis();
    Task task = TasksTestCase.getTask(DN.decode(taskDN));
    while (TaskState.isPending(task.getTaskState()))
    {
      Thread.sleep(10);
      if (System.currentTimeMillis() > (startTime + 30000L))
      {
        throw new AssertionError("Waited too long for the task to start");
      }
    }

    assertTrue(TaskState.isRunning(task.getTaskState()));


    // Perform a modification to change something other than the state.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: modify",
      "replace: description",
      "description: foo");
    assertFalse(resultCode == 0);


    // Perform a modification to cancel the task.
    resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: modify",
      "replace: ds-task-state",
      "ds-task-state: cancel");
    assertEquals(resultCode, 0);


    // We may have to wait for the task to register as done, but it should
    // definitely be done before it would have stopped normally.
    task = TasksTestCase.getCompletedTask(DN.decode(taskDN));
    assertTrue((System.currentTimeMillis() - startTime) < 30000L);
    assertTrue(TaskState.isDone(task.getTaskState()));


    // Perform a modification to delete that task.
    resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Tests to ensure that we cannot modify a task that has completed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyCompletedTask()
         throws Exception
  {
    // Schedule a task to start immediately that will simply sleep for 30
    // seconds.
    String taskID = "testModifyCompltedTask";
    String taskDN = "ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=tasks";

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: extensibleObject",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask");
    assertTrue(DirectoryServer.entryExists(DN.decode(taskDN)));


    // Wait until the task has completed.
    Task task = TasksTestCase.getCompletedTask(DN.decode(taskDN));
    assertTrue(TaskState.isDone(task.getTaskState()));


    // Perform a modification to update a non-state attribute.
    int resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: modify",
      "add: description",
      "description: foo");
    assertFalse(resultCode == 0);


    // Perform a modification to delete that task.
    resultCode = TestCaseUtils.applyModifications(
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }
}

