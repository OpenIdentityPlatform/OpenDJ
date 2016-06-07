/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;

/**
 * This class provides an implementation of a Directory Server task that can be
 * used to terminate a client connection.
 */
public class DisconnectClientTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicates whether to send a notification message to the client. */
  private boolean notifyClient;

  /** The connection ID for the client connection to terminate. */
  private long connectionID;

  /** The disconnect message to send to the client. */
  private LocalizableMessage disconnectMessage;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_DISCONNECT_CLIENT_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the client has the
    // DISCONNECT_CLIENT privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection conn = operation.getClientConnection();
      if (! conn.hasPrivilege(Privilege.DISCONNECT_CLIENT, operation))
      {
        LocalizableMessage message = ERR_TASK_DISCONNECT_NO_PRIVILEGE.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }

    final Entry taskEntry = getTaskEntry();
    connectionID = getConnectionID(taskEntry);
    if (connectionID < 0)
    {
      LocalizableMessage message =
          ERR_TASK_DISCONNECT_NO_CONN_ID.get(ATTR_TASK_DISCONNECT_CONN_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    notifyClient = mustNotifyClient(taskEntry);
    disconnectMessage = getDisconnectMessage(taskEntry);
  }

  private long getConnectionID(Entry taskEntry) throws DirectoryException
  {
    final AttributeType attrType = DirectoryServer.getSchema().getAttributeType(ATTR_TASK_DISCONNECT_CONN_ID);
    for (Attribute a : taskEntry.getAttribute(attrType))
    {
      for (ByteString v : a)
      {
        try
        {
          return Long.parseLong(v.toString());
        }
        catch (Exception e)
        {
          LocalizableMessage message = ERR_TASK_DISCONNECT_INVALID_CONN_ID.get(v);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message, e);
        }
      }
    }
    return -1;
  }

  private boolean mustNotifyClient(Entry taskEntry) throws DirectoryException
  {
    final AttributeType attrType = DirectoryServer.getSchema().getAttributeType(ATTR_TASK_DISCONNECT_NOTIFY_CLIENT);
    for (Attribute a : taskEntry.getAttribute(attrType))
    {
      for (ByteString v : a)
      {
        final String stringValue = toLowerCase(v.toString());
        if ("true".equals(stringValue))
        {
          return true;
        }
        else if ("false".equals(stringValue))
        {
          return false;
        }
        else
        {
          LocalizableMessage message = ERR_TASK_DISCONNECT_INVALID_NOTIFY_CLIENT.get(stringValue);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
        }
      }
    }
    return false;
  }

  private LocalizableMessage getDisconnectMessage(Entry taskEntry)
  {
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(ATTR_TASK_DISCONNECT_MESSAGE);
    for (Attribute a : taskEntry.getAttribute(attrType))
    {
      for (ByteString v : a)
      {
        return LocalizableMessage.raw(v.toString());
      }
    }
    return INFO_TASK_DISCONNECT_GENERIC_MESSAGE.get();
  }

  @Override
  protected TaskState runTask()
  {
    final ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      logger.error(ERR_TASK_DISCONNECT_NO_SUCH_CONNECTION, connectionID);
      return TaskState.COMPLETED_WITH_ERRORS;
    }

    clientConnection.disconnect(DisconnectReason.ADMIN_DISCONNECT, notifyClient, disconnectMessage);
    return TaskState.COMPLETED_SUCCESSFULLY;
  }

  private ClientConnection getClientConnection()
  {
    for (ConnectionHandler<?> handler : DirectoryServer.getConnectionHandlers())
    {
      for (ClientConnection c : handler.getClientConnections())
      {
        if (c.getConnectionID() == connectionID)
        {
          return c;
        }
      }
    }
    return null;
  }
}
