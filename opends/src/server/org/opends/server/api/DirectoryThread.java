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
package org.opends.server.api;



import org.opends.server.backends.task.Task;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a generic thread that should be the superclass
 * for all threads created by the Directory Server.  That is, instead
 * of having a class that "extends Thread", you should make it
 * "extends DirectoryThread".  This provides various value-added
 * capabilities, including:
 * <BR>
 * <UL>
 *   <LI>It helps make sure that all threads have a human-readable
 *       name so they are easier to identify in stack traces.</LI>
 *   <LI>It can capture a stack trace from the time that this thread
 *       was created that could be useful for debugging purposes.</LI>
 *   <LI>It plays an important role in ensuring that log messages
 *       generated as part of the processing for Directory Server
 *       tasks are properly captured and made available as part of
 *       that task.</LI>
 * </UL>
 */
public class DirectoryThread
       extends Thread
{



  // The stack trace taken at the time that this thread was created.
  private StackTraceElement[] creationStackTrace;

  // The task with which this thread is associated, if any.
  private Task task;

  // A reference to the thread that was used to create this thread.
  private Thread parentThread;



  /**
   * Creates a new instance of this directory thread with the
   * specified name and with the specified target as its run object.
   *
   * @param  target      The target runnable object.
   * @param  threadName  The human-readable name to use for this
   *                     thread for debugging purposes.
   */
  public DirectoryThread(Runnable target, String threadName)
  {
    super (DirectoryServer.getDirectoryThreadGroup(), target,
           threadName);


    init();
  }

  /**
   * Creates a new instance of this directory thread with the
   * specified name.
   *
   * @param  threadName  The human-readable name to use for this
   *                     thread for debugging purposes.
   */
  protected DirectoryThread(String threadName)
  {
    super(DirectoryServer.getDirectoryThreadGroup(), threadName);


    init();
  }


  /**
   * Creates a new instance of this directory thread with the
   * specified name as a part of the given thread group.
   *
   * @param  threadGroup  The thread group in which this thread is to
   *                      be placed.
   * @param  threadName   The human-readable name to use for this
   *                      thread for debugging purposes.
   */
  protected DirectoryThread(ThreadGroup threadGroup,
                            String threadName)
  {
    super(threadGroup, threadName);


    init();
  }



  /**
   * private method used to factorize constructor initialization.
   */
  private void init()
  {
    parentThread       = currentThread();
    creationStackTrace = parentThread.getStackTrace();

    if (parentThread instanceof DirectoryThread)
    {
      task = ((DirectoryThread) parentThread).task;
    }
    else
    {
      task = null;
    }

    String forceDaemonStr =
         System.getProperty(PROPERTY_FORCE_DAEMON_THREADS);
    if (forceDaemonStr != null)
    {
      String lowerStr = toLowerCase(forceDaemonStr);
      if (lowerStr.equals("true") || lowerStr.equals("yes") ||
          lowerStr.equals("on") || lowerStr.equals("1"))
      {
        setDaemon(true);
      }
    }
  }



  /**
   * Retrieves the stack trace that was captured at the time that this
   * thread was created.
   *
   * @return  The stack trace that was captured at the time that this
   *          thread was created.
   */
  public StackTraceElement[] getCreationStackTrace()
  {
    return creationStackTrace;
  }



  /**
   * Retrieves a reference to the parent thread that created this
   * directory thread.  That parent thread may or may not be a
   * directory thread.
   *
   * @return  A reference to the parent thread that created this
   *          directory thread.
   */
  public Thread getParentThread()
  {
    return parentThread;
  }



  /**
   * Retrieves the task with which this thread is associated.  This
   * will only be available for threads that are used in the process
   * of running a task.
   *
   * @return  The task with which this thread is associated, or
   *          <CODE>null</CODE> if there is none.
   */
  public Task getAssociatedTask()
  {
    return task;
  }



  /**
   * Sets the task with which this thread is associated.  It may be
   * <CODE>null</CODE> to indicate that it is not associated with any
   * task.
   *
   * @param  task  The task with which this thread is associated.
   */
  public void setAssociatedTask(Task task)
  {
    this.task = task;
  }
}

