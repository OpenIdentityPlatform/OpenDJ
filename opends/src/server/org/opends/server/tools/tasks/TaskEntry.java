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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.tools.tasks;

import org.opends.messages.Message;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.types.Entry;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import static org.opends.server.util.ServerConstants.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.Date;
import java.util.Collections;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.text.ParseException;

/**
 * Processes information from a task entry from the directory and
 * provides accessors for attribute information.  In some cases the
 * data is formatted into more human-friendly formats.
 */
public class TaskEntry {

  private static Map<String, Message> mapClassToTypeName =
          new HashMap<String, Message>();

  private static Map<String, Message> mapAttrToDisplayName =
          new HashMap<String, Message>();


  // These attributes associated with the ds-task object
  // class are all handled explicitly below in the constructor
  private static Set<String> supAttrNames = new HashSet<String>();
  static {
    supAttrNames.add("ds-task-id");
    supAttrNames.add("ds-task-class-name");
    supAttrNames.add("ds-task-state");
    supAttrNames.add("ds-task-scheduled-start-time");
    supAttrNames.add("ds-task-actual-start-time");
    supAttrNames.add("ds-task-completion-time");
    supAttrNames.add("ds-task-dependency-id");
    supAttrNames.add("ds-task-failed-dependency-action");
    supAttrNames.add("ds-task-log-message");
    supAttrNames.add("ds-task-notify-on-completion");
    supAttrNames.add("ds-task-notify-on-error");
  }

  private String id;
  private String className;
  private String state;
  private String schedStart;
  private String actStart;
  private String compTime;
  private List<String> depends;
  private String depFailAct;
  private List<String> logs;
  private List<String> notifyComp;
  private List<String> notifyErr;
  private DN dn;

  /**
   * Task of the same type that implements.  Used for obtaining
   * task name and attribute display information.
   */
  private Task task;

  private Map<Message, List<String>> taskSpecificAttrValues =
          new HashMap<Message, List<String>>();

  /**
   * Creates a parameterized instance.
   *
   * @param entry to wrap
   */
  public TaskEntry(Entry entry) {
    dn = entry.getDN();

    String p = "ds-task-";
    id =         getSingleStringValue(entry, p + "id");
    className =  getSingleStringValue(entry, p + "class-name");
    state =      getSingleStringValue(entry, p + "state");
    schedStart = getSingleStringValue(entry, p + "scheduled-start-time");
    actStart =   getSingleStringValue(entry, p + "actual-start-time");
    compTime =   getSingleStringValue(entry, p + "completion-time");
    depends =    getMultiStringValue(entry,  p + "dependency-id");
    depFailAct = getSingleStringValue(entry, p + "failed-dependency-action");
    logs =       getMultiStringValue(entry,  p + "log-message");
    notifyErr =  getMultiStringValue(entry,  p + "notify-on-completion");
    notifyComp = getMultiStringValue(entry,  p + "notify-on-error");


    // Build a map of non-superior attribute value pairs for display
    Map<AttributeType, List<Attribute>> attrMap = entry.getUserAttributes();
    for (AttributeType type : attrMap.keySet()) {
      String typeName = type.getNormalizedPrimaryName();

      // See if we've handled it already above
      if (!supAttrNames.contains(typeName)) {
        Message attrTypeName = getAttributeDisplayName(
                type.getNormalizedPrimaryName());
        List<Attribute> attrList = entry.getUserAttribute(type);
        for (Attribute attr : attrList) {
          LinkedHashSet<AttributeValue> valuesSet = attr.getValues();
          for (AttributeValue av : valuesSet) {
            List<String> valueList = taskSpecificAttrValues.get(attrTypeName);
            if (valueList == null) {
              valueList = new ArrayList<String>();
              taskSpecificAttrValues.put(attrTypeName, valueList);
            }
            valueList.add(av.getStringValue());
          }
        }
      }
    }
  }

  /**
   * Gets the DN of the wrapped entry.
   *
   * @return DN of entry
   */
  public DN getDN() {
    return dn;
  }

  /**
   * Gets the ID of the task.
   *
   * @return String ID of the task
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the name of the class implementing the task represented here.
   *
   * @return String name of class
   */
  public String getClassName() {
    return className;
  }

  /**
   * Gets the state of the task.
   *
   * @return Message representing state
   */
  public Message getState() {
    Message m = Message.EMPTY;
    if (state != null) {
      TaskState ts = TaskState.fromString(state);
      if (ts != null) {
        m = ts.getDisplayName();
      }
    }
    return m;
  }

  /**
   * Gets the human-friendly scheduled time.
   *
   * @return String time
   */
  public Message getScheduledStartTime() {
    return formatTimeString(schedStart);
  }

  /**
   * Gets the human-friendly start time.
   *
   * @return String time
   */
  public Message getActualStartTime() {
    return formatTimeString(actStart);
  }

  /**
   * Gets the human-friendly completion time.
   *
   * @return String time
   */
  public Message getCompletionTime() {
    return formatTimeString(compTime);
  }

  /**
   * Gets the IDs of tasks upon which this task depends.
   *
   * @return array of IDs
   */
  public List<String> getDependencyIds() {
    return Collections.unmodifiableList(depends);
  }

  /**
   * Gets the action to take if this task fails.
   *
   * @return String action
   */
  public Message getFailedDependencyAction() {
    Message m = null;
    if (depFailAct != null) {
      FailedDependencyAction fda =
              FailedDependencyAction.fromString(depFailAct);
      if (fda != null) {
        m = fda.getDisplayName();
      }
    }
    return m;
  }

  /**
   * Gets the logs associated with this task's execution.
   *
   * @return array of log messages
   */
  public List<Message> getLogMessages() {
    List<Message> formattedLogs = new ArrayList<Message>();
    for (String aLog : logs) {
      formattedLogs.add(Message.raw(formatLogMessage(aLog)));
    }
    return Collections.unmodifiableList(formattedLogs);
  }

  /**
   * Gets the email messages that will be used for notifications
   * when the task completes.
   *
   * @return array of email addresses
   */
  public List<String> getCompletionNotificationEmailAddresses() {
    return Collections.unmodifiableList(notifyComp);
  }

  /**
   * Gets the email messages that will be used for notifications
   * when the task encounters an error.
   *
   * @return array of email addresses
   */
  public List<String> getErrorNotificationEmailAddresses() {
    return Collections.unmodifiableList(notifyErr);
  }

  /**
   * Gets a user presentable string indicating the type of this task.
   *
   * @return Message type
   */
  public Message getType() {
    Message type = Message.EMPTY;
    if (className != null) {
      type = mapClassToTypeName.get(className);
      if (type == null) {
        Task task = getTask();
        if (task != null) {
          try {
            Method m = Task.class.getMethod("getDisplayName");
            Object oName = m.invoke(task);
            if (oName instanceof Message) {
              mapClassToTypeName.put(className, (Message) oName);
              type = (Message) oName;
            }
          } catch (Exception e) {
            // ignore; this is best effort
          }
        }
      }

      // If we still can't get the type just resort
      // to the class displayName
      if (type == null) {
        type = Message.raw(className);
      }
    }
    return type;
  }

  /**
   * Indicates whether or not this task supports a cancel operation.
   *
   * @return boolean where true means this task supports being canceled.
   */
  public boolean isCancelable() {
    boolean cancelable = false;
    TaskState state = getTaskState();
    if (state != null) {
      Task task = getTask();
      cancelable = (TaskState.isPending(state) ||
              (TaskState.isRunning(state) &&
                      task != null &&
                      task.isInterruptable()));
    }
    return cancelable;
  }

  /**
   * Gets a mapping of attributes that are specific to the implementing
   * task as opposed to the superior, or base, task.
   *
   * @return mapping of atribute field labels to lists of string values for
   *         each field.
   */
  public Map<Message, List<String>> getTaskSpecificAttributeValuePairs() {
    return taskSpecificAttrValues;
  }

  /**
   * Gets the task state.
   *
   * @return TaskState of task
   */
  public TaskState getTaskState() {
    TaskState ts = null;
    if (state != null) {
      ts = TaskState.fromString(state);
    }
    return ts;
  }

  /**
   * Indicates whether or not this task is done.
   *
   * @return boolean where true means this task is done
   */
  public boolean isDone() {
    TaskState ts = getTaskState();
    return ts != null && TaskState.isDone(ts);
  }

  private String getSingleStringValue(Entry entry, String attrName) {
    List<Attribute> attrList = entry.getAttribute(attrName);
    if (attrList != null && attrList.size() == 1) {
      Set<AttributeValue> values = attrList.get(0).getValues();
      if (values != null && values.size() == 1) {
        return values.iterator().next().getStringValue();
      }
    }
    return "";
  }

  private List<String> getMultiStringValue(Entry entry, String attrName) {
    List<String> valuesList = new ArrayList<String>();
    List<Attribute> attrList = entry.getAttribute(attrName);
    if (attrList != null) {
      for (Attribute attr : attrList) {
        Set<AttributeValue> values = attr.getValues();
        if (values != null) {
          for (AttributeValue value : values) {
            valuesList.add(value.getStringValue());
          }
        }
      }
    }
    return valuesList;
  }

  private Message getAttributeDisplayName(String attrName) {
    Message name = mapAttrToDisplayName.get(attrName);
    if (name == null) {
      Task task = getTask();
      if (task != null) {
        try {
          Method m = Task.class.getMethod(
                  "getAttributeDisplayName", String.class);
          Object o = m.invoke(task, attrName);
          if (o != null && Message.class.isAssignableFrom(o.getClass())) {
            name= (Message)o;
            mapAttrToDisplayName.put(attrName, name);
          }
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (name == null) {
      name = Message.raw(attrName);
    }
    return name;
  }

  /**
   * Formats a log message for appending to the table.
   * @param msg to format
   * @return formatted message
   */
  private String formatLogMessage(String msg) {
    // Use this to prepend to log messages.  Since they
    // are long and usually wrap, without this it is
    // difficult to tell where one stops and another starts
    StringBuffer sb = new StringBuffer();
    sb.append("\u2022");
    sb.append(" ");
    sb.append(msg);
    return sb.toString();
  }

  /**
   * Formats a time string into a human friendly format.
   * @param timeString the is human hostile
   * @return string of time that is human friendly
   */
  private Message formatTimeString(String timeString) {
    Message ret = Message.EMPTY;
    if (timeString != null && timeString.length() > 0) {
      try {
        SimpleDateFormat dateFormat;
        if (timeString.endsWith("Z")) {
          dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
          dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        } else {
          dateFormat = new SimpleDateFormat(DATE_FORMAT_COMPACT_LOCAL_TIME);
        }
        Date date = dateFormat.parse(timeString);
        DateFormat df = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.LONG);
        String dateString = df.format(date);
        ret = Message.raw(dateString);
      } catch (ParseException pe){
        ret = Message.raw(timeString);
      }
    }
    return ret;
  }

  private Task getTask() {
    if (task == null && className != null) {
      try {
        Class<?> clazz = Class.forName(className);
        Object o = clazz.newInstance();
        if (Task.class.isAssignableFrom(o.getClass())) {
          this.task = (Task) o;
        }
      } catch (Exception e) {
        // ignore; this is best effort
      }
    }
    return task;
  }

}
