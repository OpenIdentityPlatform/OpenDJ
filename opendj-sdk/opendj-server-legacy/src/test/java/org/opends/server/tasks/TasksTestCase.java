/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import java.util.Set;
import java.util.concurrent.Callable;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.AttributeParser;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/** A base class for all tasks test cases. */
@Test(groups = { "precommit", "tasks" }, sequential = true)
public class TasksTestCase extends DirectoryServerTestCase {

  /**
   * Add a task definition and check that it completes with the expected state.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   * @param timeoutInSec The number of seconds to wait for the task to complete.
   * @throws Exception If the test fails.
   */
  protected void testTask(Entry taskEntry, TaskState expectedState, int timeoutInSec) throws Exception
  {
    // Add the task.
    AddOperation addOperation = getRootConnection().processAdd(taskEntry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                 "Add of the task definition was not successful");

    // Check that the task state is as expected.
    Entry resultEntry = getCompletedTaskEntry(taskEntry.getName(), timeoutInSec);
    String stateString = parseAttribute(resultEntry, ATTR_TASK_STATE).asString();
    TaskState taskState = TaskState.fromString(stateString);
    assertEquals(taskState, expectedState,
                 "The task completed in an unexpected state");

    // Check that the task contains some log messages.
    Set<String> logMessages = parseAttribute(resultEntry, ATTR_TASK_LOG_MESSAGES).asSetOfString();
    assertTrue(taskState == TaskState.COMPLETED_SUCCESSFULLY || !logMessages.isEmpty(),
        "No log messages were written to the task entry on a failed task.\n"
          + "taskState=" + taskState
          + "logMessages size=" + logMessages.size() + " and content=[" + logMessages + "]");
  }

  private Entry getCompletedTaskEntry(DN name, final int timeoutInSec) throws Exception
  {
    final SearchRequest request = newSearchRequest(name, SearchScope.BASE_OBJECT);

    TestTimer timer = new TestTimer.Builder()
      .maxSleep(timeoutInSec, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<Entry>()
    {
      @Override
      public Entry call()
      {
        InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
        Entry resultEntry = searchOperation.getSearchEntries().getFirst();
        String completionTime = parseAttribute(resultEntry, ATTR_TASK_COMPLETION_TIME).asString();
        assertNotNull(completionTime,
            "The task had not completed after " + timeoutInSec + " seconds.\nresultEntry=[" + resultEntry + "]");
        return resultEntry;
      }
    });
  }

  private AttributeParser parseAttribute(Entry resultEntry, String attrName)
  {
    return resultEntry.parseAttribute(attrName.toLowerCase());
  }

  /**
   * Retrieves the specified task from the server, regardless of its current
   * state.
   *
   * @param  taskEntryDN  The DN of the entry for the task to retrieve.
   * @return  The requested task entry.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled=false) // This isn't a test method, but TestNG thinks it is.
  public static Task getTask(final DN taskEntryDN) throws Exception
  {
    final TaskBackend taskBackend = (TaskBackend) DirectoryServer.getBackend(DN.valueOf("cn=tasks"));

    TestTimer timer = new TestTimer.Builder()
      .maxSleep(10, SECONDS)
      .sleepTimes(10, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<Task>()
    {
      @Override
      public Task call() throws Exception
      {
        Task task = taskBackend.getScheduledTask(taskEntryDN);
        assertNotNull("There is no such task " + taskEntryDN);
        return task;
      }
    });
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
  @Test(enabled=false) // This isn't a test method, but TestNG thinks it is.
  public static Task getDoneTask(final DN taskEntryDN) throws Exception
  {
    final Task task = getTask(taskEntryDN);

    TestTimer timer = new TestTimer.Builder()
      .maxSleep(20, SECONDS)
      .sleepTimes(10, MILLISECONDS)
      .toTimer();
    return timer.repeatUntilSuccess(new Callable<Task>()
    {
      @Override
      public Task call() throws Exception
      {
        assertTrue(TaskState.isDone(task.getTaskState()),
            "Task " + taskEntryDN + " did not complete in a timely manner.");
        return task;
      }
    });
  }

  @Test(enabled = false) // This isn't a test method, but TestNG thinks it is.
  public static void waitTaskCompletedSuccessfully(DN taskDN) throws Exception
  {
    Task task = getDoneTask(taskDN);
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
  }
}
