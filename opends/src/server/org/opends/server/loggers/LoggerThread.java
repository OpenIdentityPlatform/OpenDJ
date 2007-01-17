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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.Debug.*;

/**
 * This class defines a thread that will be used for performing asynchronous
 * operations on the log files.
 */
public class LoggerThread extends DirectoryThread
       implements ServerShutdownListener
{

  private static final String CLASS_NAME =
    "org.opends.server.loggers.LoggerThread";

  private CopyOnWriteArrayList<RotationPolicy> rotationPolicies;
  private CopyOnWriteArrayList<RetentionPolicy> retentionPolicies;
  private LoggerAlarmHandler handler;
  private int time;
  private boolean stopRequested;
  private Thread loggerThread;

  /**
   * Create the logger thread along with the specified sleep time,
   * the handler for the alarm that this thread generates and the
   * rotation policy.
   *
   * @param  name      The name to use for this logger thread.
   * @param  time      The length of time in milliseconds to sleep between
   *                   checks to see if any action is needed.
   * @param  handler   The alarm handler to use if a problem occurs.
   * @param  policies  The set of rotation policies to be enforced.
   * @param  rp        The set of retention policies to be enforced.
   */
  public LoggerThread(String name, int time, LoggerAlarmHandler handler,
          CopyOnWriteArrayList<RotationPolicy> policies,
          CopyOnWriteArrayList<RetentionPolicy> rp)
  {
    super(name);

    this.time = time;
    this.handler = handler;
    this.rotationPolicies = policies;
    this.retentionPolicies = rp;

    loggerThread = null;
    stopRequested = false;

    DirectoryServer.registerShutdownListener(this);
  }

  /**
   * The run method of the thread. It wakes up periodically and
   * checks whether the file needs to be rotated based on the
   * rotation policy.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");

    this.loggerThread = Thread.currentThread();

    while(! stopRequested)
    {
      try
      {
        sleep(time);
      }
      catch(InterruptedException e)
      {
        // We expect this to happen.
      }
      catch(Exception e)
      {
        assert debugException(CLASS_NAME, "run", e);
      }

      handler.flush();

      if(rotationPolicies != null)
      {
        for(RotationPolicy rotationPolicy : rotationPolicies)
        {
          if(rotationPolicy.rotateFile())
          {
            handler.rollover();
          }
        }
      }

      if(retentionPolicies != null)
      {
        for(RetentionPolicy retentionPolicy : retentionPolicies)
        {
          int numFilesDeleted = retentionPolicy.deleteFiles();
          System.out.println(numFilesDeleted + " files deleted");
        }
      }
    }
  }



  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  public String getShutdownListenerName()
  {
    assert debugEnter(CLASS_NAME, "getShutdownListenerName");

    return "Logger Thread " + getName();
  }



  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this shutdown listener should take any action necessary to prepare
   * for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public void processServerShutdown(String reason)
  {
    assert debugEnter(CLASS_NAME, "processServerShutdown",
                      String.valueOf(reason));

    stopRequested = true;

    try
    {
      if (loggerThread != null)
      {
        loggerThread.interrupt();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "processServerShutdown", e);
    }
  }
}

