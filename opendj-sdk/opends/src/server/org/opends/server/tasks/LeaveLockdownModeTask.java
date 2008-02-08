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
package org.opends.server.tasks;
import org.opends.messages.Message;



import java.net.InetAddress;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.messages.TaskMessages.*;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used bring the server out of lockdown mode.
 */
public class LeaveLockdownModeTask
       extends Task
{

  /**
   * {@inheritDoc}
   */
  public Message getDisplayName() {
    return INFO_TASK_LEAVE_LOCKDOWN_MODE_NAME.get();
  }

  /**
   * {@inheritDoc}
   */
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
      if ((authzDN == null) || (! DirectoryServer.isRootDN(authzDN)))
      {
        Message message = ERR_TASK_LEAVELOCKDOWN_NOT_ROOT.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      InetAddress clientAddress =
           operation.getClientConnection().getRemoteAddress();
      if ((clientAddress != null) && (! clientAddress.isLoopbackAddress()))
      {
        Message message = ERR_TASK_LEAVELOCKDOWN_NOT_LOOPBACK.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    DirectoryServer.setLockdownMode(false);
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}

