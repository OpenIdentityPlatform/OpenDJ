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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * This class provides an implementation of a Directory Server task always
 * completes successfully.  It is intended only for testing purposes.
 */
public class DummyTask extends Task
{
  /** Re-using an existing attribute to handle sleep time attribute. */
  public final static String TASK_SLEEP_TIME_ATTRIBUTE = "ds-cfg-time-limit";

  /** The length of time that the task should sleep before completing. */
  private long sleepTime;

  /**
   * The task state to use when interrupting the task.  This will be
   * null unless the task gets interrupted.
   */
  private volatile TaskState interruptedState;

  @Override
  public LocalizableMessage getDisplayName() {
    return LocalizableMessage.raw("Dummy");
  }

  @Override
  public void initializeTask() throws DirectoryException
  {
    sleepTime = 0;
    interruptedState = null;

    Entry taskEntry = getTaskEntry();
    if (taskEntry != null)
    {
      for (Attribute a : taskEntry.getAttribute(TASK_SLEEP_TIME_ATTRIBUTE))
      {
        for (ByteString v : a)
        {
          sleepTime = Long.parseLong(v.toString());
        }
      }
    }
  }

  @Override
  protected TaskState runTask()
  {
    long stopTime = System.currentTimeMillis() + sleepTime;
    while (interruptedState == null && System.currentTimeMillis() < stopTime)
    {
      try
      {
        Thread.sleep(10);
      } catch (InterruptedException e) {}
    }

    if (interruptedState != null)
    {
      return interruptedState;
    }
    return TaskState.COMPLETED_SUCCESSFULLY;
  }

  @Override
  public boolean isInterruptable()
  {
    return true;
  }

  @Override
  public void interruptTask(TaskState taskState, LocalizableMessage interruptMessage)
  {
    interruptedState = taskState;
    setTaskInterruptState(taskState);
  }
}
