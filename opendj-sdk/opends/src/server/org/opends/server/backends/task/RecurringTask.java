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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.task;



import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a information about a recurring task, which will be used
 * to repeatedly schedule tasks for processing.
 */
public class RecurringTask
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.task.RecurringTask";



  // The entry that actually defines this task.
  private Entry recurringTaskEntry;

  // The unique ID for this recurring task.
  private String recurringTaskID;

  // The fully-qualified name of the class that will be used to implement the
  // class.
  private String taskClassName;

  // The reference to the task scheduler that will be used to schedule new
  // iterations of this recurring task.
  private TaskScheduler taskScheduler;



  /**
   * Creates a new recurring task based on the information in the provided
   * entry.
   *
   * @param  taskScheduler       A reference to the task scheduler that may be
   *                             used to schedule new tasks.
   * @param  recurringTaskEntry  The entry containing the information to use to
   *                             define the task to process.
   *
   * @throws  DirectoryException  If the provided entry does not contain a valid
   *                              recurring task definition.
   */
  public RecurringTask(TaskScheduler taskScheduler, Entry recurringTaskEntry)
         throws DirectoryException
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(taskScheduler),
                            String.valueOf(recurringTaskEntry));

    this.taskScheduler      = taskScheduler;
    this.recurringTaskEntry = recurringTaskEntry;


    // Get the recurring task ID from the entry.  If there isn't one, then fail.
    AttributeType attrType = DirectoryServer.getAttributeType(
                                  ATTR_RECURRING_TASK_ID.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(
                                      ATTR_RECURRING_TASK_ID);
    }

    List<Attribute> attrList = recurringTaskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      int    msgID   = MSGID_RECURRINGTASK_NO_ID_ATTRIBUTE;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    if (attrList.size() > 1)
    {
      int    msgID   = MSGID_RECURRINGTASK_MULTIPLE_ID_TYPES;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    Attribute attr = attrList.get(0);
    LinkedHashSet<AttributeValue> values = attr.getValues();
    if ((values == null) || values.isEmpty())
    {
      int    msgID   = MSGID_RECURRINGTASK_NO_ID;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    Iterator<AttributeValue> iterator = values.iterator();
    AttributeValue value = iterator.next();
    if (iterator.hasNext())
    {
      int    msgID   = MSGID_RECURRINGTASK_MULTIPLE_ID_VALUES;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_ID);
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION, message,
                                   msgID);
    }

    recurringTaskID = value.getStringValue();


    // FIXME -- Need to have some method of getting the scheduling information
    //          from the recurring task entry.


    // Get the class name from the entry.  If there isn't one, then fail.
    attrType = DirectoryServer.getAttributeType(
                    ATTR_RECURRING_TASK_CLASS_NAME.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(
                                      ATTR_RECURRING_TASK_CLASS_NAME);
    }

    attrList = recurringTaskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      int    msgID   = MSGID_RECURRINGTASK_NO_CLASS_ATTRIBUTE;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_CLASS_NAME);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    if (attrList.size() > 0)
    {
      int    msgID   = MSGID_RECURRINGTASK_MULTIPLE_CLASS_TYPES;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_CLASS_NAME);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    attr = attrList.get(0);
    values = attr.getValues();
    if ((values == null) || values.isEmpty())
    {
      int    msgID   = MSGID_RECURRINGTASK_NO_CLASS_VALUES;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_CLASS_NAME);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    iterator = values.iterator();
    value = iterator.next();
    if (iterator.hasNext())
    {
      int    msgID   = MSGID_RECURRINGTASK_MULTIPLE_CLASS_VALUES;
      String message = getMessage(msgID, ATTR_RECURRING_TASK_CLASS_NAME);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    taskClassName = value.getStringValue();


    // Make sure that the specified class can be loaded.
    Class taskClass;
    try
    {
      // FIXME -- Should this use a custom class loader?
      taskClass = Class.forName(taskClassName);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      int    msgID   = MSGID_RECURRINGTASK_CANNOT_LOAD_CLASS;
      String message = getMessage(msgID, String.valueOf(taskClassName),
                                  ATTR_RECURRING_TASK_CLASS_NAME,
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID, e);
    }


    // Make sure that the specified class can be instantiated as a task.
    Task task;
    try
    {
      task = (Task) taskClass.newInstance();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      int    msgID   = MSGID_RECURRINGTASK_CANNOT_INSTANTIATE_CLASS_AS_TASK;
      String message = getMessage(msgID, String.valueOf(taskClassName),
                                  Task.class.getName());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID, e);
    }


    // Make sure that we can initialize the task with the information in the
    // provided entry.
    try
    {
      task.initializeTaskInternal(taskScheduler, recurringTaskEntry);
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "<init>", ie);

      int    msgID   = MSGID_RECURRINGTASK_CANNOT_INITIALIZE_INTERNAL;
      String message = getMessage(msgID, String.valueOf(taskClassName),
                                  ie.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, ie);
    }

    task.initializeTask();
  }



  /**
   * Retrieves the unique ID assigned to this recurring task.
   *
   * @return  The unique ID assigned to this recurring task.
   */
  public String getRecurringTaskID()
  {
    assert debugEnter(CLASS_NAME, "getRecurringTaskID");

    return recurringTaskID;
  }



  /**
   * Retrieves the entry containing the data for this recurring task.
   *
   * @return  The entry containing the data for this recurring task.
   */
  public Entry getRecurringTaskEntry()
  {
    assert debugEnter(CLASS_NAME, "getRecurringTaskEntry");

    return recurringTaskEntry;
  }



  /**
   * Retrieves the fully-qualified name of the Java class that provides the
   * implementation logic for this recurring task.
   *
   * @return  The fully-qualified name of the Java class that provides the
   *          implementation logic for this recurring task.
   */
  public String getTaskClassName()
  {
    assert debugEnter(CLASS_NAME, "getTaskClassName");

    return taskClassName;
  }



  /**
   * Schedules the next iteration of this recurring task for processing.
   *
   * @return  The task that has been scheduled for processing.
   */
  public Task scheduleNextIteration()
  {
    assert debugEnter(CLASS_NAME, "scheduleNextIteration");

    // NYI
    return null;
  }
}

