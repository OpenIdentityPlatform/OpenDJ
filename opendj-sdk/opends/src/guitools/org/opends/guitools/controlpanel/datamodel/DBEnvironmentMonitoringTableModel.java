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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The abstract table model used to display all the network groups.
 */
public class DBEnvironmentMonitoringTableModel extends SortableTableModel
implements Comparator<BackendDescriptor>
{
  private static final long serialVersionUID = 548035716525600536L;
  private Set<BackendDescriptor> data = new HashSet<BackendDescriptor>();
  private ArrayList<String[]> dataArray = new ArrayList<String[]>();
  private ArrayList<BackendDescriptor> dataSourceArray =
    new ArrayList<BackendDescriptor>();

  private String[] columnNames = {};
  private Message NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private Message NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();


  /**
   * The operations to be displayed.
   */
  private LinkedHashSet<String> attributes = new LinkedHashSet<String>();
  /**
   * The sort column of the table.
   */
  private int sortColumn = 0;
  /**
   * Whether the sorting is ascending or descending.
   */
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
   * {@inheritDoc}
   */
  public int getColumnCount()
  {
    return columnNames.length;
  }

  /**
   * {@inheritDoc}
   */
  public int getRowCount()
  {
    return dataArray.size();
  }

  /**
   * {@inheritDoc}
   */
  public Object getValueAt(int row, int col)
  {
    return dataArray.get(row)[col];
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
  public int compare(BackendDescriptor desc1, BackendDescriptor desc2)
  {
    int result;
    ArrayList<Integer> possibleResults = new ArrayList<Integer>();

    possibleResults.add(getName(desc1).compareTo(getName(desc2)));
    for (String attrName : attributes)
    {
      int possibleResult;
      if (desc1.getMonitoringEntry() == null)
      {
        if (desc2.getMonitoringEntry() == null)
        {
          possibleResult = 0;
        }
        else
        {
          possibleResult = -1;
        }
      }
      else if (desc2.getMonitoringEntry() == null)
      {
        possibleResult = 1;
      }
      else
      {
        Object v1 = null;
        Object v2 = null;

        for (String attr : desc1.getMonitoringEntry().getAttributeNames())
        {
          if (attr.equalsIgnoreCase(attrName))
          {
            v1 = getFirstMonitoringValue(desc1.getMonitoringEntry(), attrName);
            break;
          }
        }
        for (String attr : desc2.getMonitoringEntry().getAttributeNames())
        {
          if (attr.equalsIgnoreCase(attrName))
          {
            v2 = getFirstMonitoringValue(desc2.getMonitoringEntry(), attrName);
            break;
          }
        }

        if (v1 == null)
        {
          if (v2 == null)
          {
            possibleResult = 0;
          }
          else
          {
            possibleResult = -1;
          }
        }
        else if (v2 == null)
        {
          possibleResult = 1;
        }
        else
        {
          if (v1 instanceof Number)
          {
            if ((v1 instanceof Double) || (v2 instanceof Double))
            {
              double n1 = ((Number)v1).doubleValue();
              double n2 = ((Number)v2).doubleValue();
              if (n1 > n2)
              {
                possibleResult = 1;
              }
              else if (n1 < n2)
              {
                possibleResult = -1;
              }
              else
              {
                possibleResult = 0;
              }
            }
            else
            {
              long n1 = ((Number)v1).longValue();
              long n2 = ((Number)v2).longValue();
              if (n1 > n2)
              {
                possibleResult = 1;
              }
              else if (n1 < n2)
              {
                possibleResult = -1;
              }
              else
              {
                possibleResult = 0;
              }
            }
          }
          else if (v2 instanceof Number)
          {
            possibleResult = -1;
          }
          else
          {
            possibleResult = v1.toString().compareTo(v2.toString());
          }
        }
      }
      possibleResults.add(possibleResult);
    }

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

  /**
   * Returns the operations displayed by this table model.
   * @return the operations displayed by this table model.
   */
  public Collection<String> getAttributes()
  {
    return attributes;
  }

  /**
   * Sets the operations displayed by this table model.
   * @param operations the operations displayed by this table model.
   */
  public void setAttributes(LinkedHashSet<String> operations)
  {
    this.attributes.clear();
    this.attributes.addAll(operations);
    columnNames = new String[operations.size() + 1];
    columnNames[0] = INFO_CTRL_PANEL_DB_HEADER.get().toString();
    int i = 1;
    for (String operation : operations)
    {
      columnNames[i] = operation;
      i++;
    }
  }

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<BackendDescriptor> sortedSet = new TreeSet<BackendDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    dataSourceArray.clear();
    for (BackendDescriptor ach : sortedSet)
    {
      String[] s = getLine(ach);
      dataArray.add(s);
      dataSourceArray.add(ach);
    }

    // Add the total: always at the end

    String[] line = new String[attributes.size() + 1];
    line[0] = "<html><b>"+INFO_CTRL_PANEL_TOTAL_LABEL.get().toString()+
    "</b>";
    for (int i=1; i<line.length; i++)
    {
      boolean valueSet = false;
      boolean notImplemented = false;
      long totalValue = 0;
      for (int j=0; j<dataArray.size(); j++)
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
   * {@inheritDoc}
   */
  protected String[] getColumnNames()
  {
    return columnNames;
  }

  /**
   * Returns the label to be used for the provided backend.
   * @param backend the backend.
   * @return the label to be used for the provided backend.
   */
  protected String getName(BackendDescriptor backend)
  {
    return backend.getBackendID();
  }

  /**
   * Returns the monitoring entry associated with the provided backend.
   * @param backend the backend.
   * @return the monitoring entry associated with the provided backend.  Returns
   * <CODE>null</CODE> if there is no monitoring entry associated.
   */
  protected CustomSearchResult getMonitoringEntry(
      BackendDescriptor backend)
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
      Object o = getFirstMonitoringValue(monitoringEntry, attr);
      if (o == null)
      {
        line[i] = NO_VALUE_SET.toString();
      }
      else
      {
        line[i] = o.toString();
      }
      i++;
    }
    return line;
  }

  /**
   * Returns the first value for a given attribute in the provided entry.
   * @param sr the entry.
   * @param attrName the attribute name.
   * @return the first value for a given attribute in the provided entry.
   */
  protected Object getFirstMonitoringValue(CustomSearchResult sr,
      String attrName)
  {
    return Utilities.getFirstMonitoringValue(sr, attrName);
  }
}
