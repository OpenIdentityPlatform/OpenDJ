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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.tools.tasks.TaskEntry;

/**
 * The table used to display the tasks.
 *
 */
public class TaskTableModel  extends SortableTableModel
implements Comparator<TaskEntry>
{
  private static final long serialVersionUID = -351142550147124L;
  private Set<TaskEntry> data = new HashSet<TaskEntry>();
  private ArrayList<TaskEntry> dataSourceArray = new ArrayList<TaskEntry>();

  LinkedHashSet<Message> displayedAttributes = new LinkedHashSet<Message>();
  final LinkedHashSet<Message> defaultAttributes = new LinkedHashSet<Message>();
  {
    defaultAttributes.add(INFO_TASKINFO_FIELD_ID.get());
    defaultAttributes.add(INFO_TASKINFO_FIELD_TYPE.get());
    defaultAttributes.add(INFO_TASKINFO_FIELD_STATUS.get());
    defaultAttributes.add(INFO_CTRL_PANEL_TASK_CANCELABLE.get());
  }
  LinkedHashSet<Message> allAttributes = new LinkedHashSet<Message>();
  {
    allAttributes.addAll(defaultAttributes);
    allAttributes.add(INFO_TASKINFO_FIELD_SCHEDULED_START.get());
    allAttributes.add(INFO_TASKINFO_FIELD_ACTUAL_START.get());
    allAttributes.add(INFO_TASKINFO_FIELD_COMPLETION_TIME.get());
    allAttributes.add(INFO_TASKINFO_FIELD_DEPENDENCY.get());
    allAttributes.add(INFO_TASKINFO_FIELD_FAILED_DEPENDENCY_ACTION.get());
    allAttributes.add(INFO_TASKINFO_FIELD_NOTIFY_ON_COMPLETION.get());
    allAttributes.add(INFO_TASKINFO_FIELD_NOTIFY_ON_ERROR.get());
  }

  private String[] columnNames = {};

  /**
   * The sort column of the table.
   */
  private int sortColumn = 0;
  /**
   * Whether the sorting is ascending or descending.
   */
  private boolean sortAscending = true;

  /**
   * Default constructor.
   */
  public TaskTableModel()
  {
    super();
    setAttributes(defaultAttributes);
  }

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<TaskEntry> newData)
  {
    if (!newData.equals(data))
    {
      data.clear();
      data.addAll(newData);
      updateDataArray();
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  public void forceResort()
  {
    updateDataArray();
    fireTableDataChanged();
  }

  /**
   * Updates the table model contents, sorts its contents depending on the
   * sort options set by the user and updates the column structure.
   */
  public void forceDataStructureChange()
  {
    updateDataArray();
    fireTableStructureChanged();
    fireTableDataChanged();
  }

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<TaskEntry> sortedSet = new TreeSet<TaskEntry>(this);
    sortedSet.addAll(data);
    dataSourceArray.clear();
    for (TaskEntry task : sortedSet)
    {
      dataSourceArray.add(task);
    }
  }

  /**
   * Sets the operations displayed by this table model.
   * @param attributes the attributes displayed by this table model.
   */
  public void setAttributes(LinkedHashSet<Message> attributes)
  {
    if (!allAttributes.containsAll(attributes))
    {
      throw new IllegalArgumentException(
          "Some of the provided attributes are not valid.");
    }
    this.displayedAttributes.clear();
    this.displayedAttributes.addAll(attributes);
    int columnCount = attributes.size();
    columnNames = new String[columnCount];
    int i = 0;
    for (Message attribute : attributes)
    {
      columnNames[i] = getHeader(attribute, 15);
      i++;
    }
  }

  /**
   * {@inheritDoc}
   */
  public Class<?> getColumnClass(int column)
  {
    return Message.class;
  }

  /**
   * {@inheritDoc}
   */
  public String getColumnName(int col) {
    return columnNames[col];
  }

  /**
   * {@inheritDoc}
   */
  public Object getValueAt(int row, int column)
  {
    Message value;
    column = getFixedOrderColumn(column);
    TaskEntry taskEntry = get(row);
    switch (column)
    {
    case 0:
      value = Message.raw(taskEntry.getId());
      break;
    case 1:
      value = taskEntry.getType();
      break;
    case 2:
      value = taskEntry.getState();
      break;
    case 3:
      if (taskEntry.isCancelable())
      {
        value = INFO_CTRL_PANEL_TASK_IS_CANCELABLE.get();
      }
      else
      {
        value = INFO_CTRL_PANEL_TASK_IS_NOT_CANCELABLE.get();
      }
      break;
    case 4:
      if (TaskState.isRecurring(get(row).getTaskState()))
      {
        value = taskEntry.getScheduleTab();
      } else {
        value = taskEntry.getScheduledStartTime();
        if (value == null || value.equals(Message.EMPTY))
        {
          value = INFO_TASKINFO_IMMEDIATE_EXECUTION.get();
        }
      }
      break;
    case 5:
      value = taskEntry.getActualStartTime();
      break;
    case 6:
      value = taskEntry.getCompletionTime();
      break;
    case 7:
      value = getValue(taskEntry.getDependencyIds(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
      break;
    case 8:
      value = taskEntry.getFailedDependencyAction();
      if (value == null)
      {
        value = INFO_TASKINFO_NONE.get();
      }
      break;
    case 9:
      value = getValue(taskEntry.getCompletionNotificationEmailAddresses(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
      break;
    case 10:
      value = getValue(taskEntry.getErrorNotificationEmailAddresses(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
      break;
    default:
      throw new IllegalArgumentException("Invalid column: "+column);
    }
    return value;
  }

  /**
   * Returns the row count.
   * @return the row count.
   */
  public int getRowCount()
  {
    return dataSourceArray.size();
  }

  /**
   * Returns the column count.
   * @return the column count.
   */
  public int getColumnCount()
  {
    return columnNames.length;
  }

  /**
   * Gets the TaskDescriptor in a given row.
   * @param row the row.
   * @return the TaskDescriptor in a given row.
   */
  public TaskEntry get(int row)
  {
    return dataSourceArray.get(row);
  }

  /**
   * Returns the set of attributes ordered.
   * @return the set of attributes ordered.
   */
  public LinkedHashSet<Message> getDisplayedAttributes()
  {
    return displayedAttributes;
  }

  /**
   * Returns the set of attributes ordered.
   * @return the set of attributes ordered.
   */
  public LinkedHashSet<Message> getAllAttributes()
  {
    return allAttributes;
  }

  /**
   * {@inheritDoc}
   */
  public int compare(TaskEntry desc1, TaskEntry desc2)
  {
    int result;
    ArrayList<Integer> possibleResults = new ArrayList<Integer>();

    possibleResults.add(desc1.getId().compareTo(desc2.getId()));
    possibleResults.add(desc1.getType().compareTo(desc2.getType()));
    possibleResults.add(desc1.getState().compareTo(desc2.getState()));
    possibleResults.add(String.valueOf(desc1.isCancelable()).compareTo(
        String.valueOf(desc2.isCancelable())));

    result = possibleResults.get(getSortColumn());
    if (result == 0)
    {
      for (int i : possibleResults)
      {
        if (i != 0)
        {
          result = i;
          break;
        }
      }
    }
    if (!isSortAscending())
    {
      result = -result;
    }
    return result;
  }

  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  public int getSortColumn()
  {
    return sortColumn;
  }

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  public void setSortColumn(int sortColumn)
  {
    this.sortColumn = sortColumn;
  }

  private int getFixedOrderColumn(int column)
  {
    int fixedOrderColumn = 0;
    int i=0;
    Message colMsg = null;
    for (Message msg : displayedAttributes)
    {
      if (i == column)
      {
        colMsg = msg;
        break;
      }
      i++;
    }
    for (Message msg : allAttributes)
    {
      if (msg.equals(colMsg))
      {
        break;
      }
      fixedOrderColumn++;
    }
    return fixedOrderColumn;
  }

  private Message getValue(List<String> values, Message valueIfEmpty)
  {
    Message msg;
    if (values.isEmpty())
    {
      msg = valueIfEmpty;
    }
    else
    {
      String s = Utils.getStringFromCollection(values, "<br>");
      if (values.size() > 1)
      {
        msg = Message.raw(
            "<html>"+Utilities.applyFont(s, ColorAndFontConstants.tableFont));
      }
      else
      {
        msg = Message.raw(s);
      }
    }
    return msg;
  }
}
