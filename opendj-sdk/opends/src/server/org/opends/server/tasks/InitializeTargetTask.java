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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;
import org.opends.server.replication.plugin.LDAPReplicationDomain;

import org.opends.messages.TaskMessages;
import org.opends.server.types.ResultCode;

import org.opends.messages.MessageBuilder;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.List;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;


/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data from an LDIF file into a backend.
 */
public class InitializeTargetTask extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Config properties
  private String  domainString            = null;
  private LDAPReplicationDomain domain = null;
  private short target;
  private long total;

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
    AttributeType typeScope;

    typeDomainBase =
      getAttributeType(ATTR_TASK_INITIALIZE_TARGET_DOMAIN_DN, true);
    typeScope =
      getAttributeType(ATTR_TASK_INITIALIZE_TARGET_SCOPE, true);

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
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, e);
    }

    attrList = taskEntry.getAttribute(typeScope);
    String targetString = TaskUtils.getSingleValueString(attrList);
    target = domain.decodeTarget(targetString);

    setTotal(0);
  }

  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("DebugInfo" + "InitializeTarget Task/runTask ");
    }
    try
    {
      domain.initializeRemote(target, this);
    }
    catch(DirectoryException de)
    {
      // This log will go to the task log message
      MessageBuilder mb = new MessageBuilder();
      mb.append("Initialize Task stopped by error");
      mb.append(de.getMessageObject());
      logError(mb.toMessage());

      return TaskState.STOPPED_BY_ERROR;
    }
    return TaskState.COMPLETED_SUCCESSFULLY;
  }

  /**
   * Set the total number of entries expected to be exported.
   * @param total The total number of entries.
   * @throws DirectoryException when a problem occurs
   */
  public void setTotal(long total) throws DirectoryException
  {
    this.total = total;
    replaceAttributeValue(ATTR_TASK_INITIALIZE_LEFT,
        String.valueOf(total));
    replaceAttributeValue(ATTR_TASK_INITIALIZE_DONE, String.valueOf(0));
  }

  /**
   * Set the total number of entries still to be exported.
   * @param left The total number of entries to be exported.
   * @throws DirectoryException when a problem occurs
   */
  public void setLeft(long left)  throws DirectoryException
  {
    replaceAttributeValue(ATTR_TASK_INITIALIZE_LEFT, String.valueOf(left));
    replaceAttributeValue(ATTR_TASK_INITIALIZE_DONE,String.valueOf(total-left));
  }
}
