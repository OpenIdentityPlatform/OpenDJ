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
 * Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.event;

import java.util.Collections;
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;


/**
 * The event used to notify that a backend has been populated (using import
 * or restore for example).
 *
 */
public class BackendPopulatedEvent
{
  private Set<BackendDescriptor> backends;

  /**
   * The constructor of the event.
   * @param backends the set of populated backends.
   */
  public BackendPopulatedEvent(Set<BackendDescriptor> backends)
  {
    this.backends = Collections.unmodifiableSet(backends);
  }

  /**
   * Returns the set of populated backends.
   * @return the set of populated backends.
   */
  public Set<BackendDescriptor> getBackends()
  {
    return backends;
  }
}
