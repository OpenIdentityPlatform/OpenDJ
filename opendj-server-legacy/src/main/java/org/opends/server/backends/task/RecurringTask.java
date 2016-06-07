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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.RDN;

import static java.util.Calendar.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a information about a recurring task, which will be used
 * to repeatedly schedule tasks for processing.
 * <br>
 * It also provides some static methods that allow to validate strings in
 * crontab (5) format.
 */
public class RecurringTask
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the entry that actually defines this task. */
  private final DN recurringTaskEntryDN;

  /** The entry that actually defines this task. */
  private final Entry recurringTaskEntry;

  /** The unique ID for this recurring task. */
  private final String recurringTaskID;

  /**
   * The fully-qualified name of the class that will be used to implement the
   * class.
   */
  private final String taskClassName;

  /** Task instance. */
  private Task task;

  /** Task scheduler for this task. */
  private final TaskScheduler taskScheduler;

  /** Number of tokens in the task schedule tab. */
  private static final int TASKTAB_NUM_TOKENS = 5;

  /** Maximum year month days. */
  static final int MONTH_LENGTH[]
        = {31,28,31,30,31,30,31,31,30,31,30,31};

  /** Maximum leap year month days. */
  static final int LEAP_MONTH_LENGTH[]
        = {31,29,31,30,31,30,31,31,30,31,30,31};

  /** Task tab fields. */
  private static enum TaskTab {MINUTE, HOUR, DAY, MONTH, WEEKDAY}

  private static final int MINUTE_INDEX = 0;
  private static final int HOUR_INDEX = 1;
  private static final int DAY_INDEX = 2;
  private static final int MONTH_INDEX = 3;
  private static final int WEEKDAY_INDEX = 4;

  /** Wildcard match pattern. */
  private static final Pattern wildcardPattern = Pattern.compile("^\\*(?:/(\\d+))?");

  /** Exact match pattern. */
  private static final Pattern exactPattern = Pattern.compile("(\\d+)");

  /** Range match pattern. */
  private static final Pattern rangePattern = Pattern.compile("(\\d+)-(\\d+)(?:/(\\d+))?");

  /** Boolean arrays holding task tab slots. */
  private final boolean[] minutesArray;
  private final boolean[] hoursArray;
  private final boolean[] daysArray;
  private final boolean[] monthArray;
  private final boolean[] weekdayArray;

  private final ServerContext serverContext;

  /**
   * Creates a new recurring task based on the information in the provided
   * entry.
   *
   * @param serverContext
   *            The server context.
   *
   * @param  taskScheduler       A reference to the task scheduler that may be
   *                             used to schedule new tasks.
   * @param  recurringTaskEntry  The entry containing the information to use to
   *                             define the task to process.
   *
   * @throws  DirectoryException  If the provided entry does not contain a valid
   *                              recurring task definition.
   */
  public RecurringTask(ServerContext serverContext, TaskScheduler taskScheduler, Entry recurringTaskEntry)
         throws DirectoryException
  {
    this.serverContext = serverContext;
    this.taskScheduler = taskScheduler;
    this.recurringTaskEntry = recurringTaskEntry;
    this.recurringTaskEntryDN = recurringTaskEntry.getName();

    // Get the recurring task ID from the entry.  If there isn't one, then fail.
    Attribute attr = getSingleAttribute(recurringTaskEntry, ATTR_RECURRING_TASK_ID,
        ERR_RECURRINGTASK_NO_ID_ATTRIBUTE, ERR_RECURRINGTASK_MULTIPLE_ID_TYPES, ERR_RECURRINGTASK_NO_ID);
    recurringTaskID = getSingleAttributeValue(attr,
        ResultCode.OBJECTCLASS_VIOLATION, ERR_RECURRINGTASK_MULTIPLE_ID_VALUES, ATTR_RECURRING_TASK_ID);

    // Get the schedule for this task.
    attr = getSingleAttribute(recurringTaskEntry, ATTR_RECURRING_TASK_SCHEDULE,ERR_RECURRINGTASK_NO_SCHEDULE_ATTRIBUTE,
            ERR_RECURRINGTASK_MULTIPLE_SCHEDULE_TYPES, ERR_RECURRINGTASK_NO_SCHEDULE_VALUES);
    String taskScheduleTab = getSingleAttributeValue(attr,
        ResultCode.CONSTRAINT_VIOLATION, ERR_RECURRINGTASK_MULTIPLE_SCHEDULE_VALUES, ATTR_RECURRING_TASK_SCHEDULE);

    boolean[][] taskArrays = new boolean[][]{null, null, null, null, null};

    parseTaskTab(taskScheduleTab, taskArrays, true);

    minutesArray = taskArrays[MINUTE_INDEX];
    hoursArray = taskArrays[HOUR_INDEX];
    daysArray = taskArrays[DAY_INDEX];
    monthArray = taskArrays[MONTH_INDEX];
    weekdayArray = taskArrays[WEEKDAY_INDEX];

    // Get the class name from the entry.  If there isn't one, then fail.
    attr = getSingleAttribute(recurringTaskEntry, ATTR_TASK_CLASS, ERR_TASKSCHED_NO_CLASS_ATTRIBUTE,
        ERR_TASKSCHED_MULTIPLE_CLASS_TYPES, ERR_TASKSCHED_NO_CLASS_VALUES);
    taskClassName = getSingleAttributeValue(attr,
        ResultCode.CONSTRAINT_VIOLATION, ERR_TASKSCHED_MULTIPLE_CLASS_VALUES, ATTR_TASK_CLASS);


    // Make sure that the specified class can be loaded.
    Class<?> taskClass;
    try
    {
      taskClass = DirectoryServer.loadClass(taskClassName);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_RECURRINGTASK_CANNOT_LOAD_CLASS.
          get(taskClassName, ATTR_TASK_CLASS, getExceptionMessage(e));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message, e);
    }


    // Make sure that the specified class can be instantiated as a task.
    try
    {
      task = (Task) taskClass.newInstance();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_RECURRINGTASK_CANNOT_INSTANTIATE_CLASS_AS_TASK.get(
          taskClassName, Task.class.getName());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message, e);
    }


    // Make sure that we can initialize the task with the information in the
    // provided entry.
    try
    {
      task.initializeTaskInternal(serverContext, taskScheduler, recurringTaskEntry);
    }
    catch (InitializationException ie)
    {
      logger.traceException(ie);

      LocalizableMessage message = ERR_RECURRINGTASK_CANNOT_INITIALIZE_INTERNAL.get( taskClassName, ie.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, ie);
    }

    task.initializeTask();
  }

  private String getSingleAttributeValue(Attribute attr, ResultCode erorrRc, Arg1<Object> multipleAttrValueErrorMsg,
      String attrName) throws DirectoryException
  {
    Iterator<ByteString> it = attr.iterator();
    ByteString value = it.next();
    if (it.hasNext())
    {
      throw new DirectoryException(erorrRc, multipleAttrValueErrorMsg.get(attrName));
    }
    return value.toString();
  }

  private Attribute getSingleAttribute(Entry taskEntry, String attrName, Arg1<Object> noEntryErrorMsg,
      Arg1<Object> multipleEntriesErrorMsg, Arg1<Object> noAttrValueErrorMsg) throws DirectoryException
  {
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attrName);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if (attrList.isEmpty())
    {
      LocalizableMessage message = noEntryErrorMsg.get(attrName);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    if (attrList.size() > 1)
    {
      LocalizableMessage message = multipleEntriesErrorMsg.get(attrName);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    Attribute attr = attrList.get(0);
    if (attr.isEmpty())
    {
      LocalizableMessage message = noAttrValueErrorMsg.get(attrName);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
    return attr;
  }



  /**
   * Retrieves the unique ID assigned to this recurring task.
   *
   * @return  The unique ID assigned to this recurring task.
   */
  public String getRecurringTaskID()
  {
    return recurringTaskID;
  }



  /**
   * Retrieves the DN of the entry containing the data for this recurring task.
   *
   * @return  The DN of the entry containing the data for this recurring task.
   */
  public DN getRecurringTaskEntryDN()
  {
    return recurringTaskEntryDN;
  }



  /**
   * Retrieves the entry containing the data for this recurring task.
   *
   * @return  The entry containing the data for this recurring task.
   */
  public Entry getRecurringTaskEntry()
  {
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
    return taskClassName;
  }



  /**
   * Schedules the next iteration of this recurring task for processing.
   * @param  calendar date and time to schedule next iteration from.
   * @return The task that has been scheduled for processing.
   * @throws DirectoryException to indicate an error.
   */
  public Task scheduleNextIteration(GregorianCalendar calendar)
          throws DirectoryException
  {
    Task nextTask = null;
    Date nextTaskDate = null;

    try {
      nextTaskDate = getNextIteration(calendar);
    } catch (IllegalArgumentException e) {
      logger.traceException(e);

      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
        ERR_RECURRINGTASK_INVALID_TOKENS_COMBO.get(
        ATTR_RECURRING_TASK_SCHEDULE));
    }

    SimpleDateFormat dateFormat = new SimpleDateFormat(
      DATE_FORMAT_COMPACT_LOCAL_TIME);
    String nextTaskStartTime = dateFormat.format(nextTaskDate);

    try {
      // Make a regular task iteration from this recurring task.
      nextTask = task.getClass().newInstance();
      Entry nextTaskEntry = recurringTaskEntry.duplicate(false);
      SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
      String nextTaskID = task.getTaskID() + "-" + df.format(nextTaskDate);
      String nextTaskIDName = NAME_PREFIX_TASK + "id";
      nextTaskEntry.replaceAttribute(Attributes.create(nextTaskIDName, nextTaskID));

      RDN nextTaskRDN = new RDN(DirectoryServer.getSchema().getAttributeType(nextTaskIDName), nextTaskID);
      DN nextTaskDN = taskScheduler.getTaskBackend().getScheduledTasksParentDN().child(nextTaskRDN);
      nextTaskEntry.setDN(nextTaskDN);

      String nextTaskStartTimeName = NAME_PREFIX_TASK + "scheduled-start-time";
      nextTaskEntry.replaceAttribute(Attributes.create(nextTaskStartTimeName, nextTaskStartTime));

      nextTask.initializeTaskInternal(serverContext, taskScheduler, nextTaskEntry);
      nextTask.initializeTask();
    } catch (Exception e) {
      // Should not happen, debug log it otherwise.
      logger.traceException(e);
    }

    return nextTask;
  }

  /**
   * Parse and validate recurring task schedule.
   * @param taskSchedule recurring task schedule tab in crontab(5) format.
   * @throws DirectoryException to indicate an error.
   */
  public static void parseTaskTab(String taskSchedule) throws DirectoryException
  {
    parseTaskTab(taskSchedule, new boolean[][]{null, null, null, null, null},
        false);
  }

  /**
   * Parse and validate recurring task schedule.
   * @param taskSchedule recurring task schedule tab in crontab(5) format.
   * @param arrays an array of 5 boolean arrays.  The array has the following
   * structure: {minutesArray, hoursArray, daysArray, monthArray, weekdayArray}.
   * @param referToTaskEntryAttribute whether the error messages must refer
   * to the task entry attribute or not.  This is used to have meaningful
   * messages when the {@link #parseTaskTab(String)} is called to validate
   * a crontab formatted string.
   * @throws DirectoryException to indicate an error.
   */
  private static void parseTaskTab(String taskSchedule, boolean[][] arrays,
      boolean referToTaskEntryAttribute) throws DirectoryException
  {
    StringTokenizer st = new StringTokenizer(taskSchedule);

    if (st.countTokens() != TASKTAB_NUM_TOKENS) {
      if (referToTaskEntryAttribute)
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_RECURRINGTASK_INVALID_N_TOKENS.get(
                ATTR_RECURRING_TASK_SCHEDULE));
      }
      else
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_RECURRINGTASK_INVALID_N_TOKENS_SIMPLE.get());
      }
    }

    for (TaskTab taskTabToken : TaskTab.values()) {
      String token = st.nextToken();
      switch (taskTabToken) {
        case MINUTE:
          try {
            arrays[MINUTE_INDEX] = parseTaskTabField(token, 0, 59);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MINUTE_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MINUTE_TOKEN_SIMPLE.get());
            }
          }
          break;
        case HOUR:
          try {
            arrays[HOUR_INDEX] = parseTaskTabField(token, 0, 23);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_HOUR_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_HOUR_TOKEN_SIMPLE.get());
            }
          }
          break;
        case DAY:
          try {
            arrays[DAY_INDEX] = parseTaskTabField(token, 1, 31);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_DAY_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_DAY_TOKEN_SIMPLE.get());
            }
          }
          break;
        case MONTH:
          try {
            arrays[MONTH_INDEX] = parseTaskTabField(token, 1, 12);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MONTH_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_MONTH_TOKEN_SIMPLE.get());
            }
          }
          break;
        case WEEKDAY:
          try {
            arrays[WEEKDAY_INDEX] = parseTaskTabField(token, 0, 6);
          } catch (IllegalArgumentException e) {
            if (referToTaskEntryAttribute)
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_WEEKDAY_TOKEN.get(
                      ATTR_RECURRING_TASK_SCHEDULE));
            }
            else
            {
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  ERR_RECURRINGTASK_INVALID_WEEKDAY_TOKEN_SIMPLE.get());
            }
          }
          break;
      }
    }
  }

  /**
   * Parse and validate recurring task schedule field.
   *
   * @param tabField recurring task schedule field in crontab(5) format.
   * @param minValue minimum value allowed for this field.
   * @param maxValue maximum value allowed for this field.
   * @return boolean schedule slots range set according to the schedule field.
   * @throws IllegalArgumentException if tab field is invalid.
   */
  public static boolean[] parseTaskTabField(String tabField,
    int minValue, int maxValue) throws IllegalArgumentException
  {
    boolean[] valueList = new boolean[maxValue + 1];

    // Wildcard with optional increment.
    Matcher m = wildcardPattern.matcher(tabField);
    if (m.matches() && m.groupCount() == 1)
    {
      String stepString = m.group(1);
      int increment = isValueAbsent(stepString) ? 1 : Integer.parseInt(stepString);
      for (int i = minValue; i <= maxValue; i += increment)
      {
        valueList[i] = true;
      }
      return valueList;
    }

    // List.
    for (String listVal : tabField.split(","))
    {
      // Single number.
      m = exactPattern.matcher(listVal);
      if (m.matches() && m.groupCount() == 1)
      {
        String exactValue = m.group(1);
        if (isValueAbsent(exactValue))
        {
          throw new IllegalArgumentException();
        }
        int value = Integer.parseInt(exactValue);
        if (value < minValue || value > maxValue)
        {
          throw new IllegalArgumentException();
        }
        valueList[value] = true;
        continue;
      }

      // Range of numbers with optional increment.
      m = rangePattern.matcher(listVal);
      if (m.matches() && m.groupCount() == 3) {
        String startString = m.group(1);
        String endString = m.group(2);
        String stepString = m.group(3);
        int increment = isValueAbsent(stepString) ? 1 : Integer.parseInt(stepString);
        if (isValueAbsent(startString) || isValueAbsent(endString))
        {
          throw new IllegalArgumentException();
        }
        int startValue = Integer.parseInt(startString);
        int endValue = Integer.parseInt(endString);
        if (startValue > endValue || startValue < minValue || endValue > maxValue)
        {
          throw new IllegalArgumentException();
        }
        for (int i = startValue; i <= endValue; i += increment)
        {
          valueList[i] = true;
        }
        continue;
      }

      // Can only have a list of numbers and ranges.
      throw new IllegalArgumentException();
    }

    return valueList;
  }

  /**
   * Check if a String from a Matcher group is absent. Matcher returns empty strings
   * for optional groups that are absent.
   *
   * @param s A string returned from Matcher.group()
   * @return true if the string is unusable, false if it is usable.
   */
  private static boolean isValueAbsent(String s)
  {
    return s == null || s.length() == 0;
  }
  /**
   * Get next recurring slot from the range.
   * @param timesList the range.
   * @param fromNow the current slot.
   * @return next recurring slot in the range.
   */
  private int getNextTimeSlice(boolean[] timesList, int fromNow)
  {
    for (int i = fromNow; i < timesList.length; i++) {
      if (timesList[i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get next task iteration date according to recurring schedule.
   * @param  calendar date and time to schedule from.
   * @return next task iteration date.
   * @throws IllegalArgumentException if recurring schedule is invalid.
   */
  private Date getNextIteration(GregorianCalendar calendar)
          throws IllegalArgumentException
  {
    int minute, hour, day, month, weekday;
    calendar.setFirstDayOfWeek(GregorianCalendar.SUNDAY);
    calendar.add(GregorianCalendar.MINUTE, 1);
    calendar.set(GregorianCalendar.SECOND, 0);
    calendar.set(GregorianCalendar.MILLISECOND, 0);
    calendar.setLenient(false);

    // Weekday
    for (;;) {
      // Month
      for (;;) {
        // Day
        for (;;) {
          // Hour
          for (;;) {
            // Minute
            for (;;) {
              minute = getNextTimeSlice(minutesArray, calendar.get(MINUTE));
              if (minute == -1) {
                calendar.set(GregorianCalendar.MINUTE, 0);
                calendar.add(GregorianCalendar.HOUR_OF_DAY, 1);
              } else {
                calendar.set(GregorianCalendar.MINUTE, minute);
                break;
              }
            }
            hour = getNextTimeSlice(hoursArray,
              calendar.get(GregorianCalendar.HOUR_OF_DAY));
            if (hour == -1) {
              calendar.set(GregorianCalendar.HOUR_OF_DAY, 0);
              calendar.add(GregorianCalendar.DAY_OF_MONTH, 1);
            } else {
              calendar.set(GregorianCalendar.HOUR_OF_DAY, hour);
              break;
            }
          }
          day = getNextTimeSlice(daysArray,
            calendar.get(GregorianCalendar.DAY_OF_MONTH));
          if (day == -1 || day > calendar.getActualMaximum(DAY_OF_MONTH))
          {
            calendar.set(GregorianCalendar.DAY_OF_MONTH, 1);
            calendar.add(GregorianCalendar.MONTH, 1);
          } else {
            calendar.set(GregorianCalendar.DAY_OF_MONTH, day);
            break;
          }
        }
        month = getNextTimeSlice(monthArray, calendar.get(MONTH) + 1);
        if (month == -1) {
          calendar.set(GregorianCalendar.MONTH, 0);
          calendar.add(GregorianCalendar.YEAR, 1);
        }
        else if (day > LEAP_MONTH_LENGTH[month - 1]
            && (getNextTimeSlice(daysArray, 1) != day
                || getNextTimeSlice(monthArray, 1) != month))
        {
          calendar.set(DAY_OF_MONTH, 1);
          calendar.add(MONTH, 1);
        } else if (day > MONTH_LENGTH[month - 1]
            && !calendar.isLeapYear(calendar.get(YEAR))) {
          calendar.add(YEAR, 1);
        } else {
          calendar.set(MONTH, month - 1);
          break;
        }
      }
      weekday = getNextTimeSlice(weekdayArray, calendar.get(DAY_OF_WEEK) - 1);
      if (weekday == -1
          || weekday != calendar.get(DAY_OF_WEEK) - 1)
      {
        calendar.add(GregorianCalendar.DAY_OF_MONTH, 1);
      } else {
        break;
      }
    }

    return calendar.getTime();
  }
}
