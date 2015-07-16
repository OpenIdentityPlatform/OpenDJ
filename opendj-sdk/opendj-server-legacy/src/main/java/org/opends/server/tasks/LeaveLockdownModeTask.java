/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tasks;
import org.forgerock.i18n.LocalizableMessage;



import java.net.InetAddress;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import static org.opends.messages.TaskMessages.*;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used bring the server out of lockdown mode.
 */
public class LeaveLockdownModeTask
       extends Task
{

  /** {@inheritDoc} */
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_LEAVE_LOCKDOWN_MODE_NAME.get();
  }

  /** {@inheritDoc} */
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



  /** {@inheritDoc} */
  protected TaskState runTask()
  {
    DirectoryServer.setLockdownMode(false);
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
