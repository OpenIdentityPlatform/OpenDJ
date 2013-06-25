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

import java.util.EventObject;

/**
 * This class defines an event for the browser.  It basically it is used to
 * communicate between the BrowserController and the NodeRefresher classes.
 * @author jvergara
 *
 */
public class BrowserEvent extends EventObject
{
  private static final long serialVersionUID = 6476274376887062526L;

  /**
   * The different types of events that we can have.
   *
   */
  public enum Type
  {
    /**
     * Update of the entry started.
     */
    UPDATE_START,
    /**
     * Update of the entry ended.
     */
    UPDATE_END,
    /**
     * Insert of children started.
     */
    INSERT_CHILDREN_START,
    /**
     * Insert of children ended.
     */
    INSERT_CHILDREN_END,
    /**
     * The specified size limit (max number of children to be returned) in the
     * BrowserController was reached.
     */
    SIZE_LIMIT_REACHED

  };

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
