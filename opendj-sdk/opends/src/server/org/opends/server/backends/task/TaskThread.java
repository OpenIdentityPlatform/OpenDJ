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
package org.opends.server.backends.task;
import org.opends.messages.Message;



import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.BackendMessages.*;

import static org.opends.server.util.StaticUtils.*;

import java.util.Map;



/**
 * This class defines a thread that will be used to execute a scheduled task
 * within the server and provide appropriate notification that the task is
 * complete.
 */
public class TaskThread
       extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // Indicates whether a request has been made for this thread to exit.
  private boolean exitRequested;

  // The thread ID for this task thread.
  private int threadID;

  // The reference to the scheduler with which this thread is associated.
  private TaskScheduler taskScheduler;

  // The object that will be used for signaling the thread when there is new
  // work to perform.
  private Object notifyLock;



  /**
   * Creates a new task thread with the provided information.
   *
   * @param  taskScheduler  The reference to the task scheduler with which this
   *                        thread is associated.
   * @param  threadID       The ID assigned to this task thread.
   */
  public TaskThread(TaskScheduler taskScheduler, int threadID)
  {
    super("Task Thread " + threadID);


    this.taskScheduler = taskScheduler;
    this.threadID      = threadID;

    notifyLock    = new Object();
    exitRequested = false;

    setAssociatedTask(null);
  }



  /**
   * Retrieves the task currently being processed by this thread, if it is
   * active.
   *
   * @return  The task currently being processed by this thread, or
   *          <CODE>null</CODE> if it is not processing any task.
   */
  public Task getTask()
  {
    return getAssociatedTask();
  }



  /**
   * Provides a new task for processing by this thread.  This does not do any
   * check to ensure that no task is already in process.
   *
   * @param  task  The task to be processed.
   */
  public void setTask(Task task)
  {
    setAssociatedTask(task);

    synchronized (notifyLock)
    {
      notifyLock.notify();
    }
  }



  /**
   * Attempts to interrupt processing on the task in progress.
   *
   * @param  interruptState   The state to use for the task if it is
   *                          successfully interrupted.
   * @param  interruptReason  The human-readable reason that the task is to be
   *                          interrupted.
   * @param  exitThread       Indicates whether this thread should exit when
   *                          processing on the active task has completed.
   */
  public void interruptTask(TaskState interruptState, Message interruptReason,
                            boolean exitThread)
  {
    if (getAssociatedTask() != null)
    {
      try
      {
        getAssociatedTask().interruptTask(interruptState, interruptReason);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (exitThread)
    {
      exitRequested = true;
    }
  }



  /**
   * Operates in a loop, sleeping until there is no work to do, then
   * processing the task and returning to the scheduler for more work.
   */
  public void run()
  {
    while (! exitRequested)
    {
      if (getAssociatedTask() == null)
      {
        try
        {
          synchronized (notifyLock)
          {
            notifyLock.wait(5000);
          }
        }
        catch (InterruptedException ie)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ie);
          }
        }

        continue;
      }

      try
      {
        TaskState returnState = getAssociatedTask().execute();
        getAssociatedTask().setTaskState(returnState);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Task task = getAssociatedTask();

        Message message = ERR_TASK_EXECUTE_FAILED.
            get(String.valueOf(task.getTaskEntry().getDN()),
                stackTraceToSingleLineString(e));
        logError(message);
        task.setTaskState(TaskState.STOPPED_BY_ERROR);
      }

      Task completedTask = getAssociatedTask();
      setAssociatedTask(null);
      if (! taskScheduler.threadDone(this, completedTask))
      {
        exitRequested = true;
        break;
      }
    }

    if (getAssociatedTask() != null)
    {
      Task task = getAssociatedTask();
      task.setTaskState(TaskState.STOPPED_BY_SHUTDOWN);
      taskScheduler.threadDone(this, task);
    }
  }



  /**
   * Retrieves any relevent debug information with which this tread is
   * associated so they can be included in debug messages.
   *
   * @return debug information about this thread as a string.
   */
  public Map<String, String> getDebugProperties()
  {
    Map<String, String> properties = super.getDebugProperties();

    if (getAssociatedTask() != null)
    {
      properties.put("task", getAssociatedTask().toString());
    }

    return properties;
  }
}

