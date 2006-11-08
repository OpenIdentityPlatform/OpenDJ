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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

/**
 * Implements an iterator over all the entry IDs in the backend.
 * As currently implemented, will return every entry ID ever
 * assigned, regardless of whether the entry still exists.
 */
public class IndexIteratorAllIds extends IndexIteratorRange
{
  /**
   * Create a new iterator over all the entry IDs in the backend.
   *
   * @param rootContainer The root container where IDs from this
   *                     iterator will cover.
   */
  public IndexIteratorAllIds(RootContainer rootContainer)
  {
    super(rootContainer.getLowestEntryID(), rootContainer.getHighestEntryID());
  }
}
