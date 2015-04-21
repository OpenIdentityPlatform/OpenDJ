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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.tasks;

import java.util.Set;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.AttributeParser;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * A base class for all tasks test cases.
 */
@Test(groups = { "precommit", "tasks" }, sequential = true)
public class TasksTestCase extends DirectoryServerTestCase {

  /**
   * Add a task definition and check that it completes with the expected state.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   * @param timeout The number of seconds to wait for the task to complete.
   * @throws Exception If the test fails.
   */
  protected void testTask(Entry taskEntry, TaskState expectedState, int timeout)
       throws Exception
  {
    InternalClientConnection connection = getRootConnection();

    // Add the task.
    AddOperation addOperation = connection.processAdd(taskEntry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                 "Add of the task definition was not successful");

    // Wait until the task completes.
    final SearchRequest request = newSearchRequest(taskEntry.getName(), SearchScope.BASE_OBJECT);
    Entry resultEntry = null;
    String completionTime = null;
    long startMillisecs = System.currentTimeMillis();
    boolean timedOut;
    do
    {
      Thread.sleep(100);
      InternalSearchOperation searchOperation = connection.processSearch(request);
      resultEntry = searchOperation.getSearchEntries().getFirst();
      completionTime = parseAttribute(resultEntry, ATTR_TASK_COMPLETION_TIME).asString();
      timedOut = System.currentTimeMillis() - startMillisecs > 1000 * timeout;
    }
    while (completionTime == null && !timedOut);

    assertNotNull(completionTime, "The task had not completed after " + timeout + " seconds.\n"
        + "resultEntry=[" + resultEntry + "]");

    // Check that the task state is as expected.
    String stateString = parseAttribute(resultEntry, ATTR_TASK_STATE).asString();
    TaskState taskState = TaskState.fromString(stateString);
    assertEquals(taskState, expectedState,
                 "The task completed in an unexpected state");

    // Check that the task contains some log messages.
    Set<String> logMessages = parseAttribute(resultEntry, ATTR_TASK_LOG_MESSAGES).asSetOfString();
    if (taskState != TaskState.COMPLETED_SUCCESSFULLY && logMessages.isEmpty())
    {
      fail("No log messages were written to the task entry on a failed task.\n"
          + "taskState=" + taskState
          + "logMessages size=" + logMessages.size() + " and content=[" + logMessages + "]");
    }
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
  public static Task getTask(DN taskEntryDN) throws Exception
  {
    TaskBackend taskBackend =
         (TaskBackend) DirectoryServer.getBackend(DN.valueOf("cn=tasks"));
    Task task = taskBackend.getScheduledTask(taskEntryDN);
    if (task == null)
    {
      long stopWaitingTime = System.currentTimeMillis() + 10000L;
      while (task == null && System.currentTimeMillis() < stopWaitingTime)
      {
        Thread.sleep(10);
        task = taskBackend.getScheduledTask(taskEntryDN);
      }
    }

    assertNotNull(task, "There is no such task " + taskEntryDN);
    return task;
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
  public static Task getCompletedTask(DN taskEntryDN) throws Exception
  {
    Task task = getTask(taskEntryDN);

    if (! TaskState.isDone(task.getTaskState()))
    {
      long stopWaitingTime = System.currentTimeMillis() + 20000L;
      while (! TaskState.isDone(task.getTaskState()) &&
             System.currentTimeMillis() < stopWaitingTime)
      {
        Thread.sleep(10);
      }
    }

    assertTrue(TaskState.isDone(task.getTaskState()), "Task " + taskEntryDN + " did not complete in a timely manner.");
    return task;
  }
}
