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
package org.opends.server.tasks;



import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;




/**
 * This class defines a thread that will be spawned to invoke the Directory
 * Server shutdown process.  This needs to be a separate thread because the task
 * that creates it has to complete before the server can really shut down.
 */
public class ShutdownTaskThread
       extends DirectoryThread
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.ShutdownTaskThread";



  // The shutdown message that will be used.
  private String shutdownMessage;



  /**
   * Creates a new instance of this shutdown task thread with the provided
   * message.
   *
   * @param  shutdownMessage  The shutdown message that will be used.
   */
  public ShutdownTaskThread(String shutdownMessage)
  {
    super("Shutdown Task Thread");


    this.shutdownMessage = shutdownMessage;

    setDaemon(true);
  }



  /**
   * Invokes the Directory Server shutdown process.
   */
  public void run()
  {

    DirectoryServer.shutDown(CLASS_NAME, shutdownMessage);
  }
}

