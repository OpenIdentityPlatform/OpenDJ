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
 * be used to import data over the replication protocol from another
 * server hosting the same replication domain.
 */
public class InitializeTask extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  boolean isCompressed            = false;
  boolean isEncrypted             = false;
  boolean skipSchemaValidation    = false;
  String  domainString            = null;
  short  source;
  ReplicationDomain domain = null;
  TaskState initState;

  // The total number of entries expected to be processed when this import
  // will end successfully
  long total = 0;

  // The number of entries still to be processed for this import to be
  // completed
  long left = 0;

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {

    // FIXME -- Do we need any special authorization here?
    Entry taskEntry = getTaskEntry();

    AttributeType typeDomainBase;
    AttributeType typeSourceScope;

    typeDomainBase =
      getAttributeType(ATTR_TASK_INITIALIZE_DOMAIN_DN, true);
    typeSourceScope =
      getAttributeType(ATTR_TASK_INITIALIZE_SOURCE, true);

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


    attrList = taskEntry.getAttribute(typeSourceScope);
    String sourceString = TaskUtils.getSingleValueString(attrList);
    source = domain.decodeSource(sourceString);

    createAttribute(ATTR_TASK_INITIALIZE_LEFT, 0);
    createAttribute(ATTR_TASK_INITIALIZE_DONE, 0);
  }

  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("InitializeTask is starting domain: %s source:%d",
                domain.getBaseDN(), source);
    }
    initState = getTaskState(); // RUNNING
    try
    {
      // launch the import
      domain.initialize(source, this);

      synchronized(initState)
      {
        // Waiting for the end of the job
        while (initState == TaskState.RUNNING)
        {
          initState.wait(1000);
          updateAttribute(ATTR_TASK_INITIALIZE_LEFT, left);
          updateAttribute(ATTR_TASK_INITIALIZE_DONE, total-left);
        }
      }
      updateAttribute(ATTR_TASK_INITIALIZE_LEFT, left);
      updateAttribute(ATTR_TASK_INITIALIZE_DONE, total-left);
    }
    catch(InterruptedException ie) {}
    catch(DirectoryException de)
    {
      int msgID   = de.getMessageID();
      String message = getMessage(msgID, de.getErrorMessage());
      logError(ErrorLogCategory.TASK, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      initState = TaskState.STOPPED_BY_ERROR;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("InitializeTask is ending with state:%d", initState);
    }
    return initState;
  }

  /**
   * Set the state for the current task.
   *
   * @param newState The new state value to set
   * @param de  When the new state is different from COMPLETED_SUCCESSFULLY
   * this is the exception that contains the cause of the failure.
   */
  public void setState(TaskState newState, DirectoryException de)
  {
    try
    {
      if (de != null)
      {
        int msgID   = de.getMessageID();
        String message = getMessage(msgID, de.getErrorMessage());
        logError(ErrorLogCategory.TASK, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
      }
      if (debugEnabled())
      {
        logError(ErrorLogCategory.TASK,
            ErrorLogSeverity.SEVERE_ERROR,
            "setState: "+newState, 1);
        TRACER.debugInfo("InitializeTask/setState: ", newState);
      }
      initState = newState;
      synchronized (initState)
      {
        initState.notify();
      }
    }
    catch(Exception e)
    {}
  }

  /**
   * Create a new attribute the task entry.
   * @param name The name of the attribute
   * @param value The value to store
   */
  protected void createAttribute(String name, long value)
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
