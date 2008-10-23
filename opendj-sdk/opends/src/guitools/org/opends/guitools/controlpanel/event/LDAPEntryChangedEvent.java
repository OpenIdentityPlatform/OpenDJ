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

import org.opends.server.types.Entry;

/**
 * Method that describes that an entry has changed.  This is used by the LDAP
 * entry editors.
 *
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
