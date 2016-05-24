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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.tasks;

import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;

/**
 * Processes information from a task entry from the directory and
 * provides accessors for attribute information.  In some cases the
 * data is formatted into more human-friendly formats.
 */
public class TaskEntry {

  private static Map<String, LocalizableMessage> mapClassToTypeName = new HashMap<>();
  private static Map<String, LocalizableMessage> mapAttrToDisplayName = new HashMap<>();

  private int hashCode;

  /**
   * These attributes associated with the ds-task object
   * class are all handled explicitly below in the constructor.
   */
  private static final Set<String> supAttrNames = newHashSet(
    // @formatter:off
    "ds-task-id",
    "ds-task-class-name",
    "ds-task-state",
    "ds-task-scheduled-start-time",
    "ds-task-actual-start-time",
    "ds-task-completion-time",
    "ds-task-dependency-id",
    "ds-task-failed-dependency-action",
    "ds-task-log-message",
    "ds-task-notify-on-completion",
    "ds-task-notify-on-error",
    "ds-recurring-task-id",
    "ds-recurring-task-schedule");
    // @formatter:on

  private String id;
  private String className;
  private String state;
  private String schedStart;
  private String actStart;
  private String compTime;
  private String schedTab;
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

  private Map<LocalizableMessage, List<String>> taskSpecificAttrValues = new HashMap<>();

  /**
   * Creates a parameterized instance.
   *
   * @param entry to wrap
   */
  public TaskEntry(Entry entry) {
    dn = entry.getName();

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
    notifyErr =  getMultiStringValue(entry,  p + "notify-on-error");
    notifyComp = getMultiStringValue(entry,  p + "notify-on-completion");
    schedTab =   getSingleStringValue(entry, "ds-recurring-task-schedule");

    // Build a map of non-superior attribute value pairs for display
    for (AttributeType attrType : entry.getUserAttributes().keySet()) {
      // See if we've handled it already above
      if (!hasAnyNameOrOID(attrType, supAttrNames)) {
        LocalizableMessage attrTypeName = getAttributeDisplayName(attrType);
        for (Attribute attr : entry.getUserAttribute(attrType)) {
          for (ByteString av : attr) {
            List<String> valueList = taskSpecificAttrValues.get(attrTypeName);
            if (valueList == null) {
              valueList = new ArrayList<>();
              taskSpecificAttrValues.put(attrTypeName, valueList);
            }
            valueList.add(av.toString());
          }
        }
      }
    }

    hashCode += id.hashCode();
    hashCode += className.hashCode();
    hashCode += state.hashCode();
    hashCode += schedStart.hashCode();
    hashCode += actStart.hashCode();
    hashCode += compTime.hashCode();
    hashCode += depends.hashCode();
    hashCode += depFailAct.hashCode();
    hashCode += logs.hashCode();
    hashCode += notifyErr.hashCode();
    hashCode += notifyComp.hashCode();
    hashCode += schedTab.hashCode();
    hashCode += taskSpecificAttrValues.hashCode();
  }

  private boolean hasAnyNameOrOID(AttributeType attrType, Set<String> attrNames)
  {
    for (String attrName : attrNames)
    {
      if (attrType.hasNameOrOID(attrName))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (! (o instanceof TaskEntry))
    {
      return false;
    }

    TaskEntry e = (TaskEntry) o;

    return e.id.equals(id) &&
    e.className.equals(className) &&
    e.state.equals(state) &&
    e.schedStart.equals(schedStart) &&
    e.actStart.equals(actStart) &&
    e.compTime.equals(compTime) &&
    e.depends.equals(depends) &&
    e.depFailAct.equals(depFailAct) &&
    e.logs.equals(logs) &&
    e.notifyErr.equals(notifyErr) &&
    e.notifyComp.equals(notifyComp) &&
    e.schedTab.equals(schedTab) &&
    e.taskSpecificAttrValues.equals(taskSpecificAttrValues);
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
   * @return LocalizableMessage representing state
   */
  public LocalizableMessage getState() {
    LocalizableMessage m = LocalizableMessage.EMPTY;
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
  public LocalizableMessage getScheduledStartTime() {
    return formatTimeString(schedStart);
  }

  /**
   * Gets the human-friendly start time.
   *
   * @return String time
   */
  public LocalizableMessage getActualStartTime() {
    return formatTimeString(actStart);
  }

  /**
   * Gets the human-friendly completion time.
   *
   * @return String time
   */
  public LocalizableMessage getCompletionTime() {
    return formatTimeString(compTime);
  }

  /**
   * Gets recurring schedule tab.
   *
   * @return LocalizableMessage tab string
   */
  public LocalizableMessage getScheduleTab() {
    return LocalizableMessage.raw(schedTab);
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
  public LocalizableMessage getFailedDependencyAction() {
    LocalizableMessage m = null;
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
  public List<LocalizableMessage> getLogMessages() {
    List<LocalizableMessage> formattedLogs = new ArrayList<>();
    for (String aLog : logs) {
      formattedLogs.add(LocalizableMessage.raw(aLog));
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
   * @return LocalizableMessage type
   */
  public LocalizableMessage getType() {
    LocalizableMessage type = LocalizableMessage.EMPTY;
    if (className != null) {
      type = mapClassToTypeName.get(className);
      if (type == null) {
        Task task = getTask();
        if (task != null) {
          LocalizableMessage message = task.getDisplayName();
          mapClassToTypeName.put(className, message);
          type = message;
        }
      }

      // If we still can't get the type just resort
      // to the class displayName
      if (type == null) {
        type = LocalizableMessage.raw(className);
      }
    }
    return type;
  }

  /**
   * Indicates whether this task supports a cancel operation.
   *
   * @return boolean where true means this task supports being canceled.
   */
  public boolean isCancelable() {
    TaskState state = getTaskState();
    if (state != null) {
      Task task = getTask();
      return TaskState.isPending(state)
          || TaskState.isRecurring(state)
          || (TaskState.isRunning(state)
              && task != null
              && task.isInterruptable());
    }
    return false;
  }

  /**
   * Gets a mapping of attributes that are specific to the implementing
   * task as opposed to the superior, or base, task.
   *
   * @return mapping of attribute field labels to lists of string values for each field.
   */
  public Map<LocalizableMessage, List<String>> getTaskSpecificAttributeValuePairs() {
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
   * Indicates whether this task is done.
   *
   * @return boolean where true means this task is done
   */
  public boolean isDone() {
    TaskState ts = getTaskState();
    return ts != null && TaskState.isDone(ts);
  }

  private String getSingleStringValue(Entry entry, String attrName) {
    List<Attribute> attrList = entry.getAttribute(attrName);
    if (attrList.size() == 1) {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty()) {
        return attr.iterator().next().toString();
      }
    }
    return "";
  }

  private List<String> getMultiStringValue(Entry entry, String attrName) {
    List<String> valuesList = new ArrayList<>();
    for (Attribute attr : entry.getAttribute(attrName)) {
      for (ByteString value : attr) {
        valuesList.add(value.toString());
      }
    }
    return valuesList;
  }

  private LocalizableMessage getAttributeDisplayName(AttributeType attrType) {
    final String attrName = StaticUtils.toLowerCase(attrType.getNameOrOID());
    LocalizableMessage name = mapAttrToDisplayName.get(attrName);
    if (name == null) {
      Task task = getTask();
      if (task != null) {
        try {
          Object o = task.getAttributeDisplayName(attrName);
          if (o instanceof LocalizableMessage) {
            name= (LocalizableMessage)o;
            mapAttrToDisplayName.put(attrName, name);
          }
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (name == null) {
      name = LocalizableMessage.raw(attrName);
    }
    return name;
  }

  /**
   * Formats a time string into a human friendly format.
   * @param timeString the is human hostile
   * @return string of time that is human friendly
   */
  private LocalizableMessage formatTimeString(String timeString) {
    LocalizableMessage ret = LocalizableMessage.EMPTY;
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
        ret = LocalizableMessage.raw(dateString);
      } catch (ParseException pe){
        ret = LocalizableMessage.raw(timeString);
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
