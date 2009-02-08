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
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.server.admin.std.meta.AdministrationConnectorCfgDefn;

/**
 * This class is used to represent a Listener and is aimed to be used by the
 * classes in the ListenersTableModel class.
 */
public class ConnectionHandlerDescriptor
{
  private Set<CustomSearchResult> monitoringEntries = Collections.emptySet();

  /**
   * Enumeration used to represent the state of the listener.
   */
  public enum State
  {
    /**
     * The listener is enabled.
     */
    ENABLED,
    /**
     * The listener is disabled.
     */
    DISABLED,
    /**
     * The state of the listener is unknown.
     */
    UNKNOWN
  };

  /**
   * Enumeration used to represent the Protocol of the listener.
   *
   */
  public enum Protocol
  {
    /**
     * LDAP protocol.
     */
    LDAP(INFO_CTRL_PANEL_CONN_HANDLER_LDAP.get()),
    /**
     * LDAP accepting Start TLS protocol.
     */
    LDAP_STARTTLS(INFO_CTRL_PANEL_CONN_HANDLER_LDAP_STARTTLS.get()),
    /**
     * LDAP secure protocol.
     */
    LDAPS(INFO_CTRL_PANEL_CONN_HANDLER_LDAPS.get()),
    /**
     * JMX protocol.
     */
    JMX(INFO_CTRL_PANEL_CONN_HANDLER_JMX.get()),
    /**
     * JMX secure protocol.
     */
    JMXS(INFO_CTRL_PANEL_CONN_HANDLER_JMXS.get()),
    /**
     * LDIF protocol.
     */
    LDIF(INFO_CTRL_PANEL_CONN_HANDLER_LDIF.get()),
    /**
     * SNMP protocol.
     */
    SNMP(INFO_CTRL_PANEL_CONN_HANDLER_SNMP.get()),
    /**
     * Replication protocol.  Even if in the configuration is not considered
     * as a listener, we display it on the table.
     */
    REPLICATION(INFO_CTRL_PANEL_CONN_HANDLER_REPLICATION.get()),
    /**
     * Secure replication protocol.
     */
    REPLICATION_SECURE(INFO_CTRL_PANEL_CONN_HANDLER_REPLICATION_SECURE.get()),
    /**
     * Admin connector protocol.
     */
    ADMINISTRATION_CONNECTOR(INFO_CTRL_PANEL_CONN_HANDLER_ADMINISTRATION.get()),
    /**
     * Other protocol.
     */
    OTHER(INFO_CTRL_PANEL_CONN_HANDLER_OTHER.get());

    private Message displayMessage;

    private Protocol(Message displayMessage)
    {
      this.displayMessage = displayMessage;
    }

    /**
     * Returns the display Message to be used for the protocol.
     * @return the display Message to be used for the protocol.
     */
    public Message getDisplayMessage()
    {
      return displayMessage;
    }
  }

  private State state;
  private SortedSet<InetAddress> addresses = new TreeSet<InetAddress>(
      AdministrationConnectorCfgDefn.getInstance().
      getListenAddressPropertyDefinition());
  private int port;
  private Protocol protocol;
  private String toString;
  private String name;

  private int hashCode;

  /**
   * Constructor for the connection handler..
   * @param addresses the list of InetAdresses of the listener.
   * @param port the port of the connection handler.
   * @param protocol the protocol of the listener.
   * @param state the state of the connection handler (enabled, disabled, etc.).
   * @param name the name of the listener.
   * @param monitoringEntries the LDAP entries containing the monitoring
   * information.
   */
  public ConnectionHandlerDescriptor(Collection<InetAddress> addresses,
      int port, Protocol protocol, State state, String name,
      Set<CustomSearchResult> monitoringEntries)
  {
    this.addresses.addAll(addresses);
    this.port = port;
    this.protocol = protocol;
    this.state = state;
    this.name = name;
    this.monitoringEntries = Collections.unmodifiableSet(monitoringEntries);

    StringBuilder builder = new StringBuilder();
    builder.append(getProtocol() + " " + getState() + " ");
    for (InetAddress address : addresses)
    {
      builder.append(address.toString());
    }
    builder.append(" Port: "+port);
    toString = builder.toString();
    hashCode = toString.hashCode();
  }

  /**
   * Returns the address port representation of the listener.
   * @return the address port representation of the listener.
   */
  public SortedSet<InetAddress> getAddresses()
  {
    return addresses;
  }

  /**
   * Returns the protocol of the listener.
   * @return the protocol of the listener.
   */
  public Protocol getProtocol()
  {
    return protocol;
  }

  /**
   * Returns the state of the listener.
   * @return the state of the listener.
   */
  public State getState()
  {
    return state;
  }

  /**
   * Returns the monitoring entries.
   * @return the monitoring entries.
   */
  public Set<CustomSearchResult> getMonitoringEntries()
  {
    return monitoringEntries;
  }

  /**
   * Sets the monitoring entries.
   * @param monitoringEntries the monitoring entries.
   */
  public void setMonitoringEntries(Set<CustomSearchResult> monitoringEntries)
  {
    this.monitoringEntries = Collections.unmodifiableSet(monitoringEntries);
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
  public String toString()
  {
    return toString;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (o == this)
    {
      equals = true;
    }
    else if (o instanceof ConnectionHandlerDescriptor)
    {
      equals = toString.equals(o.toString());
      if (equals)
      {
        ConnectionHandlerDescriptor ch =
          (ConnectionHandlerDescriptor)o;
        // Compare monitoring entries
        equals = (getMonitoringEntries().equals(ch.getMonitoringEntries()));
      }
    }
    return equals;
  }

  /**
   * Returns the port of the connection handler.
   * @return the port of the connection handler.
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Returns the name of the connection handler.
   * @return the name of the connection handler.
   */
  public String getName()
  {
    return name;
  }
}
