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

import java.util.EventObject;

/**
 * This class defines an event for the browser.  It basically it is used to
 * communicate between the BrowserController and the NodeRefresher classes.
 * @author jvergara
 */
public class BrowserEvent extends EventObject
{
  private static final long serialVersionUID = 6476274376887062526L;

  /** The different types of events that we can have. */
  public enum Type
  {
    /** Update of the entry started. */
    UPDATE_START,
    /** Update of the entry ended. */
    UPDATE_END,
    /** Insert of children started. */
    INSERT_CHILDREN_START,
    /** Insert of children ended. */
    INSERT_CHILDREN_END,
    /**
     * The specified size limit (max number of children to be returned) in the
     * BrowserController was reached.
     */
    SIZE_LIMIT_REACHED
  }

  private Type type;

  /**
   * Constructor of the event.
   * @param source the Object that generated this event.
   * @param id the type of the event.
   */
  public BrowserEvent(Object source, Type id) {
    super(source);
    this.type = id;
  }

  /**
   * Returns the type of event.
   * @return the type of event.
   */
  public Type getType() {
    return type;
  }
}
