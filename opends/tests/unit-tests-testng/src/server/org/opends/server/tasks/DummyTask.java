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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;



/**
 * This class provides an implementation of a Directory Server task always
 * completes successfully.  It is intended only for testing purposes.
 */
public class DummyTask
       extends Task
{
  // The length of time that the task should sleep before completing.
  private long sleepTime;

  // The task state to use when interrupting the task.  This will be null unless
  // the task gets interrupted.
  private volatile TaskState interruptedState;



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeTask()
         throws DirectoryException
  {
    sleepTime = 0;
    interruptedState = null;

    Entry taskEntry = getTaskEntry();
    if (taskEntry != null)
    {
      List<Attribute> attrList =
           taskEntry.getAttribute("ds-task-dummy-sleep-time");
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            sleepTime = Long.parseLong(v.getStringValue());
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    long stopTime = System.currentTimeMillis() + sleepTime;
    while ((interruptedState == null) &&
           (System.currentTimeMillis() < stopTime))
    {
      try
      {
        Thread.sleep(10);
      } catch (Exception e) {}
    }

    if (interruptedState == null)
    {
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
    else
    {
      return interruptedState;
    }
  }



  /**
   * {@inheritDoc}
   */
  public void interruptTask(TaskState taskState, Message interruptMessage)
  {
    interruptedState = taskState;
  }
}

