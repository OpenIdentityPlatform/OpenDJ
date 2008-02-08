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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;
import org.opends.messages.Message;



import org.opends.server.core.DirectoryServer;




/**
 * This class defines a thread that will be spawned to invoke a Directory Server
 * in-core restart.  That is, the server will perform an internal shutdown, and
 * will then re-bootstrap and start itself up again without ever exiting the
 * JVM.
 * <BR><BR>
 * Note that there are two significant differences between this thread and the
 * shutdown task thread (other than the obvious difference that this one does a
 * restart while the other does a shutdown):  this class extends
 * <CODE>java.lang.Thread</CODE> instead of
 * <CODE>org.opends.server.core.DirectoryThread</CODE>, and this thread is not a
 * daemon thread.  These changes are needed to guarantee that the JVM does not
 * exit before we get a chance to restart it if all non-daemon threads go away.
 */
public class RestartTaskThread
       extends Thread
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.RestartTaskThread";



  // The shutdown message that will be used.
  private Message shutdownMessage;



  /**
   * Creates a new instance of this shutdown task thread with the provided
   * message.
   *
   * @param  shutdownMessage  The shutdown message that will be used.
   */
  public RestartTaskThread(Message shutdownMessage)
  {
    super("Restart Task Thread");


    this.shutdownMessage = shutdownMessage;
  }



  /**
   * Invokes the Directory Server shutdown process.
   */
  public void run()
  {
    DirectoryServer.restart(CLASS_NAME, shutdownMessage);
  }
}

