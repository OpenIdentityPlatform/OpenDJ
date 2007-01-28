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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */


package org.opends.server.tasks;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.schema.DirectoryStringSyntax;
import static org.opends.server.config.ConfigConstants.
     ATTR_TASK_COMPLETION_TIME;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_STATE;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_LOG_MESSAGES;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.*;

import java.util.ArrayList;

/**
 * A base class for all tasks test cases.
 */
@Test(groups = { "precommit", "tasks" })
public class TasksTestCase extends DirectoryServerTestCase {

  /**
   * Add a task definition and check that it completes with the expected state.
   * The task is expected to complete quickly and the timeout is set
   * accordingly.
   * @param taskEntry The task entry.
   * @param expectedState The expected completion state of the task.
   * @throws Exception If the test fails.
   */
  protected void testTask(Entry taskEntry, TaskState expectedState)
       throws Exception
  {
    testTask(taskEntry, expectedState, 30);
  }

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
    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the task.
    AddOperation addOperation =
         connection.processAdd(taskEntry.getDN(),
                               taskEntry.getObjectClasses(),
                               taskEntry.getUserAttributes(),
                               taskEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                 "Add of the task definition was not successful");

    // Wait until the task completes.
    AttributeType completionTimeType = DirectoryServer.getAttributeType(
         ATTR_TASK_COMPLETION_TIME.toLowerCase());
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectclass=*)");
    Entry resultEntry = null;
    String completionTime = null;
    long startMillisecs = System.currentTimeMillis();
    do
    {
      InternalSearchOperation searchOperation =
           connection.processSearch(taskEntry.getDN(),
                                    SearchScope.BASE_OBJECT,
                                    filter);
      try
      {
        resultEntry = searchOperation.getSearchEntries().getFirst();
      } catch (Exception e)
      {
        // FIXME How is this possible?  Must be issue 858.
//        fail("Task entry was not returned from the search.");
        continue;
      }
      completionTime =
           resultEntry.getAttributeValue(completionTimeType,
                                         DirectoryStringSyntax.DECODER);

      if (completionTime == null)
      {
        if (System.currentTimeMillis() - startMillisecs > 1000*timeout)
        {
          break;
        }
        Thread.sleep(10);
      }
    } while (completionTime == null);

    if (completionTime == null)
    {
      fail("The task had not completed after " + timeout + " seconds.");
    }

    // Check that the task state is as expected.
    AttributeType taskStateType =
         DirectoryServer.getAttributeType(ATTR_TASK_STATE.toLowerCase());
    String stateString =
         resultEntry.getAttributeValue(taskStateType,
                                       DirectoryStringSyntax.DECODER);
    TaskState taskState = TaskState.fromString(stateString);
    assertEquals(taskState, expectedState,
                 "The task completed in an unexpected state");

    // Check that the task contains some log messages.
    AttributeType logMessagesType = DirectoryServer.getAttributeType(
         ATTR_TASK_LOG_MESSAGES.toLowerCase());
    ArrayList<String> logMessages = new ArrayList<String>();
    resultEntry.getAttributeValues(logMessagesType,
                                   DirectoryStringSyntax.DECODER,
                                   logMessages);
    if (taskState != TaskState.COMPLETED_SUCCESSFULLY &&
        logMessages.size() == 0)
    {
      fail("No log messages were written to the task entry on a failed task");
    }
  }

}
