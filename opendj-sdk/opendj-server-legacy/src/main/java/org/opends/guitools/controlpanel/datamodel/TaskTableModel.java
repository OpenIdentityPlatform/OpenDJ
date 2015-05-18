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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.forgerock.util.Utils.*;
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
import org.forgerock.i18n.LocalizableMessage;
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
  private Set<TaskEntry> data = new HashSet<>();
  private ArrayList<TaskEntry> dataSourceArray = new ArrayList<>();

  LinkedHashSet<LocalizableMessage> displayedAttributes = new LinkedHashSet<>();
  final LinkedHashSet<LocalizableMessage> defaultAttributes = new LinkedHashSet<>();
  {
    defaultAttributes.add(INFO_TASKINFO_FIELD_ID.get());
    defaultAttributes.add(INFO_TASKINFO_FIELD_TYPE.get());
    defaultAttributes.add(INFO_TASKINFO_FIELD_STATUS.get());
    defaultAttributes.add(INFO_CTRL_PANEL_TASK_CANCELABLE.get());
  }
  LinkedHashSet<LocalizableMessage> allAttributes = new LinkedHashSet<>();
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
  private int sortColumn;
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
    TreeSet<TaskEntry> sortedSet = new TreeSet<>(this);
    sortedSet.addAll(data);
    dataSourceArray.clear();
    dataSourceArray.addAll(sortedSet);
  }

  /**
   * Sets the operations displayed by this table model.
   * @param attributes the attributes displayed by this table model.
   */
  public void setAttributes(LinkedHashSet<LocalizableMessage> attributes)
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
    for (LocalizableMessage attribute : attributes)
    {
      columnNames[i] = getHeader(attribute, 15);
      i++;
    }
  }

  /** {@inheritDoc} */
  public Class<?> getColumnClass(int column)
  {
    return LocalizableMessage.class;
  }

  /** {@inheritDoc} */
  public String getColumnName(int col) {
    return columnNames[col];
  }

  /** {@inheritDoc} */
  public Object getValueAt(int row, int column)
  {
    LocalizableMessage value;
    column = getFixedOrderColumn(column);
    TaskEntry taskEntry = get(row);
    switch (column)
    {
    case 0:
      return LocalizableMessage.raw(taskEntry.getId());
    case 1:
      return taskEntry.getType();
    case 2:
      return taskEntry.getState();
    case 3:
      return taskEntry.isCancelable()
          ? INFO_CTRL_PANEL_TASK_IS_CANCELABLE.get()
          : INFO_CTRL_PANEL_TASK_IS_NOT_CANCELABLE.get();
    case 4:
      if (TaskState.isRecurring(get(row).getTaskState()))
      {
        return taskEntry.getScheduleTab();
      }

      value = taskEntry.getScheduledStartTime();
      if (value == null || value.equals(LocalizableMessage.EMPTY))
      {
        return INFO_TASKINFO_IMMEDIATE_EXECUTION.get();
      }
      return value;
    case 5:
      return taskEntry.getActualStartTime();
    case 6:
      return taskEntry.getCompletionTime();
    case 7:
      return getValue(taskEntry.getDependencyIds(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
    case 8:
      value = taskEntry.getFailedDependencyAction();
      if (value != null)
      {
        return value;
      }
      return INFO_TASKINFO_NONE.get();
    case 9:
      return getValue(taskEntry.getCompletionNotificationEmailAddresses(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
    case 10:
      return getValue(taskEntry.getErrorNotificationEmailAddresses(),
          INFO_TASKINFO_NONE_SPECIFIED.get());
    default:
      throw new IllegalArgumentException("Invalid column: "+column);
    }
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
  public LinkedHashSet<LocalizableMessage> getDisplayedAttributes()
  {
    return displayedAttributes;
  }

  /**
   * Returns the set of attributes ordered.
   * @return the set of attributes ordered.
   */
  public LinkedHashSet<LocalizableMessage> getAllAttributes()
  {
    return allAttributes;
  }

  /** {@inheritDoc} */
  public int compare(TaskEntry desc1, TaskEntry desc2)
  {
    int result;
    ArrayList<Integer> possibleResults = new ArrayList<>();

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
    LocalizableMessage colMsg = null;
    for (LocalizableMessage msg : displayedAttributes)
    {
      if (i == column)
      {
        colMsg = msg;
        break;
      }
      i++;
    }
    for (LocalizableMessage msg : allAttributes)
    {
      if (msg.equals(colMsg))
      {
        break;
      }
      fixedOrderColumn++;
    }
    return fixedOrderColumn;
  }

  private LocalizableMessage getValue(List<String> values, LocalizableMessage valueIfEmpty)
  {
    LocalizableMessage msg;
    if (values.isEmpty())
    {
      msg = valueIfEmpty;
    }
    else
    {
      String s = joinAsString("<br>", values);
      if (values.size() > 1)
      {
        msg = LocalizableMessage.raw(
            "<html>"+Utilities.applyFont(s, ColorAndFontConstants.tableFont));
      }
      else
      {
        msg = LocalizableMessage.raw(s);
      }
    }
    return msg;
  }
}
