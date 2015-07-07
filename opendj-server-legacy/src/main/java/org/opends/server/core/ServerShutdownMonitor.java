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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.Iterator;
import java.util.LinkedList;

import org.opends.server.api.DirectoryThread;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a daemon thread that will be used to monitor the server
 * shutdown process and may help nudge it along if it appears to get hung.
 */
class ServerShutdownMonitor extends DirectoryThread
{
  /**
   * Time in milliseconds for the shutdown monitor to:
   * <ol>
   * <li>wait before sending interrupt to threads</li>
   * <li>wait before final shutdown</li>
   * </ol>
   */
  static final long WAIT_TIME = 30000;

  /**
   * Indicates whether the monitor has completed and the shutdown may be
   * finalized with a call to {@link System#exit()}.
   */
  private volatile boolean monitorDone;

  /** The list of threads that need to be monitored. */
  private final LinkedList<Thread> threadList;



  /**
   * Creates a new instance of this shutdown monitor thread that will collect
   * information about the threads that need to be watched to ensure that they
   * shut down properly.
   */
  public ServerShutdownMonitor()
  {
    super("Directory Server Shutdown Monitor");
    setDaemon(true);


    // Get the thread ID of the current thread, since it is the one that is
    // actually processing the server shutdown and therefore shouldn't be
    // interrupted or impeded.
    long currentThreadID = Thread.currentThread().getId();


    // Get the Directory Server thread group and identify all of the non-daemon
    // threads that are currently active.  This can be an inexact science, so
    // we'll make sure to allocate enough room for double the threads that we
    // think are currently running.
    threadList = new LinkedList<>();
    ThreadGroup threadGroup = DirectoryThread.DIRECTORY_THREAD_GROUP;
    Thread[] threadArray = new Thread[threadGroup.activeCount() * 2];
    int numThreads = threadGroup.enumerate(threadArray, true);
    for (int i=0; i < numThreads; i++)
    {
      Thread t = threadArray[i];
      if (t.isAlive() && !t.isDaemon() && t.getId() != currentThreadID)
      {
        threadList.add(t);
      }
    }

    monitorDone = true;
  }



  /**
   * Operates in a loop, waiting for all threads to be stopped.  At certain
   * milestones, if there are threads still running then it will attempt to
   * get them to stop.
   */
  @Override
  public void run()
  {
    monitorDone = false;

    try
    {
      // First, check to see if we need to do anything at all.  If all threads
      // are stopped, then we don't have a problem.
      removeDeadThreads();
      if (threadList.isEmpty())
      {
        return;
      }

      // For the first milestone, we'll run for up to 30 seconds just checking
      // to see whether all threads have stopped yet.
      if (waitAllThreadsDied(WAIT_TIME))
      {
        return;
      }

      // Now we're at the second milestone, where we'll interrupt all threads
      // that are still running and then wait for up to 30 more seconds for them
      // to stop.
      for (Thread t : threadList)
      {
        try
        {
          if (t.isAlive())
          {
            t.interrupt();
          }
        } catch (Exception e) {}
      }

      if (waitAllThreadsDied(WAIT_TIME))
      {
        return;
      }

      // At this time, we could try to stop or destroy any remaining threads,
      // but we won't do that because we'll use a System.exit in the thread that
      // initiated a shutdown and it should take care of anything else that
      // might still be running.  Nevertheless, we'll print an error message to
      // standard error so that an administrator might see something that needs
      // to be investigated further.
      System.err.println("WARNING:  The following threads were still active " +
                         "after waiting up to 60 seconds for them to stop:");

      for (Thread t : threadList)
      {
        System.err.println("Thread Name:  " + t.getName());
        System.err.println("Stack Trace:");

        for (StackTraceElement e : t.getStackTrace())
        {
          System.err.print("              " + e.getClassName() + "." +
                           e.getMethodName() + "(" + e.getFileName() + ":");

          if (e.isNativeMethod())
          {
            System.err.print("native method");
          }
          else
          {
            System.err.print(e.getLineNumber());
          }

          System.err.println(")");
          System.err.println();
        }
      }
    }
    finally
    {
      monitorDone = true;
    }
  }

  private boolean waitAllThreadsDied(final long maxWaitMillis)
  {
    final long stopTime = System.currentTimeMillis() + maxWaitMillis;
    while (System.currentTimeMillis() < stopTime)
    {
      removeDeadThreads();
      if (threadList.isEmpty())
      {
        return true;
      }

      StaticUtils.sleep(10);
    }
    return false;
  }

  private void removeDeadThreads()
  {
    for (Iterator<Thread> iter = threadList.iterator(); iter.hasNext();)
    {
      final Thread t = iter.next();
      if (!t.isAlive())
      {
        iter.remove();
      }
    }
  }

  /**
   * Waits for the monitor thread to complete any necessary processing.  This
   * method will not return until the monitor thread has stopped running.
   */
  public void waitForMonitor()
  {
    while (! monitorDone)
    {
      StaticUtils.sleep(10);
    }
  }
}
