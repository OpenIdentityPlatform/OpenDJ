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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.guitools.statuspanel;

import org.opends.messages.Message;

/**
 * This class is used to represent a Listener and is aimed to be used by the
 * classes in the ListenersTableModel class.
 */
public class ListenerDescriptor
{
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
    LDAP,
    /**
     * LDAP secure protocol.
     */
    LDAPS,
    /**
     * JMX protocol.
     */
    JMX,
    /**
     * JMX secure protocol.
     */
    JMXS,
    /**
     * Other protocol.
     */
    OTHER
  }

  private State state;
  private String addressPort;
  private Protocol protocol;
  private Message protocolDescription;

  /**
   * Constructor for thid class.
   * @param addressPort the address port reprentation of the listener.
   * @param protocol the protocol of the listener.
   * @param protocolDescription the String used to describe the protocol.
   * @param state the state of the listener.
   */
  public ListenerDescriptor(String addressPort, Protocol protocol,
      Message protocolDescription, State state)
  {
    this.addressPort = addressPort;
    this.protocol = protocol;
    this.protocolDescription = protocolDescription;
    this.state = state;
  }

  /**
   * Returns the address port representation of the listener.
   * @return the address port representation of the listener.
   */
  public String getAddressPort()
  {
    return addressPort;
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
   * Returns the protocol description of the listener.
   * @return the protocol description of the listener.
   */
  public Message getProtocolDescription()
  {
    return protocolDescription;
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
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return (getAddressPort() + getProtocolDescription() +
        getState()).hashCode();
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
    else if (o instanceof ListenerDescriptor)
    {
      equals = hashCode() == o.hashCode();
    }
    return equals;
  }
}
