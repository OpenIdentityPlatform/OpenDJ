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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

import org.opends.statuspanel.ListenerDescriptor;
import org.opends.statuspanel.i18n.ResourceProvider;

/**
 * This class is just a table model used to display the information about
 * listeners in a table.
 *
 */
public class ListenersTableModel extends AbstractTableModel
implements SortableTableModel, Comparator<ListenerDescriptor>
{
  private static final long serialVersionUID = -1121308303480078376L;
  private HashSet<ListenerDescriptor> data = new HashSet<ListenerDescriptor>();
  private ArrayList<ListenerDescriptor> dataArray =
    new ArrayList<ListenerDescriptor>();
  private final String[] COLUMN_NAMES = {
    getMsg("address-port-column"),
    getMsg("protocol-column"),
    getMsg("state-column")
  };
  private int sortColumn = 0;
  private boolean sortAscending = true;

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<ListenerDescriptor> newData)
  {
    if (!newData.equals(data))
    {
      data.clear();
      data.addAll(newData);
      dataArray.clear();
      TreeSet<ListenerDescriptor> sortedSet =
        new TreeSet<ListenerDescriptor>(this);
      sortedSet.addAll(data);
      dataArray.addAll(sortedSet);
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  public void forceResort()
  {
    dataArray.clear();
    TreeSet<ListenerDescriptor> sortedSet =
      new TreeSet<ListenerDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.addAll(sortedSet);
    fireTableDataChanged();
  }

  /**
   * Comparable implementation.
   * @param desc1 the first listener descriptor to compare.
   * @param desc2 the second listener descriptor to compare.
   * @return 1 if according to the sorting options set by the user the first
   * listener descriptor must be put before the second descriptor, 0 if they
   * are equivalent in terms of sorting and -1 if the second descriptor must
   * be put before the first descriptor.
   */
  public int compare(ListenerDescriptor desc1, ListenerDescriptor desc2)
  {
    int result = 0;
    if (sortColumn == 0)
    {
      result = desc1.getAddressPort().compareTo(desc2.getAddressPort());

      if (result == 0)
      {
        result = desc1.getProtocolDescription().compareTo(
            desc2.getProtocolDescription());
      }

      if (result == 0)
      {
        result = desc1.getState().compareTo(desc2.getState());
      }
    }
    else if (sortColumn == 1)
    {
      result = desc1.getProtocolDescription().compareTo(
          desc2.getProtocolDescription());

      if (result == 0)
      {
        result = desc1.getAddressPort().compareTo(desc2.getAddressPort());
      }

      if (result == 0)
      {
        result = desc1.getState().compareTo(desc2.getState());
      }
    }
    else
    {
      result = desc1.getState().compareTo(desc2.getState());

      if (result == 0)
      {
        result = desc1.getAddressPort().compareTo(desc2.getAddressPort());
      }

      if (result == 0)
      {
        result = desc1.getProtocolDescription().compareTo(
            desc2.getProtocolDescription());
      }
    }

    if (!sortAscending)
    {
      result = -result;
    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public int getColumnCount()
  {
    return 3;
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
    Object v;
    ListenerDescriptor desc = dataArray.get(row);
    if (col == 0)
    {
      v = desc.getAddressPort();
    }
    else if (col == 1)
    {
      v = desc.getProtocolDescription().toString();
    }
    else
    {
      switch (desc.getState())
      {
      case ENABLED:
        v = getMsg("enabled-label");
        break;

      case DISABLED:
        v = getMsg("disabled-label");
        break;

      case UNKNOWN:
        v = getMsg("unknown-label");
        break;

        default:
          throw new IllegalStateException("Unknown state: "+desc.getState());
      }
    }
    return v;
  }

  /**
   * {@inheritDoc}
   */
  public String getColumnName(int col) {
    return COLUMN_NAMES[col];
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
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
