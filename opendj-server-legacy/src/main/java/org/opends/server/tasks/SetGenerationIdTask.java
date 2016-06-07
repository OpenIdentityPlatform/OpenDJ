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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
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
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data over the replication protocol from another
 * server hosting the same replication domain.
 */
public class SetGenerationIdTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private String  domainString;
  private ReplicationDomain domain;
  private Long generationId;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return TaskMessages.INFO_TASK_SET_GENERATION_ID_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeTask() throws DirectoryException
  {
    if (TaskState.isDone(getTaskState()))
    {
      return;
    }

    // FIXME -- Do we need any special authorization here?
    Entry taskEntry = getTaskEntry();

    // Retrieves the eventual generation-ID
    AttributeType typeNewValue = getSchema().getAttributeType(ATTR_TASK_SET_GENERATION_ID_NEW_VALUE);
    List<Attribute> attrList = taskEntry.getAttribute(typeNewValue);
    if (!attrList.isEmpty())
    {
      try
      {
        generationId = Long.parseLong(TaskUtils.getSingleValueString(attrList));
      }
      catch(Exception e)
      {
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_GENERATION_ID.get());
        mb.append(e.getMessage());
        throw new DirectoryException(ResultCode.CLIENT_SIDE_PARAM_ERROR, mb.toMessage());
      }
    }

    // Retrieves the replication domain
    AttributeType typeDomainBase = getSchema().getAttributeType(ATTR_TASK_SET_GENERATION_ID_DOMAIN_DN);
    attrList = taskEntry.getAttribute(typeDomainBase);
    domainString = TaskUtils.getSingleValueString(attrList);

    try
    {
      DN dn = DN.valueOf(domainString);
      domain = LDAPReplicationDomain.retrievesReplicationDomain(dn);
    }
    catch(DirectoryException e)
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_DN.get());
      mb.append(e.getMessage());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected TaskState runTask()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("setGenerationIdTask is starting on domain %s" + domain.getBaseDN());
    }

    try
    {
      domain.resetGenerationId(generationId);
    }
    catch(DirectoryException de)
    {
      logger.error(de.getMessageObject());
      return TaskState.STOPPED_BY_ERROR;
    }

    if (logger.isTraceEnabled())
    {
      logger.trace("setGenerationIdTask is ending SUCCESSFULLY");
    }
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
