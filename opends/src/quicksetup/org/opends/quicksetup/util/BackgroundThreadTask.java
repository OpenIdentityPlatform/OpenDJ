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
package org.opends.quicksetup.util;



/**
 * This class defines a thread that will be used to actually perform the
 * processing for a background task.
 */
class BackgroundTaskThread
      extends Thread
{
  // The background task that is to be processed.
  private final BackgroundTask backgroundTask;



  /**
   * Creates a new background task thread that will be used to process the
   * provided task.
   *
   * @param  backgroundTask  The task to be processed.
   */
  public BackgroundTaskThread(BackgroundTask backgroundTask)
  {
    this.backgroundTask = backgroundTask;
  }



  /**
   * Performs the processing associated with the background task.
   */
  public void run()
  {
    try
    {
      Object returnValue = backgroundTask.processBackgroundTask();
      backgroundTask.backgroundTaskCompleted(returnValue, null);
    }
    catch (Throwable t)
    {
      backgroundTask.backgroundTaskCompleted(null, t);
    }
  }
}
