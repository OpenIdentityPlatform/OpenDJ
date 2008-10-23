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

//Note: in terms of synchronization, this implementation assumes that the
//interrupt method is only called in the event thread (this class is used
//when the user selects a node in the LDAP entry browser).
/**
 * The event that is create when there is an error reading an entry.  It is
 * used in the LDAP entry browser to notify of this kind of errors.
 */
public class EntryReadErrorEvent
{
  private Object source;
  private Throwable t;
  private String dn;

  /**
   * Constructor for the event.
   * @param source the source of this event.
   * @param dn the DN of the entry we were searching.
   * @param t the throwable that we got as error.
   */
  public EntryReadErrorEvent(Object source, String dn, Throwable t)
  {
    this.source = source;
    this.t = t;
    this.dn = dn;
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
   * Returns the throwable that we got as error.
   * @return the throwable that we got as error.
   */
  public Throwable getError()
  {
    return t;
  }

  /**
   * Returns the DN of the entry we were searching.
   * @return the DN of the entry we were searching.
   */
  public String getDN()
  {
    return dn;
  }
}
