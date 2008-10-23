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



import static org.opends.messages.TaskMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used to stop the server.
 */
public class ShutdownTask
       extends Task
{



  // Indicates whether to use an exit code that indicates the server should be
  // restarted.
  private boolean restart;

  // The shutdown message that will be used.
  private Message shutdownMessage;


  /**
   * {@inheritDoc}
   */
  public Message getDisplayName() {
    return INFO_TASK_SHUTDOWN_NAME.get();
  }

  /**
   * Performs any task-specific initialization that may be required before
   * processing can start.  This default implementation does not do anything,
   * but subclasses may override it as necessary.  This method will be called at
   * the time the task is scheduled, and therefore any failure in this method
   * will be returned to the client.
   *
   * @throws  DirectoryException  If a problem occurs during initialization that
   *                              should be returned to the client.
   */
  public void initializeTask()
         throws DirectoryException
  {
    // See if the entry contains a shutdown message.  If so, then use it.
    // Otherwise, use a default message.
    Entry taskEntry = getTaskEntry();

    restart         = false;
    shutdownMessage = INFO_TASK_SHUTDOWN_DEFAULT_MESSAGE.get(
        String.valueOf(taskEntry.getDN()));

    AttributeType attrType =
         DirectoryServer.getAttributeType(ATTR_SHUTDOWN_MESSAGE, true);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if ((attrList != null) && (attrList.size() > 0))
    {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty())
      {
        String valueString = attr.iterator().next()
            .getStringValue();

        shutdownMessage = INFO_TASK_SHUTDOWN_CUSTOM_MESSAGE.get(String
            .valueOf(taskEntry.getDN()), String.valueOf(valueString));
      }
    }


    attrType = DirectoryServer.getAttributeType(ATTR_RESTART_SERVER, true);
    attrList = taskEntry.getAttribute(attrType);
    if ((attrList != null) && (attrList.size() > 0))
    {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty())
      {
        String valueString = toLowerCase(attr.iterator().next()
            .getStringValue());

        restart = (valueString.equals("true") || valueString.equals("yes")
            || valueString.equals("on") || valueString.equals("1"));
      }
    }


    // If the client connection is available, then make sure the associated
    // client has either the SERVER_SHUTDOWN or SERVER_RESTART privilege, based
    // on the appropriate action.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (restart)
      {
        if (! clientConnection.hasPrivilege(Privilege.SERVER_RESTART,
                                            operation))
        {
          Message message =
              ERR_TASK_SHUTDOWN_INSUFFICIENT_RESTART_PRIVILEGES.get();
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                       message);
        }
      }
      else
      {
        if (! clientConnection.hasPrivilege(Privilege.SERVER_SHUTDOWN,
                                            operation))
        {
          Message message =
              ERR_TASK_SHUTDOWN_INSUFFICIENT_SHUTDOWN_PRIVILEGES.get();
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                       message);
        }
      }
    }
  }



  /**
   * Performs the actual core processing for this task.  This method should not
   * return until all processing associated with this task has completed.
   *
   * @return  The final state to use for the task.
   */
  public TaskState runTask()
  {
    // This is a unique case in that the shutdown cannot finish until this task
    // is finished, but this task can't really be finished until the shutdown is
    // complete.  To work around this catch-22, we'll spawn a separate thread
    // that will be responsible for really invoking the shutdown and then this
    // method will return.  We'll have to use different types of threads
    // depending on whether we're doing a restart or a shutdown.
    if (restart)
    {
      RestartTaskThread restartThread = new RestartTaskThread(shutdownMessage);
      restartThread.start();
    }
    else
    {
      ShutdownTaskThread shutdownThread =
           new ShutdownTaskThread(shutdownMessage);
      shutdownThread.start();
    }

    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}

