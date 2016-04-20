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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor.State;

/** The table model used by the table that displays the connection handlers. */
public class ConnectionHandlerTableModel extends SortableTableModel
implements Comparator<ConnectionHandlerDescriptor>
{
  private static final long serialVersionUID = -1121308303480078376L;
  private Set<ConnectionHandlerDescriptor> data = new HashSet<>();
  private ArrayList<String[]> dataArray = new ArrayList<>();
  private String[] COLUMN_NAMES;
  private int sortColumn;
  private boolean sortAscending = true;

  /** Constructor for this table model. */
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
  @Override
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
  @Override
  public int compare(ConnectionHandlerDescriptor desc1,
      ConnectionHandlerDescriptor desc2)
  {
    int result = 0;
    if (sortColumn == 0)
    {
      if (desc1.getAddresses().equals(desc2.getAddresses()))
      {
        Integer port1 = Integer.valueOf(desc1.getPort());
        Integer port2 = Integer.valueOf(desc2.getPort());
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

  @Override
  public int getColumnCount()
  {
    return 3;
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
    return COLUMN_NAMES[col];
  }

  @Override
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  @Override
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  @Override
  public int getSortColumn()
  {
    return sortColumn;
  }

  @Override
  public void setSortColumn(int sortColumn)
  {
    this.sortColumn = sortColumn;
  }

  private String getAddressPortString(ConnectionHandlerDescriptor desc)
  {
    Set<InetAddress> addresses = desc.getAddresses();
    if (!addresses.isEmpty())
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
        buf.append(address.getHostAddress());
        added = true;
        if (desc.getPort() > 0)
        {
          buf.append(":").append(desc.getPort());
        }
      }
      return buf.toString();
    }

    if (desc.getPort() > 0)
    {
      return String.valueOf(desc.getPort());
    }
    return INFO_NOT_APPLICABLE_LABEL.get().toString();
  }

  private String getProtocolString(ConnectionHandlerDescriptor desc)
  {
    switch (desc.getProtocol())
    {
    case OTHER:
      return desc.getName();
    default:
      return desc.getProtocol().getDisplayMessage().toString();
    }
  }

  private void updateDataArray()
  {
    TreeSet<ConnectionHandlerDescriptor> sortedSet = new TreeSet<>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    for (ConnectionHandlerDescriptor desc : sortedSet)
    {
      dataArray.add(new String[] {
        getAddressPortString(desc),
        getProtocolString(desc),
        toLabel(desc.getState())
      });
    }
  }

  private String toLabel(State state)
  {
    switch (state)
    {
    case ENABLED:
      return INFO_ENABLED_LABEL.get().toString();
    case DISABLED:
      return INFO_DISABLED_LABEL.get().toString();
    case UNKNOWN:
      return INFO_UNKNOWN_LABEL.get().toString();
    default:
      throw new RuntimeException("Unknown state: " + state);
    }
  }
}
