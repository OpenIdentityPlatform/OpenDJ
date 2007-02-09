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



import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.Operation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.TaskMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used to stop the server.
 */
public class ShutdownTask
       extends Task
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.ShutdownTask";



  // Indicates whether to use an exit code that indicates the server should be
  // restarted.
  private boolean restart;

  // The shutdown message that will be used.
  private String shutdownMessage;



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
    assert debugEnter(CLASS_NAME, "initializeTask");


    // See if the entry contains a shutdown message.  If so, then use it.
    // Otherwise, use a default message.
    Entry taskEntry = getTaskEntry();

    restart         = false;
    shutdownMessage = getMessage(MSGID_TASK_SHUTDOWN_DEFAULT_MESSAGE,
                                 String.valueOf(taskEntry.getDN()));

    AttributeType attrType =
         DirectoryServer.getAttributeType(ATTR_SHUTDOWN_MESSAGE, true);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if ((attrList != null) && (attrList.size() > 0))
    {
      Attribute attr = attrList.get(0);
      LinkedHashSet<AttributeValue> values = attr.getValues();
      if ((values != null) && (! values.isEmpty()))
      {
        String valueString = values.iterator().next().getStringValue();

        shutdownMessage = getMessage(MSGID_TASK_SHUTDOWN_CUSTOM_MESSAGE,
                                     String.valueOf(taskEntry.getDN()),
                                     String.valueOf(valueString));
      }
    }


    attrType = DirectoryServer.getAttributeType(ATTR_RESTART_SERVER, true);
    attrList = taskEntry.getAttribute(attrType);
    if ((attrList != null) && (attrList.size() > 0))
    {
      Attribute attr = attrList.get(0);
      LinkedHashSet<AttributeValue> values = attr.getValues();
      if ((values != null) && (! values.isEmpty()))
      {
        String valueString =
             toLowerCase(values.iterator().next().getStringValue());

        restart = (valueString.equals("true") || valueString.equals("yes") ||
                   valueString.equals("on") || valueString.equals("1"));
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
          int    msgID   = MSGID_TASK_SHUTDOWN_INSUFFICIENT_RESTART_PRIVILEGES;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                       message, msgID);
        }
      }
      else
      {
        if (! clientConnection.hasPrivilege(Privilege.SERVER_SHUTDOWN,
                                            operation))
        {
          int    msgID   = MSGID_TASK_SHUTDOWN_INSUFFICIENT_SHUTDOWN_PRIVILEGES;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                       message, msgID);
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
    assert debugEnter(CLASS_NAME, "runTask");


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

