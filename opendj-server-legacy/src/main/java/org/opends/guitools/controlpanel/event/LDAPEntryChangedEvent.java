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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.event;

import org.forgerock.opendj.ldap.Entry;

/**
 * Method that describes that an entry has changed. This is used by the LDAP entry editors.
 */
public class LDAPEntryChangedEvent
{
  private Object source;
  private Entry entry;

  /**
   * Constructor of the event.
   * @param source the source of the event.
   * @param entry the entry that has been modified (the object contains the new
   * values of the entry).
   */
  public LDAPEntryChangedEvent(Object source, Entry entry)
  {
    this.source = source;
    this.entry = entry;
  }

  /**
   * Returns the entry that has been modified (the object contains the new
   * values of the entry).
   * @return the entry that has been modified.
   */
  public Entry getEntry()
  {
    return entry;
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
