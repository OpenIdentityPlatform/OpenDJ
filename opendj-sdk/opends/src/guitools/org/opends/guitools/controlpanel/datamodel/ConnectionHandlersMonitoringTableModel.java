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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor.
Protocol;
import org.opends.messages.Message;

/**
 * The table model used to display the monitoring information of connection
 * handlers.
 *
 */
public class ConnectionHandlersMonitoringTableModel extends
MonitoringTableModel<ConnectionHandlerDescriptor,
AddressConnectionHandlerDescriptor>
{
  private static final long serialVersionUID = -8891998773191495L;

  /**
   * {@inheritDoc}
   */
  protected Set<AddressConnectionHandlerDescriptor> convertToInternalData(
      Set<ConnectionHandlerDescriptor> newData)
  {
    Set<AddressConnectionHandlerDescriptor> newAddresses =
      new HashSet<AddressConnectionHandlerDescriptor>();
    for (ConnectionHandlerDescriptor ch : newData)
    {
      if (ch.getAddresses().isEmpty())
      {
        newAddresses.add(new AddressConnectionHandlerDescriptor(ch, null,
            getMonitoringEntry(null, ch)));
      }
      else
      {
        for (InetAddress address : ch.getAddresses())
        {
          newAddresses.add(new AddressConnectionHandlerDescriptor(ch, address,
              getMonitoringEntry(address, ch)));
        }
      }
    }
    return newAddresses;
  }



  /**
   * {@inheritDoc}
   */
  public int compare(AddressConnectionHandlerDescriptor desc1,
      AddressConnectionHandlerDescriptor desc2)
  {
    int result;
    ArrayList<Integer> possibleResults = new ArrayList<Integer>();

    possibleResults.add(compareNames(desc1, desc2));
    possibleResults.addAll(getMonitoringPossibleResults(
        desc1.getMonitoringEntry(), desc2.getMonitoringEntry()));

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

  private int compareNames(AddressConnectionHandlerDescriptor ach1,
      AddressConnectionHandlerDescriptor ach2)
  {
    int compare = 0;
    boolean addressEqual = false;
    if (ach1.getAddress() == null)
    {
      if (ach2.getAddress() == null)
      {
        addressEqual = true;
      }
    }
    else if (ach2.getAddress() != null)
    {
      addressEqual = ach1.getAddress().equals(ach2.getAddress());
    }
    if (addressEqual)
    {
      Integer port1 = new Integer(ach1.getConnectionHandler().getPort());
      Integer port2 = new Integer(ach2.getConnectionHandler().getPort());
      compare = port1.compareTo(port2);
    }
    else
    {
      compare = getName(ach1).compareTo(getName(ach2));
    }
    return compare;
  }

  /**
   * {@inheritDoc}
   */
  protected CustomSearchResult getMonitoringEntry(
      AddressConnectionHandlerDescriptor ach)
  {
    return ach.getMonitoringEntry();
  }

  /**
   * {@inheritDoc}
   */
  protected String getName(AddressConnectionHandlerDescriptor ach)
  {
    StringBuilder sb = new StringBuilder();
    ConnectionHandlerDescriptor ch = ach.getConnectionHandler();
    if (ch.getProtocol() == Protocol.ADMINISTRATION_CONNECTOR)
    {
      sb.append(INFO_CTRL_PANEL_ADMINISTRATION_CONNECTOR_NAME.get(
          ch.getPort()));
    }
    else
    {
      if (ach.getAddress() != null)
      {
        sb.append(ach.getAddress().getHostName()+":"+ch.getPort());
      }
      else
      {
        sb.append(ch.getPort());
      }
      sb.append(" - ");
      switch (ch.getProtocol())
      {
      case OTHER:
        sb.append(ch.getName());
        break;
      default:
        sb.append(ch.getProtocol().getDisplayMessage().toString());
      break;
      }
    }
    return sb.toString();
  }

  private CustomSearchResult getMonitoringEntry(InetAddress address,
      ConnectionHandlerDescriptor cch)
  {
    CustomSearchResult monitoringEntry = null;
    for (CustomSearchResult sr : cch.getMonitoringEntries())
    {
      String cn = (String)getFirstMonitoringValue(sr, "cn");
      if (cn != null)
      {
        if (address == null)
        {
          monitoringEntry = sr;
          break;
        }
        else
        {
          if (cn.endsWith(
              " "+address.getHostAddress()+" port "+cch.getPort()+
              " Statistics") ||
              cn.endsWith(
                  " "+address.getHostName()+" port "+cch.getPort()+
                  " Statistics"))
          {
            monitoringEntry = sr;
            break;
          }
        }
      }
    }
    return monitoringEntry;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getNameHeader()
  {
    return INFO_CTRL_PANEL_CONNECTION_HANDLER_HEADER.get();
  }
}

/**
 * The table model has one line per address, this object represents that
 * address and all the associated monitoring information.
 *
 */
class AddressConnectionHandlerDescriptor
{
  private ConnectionHandlerDescriptor ch;
  private InetAddress address;
  private CustomSearchResult monitoringEntry;
  private int hashCode;

  /**
   * Constructor of this data structure.
   * @param ch the connection handler descriptor.
   * @param address the address.
   * @param monitoringEntry the monitoringEntry.
   */
  public AddressConnectionHandlerDescriptor(
      ConnectionHandlerDescriptor ch,
      InetAddress address,
      CustomSearchResult monitoringEntry)
  {
    this.ch = ch;
    this.address = address;
    this.monitoringEntry = monitoringEntry;

    if (address != null)
    {
      hashCode = ch.hashCode() + address.hashCode();
    }
    else
    {
      hashCode = ch.hashCode();
    }
  }

  /**
   * Returns the address.
   * @return the address.
   */
  public InetAddress getAddress()
  {
    return address;
  }

  /**
   * Returns the connection handler descriptor.
   * @return the connection handler descriptor.
   */
  public ConnectionHandlerDescriptor getConnectionHandler()
  {
    return ch;
  }

  /**
   * Returns the monitoring entry.
   * @return the monitoring entry.
   */
  public CustomSearchResult getMonitoringEntry()
  {
    return monitoringEntry;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (o != this)
    {
      if (o instanceof AddressConnectionHandlerDescriptor)
      {
        AddressConnectionHandlerDescriptor ach =
          (AddressConnectionHandlerDescriptor)o;
        if (ach.getAddress() == null)
        {
          equals = getAddress() == null;
        }
        else if (getAddress() == null)
        {
          equals = false;
        }
        else
        {
          equals = ach.getAddress().equals(getAddress());
        }
        if (equals)
        {
          equals = ach.getConnectionHandler().equals(getConnectionHandler());
        }
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }
}