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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.messages.TaskMessages;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to purge the replication historical informations stored in the
 * user entries to solve conflicts.
 */
public class PurgeConflictsHistoricalTask extends Task
{
  /** The default value for the maximum duration of the purge expressed in seconds. */
  public static final int DEFAULT_MAX_DURATION = 60 * 60;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private String domainString;
  private LDAPReplicationDomain domain;

  /**
   *                 current historical purge delay
   *                <--------------------------------->
   * -----------------------------------------------------------------> t
   *               |                           |            |
   *           current                      task           task
   *           CSN being purged           start date    max end date
   *                                           <------------>
   *                                          config.purgeMaxDuration
   *
   * The task will start purging the change with the oldest CSN found.
   * The task run as long as :
   *  - the end date (computed from the configured max duration) is not reached
   *  - the CSN purged is oldest than the configured historical purge delay
   */
  private int purgeTaskMaxDurationInSec = DEFAULT_MAX_DURATION;

  private TaskState initState;


  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return TaskMessages.INFO_TASK_PURGE_CONFLICTS_HIST_NAME.get();
  }

  /** {@inheritDoc} */
  @Override public void initializeTask() throws DirectoryException
  {
    if (TaskState.isDone(getTaskState()))
    {
      return;
    }

    // FIXME -- Do we need any special authorization here?
    Entry taskEntry = getTaskEntry();

    AttributeType typeDomainBase = getSchema().getAttributeType(ATTR_TASK_CONFLICTS_HIST_PURGE_DOMAIN_DN);
    List<Attribute> attrList = taskEntry.getAttribute(typeDomainBase);
    domainString = TaskUtils.getSingleValueString(attrList);

    try
    {
      DN dn = DN.valueOf(domainString);
      // We can assume that this is an LDAP replication domain
      domain = LDAPReplicationDomain.retrievesReplicationDomain(dn);
    }
    catch(DirectoryException e)
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_DN.get());
      mb.append(e.getMessage());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, mb.toMessage());
    }

    AttributeType typeMaxDuration = getSchema().getAttributeType(ATTR_TASK_CONFLICTS_HIST_PURGE_MAX_DURATION);
    attrList = taskEntry.getAttribute(typeMaxDuration);
    String maxDurationStringInSec = TaskUtils.getSingleValueString(attrList);

    if (maxDurationStringInSec != null)
    {
      try
      {
        purgeTaskMaxDurationInSec = Integer.decode(maxDurationStringInSec);
      }
      catch(Exception e)
      {
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM,
            TaskMessages.ERR_TASK_INVALID_ATTRIBUTE_VALUE.get(
        ATTR_TASK_CONFLICTS_HIST_PURGE_MAX_DURATION, e.getLocalizedMessage()));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  protected TaskState runTask()
  {
    Boolean purgeCompletedInTime = false;
    logger.trace("PurgeConflictsHistoricalTask is starting on domain: %s max duration (sec): %d",
        domain.getBaseDN(), purgeTaskMaxDurationInSec);
    try
    {
      replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COMPLETED_IN_TIME, purgeCompletedInTime.toString());

      // launch the task
      domain.purgeConflictsHistorical(this, TimeThread.getTime() + purgeTaskMaxDurationInSec*1000);

      purgeCompletedInTime = true;
      replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COMPLETED_IN_TIME, purgeCompletedInTime.toString());

      initState =  TaskState.COMPLETED_SUCCESSFULLY;
    }
    catch(DirectoryException de)
    {
      logger.trace("PurgeConflictsHistoricalTask exception %s", de.getLocalizedMessage());
      if (de.getResultCode() != ResultCode.ADMIN_LIMIT_EXCEEDED)
      {
        // Error raised at submission time
        logger.error(de.getMessageObject());
        initState = TaskState.STOPPED_BY_ERROR;
      }
      else
      {
        initState =  TaskState.COMPLETED_SUCCESSFULLY;
      }
    }
    finally
    {
      try
      {
        // sets in the attributes the last stats values
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COUNT, String.valueOf(purgeCount));
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_LAST_CSN, lastCSN.toStringUI());
        logger.trace("PurgeConflictsHistoricalTask write attrs %d", purgeCount);
      }
      catch(Exception e)
      {
        logger.trace("PurgeConflictsHistoricalTask exception %s", e.getLocalizedMessage());
        initState = TaskState.STOPPED_BY_ERROR;
      }
    }

    logger.trace("PurgeConflictsHistoricalTask is ending with state: %s completedInTime: %s",
        initState, purgeCompletedInTime);
    return initState;
  }

  private int updateAttrPeriod;
  private CSN lastCSN;
  private int purgeCount;

  /**
   * Set the last CSN purged and the count of purged values in order to monitor
   * the historical purge.
   *
   * @param lastCSN
   *          the last CSN purged.
   * @param purgeCount
   *          the count of purged values.
   */
  public void setProgressStats(CSN lastCSN, int purgeCount)
  {
    try
    {
      if (purgeCount == 0)
      {
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_FIRST_CSN, lastCSN.toStringUI());
      }

      // we don't want the update of the task to overload too much task duration
      this.purgeCount = purgeCount;
      this.lastCSN = lastCSN;
      if (++updateAttrPeriod % 100 == 0)
      {
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COUNT, String.valueOf(purgeCount));
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_LAST_CSN, lastCSN.toStringUI());
        logger.trace("PurgeConflictsHistoricalTask write attrs %d", purgeCount);
      }
    }
    catch(DirectoryException de)
    {
      logger.trace("PurgeConflictsHistoricalTask exception %s", de.getLocalizedMessage());
      initState = TaskState.STOPPED_BY_ERROR;
    }
  }
}
