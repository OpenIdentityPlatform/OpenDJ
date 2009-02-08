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
 * @param <T> the type of the objects passed externally to the table model.
 * @param <P> the type of the objects used internally by the table model.
 */
public abstract class MonitoringTableModel<T, P> extends SortableTableModel
implements Comparator<P>
{
  private Set<P> data = new HashSet<P>();
  private ArrayList<String[]> dataArray = new ArrayList<String[]>();
  private ArrayList<P> dataSourceArray = new ArrayList<P>();
  private boolean showAverages;
  private long runningTime;

  private String[] columnNames = {};
  private Message NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private Message NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();


  /**
   * The attributes to be displayed.
   */
  private LinkedHashSet<MonitoringAttributes> attributes =
    new LinkedHashSet<MonitoringAttributes>();
  /**
   * The sort column of the table.
   */
  private int sortColumn = 0;
  /**
   * Whether the sorting is ascending or descending.
   */
  private boolean sortAscending = true;

  /**
   * Indicates whether a total row must be added or not.  The default behavior
   * is to add it.
   * @return <CODE>true</CODE> if a total row must be added and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean addTotalRow()
  {
    return true;
  }

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
  protected abstract Message getNameHeader();

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

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<P> sortedSet = new TreeSet<P>(this);
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
    if (addTotalRow())
    {
      String[] line = new String[columnNames.length];
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
  }

  /**
   * {@inheritDoc}
   */
  protected String[] getColumnNames()
  {
    return columnNames;
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
            if ((index != -1) && ((index + 3) < s.length()))
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
    return getHeader(INFO_CTRL_PANEL_AVERAGE_HEADER.get(
        attr.getMessage().toString()), 15);
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
    ArrayList<Integer> possibleResults = new ArrayList<Integer>();
    for (MonitoringAttributes operation : getAttributes())
    {
      int possibleResult;
      if (monitor1 == null)
      {
        if (monitor2 == null)
        {
          possibleResult = 0;
        }
        else
        {
          possibleResult = -1;
        }
      }
      else if (monitor2 == null)
      {
        possibleResult = 1;
      }
      else
      {
        Object v1 = null;
        Object v2 = null;

        for (String attrName : monitor1.getAttributeNames())
        {
          if (operation.getAttributeName().equalsIgnoreCase(attrName))
          {
            v1 = getFirstMonitoringValue(monitor1, attrName);
            break;
          }
        }
        for (String attrName : monitor2.getAttributeNames())
        {
          if (operation.getAttributeName().equalsIgnoreCase(attrName))
          {
            v2 = getFirstMonitoringValue(monitor2, attrName);
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
            if (v2 instanceof Number)
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
            else
            {
              possibleResult = 1;
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
    return possibleResults;
  }
}
