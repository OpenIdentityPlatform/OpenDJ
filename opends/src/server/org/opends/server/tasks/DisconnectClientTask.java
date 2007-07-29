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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import java.util.List;

import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.TaskMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used to terminate a client connection.
 */
public class DisconnectClientTask
       extends Task
{
  // Indicates whether to send a notification message to the client.
  private boolean notifyClient;

  // The connection ID for the client connection to terminate.
  private long connectionID;

  // The disconnect message to send to the client.
  private String disconnectMessage;



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeTask()
         throws DirectoryException
  {
    // If the client connection is available, then make sure the client has the
    // DISCONNECT_CLIENT privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection conn = operation.getClientConnection();
      if (! conn.hasPrivilege(Privilege.DISCONNECT_CLIENT, operation))
      {
        int    msgID   = MSGID_TASK_DISCONNECT_NO_PRIVILEGE;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    // Get the connection ID for the client connection.
    Entry taskEntry = getTaskEntry();
    connectionID = -1L;
    AttributeType attrType =
         DirectoryServer.getAttributeType(ATTR_TASK_DISCONNECT_CONN_ID, true);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if (attrList != null)
    {
connIDLoop:
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          try
          {
            connectionID = Long.parseLong(v.getStringValue());
            break connIDLoop;
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_TASK_DISCONNECT_INVALID_CONN_ID;
            String message = getMessage(msgID, v.getStringValue());
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID, e);
          }
        }
      }
    }

    if (connectionID < 0)
    {
      int    msgID   = MSGID_TASK_DISCONNECT_NO_CONN_ID;
      String message = getMessage(msgID, ATTR_TASK_DISCONNECT_CONN_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                   message, msgID);
    }


    // Determine whether to notify the client.
    notifyClient = false;
    attrType =
         DirectoryServer.getAttributeType(ATTR_TASK_DISCONNECT_NOTIFY_CLIENT,
                                          true);
    attrList = taskEntry.getAttribute(attrType);
    if (attrList != null)
    {
notifyClientLoop:
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          String stringValue = toLowerCase(v.getStringValue());
          if (stringValue.equals("true"))
          {
            notifyClient = true;
            break notifyClientLoop;
          }
          else if (stringValue.equals("false"))
          {
            break notifyClientLoop;
          }
          else
          {
            int    msgID   = MSGID_TASK_DISCONNECT_INVALID_NOTIFY_CLIENT;
            String message = getMessage(msgID, stringValue);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
        }
      }
    }


    // Get the disconnect message.
    disconnectMessage = getMessage(MSGID_TASK_DISCONNECT_GENERIC_MESSAGE);
    attrType = DirectoryServer.getAttributeType(ATTR_TASK_DISCONNECT_MESSAGE,
                                                true);
    attrList = taskEntry.getAttribute(attrType);
    if (attrList != null)
    {
disconnectMessageLoop:
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          disconnectMessage = v.getStringValue();
          break disconnectMessageLoop;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    // Get the specified client connection.
    ClientConnection clientConnection = null;
    for (ConnectionHandler handler : DirectoryServer.getConnectionHandlers())
    {
      ConnectionHandler<? extends ConnectionHandlerCfg> connHandler =
           (ConnectionHandler<? extends ConnectionHandlerCfg>) handler;
      for (ClientConnection c : connHandler.getClientConnections())
      {
        if (c.getConnectionID() == connectionID)
        {
          clientConnection = c;
          break;
        }
      }
    }


    // If there is no such client connection, then return an error.  Otherwise,
    // terminate it.
    if (clientConnection == null)
    {
      int    msgID   = MSGID_TASK_DISCONNECT_NO_SUCH_CONNECTION;
      String message = getMessage(msgID, connectionID);

      logError(ErrorLogCategory.TASK, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);

      return TaskState.COMPLETED_WITH_ERRORS;
    }
    else
    {
      clientConnection.disconnect(DisconnectReason.ADMIN_DISCONNECT,
                                  notifyClient, disconnectMessage,
                                  MSGID_TASK_DISCONNECT_MESSAGE);
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
  }
}

