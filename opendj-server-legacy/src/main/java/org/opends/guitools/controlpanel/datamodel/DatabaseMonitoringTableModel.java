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
 * Copyright 2009 Sun Microsystems, Inc.
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

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.CollectionUtils.*;

/** The table model used to display all the database monitoring information. */
public class DatabaseMonitoringTableModel extends SortableTableModel implements Comparator<BackendDescriptor>
{
  private static final long serialVersionUID = 548035716525600536L;
  private final Set<BackendDescriptor> data = new HashSet<>();
  private final List<String[]> dataArray = new ArrayList<>();

  private String[] columnNames = {};
  private final LocalizableMessage NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private final LocalizableMessage NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();

  /** The fields to be displayed. */
  private final Set<String> attributes = new LinkedHashSet<>();
  /** The sort column of the table. */
  private int sortColumn;
  /** Whether the sorting is ascending or descending. */
  private boolean sortAscending = true;

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<BackendDescriptor> newData)
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

  @Override
  public int compare(BackendDescriptor desc1, BackendDescriptor desc2)
  {
    CustomSearchResult monitor1 = desc1.getMonitoringEntry();
    CustomSearchResult monitor2 = desc2.getMonitoringEntry();

    ArrayList<Integer> possibleResults = newArrayList(getName(desc1).compareTo(getName(desc2)));
    computeMonitoringPossibleResults(monitor1, monitor2, possibleResults, attributes);

    int result = possibleResults.get(getSortColumn());
    if (result == 0)
    {
      result = getFirstNonZero(possibleResults);
    }
    if (!isSortAscending())
    {
      result = -result;
    }
    return result;
  }

  private int getFirstNonZero(ArrayList<Integer> possibleResults)
  {
    for (int i : possibleResults)
    {
      if (i != 0)
      {
        return i;
      }
    }
    return 0;
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
   * Returns the fields displayed by this table model.
   * @return the fields displayed by this table model.
   */
  public Collection<String> getAttributes()
  {
    return attributes;
  }

  /**
   * Sets the fields displayed by this table model.
   * @param fields the statistic fields displayed by this table model.
   */
  public void setAttributes(Set<String> fields)
  {
    this.attributes.clear();
    this.attributes.addAll(fields);
    columnNames = new String[fields.size() + 1];
    columnNames[0] = INFO_CTRL_PANEL_DB_HEADER.get().toString();
    int i = 1;
    for (String field : fields)
    {
      columnNames[i] = field;
      i++;
    }
  }

  /** Updates the array data. This includes resorting it. */
  private void updateDataArray()
  {
    TreeSet<BackendDescriptor> sortedSet = new TreeSet<>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    for (BackendDescriptor ach : sortedSet)
    {
      String[] s = getLine(ach);
      dataArray.add(s);
    }

    // Add the total: always at the end

    String[] line = new String[attributes.size() + 1];
    line[0] = "<html><b>" + INFO_CTRL_PANEL_TOTAL_LABEL.get() + "</b>";
    for (int i=1; i<line.length; i++)
    {
      boolean valueSet = false;
      boolean notImplemented = false;
      long totalValue = 0;
      for (String[] l : dataArray)
      {
        String value = l[i];
        try
        {
          long v = Long.parseLong(value);
          totalValue += v;
          valueSet = true;
        }
        catch (Throwable t)
        {
          try
          {
            double v = Double.parseDouble(value);
            totalValue += v;
            valueSet = true;
          }
          catch (Throwable t2)
          {
            notImplemented = NOT_IMPLEMENTED.toString().equals(value);
          }
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
   * Returns the label to be used for the provided backend.
   * @param backend the backend.
   * @return the label to be used for the provided backend.
   */
  private String getName(BackendDescriptor backend)
  {
    return backend.getBackendID();
  }

  /**
   * Returns the monitoring entry associated with the provided backend.
   * @param backend the backend.
   * @return the monitoring entry associated with the provided backend.  Returns
   * <CODE>null</CODE> if there is no monitoring entry associated.
   */
  private CustomSearchResult getMonitoringEntry(BackendDescriptor backend)
  {
    return backend.getMonitoringEntry();
  }

  private String[] getLine(BackendDescriptor backend)
  {
    String[] line = new String[attributes.size() + 1];
    line[0] = getName(backend);
    int i = 1;
    CustomSearchResult monitoringEntry = getMonitoringEntry(backend);
    for (String attr : attributes)
    {
      String o = getFirstValueAsString(monitoringEntry, attr);
      if (o != null)
      {
        line[i] = o;
      }
      else
      {
        line[i] = NO_VALUE_SET.toString();
      }
      i++;
    }
    return line;
  }
}
