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
 *      Copyright 2008 Sun Microsystems, Inc.
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
