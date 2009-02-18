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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The table model used by the table that displays the connection handlers.
 *
 */
public class ConnectionHandlerTableModel extends SortableTableModel
implements Comparator<ConnectionHandlerDescriptor>
{
  private static final long serialVersionUID = -1121308303480078376L;
  private Set<ConnectionHandlerDescriptor> data =
    new HashSet<ConnectionHandlerDescriptor>();
  private ArrayList<String[]> dataArray =
    new ArrayList<String[]>();
  private String[] COLUMN_NAMES;
  private int sortColumn = 0;
  private boolean sortAscending = true;

  /**
   * Constructor for this table model.
   */
  public ConnectionHandlerTableModel()
  {
    this(true);
  }

  /**
   * Constructor for this table model.
   * @param wrapHeader whether to wrap the headers or not.
   * monitoring information or not.
   */
  public ConnectionHandlerTableModel(boolean wrapHeader)
  {
    if (wrapHeader)
    {
      COLUMN_NAMES = new String[] {
          getHeader(INFO_ADDRESS_PORT_COLUMN.get()),
          getHeader(INFO_PROTOCOL_COLUMN.get()),
          getHeader(INFO_STATE_COLUMN.get())
      };
    }
    else
    {
      COLUMN_NAMES = new String[] {
          INFO_ADDRESS_PORT_COLUMN.get().toString(),
          INFO_PROTOCOL_COLUMN.get().toString(),
          INFO_STATE_COLUMN.get().toString()
      };
    }
  }

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<ConnectionHandlerDescriptor> newData)
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
   * Comparable implementation.
   * @param desc1 the first listener descriptor to compare.
   * @param desc2 the second listener descriptor to compare.
   * @return 1 if according to the sorting options set by the user the first
   * listener descriptor must be put before the second descriptor, 0 if they
   * are equivalent in terms of sorting and -1 if the second descriptor must
   * be put before the first descriptor.
   */
  public int compare(ConnectionHandlerDescriptor desc1,
      ConnectionHandlerDescriptor desc2)
  {
    int result = 0;
    if (sortColumn == 0)
    {
      if (desc1.getAddresses().equals(desc2.getAddresses()))
      {
        Integer port1 = new Integer(desc1.getPort());
        Integer port2 = new Integer(desc2.getPort());
        result = port1.compareTo(port2);
      }
      else
      {
        result = getAddressPortString(desc1).compareTo(
            getAddressPortString(desc2));
      }
      if (result == 0)
      {
        result = getProtocolString(desc1).compareTo(
            getProtocolString(desc2));
      }

      if (result == 0)
      {
        result = desc1.getState().compareTo(desc2.getState());
      }
    }
    else if (sortColumn == 1)
    {
      result = getProtocolString(desc1).compareTo(
          getProtocolString(desc2));

      if (result == 0)
      {
        result = getAddressPortString(desc1).compareTo(
            getAddressPortString(desc2));
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
        result = getAddressPortString(desc1).compareTo(
            getAddressPortString(desc2));
      }

      if (result == 0)
      {
        result = getProtocolString(desc1).compareTo(
            getProtocolString(desc2));
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
    return dataArray.get(row)[col];
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

  private String getAddressPortString(ConnectionHandlerDescriptor desc)
  {
    Set<InetAddress> addresses = desc.getAddresses();
    String returnValue;
    if (addresses.size() == 0)
    {
      if (desc.getPort() > 0)
      {
        returnValue = String.valueOf(desc.getPort());
      }
      else
      {
        returnValue = INFO_NOT_APPLICABLE_LABEL.get().toString();
      }
    }
    else
    {
      StringBuilder buf = new StringBuilder();
      buf.append("<html>");
      boolean added = false;
      for (InetAddress address : addresses)
      {
        if (added)
        {
          buf.append("<br>");
        }
        buf.append(address.getCanonicalHostName());
        added = true;
        if (desc.getPort() > 0)
        {
          buf.append(":"+desc.getPort());
        }
      }
      returnValue = buf.toString();
    }
    return returnValue;
  }

  private String getProtocolString(ConnectionHandlerDescriptor desc)
  {
    String returnValue;
    switch (desc.getProtocol())
    {
    case OTHER:
      returnValue = desc.getName();
      break;
    default:
      returnValue = desc.getProtocol().getDisplayMessage().toString();
      break;
    }
    return returnValue;
  }

  private void updateDataArray()
  {
    TreeSet<ConnectionHandlerDescriptor> sortedSet =
      new TreeSet<ConnectionHandlerDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    for (ConnectionHandlerDescriptor desc : sortedSet)
    {
      String[] s = new String[3];
      s[0] = getAddressPortString(desc);
      s[1] = getProtocolString(desc);

      switch (desc.getState())
      {
      case ENABLED:
        s[2] = INFO_ENABLED_LABEL.get().toString();
        break;

      case DISABLED:
        s[2] = INFO_DISABLED_LABEL.get().toString();
        break;

      case UNKNOWN:
        s[2] = INFO_UNKNOWN_LABEL.get().toString();
        break;

      default:
        throw new IllegalStateException("Unknown state: "+desc.getState());
      }
      dataArray.add(s);
    }
  }
}
