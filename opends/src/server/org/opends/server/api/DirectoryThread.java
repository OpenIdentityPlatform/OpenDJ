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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.Map;
import java.util.LinkedHashMap;

import org.opends.server.backends.task.Task;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.loggers.debug.DebugLogger.
    debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import static org.opends.server.util.StaticUtils.stackTraceToString;
import static org.opends.server.util.ServerConstants.
    ALERT_TYPE_UNCAUGHT_EXCEPTION;
import static org.opends.server.util.ServerConstants.
    ALERT_DESCRIPTION_UNCAUGHT_EXCEPTION;
import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.
    ERR_UNCAUGHT_THREAD_EXCEPTION;


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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=true,
     mayInvoke=true)
public class DirectoryThread
       extends Thread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The directory thread group that all directory threads will be a
   * member of.
   */
  public static final DirectoryThreadGroup DIRECTORY_THREAD_GROUP =
      new DirectoryThreadGroup();

  // The stack trace taken at the time that this thread was created.
  private StackTraceElement[] creationStackTrace;

  // The task with which this thread is associated, if any.
  private Task task;

  // A reference to the thread that was used to create this thread.
  private Thread parentThread;

  /**
   * A thread group for all directory threads. This implements a
   * custom unhandledException handler that logs the error.
   */
  private static class DirectoryThreadGroup extends ThreadGroup
      implements AlertGenerator
  {
    private final LinkedHashMap<String,String> alerts;

    /**
     * Private constructor for DirectoryThreadGroup.
     */
    private DirectoryThreadGroup()
    {
      super("Directory Server Thread Group");
      alerts = new LinkedHashMap<String,String>();
      alerts.put(ALERT_TYPE_UNCAUGHT_EXCEPTION,
          ALERT_DESCRIPTION_UNCAUGHT_EXCEPTION);
    }

    /**
     * {@inheritDoc}
     */
    public DN getComponentEntryDN() {
      return DN.NULL_DN;
    }

    /**
     * {@inheritDoc}
     */
    public String getClassName() {
      return "org.oepnds.server.api.DirectoryThread";
    }

    /**
     * {@inheritDoc}
     */
    public LinkedHashMap<String, String> getAlerts() {
      return alerts;
    }

    /**
     * Provides a means of handling a case in which a thread is about
     * to die because of an unhandled exception.  This method does
     * nothing to try to prevent the death of that thread, but will
     * at least log it so that it can be available for debugging
     * purposes.
     *
     * @param  t  The thread that threw the exception.
     * @param  e  The exception that was thrown but not properly
     *            handled.
     */
    @Override
    public void uncaughtException(Thread t, Throwable e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_UNCAUGHT_THREAD_EXCEPTION.get(
          t.getName(), stackTraceToString(e));
      logError(message);
      DirectoryServer.sendAlertNotification(this,
          ALERT_TYPE_UNCAUGHT_EXCEPTION, message);
    }
  }

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
    super (DIRECTORY_THREAD_GROUP, target,
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
    super(DIRECTORY_THREAD_GROUP, threadName);


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

    if (DirectoryServer.getEnvironmentConfig().forceDaemonThreads())
    {
      setDaemon(true);
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
   *          {@code null} if there is none.
   */
  public Task getAssociatedTask()
  {
    return task;
  }



  /**
   * Sets the task with which this thread is associated.  It may be
   * {@code null} to indicate that it is not associated with any task.
   *
   * @param  task  The task with which this thread is associated.
   */
  public void setAssociatedTask(Task task)
  {
    this.task = task;
  }


  /**
   * Retrieves any relevent debug information with which this tread is
   * associated so they can be included in debug messages.
   *
   * @return debug information about this thread as a string.
   */
  public Map<String, String> getDebugProperties()
  {
    LinkedHashMap<String, String> properties =
        new LinkedHashMap<String, String>();

    properties.put("parentThread", parentThread.getName() +
        "(" + parentThread.getId() + ")");
    properties.put("isDaemon", String.valueOf(this.isDaemon()));

    return properties;
  }
}

