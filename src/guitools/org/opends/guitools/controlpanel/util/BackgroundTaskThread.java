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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import javax.swing.SwingUtilities;

/**
* This class defines a thread that will be used to actually perform the
* processing for a background task.
* @param <T> type of object returned by the background task fed to this
* object
*/
class BackgroundTaskThread<T>
     extends Thread
{
 // The background task that is to be processed.
 private final BackgroundTask<T> backgroundTask;



 /**
  * Creates a new background task thread that will be used to process the
  * provided task.
  *
  * @param  backgroundTask  The task to be processed.
  */
 public BackgroundTaskThread(BackgroundTask<T> backgroundTask)
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
     final T returnValue = backgroundTask.processBackgroundTask();
     SwingUtilities.invokeLater(new Runnable()
     {
       /**
        * {@inheritDoc}
        */
       public void run()
       {
         backgroundTask.backgroundTaskCompleted(returnValue, null);
       }
     });
   }
   catch (final Throwable t)
   {
     if (!isInterrupted())
     {
       SwingUtilities.invokeLater(new Runnable()
       {
         /**
          * {@inheritDoc}
          */
         public void run()
         {
           backgroundTask.backgroundTaskCompleted(null, t);
         }
       });
     }
   }
 }
}
