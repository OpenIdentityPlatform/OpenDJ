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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.util.Utilities;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

/**
 * The abstract table model used to display all the network groups.
 * @param <T> the type of the objects passed externally to the table model.
 * @param <P> the type of the objects used internally by the table model.
 */
abstract class MonitoringTableModel<T, P> extends SortableTableModel implements Comparator<P>
{
  private static final long serialVersionUID = -3974562860632179025L;
  private final Set<P> data = new HashSet<>();
  private final ArrayList<String[]> dataArray = new ArrayList<>();
  private final ArrayList<P> dataSourceArray = new ArrayList<>();
  private boolean showAverages;
  private long runningTime;

  private String[] columnNames = {};
  private final LocalizableMessage NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private final LocalizableMessage NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();

  /** The attributes to be displayed. */
  private final LinkedHashSet<MonitoringAttributes> attributes = new LinkedHashSet<>();
  /** The sort column of the table. */
  private int sortColumn;
  /** Whether the sorting is ascending or descending. */
  private boolean sortAscending = true;

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   * @param runningTime the running time of the server in miliseconds.
   */
  public void setData(Set<T> newData, long runningTime)
  {
    this.runningTime = runningTime;
    Set<P> newInternalData = convertToInternalData(newData);

    // When we show the averages, the data displayed changes (even if the
    // monitoring data has not).
    if (!newInternalData.equals(data) || showAverages)
    {
      data.clear();
      data.addAll(newInternalData);
      updateDataArray();
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  @Override
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

  @Override
  public int getColumnCount()
  {
    return columnNames.length;
  }

  @Override
  public int getRowCount()
  {
    return dataArray.size();
  }

  @Override
  public Object getValueAt(int row, int col)
  {
    return dataArray.get(row)[col];
  }

  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  @Override
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  @Override
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  @Override
  public int getSortColumn()
  {
    return sortColumn;
  }

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  @Override
  public void setSortColumn(int sortColumn)
  {
    this.sortColumn = sortColumn;
  }

  /**
   * Returns the attributes displayed by this table model.
   * @return the attributes displayed by this table model.
   */
  public Collection<MonitoringAttributes> getAttributes()
  {
    return attributes;
  }

  /**
   * Returns the header to be used for the name of the object.
   * @return the header to be used for the name of the object.
   */
  protected abstract LocalizableMessage getNameHeader();

  /**
   * Sets the operations displayed by this table model.
   * @param attributes the attributes displayed by this table model.
   * @param showAverages whether the averages (when makes sense) should be
   * displayed or not.
   */
  public void setAttributes(LinkedHashSet<MonitoringAttributes> attributes,
      boolean showAverages)
  {
    this.showAverages = showAverages;
    this.attributes.clear();
    this.attributes.addAll(attributes);
    int columnCount = attributes.size() + 1;
    if (showAverages)
    {
      for (MonitoringAttributes attr : attributes)
      {
        if (attr.canHaveAverage())
        {
          columnCount ++;
        }
      }
    }
    columnNames = new String[columnCount];
    columnNames[0] = getHeader(getNameHeader());
    int i = 1;
    for (MonitoringAttributes attribute : attributes)
    {
      columnNames[i] = getHeader(attribute.getMessage(), 15);
      if (showAverages && attribute.canHaveAverage())
      {
        i++;
        columnNames[i] = getAverageHeader(attribute);
      }
      i++;
    }
  }

  /** Updates the array data. This includes resorting it. */
  private void updateDataArray()
  {
    TreeSet<P> sortedSet = new TreeSet<>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    dataSourceArray.clear();
    for (P ach : sortedSet)
    {
      String[] s = getLine(ach);
      dataArray.add(s);
      dataSourceArray.add(ach);
    }

    // Add the total: always at the end
    String[] line = new String[columnNames.length];
    line[0] = "<html><b>" + INFO_CTRL_PANEL_TOTAL_LABEL.get() + "</b>";
    for (int i = 1; i < line.length; i++)
    {
      boolean valueSet = false;
      boolean notImplemented = false;
      long totalValue = 0;
      for (int j = 0; j < dataArray.size(); j++)
      {
        String[] l = dataArray.get(j);
        String value = l[i];
        try
        {
          long v = Long.parseLong(value);
          totalValue += v;
          valueSet = true;
        }
        catch (Throwable t)
        {
          notImplemented = NOT_IMPLEMENTED.toString().equals(value);
        }
      }
      if (notImplemented)
      {
        line[i] = NOT_IMPLEMENTED.toString();
      }
      else if (valueSet)
      {
        line[i] = String.valueOf(totalValue);
      }
      else
      {
        line[i] = NO_VALUE_SET.toString();
      }
    }
    dataArray.add(line);
  }

  /**
   * Converts the provided data into internal data to be used by the table
   * model.
   * @param o the set of objects to convert.
   * @return the set of objects to be used by the table model.
   */
  protected abstract Set<P> convertToInternalData(Set<T> o);

  /**
   * Returns the label to be used for the provided internal object.
   * @param o the internal object.
   * @return the label to be used for the provided internal object.
   */
  protected abstract String getName(P o);

  /**
   * Returns the monitoring entry associated with the provided object.
   * @param o the internal object.
   * @return the monitoring entry associated with the provided object.  Returns
   * <CODE>null</CODE> if there is no monitoring entry associated.
   */
  protected abstract CustomSearchResult getMonitoringEntry(P o);

  private String[] getLine(P o)
  {
    String[] line = new String[columnNames.length];
    line[0] = getName(o);
    int i = 1;
    CustomSearchResult monitoringEntry = getMonitoringEntry(o);
    for (MonitoringAttributes attribute : attributes)
    {
      line[i] = Utilities.getMonitoringValue(
          attribute, monitoringEntry);
      if (showAverages && attribute.canHaveAverage())
      {
        i++;
        try
        {
          if (runningTime > 0)
          {
            long v = Long.parseLong(line[i - 1]);
            long average = (1000 * v) / runningTime;
            String s = String.valueOf(average);
            int index = s.indexOf(".");
            // Show a maximum of two decimals.
            if (index != -1 && index + 3 < s.length())
            {
              s = s.substring(0, index + 3);
            }
            line[i] = s;
          }
          else
          {
            line[i] = NO_VALUE_SET.toString();
          }
        }
        catch (Throwable t)
        {
          line[i] = NO_VALUE_SET.toString();
        }
      }
      i++;
    }
    return line;
  }

  /**
   * Returns the String to be used as header on the table to display the average
   * for a given monitoring attribute.
   * @param attr the monitoring attribute.
   * @return the String to be used as header on the table to display the average
   * for a given monitoring attribute.
   */
  private String getAverageHeader(MonitoringAttributes attr)
  {
    return getHeader(INFO_CTRL_PANEL_AVERAGE_HEADER.get(attr.getMessage()), 15);
  }

  /**
   * Returns a list of integer with all the values of two monitoring entries
   * compared.
   * @param monitor1 the first monitoring entry.
   * @param monitor2 the second monitoring entry.
   * @return a list of integer with all the values of two monitoring entries
   * compared.
   */
  protected ArrayList<Integer> getMonitoringPossibleResults(
      CustomSearchResult monitor1, CustomSearchResult monitor2)
  {
    final List<String> attrs = new ArrayList<>();
    for (MonitoringAttributes operation : getAttributes())
    {
      attrs.add(operation.getAttributeName());
    }
    final ArrayList<Integer> possibleResults = new ArrayList<>();
    computeMonitoringPossibleResults(monitor1, monitor2, possibleResults, attrs);
    return possibleResults;
  }
}
