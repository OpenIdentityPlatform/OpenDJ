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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.opends.server.types.ObjectClass;

/**
 * This is the event sent to notify the changes made in the superiors of a given
 * object class.  It is used mainly by the
 * {@link
 * org.opends.guitools.controlpanel.components.SuperiorObjectClassesEditor}
 * class.  It is linked to the {@link SuperiorObjectClassesChangedListener}
 * interface.
 *
 */
public class SuperiorObjectClassesChangedEvent
{
  private Object source;
  private Set<ObjectClass> newObjectClasses = new HashSet<ObjectClass>();

  /**
   * Constructor of the event.
   * @param source the source of the event.
   * @param newObjectClasses the set of new superior object classes.
   */
  public SuperiorObjectClassesChangedEvent(Object source,
      Set<ObjectClass> newObjectClasses)
  {
    this.source = source;
    this.newObjectClasses.addAll(newObjectClasses);
  }

  /**
   * Returns the source of the object.
   * @return the source of the object.
   */
  public Object getSource()
  {
    return source;
  }

  /**
   * Returns the new superior object classes.
   * @return the new superior object classes.
   */
  public Set<ObjectClass> getNewObjectClasses()
  {
    return Collections.unmodifiableSet(newObjectClasses);
  }
}
