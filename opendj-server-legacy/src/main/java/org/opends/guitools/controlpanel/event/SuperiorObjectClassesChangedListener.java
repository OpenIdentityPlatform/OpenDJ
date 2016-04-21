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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.event;

/**
 * This is the listener that receives events about the changes made in the superiors of a given
 * object class. It is used mainly by the
 * {@link org.opends.guitools.controlpanel.ui.components.SuperiorObjectClassesEditor} class.
 */
public interface SuperiorObjectClassesChangedListener
{
  /**
   * Method called to notify the listener that a change has been made in the
   * superiors of an object class.
   * @param ev the event describing the new superior object classes.
   */
  void parentObjectClassesChanged(SuperiorObjectClassesChangedEvent ev);
}
