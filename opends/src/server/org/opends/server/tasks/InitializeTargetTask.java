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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.messages.TaskMessages;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.replication.plugin.ReplicationDomain;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

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
  boolean append                  = false;
  boolean isCompressed            = false;
  boolean isEncrypted             = false;
  boolean skipSchemaValidation    = false;
  String  domainString            = null;
  ReplicationDomain domain = null;
  short target;
  long total;
  long left;

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
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

    DN domainDN = DN.nullDN();
    try
    {
      domainDN = DN.decode(domainString);
    }
    catch(Exception e)
    {
      int    msgID   = TaskMessages.MSGID_TASK_INITIALIZE_INVALID_DN;
      String message = getMessage(msgID) + e.getMessage();
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message, msgID);
    }
    domain=ReplicationDomain.retrievesReplicationDomain(domainDN);

    attrList = taskEntry.getAttribute(typeScope);
    String targetString = TaskUtils.getSingleValueString(attrList);
    target = domain.decodeTarget(targetString);

    createCounterAttribute(ATTR_TASK_INITIALIZE_LEFT, 0);
    createCounterAttribute(ATTR_TASK_INITIALIZE_DONE, 0);
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
      domain.initializeTarget(target, this);
    }
    catch(DirectoryException de)
    {
      // This log will go to the task log message
      logError(ErrorLogCategory.TASK,
          ErrorLogSeverity.SEVERE_ERROR,
          "Initialize Task stopped by error" + de.getErrorMessage(), 1);

      return TaskState.STOPPED_BY_ERROR;
    }
    return TaskState.COMPLETED_SUCCESSFULLY;
  }

  /**
   * Create attribute to store entry counters.
   * @param name The name of the attribute.
   * @param value The value to store for that attribute.
   */
  protected void createCounterAttribute(String name, long value)
  {
    AttributeType type;
    LinkedHashSet<AttributeValue> values =
      new LinkedHashSet<AttributeValue>();

    Entry taskEntry = getTaskEntry();
    try
    {
      type = getAttributeType(name, true);
      values.add(new AttributeValue(type,
          new ASN1OctetString(String.valueOf(value))));
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(type, name,values));
      taskEntry.putAttribute(type, attrList);
    }
    finally
    {
      // taskScheduler.unlockEntry(taskEntryDN, lock);
    }
  }

  /**
   * Set the total number of entries expected to be exported.
   * @param total The total number of entries.
   */
  public void setTotal(long total)
  {
    this.total = total;
    try
    {
      updateAttribute(ATTR_TASK_INITIALIZE_LEFT, total);
      updateAttribute(ATTR_TASK_INITIALIZE_DONE, 0);
    }
    catch(Exception e) {}
  }

  /**
   * Set the total number of entries still to be exported.
   * @param left The total number of entries to be exported.
   */
  public void setLeft(long left)
  {
    this.left = left;
    try
    {
      updateAttribute(ATTR_TASK_INITIALIZE_LEFT, left);
      updateAttribute(ATTR_TASK_INITIALIZE_DONE, total-left);
    }
    catch(Exception e) {}
  }

  /**
   * Update an attribute for this task.
   * @param name The name of the attribute.
   * @param value The value.
   * @throws DirectoryException When an error occurs.
   */
  protected void updateAttribute(String name, long value)
  throws DirectoryException
  {
    Entry taskEntry = getTaskEntry();

    ArrayList<Modification> modifications = new ArrayList<Modification>();
    modifications.add(new Modification(ModificationType.REPLACE,
        new Attribute(name, String.valueOf(value))));

    taskEntry.applyModifications(modifications);
  }
}
