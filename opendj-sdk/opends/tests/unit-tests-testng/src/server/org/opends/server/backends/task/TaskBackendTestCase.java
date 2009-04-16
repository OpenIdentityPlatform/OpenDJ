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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.task;



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import java.util.UUID;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.BackendTestCase;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.TasksTestCase;
import org.opends.server.types.DN;

import org.opends.server.types.ResultCode;
import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



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
   * @throws Exception If an unexpected error occurred.
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
    int resultCode = TestCaseUtils.applyModifications(true,
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
    int resultCode = TestCaseUtils.applyModifications(true,
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
    int resultCode = TestCaseUtils.applyModifications(true,
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
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: " + taskDN,
      "changetype: modify",
      "add: description",
      "description: foo");
    assertEquals(resultCode, 0);


    // Perform a modification to update the task state.
    resultCode = TestCaseUtils.applyModifications(true,
      "dn: " + taskDN,
      "changetype: modify",
      "replace: ds-task-state",
      "ds-task-state: " + TaskState.CANCELED_BEFORE_STARTING.toString());
    assertEquals(resultCode, 0);

    // Delete the task.
    resultCode = TestCaseUtils.applyModifications(true,
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
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: " + taskDN,
      "changetype: modify",
      "replace: description",
      "description: foo");
    assertFalse(resultCode == 0);


    // Perform a modification to cancel the task.
    resultCode = TestCaseUtils.applyModifications(true,
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
    resultCode = TestCaseUtils.applyModifications(true,
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
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: " + taskDN,
      "changetype: modify",
      "add: description",
      "description: foo");
    assertFalse(resultCode == 0);


    // Perform a modification to delete that task.
    resultCode = TestCaseUtils.applyModifications(true,
      "dn: " + taskDN,
      "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
  }



  /**
   * Creates test schedules for recurring tasks tests.
   *
   * @return The set of invalid and valid schedules.
   */
  @DataProvider(name="recurringTaskSchedules")
  public Object[][] createRecurringTaskSchedules() {
    return new Object[][] {
        { "* * * *",       false },
        { "* * * * * *",   false },
        { "*:*:*:*:*",     false },
        { "60 * * * *",    false },
        { "-1 * * * *",    false },
        { "1-60 * * * *",  false },
        { "1,60 * * * *",  false },
        { "* 24 * * *",    false },
        { "* -1 * * *",    false },
        { "* 1-24 * * *",  false },
        { "* 1,24 * * *",  false },
        { "* * 32 * *",    false },
        { "* * 0 * *",     false },
        { "* * 1-32 * *",  false },
        { "* * 1,32 * *",  false },
        { "* * * 13 *",    false },
        { "* * * 0 *",     false },
        { "* * * 1-13 *",  false },
        { "* * * 1,13 *",  false },
        { "* * * * 7",     false },
        { "* * * * -1",    false },
        { "* * * * 1-7",   false },
        { "* * * * 1,7",   false },
        { "* * 31 2 *",    false },
        { "* * * * *",     true },
        { "59 * * * *",    true },
        { "0 * * * *",     true },
        { "0-59 * * * *",  true },
        { "0,59 * * * *",  true },
        { "* 23 * * *",    true },
        { "* 0 * * *",     true },
        { "* 0-23 * * *",  true },
        { "* 0,23 * * *",  true },
        { "* * 31 * *",    true },
        { "* * 1 * *",     true },
        { "* * 1-31 * *",  true },
        { "* * 1,31 * *",  true },
        { "* * * 12 *",    true },
        { "* * * 1 *",     true },
        { "* * * 1-12 *",  true },
        { "* * * 1,12 *",  true },
        { "* * * * 6",     true },
        { "* * * * 0",     true },
        { "* * * * 0-6",   true },
        { "* * * * 0,6",   true }
    };
  }



  /**
   * Tests basic recurring task functionality and parser.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testRecurringTask() throws Exception
  {
    String taskID = "testRecurringTask";
    String taskDN =
        "ds-recurring-task-id=" + taskID
            + ",cn=Recurring Tasks,cn=tasks";
    String taskSchedule = "00 * * * *";

    GregorianCalendar calendar = new GregorianCalendar();
    calendar.setFirstDayOfWeek(GregorianCalendar.SUNDAY);
    calendar.setLenient(false);
    calendar.add(GregorianCalendar.HOUR_OF_DAY, 1);
    calendar.set(GregorianCalendar.MINUTE, 0);
    calendar.set(GregorianCalendar.SECOND, 0);

    Date scheduledDate = calendar.getTime();
    String scheduledTaskID = taskID + " - " + scheduledDate.toString();
    String scheduledTaskDN =
        "ds-task-id=" + scheduledTaskID
            + ",cn=Scheduled Tasks,cn=tasks";

    assertTrue(addRecurringTask(taskID, taskSchedule));

    Task scheduledTask =
        TasksTestCase.getTask(DN.decode(scheduledTaskDN));
    assertTrue(TaskState.isPending(scheduledTask.getTaskState()));

    // Perform a modification to update a non-state attribute.
    int resultCode =
        TestCaseUtils.applyModifications(true, "dn: " + taskDN,
            "changetype: modify",
            "replace: ds-recurring-task-schedule",
            "ds-recurring-task-schedule: * * * * *");
    assertFalse(resultCode == 0);

    // Delete recurring task.
    resultCode =
        TestCaseUtils.applyModifications(true, "dn: " + taskDN,
            "changetype: delete");
    assertEquals(resultCode, 0);
    assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));

    // Make sure scheduled task got canceled.
    scheduledTask = TasksTestCase.getTask(DN.decode(scheduledTaskDN));
    assertTrue(TaskState.isCancelled(scheduledTask.getTaskState()));
  }



  /**
   * Tests basic recurring task functionality and parser.
   *
   * @param schedule
   *          The schedule string.
   * @param isValid
   *          <code>true</code> if the schedule is expected to be valid.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "recurringTaskSchedules")
  public void testRecurringTaskSchedules(String schedule,
      boolean isValid) throws Exception
  {
    String taskID = "testRecurringTask" + "-" + UUID.randomUUID();
    String taskDN =
        "ds-recurring-task-id=" + taskID
            + ",cn=Recurring Tasks,cn=tasks";

    try
    {
      assertEquals(addRecurringTask(taskID, schedule), isValid);
      if (isValid)
      {
        // Delete recurring task.
        int resultCode =
            TestCaseUtils.applyModifications(true, "dn: " + taskDN,
                "changetype: delete");
        assertEquals(resultCode, 0);
        assertFalse(DirectoryServer.entryExists(DN.decode(taskDN)));
      }
    }
    finally
    {
      try
      {
        // Force deletion of recurring task.
        TestCaseUtils.applyModifications(true, "dn: " + taskDN,
            "changetype: delete");
      }
      finally
      {
        // Do nothing.
      }
    }
  }



  /**
   * Adds recurring task to the task backend.
   *
   * @param  taskID  recurring task id.
   *
   * @param  taskSchedule  recurring task schedule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   * @return <CODE>true</CODE> if task successfully added to
   *         the task backend, <CODE>false</CODE> otherwise.
   */
  @Test(enabled=false) // This isn't a test method, but TestNG thinks it is.
  private boolean addRecurringTask(String taskID, String taskSchedule)
          throws Exception
  {
    String taskDN = "ds-recurring-task-id=" +
      taskID + ",cn=Recurring Tasks,cn=tasks";

    ResultCode rc = TestCaseUtils.addEntryOperation(
      "dn: " + taskDN,
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: ds-recurring-task",
      "objectClass: extensibleObject",
      "ds-recurring-task-id: " + taskID,
      "ds-recurring-task-schedule: " + taskSchedule,
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DummyTask",
      "ds-task-dummy-sleep-time: 0");

    if (rc != ResultCode.SUCCESS) {
      return false;
    }

    if (DirectoryServer.entryExists(DN.decode(taskDN))) {
      return true;
    } else {
      return false;
    }
  }
}
