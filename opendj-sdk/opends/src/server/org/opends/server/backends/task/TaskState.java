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
import static org.opends.messages.TaskMessages.*;


/**
 * This enumeration defines the various states that a task can have during its
 * lifetime.
 */
public enum TaskState
{
  /**
   * The task state that indicates that the task has not yet been scheduled,
   * or possibly that the scheduler is currently not running.
   */
  UNSCHEDULED(INFO_TASK_STATE_UNSCHEDULED.get()),



  /**
   * The task state that indicates that the task has been disabled by an
   * administrator.
   */
  DISABLED(INFO_TASK_STATE_DISABLED.get()),



  /**
   * The task state that indicates that the task's scheduled start time has not
   * yet arrived.
   */
  WAITING_ON_START_TIME(INFO_TASK_STATE_WAITING_ON_START_TIME.get()),



  /**
   * The task state that indicates that at least one of the task's defined
   * dependencies has not yet completed.
   */
  WAITING_ON_DEPENDENCY(INFO_TASK_STATE_WAITING_ON_DEPENDENCY.get()),



  /**
   * The task state that indicates that the task is currently running.
   */
  RUNNING(INFO_TASK_STATE_RUNNING.get()),



  /**
   * The task state that indicates that the task has completed without any
   * errors.
   */
  COMPLETED_SUCCESSFULLY(INFO_TASK_STATE_COMPLETED_SUCCESSFULLY.get()),



  /**
   * The task state that indicates that the task was able to complete its
   * intended goal, but that one or more errors were encountered during the
   * process.
   */
  COMPLETED_WITH_ERRORS(INFO_TASK_STATE_COMPLETED_WITH_ERRORS.get()),



  /**
   * The task state that indicates that the task was unable to complete because
   * it was interrupted by the shutdown of the task backend.
   */
  STOPPED_BY_SHUTDOWN(INFO_TASK_STATE_STOPPED_BY_SHUTDOWN.get()),



  /**
   * The task state that indicates that one or more errors prevented the task
   * from completing.
   */
  STOPPED_BY_ERROR(INFO_TASK_STATE_STOPPED_BY_ERROR.get()),



  /**
   * The task state that indicates that the task was stopped by an administrator
   * after it had already started but before it was able to complete.
   */
  STOPPED_BY_ADMINISTRATOR(INFO_TASK_STATE_STOPPED_BY_ADMINISTRATOR.get()),



  /**
   * The task state that indicates that the task was canceled by an
   * administrator before it started running.
   */
  CANCELED_BEFORE_STARTING(INFO_TASK_STATE_CANCELED_BEFORE_STARTING.get());






  /**
   * Indicates whether a task with the specified state is currently pending
   * execution.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the stask tate indicates that the task is
   *          currently pending, or <CODE>false</CODE> otherwise.
   */
  public static boolean isPending(TaskState taskState)
  {
    switch (taskState)
    {
      case UNSCHEDULED:
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether a task with the specified state is currently running.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the task state indicates that the task is
   *          currently running, or <CODE>false</CODE> otherwise.
   */
  public static boolean isRunning(TaskState taskState)
  {
    switch (taskState)
    {
      case RUNNING:
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether a task with the specified state has completed all the
   * processing that it will do, regardless of whether it completed its
   * intended goal.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  <CODE>false</CODE> if the task state indicates that the task has
   *          not yet started or is currently running, or <CODE>true</CODE>
   *          otherwise.
   */
  public static boolean isDone(TaskState taskState)
  {
    switch (taskState)
    {
      case UNSCHEDULED:
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
      case RUNNING:
        return false;
      default:
        return true;
    }
  }



  /**
   * Indicates whether a task with the specified state has been able to complete
   * its intended goal.
   *
   * @param  taskState  The task state for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the task state indicates that the task
   *          completed successfully or with minor errors that still allowed it
   *          to achieve its goal, or <CODE>false</CODE> otherwise.
   */
  public static boolean isSuccessful(TaskState taskState)
  {
    switch (taskState)
    {
      case WAITING_ON_START_TIME:
      case WAITING_ON_DEPENDENCY:
      case RUNNING:
      case STOPPED_BY_ERROR:
        return false;
      default:
        return true;
    }
  }



  /**
   * Retrieves the task state that corresponds to the provided string value.
   *
   * @param  s  The string value for which to retrieve the corresponding task
   *            state.
   *
   * @return  The corresponding task state, or <CODE>null</CODE> if none could
   *          be associated with the provided string.
   */
  public static TaskState fromString(String s)
  {
    String lowerString = s.toLowerCase();
    if (lowerString.equals("unscheduled"))
    {
      return UNSCHEDULED;
    }
    else if (lowerString.equals("disabled"))
    {
      return DISABLED;
    }
    else if (lowerString.equals("waiting_on_start_time"))
    {
      return WAITING_ON_START_TIME;
    }
    else if (lowerString.equals("waiting_on_dependency"))
    {
      return WAITING_ON_DEPENDENCY;
    }
    else if (lowerString.equals("running"))
    {
      return RUNNING;
    }
    else if (lowerString.equals("completed_successfully"))
    {
      return COMPLETED_SUCCESSFULLY;
    }
    else if (lowerString.equals("completed_with_errors"))
    {
      return COMPLETED_WITH_ERRORS;
    }
    else if (lowerString.equals("stopped_by_shutdown"))
    {
      return STOPPED_BY_SHUTDOWN;
    }
    else if (lowerString.equals("stopped_by_error"))
    {
      return STOPPED_BY_ERROR;
    }
    else if (lowerString.equals("stopped_by_administrator"))
    {
      return STOPPED_BY_ADMINISTRATOR;
    }
    else if (lowerString.equals("canceled_before_starting"))
    {
      return CANCELED_BEFORE_STARTING;
    }
    else
    {
      return null;
    }
  }

  private Message displayName;

  /**
   * Gets a locale sensitive representation of this state.
   *
   * @return Message describing state
   */
  public Message getDisplayName() {
    return displayName;
  }

  private TaskState(Message displayName) {
    this.displayName = displayName;
  }
}

