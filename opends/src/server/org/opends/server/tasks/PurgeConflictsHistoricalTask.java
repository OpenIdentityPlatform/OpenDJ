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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;
import org.opends.server.replication.plugin.LDAPReplicationDomain;

import org.opends.server.types.ResultCode;

import org.opends.messages.MessageBuilder;


import org.opends.messages.Message;
import org.opends.messages.Category;
import org.opends.messages.Severity;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import java.util.List;

import org.opends.messages.TaskMessages;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.TimeThread;
import org.opends.server.replication.common.ChangeNumber;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to purge the replication historical informations stored in the
 * user entries to solve conflicts.
 */
public class PurgeConflictsHistoricalTask extends Task
{
  /**
   * The default value for the maximum duration of the purge expressed in
   * seconds.
   */
  public static final int DEFAULT_MAX_DURATION = 60 * 60;
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private String  domainString = null;
  private LDAPReplicationDomain domain = null;

  /**
   *                 current historical purge delay
   *                <--------------------------------->
   * -----------------------------------------------------------------> t
   *               |                           |            |
   *           current                      task           task
   *           CN being purged           start date    max end date
   *                                           <------------>
   *                                          config.purgeMaxDuration
   *
   * The task will start purging the change with the oldest CN found.
   * The task run as long as :
   *  - the end date (computed from the configured max duration) is not reached
   *  - the CN purged is oldest than the configured historical purge delay
   *
   *
   */

  private int purgeTaskMaxDurationInSec = DEFAULT_MAX_DURATION;

  TaskState initState;


  private static final void debugInfo(String s)
  {
    if (debugEnabled())
    {
      System.out.println(Message.raw(Category.SYNC, Severity.NOTICE, s));
      TRACER.debugInfo(s);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getDisplayName() {
    return TaskMessages.INFO_TASK_PURGE_CONFLICTS_HIST_NAME.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    if (TaskState.isDone(getTaskState()))
    {
      return;
    }

    // FIXME -- Do we need any special authorization here?
    Entry taskEntry = getTaskEntry();

    AttributeType typeDomainBase;
    typeDomainBase =
      getAttributeType(ATTR_TASK_CONFLICTS_HIST_PURGE_DOMAIN_DN, true);

    List<Attribute> attrList;
    attrList = taskEntry.getAttribute(typeDomainBase);
    domainString = TaskUtils.getSingleValueString(attrList);

    try
    {
      DN dn = DN.decode(domainString);
      // We can assume that this is an LDAP replication domain
      domain = LDAPReplicationDomain.retrievesReplicationDomain(dn);
    }
    catch(DirectoryException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_DN.get());
      mb.append(e.getMessage());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          mb.toMessage());
    }

    AttributeType typeMaxDuration;
    typeMaxDuration =
      getAttributeType(ATTR_TASK_CONFLICTS_HIST_PURGE_MAX_DURATION, true);
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

  /**
   * {@inheritDoc}
   */
  @Override
  protected TaskState runTask()
  {
    Boolean purgeCompletedInTime = false;
    if (debugEnabled())
    {
      debugInfo("[PURGE] PurgeConflictsHistoricalTask is starting "
          + "on domain: " + domain.getServiceID()
          + "max duration (sec):" + purgeTaskMaxDurationInSec);
    }
    try
    {
      replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COMPLETED_IN_TIME,
          purgeCompletedInTime.toString());

      // launch the task
      domain.purgeConflictsHistorical(this,
          TimeThread.getTime() + (purgeTaskMaxDurationInSec*1000));

      purgeCompletedInTime = true;
      replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COMPLETED_IN_TIME,
          purgeCompletedInTime.toString());

      initState =  TaskState.COMPLETED_SUCCESSFULLY;
    }
    catch(DirectoryException de)
    {
      debugInfo("[PURGE] PurgeConflictsHistoricalTask exception " +
          de.getLocalizedMessage());
      if (de.getResultCode() != ResultCode.ADMIN_LIMIT_EXCEEDED)
      {
        // Error raised at submission time
        logError(de.getMessageObject());
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
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COUNT,
            String.valueOf(this.purgeCount));
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_LAST_CN,
            this.lastCN.toStringUI());
        debugInfo("[PURGE] PurgeConflictsHistoricalTask write  attrs ");
      }
      catch(Exception e)
      {
        debugInfo("[PURGE] PurgeConflictsHistoricalTask exception " +
            e.getLocalizedMessage());
        initState = TaskState.STOPPED_BY_ERROR;
      }
    }

    if (debugEnabled())
    {
      debugInfo("[PURGE] PurgeConflictsHistoricalTask is ending " +
            "with state:" + initState.toString() +
            " completedInTime:" + purgeCompletedInTime);
    }
    return initState;
  }

  int updateAttrPeriod = 0;
  ChangeNumber lastCN;
  int purgeCount;

  /**
   * Set the last changenumber purged and the count of purged values in order
   * to monitor the historical purge.
   * @param lastCN the last changeNumber purged.
   * @param purgeCount the count of purged values.
   */
  public void setProgressStats(ChangeNumber lastCN, int purgeCount)
  {
    try
    {
      if (purgeCount == 0)
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_FIRST_CN,
            lastCN.toStringUI());

      // we don't want the update of the task to overload too much task duration
      this.purgeCount = purgeCount;
      this.lastCN = lastCN;
      if (++updateAttrPeriod % 100 == 0)
      {
        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_COUNT,
            String.valueOf(purgeCount));

        replaceAttributeValue(ATTR_TASK_CONFLICTS_HIST_PURGE_LAST_CN,
            lastCN.toStringUI());
        debugInfo("[PURGE] PurgeConflictsHistoricalTask write  attrs "
            + purgeCount);
      }
    }
    catch(DirectoryException de)
    {
      debugInfo("[PURGE] PurgeConflictsHistoricalTask exception " +
          de.getLocalizedMessage());
      initState = TaskState.STOPPED_BY_ERROR;
    }
  }
}
