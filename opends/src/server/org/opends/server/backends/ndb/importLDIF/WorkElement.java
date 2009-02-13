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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.backends.ndb.importLDIF;

import org.opends.server.types.Entry;

/**
 * A work element passed on the work queue.
 */
public class WorkElement {

  // The entry to import.
  private Entry entry;

  // Used in replace mode, this is the entry to replace.
  private Entry existingEntry;

  // The context related to the entry.
  private DNContext context;

  /**
   * Create a work element instance.
   *
   * @param entry The entry to import.
   * @param context The context related to the entry.
   */
  private WorkElement(Entry entry, DNContext context )  {
    this.entry = entry;
    this.context = context;
  }

  /**
   * Static to create an work element.
   *
   * @param entry The entry to import.
   * @param context The context related to the entry.
   * @return  A work element to put on the queue.
   */
  public static
  WorkElement decode(Entry entry, DNContext context ) {
    return new WorkElement(entry, context);
  }

  /**
   * Return the entry to import.
   *
   * @return  The entry to import.
   */
  public Entry getEntry() {
    return entry;
  }

  /**
   * Return the context related to the entry.
   *
   * @return The context.
   */
  public DNContext getContext() {
    return context;
  }

  /**
   * Return an existing entry, used during replace mode.
   *
   * @return An existing entry.
   */
  public Entry getExistingEntry() {
    return existingEntry;
  }

  /**
   * Set the existing entry.
   *
   * @param existingEntry The existing entry to set.
   */
  public void setExistingEntry(Entry existingEntry) {
    this.existingEntry = existingEntry;
  }
}
