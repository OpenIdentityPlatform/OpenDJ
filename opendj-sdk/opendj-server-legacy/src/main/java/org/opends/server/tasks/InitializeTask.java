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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.TaskMessages;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data over the replication protocol from another
 * server hosting the same replication domain.
 */
public class InitializeTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private String domainString;
  private int  source;
  private LDAPReplicationDomain domain;
  private TaskState initState;

  /** The total number of entries expected to be processed when this import will end successfully. */
  private long total;
  /** The number of entries still to be processed for this import to be completed. */
  private long left;
  private LocalizableMessage taskCompletionError;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return TaskMessages.INFO_TASK_INITIALIZE_NAME.get();
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

    AttributeType typeDomainBase = getAttributeTypeOrDefault(ATTR_TASK_INITIALIZE_DOMAIN_DN);
    AttributeType typeSourceScope = getAttributeTypeOrDefault(ATTR_TASK_INITIALIZE_SOURCE);

    List<Attribute> attrList;
    attrList = taskEntry.getAttribute(typeDomainBase);
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
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, e);
    }

    attrList = taskEntry.getAttribute(typeSourceScope);
    String sourceString = TaskUtils.getSingleValueString(attrList);
    source = domain.decodeSource(sourceString);

    replaceAttributeValue(ATTR_TASK_INITIALIZE_LEFT, String.valueOf(0));
    replaceAttributeValue(ATTR_TASK_INITIALIZE_DONE, String.valueOf(0));
  }

  /** {@inheritDoc} */
  @Override
  protected TaskState runTask()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("[IE] InitializeTask is starting on domain: %s from source:%d", domain.getBaseDN(), source);
    }
    initState = getTaskState();
    try
    {
      // launch the import
      domain.initializeFromRemote(source, this);

      synchronized(initState)
      {
        // Waiting for the end of the job
        while (initState == TaskState.RUNNING)
        {
          initState.wait(1000);
          replaceAttributeValue(ATTR_TASK_INITIALIZE_LEFT, String.valueOf(left));
          replaceAttributeValue(ATTR_TASK_INITIALIZE_DONE, String.valueOf(total-left));
        }
      }
      replaceAttributeValue(ATTR_TASK_INITIALIZE_LEFT, String.valueOf(left));
      replaceAttributeValue(ATTR_TASK_INITIALIZE_DONE, String.valueOf(total-left));

      // Error raised at completion time
      if (taskCompletionError != null)
      {
        logger.error(taskCompletionError);
      }

    }
    catch(InterruptedException ie) {}
    catch(DirectoryException de)
    {
      // Error raised at submission time
      logger.error(de.getMessageObject());
      initState = TaskState.STOPPED_BY_ERROR;
    }

    logger.trace("[IE] InitializeTask is ending with state: %s", initState);
    return initState;
  }

  /**
   * Set the state for the current task.
   *
   * @param de  When the new state is different from COMPLETED_SUCCESSFULLY
   * this is the exception that contains the cause of the failure.
   */
  public void updateTaskCompletionState(DirectoryException de)
  {
    initState =  TaskState.STOPPED_BY_ERROR;
    try
    {
      if (de == null)
      {
        initState =  TaskState.COMPLETED_SUCCESSFULLY;
      }
      else
      {
        taskCompletionError = de.getMessageObject();
      }
    }
    finally
    {
      // Wake up runTask method waiting for completion
      synchronized (initState)
      {
        initState.notify();
      }
    }
  }


  /**
   * Set the total number of entries expected to be imported.
   * @param total The total number of entries.
   */
  public void setTotal(long total)
  {
    this.total = total;
  }

  /**
   * Set the total number of entries still to be imported.
   * @param left The total number of entries to be imported.
   */
  public void setLeft(long left)
  {
    this.left = left;
  }
}
