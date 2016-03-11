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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.ObjectClass;

/**
 * This is the event sent to notify the changes made in the superiors of a given object class. It is
 * used mainly by the
 * {@link org.opends.guitools.controlpanel.ui.components.SuperiorObjectClassesEditor} class. It is
 * linked to the {@link SuperiorObjectClassesChangedListener} interface.
 */
public class SuperiorObjectClassesChangedEvent
{
  private Object source;
  private Set<ObjectClass> newObjectClasses = new HashSet<>();

  /**
   * Constructor of the event.
   * @param source the source of the event.
   * @param newObjectClasses the set of new superior object classes.
   */
  public SuperiorObjectClassesChangedEvent(Object source, Set<ObjectClass> newObjectClasses)
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
