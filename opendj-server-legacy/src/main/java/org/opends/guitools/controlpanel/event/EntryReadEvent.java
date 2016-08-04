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
 * The class used to notify that a new entry has been successfully read.  Used
 * in the LDAP entry browser.
 */
public class EntryReadEvent
{
  private Object source;
  private Entry sr;

  /**
   * The event constructor.
   * @param source the source of the event.
   * @param sr the search result containing the entry that was read.
   */
  public EntryReadEvent(Object source, Entry sr)
  {
    this.source = source;
    this.sr = sr;
  }

  /**
   * Returns the source of the event.
   * @return the source of the event.
   */
  public Object getSource()
  {
    return source;
  }

  /**
   * Returns the search result containing the entry that was read.
   * @return the search result containing the entry that was read.
   */
  public Entry getSearchResult()
  {
    return sr;
  }
}
