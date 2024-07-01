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
 * Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.event;

import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;

/**
 * The event that describes a change in the configuration.  It will be created
 * in ControlCenterInfo when the configuration is read and there has been a
 * modification between the newly read configuration and the configuration we
 * read previously.
 *
 */
public class ConfigurationChangeEvent
{
  private Object source;
  private ServerDescriptor newDescriptor;

  /**
   * Constructor for the event.
   * @param source the source of this event.
   * @param newDescriptor the object describing the new configuration.
   */
  public ConfigurationChangeEvent(Object source, ServerDescriptor newDescriptor)
  {
    this.source = source;
    this.newDescriptor = newDescriptor;
  }

  /**
   * Returns the object describing the new configuration.
   * @return the object describing the new configuration.
   */
  public ServerDescriptor getNewDescriptor()
  {
    return newDescriptor;
  }

  /**
   * Returns the source of the event.
   * @return the source of the event.
   */
  public Object getSource()
  {
    return source;
  }
}
