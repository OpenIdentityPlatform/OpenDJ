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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

import java.util.List;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_RESET_CHANGE_NUMBER_BASE_DN;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_RESET_CHANGE_NUMBER_CSN;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_RESET_CHANGE_NUMBER_TO;
import static org.opends.server.core.DirectoryServer.getSchema;
import static org.opends.messages.TaskMessages.*;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to rebuild the change number index with a given change number and a
 * change represented by its CSN.
 */
public class ResetChangeNumberTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private int newFirstChangeNumber;
  private DN baseDN;
  private CSN newFirstCSN;
  private ReplicationServer targetRS;

  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_RESET_CHANGE_NUMBER.get();
  }

  @Override public void initializeTask() throws DirectoryException
  {
    if (TaskState.isDone(getTaskState()))
    {
      return;
    }

    final Entry taskEntry = getTaskEntry();
    newFirstChangeNumber = TaskUtils.getSingleValueInteger(
        getTaskParameter(taskEntry, ATTR_TASK_RESET_CHANGE_NUMBER_TO), 1);
    newFirstCSN = CSN.valueOf(TaskUtils.getSingleValueString(
        getTaskParameter(taskEntry, ATTR_TASK_RESET_CHANGE_NUMBER_CSN)));
    baseDN = DN.valueOf(TaskUtils.getSingleValueString(
        getTaskParameter(taskEntry, ATTR_TASK_RESET_CHANGE_NUMBER_BASE_DN)));

    if (newFirstChangeNumber < 1)
    {
      throw new DirectoryException(UNWILLING_TO_PERFORM,
          ERR_TASK_RESET_CHANGE_NUMBER_INVALID.get(newFirstChangeNumber));
    }

    List<ReplicationServer> allRSes = ReplicationServer.getAllInstances();
    if (allRSes.isEmpty())
    {
      throw new DirectoryException(NO_SUCH_OBJECT, ERR_TASK_RESET_CHANGE_NUMBER_NO_RSES.get());
    }

    for (ReplicationServer rs : allRSes)
    {
      if (rs.getReplicationServerDomain(baseDN) != null)
      {
        targetRS = rs;
        return;
      }
    }
    throw new DirectoryException(NO_SUCH_OBJECT, ERR_TASK_RESET_CHANGE_NUMBER_CHANGELOG_NOT_FOUND.get(baseDN));
  }

  private List<Attribute> getTaskParameter(Entry taskEntry, String attrTaskResetChangeNumberTo)
  {
    AttributeType taskAttr = getSchema().getAttributeType(attrTaskResetChangeNumberTo);
    return taskEntry.getAttribute(taskAttr);
  }

  @Override
  protected TaskState runTask()
  {
    logger.trace("Reset change number task is starting with new changeNumber %d having CSN %s",
        newFirstChangeNumber, newFirstCSN);

    try
    {
      targetRS.getChangelogDB().getChangeNumberIndexDB().resetChangeNumberTo(newFirstChangeNumber, baseDN, newFirstCSN);
      return returnWithDebug(TaskState.COMPLETED_SUCCESSFULLY);
    }
    catch (ChangelogException ce)
    {
      logger.error(ERR_TASK_RESET_CHANGE_NUMBER_FAILED, ce.getMessageObject());
      return returnWithDebug(TaskState.STOPPED_BY_ERROR);
    }
  }

  private TaskState returnWithDebug(TaskState state)
  {
    logger.trace("state: %s", state);
    return state;
  }
}
