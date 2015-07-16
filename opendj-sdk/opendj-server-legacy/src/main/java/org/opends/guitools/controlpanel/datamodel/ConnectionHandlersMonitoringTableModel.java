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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor.Protocol;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

/**
 * The table model used to display the monitoring information of connection
 * handlers.
 */
public class ConnectionHandlersMonitoringTableModel extends
MonitoringTableModel<ConnectionHandlerDescriptor,
AddressConnectionHandlerDescriptor>
{
  private static final long serialVersionUID = -8891998773191495L;

  /** {@inheritDoc} */
  @Override
  protected Set<AddressConnectionHandlerDescriptor> convertToInternalData(
      Set<ConnectionHandlerDescriptor> newData)
  {
    Set<AddressConnectionHandlerDescriptor> newAddresses = new HashSet<>();
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

  /** {@inheritDoc} */
  @Override
  public int compare(AddressConnectionHandlerDescriptor desc1,
      AddressConnectionHandlerDescriptor desc2)
  {
    ArrayList<Integer> possibleResults = new ArrayList<>();

    possibleResults.add(compareNames(desc1, desc2));
    possibleResults.addAll(getMonitoringPossibleResults(
        desc1.getMonitoringEntry(), desc2.getMonitoringEntry()));

    int result = possibleResults.get(getSortColumn());
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
    if (Objects.equals(ach1.getAddress(), ach2.getAddress()))
    {
      Integer port1 = Integer.valueOf(ach1.getConnectionHandler().getPort());
      Integer port2 = Integer.valueOf(ach2.getConnectionHandler().getPort());
      return port1.compareTo(port2);
    }
    return getName(ach1).compareTo(getName(ach2));
  }

  /** {@inheritDoc} */
  @Override
  protected CustomSearchResult getMonitoringEntry(
      AddressConnectionHandlerDescriptor ach)
  {
    return ach.getMonitoringEntry();
  }

  /** {@inheritDoc} */
  @Override
  protected String getName(AddressConnectionHandlerDescriptor ach)
  {
    StringBuilder sb = new StringBuilder();
    ConnectionHandlerDescriptor ch = ach.getConnectionHandler();
    if (ch.getProtocol() == Protocol.ADMINISTRATION_CONNECTOR)
    {
      sb.append(INFO_CTRL_PANEL_ADMINISTRATION_CONNECTOR_NAME.get(ch.getPort()));
    }
    else
    {
      if (ach.getAddress() != null)
      {
        sb.append(ach.getAddress().getHostAddress()).append(":").append(ch.getPort());
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
        sb.append(ch.getProtocol().getDisplayMessage());
      break;
      }
    }
    return sb.toString();
  }

  private CustomSearchResult getMonitoringEntry(InetAddress address,
      ConnectionHandlerDescriptor cch)
  {
    for (CustomSearchResult sr : cch.getMonitoringEntries())
    {
      String cn = getFirstValueAsString(sr, "cn");
      if (cn != null)
      {
        if (address == null)
        {
          return sr;
        }
        if (cn.endsWith(" " + address.getHostAddress() + " port " + cch.getPort() + " Statistics"))
        {
          return sr;
        }
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected LocalizableMessage getNameHeader()
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

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return hashCode;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o)
  {
    if (o != this)
    {
      return true;
    }
    if (!(o instanceof AddressConnectionHandlerDescriptor))
    {
      return false;
    }
    AddressConnectionHandlerDescriptor ach = (AddressConnectionHandlerDescriptor) o;
    return Objects.equals(getAddress(), ach.getAddress())
        && ach.getConnectionHandler().equals(getConnectionHandler());
  }
}