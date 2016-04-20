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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;

import java.net.InetAddress;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;

/**
 * This class provides an implementation of a Directory Server task that can be
 * used bring the server out of lockdown mode.
 */
public class LeaveLockdownModeTask
       extends Task
{
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_LEAVE_LOCKDOWN_MODE_NAME.get();
  }

  @Override
  public void initializeTask()
         throws DirectoryException
  {
    // If the client connection is available, then make sure it is authorized
    // as a root user.
    Operation operation = getOperation();
    if (operation != null)
    {
      DN authzDN = operation.getAuthorizationDN();
      if (authzDN == null || !operation.getClientConnection().hasPrivilege(
          Privilege.SERVER_LOCKDOWN, operation))
      {
        LocalizableMessage message = ERR_TASK_LEAVELOCKDOWN_NOT_ROOT.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      InetAddress clientAddress = operation.getClientConnection().getRemoteAddress();
      if (clientAddress != null && !clientAddress.isLoopbackAddress())
      {
        LocalizableMessage message = ERR_TASK_LEAVELOCKDOWN_NOT_LOOPBACK.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }

  @Override
  protected TaskState runTask()
  {
    DirectoryServer.setLockdownMode(false);
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
